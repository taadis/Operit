package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MCP 服务器插件安装工具类
 *
 * 负责从 GitHub 下载 MCP 服务器插件仓库，并将其解压到设备存储中
 */
class MCPInstaller(private val context: Context) {

    companion object {
        private const val TAG = "MCPInstaller"
        private const val BUFFER_SIZE = 8192 // 8KB
        private const val DEFAULT_TIMEOUT = 15000 // 15 seconds - reduced from 30 seconds
        private const val CONNECT_TIMEOUT = 10000 // 10 seconds for connection
        private const val READ_TIMEOUT = 15000 // 15 seconds for read

        // 插件安装目录
        private const val PLUGINS_DIR_NAME = "mcp_plugins"
        // 外部存储 Operit 目录名称
        private const val OPERIT_DIR_NAME = "Operit"
    }

    // 获取插件目录
    val pluginsBaseDir by lazy {
        // 使用下载目录中的 Operit 文件夹
        val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, OPERIT_DIR_NAME)
        val pluginsDir = File(operitDir, PLUGINS_DIR_NAME)

        // 确保目录存在
        if (!operitDir.exists()) {
            operitDir.mkdirs()
        }

        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }

        // 如果无法使用外部下载目录，回退到应用私有存储
        if (pluginsDir.exists() && pluginsDir.canWrite()) {
            pluginsDir
        } else {
            // 回退到应用私有目录
            val fallbackDir =
                    context.getExternalFilesDir(PLUGINS_DIR_NAME)
                            ?: File(context.filesDir, PLUGINS_DIR_NAME).also {
                                if (!it.exists()) it.mkdirs()
                            }

            Log.w(TAG, "无法使用下载目录，使用应用私有目录: ${fallbackDir.path}")
            fallbackDir
        }
    }

    /**
     * 安装 MCP 插件
     *
     * @param server 要安装的 MCP 服务器
     * @param progressCallback 安装进度回调
     * @return 安装结果
     */
    suspend fun installPlugin(
            server: MCPServer,
            progressCallback: (InstallProgress) -> Unit
    ): InstallResult {
        progressCallback(InstallProgress.Preparing)

        try {
            val repoOwnerAndName = extractOwnerAndRepo(server.repoUrl)
            if (repoOwnerAndName == null) {
                Log.e(TAG, "无法从 URL 提取仓库所有者和名称: ${server.repoUrl}")
                return InstallResult.Error("无效的 GitHub 仓库 URL")
            }

            val (owner, repoName) = repoOwnerAndName

            // 创建目标目录
            val pluginDir = File(pluginsBaseDir, server.id)
            if (pluginDir.exists()) {
                Log.d(TAG, "删除已存在的插件目录: ${pluginDir.path}")
                pluginDir.deleteRecursively()
            }
            pluginDir.mkdirs()

            // 构建 ZIP 下载 URL
            Log.d(TAG, "准备下载仓库: $owner/$repoName")

            // 下载并解压
            progressCallback(InstallProgress.Downloading(0))
            // 尝试多种下载方式
            val zipFile =
                    tryDownloadZipWithMultipleMethods(owner, repoName, server.id, progressCallback)

            if (zipFile == null || !zipFile.exists()) {
                return InstallResult.Error("下载仓库 ZIP 文件失败")
            }

            progressCallback(InstallProgress.Extracting(0))
            val extractSuccess = extractZipFile(zipFile, pluginDir, progressCallback)

            // 删除 ZIP 文件
            zipFile.delete()

            if (!extractSuccess) {
                // 清理失败的安装
                pluginDir.deleteRecursively()
                return InstallResult.Error("解压仓库文件失败")
            }

            // 获取解压后的根目录（GitHub ZIP 解压后会有一个目录层次）
            val extractedDirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if (extractedDirs.isEmpty()) {
                return InstallResult.Error("解压后没有找到仓库目录")
            }

            // 一般格式是 {repoName}-{branch}，如 "WireMCP-main"
            val mainDir = extractedDirs.first()
            Log.d(TAG, "插件解压成功，主目录: ${mainDir.path}")

            // 查找插件配置文件
            val configFile = findConfigFile(mainDir)
            if (configFile == null) {
                // 不要清理，可能是有效插件但没有找到配置文件
                Log.w(TAG, "未找到插件配置文件")
            } else {
                Log.d(TAG, "找到插件配置文件: ${configFile.path}")
            }

            progressCallback(InstallProgress.Finished)
            return InstallResult.Success(mainDir.path)
        } catch (e: Exception) {
            Log.e(TAG, "安装插件失败", e)
            return InstallResult.Error("安装插件时出错: ${e.message}")
        }
    }

    /**
     * 下载仓库 ZIP 文件
     *
     * @param zipUrl ZIP 下载 URL
     * @param serverId 服务器 ID，用于临时文件命名
     * @param progressCallback 进度回调
     * @param owner 仓库所有者
     * @param repoName 仓库名称
     * @return 下载的 ZIP 文件
     */
    private suspend fun downloadRepositoryZip(
            zipUrl: String,
            serverId: String,
            progressCallback: (InstallProgress) -> Unit,
            owner: String? = null,
            repoName: String? = null
    ): File? =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "开始下载ZIP: $zipUrl")
                val tempFile = File(context.cacheDir, "mcp_${serverId}_repo.zip")

                try {
                    val url = URL(zipUrl)
                    // 尝试使用OkHttp作为备选方案
                    try {
                        // 基本的HttpURLConnection
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = CONNECT_TIMEOUT // 使用更短的连接超时
                        connection.readTimeout = READ_TIMEOUT
                        connection.doInput = true
                        Log.d(TAG, "正在连接到: $zipUrl")

                        // 尝试使用常见的用户代理，有些服务器会拒绝不带用户代理的请求
                        connection.setRequestProperty(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"
                        )

                        try {
                            connection.connect()
                        } catch (e: Exception) {
                            Log.e(TAG, "连接到 $zipUrl 失败: ${e.message}")
                            throw e
                        }

                        val responseCode = connection.responseCode
                        if (responseCode != HttpURLConnection.HTTP_OK) {
                            Log.e(TAG, "下载仓库ZIP失败，HTTP响应码: $responseCode")
                            return@withContext null
                        }

                        val contentLength = connection.contentLength.toLong()
                        Log.d(TAG, "开始下载，文件大小: $contentLength 字节")

                        val inputStream = BufferedInputStream(connection.inputStream)
                        val outputStream = FileOutputStream(tempFile)

                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var totalBytesRead: Long = 0

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // 计算下载进度百分比
                            val progress =
                                    if (contentLength > 0) {
                                        (totalBytesRead * 100 / contentLength).toInt()
                                    } else {
                                        -1 // 未知大小
                                    }

                            // 每10%更新一次进度
                            if (progress % 10 == 0 || progress == 100) {
                                progressCallback(InstallProgress.Downloading(progress))
                            }
                        }

                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()

                        Log.d(TAG, "下载完成，保存到: ${tempFile.path}")
                        return@withContext tempFile
                    } catch (e: Exception) {
                        Log.e(TAG, "下载仓库ZIP文件失败: ${e.message}", e)

                        // 尝试备选下载URL，使用raw.githubusercontent.com
                        if (owner != null && repoName != null) {
                            try {
                                Log.d(TAG, "尝试使用备选下载URL")
                                // 替换GitHub URL以使用raw.githubusercontent.com
                                val altZipUrl =
                                        zipUrl.replace(
                                                "github.com/$owner/$repoName/archive/refs/heads/main.zip",
                                                "raw.githubusercontent.com/$owner/$repoName/main/archive.zip"
                                        )
                                Log.d(TAG, "备选下载URL: $altZipUrl")

                                val altConnection =
                                        URL(altZipUrl).openConnection() as HttpURLConnection
                                altConnection.connectTimeout = DEFAULT_TIMEOUT
                                altConnection.readTimeout = DEFAULT_TIMEOUT
                                altConnection.connect()

                                if (altConnection.responseCode == HttpURLConnection.HTTP_OK) {
                                    val altInputStream =
                                            BufferedInputStream(altConnection.inputStream)
                                    val altOutputStream = FileOutputStream(tempFile)

                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var bytesRead: Int

                                    while (altInputStream.read(buffer).also { bytesRead = it } !=
                                            -1) {
                                        altOutputStream.write(buffer, 0, bytesRead)
                                    }

                                    altOutputStream.flush()
                                    altOutputStream.close()
                                    altInputStream.close()

                                    Log.d(TAG, "备选下载完成, 保存到: ${tempFile.path}")
                                    return@withContext tempFile
                                } else {
                                    Log.e(TAG, "备选下载也失败了，HTTP响应码: ${altConnection.responseCode}")
                                }
                            } catch (e2: Exception) {
                                Log.e(TAG, "备选下载也失败了: ${e2.message}", e2)
                            }
                        } else {
                            Log.e(TAG, "无法使用备选下载URL: owner或repoName为null")
                        }

                        if (tempFile.exists()) tempFile.delete()
                        return@withContext null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "下载仓库ZIP文件失败: ${e.message}", e)
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext null
                }
            }

    /** 尝试多种方法下载ZIP文件 */
    private suspend fun tryDownloadZipWithMultipleMethods(
            owner: String,
            repoName: String,
            serverId: String,
            progressCallback: (InstallProgress) -> Unit
    ): File? {
        // 常见的默认分支名称
        val branches = listOf("main", "master", "develop", "dev")

        for (branch in branches) {
            // 尝试各种分支名称的标准下载
            val standardUrl = "https://github.com/$owner/$repoName/archive/refs/heads/$branch.zip"
            Log.d(TAG, "尝试分支 $branch: 标准GitHub ZIP - $standardUrl")
            val standardFile =
                    downloadRepositoryZip(standardUrl, serverId, progressCallback, owner, repoName)
            if (standardFile != null && standardFile.exists() && standardFile.length() > 0) {
                Log.d(TAG, "分支 $branch 标准下载成功")
                return standardFile
            }

            // 尝试各种分支名称的codeload下载
            val codeloadUrl = "https://codeload.github.com/$owner/$repoName/zip/refs/heads/$branch"
            Log.d(TAG, "尝试分支 $branch: codeload.github.com - $codeloadUrl")
            val codeloadFile =
                    downloadRepositoryZip(codeloadUrl, serverId, progressCallback, owner, repoName)
            if (codeloadFile != null && codeloadFile.exists() && codeloadFile.length() > 0) {
                Log.d(TAG, "分支 $branch codeload下载成功")
                return codeloadFile
            }
        }

        // 方法2: 使用GitHub API的tarball接口 (提供tar.gz格式，但对某些网络情况可能更好)
        // 注意：API接口会自动使用默认分支，不需要指定分支名称
        val tarballUrl = "https://api.github.com/repos/$owner/$repoName/tarball"
        Log.d(TAG, "尝试方法: GitHub API tarball - $tarballUrl")
        val tarballFile =
                downloadRepositoryZip(tarballUrl, serverId, progressCallback, owner, repoName)
        if (tarballFile != null && tarballFile.exists() && tarballFile.length() > 0) {
            Log.d(TAG, "GitHub API tarball下载成功")
            return tarballFile
        }

        // 方法3: 尝试Gitee镜像 (这是中国用户的备选方案)
        // 尝试不同的分支名称
        for (branch in branches) {
            val giteeUrl = "https://gitee.com/$owner/$repoName/repository/archive/$branch.zip"
            Log.d(TAG, "尝试分支 $branch: Gitee镜像 - $giteeUrl")
            val giteeFile =
                    downloadRepositoryZip(giteeUrl, serverId, progressCallback, owner, repoName)
            if (giteeFile != null && giteeFile.exists() && giteeFile.length() > 0) {
                Log.d(TAG, "分支 $branch Gitee下载成功")
                return giteeFile
            }
        }

        Log.e(TAG, "所有下载方法都失败")
        return null
    }

    /**
     * 解压 ZIP 文件到目标目录
     *
     * @param zipFile ZIP 文件
     * @param targetDir 目标目录
     * @param progressCallback 进度回调
     * @return 解压是否成功
     */
    private suspend fun extractZipFile(
            zipFile: File,
            targetDir: File,
            progressCallback: (InstallProgress) -> Unit
    ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = zipFile.inputStream()
                    val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))

                    var entry = zipInputStream.nextEntry
                    val totalEntries = countZipEntries(zipFile)
                    var extractedCount = 0

                    while (entry != null) {
                        val entryName = entry.name
                        val outFile = File(targetDir, entryName)

                        // 跳过以下类型的文件
                        if (entryName.contains("__MACOSX") || entryName.endsWith(".DS_Store")) {
                            zipInputStream.closeEntry()
                            entry = zipInputStream.nextEntry
                            continue
                        }

                        // 创建必要的目录
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            // 确保父目录存在
                            outFile.parentFile?.mkdirs()

                            // 写出文件
                            val outputStream = FileOutputStream(outFile)
                            val buffer = ByteArray(BUFFER_SIZE)
                            var len: Int

                            while (zipInputStream.read(buffer).also { len = it } > 0) {
                                outputStream.write(buffer, 0, len)
                            }

                            outputStream.close()
                        }

                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry

                        // 更新解压进度
                        extractedCount++
                        val progress =
                                if (totalEntries > 0) {
                                    (extractedCount * 100 / totalEntries).toInt()
                                } else {
                                    -1
                                }

                        // 每10%更新一次进度
                        if (progress % 10 == 0 || progress == 100) {
                            progressCallback(InstallProgress.Extracting(progress))
                        }
                    }

                    zipInputStream.close()
                    inputStream.close()

                    Log.d(TAG, "解压完成，文件解压到: ${targetDir.path}")
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "解压ZIP文件失败", e)
                    return@withContext false
                }
            }

    /** 计算 ZIP 文件中的条目数量 */
    private fun countZipEntries(zipFile: File): Int {
        var count = 0
        try {
            val inputStream = zipFile.inputStream()
            val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))

            while (zipInputStream.nextEntry != null) {
                count++
                zipInputStream.closeEntry()
            }

            zipInputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "计算ZIP条目数量失败", e)
        }
        return count
    }

    /** 从 GitHub 仓库 URL 中提取所有者和仓库名 */
    private fun extractOwnerAndRepo(repoUrl: String): Pair<String, String>? {
        val regex = "https?://(?:www\\.)?github\\.com/([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)".toRegex()
        val matchResult = regex.find(repoUrl) ?: return null

        val owner = matchResult.groupValues[1]
        val repo = matchResult.groupValues[2]

        if (owner.isBlank() || repo.isBlank()) return null

        return owner to repo
    }

    /** 查找插件配置文件 可能是 README.md, llms-install.md 或其他文件 */
    private fun findConfigFile(pluginDir: File): File? {
        // 首先查找 llms-install.md
        var configFile = File(pluginDir, "llms-install.md")
        if (configFile.exists()) return configFile

        // 然后查找 README.md
        configFile = File(pluginDir, "README.md")
        if (configFile.exists()) return configFile

        // 最后查找任何 md 文件
        return pluginDir.listFiles()?.find { it.extension.equals("md", ignoreCase = true) }
    }

    /**
     * 卸载插件
     *
     * @param serverId 服务器 ID
     * @return 是否成功卸载
     */
    suspend fun uninstallPlugin(serverId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val pluginDir = File(pluginsBaseDir, serverId)
                if (!pluginDir.exists()) {
                    Log.w(TAG, "卸载插件失败：目录不存在 ${pluginDir.path}")
                    return@withContext false
                }

                val result = pluginDir.deleteRecursively()
                Log.d(TAG, "插件卸载${if (result) "成功" else "失败"}: $serverId")
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "卸载插件时发生错误", e)
                return@withContext false
            }
        }
    }

    /**
     * 检查插件是否已安装
     *
     * @param serverId 服务器 ID
     * @return 是否已安装
     */
    fun isPluginInstalled(serverId: String): Boolean {
        val pluginDir = File(pluginsBaseDir, serverId)
        return pluginDir.exists() && pluginDir.isDirectory
    }

    /**
     * 获取已安装插件的路径
     *
     * @param serverId 服务器 ID
     * @return 插件路径，未安装则返回 null
     */
    fun getInstalledPluginPath(serverId: String): String? {
        val pluginDir = File(pluginsBaseDir, serverId)
        if (!pluginDir.exists() || !pluginDir.isDirectory) {
            return null
        }

        // 查找实际的插件目录（GitHub ZIP 解压后会有一个目录层次）
        val extractedDirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (extractedDirs.isEmpty()) {
            return null
        }

        return extractedDirs.first().path
    }
}

/** 安装进度状态 */
sealed class InstallProgress {
    object Preparing : InstallProgress()
    data class Downloading(val progress: Int) : InstallProgress() // -1 表示未知进度
    data class Extracting(val progress: Int) : InstallProgress() // -1 表示未知进度
    object Finished : InstallProgress()
}

/** 安装结果 */
sealed class InstallResult {
    data class Success(val pluginPath: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}

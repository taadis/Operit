package com.ai.assistance.operit.data.updates

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ai.assistance.operit.R
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

// 更新状态
sealed class UpdateStatus {
    object Initial : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(
            val newVersion: String,
            val updateUrl: String,
            val releaseNotes: String,
            val downloadUrl: String = "" // 添加下载URL
    ) : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus() // 添加下载进度状态
    object UpToDate : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

/** UpdateManager - 处理应用更新的核心类 负责检查更新、下载更新包和安装更新 */
class UpdateManager private constructor(private val context: Context) {
    private val TAG = "UpdateManager"

    // 更新状态LiveData，可从UI中观察
    private val _updateStatus = MutableLiveData<UpdateStatus>(UpdateStatus.Initial)
    val updateStatus: LiveData<UpdateStatus> = _updateStatus

    // 下载ID
    private var downloadId: Long = -1L

    // 下载完成的广播接收器
    private var downloadCompleteReceiver: BroadcastReceiver? = null

    init {
        Log.d(TAG, "UpdateManager initialized")
    }

    companion object {
        @Volatile private var INSTANCE: UpdateManager? = null

        fun getInstance(context: Context): UpdateManager {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance = UpdateManager(context.applicationContext)
                        INSTANCE = instance
                        instance
                    }
        }

        /**
         * 比较两个版本号
         * @return -1 如果v1 < v2, 0 如果 v1 == v2, 1 如果 v1 > v2
         */
        fun compareVersions(v1: String, v2: String): Int {
            // 移除可能的 'v' 前缀
            val version1 = v1.removePrefix("v")
            val version2 = v2.removePrefix("v")

            val parts1 = version1.split(".")
            val parts2 = version2.split(".")

            val maxLength = maxOf(parts1.size, parts2.size)

            for (i in 0 until maxLength) {
                val part1 = if (i < parts1.size) parts1[i].toIntOrNull() ?: 0 else 0
                val part2 = if (i < parts2.size) parts2[i].toIntOrNull() ?: 0 else 0

                if (part1 < part2) return -1
                if (part1 > part2) return 1
            }

            return 0
        }

        /** 检查更新，返回更新状态 用于从MainActivity直接检查更新 */
        suspend fun checkForUpdates(context: Context, currentVersion: String): UpdateStatus {
            val manager = getInstance(context)
            return manager.checkForUpdatesInternal(currentVersion)
        }
    }

    /** 注册下载完成广播接收器 */
    fun registerDownloadReceiver(onComplete: (Uri?) -> Unit) {
        // 清理旧的接收器
        unregisterDownloadReceiver()

        // 创建新的接收器
        downloadCompleteReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId && downloadId != -1L) {
                            val downloadManager =
                                    context.getSystemService(Context.DOWNLOAD_SERVICE) as
                                            DownloadManager
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = downloadManager.query(query)

                            var uri: Uri? = null
                            if (cursor.moveToFirst()) {
                                val columnIndex =
                                        cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                if (columnIndex != -1) {
                                    val status = cursor.getInt(columnIndex)
                                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                        // 下载成功，获取文件URI
                                        val uriColumnIndex =
                                                cursor.getColumnIndex(
                                                        DownloadManager.COLUMN_LOCAL_URI
                                                )
                                        if (uriColumnIndex != -1) {
                                            val uriString = cursor.getString(uriColumnIndex)
                                            uri = Uri.parse(uriString)
                                        }
                                    } else {
                                        _updateStatus.postValue(
                                                UpdateStatus.Error("下载失败，状态码: $status")
                                        )
                                    }
                                }
                            }
                            cursor.close()

                            // 调用回调并传递URI
                            onComplete(uri)
                        }
                    }
                }

        // 注册接收器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                    downloadCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                    downloadCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        Log.d(TAG, "Download receiver registered")
    }

    /** 取消注册下载接收器 */
    fun unregisterDownloadReceiver() {
        downloadCompleteReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "Download receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
        downloadCompleteReceiver = null
    }

    /** 开始更新检查流程 */
    suspend fun checkForUpdates(currentVersion: String) {
        _updateStatus.postValue(UpdateStatus.Checking)

        try {
            val result = checkForUpdatesInternal(currentVersion)
            _updateStatus.postValue(result)
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            _updateStatus.postValue(UpdateStatus.Error("更新检查失败: ${e.message}"))
        }
    }

    /** 检查更新的内部实现 */
    private suspend fun checkForUpdatesInternal(currentVersion: String): UpdateStatus {
        return withContext(Dispatchers.IO) {
            try {
                // 从字符串资源中获取GitHub仓库信息
                val aboutWebsite = context.getString(R.string.about_website)

                // 解析GitHub仓库链接 - 处理HTML格式
                val htmlContent = aboutWebsite.replace("&lt;", "<").replace("&gt;", ">")
                val githubUrlPattern = "https://github.com/([^/\"<>]+)/([^/\"<>]+)".toRegex()
                val matchResult = githubUrlPattern.find(htmlContent)

                val (repoOwner, repoName) =
                        if (matchResult != null) {
                            Pair(matchResult.groupValues[1], matchResult.groupValues[2])
                        } else {
                            Pair("AAswordman", "Operit") // 默认值
                        }

                val allReleasesUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases"
                var tagName = ""
                var htmlUrl = ""
                var body = ""
                var downloadUrl = ""

                // 尝试获取所有发布版本
                val allReleasesConn = URL(allReleasesUrl).openConnection() as HttpURLConnection
                allReleasesConn.requestMethod = "GET"
                allReleasesConn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (allReleasesConn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response =
                            allReleasesConn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)

                    if (jsonArray.length() > 0) {
                        // 获取第一个发布（最新的）
                        val latestRelease = jsonArray.getJSONObject(0)
                        tagName = latestRelease.getString("tag_name")
                        htmlUrl = latestRelease.getString("html_url")
                        body = latestRelease.optString("body", "")

                        // 寻找APK下载地址
                        val assets = latestRelease.optJSONArray("assets")
                        if (assets != null && assets.length() > 0) {
                            // 查找apk文件
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.getString("name")
                                if (name.endsWith(".apk")) {
                                    downloadUrl = asset.getString("browser_download_url")
                                    break
                                }
                            }
                        }

                        // 如果找到版本但没找到下载链接
                        if (downloadUrl.isEmpty()) {
                            downloadUrl = htmlUrl // 退回到使用发布页面
                        }

                        // 比较版本
                        return@withContext if (compareVersions(tagName, currentVersion) > 0) {
                            UpdateStatus.Available(tagName, htmlUrl, body, downloadUrl)
                        } else {
                            UpdateStatus.UpToDate
                        }
                    } else {
                        return@withContext UpdateStatus.Error("仓库 $repoOwner/$repoName 没有发布版本")
                    }
                } else if (allReleasesConn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@withContext UpdateStatus.Error("仓库不存在或无法访问: $repoOwner/$repoName")
                } else {
                    return@withContext UpdateStatus.Error(
                            "API请求失败: HTTP ${allReleasesConn.responseCode}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                return@withContext UpdateStatus.Error("更新检查失败: ${e.message}")
            }
        }
    }

    /** 下载APK更新 */
    suspend fun downloadUpdate(downloadUrl: String, version: String) {
        try {
            Log.d(TAG, "Starting download from $downloadUrl")
            _updateStatus.postValue(UpdateStatus.Downloading(0f))

            val fileName = "Operit-${version}.apk"

            val request =
                    DownloadManager.Request(Uri.parse(downloadUrl))
                            .setTitle("下载 Operit ${version}")
                            .setDescription("正在下载新版本...")
                            .setNotificationVisibility(
                                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                            )
                            .setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    fileName
                            )

            val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)

            // 循环监控下载进度
            withContext(Dispatchers.IO) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                var downloading = true

                while (downloading && _updateStatus.value is UpdateStatus.Downloading) {
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIndex != -1) {
                            val status = cursor.getInt(statusIndex)

                            when (status) {
                                DownloadManager.STATUS_RUNNING -> {
                                    val bytesDownloadedIndex =
                                            cursor.getColumnIndex(
                                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                            )
                                    val bytesTotalIndex =
                                            cursor.getColumnIndex(
                                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                                            )

                                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                                        val bytesTotal = cursor.getLong(bytesTotalIndex)

                                        if (bytesTotal > 0) {
                                            val progress =
                                                    bytesDownloaded.toFloat() / bytesTotal.toFloat()
                                            _updateStatus.postValue(
                                                    UpdateStatus.Downloading(progress)
                                            )
                                        }
                                    }
                                }
                                DownloadManager.STATUS_SUCCESSFUL,
                                DownloadManager.STATUS_FAILED -> {
                                    downloading = false
                                }
                            }
                        }
                    }
                    cursor.close()
                    kotlinx.coroutines.delay(500) // 每0.5秒更新一次
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _updateStatus.postValue(UpdateStatus.Error("下载失败: ${e.message}"))
        }
    }

    /** 创建用于安装APK的Intent */
    fun createInstallIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
    }
}

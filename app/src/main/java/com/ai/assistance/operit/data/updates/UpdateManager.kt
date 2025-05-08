package com.ai.assistance.operit.data.updates

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ai.assistance.operit.R
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

// 更新状态 - 移除下载相关状态
sealed class UpdateStatus {
    object Initial : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(
            val newVersion: String,
            val updateUrl: String,
            val releaseNotes: String,
            val downloadUrl: String = "" // 保留下载URL字段用于浏览器打开
    ) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

/** UpdateManager - 处理应用更新的核心类 负责检查更新 */
class UpdateManager private constructor(private val context: Context) {
    private val TAG = "UpdateManager"

    // 更新状态LiveData，可从UI中观察
    private val _updateStatus = MutableLiveData<UpdateStatus>(UpdateStatus.Initial)
    val updateStatus: LiveData<UpdateStatus> = _updateStatus

    init {
        Log.d(TAG, "UpdateManager initialized")
    }

    companion object {
        @Volatile private var INSTANCE: UpdateManager? = null

        // 可用的GitHub加速镜像站点列表
        private val GITHUB_PROXY_URLS = listOf(
            "https://ghfast.top/",         // 目前国内可访问的最佳选择
            "https://hub.gitmirror.com/",  // 备选源
            "https://github.moeyy.xyz/",   // 另一个备选
            "https://github.abskoop.workers.dev/"  // 最后的备选
        )
        
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
        
        /**
         * 获取给定原始GitHub URL的加速下载URL
         * @param originalUrl 从GitHub获取的原始URL
         * @return 加速的下载URL
         */
        fun getAcceleratedDownloadUrl(version: String, originalUrl: String): String {
            // 直接使用第一个加速镜像
            val proxyUrl = GITHUB_PROXY_URLS[0]
            
            // 确认URL是GitHub的下载链接
            if (originalUrl.contains("github.com") && 
                (originalUrl.contains("/releases/download/") || 
                 originalUrl.contains("/archive/") ||
                 originalUrl.contains("/blob/"))) {
                // 返回加速链接
                return "$proxyUrl$originalUrl"
            }
            
            // 如果不是GitHub下载链接，则返回原始链接
            return originalUrl
        }
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
}

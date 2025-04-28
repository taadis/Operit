package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 图片缓存类，用于缓存MCP服务器图标，避免重复加载 */
object MCPImageCache {
    private const val TAG = "MCPImageCache"
    private const val VERBOSE_LOGGING = false // 关闭详细日志以删除图片相关日志

    // 超时设置（毫秒）- 显著减少以符合用户体验预期
    private const val CONNECTION_TIMEOUT = 2000 // 2秒连接超时
    private const val READ_TIMEOUT = 2000 // 2秒读取超时
    private const val COROUTINE_TIMEOUT = 3000L // 3秒总超时（包括重试）
    private const val FAST_FAIL_TIMEOUT = 2000L // 2秒快速失败超时

    // 磁盘缓存目录名
    private const val CACHE_DIR_NAME = "mcp_images"

    // 应用上下文，用于访问缓存目录
    private var appContext: Context? = null

    // 磁盘缓存目录
    private val diskCacheDir: File?
        get() =
                appContext?.let {
                    File(it.cacheDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }
                }

    // 可能被墙的域名列表
    private val POTENTIALLY_BLOCKED_DOMAINS =
            listOf(
                    "twitter.com",
                    "twimg.com",
                    "t.co",
                    "facebook.com",
                    "fbcdn.net",
                    "instagram.com",
                    "cdninstagram.com",
                    "google.com",
                    "googleapis.com",
                    "gstatic.com",
                    "githubusercontent.com" // 添加可能不稳定的GitHub原始内容链接
            )

    // 默认的图标URL（在所有URL都失败时使用）
    private const val DEFAULT_LOGO_URL =
            "https://raw.githubusercontent.com/modelcontextprotocol/servers/main/assets/logo.png"

    // 使用LruCache实现内存缓存，设置最大缓存大小为10MB
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 使用1/8的可用内存

    private val memoryCache =
            object : LruCache<String, Bitmap>(cacheSize) {
                override fun sizeOf(key: String, bitmap: Bitmap): Int {
                    // 返回以KB为单位的大小
                    return bitmap.byteCount / 1024
                }
            }

    /** 初始化缓存，必须在使用前调用 */
    fun initialize(context: Context) {
        appContext = context.applicationContext

        // 确保缓存目录存在
        diskCacheDir?.mkdirs()

        Log.d(TAG, "MCPImageCache 已初始化，缓存目录: ${diskCacheDir?.path}")
    }

    /** 从内存缓存中获取图片 */
    fun getBitmapFromCache(url: String): Bitmap? {
        return memoryCache.get(url)
    }

    /** 从磁盘缓存获取图片 */
    private fun getBitmapFromDiskCache(url: String): Bitmap? {
        val cacheFile = getCacheFileForUrl(url) ?: return null

        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            return null
        }

        return try {
            BitmapFactory.decodeFile(cacheFile.path)
        } catch (e: Exception) {
            Log.e(TAG, "读取磁盘缓存图片失败: ${e.message}")
            null
        }
    }

    /** 将图片添加到内存缓存 */
    fun addBitmapToCache(url: String, bitmap: Bitmap) {
        if (getBitmapFromCache(url) == null) {
            memoryCache.put(url, bitmap)
        }
    }

    /** 将图片保存到磁盘缓存 */
    private fun saveBitmapToDiskCache(url: String, bitmap: Bitmap): Boolean {
        val cacheFile = getCacheFileForUrl(url) ?: return false

        return try {
            FileOutputStream(cacheFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "保存图片到磁盘缓存: $url -> ${cacheFile.path}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存图片到磁盘缓存失败: ${e.message}")
            false
        }
    }

    /** 获取URL对应的缓存文件 */
    private fun getCacheFileForUrl(url: String): File? {
        val cacheDir = diskCacheDir ?: return null

        // 使用URL的MD5哈希作为文件名，避免特殊字符和长度问题
        val fileName = hashKeyForDisk(url)
        return File(cacheDir, fileName)
    }

    /** 将URL转换为MD5哈希，用作文件名 */
    private fun hashKeyForDisk(key: String): String {
        try {
            val md = MessageDigest.getInstance("MD5")
            md.update(key.toByteArray())
            val digest = md.digest()

            // 将字节数组转换为十六进制字符串
            val hexString = StringBuilder()
            for (b in digest) {
                val hex = Integer.toHexString(0xFF and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            return hexString.toString()
        } catch (e: Exception) {
            // 如果MD5失败，使用URL的哈希码（不太理想但作为备用）
            return key.hashCode().toString()
        }
    }

    /** 检查URL是否可能被墙 */
    private fun isPotentiallyBlocked(url: String): Boolean {
        return POTENTIALLY_BLOCKED_DOMAINS.any { domain -> url.contains(domain, ignoreCase = true) }
    }

    /**
     * 加载带有快速失败回退机制的图片
     * 可以尝试多个备选URL，在2秒内返回任何一个成功的结果，或者默认图标
     */
    suspend fun loadImageWithFallback(serverName: String, category: String): Bitmap? {
        // 获取Logo URL
        val primaryUrl = MCPRepositoryConstants.OfficialMCPConstants.OfficialServerLogos.getLogoUrl(serverName, category)
        
        return withContext(Dispatchers.IO) {
            try {
                // 1. 先尝试从内存缓存获取
                getBitmapFromCache(primaryUrl)?.let {
                    return@withContext it
                }

                // 2. 再尝试从磁盘缓存获取
                getBitmapFromDiskCache(primaryUrl)?.let { bitmap ->
                    // 将从磁盘加载的图片也放入内存缓存
                    addBitmapToCache(primaryUrl, bitmap)
                    return@withContext bitmap
                }
                
                // 3. 如果内存和磁盘缓存都没有，快速尝试从网络加载
                val bitmap = loadImageWithTimeout(primaryUrl, FAST_FAIL_TIMEOUT)
                if (bitmap != null) {
                    addBitmapToCache(primaryUrl, bitmap)
                    saveBitmapToDiskCache(primaryUrl, bitmap)
                    return@withContext bitmap
                }
                
                // 4. 如果快速尝试失败，立即使用默认图标
                loadDefaultLogo()
            } catch (e: Exception) {
                Log.e(TAG, "加载图片失败: ${e.message}")
                loadDefaultLogo()
            }
        }
    }
    
    /** 加载默认Logo */
    private suspend fun loadDefaultLogo(): Bitmap? {
        // 检查默认Logo是否已经在缓存中
        getBitmapFromCache(DEFAULT_LOGO_URL)?.let {
            return it
        }
        
        // 尝试从磁盘缓存加载
        getBitmapFromDiskCache(DEFAULT_LOGO_URL)?.let { bitmap ->
            addBitmapToCache(DEFAULT_LOGO_URL, bitmap)
            return bitmap
        }
        
        // 尝试从网络加载默认Logo，最多等待3秒
        return try {
            val bitmap = loadImageFromNetwork(DEFAULT_LOGO_URL)
            if (bitmap != null) {
                addBitmapToCache(DEFAULT_LOGO_URL, bitmap)
                saveBitmapToDiskCache(DEFAULT_LOGO_URL, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "加载默认图标失败: ${e.message}")
            null
        }
    }

    /** 从URL加载图片，处理重定向，并自动缓存结果 */
    suspend fun loadImage(url: String): Bitmap? {
        // 检查URL是否可能被墙
        if (isPotentiallyBlocked(url)) {
            Log.d(TAG, "URL可能不可用，跳过: $url")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                // 1. 先尝试从内存缓存获取
                getBitmapFromCache(url)?.let {
                    if (VERBOSE_LOGGING) Log.d(TAG, "从内存缓存加载图片: $url")
                    return@withContext it
                }

                // 2. 再尝试从磁盘缓存获取
                getBitmapFromDiskCache(url)?.let { bitmap ->
                    if (VERBOSE_LOGGING) Log.d(TAG, "从磁盘缓存加载图片: $url")
                    // 将从磁盘加载的图片也放入内存缓存
                    addBitmapToCache(url, bitmap)
                    return@withContext bitmap
                }

                if (VERBOSE_LOGGING) Log.d(TAG, "缓存未命中，从网络加载图片: $url")

                // 3. 如果内存和磁盘缓存都没有，则从网络加载
                val bitmap = loadImageFromNetwork(url)

                // 如果成功从网络加载，同时保存到内存和磁盘缓存
                if (bitmap != null) {
                    addBitmapToCache(url, bitmap)
                    saveBitmapToDiskCache(url, bitmap)
                }

                return@withContext bitmap
            } catch (e: Exception) {
                Log.e(TAG, "加载图片失败: ${e.message}")
                null
            }
        }
    }
    
    /** 带超时的图片加载，如果超过指定时间会立即返回null */
    private suspend fun loadImageWithTimeout(url: String, timeoutMs: Long): Bitmap? {
        return try {
            withTimeout(timeoutMs) {
                loadImageFromNetwork(url)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "加载图片超时: $url (${timeoutMs}ms)")
            null
        } catch (e: Exception) {
            Log.e(TAG, "加载图片异常: ${e.message}")
            null
        }
    }

    /** 从网络加载图片 */
    private suspend fun loadImageFromNetwork(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = true
            // 设置User-Agent避免某些服务器阻止请求
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            try {
                // 连接并获取响应码
                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 尝试直接读取
                    val input: InputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(input)
                    input.close()

                    if (bitmap != null) {
                        return bitmap
                    }
                } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                            responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                            responseCode == HttpURLConnection.HTTP_SEE_OTHER
                ) {
                    val newUrl = connection.getHeaderField("Location")

                    // 关闭原连接，使用新URL
                    connection.disconnect()

                    // 递归调用以处理重定向
                    if (!newUrl.isNullOrBlank()) {
                        return loadImageFromNetwork(newUrl)
                    }
                }

                null
            } catch (e: Exception) {
                Log.e(TAG, "网络加载图片失败: ${e.message}")
                null
            } finally {
                connection.disconnect()
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "网络加载图片失败: Read timed out")
            null
        } catch (e: Exception) {
            Log.e(TAG, "加载图片异常: ${e.message}")
            null
        }
    }

    /** 清除所有缓存 */
    fun clearCache() {
        // 清除内存缓存
        memoryCache.evictAll()

        // 清除磁盘缓存
        clearDiskCache()
    }

    /** 清除磁盘缓存 */
    fun clearDiskCache() {
        diskCacheDir?.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "已清除磁盘缓存")
    }

    /** 获取缓存大小统计信息 */
    fun getCacheStats(): CacheStats {
        val memorySize = memoryCache.size()

        var diskCacheSize = 0L
        var diskCacheCount = 0

        diskCacheDir?.listFiles()?.let { files ->
            diskCacheCount = files.size
            diskCacheSize = files.sumOf { it.length() }
        }

        return CacheStats(
                memoryCount = memorySize,
                diskCount = diskCacheCount,
                diskSizeBytes = diskCacheSize
        )
    }

    /** 缓存统计信息 */
    data class CacheStats(val memoryCount: Int, val diskCount: Int, val diskSizeBytes: Long) {
        val diskSizeKB: Long = diskSizeBytes / 1024
        val diskSizeMB: Float = diskSizeKB / 1024f
    }
}

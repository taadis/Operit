package com.ai.assistance.operit.core.application

import android.app.Application
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.mcp.MCPImageCache
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.util.TextSegmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Application class for Operit */
class OperitApplication : Application() {

    companion object {
        /** Global JSON instance with custom serializers */
        lateinit var json: Json
            private set

        // 全局应用实例
        lateinit var instance: OperitApplication
            private set

        // 全局ImageLoader实例，用于高效缓存图片
        lateinit var globalImageLoader: ImageLoader
            private set
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize the JSON serializer with our custom module
        json = Json {
            serializersModule = SerializationSetup.module
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }

        // 初始化用户偏好管理器
        initUserPreferencesManager(applicationContext)

        // 初始化图片缓存
        MCPImageCache.initialize(applicationContext)

        // 初始化TextSegmenter
        applicationScope.launch { TextSegmenter.initialize(applicationContext) }

        // 预加载数据库
        applicationScope.launch {
            // 简单访问数据库以触发初始化
            database.problemDao().getProblemCount()
        }

        // 初始化全局图片加载器，设置强大的缓存策略
        globalImageLoader =
                ImageLoader.Builder(this)
                        .crossfade(true)
                        .respectCacheHeaders(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCache {
                            DiskCache.Builder()
                                    .directory(filesDir.resolve("image_cache"))
                                    .maxSizeBytes(50 * 1024 * 1024) // 50MB磁盘缓存上限，比百分比更精确
                                    .build()
                        }
                        .memoryCache {
                            // 设置内存缓存最大大小为应用可用内存的15%
                            coil.memory.MemoryCache.Builder(this)
                                    .maxSizePercent(0.15)
                                    .build()
                        }
                        .build()

        // MCP插件启动逻辑已移至MainActivity中处理
    }
}

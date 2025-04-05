package com.ai.assistance.operit.core.application

import android.app.Application
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.util.TextSegmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager

/**
 * Application class for Operit
 */
class OperitApplication : Application() {
    
    companion object {
        /**
         * Global JSON instance with custom serializers
         */
        lateinit var json: Json
            private set
    }
    
    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
        
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
        
        // 初始化TextSegmenter
        applicationScope.launch {
            TextSegmenter.initialize(applicationContext)
        }
        
        // 预加载数据库
        applicationScope.launch {
            // 简单访问数据库以触发初始化
            database.problemDao().getProblemCount()
        }
    }
} 
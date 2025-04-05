package com.ai.assistance.operit.core

import android.app.Application
import com.ai.assistance.operit.util.SerializationSetup
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.core.data.initUserPreferencesManager

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
    }
} 
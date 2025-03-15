package com.ai.assistance.operit.util

import kotlinx.serialization.json.Json

/**
 * Utility class for JSON operations with custom serialization support
 */
object JsonUtil {
    /**
     * Configured JSON instance with our custom serializers module
     */
    val json = Json {
        // Use our custom serializers module
        serializersModule = SerializationSetup.module
        
        // Additional configuration options
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
    }
} 
package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.FunctionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 为功能配置创建专用的DataStore
private val Context.functionalConfigDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "functional_configs")

/** 管理不同功能使用的模型配置 这个类用于将FunctionType映射到对应的ModelConfigID */
class FunctionalConfigManager(private val context: Context) {

    // 定义key
    companion object {
        // 功能配置映射key
        val FUNCTION_CONFIG_MAPPING = stringPreferencesKey("function_config_mapping")

        // 默认映射值
        const val DEFAULT_CONFIG_ID = "default"
    }

    // Json解析器
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 获取ModelConfigManager实例用于配置查询
    private val modelConfigManager = ModelConfigManager(context)

    // 获取功能配置映射
    val functionConfigMappingFlow: Flow<Map<FunctionType, String>> =
            context.functionalConfigDataStore.data.map { preferences ->
                val mappingJson = preferences[FUNCTION_CONFIG_MAPPING] ?: "{}"
                if (mappingJson == "{}") {
                    // 返回默认映射
                    FunctionType.values().associateWith { DEFAULT_CONFIG_ID }
                } else {
                    try {
                        val rawMap = json.decodeFromString<Map<String, String>>(mappingJson)
                        // 将字符串键转换为FunctionType枚举
                        rawMap.entries.associate { FunctionType.valueOf(it.key) to it.value }
                    } catch (e: Exception) {
                        // 如果解析失败，返回默认映射
                        FunctionType.values().associateWith { DEFAULT_CONFIG_ID }
                    }
                }
            }

    // 初始化，确保有默认映射
    suspend fun initializeIfNeeded() {
        val mapping = functionConfigMappingFlow.first()

        // 如果映射为空，创建默认映射
        if (mapping.isEmpty() || mapping.values.all { it == DEFAULT_CONFIG_ID }) {
            val defaultMapping = FunctionType.values().associateWith { DEFAULT_CONFIG_ID }
            saveFunctionConfigMapping(defaultMapping)
        }

        // 确保ModelConfigManager也已初始化
        modelConfigManager.initializeIfNeeded()
    }

    // 保存功能配置映射
    suspend fun saveFunctionConfigMapping(mapping: Map<FunctionType, String>) {
        // 将FunctionType枚举转换为字符串键
        val stringMapping = mapping.entries.associate { it.key.name to it.value }

        context.functionalConfigDataStore.edit { preferences ->
            preferences[FUNCTION_CONFIG_MAPPING] = json.encodeToString(stringMapping)
        }
    }

    // 获取指定功能的配置ID
    suspend fun getConfigIdForFunction(functionType: FunctionType): String {
        val mapping = functionConfigMappingFlow.first()
        return mapping[functionType] ?: DEFAULT_CONFIG_ID
    }

    // 设置指定功能的配置ID
    suspend fun setConfigForFunction(functionType: FunctionType, configId: String) {
        val mapping = functionConfigMappingFlow.first().toMutableMap()
        mapping[functionType] = configId
        saveFunctionConfigMapping(mapping)
    }

    // 重置指定功能的配置为默认
    suspend fun resetFunctionConfig(functionType: FunctionType) {
        setConfigForFunction(functionType, DEFAULT_CONFIG_ID)
    }

    // 重置所有功能配置为默认
    suspend fun resetAllFunctionConfigs() {
        val defaultMapping = FunctionType.values().associateWith { DEFAULT_CONFIG_ID }
        saveFunctionConfigMapping(defaultMapping)
    }
}

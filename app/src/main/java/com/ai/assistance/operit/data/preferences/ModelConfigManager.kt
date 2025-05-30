package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.CustomParameterData
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 为ModelConfig创建专用的DataStore
private val Context.modelConfigDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "model_configs")

// 获取ApiPreferences的DataStore
private val Context.apiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "api_settings")

class ModelConfigManager(private val context: Context) {

    // 定义key
    companion object {
        // 配置相关key
        val CONFIG_LIST = stringPreferencesKey("config_list")
        val ACTIVE_CONFIG_ID = stringPreferencesKey("active_config_id")

        // 默认值
        const val DEFAULT_CONFIG_ID = "default"
        const val DEFAULT_CONFIG_NAME = "默认配置"
    }

    // Json解析器，支持宽松模式
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 获取ApiPreferences实例用于默认配置
    private val apiPreferences = ApiPreferences(context)

    // 获取所有配置ID列表
    val configListFlow: Flow<List<String>> =
            context.modelConfigDataStore.data.map { preferences ->
                val configList = preferences[CONFIG_LIST] ?: ""
                if (configList.isEmpty()) listOf(DEFAULT_CONFIG_ID)
                else json.decodeFromString<List<String>>(configList)
            }

    // 获取当前活跃配置ID
    val activeConfigIdFlow: Flow<String> =
            context.modelConfigDataStore.data.map { preferences ->
                preferences[ACTIVE_CONFIG_ID] ?: DEFAULT_CONFIG_ID
            }

    // 初始化，确保至少有一个默认配置
    suspend fun initializeIfNeeded() {
        val configList = configListFlow.first()

        if (configList.isEmpty() || configList == listOf(DEFAULT_CONFIG_ID)) {
            // 创建默认配置（从现有ApiPreferences导入）
            val defaultConfig = createDefaultConfigFromApiPreferences()
            saveModelConfig(defaultConfig)

            // 保存配置列表和活跃ID
            context.modelConfigDataStore.edit { preferences ->
                preferences[CONFIG_LIST] = json.encodeToString(listOf(DEFAULT_CONFIG_ID))
                preferences[ACTIVE_CONFIG_ID] = DEFAULT_CONFIG_ID
            }
        }
    }

    // 从原有ApiPreferences创建默认配置
    private suspend fun createDefaultConfigFromApiPreferences(): ModelConfigData {
        // 获取API设置
        val apiKey = apiPreferences.apiKeyFlow.first()
        val apiEndpoint = apiPreferences.apiEndpointFlow.first()
        val modelName = apiPreferences.modelNameFlow.first()

        // 获取参数启用状态
        val maxTokensEnabled = apiPreferences.maxTokensEnabledFlow.first()
        val temperatureEnabled = apiPreferences.temperatureEnabledFlow.first()
        val topPEnabled = apiPreferences.topPEnabledFlow.first()
        val topKEnabled = apiPreferences.topKEnabledFlow.first()
        val presencePenaltyEnabled = apiPreferences.presencePenaltyEnabledFlow.first()
        val frequencyPenaltyEnabled = apiPreferences.frequencyPenaltyEnabledFlow.first()
        val repetitionPenaltyEnabled = apiPreferences.repetitionPenaltyEnabledFlow.first()

        // 获取参数值
        val maxTokens = apiPreferences.maxTokensFlow.first()
        val temperature = apiPreferences.temperatureFlow.first()
        val topP = apiPreferences.topPFlow.first()
        val topK = apiPreferences.topKFlow.first()
        val presencePenalty = apiPreferences.presencePenaltyFlow.first()
        val frequencyPenalty = apiPreferences.frequencyPenaltyFlow.first()
        val repetitionPenalty = apiPreferences.repetitionPenaltyFlow.first()

        // 获取自定义参数
        val customParamsJson =
                try {
                    context.apiDataStore.data.first()[ApiPreferences.CUSTOM_PARAMETERS] ?: "[]"
                } catch (e: Exception) {
                    "[]"
                }

        return ModelConfigData(
                id = DEFAULT_CONFIG_ID,
                name = DEFAULT_CONFIG_NAME,
                isActive = true,
                apiKey = apiKey,
                apiEndpoint = apiEndpoint,
                modelName = modelName,
                hasCustomParameters = customParamsJson != "[]",
                maxTokensEnabled = maxTokensEnabled,
                temperatureEnabled = temperatureEnabled,
                topPEnabled = topPEnabled,
                topKEnabled = topKEnabled,
                presencePenaltyEnabled = presencePenaltyEnabled,
                frequencyPenaltyEnabled = frequencyPenaltyEnabled,
                repetitionPenaltyEnabled = repetitionPenaltyEnabled,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP,
                topK = topK,
                presencePenalty = presencePenalty,
                frequencyPenalty = frequencyPenalty,
                repetitionPenalty = repetitionPenalty,
                customParameters = customParamsJson
        )
    }

    // 保存配置
    suspend fun saveModelConfig(config: ModelConfigData) {
        val configKey = stringPreferencesKey("config_${config.id}")
        context.modelConfigDataStore.edit { preferences ->
            preferences[configKey] = json.encodeToString(config)
        }
    }

    // 获取指定ID的配置
    fun getModelConfigFlow(configId: String): Flow<ModelConfigData> {
        val configKey = stringPreferencesKey("config_${configId}")
        return context.modelConfigDataStore.data.map { preferences ->
            val configJson = preferences[configKey]
            if (configJson != null) {
                try {
                    json.decodeFromString<ModelConfigData>(configJson)
                } catch (e: Exception) {
                    // 如果解析失败，回退到创建一个新配置
                    if (configId == DEFAULT_CONFIG_ID) {
                        createDefaultConfigFromApiPreferences()
                    } else {
                        ModelConfigData(id = configId, name = "配置 $configId", isActive = false)
                    }
                }
            } else {
                if (configId == DEFAULT_CONFIG_ID) {
                    createDefaultConfigFromApiPreferences()
                } else {
                    ModelConfigData(id = configId, name = "配置 $configId", isActive = false)
                }
            }
        }
    }

    // 获取所有配置的摘要信息
    suspend fun getAllConfigSummaries(): List<ModelConfigSummary> {
        val configIds = configListFlow.first()
        val activeId = activeConfigIdFlow.first()
        val summaries = mutableListOf<ModelConfigSummary>()

        for (id in configIds) {
            val config = getModelConfigFlow(id).first()
            summaries.add(
                    ModelConfigSummary(
                            id = config.id,
                            name = config.name,
                            isActive = id == activeId,
                            modelName = config.modelName,
                            apiEndpoint = config.apiEndpoint
                    )
            )
        }

        return summaries
    }

    // 创建新配置
    suspend fun createConfig(name: String): String {
        val configId = UUID.randomUUID().toString()
        val configList = configListFlow.first().toMutableList()

        // 复制当前活跃配置作为新配置的基础
        val activeId = activeConfigIdFlow.first()
        val baseConfig = getModelConfigFlow(activeId).first()

        val newConfig = baseConfig.copy(id = configId, name = name, isActive = false)

        // 保存新配置
        saveModelConfig(newConfig)

        // 更新配置列表
        configList.add(configId)
        context.modelConfigDataStore.edit { preferences ->
            preferences[CONFIG_LIST] = json.encodeToString(configList)
        }

        return configId
    }

    // 删除配置
    suspend fun deleteConfig(configId: String) {
        // 不能删除默认配置
        if (configId == DEFAULT_CONFIG_ID) return

        val configList = configListFlow.first().toMutableList()
        val activeId = activeConfigIdFlow.first()

        // 如果删除的是当前活跃配置，切换到默认配置
        if (configId == activeId) {
            setActiveConfig(DEFAULT_CONFIG_ID)
        }

        // 从列表中移除
        configList.remove(configId)
        context.modelConfigDataStore.edit { preferences ->
            preferences[CONFIG_LIST] = json.encodeToString(configList)
            // 删除配置数据
            preferences.remove(stringPreferencesKey("config_${configId}"))
        }
    }

    // 设置活跃配置
    suspend fun setActiveConfig(configId: String) {
        val configList = configListFlow.first()
        if (configId !in configList) return

        // 获取所选配置
        val selectedConfig = getModelConfigFlow(configId).first()

        // 更新所有配置的活跃状态
        for (id in configList) {
            if (id == configId) {
                val config = getModelConfigFlow(id).first()
                saveModelConfig(config.copy(isActive = true))
            } else {
                val config = getModelConfigFlow(id).first()
                if (config.isActive) {
                    saveModelConfig(config.copy(isActive = false))
                }
            }
        }

        // 更新活跃配置ID
        context.modelConfigDataStore.edit { preferences ->
            preferences[ACTIVE_CONFIG_ID] = configId
        }

        // 将活跃配置同步到ApiPreferences (这样现有代码不需要修改)
        applyConfigToApiPreferences(selectedConfig)
    }

    // 将配置应用到ApiPreferences
    private suspend fun applyConfigToApiPreferences(config: ModelConfigData) {
        // 保存API设置
        apiPreferences.saveApiSettings(
                apiKey = config.apiKey,
                endpoint = config.apiEndpoint,
                modelName = config.modelName
        )

        // 保存模型参数
        val parameters = mutableListOf<ModelParameter<*>>()

        // 添加标准参数
        parameters.add(
                ModelParameter(
                        id = "max_tokens",
                        name = "最大生成Token数",
                        apiName = "max_tokens",
                        description = "控制AI每次最多生成的Token数量",
                        defaultValue = ApiPreferences.DEFAULT_MAX_TOKENS,
                        currentValue = config.maxTokens,
                        isEnabled = config.maxTokensEnabled,
                        valueType = ParameterValueType.INT,
                        minValue = 1,
                        maxValue = 16000,
                        category = ParameterCategory.GENERATION
                )
        )

        parameters.add(
                ModelParameter(
                        id = "temperature",
                        name = "温度",
                        apiName = "temperature",
                        description = "控制输出的随机性。较低的值更确定性，较高的值更随机",
                        defaultValue = ApiPreferences.DEFAULT_TEMPERATURE,
                        currentValue = config.temperature,
                        isEnabled = config.temperatureEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.CREATIVITY
                )
        )

        parameters.add(
                ModelParameter(
                        id = "top_p",
                        name = "Top-P 采样",
                        apiName = "top_p",
                        description = "作为温度的替代方案，模型仅考虑概率最高的Top-P比例的token",
                        defaultValue = ApiPreferences.DEFAULT_TOP_P,
                        currentValue = config.topP,
                        isEnabled = config.topPEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        category = ParameterCategory.CREATIVITY
                )
        )

        parameters.add(
                ModelParameter(
                        id = "top_k",
                        name = "Top-K 采样",
                        apiName = "top_k",
                        description = "模型仅考虑概率最高的K个token。0表示禁用",
                        defaultValue = ApiPreferences.DEFAULT_TOP_K,
                        currentValue = config.topK,
                        isEnabled = config.topKEnabled,
                        valueType = ParameterValueType.INT,
                        minValue = 0,
                        maxValue = 100,
                        category = ParameterCategory.CREATIVITY
                )
        )

        parameters.add(
                ModelParameter(
                        id = "presence_penalty",
                        name = "存在惩罚",
                        apiName = "presence_penalty",
                        description = "增强模型谈论新主题的倾向。值越高，惩罚越大",
                        defaultValue = ApiPreferences.DEFAULT_PRESENCE_PENALTY,
                        currentValue = config.presencePenalty,
                        isEnabled = config.presencePenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = -2.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        parameters.add(
                ModelParameter(
                        id = "frequency_penalty",
                        name = "频率惩罚",
                        apiName = "frequency_penalty",
                        description = "减少模型重复同一词语的可能性。值越高，惩罚越大",
                        defaultValue = ApiPreferences.DEFAULT_FREQUENCY_PENALTY,
                        currentValue = config.frequencyPenalty,
                        isEnabled = config.frequencyPenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = -2.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        parameters.add(
                ModelParameter(
                        id = "repetition_penalty",
                        name = "重复惩罚",
                        apiName = "repetition_penalty",
                        description = "进一步减少重复。1.0表示不惩罚，大于1.0会降低重复可能性",
                        defaultValue = ApiPreferences.DEFAULT_REPETITION_PENALTY,
                        currentValue = config.repetitionPenalty,
                        isEnabled = config.repetitionPenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        // 如果有自定义参数，添加到列表中
        if (config.hasCustomParameters && config.customParameters != "[]") {
            try {
                val customParamsList =
                        json.decodeFromString<List<CustomParameterData>>(config.customParameters)
                for (customParam in customParamsList) {
                    val param =
                            when (ParameterValueType.valueOf(customParam.valueType)) {
                                ParameterValueType.INT -> {
                                    ModelParameter(
                                            id = customParam.id,
                                            name = customParam.name,
                                            apiName = customParam.apiName,
                                            description = customParam.description,
                                            defaultValue = customParam.defaultValue.toInt(),
                                            currentValue = customParam.currentValue.toInt(),
                                            isEnabled = customParam.isEnabled,
                                            valueType = ParameterValueType.INT,
                                            minValue = customParam.minValue?.toInt(),
                                            maxValue = customParam.maxValue?.toInt(),
                                            category =
                                                    ParameterCategory.valueOf(customParam.category),
                                            isCustom = true
                                    )
                                }
                                ParameterValueType.FLOAT -> {
                                    ModelParameter(
                                            id = customParam.id,
                                            name = customParam.name,
                                            apiName = customParam.apiName,
                                            description = customParam.description,
                                            defaultValue = customParam.defaultValue.toFloat(),
                                            currentValue = customParam.currentValue.toFloat(),
                                            isEnabled = customParam.isEnabled,
                                            valueType = ParameterValueType.FLOAT,
                                            minValue = customParam.minValue?.toFloat(),
                                            maxValue = customParam.maxValue?.toFloat(),
                                            category =
                                                    ParameterCategory.valueOf(customParam.category),
                                            isCustom = true
                                    )
                                }
                                ParameterValueType.STRING -> {
                                    ModelParameter(
                                            id = customParam.id,
                                            name = customParam.name,
                                            apiName = customParam.apiName,
                                            description = customParam.description,
                                            defaultValue = customParam.defaultValue,
                                            currentValue = customParam.currentValue,
                                            isEnabled = customParam.isEnabled,
                                            valueType = ParameterValueType.STRING,
                                            category =
                                                    ParameterCategory.valueOf(customParam.category),
                                            isCustom = true
                                    )
                                }
                                ParameterValueType.BOOLEAN -> {
                                    ModelParameter(
                                            id = customParam.id,
                                            name = customParam.name,
                                            apiName = customParam.apiName,
                                            description = customParam.description,
                                            defaultValue = customParam.defaultValue.toBoolean(),
                                            currentValue = customParam.currentValue.toBoolean(),
                                            isEnabled = customParam.isEnabled,
                                            valueType = ParameterValueType.BOOLEAN,
                                            category =
                                                    ParameterCategory.valueOf(customParam.category),
                                            isCustom = true
                                    )
                                }
                            }
                    parameters.add(param)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 保存所有参数
        try {
            apiPreferences.saveModelParameters(parameters)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 更新配置
    suspend fun updateModelConfig(
            configId: String,
            name: String? = null,
            apiKey: String? = null,
            apiEndpoint: String? = null,
            modelName: String? = null,
            parameters: List<ModelParameter<*>>? = null
    ) {
        val config = getModelConfigFlow(configId).first()

        // 准备更新的配置
        var updatedConfig = config.copy()

        // 更新名称
        if (name != null) {
            updatedConfig = updatedConfig.copy(name = name)
        }

        // 更新API设置
        if (apiKey != null) {
            updatedConfig = updatedConfig.copy(apiKey = apiKey)
        }

        if (apiEndpoint != null) {
            updatedConfig = updatedConfig.copy(apiEndpoint = apiEndpoint)
        }

        if (modelName != null) {
            updatedConfig = updatedConfig.copy(modelName = modelName)
        }

        // 更新参数
        if (parameters != null) {
            // 提取标准参数设置和自定义参数
            val standardParams = parameters.filterNot { it.isCustom }
            val customParams = parameters.filter { it.isCustom }

            for (param in standardParams) {
                when (param.id) {
                    "max_tokens" -> {
                        updatedConfig =
                                updatedConfig.copy(
                                        maxTokens = param.currentValue as Int,
                                        maxTokensEnabled = param.isEnabled
                                )
                    }
                    "temperature" -> {
                        updatedConfig =
                                updatedConfig.copy(
                                        temperature = param.currentValue as Float,
                                        temperatureEnabled = param.isEnabled
                                )
                    }
                    "top_p" -> {
                        updatedConfig =
                                updatedConfig.copy(
                                        topP = param.currentValue as Float,
                                        topPEnabled = param.isEnabled
                                )
                    }
                    "top_k" -> {
                        updatedConfig =
                                updatedConfig.copy(
                                        topK = param.currentValue as Int,
                                        topKEnabled = param.isEnabled
                                )
                    }
                    "presence_penalty" -> {
                        updatedConfig =
                                updatedConfig.copy(
                                        presencePenalty = param.currentValue as Float,
                                        presencePenaltyEnabled = param.isEnabled
                                )
                    }
                    "frequency_penalty" -> {
                        updatedConfig =
                                updatedConfig.copy(
                                        frequencyPenalty = param.currentValue as Float,
                                        frequencyPenaltyEnabled = param.isEnabled
                                )
                    }
                    "repetition_penalty" -> {
                        updatedConfig =
                                updatedConfig.copy(
                                        repetitionPenalty = param.currentValue as Float,
                                        repetitionPenaltyEnabled = param.isEnabled
                                )
                    }
                }
            }

            // 更新自定义参数
            if (customParams.isNotEmpty()) {
                val customParamsList =
                        customParams.map { param ->
                            when (param.valueType) {
                                ParameterValueType.INT -> {
                                    CustomParameterData(
                                            id = param.id,
                                            name = param.name,
                                            apiName = param.apiName,
                                            description = param.description,
                                            defaultValue = (param.defaultValue as Int).toString(),
                                            currentValue = (param.currentValue as Int).toString(),
                                            isEnabled = param.isEnabled,
                                            valueType = param.valueType.name,
                                            minValue = (param.minValue as? Int)?.toString(),
                                            maxValue = (param.maxValue as? Int)?.toString(),
                                            category = param.category.name
                                    )
                                }
                                ParameterValueType.FLOAT -> {
                                    CustomParameterData(
                                            id = param.id,
                                            name = param.name,
                                            apiName = param.apiName,
                                            description = param.description,
                                            defaultValue = (param.defaultValue as Float).toString(),
                                            currentValue = (param.currentValue as Float).toString(),
                                            isEnabled = param.isEnabled,
                                            valueType = param.valueType.name,
                                            minValue = (param.minValue as? Float)?.toString(),
                                            maxValue = (param.maxValue as? Float)?.toString(),
                                            category = param.category.name
                                    )
                                }
                                ParameterValueType.STRING -> {
                                    CustomParameterData(
                                            id = param.id,
                                            name = param.name,
                                            apiName = param.apiName,
                                            description = param.description,
                                            defaultValue = param.defaultValue as String,
                                            currentValue = param.currentValue as String,
                                            isEnabled = param.isEnabled,
                                            valueType = param.valueType.name,
                                            category = param.category.name
                                    )
                                }
                                ParameterValueType.BOOLEAN -> {
                                    CustomParameterData(
                                            id = param.id,
                                            name = param.name,
                                            apiName = param.apiName,
                                            description = param.description,
                                            defaultValue =
                                                    (param.defaultValue as Boolean).toString(),
                                            currentValue =
                                                    (param.currentValue as Boolean).toString(),
                                            isEnabled = param.isEnabled,
                                            valueType = param.valueType.name,
                                            category = param.category.name
                                    )
                                }
                            }
                        }

                updatedConfig =
                        updatedConfig.copy(
                                hasCustomParameters = true,
                                customParameters = json.encodeToString(customParamsList)
                        )
            } else if (customParams.isEmpty() && parameters.isNotEmpty()) {
                // 如果参数列表中没有自定义参数但有其他参数，则清空自定义参数
                updatedConfig =
                        updatedConfig.copy(hasCustomParameters = false, customParameters = "[]")
            }
        }

        // 保存更新后的配置
        saveModelConfig(updatedConfig)

        // 如果这是活跃配置，同步到ApiPreferences
        if (updatedConfig.isActive) {
            applyConfigToApiPreferences(updatedConfig)
        }
    }
}

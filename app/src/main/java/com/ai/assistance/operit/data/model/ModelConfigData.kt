package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/** 表示完整的模型配置，包括API设置和模型参数 */
@Serializable
data class ModelConfigData(
        val id: String,
        val name: String,
        val isActive: Boolean = false,

        // API设置
        val apiKey: String = "",
        val apiEndpoint: String = "",
        val modelName: String = "",

        // 是否包含自定义参数
        val hasCustomParameters: Boolean = false,

        // 模型参数的enabled状态
        val maxTokensEnabled: Boolean = false,
        val temperatureEnabled: Boolean = false,
        val topPEnabled: Boolean = false,
        val topKEnabled: Boolean = false,
        val presencePenaltyEnabled: Boolean = false,
        val frequencyPenaltyEnabled: Boolean = false,
        val repetitionPenaltyEnabled: Boolean = false,

        // 模型参数值
        val maxTokens: Int = 4096,
        val temperature: Float = 1.0f,
        val topP: Float = 1.0f,
        val topK: Int = 0,
        val presencePenalty: Float = 0.0f,
        val frequencyPenalty: Float = 0.0f,
        val repetitionPenalty: Float = 1.0f,

        // 自定义参数JSON字符串
        val customParameters: String = "[]"
)

/** 简化版的模型配置数据，用于列表显示 */
@Serializable
data class ModelConfigSummary(
        val id: String,
        val name: String,
        val isActive: Boolean = false,
        val modelName: String = "",
        val apiEndpoint: String = ""
)

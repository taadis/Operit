package com.ai.assistance.operit.ui.features.token.models

/** 表示Deepseek API密钥的数据模型 */
data class ApiKey(
        val name: String,
        val sensitiveId: String,
        val createdAt: Long,
        val lastUse: Long?,
        val trackingId: String
)

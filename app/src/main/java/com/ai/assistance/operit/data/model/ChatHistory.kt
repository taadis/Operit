package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.util.LocalDateTimeSerializer
import java.util.UUID
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ChatHistory(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
) 
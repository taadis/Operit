package com.ai.assistance.operit.model

import java.util.UUID
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

@Serializable
data class ChatHistory(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) 
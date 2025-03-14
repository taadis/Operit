package com.ai.assistance.operit.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val sender: String, // "user" or "ai"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) 
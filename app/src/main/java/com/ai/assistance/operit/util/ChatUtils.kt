package com.ai.assistance.operit.util

import android.util.Log
import com.ai.assistance.operit.model.ChatMessage

/**
 * Utility functions for chat message handling
 */
object ChatUtils {
    fun mapToStandardRole(role: String): String {
        return when (role) {
            "ai" -> "assistant"
            "tool" -> "user" // AI, assistant and tool messages map to assistant
            "user" -> "user"  // User messages remain as user
            "system" -> "system" // System messages remain as system
            else -> role // Default to user for any other role
        }
    }
    
    fun mapToStandardRoleForToolServer(role: String): String {
        return when (role) {
            "ai" -> "assistant"
            "tool" -> "tool" // AI, assistant and tool messages map to assistant
            "user" -> "user"  // User messages remain as user
            "system" -> "system" // System messages remain as system
            else -> "user" // Default to user for any other role
        }
    }
    
    fun mapChatHistoryToStandardRoles(chatHistory: List<Pair<String, String>>): List<Pair<String, String>> {
        return chatHistory.map { (role, content) -> 
            Pair(mapToStandardRole(role), content)
        }
    }
    fun mapChatHistoryToStandardRolesForToolServer(chatHistory: List<Pair<String, String>>): List<Pair<String, String>> {
        return chatHistory.map { (role, content) -> 
            Pair(mapToStandardRoleForToolServer(role), content)
        }
    }
    
    fun prepareMessagesForApi(
        messages: List<ChatMessage>,
        includeRoles: Set<String> = setOf("user", "ai", "system")
    ): List<Pair<String, String>> {
        return messages
            .filter { it.sender in includeRoles }
            .map { Pair(mapToStandardRole(it.sender), it.content) }
    }

    fun prepareMessagesForToolServer(
        messages: List<ChatMessage>,
        includeRoles: Set<String> = setOf("user", "ai", "system", "tool")
    ): List<Pair<String, String>> {
        return messages
            .filter { it.sender in includeRoles }
            .map { Pair(mapToStandardRoleForToolServer(it.sender), it.content) }
    }
} 
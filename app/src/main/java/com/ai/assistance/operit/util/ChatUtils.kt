package com.ai.assistance.operit.util

import com.ai.assistance.operit.model.ChatMessage

/**
 * Utility functions for chat message handling
 */
object ChatUtils {
    /**
     * Maps various sender/role types to the standard roles expected by AI APIs
     * @param role The original role/sender string
     * @return The mapped standard role (assistant, user, or system)
     */
    fun mapToStandardRole(role: String): String {
        return when (role) {
            "ai", "assistant", "tool" -> "assistant" // AI, assistant and tool messages map to assistant
            "user" -> "user"  // User messages remain as user
            "system" -> "system" // System messages remain as system
            else -> "user" // Default to user for any other role
        }
    }
    
    /**
     * Maps a list of chat message pairs to use standard roles.
     * This function is used to prepare chat history for submission to AI APIs.
     * 
     * @param chatHistory List of role-content pairs to be mapped
     * @return A new list with standardized roles
     */
    fun mapChatHistoryToStandardRoles(chatHistory: List<Pair<String, String>>): List<Pair<String, String>> {
        return chatHistory.map { (role, content) -> 
            Pair(mapToStandardRole(role), content)
        }
    }
    
    /**
     * Filters and converts ChatMessage objects to API-ready format
     * 
     * @param messages List of ChatMessage objects
     * @param includeRoles Set of role types to include in the result
     * @return List of standardized role-content pairs ready for API submission
     */
    fun prepareMessagesForApi(
        messages: List<ChatMessage>,
        includeRoles: Set<String> = setOf("user", "ai", "system")
    ): List<Pair<String, String>> {
        return messages
            .filter { it.sender in includeRoles }
            .map { Pair(mapToStandardRole(it.sender), it.content) }
    }
} 
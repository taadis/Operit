package com.ai.assistance.operit.util

/** Utility functions for chat message handling */
object ChatUtils {
    fun mapToStandardRole(role: String): String {
        return when (role) {
            "ai" -> "assistant"
            "tool" -> "user" // AI, assistant and tool messages map to assistant
            "user" -> "user" // User messages remain as user
            "system" -> "system" // System messages remain as system
            "summary" -> "user" // Summary messages are treated as user messages
            else -> role // Default to user for any other role
        }
    }

    /** 过滤掉内容中的思考部分 移除<think></think>标签及其中的内容，并处理未闭合的情况 */
    fun removeThinkingContent(content: String): String {
        // 使用正则表达式匹配<think>标签及其内容
        // 这个正则表达式会匹配两种情况：
        // 1. <think>...</think> (正常闭合的标签)
        // 2. <think>... (未闭合，直到字符串末尾)
        // \\z 匹配字符串的绝对末尾
        val thinkPattern = "<think>.*?(</think>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        return content.replace(thinkPattern, "").trim()
    }

    fun mapChatHistoryToStandardRoles(
            chatHistory: List<Pair<String, String>>
    ): List<Pair<String, String>> {
        return chatHistory.map { (role, content) ->
            val standardRole = mapToStandardRole(role)
            // 对于assistant角色的消息，移除思考内容
            val processedContent =
                    if (standardRole == "assistant" || role == "ai") {
                        removeThinkingContent(content)
                    } else {
                        content
                    }
            Pair(standardRole, processedContent)
        }
    }
}

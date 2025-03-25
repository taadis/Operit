package com.ai.assistance.operit.api.enhanced.models

import com.ai.assistance.operit.model.ToolResult

/**
 * Manages the markup elements used in conversations with the AI assistant.
 * 
 * This class handles the generation of standardized XML-formatted status messages,
 * tool invocation formats, and tool results to be displayed in the conversation.
 */
class ConversationMarkupManager {

    companion object {
        private const val TAG = "ConversationMarkupManager"
        
        /**
         * Creates a 'thinking' status markup element.
         * 
         * @return The formatted status element
         */
        fun createThinkingStatus(): String {
            return "<status type=\"thinking\"></status>"
        }
        
        /**
         * Creates a 'complete' status markup element.
         * 
         * @return The formatted status element
         */
        fun createCompleteStatus(): String {
            return "<status type=\"complete\"></status>"
        }
        
        /**
         * Creates an 'executing' status markup element for a tool.
         * 
         * @param toolName The name of the tool being executed
         * @return The formatted status element
         */
        fun createExecutingToolStatus(toolName: String): String {
            return "<status type=\"executing\" tool=\"$toolName\"></status>"
        }
        
        /**
         * Creates a 'result' status markup element for a tool.
         * 
         * @param toolName The name of the tool that produced the result
         * @param success Whether the tool execution was successful
         * @param resultText The result content
         * @return The formatted status element
         */
        fun createToolResultStatus(toolName: String, success: Boolean, resultText: String): String {
            return "<status type=\"result\" tool=\"$toolName\" success=\"$success\">$resultText</status>"
        }
        
        /**
         * Creates an 'error' status markup element for a tool.
         * 
         * @param toolName The name of the tool that produced the error
         * @param errorMessage The error message
         * @return The formatted status element
         */
        fun createToolErrorStatus(toolName: String, errorMessage: String): String {
            return "<status type=\"error\" tool=\"$toolName\">$errorMessage</status>"
        }
        
        /**
         * Creates a 'warning' status markup element.
         * 
         * @param warningMessage The warning message to display
         * @return The formatted status element
         */
        fun createWarningStatus(warningMessage: String): String {
            return "<status type=\"warning\">$warningMessage</status>"
        }
        
        /**
         * Formats a tool result message for sending to the AI.
         * 
         * @param result The tool execution result
         * @return The formatted tool result message
         */
        fun formatToolResultForMessage(result: ToolResult): String {
            return if (result.success) {
                """
                <tool_result name="${result.toolName}" status="success">
                <content>
                ${result.result}
                </content>
                </tool_result>
                """.trimIndent()
            } else {
                """
                <tool_result name="${result.toolName}" status="error">
                <error>${result.error ?: "Unknown error"}</error>
                </tool_result>
            """.trimIndent()
            }
        }
        
        /**
         * Formats a message indicating multiple tool invocations were found but only one will be processed.
         * 
         * @param toolName The name of the tool that will be processed
         * @return The formatted warning message
         */
        fun createMultipleToolsWarning(toolName: String): String {
            return createWarningStatus("Multiple tool invocations found. Only the tool `$toolName` will be executed.")
        }
        
        /**
         * Creates a message for when a tool is not available.
         * 
         * @param toolName The name of the unavailable tool
         * @return The formatted error message
         */
        fun createToolNotAvailableError(toolName: String): String {
            return createToolErrorStatus(toolName, "The tool `$toolName` is not available.")
        }
        
        /**
         * Cleans task completion content by removing the completion marker
         * and adding a completion status.
         * 
         * @param content The content to clean
         * @return The cleaned content with completion status
         */
        fun createTaskCompletionContent(content: String): String {
            return content
                .replace("[TASK_COMPLETE]", "")
                .trim() + "\n" + createCompleteStatus()
        }
        
        /**
         * Creates a thinking status appended to existing content.
         * 
         * @param currentContent The current conversation content
         * @return The content with thinking status appended
         */
        fun appendThinkingStatus(currentContent: String): String {
            return "$currentContent\n" + createThinkingStatus()
        }
        
        /**
         * Checks if content contains a task completion marker.
         * 
         * @param content The content to check
         * @return True if the content contains a task completion marker
         */
        fun containsTaskCompletion(content: String): Boolean {
            return content.contains("[TASK_COMPLETE]")
        }
        
        /**
         * 创建通用错误状态
         */
        fun createErrorStatus(title: String, message: String): String {
            return "<status type=\"error\"><title>$title</title><message>$message</message></status>"
        }
    }
} 
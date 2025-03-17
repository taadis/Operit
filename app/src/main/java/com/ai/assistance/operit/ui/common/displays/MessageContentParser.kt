package com.ai.assistance.operit.ui.common.displays

/**
 * A utility class to parse message content with XML markup.
 * Extracts tool requests, executions, and results from message content.
 */
class MessageContentParser {
    companion object {
        // XML markup patterns
        private val xmlStatusPattern = Regex("<status\\s+type=\"([^\"]+)\"(?:\\s+tool=\"([^\"]+)\")?(?:\\s+success=\"([^\"]+)\")?>([\\s\\S]*?)</status>")
        private val xmlToolResultPattern = Regex("<tool_result\\s+name=\"([^\"]+)\"\\s+status=\"([^\"]+)\">\\s*<content>([\\s\\S]*?)</content>\\s*</tool_result>")
        private val xmlToolErrorPattern = Regex("<tool_result\\s+name=\"([^\"]+)\"\\s+status=\"error\">\\s*<e>([\\s\\S]*?)</e>\\s*</tool_result>")
        private val xmlToolRequestPattern = Regex("<tool\\s+name=\"([^\"]+)\"(?:\\s+description=\"([^\"]+)\")?>([\\s\\S]*?)</tool>")
        private val paramPattern = Regex("<param\\s+name=\"([^\"]+)\">([\\s\\S]*?)</param>")
        
        // Legacy markup patterns
        private val toolExecutionPattern = Regex("\\*\\*Tool Execution \\[(.*?)\\]\\*\\*\n_Processing\\.\\.\\._")
        private val toolResultPattern = Regex("\\*\\*Tool Result \\[(.*?)\\]\\*\\*\n```\n([\\s\\S]*?)\n```")
        private val toolErrorPattern = Regex("\\*\\*Tool Error \\[(.*?)\\]\\*\\*\n```\n([\\s\\S]*?)\n```")
        
        /**
         * Content segment types for parsed message
         */
        sealed class ContentSegment {
            data class Text(val content: String) : ContentSegment()
            data class ToolRequest(val name: String, val description: String, val params: String) : ContentSegment()
            data class ToolExecution(val name: String) : ContentSegment()
            data class ToolResult(val name: String, val content: String, val isError: Boolean) : ContentSegment()
            data class Status(val type: String, val toolName: String, val content: String, val success: Boolean) : ContentSegment()
        }
        
        /**
         * Parse message content and split into segments
         */
        fun parseContent(content: String, supportToolMarkup: Boolean): List<ContentSegment> {
            if (!supportToolMarkup) {
                return listOf(ContentSegment.Text(content))
            }
            
            val segments = mutableListOf<ContentSegment>()
            var remainingContent = content
            
            // Collect all patterns and their ranges
            val allMatches = mutableListOf<Triple<Int, Int, Regex>>() // start, end, pattern type
            
            // Find all matches for each pattern
            findMatchesAndAdd(xmlStatusPattern, remainingContent, allMatches)
            findMatchesAndAdd(xmlToolResultPattern, remainingContent, allMatches)
            findMatchesAndAdd(xmlToolErrorPattern, remainingContent, allMatches)
            findMatchesAndAdd(xmlToolRequestPattern, remainingContent, allMatches)
            findMatchesAndAdd(toolExecutionPattern, remainingContent, allMatches)
            findMatchesAndAdd(toolResultPattern, remainingContent, allMatches)
            findMatchesAndAdd(toolErrorPattern, remainingContent, allMatches)
            
            // Sort matches by start position
            val sortedMatches = allMatches.sortedBy { it.first }
            
            // Process text between and including matches
            var lastEnd = 0
            for (match in sortedMatches) {
                // Add text before match
                if (match.first > lastEnd) {
                    val textBefore = remainingContent.substring(lastEnd, match.first)
                    if (textBefore.isNotBlank()) {
                        segments.add(ContentSegment.Text(textBefore))
                    }
                }
                
                // Add tool segment based on pattern
                val matchText = remainingContent.substring(match.first, match.second)
                when (match.third) {
                    xmlStatusPattern -> parseStatusMatch(matchText, segments)
                    xmlToolRequestPattern -> parseToolRequestMatch(matchText, segments)
                    xmlToolResultPattern -> parseToolResultMatch(matchText, segments)
                    xmlToolErrorPattern -> parseToolErrorMatch(matchText, segments)
                    toolExecutionPattern -> parseLegacyToolExecution(matchText, segments)
                    toolResultPattern -> parseLegacyToolResult(matchText, segments)
                    toolErrorPattern -> parseLegacyToolError(matchText, segments)
                }
                
                lastEnd = match.second
            }
            
            // Add remaining text
            if (lastEnd < remainingContent.length) {
                val textAfter = remainingContent.substring(lastEnd)
                if (textAfter.isNotBlank()) {
                    segments.add(ContentSegment.Text(textAfter))
                }
            }
            
            return segments
        }
        
        private fun findMatchesAndAdd(pattern: Regex, content: String, matches: MutableList<Triple<Int, Int, Regex>>) {
            pattern.findAll(content).forEach { match ->
                matches.add(Triple(match.range.first, match.range.last + 1, pattern))
            }
        }
        
        private fun parseStatusMatch(matchText: String, segments: MutableList<ContentSegment>) {
            val match = xmlStatusPattern.find(matchText)
            if (match != null) {
                val statusType = match.groupValues[1]
                val toolName = match.groupValues[2]
                val success = match.groupValues[3].toBoolean()
                val content = match.groupValues[4]
                segments.add(ContentSegment.Status(statusType, toolName, content, success))
            }
        }
        
        private fun parseToolRequestMatch(matchText: String, segments: MutableList<ContentSegment>) {
            val match = xmlToolRequestPattern.find(matchText)
            if (match != null) {
                val toolName = match.groupValues[1]
                val description = match.groupValues[2]
                val params = match.groupValues[3]
                segments.add(ContentSegment.ToolRequest(toolName, description, params))
            }
        }
        
        private fun parseToolResultMatch(matchText: String, segments: MutableList<ContentSegment>) {
            val match = xmlToolResultPattern.find(matchText)
            if (match != null) {
                val toolName = match.groupValues[1]
                val status = match.groupValues[2]
                val content = match.groupValues[3]
                segments.add(ContentSegment.ToolResult(toolName, content, status != "success"))
            }
        }
        
        private fun parseToolErrorMatch(matchText: String, segments: MutableList<ContentSegment>) {
            val match = xmlToolErrorPattern.find(matchText)
            if (match != null) {
                val toolName = match.groupValues[1]
                val content = match.groupValues[2]
                segments.add(ContentSegment.ToolResult(toolName, content, true))
            }
        }
        
        private fun parseLegacyToolExecution(matchText: String, segments: MutableList<ContentSegment>) {
            val match = toolExecutionPattern.find(matchText)
            if (match != null) {
                val toolName = match.groupValues[1]
                segments.add(ContentSegment.ToolExecution(toolName))
            }
        }
        
        private fun parseLegacyToolResult(matchText: String, segments: MutableList<ContentSegment>) {
            val match = toolResultPattern.find(matchText)
            if (match != null) {
                val toolName = match.groupValues[1]
                val content = match.groupValues[2]
                segments.add(ContentSegment.ToolResult(toolName, content, false))
            }
        }
        
        private fun parseLegacyToolError(matchText: String, segments: MutableList<ContentSegment>) {
            val match = toolErrorPattern.find(matchText)
            if (match != null) {
                val toolName = match.groupValues[1]
                val content = match.groupValues[2]
                segments.add(ContentSegment.ToolResult(toolName, content, true))
            }
        }
    }
} 
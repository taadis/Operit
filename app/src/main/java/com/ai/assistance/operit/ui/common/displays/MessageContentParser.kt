package com.ai.assistance.operit.ui.common.displays

/**
 * A utility class to parse message content with XML markup.
 * Extracts tool requests, executions, and results from message content.
 */
class MessageContentParser {
    companion object {
        // XML markup patterns
        public val xmlStatusPattern = Regex("<status\\s+type=\"([^\"]+)\"(?:\\s+uuid=\"([^\"]+)\")?(?:\\s+title=\"([^\"]+)\")?(?:\\s+subtitle=\"([^\"]+)\")?>([\\s\\S]*?)</status>")
        public val xmlToolResultPattern = Regex("<tool_result\\s+name=\"([^\"]+)\"\\s+status=\"([^\"]+)\">\\s*<content>([\\s\\S]*?)</content>\\s*</tool_result>")
        private val xmlToolRequestPattern = Regex("<tool\\s+name=\"([^\"]+)\"(?:\\s+description=\"([^\"]+)\")?>([\\s\\S]*?)</tool>")
        
        // 添加计划项XML标记的模式匹配
        private val planItemPattern = Regex("<plan_item\\s+(?:.*?)?id=\"([^\"]+)\"(?:.*?)status=\"([^\"]+)\"(?:.*?)>([^<]+)</plan_item>", RegexOption.DOT_MATCHES_ALL)
        private val planUpdatePattern = Regex("<plan_update\\s+(?:.*?)?(?:id=\"([^\"]+)\"(?:.*?)status=\"([^\"]+)\"|status=\"([^\"]+)\"(?:.*?)id=\"([^\"]+)\")(?:.*?)(?:/>|></plan_update>|>(?:[^<]*)</plan_update>)", RegexOption.DOT_MATCHES_ALL)

        // 添加缺失的工具名称和参数解析模式
        public val namePattern = Regex("<tool\\s+name=\"([^\"]+)\"")
        public val toolParamPattern = Regex("<param\\s+name=\"([^\"]+)\">([\\s\\S]*?)</param>")
    }
} 
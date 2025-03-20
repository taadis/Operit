package com.ai.assistance.operit.util

import com.ai.assistance.operit.data.preferencesManager
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 用户偏好分析工具，用于分析用户交互并更新用户偏好
 */
object UserPreferenceAnalyzer {
    
    // 记录用户输入的历史，用于偏好分析
    private val userInputHistory = mutableListOf<String>()
    
    // 用户偏好关键词映射表
    private val preferenceKeywords = mapOf(
        "问答风格" to listOf("详细", "简洁", "专业", "通俗", "轻松", "严肃"),
        "身份信息" to listOf("学生", "教师", "工程师", "医生", "律师", "艺术家", "设计师", "程序员"),
        "家庭信息" to listOf("孩子", "父母", "家人", "宝宝", "丈夫", "妻子", "老婆", "老公"),
        "社交信息" to listOf("朋友", "同事", "聚会", "社交", "团队"),
        "购物习惯" to listOf("购物", "价格", "便宜", "品质", "品牌", "推荐", "评价")
    )
    
    /**
     * 记录并分析用户输入
     * @param input 用户输入的文本
     */
    fun analyzeUserInput(input: String) {
        // 添加到历史记录
        userInputHistory.add(input)
        
        // 如果历史记录太大，移除最早的记录
        if (userInputHistory.size > 100) {
            userInputHistory.removeAt(0)
        }
        
        // 异步分析并更新用户偏好
        CoroutineScope(Dispatchers.IO).launch {
            val preferences = analyzePreferences()
            updateUserPreferences(preferences)
        }
    }
    
    /**
     * 分析用户输入历史，生成用户偏好描述
     * @return 用户偏好描述（不超过100字）
     */
    private fun analyzePreferences(): String {
        // 如果历史记录太少，返回空
        if (userInputHistory.size < 5) return ""
        
        val allText = userInputHistory.joinToString(" ")
        val preferences = mutableMapOf<String, MutableList<String>>()
        
        // 对每个类别进行关键词匹配
        preferenceKeywords.forEach { (category, keywords) ->
            val categoryPreferences = mutableListOf<String>()
            
            keywords.forEach { keyword ->
                // 简单的关键词匹配，实际应用中可以使用更复杂的算法
                if (allText.contains(keyword, ignoreCase = true)) {
                    categoryPreferences.add(keyword)
                }
            }
            
            if (categoryPreferences.isNotEmpty()) {
                preferences[category] = categoryPreferences
            }
        }
        
        // 生成偏好描述
        return generatePreferencesDescription(preferences)
    }
    
    /**
     * 根据匹配的偏好生成描述
     * @param preferences 各类别匹配到的偏好关键词
     * @return 用户偏好描述（不超过100字）
     */
    private fun generatePreferencesDescription(preferences: Map<String, List<String>>): String {
        if (preferences.isEmpty()) return ""
        
        val description = StringBuilder()
        
        preferences.forEach { (category, keywords) ->
            if (keywords.isNotEmpty()) {
                description.append("$category: ${keywords.joinToString(", ")}. ")
            }
        }
        
        // 确保不超过100字
        var result = description.toString()
        if (result.length > 100) {
            result = result.substring(0, 97) + "..."
        }
        
        return result
    }
    
    /**
     * 更新用户偏好设置
     * @param preferences 用户偏好描述
     */
    private suspend fun updateUserPreferences(preferences: String) {
        if (preferences.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                preferencesManager.updatePreferencesText(preferences)
            }
        }
    }
    
    /**
     * 使用AI分析用户与AI的对话，生成用户偏好描述
     * @param interactions 用户与AI的交互消息列表
     * @param aiService 增强AI服务实例
     */
    fun analyzeWithAI(interactions: List<ChatMessage>, aiService: EnhancedAIService) {
        if (interactions.isEmpty()) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 将交互信息转换为适用于API的格式
                val apiFormatInteractions = interactions.map { 
                    when (it.sender) {
                        "user" -> "user" to it.content
                        "ai" -> "assistant" to it.content
                        else -> "system" to it.content
                    }
                }
                
                // 使用新方法直接调用AI服务分析用户偏好
                aiService.analyzeUserPreferences(
                    conversationHistory = apiFormatInteractions
                ) { result ->
                    // 处理分析结果
                    if (result.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            // 更新用户偏好
                            updateUserPreferences(result)
                            android.util.Log.d("UserPreferenceAnalyzer", "用户偏好更新成功: $result")
                        }
                    }
                }
            } catch (e: Exception) {
                // 分析失败，记录日志但不中断用户体验
                android.util.Log.e("UserPreferenceAnalyzer", "AI分析失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 构建用于AI分析的提示
     * @param interactions 用户与AI的交互消息列表
     * @return 分析提示
     */
    private fun buildAnalysisPrompt(interactions: List<ChatMessage>): String {
        val userMessages = interactions.filter { it.sender == "user" }
            .map { it.content }
            .joinToString("\n\n- ")
        
        return """
            你是一个用户偏好分析专家。基于以下用户的交互记录，请分析用户的偏好，包括问答风格、身份信息、家庭情况、社交信息和购物习惯等方面。
            
            用户的交互记录：
            - $userMessages
            
            请自然地总结用户的个性化偏好，不超过100字。
        """.trimIndent()
    }
}
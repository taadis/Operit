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
                // 过滤掉包含工具结果的用户消息
                val filteredInteractions = interactions.filter { 
                    !(it.sender == "user" && it.content.contains("**Tool Result"))
                }
                
                // 将交互信息转换为适用于API的格式
                val apiFormatInteractions = filteredInteractions.map { 
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
}
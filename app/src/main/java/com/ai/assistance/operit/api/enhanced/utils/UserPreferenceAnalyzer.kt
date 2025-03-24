package com.ai.assistance.operit.api.enhanced.utils

import android.util.Log
import com.ai.assistance.operit.data.preferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Utility class for analyzing and managing user preferences
 */
object UserPreferenceAnalyzer {
    private const val TAG = "UserPreferenceAnalyzer"
    private val ioScope = CoroutineScope(Dispatchers.IO)

    /**
     * Analyze user preferences from conversation history
     * 
     * @param aiService The AIService instance to use for analysis
     * @param conversationHistory The conversation history to analyze
     * @param onResult Callback function that receives the analysis result
     */
    suspend fun analyzeUserPreferences(
        aiService: com.ai.assistance.operit.api.AIService,
        conversationHistory: List<Pair<String, String>>,
        onResult: suspend (String) -> Unit
    ) {
        try {
            // 获取当前的用户偏好
            val currentPreferences = preferencesManager.userPreferencesFlow.first()
            
            val systemPrompt = """
                你是一个用户偏好分析专家。请基于用户的对话历史和当前的AI印象偏好，分析并更新用户的偏好描述。
                
                当前的AI印象偏好：
                ${if (currentPreferences.preferences.isNotEmpty()) currentPreferences.preferences else "暂无偏好信息"}
                
                请基于新的对话历史，对上述偏好进行更新和补充。更新时请注意：
                1. 保持已有偏好的合理部分
                2. 根据新的对话内容补充或修改相关描述
                3. 确保描述自然流畅，不超过100字
                4. 重点关注：
                   - 提问风格偏好
                   - 个人信息（如有提及）
                   - 家庭信息（如有提及）
                   - 社交信息（如有提及）
                   - 购物习惯（如有提及）
                
                请提供一个自然的、符合用户特点的总结。注意：请基于用户真实表现出的特点进行分析，而不是臆测或过度推断。
            """.trimIndent()
            
            // 构建请求体
            val messages = mutableListOf<Pair<String, String>>()
            messages.add(Pair("system", systemPrompt))
            
            // 添加用户对话历史作为分析材料
            val analysisMessage = StringBuilder("以下是用户的对话历史，请分析其中的偏好特点：\n\n")
            for ((role, content) in conversationHistory) {
                if (role == "user") {
                    analysisMessage.append("用户: $content\n")
                } else if (role == "assistant") {
                    analysisMessage.append("助手: $content\n")
                }
            }
            
            messages.add(Pair("user", analysisMessage.toString()))
            
            // 收集分析结果
            val result = StringBuilder()
            
            // 直接调用 aiService，不经过 sendMessage
            withContext(Dispatchers.IO) {
                aiService.sendMessage(
                    message = analysisMessage.toString(),
                    onPartialResponse = { content, _ ->
                        // 只保存内容，不处理思考过程
                        result.clear()
                        result.append(content)
                    },
                    chatHistory = messages,
                    onComplete = {
                        Log.d(TAG, "用户偏好分析完成")
                        // 返回最终分析结果
                        val finalResult = result.toString().trim()
                        ioScope.launch {
                            onResult(finalResult)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "用户偏好分析失败", e)
            // 出错时返回空结果
            ioScope.launch {
                onResult("")
            }
        }
    }

    /**
     * Analyze and save user preferences
     * 
     * @param aiService The AIService instance to use for analysis
     * @param conversationHistory The conversation history to analyze
     */
    suspend fun analyzeAndSaveUserPreferences(
        aiService: com.ai.assistance.operit.api.AIService,
        conversationHistory: List<Pair<String, String>>
    ) {
        try {
            // 分析用户偏好
            analyzeUserPreferences(aiService, conversationHistory) { preferences ->
                if (preferences.isNotEmpty()) {
                    // 保存新的偏好
                    try {
                        // 将更新操作放在协程作用域中执行
                        withContext(Dispatchers.IO) {
                            preferencesManager.updatePreferences(preferences)
                            Log.d(TAG, "用户偏好已更新")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "保存用户偏好失败", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "分析用户偏好失败", e)
        }
    }
}
package com.ai.assistance.operit.api.enhanced.utils

import android.util.Log
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.data.preferencesManager
import com.ai.assistance.operit.data.ApiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.content.Context

/**
 * Utility class for managing operations related to the problem library
 */
object ProblemLibraryManager {
    private const val TAG = "ProblemLibraryManager"
    private var apiPreferences: ApiPreferences? = null
    
    // 初始化函数
    fun initialize(context: Context) {
        if (apiPreferences == null) {
            apiPreferences = ApiPreferences(context)
        }
    }

    /**
     * Data class to hold the combined analysis results
     */
    data class AnalysisResults(
        val problemSummary: String = "",
        val userPreferences: String = "",
        val solutionSummary: String = ""  // 添加解决方案摘要字段
    )
    
    /**
     * 估算文本的token数量
     * @param text 要估算的文本
     * @return 估算的token数量
     */
    private fun estimateTokenCount(text: String?): Int {
        if (text == null || text.isEmpty()) return 0
        
        // 简单估算：中文每个字约1.5个token，英文每4个字符约1个token
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount
        
        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toInt()
    }

    /**
     * Combined method to generate problem summary and analyze user preferences in a single AI call
     * 
     * @param aiService The AIService instance to use for analysis
     * @param query The user's original query
     * @param solution The AI-generated solution content
     * @param conversationHistory Full conversation history for additional context
     * @return An AnalysisResults object containing both the problem summary and user preferences
     */
    suspend fun generateCombinedAnalysis(
        aiService: com.ai.assistance.operit.api.AIService,
        query: String, 
        solution: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): AnalysisResults {
        try {
            Log.d(TAG, "开始组合分析，历史消息数: ${conversationHistory.size}")
            
            val systemPrompt = """
                你是一个专业的问题分析专家。你的任务是：
                1. 根据用户的问题和解决方案，生成一个简洁的问题摘要
                2. 分析用户的对话历史，提取用户偏好信息
                3. 对解决方案进行归纳总结，提炼核心内容
                
                你需要返回一个固定格式的JSON对象，包含三个字段：
                
                {
                  "problem_summary": "问题摘要内容",
                  "user_preferences": "用户偏好分析结果",
                  "solution_summary": "解决方案摘要内容"
                }
                
                问题摘要的要求：
                - 清晰描述问题的核心，不超过100字
                - 提取问题中的关键概念和工具使用场景
                - 使用关键词和短语，便于后续搜索匹配
                - 客观，不包含情感和主观评价
                - 保留技术术语和专业名词
                
                用户偏好分析的要求：
                - 从对话历史中提取用户的使用习惯、风格偏好、专业背景等信息
                - 分析用户可能喜欢的回答方式（如详细/简洁、专业/通俗等）
                - 不超过150字
                - 尽量客观，基于对话内容推断

                解决方案摘要的要求：
                - 提炼解决方案的核心步骤和关键点
                - 保留关键技术细节和实现方法
                - 不超过200字
                - 结构化，便于快速理解
                - 客观中立，不添加评价
                
                只返回格式正确的JSON对象，不要添加任何其他内容。
            """.trimIndent()

            // 构建请求
            val messages = mutableListOf<Pair<String, String>>()
            messages.add(Pair("system", systemPrompt))
            
            // 构建分析请求内容，包含完整上下文
            val analysisMessage = buildAnalysisMessage(query, solution, conversationHistory)
            
            // 估算输入token
            val inputTokenCount = estimateTokenCount(systemPrompt) + estimateTokenCount(analysisMessage)
            Log.d(TAG, "估算的输入Token数: $inputTokenCount")
            
            messages.add(Pair("user", analysisMessage))
            
            // 收集分析结果
            val result = StringBuilder()
            var outputTokenCount = 0
            
            // 直接调用 aiService
            withContext(Dispatchers.IO) {
                try {
                    aiService.sendMessage(
                        message = analysisMessage,
                        onPartialResponse = { content, _ ->
                            // 只保存内容，不处理思考过程
                            result.clear()
                            result.append(content)
                            // 更新输出token计数
                            outputTokenCount = estimateTokenCount(content)
                        },
                        chatHistory = messages,
                        onComplete = {
                            Log.d(TAG, "组合分析完成，输入Token: $inputTokenCount，输出Token: $outputTokenCount")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "AI服务调用失败", e)
                }
            }
            
            // 更新token统计
            apiPreferences?.let { prefs ->
                try {
                    withContext(Dispatchers.IO) {
                        prefs.updatePreferenceAnalysisTokens(inputTokenCount, outputTokenCount)
                        Log.d(TAG, "偏好分析token统计已更新：输入+$inputTokenCount，输出+$outputTokenCount")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新token统计失败", e)
                }
            } ?: Log.w(TAG, "ApiPreferences未初始化，无法更新token统计")
            
            return parseAnalysisResults(result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "组合分析失败", e)
            return AnalysisResults() // 出错时返回空对象
        }
    }
    
    /**
     * Build the message for combined analysis
     */
    private fun buildAnalysisMessage(
        query: String,
        solution: String,
        conversationHistory: List<Pair<String, String>>
    ): String {
        val messageBuilder = StringBuilder()
        
        // 添加当前问题和解决方案
        messageBuilder.appendLine("Question:")
        messageBuilder.appendLine(query)
        messageBuilder.appendLine()
        messageBuilder.appendLine("Solution:")
        // 增加解决方案的长度上限，确保AI有足够内容进行总结
        messageBuilder.appendLine(solution.take(2000)) // 从1000增加到2000字符
        messageBuilder.appendLine()
        
        // 添加对话历史用于偏好分析，最多取最近10条
        val lastestUserIndex = conversationHistory.indexOfLast { it.first == "user" }
        
        // 检查lastestUserIndex是否有效
        val recentHistory = if (lastestUserIndex > 0) {
            // 有效索引，取前面的消息
            conversationHistory.subList(0, lastestUserIndex).takeLast(10)
        } else {
            // 无效索引或只有第一条是user消息，使用空列表
            emptyList()
        }

        if (recentHistory.isNotEmpty()) {
            messageBuilder.appendLine("History:")
            recentHistory.forEach { (role, content) ->
                messageBuilder.appendLine("$role: ${content.take(200)}") // 每条消息最多200字
            }
        }
        
        return messageBuilder.toString()
    }
    
    /**
     * Parse the combined analysis results from JSON
     */
    private fun parseAnalysisResults(jsonString: String): AnalysisResults {
        return try {
            // 清理可能的非JSON前缀
            val cleanJson = jsonString.trim().let {
                val startIndex = it.indexOf("{")
                val endIndex = it.lastIndexOf("}")
                if (startIndex >= 0 && endIndex > startIndex) {
                    it.substring(startIndex, endIndex + 1)
                } else {
                    it
                }
            }
            
            val json = JSONObject(cleanJson)
            AnalysisResults(
                problemSummary = json.optString("problem_summary", "").take(200),
                userPreferences = json.optString("user_preferences", "").take(200),
                solutionSummary = json.optString("solution_summary", "").take(300)  // 添加解决方案摘要解析
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析分析结果失败", e)
            // 如果JSON解析失败，尝试将整个字符串作为摘要
            AnalysisResults(problemSummary = jsonString.take(200))
        }
    }

    /**
     * Save a problem record to the problem library and update user preferences
     */
    suspend fun saveProblemToLibrary(
        toolHandler: AIToolHandler,
        conversationHistory: List<Pair<String, String>>,
        content: String,
        aiService: com.ai.assistance.operit.api.AIService,
        context: Context? = null
    ) {
        try {
            // 检查会话历史是否为空
            if (conversationHistory.isEmpty()) {
                Log.w(TAG, "会话历史为空，跳过保存问题记录")
                return
            }
            
            // 确保ApiPreferences已初始化
            if (context != null) { 
                initialize(context) 
                Log.d(TAG, "ApiPreferences初始化成功")
            } else {
                Log.w(TAG, "Context为空，ApiPreferences可能未初始化，token统计可能不会更新")
            }
            
            // 提取使用的工具
            val toolInvocations = toolHandler.extractToolInvocations(content)
            val tools = toolInvocations.map { it.tool.name }

            // 获取用户最后一条消息作为查询
            val query = conversationHistory.lastOrNull { it.first == "user" }?.second ?: ""
            if (query.isEmpty()) {
                Log.w(TAG, "未找到用户查询消息，使用空字符串")
            }
            
            Log.d(TAG, "开始生成组合分析")
            // 生成组合分析：问题摘要、用户偏好和解决方案摘要
            val analysisResults = try {
                generateCombinedAnalysis(aiService, query, content, conversationHistory)
            } catch (e: Exception) {
                Log.e(TAG, "生成组合分析失败", e)
                AnalysisResults() // 使用空对象
            }
            
            // 更新用户偏好（如果有）
            if (analysisResults.userPreferences.isNotEmpty()) {
                try {
                    withContext(Dispatchers.IO) {
                        preferencesManager.updatePreferencesText(analysisResults.userPreferences)
                        Log.d(TAG, "用户偏好已更新")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新用户偏好失败", e)
                }
            } else {
                Log.d(TAG, "用户偏好分析为空，跳过更新")
            }

            Log.d(TAG, "问题摘要: ${analysisResults.problemSummary}")
            Log.d(TAG, "解决方案摘要: ${analysisResults.solutionSummary}")

            // 创建问题记录，使用AI生成的解决方案摘要而不是原始内容
            val record = AIToolHandler.ProblemRecord(
                uuid = java.util.UUID.randomUUID().toString(),
                query = query,
                solution = if (analysisResults.solutionSummary.isNotEmpty()) 
                             analysisResults.solutionSummary  // 使用AI生成的解决方案摘要
                           else content.take(300),            // 如果摘要为空，则取原内容的前300字符
                tools = tools,
                summary = analysisResults.problemSummary
            )

            try {
                // 保存到问题库
                toolHandler.saveProblemRecord(record)
                Log.d(TAG, "问题记录已保存到库: ${record.uuid}")
            } catch (e: Exception) {
                Log.e(TAG, "保存问题记录到库失败", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存问题记录失败", e)
        }
    }
} 
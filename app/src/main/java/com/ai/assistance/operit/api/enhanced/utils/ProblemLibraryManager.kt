package com.ai.assistance.operit.api.enhanced.utils

import android.util.Log
import com.ai.assistance.operit.tools.AIToolHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for managing operations related to the problem library
 */
object ProblemLibraryManager {
    private const val TAG = "ProblemLibraryManager"

    /**
     * Generate a summary for a problem using AI
     * 
     * @param aiService The AIService instance to use for generating the summary
     * @param query The user's original query
     * @param solution The AI-generated solution content
     * @return A generated summary of the problem
     */
    suspend fun generateProblemSummary(
        aiService: com.ai.assistance.operit.api.AIService,
        query: String, 
        solution: String
    ): String {
        try {
            val systemPrompt = """
                你是一个专业的问题总结专家。你的任务是根据用户的问题和解决方案，生成一个简洁的问题摘要。
                
                总结时需要遵循以下规则：
                1. 摘要应该清晰描述问题的核心，不超过100字
                2. 提取问题中的关键概念和工具使用场景
                3. 使用关键词和短语，便于后续搜索匹配
                4. 总结应该是客观的，不包含情感和主观评价
                5. 保留技术术语和专业名词
                
                只输出总结内容，不要添加任何前缀、解释或其他内容。
            """.trimIndent()

            // 构建请求
            val messages = mutableListOf<Pair<String, String>>()
            messages.add(Pair("system", systemPrompt))
            
            val analysisMessage = """
                问题:
                $query
                
                解决方案:
                ${solution.take(1000)}  // 只取前1000个字符，避免过长
            """.trimIndent()
            
            messages.add(Pair("user", analysisMessage))
            
            // 收集分析结果
            val result = StringBuilder()
            
            // 直接调用 aiService
            withContext(Dispatchers.IO) {
                aiService.sendMessage(
                    message = analysisMessage,
                    onPartialResponse = { content, _ ->
                        // 只保存内容，不处理思考过程
                        result.clear()
                        result.append(content)
                    },
                    chatHistory = messages,
                    onComplete = {
                        Log.d(TAG, "问题总结生成完成")
                    }
                )
            }
            
            // 返回生成的总结，最多200字
            return result.toString().trim().take(200)
        } catch (e: Exception) {
            Log.e(TAG, "生成问题总结失败", e)
            return "" // 出错时返回空
        }
    }

    /**
     * Save a problem record to the problem library
     * 
     * @param toolHandler The AIToolHandler instance used to save the record
     * @param conversationHistory The conversation history
     * @param content The AI-generated content
     */
    suspend fun saveProblemToLibrary(
        toolHandler: AIToolHandler,
        conversationHistory: List<Pair<String, String>>,
        content: String,
        aiService: com.ai.assistance.operit.api.AIService
    ) {
        try {
            // 提取使用的工具
            val toolInvocations = toolHandler.extractToolInvocations(content)
            val tools = toolInvocations.map { it.tool.name }

            // 获取用户最后一条消息作为查询
            val query = conversationHistory.lastOrNull { it.first == "user" }?.second ?: ""
            
            // 生成问题总结
            val summary = generateProblemSummary(aiService, query, content)

            // 创建问题记录
            val record = AIToolHandler.ProblemRecord(
                uuid = java.util.UUID.randomUUID().toString(),
                query = query,
                solution = content,
                tools = tools,
                summary = summary
            )

            // 保存到问题库
            toolHandler.saveProblemRecord(record)
            Log.d(TAG, "问题记录已保存到库: ${record.uuid}")
        } catch (e: Exception) {
            Log.e(TAG, "保存问题记录失败", e)
        }
    }
} 
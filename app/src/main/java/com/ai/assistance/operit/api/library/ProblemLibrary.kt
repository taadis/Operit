package com.ai.assistance.operit.api.library

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.AIService
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.tools.AIToolHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 问题库管理类 - 提供分析对话内容并存储问题记录的功能
 */
object ProblemLibrary {
    private const val TAG = "ProblemLibrary"
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var apiPreferences: ApiPreferences? = null
    
    /**
     * 分析结果数据类
     */
    data class AnalysisResults(
        val problemSummary: String = "",
        val userPreferences: String = "",
        val solutionSummary: String = ""
    )
    
    /**
     * 初始化问题库
     */
    fun initialize(context: Context) {
        apiPreferences = ApiPreferences(context.applicationContext)
    }
    
    /**
     * 保存问题到问题库（异步方式）
     */
    fun saveProblemAsync(
        context: Context,
        toolHandler: AIToolHandler,
        conversationHistory: List<Pair<String, String>>,
        content: String,
        aiService: AIService
    ) {
        ensureInitialized(context)
        
        coroutineScope.launch {
            try {
                saveProblem(toolHandler, conversationHistory, content, aiService)
            } catch (e: Exception) {
                Log.e(TAG, "保存问题记录失败", e)
            }
        }
    }
    
    /**
     * 分析对话内容并生成摘要（异步，带回调）
     */
    fun analyzeQueryAsync(
        context: Context, 
        query: String, 
        response: String,
        aiService: AIService,
        callback: (AnalysisResults) -> Unit
    ) {
        ensureInitialized(context)
        
        coroutineScope.launch {
            try {
                val result = generateAnalysis(aiService, query, response, emptyList())
                callback(result)
            } catch (e: Exception) {
                Log.e(TAG, "分析对话失败", e)
                callback(AnalysisResults())
            }
        }
    }
    
    /**
     * 确保已初始化
     */
    private fun ensureInitialized(context: Context) {
        if (apiPreferences == null) {
            initialize(context)
        }
    }
    
    /**
     * 保存问题记录（内部实现）
     */
    private suspend fun saveProblem(
        toolHandler: AIToolHandler,
        conversationHistory: List<Pair<String, String>>,
        content: String,
        aiService: AIService
    ) {
        // 检查会话历史是否为空
        if (conversationHistory.isEmpty()) {
            Log.w(TAG, "会话历史为空，跳过保存问题记录")
            return
        }
        
        // 提取使用的工具
        val toolInvocations = toolHandler.extractToolInvocations(content)
        val tools = toolInvocations.map { it.tool.name }

        // 获取用户最后一条消息作为查询
        val query = conversationHistory.lastOrNull { it.first == "user" }?.second ?: ""
        if (query.isEmpty()) {
            Log.w(TAG, "未找到用户查询消息，使用空字符串")
        }
        
        // 生成问题分析
        val analysisResults = try {
            generateAnalysis(aiService, query, content, conversationHistory)
        } catch (e: Exception) {
            Log.e(TAG, "生成分析失败", e)
            AnalysisResults()
        }
        
        // 更新用户偏好
        if (analysisResults.userPreferences.isNotEmpty()) {
            try {
                withContext(Dispatchers.IO) {
                    preferencesManager.updatePreferencesText(analysisResults.userPreferences)
                    Log.d(TAG, "用户偏好已更新")
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新用户偏好失败", e)
            }
        }

        // 创建并保存问题记录
        val record = AIToolHandler.ProblemRecord(
            uuid = java.util.UUID.randomUUID().toString(),
            query = query,
            solution = if (analysisResults.solutionSummary.isNotEmpty()) 
                        analysisResults.solutionSummary
                      else content.take(300),
            tools = tools,
            summary = analysisResults.problemSummary
        )

        try {
            toolHandler.saveProblemRecord(record)
            Log.d(TAG, "问题记录已保存: ${record.uuid}")
        } catch (e: Exception) {
            Log.e(TAG, "保存问题记录失败", e)
        }
    }
    
    /**
     * 生成分析结果
     */
    private suspend fun generateAnalysis(
        aiService: AIService,
        query: String, 
        solution: String,
        conversationHistory: List<Pair<String, String>>
    ): AnalysisResults {
        try {
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
                
                问题摘要：清晰描述问题核心，不超过100字，客观，保留技术术语
                用户偏好：提取使用习惯与风格偏好，不超过150字
                解决方案摘要：提炼核心步骤和关键点，不超过200字，结构化
                
                只返回格式正确的JSON对象，不要添加任何其他内容。
            """.trimIndent()

            // 构建分析消息
            val analysisMessage = buildAnalysisMessage(query, solution, conversationHistory)
            
            // 预估token
            val inputTokens = estimateTokenCount(systemPrompt) + estimateTokenCount(analysisMessage)
            
            // 准备消息
            val messages = listOf(
                Pair("system", systemPrompt),
                Pair("user", analysisMessage)
            )
            
            // 收集结果
            val result = StringBuilder()
            var outputTokens = 0
            
            // 调用AI服务
            withContext(Dispatchers.IO) {
                aiService.sendMessage(
                    message = analysisMessage,
                    onPartialResponse = { content, _ ->
                        result.clear()
                        result.append(content)
                        outputTokens = estimateTokenCount(content)
                    },
                    chatHistory = messages,
                    onComplete = {}
                )
            }
            
            // 更新token统计
            apiPreferences?.updatePreferenceAnalysisTokens(inputTokens, outputTokens)
            
            // 解析结果
            return parseAnalysisResult(result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "生成分析失败", e)
            return AnalysisResults()
        }
    }
    
    /**
     * 构建分析消息
     */
    private fun buildAnalysisMessage(
        query: String,
        solution: String,
        conversationHistory: List<Pair<String, String>>
    ): String {
        val messageBuilder = StringBuilder()
        
        // 添加问题和解决方案
        messageBuilder.appendLine("问题:")
        messageBuilder.appendLine(query)
        messageBuilder.appendLine()
        messageBuilder.appendLine("解决方案:")
        messageBuilder.appendLine(solution.take(2000))
        messageBuilder.appendLine()
        
        // 添加对话历史（最多10条）
        val recentHistory = conversationHistory.takeLast(10)
        if (recentHistory.isNotEmpty()) {
            messageBuilder.appendLine("历史记录:")
            recentHistory.forEach { (role, content) ->
                messageBuilder.appendLine("$role: ${content.take(200)}")
            }
        }
        
        return messageBuilder.toString()
    }
    
    /**
     * 解析分析结果
     */
    private fun parseAnalysisResult(jsonString: String): AnalysisResults {
        return try {
            // 清理非JSON前缀
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
                solutionSummary = json.optString("solution_summary", "").take(300)
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析分析结果失败", e)
            AnalysisResults(problemSummary = jsonString.take(200))
        }
    }
    
    /**
     * 估算token数量
     */
    private fun estimateTokenCount(text: String?): Int {
        if (text == null || text.isEmpty()) return 0
        
        // 简单估算：中文字符约1.5个token，其他字符约0.25个token
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount
        
        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toInt()
    }
} 
package com.ai.assistance.operit.api

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.flatMap
import com.ai.assistance.operit.util.stream.plugins.PluginState
import com.ai.assistance.operit.util.stream.plugins.StreamPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Google Gemini API的实现 支持标准Gemini接口流式传输 */
class GeminiProvider(
        private val apiEndpoint: String,
        private val apiKey: String,
        private val modelName: String
) : AIService {
    companion object {
        private const val TAG = "GeminiProvider"
        private const val DEBUG = true // 开启调试日志
    }

    // HTTP客户端
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val requestId = System.currentTimeMillis().toString()
                        Log.d(TAG, "发送请求: ${request.method} ${request.url}")

                        val startTime = System.currentTimeMillis()
                        val response = chain.proceed(request)
                        val duration = System.currentTimeMillis() - startTime

                        Log.d(TAG, "收到响应: ${response.code}, 耗时: ${duration}ms")
                        response
                    }
                    .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 活跃请求，用于取消流式请求
    private var activeCall: Call? = null

    // Token计数
    private var _inputTokenCount = 0
    private var _outputTokenCount = 0

    override val inputTokenCount: Int
        get() = _inputTokenCount
    override val outputTokenCount: Int
        get() = _outputTokenCount

    // 取消当前流式传输
    override fun cancelStreaming() {
        activeCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
                Log.d(TAG, "已取消当前流式传输")
            }
        }
        activeCall = null
    }

    // 重置Token计数
    override fun resetTokenCounts() {
        _inputTokenCount = 0
        _outputTokenCount = 0
    }

    // 工具函数：分块打印大型文本日志
    private fun logLargeString(tag: String, message: String, prefix: String = "") {
        // 设置单次日志输出的最大长度（Android日志上限约为4000字符）
        val maxLogSize = 3000

        // 如果消息长度超过限制，分块打印
        if (message.length > maxLogSize) {
            // 计算需要分多少块打印
            val chunkCount = message.length / maxLogSize + 1

            for (i in 0 until chunkCount) {
                val start = i * maxLogSize
                val end = minOf((i + 1) * maxLogSize, message.length)
                val chunkMessage = message.substring(start, end)

                // 打印带有编号的日志
                Log.d(tag, "$prefix Part ${i+1}/$chunkCount: $chunkMessage")
            }
        } else {
            // 消息长度在限制之内，直接打印
            Log.d(tag, "$prefix$message")
        }
    }

    // 日志辅助方法
    private fun logDebug(message: String) {
        if (DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    /**
     * JSON流解析插件，用于在字符流中识别和提取JSON对象/数组
     * 可以实现增量解析，无需等待完整JSON块
     */
    private class JsonStreamPlugin : StreamPlugin {
        private var depth = 0
        private var inString = false
        private var escaped = false
        
        override var state: PluginState = PluginState.IDLE
            private set

        override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
            // 处理转义状态
            if (escaped) {
                escaped = false
            } else if (c == '\\' && inString) {
                escaped = true
            } else if (c == '"') {
                inString = !inString
            } else if (!inString) {
                // 只有不在字符串内时才处理括号
                when (c) {
                    '{', '[' -> {
                        depth++
                        if (state == PluginState.IDLE) {
                            state = PluginState.PROCESSING
                        }
                    }
                    '}', ']' -> {
                        depth--
                        if (depth == 0 && state == PluginState.PROCESSING) {
                            // 一个完整的JSON对象或数组已处理完
                            state = PluginState.IDLE
                        }
                    }
                }
            }

            // 返回true表示这个字符应该被包含在输出中
            return state == PluginState.PROCESSING || (depth == 0 && (c == '}' || c == ']'))
        }

        override fun initPlugin(): Boolean {
            reset()
            return true
        }

        override fun destroy() {
            // 无需释放资源
        }

        override fun reset() {
            depth = 0
            inString = false
            escaped = false
            state = PluginState.IDLE
        }
    }

    /** 发送消息到Gemini API */
    override suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>
    ): Stream<String> = stream {
        val requestId = System.currentTimeMillis().toString()
        // 重置token计数
        resetTokenCounts()

        Log.d(TAG, "发送消息到Gemini API, 模型: $modelName")

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        val requestBody = createRequestBody(message, chatHistory, modelParameters)
        val request = createRequest(requestBody, true, requestId) // 使用流式请求

        logDebug("连接到Gemini服务...")

        while (retryCount < maxRetries) {
            try {
                // 创建响应行流
                val lineStream = stream<String> {
                    try {
                        val call = client.newCall(request)
                        activeCall = call

                        logDebug("建立连接中...")

                        val startTime = System.currentTimeMillis()
                        call.execute().use { response ->
                            val duration = System.currentTimeMillis() - startTime
                            Log.d(TAG, "收到初始响应, 耗时: ${duration}ms, 状态码: ${response.code}")

                            logDebug("连接成功，处理响应...")

                            if (!response.isSuccessful) {
                                val errorBody = response.body?.string() ?: "无错误详情"
                                logError("API请求失败: ${response.code}, $errorBody")
                                throw IOException("API请求失败: ${response.code}, $errorBody")
                            }

                            val responseBody = response.body ?: throw IOException("响应为空")
                            val reader = responseBody.charStream().buffered()

                            reader.useLines { lines ->
                                for (line in lines) {
                                    if (activeCall?.isCanceled() == true) break
                                    emit(line)
                                }
                            }
                        }
                    } finally {
                        activeCall = null
                    }
                }

                // 使用Stream处理响应行，过滤并转换为字符流
                val charStream = lineStream.flatMap { line ->
                    stream {
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6).trim()
                            if (data != "[DONE]") {
                                for (c in data) {
                                    emit(c)
                                }
                            }
                        }
                    }
                }

                // 使用JsonStreamPlugin分割JSON
                val jsonPlugin = JsonStreamPlugin()
                var hasEmittedContent = false

                charStream.splitBy(listOf(jsonPlugin)).collect { group ->
                    if (group.tag is JsonStreamPlugin) {
                        // 收集JSON块
                        val jsonBuilder = StringBuilder()
                        group.stream.collect { jsonBuilder.append(it) }
                        
                        try {
                            val jsonStr = jsonBuilder.toString()
                            logDebug("处理JSON块: ${jsonStr.take(100)}${if (jsonStr.length > 100) "..." else ""}")
                            
                            // 解析JSON
                            val json = if (jsonStr.startsWith("{")) {
                                JSONObject(jsonStr)
                            } else if (jsonStr.startsWith("[")) {
                                JSONArray(jsonStr)
                                null  // 我们主要处理JSONObject
                            } else {
                                null
                            }
                            
                            // 处理JSON对象
                            if (json is JSONObject) {
                                val content = extractContentFromJson(json, requestId)
                                if (content.isNotEmpty()) {
                                    _outputTokenCount += estimateTokenCount(content)
                                    emit(content)
                                    hasEmittedContent = true
                                }
                            }
                        } catch (e: Exception) {
                            // JSON可能不完整，这是正常的流式处理中间状态
                            logDebug("JSON处理中: ${e.message}")
                        }
                    }
                }

                // 如果没有内容发射，发送一个空格
                if (!hasEmittedContent) {
                    logDebug("未检测到内容，发送空格")
                    emit(" ")
                }

                // 成功处理后返回
                return@stream
            } catch (e: SocketTimeoutException) {
                lastException = e
                retryCount++
                logError("连接超时，尝试重试 $retryCount/$maxRetries", e)

                emit("【连接超时，正在重试 $retryCount...】")
                delay(1000L * retryCount)
            } catch (e: UnknownHostException) {
                logError("无法连接到服务器，可能是网络问题", e)
                emit("【无法连接到服务器，请检查网络】")
                throw IOException("无法连接到服务器，请检查网络连接")
            } catch (e: Exception) {
                logError("发送消息时发生异常", e)
                emit("【连接失败: ${e.message}】")
                throw IOException("AI响应获取失败: ${e.message}")
            }
        }

        logError("重试${maxRetries}次后仍然失败", lastException)
        emit("【重试失败，请检查网络连接】")
        throw IOException("连接超时，已重试 $maxRetries 次")
    }

    /** 创建请求体 */
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>
    ): RequestBody {
        val requestId = System.currentTimeMillis().toString()
        val json = JSONObject()
        val contentsArray = JSONArray()

        logDebug("开始创建请求体，历史消息: ${chatHistory.size}条")

        // 标准化历史消息
        val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(chatHistory)

        // 调试输出所有历史消息
        for ((index, pair) in standardizedHistory.withIndex()) {
            logDebug("历史消息[$index]: role=${pair.first}, content长度=${pair.second.length}")
        }

        // 处理历史消息
        if (standardizedHistory.isNotEmpty()) {
            var lastRole: String? = null
            var hasSystemMessage = false
            var systemContent: String? = null

            // 首先检查是否有系统消息
            for ((index, pair) in standardizedHistory.withIndex()) {
                if (pair.first == "system") {
                    hasSystemMessage = true
                    systemContent = pair.second
                    logDebug("发现系统消息: ${systemContent.take(50)}...")

                    // 估算token
                    val tokens = estimateTokenCount(systemContent) + 20 // 增加一些token计数以包含额外的格式标记
                    _inputTokenCount += tokens
                    break // 只处理第一条系统消息
                }
            }

            // 如果有系统消息，使用专用的system_instruction字段
            if (hasSystemMessage && systemContent != null) {
                val systemInstruction = JSONObject()
                val systemPartsArray = JSONArray()
                val systemTextPart = JSONObject()
                
                systemTextPart.put("text", systemContent)
                systemPartsArray.put(systemTextPart)
                
                systemInstruction.put("parts", systemPartsArray)
                json.put("systemInstruction", systemInstruction)
            }

            // 处理其余消息
            for ((role, content) in standardizedHistory) {
                // 跳过已处理的系统消息
                if (role == "system") {
                    continue
                }

                // 如果与上一条消息角色相同，跳过
                if (role == lastRole) {
                    logDebug("跳过连续相同角色消息: $role")
                    continue
                }

                lastRole = role

                val contentObject = JSONObject()
                val partsArray = JSONArray()
                val textPart = JSONObject()
                textPart.put("text", content)
                partsArray.put(textPart)

                contentObject.put("role", if (role == "assistant") "model" else role)
                contentObject.put("parts", partsArray)
                contentsArray.put(contentObject)

                // 估算token
                val tokens = estimateTokenCount(content)
                _inputTokenCount += tokens
            }
        }

        // 添加当前用户消息
        val userContentObject = JSONObject()
        val userPartsArray = JSONArray()
        val userTextPart = JSONObject()
        userTextPart.put("text", message)
        userPartsArray.put(userTextPart)

        userContentObject.put("role", "user")
        userContentObject.put("parts", userPartsArray)
        contentsArray.put(userContentObject)

        // 估算token
        val tokens = estimateTokenCount(message)
        _inputTokenCount += tokens

        // 添加contents到请求体
        json.put("contents", contentsArray)

        // 添加生成配置
        val generationConfig = JSONObject()

        // 添加模型参数
        for (param in modelParameters) {
            if (param.isEnabled) {
                when (param.apiName) {
                    "temperature" ->
                            generationConfig.put(
                                    "temperature",
                                    (param.currentValue as Number).toFloat()
                            )
                    "top_p" ->
                            generationConfig.put("topP", (param.currentValue as Number).toFloat())
                    "top_k" -> generationConfig.put("topK", (param.currentValue as Number).toInt())
                    "max_tokens" ->
                            generationConfig.put(
                                    "maxOutputTokens",
                                    (param.currentValue as Number).toInt()
                            )
                    else -> generationConfig.put(param.apiName, param.currentValue)
                }
            }
        }

        json.put("generationConfig", generationConfig)

        val jsonString = json.toString()
        // 使用分块日志函数记录完整的请求体
        logLargeString(TAG, jsonString, "请求体JSON: ")

        return jsonString.toRequestBody(JSON)
    }

    /** 创建HTTP请求 */
    private fun createRequest(
            requestBody: RequestBody,
            isStreaming: Boolean,
            requestId: String
    ): Request {
        // 确定请求URL
        val baseUrl = determineBaseUrl(apiEndpoint)
        val method = if (isStreaming) "streamGenerateContent" else "generateContent"
        val requestUrl = "$baseUrl/v1beta/models/$modelName:$method"

        Log.d(TAG, "请求URL: $requestUrl")

        // 创建Request Builder
        val builder = Request.Builder()

        // 添加API密钥
        val finalUrl =
                if (requestUrl.contains("?")) {
                    "$requestUrl&key=$apiKey"
                } else {
                    "$requestUrl?key=$apiKey"
                }

        return builder.url(finalUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
    }

    /** 确定基础URL */
    private fun determineBaseUrl(endpoint: String): String {
        return try {
            val url = URL(endpoint)
            "${url.protocol}://${url.host}"
        } catch (e: Exception) {
            logError("解析API端点失败", e)
            "https://generativelanguage.googleapis.com"
        }
    }

    /** 从Gemini响应JSON中提取内容 */
    private fun extractContentFromJson(json: JSONObject, requestId: String): String {
        val contentBuilder = StringBuilder()

        try {
            // 检查是否有错误信息
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorMsg = error.optString("message", "未知错误")
                logError("API返回错误: $errorMsg")
                return "" // 有错误时返回空字符串
            }

            // 提取候选项
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                logDebug("未找到候选项")
                return ""
            }

            // 处理第一个candidate
            val candidate = candidates.getJSONObject(0)

            // 检查finish_reason
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason.isNotEmpty() && finishReason != "STOP") {
                logDebug("收到完成原因: $finishReason")
            }

            // 提取content对象
            val content = candidate.optJSONObject("content")
            if (content == null) {
                logDebug("未找到content对象")
                return ""
            }

            // 提取parts数组
            val parts = content.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                logDebug("未找到parts数组或为空")
                return ""
            }

            // 遍历parts，提取text内容
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")

                if (text.isNotEmpty()) {
                    contentBuilder.append(text)

                    // 估算token
                    val tokens = estimateTokenCount(text)
                    _outputTokenCount += tokens
                    logDebug("提取文本，长度=${text.length}, tokens=$tokens")
                }
            }

            return contentBuilder.toString()
        } catch (e: Exception) {
            logError("提取内容时发生错误: ${e.message}", e)
            return ""
        }
    }

    /** 估算Token数量 */
    private fun estimateTokenCount(text: String): Int {
        // 简单估算：中文每个字约1.5个token，英文每4个字符约1个token
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount

        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toInt()
    }

    /** 获取模型列表 */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        return ModelListFetcher.getModelsList(
                apiKey = apiKey,
                apiEndpoint = apiEndpoint,
                apiProviderType = ApiProviderType.GOOGLE
        )
    }
}

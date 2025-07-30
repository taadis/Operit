package com.ai.assistance.operit.api.chat

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.stream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
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
    private val client: OkHttpClient = HttpClientFactory.instance

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

    /** 发送消息到Gemini API */
    override suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            onTokensUpdated: suspend (input: Int, output: Int) -> Unit,
            onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        val requestId = System.currentTimeMillis().toString()
        // 重置token计数
        resetTokenCounts()
        onTokensUpdated(_inputTokenCount, _outputTokenCount)

        Log.d(TAG, "发送消息到Gemini API, 模型: $modelName")

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        // 用于保存已接收到的内容，以便在重试时使用
        val receivedContent = StringBuilder()


        // 状态更新函数 - 在Stream中我们使用emit来传递连接状态
        val emitConnectionStatus: (String) -> Unit = { status ->
            // 这里可以根据需要处理连接状态，例如记录日志
            logDebug("连接状态: $status")
        }

        emitConnectionStatus("连接到Gemini服务...")

        while (retryCount < maxRetries) {
            try {
                // 如果是重试，我们需要构建一个新的请求
                val currentMessage: String
                val currentHistory: List<Pair<String, String>>

                if (retryCount > 0 && receivedContent.isNotEmpty()) {
                    Log.d(TAG, "【Gemini 重试】准备续写请求，已接收内容长度: ${receivedContent.length}")
                    currentMessage = message + "\n\n[SYSTEM NOTE] The previous response was cut off by a network error. You MUST continue from the exact point of interruption. Do not repeat any content. If you were in the middle of a code block or XML tag, complete it. Just output the text that would have come next."
                    val resumeHistory = chatHistory.toMutableList()
                    resumeHistory.add("model" to receivedContent.toString()) // Gemini uses 'model' role for assistant
                    currentHistory = resumeHistory
                } else {
                    currentMessage = message
                    currentHistory = chatHistory
                }

                val requestBody = createRequestBody(currentMessage, currentHistory, modelParameters, enableThinking)
                onTokensUpdated(_inputTokenCount, _outputTokenCount)
                val request = createRequest(requestBody, true, requestId) // 使用流式请求

                val call = client.newCall(request)
                activeCall = call

                emitConnectionStatus("建立连接中...")

                val startTime = System.currentTimeMillis()
                call.execute().use { response ->
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "收到初始响应, 耗时: ${duration}ms, 状态码: ${response.code}")

                    emitConnectionStatus("连接成功，处理响应...")

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "无错误详情"
                        logError("API请求失败: ${response.code}, $errorBody")
                        throw IOException("API请求失败: ${response.code}, $errorBody")
                    }

                    // 处理响应
                    processStreamingResponse(response, this, requestId, onTokensUpdated, receivedContent)
                }

                activeCall = null
                return@stream
            } catch (e: SocketTimeoutException) {
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("连接超时且达到最大重试次数", e)
                    throw IOException("AI响应获取失败，连接超时且已达最大重试次数: ${e.message}")
                }
                logError("连接超时，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络超时，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: UnknownHostException) {
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("无法解析主机且达到最大重试次数", e)
                    throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
                }
                logError("无法解析主机，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络不稳定，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: IOException) {
                // 捕获所有其他IO异常，包括流读取中断
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("达到最大重试次数后仍然失败", e)
                    throw IOException("AI响应获取失败，已达最大重试次数: ${e.message}")
                }
                logError("IO异常，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络中断，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: Exception) {
                lastException = e
                retryCount++
                 if (retryCount >= maxRetries) {
                    logError("未知异常且达到最大重试次数", e)
                    throw IOException("AI响应获取失败: ${e.message}")
                }
                logError("发送消息时发生异常，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【连接失败，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            }
        }

        logError("重试${maxRetries}次后仍然失败", lastException)
        throw IOException("连接超时或中断，已重试 $maxRetries 次: ${lastException?.message}")
    }

    /** 创建请求体 */
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean
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
                    val tokens = ChatUtils.estimateTokenCount(systemContent) + 20 // 增加一些token计数以包含额外的格式标记
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
                val tokens = ChatUtils.estimateTokenCount(content)
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
        val tokens = ChatUtils.estimateTokenCount(message)
        _inputTokenCount += tokens

        // 添加contents到请求体
        json.put("contents", contentsArray)

        // 添加生成配置
        val generationConfig = JSONObject()

        // 如果启用了思考模式，则为Gemini模型添加特定的`thinkingConfig`参数
        if (enableThinking) {
            val thinkingConfig = JSONObject()
            thinkingConfig.put("includeThoughts", true)
            generationConfig.put("thinkingConfig", thinkingConfig)
            logDebug("已为Gemini模型启用“思考模式”。")
        }

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

    /** 处理API流式响应 */
    private suspend fun processStreamingResponse(
            response: Response,
            streamBuilder: StreamCollector<String>,
            requestId: String,
            onTokensUpdated: suspend (input: Int, output: Int) -> Unit,
            receivedContent: StringBuilder
    ) {
        Log.d(TAG, "开始处理响应流")
        val responseBody = response.body ?: throw IOException("响应为空")
        val reader = responseBody.charStream().buffered()

        // 注意：不再使用fullContent累积所有内容
        var lineCount = 0
        var dataCount = 0
        var jsonCount = 0
        var contentCount = 0

        // 恢复JSON累积逻辑，用于处理分段JSON
        val completeJsonBuilder = StringBuilder()
        var isCollectingJson = false
        var jsonDepth = 0
        var jsonStartSymbol = ' ' // 记录JSON是以 { 还是 [ 开始的

        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    lineCount++
                    // 检查是否已取消
                    if (activeCall?.isCanceled() == true) {
                        return@forEach
                    }

                    // 处理SSE数据
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        dataCount++

                        // 跳过结束标记
                        if (data == "[DONE]") {
                            logDebug("收到流结束标记 [DONE]")
                            return@forEach
                        }

                        try {
                            // 立即解析每个SSE数据行的JSON
                            val json = JSONObject(data)
                            jsonCount++

                            val content = extractContentFromJson(json, requestId, onTokensUpdated)
                            if (content.isNotEmpty()) {
                                contentCount++
                                logDebug("提取SSE内容，长度: ${content.length}")
                                receivedContent.append(content)

                                // 只发送新增的内容
                                streamBuilder.emit(content)
                            }
                        } catch (e: Exception) {
                            logError("解析SSE响应数据失败: ${e.message}", e)
                        }
                    } else if (line.trim().isNotEmpty()) {
                        // 处理可能分段的JSON数据
                        val trimmedLine = line.trim()

                        // 检查是否开始收集JSON
                        if (!isCollectingJson &&
                                        (trimmedLine.startsWith("{") || trimmedLine.startsWith("["))
                        ) {
                            isCollectingJson = true
                            jsonDepth = 0
                            completeJsonBuilder.clear()
                            jsonStartSymbol = trimmedLine[0]
                            logDebug("开始收集JSON，起始符号: $jsonStartSymbol")
                        }

                        if (isCollectingJson) {
                            completeJsonBuilder.append(trimmedLine)

                            // 更新JSON深度
                            for (char in trimmedLine) {
                                if (char == '{' || char == '[') jsonDepth++
                                if (char == '}' || char == ']') jsonDepth--
                            }

                            // 尝试作为完整JSON解析
                            val possibleComplete = completeJsonBuilder.toString()
                            try {
                                if (jsonDepth == 0) {
                                    logDebug("尝试解析完整JSON: ${possibleComplete.take(50)}...")
                                    val jsonContent =
                                            if (jsonStartSymbol == '[') {
                                                JSONArray(possibleComplete)
                                            } else {
                                                JSONObject(possibleComplete)
                                            }

                                    // 解析成功，处理内容
                                    logDebug("成功解析完整JSON，长度: ${possibleComplete.length}")

                                    when (jsonContent) {
                                        is JSONArray -> {
                                            // 处理JSON数组
                                            for (i in 0 until jsonContent.length()) {
                                                val jsonObject = jsonContent.optJSONObject(i)
                                                if (jsonObject != null) {
                                                    jsonCount++
                                                    val content =
                                                            extractContentFromJson(
                                                                    jsonObject,
                                                                    requestId,
                                                                    onTokensUpdated
                                                            )
                                                    if (content.isNotEmpty()) {
                                                        contentCount++
                                                        logDebug(
                                                                "从JSON数组[$i]提取内容，长度: ${content.length}"
                                                        )
                                                        receivedContent.append(content)

                                                        // 只发送这个单独对象产生的内容
                                                        streamBuilder.emit(content)
                                                    }
                                                }
                                            }
                                        }
                                        is JSONObject -> {
                                            // 处理JSON对象
                                            jsonCount++
                                            val content =
                                                    extractContentFromJson(jsonContent, requestId, onTokensUpdated)
                                            if (content.isNotEmpty()) {
                                                contentCount++
                                                logDebug("从JSON对象提取内容，长度: ${content.length}")
                                                receivedContent.append(content)

                                                // 只发送新提取的内容
                                                streamBuilder.emit(content)
                                            }
                                        }
                                    }

                                    // 解析成功后重置收集器
                                    isCollectingJson = false
                                    completeJsonBuilder.clear()
                                }
                            } catch (e: Exception) {
                                // JSON尚未完整，继续收集
                                if (jsonDepth > 0) {
                                    // 仍在收集，这是预期的
                                    logDebug("继续收集JSON，当前深度: $jsonDepth")
                                } else {
                                    // 深度为0但解析失败，可能是无效JSON
                                    logError("JSON解析失败: ${e.message}", e)
                                    isCollectingJson = false
                                    completeJsonBuilder.clear()
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "响应处理完成: 共${lineCount}行, ${jsonCount}个JSON块, 提取${contentCount}个内容块")

            // 检查是否还有未解析完的JSON
            if (isCollectingJson && completeJsonBuilder.isNotEmpty()) {
                try {
                    val finalJson = completeJsonBuilder.toString()
                    Log.d(TAG, "处理最终收集的JSON，长度: ${finalJson.length}")

                    val jsonContent =
                            if (jsonStartSymbol == '[') {
                                JSONArray(finalJson)
                            } else {
                                JSONObject(finalJson)
                            }

                    // 处理内容
                    when (jsonContent) {
                        is JSONArray -> {
                            for (i in 0 until jsonContent.length()) {
                                val jsonObject = jsonContent.optJSONObject(i) ?: continue
                                jsonCount++
                                val content = extractContentFromJson(jsonObject, requestId, onTokensUpdated)
                                if (content.isNotEmpty()) {
                                    contentCount++
                                    logDebug("从最终JSON数组[$i]提取内容，长度: ${content.length}")
                                    receivedContent.append(content)
                                    streamBuilder.emit(content)
                                }
                            }
                        }
                        is JSONObject -> {
                            jsonCount++
                            val content = extractContentFromJson(jsonContent, requestId, onTokensUpdated)
                            if (content.isNotEmpty()) {
                                contentCount++
                                logDebug("从最终JSON对象提取内容，长度: ${content.length}")
                                receivedContent.append(content)
                                streamBuilder.emit(content)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logError("解析最终收集的JSON失败: ${e.message}", e)
                }
            }

            // 确保至少发送一次内容
            if (contentCount == 0) {
                logDebug("未检测到内容，发送空格")
                streamBuilder.emit(" ")
            }
        } catch (e: Exception) {
            logError("处理响应时发生异常: ${e.message}", e)
            throw e
        } finally {
            activeCall = null
        }
    }

    /** 从Gemini响应JSON中提取内容 */
    private suspend fun extractContentFromJson(
        json: JSONObject,
        requestId: String,
        onTokensUpdated: suspend (input: Int, output: Int) -> Unit
    ): String {
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
                val isThought = part.optBoolean("thought", false)

                if (text.isNotEmpty()) {
                    if (isThought) {
                        contentBuilder.append("<think>").append(text).append("</think>")
                        logDebug("提取思考内容，长度=${text.length}")
                    } else {
                        contentBuilder.append(text)
                        logDebug("提取文本，长度=${text.length}")
                    }

                    // 估算token
                    val tokens = ChatUtils.estimateTokenCount(text)
                    _outputTokenCount += tokens
                    onTokensUpdated(_inputTokenCount, _outputTokenCount)
                }
            }

            return contentBuilder.toString()
        } catch (e: Exception) {
            logError("提取内容时发生错误: ${e.message}", e)
            return ""
        }
    }

    /** 获取模型列表 */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        return ModelListFetcher.getModelsList(
                apiKey = apiKey,
                apiEndpoint = apiEndpoint,
                apiProviderType = ApiProviderType.GOOGLE
        )
    }

    override suspend fun testConnection(): Result<String> {
        return try {
            // 通过发送一条短消息来测试完整的连接、认证和API端点。
            // 这比getModelsList更可靠，因为它直接命中了聊天API。
            // 提供一个通用的系统提示，以防止某些需要它的模型出现错误。
            val testHistory = listOf("system" to "You are a helpful assistant.")
            val stream = sendMessage("Hi", testHistory, emptyList(), false, onTokensUpdated = { _, _ -> }, onNonFatalError = {})

            // 消耗流以确保连接有效。
            // 对 "Hi" 的响应应该很短，所以这会很快完成。
            var hasReceivedData = false
            stream.collect {
                hasReceivedData = true
            }

            // 某些情况下，即使连接成功，也可能不会返回任何数据（例如，如果模型只处理了提示而没有生成响应）。
            // 因此，只要不抛出异常，我们就认为连接成功。
            Result.success("连接成功！")
        } catch (e: Exception) {
            logError("连接测试失败", e)
            Result.failure(IOException("连接测试失败: ${e.message}", e))
        }
    }
}

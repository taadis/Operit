package com.ai.assistance.operit.api.chat

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Anthropic Claude API的实现，处理Claude特有的API格式 */
class ClaudeProvider(
        private val apiEndpoint: String,
        private val apiKey: String,
        private val modelName: String,
        private val client: OkHttpClient,
        private val customHeaders: Map<String, String> = emptyMap()
) : AIService {
    // private val client: OkHttpClient = HttpClientFactory.instance

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val ANTHROPIC_VERSION = "2023-06-01" // Claude API版本

    // 当前活跃的Call对象，用于取消流式传输
    private var activeCall: Call? = null
    @Volatile private var isManuallyCancelled = false

    // 添加token计数器
    private var _inputTokenCount = 0
    private var _outputTokenCount = 0

    // 公开token计数
    override val inputTokenCount: Int
        get() = _inputTokenCount
    override val outputTokenCount: Int
        get() = _outputTokenCount

    // 重置token计数
    override fun resetTokenCounts() {
        _inputTokenCount = 0
        _outputTokenCount = 0
    }

    // 取消当前流式传输
    override fun cancelStreaming() {
        isManuallyCancelled = true
        activeCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
                Log.d("AIService", "已取消当前流式传输")
            }
        }
        activeCall = null
    }

    // 创建Claude API请求体
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>> = emptyList(),
            enableThinking: Boolean
    ): RequestBody {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", true)

        // 添加已启用的模型参数
        addParameters(jsonObject, modelParameters)

        // Claude使用messages格式，但与OpenAI稍有不同
        val messagesArray = JSONArray()

        // 提取系统消息
        val systemMessages = chatHistory.filter { it.first.equals("system", ignoreCase = true) }
        var systemPrompt: String? = null

        if (systemMessages.isNotEmpty()) {
            // Claude只支持一个系统消息，所以我们需要合并所有系统消息
            systemPrompt = systemMessages.joinToString("\n\n") { it.second }
        }

        // 处理用户和助手消息
        val standardizedHistory =
                ChatUtils.mapChatHistoryToStandardRoles(chatHistory).filter {
                    it.first != "system"
                } // 系统消息已单独处理

        // 添加历史消息
        for ((role, content) in standardizedHistory) {
            val messageObject = JSONObject()

            // Claude使用"assistant"而不是"model"
            val claudeRole =
                    when (role) {
                        "assistant" -> "assistant"
                        else -> "user"
                    }

            messageObject.put("role", claudeRole)

            // 创建消息内容
            val contentObject = JSONObject()
            contentObject.put("type", "text")
            contentObject.put("text", content)

            // Claude API使用"content"数组
            val contentArray = JSONArray()
            contentArray.put(contentObject)
            messageObject.put("content", contentArray)

            messagesArray.put(messageObject)

            // 计算输入token
            _inputTokenCount += ChatUtils.estimateTokenCount(content)
        }

        // 添加当前用户消息
        val lastMessageIndex = messagesArray.length() - 1
        val lastMessageRole =
                if (lastMessageIndex >= 0) {
                    messagesArray.getJSONObject(lastMessageIndex).getString("role")
                } else null

        if (lastMessageRole != "user") {
            // 添加新的用户消息
            val userMessage = JSONObject()
            userMessage.put("role", "user")

            val userContentObject = JSONObject()
            userContentObject.put("type", "text")
            userContentObject.put("text", message)

            val userContentArray = JSONArray()
            userContentArray.put(userContentObject)
            userMessage.put("content", userContentArray)

            messagesArray.put(userMessage)

            // 计算当前消息的token
            _inputTokenCount += ChatUtils.estimateTokenCount(message)
        } else {
            // 如果上一条消息也是用户，将当前消息与上一条合并
            Log.d("AIService", "合并连续的用户消息")
            val lastMessage = messagesArray.getJSONObject(lastMessageIndex)
            val lastContentArray = lastMessage.getJSONArray("content")
            val lastContentObject = lastContentArray.getJSONObject(0)
            val existingText = lastContentObject.getString("text")

            lastContentObject.put("text", existingText + "\n" + message)

            // 重新计算合并后消息的token
            _inputTokenCount += ChatUtils.estimateTokenCount(message)
        }

        jsonObject.put("messages", messagesArray)

        // Claude对系统消息的处理有所不同，它使用system参数
        if (systemPrompt != null) {
            jsonObject.put("system", systemPrompt)
            _inputTokenCount += ChatUtils.estimateTokenCount(systemPrompt)
        }

        // 添加extended thinking支持
        if (enableThinking) {
            val thinkingObject = JSONObject()
            thinkingObject.put("type", "enabled")
            jsonObject.put("thinking", thinkingObject)
            Log.d("AIService", "启用Claude的extended thinking功能")
        }

        Log.d("AIService", "Claude请求体: ${jsonObject.toString(4)}")
        return jsonObject.toString().toRequestBody(JSON)
    }

    // 添加模型参数
    private fun addParameters(jsonObject: JSONObject, modelParameters: List<ModelParameter<*>>) {
        for (param in modelParameters) {
            if (param.isEnabled) {
                when (param.apiName) {
                    "temperature" ->
                            jsonObject.put("temperature", (param.currentValue as Number).toFloat())
                    "top_p" -> jsonObject.put("top_p", (param.currentValue as Number).toFloat())
                    "top_k" -> jsonObject.put("top_k", (param.currentValue as Number).toInt())
                    "max_tokens" ->
                            jsonObject.put("max_tokens", (param.currentValue as Number).toInt())
                    "max_tokens_to_sample" ->
                            jsonObject.put(
                                    "max_tokens_to_sample",
                                    (param.currentValue as Number).toInt()
                            )
                    "stop_sequences" -> {
                        // 处理停止序列
                        val stopSequences = param.currentValue as? List<*>
                        if (stopSequences != null) {
                            val stopArray = JSONArray()
                            stopSequences.forEach { stopArray.put(it.toString()) }
                            jsonObject.put("stop_sequences", stopArray)
                        }
                    }
                    // 忽略thinking相关参数，因为它们会在单独的部分处理
                    "thinking",
                    "budget_tokens" -> {
                        // 忽略，在特定部分处理
                    }
                    else -> {
                        // 添加其他Claude特定参数
                        when (param.valueType) {
                            com.ai.assistance.operit.data.model.ParameterValueType.INT ->
                                    jsonObject.put(param.apiName, param.currentValue as Int)
                            com.ai.assistance.operit.data.model.ParameterValueType.FLOAT ->
                                    jsonObject.put(param.apiName, param.currentValue as Float)
                            com.ai.assistance.operit.data.model.ParameterValueType.STRING ->
                                    jsonObject.put(param.apiName, param.currentValue as String)
                            com.ai.assistance.operit.data.model.ParameterValueType.BOOLEAN ->
                                    jsonObject.put(param.apiName, param.currentValue as Boolean)
                        }
                    }
                }
                Log.d("AIService", "添加Claude参数 ${param.apiName} = ${param.currentValue}")
            }
        }
    }

    // 创建请求
    private fun createRequest(requestBody: RequestBody): Request {
        val builder =
                Request.Builder()
                        .url(apiEndpoint)
                        .post(requestBody)
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", ANTHROPIC_VERSION)
                        .addHeader("Content-Type", "application/json")

        // 添加自定义请求头
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }

        val request = builder.build()
        Log.d("AIService", "Claude请求头: \n${request.headers}")
        return request
    }

    override suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            onTokensUpdated: suspend (input: Int, output: Int) -> Unit,
            onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        isManuallyCancelled = false
        // 重置token计数
        _inputTokenCount = 0
        _outputTokenCount = 0

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        // 用于保存已接收到的内容，以便在重试时使用
        val receivedContent = StringBuilder()

        Log.d("AIService", "准备连接到Claude AI服务...")
        while (retryCount < maxRetries) {
            try {
                // 如果是重试，我们需要构建一个新的请求
                val currentMessage: String
                val currentHistory: List<Pair<String, String>>

                if (retryCount > 0 && receivedContent.isNotEmpty()) {
                    Log.d("AIService", "【Claude 重试】准备续写请求，已接收内容: ${receivedContent.length}")
                    // Claude 对续写指令可能需要不同的优化，这里使用一个通用的方式
                    currentMessage = message + "\n\n[SYSTEM NOTE] The previous response was cut off by a network error. You MUST continue from the exact point of interruption. Do not repeat any content. If you were in the middle of a code block or XML tag, complete it. Just output the text that would have come next."
                    val resumeHistory = chatHistory.toMutableList()
                    resumeHistory.add("assistant" to receivedContent.toString())
                    currentHistory = resumeHistory
                } else {
                    currentMessage = message
                    currentHistory = chatHistory
                }

                val requestBody = createRequestBody(currentMessage, currentHistory, modelParameters, enableThinking)
                onTokensUpdated(_inputTokenCount, _outputTokenCount)
                val request = createRequest(requestBody)

                // 创建Call对象并保存到activeCall中，以便可以取消
                val call = client.newCall(request)
                activeCall = call

                Log.d("AIService", "正在建立连接...")
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error details"
                        throw IOException("API请求失败，状态码: ${response.code}，错误信息: $errorBody")
                    }

                    Log.d("AIService", "连接成功，等待响应...")
                    val responseBody = response.body ?: throw IOException("API响应为空")
                    val reader = responseBody.charStream().buffered()
                    var wasCancelled = false

                    try {
                        reader.useLines { lines ->
                            lines.forEach { line ->
                                // 如果call已被取消，提前退出
                                if (activeCall?.isCanceled() == true) {
                                    Log.d("AIService", "流式传输已被取消，提前退出处理")
                                    wasCancelled = true
                                    return@forEach
                                }

                                if (line.startsWith("data: ")) {
                                    val data = line.substring(6).trim()
                                    if (data == "[DONE]") {
                                        return@forEach
                                    }

                                    try {
                                        val jsonResponse = JSONObject(data)

                                        // Claude API在响应中包含content
                                        val type = jsonResponse.optString("type", "")
                                        
                                        // 根据type处理不同的事件
                                        when (type) {
                                            "content_block_delta" -> {
                                                val delta = jsonResponse.optJSONObject("delta")
                                                if (delta != null) {
                                                    val content = delta.optString("text", "")
                                                    if (content.isNotEmpty()) {
                                                        _outputTokenCount += ChatUtils.estimateTokenCount(content)
                                                        onTokensUpdated(_inputTokenCount, _outputTokenCount)
                                                        emit(content)
                                                        receivedContent.append(content)
                                                    }
                                                }
                                            }
                                            "message_delta" -> {
                                                 //  可以处理stop_reason等
                                            }
                                            "content_block_stop" -> {
                                                // 块停止
                                            }
                                        }

                                    } catch (e: Exception) {
                                        // 忽略解析错误，继续处理下一行
                                        Log.w("AIService", "JSON解析错误: ${e.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (isManuallyCancelled) {
                            Log.d("AIService", "【Claude】流式传输已被用户取消，停止后续操作。")
                            throw UserCancellationException("请求已被用户取消", e)
                        }
                        // 捕获IO异常，可能是由于取消Call导致的
                        if (activeCall?.isCanceled() == true) {
                            Log.d("AIService", "流式传输已被取消，处理IO异常")
                            wasCancelled = true
                        } else {
                             Log.e("AIService", "【Claude】流式读取时发生IO异常，准备重试", e)
                            lastException = e
                            throw e
                        }
                    }

                    // 清理活跃Call引用
                    activeCall = null
                }

                // 成功处理后，返回
                 Log.d( "AIService", "【Claude】请求成功完成")
                return@stream
            } catch (e: SocketTimeoutException) {
                if (isManuallyCancelled) {
                    Log.d("AIService", "【Claude】请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    Log.e("AIService", "【Claude】连接超时且达到最大重试次数", e)
                    throw IOException("AI响应获取失败，连接超时且已达最大重试次数: ${e.message}")
                }
                Log.w("AIService", "【Claude】连接超时，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络超时，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: UnknownHostException) {
                if (isManuallyCancelled) {
                    Log.d("AIService", "【Claude】请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    Log.e("AIService", "【Claude】无法解析主机且达到最大重试次数", e)
                throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
                }
                Log.w("AIService", "【Claude】无法解析主机，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络不稳定，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: IOException) {
                if (isManuallyCancelled) {
                    Log.d("AIService", "【Claude】请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if(retryCount >= maxRetries) {
                    Log.e("AIService", "【Claude】达到最大重试次数", e)
                    throw IOException("AI响应获取失败，已达最大重试次数: ${e.message}")
                }
                Log.w("AIService", "【Claude】网络中断，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络中断，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            }
            catch (e: Exception) {
                if (isManuallyCancelled) {
                    Log.d("AIService", "【Claude】请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if(retryCount >= maxRetries) {
                    Log.e("AIService", "【Claude】发生未知异常且达到最大重试次数", e)
                    throw IOException("AI响应获取失败: ${e.message}")
                }
                Log.e("AIService", "【Claude】连接失败，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【连接失败，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            }
        }
        
        Log.e("AIService", "【Claude】重试失败，请检查网络连接", lastException)
        // 所有重试都失败
        throw IOException("连接超时或中断，已重试 $maxRetries 次: ${lastException?.message}")
    }

    /**
     * 获取模型列表 注意：此方法直接调用ModelListFetcher获取模型列表
     * @return 模型列表结果
     */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        // 调用ModelListFetcher获取模型列表
        return ModelListFetcher.getModelsList(
                apiKey = apiKey,
                apiEndpoint = apiEndpoint,
                apiProviderType = ApiProviderType.ANTHROPIC
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
            stream.collect { _ -> }

            Result.success("连接成功！")
        } catch (e: Exception) {
            Log.e("AIService", "连接测试失败", e)
            Result.failure(IOException("连接测试失败: ${e.message}", e))
        }
    }
}

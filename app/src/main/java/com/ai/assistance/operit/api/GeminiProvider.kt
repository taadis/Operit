package com.ai.assistance.operit.api

import android.util.Log
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.ChatUtils
import java.io.IOException
import java.net.SocketTimeoutException
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

/** Google Gemini API的实现，支持标准Gemini接口及通过兼容层的OpenAI格式 */
class GeminiProvider(
        private val apiEndpoint: String,
        private val apiKey: String,
        private val modelName: String
) : AIService {
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 当前活跃的Call对象，用于取消流式传输
    private var activeCall: Call? = null

    // 添加token计数器
    private var _inputTokenCount = 0
    private var _outputTokenCount = 0

    // 公开token计数
    override val inputTokenCount: Int
        get() = _inputTokenCount
    override val outputTokenCount: Int
        get() = _outputTokenCount

    // Token计数逻辑
    private fun estimateTokenCount(text: String): Int {
        // 简单估算：中文每个字约1.5个token，英文每4个字符约1个token
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount

        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toInt()
    }

    // 重置token计数
    override fun resetTokenCounts() {
        _inputTokenCount = 0
        _outputTokenCount = 0
    }

    // 取消当前流式传输
    override fun cancelStreaming() {
        activeCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
                Log.d("AIService", "已取消当前流式传输")
            }
        }
        activeCall = null
    }

    // 解析服务器返回的内容，处理<think>标签
    private fun parseResponse(content: String): Pair<String, String?> {
        val thinkStartTag = "<think>"
        val thinkEndTag = "</think>"

        val startIndex = content.indexOf(thinkStartTag)
        val endIndex = content.lastIndexOf(thinkEndTag)

        return if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            // 提取思考内容和主要内容
            val thinkContent = content.substring(startIndex + thinkStartTag.length, endIndex).trim()
            val mainContent = content.substring(endIndex + thinkEndTag.length).trim()
            Pair(mainContent, thinkContent)
        } else if (startIndex != -1 && endIndex == -1) {
            Pair("", content.substring(startIndex + thinkStartTag.length).trim())
        } else {
            // 没有思考内容，返回原始内容
            Pair(content, null)
        }
    }

    // 创建Gemini API的请求体
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>> = emptyList()
    ): RequestBody {
        // 检测API端点是否使用OpenAI兼容模式
        val isOpenAICompatMode = apiEndpoint.contains("/openai/")

        return if (isOpenAICompatMode) {
            createOpenAICompatRequestBody(message, chatHistory, modelParameters)
        } else {
            createNativeGeminiRequestBody(message, chatHistory, modelParameters)
        }
    }

    // 创建原生Gemini API请求体
    private fun createNativeGeminiRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>
    ): RequestBody {
        val jsonObject = JSONObject()

        // 创建contents数组
        val contentsArray = JSONArray()

        // 首先添加历史消息
        if (chatHistory.isNotEmpty()) {
            // 使用工具函数统一转换角色格式，并确保没有连续的相同角色
            val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(chatHistory)

            // 防止连续相同角色消息
            val filteredHistory = mutableListOf<Pair<String, String>>()
            var lastRole: String? = null

            for ((role, content) in standardizedHistory) {
                // 如果是系统消息，添加为systemInstruction
                if (role == "system") {
                    // 系统消息会在后面单独处理
                    continue
                }

                // 如果当前消息角色与上一个相同，跳过
                if (role == lastRole) {
                    Log.d("AIService", "跳过连续相同角色的消息: $role")
                    continue
                }

                filteredHistory.add(Pair(role, content))
                lastRole = role

                // 添加历史消息到contents数组
                val contentObject = JSONObject()
                val partsArray = JSONArray()
                val textPart = JSONObject()
                textPart.put("text", content)
                partsArray.put(textPart)

                contentObject.put("role", if (role == "assistant") "model" else role)
                contentObject.put("parts", partsArray)
                contentsArray.put(contentObject)

                // 计算输入token
                _inputTokenCount += estimateTokenCount(content)
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

        // 计算当前消息的token
        _inputTokenCount += estimateTokenCount(message)

        // 添加内容到请求体
        jsonObject.put("contents", contentsArray)

        // 添加生成配置
        val generationConfig = JSONObject()

        // 添加已启用的模型参数
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
                    else -> {
                        // 其他Gemini特定参数
                        generationConfig.put(param.apiName, param.currentValue)
                    }
                }
                Log.d("AIService", "添加参数 ${param.apiName} = ${param.currentValue}")
            }
        }

        // 设置流式响应
        jsonObject.put("generationConfig", generationConfig)
        jsonObject.put("stream", true)

        // 提取系统消息作为systemInstruction
        val systemMessages = chatHistory.filter { it.first.equals("system", ignoreCase = true) }
        if (systemMessages.isNotEmpty()) {
            val combinedSystemMessage = systemMessages.joinToString("\n") { it.second }

            val systemInstructionObject = JSONObject()
            val systemPartsArray = JSONArray()
            val systemTextPart = JSONObject()
            systemTextPart.put("text", combinedSystemMessage)
            systemPartsArray.put(systemTextPart)

            systemInstructionObject.put("parts", systemPartsArray)
            jsonObject.put("systemInstruction", systemInstructionObject)

            // 计算系统消息token
            _inputTokenCount += estimateTokenCount(combinedSystemMessage)
        }

        // 使用分块日志函数记录完整的请求体
        Log.d("AIService", "Gemini 请求体: ${jsonObject.toString(4)}")
        return jsonObject.toString().toRequestBody(JSON)
    }

    // 创建OpenAI兼容模式的请求体
    private fun createOpenAICompatRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>
    ): RequestBody {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", true)

        // 添加已启用的模型参数
        for (param in modelParameters) {
            if (param.isEnabled) {
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
                Log.d("AIService", "添加OpenAI兼容参数 ${param.apiName} = ${param.currentValue}")
            }
        }

        // 创建消息数组
        val messagesArray = JSONArray()

        // 添加历史消息
        if (chatHistory.isNotEmpty()) {
            val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(chatHistory)

            // 防止连续相同角色消息
            val filteredHistory = mutableListOf<Pair<String, String>>()
            var lastRole: String? = null

            for ((role, content) in standardizedHistory) {
                // 如果当前消息角色与上一个相同，跳过（除了system消息）
                if (role == lastRole && role != "system") {
                    Log.d("AIService", "跳过连续相同角色的消息: $role")
                    continue
                }

                filteredHistory.add(Pair(role, content))
                lastRole = role
            }

            // 将过滤后的历史添加到消息数组
            for ((role, content) in filteredHistory) {
                val historyMessage = JSONObject()
                historyMessage.put("role", role)
                historyMessage.put("content", content)
                messagesArray.put(historyMessage)

                // 计算输入token
                _inputTokenCount += estimateTokenCount(content)
            }
        }

        // 添加当前消息
        val lastMessageRole =
                if (messagesArray.length() > 0) {
                    messagesArray.getJSONObject(messagesArray.length() - 1).getString("role")
                } else null

        if (lastMessageRole != "user") {
            val messageObject = JSONObject()
            messageObject.put("role", "user")
            messageObject.put("content", message)
            messagesArray.put(messageObject)

            // 计算当前消息的token
            _inputTokenCount += estimateTokenCount(message)
        } else {
            // 如果上一条消息也是用户，将当前消息与上一条合并
            Log.d("AIService", "合并连续的用户消息")
            val lastMessage = messagesArray.getJSONObject(messagesArray.length() - 1)
            val combinedContent = lastMessage.getString("content") + "\n" + message
            lastMessage.put("content", combinedContent)

            // 重新计算合并后消息的token
            _inputTokenCount += estimateTokenCount(message)
        }

        jsonObject.put("messages", messagesArray)

        // 添加推理努力度参数（特定于Gemini的"thinking"功能）
        val reasoningEffort = modelParameters.find { it.apiName == "reasoning_effort" }
        if (reasoningEffort != null && reasoningEffort.isEnabled) {
            val value = reasoningEffort.currentValue as? String
            if (!value.isNullOrEmpty()) {
                jsonObject.put("reasoning_effort", value)
                Log.d("AIService", "添加推理努力度参数: $value")
            }
        }

        Log.d("AIService", "OpenAI兼容请求体: ${jsonObject.toString(4)}")
        return jsonObject.toString().toRequestBody(JSON)
    }

    // 创建请求
    private fun createRequest(requestBody: RequestBody): Request {
        // 检测API端点是否使用OpenAI兼容模式
        val isOpenAICompatMode = apiEndpoint.contains("/openai/")

        val builder = Request.Builder().url(apiEndpoint).post(requestBody)

        if (isOpenAICompatMode) {
            // OpenAI兼容模式使用Bearer认证
            builder.addHeader("Authorization", "Bearer $apiKey")
        } else {
            // 原生Gemini API使用API密钥参数
            // 注意：这通常是通过URL参数而不是头部提供，但这里我们仍然添加为头部以保持一致性
            builder.addHeader("x-goog-api-key", apiKey)
        }

        builder.addHeader("Content-Type", "application/json")

        return builder.build()
    }

    // 处理响应
    private suspend fun processResponse(
            response: Response,
            onPartialResponse: (content: String, thinking: String?) -> Unit,
            onComplete: () -> Unit
    ) {
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error details"
            throw IOException("API请求失败，状态码: ${response.code}，错误信息: $errorBody")
        }

        val responseBody = response.body ?: throw IOException("API响应为空")
        val reader = responseBody.charStream().buffered()
        val isOpenAICompatMode = response.request.url.toString().contains("/openai/")

        var currentContent = StringBuilder()
        var currentThinking = StringBuilder()
        var isFirstResponse = true
        var wasCancelled = false // 添加取消标志

        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    // 如果call已被取消，提前退出
                    if (activeCall?.isCanceled() == true) {
                        Log.d("AIService", "流式传输已被取消，提前退出处理")
                        wasCancelled = true // 设置取消标志
                        return@forEach
                    }

                    // 处理Server-Sent Events (SSE)格式的响应
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        if (data != "[DONE]") {
                            try {
                                val jsonResponse = JSONObject(data)

                                if (isOpenAICompatMode) {
                                    // 处理OpenAI兼容格式
                                    processOpenAICompatResponse(
                                            jsonResponse,
                                            currentContent,
                                            currentThinking,
                                            onPartialResponse,
                                            isFirstResponse
                                    )
                                } else {
                                    // 处理原生Gemini API格式
                                    processNativeGeminiResponse(
                                            jsonResponse,
                                            currentContent,
                                            currentThinking,
                                            onPartialResponse,
                                            isFirstResponse
                                    )
                                }

                                // 当收到第一个有效内容时，标记不再是首次响应
                                if (isFirstResponse) {
                                    isFirstResponse = false
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误，继续处理下一行
                                Log.d("AIService", "JSON解析错误: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // 捕获IO异常，可能是由于取消Call导致的
            if (activeCall?.isCanceled() == true) {
                Log.d("AIService", "流式传输已被取消，处理IO异常")
                wasCancelled = true // 设置取消标志
            } else {
                throw e
            }
        }

        // 只有在未被取消时才调用完成回调
        if (!wasCancelled && activeCall?.isCanceled() != true) {
            Log.d("AIService", "处理完成，调用完成回调")
            onComplete()
        } else {
            Log.d("AIService", "流被取消，跳过完成回调")
        }

        // 清理活跃Call引用
        activeCall = null
    }

    // 处理OpenAI兼容格式的响应
    private fun processOpenAICompatResponse(
            jsonResponse: JSONObject,
            currentContent: StringBuilder,
            currentThinking: StringBuilder,
            onPartialResponse: (content: String, thinking: String?) -> Unit,
            isFirstResponse: Boolean
    ) {
        val choices = jsonResponse.optJSONArray("choices")

        if (choices != null && choices.length() > 0) {
            val choice = choices.getJSONObject(0)

            // 处理delta格式（流式响应）
            val delta = choice.optJSONObject("delta")
            if (delta != null) {
                // 尝试获取常规内容
                val content = delta.optString("content", "")

                // 更新内容
                if (content.isNotEmpty() && content != "null") {
                    currentContent.append(content)

                    // 计算输出tokens
                    _outputTokenCount += estimateTokenCount(content)

                    // 解析内容，检查<think>标签
                    val (mainContent, thinkContent) = parseResponse(currentContent.toString())

                    // 确定最终输出内容
                    val finalContent =
                            if (mainContent.isNotEmpty()) mainContent else currentContent.toString()

                    // 确定思考内容
                    val finalThinking = thinkContent

                    onPartialResponse(finalContent, finalThinking)
                }
            }
        }
    }

    // 处理原生Gemini API格式的响应
    private fun processNativeGeminiResponse(
            jsonResponse: JSONObject,
            currentContent: StringBuilder,
            currentThinking: StringBuilder,
            onPartialResponse: (content: String, thinking: String?) -> Unit,
            isFirstResponse: Boolean
    ) {
        val candidates = jsonResponse.optJSONArray("candidates")

        if (candidates != null && candidates.length() > 0) {
            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")

            if (content != null) {
                val parts = content.optJSONArray("parts")

                if (parts != null && parts.length() > 0) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val text = part.optString("text", "")

                        if (text.isNotEmpty()) {
                            // 更新内容
                            currentContent.append(text)

                            // 计算输出tokens
                            _outputTokenCount += estimateTokenCount(text)
                        }
                    }

                    // 检查有无thinking内容（Gemini特有）
                    val isThinking = candidate.optBoolean("isThinking", false)
                    val reasoning = content.optString("reasoning", "")

                    if (isThinking || reasoning.isNotEmpty()) {
                        if (reasoning.isNotEmpty()) {
                            currentThinking.append(reasoning)
                        } else {
                            // 尝试从content中提取thinking内容
                            val (mainContent, thinkContent) =
                                    parseResponse(currentContent.toString())
                            if (thinkContent != null) {
                                currentThinking.append(thinkContent)
                            }
                        }
                    }

                    // 解析内容，检查<think>标签
                    val (mainContent, thinkContent) = parseResponse(currentContent.toString())

                    // 确定最终输出内容
                    val finalContent =
                            if (mainContent.isNotEmpty()) mainContent else currentContent.toString()

                    // 确定思考内容，优先使用Gemini专有的reasoning字段
                    val finalThinking =
                            if (currentThinking.isNotEmpty()) currentThinking.toString()
                            else thinkContent

                    onPartialResponse(finalContent, finalThinking)
                }
            }
        }
    }

    override suspend fun sendMessage(
            message: String,
            onPartialResponse: (content: String, thinking: String?) -> Unit,
            chatHistory: List<Pair<String, String>>,
            onComplete: () -> Unit,
            onConnectionStatus: ((status: String) -> Unit)?,
            modelParameters: List<ModelParameter<*>>
    ) =
            withContext(Dispatchers.IO) {
                // 重置token计数
                _inputTokenCount = 0
                _outputTokenCount = 0

                val maxRetries = 3
                var retryCount = 0
                var lastException: Exception? = null

                val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(chatHistory)
                val requestBody = createRequestBody(message, standardizedHistory, modelParameters)
                val request = createRequest(requestBody)

                onConnectionStatus?.invoke("准备连接到Gemini AI服务...")
                while (retryCount < maxRetries) {
                    try {
                        // 创建Call对象并保存到activeCall中，以便可以取消
                        val call = client.newCall(request)
                        activeCall = call

                        onConnectionStatus?.invoke("正在建立连接...")
                        call.execute().use { response ->
                            onConnectionStatus?.invoke("连接成功，等待响应...")
                            processResponse(response, onPartialResponse, onComplete)
                        }

                        // 成功处理后，清空activeCall
                        activeCall = null

                        // 成功处理，返回
                        return@withContext
                    } catch (e: SocketTimeoutException) {
                        lastException = e
                        retryCount++
                        // 通知正在重试
                        onConnectionStatus?.invoke("连接超时，正在进行第 $retryCount 次重试...")
                        onPartialResponse("", "连接超时，正在进行第 $retryCount 次重试...")
                        // 指数退避重试
                        delay(1000L * retryCount)
                    } catch (e: UnknownHostException) {
                        onConnectionStatus?.invoke("无法连接到服务器，请检查网络")
                        throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
                    } catch (e: Exception) {
                        onConnectionStatus?.invoke("连接失败: ${e.message}")
                        throw IOException(
                                "AI响应获取失败: ${e.message} ${e.stackTrace.joinToString("\n")}"
                        )
                    }
                }

                onConnectionStatus?.invoke("重试失败，请检查网络连接")
                // 所有重试都失败
                throw IOException("连接超时，已重试 $maxRetries 次: ${lastException?.message}")
            }
}

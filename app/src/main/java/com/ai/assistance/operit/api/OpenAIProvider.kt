package com.ai.assistance.operit.api

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
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

/** OpenAI API格式的实现，支持标准OpenAI接口和兼容此格式的其他提供商 */
class OpenAIProvider(
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

    /**
     * 获取模型列表 注意：此方法直接调用ModelListFetcher获取模型列表
     * @return 模型列表结果
     */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        // 调用ModelListFetcher获取模型列表
        return ModelListFetcher.getModelsList(
                apiKey = apiKey,
                apiEndpoint = apiEndpoint,
                apiProviderType = ApiProviderType.OPENAI // 默认为OpenAI类型
        )
    }

    // 解析服务器返回的内容，不再需要处理<think>标签
    private fun parseResponse(content: String): String {
        return content
    }

    // 创建请求体
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>> = emptyList()
    ): RequestBody {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", true) // 启用流式响应

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
                Log.d("AIService", "添加参数 ${param.apiName} = ${param.currentValue}")
            }
        }

        // 创建消息数组，包含聊天历史
        val messagesArray = JSONArray()

        // 添加聊天历史
        if (chatHistory.isNotEmpty()) {

            // 添加历史消息 - 使用工具函数统一转换角色格式，并确保没有连续的相同角色
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

        // 添加当前消息，确保与上一条消息角色不同
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
            if (lastMessage.getString("content") != message) {
                val combinedContent = lastMessage.getString("content") + "\n" + message
                lastMessage.put("content", combinedContent)

                // 重新计算合并后消息的token
                _inputTokenCount += estimateTokenCount(message)
            }
        }

        jsonObject.put("messages", messagesArray)

        // 使用分块日志函数记录完整的请求体
        logLargeString("AIService", jsonObject.toString(4), "请求体: ")
        return jsonObject.toString().toRequestBody(JSON)
    }

    // 创建请求
    private fun createRequest(requestBody: RequestBody): Request {
        return Request.Builder()
                .url(apiEndpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
    }

    override suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>
    ): Stream<String> = stream {
        // 重置token计数
        _inputTokenCount = 0
        _outputTokenCount = 0

        Log.d(
                "AIService",
                "【发送消息】开始处理sendMessage请求，消息长度: ${message.length}，历史记录数量: ${chatHistory.size}"
        )

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        Log.d("AIService", "【发送消息】标准化聊天历史记录，原始大小: ${chatHistory.size}")
        val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(chatHistory)
        Log.d("AIService", "【发送消息】历史记录标准化完成，标准化后大小: ${standardizedHistory.size}")

        Log.d(
                "AIService",
                "【发送消息】准备构建请求体，模型参数数量: ${modelParameters.size}，已启用参数: ${modelParameters.count { it.isEnabled }}"
        )
        val requestBody = createRequestBody(message, standardizedHistory, modelParameters)
        val request = createRequest(requestBody)
        Log.d("AIService", "【发送消息】请求体构建完成，目标模型: $modelName，API端点: $apiEndpoint")

        Log.d("AIService", "【发送消息】准备连接到AI服务...")
        while (retryCount < maxRetries) {
            try {
                // 创建Call对象并保存到activeCall中，以便可以取消
                val call = client.newCall(request)
                activeCall = call

                Log.d("AIService", "【发送消息】正在建立连接到服务器...")

                // 确保在IO线程执行网络请求
                Log.d("AIService", "【发送消息】切换到IO线程执行网络请求")
                val response = withContext(Dispatchers.IO) { call.execute() }

                try {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error details"
                        Log.e("AIService", "【发送消息】API请求失败，状态码: ${response.code}，错误信息: $errorBody")
                        throw IOException("API请求失败，状态码: ${response.code}，错误信息: $errorBody")
                    }

                    Log.d("AIService", "【发送消息】连接成功(状态码: ${response.code})，准备处理流式响应...")
                    val responseBody = response.body ?: throw IOException("API响应为空")

                    // 在IO线程中读取响应
                    withContext(Dispatchers.IO) {
                        Log.d("AIService", "【发送消息】开始读取流式响应")
                        val reader = responseBody.charStream().buffered()
                        var currentContent = StringBuilder()
                        var isFirstResponse = true
                        var wasCancelled = false
                        var chunkCount = 0
                        var lastLogTime = System.currentTimeMillis()

                        // 跟踪思考内容的状态
                        var isInReasoningMode = false
                        var hasEmittedThinkStart = false

                        try {
                            reader.useLines { lines ->
                                lines.forEach { line ->
                                    // 如果call已被取消，提前退出
                                    if (activeCall?.isCanceled() == true) {
                                        Log.d("AIService", "【发送消息】流式传输已被取消，提前退出处理")
                                        wasCancelled = true
                                        return@forEach
                                    }

                                    if (line.startsWith("data: ")) {
                                        val data = line.substring(6).trim()
                                        if (data != "[DONE]") {
                                            chunkCount++
                                            // 每10个块或500ms记录一次日志
                                            val currentTime = System.currentTimeMillis()
                                            if (chunkCount % 10 == 0 ||
                                                            currentTime - lastLogTime > 500
                                            ) {
                                                // Log.d("AIService", "【发送消息】已处理数据块: $chunkCount")
                                                lastLogTime = currentTime
                                            }

                                            try {
                                                val jsonResponse = JSONObject(data)
                                                val choices = jsonResponse.getJSONArray("choices")

                                                if (choices.length() > 0) {
                                                    val choice = choices.getJSONObject(0)

                                                    // 处理delta格式（流式响应）
                                                    val delta = choice.optJSONObject("delta")
                                                    if (delta != null) {
                                                        // 检查是否有思考内容
                                                        val reasoningContent =
                                                                delta.optString(
                                                                        "reasoning_content",
                                                                        ""
                                                                )
                                                        val regularContent =
                                                                delta.optString("content", "")

                                                        // 处理思考内容
                                                        if (reasoningContent.isNotEmpty() &&
                                                                        reasoningContent != "null"
                                                        ) {
                                                            if (!isInReasoningMode) {
                                                                isInReasoningMode = true
                                                                // 第一次发现思考内容，发射<think>开始标签
                                                                if (!hasEmittedThinkStart) {
                                                                    emit("<think>")
                                                                    hasEmittedThinkStart = true
                                                                }
                                                            }
                                                            // 发射思考内容
                                                            emit(reasoningContent)
                                                            _outputTokenCount +=
                                                                    estimateTokenCount(
                                                                            reasoningContent
                                                                    )
                                                        }
                                                        // 处理常规内容
                                                        else if (regularContent.isNotEmpty() &&
                                                                        regularContent != "null"
                                                        ) {
                                                            // 如果之前在思考模式，现在切换到了常规内容，需要关闭思考标签
                                                            if (isInReasoningMode) {
                                                                isInReasoningMode = false
                                                                emit("</think>")
                                                            }

                                                            // 当收到第一个有效内容时，标记不再是首次响应
                                                            if (isFirstResponse) {
                                                                isFirstResponse = false
                                                                Log.d(
                                                                        "AIService",
                                                                        "【发送消息】收到首个有效内容片段"
                                                                )
                                                            }

                                                            // 更新内容
                                                            currentContent.append(regularContent)

                                                            // 计算输出tokens
                                                            _outputTokenCount +=
                                                                    estimateTokenCount(
                                                                            regularContent
                                                                    )

                                                            // 发射内容
                                                            emit(regularContent)
                                                        }
                                                    }
                                                    // 处理message格式（非流式响应）
                                                    else {
                                                        val message =
                                                                choice.optJSONObject("message")
                                                        if (message != null) {
                                                            val reasoningContent =
                                                                    message.optString(
                                                                            "reasoning_content",
                                                                            ""
                                                                    )
                                                            val regularContent =
                                                                    message.optString("content", "")

                                                            // 先处理思考内容（如果有）
                                                            if (reasoningContent.isNotEmpty() &&
                                                                            reasoningContent !=
                                                                                    "null"
                                                            ) {
                                                                emit(
                                                                        "<think>" +
                                                                                reasoningContent +
                                                                                "</think>"
                                                                )
                                                                _outputTokenCount +=
                                                                        estimateTokenCount(
                                                                                reasoningContent
                                                                        )
                                                            }

                                                            // 然后处理常规内容
                                                            if (regularContent.isNotEmpty() &&
                                                                            regularContent != "null"
                                                            ) {
                                                                emit(regularContent)
                                                                _outputTokenCount +=
                                                                        estimateTokenCount(
                                                                                regularContent
                                                                        )
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                // 忽略解析错误，继续处理下一行
                                                Log.w("AIService", "【发送消息】JSON解析错误: ${e.message}")
                                            }
                                        } else {
                                            // 收到流结束标记，如果还在思考模式，确保关闭思考标签
                                            if (isInReasoningMode) {
                                                isInReasoningMode = false
                                                emit("</think>")
                                            }
                                            Log.d("AIService", "【发送消息】收到流结束标记[DONE]")
                                        }
                                    }
                                }
                            }
                            Log.d(
                                    "AIService",
                                    "【发送消息】响应流处理完成，总块数: $chunkCount，输出token: $_outputTokenCount"
                            )
                        } catch (e: IOException) {
                            // 捕获IO异常，可能是由于取消Call导致的
                            if (activeCall?.isCanceled() == true) {
                                Log.d("AIService", "【发送消息】流式传输已被取消，处理IO异常")
                                wasCancelled = true
                            } else {
                                Log.e("AIService", "【发送消息】流式读取时发生IO异常", e)
                                throw e
                            }
                        }
                    }

                    // 清理活跃Call引用
                    activeCall = null
                    Log.d("AIService", "【发送消息】响应处理完成，已清理活跃Call引用")
                } finally {
                    response.close()
                    Log.d("AIService", "【发送消息】关闭响应连接")
                }

                // 成功处理后返回
                Log.d(
                        "AIService",
                        "【发送消息】请求成功完成，输入token: $_inputTokenCount，输出token: $_outputTokenCount"
                )
                return@stream
            } catch (e: SocketTimeoutException) {
                lastException = e
                retryCount++
                Log.w("AIService", "【发送消息】连接超时，正在进行第 $retryCount 次重试...", e)
                emit("【连接超时，正在进行第 $retryCount 次重试...】")
                // 指数退避重试
                delay(1000L * retryCount)
            } catch (e: UnknownHostException) {
                Log.e("AIService", "【发送消息】无法连接到服务器，请检查网络", e)
                emit("【无法连接到服务器，请检查网络】")
                throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
            } catch (e: Exception) {
                Log.e("AIService", "【发送消息】连接失败", e)
                emit("【连接失败: ${e.message}】")
                throw IOException("AI响应获取失败: ${e.message} ${e.stackTrace.joinToString("\n")}")
            }
        }

        // 所有重试都失败
        Log.e("AIService", "【发送消息】重试失败，请检查网络连接，最大重试次数: $maxRetries", lastException)
        emit("【重试失败，请检查网络连接】")
        throw IOException("连接超时，已重试 $maxRetries 次: ${lastException?.message}")
    }
}

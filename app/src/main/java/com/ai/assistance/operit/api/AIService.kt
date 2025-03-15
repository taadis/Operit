package com.ai.assistance.operit.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

class AIService(
    private val apiEndpoint: String, 
    private val apiKey: String,
    private val modelName: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val JSON = "application/json; charset=utf-8".toMediaType()

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
        } else if(startIndex != -1 && endIndex == -1){
            Pair("",content.substring(startIndex + thinkStartTag.length).trim())
        }else{
            // 没有思考内容，返回原始内容
            Pair(content, null)
        }
    }
    
    // 创建请求体
    private fun createRequestBody(message: String, chatHistory: List<Pair<String, String>>): RequestBody {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", true) // 启用流式响应
        
        // 创建消息数组，包含聊天历史
        val messagesArray = JSONArray()
        
        // 添加聊天历史
        if (chatHistory.isNotEmpty()) {
            // 添加系统消息指示这是有记忆的会话
            val systemMessage = JSONObject()
            systemMessage.put("role", "system")
            systemMessage.put("content", "这是一个有记忆的持续会话，请基于之前的对话上下文回答用户的问题。")
            messagesArray.put(systemMessage)
            
            // 添加历史消息
            for ((role, content) in chatHistory) {
                val historyMessage = JSONObject()
                historyMessage.put("role", role)
                historyMessage.put("content", content)
                messagesArray.put(historyMessage)
            }
        }
        
        // 添加当前消息
        val messageObject = JSONObject()
        messageObject.put("role", "user")
        messageObject.put("content", message)
        messagesArray.put(messageObject)
        
        jsonObject.put("messages", messagesArray)
        
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
        var currentContent = StringBuilder()

        reader.useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data != "[DONE]") {
                        try {
                            val jsonResponse = JSONObject(data)
                            val choices = jsonResponse.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content", "") ?: ""
                                if (content.isNotEmpty()) {
                                    currentContent.append(content)
                                    // 解析内容并回调
                                    val (mainContent, thinkContent) = parseResponse(currentContent.toString())
                                    onPartialResponse(mainContent, thinkContent)
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误，继续处理下一行
                        }
                    }
                }
            }
        }
        
        // 处理完成，调用完成回调
        onComplete()
    }

    suspend fun sendMessage(
        message: String,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>> = emptyList(),
        onComplete: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null
        
        val requestBody = createRequestBody(message, chatHistory)
        val request = createRequest(requestBody)
        
        try {
            while (retryCount < maxRetries) {
                try {
                    client.newCall(request).execute().use { response ->
                        processResponse(response, onPartialResponse, onComplete)
                    }
                    // 成功处理，返回
                    return@withContext
                } catch (e: SocketTimeoutException) {
                    lastException = e
                    retryCount++
                    // 通知正在重试
                    onPartialResponse("", "连接超时，正在进行第 $retryCount 次重试...")
                    // 指数退避重试
                    delay(1000L * retryCount)
                } catch (e: UnknownHostException) {
                    throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
                } catch (e: Exception) {
                    throw IOException("AI响应获取失败: ${e.message} ${e.stackTrace.joinToString("\n")}")
                }
            }
            
            // 所有重试都失败
            throw IOException("连接超时，已重试 $maxRetries 次: ${lastException?.message}")
        } finally {
            // 无论成功或失败，确保调用onComplete
            onComplete()
        }
    }

    /**
     * Provides feedback to the AI about tool execution results.
     * This enables bidirectional streaming for tool calls, allowing Claude to continue
     * the conversation based on tool results.
     * 
     * @param feedback The feedback message containing tool execution results
     */
    suspend fun provideFeedback(feedback: String) = withContext(Dispatchers.IO) {
        try {
            // Create a feedback request
            val jsonObject = JSONObject()
            jsonObject.put("model", modelName)
            jsonObject.put("stream", true)
            
            // Create a message object for the feedback
            val messageObject = JSONObject()
            messageObject.put("role", "assistant")
            messageObject.put("content", feedback)
            
            // Add the message to the messages array
            val messagesArray = JSONArray()
            messagesArray.put(messageObject)
            jsonObject.put("messages", messagesArray)
            
            // Add a flag to indicate this is a tool response feedback
            jsonObject.put("tool_feedback", true)
            
            // Create the request
            val requestBody = jsonObject.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$apiEndpoint/feedback") // Use a feedback endpoint
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            // Send the feedback
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error details"
                    throw IOException("Tool feedback request failed with code: ${response.code}, error: $errorBody")
                }
            }
        } catch (e: Exception) {
            throw IOException("Failed to send tool feedback: ${e.message}")
        }
    }
} 
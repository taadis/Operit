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
import com.ai.assistance.operit.util.ChatUtils
import android.util.Log

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
            }
        }
        
        // 添加当前消息，确保与上一条消息角色不同
        val lastMessageRole = if (messagesArray.length() > 0) {
            messagesArray.getJSONObject(messagesArray.length() - 1).getString("role")
        } else null
        
        if (lastMessageRole != "user") {
            val messageObject = JSONObject()
            messageObject.put("role", "user")
            messageObject.put("content", message)
            messagesArray.put(messageObject)
        } else {
            // 如果上一条消息也是用户，将当前消息与上一条合并
            Log.d("AIService", "合并连续的用户消息")
            val lastMessage = messagesArray.getJSONObject(messagesArray.length() - 1)
            val combinedContent = lastMessage.getString("content") + "\n" + message
            lastMessage.put("content", combinedContent)
        }
        
        jsonObject.put("messages", messagesArray)
        Log.d("AIService", "请求体: $jsonObject")
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
                                if (content.isNotEmpty() && content != "null") {
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
    }
}
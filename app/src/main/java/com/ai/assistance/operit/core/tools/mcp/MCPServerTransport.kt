package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * MCP服务器传输接口
 * 
 * 定义与客户端通信的方式，移植自官方SDK的ServerTransport接口
 */
interface MCPServerTransport {
    /**
     * 与服务器建立连接
     *
     * @param handler 处理收到的消息的回调
     * @param onError 当传输发生错误时的回调
     */
    fun connect(
        handler: (message: String) -> String,
        onError: (error: Throwable) -> Unit
    )
    
    /**
     * 断开与服务器的连接
     */
    fun disconnect()
    
    /**
     * 发送消息到客户端
     *
     * @param message 要发送的消息
     */
    fun send(message: String)
}

/**
 * 内存中的MCP服务器传输实现
 * 
 * 使用内存中的消息队列实现服务器与客户端之间的通信
 * 可以用于测试和内部通信场景
 */
class InMemoryMCPServerTransport : MCPServerTransport {
    companion object {
        private const val TAG = "InMemoryMCPTransport"
        
        // 全局服务器实例映射，用于客户端连接到特定服务器
        private val servers = ConcurrentHashMap<String, InMemoryMCPServerTransport>()
        
        /**
         * 获取或创建服务器实例
         * 
         * @param serverId 服务器ID
         * @return 服务器传输实例
         */
        fun getOrCreateServer(serverId: String): InMemoryMCPServerTransport {
            return servers.getOrPut(serverId) { InMemoryMCPServerTransport() }
        }
        
        /**
         * 移除服务器实例
         * 
         * @param serverId 服务器ID
         */
        fun removeServer(serverId: String) {
            servers.remove(serverId)
        }
    }
    
    // 服务器到客户端的消息队列
    private val serverToClientQueue = ConcurrentLinkedQueue<String>()
    
    // 客户端到服务器的消息队列
    private val clientToServerQueue = ConcurrentLinkedQueue<String>()
    
    // 消息处理器
    private var messageHandler: ((String) -> String)? = null
    
    // 错误处理器
    private var errorHandler: ((Throwable) -> Unit)? = null
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 连接状态
    private var connected = false
    
    override fun connect(
        handler: (message: String) -> String,
        onError: (error: Throwable) -> Unit
    ) {
        messageHandler = handler
        errorHandler = onError
        connected = true
        
        // 启动消息处理循环
        scope.launch {
            while (connected) {
                try {
                    // 处理客户端到服务器的消息
                    val message = clientToServerQueue.poll()
                    if (message != null) {
                        val response = handler(message)
                        if (response.isNotEmpty()) {
                            serverToClientQueue.add(response)
                        }
                    }
                    
                    // 避免CPU忙循环
                    if (clientToServerQueue.isEmpty()) {
                        Thread.sleep(10)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理消息时出错", e)
                    onError(e)
                }
            }
        }
    }
    
    override fun disconnect() {
        connected = false
        messageHandler = null
        errorHandler = null
    }
    
    override fun send(message: String) {
        if (!connected) {
            Log.w(TAG, "尝试在未连接状态下发送消息")
            return
        }
        
        serverToClientQueue.add(message)
    }
    
    /**
     * 从客户端发送消息到服务器
     * 
     * @param message 客户端消息
     */
    fun sendFromClient(message: String): String? {
        if (!connected) {
            Log.w(TAG, "尝试在未连接状态下从客户端发送消息")
            return null
        }
        
        // 将消息添加到队列
        clientToServerQueue.add(message)
        
        // 等待响应（在实际应用中应该使用异步方式）
        var response: String? = null
        for (i in 0 until 100) { // 最多等待1秒
            if (!serverToClientQueue.isEmpty()) {
                response = serverToClientQueue.poll()
                break
            }
            Thread.sleep(10)
        }
        
        return response
    }
}

/**
 * HTTP方式的MCP服务器传输实现
 * 
 * 通过HTTP请求和响应实现MCP协议，可以用于与外部客户端通信
 */
class HttpMCPServerTransport(private val context: Context) : MCPServerTransport {
    companion object {
        private const val TAG = "HttpMCPTransport"
    }
    
    // 消息处理器
    private var messageHandler: ((String) -> String)? = null
    
    // 错误处理器
    private var errorHandler: ((Throwable) -> Unit)? = null
    
    // 连接状态
    private var connected = false
    
    override fun connect(
        handler: (message: String) -> String,
        onError: (error: Throwable) -> Unit
    ) {
        messageHandler = handler
        errorHandler = onError
        connected = true
    }
    
    override fun disconnect() {
        connected = false
        messageHandler = null
        errorHandler = null
    }
    
    override fun send(message: String) {
        // 在HTTP模式下，我们不会主动发送消息
        // 只在收到请求时返回响应
        Log.d(TAG, "在HTTP模式下尝试发送消息，但这个操作被忽略")
    }
    
    /**
     * 处理HTTP请求
     * 
     * @param requestBody HTTP请求体
     * @return HTTP响应体
     */
    fun handleHttpRequest(requestBody: String): String {
        if (!connected) {
            Log.w(TAG, "尝试在未连接状态下处理HTTP请求")
            return createErrorResponse("-1", -32603, "Server not connected")
        }
        
        try {
            val handler = messageHandler
                ?: return createErrorResponse("-1", -32603, "Message handler not set")
            
            // 调用消息处理器处理请求
            return handler(requestBody)
        } catch (e: Exception) {
            Log.e(TAG, "处理HTTP请求时出错", e)
            errorHandler?.invoke(e)
            return createErrorResponse("-1", -32603, "Internal error: ${e.message}")
        }
    }
    
    /**
     * 创建错误响应
     */
    private fun createErrorResponse(id: String, code: Int, message: String): String {
        val responseObj = JSONObject()
        responseObj.put("jsonrpc", "2.0")
        responseObj.put("id", id)
        
        val errorObj = JSONObject()
        errorObj.put("code", code)
        errorObj.put("message", message)
        
        responseObj.put("error", errorObj)
        
        return responseObj.toString()
    }
} 
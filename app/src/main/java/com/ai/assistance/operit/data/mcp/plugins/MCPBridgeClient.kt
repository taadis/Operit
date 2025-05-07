package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.util.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** MCPBridgeClient - Client for communicating with MCP services through a bridge */
class MCPBridgeClient(private val context: Context, private val serviceName: String) {
    companion object {
        private const val TAG = "MCPBridgeClient"
    }

    private val bridge = MCPBridge(context)
    private val isConnected = AtomicBoolean(false)
    private var lastPingTime = 0L

    /** Connect to the MCP service */
    suspend fun connect(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    // Check if service is registered
                    val listResponse = bridge.listMcpServices() ?: return@withContext false

                    // Find service in list
                    val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                    var serviceFound = false

                    if (services != null) {
                        for (i in 0 until services.length()) {
                            val service = services.optJSONObject(i)
                            if (service?.optString("name", "") == serviceName) {
                                serviceFound = true
                                break
                            }
                        }
                    }

                    if (!serviceFound) {
                        Log.w(TAG, "Service $serviceName not registered")
                        return@withContext false
                    }

                    // Verify connection with ping
                    val pingResult = bridge.pingMcpService(serviceName)

                    if (pingResult != null) {
                        isConnected.set(true)
                        return@withContext true
                    }

                    // Fallback to status check
                    val statusResult = bridge.getStatus()
                    if (statusResult?.optBoolean("success", false) == true &&
                                    statusResult.optJSONObject("result")?.optString("mcpName") ==
                                            serviceName
                    ) {
                        isConnected.set(true)
                        return@withContext true
                    }

                    return@withContext false
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting to MCP service: ${e.message}")
                    return@withContext false
                }
            }

    /** Check if connected */
    fun isConnected(): Boolean = isConnected.get()

    /** Ping the service */
    suspend fun ping(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val startTime = System.currentTimeMillis()
                    val result = bridge.pingMcpService(serviceName)

                    if (result != null) {
                        val responseObj = result.optJSONObject("result")
                        val status = responseObj?.optString("status")
                        val mcpName = responseObj?.optString("mcpName")

                        // Only return true if this service is actually active
                        if (status == "ok" && mcpName == serviceName) {
                            lastPingTime = System.currentTimeMillis() - startTime
                            isConnected.set(true)
                            return@withContext true
                        }

                        // If it's registered but not active, we're not truly connected
                        Log.d(
                                TAG,
                                "Service $serviceName is registered but not active. Current service: $mcpName"
                        )
                        return@withContext false
                    }
                    return@withContext false
                } catch (e: Exception) {
                    return@withContext false
                }
            }

    /** Synchronous ping method */
    fun pingSync(): Boolean = kotlinx.coroutines.runBlocking { ping() }

    /** Get last ping time */
    fun getLastPingTime(): Long = lastPingTime

    /** Call a tool on the MCP service */
    suspend fun callTool(method: String, params: JSONObject): JSONObject? =
            withContext(Dispatchers.IO) {
                try {
                    // Connect if not connected
                    if (!isConnected.get()) {
                        Log.d(TAG, "尝试重新连接 $serviceName 服务")
                        val connectSuccess = connect()
                        if (!connectSuccess) {
                            Log.e(TAG, "无法连接到 $serviceName 服务")
                            return@withContext null
                        }
                    }

                    // Build parameters
                    val callParams =
                            JSONObject().apply {
                                put("method", method)
                                put("params", params)
                                put("name", serviceName)
                                put("id", UUID.randomUUID().toString())
                            }

                    // Build command
                    val command =
                            JSONObject().apply {
                                put("command", "toolcall")
                                put("id", UUID.randomUUID().toString())
                                put("params", callParams)
                            }

                    // Send command
                    val response = MCPBridge.sendCommand(command)

                    if (response?.optBoolean("success", false) == true) {
                        return@withContext response.optJSONObject("result")
                    } else {
                        val errorMsg =
                                response?.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"

                        // Check for connection errors and handle reconnection
                        if (errorMsg.contains("not available") ||
                                        errorMsg.contains("not connected") ||
                                        errorMsg.contains("connection closed") ||
                                        errorMsg.contains("timeout") ||
                                        response == null
                        ) {
                            Log.w(TAG, "检测到连接错误: $errorMsg, 标记为已断开")
                            isConnected.set(false)

                            // Try to reconnect once
                            Log.d(TAG, "尝试立即重新连接")
                            if (connect()) {
                                // If reconnect succeeds, try the call again (one retry)
                                Log.d(TAG, "重新连接成功，重试工具调用")
                                val retryCommand = JSONObject(command.toString())
                                val retryResponse = MCPBridge.sendCommand(retryCommand)

                                if (retryResponse?.optBoolean("success", false) == true) {
                                    return@withContext retryResponse.optJSONObject("result")
                                }
                            }
                        }

                        Log.e(TAG, "工具调用错误: $errorMsg")
                        return@withContext null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling tool $method: ${e.message}")
                    // Mark as disconnected on exception
                    isConnected.set(false)
                    return@withContext null
                }
            }

    /** Call a tool with Map parameters */
    suspend fun callTool(method: String, params: Map<String, Any>): JSONObject? {
        val paramsJson = JSONObject()
        params.forEach { (key, value) -> paramsJson.put(key, value) }
        return callTool(method, paramsJson)
    }

    /** Synchronous tool call */
    fun callToolSync(method: String, params: JSONObject): JSONObject? {
        return kotlinx.coroutines.runBlocking { callTool(method, params) }
    }

    /** Synchronous tool call with Map */
    fun callToolSync(method: String, params: Map<String, Any>): JSONObject? {
        val paramsJson = JSONObject()
        params.forEach { (key, value) -> paramsJson.put(key, value) }
        return callToolSync(method, paramsJson)
    }

    /** Get all tools provided by the service */
    suspend fun getTools(): List<JSONObject> =
            withContext(Dispatchers.IO) {
                try {
                    // Connect if not connected
                    if (!isConnected.get()) {
                        Log.d(TAG, "尝试重新连接 $serviceName 服务以获取工具列表")
                        val connectSuccess = connect()
                        if (!connectSuccess) {
                            Log.e(TAG, "无法连接到 $serviceName 服务")
                            return@withContext emptyList()
                        }
                    }

                    // Get tools - pass the service name to be more specific
                    val response = bridge.listTools(serviceName)

                    if (response?.optBoolean("success", false) == true) {
                        val toolsArray =
                                response.optJSONObject("result")?.optJSONArray("tools")
                                        ?: return@withContext emptyList()

                        val tools = mutableListOf<JSONObject>()
                        for (i in 0 until toolsArray.length()) {
                            val tool = toolsArray.optJSONObject(i)
                            if (tool != null) {
                                tools.add(tool)
                            }
                        }

                        if (tools.isNotEmpty()) {
                            Log.d(TAG, "成功获取 ${tools.size} 个工具")
                        } else {
                            Log.w(TAG, "服务 $serviceName 未返回任何工具")
                        }

                        return@withContext tools
                    } else {
                        // Check for connection errors
                        val errorMsg =
                                response?.optJSONObject("error")?.optString("message")
                                        ?: "Unknown error"
                        if (errorMsg.contains("not available") ||
                                        errorMsg.contains("not connected") ||
                                        errorMsg.contains("connection closed") ||
                                        errorMsg.contains("timeout") ||
                                        response == null
                        ) {
                            Log.w(TAG, "获取工具列表时检测到连接错误，标记为已断开")
                            isConnected.set(false)
                        }

                        Log.e(TAG, "获取工具列表失败: $errorMsg")
                        return@withContext emptyList()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting tools: ${e.message}")
                    // Mark as disconnected on exception
                    isConnected.set(false)
                    return@withContext emptyList()
                }
            }

    /** Disconnect from the service */
    fun disconnect() {
        isConnected.set(false)
    }
}

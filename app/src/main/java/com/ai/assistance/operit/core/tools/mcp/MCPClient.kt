package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.tools.ToolExecutor
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.serialization.Serializable

/**
 * Model Context Protocol (MCP) Client
 *
 * 负责与MCP服务器通信，使用JSON-RPC 2.0格式进行请求和响应
 */
class MCPClient(private val context: Context, private val serverConfig: MCPServerConfig) {
    companion object {
        private const val TAG = "MCPClient"
        private const val JSON_RPC_VERSION = "2.0"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val okHttpClient =
            OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()

    // 为了存储和跟踪服务器的能力
    private var serverCapabilities: Map<String, Any>? = null
    private var initialized = false

    // JSON媒体类型常量
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * 初始化与MCP服务器的连接
     *
     * @return 成功或失败的消息
     */
    fun initialize(): String {
        try {
            // 创建初始化请求
            val initRequestId = UUID.randomUUID().toString()
            val initRequest =
                    JSONObject().apply {
                        put("jsonrpc", JSON_RPC_VERSION)
                        put("id", initRequestId)
                        put("method", "initialize")
                        put(
                                "params",
                                JSONObject().apply {
                                    put("protocolVersion", "2024-11-05")
                                    put("name", "operit-mcp-client")
                                    put("version", "1.0.0")
                                    put(
                                            "capabilities",
                                            JSONObject().apply {
                                                // 添加客户端能力
                                                put(
                                                        "sampling",
                                                        JSONObject().apply {
                                                            put(
                                                                    "supportedTypes",
                                                                    JSONArray().apply {
                                                                        put("text")
                                                                    }
                                                            )
                                                        }
                                                )
                                            }
                                    )
                                }
                        )
                    }

            // 发送初始化请求
            Log.d(TAG, "正在初始化MCP服务器 ${serverConfig.name} (${serverConfig.endpoint})")
            val response = sendRequest(initRequest.toString())

            // 解析响应
            val responseJson = JSONObject(response)
            if (responseJson.has("error")) {
                val error = responseJson.getJSONObject("error")
                val errorMessage = "MCP初始化错误: ${error.getString("message")}"
                Log.e(TAG, errorMessage)
                return errorMessage
            }

            // 保存服务器能力
            val result = responseJson.getJSONObject("result")
            val capabilities = result.getJSONObject("capabilities")
            serverCapabilities = parseCapabilities(capabilities)

            // 发送initialized通知
            val initializedNotification =
                    JSONObject().apply {
                        put("jsonrpc", JSON_RPC_VERSION)
                        put("method", "initialized")
                    }
            sendRequest(initializedNotification.toString())

            initialized = true
            return "已成功初始化MCP服务器: ${serverConfig.name}"
        } catch (e: Exception) {
            Log.e(TAG, "MCP初始化错误", e)
            return "MCP初始化失败: ${e.message}"
        }
    }

    /** 解析服务器能力对象为Map */
    private fun parseCapabilities(capabilities: JSONObject): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        // 检查服务器支持的功能
        if (capabilities.has("tools")) {
            result["tools"] = true
        }
        if (capabilities.has("resources")) {
            result["resources"] = true
        }
        if (capabilities.has("prompts")) {
            result["prompts"] = true
        }

        return result
    }

    /**
     * 获取服务器可用工具列表
     *
     * @return 工具列表，每个工具包含名称和描述
     */
    fun getTools(): List<MCPTool> {
        if (!initialized) {
            Log.e(TAG, "尝试在初始化前获取工具")
            return emptyList()
        }

        try {
            val toolsRequestId = UUID.randomUUID().toString()
            val toolsRequest =
                    JSONObject().apply {
                        put("jsonrpc", JSON_RPC_VERSION)
                        put("id", toolsRequestId)
                        put("method", "tools/list")
                    }

            // 发送获取工具列表请求
            val response = sendRequest(toolsRequest.toString())
            val responseJson = JSONObject(response)

            if (responseJson.has("error")) {
                val error = responseJson.getJSONObject("error")
                Log.e(TAG, "获取工具列表错误: ${error.getString("message")}")
                return emptyList()
            }

            // 解析工具列表
            val result = responseJson.getJSONObject("result")
            val tools = result.getJSONArray("tools")

            val toolsList = mutableListOf<MCPTool>()
            for (i in 0 until tools.length()) {
                val tool = tools.getJSONObject(i)
                val name = tool.getString("name")
                val description = if (tool.has("description")) tool.getString("description") else ""

                // 解析参数
                val params = mutableListOf<MCPToolParameter>()
                if (tool.has("parameters")) {
                    val parameters = tool.getJSONObject("parameters")
                    if (parameters.has("properties")) {
                        val properties = parameters.getJSONObject("properties")
                        properties.keys().forEach { paramName ->
                            val property = properties.getJSONObject(paramName)
                            val paramDesc =
                                    if (property.has("description"))
                                            property.getString("description")
                                    else ""
                            val paramType =
                                    if (property.has("type")) property.getString("type")
                                    else "string"
                            val required =
                                    if (parameters.has("required")) {
                                        val requiredParams = parameters.getJSONArray("required")
                                        (0 until requiredParams.length()).any {
                                            requiredParams.getString(it) == paramName
                                        }
                                    } else false

                            params.add(
                                    MCPToolParameter(
                                            name = paramName,
                                            description = paramDesc,
                                            type = paramType,
                                            required = required
                                    )
                            )
                        }
                    }
                }

                toolsList.add(MCPTool(name, description, params))
            }

            return toolsList
        } catch (e: Exception) {
            Log.e(TAG, "获取工具列表时出错", e)
            return emptyList()
        }
    }

    /**
     * 调用MCP工具
     *
     * @param toolName 工具名称
     * @param parameters 参数列表
     * @return 工具执行结果
     */
    fun invokeTool(toolName: String, parameters: Map<String, String>): ToolResult {
        if (!initialized) {
            return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = "MCP客户端未初始化"
            )
        }

        try {
            val invokeRequestId = UUID.randomUUID().toString()
            val invokeRequest =
                    JSONObject().apply {
                        put("jsonrpc", JSON_RPC_VERSION)
                        put("id", invokeRequestId)
                        put("method", "tools/invoke")
                        put(
                                "params",
                                JSONObject().apply {
                                    put("name", toolName)
                                    put(
                                            "arguments",
                                            JSONObject().apply {
                                                // 添加所有参数
                                                parameters.forEach { (key, value) ->
                                                    put(key, value)
                                                }
                                            }
                                    )
                                }
                        )
                    }

            // 发送工具调用请求
            Log.d(TAG, "调用MCP工具: $toolName 参数: $parameters")
            val response = sendRequest(invokeRequest.toString())
            val responseJson = JSONObject(response)

            if (responseJson.has("error")) {
                val error = responseJson.getJSONObject("error")
                val errorMessage = error.getString("message")
                Log.e(TAG, "工具调用错误: $errorMessage")
                return ToolResult(
                        toolName = toolName,
                        success = false,
                        result = StringResultData(""),
                        error = errorMessage
                )
            }

            // 解析工具执行结果
            val result = responseJson.getJSONObject("result")
            val resultStr = result.toString(2) // 美化JSON

            return ToolResult(
                    toolName = toolName,
                    success = true,
                    result = StringResultData(resultStr)
            )
        } catch (e: Exception) {
            Log.e(TAG, "调用工具时出错", e)
            return ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = "调用出错: ${e.message}"
            )
        }
    }

    /**
     * 发送请求到MCP服务器
     *
     * @param requestBody 请求体JSON字符串
     * @return 响应体JSON字符串
     */
    private fun sendRequest(requestBody: String): String {
        val requestBodyObj = requestBody.toRequestBody(jsonMediaType)
        val request = Request.Builder().url(serverConfig.endpoint).post(requestBodyObj).build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP错误代码: ${response.code}")
        }

        return response.body?.string() ?: throw Exception("空响应")
    }

    /** 关闭MCP客户端连接 */
    fun shutdown() {
        if (!initialized) return

        try {
            val shutdownRequestId = UUID.randomUUID().toString()
            val shutdownRequest =
                    JSONObject().apply {
                        put("jsonrpc", JSON_RPC_VERSION)
                        put("id", shutdownRequestId)
                        put("method", "shutdown")
                    }

            // 发送关闭请求
            val response = sendRequest(shutdownRequest.toString())

            // 发送终止通知
            val exitNotification =
                    JSONObject().apply {
                        put("jsonrpc", JSON_RPC_VERSION)
                        put("method", "exit")
                    }
            sendRequest(exitNotification.toString())

            initialized = false
            Log.d(TAG, "已关闭MCP服务器连接: ${serverConfig.name}")
        } catch (e: Exception) {
            Log.e(TAG, "关闭MCP客户端时出错", e)
        }
    }

    /** 创建一个ToolExecutor接口的实现，用于处理MCP工具调用 */
    fun createToolExecutor(toolName: String): ToolExecutor {
        return object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult {
                // 将AITool转换为MCP参数映射
                val params = tool.parameters.associate { it.name to it.value }

                // 调用MCP工具并返回结果
                return invokeTool(toolName, params)
            }

            override fun validateParameters(tool: AITool): ToolValidationResult {
                // 这里可以实现参数验证，但目前简单返回成功
                return ToolValidationResult(valid = true)
            }
        }
    }
}

/** MCP服务器配置 */
@Serializable
data class MCPServerConfig(
        val name: String,
        val endpoint: String,
        val description: String = "",
        val capabilities: List<String> = emptyList()
)

/** MCP工具参数 */
@Serializable
data class MCPToolParameter(
        val name: String,
        val description: String,
        val type: String = "string",
        val required: Boolean = false
)

/** MCP工具 */
@Serializable
data class MCPTool(
        val name: String,
        val description: String,
        val parameters: List<MCPToolParameter> = emptyList()
)

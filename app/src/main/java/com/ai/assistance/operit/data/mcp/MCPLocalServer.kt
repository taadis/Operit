package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import java.io.BufferedInputStream
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.kotlinx.mcp.*
import org.jetbrains.kotlinx.mcp.server.Server
import org.jetbrains.kotlinx.mcp.server.ServerOptions
import org.jetbrains.kotlinx.mcp.server.StdioServerTransport

/**
 * MCPLocalServer - 本地MCP服务器管理器
 *
 * 负责管理本地MCP服务器的生命周期、配置和状态 使用官方JetBrains MCP Kotlin SDK实现
 */
class MCPLocalServer private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPLocalServer"
        private const val PREFS_NAME = "mcp_local_server_prefs"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_SERVER_PATH = "server_path"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val SERVER_NAME = "local-mcp-server"

        private const val DEFAULT_PORT = 8765
        private const val DEFAULT_LOG_LEVEL = "info"
        private const val MAX_LOG_ENTRIES = 1000

        @Volatile private var INSTANCE: MCPLocalServer? = null

        fun getInstance(context: Context): MCPLocalServer {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: MCPLocalServer(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    // MCP服务器管理器，用于注册服务器到其他组件
    private val mcpManager = MCPManager.getInstance(context)

    // MCP服务器实例
    private var mcpServer: Server? = null

    // 用于管道通信的流
    private var inputPipe: PipedInputStream? = null
    private var outputPipe: PipedOutputStream? = null

    // 包装后的用于传输层的流
    private var bufferedInput: BufferedInputStream? = null
    private var printOutput: PrintStream? = null

    // 传输层
    private var transport: StdioServerTransport? = null

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 日志队列
    private val logEntries = ConcurrentLinkedQueue<LogEntry>()

    // 持久化配置
    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 服务器状态流
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // 启动时间戳
    private val _startTime = MutableStateFlow<Long?>(null)
    val startTime: StateFlow<Long?> = _startTime.asStateFlow()

    // 服务器配置
    private val _serverPort = MutableStateFlow(prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT))
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    // 服务器路径
    private val _serverPath =
            MutableStateFlow(
                    prefs.getString(KEY_SERVER_PATH, getDefaultServerPath())
                            ?: getDefaultServerPath()
            )
    val serverPath: StateFlow<String> = _serverPath.asStateFlow()

    // 是否自动启动
    private val _autoStart = MutableStateFlow(prefs.getBoolean(KEY_AUTO_START, false))
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    // 日志级别
    private val _logLevel =
            MutableStateFlow(prefs.getString(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL) ?: DEFAULT_LOG_LEVEL)
    val logLevel: StateFlow<String> = _logLevel.asStateFlow()

    // 连接的客户端数
    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    // 最近的日志消息
    private val _lastLogMessage = MutableStateFlow<String?>(null)
    val lastLogMessage: StateFlow<String?> = _lastLogMessage.asStateFlow()

    // 已注册的工具列表
    private val _registeredTools = MutableStateFlow<List<ToolInfo>>(emptyList())
    val registeredTools: StateFlow<List<ToolInfo>> = _registeredTools.asStateFlow()

    init {
        Log.d(TAG, "MCPLocalServer 初始化，默认端口: ${_serverPort.value}")
    }

    /** 初始化MCP服务器并自动启动已部署的插件 在应用启动时执行，不依赖于界面加载 */
    fun initAndAutoStartPlugins() {
        Log.d(TAG, "开始初始化MCP服务器并检查自动启动插件")

        scope.launch {
            try {
                // 判断是否设置了自动启动
                val isAutoStart = autoStart.value

                if (isAutoStart) {
                    Log.d(TAG, "自动启动设置已启用，正在启动MCP服务器")
                    val success = startServer()

                    if (success) {
                        Log.d(TAG, "MCP服务器启动成功，等待服务器就绪")
                        // 等待服务器完全就绪
                        delay(2000)

                        // 检查服务器是否确实运行中
                        val isServerRunning = isRunning.value

                        if (isServerRunning) {
                            Log.d(TAG, "MCP服务器运行中，准备启动已部署插件")
                            
                            // 使用MCPStarter启动所有已部署的插件
                            val mcpStarter = MCPStarter(context)
                            mcpStarter.startAllDeployedPlugins()
                        } else {
                            Log.e(TAG, "MCP服务器未能成功启动")
                        }
                    } else {
                        Log.e(TAG, "MCP服务器启动失败")
                    }
                } else {
                    Log.d(TAG, "MCP服务器自动启动未启用")
                }
            } catch (e: Exception) {
                Log.e(TAG, "MCP服务器自动启动过程中出错", e)
            }
        }
    }

    /** 从配置中提取服务器名称 */
    private fun extractServerNameFromConfig(configJson: String): String? {
        // 使用MCPVscodeConfig解析服务器名称
        return MCPVscodeConfig.extractServerName(configJson)
    }

    /** 获取默认服务器路径 */
    private fun getDefaultServerPath(): String {
        val storageDir = context.getExternalFilesDir(null)
        return File(storageDir, "mcp_server").absolutePath
    }

    /**
     * 启动服务器
     *
     * @return 是否成功启动
     */
    fun startServer(): Boolean {
        if (_isRunning.value) {
            Log.i(TAG, "服务器已在运行")
            return true
        }

        try {
            Log.i(TAG, "启动MCP服务器")

            // 1. 创建并设置服务器
            mcpServer = createServer()

            // 2. 创建管道和传输层
            setupTransport()

            // 3. 在协程中连接服务器
            scope.launch {
                try {
                    mcpServer?.connect(transport!!)
                } catch (e: Exception) {
                    Log.e(TAG, "服务器运行异常", e)
                    stopServer()
                }
            }

            // 4. 注册到MCPManager
            val serverConfig =
                    MCPServerConfig(
                            name = SERVER_NAME,
                            endpoint = "mcp://local",
                            description = "本地MCP服务器",
                            capabilities = listOf("tools", "resources")
                    )
            mcpManager.registerServer(SERVER_NAME, serverConfig)

            _isRunning.value = true
            _startTime.value = System.currentTimeMillis()
            _connectedClients.value = 1
            return true
        } catch (e: Exception) {
            Log.e(TAG, "启动服务器失败", e)
            stopServer()
            return false
        }
    }

    /**
     * 停止服务器
     *
     * @return 是否成功停止
     */
    fun stopServer(): Boolean {
        if (!_isRunning.value) {
            Log.i(TAG, "服务器当前没有运行")
            return true
        }

        try {
            Log.i(TAG, "正在停止MCP服务器")

            // 关闭传输层 - 在协程中执行
            scope.launch { transport?.close() }

            // 关闭管道
            closePipes()

            // 停止MCP服务器
            mcpServer = null

            // 更新状态
            _isRunning.value = false
            _connectedClients.value = 0

            return true
        } catch (e: Exception) {
            Log.e(TAG, "停止MCP服务器失败", e)
            return false
        }
    }

    /** 设置通信管道 */
    private fun setupTransport() {
        closePipes()

        // 创建管道
        inputPipe = PipedInputStream(4096)
        outputPipe = PipedOutputStream(inputPipe)

        // 包装为StdioServerTransport所需的类型
        bufferedInput = BufferedInputStream(inputPipe)
        printOutput = PrintStream(outputPipe, true)

        // 创建传输层
        transport = StdioServerTransport(bufferedInput!!, printOutput!!)
    }

    /** 关闭通信管道 */
    private fun closePipes() {
        try {
            bufferedInput?.close()
            printOutput?.close()
            inputPipe?.close()
            outputPipe?.close()
        } catch (e: Exception) {
            Log.e(TAG, "关闭管道失败", e)
        } finally {
            bufferedInput = null
            printOutput = null
            inputPipe = null
            outputPipe = null
            transport = null
        }
    }

    /** 创建MCP服务器实例 */
    private fun createServer(): Server {
        // 创建服务器信息
        val serverInfo = Implementation(name = "operit-local-mcp-server", version = "1.0.0")

        // 创建服务器能力配置
        val capabilities =
                ServerCapabilities(
                        resources =
                                ServerCapabilities.Resources(subscribe = true, listChanged = true),
                        tools = ServerCapabilities.Tools(listChanged = true)
                )

        // 创建服务器选项
        val options = ServerOptions(capabilities = capabilities, enforceStrictCapabilities = false)

        // 创建MCP服务器
        val server = Server(serverInfo, options)

        // 跟踪工具注册状态，确保先注册工具
        val tools = mutableListOf<ToolInfo>()

        // 注册工具
        registerSystemTools(server)

        // 注册资源
        registerSystemResources(server)

        // 手动设置ListToolsRequest处理器以确保响应速度
        server.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
            Log.d(TAG, "收到工具列表请求，立即响应 ${_registeredTools.value.size} 个工具")

            // 直接从已注册工具列表构建工具对象
            val serverTools =
                    _registeredTools.value.map { toolInfo ->
                        // 创建参数模式
                        val params =
                                toolInfo.parameters.associate { param ->
                                    param.name to
                                            JsonObject(
                                                    mapOf(
                                                            "type" to JsonPrimitive(param.type),
                                                            "description" to
                                                                    JsonPrimitive(
                                                                            param.description
                                                                    ),
                                                            "required" to
                                                                    JsonPrimitive(param.required)
                                                    )
                                            )
                                }

                        // 创建工具对象
                        Tool(
                                name = toolInfo.name,
                                description = toolInfo.description,
                                inputSchema = Tool.Input(JsonObject(params))
                        )
                    }

            // 返回工具列表结果
            ListToolsResult(tools = serverTools, nextCursor = null)
        }

        return server
    }

    /** 注册系统工具到服务器 */
    private fun registerSystemTools(server: Server) {
        val tools = mutableListOf<ToolInfo>()

        // 系统信息工具
        server.addTool(
                name = "system_info",
                description = "获取系统信息",
                inputSchema = Tool.Input(JsonObject(emptyMap()))
        ) { _ ->
            val systemInfo =
                    mapOf(
                            "os" to System.getProperty("os.name"),
                            "version" to System.getProperty("os.version"),
                            "arch" to System.getProperty("os.arch"),
                            "processors" to Runtime.getRuntime().availableProcessors()
                    )

            val text = systemInfo.entries.joinToString("\n") { "${it.key}: ${it.value}" }

            Log.d(TAG, "执行系统信息工具")
            CallToolResult(content = listOf(TextContent(text)))
        }

        tools.add(ToolInfo("system_info", "获取系统信息"))

        // 回显工具
        val echoParams =
                JsonObject(
                        mapOf(
                                "text" to
                                        JsonObject(
                                                mapOf(
                                                        "type" to JsonPrimitive("string"),
                                                        "description" to JsonPrimitive("要回显的文本")
                                                )
                                        )
                        )
                )

        server.addTool(
                name = "echo",
                description = "回显输入的文本",
                inputSchema = Tool.Input(echoParams)
        ) { request ->
            val text = request.arguments["text"]?.toString()?.removeSurrounding("\"") ?: ""
            Log.d(TAG, "执行回显工具: $text")
            CallToolResult(content = listOf(TextContent("回显: $text")))
        }

        tools.add(
                ToolInfo("echo", "回显输入的文本", listOf(ToolParamInfo("text", "要回显的文本", "string", true)))
        )

        // 更新已注册工具列表
        _registeredTools.value = tools

        Log.d(TAG, "已注册 ${tools.size} 个系统工具")
    }

    /** 注册系统资源到服务器 */
    private fun registerSystemResources(server: Server) {
        // 系统信息资源
        server.addResource(
                uri = "mcp://local/system_info",
                name = "系统信息",
                description = "系统信息资源",
                mimeType = "text/plain"
        ) { _ ->
            val systemInfo =
                    mapOf(
                            "os" to System.getProperty("os.name"),
                            "version" to System.getProperty("os.version"),
                            "arch" to System.getProperty("os.arch"),
                            "processors" to Runtime.getRuntime().availableProcessors()
                    )

            val text = systemInfo.entries.joinToString("\n") { "${it.key}: ${it.value}" }

            ReadResourceResult(
                    contents =
                            listOf(
                                    TextResourceContents(
                                            text = text,
                                            uri = "mcp://local/system_info",
                                            mimeType = "text/plain"
                                    )
                            )
            )
        }
    }

    /** 保存服务器端口设置 */
    fun saveServerPort(port: Int): Boolean {
        if (port < 1024 || port > 65535) {
            log(LogLevel.ERROR, "无效的端口号: $port")
            return false
        }

        _serverPort.value = port
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply()

        // 如果服务器正在运行，需要重启才能应用新端口
        if (_isRunning.value) {
            log(LogLevel.WARNING, "端口已更改，服务器需要重启以应用新设置")
        }

        return true
    }

    /** 保存服务器路径设置 */
    fun saveServerPath(path: String): Boolean {
        val directory = File(path)

        // 尝试创建目录（如果不存在）
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                log(LogLevel.ERROR, "无法创建服务器目录: $path")
                return false
            }
        }

        // 检查目录是否可写
        if (!directory.canWrite()) {
            log(LogLevel.ERROR, "服务器目录无写入权限: $path")
            return false
        }

        _serverPath.value = path
        prefs.edit().putString(KEY_SERVER_PATH, path).apply()
        log(LogLevel.INFO, "已更新服务器路径: $path")

        return true
    }

    /** 保存自动启动设置 */
    fun saveAutoStart(autoStart: Boolean) {
        _autoStart.value = autoStart
        prefs.edit().putBoolean(KEY_AUTO_START, autoStart).apply()
        log(LogLevel.INFO, "已更新自动启动设置: $autoStart")
    }

    /** 保存日志级别设置 */
    fun saveLogLevel(level: String) {
        try {
            val logLevel = LogLevel.valueOf(level.uppercase())
            _logLevel.value = level.lowercase()
            prefs.edit().putString(KEY_LOG_LEVEL, level.lowercase()).apply()
            log(LogLevel.INFO, "已更新日志级别: $level")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "无效的日志级别: $level")
        }
    }

    /**
     * 检测插件配置文件
     *
     * @param pluginId 插件ID
     * @return 是否存在配置文件
     */
    fun hasPluginConfig(pluginId: String): Boolean {
        val configFile = File(_serverPath.value, "plugins/$pluginId/config.json")
        return configFile.exists() && configFile.isFile
    }

    /**
     * 获取插件配置
     *
     * @param pluginId 插件ID
     * @return 配置内容JSON字符串，如果不存在返回空字符串
     */
    fun getPluginConfig(pluginId: String): String {
        val configFile = File(_serverPath.value, "plugins/$pluginId/config.json")
        return if (configFile.exists() && configFile.isFile) {
            configFile.readText()
        } else {
            "{}"
        }
    }

    /**
     * 保存插件配置
     *
     * @param pluginId 插件ID
     * @param config 配置内容JSON字符串
     * @return 是否保存成功
     */
    fun savePluginConfig(pluginId: String, config: String): Boolean {
        try {
            val pluginDir = File(_serverPath.value, "plugins/$pluginId")
            if (!pluginDir.exists()) {
                pluginDir.mkdirs()
            }

            val configFile = File(pluginDir, "config.json")
            configFile.writeText(config)

            log(LogLevel.INFO, "已更新插件 $pluginId 的配置")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "保存插件配置失败: $pluginId", e)
            log(LogLevel.ERROR, "保存插件配置失败: ${e.message}")
            return false
        }
    }

    /** 记录日志 */
    private fun log(level: LogLevel, message: String) {
        // 检查日志级别
        val currentLevel =
                try {
                    LogLevel.valueOf(_logLevel.value.uppercase())
                } catch (e: IllegalArgumentException) {
                    LogLevel.INFO
                }

        // 如果日志级别小于当前设置的级别，不记录
        if (level.severity < currentLevel.severity) {
            return
        }

        // 创建日志条目
        val logEntry = LogEntry(System.currentTimeMillis(), level, message)

        // 添加到日志队列
        logEntries.add(logEntry)

        // 如果超过最大条数，移除旧的
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.poll()
        }

        // 更新最后日志消息
        _lastLogMessage.value = message

        // 打印到Android日志
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARNING -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
    }

    /** 格式化时间戳 */
    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return "N/A"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    /** 格式化持续时间 */
    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%d小时 %d分钟 %d秒", hours, minutes, secs)
        } else if (minutes > 0) {
            String.format("%d分钟 %d秒", minutes, secs)
        } else {
            String.format("%d秒", secs)
        }
    }

    // 新增方法：获取服务器管道
    suspend fun getServerPipes(): Pair<PipedInputStream, PipedOutputStream>? {
        if (!_isRunning.value) {
            Log.e(TAG, "服务器未运行，无法获取管道")
            return null
        }

        try {
            // 创建新的双向连接管道
            val clientToServer = PipedInputStream(4096)
            val serverToClient = PipedInputStream(4096)

            // 创建新的输出流连接到客户端输入流
            val newServerOutput = PipedOutputStream(serverToClient)
            val newClientOutput = PipedOutputStream(clientToServer)

            // 停止现有服务器 - 完全重启而不是仅替换管道
            // 这样可以避免新旧连接之间的死锁问题
            Log.d(TAG, "停止现有服务器以准备新连接")

            // 保存服务器实例以便重用
            val serverInstance = mcpServer

            // 关闭旧的传输层和管道，但保持服务器实例
            try {
                // 现在可以直接调用suspend方法，因为当前方法也是suspend的
                transport?.close()
            } catch (e: Exception) {
                Log.w(TAG, "关闭旧传输层失败，忽略", e)
            }

            // 关闭旧的管道
            try {
                bufferedInput?.close()
                printOutput?.close()
                inputPipe?.close()
                outputPipe?.close()
            } catch (e: Exception) {
                Log.w(TAG, "关闭旧管道失败，忽略", e)
            }

            // 更新服务器传输层
            inputPipe = clientToServer
            bufferedInput = BufferedInputStream(clientToServer)
            printOutput = PrintStream(newServerOutput, true)

            // 重新创建传输层
            transport = StdioServerTransport(bufferedInput!!, printOutput!!)

            // 在协程中连接服务器
            // 不在此等待连接完成，避免与客户端初始化发生死锁
            scope.launch {
                try {
                    Log.d(TAG, "服务器管道已准备，等待客户端连接")
                    // 使用重用的服务器实例重新连接
                    serverInstance?.let { server ->
                        // 简单连接服务器传输层，不做复杂检查
                        Log.d(TAG, "连接服务器传输层")
                        server.connect(transport!!)
                        Log.d(TAG, "服务器连接已建立")
                    }
                            ?: run { Log.e(TAG, "服务器实例为空，无法连接") }
                } catch (e: Exception) {
                    Log.e(TAG, "重新连接服务器传输层失败", e)
                }
            }

            // 返回客户端应该使用的管道对
            Log.d(TAG, "已创建直接管道连接")
            return Pair(serverToClient, newClientOutput)
        } catch (e: Exception) {
            Log.e(TAG, "创建客户端管道失败", e)
            return null
        }
    }
}

/** 日志级别 */
enum class LogLevel(val severity: Int) {
    DEBUG(0),
    INFO(1),
    WARNING(2),
    ERROR(3)
}

/** 日志条目 */
data class LogEntry(val timestamp: Long, val level: LogLevel, val message: String)

/** 工具参数定义 */
data class ToolParam(
        val description: String,
        val type: String = "string",
        val required: Boolean = false
)

/** 工具信息 */
@Serializable
data class ToolInfo(
        val name: String,
        val description: String,
        val parameters: List<ToolParamInfo> = emptyList()
)

/** 工具参数信息 */
@Serializable
data class ToolParamInfo(
        val name: String,
        val description: String,
        val type: String = "string",
        val required: Boolean = false
)

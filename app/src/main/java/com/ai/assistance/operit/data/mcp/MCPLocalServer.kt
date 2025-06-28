package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import java.io.File
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * MCPLocalServer - 工具注册管理器
 *
 * 负责管理MCP工具的配置和状态，不直接创建或管理MCP服务器
 */
class MCPLocalServer private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPLocalServer"
        private const val PREFS_NAME = "mcp_local_server_prefs"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_SERVER_PATH = "server_path"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_LOG_LEVEL = "log_level"

        private const val DEFAULT_PORT = 8752
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

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 日志队列
    private val logEntries = ConcurrentLinkedQueue<LogEntry>()

    // 持久化配置
    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 服务状态流
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // 启动时间戳
    private val _startTime = MutableStateFlow<Long?>(null)
    val startTime: StateFlow<Long?> = _startTime.asStateFlow()

    // 服务配置
    private val _serverPort = MutableStateFlow(prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT))
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    // 服务路径
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

    /** 初始化MCP并自动启动已部署的插件 */
    fun initAndAutoStartPlugins() {
        Log.d(TAG, "开始初始化MCP并检查自动启动插件")

        scope.launch {
            try {
                // 判断是否设置了自动启动
                val isAutoStart = autoStart.value

                if (isAutoStart) {
                    Log.d(TAG, "自动启动设置已启用，正在启动MCP服务")
                    val success = startServer()

                    if (success) {
                        Log.d(TAG, "MCP服务启动成功，等待服务就绪")
                        // 等待服务就绪
                        delay(2000)

                        // 检查服务是否确实运行中
                        val isServerRunning = isRunning.value

                        if (isServerRunning) {
                            Log.d(TAG, "MCP服务运行中，准备启动已部署插件")

                            // 使用MCPStarter启动所有已部署的插件
                            val mcpStarter = MCPStarter(context)
                            mcpStarter.startAllDeployedPlugins()
                        } else {
                            Log.e(TAG, "MCP服务未能成功启动")
                        }
                    } else {
                        Log.e(TAG, "MCP服务启动失败")
                    }
                } else {
                    Log.d(TAG, "MCP服务自动启动未启用")
                }
            } catch (e: Exception) {
                Log.e(TAG, "MCP服务自动启动过程中出错", e)
            }
        }
    }

    /** 获取默认服务路径 */
    private fun getDefaultServerPath(): String {
        val storageDir = context.getExternalFilesDir(null)
        return File(storageDir, "mcp_server").absolutePath
    }

    /**
     * 启动服务
     *
     * @return 是否成功启动
     */
    fun startServer(): Boolean {
        if (_isRunning.value) {
            Log.i(TAG, "服务已在运行")
            return true
        }

        try {
            Log.i(TAG, "启动MCP服务")

            // 注册预定义的工具
            registerTools()

            // 更新状态
            _isRunning.value = true
            _startTime.value = System.currentTimeMillis()
            _connectedClients.value = 1
            return true
        } catch (e: Exception) {
            Log.e(TAG, "启动服务失败", e)
            stopServer()
            return false
        }
    }

    /**
     * 停止服务
     *
     * @return 是否成功停止
     */
    fun stopServer(): Boolean {
        if (!_isRunning.value) {
            Log.i(TAG, "服务当前没有运行")
            return true
        }

        try {
            Log.i(TAG, "正在停止MCP服务")

            // 更新状态
            _isRunning.value = false
            _connectedClients.value = 0

            return true
        } catch (e: Exception) {
            Log.e(TAG, "停止MCP服务失败", e)
            return false
        }
    }

    /** 注册工具 */
    private fun registerTools() {
        val tools = mutableListOf<ToolInfo>()

        // 系统信息工具
        tools.add(ToolInfo("system_info", "获取系统信息"))

        // 回显工具
        tools.add(
                ToolInfo("echo", "回显输入的文本", listOf(ToolParamInfo("text", "要回显的文本", "string", true)))
        )

        // 更新已注册工具列表
        _registeredTools.value = tools

        Log.d(TAG, "已注册 ${tools.size} 个系统工具")
    }

    /** 保存服务端口设置 */
    fun saveServerPort(port: Int): Boolean {
        if (port < 1024 || port > 65535) {
            log(LogLevel.ERROR, "无效的端口号: $port")
            return false
        }

        _serverPort.value = port
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply()

        // 如果服务正在运行，需要重启才能应用新端口
        if (_isRunning.value) {
            log(LogLevel.WARNING, "端口已更改，服务需要重启以应用新设置")
        }

        return true
    }

    /** 保存服务路径设置 */
    fun saveServerPath(path: String): Boolean {
        val directory = File(path)

        // 尝试创建目录（如果不存在）
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                log(LogLevel.ERROR, "无法创建服务目录: $path")
                return false
            }
        }

        // 检查目录是否可写
        if (!directory.canWrite()) {
            log(LogLevel.ERROR, "服务目录无写入权限: $path")
            return false
        }

        _serverPath.value = path
        prefs.edit().putString(KEY_SERVER_PATH, path).apply()
        log(LogLevel.INFO, "已更新服务目录: $path")

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

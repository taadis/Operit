package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MCPLocalServer - 本地MCP服务器管理器
 * 
 * 负责管理本地MCP服务器的生命周期、配置和状态
 * 注意：目前是占位实现，未来将完成实际功能
 */
class MCPLocalServer private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPLocalServer"
        private const val PREFS_NAME = "mcp_local_server_prefs"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_SERVER_PATH = "server_path"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_LOG_LEVEL = "log_level"
        
        private const val DEFAULT_PORT = 8765
        private const val DEFAULT_LOG_LEVEL = "info"
        
        @Volatile
        private var INSTANCE: MCPLocalServer? = null
        
        fun getInstance(context: Context): MCPLocalServer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MCPLocalServer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 持久化配置
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
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
    private val _serverPath = MutableStateFlow(prefs.getString(KEY_SERVER_PATH, getDefaultServerPath()) ?: getDefaultServerPath())
    val serverPath: StateFlow<String> = _serverPath.asStateFlow()
    
    // 是否自动启动
    private val _autoStart = MutableStateFlow(prefs.getBoolean(KEY_AUTO_START, false))
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()
    
    // 日志级别
    private val _logLevel = MutableStateFlow(prefs.getString(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL) ?: DEFAULT_LOG_LEVEL)
    val logLevel: StateFlow<String> = _logLevel.asStateFlow()
    
    // 连接的客户端数
    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()
    
    // 最近的日志消息
    private val _lastLogMessage = MutableStateFlow<String?>(null)
    val lastLogMessage: StateFlow<String?> = _lastLogMessage.asStateFlow()
    
    init {
        Log.d(TAG, "MCPLocalServer 初始化，默认端口: ${_serverPort.value}")
        
        // 如果设置了自动启动，尝试启动服务器
        if (_autoStart.value) {
            // TODO: 实际启动服务器逻辑
            // 目前只是模拟
            _isRunning.value = true
            _startTime.value = System.currentTimeMillis()
        }
    }
    
    /**
     * 获取默认服务器路径
     */
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
        // TODO: 实际启动服务器逻辑
        // 目前只是模拟
        if (_isRunning.value) {
            Log.d(TAG, "服务器已经在运行中")
            return true
        }
        
        Log.d(TAG, "正在启动MCP服务器，端口: ${_serverPort.value}, 路径: ${_serverPath.value}")
        
        // 模拟启动成功
        _isRunning.value = true
        _startTime.value = System.currentTimeMillis()
        _lastLogMessage.value = "服务器已启动在端口 ${_serverPort.value}"
        
        // 模拟连接
        _connectedClients.value = 0
        
        return true
    }
    
    /**
     * 停止服务器
     * 
     * @return 是否成功停止
     */
    fun stopServer(): Boolean {
        // TODO: 实际停止服务器逻辑
        // 目前只是模拟
        if (!_isRunning.value) {
            Log.d(TAG, "服务器当前没有运行")
            return true
        }
        
        Log.d(TAG, "正在停止MCP服务器")
        
        // 模拟停止成功
        _isRunning.value = false
        _lastLogMessage.value = "服务器已停止"
        _connectedClients.value = 0
        
        return true
    }
    
    /**
     * 保存服务器端口设置
     */
    fun saveServerPort(port: Int): Boolean {
        if (port < 1024 || port > 65535) {
            Log.e(TAG, "无效的端口号: $port")
            return false
        }
        
        _serverPort.value = port
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply()
        
        // 如果服务器正在运行，需要重启才能应用新端口
        if (_isRunning.value) {
            Log.d(TAG, "端口已更改，服务器需要重启以应用新设置")
            _lastLogMessage.value = "端口已更改，需要重启服务器应用新设置"
        }
        
        return true
    }
    
    /**
     * 保存服务器路径设置
     */
    fun saveServerPath(path: String): Boolean {
        // TODO: 验证路径有效性
        _serverPath.value = path
        prefs.edit().putString(KEY_SERVER_PATH, path).apply()
        
        return true
    }
    
    /**
     * 保存自动启动设置
     */
    fun saveAutoStart(autoStart: Boolean) {
        _autoStart.value = autoStart
        prefs.edit().putBoolean(KEY_AUTO_START, autoStart).apply()
    }
    
    /**
     * 保存日志级别设置
     */
    fun saveLogLevel(level: String) {
        if (level !in listOf("debug", "info", "warning", "error")) {
            Log.e(TAG, "无效的日志级别: $level")
            return
        }
        
        _logLevel.value = level
        prefs.edit().putString(KEY_LOG_LEVEL, level).apply()
    }
    
    /**
     * 检测插件配置文件
     * 
     * @param pluginId 插件ID
     * @return 是否存在配置文件
     */
    fun hasPluginConfig(pluginId: String): Boolean {
        // TODO: 实际检测逻辑
        return false
    }
    
    /**
     * 获取插件配置
     * 
     * @param pluginId 插件ID
     * @return 配置内容JSON字符串，如果不存在返回空字符串
     */
    fun getPluginConfig(pluginId: String): String {
        // TODO: 实际获取配置逻辑
        return "{}"
    }
    
    /**
     * 保存插件配置
     * 
     * @param pluginId 插件ID
     * @param config 配置内容JSON字符串
     * @return 是否保存成功
     */
    fun savePluginConfig(pluginId: String, config: String): Boolean {
        // TODO: 实际保存配置逻辑
        _lastLogMessage.value = "已更新插件 $pluginId 的配置"
        return true
    }
    
    /**
     * 模拟连接客户端
     * 仅用于测试，将来会被删除
     */
    fun simulateClientConnection() {
        _connectedClients.value = _connectedClients.value + 1
        _lastLogMessage.value = "新客户端已连接，总计: ${_connectedClients.value}"
    }
    
    /**
     * 模拟断开客户端
     * 仅用于测试，将来会被删除
     */
    fun simulateClientDisconnection() {
        if (_connectedClients.value > 0) {
            _connectedClients.value = _connectedClients.value - 1
            _lastLogMessage.value = "客户端已断开，总计: ${_connectedClients.value}"
        }
    }
} 
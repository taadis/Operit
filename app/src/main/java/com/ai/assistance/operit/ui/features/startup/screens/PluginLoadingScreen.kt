package com.ai.assistance.operit.ui.features.startup.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.data.mcp.MCPInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.content.Context

/**
 * 表示插件加载状态的枚举
 */
enum class PluginStatus {
    WAITING,   // 等待加载
    LOADING,   // 正在加载
    SUCCESS,   // 加载成功
    FAILED     // 加载失败
}

/**
 * 表示单个插件的加载信息
 */
data class PluginInfo(
    val id: String,
    val displayName: String,
    var status: PluginStatus = PluginStatus.WAITING,
    var message: String = ""
) {
    val shortName: String get() = id.split("/").lastOrNull() ?: id
}

/**
 * 插件加载屏幕
 *
 * 在应用启动时显示插件加载进度的全屏界面
 */
@Composable
fun PluginLoadingScreen(
    isVisible: Boolean,
    progress: Float,
    message: String,
    pluginsStarted: Int,
    pluginsTotal: Int,
    pluginsList: List<PluginInfo>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            initialAlpha = 0f,
            animationSpec = androidx.compose.animation.core.tween(500)
        ),
        exit = fadeOut(
            targetAlpha = 0f,
            animationSpec = androidx.compose.animation.core.tween(800)
        )
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp)
                ) {
                    // 应用名称/Logo
                    Text(
                        text = "OPERIT",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 32.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 总体进度条
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary,
                        strokeCap = StrokeCap.Round
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 状态消息
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 总插件统计
                    Text(
                        text = "已启动: $pluginsStarted / $pluginsTotal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 插件列表
                    if (pluginsList.isNotEmpty()) {
                        Text(
                            text = "插件加载状态",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 插件加载状态列表
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 300.dp)
                        ) {
                            items(pluginsList) { plugin ->
                                PluginStatusItem(
                                    plugin = plugin,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 底部版权信息
                    Text(
                        text = "© 2023 AI Assistance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 单个插件状态项
 */
@Composable
fun PluginStatusItem(
    plugin: PluginInfo,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (plugin.status == PluginStatus.LOADING) 1f else 0f,
        label = "loading_progress"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        // 状态图标或加载指示器
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp)
        ) {
            when (plugin.status) {
                PluginStatus.WAITING -> {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                PluginStatus.LOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
                PluginStatus.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "加载成功",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                PluginStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "加载失败",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 插件名称和状态
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = plugin.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (plugin.message.isNotEmpty()) {
                Text(
                    text = plugin.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (plugin.status) {
                        PluginStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 插件加载状态管理器
 *
 * 用于管理插件加载过程中的各种状态
 */
class PluginLoadingState {
    // 进度值 (0.0f - 1.0f)
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress
    
    // 当前状态消息
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message
    
    // 已启动的插件数量
    private val _pluginsStarted = MutableStateFlow(0)
    val pluginsStarted: StateFlow<Int> = _pluginsStarted
    
    // 总插件数量
    private val _pluginsTotal = MutableStateFlow(0)
    val pluginsTotal: StateFlow<Int> = _pluginsTotal
    
    // 是否显示加载屏幕
    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible
    
    // 插件列表及其状态
    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins
    
    // 应用上下文，用于获取MCPInstaller
    private var appContext: Context? = null
    
    // 设置应用上下文
    fun setAppContext(context: Context) {
        this.appContext = context
    }
    
    /**
     * 更新进度信息
     */
    fun updateProgress(progress: Float) {
        _progress.value = progress
    }
    
    /**
     * 更新状态消息
     */
    fun updateMessage(message: String) {
        _message.value = message
    }
    
    /**
     * 更新插件统计
     */
    fun updatePluginStats(started: Int, total: Int) {
        _pluginsStarted.value = started
        _pluginsTotal.value = total
    }
    
    /**
     * 设置插件列表
     */
    fun setPlugins(pluginIds: List<String>) {
        val context = appContext
        val plugins = pluginIds.map { id ->
            // 尝试从metadata获取插件名称
            var displayName = id.split("/").lastOrNull() ?: id
            
            // 如果上下文可用，尝试从元数据获取名称
            if (context != null) {
                try {
                    val mcpInstaller = MCPInstaller(context)
                    val pluginInfo = mcpInstaller.getInstalledPluginInfo(id)
                    if (pluginInfo?.metadata != null) {
                        displayName = pluginInfo.metadata.originalName
                    }
                } catch (e: Exception) {
                    // 获取元数据失败，使用默认名称
                }
            }
            
            PluginInfo(
                id = id,
                displayName = displayName
            )
        }
        _plugins.value = plugins
        _pluginsTotal.value = plugins.size
    }
    
    /**
     * 更新插件状态
     */
    fun updatePluginStatus(pluginId: String, status: PluginStatus, message: String = "") {
        val currentPlugins = _plugins.value.toMutableList()
        val pluginIndex = currentPlugins.indexOfFirst { it.id == pluginId }
        
        if (pluginIndex >= 0) {
            val plugin = currentPlugins[pluginIndex].copy(
                status = status,
                message = message
            )
            currentPlugins[pluginIndex] = plugin
            _plugins.value = currentPlugins
            
            // 更新已启动计数
            if (status == PluginStatus.SUCCESS) {
                _pluginsStarted.value = _plugins.value.count { it.status == PluginStatus.SUCCESS }
            }
        }
    }
    
    /**
     * 开始加载指定插件
     */
    fun startLoadingPlugin(pluginId: String) {
        updatePluginStatus(pluginId, PluginStatus.LOADING, "正在加载...")
    }
    
    /**
     * 标记插件加载成功
     */
    fun setPluginSuccess(pluginId: String, message: String = "加载成功") {
        updatePluginStatus(pluginId, PluginStatus.SUCCESS, message)
    }
    
    /**
     * 标记插件加载失败
     */
    fun setPluginFailed(pluginId: String, message: String = "加载失败") {
        updatePluginStatus(pluginId, PluginStatus.FAILED, message)
    }
    
    /**
     * 显示加载屏幕
     */
    fun show() {
        _isVisible.value = true
    }
    
    /**
     * 隐藏加载屏幕
     */
    fun hide() {
        _isVisible.value = false
    }
    
    /**
     * 重置所有状态
     */
    fun reset() {
        _progress.value = 0f
        _message.value = ""
        _pluginsStarted.value = 0
        _pluginsTotal.value = 0
        _plugins.value = emptyList()
        _isVisible.value = false
    }
}

/**
 * 插件加载屏幕的预览视图
 */
@Composable
fun PluginLoadingScreenWithState(
    loadingState: PluginLoadingState,
    modifier: Modifier = Modifier
) {
    val isVisible by loadingState.isVisible.collectAsState()
    val progress by loadingState.progress.collectAsState()
    val message by loadingState.message.collectAsState()
    val pluginsStarted by loadingState.pluginsStarted.collectAsState()
    val pluginsTotal by loadingState.pluginsTotal.collectAsState()
    val plugins by loadingState.plugins.collectAsState()
    
    PluginLoadingScreen(
        isVisible = isVisible,
        progress = progress,
        message = message,
        pluginsStarted = pluginsStarted,
        pluginsTotal = pluginsTotal,
        pluginsList = plugins,
        modifier = modifier
    )
} 
 
package com.ai.assistance.operit.ui.features.packages.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPInstallProgressDialog
import com.ai.assistance.operit.ui.features.packages.components.dialogs.MCPServerDetailsDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/** MCP配置屏幕 - 专注于插件配置和一键使用 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPConfigScreen() {
        val context = LocalContext.current
        val mcpLocalServer = remember { MCPLocalServer.getInstance(context) }
        val mcpRepository = remember { MCPRepository(context) }
        val scope = rememberCoroutineScope()

        // 创建 ViewModel
        val viewModel = remember {
                MCPViewModel.Factory(mcpRepository).create(MCPViewModel::class.java)
        }

        // 安装进度和结果状态
        val installProgress by viewModel.installProgress.collectAsState()
        val installResult by viewModel.installResult.collectAsState()
        val currentInstallingPlugin by viewModel.currentServer.collectAsState()

        // MCP服务器状态
        val isServerRunning = mcpLocalServer.isRunning.collectAsState().value
        val serverStartTime = mcpLocalServer.startTime.collectAsState().value
        val serverPort = mcpLocalServer.serverPort.collectAsState().value
        val serverPath = mcpLocalServer.serverPath.collectAsState().value
        val autoStart = mcpLocalServer.autoStart.collectAsState().value
        val logLevel = mcpLocalServer.logLevel.collectAsState().value
        val connectedClients = mcpLocalServer.connectedClients.collectAsState().value
        val lastLogMessage = mcpLocalServer.lastLogMessage.collectAsState().value

        // 编辑状态
        var serverPortInput by remember { mutableStateOf(serverPort.toString()) }
        var serverPathInput by remember { mutableStateOf(serverPath) }
        var autoStartInput by remember { mutableStateOf(autoStart) }
        var logLevelInput by remember { mutableStateOf(logLevel) }

        // 插件相关状态
        val installedPlugins =
                mcpRepository.installedPluginIds.collectAsState(initial = emptySet()).value
        var selectedPluginId by remember { mutableStateOf<String?>(null) }
        var pluginConfigJson by remember { mutableStateOf("") }

        // 卸载确认对话框状态
        var showUninstallConfirmDialog by remember { mutableStateOf(false) }
        var pluginToUninstall by remember { mutableStateOf<String?>(null) }

        // 设置面板展开状态 - 默认折叠高级设置
        var showServerSettings by remember { mutableStateOf(false) }
        var showAdvancedOptions by remember { mutableStateOf(false) }

        // 提示信息状态
        val snackbarHostState = remember { SnackbarHostState() }

        // 获取选中插件的配置
        LaunchedEffect(selectedPluginId) {
                selectedPluginId?.let { pluginConfigJson = mcpLocalServer.getPluginConfig(it) }
        }

        // 格式化启动时间
        val formattedStartTime =
                remember(serverStartTime) {
                        serverStartTime?.let {
                                val sdf =
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                sdf.format(Date(it))
                        }
                                ?: "未启动"
                }

        // 运行时长
        val runtimeText =
                remember(serverStartTime, isServerRunning) {
                        if (isServerRunning && serverStartTime != null) {
                                val now = System.currentTimeMillis()
                                val runtime = now - serverStartTime
                                DateUtils.formatElapsedTime(runtime / 1000)
                        } else {
                                "00:00:00"
                        }
                }

        // 保存服务器设置
        fun saveServerSettings() {
                try {
                        // 保存端口
                        val port = serverPortInput.toIntOrNull()
                        if (port != null) {
                                mcpLocalServer.saveServerPort(port)
                        } else {
                                throw IllegalArgumentException("端口必须是有效的数字")
                        }

                        // 保存路径
                        mcpLocalServer.saveServerPath(serverPathInput)

                        // 保存自动启动
                        mcpLocalServer.saveAutoStart(autoStartInput)

                        // 保存日志级别
                        mcpLocalServer.saveLogLevel(logLevelInput)

                        scope.launch { snackbarHostState.showSnackbar("服务器设置已保存") }
                } catch (e: Exception) {
                        scope.launch { snackbarHostState.showSnackbar("保存设置失败: ${e.message}") }
                }
        }

        // 保存插件配置
        fun savePluginConfig() {
                selectedPluginId?.let { pluginId ->
                        try {
                                val result =
                                        mcpLocalServer.savePluginConfig(pluginId, pluginConfigJson)
                                if (result) {
                                        scope.launch { snackbarHostState.showSnackbar("插件配置已保存") }
                                } else {
                                        scope.launch { snackbarHostState.showSnackbar("保存插件配置失败") }
                                }
                        } catch (e: Exception) {
                                scope.launch {
                                        snackbarHostState.showSnackbar("保存插件配置错误: ${e.message}")
                                }
                        }
                }
        }

        // 卸载插件
        fun uninstallPlugin(pluginId: String) {
                val server = mcpRepository.mcpServers.value.find { it.id == pluginId }
                if (server != null) {
                        val uiServer =
                                com.ai.assistance.operit.ui.features.packages.screens.mcp.model
                                        .MCPServer(
                                                id = server.id,
                                                name = server.name,
                                                description = server.description,
                                                logoUrl = server.logoUrl,
                                                stars = server.stars,
                                                category = server.category,
                                                requiresApiKey = server.requiresApiKey,
                                                author = server.author,
                                                isVerified = server.isVerified,
                                                isInstalled = server.isInstalled,
                                                version = server.version,
                                                updatedAt = server.updatedAt,
                                                longDescription = server.longDescription,
                                                repoUrl = server.repoUrl
                                        )
                        viewModel.uninstallServer(uiServer)
                } else {
                        scope.launch { snackbarHostState.showSnackbar("找不到插件信息，无法卸载") }
                }
        }

        // 从插件ID中提取显示名称 - 使用插件管理器获取更准确的信息
        fun getPluginDisplayName(pluginId: String): String {
                // 尝试从插件元数据中获取原始名称
                val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)

                // 如果存在元数据中的原始名称，则使用它
                val originalName = pluginInfo?.getOriginalName()
                if (originalName != null && originalName.isNotBlank()) {
                        return originalName
                }

                // 回退方案：从ID中提取名称
                val parts = pluginId.split("/")
                return if (parts.size > 1) {
                        // 如果有斜杠，取最后一部分作为插件名
                        val name = parts.last()
                        // 转换 kebab-case 为更友好的显示格式
                        name.split("-").joinToString(" ") { word ->
                                word.capitalize(Locale.getDefault())
                        }
                } else if (pluginId.startsWith("official_")) {
                        // 处理官方插件ID
                        pluginId.removePrefix("official_").split("_").joinToString(" ") { word ->
                                word.capitalize(Locale.getDefault())
                        }
                } else {
                        // 没有斜杠的情况
                        pluginId
                }
        }

        // 获取插件描述
        fun getPluginDescription(pluginId: String): String? {
                val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)
                return pluginInfo?.getOriginalDescription()
        }

        // 获取插件元数据对象 - 用于弹窗显示
        fun getPluginAsServer(
                pluginId: String
        ): com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer? {
                // 先从已加载的服务器列表中查找
                val existingServer = mcpRepository.mcpServers.value.find { it.id == pluginId }

                if (existingServer != null) {
                        // 直接转换现有服务器对象
                        return com.ai.assistance.operit.ui.features.packages.screens.mcp.model
                                .MCPServer(
                                        id = existingServer.id,
                                        name = existingServer.name,
                                        description = existingServer.description,
                                        logoUrl = existingServer.logoUrl,
                                        stars = existingServer.stars,
                                        category = existingServer.category,
                                        requiresApiKey = existingServer.requiresApiKey,
                                        author = existingServer.author,
                                        isVerified = existingServer.isVerified,
                                        isInstalled = existingServer.isInstalled,
                                        version = existingServer.version,
                                        updatedAt = existingServer.updatedAt,
                                        longDescription = existingServer.longDescription,
                                        repoUrl = existingServer.repoUrl
                                )
                }

                // 如果未找到，则创建一个基本的服务器对象
                val displayName = getPluginDisplayName(pluginId)
                val description = getPluginDescription(pluginId) ?: "本地安装的插件"
                val installedPath = viewModel.getInstalledPath(pluginId)

                // 获取插件元数据
                val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)
                val author = pluginInfo?.getAuthor() ?: "本地安装"
                val version = pluginInfo?.getVersion() ?: "本地版本"
                val repoUrl = pluginInfo?.getRepoUrl() ?: ""
                val longDescription = pluginInfo?.getLongDescription() ?: description

                // 创建基本服务器对象
                return com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer(
                        id = pluginId,
                        name = displayName,
                        description = description,
                        logoUrl = "", // 本地插件可能没有图标
                        stars = 0,
                        category = "已安装插件",
                        requiresApiKey = false,
                        author = author,
                        isVerified = false,
                        isInstalled = true,
                        version = version,
                        updatedAt = "",
                        longDescription = longDescription,
                        repoUrl = repoUrl
                )
        }

        // 确认重启服务器对话框
        var showRestartConfirmDialog by remember { mutableStateOf(false) }

        // 当前选中的插件，用于显示详情对话框
        var selectedPluginForDetails by remember {
                mutableStateOf<
                        com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer?>(
                        null
                )
        }

        if (showRestartConfirmDialog) {
                AlertDialog(
                        onDismissRequest = { showRestartConfirmDialog = false },
                        title = { Text("确认重启服务器") },
                        text = { Text("重启服务器将断开所有当前连接的客户端。确定要继续吗？") },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                // 重启服务器
                                                mcpLocalServer.stopServer()
                                                mcpLocalServer.startServer()
                                                showRestartConfirmDialog = false
                                        }
                                ) { Text("确认") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showRestartConfirmDialog = false }) {
                                        Text("取消")
                                }
                        }
                )
        }

        // 卸载确认对话框
        if (showUninstallConfirmDialog && pluginToUninstall != null) {
                val displayName = getPluginDisplayName(pluginToUninstall!!)
                AlertDialog(
                        onDismissRequest = {
                                showUninstallConfirmDialog = false
                                pluginToUninstall = null
                        },
                        title = { Text("确认卸载插件") },
                        text = { Text("确定要卸载 $displayName 插件吗？卸载后配置信息将会被删除。") },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                // 执行卸载
                                                pluginToUninstall?.let {
                                                        uninstallPlugin(it)
                                                        if (selectedPluginId == it) {
                                                                selectedPluginId = null
                                                                pluginConfigJson = ""
                                                        }
                                                }
                                                showUninstallConfirmDialog = false
                                                pluginToUninstall = null
                                        }
                                ) { Text("确认卸载") }
                        },
                        dismissButton = {
                                TextButton(
                                        onClick = {
                                                showUninstallConfirmDialog = false
                                                pluginToUninstall = null
                                        }
                                ) { Text("取消") }
                        }
                )
        }

        // 显示插件详情对话框
        if (selectedPluginForDetails != null) {
                val installedPath = viewModel.getInstalledPath(selectedPluginForDetails!!.id)
                MCPServerDetailsDialog(
                        server = selectedPluginForDetails!!,
                        onDismiss = { selectedPluginForDetails = null },
                        onInstall = { /* 不需要安装功能 */},
                        onUninstall = { server ->
                                viewModel.uninstallServer(server)
                                selectedPluginForDetails = null
                        },
                        installedPath = installedPath,
                        pluginConfig = pluginConfigJson,
                        onSaveConfig = {
                                savePluginConfig()
                                scope.launch { snackbarHostState.showSnackbar("插件配置已保存") }
                        },
                        onUpdateConfig = { newConfig -> pluginConfigJson = newConfig }
                )
        }

        // 显示安装/卸载进度
        if (installProgress != null && currentInstallingPlugin != null) {
                MCPInstallProgressDialog(
                        installProgress = installProgress,
                        onDismissRequest = { viewModel.resetInstallState() },
                        result = installResult,
                        serverName = currentInstallingPlugin?.name ?: "MCP 插件"
                )
        }

        Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                                // 标题栏
                                Surface(
                                        color =
                                                MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.1f
                                                ),
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Extension,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                        text = "MCP配置",
                                                        style =
                                                                MaterialTheme.typography.titleMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        )
                                                )
                                        }
                                }

                                // 服务器状态指示器
                                Surface(
                                        color =
                                                if (isServerRunning)
                                                        MaterialTheme.colorScheme.tertiary.copy(
                                                                alpha = 0.2f
                                                        )
                                                else
                                                        MaterialTheme.colorScheme.error.copy(
                                                                alpha = 0.1f
                                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 8.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(12.dp)
                                                                        .background(
                                                                                color =
                                                                                        if (isServerRunning
                                                                                        )
                                                                                                Color.Green
                                                                                        else
                                                                                                Color.Red,
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                6.dp
                                                                                        )
                                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                        text =
                                                                if (isServerRunning) "服务器运行中"
                                                                else "服务器已停止",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                if (isServerRunning) {
                                                        Text(
                                                                text =
                                                                        "已连接: $connectedClients 个客户端",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall
                                                        )
                                                }
                                        }
                                }
                        }
                }
        ) { paddingValues ->
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .padding(paddingValues)
                                        .padding(16.dp)
                                        .verticalScroll(rememberScrollState())
                ) {
                        // 插件配置卡片 - 移到顶部
                        Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        )
                        ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text(
                                                        text = "已安装插件管理",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                )

                                                Spacer(modifier = Modifier.weight(1f))

                                                // 刷新插件列表按钮
                                                FilledTonalButton(
                                                        onClick = {
                                                                viewModel.refreshLocalPlugins()
                                                                scope.launch {
                                                                        snackbarHostState
                                                                                .showSnackbar(
                                                                                        "已刷新插件列表"
                                                                                )
                                                                }
                                                        },
                                                        modifier = Modifier.padding(start = 8.dp)
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Refresh,
                                                                contentDescription = "刷新",
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                                "刷新",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium
                                                        )
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (installedPlugins.isEmpty()) {
                                                // 无已安装插件提示，友好的引导
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.3f
                                                                                        )
                                                                        )
                                                                        .padding(vertical = 24.dp),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Column(
                                                                horizontalAlignment =
                                                                        Alignment.CenterHorizontally
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default.Info,
                                                                        contentDescription = null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                        modifier =
                                                                                Modifier.size(32.dp)
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        8.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        "还没有安装MCP插件",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodyMedium
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        "请先前往「插件市场」安装插件",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        12.dp
                                                                                )
                                                                )
                                                                FilledTonalButton(
                                                                        onClick = {
                                                                                // 这里需要导航到插件市场页面
                                                                                // 实际实现时需要添加导航逻辑
                                                                        }
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .ShoppingCart,
                                                                                contentDescription =
                                                                                        null,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                18.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                4.dp
                                                                                        )
                                                                        )
                                                                        Text("前往插件市场")
                                                                }
                                                        }
                                                }
                                        } else {
                                                // 显示已安装插件计数
                                                Text(
                                                        text = "共安装了 ${installedPlugins.size} 个插件",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )

                                                Spacer(modifier = Modifier.height(12.dp))

                                                // 显示插件网格视图
                                                LazyVerticalGrid(
                                                        columns =
                                                                GridCells.Adaptive(
                                                                        minSize = 160.dp
                                                                ),
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(12.dp),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(12.dp),
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .height(300.dp)
                                                ) {
                                                        items(installedPlugins.toList()) { pluginId
                                                                ->
                                                                PluginCard(
                                                                        pluginId = pluginId,
                                                                        displayName =
                                                                                getPluginDisplayName(
                                                                                        pluginId
                                                                                ),
                                                                        description =
                                                                                getPluginDescription(
                                                                                        pluginId
                                                                                ),
                                                                        isOfficial =
                                                                                pluginId.startsWith(
                                                                                        "official_"
                                                                                ),
                                                                        onClick = {
                                                                                // 打开详情弹窗
                                                                                selectedPluginId =
                                                                                        pluginId
                                                                                pluginConfigJson =
                                                                                        mcpLocalServer
                                                                                                .getPluginConfig(
                                                                                                        pluginId
                                                                                                )
                                                                                selectedPluginForDetails =
                                                                                        getPluginAsServer(
                                                                                                pluginId
                                                                                        )
                                                                        }
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }

                        // 一键操作卡片
                        Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        )
                        ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(
                                                text = "服务器控制",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        // 一键启动/停止按钮 - 显著且简单
                                        if (isServerRunning) {
                                                OutlinedButton(
                                                        onClick = { mcpLocalServer.stopServer() },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors =
                                                                ButtonDefaults.outlinedButtonColors(
                                                                        contentColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                )
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Stop,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                                "停止服务器",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyLarge
                                                        )
                                                }
                                        } else {
                                                Button(
                                                        onClick = { mcpLocalServer.startServer() },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors =
                                                                ButtonDefaults.buttonColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                )
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.PlayArrow,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                                "启动服务器",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyLarge
                                                        )
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // 服务器状态摘要 - 简单明了
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.5f)
                                                                )
                                                                .padding(12.dp)
                                        ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                "状态: ${if (isServerRunning) "运行中" else "已停止"}"
                                                        )

                                                        if (isServerRunning) {
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )
                                                                Text("运行时间: $runtimeText")
                                                        }

                                                        if (lastLogMessage != null) {
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        4.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        "最近消息: $lastLogMessage",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall
                                                                )
                                                        }
                                                }

                                                if (isServerRunning) {
                                                        FilledTonalButton(
                                                                onClick = {
                                                                        showRestartConfirmDialog =
                                                                                true
                                                                },
                                                                modifier =
                                                                        Modifier.align(
                                                                                Alignment
                                                                                        .CenterVertically
                                                                        )
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .Refresh,
                                                                        contentDescription = null
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(4.dp)
                                                                )
                                                                Text("重启")
                                                        }
                                                }
                                        }
                                }
                        }

                        // 高级选项卡片 - 默认折叠
                        Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        )
                        ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        // 高级选项标题栏（可点击展开/折叠）
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth().clickable {
                                                                showAdvancedOptions =
                                                                        !showAdvancedOptions
                                                        },
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                        text = "高级选项",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                Icon(
                                                        imageVector =
                                                                if (showAdvancedOptions)
                                                                        Icons.Default.ExpandLess
                                                                else Icons.Default.ExpandMore,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                        }

                                        // 高级选项内容（可折叠）
                                        if (showAdvancedOptions) {
                                                Spacer(modifier = Modifier.height(16.dp))

                                                // 服务器设置标题栏（可点击展开/折叠）
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.5f
                                                                                        )
                                                                        )
                                                                        .clickable {
                                                                                showServerSettings =
                                                                                        !showServerSettings
                                                                        }
                                                                        .padding(12.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Storage,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                                text = "服务器设置",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                        Spacer(modifier = Modifier.weight(1f))
                                                        Icon(
                                                                imageVector =
                                                                        if (showServerSettings)
                                                                                Icons.Default
                                                                                        .ExpandLess
                                                                        else
                                                                                Icons.Default
                                                                                        .ExpandMore,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }

                                                // 服务器设置内容（可折叠）
                                                if (showServerSettings) {
                                                        Spacer(modifier = Modifier.height(16.dp))

                                                        // 端口设置
                                                        OutlinedTextField(
                                                                value = serverPortInput,
                                                                onValueChange = {
                                                                        serverPortInput = it
                                                                },
                                                                label = { Text("服务器端口") },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                keyboardOptions =
                                                                        KeyboardOptions(
                                                                                keyboardType =
                                                                                        KeyboardType
                                                                                                .Number
                                                                        ),
                                                                singleLine = true
                                                        )

                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        // 路径设置
                                                        OutlinedTextField(
                                                                value = serverPathInput,
                                                                onValueChange = {
                                                                        serverPathInput = it
                                                                },
                                                                label = { Text("服务器路径") },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                singleLine = true
                                                        )

                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        // 日志级别设置
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Text(
                                                                        "日志级别:",
                                                                        modifier =
                                                                                Modifier.width(
                                                                                        80.dp
                                                                                )
                                                                )

                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        )
                                                                ) {
                                                                        val levels =
                                                                                listOf(
                                                                                        "debug",
                                                                                        "info",
                                                                                        "warning",
                                                                                        "error"
                                                                                )
                                                                        levels.forEach { level ->
                                                                                FilterChip(
                                                                                        selected =
                                                                                                logLevelInput ==
                                                                                                        level,
                                                                                        onClick = {
                                                                                                logLevelInput =
                                                                                                        level
                                                                                        },
                                                                                        label = {
                                                                                                Text(
                                                                                                        level
                                                                                                )
                                                                                        }
                                                                                )
                                                                        }
                                                                }
                                                        }

                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        // 自动启动设置
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Text("随应用启动服务器:")
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                )
                                                                Switch(
                                                                        checked = autoStartInput,
                                                                        onCheckedChange = {
                                                                                autoStartInput = it
                                                                        }
                                                                )
                                                        }

                                                        Spacer(modifier = Modifier.height(16.dp))

                                                        // 保存按钮
                                                        Button(
                                                                onClick = { saveServerSettings() },
                                                                modifier =
                                                                        Modifier.align(
                                                                                Alignment.End
                                                                        )
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default.Save,
                                                                        contentDescription = null,
                                                                        modifier =
                                                                                Modifier.size(18.dp)
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(4.dp)
                                                                )
                                                                Text("保存设置")
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // 开发测试按钮 - 在高级选项中
                                                if (isServerRunning) {
                                                        Text(
                                                                "连接测试",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                fontWeight = FontWeight.Medium
                                                        )

                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(8.dp)
                                                        ) {
                                                                OutlinedButton(
                                                                        onClick = {
                                                                                mcpLocalServer
                                                                                        .simulateClientConnection()
                                                                        },
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Add,
                                                                                contentDescription =
                                                                                        null,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                16.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                4.dp
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                "模拟客户端连接",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall
                                                                        )
                                                                }

                                                                OutlinedButton(
                                                                        onClick = {
                                                                                mcpLocalServer
                                                                                        .simulateClientDisconnection()
                                                                        },
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Close,
                                                                                contentDescription =
                                                                                        null,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                16.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                4.dp
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                "模拟客户端断开",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }

                        // 提供足够的底部空间
                        Spacer(modifier = Modifier.height(32.dp))
                }
        }
}

@Composable
private fun PluginCard(
        pluginId: String,
        displayName: String,
        description: String?,
        isOfficial: Boolean,
        onClick: () -> Unit
) {
        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onClick)
                                .border(
                                        width = 1.dp,
                                        color =
                                                MaterialTheme.colorScheme.outline.copy(
                                                        alpha = 0.5f
                                                ),
                                        shape = RoundedCornerShape(12.dp)
                                ),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
        ) {
                Column(
                        modifier = Modifier.padding(12.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                ) {
                        // 图标和标题区域
                        Column {
                                // 图标或占位符
                                Box(
                                        modifier =
                                                Modifier.size(48.dp)
                                                        .background(
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer,
                                                                shape = RoundedCornerShape(8.dp)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Extension,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 插件名称
                                Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                )

                                // 描述（如果有）
                                if (description != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                }
                        }

                        // 底部标签区域
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                // 官方标签
                                if (isOfficial) {
                                        Surface(
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.2f
                                                        ),
                                                shape = RoundedCornerShape(4.dp)
                                        ) {
                                                Text(
                                                        "官方",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier =
                                                                Modifier.padding(
                                                                        horizontal = 6.dp,
                                                                        vertical = 2.dp
                                                                ),
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                        }
                                }

                                // 右下角箭头图标
                                Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "查看详情",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                )
                        }
                }
        }
}

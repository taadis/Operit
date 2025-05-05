package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.mcp.plugins.MCPDeployer
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.ui.features.packages.components.dialogs.MCPServerDetailsDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPDeployProgressDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPInstallProgressDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPDeployViewModel
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPViewModel
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** MCP配置屏幕 - 极简风格界面，专注于插件快速部署 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPConfigScreen() {
    val context = LocalContext.current
    val mcpLocalServer = remember { MCPLocalServer.getInstance(context) }
    val mcpRepository = remember { MCPRepository(context) }
    val mcpConfigPreferences = remember {
        com.ai.assistance.operit.data.mcp.MCPConfigPreferences(context)
    }
    val scope = rememberCoroutineScope()

    // 实例化两个ViewModel
    val viewModel = remember {
        MCPViewModel.Factory(mcpRepository).create(MCPViewModel::class.java)
    }
    val deployViewModel = remember {
        MCPDeployViewModel.Factory(context, mcpRepository).create(MCPDeployViewModel::class.java)
    }

    // 状态收集
    val isServerRunning = mcpLocalServer.isRunning.collectAsState().value
    val installProgress by viewModel.installProgress.collectAsState()
    val installResult by viewModel.installResult.collectAsState()
    val currentInstallingPlugin by viewModel.currentServer.collectAsState()
    val installedPlugins =
            mcpRepository.installedPluginIds.collectAsState(initial = emptySet()).value
    val snackbarHostState = remember { SnackbarHostState() }

    // 部署状态
    val deploymentStatus by deployViewModel.deploymentStatus.collectAsState()
    val outputMessages by deployViewModel.outputMessages.collectAsState()
    val currentDeployingPlugin by deployViewModel.currentDeployingPlugin.collectAsState()

    // 服务器设置状态
    val serverPort = mcpLocalServer.serverPort.collectAsState().value
    val serverPath = mcpLocalServer.serverPath.collectAsState().value
    val autoStart = mcpLocalServer.autoStart.collectAsState().value
    val logLevel = mcpLocalServer.logLevel.collectAsState().value
    val connectedClients = mcpLocalServer.connectedClients.collectAsState().value

    // 标记是否已经执行过初始化时的自动启动
    var initialAutoStartPerformed = remember { mutableStateOf(false) }

    // 在应用启动时检查自动启动设置，而不是等待UI完全加载
    LaunchedEffect(Unit) {
        // 仅在首次加载时执行一次
        if (!initialAutoStartPerformed.value) {
            android.util.Log.d("MCPConfigScreen", "初始化 - 检查服务器状态")

            // 只记录服务器状态，不再重复启动服务器(已由 Application 中的 initAndAutoStartPlugins 控制)
            if (isServerRunning) {
                android.util.Log.d("MCPConfigScreen", "MCP服务器已在运行")
            } else {
                android.util.Log.d("MCPConfigScreen", "MCP服务器未运行")
            }

            // 读取并记录已安装的MCP插件列表，但不执行任何操作
            android.util.Log.d("MCPConfigScreen", "已安装的MCP插件列表:")
            installedPlugins.forEach { pluginId ->
                try {
                    val isAutoDeploy = mcpConfigPreferences.getAutoDeployFlow(pluginId).first()
                    android.util.Log.d("MCPConfigScreen", "插件ID: $pluginId, 自动部署: $isAutoDeploy")
                } catch (e: Exception) {
                    android.util.Log.e("MCPConfigScreen", "无法读取插件 $pluginId 的自动部署设置: ${e.message}")
                }
            }

            initialAutoStartPerformed.value = true
        }
    }

    // 编辑状态
    var serverPortInput by remember { mutableStateOf(serverPort.toString()) }
    var serverPathInput by remember { mutableStateOf(serverPath) }
    var autoStartInput by remember { mutableStateOf(autoStart) }
    var logLevelInput by remember { mutableStateOf(logLevel) }

    // 界面状态
    var selectedPluginId by remember { mutableStateOf<String?>(null) }
    var pluginConfigJson by remember { mutableStateOf("") }
    var selectedPluginForDetails by remember {
        mutableStateOf<com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer?>(
                null
        )
    }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var pluginToDeploy by remember { mutableStateOf<String?>(null) }

    // 获取选中插件的配置
    LaunchedEffect(selectedPluginId) {
        selectedPluginId?.let { pluginConfigJson = mcpLocalServer.getPluginConfig(it) }
    }

    // 从插件ID中提取显示名称
    fun getPluginDisplayName(pluginId: String): String {
        val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)
        val originalName = pluginInfo?.getOriginalName()

        if (originalName != null && originalName.isNotBlank()) {
            return originalName
        }

        return when {
            pluginId.contains("/") -> pluginId.split("/").last().replace("-", " ").capitalize()
            pluginId.startsWith("official_") ->
                    pluginId.removePrefix("official_").replace("_", " ").capitalize()
            else -> pluginId
        }
    }

    // 获取插件元数据
    fun getPluginAsServer(
            pluginId: String
    ): com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer? {
        val existingServer = mcpRepository.mcpServers.value.find { it.id == pluginId }

        if (existingServer != null) {
            return com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer(
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

        val displayName = getPluginDisplayName(pluginId)
        val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)

        return com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer(
                id = pluginId,
                name = displayName,
                description = pluginInfo?.getOriginalDescription() ?: "本地安装的插件",
                logoUrl = "",
                stars = 0,
                category = "已安装插件",
                requiresApiKey = false,
                author = pluginInfo?.getAuthor() ?: "本地安装",
                isVerified = false,
                isInstalled = true,
                version = pluginInfo?.getVersion() ?: "本地版本",
                updatedAt = "",
                longDescription = pluginInfo?.getLongDescription()
                                ?: (pluginInfo?.getOriginalDescription() ?: "本地安装的插件"),
                repoUrl = pluginInfo?.getRepoUrl() ?: ""
        )
    }

    // 部署插件
    fun deployPlugin(pluginId: String) {
        if (!isServerRunning) {
            mcpLocalServer.startServer()
        }

        // 使用ViewModel处理部署
        deployViewModel.deployPlugin(pluginId)

        // 清除部署确认状态
        pluginToDeploy = null
    }

    // 保存服务器设置
    fun saveServerSettings() {
        try {
            val port = serverPortInput.toIntOrNull()
            if (port != null) {
                mcpLocalServer.saveServerPort(port)
            } else {
                throw IllegalArgumentException("端口必须是有效的数字")
            }

            mcpLocalServer.saveServerPath(serverPathInput)

            // 检查自动启动设置是否有变化
            if (autoStartInput != autoStart) {
                android.util.Log.d("MCPConfigScreen", "自动启动设置已更改: $autoStart -> $autoStartInput")
                mcpLocalServer.saveAutoStart(autoStartInput)
            } else {
                mcpLocalServer.saveAutoStart(autoStartInput)
            }

            mcpLocalServer.saveLogLevel(logLevelInput)

            scope.launch { snackbarHostState.showSnackbar("服务器设置已保存") }
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("保存失败: ${e.message}") }
        }
    }

    // 监听部署状态变化，当成功时显示提示
    LaunchedEffect(deploymentStatus) {
        if (deploymentStatus is MCPDeployer.DeploymentStatus.Success) {
            currentDeployingPlugin?.let { pluginId ->
                snackbarHostState.showSnackbar("插件 ${getPluginDisplayName(pluginId)} 已成功部署")
            }
        }
    }

    // 部署确认对话框
    if (pluginToDeploy != null) {
        AlertDialog(
                onDismissRequest = { pluginToDeploy = null },
                title = { Text("确认部署") },
                text = { Text("将部署插件: ${getPluginDisplayName(pluginToDeploy!!)}") },
                confirmButton = {
                    TextButton(onClick = { deployPlugin(pluginToDeploy!!) }) { Text("部署") }
                },
                dismissButton = { TextButton(onClick = { pluginToDeploy = null }) { Text("取消") } }
        )
    }

    // 部署进度对话框
    if (currentDeployingPlugin != null) {
        MCPDeployProgressDialog(
                deploymentStatus = deploymentStatus,
                onDismissRequest = {
                    if (deploymentStatus !is MCPDeployer.DeploymentStatus.InProgress) {
                        deployViewModel.resetDeploymentState()
                    }
                },
                onRetry = { currentDeployingPlugin?.let { deployPlugin(it) } },
                pluginName = currentDeployingPlugin?.let { getPluginDisplayName(it) } ?: "",
                outputMessages = outputMessages
        )
    }

    // 插件详情对话框
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
                    selectedPluginId?.let { pluginId ->
                        mcpLocalServer.savePluginConfig(pluginId, pluginConfigJson)
                        scope.launch { snackbarHostState.showSnackbar("配置已保存") }
                    }
                },
                onUpdateConfig = { newConfig -> pluginConfigJson = newConfig }
        )
    }

    // 安装进度对话框
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
                TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("MCP管理")
                                Spacer(Modifier.width(8.dp))
                                Box(
                                        modifier =
                                                Modifier.size(8.dp)
                                                        .background(
                                                                color =
                                                                        if (isServerRunning)
                                                                                Color.Green
                                                                        else Color.Red,
                                                                shape = RoundedCornerShape(4.dp)
                                                        )
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                    onClick = {
                                        viewModel.refreshLocalPlugins()
                                        // 刷新插件列表后，检查并部署标记为自动部署的插件
                                        scope.launch {
                                            // 等待插件列表刷新完成
                                            delay(500)
                                            // 仅在服务器运行时执行自动部署
                                            if (isServerRunning) {
                                                installedPlugins.forEach { pluginId ->
                                                    val isAutoDeploy =
                                                            mcpConfigPreferences
                                                                    .getAutoDeployFlow(pluginId)
                                                                    .first()
                                                    if (isAutoDeploy &&
                                                                    currentDeployingPlugin == null
                                                    ) {
                                                        deployViewModel.deployPlugin(pluginId)
                                                        // 部署一个后暂停，避免同时部署多个
                                                        delay(300)
                                                    }
                                                }
                                            }
                                            snackbarHostState.showSnackbar("已刷新")
                                        }
                                    }
                            ) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
                            IconButton(onClick = { showAdvancedOptions = !showAdvancedOptions }) {
                                Icon(
                                        if (showAdvancedOptions) Icons.Default.ExpandLess
                                        else Icons.Default.ExpandMore,
                                        contentDescription = "设置"
                                )
                            }
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                )
                )
            }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            // 主界面内容
            if (installedPlugins.isEmpty()) {
                // 无插件提示
                Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        contentAlignment = Alignment.Center
                ) { Text("暂无插件，请前往插件市场安装", style = MaterialTheme.typography.bodyMedium) }
            } else {
                // 插件列表
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                                PaddingValues(bottom = if (showAdvancedOptions) 280.dp else 0.dp)
                ) {
                    items(installedPlugins.toList()) { pluginId ->
                        val autoDeployState =
                                mcpConfigPreferences
                                        .getAutoDeployFlow(pluginId)
                                        .collectAsState(initial = false)

                        // 获取插件部署成功状态
                        val deploySuccessState =
                                mcpConfigPreferences
                                        .getDeploySuccessFlow(pluginId)
                                        .collectAsState(initial = false)

                        // 获取插件最后部署时间
                        val lastDeployTimeState =
                                mcpConfigPreferences
                                        .getLastDeployTimeFlow(pluginId)
                                        .collectAsState(initial = 0L)

                        PluginListItem(
                                pluginId = pluginId,
                                displayName = getPluginDisplayName(pluginId),
                                isOfficial = pluginId.startsWith("official_"),
                                onClick = {
                                    selectedPluginId = pluginId
                                    pluginConfigJson = mcpLocalServer.getPluginConfig(pluginId)
                                    selectedPluginForDetails = getPluginAsServer(pluginId)
                                },
                                onDeploy = { pluginToDeploy = pluginId },
                                isAutoDeploy = autoDeployState.value,
                                onAutoDeployChange = { isChecked ->
                                    scope.launch {
                                        mcpConfigPreferences.saveAutoDeploy(pluginId, isChecked)
                                    }
                                },
                                isDeployed = deploySuccessState.value,
                                lastDeployTime = lastDeployTimeState.value
                        )
                        Divider(modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }

            // 高级设置面板 (从底部滑出)
            if (showAdvancedOptions) {
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .heightIn(max = 300.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    "高级设置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showAdvancedOptions = false }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        }

                        // 服务器状态和控制
                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    "服务器状态: ${if (isServerRunning) "运行中" else "已停止"}",
                                    style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))

                            if (isServerRunning) {
                                OutlinedButton(
                                        onClick = { mcpLocalServer.stopServer() },
                                        contentPadding =
                                                PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Stop,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("停止")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                        onClick = {
                                            mcpLocalServer.stopServer()
                                            mcpLocalServer.startServer()
                                        },
                                        contentPadding =
                                                PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("重启")
                                }
                            } else {
                                Button(
                                        onClick = { mcpLocalServer.startServer() },
                                        contentPadding =
                                                PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("启动")
                                }
                            }
                        }

                        if (isServerRunning) {
                            Text(
                                    "已连接客户端: $connectedClients",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        // 服务器设置
                        Text(
                                "服务器配置",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                    value = serverPortInput,
                                    onValueChange = { serverPortInput = it },
                                    label = {
                                        Text("端口", style = MaterialTheme.typography.bodySmall)
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions =
                                            KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(Modifier.width(8.dp))

                            OutlinedTextField(
                                    value = serverPathInput,
                                    onValueChange = { serverPathInput = it },
                                    label = {
                                        Text("路径", style = MaterialTheme.typography.bodySmall)
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                    textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("日志级别:")
                            Spacer(Modifier.width(8.dp))

                            val levels = listOf("debug", "info", "warning", "error")
                            levels.forEach { level ->
                                FilterChip(
                                        onClick = { logLevelInput = level },
                                        label = { Text(level) },
                                        selected = logLevelInput == level,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        }

                        Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("自动启动服务器")
                                Text(
                                        "应用启动时自动启动MCP服务器",
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                                )
                            }
                            Switch(
                                    checked = autoStartInput,
                                    onCheckedChange = { autoStartInput = it }
                            )
                        }

                        Button(
                                onClick = { saveServerSettings() },
                                modifier = Modifier.align(Alignment.End)
                        ) { Text("保存配置") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginListItem(
        pluginId: String,
        displayName: String,
        isOfficial: Boolean,
        onClick: () -> Unit,
        onDeploy: () -> Unit,
        isAutoDeploy: Boolean,
        onAutoDeployChange: (Boolean) -> Unit,
        isDeployed: Boolean = false,
        lastDeployTime: Long = 0L
) {
    // 获取屏幕尺寸以优化开关大小
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600
    val switchScale = if (isTablet) 0.8f else 0.7f

    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 插件图标
        Box(
                modifier =
                        Modifier.size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
            )

            // 添加自动部署指示徽章
            if (isAutoDeploy) {
                Box(
                        modifier =
                                Modifier.size(10.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(5.dp)
                                        )
                )
            }
        }

        // 名称和标签
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                )

                // 添加自动部署标签
                if (isAutoDeploy) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            "自动",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                    Modifier.background(
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .primaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // 添加部署成功标签
                if (isDeployed) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            "已部署",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier =
                                    Modifier.background(
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .tertiaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            if (isOfficial) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            "官方",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                    )

                    // 显示最后部署时间
                    if (lastDeployTime > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val dateStr =
                                java.text.SimpleDateFormat(
                                                "yyyy-MM-dd HH:mm",
                                                java.util.Locale.getDefault()
                                        )
                                        .format(java.util.Date(lastDeployTime))
                        Text(
                                "最近部署: $dateStr",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                        )
                        )
                    }
                }
            } else if (lastDeployTime > 0) {
                // 非官方插件也显示最后部署时间
                val dateStr =
                        java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm",
                                        java.util.Locale.getDefault()
                                )
                                .format(java.util.Date(lastDeployTime))
                Text(
                        "最近部署: $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // 部署按钮 - 根据部署状态变化样式
        TextButton(
                onClick = onDeploy,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp),
                colors =
                        ButtonDefaults.textButtonColors(
                                contentColor =
                                        if (isDeployed) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary
                        )
        ) { Text(if (isDeployed) "重新部署" else "部署") }

        Spacer(modifier = Modifier.width(4.dp))

        // 自动部署开关 - 紧凑样式
        Box(modifier = Modifier.padding(end = 4.dp), contentAlignment = Alignment.Center) {
            Switch(
                    checked = isAutoDeploy,
                    onCheckedChange = onAutoDeployChange,
                    modifier =
                            Modifier.scale(switchScale) // 根据屏幕尺寸调整缩放比例
                                    .padding(0.dp),
                    colors =
                            SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            )
        }
    }
}

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
import androidx.compose.material.icons.filled.Download
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
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPDeployer
import com.ai.assistance.operit.ui.features.packages.components.dialogs.MCPServerDetailsDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPDeployProgressDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPInstallProgressDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPDeployViewModel
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPViewModel
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ai.assistance.operit.data.mcp.InstallResult
import com.ai.assistance.operit.data.mcp.InstallProgress

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
    val environmentVariables by deployViewModel.environmentVariables.collectAsState()

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
                    val isEnabled = mcpConfigPreferences.getPluginEnabledFlow(pluginId).first()
                    android.util.Log.d("MCPConfigScreen", "插件ID: $pluginId, 已启用: $isEnabled")
                } catch (e: Exception) {
                    android.util.Log.e("MCPConfigScreen", "无法读取插件 $pluginId 的启用状态: ${e.message}")
                }
            }

            initialAutoStartPerformed.value = true
        }
    }

    // 界面状态
    var selectedPluginId by remember { mutableStateOf<String?>(null) }
    var pluginConfigJson by remember { mutableStateOf("") }
    var selectedPluginForDetails by remember {
        mutableStateOf<com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer?>(
                null
        )
    }
    var pluginToDeploy by remember { mutableStateOf<String?>(null) }

    // 添加新的状态变量来跟踪对话框展示
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCustomCommandsDialog by remember { mutableStateOf(false) }

    // 添加导入对话框状态
    var showImportDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var pluginNameInput by remember { mutableStateOf("") }
    var pluginDescriptionInput by remember { mutableStateOf("") }
    var pluginAuthorInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    // 新增：导入方式选择和压缩包路径
    var importTabIndex by remember { mutableStateOf(0) } // 0: 仓库导入, 1: 压缩包导入
    var zipFilePath by remember { mutableStateOf("") }
    var showFilePickerDialog by remember { mutableStateOf(false) }

    // 新增：远程服务相关状态
    var remoteHostInput by remember { mutableStateOf("") }
    var remotePortInput by remember { mutableStateOf("") }

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

    // 监听部署状态变化，当成功时显示提示
    LaunchedEffect(deploymentStatus) {
        if (deploymentStatus is MCPDeployer.DeploymentStatus.Success) {
            currentDeployingPlugin?.let { pluginId ->
                snackbarHostState.showSnackbar("插件 ${getPluginDisplayName(pluginId)} 已成功部署")
            }
        }
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

    // 部署确认对话框 - 新增
    if (showConfirmDialog && pluginToDeploy != null) {
        com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPDeployConfirmDialog(
                pluginName = getPluginDisplayName(pluginToDeploy!!),
                onDismissRequest = {
                    showConfirmDialog = false
                    pluginToDeploy = null
                },
                onConfirm = {
                    // 直接部署 - 获取命令后立即执行部署
                    if (!isServerRunning) {
                        mcpLocalServer.startServer()
                    }

                    // 在协程内部复制当前的pluginId避免外部状态变化导致空指针异常
                    val pluginId = pluginToDeploy!!
                    
                    // 确保已获取命令
                    scope.launch {
                        if (deployViewModel.generatedCommands.value.isEmpty()) {
                            deployViewModel.getDeployCommands(pluginId)
                        }
                        // 使用默认命令部署
                        deployViewModel.deployPlugin(pluginId)
                    }

                    // 重置状态
                    showConfirmDialog = false
                    pluginToDeploy = null
                },
                onCustomize = {
                    // 先关闭确认对话框，然后显示命令编辑对话框
                    showConfirmDialog = false
                    showCustomCommandsDialog = true

                    // 立即尝试获取命令，不要等到命令编辑对话框渲染后再获取
                    scope.launch {
                        val pluginId = pluginToDeploy
                        if (pluginId != null && deployViewModel.generatedCommands.value.isEmpty()) {
                            deployViewModel.getDeployCommands(pluginId)
                        }
                    }
                }
        )
    }

    // 命令编辑对话框 - 修改为只在选择自定义后显示
    if (showCustomCommandsDialog && pluginToDeploy != null) {
        // 检查命令是否已生成
        val commandsAvailable =
                deployViewModel.generatedCommands.collectAsState().value.isNotEmpty()

        // 显示命令编辑对话框，暂时移除isLoading参数
        com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPCommandsEditDialog(
                pluginName = getPluginDisplayName(pluginToDeploy!!),
                commands = deployViewModel.generatedCommands.value,
                // 注释掉isLoading参数直到MCPCommandsEditDialog.kt的修改生效
                // isLoading = !commandsAvailable,
                onDismissRequest = {
                    showCustomCommandsDialog = false
                    pluginToDeploy = null
                },
                onConfirm = { customCommands ->
                    // 使用自定义命令部署
                    if (!isServerRunning) {
                        mcpLocalServer.startServer()
                    }
                    
                    // 在协程内部复制插件ID以避免空指针异常
                    val pluginId = pluginToDeploy!!
                    deployViewModel.deployPluginWithCommands(pluginId, customCommands)

                    // 重置状态
                    showCustomCommandsDialog = false
                    pluginToDeploy = null
                }
        )

        // 如果还没有获取命令，异步获取
        LaunchedEffect(pluginToDeploy) {
            if (!commandsAvailable) {
                deployViewModel.getDeployCommands(pluginToDeploy!!)
            }
        }
    }

    // 部署进度对话框
    if (currentDeployingPlugin != null) {
        MCPDeployProgressDialog(
                deploymentStatus = deploymentStatus,
                onDismissRequest = { deployViewModel.resetDeploymentState() },
                onRetry = {
                    currentDeployingPlugin?.let { pluginId ->
                        deployViewModel.deployPlugin(pluginId)
                    }
                },
                pluginName = currentDeployingPlugin?.let { getPluginDisplayName(it) } ?: "",
                outputMessages = outputMessages,
                environmentVariables = environmentVariables,
                onEnvironmentVariablesChange = { newEnvVars ->
                    deployViewModel.setEnvironmentVariables(newEnvVars)
                }
        )
    }

    // 安装进度对话框
    if (installProgress != null && currentInstallingPlugin != null) {
        // 将值存储在本地变量中以避免智能转换问题
        val currentInstallResult = installResult
        // 判断当前是否是卸载操作
        val isUninstallOperation = 
            if (currentInstallResult is InstallResult.Success) {
                currentInstallResult.pluginPath.isEmpty()
            } else {
                false
            }
        
        MCPInstallProgressDialog(
                installProgress = installProgress,
                onDismissRequest = { viewModel.resetInstallState() },
                result = installResult,
                serverName = currentInstallingPlugin?.name ?: "MCP 插件",
                // 添加操作类型参数：卸载/安装
                operationType = if (isUninstallOperation) "卸载" else "安装"
        )
    }

    // 导入插件对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入或连接MCP服务") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 添加顶部导入方式选择
                    TabRow(selectedTabIndex = importTabIndex) {
                        Tab(
                            selected = importTabIndex == 0,
                            onClick = { importTabIndex = 0 },
                            text = { Text("从仓库导入") }
                        )
                        Tab(
                            selected = importTabIndex == 1,
                            onClick = { importTabIndex = 1 },
                            text = { Text("从压缩包导入") }
                        )
                        Tab(
                            selected = importTabIndex == 2,
                            onClick = { importTabIndex = 2 },
                            text = { Text("连接远程服务") }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    when (importTabIndex) {
                        0 -> {
                            // 从仓库导入
                            Text("请输入插件仓库链接和相关信息", style = MaterialTheme.typography.bodyMedium)
                            
                            OutlinedTextField(
                                value = repoUrlInput,
                                onValueChange = { repoUrlInput = it },
                                label = { Text("仓库链接") },
                                placeholder = { Text("https://github.com/username/repo") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                        }
                        1 -> {
                            // 从压缩包导入
                            Text("请选择MCP插件压缩包", style = MaterialTheme.typography.bodyMedium)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = zipFilePath,
                                    onValueChange = { /* 只读 */ },
                                    label = { Text("插件压缩包") },
                                    placeholder = { Text("选择.zip文件") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    readOnly = true
                                )
                                
                                IconButton(onClick = { showFilePickerDialog = true }) {
                                    Icon(Icons.Default.Folder, contentDescription = "选择文件")
                                }
                            }
                        }
                        2 -> {
                            // 连接远程服务
                            Text("请输入远程服务地址和相关信息", style = MaterialTheme.typography.bodyMedium)

                            OutlinedTextField(
                                value = remoteHostInput,
                                onValueChange = { remoteHostInput = it },
                                label = { Text("主机地址") },
                                placeholder = { Text("127.0.0.1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )

                            OutlinedTextField(
                                value = remotePortInput,
                                onValueChange = { remotePortInput = it.filter { char -> char.isDigit() } },
                                label = { Text("端口") },
                                placeholder = { Text("8752") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("服务元数据", style = MaterialTheme.typography.titleSmall)
                    
                    OutlinedTextField(
                        value = pluginNameInput,
                        onValueChange = { pluginNameInput = it },
                        label = { Text("插件名称") },
                        placeholder = { Text("我的MCP插件") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = pluginDescriptionInput,
                        onValueChange = { pluginDescriptionInput = it },
                        label = { Text("插件描述") },
                        placeholder = { Text("这是一个MCP插件") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    
                    OutlinedTextField(
                        value = pluginAuthorInput,
                        onValueChange = { pluginAuthorInput = it },
                        label = { Text("作者") },
                        placeholder = { Text("作者名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isRemote = importTabIndex == 2
                        val isRepoImport = importTabIndex == 0 && repoUrlInput.isNotBlank() && pluginNameInput.isNotBlank()
                        val isZipImport = importTabIndex == 1 && zipFilePath.isNotBlank() && pluginNameInput.isNotBlank()
                        val isRemoteConnect = isRemote && remoteHostInput.isNotBlank() && remotePortInput.isNotBlank() && pluginNameInput.isNotBlank()

                        if (isRepoImport || isZipImport || isRemoteConnect) {
                            isImporting = true
                            // 生成一个唯一的ID
                            val importId = if (isRemote) "remote_${pluginNameInput.replace(" ", "_").lowercase()}" else "import_${java.util.UUID.randomUUID().toString().substring(0, 8)}"
                            
                            // 创建服务器对象
                            val server = com.ai.assistance.operit.data.mcp.MCPServer(
                                id = importId,
                                name = pluginNameInput,
                                description = pluginDescriptionInput,
                                logoUrl = "",
                                stars = 0,
                                category = if(isRemote) "远程服务" else "导入插件",
                                requiresApiKey = false,
                                author = pluginAuthorInput,
                                isVerified = false,
                                isInstalled = isRemote, // 远程服务视为"已安装"
                                version = "1.0.0",
                                updatedAt = "",
                                longDescription = pluginDescriptionInput,
                                repoUrl = if (importTabIndex == 0) repoUrlInput else "",
                                type = if(isRemote) "remote" else "local",
                                host = if(isRemote) remoteHostInput else null,
                                port = if(isRemote) remotePortInput.toIntOrNull() else null
                            )
                            
                            if(isRemote){
                                // 对于远程服务，直接保存到仓库
                                viewModel.addRemoteServer(server)
                                scope.launch {
                                    snackbarHostState.showSnackbar("远程服务 ${server.name} 已添加")
                                }
                            } else {
                                // 本地插件走安装流程
                                val mcpServer = com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer(
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
                                    repoUrl = server.repoUrl,
                                    type = server.type,
                                    host = server.host,
                                    port = server.port
                                )

                                if (importTabIndex == 0) {
                                    viewModel.installServer(mcpServer)
                                } else {
                                    viewModel.installServerFromZip(mcpServer, zipFilePath)
                                }
                            }
                            
                            // 清空输入并关闭对话框
                            repoUrlInput = ""
                            pluginNameInput = ""
                            pluginDescriptionInput = ""
                            pluginAuthorInput = ""
                            zipFilePath = ""
                            remoteHostInput = ""
                            remotePortInput = ""
                            showImportDialog = false
                            isImporting = false
                        } else {
                            val errorMessage = when (importTabIndex) {
                                0 -> "请至少输入仓库链接和插件名称"
                                1 -> "请选择压缩包并输入插件名称"
                                else -> "请完整输入远程服务信息和名称"
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar(errorMessage)
                            }
                        }
                    },
                    enabled = !isImporting && 
                             ((importTabIndex == 0 && repoUrlInput.isNotBlank() && pluginNameInput.isNotBlank()) ||
                              (importTabIndex == 1 && zipFilePath.isNotBlank() && pluginNameInput.isNotBlank()) ||
                              (importTabIndex == 2 && remoteHostInput.isNotBlank() && remotePortInput.isNotBlank() && pluginNameInput.isNotBlank()))
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if(importTabIndex == 2) "连接" else "导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 文件选择对话框
    if (showFilePickerDialog) {
        AlertDialog(
            onDismissRequest = { showFilePickerDialog = false },
            title = { Text("选择MCP插件压缩包") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("请使用系统文件选择器选择一个.zip格式的MCP插件压缩包")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            // 触发系统文件选择器
                            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT)
                            intent.type = "application/zip"
                            val chooser = android.content.Intent.createChooser(intent, "选择MCP插件压缩包")
                            
                            // 使用Activity启动选择器
                            val activity = context as? android.app.Activity
                            activity?.startActivityForResult(chooser, 1001)
                            
                            // 设置监听器接收选择结果
                            val activityResultCallback = object : androidx.activity.result.ActivityResultCallback<androidx.activity.result.ActivityResult> {
                                override fun onActivityResult(result: androidx.activity.result.ActivityResult) {
                                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                                        result.data?.data?.let { uri ->
                                            // 获取文件路径
                                            val cursor = context.contentResolver.query(uri, null, null, null, null)
                                            cursor?.use {
                                                if (it.moveToFirst()) {
                                                    val displayName = it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                                                    zipFilePath = displayName
                                                    
                                                    // 保存URI以便后续处理
                                                    viewModel.setSelectedZipUri(uri)
                                                }
                                            }
                                        }
                                    }
                                    showFilePickerDialog = false
                                }
                            }
                            
                            // 注册回调
                            val registry = (context as androidx.activity.ComponentActivity).activityResultRegistry
                            val launcher = registry.register("zip_picker", androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), activityResultCallback)
                            launcher.launch(chooser)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("打开文件选择器")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFilePickerDialog = false }) {
                    Text("取消")
                }
            }
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
                                        showImportDialog = true
                                    }
                            ) { Icon(Icons.Default.Download, contentDescription = "导入") }
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
                        modifier = Modifier.fillMaxSize()
                ) {
                    items(installedPlugins.toList()) { pluginId ->
                        // 获取插件部署成功状态
                        val deploySuccessState =
                                mcpConfigPreferences
                                        .getDeploySuccessFlow(pluginId)
                                        .collectAsState(initial = false)

                        // 获取插件启用状态
                        val pluginEnabledState =
                                mcpConfigPreferences
                                        .getPluginEnabledFlow(pluginId)
                                        .collectAsState(initial = true)

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
                                onDeploy = {
                                    pluginToDeploy = pluginId
                                    showConfirmDialog = true // 显示确认对话框而不是直接进入命令编辑
                                },
                                isEnabled = pluginEnabledState.value,
                                onEnabledChange = { isChecked ->
                                    scope.launch {
                                        mcpConfigPreferences.savePluginEnabled(pluginId, isChecked)
                                    }
                                },
                                isDeployed = deploySuccessState.value,
                                lastDeployTime = lastDeployTimeState.value
                        )
                        Divider(modifier = Modifier.padding(horizontal = 4.dp))
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
        isEnabled: Boolean,
        onEnabledChange: (Boolean) -> Unit,
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

            // 添加启用状态指示徽章 - 右上角
            if (isEnabled) {
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
            
            // 添加部署状态指示徽章 - 右下角
            if (isDeployed) {
                Box(
                        modifier =
                                Modifier.size(10.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 4.dp, y = 4.dp)
                                        .background(
                                                color = MaterialTheme.colorScheme.tertiary,
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

                // 移除文本标签，改为使用角标指示
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

        // 启用/禁用开关 - 紧凑样式
        Box(modifier = Modifier.padding(end = 4.dp), contentAlignment = Alignment.Center) {
            Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange,
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

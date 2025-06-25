package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.permissions.ToolCategory
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPermissionSettingsScreen(
    navigateBack: () -> Unit
) {
    val context = LocalContext.current
    val toolPermissionSystem = remember { ToolPermissionSystem.getInstance(context) }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    
    // 设置权限系统的颜色方案
    LaunchedEffect(colorScheme) {
        toolPermissionSystem.setColorScheme(colorScheme)
    }
    
    // 收集工具权限设置
    val masterSwitch = toolPermissionSystem.masterSwitchFlow.collectAsState(initial = PermissionLevel.ASK).value
    val systemOperationPermission = toolPermissionSystem.systemOperationPermissionFlow.collectAsState(
        initial = ToolCategory.getDefaultPermissionLevel(ToolCategory.SYSTEM_OPERATION)
    ).value
    val networkPermission = toolPermissionSystem.networkPermissionFlow.collectAsState(
        initial = ToolCategory.getDefaultPermissionLevel(ToolCategory.NETWORK)
    ).value
    val uiAutomationPermission = toolPermissionSystem.uiAutomationPermissionFlow.collectAsState(
        initial = ToolCategory.getDefaultPermissionLevel(ToolCategory.UI_AUTOMATION)
    ).value
    val fileReadPermission = toolPermissionSystem.fileReadPermissionFlow.collectAsState(
        initial = ToolCategory.getDefaultPermissionLevel(ToolCategory.FILE_READ)
    ).value
    val fileWritePermission = toolPermissionSystem.fileWritePermissionFlow.collectAsState(
        initial = ToolCategory.getDefaultPermissionLevel(ToolCategory.FILE_WRITE)
    ).value
    
    // 可变状态
    var masterSwitchInput by remember { mutableStateOf(masterSwitch) }
    var systemOperationPermissionInput by remember { mutableStateOf(systemOperationPermission) }
    var networkPermissionInput by remember { mutableStateOf(networkPermission) }
    var uiAutomationPermissionInput by remember { mutableStateOf(uiAutomationPermission) }
    var fileReadPermissionInput by remember { mutableStateOf(fileReadPermission) }
    var fileWritePermissionInput by remember { mutableStateOf(fileWritePermission) }
    
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    
    // 当设置变化时更新本地状态
    LaunchedEffect(masterSwitch, systemOperationPermission, networkPermission, 
                 uiAutomationPermission, fileReadPermission, fileWritePermission) {
        masterSwitchInput = masterSwitch
        systemOperationPermissionInput = systemOperationPermission
        networkPermissionInput = networkPermission
        uiAutomationPermissionInput = uiAutomationPermission
        fileReadPermissionInput = fileReadPermission
        fileWritePermissionInput = fileWritePermission
    }
    
     Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 全局权限开关
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "全局权限开关",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Text(
                        text = "此设置控制所有AI工具的权限。设置为\"询问\"时，将对所有工具进行权限询问；设置为\"允许\"时，将使用各类工具的具体权限设置。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    PermissionLevelSelector(
                        selectedLevel = masterSwitchInput,
                        showCautionOption = true,
                        onLevelSelected = { level ->
                            masterSwitchInput = level
                            
                            // 保存设置
                            scope.launch {
                                toolPermissionSystem.saveMasterSwitch(level)
                                showSaveSuccessMessage = true
                            }
                        }
                    )
                }
            }
            
            // 系统操作权限
            PermissionCategoryCard(
                title = "系统操作权限",
                description = "系统设置修改、应用安装/卸载、启动/停止应用等",
                currentLevel = systemOperationPermissionInput,
                showCautionOption = true,
                onLevelSelected = { level ->
                    systemOperationPermissionInput = level
                    
                    // 保存设置
                    scope.launch {
                        toolPermissionSystem.saveSystemOperationPermission(level)
                        showSaveSuccessMessage = true
                    }
                }
            )
            
            // 网络访问权限
            PermissionCategoryCard(
                title = "网络访问权限",
                description = "网页获取、HTTP请求、网络搜索、文件下载等",
                currentLevel = networkPermissionInput,
                showCautionOption = true,
                onLevelSelected = { level ->
                    networkPermissionInput = level
                    
                    // 保存设置
                    scope.launch {
                        toolPermissionSystem.saveNetworkPermission(level)
                        showSaveSuccessMessage = true
                    }
                }
            )
            
            // UI自动化权限
            PermissionCategoryCard(
                title = "UI自动化权限",
                description = "点击操作、输入文本、按键、滑动等",
                currentLevel = uiAutomationPermissionInput,
                showCautionOption = true,
                onLevelSelected = { level ->
                    uiAutomationPermissionInput = level
                    
                    // 保存设置
                    scope.launch {
                        toolPermissionSystem.saveUIAutomationPermission(level)
                        showSaveSuccessMessage = true
                    }
                }
            )
            
            // 文件读取权限
            PermissionCategoryCard(
                title = "文件读取权限",
                description = "列出文件、读取文件内容、文件信息获取等",
                currentLevel = fileReadPermissionInput,
                showCautionOption = true,
                onLevelSelected = { level ->
                    fileReadPermissionInput = level
                    
                    // 保存设置
                    scope.launch {
                        toolPermissionSystem.saveFileReadPermission(level)
                        showSaveSuccessMessage = true
                    }
                }
            )
            
            // 文件写入权限
            PermissionCategoryCard(
                title = "文件写入权限",
                description = "文件写入、删除、移动、压缩/解压等",
                currentLevel = fileWritePermissionInput,
                showCautionOption = true,
                onLevelSelected = { level ->
                    fileWritePermissionInput = level
                    
                    // 保存设置
                    scope.launch {
                        toolPermissionSystem.saveFileWritePermission(level)
                        showSaveSuccessMessage = true
                    }
                }
            )
            
            // 全部保存按钮
            Button(
                onClick = {
                    scope.launch {
                        toolPermissionSystem.saveAllPermissions(
                            masterSwitchInput,
                            systemOperationPermissionInput,
                            networkPermissionInput,
                            uiAutomationPermissionInput,
                            fileReadPermissionInput,
                            fileWritePermissionInput
                        )
                        showSaveSuccessMessage = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text("保存所有设置")
            }
            
            if (showSaveSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showSaveSuccessMessage = false
                }
                
                Text(
                    text = "设置已保存",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
}

@Composable
fun PermissionCategoryCard(
    title: String,
    description: String,
    currentLevel: PermissionLevel,
    showCautionOption: Boolean = true,
    onLevelSelected: (PermissionLevel) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            PermissionLevelSelector(
                selectedLevel = currentLevel,
                showCautionOption = showCautionOption,
                onLevelSelected = onLevelSelected
            )
        }
    }
}

@Composable
fun PermissionLevelSelector(
    selectedLevel: PermissionLevel,
    showCautionOption: Boolean = true,
    onLevelSelected: (PermissionLevel) -> Unit
) {
    val options = if (showCautionOption) {
        PermissionLevel.values().toList()
    } else {
        PermissionLevel.values().filter { it != PermissionLevel.CAUTION }
    }
    
    Column {
        options.forEach { level ->
            val levelName = when (level) {
                PermissionLevel.ALLOW -> "允许 (自动执行，不询问)"
                PermissionLevel.CAUTION -> "警惕 (仅对危险操作询问)"
                PermissionLevel.ASK -> "询问 (总是询问)"
                PermissionLevel.FORBID -> "禁止 (不允许执行)"
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedLevel == level,
                    onClick = { onLevelSelected(level) }
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = levelName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
} 
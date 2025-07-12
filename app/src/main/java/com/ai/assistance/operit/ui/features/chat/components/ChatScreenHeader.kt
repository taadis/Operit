package com.ai.assistance.operit.ui.features.chat.components

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import kotlinx.coroutines.launch

@Composable
fun useFloatingWindowLauncher(
    actualViewModel: ChatViewModel,
    permissionLauncher: ActivityResultLauncher<String>
): () -> Unit {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    return {
        actualViewModel.onFloatingButtonClick(
            FloatingMode.WINDOW,
            permissionLauncher,
            colorScheme,
            typography
        )
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun ChatScreenHeader(
        actualViewModel: ChatViewModel,
        showChatHistorySelector: Boolean,
        chatHistories: List<ChatHistory>,
        currentChatId: String
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val currentChatTitle = chatHistories.find { it.id == currentChatId }?.title
    val scope = rememberCoroutineScope()

    // The permission launcher needs to be at a level where it can be remembered,
    // usually the screen level, but we pass it down here.
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // After permission is granted, the ViewModel's logic (which is already running) will proceed.
                // We might need to re-trigger the action if the ViewModel doesn't handle this state internally.
                // For now, we assume the ViewModel handles it.
                actualViewModel.launchFloatingModeIn(FloatingMode.WINDOW, colorScheme, typography)
            } else {
                actualViewModel.showToast("麦克风权限被拒绝")
            }
        }


    val launchFloatingWindow = useFloatingWindowLauncher(actualViewModel, permissionLauncher)

    // 获取是否显示模型选择器的设置
    val apiPreferences = remember { ApiPreferences(context) }
    val showModelSelector by apiPreferences.showModelSelectorFlow.collectAsState(initial = false)

    // 获取功能配置管理器
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }

    // 获取当前配置映射和可用配置摘要
    val configMapping by
            functionalConfigManager.functionConfigMappingFlow.collectAsState(initial = emptyMap())
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    var showModelDropdown by remember { mutableStateOf(false) }

    // 加载配置摘要
    androidx.compose.runtime.LaunchedEffect(Unit) {
        configSummaries = modelConfigManager.getAllConfigSummaries()
    }

    // 获取当前聊天功能的配置ID
    val currentConfigId =
            configMapping[FunctionType.CHAT] ?: FunctionalConfigManager.DEFAULT_CONFIG_ID
    val currentConfig = configSummaries.find { it.id == currentConfigId }

    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 左侧：聊天历史按钮
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChatHeader(
                    showChatHistorySelector = showChatHistorySelector,
                    onToggleChatHistorySelector = { actualViewModel.toggleChatHistorySelector() },
                    currentChatTitle = currentChatTitle,
                    modifier = Modifier,
                    isFloatingMode = actualViewModel.isFloatingMode.value,
                    onLaunchFloatingWindow = launchFloatingWindow
            )
        }

        // 右侧：模型选择器和统计信息，水平排列
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            // 如果启用了模型选择器，添加模型选择按钮
            if (showModelSelector && currentConfig != null) {
                Box {
                    // 模型选择（与统计信息相同样式）
                    Row(
                            modifier =
                                    Modifier.background(
                                                    color =
                                                            MaterialTheme.colorScheme.surface.copy(
                                                                    alpha = 0.8f
                                                            ),
                                                    shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable { showModelDropdown = !showModelDropdown }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 使用与StatItem相同的布局风格
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.widthIn(max = 60.dp)
                        ) {
                            Text(
                                    text = stringResource(R.string.model),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                    text = currentConfig.name,
                                    style =
                                            MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight =
                                                            androidx.compose.ui.text.font.FontWeight
                                                                    .Bold
                                            ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 添加一个小图标指示可展开
                        Icon(
                                imageVector =
                                        if (showModelDropdown) Icons.Filled.KeyboardArrowUp
                                        else Icons.Filled.KeyboardArrowDown,
                                contentDescription =
                                        if (showModelDropdown)
                                                stringResource(R.string.wizard_collapse)
                                        else stringResource(R.string.wizard_expand),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }

                    // 模型选择下拉菜单
                    DropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false },
                            modifier =
                                    Modifier.width(IntrinsicSize.Min)
                                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        configSummaries.forEach { config ->
                            val isSelected = config.id == currentConfigId

                            DropdownMenuItem(
                                    text = {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                Text(
                                                        text = config.name,
                                                        style =
                                                                MaterialTheme.typography.bodyMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        if (isSelected
                                                                                        )
                                                                                                androidx.compose
                                                                                                        .ui
                                                                                                        .text
                                                                                                        .font
                                                                                                        .FontWeight
                                                                                                        .Bold
                                                                                        else
                                                                                                androidx.compose
                                                                                                        .ui
                                                                                                        .text
                                                                                                        .font
                                                                                                        .FontWeight
                                                                                                        .Normal
                                                                        ),
                                                        color =
                                                                if (isSelected)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                )

                                                Text(
                                                        text = config.modelName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            if (isSelected) {
                                                Icon(
                                                        imageVector =
                                                                Icons.Default.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        scope.launch {
                                            functionalConfigManager.setConfigForFunction(
                                                    FunctionType.CHAT,
                                                    config.id
                                            )
                                            // 刷新聊天服务
                                            EnhancedAIService.refreshServiceForFunction(
                                                    context,
                                                    FunctionType.CHAT
                                            )
                                            showModelDropdown = false
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // 统计信息
            val contextWindowSize = actualViewModel.contextWindowSize.value
            val inputTokenCount = actualViewModel.inputTokenCount.value
            val outputTokenCount = actualViewModel.outputTokenCount.value
            val totalTokenCount = inputTokenCount + outputTokenCount

            // 使用一个状态来跟踪是否显示详细信息
            val (showDetailedStats, setShowDetailedStats) = remember { mutableStateOf(false) }

            Box {
                // 主要显示（只有总计）
                Row(
                        modifier =
                                Modifier.background(
                                                color =
                                                        MaterialTheme.colorScheme.surface.copy(
                                                                alpha = 0.8f
                                                        ),
                                                shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { setShowDetailedStats(!showDetailedStats) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatItem(
                            label = stringResource(R.string.total),
                            value = "$totalTokenCount",
                            isHighlighted = true
                    )

                    // 添加一个小图标指示可展开
                    Icon(
                            imageVector =
                                    if (showDetailedStats) Icons.Filled.KeyboardArrowUp
                                    else Icons.Filled.KeyboardArrowDown,
                            contentDescription =
                                    if (showDetailedStats) stringResource(R.string.wizard_collapse)
                                    else stringResource(R.string.wizard_expand),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }

                // 简化的下拉框
                DropdownMenu(
                        expanded = showDetailedStats,
                        onDismissRequest = { setShowDetailedStats(false) },
                        modifier =
                                Modifier.width(IntrinsicSize.Min)
                                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.context_window, contextWindowSize))
                            },
                            onClick = {},
                            enabled = false
                    )
                    DropdownMenuItem(
                            text = { Text(stringResource(R.string.input_tokens, inputTokenCount)) },
                            onClick = {},
                            enabled = false
                    )
                    DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.output_tokens, outputTokenCount))
                            },
                            onClick = {},
                            enabled = false
                    )
                    DropdownMenuItem(
                            text = {
                                Text(
                                        stringResource(R.string.total_tokens, totalTokenCount),
                                        style =
                                                MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight =
                                                                androidx.compose.ui.text.font
                                                                        .FontWeight.Bold
                                                ),
                                        color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {},
                            enabled = false
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, isHighlighted: Boolean = false) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
                text = value,
                style =
                        MaterialTheme.typography.labelMedium.copy(
                                fontWeight =
                                        if (isHighlighted)
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                        else androidx.compose.ui.text.font.FontWeight.Normal
                        ),
                color =
                        if (isHighlighted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ChatSettingsBar(
        actualViewModel: ChatViewModel,
        memoryOptimization: Boolean,
        masterPermissionLevel: PermissionLevel,
        enableAiPlanning: Boolean
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(
                                    color =
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.1f
                                            )
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 自动批准开关 - 左侧第一个开关
        Row(
                modifier =
                        Modifier.background(
                                        color =
                                                if (masterPermissionLevel == PermissionLevel.ALLOW)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.2f
                                                        )
                                                else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clickable { actualViewModel.toggleMasterPermission() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                    text = stringResource(R.string.auto_approve),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                    text =
                            if (masterPermissionLevel == PermissionLevel.ALLOW)
                                    stringResource(R.string.enabled)
                            else stringResource(R.string.ask),
                    style =
                            MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                    color =
                            if (masterPermissionLevel == PermissionLevel.ALLOW)
                                    MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // AI计划模式开关 - 更详细的文本
        Row(
                modifier =
                        Modifier.background(
                                        color =
                                                if (enableAiPlanning)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.2f
                                                        )
                                                else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clickable { actualViewModel.toggleAiPlanning() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                    text = stringResource(R.string.ai_planning_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                    text =
                            if (enableAiPlanning) stringResource(R.string.enabled)
                            else stringResource(R.string.disabled),
                    style =
                            MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                    color =
                            if (enableAiPlanning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

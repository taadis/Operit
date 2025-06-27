package com.ai.assistance.operit.ui.features.assistant.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.PromptProfile
import com.ai.assistance.operit.data.preferences.*
import com.ai.assistance.operit.ui.features.assistant.components.Live2DControls
import com.ai.assistance.operit.ui.features.assistant.components.ModelSelector
import com.ai.assistance.operit.ui.features.assistant.components.SettingItem
import com.ai.assistance.operit.ui.features.assistant.viewmodel.Live2DViewModel
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.live2d.Live2DViewCompose

/** 助手配置屏幕 提供Live2D模型预览和相关配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigScreen(
    navigateToModelConfig: () -> Unit,
    navigateToModelPrompts: () -> Unit,
    navigateToFunctionalConfig: () -> Unit,
    navigateToFunctionalPrompts: () -> Unit,
    navigateToUserPreferences: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: Live2DViewModel = viewModel(factory = Live2DViewModel.Factory(context))
    val uiState by viewModel.uiState.collectAsState()

    // Preferences Managers
    val functionalPromptManager = remember { FunctionalPromptManager(context) }
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val promptPreferences = remember { PromptPreferencesManager(context) }
    val userPrefsManager = remember { UserPreferencesManager(context) }

    // State for the selected function type
    var selectedFunctionType by remember { mutableStateOf(PromptFunctionType.CHAT) }

    // 获取当前活跃的用户偏好/性格配置
    val activeUserPrefProfileId by userPrefsManager.activeProfileIdFlow.collectAsState(initial = "default")
    val activeUserPrefProfile by userPrefsManager.getUserPreferencesFlow(activeUserPrefProfileId).collectAsState(initial = null)
    val activeUserPrefProfileName = activeUserPrefProfile?.name ?: "加载中..."

    // 根据所选功能获取数据
    val promptProfileId by functionalPromptManager.getPromptProfileIdForFunction(selectedFunctionType)
        .collectAsState(initial = FunctionalPromptManager.getDefaultProfileIdForFunction(selectedFunctionType))
    val promptProfile by promptPreferences.getPromptProfileFlow(promptProfileId)
        .collectAsState(initial = null)

    val functionType = when(selectedFunctionType) {
        PromptFunctionType.CHAT -> FunctionType.CHAT
        PromptFunctionType.VOICE -> FunctionType.SUMMARY
        PromptFunctionType.DESKTOP_PET -> FunctionType.PROBLEM_LIBRARY
    }
    val modelConfigId = remember { mutableStateOf(FunctionalConfigManager.DEFAULT_CONFIG_ID) }
    LaunchedEffect(selectedFunctionType) {
        modelConfigId.value = functionalConfigManager.getConfigIdForFunction(functionType)
    }
    val modelConfig by modelConfigManager.getModelConfigFlow(modelConfigId.value)
        .collectAsState(initial = null)

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState(initial = uiState.scrollPosition)

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { position ->
            viewModel.updateScrollPosition(position)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("助手配置") },
                actions = {
                    IconButton(onClick = { viewModel.scanUserModels() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "扫描用户模型"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
                .verticalScroll(scrollState)
        ) {
            // Live2D预览区域
            Live2DPreviewSection(
                viewModel,
                uiState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 模型选择器
            Text(
                "模型选择",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
            ModelSelector(
                models = uiState.models,
                selectedModelId = uiState.currentModel?.id,
                onModelSelected = { viewModel.switchModel(it) },
                onDeleteModel = { viewModel.deleteUserModel(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 功能配置区域
            Text(
                "功能配置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                    // 功能切换器
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PromptFunctionType.values().forEach { functionType ->
                            FilterChip(
                                selected = selectedFunctionType == functionType,
                                onClick = { selectedFunctionType = functionType },
                                label = { Text(getFunctionDisplayName(functionType)) }
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    SettingItem(
                        icon = Icons.Default.Face,
                        title = "用户性格",
                        value = activeUserPrefProfileName,
                        onClick = navigateToUserPreferences
                    )
                    
                    SettingItem(
                        icon = Icons.Default.Message,
                        title = "功能提示词",
                        value = promptProfile?.name ?: "未配置",
                        onClick = navigateToFunctionalPrompts
                    )
                    
                    SettingItem(
                        icon = Icons.Default.Api,
                        title = "功能模型",
                        value = modelConfig?.name ?: "未配置",
                        onClick = navigateToFunctionalConfig
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live2D详细配置
            if (uiState.currentModel != null && uiState.config != null) {
                var isLive2dSettingsExpanded by remember { mutableStateOf(false) }

                ExpandableSection(
                    title = "Live2D 详细设置",
                    expanded = isLive2dSettingsExpanded,
                    onToggle = { isLive2dSettingsExpanded = !isLive2dSettingsExpanded }
                ) {
                    Live2DControls(
                        config = uiState.config,
                        onScaleChanged = viewModel::updateScale,
                        onTranslateXChanged = viewModel::updateTranslateX,
                        onTranslateYChanged = viewModel::updateTranslateY,
                        onMouthFormChanged = viewModel::updateMouthForm,
                        onMouthOpenYChanged = viewModel::updateMouthOpenY,
                        onAutoBlinkChanged = viewModel::setAutoBlinkEnabled,
                        onRenderBackChanged = viewModel::setRenderBack,
                        onResetConfig = viewModel::resetConfig
                    )
                }
            }
            
            // 底部空间
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title, 
                    style = MaterialTheme.typography.titleSmall, 
                    fontWeight = FontWeight.Bold
                )
                
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            if (expanded) {
                Divider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Column {
                    content()
                }
            }
        }
    }
}

@Composable
private fun Live2DPreviewSection(
    viewModel: Live2DViewModel,
    uiState: Live2DViewModel.UiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (JniBridgeJava.isLibraryLoaded()) {
                if (uiState.models.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "没有可用的Live2D模型", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (uiState.errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "加载失败: ${uiState.errorMessage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Live2DViewCompose(
                        modifier = Modifier.fillMaxSize(),
                        model = uiState.currentModel,
                        config = uiState.config,
                        expressionToApply = uiState.expressionToApply,
                        onExpressionApplied = viewModel::onExpressionApplied,
                        triggerRandomTap = uiState.triggerRandomTap,
                        onRandomTapHandled = viewModel::onRandomTapHandled,
                        onError = { error -> viewModel.updateErrorMessage("加载失败: $error") }
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Live2D库未正确加载",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

private fun getFunctionDisplayName(functionType: PromptFunctionType): String {
    return when (functionType) {
        PromptFunctionType.CHAT -> "聊天"
        PromptFunctionType.VOICE -> "语音"
        PromptFunctionType.DESKTOP_PET -> "桌宠"
    }
}

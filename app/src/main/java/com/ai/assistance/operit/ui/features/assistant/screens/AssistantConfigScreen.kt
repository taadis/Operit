package com.ai.assistance.operit.ui.features.assistant.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.model.Live2DModel
import com.ai.assistance.operit.ui.features.assistant.viewmodel.Live2DViewModel
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.live2d.Live2DViewCompose

/** 助手配置屏幕 提供Live2D模型预览和相关配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigScreen() {
        val context = LocalContext.current
        val viewModel: Live2DViewModel = viewModel(factory = Live2DViewModel.Factory(context))
        val uiState by viewModel.uiState.collectAsState()
        val scope = rememberCoroutineScope()

        val snackbarHostState = remember { SnackbarHostState() }

        // 显示错误消息
        LaunchedEffect(uiState.errorMessage) {
                uiState.errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearErrorMessage()
                }
        }

        // 显示操作成功消息
        LaunchedEffect(uiState.operationSuccess) {
                if (uiState.operationSuccess) {
                        snackbarHostState.showSnackbar("操作成功")
                        viewModel.clearOperationSuccess()
                }
        }

        Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                        TopAppBar(
                                title = { Text("Live2D助手配置") },
                                actions = {
                                        // 添加扫描用户模型按钮
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
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        // 显示加载中或扫描中
                        if (uiState.isLoading || uiState.isScanning) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(60.dp).align(Alignment.Center)
                                )
                        } else {
                                // 主内容
                                AssistantConfigContent(
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onModelSelected = { modelId ->
                                                viewModel.switchModel(modelId)
                                        },
                                        onDeleteModel = { modelId ->
                                                viewModel.deleteUserModel(modelId)
                                        },
                                        onScaleChanged = { scale -> viewModel.updateScale(scale) },
                                        onTranslateXChanged = { x ->
                                                viewModel.updateTranslateX(x)
                                        },
                                        onTranslateYChanged = { y ->
                                                viewModel.updateTranslateY(y)
                                        },
                                        onMouthFormChanged = { value ->
                                                viewModel.updateMouthForm(value)
                                        },
                                        onMouthOpenYChanged = { value ->
                                                viewModel.updateMouthOpenY(value)
                                        },
                                        onAutoBlinkChanged = { enabled ->
                                                viewModel.setAutoBlinkEnabled(enabled)
                                        },
                                        onRenderBackChanged = { enabled ->
                                                viewModel.setRenderBack(enabled)
                                        },
                                        onResetConfig = { viewModel.resetConfig() }
                                )
                        }
                }
        }
}

/** 助手配置屏幕内容 */
@Composable
private fun AssistantConfigContent(
        viewModel: Live2DViewModel,
        uiState: Live2DViewModel.UiState,
        onModelSelected: (String) -> Unit,
        onDeleteModel: (String) -> Unit,
        onScaleChanged: (Float) -> Unit,
        onTranslateXChanged: (Float) -> Unit,
        onTranslateYChanged: (Float) -> Unit,
        onMouthFormChanged: (Float) -> Unit,
        onMouthOpenYChanged: (Float) -> Unit,
        onAutoBlinkChanged: (Boolean) -> Unit,
        onRenderBackChanged: (Boolean) -> Unit,
        onResetConfig: () -> Unit
) {
        val context = LocalContext.current

        Column(
                modifier =
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
        ) {
                // Live2D预览区域
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(300.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                        if (JniBridgeJava.isLibraryLoaded()) {
                                // 判断是否有模型可用
                                if (uiState.models.isEmpty()) {
                                        // 没有模型可用
                                        Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Column(
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally
                                                ) {
                                                        Text(
                                                                text = "没有可用的Live2D模型",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyLarge
                                                        )
                                                        Text(
                                                                text = "请将模型文件放入assets/live2d目录",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium
                                                        )
                                                }
                                        }
                                } else if (uiState.errorMessage != null) {
                                        // 显示加载错误
                                        Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Column(
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally
                                                ) {
                                                        Text(
                                                                text = "加载Live2D模型失败",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyLarge,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .error
                                                        )
                                                        Text(
                                                                text = uiState.errorMessage,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .error
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Button(
                                                                onClick = {
                                                                        viewModel.scanUserModels()
                                                                }
                                                        ) { Text("重新加载模型") }
                                                }
                                        }
                                } else {
                                        // 正常显示Live2D模型
                                        Live2DViewCompose(
                                                modifier = Modifier.fillMaxSize(),
                                                model = uiState.currentModel,
                                                config = uiState.config,
                                                expressionToApply = uiState.expressionToApply,
                                                onExpressionApplied = viewModel::onExpressionApplied,
                                                triggerRandomTap = uiState.triggerRandomTap,
                                                onRandomTapHandled = viewModel::onRandomTapHandled,
                                                onError = { error ->
                                                        // 通过ViewModel更新错误状态
                                                        viewModel.updateErrorMessage(
                                                                "加载Live2D时发生错误: $error"
                                                        )
                                                }
                                        )
                                }
                        } else {
                                // 显示错误信息，库未加载
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                        text = "Live2D库未正确加载",
                                                        style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                        text = "请检查Live2D依赖是否正确安装，或缺少必要的资源文件",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.error,
                                                        textAlign = TextAlign.Center,
                                                        modifier =
                                                                Modifier.padding(horizontal = 16.dp)
                                                )
                                        }
                                }
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 模型选择
                Text(
                        text = "选择模型",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                        items(uiState.models) { model ->
                                ModelItem(
                                        model = model,
                                        isSelected = model.id == uiState.config?.modelId,
                                        onModelClick = { onModelSelected(model.id) },
                                        onDeleteClick = { onDeleteModel(model.id) }
                                )
                        }
                }

                // 如果没有模型，显示提示信息
                if (uiState.models.isEmpty()) {
                        Text(
                                text = "没有可用的模型。请检查assets/live2d目录或导入自定义模型。",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                        )
                }

                // 表情选择（如果当前模型有表情）
                uiState.currentModel?.let { currentModel ->
                        if (currentModel.expressions.isNotEmpty()) {
                                Text(
                                        text = "表情",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                                )

                                LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                ) {
                                        items(currentModel.expressions) { expression ->
                                                FilterChip(
                                                        selected = false,
                                                        onClick = {
                                                                viewModel.applyExpression(expression)
                                                        },
                                                        label = { Text(expression) }
                                                )
                                        }
                                }
                        }
                }

                // 自定义表情输入
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        OutlinedTextField(
                                value = uiState.manualExpression,
                                onValueChange = { viewModel.updateManualExpression(it) },
                                label = { Text("手动输入表情名称") },
                                modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { viewModel.applyExpression(uiState.manualExpression) }) {
                                Text("应用")
                        }
                }

                // 只有在有配置时才显示控制选项
                uiState.config?.let { config ->
                        // 控制选项
                        Text(
                                text = "模型控制",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                        )

                        // 缩放控制
                        Text(
                                text = "缩放: ${String.format("%.1f", config.scale)}",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                                value = config.scale,
                                onValueChange = onScaleChanged,
                                valueRange = 0.5f..2.0f,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 水平位置控制
                        Text(
                                text = "水平位置: ${String.format("%.1f", config.translateX)}",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                                value = config.translateX,
                                onValueChange = onTranslateXChanged,
                                valueRange = -1.0f..1.0f,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 垂直位置控制
                        Text(
                                text = "垂直位置: ${String.format("%.1f", config.translateY)}",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                                value = config.translateY,
                                onValueChange = onTranslateYChanged,
                                valueRange = -1.0f..1.0f,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 嘴部形状控制
                        Text(
                                text = "嘴部形状: ${String.format("%.1f", config.mouthForm)}",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                                value = config.mouthForm,
                                onValueChange = onMouthFormChanged,
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 嘴部开合控制
                        Text(
                                text = "嘴部开合: ${String.format("%.1f", config.mouthOpenY)}",
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                                value = config.mouthOpenY,
                                onValueChange = onMouthOpenYChanged,
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 自动眨眼开关
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                                Text(
                                        text = "自动眨眼",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                )
                                Switch(
                                        checked = config.autoBlinkEnabled,
                                        onCheckedChange = onAutoBlinkChanged
                                )
                        }

                        // 渲染背景开关
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                                Text(
                                        text = "渲染背景",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                )
                                Switch(
                                        checked = config.renderBack,
                                        onCheckedChange = onRenderBackChanged
                                )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 随机互动按钮
                        OutlinedButton(
                                onClick = { viewModel.triggerRandomTap() },
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                Text("随机互动 (模拟点击)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 恢复默认设置按钮
                        Button(onClick = onResetConfig, modifier = Modifier.fillMaxWidth()) {
                                Text("恢复默认设置")
                        }
                }

                Spacer(modifier = Modifier.height(32.dp))
        }
}

/** 模型项，显示模型名称和操作按钮 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelItem(
        model: Live2DModel,
        isSelected: Boolean,
        onModelClick: () -> Unit,
        onDeleteClick: () -> Unit
) {
        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

        Card(
                modifier =
                        Modifier.width(120.dp)
                                .height(140.dp)
                                .border(
                                        width = 2.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(onClick = onModelClick),
                shape = RoundedCornerShape(8.dp),
        ) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                        // 模型缩略图或占位符
                        Box(
                                modifier =
                                        Modifier.size(70.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 模型名称
                        Text(
                                text = model.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 操作按钮
                        Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                if (!model.isBuiltIn) {
                                        // 用户模型显示删除按钮
                                        IconButton(
                                                onClick = onDeleteClick,
                                                modifier = Modifier.size(24.dp)
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "删除",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                )
                                        }
                                }
                        }
                }
        }
}

package com.ai.assistance.operit.ui.features.assistant.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.dragonbones.DragonBonesModel
import com.ai.assistance.dragonbones.DragonBonesViewCompose
import com.ai.assistance.dragonbones.rememberDragonBonesController
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.*
import com.ai.assistance.operit.ui.features.assistant.components.SettingItem
import com.ai.assistance.operit.ui.features.assistant.viewmodel.Live2DViewModel
import kotlinx.coroutines.launch

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

        // 启动文件选择器
        val zipFileLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                                result.data?.data?.let { uri ->
                                        // 导入选择的zip文件
                                        viewModel.importModelFromZip(uri)
                                }
                        }
                }

        // 打开文件选择器的函数
        val openZipFilePicker = {
                val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/zip"
                                putExtra(
                                        Intent.EXTRA_MIME_TYPES,
                                        arrayOf("application/zip", "application/x-zip-compressed")
                                )
                        }
                zipFileLauncher.launch(intent)
        }

        // State for the selected function type
        var selectedFunctionType by remember { mutableStateOf(PromptFunctionType.CHAT) }

        // 获取当前活跃的用户偏好/性格配置
        val activeUserPrefProfileId by
                userPrefsManager.activeProfileIdFlow.collectAsState(initial = "default")
        val activeUserPrefProfile by
                userPrefsManager
                        .getUserPreferencesFlow(activeUserPrefProfileId)
                        .collectAsState(initial = null)
        val activeUserPrefProfileName = activeUserPrefProfile?.name ?: "加载中..."

        // 根据所选功能获取数据
        val promptProfileId by
                functionalPromptManager
                        .getPromptProfileIdForFunction(selectedFunctionType)
                        .collectAsState(
                                initial =
                                        FunctionalPromptManager.getDefaultProfileIdForFunction(
                                                selectedFunctionType
                                        )
                        )
        val promptProfile by
                promptPreferences
                        .getPromptProfileFlow(promptProfileId)
                        .collectAsState(initial = null)

        val functionType =
                when (selectedFunctionType) {
                        PromptFunctionType.CHAT -> FunctionType.CHAT
                        PromptFunctionType.VOICE -> FunctionType.SUMMARY
                        PromptFunctionType.DESKTOP_PET -> FunctionType.PROBLEM_LIBRARY
                }
        val modelConfigId = remember { mutableStateOf(FunctionalConfigManager.DEFAULT_CONFIG_ID) }
        LaunchedEffect(selectedFunctionType) {
                modelConfigId.value = functionalConfigManager.getConfigIdForFunction(functionType)
        }
        val modelConfig by
                modelConfigManager
                        .getModelConfigFlow(modelConfigId.value)
                        .collectAsState(initial = null)

        val snackbarHostState = remember { SnackbarHostState() }
        val scrollState = rememberScrollState(initial = uiState.scrollPosition)
        val scope = rememberCoroutineScope()
        val dragonBonesController = rememberDragonBonesController()

        LaunchedEffect(dragonBonesController) {
                dragonBonesController.onSlotTap = { slotName ->
                        scope.launch { snackbarHostState.showSnackbar("Tapped on: $slotName") }
                }
        }

        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        viewModel.updateScrollPosition(position)
                }
        }

        // 显示操作结果的 SnackBar
        LaunchedEffect(uiState.operationSuccess, uiState.errorMessage) {
                if (uiState.operationSuccess) {
                        snackbarHostState.showSnackbar("操作成功")
                        viewModel.clearOperationSuccess()
                } else if (uiState.errorMessage != null) {
                        snackbarHostState.showSnackbar(uiState.errorMessage ?: "发生错误")
                        viewModel.clearErrorMessage()
                }
        }

        Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                        TopAppBar(
                                title = { Text("助手配置") },
                                actions = {
                                        // 导入模型按钮
                                        /* IconButton(
                                                onClick = openZipFilePicker,
                                                enabled = !uiState.isImporting && !uiState.isLoading
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.FileUpload,
                                                    contentDescription = "导入模型"
                                            )
                                        }

                                        // 刷新模型列表按钮
                                        IconButton(
                                                onClick = { viewModel.scanUserModels() },
                                                enabled =
                                                        !uiState.isImporting &&
                                                                !uiState.isLoading &&
                                                                !uiState.isScanning
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "扫描用户模型"
                                            )
                                                    }*/
                                }
                        )
                }
        ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                        // 主要内容
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .padding(horizontal = 12.dp)
                                                .verticalScroll(scrollState)
                        ) {
                                // Live2D预览区域
                                DragonBonesPreviewSection(
                                        modifier = Modifier.fillMaxWidth().height(300.dp),
                                        controller = dragonBonesController
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                DragonBonesConfigSection(controller = dragonBonesController)

                                Spacer(modifier = Modifier.height(12.dp))

                                // 功能配置区域
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(bottom = 4.dp, start = 4.dp)
                                ) {
                                        Text(
                                                "功能配置",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                                "聊天 语音 桌宠将会根据使用情况自动选择对应的提示词配置",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.1f
                                                )
                                ) {
                                        Column(
                                                modifier =
                                                        Modifier.padding(
                                                                vertical = 4.dp,
                                                                horizontal = 8.dp
                                                        )
                                        ) {
                                                // 功能切换器
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 4.dp),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        PromptFunctionType.values().forEach {
                                                                functionType ->
                                                                FilterChip(
                                                                        selected =
                                                                                selectedFunctionType ==
                                                                                        functionType,
                                                                        onClick = {
                                                                                selectedFunctionType =
                                                                                        functionType
                                                                        },
                                                                        label = {
                                                                                Text(
                                                                                        getFunctionDisplayName(
                                                                                                functionType
                                                                                        )
                                                                                )
                                                                        }
                                                                )
                                                        }
                                                }

                                                Divider(
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                )

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

                                // 底部空间
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 加载指示器覆盖层
                        if (uiState.isLoading || uiState.isImporting) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.7f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text =
                                                                if (uiState.isImporting) "正在导入模型..."
                                                                else "处理中...",
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun DragonBonesConfigSection(
        controller: com.ai.assistance.dragonbones.DragonBonesController
) {
        var expanded by remember { mutableStateOf(true) }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, start = 4.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Text(
                                "龙骨配置",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                        )
                        Icon(
                                imageVector =
                                        if (expanded) Icons.Default.ExpandLess
                                        else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "折叠" else "展开"
                        )
                }
        }

        if (expanded) {
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                // Scale Slider
                                Text(
                                        text = "缩放: ${String.format("%.2f", controller.scale)}",
                                        style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                        value = controller.scale,
                                        onValueChange = { controller.scale = it },
                                        valueRange = 0.1f..2.0f
                                )

                                // TranslationX Slider
                                Text(
                                        text =
                                                "X轴位移: ${String.format("%.1f", controller.translationX)}",
                                        style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                        value = controller.translationX,
                                        onValueChange = { controller.translationX = it },
                                        valueRange = -500f..500f
                                )

                                // TranslationY Slider
                                Text(
                                        text =
                                                "Y轴位移: ${String.format("%.1f", controller.translationY)}",
                                        style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                        value = controller.translationY,
                                        onValueChange = { controller.translationY = it },
                                        valueRange = -500f..500f
                                )
                        }
                }
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DragonBonesPreviewSection(
        modifier: Modifier = Modifier,
        controller: com.ai.assistance.dragonbones.DragonBonesController
) {
        Surface(
                modifier =
                        modifier.pointerInput(Unit) {
                                detectTapGestures { offset ->
                                        // "ik_target" is a hypothetical bone name for the IK
                                        // target.
                                        // You would replace this with the actual name from your
                                        // DragonBones model.
                                        controller.overrideBonePosition(
                                                "ik_target",
                                                offset.x,
                                                offset.y
                                        )
                                }
                        },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border =
                        BorderStroke(
                                width = 1.dp,
                                brush =
                                        Brush.verticalGradient(
                                                colors =
                                                        listOf(
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.5f),
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.2f)
                                                        )
                                        )
                        )
        ) {
                Box(modifier = Modifier.fillMaxSize()) {
                        val model = remember {
                                DragonBonesModel(
                                        skeletonPath =
                                                "dragonbones/models/loong/loongbones-web.json",
                                        textureJsonPath =
                                                "dragonbones/models/loong/loongbones-web_tex.json",
                                        textureImagePath =
                                                "dragonbones/models/loong/loongbones-web_tex.png"
                                )
                        }

                        DragonBonesViewCompose(
                                modifier = Modifier.fillMaxSize(),
                                model = model,
                                controller = controller,
                                onError = { error -> println("DragonBones error: $error") }
                        )

                        // 动画控制区域
                        if (controller.animationNames.isNotEmpty()) {
                                FlowRow(
                                        modifier =
                                                Modifier.align(Alignment.BottomCenter)
                                                        .padding(8.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.3f),
                                                                RoundedCornerShape(16.dp)
                                                        )
                                                        .padding(
                                                                horizontal = 8.dp,
                                                                vertical = 4.dp
                                                        ),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalArrangement = Arrangement.Center,
                                        maxItemsInEachRow = 4
                                ) {
                                        controller.animationNames.forEach { name ->
                                                FilterChip(
                                                        modifier =
                                                                Modifier.padding(horizontal = 4.dp),
                                                        selected =
                                                                false, // Or manage selection state
                                                        // if needed
                                                        onClick = {
                                                                controller.playAnimation(name)
                                                        },
                                                        label = { Text(name) }
                                                )
                                        }
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

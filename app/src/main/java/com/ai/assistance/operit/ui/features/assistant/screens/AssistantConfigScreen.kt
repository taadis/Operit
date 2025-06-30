package com.ai.assistance.operit.ui.features.assistant.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.dragonbones.DragonBonesModel
import com.ai.assistance.dragonbones.rememberDragonBonesController
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.*
import com.ai.assistance.operit.ui.components.ManagedDragonBonesView
import com.ai.assistance.operit.ui.features.assistant.components.DragonBonesConfigSection
import com.ai.assistance.operit.ui.features.assistant.components.DragonBonesPreviewSection
import com.ai.assistance.operit.ui.features.assistant.components.HowToImportSection
import com.ai.assistance.operit.ui.features.assistant.components.SettingItem
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import com.ai.assistance.operit.ui.features.settings.screens.getFunctionDisplayName
import java.io.File
import kotlinx.coroutines.launch

/** 助手配置屏幕 提供DragonBones模型预览和相关配置 */
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
        val viewModel: AssistantConfigViewModel = viewModel(factory = AssistantConfigViewModel.Factory(context))
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

        // Sync ViewModel state with Controller
        LaunchedEffect(uiState.config) {
                uiState.config?.let {
                        dragonBonesController.scale = it.scale
                        dragonBonesController.translationX = it.translateX
                        dragonBonesController.translationY = it.translateY
                }
        }

        // Sync Controller changes back to ViewModel
        LaunchedEffect(
                dragonBonesController.scale,
                dragonBonesController.translationX,
                dragonBonesController.translationY
        ) {
                uiState.config?.let {
                        if (it.scale != dragonBonesController.scale ||
                                it.translateX != dragonBonesController.translationX ||
                                it.translateY != dragonBonesController.translationY
                        ) {
                                viewModel.updateScale(dragonBonesController.scale)
                                viewModel.updateTranslateX(dragonBonesController.translationX)
                                viewModel.updateTranslateY(dragonBonesController.translationY)
                        }
                }
        }

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
                                        IconButton(
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
                                        }
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
                                // DragonBones预览区域
                                DragonBonesPreviewSection(
                                        modifier = Modifier.fillMaxWidth().height(300.dp),
                                        controller = dragonBonesController,
                                        uiState = uiState
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                DragonBonesConfigSection(
                                        controller = dragonBonesController,
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onImportClick = { openZipFilePicker() }
                                )

                                HowToImportSection()

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

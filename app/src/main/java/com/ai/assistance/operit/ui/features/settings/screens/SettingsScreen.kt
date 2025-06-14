package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import kotlinx.coroutines.launch

// 保存滑动状态变量，使其跨重组保持
private val SettingsScreenScrollPosition = mutableStateOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        onNavigateToUserPreferences: () -> Unit,
        navigateToToolPermissions: () -> Unit,
        navigateToModelConfig: () -> Unit,
        navigateToThemeSettings: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToFunctionalConfig: () -> Unit,
        navigateToChatHistorySettings: () -> Unit,
        navigateToLanguageSettings: () -> Unit
) {
        val context = LocalContext.current
        val apiPreferences = remember { ApiPreferences(context) }
        val scope = rememberCoroutineScope()

        // 创建和记住滚动状态，设置为上次保存的位置
        val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

        // 当滚动状态改变时更新保存的位置
        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        SettingsScreenScrollPosition.value = position
                }
        }

        // Collect API settings as state
        val apiKey = apiPreferences.apiKeyFlow.collectAsState(initial = "").value
        val apiEndpoint =
                apiPreferences.apiEndpointFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_API_ENDPOINT
                        )
                        .value
        val modelName =
                apiPreferences.modelNameFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_MODEL_NAME
                        )
                        .value
        val memoryOptimization =
                apiPreferences.memoryOptimizationFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_MEMORY_OPTIMIZATION
                        )
                        .value
        val showFpsCounter =
                apiPreferences.showFpsCounterFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_SHOW_FPS_COUNTER
                        )
                        .value
        val autoGrantAccessibility =
                apiPreferences.autoGrantAccessibilityFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_AUTO_GRANT_ACCESSIBILITY
                        )
                        .value

        // Mutable state for editing
        var apiKeyInput by remember { mutableStateOf(apiKey) }
        var apiEndpointInput by remember { mutableStateOf(apiEndpoint) }
        var modelNameInput by remember { mutableStateOf(modelName) }
        var memoryOptimizationInput by remember { mutableStateOf(memoryOptimization) }
        var showFpsCounterInput by remember { mutableStateOf(showFpsCounter) }
        var autoGrantAccessibilityInput by remember { mutableStateOf(autoGrantAccessibility) }
        var showSaveSuccessMessage by remember { mutableStateOf(false) }

        // Add state for endpoint warning
        var endpointWarningMessage by remember { mutableStateOf<String?>(null) }
        var showEndpointWarning by remember { mutableStateOf(false) }

        // Add state to check if using default API key
        val isUsingDefaultApiKey = apiKeyInput == ApiPreferences.DEFAULT_API_KEY
        var showModelRestrictionInfo by remember { mutableStateOf(isUsingDefaultApiKey) }

        // Force deepseek-chat model when using default API key
        LaunchedEffect(apiKeyInput) {
                if (apiKeyInput == ApiPreferences.DEFAULT_API_KEY) {
                        modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
                        showModelRestrictionInfo = true
                } else {
                        showModelRestrictionInfo = false
                }
        }

        // Update local state when preferences change
        LaunchedEffect(
                apiKey,
                apiEndpoint,
                modelName,
                memoryOptimization,
                showFpsCounter,
                autoGrantAccessibility
        ) {
                apiKeyInput = apiKey
                apiEndpointInput = apiEndpoint
                modelNameInput = modelName
                memoryOptimizationInput = memoryOptimization
                showFpsCounterInput = showFpsCounter
                autoGrantAccessibilityInput = autoGrantAccessibility
        }

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(scrollState) // 使用保存的滚动状态
        ) {
                // ======= SECTION 1: PERSONALIZATION =======
                SettingsSectionTitle(title = "个性化", icon = Icons.Default.Person)

                // 用户偏好设置
                SettingsCard(
                        title = "用户偏好设置",
                        description = "配置个人信息和偏好，让AI更好地了解你，包括年龄、性格、身份、职业和期待的AI风格",
                        onClick = onNavigateToUserPreferences,
                        buttonText = "配置用户偏好",
                        icon = Icons.Default.Face
                )

                // 模型提示词设置
                SettingsCard(
                        title = "模型提示词设置",
                        description = "自定义AI助手的系统提示词，包括自我介绍和语气风格，使AI更符合您的预期",
                        onClick = navigateToModelPrompts,
                        buttonText = "配置提示词",
                        icon = Icons.Default.Message
                )

                // 主题设置
                SettingsCard(
                        title = "主题和外观",
                        description = "个性化应用外观，包括深色/浅色模式和自定义配色方案",
                        onClick = navigateToThemeSettings,
                        buttonText = "自定义主题",
                        icon = Icons.Default.Palette
                )

                // 语言设置
                SettingsCard(
                        title = stringResource(R.string.language_settings),
                        description = stringResource(R.string.language_settings_desc),
                        onClick = navigateToLanguageSettings,
                        buttonText = stringResource(R.string.change_language),
                        icon = Icons.Default.Language
                )

                // 聊天记录管理
                SettingsCard(
                        title = "数据备份与恢复",
                        description = "导入、导出或删除聊天记录和问题库数据，方便备份和恢复重要信息",
                        onClick = navigateToChatHistorySettings,
                        buttonText = "管理备份",
                        icon = Icons.Default.History
                )

                // ======= SECTION 2: AI MODEL CONFIGURATION =======
                SettingsSectionTitle(title = "AI模型配置", icon = Icons.Default.Settings)

                // 模型与参数配置卡片
                SettingsCard(
                        title = "模型与参数配置",
                        description = "配置API设置、模型名称与参数，支持多配置管理",
                        onClick = navigateToModelConfig,
                        buttonText = "配置模型",
                        icon = Icons.Default.Api
                )

                // 功能模型配置卡片
                SettingsCard(
                        title = "功能模型配置",
                        description = "为不同功能（如对话、总结、问题库等）配置专用的模型与API设置",
                        onClick = navigateToFunctionalConfig,
                        buttonText = "配置功能",
                        icon = Icons.Default.Tune
                )

                // ======= SECTION 3: DISPLAY AND BEHAVIOR =======
                SettingsSectionTitle(title = "显示与行为", icon = Icons.Default.Visibility)

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.DisplaySettings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text =
                                                        stringResource(
                                                                id = R.string.display_settings
                                                        ),
                                                style = MaterialTheme.typography.titleMedium
                                        )
                                }

                                // 记忆优化开关
                                SettingsToggle(
                                        title = "记忆优化",
                                        description = "开启后，AI会有一定的遗忘能力",
                                        checked = memoryOptimizationInput,
                                        onCheckedChange = {
                                                memoryOptimizationInput = it
                                                scope.launch {
                                                        apiPreferences.saveMemoryOptimization(it)
                                                        showSaveSuccessMessage = true
                                                }
                                        }
                                )

                                // 帧率显示开关
                                SettingsToggle(
                                        title = "显示帧率",
                                        description = "在屏幕右上角显示实时帧率",
                                        checked = showFpsCounterInput,
                                        onCheckedChange = {
                                                showFpsCounterInput = it
                                                scope.launch {
                                                        apiPreferences.saveShowFpsCounter(it)
                                                        showSaveSuccessMessage = true
                                                }
                                        }
                                )

                                // 自动授予无障碍权限开关
                                SettingsToggle(
                                        title = "自动授予无障碍权限",
                                        description = "关闭后需每次打开软件手动授权",
                                        checked = autoGrantAccessibilityInput,
                                        onCheckedChange = {
                                                autoGrantAccessibilityInput = it
                                                scope.launch {
                                                        apiPreferences.saveAutoGrantAccessibility(
                                                                it
                                                        )
                                                        showSaveSuccessMessage = true
                                                }
                                        }
                                )
                        }
                }

                // ======= SECTION 4: PERMISSIONS AND SECURITY =======
                SettingsSectionTitle(title = "权限与安全", icon = Icons.Default.Security)

                // 工具权限设置卡片
                SettingsCard(
                        title = "工具权限设置",
                        description = "配置AI助手工具的权限级别，可以设置为自动允许、询问或禁止",
                        onClick = navigateToToolPermissions,
                        buttonText = "配置工具权限",
                        icon = Icons.Default.AdminPanelSettings
                )

                // ======= SECTION 5: USAGE STATISTICS =======
                SettingsSectionTitle(title = "使用统计", icon = Icons.Default.Analytics)

                // Token统计卡片
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.DataUsage,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text = "Token统计",
                                                style = MaterialTheme.typography.titleMedium
                                        )
                                }

                                // 从数据源读取token统计数据
                                val chatHistories = remember {
                                        ChatHistoryManager.getInstance(context)
                                }
                                var totalInputTokens by remember { mutableStateOf(0) }
                                var totalOutputTokens by remember { mutableStateOf(0) }
                                var preferenceAnalysisInputTokens by remember { mutableStateOf(0) }
                                var preferenceAnalysisOutputTokens by remember { mutableStateOf(0) }

                                // 读取和统计token数据
                                LaunchedEffect(Unit) {
                                        // 聊天历史token计数
                                        chatHistories.chatHistoriesFlow.collect { histories ->
                                                totalInputTokens =
                                                        histories.sumOf { it.inputTokens }
                                                totalOutputTokens =
                                                        histories.sumOf { it.outputTokens }
                                        }
                                }

                                LaunchedEffect(Unit) {
                                        apiPreferences.preferenceAnalysisInputTokensFlow.collect {
                                                tokens ->
                                                preferenceAnalysisInputTokens = tokens
                                        }
                                }

                                LaunchedEffect(Unit) {
                                        apiPreferences.preferenceAnalysisOutputTokensFlow.collect {
                                                tokens ->
                                                preferenceAnalysisOutputTokens = tokens
                                        }
                                }

                                // 计算费用
                                val usdToRmbRate = 7.2
                                val chatInputCost =
                                        totalInputTokens * 0.27 / 1_000_000 * usdToRmbRate
                                val chatOutputCost =
                                        totalOutputTokens * 1.10 / 1_000_000 * usdToRmbRate
                                val preferenceAnalysisInputCost =
                                        preferenceAnalysisInputTokens * 0.27 / 1_000_000 *
                                                usdToRmbRate
                                val preferenceAnalysisOutputCost =
                                        preferenceAnalysisOutputTokens * 1.10 / 1_000_000 *
                                                usdToRmbRate
                                val totalCost =
                                        chatInputCost +
                                                chatOutputCost +
                                                preferenceAnalysisInputCost +
                                                preferenceAnalysisOutputCost

                                // 统计信息表格
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        TokenStatRow(
                                                label = "聊天输入Token",
                                                value = totalInputTokens.toString(),
                                                cost = "¥${String.format("%.2f", chatInputCost)}"
                                        )

                                        TokenStatRow(
                                                label = "聊天输出Token",
                                                value = totalOutputTokens.toString(),
                                                cost = "¥${String.format("%.2f", chatOutputCost)}"
                                        )

                                        TokenStatRow(
                                                label = "偏好分析输入Token",
                                                value = preferenceAnalysisInputTokens.toString(),
                                                cost =
                                                        "¥${String.format("%.2f", preferenceAnalysisInputCost)}"
                                        )

                                        TokenStatRow(
                                                label = "偏好分析输出Token",
                                                value = preferenceAnalysisOutputTokens.toString(),
                                                cost =
                                                        "¥${String.format("%.2f", preferenceAnalysisOutputCost)}"
                                        )

                                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                                        TokenStatRow(
                                                label = "总计",
                                                value =
                                                        (totalInputTokens +
                                                                        totalOutputTokens +
                                                                        preferenceAnalysisInputTokens +
                                                                        preferenceAnalysisOutputTokens)
                                                                .toString(),
                                                cost = "¥${String.format("%.2f", totalCost)}",
                                                isHighlighted = true
                                        )
                                }

                                // 重置按钮
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                ) {
                                        OutlinedButton(
                                                onClick = {
                                                        scope.launch {
                                                                apiPreferences
                                                                        .resetPreferenceAnalysisTokens()
                                                                showSaveSuccessMessage = true
                                                        }
                                                }
                                        ) { Text("重置偏好分析计数") }
                                }

                                // 幽默解释
                                Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                )
                                ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                        text = "为什么我要关心Token统计？",
                                                        style =
                                                                MaterialTheme.typography.bodyMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                )

                                                Text(
                                                        text =
                                                                "因为它就像一个饭店账单，只不过这里的'饭'是AI的思考，而'钱'是以人民币计算的！偏好分析是AI暗中观察你的口味，为你定制下次的\"菜单\"。别担心，即使你聊到手指抽筋，这些费用可能还不够买一杯奶茶～",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun SettingsCard(
        title: String,
        description: String,
        onClick: () -> Unit,
        buttonText: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector
) {
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth() // 确保Column填满Card的宽度
                                        .padding(16.dp)
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = title, style = MaterialTheme.typography.titleMedium)
                        }

                        Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 使用Row替代Button的align(End)
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                        ) { Button(onClick = onClick) { Text(buttonText) } }
                }
        }
}

@Composable
private fun SettingsToggle(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(text = title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }

                Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.padding(top = 4.dp)
                )
        }
}

@Composable
private fun SettingsSectionTitle(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector
) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )
        }
        Divider(modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun TokenStatRow(
        label: String,
        value: String,
        cost: String,
        isHighlighted: Boolean = false
) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (isHighlighted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                                text = value,
                                style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight =
                                                        if (isHighlighted) FontWeight.Bold
                                                        else FontWeight.Normal
                                        ),
                                color =
                                        if (isHighlighted) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                                text = cost,
                                style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight =
                                                        if (isHighlighted) FontWeight.Bold
                                                        else FontWeight.Normal
                                        ),
                                color =
                                        if (isHighlighted) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                        )
                }
        }
}

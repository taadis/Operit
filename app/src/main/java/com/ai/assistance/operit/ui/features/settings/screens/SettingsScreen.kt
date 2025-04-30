package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.ModelEndPointFix
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateToUserPreferences: () -> Unit, navigateToToolPermissions: () -> Unit) {
        val context = LocalContext.current
        val apiPreferences = remember { ApiPreferences(context) }
        val scope = rememberCoroutineScope()

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
        val showThinking =
                apiPreferences.showThinkingFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_SHOW_THINKING
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
        val collapseExecution =
                apiPreferences.collapseExecutionFlow.collectAsState(
                                initial = ApiPreferences.DEFAULT_COLLAPSE_EXECUTION
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
        var showThinkingInput by remember { mutableStateOf(showThinking) }
        var memoryOptimizationInput by remember { mutableStateOf(memoryOptimization) }
        var showFpsCounterInput by remember { mutableStateOf(showFpsCounter) }
        var collapseExecutionInput by remember { mutableStateOf(collapseExecution) }
        var autoGrantAccessibilityInput by remember { mutableStateOf(autoGrantAccessibility) }
        var showSaveSuccessMessage by remember { mutableStateOf(false) }

        // Update local state when preferences change
        LaunchedEffect(
                apiKey,
                apiEndpoint,
                modelName,
                showThinking,
                memoryOptimization,
                showFpsCounter,
                collapseExecution,
                autoGrantAccessibility
        ) {
                apiKeyInput = apiKey
                apiEndpointInput = apiEndpoint
                modelNameInput = modelName
                showThinkingInput = showThinking
                memoryOptimizationInput = memoryOptimization
                showFpsCounterInput = showFpsCounter
                collapseExecutionInput = collapseExecution
                autoGrantAccessibilityInput = autoGrantAccessibility
        }

        Column(
                modifier =
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
        ) {
                Text(
                        text = stringResource(id = R.string.settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = stringResource(id = R.string.api_settings),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                OutlinedTextField(
                                        value = apiEndpointInput,
                                        onValueChange = {
                                                apiEndpointInput = it

                                                // Try to fix the endpoint on-the-fly if user
                                                // removes focus
                                                if (!ModelEndPointFix.isValidEndpoint(it) &&
                                                                it.isNotBlank()
                                                ) {
                                                        ModelEndPointFix.fixEndpoint(it)?.let {
                                                                fixed ->
                                                                apiEndpointInput = fixed
                                                        }
                                                }
                                        },
                                        label = {
                                                Text(stringResource(id = R.string.api_endpoint))
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )

                                OutlinedTextField(
                                        value = apiKeyInput,
                                        onValueChange = { apiKeyInput = it },
                                        label = { Text(stringResource(id = R.string.api_key)) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )

                                OutlinedTextField(
                                        value = modelNameInput,
                                        onValueChange = { modelNameInput = it },
                                        label = { Text(stringResource(id = R.string.model_name)) },
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                )

                                Button(
                                        onClick = {
                                                scope.launch {
                                                        // Check and fix the endpoint before saving
                                                        val endpointToSave =
                                                                if (!ModelEndPointFix
                                                                                .isValidEndpoint(
                                                                                        apiEndpointInput
                                                                                )
                                                                ) {
                                                                        // Try to fix the endpoint
                                                                        ModelEndPointFix
                                                                                .fixEndpoint(
                                                                                        apiEndpointInput
                                                                                )
                                                                                ?: apiEndpointInput
                                                                } else {
                                                                        apiEndpointInput
                                                                }

                                                        // Update the UI if endpoint was fixed
                                                        if (endpointToSave != apiEndpointInput) {
                                                                apiEndpointInput = endpointToSave
                                                        }

                                                        apiPreferences.saveAllSettings(
                                                                apiKeyInput,
                                                                endpointToSave, // Use the
                                                                // potentially fixed
                                                                // endpoint
                                                                modelNameInput,
                                                                showThinkingInput,
                                                                memoryOptimizationInput,
                                                                showFpsCounterInput,
                                                                false, // enableAiPlanning
                                                                collapseExecutionInput,
                                                                autoGrantAccessibilityInput
                                                        )
                                                        showSaveSuccessMessage = true
                                                }
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                ) { Text(stringResource(id = R.string.save_settings)) }

                                if (showSaveSuccessMessage) {
                                        LaunchedEffect(Unit) {
                                                kotlinx.coroutines.delay(3000)
                                                showSaveSuccessMessage = false
                                        }

                                        Text(
                                                text = stringResource(id = R.string.settings_saved),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier =
                                                        Modifier.padding(top = 8.dp).fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                        )
                                }
                        }
                }

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = stringResource(id = R.string.display_settings),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Column {
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        id = R.string.show_thinking
                                                                ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                        text = "有的模型不具备思考能力，就可以关掉它",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }

                                        Switch(
                                                checked = showThinkingInput,
                                                onCheckedChange = {
                                                        showThinkingInput = it
                                                        scope.launch {
                                                                apiPreferences.saveShowThinking(it)
                                                                showSaveSuccessMessage = true
                                                        }
                                                }
                                        )
                                }

                                // 记忆优化开关
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Column {
                                                Text(
                                                        text = "记忆优化",
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                        text = "开启后，AI会有一定的遗忘能力",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }

                                        Switch(
                                                checked = memoryOptimizationInput,
                                                onCheckedChange = {
                                                        memoryOptimizationInput = it
                                                        scope.launch {
                                                                apiPreferences
                                                                        .saveMemoryOptimization(it)
                                                                showSaveSuccessMessage = true
                                                        }
                                                }
                                        )
                                }

                                // 帧率显示开关
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Column {
                                                Text(
                                                        text = "显示帧率",
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                        text = "在屏幕右上角显示实时帧率",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }

                                        Switch(
                                                checked = showFpsCounterInput,
                                                onCheckedChange = {
                                                        showFpsCounterInput = it
                                                        scope.launch {
                                                                apiPreferences.saveShowFpsCounter(
                                                                        it
                                                                )
                                                                showSaveSuccessMessage = true
                                                        }
                                                }
                                        )
                                }

                                // 折叠执行开关
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Column {
                                                Text(
                                                        text = "折叠执行",
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                        text = "执行结果默认折叠显示",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }

                                        Switch(
                                                checked = collapseExecutionInput,
                                                onCheckedChange = {
                                                        collapseExecutionInput = it
                                                        scope.launch {
                                                                apiPreferences
                                                                        .saveCollapseExecution(it)
                                                                showSaveSuccessMessage = true
                                                        }
                                                }
                                        )
                                }

                                // 自动授予无障碍权限开关
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Column {
                                                Text(
                                                        text = "自动授予无障碍权限",
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                        text = "关闭后需每次打开软件手动授权",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }

                                        Switch(
                                                checked = autoGrantAccessibilityInput,
                                                onCheckedChange = {
                                                        autoGrantAccessibilityInput = it
                                                        scope.launch {
                                                                apiPreferences
                                                                        .saveAutoGrantAccessibility(
                                                                                it
                                                                        )
                                                                showSaveSuccessMessage = true
                                                        }
                                                }
                                        )
                                }
                        }
                }

                // 工具权限设置卡片
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = "工具权限设置",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Text(
                                        text = "配置AI助手工具的权限级别，可以设置为自动允许、询问或禁止",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Button(
                                        onClick = navigateToToolPermissions,
                                        modifier = Modifier.align(Alignment.End)
                                ) { Text("配置工具权限") }
                        }
                }

                // 用户偏好设置
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = "用户偏好设置",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Text(
                                        text = "配置个人信息和偏好，让AI更好地了解你，包括年龄、性格、身份、职业和期待的AI风格",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Button(
                                        onClick = onNavigateToUserPreferences,
                                        modifier = Modifier.align(Alignment.End)
                                ) { Text("配置用户偏好") }
                        }
                }

                // Token统计卡片
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = "Token统计",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // 从数据源读取token统计数据
                                val chatHistories = remember { ChatHistoryManager(context) }
                                var totalInputTokens by remember { mutableStateOf(0) }
                                var totalOutputTokens by remember { mutableStateOf(0) }
                                var preferenceAnalysisInputTokens by remember { mutableStateOf(0) }
                                var preferenceAnalysisOutputTokens by remember { mutableStateOf(0) }

                                // 计算所有聊天历史的token总和和读取偏好分析token计数
                                LaunchedEffect(Unit) {
                                        // 聊天历史token计数
                                        chatHistories.chatHistoriesFlow.collect { histories ->
                                                totalInputTokens =
                                                        histories.sumOf { it.inputTokens }
                                                totalOutputTokens =
                                                        histories.sumOf { it.outputTokens }
                                        }
                                }

                                // 读取偏好分析token计数
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

                                // 计算费用（使用DeepSeek的价格）
                                // DeepSeek价格：输入tokens $0.27/百万，输出tokens $1.10/百万
                                // 添加USD到RMB的汇率转换（假设1 USD = 7.2 RMB）
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

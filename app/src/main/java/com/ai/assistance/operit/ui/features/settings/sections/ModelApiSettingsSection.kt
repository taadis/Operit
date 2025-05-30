package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.api.OpenAIService
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.ModelEndPointFix
import kotlinx.coroutines.launch

@Composable
fun ModelApiSettingsSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        showNotification: (String) -> Unit
) {
        val scope = rememberCoroutineScope()
        val openAIService = remember { OpenAIService() }

        // API编辑状态
        var apiEndpointInput by remember(config.id) { mutableStateOf(config.apiEndpoint) }
        var apiKeyInput by remember(config.id) { mutableStateOf(config.apiKey) }
        var modelNameInput by remember(config.id) { mutableStateOf(config.modelName) }

        // 警告状态
        var showEndpointWarning by remember { mutableStateOf(false) }
        var endpointWarningMessage by remember { mutableStateOf<String?>(null) }

        // 模型列表状态
        var isLoadingModels by remember { mutableStateOf(false) }
        var showModelsDialog by remember { mutableStateOf(false) }
        var modelsList by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
        var modelLoadError by remember { mutableStateOf<String?>(null) }

        // 检查是否使用默认API密钥
        val isUsingDefaultApiKey = apiKeyInput == ApiPreferences.DEFAULT_API_KEY
        var showModelRestrictionInfo by remember { mutableStateOf(isUsingDefaultApiKey) }

        // 当使用默认API密钥时限制模型名称
        LaunchedEffect(apiKeyInput) {
                if (apiKeyInput == ApiPreferences.DEFAULT_API_KEY) {
                        modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
                        showModelRestrictionInfo = true
                } else {
                        showModelRestrictionInfo = false
                }
        }

        // API设置卡片
        Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Api,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = "API设置",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        // API端点输入
                        OutlinedTextField(
                                value = apiEndpointInput,
                                onValueChange = {
                                        apiEndpointInput = it

                                        // 检查是否包含补全路径
                                        if (it.isNotBlank() &&
                                                        !ModelEndPointFix.containsCompletionsPath(
                                                                it
                                                        )
                                        ) {
                                                endpointWarningMessage =
                                                        "提示：API地址应包含补全路径，如v1/chat/completions"
                                                showEndpointWarning = true
                                        } else {
                                                showEndpointWarning = false
                                        }
                                },
                                label = { Text("API端点") },
                                placeholder = {
                                        Text("例如: https://api.openai.com/v1/chat/completions")
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                singleLine = true
                        )

                        // 显示端点警告消息
                        if (showEndpointWarning) {
                                Text(
                                        text = endpointWarningMessage ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        fontSize = 12.sp
                                )
                        }

                        // API密钥输入
                        OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = {
                                        apiKeyInput = it

                                        // 当API密钥改变时检查是否需要限制模型
                                        if (it == ApiPreferences.DEFAULT_API_KEY) {
                                                modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
                                                showModelRestrictionInfo = true
                                        } else {
                                                showModelRestrictionInfo = false
                                        }
                                },
                                label = { Text("API密钥") },
                                placeholder = { Text("输入API密钥") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                singleLine = true
                        )

                        // 模型名称输入和模型列表按钮
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                                OutlinedTextField(
                                        value = modelNameInput,
                                        onValueChange = {
                                                // 只有在不使用默认API密钥时才允许更改
                                                if (!isUsingDefaultApiKey) {
                                                        modelNameInput = it
                                                }
                                        },
                                        label = { Text("模型名称") },
                                        placeholder = {
                                                Text("例如: gpt-4, claude-3-opus-20240229...")
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isUsingDefaultApiKey,
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        disabledTextColor =
                                                                if (isUsingDefaultApiKey)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface.copy(
                                                                                alpha = 0.38f
                                                                        ),
                                                        disabledBorderColor =
                                                                if (isUsingDefaultApiKey)
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.3f
                                                                        )
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .outline.copy(
                                                                                alpha = 0.12f
                                                                        ),
                                                        disabledLabelColor =
                                                                if (isUsingDefaultApiKey)
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.7f
                                                                        )
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                                                .copy(alpha = 0.38f)
                                                ),
                                        singleLine = true
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // 获取模型列表按钮
                                IconButton(
                                        onClick = {
                                                scope.launch {
                                                        if (apiEndpointInput.isNotBlank() &&
                                                                        apiKeyInput.isNotBlank() &&
                                                                        !isUsingDefaultApiKey
                                                        ) {
                                                                isLoadingModels = true
                                                                modelLoadError = null

                                                                try {
                                                                        val result =
                                                                                openAIService
                                                                                        .getModels(
                                                                                                apiKeyInput,
                                                                                                apiEndpointInput
                                                                                        )
                                                                        if (result.isSuccess) {
                                                                                modelsList =
                                                                                        result.getOrThrow()
                                                                                showModelsDialog =
                                                                                        true
                                                                        } else {
                                                                                modelLoadError =
                                                                                        "获取模型列表失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                                                                                showNotification(
                                                                                        modelLoadError
                                                                                                ?: "获取模型列表失败"
                                                                                )
                                                                        }
                                                                } catch (e: Exception) {
                                                                        modelLoadError =
                                                                                "获取模型列表失败: ${e.message}"
                                                                        showNotification(
                                                                                modelLoadError
                                                                                        ?: "获取模型列表失败"
                                                                        )
                                                                } finally {
                                                                        isLoadingModels = false
                                                                }
                                                        } else if (isUsingDefaultApiKey) {
                                                                showNotification("使用默认配置时无法获取模型列表")
                                                        } else {
                                                                showNotification("请先填写API端点和密钥")
                                                        }
                                                }
                                        },
                                        modifier = Modifier.size(48.dp),
                                        colors =
                                                IconButtonDefaults.iconButtonColors(
                                                        contentColor =
                                                                MaterialTheme.colorScheme.primary
                                                ),
                                        enabled = !isUsingDefaultApiKey
                                ) {
                                        if (isLoadingModels) {
                                                CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp
                                                )
                                        } else {
                                                Icon(
                                                        imageVector =
                                                                Icons.Default.FormatListBulleted,
                                                        contentDescription = "获取模型列表",
                                                        tint =
                                                                if (!isUsingDefaultApiKey)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface.copy(
                                                                                alpha = 0.38f
                                                                        )
                                                )
                                        }
                                }
                        }

                        // 显示模型限制信息
                        if (showModelRestrictionInfo) {
                                Text(
                                        text = "使用默认配置时，只能使用deepseek-chat模型",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 16.dp),
                                        fontSize = 12.sp
                                )
                        }

                        // 保存按钮
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                        ) {
                                Button(
                                        onClick = {
                                                scope.launch {
                                                        // 检查端点是否缺少补全路径
                                                        if (apiEndpointInput.isNotBlank() &&
                                                                        !ModelEndPointFix
                                                                                .containsCompletionsPath(
                                                                                        apiEndpointInput
                                                                                )
                                                        ) {
                                                                endpointWarningMessage =
                                                                        "警告：您的API地址不包含补全路径（如v1/chat/completions）。请确保这是您想要的配置。"
                                                                showEndpointWarning = true
                                                        }

                                                        // 强制在使用默认API密钥时使用默认模型
                                                        val modelToSave =
                                                                if (apiKeyInput ==
                                                                                ApiPreferences
                                                                                        .DEFAULT_API_KEY
                                                                ) {
                                                                        ApiPreferences
                                                                                .DEFAULT_MODEL_NAME
                                                                } else {
                                                                        modelNameInput
                                                                }

                                                        // 更新配置
                                                        configManager.updateModelConfig(
                                                                configId = config.id,
                                                                apiKey = apiKeyInput,
                                                                apiEndpoint = apiEndpointInput,
                                                                modelName = modelToSave
                                                        )

                                                        showNotification("API设置已保存")
                                                }
                                        }
                                ) { Text("保存API设置") }
                        }
                }
        }

        // 模型列表对话框
        if (showModelsDialog) {
                Dialog(onDismissRequest = { showModelsDialog = false }) {
                        Card(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        )
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        // 标题栏
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(bottom = 16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text(
                                                        "可用模型列表",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                )

                                                IconButton(
                                                        onClick = {
                                                                showModelsDialog = false
                                                                scope.launch {
                                                                        if (apiEndpointInput
                                                                                        .isNotBlank() &&
                                                                                        apiKeyInput
                                                                                                .isNotBlank() &&
                                                                                        !isUsingDefaultApiKey
                                                                        ) {
                                                                                isLoadingModels =
                                                                                        true
                                                                                try {
                                                                                        val result =
                                                                                                openAIService
                                                                                                        .getModels(
                                                                                                                apiKeyInput,
                                                                                                                apiEndpointInput
                                                                                                        )
                                                                                        if (result.isSuccess
                                                                                        ) {
                                                                                                modelsList =
                                                                                                        result.getOrThrow()
                                                                                                showModelsDialog =
                                                                                                        true
                                                                                        } else {
                                                                                                modelLoadError =
                                                                                                        "刷新模型列表失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                                                                                                showNotification(
                                                                                                        modelLoadError
                                                                                                                ?: "刷新模型列表失败"
                                                                                                )
                                                                                        }
                                                                                } catch (
                                                                                        e:
                                                                                                Exception) {
                                                                                        modelLoadError =
                                                                                                "刷新模型列表失败: ${e.message}"
                                                                                        showNotification(
                                                                                                modelLoadError
                                                                                                        ?: "刷新模型列表失败"
                                                                                        )
                                                                                } finally {
                                                                                        isLoadingModels =
                                                                                                false
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Refresh,
                                                                contentDescription = "刷新模型列表"
                                                        )
                                                }
                                        }

                                        // 模型列表
                                        if (modelsList.isEmpty()) {
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .height(200.dp),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Text(
                                                                text = modelLoadError ?: "没有找到可用模型",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                        } else {
                                                androidx.compose.foundation.lazy.LazyColumn(
                                                        modifier =
                                                                Modifier.fillMaxWidth().weight(1f)
                                                ) {
                                                        items(modelsList.size) { index ->
                                                                val model = modelsList[index]
                                                                androidx.compose.material3.ListItem(
                                                                        headlineContent = {
                                                                                Text(model.id)
                                                                        },
                                                                        modifier =
                                                                                Modifier.clickable {
                                                                                        modelNameInput =
                                                                                                model.id
                                                                                        showModelsDialog =
                                                                                                false
                                                                                }
                                                                )
                                                                if (index < modelsList.size - 1) {
                                                                        Divider(
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                horizontal =
                                                                                                        8.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        // 底部按钮
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(top = 16.dp),
                                                horizontalArrangement = Arrangement.End
                                        ) {
                                                TextButton(onClick = { showModelsDialog = false }) {
                                                        Text("关闭")
                                                }
                                        }
                                }
                        }
                }
        }
}

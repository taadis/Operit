package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FreeUsagePreferences
import com.ai.assistance.operit.ui.features.chat.components.config.*
import com.ai.assistance.operit.util.ModelEndPointFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 简洁风格的AI助手配置界面 */
@Composable
fun ConfigurationScreen(
        apiEndpoint: String,
        apiKey: String,
        modelName: String,
        onApiEndpointChange: (String) -> Unit,
        onApiKeyChange: (String) -> Unit,
        onModelNameChange: (String) -> Unit,
        onSaveConfig: () -> Unit,
        onError: (String) -> Unit,
        coroutineScope: CoroutineScope,
        onUseDefault: () -> Unit = {},
        isUsingDefault: Boolean = false,
        onNavigateToChat: () -> Unit = {},
        onNavigateToTokenConfig: () -> Unit = {}
) {
    // 获取Context
    val context = LocalContext.current

    // 初始化免费使用偏好
    val freeUsagePreferences = remember { FreeUsagePreferences(context) }
    val remainingUsages by freeUsagePreferences.remainingUsagesFlow.collectAsState()
    val maxDailyUsage = freeUsagePreferences.getMaxDailyUsage()

    // 状态管理
    var apiKeyInput by remember { mutableStateOf(if (isUsingDefault) "" else apiKey) }
    var apiEndpointInput by remember { mutableStateOf(apiEndpoint) }
    var modelNameInput by remember { mutableStateOf(modelName) }
    var showCustomFields by remember { mutableStateOf(false) }
    var showEndpointWarning by remember { mutableStateOf(false) }
    var endpointWarningMessage by remember { mutableStateOf<String?>(null) }
    var showTokenInfoDialog by remember { mutableStateOf(false) }
    var showFreeUsageDialog by remember { mutableStateOf(false) }

    // 使用默认密钥的状态检测
    val isUsingDefaultApiKey by
            remember(apiKeyInput) {
                derivedStateOf {
                    apiKeyInput == ApiPreferences.DEFAULT_API_KEY ||
                            (apiKeyInput.isBlank() && isUsingDefault)
                }
            }

    // 检测用户是否输入了自己的token
    val hasEnteredToken = apiKeyInput.isNotBlank() && apiKeyInput != ApiPreferences.DEFAULT_API_KEY

    // 默认API密钥效果
    LaunchedEffect(isUsingDefaultApiKey) {
        if (isUsingDefaultApiKey) {
            modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
            onModelNameChange(ApiPreferences.DEFAULT_MODEL_NAME)
        }
    }

    // 导航处理
    LaunchedEffect(isUsingDefault) {
        if (!isUsingDefault) {
            onNavigateToChat()
        }
    }

    // 密钥信息对话框
    if (showTokenInfoDialog) {
        TokenInfoDialog(
                onDismiss = { showTokenInfoDialog = false },
                onConfirm = {
                    showTokenInfoDialog = false
                    onNavigateToTokenConfig()
                }
        )
    }

    // 免费使用确认对话框
    if (showFreeUsageDialog) {
        FreeUsageConfirmDialog(
                onDismiss = { showFreeUsageDialog = false },
                onConfirm = {
                    showFreeUsageDialog = false

                    // Record usage
                    if (freeUsagePreferences.canUseFreeTier()) {
                        freeUsagePreferences.recordUsage()

                        // Apply free API settings
                        apiKeyInput = ""
                        apiEndpointInput = ApiPreferences.DEFAULT_API_ENDPOINT
                        modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME

                        onApiKeyChange(ApiPreferences.DEFAULT_API_KEY)
                        onApiEndpointChange(ApiPreferences.DEFAULT_API_ENDPOINT)
                        onModelNameChange(ApiPreferences.DEFAULT_MODEL_NAME)

                        onUseDefault()
                        showCustomFields = false
                    } else {
                        onError("今日免费次数已用完")
                    }
                },
                remainingUsages = remainingUsages,
                maxDailyUsage = maxDailyUsage
        )
    }

    // 主界面 - 简洁设计
    Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
            contentAlignment = Alignment.Center
    ) {
        Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            // 标题和说明
            Text(
                    text = "Operit AI 助手",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = "我们默认使用DeepSeek API，如需更换请点击下方自定义选项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // API密钥输入框 - 简洁设计
            OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API密钥") },
                    placeholder = { Text("DeepSeek API密钥") },
                    leadingIcon = {
                        Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "API密钥",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                            OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor =
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                            ),
                    singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 主按钮 - 根据输入状态动态变化
            Button(
                    onClick = {
                        if (hasEnteredToken) {
                            // 如果用户已输入token，直接保存配置
                            onApiKeyChange(apiKeyInput)
                            if (!showCustomFields) {
                                // 如果没有显示自定义字段，使用默认端点和模型
                                onApiEndpointChange(ApiPreferences.DEFAULT_API_ENDPOINT)
                                onModelNameChange(ApiPreferences.DEFAULT_MODEL_NAME)
                            }
                            onSaveConfig()
                        } else {
                            // 否则显示获取token的对话框
                            showTokenInfoDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor =
                                            if (hasEnteredToken)
                                                    MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.primary
                            )
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    if (hasEnteredToken) {
                        Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                            if (hasEnteredToken) "确认并保存" else "获取Token",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color =
                                    if (hasEnteredToken)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 底部选项 - 左右并排
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧 - 薅作者的
                TextButton(
                        onClick = {
                            // 显示确认对话框
                            showFreeUsageDialog = true
                        },
                        modifier = Modifier.weight(1f)
                ) {
                    Text(
                            "薅作者的",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                    )
                }

                // 分隔线
                Divider(
                        modifier =
                                Modifier.height(24.dp)
                                        .width(1.dp)
                                        .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 右侧 - 自定义
                TextButton(
                        onClick = { showCustomFields = !showCustomFields },
                        modifier = Modifier.weight(1f)
                ) {
                    Text(
                            "自定义",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                    )
                }
            }

            // 自定义配置区域 - 保持简洁
            AnimatedVisibility(visible = showCustomFields) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Divider(
                            modifier = Modifier.padding(bottom = 24.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // API接口地址
                    OutlinedTextField(
                            value = apiEndpointInput,
                            onValueChange = {
                                apiEndpointInput = it
                                onApiEndpointChange(it)

                                if (it.isNotBlank() && !ModelEndPointFix.containsCompletionsPath(it)
                                ) {
                                    endpointWarningMessage = "提示：应包含补全路径，如v1/chat/completions"
                                    showEndpointWarning = true

                                    coroutineScope.launch {
                                        delay(5000)
                                        showEndpointWarning = false
                                    }
                                } else {
                                    showEndpointWarning = false
                                }
                            },
                            label = { Text("API接口地址") },
                            placeholder = { Text("API地址应包含补全路径") },
                            leadingIcon = {
                                Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = "API接口地址",
                                        tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                    )

                    if (showEndpointWarning && endpointWarningMessage != null) {
                        Text(
                                text = endpointWarningMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                        if (endpointWarningMessage?.startsWith("警告") == true)
                                                MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                                fontSize = 12.sp
                        )
                    }

                    // 模型名称
                    OutlinedTextField(
                            value = modelNameInput,
                            onValueChange = {
                                if (!isUsingDefaultApiKey) {
                                    modelNameInput = it
                                    onModelNameChange(it)
                                }
                            },
                            label = { Text("模型名称") },
                            placeholder = { Text("例如：deepseek-chat") },
                            leadingIcon = {
                                Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = "模型名称",
                                        tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            enabled = !isUsingDefaultApiKey,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                    )

                    // 保存按钮
                    Button(
                            onClick = {
                                if (apiEndpointInput.isNotBlank() &&
                                                (apiKeyInput.isNotBlank() || isUsingDefault) &&
                                                modelNameInput.isNotBlank()
                                ) {
                                    // 检查端点格式
                                    if (!ModelEndPointFix.containsCompletionsPath(apiEndpointInput)
                                    ) {
                                        endpointWarningMessage = "警告：API地址不包含补全路径"
                                        showEndpointWarning = true
                                        coroutineScope.launch {
                                            delay(5000)
                                            showEndpointWarning = false
                                        }
                                    }

                                    // 保存API密钥
                                    if (apiKeyInput.isNotBlank() || !isUsingDefault) {
                                        onApiKeyChange(apiKeyInput)
                                    }

                                    // 保存配置
                                    onSaveConfig()
                                } else {
                                    onError("请完成所有配置项")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                    ) { Text("保存并开始使用", fontWeight = FontWeight.Medium) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 极简底部提示
            if (!showCustomFields) {
                Text(
                        text = "自己的token更加稳定，薅作者的用多了作者会破产，软件就没有更新了",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

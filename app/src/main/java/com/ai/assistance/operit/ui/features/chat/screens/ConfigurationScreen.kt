package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

@OptIn(ExperimentalMaterial3Api::class)
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
        onUseDefault: () -> Unit,
        isUsingDefault: Boolean,
        onNavigateToChat: () -> Unit,
        onNavigateToTokenConfig: () -> Unit,
        onNavigateToSettings: () -> Unit
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 记录是否是自定义配置
    val isCustomConfig = remember { mutableStateOf(!isUsingDefault) }

    // 表单状态
    val isEndpointValid = remember { mutableStateOf(apiEndpoint.isNotBlank()) }
    val isApiKeyValid = remember { mutableStateOf(apiKey.isNotBlank()) }
    val isModelNameValid = remember { mutableStateOf(modelName.isNotBlank()) }

    // 引入更多的模型选项
    val modelOptions = listOf(
        "claude-3-5-sonnet-20240620",
        "claude-3-opus-20240229",
        "claude-3-sonnet-20240229",
        "claude-3-haiku-20240307"
    )
    
    // 显示一些说明选项
    val showModelInfo = remember { mutableStateOf(false) }
    val showEndpointInfo = remember { mutableStateOf(false) }
    val showApiKeyInfo = remember { mutableStateOf(false) }

    // 获取Context
    val context = LocalContext.current

    // 初始化免费使用偏好
    val freeUsagePreferences = remember { FreeUsagePreferences(context) }
    val remainingUsages by freeUsagePreferences.remainingUsagesFlow.collectAsState()
    val maxDailyUsage = freeUsagePreferences.getMaxDailyUsage()

    // 状态管理
    var apiKeyInput by remember { mutableStateOf(if (isUsingDefault) "" else apiKey) }
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

                        onApiKeyChange(ApiPreferences.DEFAULT_API_KEY)
                        onApiEndpointChange(ApiPreferences.DEFAULT_API_ENDPOINT)
                        onModelNameChange(ApiPreferences.DEFAULT_MODEL_NAME)

                        onUseDefault()
                    } else {
                        onError("今日免费次数已用完")
                    }
                },
                remainingUsages = remainingUsages,
                maxDailyUsage = maxDailyUsage
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Text(
                    text = if (isUsingDefault) "默认API配置" else "自定义API配置", 
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 配置模式选择
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(
                        onClick = {
                            isCustomConfig.value = false
                            onUseDefault()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (!isCustomConfig.value) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "使用默认配置",
                            color = if (!isCustomConfig.value) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FilledTonalButton(
                        onClick = { isCustomConfig.value = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isCustomConfig.value) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "自定义配置",
                            color = if (isCustomConfig.value) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 自定义配置表单
                if (isCustomConfig.value) {
                    // API端点
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = apiEndpoint,
                            onValueChange = { 
                                onApiEndpointChange(it)
                                isEndpointValid.value = it.isNotBlank()
                            },
                            label = { Text("API端点") },
                            placeholder = { Text("例如: https://api.anthropic.com/v1") },
                            singleLine = true,
                            isError = !isEndpointValid.value,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                IconButton(onClick = { showEndpointInfo.value = true }) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "API端点信息"
                                    )
                                }
                            }
                        )
                    }
                    
                    if (showEndpointInfo.value) {
                        AlertDialog(
                            onDismissRequest = { showEndpointInfo.value = false },
                            title = { Text("API端点") },
                            text = { Text("API端点是用于访问Claude API的URL地址。通常为https://api.anthropic.com/v1。请确保您使用的是正确的端点，这可能会根据您使用的服务而有所不同。") },
                            confirmButton = {
                                TextButton(onClick = { showEndpointInfo.value = false }) {
                                    Text("确定")
                                }
                            }
                        )
                    }
                    
                    // API密钥
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { 
                                onApiKeyChange(it)
                                isApiKeyValid.value = it.isNotBlank()
                            },
                            label = { Text("API密钥") },
                            placeholder = { Text("您的Claude API密钥") },
                            singleLine = true,
                            isError = !isApiKeyValid.value,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                IconButton(onClick = { showApiKeyInfo.value = true }) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "API密钥信息"
                                    )
                                }
                            }
                        )
                    }
                    
                    if (showApiKeyInfo.value) {
                        AlertDialog(
                            onDismissRequest = { showApiKeyInfo.value = false },
                            title = { Text("API密钥") },
                            text = { Text("API密钥是访问Claude API所需的授权令牌。您可以从Anthropic开发者控制台获取此密钥。请妥善保管您的API密钥，不要与他人分享。") },
                            confirmButton = {
                                TextButton(onClick = { showApiKeyInfo.value = false }) {
                                    Text("确定")
                                }
                            }
                        )
                    }
                    
                    // 模型选择
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = showModelInfo.value,
                            onExpandedChange = { showModelInfo.value = it }
                        ) {
                            OutlinedTextField(
                                value = modelName,
                                onValueChange = { 
                                    onModelNameChange(it)
                                    isModelNameValid.value = it.isNotBlank()
                                },
                                label = { Text("模型名称") },
                                readOnly = true,
                                singleLine = true,
                                isError = !isModelNameValid.value,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelInfo.value)
                                }
                            )
                            
                            ExposedDropdownMenu(
                                expanded = showModelInfo.value,
                                onDismissRequest = { showModelInfo.value = false }
                            ) {
                                modelOptions.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            onModelNameChange(model)
                                            isModelNameValid.value = model.isNotBlank()
                                            showModelInfo.value = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // 保存按钮
                    Button(
                        onClick = {
                            if (isCustomConfig.value) {
                                if (apiEndpoint.isBlank()) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("API端点不能为空")
                                    }
                                    return@Button
                                }
                                if (apiKey.isBlank()) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("API密钥不能为空")
                                    }
                                    return@Button
                                }
                                if (modelName.isBlank()) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("模型名称不能为空")
                                    }
                                    return@Button
                                }
                            }
                            
                            onSaveConfig()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存自定义配置")
                    }
                } else {
                    // 默认配置信息
                    Text(
                        text = "使用默认配置将会使用内置的API密钥和端点。这适用于快速测试和体验，但如果您需要自定义设置或更高的使用限制，建议使用自己的API密钥。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Button(
                        onClick = onNavigateToChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("继续使用默认配置")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // 其他设置选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onNavigateToTokenConfig,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("令牌设置")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("应用设置")
                    }
                }
            }
        }

        // 底部返回按钮
        OutlinedButton(
            onClick = onNavigateToChat,
            modifier = Modifier
                .padding(top = 16.dp)
                .align(Alignment.Start)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "返回聊天",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("返回聊天")
        }
    }

    // 显示提示信息
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(16.dp)
    )
}

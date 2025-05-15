package com.ai.assistance.operit.ui.features.chat.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.promotion.PromotionalTexts
import com.ai.assistance.operit.util.ModelEndPointFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onUseDefault: () -> Unit = {}, // 使用默认配置的回调
    isUsingDefault: Boolean = false, // 标识是否在使用默认配置
    onNavigateToChat: () -> Unit = {}, // 导航到聊天界面的回调
    onNavigateToTokenConfig: () -> Unit = {} // 导航到Token配置界面的回调
) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val scrollState = rememberScrollState()

    // 推广对话框状态
    var showPromotionDialog by remember { mutableStateOf(false) }
    var currentPromotionText by remember {
        mutableStateOf(PromotionalTexts.getPromotionByCategory(1))
    }
    var currentCategory by remember { mutableStateOf(1) } // 0 for functional, 1 for memes
    var isSharing by remember { mutableStateOf(false) }
    var shareSuccess by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    // 获取屏幕尺寸以适配不同设备
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isTablet = screenWidthDp >= 600

    // 根据是否为平板调整整体布局
    val mainPadding = if (isTablet) 16.dp else 12.dp
    val fieldHeight = if (isTablet) 56.dp else 52.dp
    val cardWidth = if (isTablet) 0.7f else 0.95f

    // 直接从 DataStore 读取最新设置
    val storedApiKey = apiPreferences.apiKeyFlow.collectAsState(initial = "").value
    val storedApiEndpoint = apiPreferences.apiEndpointFlow.collectAsState(initial = "").value
    val storedModelName = apiPreferences.modelNameFlow.collectAsState(initial = "").value

    // 如果不是使用默认配置，则自动导航到聊天界面
    LaunchedEffect(isUsingDefault) {
        if (!isUsingDefault) {
            onNavigateToChat()
            return@LaunchedEffect
        }
    }

    // 本地状态用于表单输入 - 修改初始化逻辑，保留用户输入
    var apiKeyInput by remember { mutableStateOf("") }
    var apiEndpointInput by remember { mutableStateOf(apiEndpoint) }
    var modelNameInput by remember { mutableStateOf(modelName) }

    // 添加标记表示是否显示端点修复提示
    var showEndpointFixedInfo by remember { mutableStateOf(false) }
    
    // 添加警告消息
    var endpointWarningMessage by remember { mutableStateOf<String?>(null) }

    // 添加一个标志，用于控制是否允许更新API密钥
    var shouldUpdateApiKey by remember { mutableStateOf(false) }

    // 当从 DataStore 读取到的值改变时，更新本地状态
    LaunchedEffect(Unit) {
        // API Key 字段保持为空，不从存储中加载值
        if (apiEndpointInput.isBlank()) {
            apiEndpointInput = storedApiEndpoint
            onApiEndpointChange(storedApiEndpoint)
        }
        if (modelNameInput.isBlank()) {
            modelNameInput = storedModelName
            onModelNameChange(storedModelName)
        }
    }

    val modernTextStyle = TextStyle(fontSize = 14.sp)

    // 宣传弹窗
    if (showPromotionDialog) {
        Dialog(onDismissRequest = { showPromotionDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 标题
                    Text(
                        text = "助力推广，共创未来",
                        style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 说明文字 - 修改为更有感染力的表达，明确要求分享一次
                    Text(
                        text =
                        "作为开源项目，我们希望您的帮助让更多人发现这款AI助手！分享的话，软件未来可能会更好哦！",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 推广文案卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                        CardDefaults.cardColors(
                            containerColor =
                            MaterialTheme.colorScheme
                                .primaryContainer
                        )
                    ) {
                        // 创建滚动状态
                        val promotionScrollState = rememberScrollState()

                        Column(
                            modifier =
                            Modifier.padding(16.dp)
                                .height(160.dp) // 增加高度从120dp到160dp
                                .verticalScroll(
                                    promotionScrollState
                                ), // 添加滚动功能
                            horizontalAlignment =
                            Alignment.CenterHorizontally
                        ) {
                            // 动画效果显示推广文案
                            AnimatedVisibility(
                                visible = true,
                                enter =
                                fadeIn(
                                    animationSpec =
                                    tween(300)
                                ),
                                exit =
                                fadeOut(
                                    animationSpec =
                                    tween(300)
                                )
                            ) {
                                Text(
                                    text = currentPromotionText,
                                    style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium.copy(
                                            fontSize = 13.sp
                                        ), // 从bodyLarge改为bodyMedium并设置更小的字体大小
                                    textAlign =
                                    TextAlign.Center,
                                    color =
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 成功消息
                    AnimatedVisibility(
                        visible = showSuccessMessage,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300))
                    ) {
                        Text(
                            text = "感谢分享！默认配置已成功启用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 刷新按钮 - 在功能型和梗型文案之间切换
                        Button(
                            onClick = {
                                // 切换文案类别
                                currentCategory =
                                    1 -
                                        currentCategory // Toggle between 0 and 1
                                currentPromotionText =
                                    PromotionalTexts
                                        .getPromotionByCategory(
                                            currentCategory
                                        )
                            },
                            colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .secondaryContainer,
                                contentColor =
                                MaterialTheme
                                    .colorScheme
                                    .onSecondaryContainer
                            ),
                            modifier =
                            Modifier.weight(1f)
                                .padding(end = 8.dp),
                            contentPadding =
                            PaddingValues(
                                horizontal = 8.dp,
                                vertical = 8.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "切换文案风格",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (currentCategory == 0) "抽象梗"
                                else "正经文",
                                maxLines = 1,
                                style =
                                MaterialTheme.typography
                                    .labelMedium
                            )
                        }

                        // 分享按钮 - 简化为使用更短的文字
                        Button(
                            onClick = {
                                if (!isSharing) {
                                    isSharing = true

                                    // 创建分享意图（跳转到QQ）
                                    val shareIntent =
                                        Intent().apply {
                                            action =
                                                Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                currentPromotionText
                                            )
                                            type =
                                                "text/plain"
                                            // 尝试指定QQ包名（实际实现可能需要调整）
                                            setPackage(
                                                "com.tencent.mobileqq"
                                            )
                                        }

                                    try {
                                        context.startActivity(
                                            shareIntent
                                        )

                                        // 模拟分享成功检测（实际应用中可能需要更复杂的逻辑）
                                        coroutineScope
                                            .launch {
                                                delay(
                                                    2000
                                                ) // 延迟2秒
                                                shareSuccess =
                                                    true
                                                showSuccessMessage =
                                                    true

                                                // 延迟后执行默认配置并关闭对话框
                                                delay(
                                                    1500
                                                )

                                                // 保存当前输入到ViewModel
                                                onApiEndpointChange(
                                                    apiEndpointInput
                                                )
                                                if (apiKeyInput
                                                        .isNotBlank()
                                                ) {
                                                    onApiKeyChange(
                                                        apiKeyInput
                                                    )
                                                }
                                                onModelNameChange(
                                                    modelNameInput
                                                )

                                                // 调用使用默认配置的回调
                                                onUseDefault()

                                                // 关闭对话框
                                                showPromotionDialog =
                                                    false
                                            }
                                    } catch (e: Exception) {
                                        // 处理没有安装QQ的情况
                                        coroutineScope
                                            .launch {
                                                // 显示通用分享选择器
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        Intent()
                                                            .apply {
                                                                action =
                                                                    Intent.ACTION_SEND
                                                                putExtra(
                                                                    Intent.EXTRA_TEXT,
                                                                    currentPromotionText
                                                                )
                                                                type =
                                                                    "text/plain"
                                                            },
                                                        "分享到"
                                                    )
                                                )

                                                delay(
                                                    2000
                                                ) // 延迟2秒
                                                shareSuccess =
                                                    true
                                                showSuccessMessage =
                                                    true

                                                // 延迟后执行默认配置并关闭对话框
                                                delay(
                                                    1500
                                                )
                                                onApiEndpointChange(
                                                    apiEndpointInput
                                                )
                                                if (apiKeyInput
                                                        .isNotBlank()
                                                ) {
                                                    onApiKeyChange(
                                                        apiKeyInput
                                                    )
                                                }
                                                onModelNameChange(
                                                    modelNameInput
                                                )
                                                onUseDefault()
                                                showPromotionDialog =
                                                    false
                                            }
                                    } finally {
                                        isSharing = false
                                    }
                                }
                            },
                            colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                                contentColor =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimary
                            ),
                            enabled = !isSharing,
                            modifier = Modifier.weight(1f),
                            contentPadding =
                            PaddingValues(
                                horizontal = 8.dp,
                                vertical = 8.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "分享到QQ",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "分享",
                                maxLines = 1,
                                style =
                                MaterialTheme.typography
                                    .labelMedium
                            )
                        }
                    }

                    // 添加直接使用按钮
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            // 直接使用默认配置，无需分享
                            // 保存当前输入到ViewModel
                            onApiEndpointChange(apiEndpointInput)
                            if (apiKeyInput.isNotBlank()) {
                                onApiKeyChange(apiKeyInput)
                            }
                            onModelNameChange(modelNameInput)

                            // 调用使用默认配置的回调
                            onUseDefault()

                            // 关闭对话框
                            showPromotionDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor =
                            MaterialTheme.colorScheme
                                .tertiary
                        ),
                        border =
                        BorderStroke(
                            width = 1.dp,
                            color =
                            MaterialTheme.colorScheme
                                .tertiary.copy(
                                    alpha = 0.5f
                                )
                        ),
                        contentPadding =
                        PaddingValues(
                            horizontal = 8.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text(
                            "直接使用",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(mainPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier =
            Modifier.fillMaxWidth(cardWidth)
                .padding(8.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 紧凑的标题区域
            Text(
                text = "配置AI助手",
                style =
                MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // 子标题/状态提示
            Text(
                text =
                if (isUsingDefault) "当前使用默认配置，可直接开始使用"
                else "请设置API信息或使用默认配置",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // API接口地址输入框
            OutlinedTextField(
                value = apiEndpointInput,
                onValueChange = {
                    apiEndpointInput = it
                    
                    // 更新ViewModel值
                    onApiEndpointChange(apiEndpointInput)
                    
                    // 检查端点，但不自动修复，而是显示警告
                    if (it.isNotBlank()) {
                        // 如果不包含completions路径，显示警告
                        if (!ModelEndPointFix.containsCompletionsPath(it)) {
                            endpointWarningMessage = "提示：API地址应包含补全路径，如v1/chat/completions"
                            showEndpointFixedInfo = true
                            // 自动隐藏提示
                            coroutineScope.launch {
                                delay(5000)
                                showEndpointFixedInfo = false
                            }
                        } else {
                            // 正确包含了completions
                            showEndpointFixedInfo = false
                        }
                    }
                },
                label = { Text("API接口地址", fontSize = 13.sp) },
                placeholder = { Text("API地址应包含补全路径", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = "API接口地址",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textStyle = modernTextStyle,
                shape = RoundedCornerShape(8.dp),
                colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor =
                    MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor =
                    MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor =
                    MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                maxLines = 1
            )

            // 显示端点相关提示
            if (showEndpointFixedInfo) {
                Text(
                    text = endpointWarningMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (endpointWarningMessage?.startsWith("警告") == true) 
                        MaterialTheme.colorScheme.error
                    else 
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                    fontSize = 11.sp
                )
            }

            // API密钥输入框
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = {
                    apiKeyInput = it
                    // 只在特定情况下才更新ViewModel
                    if (shouldUpdateApiKey) {
                        onApiKeyChange(it)
                    }
                },
                label = { Text("API密钥", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "API密钥",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textStyle = modernTextStyle,
                shape = RoundedCornerShape(8.dp),
                colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor =
                    MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor =
                    MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor =
                    MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                maxLines = 1
            )

            // 模型名称输入框
            OutlinedTextField(
                value = modelNameInput,
                onValueChange = {
                    modelNameInput = it
                    onModelNameChange(it)
                },
                label = { Text("模型名称", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "模型名称",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textStyle = modernTextStyle,
                shape = RoundedCornerShape(8.dp),
                colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor =
                    MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor =
                    MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor =
                    MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                maxLines = 1
            )

            // 保存按钮
            Button(
                onClick = {
                    // 检查endpoint是否包含completions，如果不包含则显示警告但不自动修复
                    if (apiEndpointInput.isNotBlank() && !ModelEndPointFix.containsCompletionsPath(apiEndpointInput)) {
                        endpointWarningMessage = "警告：您的API地址不包含补全路径（如v1/chat/completions）。请确保这是您想要的配置。"
                        showEndpointFixedInfo = true
                        // 显示较长时间
                        coroutineScope.launch {
                            delay(6000)
                            showEndpointFixedInfo = false
                        }
                    }

                    if (apiEndpointInput.isNotBlank() &&
                        apiKeyInput.isNotBlank() &&
                        modelNameInput.isNotBlank()
                    ) {
                        // 只在保存时更新API密钥
                        shouldUpdateApiKey = true
                        onApiKeyChange(apiKeyInput)
                        shouldUpdateApiKey = false

                        onSaveConfig()
                    } else {
                        onError("请输入API密钥、接口地址和模型名称")
                    }
                },
                modifier =
                Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "保存并开始使用",
                    style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // 分隔线
            Box(
                modifier =
                Modifier.fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(
                        brush =
                        Brush.horizontalGradient(
                            colors =
                            listOf(
                                Color.Transparent,
                                MaterialTheme
                                    .colorScheme
                                    .outlineVariant,
                                MaterialTheme
                                    .colorScheme
                                    .outlineVariant,
                                Color.Transparent
                            )
                        )
                    )
            )

            // 底部选项区域 - 更紧凑的按钮排列
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 使用默认配置按钮 - 修改为显示推广对话框
                Button(
                    onClick = {
                        // 显示推广对话框而不是直接使用默认配置
                        showPromotionDialog = true
                    },
                    modifier =
                    Modifier.weight(1f) // 相同的权重
                        .height(40.dp), // 减小高度
                    shape = RoundedCornerShape(8.dp),
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                        MaterialTheme.colorScheme
                            .primaryContainer,
                        contentColor =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer
                    ),
                    contentPadding =
                    PaddingValues(
                        horizontal = 6.dp,
                        vertical = 4.dp
                    ) // 减小内部padding
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "使用默认配置",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "默认配置",
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }

                // 获取Token按钮
                Button(
                    onClick = {
                        // 使用内部导航而不是Intent跳转到外部浏览器
                        onNavigateToTokenConfig()
                    },
                    modifier =
                    Modifier.weight(1f) // 相同的权重
                        .height(40.dp), // 减小高度
                    shape = RoundedCornerShape(8.dp),
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                        MaterialTheme.colorScheme
                            .secondaryContainer,
                        contentColor =
                        MaterialTheme.colorScheme
                            .onSecondaryContainer
                    ),
                    contentPadding =
                    PaddingValues(
                        horizontal = 6.dp,
                        vertical = 4.dp
                    ) // 减小内部padding
                ) {
                    Icon(
                        imageVector = Icons.Filled.Token,
                        contentDescription = "获取Token",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "获取Token",
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }

                // 捐赠支持按钮
                Button(
                    onClick = { /* 跳转到捐赠页面 */},
                    modifier =
                    Modifier.weight(1f) // 相同的权重
                        .height(40.dp), // 减小高度
                    shape = RoundedCornerShape(8.dp),
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                        MaterialTheme.colorScheme
                            .tertiaryContainer,
                        contentColor =
                        MaterialTheme.colorScheme
                            .onTertiaryContainer
                    ),
                    contentPadding =
                    PaddingValues(
                        horizontal = 6.dp,
                        vertical = 4.dp
                    ) // 减小内部padding
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "捐赠支持",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "捐赠",
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            // 底部说明文字 - 更紧凑的样式
            Text(
                text =
                "默认配置使用开发者的API接口和费用，您可以通过自定义配置使用自己的API。接口地址必须包含v1/chat/completions路径。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
            )
        }
    }
}

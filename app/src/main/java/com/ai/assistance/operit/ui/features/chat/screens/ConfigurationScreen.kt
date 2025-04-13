package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.CoroutineScope
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
        onNavigateToChat: () -> Unit = {} // 导航到聊天界面的回调
) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val scrollState = rememberScrollState()
    
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(cardWidth)
                .padding(8.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 紧凑的标题区域
            Text(
                text = "配置AI助手",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 子标题/状态提示
            Text(
                text = if (isUsingDefault) 
                    "当前使用默认配置，可直接开始使用" 
                else 
                    "请设置API信息或使用默认配置",
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
                    onApiEndpointChange(it)
                },
                label = { Text("API接口地址", fontSize = 13.sp) },
                placeholder = { Text("必须使用v1/chat/completions", fontSize = 12.sp) },
                leadingIcon = { 
                    Icon(
                        imageVector = Icons.Filled.Public, 
                        contentDescription = "API接口地址",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    ) 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textStyle = modernTextStyle,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                maxLines = 1
            )

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textStyle = modernTextStyle,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textStyle = modernTextStyle,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                maxLines = 1
            )

            // 保存按钮
            Button(
                onClick = {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) { 
                Text(
                    "保存并开始使用",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                ) 
            }

            // 分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.colorScheme.outlineVariant,
                                Color.Transparent
                            )
                        )
                    )
            )

            // 底部选项区域 - 更紧凑的按钮排列
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 使用默认配置按钮
                Button(
                    onClick = { 
                        // 使用默认配置时，将本地状态中的值也传递给ViewModel
                        onApiEndpointChange(apiEndpointInput)
                        if (apiKeyInput.isNotBlank()) {
                            onApiKeyChange(apiKeyInput)
                        }
                        onModelNameChange(modelNameInput)
                        onUseDefault() 
                    },
                    modifier = Modifier
                        .weight(1f) // 相同的权重
                        .height(40.dp), // 减小高度
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp) // 减小内部padding
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "使用默认配置",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "默认配置",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }

                // 捐赠支持按钮
                Button(
                    onClick = { /* 跳转到捐赠页面 */ },
                    modifier = Modifier
                        .weight(1f) // 相同的权重
                        .height(40.dp), // 减小高度
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp) // 减小内部padding
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "捐赠支持",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "捐赠",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            // 底部说明文字 - 更紧凑的样式
            Text(
                text = "默认配置使用开发者的API接口和费用，您可以通过自定义配置使用自己的API。接口地址必须包含v1/chat/completions路径。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp)
            )
        }
    }
}

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
    // 但只在组件首次加载时进行更新，防止用户输入被覆盖
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(12.dp)
                    .shadow(
                        elevation = 8.dp,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题区域
                Text(
                        text = "配置AI助手",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                )
                
                // 子标题/状态指示
                Text(
                    text = if (isUsingDefault) "当前使用默认配置，可直接开始使用" else "请设置您的API信息或使用默认配置",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // 主要配置内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                                .padding(bottom = 8.dp)
                                .height(52.dp),
                            textStyle = modernTextStyle,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                    )

                    // API密钥输入框 - 修改为使用本地状态，仅在保存时更新ViewModel
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
                                .padding(bottom = 8.dp)
                                .height(52.dp),
                            textStyle = modernTextStyle,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
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
                                .padding(bottom = 12.dp)
                                .height(52.dp),
                            textStyle = modernTextStyle,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
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
                                .height(46.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 2.dp
                            )
                    ) { 
                        Text(
                            "保存并开始使用",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        ) 
                    }
                }

                // 分隔线
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                Spacer(modifier = Modifier.height(14.dp))

                // 下半部分：默认配置选项和捐赠支持
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 继续使用默认区域
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 简单的提示文字
                        Text(
                            text = "快速开始",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // 继续使用默认按钮
                        ElevatedButton(
                            onClick = { 
                                // 使用默认配置时，将本地状态中的值也传递给ViewModel
                                onApiEndpointChange(apiEndpointInput)
                                // 如果用户输入了密钥，传递给ViewModel
                                if (apiKeyInput.isNotBlank()) {
                                    onApiKeyChange(apiKeyInput)
                                }
                                onModelNameChange(modelNameInput)
                                onUseDefault() 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            elevation = ButtonDefaults.elevatedButtonElevation(
                                defaultElevation = 1.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "继续使用默认",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "使用默认配置",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    // 捐赠支持区域
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 捐赠标题
                        Text(
                            text = "支持开发",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // 捐赠按钮
                        Button(
                            onClick = { /* 跳转到捐赠页面 */ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 1.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "捐赠支持",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "捐赠支持",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // 底部说明文字
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "默认配置使用开发者的API接口和费用，您可以通过自定义配置使用自己的API。接口地址必须包含v1/chat/completions路径。您的支持将帮助我们持续改进应用。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

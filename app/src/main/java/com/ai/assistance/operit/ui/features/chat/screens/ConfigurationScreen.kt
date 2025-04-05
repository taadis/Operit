package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.CoroutineScope

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
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    
    // 直接从 DataStore 读取最新设置
    val storedApiKey = apiPreferences.apiKeyFlow.collectAsState(initial = "").value
    val storedApiEndpoint = apiPreferences.apiEndpointFlow.collectAsState(initial = "").value
    val storedModelName = apiPreferences.modelNameFlow.collectAsState(initial = "").value
    
    // 本地状态用于表单输入
    var apiKeyInput by remember { mutableStateOf(apiKey) }
    var apiEndpointInput by remember { mutableStateOf(apiEndpoint) }
    var modelNameInput by remember { mutableStateOf(modelName) }
    
    // 当从 DataStore 读取到的值改变时，更新本地状态
    // 但只在组件首次加载时进行更新，防止用户输入被覆盖
    LaunchedEffect(storedApiKey, storedApiEndpoint, storedModelName) {
        if (apiKeyInput == apiKey && apiKey.isBlank()) {
            apiKeyInput = storedApiKey
            onApiKeyChange(storedApiKey)
        }
        if (apiEndpointInput == apiEndpoint && apiEndpoint.isBlank()) {
            apiEndpointInput = storedApiEndpoint
            onApiEndpointChange(storedApiEndpoint)
        }
        if (modelNameInput == modelName && modelName.isBlank()) {
            modelNameInput = storedModelName
            onModelNameChange(storedModelName)
        }
    }
    
    val modernTextStyle = TextStyle(
        fontSize = 14.sp
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .shadow(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "配置AI助手",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                OutlinedTextField(
                    value = apiEndpointInput,
                    onValueChange = { 
                        apiEndpointInput = it
                        onApiEndpointChange(it)
                    },
                    label = { Text("API接口地址") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textStyle = modernTextStyle,
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { 
                        apiKeyInput = it
                        onApiKeyChange(it)
                    },
                    label = { Text("API密钥") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textStyle = modernTextStyle,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = modelNameInput,
                    onValueChange = { 
                        modelNameInput = it
                        onModelNameChange(it)
                    },
                    label = { Text("模型名称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    textStyle = modernTextStyle,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Button(
                    onClick = { 
                        if (apiEndpointInput.isNotBlank() && apiKeyInput.isNotBlank() && modelNameInput.isNotBlank()) {
                            onSaveConfig()
                        } else {
                            onError("请输入API密钥、接口地址和模型名称")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("保存并开始使用")
                }
            }
        }
    }
} 
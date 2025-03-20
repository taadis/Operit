package com.ai.assistance.operit.ui.features.settings.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.ApiPreferences
import kotlinx.coroutines.launch
import com.ai.assistance.operit.model.UserPreferences
import com.ai.assistance.operit.data.preferencesManager
import java.lang.StringBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToUserPreferences: () -> Unit,
    navigateToToolPermissions: () -> Unit
) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val scope = rememberCoroutineScope()
    
    // Collect API settings as state
    val apiKey = apiPreferences.apiKeyFlow.collectAsState(initial = "").value
    val apiEndpoint = apiPreferences.apiEndpointFlow.collectAsState(initial = ApiPreferences.DEFAULT_API_ENDPOINT).value
    val modelName = apiPreferences.modelNameFlow.collectAsState(initial = ApiPreferences.DEFAULT_MODEL_NAME).value
    val showThinking = apiPreferences.showThinkingFlow.collectAsState(initial = ApiPreferences.DEFAULT_SHOW_THINKING).value
    
    // Mutable state for editing
    var apiKeyInput by remember { mutableStateOf(apiKey) }
    var apiEndpointInput by remember { mutableStateOf(apiEndpoint) }
    var modelNameInput by remember { mutableStateOf(modelName) }
    var showThinkingInput by remember { mutableStateOf(showThinking) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    
    // Update local state when preferences change
    LaunchedEffect(apiKey, apiEndpoint, modelName, showThinking) {
        apiKeyInput = apiKey
        apiEndpointInput = apiEndpoint
        modelNameInput = modelName
        showThinkingInput = showThinking
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(id = R.string.settings),
            style = MaterialTheme.typography.titleMedium,
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.api_settings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = apiEndpointInput,
                    onValueChange = { apiEndpointInput = it },
                    label = { Text(stringResource(id = R.string.api_endpoint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text(stringResource(id = R.string.api_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = modelNameInput,
                    onValueChange = { modelNameInput = it },
                    label = { Text(stringResource(id = R.string.model_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                
                Button(
                    onClick = {
                        scope.launch {
                            apiPreferences.saveAllSettings(
                                apiKeyInput,
                                apiEndpointInput,
                                modelNameInput,
                                showThinkingInput
                            )
                            showSaveSuccessMessage = true
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(id = R.string.save_settings))
                }
                
                if (showSaveSuccessMessage) {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        showSaveSuccessMessage = false
                    }
                    
                    Text(
                        text = stringResource(id = R.string.settings_saved),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.display_settings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(id = R.string.show_thinking),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
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
            }
        }
        
        // 工具权限设置卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
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
                ) {
                    Text("管理工具权限")
                }
            }
        }

        // 用户偏好设置
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.user_preferences),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val userPreferences = preferencesManager.userPreferencesFlow.collectAsState(initial = UserPreferences()).value
                
                if (userPreferences.isInitialized && userPreferences.preferences.isNotEmpty()) {
                    // 显示用户偏好信息
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.custom_description),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = userPreferences.preferences,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // 用户信息摘要
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (userPreferences.gender.isNotEmpty()) {
                                Text(
                                    text = "${stringResource(id = R.string.gender)}: ${userPreferences.gender}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            if (userPreferences.occupation.isNotEmpty()) {
                                Text(
                                    text = "${stringResource(id = R.string.occupation)}: ${userPreferences.occupation}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            if (userPreferences.age > 0) {
                                Text(
                                    text = "${stringResource(id = R.string.age)}: ${userPreferences.age}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    // 按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        // 重置偏好按钮
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    preferencesManager.resetPreferences()
                                }
                            }
                        ) {
                            Text(stringResource(id = R.string.reset_preferences))
                        }
                        
                        // 修改偏好按钮
                        Button(
                            onClick = onNavigateToUserPreferences
                        ) {
                            Text(stringResource(id = R.string.set_preferences))
                        }
                    }
                } else {
                    // 尚未设置偏好时显示
                    Text(
                        text = stringResource(id = R.string.no_preferences),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    
                    // 设置偏好按钮
                    Button(
                        onClick = onNavigateToUserPreferences,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(id = R.string.set_preferences))
                    }
                }
            }
        }
    }
} 
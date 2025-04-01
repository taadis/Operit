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
import com.ai.assistance.operit.data.ChatHistoryManager
import androidx.compose.ui.text.font.FontWeight

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
    val memoryOptimization = apiPreferences.memoryOptimizationFlow.collectAsState(initial = ApiPreferences.DEFAULT_MEMORY_OPTIMIZATION).value
    
    // Mutable state for editing
    var apiKeyInput by remember { mutableStateOf(apiKey) }
    var apiEndpointInput by remember { mutableStateOf(apiEndpoint) }
    var modelNameInput by remember { mutableStateOf(modelName) }
    var showThinkingInput by remember { mutableStateOf(showThinking) }
    var memoryOptimizationInput by remember { mutableStateOf(memoryOptimization) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    
    // Update local state when preferences change
    LaunchedEffect(apiKey, apiEndpoint, modelName, showThinking, memoryOptimization) {
        apiKeyInput = apiKey
        apiEndpointInput = apiEndpoint
        modelNameInput = modelName
        showThinkingInput = showThinking
        memoryOptimizationInput = memoryOptimization
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
                                showThinkingInput,
                                memoryOptimizationInput
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
                    Column {
                        Text(
                            text = stringResource(id = R.string.show_thinking),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "有的模型不具备思考能力，就可以关掉它",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = memoryOptimizationInput,
                        onCheckedChange = { 
                            memoryOptimizationInput = it
                            scope.launch {
                                apiPreferences.saveMemoryOptimization(it)
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
                        
                        // 添加偏好文本编辑功能
                        var isEditingPreferences by remember { mutableStateOf(false) }
                        var editedPreferences by remember { mutableStateOf(userPreferences.preferences) }
                        
                        if (isEditingPreferences) {
                            // 编辑模式
                            OutlinedTextField(
                                value = editedPreferences,
                                onValueChange = { editedPreferences = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .height(120.dp),
                                label = { Text(stringResource(id = R.string.edit_preferences)) }
                            )
                            
                            // 保存和取消按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                TextButton(
                                    onClick = { 
                                        isEditingPreferences = false 
                                        editedPreferences = userPreferences.preferences
                                    }
                                ) {
                                    Text(stringResource(id = R.string.cancel))
                                }
                                
                                Button(
                                    onClick = {
                                        scope.launch {
                                            preferencesManager.updatePreferencesText(editedPreferences)
                                            isEditingPreferences = false
                                        }
                                    }
                                ) {
                                    Text(stringResource(id = R.string.save))
                                }
                            }
                        } else {
                            // 显示模式
                            Text(
                                text = userPreferences.preferences,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // 编辑按钮
                            IconButton(
                                onClick = { 
                                    isEditingPreferences = true 
                                    editedPreferences = userPreferences.preferences
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(id = R.string.edit_preferences)
                                )
                            }
                        }
                        
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
        
        // Token统计卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
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
                        totalInputTokens = histories.sumOf { it.inputTokens }
                        totalOutputTokens = histories.sumOf { it.outputTokens }
                    }
                }
                
                // 读取偏好分析token计数
                LaunchedEffect(Unit) {
                    apiPreferences.preferenceAnalysisInputTokensFlow.collect { tokens ->
                        preferenceAnalysisInputTokens = tokens
                    }
                }
                
                LaunchedEffect(Unit) {
                    apiPreferences.preferenceAnalysisOutputTokensFlow.collect { tokens ->
                        preferenceAnalysisOutputTokens = tokens
                    }
                }
                
                // 计算费用（使用DeepSeek的价格）
                // DeepSeek价格：输入tokens $0.27/百万，输出tokens $1.10/百万
                val chatInputCost = totalInputTokens * 0.27 / 1_000_000
                val chatOutputCost = totalOutputTokens * 1.10 / 1_000_000
                val preferenceAnalysisInputCost = preferenceAnalysisInputTokens * 0.27 / 1_000_000
                val preferenceAnalysisOutputCost = preferenceAnalysisOutputTokens * 1.10 / 1_000_000
                val totalCost = chatInputCost + chatOutputCost + preferenceAnalysisInputCost + preferenceAnalysisOutputCost
                
                // 统计信息表格
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    TokenStatRow(
                        label = "聊天输入Token",
                        value = totalInputTokens.toString(),
                        cost = "$${String.format("%.4f", chatInputCost)}"
                    )
                    
                    TokenStatRow(
                        label = "聊天输出Token",
                        value = totalOutputTokens.toString(),
                        cost = "$${String.format("%.4f", chatOutputCost)}"
                    )
                    
                    TokenStatRow(
                        label = "偏好分析输入Token",
                        value = preferenceAnalysisInputTokens.toString(),
                        cost = "$${String.format("%.4f", preferenceAnalysisInputCost)}"
                    )
                    
                    TokenStatRow(
                        label = "偏好分析输出Token",
                        value = preferenceAnalysisOutputTokens.toString(),
                        cost = "$${String.format("%.4f", preferenceAnalysisOutputCost)}"
                    )
                    
                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    TokenStatRow(
                        label = "总计",
                        value = (totalInputTokens + totalOutputTokens + preferenceAnalysisInputTokens + preferenceAnalysisOutputTokens).toString(),
                        cost = "$${String.format("%.4f", totalCost)}",
                        isHighlighted = true
                    )
                }
                
                // 重置按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                apiPreferences.resetPreferenceAnalysisTokens()
                                showSaveSuccessMessage = true
                            }
                        }
                    ) {
                        Text("重置偏好分析计数")
                    }
                }
                
                // 幽默解释
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "为什么我要关心Token统计？",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Text(
                            text = "因为它就像一个饭店账单，只不过这里的'饭'是AI的思考，而'钱'是以微小得离谱的美元计算的！偏好分析是AI暗中观察你的口味，为你定制下次的\"菜单\"。别担心，即使你聊到手指抽筋，这些费用可能还不够买一杯咖啡～",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = cost,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
} 
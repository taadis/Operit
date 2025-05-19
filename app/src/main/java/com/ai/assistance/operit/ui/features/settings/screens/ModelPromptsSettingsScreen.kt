package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.model.PromptProfile
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.PromptPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPromptsSettingsScreen() {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val promptPreferencesManager = remember { PromptPreferencesManager(context) }
    val scope = rememberCoroutineScope()
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // 提示词配置文件列表
    val profileList = promptPreferencesManager.profileListFlow.collectAsState(initial = listOf("default")).value
    val activeProfileId = promptPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default").value

    // 对话框状态
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    // 选中的配置文件
    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    val selectedProfile = remember { mutableStateOf<PromptProfile?>(null) }

    // 编辑状态
    var editMode by remember { mutableStateOf(false) }

    // 默认提示词
    val defaultIntroPrompt = promptPreferencesManager.defaultIntroPrompt
    val defaultTonePrompt = promptPreferencesManager.defaultTonePrompt

    // 编辑状态的提示词
    var introPromptInput by remember { mutableStateOf(defaultIntroPrompt) }
    var tonePromptInput by remember { mutableStateOf(defaultTonePrompt) }

    // 动画状态
    val listState = rememberLazyListState()

    // 初始化提示词配置
    LaunchedEffect(Unit) {
        promptPreferencesManager.initializeIfNeeded()
    }

    // 加载选中的配置文件
    LaunchedEffect(selectedProfileId) {
        promptPreferencesManager.getPromptProfileFlow(selectedProfileId).collect { profile ->
            selectedProfile.value = profile
            // 初始化编辑字段
            introPromptInput = profile.introPrompt
            tonePromptInput = profile.tonePrompt
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editMode = !editMode },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (editMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (editMode) "完成编辑" else "编辑提示词"
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 配置文件选择区域
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Message,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "提示词配置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedVisibility(
                                    visible = selectedProfile.value != null && editMode,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val profile = selectedProfile.value
                                                if (profile != null) {
                                                    // 保存到自定义提示词配置
                                                    promptPreferencesManager.updatePromptProfile(
                                                        profileId = profile.id,
                                                        introPrompt = introPromptInput,
                                                        tonePrompt = tonePromptInput
                                                    )
                                                    
                                                    // 如果是当前激活的配置，也更新到ApiPreferences
                                                    if (profile.isActive) {
                                                        apiPreferences.saveCustomPrompts(
                                                            introPrompt = introPromptInput,
                                                            tonePrompt = tonePromptInput
                                                        )
                                                    }
                                                    
                                                    showSaveSuccessMessage = true
                                                    editMode = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = "保存",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showAddProfileDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "新建",
                                        fontSize = 14.sp,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        // 配置文件列表
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(profileList) { profileId ->
                                val isActive = profileId == activeProfileId
                                val isSelected = profileId == selectedProfileId
                                val profileName = runBlocking {
                                    promptPreferencesManager.getPromptProfileFlow(profileId).first().name
                                }

                                // 配置项
                                PromptProfileItem(
                                    profileName = profileName,
                                    isActive = isActive,
                                    isSelected = isSelected,
                                    onSelect = {
                                        selectedProfileId = profileId
                                        editMode = false
                                    },
                                    onActivate = {
                                        scope.launch {
                                            promptPreferencesManager.setActiveProfile(profileId)
                                            
                                            // 更新激活的配置到ApiPreferences
                                            val profile = promptPreferencesManager.getPromptProfileFlow(profileId).first()
                                            apiPreferences.saveCustomPrompts(
                                                introPrompt = profile.introPrompt,
                                                tonePrompt = profile.tonePrompt
                                            )
                                        }
                                    },
                                    onDelete = if (profileId != "default") {
                                        {
                                            scope.launch {
                                                promptPreferencesManager.deleteProfile(profileId)
                                                if (selectedProfileId == profileId) {
                                                    selectedProfileId = activeProfileId
                                                }
                                            }
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 提示词详情
                AnimatedVisibility(
                    visible = selectedProfile.value != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val profile = selectedProfile.value
                    if (profile != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Message,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "自定义系统提示词",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "定制AI的行为和语气风格",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // AI self-introduction prompt
                                Text(
                                    text = "AI自我介绍",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                OutlinedTextField(
                                    value = if (editMode) introPromptInput else profile.introPrompt,
                                    onValueChange = { if (editMode) introPromptInput = it },
                                    label = { Text("自我介绍提示词") },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    placeholder = { Text(defaultIntroPrompt) },
                                    minLines = 3,
                                    enabled = editMode
                                )

                                // AI tone and style prompt
                                Text(
                                    text = "AI语气风格",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                                )

                                OutlinedTextField(
                                    value = if (editMode) tonePromptInput else profile.tonePrompt,
                                    onValueChange = { if (editMode) tonePromptInput = it },
                                    label = { Text("语气风格提示词") },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    placeholder = { Text(defaultTonePrompt) },
                                    minLines = 3,
                                    enabled = editMode
                                )

                                if (editMode) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(
                                            onClick = {
                                                introPromptInput = defaultIntroPrompt
                                                tonePromptInput = defaultTonePrompt
                                            }
                                        ) { Text("恢复默认提示词") }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    // 保存到提示词配置
                                                    promptPreferencesManager.updatePromptProfile(
                                                        profileId = profile.id,
                                                        introPrompt = introPromptInput,
                                                        tonePrompt = tonePromptInput
                                                    )
                                                    
                                                    // 如果是当前激活的配置，也更新到ApiPreferences
                                                    if (profile.isActive) {
                                                        apiPreferences.saveCustomPrompts(
                                                            introPrompt = introPromptInput,
                                                            tonePrompt = tonePromptInput
                                                        )
                                                    }
                                                    
                                                    showSaveSuccessMessage = true
                                                    editMode = false
                                                }
                                            }
                                        ) { Text("保存提示词") }
                                    }
                                }
                            }
                        }
                    }
                }

                // 提示词解释卡片
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "关于系统提示词",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "系统提示词用于定义AI助手的行为和风格：",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            PromptInfoRow("自我介绍", "定义AI的身份和能力范围")
                            PromptInfoRow("语气风格", "设置AI回复的语气和表达方式")
                        }
                        
                        Text(
                            text = "提示：有效的系统提示词能让AI助手更好地符合您的期望，但过长的提示词可能会消耗更多Token。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 成功保存消息
                if (showSaveSuccessMessage) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "提示词已保存",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LaunchedEffect(showSaveSuccessMessage) {
                        kotlinx.coroutines.delay(3000)
                        showSaveSuccessMessage = false
                    }
                }
            }
        }
    }
    
    // 新建配置文件对话框
    if (showAddProfileDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddProfileDialog = false
                newProfileName = ""
            },
            title = {
                Text(
                    "新建提示词配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "创建新的提示词配置，个性化AI助手行为",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("配置名称", fontSize = 12.sp) },
                        placeholder = { Text("例如: 工作助手、学习助手...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            scope.launch {
                                val profileId = promptPreferencesManager.createProfile(newProfileName)
                                selectedProfileId = profileId
                                showAddProfileDialog = false
                                newProfileName = ""
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) { Text("创建", fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddProfileDialog = false
                        newProfileName = ""
                    }
                ) { Text("取消", fontSize = 13.sp) }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun PromptProfileItem(
    profileName: String,
    isActive: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onActivate: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            isActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 1.dp else 0.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.size(36.dp)
            )

            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isActive) {
                    Text(
                        text = "当前激活",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isActive) {
                    OutlinedButton(
                        onClick = onActivate,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(28.dp)
                    ) { 
                        Text("激活", style = MaterialTheme.typography.labelMedium, fontSize = 13.sp) 
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PromptInfoRow(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
    }
}
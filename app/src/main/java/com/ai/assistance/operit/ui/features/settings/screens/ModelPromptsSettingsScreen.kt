package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.data.model.PromptProfile
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.PromptPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ModelPromptsSettingsScreen(
        onBackPressed: () -> Unit = {},
        onNavigateToFunctionalPrompts: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val promptPreferencesManager = remember { PromptPreferencesManager(context) }
    val scope = rememberCoroutineScope()
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // 提示词配置文件列表
    val profileList =
            promptPreferencesManager.profileListFlow.collectAsState(initial = listOf("default"))
                    .value

    // 对话框状态
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    // 选中的配置文件
    var selectedProfileId by remember { mutableStateOf(profileList.firstOrNull() ?: "default") }
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

    // 下拉菜单状态
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = promptPreferencesManager.getPromptProfileFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    // 初始化提示词配置
    LaunchedEffect(Unit) { promptPreferencesManager.initializeIfNeeded() }

    // 加载选中的配置文件
    LaunchedEffect(selectedProfileId) {
        promptPreferencesManager.getPromptProfileFlow(selectedProfileId).collect { profile ->
            selectedProfile.value = profile
            // 初始化编辑字段
            introPromptInput = profile.introPrompt
            tonePromptInput = profile.tonePrompt
        }
    }

    // 保存提示词函数
    fun savePrompts() {
        val profile = selectedProfile.value
        if (profile != null) {
            scope.launch {
                // 保存到提示词配置
                promptPreferencesManager.updatePromptProfile(
                    profileId = profile.id,
                    introPrompt = introPromptInput,
                    tonePrompt = tonePromptInput
                )
                
                showSaveSuccessMessage = true
                editMode = false
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    if (editMode) {
                        // 如果是编辑模式，点击时保存
                        savePrompts()
                    } else {
                        // 如果不是编辑模式，进入编辑模式
                        editMode = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (editMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (editMode) "保存提示词" else "编辑提示词"
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .verticalScroll(rememberScrollState())
            ) {
                // 配置文件选择区域 - 卡片内的布局重新组织
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        
                        // 配置选择器区 - 标签和新建按钮放在一行
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 配置文件选择标签 - 更大的字体
                            Text(
                                "请选择提示词配置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            
                            // 新建按钮 - 更小的尺寸
                            OutlinedButton(
                                onClick = { showAddProfileDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    "新建",
                                    fontSize = 12.sp,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        
                        val selectedProfileName = profileNameMap[selectedProfileId] ?: "默认配置"
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            tonalElevation = 0.5.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = selectedProfileName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                AnimatedContent(
                                    targetState = isDropdownExpanded,
                                    transitionSpec = {
                                        fadeIn() + scaleIn() with fadeOut() + scaleOut()
                                    }
                                ) { expanded ->
                                    Icon(
                                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "选择配置",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable { onNavigateToFunctionalPrompts() }
                                                .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Text(
                                        text = "为功能分配提示词配置",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                )
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "为功能分配提示词配置",
                                        tint = MaterialTheme.colorScheme.primary
                                )
                        }
                        
                        // 操作按钮
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 删除按钮
                            if (selectedProfileId != "default") {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            promptPreferencesManager.deleteProfile(selectedProfileId)
                                            selectedProfileId = profileList.firstOrNull { it != selectedProfileId } ?: "default"
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("删除", fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // 下拉菜单
                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.width(280.dp),
                        properties = PopupProperties(focusable = true)
                    ) {
                        profileList.forEach { profileId ->
                            val profileName = profileNameMap[profileId] ?: "未命名配置"
                            val isSelected = profileId == selectedProfileId
                            
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = profileName,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = null,
                                trailingIcon = if (isSelected) { {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }} else null,
                                onClick = {
                                    selectedProfileId = profileId
                                    isDropdownExpanded = false
                                    editMode = false
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = if (isSelected) MaterialTheme.colorScheme.primary 
                                              else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            
                            if (profileId != profileList.last()) {
                                Divider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp)
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
                                colors =
                                        CardDefaults.cardColors(
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
                                        value =
                                                if (editMode) introPromptInput
                                                else profile.introPrompt,
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
                                        value =
                                                if (editMode) tonePromptInput
                                                else profile.tonePrompt,
                                    onValueChange = { if (editMode) tonePromptInput = it },
                                    label = { Text("语气风格提示词") },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    placeholder = { Text(defaultTonePrompt) },
                                    minLines = 3,
                                    enabled = editMode
                                )

                                if (editMode) {
                                    Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                introPromptInput = defaultIntroPrompt
                                                tonePromptInput = defaultTonePrompt
                                            }
                                        ) { Text("恢复默认提示词") }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                savePrompts()
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
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
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
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.primaryContainer
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
                                        val profileId =
                                                promptPreferencesManager.createProfile(
                                                        newProfileName
                                                )
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
fun PromptInfoRow(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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

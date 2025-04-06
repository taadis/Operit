package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.preferencesManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPreferencesSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGuide: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // 获取所有配置文件
    val profileList by preferencesManager.profileListFlow.collectAsState(initial = emptyList())
    val activeProfileId by preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    
    // 确保默认配置文件存在并在列表中显示
    LaunchedEffect(Unit) {
        // 检查配置列表是否为空，或者不包含默认配置
        if (profileList.isEmpty() || !profileList.contains("default")) {
            // 创建默认配置
            val defaultProfileId = preferencesManager.createProfile("默认配置", isDefault = true)
            preferencesManager.setActiveProfile(defaultProfileId)
        }
    }
    
    // 分类锁定状态
    val categoryLockStatus by preferencesManager.categoryLockStatusFlow.collectAsState(initial = emptyMap())
    
    // 对话框状态
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    
    // 选中的配置文件
    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var selectedProfile by remember { mutableStateOf<PreferenceProfile?>(null) }
    
    // 编辑状态
    var editMode by remember { mutableStateOf(false) }
    var editAge by remember { mutableStateOf("") }
    var editGender by remember { mutableStateOf("") }
    var editPersonality by remember { mutableStateOf("") }
    var editIdentity by remember { mutableStateOf("") }
    var editOccupation by remember { mutableStateOf("") }
    var editAiStyle by remember { mutableStateOf("") }
    
    // 加载选中的配置文件
    LaunchedEffect(selectedProfileId) {
        preferencesManager.getUserPreferencesFlow(selectedProfileId).collect { profile ->
            selectedProfile = profile
            // 初始化编辑字段
            editAge = if (profile.age > 0) profile.age.toString() else ""
            editGender = profile.gender
            editPersonality = profile.personality
            editIdentity = profile.identity
            editOccupation = profile.occupation
            editAiStyle = profile.aiStyle
        }
    }
    
    Scaffold() { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(12.dp)  // 增加整体内边距
        ) {
            // 配置文件选择区域 - 更紧凑的头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),  // 增加间距
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "选择配置文件",
                    style = MaterialTheme.typography.titleMedium,  // 恢复使用标题字体
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),  // 增加按钮间距
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedProfile != null) {
                        IconButton(
                            onClick = { editMode = !editMode },
                            modifier = Modifier.size(40.dp)  // 增加按钮大小
                        ) {
                            Icon(
                                if (editMode) Icons.Default.Save else Icons.Default.Edit,
                                contentDescription = if (editMode) "保存" else "编辑",
                                modifier = Modifier.size(22.dp)  // 增加图标大小
                            )
                        }
                    }
                    
                    Button(
                        onClick = { showAddProfileDialog = true },
                        modifier = Modifier.height(38.dp),  // 增加按钮高度
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)  // 增加内部填充
                    ) {
                        Text("新建配置", fontSize = 14.sp)  // 增加字体大小
                    }
                }
            }
            
            // 配置文件列表 - 更紧凑的列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)  // 增加高度
                    .padding(bottom = 12.dp),  // 增加间距
                verticalArrangement = Arrangement.spacedBy(4.dp)  // 增加项目间距
            ) {
                items(profileList) { profileId ->
                    val isActive = profileId == activeProfileId
                    val isSelected = profileId == selectedProfileId
                    
                    // 获取配置文件名称
                    val profileName = runBlocking {
                        preferencesManager.getUserPreferencesFlow(profileId).first().name
                    }
                    
                    // 更紧凑的项目布局
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),  // 增加垂直间距
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedProfileId = profileId
                                editMode = false
                            },
                            modifier = Modifier.size(36.dp)  // 增加单选按钮大小
                        )
                        
                        Text(
                            text = profileName,
                            fontSize = 15.sp,  // 增加字体大小
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)  // 增加间距
                        )
                        
                        if (isActive) {
                            Text(
                                text = "(当前)",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,  // 增加字体大小
                                modifier = Modifier.padding(end = 8.dp)  // 增加间距
                            )
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    preferencesManager.setActiveProfile(profileId)
                                }
                            },
                            enabled = !isActive,
                            modifier = Modifier.height(30.dp),  // 增加按钮高度
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        ) {
                            Text("激活", fontSize = 12.sp)  // 增加字体大小
                        }
                        
                        // 只有非默认配置才显示删除按钮
                        if (profileId != "default") {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        preferencesManager.deleteProfile(profileId)
                                        if (selectedProfileId == profileId) {
                                            selectedProfileId = activeProfileId
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)  // 增加按钮大小
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)  // 增加图标大小
                                )
                            }
                        }
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 6.dp))  // 增加分隔线间距
            
            // 配置文件详情 - 使用单一 LazyColumn 避免嵌套
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)  // 增加项目间距
            ) {
                selectedProfile?.let { profile ->
                    // 标题和引导按钮
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),  // 增加间距
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "配置: ${profile.name}",
                                style = MaterialTheme.typography.titleMedium,  // 使用更大的标题字体
                            )
                            
                            // 添加引导配置按钮
                            if (!editMode) {
                                TextButton(
                                    onClick = {
                                        onNavigateToGuide(profile.name, profile.id)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)  // 增加内部填充
                                ) {
                                    Text("引导配置", fontSize = 14.sp)  // 增加字体大小
                                }
                            }
                        }
                    }
                    
                    // 年龄
                    item {
                        CompactPreferenceCategoryItem(
                            title = "年龄",
                            value = if (profile.age > 0) profile.age.toString() else "未设置",
                            editValue = editAge,
                            onValueChange = { editAge = it },
                            isLocked = categoryLockStatus["age"] ?: false,
                            onLockChange = { locked ->
                                scope.launch {
                                    preferencesManager.setCategoryLocked("age", locked)
                                }
                            },
                            editMode = editMode,
                            isNumeric = true
                        )
                    }
                    
                    // 性别
                    item {
                        CompactPreferenceCategoryItem(
                            title = "性别",
                            value = profile.gender.ifEmpty { "未设置" },
                            editValue = editGender,
                            onValueChange = { editGender = it },
                            isLocked = categoryLockStatus["gender"] ?: false,
                            onLockChange = { locked ->
                                scope.launch {
                                    preferencesManager.setCategoryLocked("gender", locked)
                                }
                            },
                            editMode = editMode
                        )
                    }
                    
                    // 性格特点
                    item {
                        CompactPreferenceCategoryItem(
                            title = "性格特点",
                            value = profile.personality.ifEmpty { "未设置" },
                            editValue = editPersonality,
                            onValueChange = { editPersonality = it },
                            isLocked = categoryLockStatus["personality"] ?: false,
                            onLockChange = { locked ->
                                scope.launch {
                                    preferencesManager.setCategoryLocked("personality", locked)
                                }
                            },
                            editMode = editMode
                        )
                    }
                    
                    // 身份认同
                    item {
                        CompactPreferenceCategoryItem(
                            title = "身份认同",
                            value = profile.identity.ifEmpty { "未设置" },
                            editValue = editIdentity,
                            onValueChange = { editIdentity = it },
                            isLocked = categoryLockStatus["identity"] ?: false,
                            onLockChange = { locked ->
                                scope.launch {
                                    preferencesManager.setCategoryLocked("identity", locked)
                                }
                            },
                            editMode = editMode
                        )
                    }
                    
                    // 职业
                    item {
                        CompactPreferenceCategoryItem(
                            title = "职业",
                            value = profile.occupation.ifEmpty { "未设置" },
                            editValue = editOccupation,
                            onValueChange = { editOccupation = it },
                            isLocked = categoryLockStatus["occupation"] ?: false,
                            onLockChange = { locked ->
                                scope.launch {
                                    preferencesManager.setCategoryLocked("occupation", locked)
                                }
                            },
                            editMode = editMode
                        )
                    }
                    
                    // AI风格偏好
                    item {
                        CompactPreferenceCategoryItem(
                            title = "AI风格",
                            value = profile.aiStyle.ifEmpty { "未设置" },
                            editValue = editAiStyle,
                            onValueChange = { editAiStyle = it },
                            isLocked = categoryLockStatus["aiStyle"] ?: false,
                            onLockChange = { locked ->
                                scope.launch {
                                    preferencesManager.setCategoryLocked("aiStyle", locked)
                                }
                            },
                            editMode = editMode
                        )
                    }
                    
                    // 保存按钮（编辑模式时显示）
                    if (editMode) {
                        item {
                            Button(
                                onClick = {
                                    scope.launch {
                                        preferencesManager.updateProfileCategory(
                                            profileId = profile.id,
                                            age = editAge.toIntOrNull(),
                                            gender = editGender.takeIf { it.isNotBlank() },
                                            personality = editPersonality.takeIf { it.isNotBlank() },
                                            identity = editIdentity.takeIf { it.isNotBlank() },
                                            occupation = editOccupation.takeIf { it.isNotBlank() },
                                            aiStyle = editAiStyle.takeIf { it.isNotBlank() }
                                        )
                                        editMode = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),  // 增加间距
                                contentPadding = PaddingValues(vertical = 12.dp)  // 增加内部填充
                            ) {
                                Text("保存更改", fontSize = 16.sp)  // 增加字体大小
                            }
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
                title = { Text("新建偏好配置") },
                text = {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("配置名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProfileName.isNotBlank()) {
                                scope.launch {
                                    val newProfileId = preferencesManager.createProfile(newProfileName)
                                    selectedProfileId = newProfileId
                                    showAddProfileDialog = false
                                    
                                    // 导航到引导页，传递配置ID和名称
                                    onNavigateToGuide(newProfileName, newProfileId)
                                }
                            }
                        }
                    ) {
                        Text("创建并配置")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddProfileDialog = false
                            newProfileName = ""
                        }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

// 更紧凑的偏好分类项
@Composable
fun CompactPreferenceCategoryItem(
    title: String,
    value: String,
    editValue: String,
    onValueChange: (String) -> Unit,
    isLocked: Boolean,
    onLockChange: (Boolean) -> Unit,
    editMode: Boolean,
    isNumeric: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),  // 增加外部间距
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)  // 增加内部填充
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp  // 增加标题字体
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)  // 增加水平间距
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "已锁定" else "未锁定",
                        modifier = Modifier.size(20.dp)  // 增加图标大小
                    )
                    
                    Switch(
                        checked = isLocked,
                        onCheckedChange = onLockChange,
                        modifier = Modifier.scale(0.8f)  // 调整开关大小
                    )
                }
            }
            
            if (editMode) {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { 
                        if (isNumeric) {
                            if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                onValueChange(it)
                            }
                        } else {
                            onValueChange(it)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),  // 增加间距
                    enabled = !isLocked,
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),  // 增加字体大小
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            } else {
                Text(
                    text = value, 
                    fontSize = 15.sp,  // 增加字体大小
                    modifier = Modifier.padding(top = 4.dp)  // 增加间距
                )
            }
        }
    }
} 
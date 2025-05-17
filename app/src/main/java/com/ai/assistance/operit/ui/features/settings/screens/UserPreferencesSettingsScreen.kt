package com.ai.assistance.operit.ui.features.settings.screens

import android.app.DatePickerDialog as AndroidDatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
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
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.preferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val activeProfileId by
            preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")

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
    val categoryLockStatus by
            preferencesManager.categoryLockStatusFlow.collectAsState(initial = emptyMap())

    // 对话框状态
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    // 选中的配置文件
    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var selectedProfile by remember { mutableStateOf<PreferenceProfile?>(null) }

    // 编辑状态
    var editMode by remember { mutableStateOf(false) }
    var editBirthDate by remember { mutableStateOf(0L) }
    var editGender by remember { mutableStateOf("") }
    var editPersonality by remember { mutableStateOf("") }
    var editIdentity by remember { mutableStateOf("") }
    var editOccupation by remember { mutableStateOf("") }
    var editAiStyle by remember { mutableStateOf("") }

    // 日期选择器状态
    val dateFormatter = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())

    // 动画状态
    val listState = rememberLazyListState()

    // 加载选中的配置文件
    LaunchedEffect(selectedProfileId) {
        preferencesManager.getUserPreferencesFlow(selectedProfileId).collect { profile ->
            selectedProfile = profile
            // 初始化编辑字段
            editBirthDate = profile.birthDate
            editGender = profile.gender
            editPersonality = profile.personality
            editIdentity = profile.identity
            editOccupation = profile.occupation
            editAiStyle = profile.aiStyle
        }
    }

    // 日期选择器函数
    val showDatePickerDialog = {
        val calendar =
                Calendar.getInstance().apply {
                    if (editBirthDate > 0) {
                        timeInMillis = editBirthDate
                    } else {
                        set(Calendar.YEAR, 1990)
                        set(Calendar.MONTH, Calendar.JANUARY)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        AndroidDatePickerDialog(
                        context,
                        { _, selectedYear, selectedMonth, selectedDay ->
                            val selectedCalendar =
                                    Calendar.getInstance().apply {
                                        set(Calendar.YEAR, selectedYear)
                                        set(Calendar.MONTH, selectedMonth)
                                        set(Calendar.DAY_OF_MONTH, selectedDay)
                                    }
                            editBirthDate = selectedCalendar.timeInMillis
                        },
                        year,
                        month,
                        day
                )
                .show()
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
                            contentDescription = if (editMode) "完成编辑" else "编辑配置"
                    )
                }
            }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                // 配置文件选择区域
                Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
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
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                        text = "偏好配置",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedVisibility(
                                        visible = selectedProfile != null && editMode,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    IconButton(
                                            onClick = {
                                                scope.launch {
                                                    preferencesManager.updateProfileCategory(
                                                            profileId = selectedProfile?.id ?: "",
                                                            birthDate = editBirthDate,
                                                            gender =
                                                                    editGender.takeIf {
                                                                        it.isNotBlank()
                                                                    },
                                                            personality =
                                                                    editPersonality.takeIf {
                                                                        it.isNotBlank()
                                                                    },
                                                            identity =
                                                                    editIdentity.takeIf {
                                                                        it.isNotBlank()
                                                                    },
                                                            occupation =
                                                                    editOccupation.takeIf {
                                                                        it.isNotBlank()
                                                                    },
                                                            aiStyle =
                                                                    editAiStyle.takeIf {
                                                                        it.isNotBlank()
                                                                    }
                                                    )
                                                    editMode = false
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
                                        border =
                                                BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary
                                                ),
                                        contentPadding =
                                                PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp),
                                        colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                        contentColor =
                                                                MaterialTheme.colorScheme.primary
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

                        // 配置文件列表 - 使用更现代的卡片设计
                        LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth().height(130.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(profileList) { profileId ->
                                val isActive = profileId == activeProfileId
                                val isSelected = profileId == selectedProfileId

                                // 获取配置文件名称
                                val profileName = runBlocking {
                                    preferencesManager
                                            .getUserPreferencesFlow(profileId)
                                            .first()
                                            .name
                                }

                                // 现代化的配置项布局
                                ProfileItem(
                                        profileName = profileName,
                                        isActive = isActive,
                                        isSelected = isSelected,
                                        onSelect = {
                                            selectedProfileId = profileId
                                            editMode = false
                                        },
                                        onActivate = {
                                            scope.launch {
                                                preferencesManager.setActiveProfile(profileId)
                                            }
                                        },
                                        onDelete =
                                                if (profileId != "default") {
                                                    {
                                                        scope.launch {
                                                            preferencesManager.deleteProfile(
                                                                    profileId
                                                            )
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

                // 配置文件详情
                AnimatedVisibility(
                        visible = selectedProfile != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                ) {
                    selectedProfile?.let { profile ->
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                // 标题和引导按钮
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                text = "${profile.name}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 添加引导配置按钮
                                    if (!editMode) {
                                        OutlinedButton(
                                                onClick = {
                                                    onNavigateToGuide(profile.name, profile.id)
                                                },
                                                shape = RoundedCornerShape(16.dp),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.primary
                                                        ),
                                                contentPadding =
                                                        PaddingValues(
                                                                horizontal = 10.dp,
                                                                vertical = 6.dp
                                                        ),
                                                modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                    Icons.Default.Assistant,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("配置向导", fontSize = 14.sp)
                                        }
                                    }
                                }

                                // 偏好分类项
                                LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 出生日期
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "出生日期",
                                                value =
                                                        if (profile.birthDate > 0)
                                                                dateFormatter.format(
                                                                        Date(profile.birthDate)
                                                                )
                                                        else "未设置",
                                                editMode = editMode,
                                                isLocked = categoryLockStatus["birthDate"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "birthDate",
                                                                locked
                                                        )
                                                    }
                                                },
                                                icon = Icons.Default.Cake,
                                                onDatePickerClick = {
                                                    if (editMode &&
                                                                    !(categoryLockStatus[
                                                                            "birthDate"]
                                                                            ?: false)
                                                    ) {
                                                        showDatePickerDialog()
                                                    }
                                                },
                                                dateValue = editBirthDate
                                        )
                                    }

                                    // 性别
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "性别",
                                                value = profile.gender.ifEmpty { "未设置" },
                                                editValue = editGender,
                                                onValueChange = { editGender = it },
                                                isLocked = categoryLockStatus["gender"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "gender",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Face
                                        )
                                    }

                                    // 性格特点
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "性格特点",
                                                value = profile.personality.ifEmpty { "未设置" },
                                                editValue = editPersonality,
                                                onValueChange = { editPersonality = it },
                                                isLocked = categoryLockStatus["personality"]
                                                                ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "personality",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Psychology
                                        )
                                    }

                                    // 身份认同
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "身份认同",
                                                value = profile.identity.ifEmpty { "未设置" },
                                                editValue = editIdentity,
                                                onValueChange = { editIdentity = it },
                                                isLocked = categoryLockStatus["identity"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "identity",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Badge
                                        )
                                    }

                                    // 职业
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "职业",
                                                value = profile.occupation.ifEmpty { "未设置" },
                                                editValue = editOccupation,
                                                onValueChange = { editOccupation = it },
                                                isLocked = categoryLockStatus["occupation"]
                                                                ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "occupation",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Work
                                        )
                                    }

                                    // AI风格偏好
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "AI风格",
                                                value = profile.aiStyle.ifEmpty { "未设置" },
                                                editValue = editAiStyle,
                                                onValueChange = { editAiStyle = it },
                                                isLocked = categoryLockStatus["aiStyle"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "aiStyle",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.SmartToy
                                        )
                                    }

                                    // 保存按钮（编辑模式时显示）
                                    if (editMode) {
                                        item {
                                            Button(
                                                    onClick = {
                                                        scope.launch {
                                                            preferencesManager
                                                                    .updateProfileCategory(
                                                                            profileId = profile.id,
                                                                            birthDate =
                                                                                    editBirthDate,
                                                                            gender =
                                                                                    editGender
                                                                                            .takeIf {
                                                                                                it.isNotBlank()
                                                                                            },
                                                                            personality =
                                                                                    editPersonality
                                                                                            .takeIf {
                                                                                                it.isNotBlank()
                                                                                            },
                                                                            identity =
                                                                                    editIdentity
                                                                                            .takeIf {
                                                                                                it.isNotBlank()
                                                                                            },
                                                                            occupation =
                                                                                    editOccupation
                                                                                            .takeIf {
                                                                                                it.isNotBlank()
                                                                                            },
                                                                            aiStyle =
                                                                                    editAiStyle
                                                                                            .takeIf {
                                                                                                it.isNotBlank()
                                                                                            }
                                                                    )
                                                            editMode = false
                                                        }
                                                    },
                                                    modifier =
                                                            Modifier.fillMaxWidth()
                                                                    .padding(top = 8.dp),
                                                    contentPadding = PaddingValues(vertical = 8.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                        "保存更改",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
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
                    title = {
                        Text(
                                "新建偏好配置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                    "创建新的偏好配置，个性化AI助手体验",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    label = { Text("配置名称", fontSize = 12.sp) },
                                    placeholder = { Text("例如: 工作、学习、娱乐...", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                            OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor =
                                                            MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor =
                                                            MaterialTheme.colorScheme.outlineVariant
                                            ),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    if (newProfileName.isNotBlank()) {
                                        scope.launch {
                                            val newProfileId =
                                                    preferencesManager.createProfile(newProfileName)
                                            selectedProfileId = newProfileId
                                            showAddProfileDialog = false

                                            // 导航到引导页，传递配置ID和名称
                                            onNavigateToGuide(newProfileName, newProfileId)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                        ) { Text("创建并配置", fontSize = 13.sp) }
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
}

@Composable
fun ProfileItem(
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
            color =
                    when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        isActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surface
                    },
            border =
                    BorderStroke(
                            width = if (isSelected) 1.dp else 0.dp,
                            color =
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                    )
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    colors =
                            RadioButtonDefaults.colors(
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
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
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
                    ) { Text("激活", style = MaterialTheme.typography.labelMedium, fontSize = 13.sp) }
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
fun ModernPreferenceCategoryItem(
        title: String,
        value: String,
        editValue: String = "",
        onValueChange: (String) -> Unit = {},
        isLocked: Boolean,
        onLockChange: (Boolean) -> Unit,
        editMode: Boolean,
        isNumeric: Boolean = false,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        placeholder: String = "请输入${title}信息",
        dateValue: Long = 0L,
        onDatePickerClick: () -> Unit = {}
) {
    val animatedElevation by
            animateDpAsState(
                    targetValue = if (editMode && !isLocked) 2.dp else 0.dp,
                    label = "elevation"
            )

    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .shadow(
                                    elevation = animatedElevation,
                                    shape = RoundedCornerShape(8.dp)
                            ),
            shape = RoundedCornerShape(8.dp),
            color =
                    if (isLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.surface,
            border =
                    BorderStroke(
                            width = if (editMode && !isLocked) 1.dp else 0.dp,
                            color =
                                    if (editMode && !isLocked) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp
                    )
                }

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                            if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) "已锁定" else "未锁定",
                            tint =
                                    if (isLocked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                    )

                    Switch(
                            checked = isLocked,
                            onCheckedChange = onLockChange,
                            modifier = Modifier.scale(0.8f),
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor =
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                            uncheckedTrackColor =
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            AnimatedContent(
                    targetState = editMode,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut())
                    },
                    label = "edit mode transition"
            ) { isEditMode ->
                if (isEditMode) {
                    if (title == "出生日期") {
                        // 出生日期使用点击卡片打开日期选择器
                        Card(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(50.dp)
                                                .clickable(
                                                        enabled = !isLocked,
                                                        onClick = onDatePickerClick
                                                ),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                disabledContainerColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.8f)
                                        )
                        ) {
                            Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text =
                                                if (dateValue > 0)
                                                        SimpleDateFormat(
                                                                        "yyyy年MM月dd日",
                                                                        Locale.getDefault()
                                                                )
                                                                .format(Date(dateValue))
                                                else "请选择出生日期",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color =
                                                if (isLocked)
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                                else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = "选择日期",
                                        tint =
                                                if (isLocked)
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                                else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
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
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                enabled = !isLocked,
                                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                                shape = RoundedCornerShape(6.dp),
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor =
                                                        MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor =
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                disabledBorderColor =
                                                        MaterialTheme.colorScheme.outlineVariant
                                                                .copy(alpha = 0.5f),
                                                disabledTextColor =
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                        ),
                                placeholder = {
                                    Text(
                                            placeholder,
                                            color =
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.6f
                                                    ),
                                            fontSize = 16.sp
                                    )
                                }
                        )
                    }
                } else {
                    val displayText =
                            if (value == "未设置") {
                                "未设置${title}"
                            } else {
                                value
                            }

                    Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                            color =
                                    if (value == "未设置")
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

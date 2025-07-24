package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import kotlinx.coroutines.launch
import com.ai.assistance.operit.data.model.PromptProfile
import com.ai.assistance.operit.data.preferences.FunctionalPromptManager
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import com.ai.assistance.operit.data.preferences.PromptPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import java.text.DecimalFormat
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.outlined.Portrait

@Composable
fun ChatSettingsBar(
    modifier: Modifier = Modifier,
    enableAiPlanning: Boolean,
    onToggleAiPlanning: () -> Unit,
    permissionLevel: PermissionLevel,
    onTogglePermission: () -> Unit,
    enableThinkingMode: Boolean,
    onToggleThinkingMode: () -> Unit,
    enableThinkingGuidance: Boolean,
    onToggleThinkingGuidance: () -> Unit,
    maxWindowSizeInK: Float,
    onContextLengthChange: (Float) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val iconScale by animateFloatAsState(
        targetValue = if (showMenu) 1.2f else 1f,
        label = "iconScale"
    )

    // 用于显示详情说明的状态，现在使用一个Pair来保存标题和内容
    var infoPopupContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showPromptDropdown by remember { mutableStateOf(false) }
    var showMemoryDropdown by remember { mutableStateOf(false) }

    // 将模型选择逻辑封装到组件内部
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val configMapping by functionalConfigManager.functionConfigMappingFlow.collectAsState(initial = emptyMap())
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    LaunchedEffect(Unit) {
        configSummaries = modelConfigManager.getAllConfigSummaries()
    }
    val currentConfigId = configMapping[FunctionType.CHAT] ?: FunctionalConfigManager.DEFAULT_CONFIG_ID

    // 将提示词选择逻辑封装到组件内部
    val functionalPromptManager = remember { FunctionalPromptManager(context) }
    val promptPreferencesManager = remember { PromptPreferencesManager(context) }
    val promptMapping by functionalPromptManager.functionPromptMappingFlow.collectAsState(initial = emptyMap())
    var promptProfiles by remember { mutableStateOf<List<PromptProfile>>(emptyList()) }
    LaunchedEffect(Unit) {
        val profileIds = promptPreferencesManager.profileListFlow.first()
        promptProfiles = profileIds.map { id ->
            promptPreferencesManager.getPromptProfileFlow(id).first()
        }
    }
    val currentPromptProfileId = promptMapping[PromptFunctionType.CHAT] ?: FunctionalPromptManager.getDefaultProfileIdForFunction(PromptFunctionType.CHAT)

    // 新增：用户偏好（记忆）选择逻辑
    val userPreferencesManager = remember { UserPreferencesManager(context) }
    val activeProfileId by userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    var preferenceProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    LaunchedEffect(Unit) {
        val profileIds = userPreferencesManager.profileListFlow.first()
        preferenceProfiles = profileIds.map { id ->
            userPreferencesManager.getUserPreferencesFlow(id).first()
        }
    }


    val onSelectModel: (String) -> Unit = { selectedId ->
        scope.launch {
            functionalConfigManager.setConfigForFunction(
                FunctionType.CHAT,
                selectedId
            )
            EnhancedAIService.refreshServiceForFunction(
                context,
                FunctionType.CHAT
            )
        }
    }
    
    val onSelectPrompt: (String) -> Unit = { selectedId ->
        scope.launch {
            functionalPromptManager.setPromptProfileForFunction(
                PromptFunctionType.CHAT,
                selectedId
            )
            EnhancedAIService.refreshServiceForFunction(
                context,
                FunctionType.CHAT
            )
        }
    }

    val onSelectMemory: (String) -> Unit = { selectedId ->
        scope.launch {
            userPreferencesManager.setActiveProfile(selectedId)
            // 用户偏好和记忆库绑定，可能影响AI行为，所以刷新服务
            EnhancedAIService.refreshServiceForFunction(
                context,
                FunctionType.CHAT
            )
        }
    }

    // The passed modifier will align this Box within its parent.
    Box(modifier = modifier) {
        Row(
            // This modifier just adds padding. The Row will wrap its content.
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.Bottom, // Align the icon column to the bottom.
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(visible = enableThinkingMode) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = "思考模式已激活",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = enableThinkingGuidance) {
                    Icon(
                        imageVector = Icons.Rounded.TipsAndUpdates,
                        contentDescription = "思考引导已激活",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = enableAiPlanning) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI计划模式已激活",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = permissionLevel == PermissionLevel.ALLOW) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = "自动批准已激活",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showMenu = !showMenu },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "设置选项",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).scale(iconScale)
                    )
                }
            }
        }

        if (showMenu) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = {
                    showMenu = false
                    showModelDropdown = false // 关闭主菜单时也关闭模型菜单
                    showPromptDropdown = false
                    showMemoryDropdown = false
                },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(modifier = Modifier.padding(top = 0.dp, bottom = 76.dp)) {
                    Card(
                        modifier = Modifier.width(220.dp), // 加宽一级菜单
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 模型选择器
                            ModelSelectorItem(
                                configSummaries = configSummaries,
                                currentConfigId = currentConfigId,
                                onSelectModel = onSelectModel,
                                expanded = showModelDropdown,
                                onExpandedChange = { showModelDropdown = it }
                            )

                            // 提示词选择器
                            PromptSelectorItem(
                                promptProfiles = promptProfiles,
                                currentProfileId = currentPromptProfileId,
                                onSelectPrompt = onSelectPrompt,
                                expanded = showPromptDropdown,
                                onExpandedChange = { showPromptDropdown = it }
                            )

                            // 记忆选择器
                            MemorySelectorItem(
                                preferenceProfiles = preferenceProfiles,
                                currentProfileId = activeProfileId,
                                onSelectMemory = onSelectMemory,
                                expanded = showMemoryDropdown,
                                onExpandedChange = { showMemoryDropdown = it },
                                onInfoClick = {
                                    infoPopupContent = "记忆" to "记忆选择包括了用户偏好和该偏好下的记忆库。如果想要新的记忆库，可以去设置新建一个用户偏好并在这里选择"
                                    showMenu = false
                                }
                            )

                            // 上下文长度设置
                            ContextLengthSettingItem(
                                maxWindowSizeInK = maxWindowSizeInK,
                                onContextLengthChange = onContextLengthChange,
                                onInfoClick = {
                                    infoPopupContent = "上下文长度" to "控制模型记忆的对话长度（单位：千tokens）。较短的长度可以节省Token，但可能会忘记早期对话内容。"
                                    showMenu = false
                                }
                            )

                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                            // AI计划模式
                            SettingItem(
                                title = "AI计划模式",
                                icon = if (enableAiPlanning) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome,
                                iconTint = if (enableAiPlanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                isChecked = enableAiPlanning,
                                onToggle = onToggleAiPlanning,
                                onInfoClick = {
                                    infoPopupContent = "AI计划模式" to "能够生成一系列计划进行执行（效果一般，不建议启用，未来将会进行替换）。"
                                    showMenu = false
                                }
                            )

                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )

                            // 自动批准
                            SettingItem(
                                title = "自动批准",
                                icon = if (permissionLevel == PermissionLevel.ALLOW) Icons.Rounded.Security else Icons.Outlined.Security,
                                iconTint = if (permissionLevel == PermissionLevel.ALLOW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                isChecked = permissionLevel == PermissionLevel.ALLOW,
                                onToggle = onTogglePermission,
                                onInfoClick = {
                                    infoPopupContent = "自动批准" to "将会自动把较为安全的工具直接执行而不询问。对于具体的分组批准，请前往设置-工具权限管理进行操作。"
                                    showMenu = false
                                }
                            )

                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )

                            // 思考模式
                            SettingItem(
                                title = "思考模式",
                                icon = if (enableThinkingMode) Icons.Rounded.Psychology else Icons.Outlined.Psychology,
                                iconTint = if (enableThinkingMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                isChecked = enableThinkingMode,
                                onToggle = onToggleThinkingMode,
                                onInfoClick = {
                                    infoPopupContent = "思考模式" to "目前只支持Qwen3和Claude，能够启用内置的思考。"
                                    showMenu = false
                                }
                            )

                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )

                            // 思考引导
                            SettingItem(
                                title = "思考引导",
                                icon = if (enableThinkingGuidance) Icons.Rounded.TipsAndUpdates else Icons.Outlined.TipsAndUpdates,
                                iconTint = if (enableThinkingGuidance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                isChecked = enableThinkingGuidance,
                                onToggle = onToggleThinkingGuidance,
                                onInfoClick = {
                                    infoPopupContent = "思考引导" to "能够让不能思考的模型强制进行思考。该选项不建议对思考模型开启。"
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 详情说明弹窗
        if (infoPopupContent != null) {
            Popup(
                alignment = Alignment.TopStart, // 将弹窗对齐到父布局的左上角
                onDismissRequest = { infoPopupContent = null },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier.padding(top = 0.dp, bottom = 76.dp, end = 40.dp) // 调整边距，使其显示在左侧
                ) {
                    Card(
                        modifier = Modifier.width(220.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = infoPopupContent!!.first,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Divider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = infoPopupContent!!.second,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )

        // 文本
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )

        // 详情按钮
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }

        // 开关
        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.scale(0.65f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun ContextLengthSettingItem(
    maxWindowSizeInK: Float,
    onContextLengthChange: (Float) -> Unit,
    onInfoClick: () -> Unit
) {
    var sliderValue by remember { mutableStateOf(maxWindowSizeInK) }
    var textValue by remember { mutableStateOf(maxWindowSizeInK.toString()) }
    val focusManager = LocalFocusManager.current
    val df = remember { DecimalFormat("#.#") }

    // 当外部传入的值变化时，同步更新内部状态
    LaunchedEffect(maxWindowSizeInK) {
        sliderValue = maxWindowSizeInK
        textValue = df.format(maxWindowSizeInK)
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = "上下文长度",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = "上下文长度",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    textValue = newText
                    newText.toFloatOrNull()?.let {
                        sliderValue = it.coerceIn(1f, 1024f)
                    }
                },
                modifier = Modifier
                    .width(50.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val finalValue = textValue.toFloatOrNull()?.coerceIn(1f, 1024f) ?: sliderValue
                        onContextLengthChange(finalValue)
                        textValue = df.format(finalValue) // 格式化并同步显示
                        focusManager.clearFocus()
                    }
                ),
                singleLine = true
            )

            Text(
                text = "k",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp, end = 4.dp)
            )


            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                textValue = df.format(it)
            },
            onValueChangeFinished = {
                onContextLengthChange(sliderValue)
            },
            valueRange = 1f..1024f,
            steps = 1022,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }
}


@Composable
private fun MemorySelectorItem(
    preferenceProfiles: List<PreferenceProfile>,
    currentProfileId: String,
    onSelectMemory: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    val currentProfile = preferenceProfiles.find { it.id == currentProfileId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Portrait,
                contentDescription = "记忆选择",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "记忆:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentProfile?.name ?: "未选择",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                preferenceProfiles.forEach { profile ->
                    val isSelected = profile.id == currentProfileId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable {
                                onSelectMemory(profile.id)
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (preferenceProfiles.last() != profile) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSelectorItem(
    configSummaries: List<ModelConfigSummary>,
    currentConfigId: String,
    onSelectModel: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val currentConfig = configSummaries.find { it.id == currentConfigId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DataObject,
                contentDescription = "模型选择",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模型:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentConfig?.name ?: "未选择",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }


            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                configSummaries.forEach { config ->
                    val isSelected = config.id == currentConfigId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable {
                                onSelectModel(config.id)
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = config.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = config.modelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (configSummaries.last() != config) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptSelectorItem(
    promptProfiles: List<PromptProfile>,
    currentProfileId: String,
    onSelectPrompt: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val currentProfile = promptProfiles.find { it.id == currentProfileId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Message,
                contentDescription = "提示词选择",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "提示词:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentProfile?.name ?: "未选择",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }


            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                promptProfiles.forEach { profile ->
                    val isSelected = profile.id == currentProfileId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable {
                                onSelectPrompt(profile.id)
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (promptProfiles.last() != profile) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
} 
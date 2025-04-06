package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

/**
 * 根据用户选择的关键信息生成偏好描述
 */
private fun generatePreferencesDescription(gender: String, occupation: String, age: Int): String {
    val genderDesc = when (gender) {
        "男" -> "一位男性用户"
        "女" -> "一位女性用户"
        else -> "一位用户"
    }
    
    val ageDesc = when {
        age in 1..12 -> "儿童"
        age in 13..17 -> "青少年"
        age in 18..25 -> "年轻人"
        age in 26..40 -> "壮年人士"
        age in 41..60 -> "中年人士"
        age > 60 -> "老年人士"
        else -> ""
    }
    
    val occupationDesc = when (occupation) {
        "学生" -> "学生身份，关注学习和个人发展"
        "上班族" -> "职场人士，注重工作效率和专业发展"
        "自由职业" -> "自由职业者，重视灵活性和创新能力"
        else -> "工作者"
    }
    
    val interestTopics = when (occupation) {
        "学生" -> "学习资源、知识整理、考试准备和个人成长"
        "上班族" -> "时间管理、专业技能、职场沟通和工作效率提升"
        "自由职业" -> "创意设计、项目管理、自我提升和技能拓展"
        else -> "各类实用工具、生活便利和个人提升"
    }
    
    val preferenceStyle = when (gender) {
        "男" -> "直接明了的交流方式，偏好简洁高效的解决方案"
        "女" -> "细致周到的交流方式，注重细节和用户体验"
        else -> "清晰有条理的交流方式，关注实用性和效率"
    }
    
    // 组装完整描述
    val description = buildString {
        append("我是$genderDesc")
        if (ageDesc.isNotEmpty()) append("，$ageDesc")
        append("，$occupationDesc。")
        append("我对$interestTopics 特别感兴趣。")
        append("在沟通中我喜欢$preferenceStyle。")
    }
    
    // 确保不超过100字
    return if (description.length > 100) description.substring(0, 97) + "..." else description
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserPreferencesGuideScreen(
    profileName: String = "",
    profileId: String = "",
    onComplete: () -> Unit,
    navigateToPermissions: () -> Unit = onComplete,
    onBackPressed: () -> Unit = onComplete
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { UserPreferencesManager(context) }
    
    var selectedGender by remember { mutableStateOf("") }
    var selectedOccupation by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var selectedPersonality by remember { mutableStateOf(setOf<String>()) }
    var selectedIdentity by remember { mutableStateOf(setOf<String>()) }
    var selectedAiStyleTags by remember { mutableStateOf(setOf<String>()) }
    
    // 各种选项数据
    val genderOptions = listOf("男", "女", "其他")
    val occupationOptions = listOf(
        "学生", "教师", "医生", "工程师", "设计师", "程序员", 
        "企业主", "销售", "客服", "自由职业", "退休人员", "其他"
    )
    val personalityOptions = listOf(
        "外向", "内向", "敏感", "理性", "感性", "谨慎", 
        "冒险", "耐心", "急躁", "乐观", "悲观", "好奇", 
        "保守", "创新", "细致", "粗放"
    )
    val identityOptions = listOf(
        "学生", "教师", "父母", "音乐爱好者", "艺术爱好者", 
        "游戏玩家", "运动员", "技术宅", "旅行爱好者", 
        "美食家", "创业者", "专业人士"
    )
    val aiStyleOptions = listOf(
        "专业严谨", "活泼幽默", "简洁直接", "耐心细致", 
        "创意思维", "技术导向", "教学指导", "情感支持"
    )
    
    // 从配置文件加载现有数据
    LaunchedEffect(profileId) {
        try {
            // 检查是否存在配置文件列表
            val profiles = preferencesManager.profileListFlow.first()
            
            // 如果配置列表为空，创建默认配置
            if (profiles.isEmpty()) {
                // 创建默认配置并设置为活动配置
                val defaultProfileId = preferencesManager.createProfile("默认配置", isDefault = true)
                preferencesManager.setActiveProfile(defaultProfileId)
                // 给一点时间让数据存储更新
                delay(100)
            }
            
            // 如果提供了profileId，加载特定配置；否则加载活动配置
            val profile = if (profileId.isNotEmpty()) {
                preferencesManager.getUserPreferencesFlow(profileId).first()
            } else {
                preferencesManager.getUserPreferencesFlow().first()
            }
            
            // 填充表单字段
            selectedGender = profile.gender
            selectedOccupation = profile.occupation
            age = if (profile.age > 0) profile.age.toString() else ""
            
            // 解析个性特点
            val personalityTags = profile.personality.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val filteredPersonalityTags = personalityTags.filter { tag -> personalityOptions.contains(tag) }.toSet()
            selectedPersonality = filteredPersonalityTags
            
            // 解析身份认同
            val identityTags = profile.identity.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val filteredIdentityTags = identityTags.filter { tag -> identityOptions.contains(tag) }.toSet()
            selectedIdentity = filteredIdentityTags
            
            // 解析AI风格标签
            val styleTags = profile.aiStyle.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val filteredStyleTags = styleTags.filter { tag -> aiStyleOptions.contains(tag) }.toSet()
            selectedAiStyleTags = filteredStyleTags
            
            // 如果没有已选的标签，默认选择一些并保存到配置中
            var needsUpdate = false
            
            if (selectedAiStyleTags.isEmpty()) {
                selectedAiStyleTags = setOf("专业严谨", "简洁直接")
                needsUpdate = true
            }
            
            if (selectedPersonality.isEmpty()) {
                selectedPersonality = setOf("理性", "耐心")
                needsUpdate = true
            }
            
            // 如果需要更新默认值，保存到配置
            if (needsUpdate) {
                val targetId = if (profileId.isNotEmpty()) profileId else profile.id
                preferencesManager.updateProfileCategory(
                    profileId = targetId,
                    aiStyle = selectedAiStyleTags.joinToString(", "),
                    personality = selectedPersonality.joinToString(", ")
                )
            }
        } catch (e: Exception) {
            // 如果获取配置失败，创建默认配置
            try {
                // 创建默认配置
                val defaultProfileId = preferencesManager.createProfile("默认配置", isDefault = true)
                
                // 设置默认值
                selectedAiStyleTags = setOf("专业严谨", "简洁直接")
                selectedPersonality = setOf("理性", "耐心")
                
                // 保存默认值到配置
                preferencesManager.updateProfileCategory(
                    profileId = defaultProfileId,
                    aiStyle = selectedAiStyleTags.joinToString(", "),
                    personality = selectedPersonality.joinToString(", ")
                )
            } catch (ex: Exception) {
                // 如果还是失败，至少确保UI有默认值显示
                selectedAiStyleTags = setOf("专业严谨", "简洁直接")
                selectedPersonality = setOf("理性", "耐心")
            }
        }
    }
    
    Scaffold(
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (profileName.isNotEmpty()) "配置「$profileName」" else stringResource(id = R.string.preferences_guide_title),
                style = MaterialTheme.typography.titleMedium
            )
            
            // 性别选择（标签选择）
            Text(stringResource(id = R.string.gender), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genderOptions.forEach { option ->
                    FilterChip(
                        selected = selectedGender == option,
                        onClick = { selectedGender = option },
                        label = { Text(option) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            // 职业选择（标签选择）
            Text(stringResource(id = R.string.occupation), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                occupationOptions.forEach { option ->
                    FilterChip(
                        selected = selectedOccupation == option,
                        onClick = { selectedOccupation = option },
                        label = { Text(option) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            // 年龄输入
            Text(stringResource(id = R.string.age), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = age,
                onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) age = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(id = R.string.enter_age)) },
                singleLine = true
            )
            
            // 性格特点选择（多选标签）
            Text("性格特点 (可多选)", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                personalityOptions.forEach { option ->
                    val isSelected = selectedPersonality.contains(option)
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            if (isSelected) {
                                selectedPersonality = selectedPersonality - option
                            } else {
                                selectedPersonality = selectedPersonality + option
                            }
                        },
                        label = { Text(option) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            // 身份认同选择（多选标签）
            Text("身份认同 (可多选)", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                identityOptions.forEach { option ->
                    val isSelected = selectedIdentity.contains(option)
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            if (isSelected) {
                                selectedIdentity = selectedIdentity - option
                            } else {
                                selectedIdentity = selectedIdentity + option
                            }
                        },
                        label = { Text(option) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            // AI风格标签选择
            Text("期待的AI风格 (可多选)", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                aiStyleOptions.forEach { option ->
                    val isSelected = selectedAiStyleTags.contains(option)
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            if (isSelected) {
                                selectedAiStyleTags = selectedAiStyleTags - option
                            } else {
                                selectedAiStyleTags = selectedAiStyleTags + option
                            }
                        },
                        label = { Text(option) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 完成按钮
            Button(
                onClick = {
                    scope.launch {
                        // 提取年龄
                        val ageInt = age.toIntOrNull() ?: 0
                        
                        // 将选中的标签合并为字符串
                        val personalityTags = selectedPersonality.joinToString(", ")
                        val identityTags = selectedIdentity.joinToString(", ")
                        val aiStyleTags = selectedAiStyleTags.joinToString(", ")
                        
                        // 更新配置信息
                        if (profileId.isNotEmpty()) {
                            // 如果提供了profileId，更新指定的配置文件
                            preferencesManager.updateProfileCategory(
                                profileId = profileId,
                                age = ageInt,
                                gender = selectedGender,
                                occupation = selectedOccupation,
                                personality = personalityTags,
                                identity = identityTags,
                                aiStyle = aiStyleTags
                            )
                        } else {
                            // 否则更新当前活动的配置文件
                            preferencesManager.updateProfileCategory(
                                age = ageInt,
                                gender = selectedGender,
                                occupation = selectedOccupation,
                                personality = personalityTags,
                                identity = identityTags,
                                aiStyle = aiStyleTags
                            )
                        }
                        
                        // 根据流程选择不同的导航目标
                        if (profileName.isNotEmpty()) {
                            // 如果是从配置页来的，返回到配置页
                            onComplete()
                        } else {
                            // 如果是首次启动应用，进入权限页
                            navigateToPermissions()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedGender.isNotEmpty() && selectedOccupation.isNotEmpty() && age.isNotEmpty()
            ) {
                Text(stringResource(id = R.string.complete))
            }
        }
    }
} 
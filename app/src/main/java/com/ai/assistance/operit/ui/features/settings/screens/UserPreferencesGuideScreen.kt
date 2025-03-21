package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.UserPreferencesManager
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPreferencesGuideScreen(
    onComplete: () -> Unit,
    navigateToPermissions: () -> Unit = onComplete
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { UserPreferencesManager(context) }
    
    var gender by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    
    val genderOptions = listOf("男", "女", "其他")
    val occupationOptions = listOf("学生", "上班族", "自由职业", "其他")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.user_preferences_guide)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.preferences_guide_title),
                style = MaterialTheme.typography.titleMedium
            )
            
            // 性别选择
            Text(stringResource(id = R.string.gender), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genderOptions.forEach { option ->
                    FilterChip(
                        selected = gender == option,
                        onClick = { gender = option },
                        label = { Text(option) }
                    )
                }
            }
            
            // 从业方向选择
            Text(stringResource(id = R.string.occupation), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                occupationOptions.forEach { option ->
                    FilterChip(
                        selected = occupation == option,
                        onClick = { occupation = option },
                        label = { Text(option) }
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
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 完成按钮
            Button(
                onClick = {
                    scope.launch {
                        // 生成偏好描述
                        val ageInt = age.toIntOrNull() ?: 0
                        val generatedDescription = generatePreferencesDescription(gender, occupation, ageInt)
                        
                        // 同时保存描述和原始数据
                        preferencesManager.updatePreferences(
                            preferencesText = generatedDescription,
                            gender = gender,
                            occupation = occupation,
                            age = ageInt,
                            isInitialized = true
                        )
                        navigateToPermissions()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = gender.isNotEmpty() && occupation.isNotEmpty() && age.isNotEmpty()
            ) {
                Text(stringResource(id = R.string.complete))
            }
        }
    }
} 
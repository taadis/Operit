package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelParametersSettingsScreen() {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val scope = rememberCoroutineScope()
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // 从 API 偏好中读取模型参数
    val maxTokens =
            apiPreferences.maxTokensFlow.collectAsState(initial = ApiPreferences.DEFAULT_MAX_TOKENS)
                    .value
    val temperature =
            apiPreferences.temperatureFlow.collectAsState(
                            initial = ApiPreferences.DEFAULT_TEMPERATURE
                    )
                    .value
    val topP = apiPreferences.topPFlow.collectAsState(initial = ApiPreferences.DEFAULT_TOP_P).value
    val topK = apiPreferences.topKFlow.collectAsState(initial = ApiPreferences.DEFAULT_TOP_K).value
    val presencePenalty =
            apiPreferences.presencePenaltyFlow.collectAsState(
                            initial = ApiPreferences.DEFAULT_PRESENCE_PENALTY
                    )
                    .value
    val frequencyPenalty =
            apiPreferences.frequencyPenaltyFlow.collectAsState(
                            initial = ApiPreferences.DEFAULT_FREQUENCY_PENALTY
                    )
                    .value
    val repetitionPenalty =
            apiPreferences.repetitionPenaltyFlow.collectAsState(
                            initial = ApiPreferences.DEFAULT_REPETITION_PENALTY
                    )
                    .value

    // 可变状态用于编辑
    var maxTokensInput by remember { mutableStateOf(maxTokens.toString()) }
    var temperatureInput by remember { mutableStateOf(temperature.toString()) }
    var topPInput by remember { mutableStateOf(topP.toString()) }
    var topKInput by remember { mutableStateOf(topK.toString()) }
    var presencePenaltyInput by remember { mutableStateOf(presencePenalty.toString()) }
    var frequencyPenaltyInput by remember { mutableStateOf(frequencyPenalty.toString()) }
    var repetitionPenaltyInput by remember { mutableStateOf(repetitionPenalty.toString()) }

    // 验证状态
    var showMaxTokensError by remember { mutableStateOf(false) }
    var showTemperatureError by remember { mutableStateOf(false) }
    var showTopPError by remember { mutableStateOf(false) }
    var showTopKError by remember { mutableStateOf(false) }
    var showPresencePenaltyError by remember { mutableStateOf(false) }
    var showFrequencyPenaltyError by remember { mutableStateOf(false) }
    var showRepetitionPenaltyError by remember { mutableStateOf(false) }

    // 当偏好改变时更新状态
    LaunchedEffect(
            maxTokens,
            temperature,
            topP,
            topK,
            presencePenalty,
            frequencyPenalty,
            repetitionPenalty
    ) {
        maxTokensInput = maxTokens.toString()
        temperatureInput = temperature.toString()
        topPInput = topP.toString()
        topKInput = topK.toString()
        presencePenaltyInput = presencePenalty.toString()
        frequencyPenaltyInput = frequencyPenalty.toString()
        repetitionPenaltyInput = repetitionPenalty.toString()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        // 顶部说明卡片
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        MaterialTheme.colorScheme.primaryContainer.copy(
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
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = "DeepSeek 模型参数",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                }

                Text(
                        text = "这些参数控制 DeepSeek 大模型的输出行为。调整这些参数可以影响AI回复的创造性、多样性和准确性。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // 参数卡片
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 最大 Token 数量参数
                ParameterSection(
                        icon = Icons.Default.TextFormat,
                        title = "最大生成Token数",
                        description = "控制AI每次最多生成的Token数量（默认: 4096）",
                        value = maxTokensInput,
                        onValueChange = {
                            maxTokensInput = it
                            showMaxTokensError =
                                    try {
                                        it.toInt() <= 0
                                    } catch (e: NumberFormatException) {
                                        true
                                    }
                        },
                        isError = showMaxTokensError,
                        errorMessage = "请输入有效的正整数",
                        keyboardType = KeyboardType.Number
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // 温度参数
                ParameterSection(
                        icon = Icons.Default.Thermostat,
                        title = "温度",
                        description = "控制输出的随机性（0.0-2.0）。较低的值更确定性，较高的值更随机（默认: 1.0）",
                        value = temperatureInput,
                        onValueChange = {
                            temperatureInput = it
                            showTemperatureError =
                                    try {
                                        val temp = it.toFloat()
                                        temp < 0.0f || temp > 2.0f
                                    } catch (e: NumberFormatException) {
                                        true
                                    }
                        },
                        isError = showTemperatureError,
                        errorMessage = "请输入0.0到2.0之间的浮点数",
                        keyboardType = KeyboardType.Decimal
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Top-P 参数
                ParameterSection(
                        icon = Icons.Default.FilterAlt,
                        title = "Top-P 采样",
                        description = "作为温度的替代方案，模型仅考虑概率最高的Top-P比例的token（默认: 1.0）",
                        value = topPInput,
                        onValueChange = {
                            topPInput = it
                            showTopPError =
                                    try {
                                        val topP = it.toFloat()
                                        topP < 0.0f || topP > 1.0f
                                    } catch (e: NumberFormatException) {
                                        true
                                    }
                        },
                        isError = showTopPError,
                        errorMessage = "请输入0.0到1.0之间的浮点数",
                        keyboardType = KeyboardType.Decimal
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Top-K 参数
                ParameterSection(
                        icon = Icons.Default.FilterList,
                        title = "Top-K 采样",
                        description = "模型仅考虑概率最高的K个token。0表示禁用（默认: 0）",
                        value = topKInput,
                        onValueChange = {
                            topKInput = it
                            showTopKError =
                                    try {
                                        it.toInt() < 0
                                    } catch (e: NumberFormatException) {
                                        true
                                    }
                        },
                        isError = showTopKError,
                        errorMessage = "请输入有效的非负整数",
                        keyboardType = KeyboardType.Number
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // 频率惩罚参数
                ParameterSection(
                        icon = Icons.Default.StackedBarChart,
                        title = "频率惩罚",
                        description = "减少模型重复同一词语的可能性。值越高，惩罚越大（默认: 0.0）",
                        value = frequencyPenaltyInput,
                        onValueChange = {
                            frequencyPenaltyInput = it
                            showFrequencyPenaltyError =
                                    try {
                                        val penalty = it.toFloat()
                                        penalty < -2.0f || penalty > 2.0f
                                    } catch (e: NumberFormatException) {
                                        true
                                    }
                        },
                        isError = showFrequencyPenaltyError,
                        errorMessage = "请输入-2.0到2.0之间的浮点数",
                        keyboardType = KeyboardType.Decimal
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // 存在惩罚参数
                ParameterSection(
                        icon = Icons.Default.Repeat,
                        title = "存在惩罚",
                        description = "增强模型谈论新主题的倾向。值越高，惩罚越大（默认: 0.0）",
                        value = presencePenaltyInput,
                        onValueChange = {
                            presencePenaltyInput = it
                            showPresencePenaltyError =
                                    try {
                                        val penalty = it.toFloat()
                                        penalty < -2.0f || penalty > 2.0f
                                    } catch (e: NumberFormatException) {
                                        true
                                    }
                        },
                        isError = showPresencePenaltyError,
                        errorMessage = "请输入-2.0到2.0之间的浮点数",
                        keyboardType = KeyboardType.Decimal
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // 重复惩罚参数
                ParameterSection(
                        icon = Icons.Default.FilterNone,
                        title = "重复惩罚",
                        description = "进一步减少重复。1.0表示不惩罚，大于1.0会降低重复可能性（默认: 1.0）",
                        value = repetitionPenaltyInput,
                        onValueChange = {
                            repetitionPenaltyInput = it
                            showRepetitionPenaltyError =
                                    try {
                                        val penalty = it.toFloat()
                                        penalty < 0.0f
                                    } catch (e: NumberFormatException) {
                                        true
                                    }
                        },
                        isError = showRepetitionPenaltyError,
                        errorMessage = "请输入大于0.0的浮点数",
                        keyboardType = KeyboardType.Decimal
                )
            }
        }

        // 温度推荐卡片
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
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
                            text = "温度推荐设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                }

                Text(
                        text = "基于DeepSeek文档的温度参数推荐设置：",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    TemperatureRecommendationRow("编程/数学", "0.0")
                    TemperatureRecommendationRow("数据清洗/分析", "1.0")
                    TemperatureRecommendationRow("一般对话", "1.3")
                    TemperatureRecommendationRow("翻译", "1.3")
                    TemperatureRecommendationRow("创意写作/诗歌", "1.5")
                }
            }
        }

        // 保存按钮
        Button(
                onClick = {
                    scope.launch {
                        try {
                            val newMaxTokens = maxTokensInput.toInt()
                            val newTemperature = temperatureInput.toFloat()
                            val newTopP = topPInput.toFloat()
                            val newTopK = topKInput.toInt()
                            val newPresencePenalty = presencePenaltyInput.toFloat()
                            val newFrequencyPenalty = frequencyPenaltyInput.toFloat()
                            val newRepetitionPenalty = repetitionPenaltyInput.toFloat()

                            // 验证所有参数
                            if (newMaxTokens <= 0) {
                                showMaxTokensError = true
                                return@launch
                            }

                            if (newTemperature < 0.0f || newTemperature > 2.0f) {
                                showTemperatureError = true
                                return@launch
                            }

                            if (newTopP < 0.0f || newTopP > 1.0f) {
                                showTopPError = true
                                return@launch
                            }

                            if (newTopK < 0) {
                                showTopKError = true
                                return@launch
                            }

                            if (newPresencePenalty < -2.0f || newPresencePenalty > 2.0f) {
                                showPresencePenaltyError = true
                                return@launch
                            }

                            if (newFrequencyPenalty < -2.0f || newFrequencyPenalty > 2.0f) {
                                showFrequencyPenaltyError = true
                                return@launch
                            }

                            if (newRepetitionPenalty < 0.0f) {
                                showRepetitionPenaltyError = true
                                return@launch
                            }

                            // 保存模型参数
                            apiPreferences.saveModelParameters(
                                    maxTokens = newMaxTokens,
                                    temperature = newTemperature,
                                    topP = newTopP,
                                    topK = newTopK,
                                    presencePenalty = newPresencePenalty,
                                    frequencyPenalty = newFrequencyPenalty,
                                    repetitionPenalty = newRepetitionPenalty
                            )
                            showSaveSuccessMessage = true
                        } catch (e: NumberFormatException) {
                            // 处理无效输入
                            if (e.message?.contains("Int") == true) {
                                // 整数解析错误
                                if (!showMaxTokensError && maxTokensInput.toIntOrNull() == null) {
                                    showMaxTokensError = true
                                }
                                if (!showTopKError && topKInput.toIntOrNull() == null) {
                                    showTopKError = true
                                }
                            } else {
                                // 浮点数解析错误
                                if (!showTemperatureError &&
                                                temperatureInput.toFloatOrNull() == null
                                ) {
                                    showTemperatureError = true
                                }
                                if (!showTopPError && topPInput.toFloatOrNull() == null) {
                                    showTopPError = true
                                }
                                if (!showPresencePenaltyError &&
                                                presencePenaltyInput.toFloatOrNull() == null
                                ) {
                                    showPresencePenaltyError = true
                                }
                                if (!showFrequencyPenaltyError &&
                                                frequencyPenaltyInput.toFloatOrNull() == null
                                ) {
                                    showFrequencyPenaltyError = true
                                }
                                if (!showRepetitionPenaltyError &&
                                                repetitionPenaltyInput.toFloatOrNull() == null
                                ) {
                                    showRepetitionPenaltyError = true
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                enabled =
                        !showMaxTokensError &&
                                !showTemperatureError &&
                                !showTopPError &&
                                !showTopKError &&
                                !showPresencePenaltyError &&
                                !showFrequencyPenaltyError &&
                                !showRepetitionPenaltyError
        ) {
            Text(
                    "保存设置",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // 保存成功提示
        if (showSaveSuccessMessage) {
            Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                            CardDefaults.cardColors(
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
                            text = "设置已保存",
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

        // 自定义提示词卡片
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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

                // 获取当前的系统提示词设置
                val defaultIntroPrompt = "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。你有各种工具可以调用，以高效完成复杂的请求。"
                val defaultTonePrompt = "保持有帮助的语气，并清楚地传达限制。使用问题库根据用户的风格、偏好和过去的信息个性化响应。"

                // 从 API 偏好中读取自定义提示词
                val customIntroPrompt =
                        apiPreferences.customIntroPromptFlow.collectAsState(
                                        initial = defaultIntroPrompt
                                )
                                .value
                val customTonePrompt =
                        apiPreferences.customTonePromptFlow.collectAsState(
                                        initial = defaultTonePrompt
                                )
                                .value

                // 可变状态用于编辑
                var introPromptInput by remember { mutableStateOf(customIntroPrompt) }
                var tonePromptInput by remember { mutableStateOf(customTonePrompt) }

                // 当偏好改变时更新状态
                LaunchedEffect(customIntroPrompt, customTonePrompt) {
                    introPromptInput = customIntroPrompt
                    tonePromptInput = customTonePrompt
                }

                // 自我介绍提示词
                Text(
                        text = "AI自我介绍",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                        value = introPromptInput,
                        onValueChange = { introPromptInput = it },
                        label = { Text("自我介绍提示词") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        placeholder = { Text(defaultIntroPrompt) },
                        minLines = 2
                )

                // 语气风格提示词
                Text(
                        text = "AI语气风格",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                )

                OutlinedTextField(
                        value = tonePromptInput,
                        onValueChange = { tonePromptInput = it },
                        label = { Text("语气风格提示词") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        placeholder = { Text(defaultTonePrompt) },
                        minLines = 2
                )

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
                                    apiPreferences.saveCustomPrompts(
                                            introPrompt = introPromptInput,
                                            tonePrompt = tonePromptInput
                                    )
                                    showSaveSuccessMessage = true
                                }
                            }
                    ) { Text("保存提示词") }
                }
            }
        }

        // 重置为默认值按钮
        OutlinedButton(
                onClick = {
                    scope.launch {
                        maxTokensInput = ApiPreferences.DEFAULT_MAX_TOKENS.toString()
                        temperatureInput = ApiPreferences.DEFAULT_TEMPERATURE.toString()
                        topPInput = ApiPreferences.DEFAULT_TOP_P.toString()
                        topKInput = ApiPreferences.DEFAULT_TOP_K.toString()
                        presencePenaltyInput = ApiPreferences.DEFAULT_PRESENCE_PENALTY.toString()
                        frequencyPenaltyInput = ApiPreferences.DEFAULT_FREQUENCY_PENALTY.toString()
                        repetitionPenaltyInput =
                                ApiPreferences.DEFAULT_REPETITION_PENALTY.toString()

                        showMaxTokensError = false
                        showTemperatureError = false
                        showTopPError = false
                        showTopKError = false
                        showPresencePenaltyError = false
                        showFrequencyPenaltyError = false
                        showRepetitionPenaltyError = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                    "重置为默认值",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ParameterSection(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        description: String,
        value: String,
        onValueChange: (String) -> Unit,
        isError: Boolean,
        errorMessage: String,
        keyboardType: KeyboardType
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )
        }

        Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(title) },
                isError = isError,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
        )

        if (isError) {
            Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
            )
        }
    }
}

@Composable
private fun TemperatureRecommendationRow(useCase: String, tempValue: String) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = useCase,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
        )

        Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Text(
                    text = tempValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

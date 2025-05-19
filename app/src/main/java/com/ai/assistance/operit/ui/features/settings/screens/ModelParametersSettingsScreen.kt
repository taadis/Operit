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
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelParametersSettingsScreen() {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val scope = rememberCoroutineScope()
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // Read model parameters from API preferences
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

    // Mutable state for editing
    var maxTokensInput by remember { mutableStateOf(maxTokens.toString()) }
    var temperatureInput by remember { mutableStateOf(temperature.toString()) }
    var topPInput by remember { mutableStateOf(topP.toString()) }
    var topKInput by remember { mutableStateOf(topK.toString()) }
    var presencePenaltyInput by remember { mutableStateOf(presencePenalty.toString()) }
    var frequencyPenaltyInput by remember { mutableStateOf(frequencyPenalty.toString()) }
    var repetitionPenaltyInput by remember { mutableStateOf(repetitionPenalty.toString()) }

    // Validation state
    var showMaxTokensError by remember { mutableStateOf(false) }
    var showTemperatureError by remember { mutableStateOf(false) }
    var showTopPError by remember { mutableStateOf(false) }
    var showTopKError by remember { mutableStateOf(false) }
    var showPresencePenaltyError by remember { mutableStateOf(false) }
    var showFrequencyPenaltyError by remember { mutableStateOf(false) }
    var showRepetitionPenaltyError by remember { mutableStateOf(false) }

    // Update state when preferences change
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
        // Screen title  

        // ======= SECTION 1: GENERATION PARAMETERS =======
        ModelParamSectionTitle(title = "生成参数", icon = Icons.Default.TextFormat)
        
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Maximum tokens parameter
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
            }
        }
        
        // ======= SECTION 2: CREATIVITY PARAMETERS =======
        ModelParamSectionTitle(title = "创造性参数", icon = Icons.Default.AutoAwesome)
        
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Temperature parameter
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

                // Top-P parameter
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

                // Top-K parameter
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
            }
        }
        
        // ======= SECTION 3: REPETITION CONTROL =======
        ModelParamSectionTitle(title = "重复控制参数", icon = Icons.Default.Repeat)
        
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Frequency penalty parameter
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

                // Presence penalty parameter
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

                // Repetition penalty parameter
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

        // Temperature recommendation card
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

        // Save button for all parameters
        Button(
                onClick = {
                    scope.launch {
                        try {
                            // Parse and validate all parameters
                            val newMaxTokens = maxTokensInput.toInt()
                            val newTemperature = temperatureInput.toFloat()
                            val newTopP = topPInput.toFloat()
                            val newTopK = topKInput.toInt()
                            val newPresencePenalty = presencePenaltyInput.toFloat()
                            val newFrequencyPenalty = frequencyPenaltyInput.toFloat()
                            val newRepetitionPenalty = repetitionPenaltyInput.toFloat()

                            // Validate all parameters
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

                            // Save model parameters
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
                            // Handle invalid input
                            if (e.message?.contains("Int") == true) {
                                // Integer parsing error
                                if (!showMaxTokensError && maxTokensInput.toIntOrNull() == null) {
                                    showMaxTokensError = true
                                }
                                if (!showTopKError && topKInput.toIntOrNull() == null) {
                                    showTopKError = true
                                }
                            } else {
                                // Float parsing error
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
                    "保存所有设置",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Reset to defaults button
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

        // Save success message
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ModelParamSectionTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Divider(modifier = Modifier.padding(bottom = 8.dp))
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

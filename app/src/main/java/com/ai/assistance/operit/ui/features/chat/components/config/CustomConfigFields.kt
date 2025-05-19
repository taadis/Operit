package com.ai.assistance.operit.ui.features.chat.components.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Component displaying custom configuration fields for API endpoint and model name. These fields
 * are shown/hidden based on the showCustomFields parameter.
 *
 * @param apiEndpoint Current API endpoint value
 * @param modelName Current model name value
 * @param onApiEndpointChange Callback when API endpoint changes
 * @param onModelNameChange Callback when model name changes
 * @param showCustomFields Whether to show the custom fields
 * @param isUsingDefaultApiKey Whether using default API key, which restricts model name
 */
@Composable
fun CustomConfigFields(
        apiEndpoint: String,
        modelName: String,
        onApiEndpointChange: (String) -> Unit,
        onModelNameChange: (String) -> Unit,
        showCustomFields: Boolean,
        isUsingDefaultApiKey: Boolean
) {
    val modernTextStyle = TextStyle(fontSize = 14.sp)

    AnimatedVisibility(
            visible = showCustomFields,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            // API Endpoint input
            OutlinedTextField(
                    value = apiEndpoint,
                    onValueChange = { onApiEndpointChange(it) },
                    label = { Text("API接口地址", fontSize = 13.sp) },
                    placeholder = { Text("API地址应包含补全路径", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = "API接口地址",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = modernTextStyle,
                    shape = RoundedCornerShape(12.dp),
                    colors =
                            OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    cursorColor = MaterialTheme.colorScheme.primary
                            ),
                    singleLine = true,
                    maxLines = 1
            )

            // Model Name input
            OutlinedTextField(
                    value = modelName,
                    onValueChange = { onModelNameChange(it) },
                    label = { Text("模型名称", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                                imageVector = Icons.Filled.SmartToy,
                                contentDescription = "模型名称",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = modernTextStyle,
                    shape = RoundedCornerShape(12.dp),
                    colors =
                            OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    disabledTextColor =
                                            if (isUsingDefaultApiKey)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.38f
                                                    ),
                                    disabledBorderColor =
                                            if (isUsingDefaultApiKey)
                                                    MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.3f
                                                    )
                                            else
                                                    MaterialTheme.colorScheme.outline.copy(
                                                            alpha = 0.12f
                                                    ),
                                    disabledLabelColor =
                                            if (isUsingDefaultApiKey)
                                                    MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.7f
                                                    )
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.38f
                                                    )
                            ),
                    singleLine = true,
                    maxLines = 1,
                    enabled = !isUsingDefaultApiKey
            )

            // Show restriction message if using default API key
            if (isUsingDefaultApiKey) {
                Text(
                        text = "使用默认配置时，只能使用deepseek-chat模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                        fontSize = 11.sp
                )
            }
        }
    }
}

package com.ai.assistance.operit.ui.features.chat.components.config

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable for the token input field on the configuration screen.
 *
 * @param apiKey Current API key value
 * @param onApiKeyChange Callback when API key changes
 */
@Composable
fun TokenInputField(apiKey: String, onApiKeyChange: (String) -> Unit) {
    val modernTextStyle = TextStyle(fontSize = 14.sp)

    OutlinedTextField(
            value = apiKey,
            onValueChange = { onApiKeyChange(it) },
            label = { Text("API密钥", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "API密钥图标",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp)
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
}

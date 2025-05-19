package com.ai.assistance.operit.ui.features.chat.components.config

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog that explains the token usage policy before redirecting to the token configuration screen.
 *
 * @param onDismiss Callback when the dialog is dismissed
 * @param onConfirm Callback when user confirms and wants to navigate to token page
 */
@Composable
fun TokenInfoDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        text = "获取 DeepSeek API Token",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                        text =
                                "你将会前往DeepSeek API官网。我们内置DeepSeek官网访问，是因为我们认为其为国内使用性价比最高的API，不仅性能强悍，而且还有各种优惠和磁盘缓存降低使用成本。\n\nAPI的访问和Token的设置与软件开发者无关，我们不会提供这方面的任何服务，也不会赚取任何费用。\n\n第一次使用自己的token建议只充1元，完全够用。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }

                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("前往获取") }
                }
            }
        }
    }
}

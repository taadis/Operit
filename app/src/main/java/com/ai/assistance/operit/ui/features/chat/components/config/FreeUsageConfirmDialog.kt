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
 * Dialog that confirms free API usage and shows cute messages to users
 *
 * @param onDismiss Callback when the user cancels
 * @param onConfirm Callback when the user confirms usage
 * @param remainingUsages The number of remaining usages for today
 * @param maxDailyUsage The maximum allowed usages per day
 */
@Composable
fun FreeUsageConfirmDialog(
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
        remainingUsages: Int,
        maxDailyUsage: Int
) {
    // Generate a random cute message
    val cuteMessages =
            listOf(
                    "少薅点，再薅真破产了qwq",
                    "别老薅作者的哇，作者也不容易呜呜",
                    "作者的资源真的不多了，求放过>_<",
                    "薅一次少一次，请珍惜每一次使用机会~",
                    "作者的钱包在哭泣，请温柔一点qaq"
            )
    val randomMessage = cuteMessages.random()

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
                // Title
                Text(
                        text = "确认使用免费资源",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Cute message
                Text(
                        text = randomMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Usage counter
                val usageText = "今日免费次数：已用${maxDailyUsage - remainingUsages}次，剩余${remainingUsages}次"
                Text(
                        text = usageText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }

                    Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            enabled = remainingUsages > 0
                    ) { Text(if (remainingUsages > 0) "确认使用" else "今日已用完") }
                }

                // Show tip if no usages left
                if (remainingUsages <= 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                            text = "明天再来吧，或者使用您自己的API密钥",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                    )
                }
            }
        }
    }
}

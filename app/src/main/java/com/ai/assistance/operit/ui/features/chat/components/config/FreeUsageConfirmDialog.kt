package com.ai.assistance.operit.ui.features.chat.components.config

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

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
        // Generate a random cute message from resources
        val context = LocalContext.current
        val cuteMessageIds =
                listOf(
                        R.string.free_usage_message_1,
                        R.string.free_usage_message_2,
                        R.string.free_usage_message_3,
                        R.string.free_usage_message_4,
                        R.string.free_usage_message_5
                )
        val randomMessageId = cuteMessageIds.random()

        Dialog(
                onDismissRequest = onDismiss,
                properties =
                        DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
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
                                        text = stringResource(id = R.string.free_usage_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Cute message
                                Text(
                                        text = stringResource(id = randomMessageId),
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Usage counter
                                val usedCount = maxDailyUsage - remainingUsages
                                Text(
                                        text =
                                                stringResource(
                                                        id = R.string.free_usage_counter,
                                                        usedCount,
                                                        remainingUsages
                                                ),
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
                                        TextButton(
                                                onClick = onDismiss,
                                                modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(id = R.string.free_usage_cancel)) }

                                        Button(
                                                onClick = onConfirm,
                                                modifier = Modifier.weight(1f),
                                                enabled = remainingUsages > 0
                                        ) {
                                                Text(
                                                        if (remainingUsages > 0)
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .free_usage_confirm
                                                                )
                                                        else
                                                                stringResource(
                                                                        id =
                                                                                R.string
                                                                                        .free_usage_depleted
                                                                )
                                                )
                                        }
                                }

                                // Show tip if no usages left
                                if (remainingUsages <= 0) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                                text = stringResource(id = R.string.free_usage_tip),
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

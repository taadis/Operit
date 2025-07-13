package com.ai.assistance.operit.ui.features.chat.components.config

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dialog that confirms free API usage and shows messages to users.
 *
 * @param onDismiss Callback when the user cancels.
 * @param onConfirm Callback when the user confirms usage.
 * @param canUseToday Whether the user is eligible to use the free tier today.
 * @param nextAvailableDate The next available date if today is not available.
 * @param waitDays The number of days to wait.
 */
@Composable
fun FreeUsageConfirmDialog(
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
        canUseToday: Boolean,
        nextAvailableDate: LocalDate? = null,
        waitDays: Int = 0
) {
    // Generate a random cute message from resources
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
                        text = stringResource(id = R.string.free_usage_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 根据是否可以使用显示不同的信息
                if (canUseToday) {
                    // Cute message
                    Text(
                            text = stringResource(id = randomMessageId),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 可爱提示
                    Text(
                            text = stringResource(id = R.string.free_usage_interval_tip),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 12.sp
                    )
                } else {
                    // 根据等待天数和可用性显示不同的信息
                    if (waitDays > 0) {
                        // 场景：需要等待
                        val formattedDate = nextAvailableDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        Text(
                                text = stringResource(id = R.string.free_usage_wait_days, waitDays),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (formattedDate != null) {
                            Text(
                                    text = stringResource(id = R.string.free_usage_next_available, formattedDate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = if (waitDays == 1) stringResource(id = R.string.free_usage_first_tip)
                                else stringResource(id = R.string.free_usage_later_tip, waitDays),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 12.sp
                        )
                    } else {
                        // 场景：今日额度用完(理论上在简化逻辑后此场景较少出现，但作为兜底)
                        Text(
                                text = stringResource(id = R.string.free_usage_used_today),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                        )
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

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (canUseToday) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.free_usage_cancel))
                        }
                        Button(
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.free_usage_confirm))
                        }
                    } else {
                        Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(id = android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

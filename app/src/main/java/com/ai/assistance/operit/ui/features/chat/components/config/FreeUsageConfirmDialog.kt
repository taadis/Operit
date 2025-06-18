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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dialog that confirms free API usage and shows cute messages to users
 *
 * @param onDismiss Callback when the user cancels
 * @param onConfirm Callback when the user confirms usage
 * @param remainingUsages The number of remaining usages for today
 * @param maxDailyUsage The maximum allowed usages per day
 * @param nextAvailableDate 下次可用的日期（如果今天不可用）
 * @param waitDays 需要等待的天数
 */
@Composable
fun FreeUsageConfirmDialog(
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
        remainingUsages: Int,
        maxDailyUsage: Int,
        nextAvailableDate: LocalDate? = null,
        waitDays: Int = 0
) {
    // 获取Context
    val context = LocalContext.current

    // 确定是否可以今天使用
    val canUseToday =
            remainingUsages > 0 &&
                    (nextAvailableDate == null || !LocalDate.now().isBefore(nextAvailableDate))

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

                // Cute message
                Text(
                        text = stringResource(id = randomMessageId),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 根据是否可以使用显示不同的信息
                if (canUseToday) {
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // 可爱提示
                    Text(
                            text = stringResource(id = R.string.free_usage_interval_tip),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 12.sp
                    )
                } else {
                    // 显示等待信息
                    nextAvailableDate?.let {
                        val formattedDate = it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                        Text(
                                text =
                                        if (waitDays > 0)
                                                stringResource(
                                                        id = R.string.free_usage_wait_days,
                                                        waitDays
                                                )
                                        else stringResource(id = R.string.free_usage_used_today),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text =
                                        stringResource(
                                                id = R.string.free_usage_next_available,
                                                formattedDate
                                        ),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 根据等待天数显示不同的提示
                        Text(
                                text =
                                        if (waitDays == 1)
                                                stringResource(id = R.string.free_usage_first_tip)
                                        else
                                                stringResource(
                                                        id = R.string.free_usage_later_tip,
                                                        waitDays
                                                ),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(id = R.string.free_usage_cancel))
                    }

                    Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            enabled = canUseToday
                    ) {
                        Text(
                                if (canUseToday) stringResource(id = R.string.free_usage_confirm)
                                else stringResource(id = R.string.free_usage_depleted)
                        )
                    }
                }

                // Show tip if no usages left
                if (!canUseToday) {
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

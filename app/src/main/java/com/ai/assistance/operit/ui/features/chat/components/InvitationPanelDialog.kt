package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun InvitationPanelDialog(
    invitationCount: Int,
    invitationMessage: String,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    onVerifyCode: (String) -> Unit
) {
    var confirmationCode by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("邀请好友", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "当前已成功邀请 $invitationCount 人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "目标: 1人解锁工作区, 2人解锁悬浮窗/语音",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Step 1: Share invitation
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                    Text("第一步：分享你的邀请语", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "让朋友完成三步：1.下载安装软件；2.复制邀请消息；3.打开软件，把弹出的返回码发给你。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(invitationMessage, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onShare(invitationMessage) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("分享邀请语")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: Verify confirmation code
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                    Text("第二步：输入朋友的返回码", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmationCode,
                        onValueChange = { confirmationCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("在此处粘贴返回码") },
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onVerifyCode(confirmationCode)
                            // Optionally clear code after submission
                            confirmationCode = ""
                        },
                        enabled = confirmationCode.isNotBlank()
                    ) {
                        Text("确认邀请")
                    }
                }
            }
        }
    }
} 
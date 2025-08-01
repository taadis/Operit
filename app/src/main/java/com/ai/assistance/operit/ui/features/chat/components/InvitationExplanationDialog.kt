package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun InvitationExplanationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "致用户的一封信") },
        text = {
            Text(
                text = "OperitAI 是一款免费、开源的软件，我们衷心希望它能为您带来价值。" +
                        "然而，许多用户在使用后，尽管很喜欢，却很少向他人推荐，这导致我们的社区发展缓慢，新功能开发也因此受限。" +
                        "\n\n我们曾尝试过各种自愿分享的方案，但收效甚微。" +
                        "为了让这个项目能持续发展，为大家带来更多强大的功能，我们迫不得已引入了邀请机制。" +
                        "您的每一次邀请，都是对我们最直接的支持。感谢您的理解与帮助！" +
                        "\n\n邀请1位好友可解锁工作区，邀请2位可解锁悬浮窗与语音助手+自动点击功能。" +
                        "\n\n作为福利，成功接受他人邀请也会为你计入1个邀请数（仅限首次）。"
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(text = "我明白了，去邀请")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        }
    )
} 
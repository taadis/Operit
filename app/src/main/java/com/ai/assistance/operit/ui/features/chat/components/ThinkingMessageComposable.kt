package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage

/** A composable function for rendering thinking/processing messages in a simple text style. */
@Composable
fun ThinkingMessageComposable(
        message: ChatMessage,
        backgroundColor: Color,
        textColor: Color,
        initialExpanded: Boolean = false // 保留参数但不再使用
) {
        // Extract plain text thinking content
        val thinkingContent = message.content

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                // 标题文本
                Text(
                        text = stringResource(id = R.string.thinking_process),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.7f)  // 使用半透明的文本颜色
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 思考内容显示为普通文本
                if (thinkingContent.isNotBlank()) {
                        Text(
                                text = thinkingContent,
                                color = textColor.copy(alpha = 0.6f), // 更淡的灰色
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )
                }
        }
}

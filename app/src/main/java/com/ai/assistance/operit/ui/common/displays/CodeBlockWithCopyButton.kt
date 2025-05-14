package com.ai.assistance.operit.ui.common.displays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * A composable that renders a code block with a copy button in the top right corner.
 *
 * @param code The code content to display
 * @param language The programming language (optional, for potential syntax highlighting)
 * @param backgroundColor The background color of the code block
 * @param textColor The text color of the code
 * @param onCopy Optional callback when copy occurs
 */
@Composable
fun CodeBlockWithCopyButton(
        code: String,
        language: String? = null,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        onCopy: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(backgroundColor)
    ) {
        // Language indicator and copy button row
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Language indicator (if provided)
            if (!language.isNullOrBlank()) {
                Text(
                        text = language,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }

            // Copy button - make it much smaller
            IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCopy?.invoke()
                    },
                    modifier = Modifier.size(24.dp).padding(0.dp)
            ) {
                Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                )
            }
        }

        // Code content
        SelectionContainer(
                modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)
        ) {
            Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = textColor
            )
        }
    }
}

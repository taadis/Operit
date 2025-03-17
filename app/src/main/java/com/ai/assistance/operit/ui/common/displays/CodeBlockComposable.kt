package com.ai.assistance.operit.ui.common.displays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A composable function for rendering code blocks with proper styling.
 * Includes selection and copy on long press capability.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeBlockComposable(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    // Extract language from markdown code block format if available
    val displayLanguage = language ?: if (code.contains("\n")) {
        code.substringBefore("\n").trim()
    } else {
        null
    }
    
    // Clean up code content if it has a language header
    val codeContent = if (displayLanguage != null && code.startsWith(displayLanguage)) {
        code.substringAfter("\n")
    } else {
        code
    }
    
    // For clipboard functionality
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Language tag (if available)
        if (!displayLanguage.isNullOrBlank()) {
            Text(
                text = displayLanguage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
        }
        
        // Code content with selection and copy on long press
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Do nothing on normal click */ },
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(codeContent))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = codeContent,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
} 
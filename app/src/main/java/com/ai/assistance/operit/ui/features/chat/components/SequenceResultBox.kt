package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A specialized component for displaying sequence execution results
 * with a more structured and visually appealing format.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SequenceResultBox(
    uuid: String,
    title: String,
    content: String,
    isSuccess: Boolean,
    enableCopy: Boolean = true,
    isCompletion: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    
    // Extract metrics from content
    val totalSteps = Regex("总步骤: (\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val successfulSteps = Regex("成功步骤: (\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val failedSteps = Regex("失败步骤: (\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val successRate = if (totalSteps > 0) (successfulSteps * 100 / totalSteps) else 0
    
    val accentColor = when {
        isCompletion -> MaterialTheme.colorScheme.tertiary
        isSuccess -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    
    val backgroundColor = when {
        isCompletion -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        isSuccess -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    }
    
    val textColor = when {
        isCompletion -> MaterialTheme.colorScheme.onTertiaryContainer
        isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .animateContentSize()
            .let {
                if (enableCopy) {
                    it.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Toggle expanded on click */ expanded = !expanded },
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(content))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                } else {
                    it.clickable { expanded = !expanded }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (isSuccess) "成功" else "失败",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                }
                
                // Expand/collapse button
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = textColor
                    )
                }
            }
            
            // Summary section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "执行摘要",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "总步骤数",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$totalSteps",
                                style = MaterialTheme.typography.titleMedium,
                                color = textColor
                            )
                        }
                        
                        Column {
                            Text(
                                text = "成功步骤",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$successfulSteps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Column {
                            Text(
                                text = "失败步骤",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$failedSteps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        Column {
                            Text(
                                text = "成功率",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$successRate%",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (successRate > 80) 
                                    MaterialTheme.colorScheme.primary 
                                else if (successRate > 50) 
                                    MaterialTheme.colorScheme.tertiary
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            // Detailed results
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "详细结果",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Format the detailed content
                val detailedContent = content.substringAfter("详细结果:").trim()
                
                // Format and display step results
                val steps = detailedContent.split("\n").filter { it.isNotBlank() }
                
                steps.forEach { step ->
                    val isStepSuccess = step.contains("成功")
                    val isStepError = step.contains("错误") || step.contains("失败")
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isStepSuccess -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    isStepError -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                    else -> textColor.copy(alpha = 0.05f)
                                }
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isStepSuccess) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isStepSuccess) "成功" else "失败",
                            tint = if (isStepSuccess) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        SelectionContainer {
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
} 
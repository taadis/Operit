package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.text.selection.SelectionContainer
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.ui.common.displays.TextWithCodeBlocksComposable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Arrangement

/**
 * A composable function for rendering AI response messages in a Cursor IDE style.
 * Supports text selection and copy on long press for different segments.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiMessageComposable(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    supportToolMarkup: Boolean = true,
    collapseExecution: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    // 添加状态变量来跟踪当前显示的结果对话框
    var showResultDialog by remember { mutableStateOf(false) }
    var resultDialogTitle by remember { mutableStateOf("") }
    var resultDialogContent by remember { mutableStateOf("") }
    var resultDialogParams by remember { mutableStateOf("") }
    
    // 添加结果对话框
    if (showResultDialog) {
        ResultDialog(
            title = resultDialogTitle,
            content = resultDialogContent,
            params = resultDialogParams.takeIf { it.isNotBlank() },
            onDismiss = { showResultDialog = false }
        )
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Response",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Parse the message content to identify tool markup
            val contentSegments =
                MessageContentParser.parseContent(message.content, supportToolMarkup)
            
            // 在折叠执行模式下，跟踪已经显示过的工具请求和执行
            val displayedTools = mutableSetOf<String>() // 已经显示过的工具名称集合
            
            // 跟踪每个工具的最终状态，键使用工具名称而不是UUID
            val toolFinalStates = mutableMapOf<String, Triple<Boolean, String?, Boolean>>() // <toolName, Triple<isProcessing, result, isError>>
            
            // 跟踪每个工具请求的参数
            val toolParams = mutableMapOf<String, String>() // <toolName, params>
            
            // 跟踪已完成的工具调用（显示过结果的工具）
            val completedTools = mutableSetOf<String>() // 已经显示过结果的工具名称集合
            
            // 折叠执行模式下，预处理分析所有工具的最终状态
            if (collapseExecution) {
                // 第一遍：找出所有工具的最终状态
                contentSegments.forEach { segment ->
                    when (segment) {
                        is ContentSegment.ToolRequest -> {
                            val toolName = segment.name
                            // 存储参数以备后用
                            toolParams[toolName] = segment.params
                            // 如果该工具未完成，则初始化为处理中状态
                            if (!completedTools.contains(toolName)) {
                                toolFinalStates[toolName] = Triple(true, null, false)
                            }
                        }
                        is ContentSegment.ToolResult -> {
                            val toolName = segment.name
                            // 更新为结果状态（优先级高于请求和执行状态）
                            toolFinalStates[toolName] = Triple(false, segment.content, segment.isError)
                            // 标记该工具已完成
                            completedTools.add(toolName)
                        }
                        is ContentSegment.Status -> {
                            if (segment.toolName.isNotBlank()) {
                                val toolName = segment.toolName
                                when (segment.type) {
                                    "executing" -> {
                                        // 只有当该工具未完成时才更新为执行状态
                                        if (!completedTools.contains(toolName)) {
                                            toolFinalStates[toolName] = Triple(true, null, false)
                                        }
                                    }
                                    "result" -> {
                                        // 更新为结果状态
                                        toolFinalStates[toolName] = Triple(false, segment.content, !segment.success)
                                        // 标记该工具已完成
                                        completedTools.add(toolName)
                                    }
                                }
                            }
                        }
                        // 添加缺失的分支以使when表达式穷尽
                        is ContentSegment.Text, 
                        is ContentSegment.ToolExecution,
                        is ContentSegment.PlanItem,
                        is ContentSegment.PlanUpdate -> {
                            // 这些类型不影响工具的最终状态，无需处理
                        }
                    }
                }
            }
            
            // Render each segment
            contentSegments.forEach { segment ->
                when (segment) {
                    is ContentSegment.Text -> {
                        // Render regular text content
                        if (segment.content.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { /* Do nothing on normal click */ },
                                        onLongClick = {
                                            clipboardManager.setText(AnnotatedString(segment.content))
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    )
                            ) {
                                TextWithCodeBlocksComposable(
                                    text = segment.content,
                                    textColor = textColor
                                )
                            }
                        }
                    }
                    
                    is ContentSegment.ToolRequest -> {
                        val toolName = segment.name
                        
                        if (collapseExecution) {
                            // 如果是多次调用同一工具，则每次都显示
                            // 检查该工具是否已经显示过并已有结果
                            if (completedTools.contains(toolName) && displayedTools.contains(toolName)) {
                                // 如果该工具已经完成并且显示过，则我们要么跳过它（如果它只是另一个请求）
                                // 要么重置状态开始新一轮显示（如果后面有新的结果）
                                
                                // 查找这个请求之后是否还有该工具的结果
                                var hasMoreResults = false
                                var hasFoundCurrentRequest = false
                                
                                contentSegments.forEach { s ->
                                    // 找到当前请求后才开始检查
                                    if (s === segment) {
                                        hasFoundCurrentRequest = true
                                        return@forEach
                                    }
                                    
                                    // 当前请求之后有该工具的结果
                                    if (hasFoundCurrentRequest && 
                                        ((s is ContentSegment.ToolResult && s.name == toolName) ||
                                         (s is ContentSegment.Status && s.toolName == toolName && s.type == "result"))) {
                                        hasMoreResults = true
                                        return@forEach
                                    }
                                }
                                
                                // 如果没有更多结果，则跳过这个请求
                                if (!hasMoreResults) {
                                    return@forEach
                                }
                                
                                // 如果有更多结果，则重置该工具的状态，开始新一轮显示
                                displayedTools.remove(toolName)
                                completedTools.remove(toolName)
                            }
                            
                            // 如果这个工具已经显示过但尚未完成，则跳过（状态会被后续的执行或结果更新）
                            if (displayedTools.contains(toolName) && !completedTools.contains(toolName)) {
                                return@forEach
                            }
                            
                            // 添加到已显示工具集合
                            displayedTools.add(toolName)
                            
                            // 获取该工具的最终状态
                            val finalState = toolFinalStates[toolName]
                            
                            // 使用共享的ToolStatusDisplay组件
                            ToolStatusDisplay(
                                toolName = toolName,
                                isProcessing = finalState?.first ?: false,
                                isError = finalState?.third ?: false,
                                result = finalState?.second,
                                params = segment.params,
                                onShowResult = {
                                    // 显示结果对话框
                                    resultDialogTitle = toolName
                                    // 如果有工具结果，显示结果，否则显示请求参数
                                    resultDialogContent = if (finalState?.second == null || finalState.second.toString().isBlank()) {
                                        "Parameters:\n${segment.params}"
                                    } else {
                                        finalState.second.toString()
                                    }
                                    resultDialogParams = segment.params
                                    showResultDialog = true
                                },
                                onCopyResult = {
                                    val result = finalState?.second
                                    if (result != null) {
                                        clipboardManager.setText(AnnotatedString(result))
                                    }
                                }
                            )
                        } else {
                            // 非折叠模式下的完整显示
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* Do nothing on normal click */ },
                                    onLongClick = {
                                        val copyText = "Tool Request: ${segment.name}\nParameters: ${segment.params}"
                                        clipboardManager.setText(AnnotatedString(copyText))
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (segment.description.isNotBlank())
                                        "Requesting tool: ${segment.name} (${segment.description})"
                                    else
                                        "Requesting tool: ${segment.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                
                                if (segment.params.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Parameters:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                    
                                    // Display parameters
                                    SelectionContainer {
                                        Text(
                                            text = segment.params,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))
                                                .padding(8.dp)
                                        )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    is ContentSegment.ToolExecution -> {
                        val toolName = segment.name
                        
                        if (collapseExecution) {
                            // 在折叠模式下，只有当工具尚未显示过时才显示
                            if (!displayedTools.contains(toolName)) {
                                // 添加到已显示工具集合
                                displayedTools.add(toolName)
                                
                                // 获取该工具的最终状态
                                val finalState = toolFinalStates[toolName]
                                
                                // 使用共享的ToolStatusDisplay组件
                                ToolStatusDisplay(
                                    toolName = toolName,
                                    isProcessing = finalState?.first ?: true,
                                    isError = finalState?.third ?: false,
                                    result = finalState?.second,
                                    params = toolParams[toolName],
                                    onShowResult = {
                                        val result = finalState?.second
                                        if (result != null) {
                                            resultDialogTitle = toolName
                                            resultDialogContent = result.toString()
                                            resultDialogParams = toolParams[toolName] ?: ""
                                            showResultDialog = true
                                        }
                                    },
                                    onCopyResult = {
                                        val result = finalState?.second
                                        if (result != null) {
                                            clipboardManager.setText(AnnotatedString(result))
                                        }
                                    }
                                )
                            }
                        } else {
                            // 非折叠模式下使用原有的ToolExecutionBox
                        ToolExecutionBox(
                            toolName = segment.name,
                            isProcessing = true,
                            result = null,
                            isError = false,
                            params = null,
                            enableCopy = true,
                            hideToolRequest = true,
                            collapseExecution = collapseExecution
                        )
                        }
                    }
                    
                    is ContentSegment.ToolResult -> {
                        val toolName = segment.name
                        
                        if (collapseExecution) {
                            // 检查该工具是否已经显示过
                            if (displayedTools.contains(toolName)) {
                                // 如果已经显示过但尚未完成，则不需要再显示
                                // 因为该工具的最终状态已经在预处理时更新为结果状态
                                // 效果会通过之前显示的组件更新
                                
                                // 标记该工具已完成
                                completedTools.add(toolName)
                                return@forEach
                            }
                            
                            // 如果工具尚未显示过，则显示结果
                            displayedTools.add(toolName)
                            completedTools.add(toolName)
                            
                            // 使用共享的ToolStatusDisplay组件
                            ToolStatusDisplay(
                                toolName = toolName,
                                isProcessing = false,
                                isError = segment.isError,
                                result = segment.content,
                                params = toolParams[toolName],
                                onShowResult = {
                                    // 显示结果对话框
                                    resultDialogTitle = toolName
                                    resultDialogContent = segment.content
                                    resultDialogParams = toolParams[toolName] ?: ""
                                    showResultDialog = true
                                },
                                onCopyResult = {
                                    clipboardManager.setText(AnnotatedString(segment.content))
                                }
                            )
                        } else {
                            // 非折叠模式下使用原有的ToolExecutionBox
                        ToolExecutionBox(
                            toolName = segment.name,
                            isProcessing = false,
                            result = segment.content,
                            isError = segment.isError,
                            params = null,
                            enableCopy = true,
                            hideToolRequest = false,
                            collapseExecution = collapseExecution
                        )
                        }
                    }
                    
                    is ContentSegment.Status -> {
                        if (segment.toolName.isNotBlank()) {
                            val toolName = segment.toolName
                            
                            if (collapseExecution) {
                                // 如果是执行状态
                                if (segment.type == "executing") {
                                    // 如果工具尚未显示过，则显示
                                    if (!displayedTools.contains(toolName)) {
                                        displayedTools.add(toolName)
                                        
                                        // 获取该工具的最终状态
                                        val finalState = toolFinalStates[toolName]
                                        
                                        // 使用共享的ToolStatusDisplay组件
                                        ToolStatusDisplay(
                                            toolName = toolName,
                                            isProcessing = finalState?.first ?: true,
                                            isError = finalState?.third ?: false,
                                            result = finalState?.second,
                                            params = toolParams[toolName],
                                            onShowResult = {
                                                val result = finalState?.second
                                                if (result != null && result.toString().isNotBlank()) {
                                                    resultDialogTitle = toolName
                                                    resultDialogContent = result.toString()
                                                    resultDialogParams = toolParams[toolName] ?: ""
                                                    showResultDialog = true
                                                }
                                            },
                                            onCopyResult = {
                                                val result = finalState?.second
                                                if (result != null) {
                                                    clipboardManager.setText(AnnotatedString(result))
                                                }
                                            }
                                        )
                                    }
                                }
                                // 如果是结果状态
                                else if (segment.type == "result") {
                                    // 如果工具尚未显示过，则显示
                                    if (!displayedTools.contains(toolName)) {
                                        displayedTools.add(toolName)
                                    }
                                    
                                    // 标记该工具已完成
                                    completedTools.add(toolName)
                                }
                                // 其他状态不处理
                            } else {
                                // 非折叠模式下使用原有的ToolExecutionBox
                                if (segment.type == "executing") {
                                    ToolExecutionBox(
                                        toolName = segment.toolName,
                                        isProcessing = true,
                                        result = null,
                                        isError = false,
                                        params = null,
                                        enableCopy = true,
                                        hideToolRequest = true,
                                        collapseExecution = collapseExecution
                                    )
                                } else { // result
                                    ToolExecutionBox(
                                        toolName = segment.toolName,
                                        isProcessing = false,
                                        result = segment.content,
                                        isError = !segment.success,
                                        params = null,
                                        enableCopy = true,
                                        hideToolRequest = false,
                                        collapseExecution = collapseExecution
                                    )
                                }
                            }
                        } else {
                            // 处理其他类型的状态
                            when (segment.type) {
                                "completion", "complete" -> {
                                    // Task completion indicator
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = "✓ Task completed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                                "wait_for_user_need" -> {
                                    // Wait for user need indicator
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = "✓ Ready for further assistance",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                                "warning" -> {
                                    // Warning information
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { /* Do nothing on normal click */ },
                                                onLongClick = {
                                                    clipboardManager.setText(AnnotatedString(segment.content))
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = segment.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // 新增：处理计划项显示
                    is ContentSegment.PlanItem -> {
                        PlanItemDisplay(segment.id, segment.status, segment.description)
                    }
                    
                    // 新增：处理计划更新显示
                    is ContentSegment.PlanUpdate -> {
                        PlanUpdateDisplay(segment.id, segment.status, segment.message)
                    }
                }
                
                // Add spacing between segments
                if (segment != contentSegments.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * 计划项的显示组件
 */
@Composable
private fun PlanItemDisplay(id: String, status: String, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标根据状态显示不同的图标
            val (icon, color) = when (status.lowercase()) {
                "todo" -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
                "in_progress" -> Icons.Filled.PlayArrow to MaterialTheme.colorScheme.primary
                "completed" -> Icons.Filled.CheckCircle to Color(0xFF4CAF50) // Green
                "failed" -> Icons.Filled.Error to Color(0xFFF44336) // Red
                "cancelled" -> Icons.Filled.Cancel to Color(0xFF9E9E9E) // Gray
                else -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
            }
            
            Icon(
                imageVector = icon,
                contentDescription = "Plan item status: $status",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // ID和状态信息
                Text(
                    text = "ID: $id • Status: ${status.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 计划更新的显示组件
 */
@Composable
private fun PlanUpdateDisplay(id: String, status: String, message: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status.lowercase()) {
                "completed" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                "failed" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                "in_progress" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
        border = BorderStroke(1.dp, when (status.lowercase()) {
            "completed" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            "failed" -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            "in_progress" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标根据状态显示不同的图标
            val (icon, color) = when (status.lowercase()) {
                "todo" -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
                "in_progress" -> Icons.Filled.PlayArrow to MaterialTheme.colorScheme.primary
                "completed" -> Icons.Filled.CheckCircle to Color(0xFF4CAF50) // Green
                "failed" -> Icons.Filled.Error to Color(0xFFF44336) // Red
                "cancelled" -> Icons.Filled.Cancel to Color(0xFF9E9E9E) // Gray
                else -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
            }
            
            Icon(
                imageVector = icon,
                contentDescription = "Plan status update: $status",
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 状态更新信息
                Text(
                    text = "Plan #$id: Status updated to ${status.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = when (status.lowercase()) {
                        "completed" -> Color(0xFF2E7D32) // dark green
                        "failed" -> Color(0xFFC62828) // dark red
                        "in_progress" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
                // 如果有消息，则显示
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
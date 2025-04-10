package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment
import com.ai.assistance.operit.ui.common.displays.TextWithCodeBlocksComposable

/**
 * A composable function for rendering AI response messages in a Cursor IDE style. Supports text
 * selection and copy on long press for different segments. Always uses collapsed execution mode for
 * tool output display.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiMessageComposable(
        message: ChatMessage,
        backgroundColor: Color,
        textColor: Color,
        supportToolMarkup: Boolean = true
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

    // Removed the Card background - Direct Column for AI response
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Response",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        // Parse the message content to identify tool markup
        val contentSegments =
                MessageContentParser.parseContent(message.content, supportToolMarkup)

        // 跟踪已经显示过的工具请求和执行
        val displayedTools = mutableSetOf<String>() // 已经显示过的工具名称集合

        // 跟踪每个工具的最终状态，键使用工具名称而不是UUID
        val toolFinalStates =
                mutableMapOf<
                        String,
                        Triple<
                                Boolean,
                                String?,
                                Boolean>>() // <toolName, Triple<isProcessing, result, isError>>

        // 跟踪每个工具请求的参数
        val toolParams = mutableMapOf<String, String>() // <toolName, params>

        // 跟踪已完成的工具调用（显示过结果的工具）
        val completedTools = mutableSetOf<String>() // 已经显示过结果的工具名称集合

        // 预处理分析所有工具的最终状态
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
                                toolFinalStates[toolName] =
                                        Triple(false, segment.content, !segment.success)
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

        // Render each segment
        contentSegments.forEach { segment ->
            when (segment) {
                is ContentSegment.Text -> {
                    // Render regular text content
                    if (segment.content.isNotBlank()) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .combinedClickable(
                                                        interactionSource =
                                                                remember {
                                                                    MutableInteractionSource()
                                                                },
                                                        indication = null,
                                                        onClick = { /* Do nothing on normal click */
                                                        },
                                                        onLongClick = {
                                                            clipboardManager.setText(
                                                                    AnnotatedString(
                                                                            segment.content
                                                                    )
                                                            )
                                                            haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                            )
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

                    // 如果是多次调用同一工具，则每次都显示
                    // 检查该工具是否已经显示过并已有结果
                    if (completedTools.contains(toolName) && displayedTools.contains(toolName)
                    ) {
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
                                            ((s is ContentSegment.ToolResult &&
                                                    s.name == toolName) ||
                                                    (s is ContentSegment.Status &&
                                                            s.toolName == toolName &&
                                                            s.type == "result"))
                            ) {
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
                    if (displayedTools.contains(toolName) && !completedTools.contains(toolName)
                    ) {
                        return@forEach
                    }

                    // 添加到已显示工具集合
                    displayedTools.add(toolName)

                    // 获取该工具的最终状态
                    val finalState = toolFinalStates[toolName]

                    // 使用共享的ToolStatusDisplay组件
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                                resultDialogContent =
                                        if (finalState?.second == null ||
                                                        finalState.second.toString().isBlank()
                                        ) {
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
                    }
                }
                is ContentSegment.ToolExecution -> {
                    val toolName = segment.name

                    // 在折叠模式下，只有当工具尚未显示过时才显示
                    if (!displayedTools.contains(toolName)) {
                        // 添加到已显示工具集合
                        displayedTools.add(toolName)

                        // 获取该工具的最终状态
                        val finalState = toolFinalStates[toolName]

                        // 使用共享的ToolStatusDisplay组件
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                    }
                }
                is ContentSegment.ToolResult -> {
                    val toolName = segment.name

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
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                    }
                }
                is ContentSegment.Status -> {
                    if (segment.toolName.isNotBlank()) {
                        val toolName = segment.toolName

                        // 如果是执行状态
                        if (segment.type == "executing") {
                            // 如果工具尚未显示过，则显示
                            if (!displayedTools.contains(toolName)) {
                                displayedTools.add(toolName)

                                // 获取该工具的最终状态
                                val finalState = toolFinalStates[toolName]

                                // 使用共享的ToolStatusDisplay组件
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    ToolStatusDisplay(
                                        toolName = toolName,
                                        isProcessing = finalState?.first ?: true,
                                        isError = finalState?.third ?: false,
                                        result = finalState?.second,
                                        params = toolParams[toolName],
                                        onShowResult = {
                                            val result = finalState?.second
                                            if (result != null && result.toString().isNotBlank()
                                            ) {
                                                resultDialogTitle = toolName
                                                resultDialogContent = result.toString()
                                                resultDialogParams = toolParams[toolName] ?: ""
                                                showResultDialog = true
                                            }
                                        },
                                        onCopyResult = {
                                            val result = finalState?.second
                                            if (result != null) {
                                                clipboardManager.setText(
                                                        AnnotatedString(result)
                                                )
                                            }
                                        }
                                    )
                                }
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
                        // 处理其他类型的状态
                        when (segment.type) {
                            "completion", "complete" -> {
                                // Task completion indicator
                                Card(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .padding(vertical = 4.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer.copy(
                                                                        alpha = 0.3f
                                                                )
                                                ),
                                        border =
                                                BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        )
                                                )
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
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .padding(vertical = 4.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .tertiaryContainer.copy(
                                                                        alpha = 0.3f
                                                                )
                                                ),
                                        border =
                                                BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.tertiary.copy(
                                                                alpha = 0.3f
                                                        )
                                                )
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
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .padding(vertical = 4.dp)
                                                        .combinedClickable(
                                                                interactionSource =
                                                                        remember {
                                                                            MutableInteractionSource()
                                                                        },
                                                                indication = null,
                                                                onClick = { /* Do nothing on normal click */
                                                                },
                                                                onLongClick = {
                                                                    clipboardManager.setText(
                                                                            AnnotatedString(
                                                                                    segment.content
                                                                            )
                                                                    )
                                                                    haptic.performHapticFeedback(
                                                                            HapticFeedbackType
                                                                                    .LongPress
                                                                    )
                                                                }
                                                        ),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer.copy(
                                                                        alpha = 0.3f
                                                                )
                                                ),
                                        border =
                                                BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.error.copy(
                                                                alpha = 0.3f
                                                        )
                                                )
                                ) {
                                    SelectionContainer {
                                        Text(
                                                text = segment.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                        MaterialTheme.colorScheme
                                                                .onErrorContainer,
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
                    UnifiedPlanDisplay(
                        id = segment.id,
                        status = segment.status,
                        content = segment.description,
                        isUpdate = false
                    )
                }

                // 新增：处理计划更新显示
                is ContentSegment.PlanUpdate -> {
                    UnifiedPlanDisplay(
                        id = segment.id,
                        status = segment.status,
                        content = segment.message ?: "",
                        isUpdate = false
                    )
                }
            }

            // Add spacing between segments
            if (segment != contentSegments.last()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** 统一的计划显示组件 - 同时处理计划项和计划更新 */
@Composable
private fun UnifiedPlanDisplay(
    id: String,
    status: String,
    content: String,
    isUpdate: Boolean
) {
    when (status.lowercase()) {
        "todo" -> {
            if (!isUpdate) { // Only show TODO items for plan items, not updates
                // TODO plans: Display in a smaller size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.RadioButtonUnchecked,
                        contentDescription = "Todo task",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        "in_progress" -> {
            // If this is a plan item or update with content, show the indicator
            if (!isUpdate || content.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    // Divider with play icon and status in the middle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            thickness = 1.dp
                        )
                        
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Plan in progress",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                Text(
                                    text = "计划 #$id 执行中",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            thickness = 1.dp
                        )
                    }
                    
                    // Show content (description or message) if available
                    if (content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            ),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            } else {
                // Empty update - don't show anything
            }
        }
        "completed", "done" -> {
            // For plan items, show completion with description
            // For updates, show a simple divider
            if (!isUpdate || content.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            thickness = 1.5.dp
                        )
                        
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Plan completed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                Text(
                                    text = "计划完成",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            thickness = 1.5.dp
                        )
                    }
                    
                    // Show content (description or message) if available
                    if (content.isNotBlank() && content != "Completed" && content != "Done") {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp, start = 16.dp, bottom = if (isUpdate) 8.dp else 0.dp),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
        else -> {
            // For other statuses (failed, cancelled, etc.)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (status.lowercase()) {
                        "failed" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        "cancelled" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    }
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, color) = when (status.lowercase()) {
                        "failed" -> Icons.Filled.Error to Color(0xFFC62828) // Dark red
                        "cancelled" -> Icons.Filled.Cancel to Color(0xFF757575) // Gray
                        else -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
                    }
                    
                    Icon(
                        imageVector = icon,
                        contentDescription = "Plan status: $status",
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = "计划 #$id: ${status.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                        
                        if (content.isNotBlank()) {
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

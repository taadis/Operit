package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.EnhancedMarkdownText
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment

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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
                text = "Response",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        // Parse the message content to identify tool markup
        val contentSegments = MessageContentParser.parseContent(message.content, supportToolMarkup)

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

        // 跟踪模拟工具: 有请求但无执行和结果，同时存在完成或等待用户响应标签
        val simulatedTools = mutableSetOf<String>() // 模拟工具名称集合

        // 计划跟踪相关数据结构
        val planStatus = mutableMapOf<String, Boolean>() // 跟踪计划的状态 <planId, isActive>
        val currentActivePlanId = remember { mutableStateOf<String?>(null) }
        val planSegments = mutableListOf<Pair<ContentSegment, @Composable () -> Unit>>()

        // 第一遍预处理：收集工具请求
        val toolRequests = mutableMapOf<String, Int>() // <toolName, position>
        contentSegments.forEachIndexed { index, segment ->
            if (segment is ContentSegment.ToolRequest) {
                toolRequests[segment.name] = index
            }
        }

        // 第二遍预处理：检查是否存在完成或等待用户响应标签
        val hasCompletionStatus =
                contentSegments.any { segment ->
                    segment is ContentSegment.Status &&
                            (segment.type == "completion" ||
                                    segment.type == "complete" ||
                                    segment.type == "wait_for_user_need")
                }

        // 第三遍预处理：识别模拟工具 - 有请求但无执行/结果状态，同时存在完成或等待用户响应标签
        if (hasCompletionStatus) {
            for ((toolName, position) in toolRequests) {
                // 检查该工具在请求之后是否有执行或结果相关标签
                val hasExecutionOrResult =
                        contentSegments.drop(position + 1).any { segment ->
                            (segment is ContentSegment.ToolExecution && segment.name == toolName) ||
                                    (segment is ContentSegment.ToolResult &&
                                            segment.name == toolName) ||
                                    (segment is ContentSegment.Status &&
                                            segment.toolName == toolName &&
                                            (segment.type == "executing" ||
                                                    segment.type == "result"))
                        }

                // 如果没有执行或结果，标记为模拟工具
                if (!hasExecutionOrResult) {
                    simulatedTools.add(toolName)
                }
            }
        }

        // 预处理：初始化工具状态和计划状态
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
                is ContentSegment.PlanItem -> {
                    val planId = segment.id
                    if (segment.status.lowercase() == "in_progress") {
                        planStatus[planId] = true
                    } else if (segment.status.lowercase() == "completed" ||
                                    segment.status.lowercase() == "done"
                    ) {
                        planStatus[planId] = false
                    }
                }
                is ContentSegment.PlanUpdate -> {
                    val planId = segment.id
                    if (segment.status.lowercase() == "in_progress") {
                        planStatus[planId] = true
                    } else if (segment.status.lowercase() == "completed" ||
                                    segment.status.lowercase() == "done"
                    ) {
                        planStatus[planId] = false
                    }
                }
                else -> {
                    /* 其他段落不影响工具和计划状态 */
                }
            }
        }

        // 第二遍：按顺序渲染所有段落，同时处理计划分组
        contentSegments.forEach { segment ->
            // 创建当前段落的可组合项
            val composable: @Composable () -> Unit = {
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
                                EnhancedMarkdownText(
                                        text = segment.content,
                                        textColor = textColor,
                                        onCodeCopied = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                        }
                                )
                            }
                        }
                    }
                    is ContentSegment.ToolRequest -> {
                        val toolName = segment.name
                        // 检查是否为模拟工具
                        val isSimulated = simulatedTools.contains(toolName)

                        // 如果是多次调用同一工具，则每次都显示
                        // 检查该工具是否已经显示过并已有结果
                        if (completedTools.contains(toolName) && displayedTools.contains(toolName)
                        ) {
                            // 如果该工具已经完成并且显示过，则我们要么跳过它（如果它只是另一个请求）
                            // 要么重置状态开始新一轮显示（如果后面有新的结果）

                            // 查找这个请求之后是否还有该工具的结果
                            var hasMoreResults = false
                            var hasFoundCurrentRequest = false

                            // 使用for循环替代forEach，以避免return@forEach
                            for (s in contentSegments) {
                                // 找到当前请求后才开始检查
                                if (s === segment) {
                                    hasFoundCurrentRequest = true
                                    continue
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
                                    break
                                }
                            }

                            // 如果没有更多结果，则不显示这个请求
                            if (!hasMoreResults) {
                                // 这里使用Unit返回而不是非局部返回
                                Unit
                            } else {
                                // 如果有更多结果，则重置该工具的状态，开始新一轮显示
                                displayedTools.remove(toolName)
                                completedTools.remove(toolName)

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
                                                                        finalState
                                                                                .second
                                                                                .toString()
                                                                                .isBlank()
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
                                                    clipboardManager.setText(
                                                            AnnotatedString(result)
                                                    )
                                                }
                                            },
                                            isSimulated = isSimulated
                                    )
                                }
                            }
                        } else if (displayedTools.contains(toolName) &&
                                        !completedTools.contains(toolName)
                        ) {
                            // 如果这个工具已经显示过但尚未完成，则跳过（状态会被后续的执行或结果更新）
                            // 这里使用Unit返回而不是非局部返回
                            Unit
                        } else {
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
                                                                    finalState
                                                                            .second
                                                                            .toString()
                                                                            .isBlank()
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
                                        },
                                        isSimulated = isSimulated
                                )
                            }
                        }
                    }
                    is ContentSegment.ToolExecution -> {
                        val toolName = segment.name
                        // 检查是否为模拟工具
                        val isSimulated = simulatedTools.contains(toolName)

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
                                        },
                                        isSimulated = isSimulated
                                )
                            }
                        }
                    }
                    is ContentSegment.ToolResult -> {
                        val toolName = segment.name
                        // 检查是否为模拟工具
                        val isSimulated = simulatedTools.contains(toolName)

                        // 检查该工具是否已经显示过
                        if (displayedTools.contains(toolName)) {
                            // 如果已经显示过但尚未完成，则不需要再显示
                            // 因为该工具的最终状态已经在预处理时更新为结果状态
                            // 效果会通过之前显示的组件更新

                            // 标记该工具已完成
                            completedTools.add(toolName)
                            // 这里使用Unit返回而不是非局部返回
                            Unit
                        } else {
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
                                            clipboardManager.setText(
                                                    AnnotatedString(segment.content)
                                            )
                                        },
                                        isSimulated = isSimulated
                                )
                            }
                        }
                    }
                    is ContentSegment.Status -> {
                        if (segment.toolName.isNotBlank()) {
                            val toolName = segment.toolName
                            // 检查是否为模拟工具
                            val isSimulated = simulatedTools.contains(toolName)

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
                                                    if (result != null &&
                                                                    result.toString().isNotBlank()
                                                    ) {
                                                        resultDialogTitle = toolName
                                                        resultDialogContent = result.toString()
                                                        resultDialogParams =
                                                                toolParams[toolName] ?: ""
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
                                                },
                                                isSimulated = isSimulated
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
                    is ContentSegment.PlanItem -> {
                        UnifiedPlanDisplay(
                                id = segment.id,
                                status = segment.status,
                                content = segment.description,
                                isUpdate = false
                        )
                    }
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

            // 处理计划相关的逻辑
            when (segment) {
                is ContentSegment.PlanItem, is ContentSegment.PlanUpdate -> {
                    val planId =
                            when (segment) {
                                is ContentSegment.PlanItem -> segment.id
                                is ContentSegment.PlanUpdate -> segment.id
                                else -> ""
                            }

                    val status =
                            when (segment) {
                                is ContentSegment.PlanItem -> segment.status
                                is ContentSegment.PlanUpdate -> segment.status
                                else -> ""
                            }

                    // 处理计划状态变化
                    if (status.lowercase() == "in_progress") {
                        // 如果有正在进行的计划，先完成它
                        if (currentActivePlanId.value != null && currentActivePlanId.value != planId
                        ) {
                            if (planSegments.isNotEmpty()) {
                                // 渲染当前活动计划
                                renderPlanWithTimeline(
                                        currentActivePlanId.value!!,
                                        planSegments.map { it.second }
                                )
                                planSegments.clear()
                            }
                        }

                        // 先渲染计划开始标记
                        composable()

                        // 设置当前活动计划ID
                        currentActivePlanId.value = planId

                        // 不将开始标记添加到计划段落中
                    } else if (status.lowercase() == "completed" || status.lowercase() == "done") {
                        // 如果计划完成并且正在追踪这个计划
                        if (currentActivePlanId.value == planId) {
                            // 如果有收集到的段落，渲染它们
                            if (planSegments.isNotEmpty()) {
                                renderPlanWithTimeline(planId, planSegments.map { it.second })
                                planSegments.clear()
                            }

                            // 单独渲染计划结束标记
                            composable()

                            // 清空当前活动计划
                            currentActivePlanId.value = null
                        } else {
                            // 如果不是当前跟踪的计划，直接渲染
                            composable()
                        }
                    } else {
                        // 其他状态（比如 todo、failed 等），直接渲染
                        composable()
                    }
                }
                else -> {
                    // 对于非计划相关的段落
                    if (currentActivePlanId.value != null) {
                        // 如果有活动计划，添加到计划中
                        planSegments.add(Pair(segment, composable))
                    } else {
                        // 否则直接渲染
                        composable()
                    }
                }
            }
        }

        // 渲染任何剩余的活动计划
        if (currentActivePlanId.value != null && planSegments.isNotEmpty()) {
            renderPlanWithTimeline(currentActivePlanId.value!!, planSegments.map { it.second })
        }
    }
}

/** 使用时间线渲染一个计划的所有段落 */
@Composable
private fun renderPlanWithTimeline(planId: String, composables: List<@Composable () -> Unit>) {
    // 如果没有内容，不渲染时间线
    if (composables.isEmpty()) return

    // 使用Row布局来更精确控制时间线和内容
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(IntrinsicSize.Min), // 使用IntrinsicSize.Min确保高度跟随内容
                verticalAlignment = Alignment.Top // 确保从顶部对齐
        ) {
            // 左侧时间线区域
            Box(modifier = Modifier.width(30.dp).fillMaxHeight()) {
                // 竖线 (时间线) - 降低不透明度使其更淡
                Box(
                        modifier =
                                Modifier.width(3.dp)
                                        .fillMaxHeight()
                                        .align(Alignment.Center)
                                        .background(
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.25f
                                                        ),
                                                shape = RoundedCornerShape(1.5.dp)
                                        )
                )

                // 开始节点 (顶部) - 更小的圆点且降低不透明度
                Box(
                        modifier =
                                Modifier.size(6.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        ),
                                                shape = CircleShape
                                        )
                )

                // 结束节点 (底部) - 更小的圆点且降低不透明度
                Box(
                        modifier =
                                Modifier.size(6.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        ),
                                                shape = CircleShape
                                        )
                                        .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = CircleShape
                                        )
                )
            }

            // 在Box中渲染该计划的所有段落
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                composables.forEach { composable -> composable() }
            }
        }
    }
}

/** 统一的计划显示组件 - 同时处理计划项和计划更新 */
@Composable
private fun UnifiedPlanDisplay(id: String, status: String, content: String, isUpdate: Boolean) {
    when (status.lowercase()) {
        "todo" -> {
            if (!isUpdate) { // Only show TODO items for plan items, not updates
                // TODO plans: Display in a smaller size
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(vertical = 2.dp) // 减少垂直间距
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp) // 减少垂直间距
                ) {
                    // 靠左对齐的计划开始标记，减少左内边距
                    Box(
                            modifier = Modifier.padding(start = 8.dp, end = 16.dp) // 减少左侧内边距
                    ) {
                        Card(
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.15f
                                                        )
                                        ),
                                // 删除边框
                                ) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Plan in progress",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // 当有内容时只显示内容，否则显示"计划 #X 执行中"
                                if (content.isNotBlank()) {
                                    // 直接显示内容，使用与标题相同的样式
                                    Text(
                                            text = content,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    // 没有内容时显示默认标题
                                    Text(
                                            text = "计划 #$id 执行中",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Empty update - don't show anything
            }
        }
        "completed", "done" -> {
            // For plan items, show a horizontal line with content in gray
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 横线代替计划完成卡片 - 移除左侧圆点
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 横线 - 横跨整个宽度
                        Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                thickness = 1.5.dp
                        )
                    }

                    // 直接在线上方显示内容（灰色文字）
                    if (content.isNotBlank() && content != "Completed" && content != "Done") {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
        "failed", "cancelled" -> {
            // Failed and cancelled plans should have same visual treatment as completed
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 横线代替状态卡片 - 移除左侧圆点
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 确定线的颜色
                        val lineColor =
                                when (status.lowercase()) {
                                    "failed" ->
                                            Color(0xFFC62828)
                                                    .copy(alpha = 0.25f) // Dark red with opacity
                                    else ->
                                            Color(0xFF757575)
                                                    .copy(alpha = 0.25f) // Gray with opacity
                                }

                        // 横线 - 横跨整个宽度
                        Divider(
                                modifier = Modifier.weight(1f),
                                color = lineColor,
                                thickness = 1.5.dp
                        )
                    }

                    // 状态文本 (红色表示失败，灰色表示取消)
                    val statusText =
                            when (status.lowercase()) {
                                "failed" -> "计划失败"
                                else -> "计划取消"
                            }

                    val textColor =
                            when (status.lowercase()) {
                                "failed" -> Color(0xFFC62828).copy(alpha = 0.7f) // Dark red
                                else -> Color(0xFF757575).copy(alpha = 0.7f) // Gray
                            }

                    Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            modifier = Modifier.padding(top = 2.dp, start = 16.dp),
                            fontWeight = FontWeight.Medium
                    )

                    // 显示内容（如果有）
                    if (content.isNotBlank()) {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
        else -> {
            // For other statuses (未分类的其他状态), use horizontal line as well
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 横线代替状态卡片 - 移除左侧圆点
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 横线 - 横跨整个宽度
                        Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                thickness = 1.5.dp
                        )
                    }

                    // 显示状态文本
                    Text(
                            text = "计划: ${status.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp, start = 16.dp),
                            fontWeight = FontWeight.Medium
                    )

                    // 显示内容（如果有）
                    if (content.isNotBlank()) {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

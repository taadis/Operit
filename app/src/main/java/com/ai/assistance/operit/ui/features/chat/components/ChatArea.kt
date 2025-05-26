package com.ai.assistance.operit.ui.features.chat.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.times
import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment

@Composable
fun ChatArea(
        chatHistory: List<ChatMessage>,
        listState: LazyListState,
        aiReferences: List<AiReference> = emptyList(),
        planItems: List<PlanItem> = emptyList(),
        enablePlanning: Boolean = false,
        toolProgress: ToolExecutionProgress,
        isLoading: Boolean,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        hasBackgroundImage: Boolean = false,
        modifier: Modifier = Modifier,
        isEditMode: Boolean = false,
        onSelectMessageToEdit: ((Int, ChatMessage) -> Unit)? = null
) {
        // 创建一个基于当前聊天内容的键，用于在聊天切换时重置内部状态
        val chatContentKey =
                remember(chatHistory) {
                        // 使用第一条消息的内容哈希或时间戳作为键，如果没有消息则使用随机值
                        chatHistory.firstOrNull()?.content?.hashCode()?.toString()
                                ?: System.currentTimeMillis().toString()
                }

        // 使用键重置存储计划活动状态的缓存
        val activePlanCache = remember(chatContentKey) { mutableMapOf<String, Boolean>() }

        // 缓存已处理的消息，避免每次重组时重新计算
        val processedMessages = remember(chatHistory) { chatHistory.withIndex().toList() }

        Column(modifier = modifier) {
                // 移除References display

                // Plan display when planning is enabled and there are plan items
                if (enablePlanning && planItems.isNotEmpty()) {
                        PlanGraphDisplay(planItems = planItems, modifier = Modifier.fillMaxWidth())
                } else {
                        // 删除全部不必要的诊断日志
                }

                // Tool progress bar
                ToolProgressBar(toolProgress = toolProgress, modifier = Modifier.fillMaxWidth())

                // Chat messages list
                LazyColumn(
                        state = listState,
                        modifier =
                                Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        // Only set transparent background when we actually have a
                                        // background image
                                        .background(
                                                if (hasBackgroundImage) Color.Transparent
                                                else Color.Transparent
                                        )
                ) {
                        items(
                                items = processedMessages,
                                // 修改键生成逻辑，使其更稳定
                                key = { (index, message) ->
                                        if (message.sender == "think") {
                                                // 对于思考消息，只使用"think"和索引作为键，不使用内容哈希
                                                "$chatContentKey-think-$index"
                                        } else {
                                                // 使用时间戳和索引作为键，这样更稳定
                                                "$chatContentKey-${message.timestamp}-$index"
                                        }
                                }
                        ) { (index, message) ->
                                // 使用key包装每个消息项，以便在特定消息变化时只重组该项
                                key(message.timestamp, message.content.hashCode()) {
                                        MessageItem(
                                                index = index,
                                                message = message,
                                                chatHistory = chatHistory,
                                                activePlanCache = activePlanCache,
                                                userMessageColor = userMessageColor,
                                                aiMessageColor = aiMessageColor,
                                                userTextColor = userTextColor,
                                                aiTextColor = aiTextColor,
                                                systemMessageColor = systemMessageColor,
                                                systemTextColor = systemTextColor,
                                                thinkingBackgroundColor = thinkingBackgroundColor,
                                                thinkingTextColor = thinkingTextColor,
                                                isEditMode = isEditMode,
                                                onSelectMessageToEdit = onSelectMessageToEdit
                                        )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                        }
                }

                // Loading indicator
                if (isLoading) {
                        Box(
                                modifier =
                                        Modifier.align(Alignment.CenterHorizontally)
                                                .padding(vertical = 8.dp)
                        ) {
                                Card(
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.8f)
                                                ),
                                        elevation =
                                                CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                        Text(
                                                text = "正在处理...",
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        ),
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                        }
                }
        }
}

/**
 * 判断用户消息是否应该被包装
 *
 * @param chatHistory 聊天历史
 * @param currentIndex 当前消息的索引
 * @return 如果用户消息应该被包装，返回true
 */
private fun shouldWrapUserMessage(
        chatHistory: List<ChatMessage>,
        currentIndex: Int,
        activePlanCache: MutableMap<String, Boolean>
): Boolean {
        // 如果是第一条消息，不需要包装
        if (currentIndex == 0) return false

        // 从当前位置向前查找，找到最近的AI消息
        var i = currentIndex - 1

        while (i >= 0) {
                val message = chatHistory[i]
                if (message.sender == "ai") {
                        // 检查这条AI消息是否有活动计划
                        if (hasActivePlan(message.content, activePlanCache)) {
                                return true
                        }
                        break
                }
                i--
        }
        return false
}

/** 检查消息内容中是否有活动计划（in_progress但未completed） */
private fun hasActivePlan(content: String, activePlanCache: MutableMap<String, Boolean>): Boolean {
        // 使用消息内容的哈希作为缓存键
        val cacheKey = content.hashCode().toString()

        // 检查缓存中是否已存在结果
        if (activePlanCache.containsKey(cacheKey)) {
                return activePlanCache[cacheKey] ?: false
        }

        val contentSegments = MessageContentParser.parseContent(content, true)

        var foundInProgress = false
        var foundCompleted = false

        // 检查是否有in_progress状态的计划标记
        for (segment in contentSegments) {
                when (segment) {
                        is ContentSegment.PlanItem, is ContentSegment.PlanUpdate -> {
                                val status =
                                        when (segment) {
                                                is ContentSegment.PlanItem -> segment.status
                                                is ContentSegment.PlanUpdate -> segment.status
                                                else -> ""
                                        }
                                Log.d("ChatArea", "Plan status found: $status")

                                if (status.lowercase() == "in_progress") {
                                        foundInProgress = true
                                        foundCompleted = false
                                } else if (status.lowercase() == "completed" ||
                                                status.lowercase() == "done"
                                ) {
                                        foundCompleted = true
                                        foundInProgress = false
                                }
                        }
                        else -> {}
                }
        }

        // 如果找到in_progress但没有completed，表示有活动计划
        val hasActive = foundInProgress && !foundCompleted

        // 将结果存入缓存
        activePlanCache[cacheKey] = hasActive

        Log.d(
                "ChatArea",
                "Has active plan: $hasActive (inProgress=$foundInProgress, completed=$foundCompleted)"
        )
        return hasActive
}

/** 单个消息项组件 将消息渲染逻辑提取到单独的组件，减少重组范围 */
@Composable
private fun MessageItem(
        index: Int,
        message: ChatMessage,
        chatHistory: List<ChatMessage>,
        activePlanCache: MutableMap<String, Boolean>,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        isEditMode: Boolean,
        onSelectMessageToEdit: ((Int, ChatMessage) -> Unit)?
) {
        // 判断当前消息类型
        val isUserMessage = message.sender == "user"

        // 编辑模式下为消息添加点击功能
        val messageModifier =
                if (isEditMode && message.sender != "think") {
                        Modifier.clickable { onSelectMessageToEdit?.invoke(index, message) }
                } else {
                        Modifier
                }

        if (isUserMessage) {
                // 对于用户消息，检查之前的AI消息是否有活动计划
                val shouldWrapUserMessage =
                        shouldWrapUserMessage(chatHistory, index, activePlanCache)

                if (shouldWrapUserMessage) {
                        // 包装用户消息，采用与计划相同的时间线样式
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                                .then(messageModifier)
                        ) {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.Top
                                ) {
                                        // 左侧时间线区域
                                        Box(modifier = Modifier.width(30.dp).fillMaxHeight()) {
                                                // 竖线
                                                Box(
                                                        modifier =
                                                                Modifier.width(3.dp)
                                                                        .fillMaxHeight()
                                                                        .align(Alignment.Center)
                                                                        .background(
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.25f
                                                                                                ),
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                1.5.dp
                                                                                        )
                                                                        )
                                                )

                                                // 节点 - 更小的圆点
                                                Box(
                                                        modifier =
                                                                Modifier.size(6.dp)
                                                                        .align(Alignment.TopCenter)
                                                                        .offset(y = 10.dp)
                                                                        .background(
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                ),
                                                                                shape = CircleShape
                                                                        )
                                                )
                                        }

                                        // 用户消息内容
                                        Box(
                                                modifier =
                                                        Modifier.weight(1f)
                                                                .padding(
                                                                        start = 8.dp,
                                                                        end = 16.dp,
                                                                        top = 4.dp,
                                                                        bottom = 4.dp
                                                                )
                                        ) {
                                                CursorStyleChatMessage(
                                                        message = message,
                                                        userMessageColor = userMessageColor,
                                                        aiMessageColor = aiMessageColor,
                                                        userTextColor = userTextColor,
                                                        aiTextColor = aiTextColor,
                                                        systemMessageColor = systemMessageColor,
                                                        systemTextColor = systemTextColor,
                                                        thinkingBackgroundColor =
                                                                thinkingBackgroundColor,
                                                        thinkingTextColor = thinkingTextColor,
                                                        supportToolMarkup = true,
                                                        initialThinkingExpanded = true
                                                )
                                        }
                                }
                        }
                } else {
                        // 正常显示用户消息
                        Box(modifier = messageModifier) {
                                CursorStyleChatMessage(
                                        message = message,
                                        userMessageColor = userMessageColor,
                                        aiMessageColor = aiMessageColor,
                                        userTextColor = userTextColor,
                                        aiTextColor = aiTextColor,
                                        systemMessageColor = systemMessageColor,
                                        systemTextColor = systemTextColor,
                                        thinkingBackgroundColor = thinkingBackgroundColor,
                                        thinkingTextColor = thinkingTextColor,
                                        supportToolMarkup = true,
                                        initialThinkingExpanded = true
                                )

                                // 在编辑模式下显示编辑指示器
                                if (isEditMode) {
                                        Box(
                                                modifier =
                                                        Modifier.align(Alignment.TopEnd)
                                                                .padding(end = 20.dp, top = 6.dp)
                                                                .size(32.dp)
                                                                .clickable(
                                                                        interactionSource =
                                                                                remember {
                                                                                        MutableInteractionSource()
                                                                                },
                                                                        indication =
                                                                                rememberRipple(
                                                                                        bounded =
                                                                                                false,
                                                                                        radius =
                                                                                                20.dp,
                                                                                        color =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary
                                                                                )
                                                                ) {
                                                                        onSelectMessageToEdit
                                                                                ?.invoke(
                                                                                        index,
                                                                                        message
                                                                                )
                                                                }
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "编辑此消息",
                                                        modifier =
                                                                Modifier.align(Alignment.Center)
                                                                        .size(16.dp),
                                                        tint =
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.8f)
                                                )
                                        }
                                }
                        }
                }
        } else {
                // 正常显示AI消息
                Box(modifier = messageModifier) {
                        CursorStyleChatMessage(
                                message = message,
                                userMessageColor = userMessageColor,
                                aiMessageColor = aiMessageColor,
                                userTextColor = userTextColor,
                                aiTextColor = aiTextColor,
                                systemMessageColor = systemMessageColor,
                                systemTextColor = systemTextColor,
                                thinkingBackgroundColor = thinkingBackgroundColor,
                                thinkingTextColor = thinkingTextColor,
                                supportToolMarkup = true,
                                initialThinkingExpanded = true
                        )

                        // 在编辑模式下显示编辑指示器（仅对AI消息）
                        if (isEditMode && message.sender == "ai") {
                                Box(
                                        modifier =
                                                Modifier.align(Alignment.TopEnd)
                                                        .padding(end = 20.dp, top = 0.dp)
                                                        .size(32.dp)
                                                        .clickable(
                                                                interactionSource =
                                                                        remember {
                                                                                MutableInteractionSource()
                                                                        },
                                                                indication =
                                                                        rememberRipple(
                                                                                bounded = false,
                                                                                radius = 20.dp,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                        )
                                                        ) {
                                                                onSelectMessageToEdit?.invoke(
                                                                        index,
                                                                        message
                                                                )
                                                        }
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "编辑此消息",
                                                modifier =
                                                        Modifier.align(Alignment.Center)
                                                                .size(16.dp),
                                                tint =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.8f
                                                        )
                                        )
                                }
                        }
                }
        }
}

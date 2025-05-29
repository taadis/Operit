package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.util.Log
import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 消息批处理工具类 负责缓冲和批量处理消息，减少频繁更新 */
class MessageBatchProcessor(
        private val coroutineScope: CoroutineScope,
        private val processInterval: Long = 500L, // 默认处理间隔时间
        private val safetyInterval: Long = 2000L, // 安全间隔时间，确保缓存内容最终会被输出
        private val onProcessMessage: (ChatMessage) -> Unit // 消息处理回调
) {
    companion object {
        private const val TAG = "MessageBatchProcessor"
    }

    // 消息处理状态
    private data class MessageState(
            var content: String = "", // 当前内容
            var batchedContent: String = "", // 批处理缓冲内容
            var lastUpdateTime: Long = 0, // 上次更新时间
            var updateJob: Job? = null, // 更新任务
            var safetyJob: Job? = null, // 安全任务，确保最终会输出
            var lastMessage: ChatMessage? = null // 上一条消息引用
    )

    // 各类消息的处理状态
    private val messageStates = mutableMapOf<String, MessageState>()

    /**
     * 处理消息
     * @param sender 发送者类型
     * @param content 消息内容
     */
    fun processMessage(sender: String, content: String) {
        val currentTime = System.currentTimeMillis()

        // 获取或创建该类型消息的状态
        val state = messageStates.getOrPut(sender) { MessageState() }

        // 存储最新内容
        state.batchedContent = content

        // 如果没有活跃的更新任务或距离上次更新超过间隔，创建新任务
        if (state.updateJob == null || currentTime - state.lastUpdateTime >= processInterval) {
            // 取消现有任务
            state.updateJob?.cancel()

            // 创建新任务
            state.updateJob =
                    coroutineScope.launch {
                        state.lastUpdateTime = System.currentTimeMillis()
                        appendMessage(sender, content)
                        delay(processInterval)
                        state.updateJob = null
                    }
        }

        // 设置安全定时器，确保内容最终会被输出
        // 只有当没有安全任务或最后一次更新时间过去太久时才创建新的安全任务
        if (state.safetyJob == null) {
            state.safetyJob =
                    coroutineScope.launch {
                        delay(safetyInterval)
                        // 如果还有未处理的内容，强制处理
                        if (state.batchedContent.isNotEmpty()) {
                            Log.d(TAG, "安全定时器触发: 处理 $sender 缓存内容")
                            appendMessage(sender, state.batchedContent)
                        }
                        state.safetyJob = null
                    }
        }
    }

    /**
     * 添加消息
     * @param sender 发送者类型
     * @param newContent 新内容
     */
    private fun appendMessage(sender: String, newContent: String) {
        // 如果没有内容，直接返回
        if (newContent.isEmpty()) return

        val state = messageStates[sender] ?: return

        // 准备要使用的内容
        val contentToUse =
                if (state.batchedContent.isNotEmpty()) {
                    Log.d(TAG, "使用 $sender 批处理内容: 内容长度=${state.batchedContent.length}")
                    val content = state.batchedContent
                    state.batchedContent = ""
                    content
                } else {
                    Log.d(TAG, "使用 $sender 新内容: 内容长度=${newContent.length}")
                    newContent
                }

        // 创建或更新消息
        val timestamp = state.lastMessage?.timestamp ?: System.currentTimeMillis()
        val message = ChatMessage(sender = sender, content = contentToUse, timestamp = timestamp)

        // 保存当前消息引用
        state.lastMessage = message

        // 回调处理消息
        onProcessMessage(message)
    }

    /** 完成处理 处理所有剩余的批处理内容 */
    fun completeProcessing() {
        // 取消所有更新任务
        messageStates.values.forEach { state ->
            state.updateJob?.cancel()
            state.updateJob = null
            state.safetyJob?.cancel()
            state.safetyJob = null

            // 处理剩余的批处理内容
            if (state.batchedContent.isNotBlank()) {
                val sender = messageStates.entries.find { it.value == state }?.key ?: return@forEach
                appendMessage(sender, state.batchedContent)
                state.batchedContent = ""
            }
        }
    }

    /** 取消处理 清空所有状态 */
    fun cancelProcessing() {
        messageStates.values.forEach { state ->
            state.updateJob?.cancel()
            state.updateJob = null
            state.safetyJob?.cancel()
            state.safetyJob = null
            state.batchedContent = ""
            state.lastUpdateTime = 0
            state.lastMessage = null
        }
    }

    /**
     * 重置特定类型消息的状态
     * @param sender 发送者类型
     */
    fun resetMessageState(sender: String) {
        messageStates[sender]?.let { state ->
            state.updateJob?.cancel()
            state.updateJob = null
            state.safetyJob?.cancel()
            state.safetyJob = null
            state.batchedContent = ""
            state.lastUpdateTime = 0
            state.lastMessage = null
        }
    }
}

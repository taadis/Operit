package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState as EnhancedInputProcessingState
import com.ai.assistance.operit.util.NetworkUtils
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.share
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 委托类，负责处理消息处理相关功能 */
class MessageProcessingDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val getChatHistory: () -> List<ChatMessage>,
        private val getMemory: (Boolean) -> List<Pair<String, String>>,
        private val addMessageToChat: (ChatMessage) -> Unit,
        private val updateChatStatistics: () -> Unit,
        private val saveCurrentChat: () -> Unit,
        private val showErrorMessage: (String) -> Unit,
        private val updateChatTitle: (String) -> Unit,
        private val onStreamComplete: () -> Unit
) {
    companion object {
        private const val TAG = "MessageProcessingDelegate"
    }

    private val _userMessage = MutableStateFlow("")
    val userMessage: StateFlow<String> = _userMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isProcessingInput = MutableStateFlow(false)
    val isProcessingInput: StateFlow<Boolean> = _isProcessingInput.asStateFlow()

    private val _inputProcessingMessage = MutableStateFlow("")
    val inputProcessingMessage: StateFlow<String> = _inputProcessingMessage.asStateFlow()

    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottomEvent = _scrollToBottomEvent.asSharedFlow()

    // 当前活跃的AI响应流
    private var currentResponseStream: SharedStream<String>? = null

    // 获取当前活跃的AI响应流
    fun getCurrentResponseStream(): SharedStream<String>? = currentResponseStream

    init {
        Log.d(TAG, "MessageProcessingDelegate初始化: 创建滚动事件流")
    }

    fun updateUserMessage(message: String) {
        _userMessage.value = message
    }

    fun sendUserMessage(chatId: String? = null) {
        sendUserMessage(emptyList(), chatId)
    }

    fun scrollToBottom() {
        _scrollToBottomEvent.tryEmit(Unit)
    }

    fun sendUserMessage(attachments: List<AttachmentInfo> = emptyList(), chatId: String? = null) {
        if (_userMessage.value.isBlank() && attachments.isEmpty()) return
        if (_isLoading.value) return

        val messageText = _userMessage.value.trim()
        _userMessage.value = ""
        _isLoading.value = true
        _isProcessingInput.value = true
        _inputProcessingMessage.value = "正在处理消息..."

        // 检查这是否是聊天中的第一条用户消息
        val isFirstMessage = getChatHistory().none { it.sender == "user" || it.sender == "ai" }
        if (isFirstMessage) {
            val newTitle =
                    when {
                        messageText.isNotBlank() -> messageText
                        attachments.isNotEmpty() -> attachments.first().fileName
                        else -> "新对话"
                    }
            updateChatTitle(newTitle)
        }

        Log.d(TAG, "开始处理用户消息：附件数量=${attachments.size}")

        val finalMessage = buildFinalMessage(messageText, attachments)
        addMessageToChat(ChatMessage(sender = "user", content = finalMessage))

        viewModelScope.launch(Dispatchers.IO) {
            lateinit var aiMessage: ChatMessage
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    withContext(Dispatchers.Main) { showErrorMessage("网络连接不可用") }
                    _isLoading.value = false
                    _isProcessingInput.value = false
                    return@launch
                }

                val service =
                        getEnhancedAiService()
                                ?: run {
                                    withContext(Dispatchers.Main) { showErrorMessage("AI服务未初始化") }
                                    _isLoading.value = false
                                    _isProcessingInput.value = false
                                    return@launch
                                }

                val history = getMemory(true)

                val startTime = System.currentTimeMillis()

                val deferred = CompletableDeferred<Unit>()
                val responseStream = service.sendMessage(finalMessage, history, chatId)

                // 将字符串流共享，以便多个收集器可以使用
                val sharedCharStream =
                        responseStream.share(
                                scope = viewModelScope,
                                replay = 0, // 不重放历史消息
                                onComplete = {
                                    deferred.complete(Unit)
                                    Log.d(
                                            TAG,
                                            "共享流完成，耗时: ${System.currentTimeMillis() - startTime}ms"
                                    )
                                    currentResponseStream = null // 清除本地引用
                                    onStreamComplete() // 通知外部流已完成
                                }
                        )

                // 更新当前响应流，使其可以被其他组件（如悬浮窗）访问
                currentResponseStream = sharedCharStream

                aiMessage = ChatMessage(sender = "ai", contentStream = sharedCharStream)
                Log.d(
                        TAG,
                        "创建带流的AI消息, stream is null: ${aiMessage.contentStream == null}, timestamp: ${aiMessage.timestamp}"
                )

                withContext(Dispatchers.Main) { addMessageToChat(aiMessage) }

                // 启动一个独立的协程来收集流内容并持续更新数据库
                viewModelScope.launch(Dispatchers.IO) {
                    val contentBuilder = StringBuilder()
                    sharedCharStream.collect { chunk ->
                        contentBuilder.append(chunk)
                        val content = contentBuilder.toString()
                        val updatedMessage = aiMessage.copy(content = content)
                        // 防止后续读取不到
                        aiMessage.content = content
                        addMessageToChat(updatedMessage)
                        _scrollToBottomEvent.tryEmit(Unit)
                    }
                }

                // 等待流完成，以便finally块可以正确执行来更新UI状态
                deferred.await()

                Log.d(TAG, "AI响应处理完成，总耗时: ${System.currentTimeMillis() - startTime}ms")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "消息发送被取消")
                    throw e
                }
                Log.e(TAG, "发送消息时出错", e)
                withContext(Dispatchers.Main) { showErrorMessage("发送消息失败: ${e.message}") }
            } finally {
                // 修改为使用 try-catch 来检查变量是否已初始化，而不是使用 ::var.isInitialized
                try {
                    // 尝试访问 aiMessage，如果未初始化会抛出 UninitializedPropertyAccessException
                    val finalContent = aiMessage.content
                    val finalMessage = aiMessage.copy(content = finalContent, contentStream = null)
                    withContext(Dispatchers.Main) { addMessageToChat(finalMessage) }
                } catch (e: UninitializedPropertyAccessException) {
                    // aiMessage 未初始化，忽略清理步骤
                    Log.d(TAG, "AI消息未初始化，跳过流清理步骤")
                }

                // 添加一个短暂的延迟，以确保UI有足够的时间来渲染最后一个数据块
                // 这有助于解决因竞态条件导致的UI内容（如状态标签）有时无法显示的问题
                withContext(Dispatchers.IO) { delay(100) }
                withContext(Dispatchers.Main) {
                    // 状态现在由 EnhancedAIService 的 inputProcessingState 控制，这里不再重置
                    // _isLoading.value = false
                    // _isProcessingInput.value = false

                    // 即使流处理完成，也需要保存一次聊天记录
                    updateChatStatistics()
                    saveCurrentChat()
                }
            }
        }
    }

    private fun buildFinalMessage(messageText: String, attachments: List<AttachmentInfo>): String {
        if (attachments.isEmpty()) return messageText

        val attachmentTexts =
                attachments.joinToString(" ") { attachment ->
                    "<attachment " +
                            "id=\"${attachment.filePath}\" " +
                            "filename=\"${attachment.fileName}\" " +
                            "type=\"${attachment.mimeType}\" " +
                            (if (attachment.fileSize > 0) "size=\"${attachment.fileSize}\" "
                            else "") +
                            (if (attachment.content.isNotEmpty())
                                    "content=\"${attachment.content}\" "
                            else "") +
                            "/>"
                }
        val result = if (messageText.isBlank()) attachmentTexts else "$messageText $attachmentTexts"
        return result
    }

    fun cancelCurrentMessage() {
        viewModelScope.launch {
            _isLoading.value = false
            _isProcessingInput.value = false
            _inputProcessingMessage.value = ""

            withContext(Dispatchers.IO) {
                getEnhancedAiService()?.cancelConversation()
                saveCurrentChat()
            }
        }
    }

    fun setInputProcessingState(isProcessing: Boolean, message: String) {
        _isProcessingInput.value = isProcessing
        _inputProcessingMessage.value = message
    }

    /**
     * 处理来自 EnhancedAIService 的输入处理状态
     * @param state 输入处理状态
     */
    fun handleInputProcessingState(state: EnhancedInputProcessingState) {
        viewModelScope.launch(Dispatchers.Main) {
            when (state) {
                is EnhancedInputProcessingState.Idle -> {
                    _isLoading.value = false
                    _isProcessingInput.value = false
                    _inputProcessingMessage.value = ""
                }
                is EnhancedInputProcessingState.Processing -> {
                    _isLoading.value = true
                    _isProcessingInput.value = true
                    _inputProcessingMessage.value = state.message
                }
                is EnhancedInputProcessingState.Connecting -> {
                    _isLoading.value = true
                    _isProcessingInput.value = true
                    _inputProcessingMessage.value = state.message
                }
                is EnhancedInputProcessingState.Receiving -> {
                    _isLoading.value = true
                    _isProcessingInput.value = true
                    _inputProcessingMessage.value = state.message
                }
                is EnhancedInputProcessingState.Completed -> {
                    _isLoading.value = false
                    _isProcessingInput.value = false
                    _inputProcessingMessage.value = ""
                }
                is EnhancedInputProcessingState.Error -> {
                    showErrorMessage(state.message)
                    _isLoading.value = false
                    _isProcessingInput.value = false
                    _inputProcessingMessage.value = ""
                }
            }
        }
    }
}

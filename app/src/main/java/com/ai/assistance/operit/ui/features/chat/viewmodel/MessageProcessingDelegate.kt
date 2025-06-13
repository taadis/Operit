package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.NetworkUtils
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
        private val updateChatTitle: (String) -> Unit
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

    fun updateUserMessage(message: String) {
        _userMessage.value = message
    }

    fun sendUserMessage(chatId: String? = null) {
        sendUserMessage(emptyList(), chatId)
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

        Log.d(
                TAG,
                "【消息处理】开始处理用户消息：文本长度=${messageText.length}，附件数量=${attachments.size}，聊天ID=${chatId ?: "无"}"
        )

        val finalMessage = buildFinalMessage(messageText, attachments)
        Log.d(TAG, "【消息处理】构建最终消息完成, 长度=${finalMessage.length}, 添加到聊天界面")
        addMessageToChat(ChatMessage(sender = "user", content = finalMessage))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "【消息处理】检查网络连接状态")
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    Log.e(TAG, "【消息处理】网络连接不可用，中止处理")
                    withContext(Dispatchers.Main) { showErrorMessage("网络连接不可用") }
                    return@launch
                }
                Log.d(TAG, "【消息处理】网络连接正常，继续处理")

                Log.d(TAG, "【消息处理】获取增强AI服务实例")
                val service =
                        getEnhancedAiService()
                                ?: run {
                                    Log.e(TAG, "【消息处理】AI服务未初始化，中止处理")
                                    withContext(Dispatchers.Main) { showErrorMessage("AI服务未初始化") }
                                    return@launch
                                }
                Log.d(TAG, "【消息处理】成功获取AI服务实例")

                Log.d(TAG, "【消息处理】准备获取聊天历史记录")
                val history = getMemory(true)
                Log.d(TAG, "【消息处理】已获取聊天历史记录，条目数: ${history.size}")

                Log.d(TAG, "【消息处理】开始调用AI服务的sendMessage方法")
                val startTime = System.currentTimeMillis()

                val deferred = CompletableDeferred<Unit>()
                val responseStream = service.sendMessage(finalMessage, history, chatId)

                Log.d(TAG, "【消息处理】已获得响应流，准备处理")

                // 将字符串流共享，以便多个收集器可以使用
                Log.d(TAG, "【消息处理】创建共享流，并设置完成回调")
                val sharedCharStream =
                        responseStream.share(
                                scope = viewModelScope,
                                onComplete = {
                                    deferred.complete(Unit)
                                    Log.d(TAG, "【消息处理】共享流 onComplete 回调被触发")
                                }
                        )

                val aiMessage = ChatMessage(sender = "ai", contentStream = sharedCharStream)
                Log.d(TAG, "【消息处理】创建AI消息对象 (timestamp: ${aiMessage.timestamp})，准备添加到UI")

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "【消息处理】添加初始AI消息到聊天界面")
                    addMessageToChat(aiMessage)
                }

                // 启动一个独立的协程来收集流内容并持续更新数据库
                viewModelScope.launch(Dispatchers.IO) {
                    Log.d(TAG, "【消息处理-持久化】启动流式保存协程 (timestamp: ${aiMessage.timestamp})")
                    val contentBuilder = StringBuilder()
                    sharedCharStream.collect { chunk ->
                        contentBuilder.append(chunk)
                        val content = contentBuilder.toString()
                        val updatedMessage = aiMessage.copy(content = content)
                        //防止后续读取不到
                        aiMessage.content = content
                        addMessageToChat(updatedMessage)
                        _scrollToBottomEvent.tryEmit(Unit)
                    }
                    Log.d(TAG, "【消息处理-持久化】流式保存协程收集完成 (timestamp: ${aiMessage.timestamp})")
                }

                // 等待流完成，以便finally块可以正确执行来更新UI状态
                Log.d(TAG, "【消息处理】等待AI响应流处理完成...")
                deferred.await()

                Log.d(TAG, "【消息处理】AI响应流处理完成")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "【消息处理】消息发送被取消")
                    throw e
                }
                Log.e(TAG, "【消息处理】发送消息时出错", e)
                withContext(Dispatchers.Main) { showErrorMessage("发送消息失败: ${e.message}") }
            } finally {
                // 添加一个短暂的延迟，以确保UI有足够的时间来渲染最后一个数据块
                // 这有助于解决因竞态条件导致的UI内容（如状态标签）有时无法显示的问题
                withContext(Dispatchers.IO) { delay(100) }
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "【消息处理】重置处理状态标志")
                    _isLoading.value = false
                    _isProcessingInput.value = false

                    // 即使流处理完成，也需要保存一次聊天记录
                    Log.d(TAG, "【消息处理】更新聊天统计信息并保存当前聊天")
                    updateChatStatistics()
                    saveCurrentChat()
                }
            }
        }
    }

    private fun buildFinalMessage(messageText: String, attachments: List<AttachmentInfo>): String {
        if (attachments.isEmpty()) return messageText

        Log.d(TAG, "【消息处理】构建带附件的消息，文本长度=${messageText.length}，附件数量=${attachments.size}")
        val attachmentTexts =
                attachments.joinToString(" ") { attachment ->
                    Log.d(
                            TAG,
                            "【消息处理】处理附件: ${attachment.fileName}, 类型: ${attachment.mimeType}, 大小: ${attachment.fileSize}"
                    )
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
        Log.d(TAG, "【消息处理】最终消息构建完成，总长度=${result.length}")
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
}

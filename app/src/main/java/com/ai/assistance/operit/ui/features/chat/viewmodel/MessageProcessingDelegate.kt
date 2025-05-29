package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 委托类，负责处理消息处理相关功能 */
class MessageProcessingDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val getShowThinking: () -> Boolean,
        private val getChatHistory: () -> List<ChatMessage>,
        private val getMemory: (Boolean) -> List<Pair<String, String>>,
        private val addMessageToChat: (ChatMessage) -> Unit,
        private val updateChatStatistics: () -> Unit,
        private val saveCurrentChat: () -> Unit,
        private val showErrorMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "MessageProcessingDelegate"
        private const val UI_UPDATE_INTERVAL = 500L // 0.5 seconds in milliseconds
    }

    // State flows
    private val _userMessage = MutableStateFlow("")
    val userMessage: StateFlow<String> = _userMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isProcessingInput = MutableStateFlow(false)
    val isProcessingInput: StateFlow<Boolean> = _isProcessingInput.asStateFlow()

    private val _inputProcessingMessage = MutableStateFlow("")
    val inputProcessingMessage: StateFlow<String> = _inputProcessingMessage.asStateFlow()

    // 消息批处理器
    private val messageBatchProcessor =
            MessageBatchProcessor(
                    coroutineScope = viewModelScope,
                    processInterval = UI_UPDATE_INTERVAL,
                    onProcessMessage = { message -> addMessageToChat(message) }
            )

    /** 更新用户消息 */
    fun updateUserMessage(message: String) {
        _userMessage.value = message
    }

    /** 向AI发送用户消息(无附件版本) 为了保持向后兼容性 */
    fun sendUserMessage() {
        sendUserMessage(emptyList())
    }

    /**
     * 向AI发送用户消息(带附件版本)
     * @param attachments 要发送的附件列表
     */
    fun sendUserMessage(attachments: List<AttachmentInfo> = emptyList()) {
        if (_userMessage.value.isBlank() && attachments.isEmpty()) {
            return
        }

        if (_isLoading.value) {
            return
        }

        // 获取消息文本
        val messageText = _userMessage.value.trim()

        // 清空输入并设置加载状态
        _userMessage.value = ""
        _isLoading.value = true

        // 确保输入处理状态可见 - 修复进度条不显示问题
        _isProcessingInput.value = true
        _inputProcessingMessage.value = "正在处理消息..."

        // 如果有附件，将其添加到消息中
        val finalMessage =
                if (attachments.isNotEmpty()) {
                    val attachmentTexts =
                            attachments.joinToString(" ") { attachment ->
                                val attachmentRef = StringBuilder("<attachment ")
                                attachmentRef.append("id=\"${attachment.filePath}\" ")
                                attachmentRef.append("filename=\"${attachment.fileName}\" ")
                                attachmentRef.append("type=\"${attachment.mimeType}\" ")

                                // 添加大小属性
                                if (attachment.fileSize > 0) {
                                    attachmentRef.append("size=\"${attachment.fileSize}\" ")
                                }

                                // 添加内容属性（如果存在）
                                if (attachment.content.isNotEmpty()) {
                                    attachmentRef.append("content=\"${attachment.content}\" ")
                                }

                                attachmentRef.append("/>")
                                attachmentRef.toString()
                            }

                    // 根据用户消息是否为空，决定如何组合最终消息
                    if (messageText.isBlank()) {
                        // 如果用户没有输入消息，直接使用附件引用
                        attachmentTexts
                    } else {
                        // 否则，将用户消息和附件引用组合在一起
                        "$messageText $attachmentTexts"
                    }
                } else {
                    messageText
                }

        // 添加用户消息到聊天历史
        addMessageToChat(ChatMessage(sender = "user", content = finalMessage))

        // 使用viewModelScope启动协程
        viewModelScope.launch {
            try {
                // 检查网络连接
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    showErrorMessage("网络连接不可用，请检查网络设置")
                    _isLoading.value = false
                    return@launch
                }

                // 获取聊天历史记录
                val history = getMemory(true)

                // 获取AI服务
                val service =
                        getEnhancedAiService()
                                ?: run {
                                    showErrorMessage("AI服务未初始化")
                                    _isLoading.value = false
                                    return@launch
                                }

                // 发送消息到AI服务
                service.sendMessage(
                        message = finalMessage,
                        onPartialResponse = { content, thinking ->
                            handlePartialResponse(content, thinking)
                        },
                        chatHistory = history,
                        onComplete = { handleResponseComplete() }
                )
            } catch (e: Exception) {
                Log.e(TAG, "发送消息时出错", e)
                showErrorMessage("发送消息失败: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    /** 处理AI部分响应 */
    private fun handlePartialResponse(content: String, thinking: String?) {
        try {
            // 如果不再加载状态，跳过响应处理
            if (!_isLoading.value) {
                Log.d(TAG, "已取消加载，跳过响应处理")
                return
            }

            // 处理思考消息
            if (thinking != null && getShowThinking()) {
                messageBatchProcessor.processMessage("think", thinking)
            }

            // 处理AI响应内容
            if (content.isNotEmpty()) {
                messageBatchProcessor.processMessage("ai", content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理AI响应时发生未处理错误", e)
            showErrorMessage("处理AI响应时发生错误: ${e.message}")
        }
    }

    /** 处理AI响应完成 */
    private fun handleResponseComplete() {
        // 完成所有批处理消息
        messageBatchProcessor.completeProcessing()

        // 重置加载状态
        _isLoading.value = false

        // 更新统计数据并保存
        updateChatStatistics()
        saveCurrentChat()
    }

    /** 取消当前对话 */
    fun cancelCurrentMessage() {
        viewModelScope.launch {
            // 取消所有批处理消息
            messageBatchProcessor.cancelProcessing()

            // 重置状态
            _isLoading.value = false
            _isProcessingInput.value = false
            _inputProcessingMessage.value = ""

            // 取消当前的AI响应
            try {
                getEnhancedAiService()?.cancelConversation()
                Log.d(TAG, "成功取消AI对话")
            } catch (e: Exception) {
                Log.e(TAG, "取消对话时发生错误", e)
                showErrorMessage("取消对话时发生错误: ${e.message}")
            }

            // 保存当前对话
            saveCurrentChat()

            Log.d(TAG, "取消流程完成")
        }
    }

    /** 收集输入处理状态 */
    fun setupInputProcessingStateCollection() {
        viewModelScope.launch {
            getEnhancedAiService()?.inputProcessingState?.collect { state ->
                when (state) {
                    InputProcessingState.Idle -> {
                        _isProcessingInput.value = false
                        _inputProcessingMessage.value = ""
                    }
                    is InputProcessingState.Processing -> {
                        _isProcessingInput.value = true
                        _inputProcessingMessage.value = state.message
                    }
                    is InputProcessingState.Connecting -> {
                        _isProcessingInput.value = true
                        _inputProcessingMessage.value = state.message
                    }
                    is InputProcessingState.Receiving -> {
                        _isProcessingInput.value = true
                        _inputProcessingMessage.value = state.message
                    }
                    InputProcessingState.Completed -> {
                        _isProcessingInput.value = false
                    }
                    is InputProcessingState.Error -> {
                        _isProcessingInput.value = false
                        _inputProcessingMessage.value = "错误: ${state.message}"

                        // 关键修复: 将服务层错误传递到错误弹窗
                        showErrorMessage(state.message)
                    }
                }
            }
        }
    }

    /** 手动设置输入处理状态 用于在附件处理等操作过程中显示进度条 */
    fun setInputProcessingState(isProcessing: Boolean, message: String = "") {
        _isProcessingInput.value = isProcessing
        _inputProcessingMessage.value = message
    }

    /** 设置输入处理状态（新版本） 接受ToolExecutionState和AITool类型的参数，用于与工具执行状态集成 */
    fun setInputProcessingState(state: ToolExecutionState, tool: AITool? = null) {
        when (state) {
            ToolExecutionState.IDLE -> {
                _isProcessingInput.value = false
                _inputProcessingMessage.value = ""
            }
            ToolExecutionState.COMPLETED -> {
                _isProcessingInput.value = false
                _inputProcessingMessage.value = "已完成"
            }
            else -> {
                _isProcessingInput.value = true
                _inputProcessingMessage.value =
                        when (state) {
                            ToolExecutionState.EXTRACTING -> "正在解析工具..."
                            ToolExecutionState.EXECUTING -> "正在执行工具: ${tool?.name ?: ""}"
                            ToolExecutionState.FAILED -> "工具执行失败"
                            else -> "处理中..."
                        }
            }
        }
    }
}

package com.ai.assistance.operit.ui.features.chat.viewmodel

import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 委托类，负责管理UI状态相关功能 */
class UiStateDelegate {
    // UI状态
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _popupMessage = MutableStateFlow<String?>(null)
    val popupMessage: StateFlow<String?> = _popupMessage.asStateFlow()

    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent: StateFlow<String?> = _toastEvent.asStateFlow()

    private val _toolProgress =
            MutableStateFlow(ToolExecutionProgress(state = ToolExecutionState.IDLE))
    val toolProgress: StateFlow<ToolExecutionProgress> = _toolProgress.asStateFlow()

    private val _aiReferences = MutableStateFlow<List<AiReference>>(emptyList())
    val aiReferences: StateFlow<List<AiReference>> = _aiReferences.asStateFlow()

    private val _masterPermissionLevel = MutableStateFlow(PermissionLevel.ASK)
    val masterPermissionLevel: StateFlow<PermissionLevel> = _masterPermissionLevel.asStateFlow()

    // 聊天统计信息
    private val _contextWindowSize = MutableStateFlow(0)
    val contextWindowSize: StateFlow<Int> = _contextWindowSize.asStateFlow()

    private val _inputTokenCount = MutableStateFlow(0)
    val inputTokenCount: StateFlow<Int> = _inputTokenCount.asStateFlow()

    private val _outputTokenCount = MutableStateFlow(0)
    val outputTokenCount: StateFlow<Int> = _outputTokenCount.asStateFlow()

    /** 显示错误消息 */
    fun showErrorMessage(message: String) {
        _errorMessage.value = message
    }

    /** 清除错误消息 */
    fun clearError() {
        _errorMessage.value = null
    }

    /** 显示弹出消息 */
    fun showPopupMessage(message: String) {
        _popupMessage.value = message
    }

    /** 清除弹出消息 */
    fun clearPopupMessage() {
        _popupMessage.value = null
    }

    /** 显示Toast消息 */
    fun showToast(message: String) {
        _toastEvent.value = message
    }

    /** 清除Toast消息 */
    fun clearToastEvent() {
        _toastEvent.value = null
    }

    /** 更新工具执行进度 */
    fun updateToolProgress(progress: ToolExecutionProgress) {
        _toolProgress.value = progress
    }

    /** 更新AI引用 */
    fun updateAiReferences(references: List<AiReference>) {
        _aiReferences.value = references
    }

    /** 更新主权限级别 */
    fun updateMasterPermissionLevel(level: PermissionLevel) {
        _masterPermissionLevel.value = level
    }

    /** 更新聊天统计信息 */
    fun updateChatStatistics(contextSize: Int, inputTokens: Int, outputTokens: Int) {
        _contextWindowSize.value = contextSize
        _inputTokenCount.value = inputTokens
        _outputTokenCount.value = outputTokens
    }
}

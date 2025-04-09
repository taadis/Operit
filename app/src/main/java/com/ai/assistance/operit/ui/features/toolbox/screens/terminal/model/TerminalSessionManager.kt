package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * 终端会话管理器
 * 管理多个终端会话，全局通用单例，在应用内切换界面时保持状态
 */
object TerminalSessionManager {
    // 所有会话列表
    private val _sessions = mutableStateListOf<TerminalSession>()
    val sessions: SnapshotStateList<TerminalSession> = _sessions
    
    // 当前活动会话ID
    private val _activeSessionId = mutableStateOf<String?>(null)
    val activeSessionId = _activeSessionId
    
    /**
     * 创建新会话
     */
    fun createSession(name: String = "Session ${_sessions.size + 1}"): TerminalSession {
        val session = TerminalSession(name = name)
        _sessions.add(session)
        
        // 如果是第一个会话，自动设为活动会话
        if (_sessions.size == 1) {
            _activeSessionId.value = session.id
        }
        
        return session
    }
    
    /**
     * 关闭会话
     */
    fun closeSession(sessionId: String) {
        val sessionIndex = _sessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex != -1) {
            _sessions.removeAt(sessionIndex)
            
            // 如果关闭的是当前活动会话，选择新的活动会话
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = if (_sessions.isNotEmpty()) {
                    // 选择前一个会话，如果没有则选择第一个会话
                    val newIndex = (sessionIndex - 1).coerceAtLeast(0)
                    _sessions[newIndex].id
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * 切换活动会话
     */
    fun switchSession(sessionId: String) {
        if (_sessions.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
        }
    }
    
    /**
     * 获取活动会话
     */
    fun getActiveSession(): TerminalSession? {
        return _activeSessionId.value?.let { id ->
            _sessions.find { it.id == id }
        }
    }
    
    /**
     * 重命名会话
     */
    fun renameSession(sessionId: String, newName: String) {
        val session = _sessions.find { it.id == sessionId }
        session?.let {
            val index = _sessions.indexOf(it)
            if (index != -1) {
                // 直接修改会话名称
                it.name = newName
            }
        }
    }
    
    /**
     * 获取会话数量
     */
    fun getSessionCount(): Int {
        return _sessions.size
    }
    
    /**
     * 检查会话是否支持流式输出
     * 在当前实现中，所有会话都支持流式输出
     */
    fun isStreamingSupported(): Boolean {
        return true
    }
} 
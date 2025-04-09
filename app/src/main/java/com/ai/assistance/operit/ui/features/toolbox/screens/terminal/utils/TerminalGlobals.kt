package com.ai.assistance.operit.ui.features.terminal.utils

import com.ai.assistance.operit.ui.features.terminal.model.TerminalSessionManager

/**
 * 全局终端工具类
 * 提供对全局终端会话管理器和相关功能的快捷访问
 */
object TerminalGlobals {
    /**
     * 获取全局会话管理器
     * 其他界面可通过此方法获取会话管理器
     */
    fun getSessionManager() = TerminalSessionManager
    
    /**
     * 获取活动终端会话
     * 如果没有活动会话，自动创建一个
     */
    fun getOrCreateActiveSession(sessionName: String = "Terminal") = 
        TerminalSessionManager.getActiveSession() ?: TerminalSessionManager.createSession(sessionName)
    
    /**
     * 在新的终端会话中执行命令
     * @param command 要执行的命令
     * @param sessionName 新会话的名称
     * @return 新创建的会话ID
     */
    fun executeInNewSession(command: String, sessionName: String): String {
        val session = TerminalSessionManager.createSession(sessionName)
        // 注意：这里不直接执行命令，因为执行命令需要Context和CoroutineScope
        // 调用者需要自行处理命令执行
        return session.id
    }
    
    /**
     * 切换到指定会话
     */
    fun switchToSession(sessionId: String) {
        TerminalSessionManager.switchSession(sessionId)
    }
} 
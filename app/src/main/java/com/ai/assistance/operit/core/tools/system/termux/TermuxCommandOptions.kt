package com.ai.assistance.operit.core.tools.system.termux

/** 执行Termux命令的高级配置选项 */
data class TermuxCommandOptions(
        val executable: String = "/data/data/com.termux/files/usr/bin/bash",
        val arguments: Array<String> = arrayOf(),
        val workingDirectory: String = "/data/data/com.termux/files/home",
        val background: Boolean = true,
        val sessionAction: String = SessionAction.ACTION_NEW_SESSION,
        val label: String? = null,
        val description: String? = null,
        val stdin: String? = null,
        val user: String? = null,
        val timeout: Long = DEFAULT_TIMEOUT,
        val timeoutAsError: Boolean = false // 控制超时是否被视为错误
) {
    companion object {
        const val DEFAULT_TIMEOUT = 360000L // 默认超时时间：360秒
        const val INACTIVITY_TIMEOUT = 100000L // 无活动超时时间：100秒
    }

    /** 会话操作类型 */
    object SessionAction {
        const val ACTION_NEW_SESSION = "0"
        const val ACTION_USE_CURRENT_SESSION = "1"
        const val ACTION_SWITCH_TO_NEW_SESSION = "2"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TermuxCommandOptions

        if (executable != other.executable) return false
        if (!arguments.contentEquals(other.arguments)) return false
        if (workingDirectory != other.workingDirectory) return false
        if (background != other.background) return false
        if (sessionAction != other.sessionAction) return false
        if (label != other.label) return false
        if (description != other.description) return false
        if (stdin != other.stdin) return false
        if (user != other.user) return false
        if (timeout != other.timeout) return false
        if (timeoutAsError != other.timeoutAsError) return false

        return true
    }

    override fun hashCode(): Int {
        var result = executable.hashCode()
        result = 31 * result + arguments.contentHashCode()
        result = 31 * result + workingDirectory.hashCode()
        result = 31 * result + background.hashCode()
        result = 31 * result + sessionAction.hashCode()
        result = 31 * result + (label?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (stdin?.hashCode() ?: 0)
        result = 31 * result + (user?.hashCode() ?: 0)
        result = 31 * result + timeout.hashCode()
        result = 31 * result + timeoutAsError.hashCode()
        return result
    }
}

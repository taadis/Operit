package com.ai.assistance.operit.core.tools.defaultTool.admin

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.debugger.DebuggerShellToolExecutor

/** 管理员级别的Shell工具执行器，继承调试版本 */
open class AdminShellToolExecutor(context: Context) : DebuggerShellToolExecutor(context) {
    // 当前阶段不添加新功能，仅继承调试实现
}

package com.ai.assistance.operit.core.tools.defaultTool.debugger

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.accessbility.AccessibilityShellToolExecutor

/** 调试级别的Shell工具执行器，继承无障碍版本 */
open class DebuggerShellToolExecutor(context: Context) : AccessibilityShellToolExecutor(context) {
    // 当前阶段不添加新功能，仅继承无障碍实现
}

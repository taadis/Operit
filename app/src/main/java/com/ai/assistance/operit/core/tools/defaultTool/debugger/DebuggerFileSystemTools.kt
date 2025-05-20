package com.ai.assistance.operit.core.tools.defaultTool.debugger

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.accessbility.AccessibilityFileSystemTools

/** 调试者级别的文件系统工具，继承无障碍级别 */
open class DebuggerFileSystemTools(context: Context) : AccessibilityFileSystemTools(context) {
    // 当前阶段不添加新功能，仅继承无障碍级别实现
}

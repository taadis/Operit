package com.ai.assistance.operit.core.tools.defaultTool.root

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.admin.AdminShellToolExecutor

/** Root级别的Shell工具执行器，继承管理员版本 */
open class RootShellToolExecutor(context: Context) : AdminShellToolExecutor(context) {
    // 当前阶段不添加新功能，仅继承管理员实现
}

package com.ai.assistance.operit.ui.features.demo.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxInstaller
import com.ai.assistance.operit.ui.features.demo.utils.getTermuxConfigStatus
import kotlinx.coroutines.delay

private const val TAG = "TermuxAuthHelper"

/**
 * 启动Termux应用
 */
suspend fun startTermux(context: Context, updateTermuxRunning: (Boolean) -> Unit) {
    // 先检查Termux是否运行
    val isRunning = checkTermuxRunning(context)
    updateTermuxRunning(isRunning)

    if (!isRunning) {
        // 简单地尝试启动Termux
        if (TermuxInstaller.openTermux(context)) {
            Toast.makeText(context, "已发送启动Termux命令", Toast.LENGTH_SHORT).show()
            // 给Termux一点启动时间
            delay(1000)
            // 再次检查状态，更新UI
            val newRunningState = checkTermuxRunning(context)
            updateTermuxRunning(newRunningState)
        } else {
            Toast.makeText(context, "启动Termux失败", Toast.LENGTH_SHORT).show()
        }
    } else {
        // Termux已经在运行中，显示通知
        Toast.makeText(context, "Termux已在运行中", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 授权Termux
 */
suspend fun authorizeTermux(
    context: Context,
    updateOutputText: (String) -> Unit,
    updateTermuxAuthorized: (Boolean) -> Unit,
    updateTermuxRunning: (Boolean) -> Unit,
    currentOutputText: String
): String {
    var outputText = currentOutputText
    outputText = "欢迎使用Termux配置工具\n开始授权Termux..."
    updateOutputText(outputText)

    // 先检查Termux是否在运行
    val isRunning = checkTermuxRunning(context)
    updateTermuxRunning(isRunning)
    
    if (!isRunning) {
        outputText += "\nTermux未运行，将尝试启动Termux..."
        updateOutputText(outputText)
        
        if (TermuxInstaller.openTermux(context)) {
            outputText += "\n已启动Termux，请稍等..."
            updateOutputText(outputText)
            // 简单等待1秒钟
            delay(1000)
            // 再次检查状态
            val newRunningState = checkTermuxRunning(context)
            updateTermuxRunning(newRunningState)
            
            if (!newRunningState) {
                outputText += "\nTermux似乎未成功启动，请手动启动Termux后再尝试"
                updateOutputText(outputText)
                Toast.makeText(context, "请手动启动Termux", Toast.LENGTH_SHORT).show()
                return outputText
            }
            outputText += "\nTermux已启动"
            updateOutputText(outputText)
        } else {
            outputText += "\nTermux启动失败，请确保Termux已正确安装"
            updateOutputText(outputText)
            Toast.makeText(context, "启动Termux失败", Toast.LENGTH_SHORT).show()
            return outputText
        }
    }

    try {
        // 先检查Shizuku权限
        if (!ShizukuAuthorizer.hasShizukuPermission()) {
            outputText += "\n缺少Shizuku API_V23权限，无法执行授权操作"
            outputText += "\n请先点击Shizuku卡片，完成Shizuku设置和授权"
            updateOutputText(outputText)
            delay(3000) // 给用户时间阅读错误信息
            return outputText
        }

        // 再检查应用清单中是否声明了RUN_COMMAND权限
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val declaredPermissions = packageInfo.requestedPermissions ?: emptyArray()
        val hasPermissionDeclared = declaredPermissions.any { it == "com.termux.permission.RUN_COMMAND" }

        if (!hasPermissionDeclared) {
            outputText += "\n应用清单中未声明Termux RUN_COMMAND权限"
            outputText += "\n请联系开发者修复此问题"
            updateOutputText(outputText)
            delay(3000) // 给用户时间阅读错误信息
            return outputText
        }

        outputText += "\n开始授予Termux权限..."
        updateOutputText(outputText)

        val success = TermuxAuthorizer.grantAllTermuxPermissions(context)

        if (success) {
            outputText += "\nTermux授权成功！"
            updateTermuxAuthorized(true)
            updateOutputText(outputText)
            Toast.makeText(context, "成功授权Termux", Toast.LENGTH_SHORT).show()

            // 授权成功后检查各项配置状态
            outputText += "\n正在检查Termux配置状态..."
            updateOutputText(outputText)
            delay(1000) // 给授权一点时间完成

            // 先读取持久化状态
            val isFullyConfigured = getTermuxConfigStatus(context)

            if (isFullyConfigured) {
                outputText += "\n检测到Termux已完成所有配置（从持久化记录）"
                updateOutputText(outputText)
            } else {
                outputText += "\n检查完成，请点击相应按钮进行配置"
                updateOutputText(outputText)
            }
        } else {
            outputText += "\nTermux授权失败，请确认以下事项:"
            outputText += "\n1. Shizuku服务是否正常运行"
            outputText += "\n2. 应用是否在AndroidManifest中声明了com.termux.permission.RUN_COMMAND权限"
            outputText += "\n3. Termux应用是否已正确安装"
            updateOutputText(outputText)
            Toast.makeText(context, "授权Termux失败，请检查Termux设置", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "授权Termux出错: ${e.message}")
        outputText += "\n授权Termux出错: ${e.message}"
        updateOutputText(outputText)
    }
    
    return outputText
} 
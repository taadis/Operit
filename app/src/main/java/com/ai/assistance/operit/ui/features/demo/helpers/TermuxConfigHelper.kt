package com.ai.assistance.operit.ui.features.demo.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.TermuxCommandExecutor
import com.ai.assistance.operit.core.tools.system.termux.TermuxUtils
import com.ai.assistance.operit.ui.features.demo.utils.getTermuxConfigStatus
import com.ai.assistance.operit.ui.features.demo.utils.saveTermuxConfigStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "TermuxConfigHelper"

/**
 * 执行Termux命令的辅助函数
 */
suspend fun executeTermuxCommand(context: Context, command: String): AndroidShellExecutor.CommandResult {
    val result = TermuxCommandExecutor.executeCommand(
            context = context,
            command = command,
            autoAuthorize = true
    )

    // 给命令一些执行时间
    delay(500)

    return result
}

/**
 * 检查Termux是否在运行
 */
fun checkTermuxRunning(context: Context): Boolean {
    return TermuxUtils.isTermuxRunning(context)
}

/**
 * 检查Termux是否获得电池优化豁免
 */
suspend fun checkTermuxBatteryOptimization(context: Context): Boolean {
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val exempted = powerManager.isIgnoringBatteryOptimizations("com.termux")
        Log.d(TAG, "检查Termux电池优化豁免: $exempted")
        exempted
    } catch (e: Exception) {
        Log.e(TAG, "检查Termux电池优化豁免错误: ${e.message}")
        false
    }
}

/**
 * 检查清华源是否已启用
 */
suspend fun checkTunaSourceEnabled(context: Context): Boolean {
    return try {
        // 更可靠的检测方式: 直接查看文件内容
        val result = executeTermuxCommand(context, "cat ${'$'}PREFIX/etc/apt/sources.list")

        // 检查文件内容中是否包含清华源URL
        val containsTuna = result.success && 
                (result.stdout.contains("mirrors.tuna.tsinghua.edu.cn") || 
                result.stdout.contains("tsinghua.edu.cn"))

        Log.d(TAG, "源文件内容: ${result.stdout}")
        Log.d(TAG, "检查清华源: $containsTuna")

        containsTuna
    } catch (e: Exception) {
        Log.e(TAG, "检查清华源错误: ${e.message}")
        false
    }
}

/**
 * 检查Python是否安装
 */
suspend fun checkPythonInstalled(context: Context): Boolean {
    return try {
        val result = executeTermuxCommand(context, "command -v python3")
        val installed = result.success && result.stdout.contains("python3")
        Log.d(TAG, "检查Python: $installed")
        installed
    } catch (e: Exception) {
        Log.e(TAG, "检查Python错误: ${e.message}")
        false
    }
}

/**
 * 检查Node.js是否安装
 */
suspend fun checkNodeInstalled(context: Context): Boolean {
    return try {
        val result = executeTermuxCommand(context, "command -v node")
        val installed = result.success && result.stdout.contains("node")
        Log.d(TAG, "检查Node.js: $installed")
        installed
    } catch (e: Exception) {
        Log.e(TAG, "检查Node.js错误: ${e.message}")
        false
    }
}

/**
 * 检查UV是否安装
 */
suspend fun checkUvInstalled(context: Context): Boolean {
    return try {
        val result = executeTermuxCommand(context, "command -v uv")
        val installed = result.success && result.stdout.contains("uv")
        Log.d(TAG, "检查UV: $installed")
        installed
    } catch (e: Exception) {
        Log.e(TAG, "检查UV错误: ${e.message}")
        false
    }
}

/**
 * 检查已安装的所有组件并更新配置状态
 */
suspend fun checkInstalledComponents(
    context: Context,
    isTermuxRunning: Boolean,
    isTermuxAuthorized: Boolean,
    isTunaSourceEnabled: Boolean,
    isPythonInstalled: Boolean,
    isUvInstalled: Boolean,
    isNodeInstalled: Boolean,
    isTermuxBatteryOptimizationExempted: Boolean,
    updateConfigStatus: (Boolean) -> Unit,
    updateSourceStatus: (Boolean) -> Unit,
    updatePythonStatus: (Boolean) -> Unit,
    updateUvStatus: (Boolean) -> Unit,
    updateNodeStatus: (Boolean) -> Unit,
    updateBatteryStatus: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        // 只在Termux已安装且已授权时才检查
        if (isTermuxAuthorized) {
            // 检查各个组件状态
            val batteryExemption = checkTermuxBatteryOptimization(context)
            val tunaEnabled = checkTunaSourceEnabled(context)
            val pythonInstalled = checkPythonInstalled(context)
            val uvInstalled = checkUvInstalled(context)
            val nodeInstalled = checkNodeInstalled(context)

            // 更新状态
            updateSourceStatus(tunaEnabled)
            updatePythonStatus(pythonInstalled)
            updateUvStatus(uvInstalled)
            updateNodeStatus(nodeInstalled)
            updateBatteryStatus(batteryExemption)

            // 检查完成后，更新持久化状态
            val allConfigured = batteryExemption && tunaEnabled && pythonInstalled && uvInstalled && nodeInstalled

            val currentSavedStatus = getTermuxConfigStatus(context)
            
            if (allConfigured && !currentSavedStatus) {
                // 如果所有组件都已配置好但持久化状态未更新，保存持久化状态
                saveTermuxConfigStatus(context, true)
                updateConfigStatus(true)
                Log.d(TAG, "所有Termux组件配置完成，保存持久化状态")
            } else if (!allConfigured && currentSavedStatus) {
                // 如果之前认为配置完成，但现在检测到有组件未配置，重置状态
                saveTermuxConfigStatus(context, false)
                updateConfigStatus(false)
                Log.d(
                    TAG,
                    "检测到Termux组件配置不完整（电池优化: $batteryExemption, 清华源: $tunaEnabled, " +
                            "Python: $pythonInstalled, UV: $uvInstalled, Node: $nodeInstalled），重置持久化状态"
                )
            }
        }
    }
}

/**
 * 配置清华源
 */
suspend fun configureTunaSource(
    context: Context,
    updateOutputText: (String) -> Unit,
    updateSourceStatus: (Boolean) -> Unit,
    updateConfigStatus: (Boolean) -> Unit,
    isTunaSourceEnabled: Boolean,
    isPythonInstalled: Boolean,
    isUvInstalled: Boolean,
    isNodeInstalled: Boolean,
    currentOutputText: String
): String {
    var outputText = currentOutputText
    try {
        // 备份原有sources.list
        val backupResult = executeTermuxCommand(
            context,
            "cp ${'$'}PREFIX/etc/apt/sources.list ${'$'}PREFIX/etc/apt/sources.list.bak"
        )
        outputText += "\n备份原始软件源配置${if (backupResult.success) "成功" else "失败"}"
        updateOutputText(outputText)

        // 设置清华源 - 使用更明确的方式
        val setTunaSourceResult = executeTermuxCommand(
            context,
            """
            echo "# 清华大学开源软件镜像站 Termux 镜像源
deb https://mirrors.tuna.tsinghua.edu.cn/termux/termux-packages-24 stable main" > ${'$'}PREFIX/etc/apt/sources.list
            """
        )
        outputText += "\n设置清华源${if (setTunaSourceResult.success) "成功" else "失败"}"
        updateOutputText(outputText)

        // 更新软件包列表
        outputText += "\n正在更新软件包列表..."
        updateOutputText(outputText)
        val updateResult = executeTermuxCommand(context, "apt update -y")
        outputText += "\n软件包列表更新${if (updateResult.success) "成功" else "失败"}"
        updateOutputText(outputText)

        if (setTunaSourceResult.success && updateResult.success) {
            updateSourceStatus(true)
            outputText += "\n清华源配置成功！"
            updateOutputText(outputText)
            
            // 重新检查，确保状态正确
            val tunaEnabled = checkTunaSourceEnabled(context)
            updateSourceStatus(tunaEnabled)

            // 检查是否所有组件都已配置完成
            if (tunaEnabled && isPythonInstalled && isNodeInstalled && isUvInstalled) {
                saveTermuxConfigStatus(context, true)
                updateConfigStatus(true)
                outputText += "\n所有Termux组件已配置完成，配置状态已保存！"
                updateOutputText(outputText)
            }

            Toast.makeText(context, "清华源设置成功", Toast.LENGTH_SHORT).show()
        } else {
            outputText += "\n清华源配置失败，正在恢复备份..."
            updateOutputText(outputText)
            // 恢复备份
            executeTermuxCommand(
                context,
                "cp ${'$'}PREFIX/etc/apt/sources.list.bak ${'$'}PREFIX/etc/apt/sources.list"
            )
            executeTermuxCommand(context, "apt update -y")
            Toast.makeText(context, "清华源设置失败", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "配置清华源错误: ${e.message}")
        outputText += "\n配置清华源出错: ${e.message}"
        updateOutputText(outputText)
        Toast.makeText(context, "配置清华源出错", Toast.LENGTH_SHORT).show()
    }
    
    return outputText
}

/**
 * 安装Python环境
 */
suspend fun installPython(
    context: Context,
    updateOutputText: (String) -> Unit,
    updatePythonStatus: (Boolean) -> Unit,
    updateConfigStatus: (Boolean) -> Unit,
    isTunaSourceEnabled: Boolean,
    isPythonInstalled: Boolean,
    isUvInstalled: Boolean,
    isNodeInstalled: Boolean,
    currentOutputText: String
): String {
    var outputText = currentOutputText
    try {
        // 安装Python和pip
        outputText += "\n正在安装Python..."
        updateOutputText(outputText)
        val installPythonResult = executeTermuxCommand(context, "pkg install python -y")
        outputText += "\nPython安装${if (installPythonResult.success) "成功" else "失败"}"
        updateOutputText(outputText)

        if (installPythonResult.success) {
            updatePythonStatus(true)
            outputText += "\nPython环境安装成功！"
            updateOutputText(outputText)

            // 检查是否所有组件都已配置完成
            if (isTunaSourceEnabled && isPythonInstalled && isUvInstalled && isNodeInstalled) {
                saveTermuxConfigStatus(context, true)
                updateConfigStatus(true)
                outputText += "\n所有Termux组件已配置完成，配置状态已保存！"
                updateOutputText(outputText)
            }

            Toast.makeText(context, "Python安装成功", Toast.LENGTH_SHORT).show()
        } else {
            outputText += "\nPython安装失败"
            updateOutputText(outputText)
            Toast.makeText(context, "Python安装失败", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "安装Python环境错误: ${e.message}")
        outputText += "\n安装Python环境出错: ${e.message}"
        updateOutputText(outputText)
        Toast.makeText(context, "安装Python环境出错", Toast.LENGTH_SHORT).show()
    }
    
    return outputText
}

/**
 * 安装UV包管理器
 */
suspend fun installUv(
    context: Context,
    updateOutputText: (String) -> Unit,
    updateUvStatus: (Boolean) -> Unit,
    updateConfigStatus: (Boolean) -> Unit,
    isTunaSourceEnabled: Boolean,
    isPythonInstalled: Boolean,
    isUvInstalled: Boolean,
    isNodeInstalled: Boolean,
    currentOutputText: String
): String {
    var outputText = currentOutputText
    try {
        // 安装UV
        outputText += "\n正在安装UV..."
        updateOutputText(outputText)
        val installUvResult = executeTermuxCommand(context, "pkg install uv -y")
        outputText += "\nUV安装${if (installUvResult.success) "成功" else "失败"}"
        updateOutputText(outputText)

        if (installUvResult.success) {
            updateUvStatus(true)
            outputText += "\nUV包管理器安装成功！"
            updateOutputText(outputText)

            // 检查是否所有组件都已配置完成
            if (isTunaSourceEnabled && isPythonInstalled && isUvInstalled && isNodeInstalled) {
                saveTermuxConfigStatus(context, true)
                updateConfigStatus(true)
                outputText += "\n所有Termux组件已配置完成，配置状态已保存！"
                updateOutputText(outputText)
            }

            Toast.makeText(context, "UV安装成功", Toast.LENGTH_SHORT).show()
        } else {
            outputText += "\nUV安装失败"
            updateOutputText(outputText)
            Toast.makeText(context, "UV安装失败", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "安装UV包管理器错误: ${e.message}")
        outputText += "\n安装UV包管理器出错: ${e.message}"
        updateOutputText(outputText)
        Toast.makeText(context, "安装UV包管理器出错", Toast.LENGTH_SHORT).show()
    }
    
    return outputText
}

/**
 * 安装Node.js环境
 */
suspend fun installNode(
    context: Context,
    updateOutputText: (String) -> Unit,
    updateNodeStatus: (Boolean) -> Unit,
    updateConfigStatus: (Boolean) -> Unit,
    isTunaSourceEnabled: Boolean,
    isPythonInstalled: Boolean,
    isUvInstalled: Boolean,
    isNodeInstalled: Boolean,
    currentOutputText: String
): String {
    var outputText = currentOutputText
    try {
        // 安装Node.js
        outputText += "\n正在安装Node.js..."
        updateOutputText(outputText)
        val installNodeResult = executeTermuxCommand(context, "pkg install nodejs -y")
        outputText += "\nNode.js安装${if (installNodeResult.success) "成功" else "失败"}"
        updateOutputText(outputText)

        if (installNodeResult.success) {
            updateNodeStatus(true)
            outputText += "\nNode.js环境安装成功！"
            updateOutputText(outputText)

            // 检查是否所有组件都已配置完成
            if (isTunaSourceEnabled && isPythonInstalled && isNodeInstalled && isUvInstalled) {
                saveTermuxConfigStatus(context, true)
                updateConfigStatus(true)
                outputText += "\n所有Termux组件已配置完成，配置状态已保存！"
                updateOutputText(outputText)
            }

            Toast.makeText(context, "Node.js安装成功", Toast.LENGTH_SHORT).show()
        } else {
            outputText += "\nNode.js安装失败"
            updateOutputText(outputText)
            Toast.makeText(context, "Node.js安装失败", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "安装Node.js环境错误: ${e.message}")
        outputText += "\n安装Node.js环境出错: ${e.message}"
        updateOutputText(outputText)
        Toast.makeText(context, "安装Node.js环境出错", Toast.LENGTH_SHORT).show()
    }
    
    return outputText
}

/**
 * 请求Termux电池优化豁免
 */
fun requestTermuxBatteryOptimization(context: Context) {
    try {
        // 使用Intent打开Termux的电池优化设置
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:com.termux")
        }
        context.startActivity(intent)
        Toast.makeText(context, "请在系统设置中允许Termux的电池优化豁免", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e(TAG, "请求Termux电池优化豁免错误: ${e.message}")
        Toast.makeText(context, "无法打开Termux电池优化设置", Toast.LENGTH_SHORT).show()
    }
} 
package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * UI操作工具类，封装与系统UI交互的方法
 */
class UIOperationUtils(private val context: Context) {
    private val TAG = "UIOperationUtils"
    
    // Shell命令执行器
    private val shellExecutor = AndroidShellExecutor
    
    /**
     * 使用uiautomator dump获取当前页面的UI层次结构
     */
    suspend fun getUiHierarchyFromUiautomatorDump(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "ui_dump.xml")
                val dumpCommand = "uiautomator dump ${tempFile.absolutePath}"
                
                // 执行uiautomator dump命令
                val result = shellExecutor.executeShellCommand(dumpCommand)
                
                if (!result.success || !tempFile.exists()) {
                    Log.e(TAG, "uiautomator dump失败: ${result.stderr}")
                    return@withContext null
                }
                
                // 读取dump文件内容
                val content = tempFile.readText()
                
                // 删除临时文件
                tempFile.delete()
                
                // 返回内容
                return@withContext content
            } catch (e: IOException) {
                Log.e(TAG, "获取UI层次结构失败", e)
                return@withContext null
            }
        }
    }
    
    /**
     * 点击指定坐标
     */
    suspend fun clickAtLocation(x: Int, y: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val tapCommand = "input tap $x $y"
                val result = shellExecutor.executeShellCommand(tapCommand)
                
                return@withContext result.success
            } catch (e: Exception) {
                Log.e(TAG, "点击失败: ($x, $y)", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 长按指定坐标
     */
    suspend fun longClickAtLocation(x: Int, y: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 使用swipe命令模拟长按（在同一位置停留一段时间）
                val swipeCommand = "input swipe $x $y $x $y 1000"
                val result = shellExecutor.executeShellCommand(swipeCommand)
                
                return@withContext result.success
            } catch (e: Exception) {
                Log.e(TAG, "长按失败: ($x, $y)", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 滑动操作
     */
    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int = 300): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val swipeCommand = "input swipe $startX $startY $endX $endY $duration"
                val result = shellExecutor.executeShellCommand(swipeCommand)
                
                return@withContext result.success
            } catch (e: Exception) {
                Log.e(TAG, "滑动失败", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 输入文本
     */
    suspend fun inputText(text: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 转义特殊字符
                val escapedText = text.replace(" ", "%s")
                    .replace("&", "\\&")
                    .replace("<", "\\<")
                    .replace(">", "\\>")
                    .replace("(", "\\(")
                    .replace(")", "\\)")
                    .replace("|", "\\|")
                    .replace(";", "\\;")
                    .replace("$", "\\$")
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace("[", "\\[")
                    .replace("]", "\\]")
                
                val inputCommand = "input text \"$escapedText\""
                val result = shellExecutor.executeShellCommand(inputCommand)
                
                return@withContext result.success
            } catch (e: Exception) {
                Log.e(TAG, "输入文本失败", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 按下指定键码
     */
    suspend fun pressKey(keyCode: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val keyCommand = "input keyevent $keyCode"
                val result = shellExecutor.executeShellCommand(keyCommand)
                
                return@withContext result.success
            } catch (e: Exception) {
                Log.e(TAG, "按键失败: $keyCode", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 显示点击反馈
     */
    suspend fun showTapFeedback(x: Float, y: Float, duration: Long = 500) {
        // 这个方法实际上会调用应用内的UI叠加层来显示点击反馈
        // 这里需要实现叠加层的显示逻辑，具体取决于您的UI实现
        // 在本示例中，我们简单地记录一个日志
        Log.d(TAG, "显示点击反馈: ($x, $y)")
    }
    
    /**
     * 隐藏点击反馈
     */
    suspend fun hideTapFeedback() {
        // 隐藏之前显示的所有点击反馈
        // 这里需要实现叠加层的隐藏逻辑
        Log.d(TAG, "隐藏点击反馈")
    }
} 
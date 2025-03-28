package com.ai.assistance.operit.tools.defaultTool

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.AdbCommandExecutor
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolResultData
import com.ai.assistance.operit.model.StringResultData
import com.ai.assistance.operit.tools.AppListData
import com.ai.assistance.operit.tools.AppOperationData
import com.ai.assistance.operit.tools.SystemSettingData
import kotlinx.serialization.Serializable

/**
 * 提供系统级操作的工具类
 * 包括系统设置修改、应用安装和卸载等
 * 这些操作需要用户明确授权
 */
class SystemOperationTools(private val context: Context) {
    
    companion object {
        private const val TAG = "SystemOperationTools"
    }
    
    
    
    /**
     * 修改系统设置
     * 支持修改各种系统设置，如音量、亮度等
     */
    suspend fun modifySystemSetting(tool: AITool): ToolResult {
        val setting = tool.parameters.find { it.name == "setting" }?.value ?: ""
        val value = tool.parameters.find { it.name == "value" }?.value ?: ""
        val namespace = tool.parameters.find { it.name == "namespace" }?.value ?: "system"
        
        if (setting.isBlank() || value.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供setting和value参数"
            )
        }
        
        // 判断命名空间是否合法
        val validNamespaces = listOf("system", "secure", "global")
        if (!validNamespaces.contains(namespace)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "命名空间必须是以下之一: ${validNamespaces.joinToString(", ")}"
            )
        }
        
        return try {
            val command = "settings put $namespace $setting $value"
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                val resultData = SystemSettingData(
                    namespace = namespace,
                    setting = setting,
                    value = value
                )
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "设置失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "修改系统设置时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "修改系统设置时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 获取系统设置的当前值
     */
    suspend fun getSystemSetting(tool: AITool): ToolResult {
        val setting = tool.parameters.find { it.name == "setting" }?.value ?: ""
        val namespace = tool.parameters.find { it.name == "namespace" }?.value ?: "system"
        
        if (setting.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供setting参数"
            )
        }
        
        // 判断命名空间是否合法
        val validNamespaces = listOf("system", "secure", "global")
        if (!validNamespaces.contains(namespace)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "命名空间必须是以下之一: ${validNamespaces.joinToString(", ")}"
            )
        }
        
        return try {
            val command = "settings get $namespace $setting"
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                val resultData = SystemSettingData(
                    namespace = namespace,
                    setting = setting,
                    value = result.stdout.trim()
                )
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "获取设置失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取系统设置时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "获取系统设置时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 安装应用程序
     * 需要APK文件的路径
     */
    suspend fun installApp(tool: AITool): ToolResult {
        val apkPath = tool.parameters.find { it.name == "apk_path" }?.value ?: ""
        
        if (apkPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供apk_path参数"
            )
        }
        
        // 检查文件是否存在
        val existsResult = AdbCommandExecutor.executeAdbCommand("test -f $apkPath && echo 'exists' || echo 'not exists'")
        if (existsResult.stdout.trim() != "exists") {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "APK文件不存在: $apkPath"
            )
        }
        
        return try {
            // 使用pm安装应用
            val command = "pm install -r $apkPath"
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success && result.stdout.contains("Success")) {
                val resultData = AppOperationData(
                    operationType = "install",
                    packageName = apkPath,
                    success = true
                )
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "安装失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "安装应用时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "安装应用时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 卸载应用程序
     * 需要提供包名
     */
    suspend fun uninstallApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
        val keepData = tool.parameters.find { it.name == "keep_data" }?.value?.toBoolean() ?: false
        
        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供package_name参数"
            )
        }
        
        // 检查应用是否已安装
        val checkCommand = "pm list packages | grep -c \"$packageName\""
        val checkResult = AdbCommandExecutor.executeAdbCommand(checkCommand)
        
        if (checkResult.stdout.trim() == "0") {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "应用未安装: $packageName"
            )
        }
        
        return try {
            // 卸载应用
            val command = if (keepData) {
                "pm uninstall -k $packageName"
            } else {
                "pm uninstall $packageName"
            }
            
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success && result.stdout.contains("Success")) {
                val details = if (keepData) "(保留数据)" else ""
                val resultData = AppOperationData(
                    operationType = "uninstall",
                    packageName = packageName,
                    success = true,
                    details = details
                )
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "卸载失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "卸载应用时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "卸载应用时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 获取已安装应用列表
     */
    suspend fun listInstalledApps(tool: AITool): ToolResult {
        val systemApps = tool.parameters.find { it.name == "include_system_apps" }?.value?.toBoolean() ?: false
        
        return try {
            val command = if (systemApps) {
                "pm list packages"
            } else {
                "pm list packages -3" // 只显示第三方应用
            }
            
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                // 格式化输出，使其更易读
                val packageList = result.stdout.split("\n")
                    .filter { it.isNotBlank() }
                    .map { it.replace("package:", "") }
                    .sorted()
                
                val resultData = AppListData(
                    includesSystemApps = systemApps,
                    packages = packageList
                )
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "获取应用列表失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取应用列表时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "获取应用列表时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 启动应用程序
     */
    suspend fun startApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
        val activity = tool.parameters.find { it.name == "activity" }?.value ?: ""
        
        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供package_name参数"
            )
        }
        
        return try {
            val command = if (activity.isBlank()) {
                "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
            } else {
                "am start -n $packageName/$activity"
            }
            
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                val details = if (activity.isNotBlank()) "活动: $activity" else ""
                val resultData = AppOperationData(
                    operationType = "start",
                    packageName = packageName,
                    success = true,
                    details = details
                )
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "启动应用失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动应用时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "启动应用时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 停止应用程序
     */
    suspend fun stopApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
        
        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供package_name参数"
            )
        }
        
        return try {
            val command = "am force-stop $packageName"
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                val resultData = AppOperationData(
                    operationType = "stop",
                    packageName = packageName,
                    success = true
                )
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = resultData,
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "停止应用失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止应用时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "停止应用时出错: ${e.message}"
            )
        }
    }
} 
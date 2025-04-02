package com.ai.assistance.operit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import android.os.Handler
import android.os.Looper

/**
 * Termux授权工具类
 */
class TermuxAuthorizer {
    companion object {
        private const val TAG = "TermuxAuthorizer"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_CONFIG_PATH = "/data/data/com.termux/files/home/.termux/termux.properties"
        private const val CACHE_EXPIRY_MS = 100000 // 100秒
        
        // 状态变更监听器
        private val stateChangeListeners = mutableListOf<() -> Unit>()
        private val mainHandler = Handler(Looper.getMainLooper())
        
        /**
         * 添加状态变更监听器
         */
        fun addStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                if (!stateChangeListeners.contains(listener)) {
                    stateChangeListeners.add(listener)
                }
            }
        }
        
        /**
         * 移除状态变更监听器
         */
        fun removeStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                stateChangeListeners.remove(listener)
            }
        }
        
        /**
         * 通知状态变化
         */
        private fun notifyStateChanged() {
            mainHandler.post {
                synchronized(stateChangeListeners) {
                    stateChangeListeners.forEach { it.invoke() }
                }
            }
        }
        
        // 配置状态缓存
        private data class ConfigStatus(val isConfigured: Boolean, val lastCheckTime: Long)
        private val configCache = AtomicReference<ConfigStatus?>(null)
        
        /**
         * 检查Termux配置
         */
        private suspend fun checkTermuxConfig(): Boolean = withContext(Dispatchers.IO) {
            // 检查缓存
            val currentTime = System.currentTimeMillis()
            val cached = configCache.get()
            if (cached != null && (currentTime - cached.lastCheckTime) < CACHE_EXPIRY_MS) {
                return@withContext cached.isConfigured
            }
            
            // 检查配置文件
            val dirCheck = AdbCommandExecutor.executeAdbCommand(
                "run-as com.termux [ -d \"/data/data/com.termux/files/home/.termux\" ] && echo \"exists\""
            )
            
            if (!dirCheck.success || !dirCheck.stdout.contains("exists")) {
                updateCache(false)
                return@withContext false
            }
            
            val fileCheck = AdbCommandExecutor.executeAdbCommand(
                "run-as com.termux [ -f \"$TERMUX_CONFIG_PATH\" ] && echo \"exists\""
            )
            
            if (!fileCheck.success || !fileCheck.stdout.contains("exists")) {
                updateCache(false)
                return@withContext false
            }
            
            val contentCheck = AdbCommandExecutor.executeAdbCommand(
                "run-as com.termux cat \"$TERMUX_CONFIG_PATH\""
            )
            
            val configured = contentCheck.success && contentCheck.stdout.contains("allow-external-apps=true")
            updateCache(configured)
            return@withContext configured
        }
        
        /**
         * 更新缓存
         */
        private fun updateCache(isConfigured: Boolean) {
            val oldStatus = configCache.getAndSet(ConfigStatus(isConfigured, System.currentTimeMillis()))
            if (oldStatus?.isConfigured != isConfigured) {
                notifyStateChanged()
            }
        }
        
        /**
         * 重置缓存
         */
        private fun resetCache() {
            configCache.set(null)
            notifyStateChanged()
            TermuxInstaller.resetInstallCache()
        }
        
        /**
         * 检查Termux是否已授权
         */
        suspend fun isTermuxAuthorized(context: Context): Boolean = withContext(Dispatchers.IO) {
            if (!TermuxInstaller.isTermuxInstalled(context) || 
                !AdbCommandExecutor.isShizukuServiceRunning() || 
                !AdbCommandExecutor.hasShizukuPermission()) {
                return@withContext false
            }
            
            return@withContext checkTermuxConfig()
        }
        
        /**
         * 授权Termux
         */
        suspend fun authorizeTermux(context: Context): Boolean = withContext(Dispatchers.IO) {
            // 必要检查
            if (!TermuxInstaller.isTermuxInstalled(context)) {
                Toast.makeText(context, "请先安装Termux应用", Toast.LENGTH_SHORT).show()
                return@withContext false
            }
            
            if (!AdbCommandExecutor.isShizukuServiceRunning() || !AdbCommandExecutor.hasShizukuPermission()) {
                Toast.makeText(context, "请确保Shizuku已运行并授权", Toast.LENGTH_SHORT).show()
                return@withContext false
            }
            
            // 检查配置状态
            if (checkTermuxConfig()) {
                return@withContext true
            }
            
            // 使用run-as尝试授权
            val runAsResult = configureWithRunAs()
            if (runAsResult) {
                notifyStateChanged()
                return@withContext true
            }
            
            // 使用替代方式尝试授权
            val alternativeResult = configureWithAlternative(context)
            if (alternativeResult) {
                notifyStateChanged()
            }
            return@withContext alternativeResult
        }
        
        /**
         * 使用run-as配置Termux
         */
        private suspend fun configureWithRunAs(): Boolean = withContext(Dispatchers.IO) {
            try {
                // 创建目录
                AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux mkdir -p /data/data/com.termux/files/home/.termux"
                )
                
                // 写入配置
                val success = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux sh -c \"echo 'allow-external-apps=true' > $TERMUX_CONFIG_PATH\""
                ).success
                
                if (!success) {
                    // 备用方式
                    val backupSuccess = AdbCommandExecutor.executeAdbCommand(
                        "run-as com.termux sh -c \"printf 'allow-external-apps=true\\n' > $TERMUX_CONFIG_PATH\""
                    ).success
                    
                    if (!backupSuccess) {
                        return@withContext false
                    }
                }
                
                // 设置权限
                AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux chmod 600 $TERMUX_CONFIG_PATH"
                )
                
                // 重启Termux
                AdbCommandExecutor.executeAdbCommand("am force-stop com.termux")
                
                // 重置缓存
                resetCache()
                
                // 验证配置
                return@withContext checkTermuxConfig()
            } catch (e: Exception) {
                Log.e(TAG, "run-as配置失败: ${e.message}")
                return@withContext false
            }
        }
        
        /**
         * 使用替代方式配置Termux
         */
        private suspend fun configureWithAlternative(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                // 创建外部配置文件
                val externalDir = context.getExternalFilesDir(null)?.absolutePath 
                    ?: "/sdcard/Android/data/${context.packageName}/files"
                val configFile = "$externalDir/termux.properties"
                
                // 写入配置
                AdbCommandExecutor.executeAdbCommand(
                    "echo 'allow-external-apps=true' > $configFile"
                )
                
                // 尝试root复制
                val rootSuccess = AdbCommandExecutor.executeAdbCommand(
                    "su -c 'mkdir -p /data/data/com.termux/files/home/.termux && " +
                    "cp $configFile /data/data/com.termux/files/home/.termux/termux.properties && " +
                    "chmod 600 /data/data/com.termux/files/home/.termux/termux.properties && " +
                    "chown -R `stat -c %u:%g /data/data/com.termux/files/home` /data/data/com.termux/files/home/.termux'"
                ).success
                
                if (rootSuccess) {
                    AdbCommandExecutor.executeAdbCommand("am force-stop com.termux")
                    resetCache()
                    return@withContext true
                }
                
                // 提示手动操作
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "请手动复制配置文件: $configFile 到 ~/.termux/termux.properties",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    val dialog = AlertDialog.Builder(context)
                        .setTitle("需要手动配置")
                        .setMessage(
                            "请在Termux中执行:\n\n" +
                            "1. mkdir -p ~/.termux\n" +
                            "2. echo 'allow-external-apps=true' > ~/.termux/termux.properties\n" +
                            "3. 重启Termux"
                        )
                        .setPositiveButton("打开Termux") { dialog, _ ->
                            dialog.dismiss()
                            TermuxInstaller.openTermux(context)
                        }
                        .setNegativeButton("取消") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                    
                    dialog.show()
                }
                
                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "配置失败: ${e.message}")
                return@withContext false
            }
        }
        
        /**
         * 请求Termux运行命令权限
         */
        suspend fun requestRunCommandPermission(context: Context): Boolean = withContext(Dispatchers.IO) {
            // 尝试自动授权
            val result = authorizeTermux(context)
            if (result) {
                notifyStateChanged()
            }
            return@withContext result
        }
    }
}
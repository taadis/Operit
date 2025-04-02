package com.ai.assistance.operit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 用于管理内置Termux应用的安装
 */
class TermuxInstaller {
    companion object {
        private const val TAG = "TermuxInstaller"
        private const val TERMUX_APK_FILENAME = "termux.apk"
        private const val TERMUX_PACKAGE_NAME = "com.termux"
        
        // 添加监听器列表，用于通知状态变更
        private val stateChangeListeners = mutableListOf<() -> Unit>()
        
        /**
         * 添加状态变更监听器
         */
        fun addStateChangeListener(listener: () -> Unit) {
            stateChangeListeners.add(listener)
        }
        
        /**
         * 移除状态变更监听器
         */
        fun removeStateChangeListener(listener: () -> Unit) {
            stateChangeListeners.remove(listener)
        }
        
        /**
         * 通知所有监听器状态已变更
         */
        private fun notifyStateChanged() {
            stateChangeListeners.forEach { it() }
        }
        
        /**
         * 检查Termux是否已安装
         * @param context Android上下文
         * @return 是否已安装Termux
         */
        fun isTermuxInstalled(context: Context): Boolean {
            try {
                // 方法1: 使用PackageManager直接查询
                val packageInfo = context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
                Log.d(TAG, "Termux is installed (detected via PackageManager)")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // 方法2: 尝试通过Intent查询
                try {
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_LAUNCHER)
                    intent.setPackage(TERMUX_PACKAGE_NAME)
                    val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
                    val isInstalled = resolveInfos.size > 0
                    if (isInstalled) {
                        Log.d(TAG, "Termux is installed (detected via Intent)")
                        return true
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Error checking via Intent: ${e2.message}")
                }
                
                // 方法3: 检查Termux应用目录是否存在
                try {
                    val termuxDataDir = File("/data/data/$TERMUX_PACKAGE_NAME")
                    if (termuxDataDir.exists()) {
                        Log.d(TAG, "Termux is installed (detected via data directory)")
                        return true
                    }
                } catch (e3: Exception) {
                    Log.e(TAG, "Error checking via file system: ${e3.message}")
                }
                
                Log.d(TAG, "Termux is not installed (all detection methods failed)")
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error checking Termux installation: ${e.message}")
                return false
            }
        }
        
        /**
         * 从assets目录复制Termux APK到应用私有目录
         * @param context Android上下文
         * @return 返回目标APK文件对象，如果失败则返回null
         */
        fun extractApkFromAssets(context: Context): File? {
            val apkFile = File(context.cacheDir, TERMUX_APK_FILENAME)
            
            try {
                context.assets.open(TERMUX_APK_FILENAME).use { inputStream ->
                    FileOutputStream(apkFile).use { outputStream ->
                        val buffer = ByteArray(4 * 1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        outputStream.flush()
                    }
                }
                return apkFile
            } catch (e: IOException) {
                Log.e(TAG, "Failed to extract Termux APK from assets", e)
                return null
            }
        }
        
        /**
         * 检查应用私有目录中是否存在提取的APK文件
         * @param context Android上下文
         * @return 是否存在APK文件
         */
        fun isApkExtracted(context: Context): Boolean {
            val apkFile = File(context.cacheDir, TERMUX_APK_FILENAME)
            return apkFile.exists() && apkFile.length() > 0
        }
        
        /**
         * 安装内置的Termux APK
         * @param context Android上下文
         * @return 是否成功启动安装界面
         */
        fun installBundledTermux(context: Context): Boolean {
            // 检查是否已经安装了Termux
            if (isTermuxInstalled(context)) {
                Log.d(TAG, "Termux is already installed")
                return false
            }
            
            try {
                // 从assets目录提取APK
                val apkFile = extractApkFromAssets(context) ?: return false
                
                // 生成APK的URI，考虑文件提供者权限
                val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }
                
                // 创建安装意图
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    // 添加安装完成后的回调
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                
                // 启动安装界面
                context.startActivity(installIntent)
                
                // 安装过程中定期检查安装状态
                Thread {
                    var installed = false
                    for (i in 1..30) { // 最多等待30秒
                        Thread.sleep(1000)
                        if (isTermuxInstalled(context)) {
                            installed = true
                            Log.d(TAG, "Termux installation confirmed after $i seconds")
                            break
                        }
                    }
                    
                    if (installed) {
                        // 通知所有监听器状态已变更
                        notifyStateChanged()
                    }
                }.start()
                
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install bundled Termux", e)
                return false
            }
        }
        
        /**
         * 打开Termux应用
         * @param context Android上下文
         * @return 是否成功打开Termux应用
         */
        fun openTermux(context: Context): Boolean {
            return try {
                val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
                if (intent != null) {
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Termux", e)
                false
            }
        }
    }
} 
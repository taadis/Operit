package com.ai.assistance.operit.tools.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 用于管理内置Shizuku应用的安装
 */
class ShizukuInstaller {
    companion object {
        private const val TAG = "ShizukuInstaller"
        private const val SHIZUKU_APK_FILENAME = "shizuku.apk"
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        
        /**
         * 从assets目录复制Shizuku APK到应用私有目录
         * @param context Android上下文
         * @return 返回目标APK文件对象，如果失败则返回null
         */
        fun extractApkFromAssets(context: Context): File? {
            val apkFile = File(context.cacheDir, SHIZUKU_APK_FILENAME)
            
            try {
                context.assets.open(SHIZUKU_APK_FILENAME).use { inputStream ->
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
                Log.e(TAG, "Failed to extract Shizuku APK from assets", e)
                return null
            }
        }
        
        /**
         * 检查应用私有目录中是否存在提取的APK文件
         * @param context Android上下文
         * @return 是否存在APK文件
         */
        fun isApkExtracted(context: Context): Boolean {
            val apkFile = File(context.cacheDir, SHIZUKU_APK_FILENAME)
            return apkFile.exists() && apkFile.length() > 0
        }
        
        /**
         * 安装内置的Shizuku APK
         * @param context Android上下文
         * @return 是否成功启动安装界面
         */
        fun installBundledShizuku(context: Context): Boolean {
            // 检查是否已经安装了Shizuku
            if (AdbCommandExecutor.isShizukuInstalled(context)) {
                Log.d(TAG, "Shizuku is already installed")
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
                }
                
                // 启动安装界面
                context.startActivity(installIntent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install bundled Shizuku", e)
                return false
            }
        }
        
        /**
         * 获取内置Shizuku APK版本信息
         * @param context Android上下文
         * @return 内置APK的版本名称，如果无法获取则返回"未知"
         */
        fun getBundledShizukuVersion(context: Context): String {
            try {
                // 从assets读取版本信息文件
                val versionInfo = context.assets.open("shizuku_version.txt").use { inputStream ->
                    inputStream.bufferedReader().readText().trim()
                }
                return versionInfo
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get bundled Shizuku version", e)
                return "未知"
            }
        }
    }
} 
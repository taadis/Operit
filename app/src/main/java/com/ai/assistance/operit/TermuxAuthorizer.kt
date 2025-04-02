package com.ai.assistance.operit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.ai.assistance.operit.AdbCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import android.app.AlertDialog

/**
 * 用于给Termux自动授权的类
 * 使用Shizuku的ADB功能来自动配置Termux
 */
class TermuxAuthorizer {
    companion object {
        private const val TAG = "TermuxAuthorizer"
        private const val TERMUX_PACKAGE = "com.termux"
        
        /**
         * 自动授权Termux，使其可以接收外部命令
         * @param context 上下文
         * @return 是否成功授权
         */
        suspend fun authorizeTermux(context: Context): Boolean = withContext(Dispatchers.IO) {
            // 1. 检查权限需求
            val permissionStatus = checkPermissions(context)
            if (!permissionStatus.allGranted) {
                Log.w(TAG, "Missing permissions: ${permissionStatus.missingPermissions.joinToString()}")
                
                // 显示缺少的权限提示
                withContext(Dispatchers.Main) {
                    if (permissionStatus.needRunCommandPermission) {
                        Toast.makeText(context, 
                            "请在设置中授权此应用使用Termux命令权限", 
                            Toast.LENGTH_LONG).show()
                        
                        // 打开应用设置页面
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open app settings", e)
                        }
                        
                        return@withContext false
                    }
                    
                    if (permissionStatus.needDrawOverPermission) {
                        Toast.makeText(context, 
                            "请授予Termux悬浮窗权限，以便命令能自动开始运行", 
                            Toast.LENGTH_LONG).show()
                        
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            intent.data = Uri.parse("package:$TERMUX_PACKAGE")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open overlay settings", e)
                        }
                    } else {
                        // Empty else branch to satisfy linter
                        Log.d(TAG, "No draw over permission needed")
                    }
                }
                
                // 进入授权流程，但是返回false表示尚未完全授权
                if (permissionStatus.needRunCommandPermission) {
                    return@withContext false
                }
            }
            
            // 2. 检查Shizuku是否可用
            if (!AdbCommandExecutor.isShizukuInstalled(context) || 
                !AdbCommandExecutor.isShizukuServiceRunning() || 
                !AdbCommandExecutor.hasShizukuPermission()) {
                Log.e(TAG, "Shizuku not available or not authorized")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "请确保Shizuku已安装、运行并授权", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }
            
            // 3. 检查Termux是否已安装
            if (!TermuxInstaller.isTermuxInstalled(context)) {
                Log.e(TAG, "Termux is not installed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "请先安装Termux应用", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }
            
            try {
                // 4. 检查是否能使用run-as (需要应用是debug版本或设备已root)
                val checkRunAsResult = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux echo 'run-as test'"
                )
                
                val canUseRunAs = checkRunAsResult.success
                Log.d(TAG, "Can use run-as: $canUseRunAs")
                
                // 5. 根据不同方式授权
                val result = if (canUseRunAs) {
                    authorizeWithRunAs(context)
                } else {
                    authorizeWithAlternativeMethods(context)
                }
                
                // 6. 如果成功，检查电池优化
                if (result) {
                    checkBatteryOptimizations(context)
                }
                
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Error authorizing Termux: ${e.message}", e)
                return@withContext false
            }
        }
        
        /**
         * 使用run-as方式授权Termux
         */
        private suspend fun authorizeWithRunAs(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                // 先检查目录是否存在
                val checkDirResult = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux [ -d \"/data/data/com.termux/files/home/.termux\" ] && echo \"exists\" || echo \"not exists\""
                )
                
                // 1. 使用run-as创建.termux目录
                if (!checkDirResult.success || !checkDirResult.stdout.contains("exists")) {
                    Log.d(TAG, ".termux目录不存在，创建目录")
                    val createDirResult = AdbCommandExecutor.executeAdbCommand(
                        "run-as com.termux mkdir -p /data/data/com.termux/files/home/.termux"
                    )
                    
                    if (!createDirResult.success) {
                        Log.e(TAG, "无法创建.termux目录: ${createDirResult.stderr}")
                        return@withContext false
                    }
                }
                
                // 检查文件是否存在
                val checkFileResult = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux [ -f \"/data/data/com.termux/files/home/.termux/termux.properties\" ] && echo \"exists\" || echo \"not exists\""
                )
                
                // 2. 如果文件存在，先检查内容，看是否需要修改
                if (checkFileResult.success && checkFileResult.stdout.contains("exists")) {
                    Log.d(TAG, "termux.properties文件已存在，检查内容")
                    val readFileResult = AdbCommandExecutor.executeAdbCommand(
                        "run-as com.termux cat /data/data/com.termux/files/home/.termux/termux.properties"
                    )
                    
                    if (readFileResult.success && readFileResult.stdout.contains("allow-external-apps=true")) {
                        Log.d(TAG, "配置文件已包含allow-external-apps=true，无需修改")
                        // 确保文件权限正确
                        AdbCommandExecutor.executeAdbCommand(
                            "run-as com.termux chmod 600 /data/data/com.termux/files/home/.termux/termux.properties"
                        )
                        // 重启Termux服务
                        AdbCommandExecutor.executeAdbCommand("am force-stop com.termux")
                        return@withContext true
                    }
                }
                
                // 3. 准备写入内容
                Log.d(TAG, "正在写入termux.properties文件")
                
                // 如果文件不存在或需要修改，创建/覆盖文件
                // 使用多种方法尝试写入，增加成功率
                // 方法1: echo直接写入
                var success = false
                val writeMethod1Result = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux sh -c \"echo 'allow-external-apps=true' > /data/data/com.termux/files/home/.termux/termux.properties\""
                )
                
                if (writeMethod1Result.success) {
                    success = true
                } else {
                    Log.w(TAG, "方法1写入失败: ${writeMethod1Result.stderr}")
                    
                    // 方法2: 使用printf写入
                    val writeMethod2Result = AdbCommandExecutor.executeAdbCommand(
                        "run-as com.termux sh -c \"printf 'allow-external-apps=true\\n' > /data/data/com.termux/files/home/.termux/termux.properties\""
                    )
                    
                    if (writeMethod2Result.success) {
                        success = true
                    } else {
                        Log.w(TAG, "方法2写入失败: ${writeMethod2Result.stderr}")
                    }
                }
                
                if (!success) {
                    Log.e(TAG, "无法写入termux.properties")
                    return@withContext false
                }
                
                // 4. 确保文件权限正确
                val chmodResult = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux chmod 600 /data/data/com.termux/files/home/.termux/termux.properties"
                )
                
                if (!chmodResult.success) {
                    Log.w(TAG, "修改文件权限失败: ${chmodResult.stderr}")
                }
                
                // 5. 重启Termux服务以应用更改
                AdbCommandExecutor.executeAdbCommand("am force-stop com.termux")
                
                // 6. 等待一段时间，让系统有时间应用变更
                delay(1000)
                
                // 7. 验证配置是否已正确写入
                val verifyResult = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux cat /data/data/com.termux/files/home/.termux/termux.properties"
                )
                
                if (!verifyResult.success) {
                    Log.e(TAG, "验证配置失败: ${verifyResult.stderr}")
                    return@withContext false
                }
                
                if (!verifyResult.stdout.contains("allow-external-apps=true")) {
                    Log.e(TAG, "配置文件内容不正确: ${verifyResult.stdout}")
                    return@withContext false
                }
                
                Log.i(TAG, "Termux授权成功完成")
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "授权过程中出错: ${e.message}", e)
                return@withContext false
            }
        }
        
        /**
         * 使用替代方法授权Termux (当run-as不可用时)
         */
        private suspend fun authorizeWithAlternativeMethods(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Trying alternative methods for Termux authorization")
                
                // 1. 尝试使用第三方存储来中转配置文件
                // 创建配置文件在外部存储上
                val externalDir = context.getExternalFilesDir(null)?.absolutePath
                    ?: "/sdcard/Android/data/${context.packageName}/files"
                val configFile = "$externalDir/termux.properties"
                
                // 创建配置文件
                val createFileResult = AdbCommandExecutor.executeAdbCommand(
                    "echo 'allow-external-apps=true' > $configFile"
                )
                
                if (!createFileResult.success) {
                    Log.w(TAG, "Failed to create config in external storage: ${createFileResult.stderr}")
                    return@withContext false
                }
                
                // 修改权限确保可读
                AdbCommandExecutor.executeAdbCommand(
                    "chmod 644 $configFile"
                )
                
                // 尝试通过ADB复制到Termux目录(需要root)
                val rootCopyResult = AdbCommandExecutor.executeAdbCommand(
                    "su -c 'cp $configFile /data/data/com.termux/files/home/.termux/termux.properties'"
                )
                
                if (rootCopyResult.success) {
                    // 使用root权限设置适当的文件所有权
                    AdbCommandExecutor.executeAdbCommand(
                        "su -c 'chown `stat -c %u:%g /data/data/com.termux/files/home` /data/data/com.termux/files/home/.termux/termux.properties'"
                    )
                    
                    // 重启Termux应用
                    AdbCommandExecutor.executeAdbCommand(
                        "am force-stop com.termux"
                    )
                    
                    Log.i(TAG, "Termux authorization completed successfully with root")
                    return@withContext true
                }
                
                // 如果root方法失败，则通知用户手动操作
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, 
                        "已生成Termux配置文件，请手动复制到Termux目录:\n" +
                        "源文件: $configFile\n" +
                        "目标路径: ~/.termux/termux.properties", 
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // 尝试启动Termux应用
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            Log.w(TAG, "Could not get launch intent for Termux")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch Termux", e)
                    }
                }
                
                // 由于这是手动操作，返回false以表示尚未完成自动授权
                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "Error in alternative authorization: ${e.message}", e)
                return@withContext false
            }
        }
        
        /**
         * 检查权限状态
         */
        private fun checkPermissions(context: Context): PermissionStatus {
            val status = PermissionStatus()
            
            // 检查com.termux.permission.RUN_COMMAND权限
            // 在Android 6.0+中，这需要运行时权限，但Termux定义的权限是通过不同机制处理的
            // 由于是UI交互，我们这里只检查权限声明，不做深入检查
            try {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName, 
                    PackageManager.GET_PERMISSIONS
                )
                
                val declaredPermissions = packageInfo.requestedPermissions ?: emptyArray()
                status.needRunCommandPermission = !declaredPermissions.contains("com.termux.permission.RUN_COMMAND")
                
                if (status.needRunCommandPermission) {
                    Log.e(TAG, "应用未在AndroidManifest中声明com.termux.permission.RUN_COMMAND权限")
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查权限声明时出错: ${e.message}", e)
                status.needRunCommandPermission = true
            }
            
            // 检查是否授予了SYSTEM_ALERT_WINDOW权限
            status.needDrawOverPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                !Settings.canDrawOverlays(context)
            } else {
                false
            }
            
            // 检查是否授予了存储权限
            status.needStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                !Environment.isExternalStorageManager()
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            
            return status
        }

        /**
         * 检查是否拥有Termux运行命令权限
         * 注意：Termux的权限检查比较特殊，我们通过尝试执行一个简单命令来检测权限
         */
        suspend fun hasRunCommandPermission(context: Context): Boolean {
            try {
                // 检查应用是否声明了此权限
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName, 
                    PackageManager.GET_PERMISSIONS
                )
                
                val declaredPermissions = packageInfo.requestedPermissions ?: return false
                val hasDeclaration = declaredPermissions.contains("com.termux.permission.RUN_COMMAND")
                
                if (!hasDeclaration) {
                    Log.e(TAG, "应用未在AndroidManifest中声明com.termux.permission.RUN_COMMAND权限")
                    return false
                }
                
                // 检查Termux的.termux/termux.properties文件是否存在并配置正确
                // 这是Termux权限的关键设置
                return hasTermuxAllowExternalAppsConfig(context)
            } catch (e: Exception) {
                Log.e(TAG, "检查Termux运行命令权限时出错: ${e.message}", e)
                return false
            }
        }
        
        /**
         * 检查Termux是否配置了allow-external-apps=true
         */
        private suspend fun hasTermuxAllowExternalAppsConfig(context: Context): Boolean {
            if (!TermuxInstaller.isTermuxInstalled(context)) {
                return false
            }
            
            if (!AdbCommandExecutor.isShizukuInstalled(context) || 
                !AdbCommandExecutor.isShizukuServiceRunning() || 
                !AdbCommandExecutor.hasShizukuPermission()) {
                return false
            }
            
            val checkConfigResult = AdbCommandExecutor.executeAdbCommand(
                "run-as com.termux [ -f \"/data/data/com.termux/files/home/.termux/termux.properties\" ] && echo \"exists\" || echo \"not exists\""
            )
            
            if (checkConfigResult.success && checkConfigResult.stdout.contains("exists")) {
                val readConfigResult = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux cat /data/data/com.termux/files/home/.termux/termux.properties | grep allow-external-apps"
                )
                
                if (readConfigResult.success && readConfigResult.stdout.contains("allow-external-apps=true")) {
                    return true
                }
            }
            
            return false
        }
        
        /**
         * 请求Termux运行命令权限
         * 此方法尝试通过启动Termux应用并引导用户授予权限
         */
        suspend fun requestRunCommandPermission(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                // 首先尝试自动授权
                val authorized = authorizeTermux(context)
                if (authorized && hasRunCommandPermission(context)) {
                    return@withContext true
                }
                
                // 如果自动授权失败，提示用户手动设置
                withContext(Dispatchers.Main) {
                    val builder = AlertDialog.Builder(context)
                    builder.setTitle("需要Termux权限")
                        .setMessage("请在Termux中执行以下操作:\n\n" +
                                "1. 打开Termux应用\n" +
                                "2. 执行命令: mkdir -p ~/.termux\n" +
                                "3. 执行命令: echo 'allow-external-apps=true' > ~/.termux/termux.properties\n" +
                                "4. 重启Termux应用")
                        .setPositiveButton("打开Termux") { dialog, _ ->
                            dialog.dismiss()
                            TermuxInstaller.openTermux(context)
                        }
                        .setNegativeButton("取消") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
                
                return@withContext false
                
            } catch (e: Exception) {
                Log.e(TAG, "请求Termux权限时出错: ${e.message}", e)
                return@withContext false
            }
        }
        
        /**
         * 检查电池优化状态
         */
        private suspend fun checkBatteryOptimizations(context: Context) {
            withContext(Dispatchers.Main) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                
                // 检查Termux是否被排除在电池优化之外
                val isTermuxIgnoringBatteryOptimizations = try {
                    powerManager.isIgnoringBatteryOptimizations(TERMUX_PACKAGE)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking Termux battery optimizations", e)
                    false
                }
                
                // 检查Termux是否受到电池优化限制，提示用户修改设置
                if (!isTermuxIgnoringBatteryOptimizations) {
                    Toast.makeText(
                        context,
                        "建议将Termux添加到电池优化白名单以提高可靠性",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open battery optimization settings", e)
                    }
                } else {
                    Log.d(TAG, "Termux is already ignoring battery optimizations")
                }
            }
        }
        
        /**
         * 检查Termux是否已被授权
         * @param context 上下文
         * @return 是否已授权
         */
        suspend fun isTermuxAuthorized(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                if (!TermuxInstaller.isTermuxInstalled(context)) {
                    Log.d(TAG, "Termux未安装，无法检查授权状态")
                    return@withContext false
                }
                
                // 首先检查是否有运行命令权限
                if (!hasRunCommandPermission(context)) {
                    Log.d(TAG, "缺少com.termux.permission.RUN_COMMAND权限或未配置allow-external-apps=true")
                    return@withContext false
                }
                
                if (!AdbCommandExecutor.isShizukuInstalled(context) || 
                    !AdbCommandExecutor.isShizukuServiceRunning() || 
                    !AdbCommandExecutor.hasShizukuPermission()) {
                    Log.d(TAG, "Shizuku未准备好，无法检查Termux授权状态")
                    return@withContext false
                }
                
                // 首先通过配置文件检查
                val checkConfigResult = AdbCommandExecutor.executeAdbCommand(
                    "run-as com.termux [ -f \"/data/data/com.termux/files/home/.termux/termux.properties\" ] && echo \"exists\" || echo \"not exists\""
                )
                
                // 如果配置文件存在
                if (checkConfigResult.success && checkConfigResult.stdout.contains("exists")) {
                    // 检查配置文件内容
                    val readConfigResult = AdbCommandExecutor.executeAdbCommand(
                        "run-as com.termux cat /data/data/com.termux/files/home/.termux/termux.properties 2>/dev/null | grep allow-external-apps"
                    )
                    
                    if (readConfigResult.success && readConfigResult.stdout.contains("allow-external-apps=true")) {
                        Log.d(TAG, "检测到Termux配置文件中已设置allow-external-apps=true")
                        return@withContext true
                    }
                }
                
                // 尝试直接运行echo命令测试
                val testCommandResult = AdbCommandExecutor.executeAdbCommand(
                    "am startservice --user 0 -n com.termux/com.termux.app.RunCommandService " +
                    "-a com.termux.RUN_COMMAND " +
                    "--es com.termux.RUN_COMMAND_PATH '/data/data/com.termux/files/usr/bin/echo' " +
                    "--esa com.termux.RUN_COMMAND_ARGUMENTS 'test' " +
                    "--ez com.termux.RUN_COMMAND_BACKGROUND 'true' " +
                    "--es com.termux.RUN_COMMAND_WORKDIR '/data/data/com.termux/files/home' " +
                    "--es com.termux.RUN_COMMAND_SESSION_ACTION '0'"
                )
                
                if (testCommandResult.success && !testCommandResult.stderr.contains("Permission Denial")) {
                    Log.d(TAG, "测试命令执行成功，Termux已授权")
                    return@withContext true
                } else {
                    Log.d(TAG, "测试命令执行失败: ${testCommandResult.stderr}")
                    Log.d(TAG, "错误分析: " + 
                        if (testCommandResult.stderr.contains("Permission denied")) {
                            "权限被拒绝，可能需要在Termux中设置allow-external-apps=true"
                        } else if (testCommandResult.stderr.contains("Service not found")) {
                            "未找到服务，可能Termux版本不兼容或未正确安装"
                        } else {
                            "未知错误，请查看日志"
                        }
                    )
                }
                
                // 尝试检查是否为targetSdk 30+的问题 (Android 11+)
                val androidVersion = Build.VERSION.SDK_INT
                if (androidVersion >= 30) {
                    Log.d(TAG, "Android 11+ 设备，可能需要在AndroidManifest中添加正确的<queries>标签")
                }
                
                return@withContext false
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Termux authorization: ${e.message}", e)
                return@withContext false
            }
        }
        
        /**
         * 权限状态数据类
         */
        private class PermissionStatus {
            var needRunCommandPermission: Boolean = false
            var needDrawOverPermission: Boolean = false
            var needStoragePermission: Boolean = false
            
            val missingPermissions: List<String>
                get() {
                    val missing = mutableListOf<String>()
                    if (needRunCommandPermission) missing.add("RunCommand Permission")
                    if (needDrawOverPermission) missing.add("Draw Over Apps")
                    if (needStoragePermission) missing.add("Storage")
                    return missing
                }
                
            val allGranted: Boolean
                get() = !needRunCommandPermission && !needDrawOverPermission && !needStoragePermission
        }
    }
} 
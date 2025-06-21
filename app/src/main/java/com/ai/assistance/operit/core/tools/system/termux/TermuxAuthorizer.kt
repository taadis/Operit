package com.ai.assistance.operit.core.tools.system.termux

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Termux授权工具类 */
class TermuxAuthorizer {
    companion object {
        private const val TAG = "TermuxAuthorizer"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_CONFIG_PATH =
                "/data/data/com.termux/files/home/.termux/termux.properties"
        private const val CACHE_EXPIRY_MS = 100000 // 100秒

        // 权限相关常量
        private const val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        private const val PERMISSION_SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW"
        private const val PERMISSION_REQUEST_INSTALL_PACKAGES =
                "android.permission.REQUEST_INSTALL_PACKAGES"
        private const val PERMISSION_TERMUX_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
        private const val PERMISSION_READ_EXTERNAL_STORAGE =
                "android.permission.READ_EXTERNAL_STORAGE"
        private const val PERMISSION_WRITE_EXTERNAL_STORAGE =
                "android.permission.WRITE_EXTERNAL_STORAGE"
        private const val PERMISSION_MANAGE_EXTERNAL_STORAGE =
                "android.permission.MANAGE_EXTERNAL_STORAGE"

        // 状态变更监听器
        private val stateChangeListeners = mutableListOf<() -> Unit>()
        private val mainHandler = Handler(Looper.getMainLooper())

        /** 添加状态变更监听器 */
        fun addStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                if (!stateChangeListeners.contains(listener)) {
                    stateChangeListeners.add(listener)
                }
            }
        }

        /** 移除状态变更监听器 */
        fun removeStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) { stateChangeListeners.remove(listener) }
        }

        /** 通知状态变化 */
        private fun notifyStateChanged() {
            mainHandler.post {
                synchronized(stateChangeListeners) { stateChangeListeners.forEach { it.invoke() } }
            }
        }

        // 配置状态缓存
        private data class ConfigStatus(val isConfigured: Boolean, val lastCheckTime: Long)

        // 权限状态缓存
        private data class PermissionStatus(
                val notificationEnabled: Boolean,
                val floatingWindowEnabled: Boolean,
                val associationEnabled: Boolean,
                val runCommandEnabled: Boolean,
                val storageEnabled: Boolean,
                val lastCheckTime: Long
        )
        private val permissionCache = AtomicReference<PermissionStatus?>(null)

        public suspend fun deleteTermuxConfig(context: Context) {
            AndroidShellExecutor.executeShellCommand(
                    "run-as com.termux sh -c 'rm -rf $TERMUX_CONFIG_PATH'"
            )
        }

        /** 检查Termux配置 */
        private suspend fun checkTermuxConfig(): Boolean =
                withContext(Dispatchers.IO) {
                    // 直接读取配置文件内容并检查
                    val readConfigCommand =
                            "run-as com.termux sh -c 'cat \"$TERMUX_CONFIG_PATH\" 2>/dev/null'"
                    val readConfigResult =
                            AndroidShellExecutor.executeShellCommand(readConfigCommand)

                    val configured =
                            readConfigResult.success &&
                                    readConfigResult.stdout.contains("allow-external-apps=true") &&
                                    !readConfigResult.stdout.contains("# allow-external-apps")
                    return@withContext configured
                }

        /** 更新权限缓存 */
        private fun updatePermissionCache(
                notificationEnabled: Boolean,
                floatingWindowEnabled: Boolean,
                associationEnabled: Boolean,
                runCommandEnabled: Boolean,
                storageEnabled: Boolean
        ) {
            val currentTime = System.currentTimeMillis()
            val oldStatus =
                    permissionCache.getAndSet(
                            PermissionStatus(
                                    notificationEnabled,
                                    floatingWindowEnabled,
                                    associationEnabled,
                                    runCommandEnabled,
                                    storageEnabled,
                                    currentTime
                            )
                    )

            if (oldStatus?.notificationEnabled != notificationEnabled ||
                            oldStatus?.floatingWindowEnabled != floatingWindowEnabled ||
                            oldStatus?.associationEnabled != associationEnabled ||
                            oldStatus?.runCommandEnabled != runCommandEnabled ||
                            oldStatus?.storageEnabled != storageEnabled
            ) {
                notifyStateChanged()
            }
        }

        /** 重置缓存 */
        private fun resetCache() {
            permissionCache.set(null)
            notifyStateChanged()
            TermuxInstaller.resetInstallCache()
        }

        /** 检查Termux是否已授权 */
        suspend fun isTermuxAuthorized(context: Context): Boolean =
                withContext(Dispatchers.IO) {
                    // 基础条件检查
                    if (!TermuxInstaller.isTermuxInstalled(context)) {
                        return@withContext false
                    }

                    // 检查配置是否启用了外部应用访问
                    val configEnabled = checkTermuxConfig()

                    // 检查是否有Run Command权限
                    val hasRunCommandPermission = checkRunCommandPermission(context)

                    // 三者都满足才算完全授权：Termux运行中、配置允许外部应用访问、有Run Command权限
                    val authorized = configEnabled && hasRunCommandPermission
                    Log.d(TAG, "Termux授权状态: 配置=$configEnabled, 权限=$hasRunCommandPermission")

                    return@withContext authorized
                }

        /** 授权Termux */
        suspend fun authorizeTermux(context: Context): Boolean =
                withContext(Dispatchers.IO) {
                    // 必要检查
                    if (!TermuxInstaller.isTermuxInstalled(context)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "请先安装Termux应用", Toast.LENGTH_SHORT).show()
                        }
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

                    return@withContext false
                }

        /** 使用run-as配置Termux */
        private suspend fun configureWithRunAs(): Boolean =
                withContext(Dispatchers.IO) {
                    try {
                        // 确保目录存在
                        val mkdirCmd =
                                "run-as com.termux sh -c 'mkdir -p /data/data/com.termux/files/home/.termux'"
                        val mkdirResult = AndroidShellExecutor.executeShellCommand(mkdirCmd)

                        if (!mkdirResult.success) {
                            Log.e(TAG, "创建.termux目录失败: ${mkdirResult.stderr}")
                            return@withContext false
                        }

                        // 写入配置
                        val success =
                                AndroidShellExecutor.executeShellCommand(
                                                "run-as com.termux sh -c \"echo 'allow-external-apps=true' > $TERMUX_CONFIG_PATH\""
                                        )
                                        .success

                        if (!success) {
                            // 备用方式
                            val backupSuccess =
                                    AndroidShellExecutor.executeShellCommand(
                                                    "run-as com.termux sh -c \"printf 'allow-external-apps=true\\n' > $TERMUX_CONFIG_PATH\""
                                            )
                                            .success

                            if (!backupSuccess) {
                                return@withContext false
                            }
                        }

                        // 设置文件权限
                        val chmodCommand = "run-as com.termux sh -c 'chmod 600 $TERMUX_CONFIG_PATH'"
                        val chmodResult = AndroidShellExecutor.executeShellCommand(chmodCommand)

                        if (!chmodResult.success) {
                            Log.e(TAG, "设置文件权限失败: ${chmodResult.stderr}")
                            return@withContext false
                        }

                        // 重启Termux
                        AndroidShellExecutor.executeShellCommand("am force-stop com.termux")

                        // 重置缓存
                        resetCache()

                        // 验证配置
                        return@withContext checkTermuxConfig()
                    } catch (e: Exception) {
                        Log.e(TAG, "run-as配置失败: ${e.message}")
                        return@withContext false
                    }
                }

        /** 检查是否有Termux Run Command权限 */
        private suspend fun checkRunCommandPermission(context: Context): Boolean =
                withContext(Dispatchers.IO) {
                    // 这个权限是在AndroidManifest中声明的，需要检查两点：
                    // 1. 应用在manifest中是否声明了该权限
                    // 2. 用户是否授予了该权限

                    try {
                        // 检查应用清单中是否声明了权限
                        val packageInfo =
                                context.packageManager.getPackageInfo(
                                        context.packageName,
                                        PackageManager.GET_PERMISSIONS
                                )

                        // 检查清单中是否声明了RUN_COMMAND权限
                        val declaredPermissions = packageInfo.requestedPermissions ?: emptyArray()
                        val hasPermissionDeclared =
                                declaredPermissions.any { it == PERMISSION_TERMUX_RUN_COMMAND }

                        Log.d(TAG, "应用是否在Manifest中声明了Termux RUN_COMMAND权限: $hasPermissionDeclared")

                        // 如果清单中未声明权限，直接返回false
                        if (!hasPermissionDeclared) {
                            Log.w(TAG, "应用清单中未声明Termux RUN_COMMAND权限")
                            return@withContext false
                        }

                        // 检查用户是否授予了该权限（使用checkCallingOrSelfPermission方法）
                        val permissionResult =
                                context.checkCallingOrSelfPermission(PERMISSION_TERMUX_RUN_COMMAND)
                        val permissionGranted =
                                permissionResult == PackageManager.PERMISSION_GRANTED

                        Log.d(TAG, "用户是否授予了Termux RUN_COMMAND权限: $permissionGranted")

                        // 总体授权判断：Manifest中声明且用户授予了权限，并且Termux配置允许外部应用访问
                        // 配置检查已经在isTermuxAuthorized方法中通过checkTermuxConfig()实现
                        return@withContext permissionGranted
                    } catch (e: Exception) {
                        Log.e(TAG, "检查Termux RUN_COMMAND权限失败: ${e.message}")
                        return@withContext false
                    }
                }

        /** 检查Termux是否有存储权限 */
        private suspend fun checkStoragePermissions(): Boolean =
                withContext(Dispatchers.IO) {
                    // 检查基本存储权限
                    val readPermission =
                            AndroidShellExecutor.executeShellCommand(
                                    "dumpsys package $TERMUX_PACKAGE | grep permission.$PERMISSION_READ_EXTERNAL_STORAGE"
                            )

                    val writePermission =
                            AndroidShellExecutor.executeShellCommand(
                                    "dumpsys package $TERMUX_PACKAGE | grep permission.$PERMISSION_WRITE_EXTERNAL_STORAGE"
                            )

                    // 对于Android 11+，还要检查MANAGE_EXTERNAL_STORAGE权限
                    val managePermission =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                AndroidShellExecutor.executeShellCommand(
                                        "dumpsys package $TERMUX_PACKAGE | grep permission.$PERMISSION_MANAGE_EXTERNAL_STORAGE"
                                )
                            } else {
                                AndroidShellExecutor.CommandResult(true, "", "")
                            }

                    // 检查基本存储权限或高级存储权限是否已授予
                    val basicStorageGranted =
                            (readPermission.success &&
                                    readPermission.stdout.contains("granted=true")) &&
                                    (writePermission.success &&
                                            writePermission.stdout.contains("granted=true"))

                    val advancedStorageGranted =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                    managePermission.success &&
                                    managePermission.stdout.contains("granted=true")

                    return@withContext basicStorageGranted || advancedStorageGranted
                }

        /** 请求Termux运行命令权限 */
        suspend fun requestRunCommandPermission(context: Context): Boolean =
                withContext(Dispatchers.IO) {
                    // 检查Termux是否已配置
                    if (!authorizeTermux(context)) {
                        return@withContext false
                    }

                    // 检查运行命令权限
                    if (checkRunCommandPermission(context)) {
                        return@withContext true
                    }

                    // 尝试授予运行命令权限
                    val packageName = context.packageName

                    // 尝试方法1: 使用pm grant
                    val grantResult =
                            AndroidShellExecutor.executeShellCommand(
                                    "pm grant $packageName $PERMISSION_TERMUX_RUN_COMMAND"
                            )

                    if (grantResult.success) {
                        Log.d(TAG, "通过pm grant授予运行命令权限成功")
                        notifyStateChanged()
                        return@withContext true
                    }
                    // 尝试方法3: 在应用列表中添加
                    val appTermuxSuccess = grantAppTermuxPermissions(context)
                    if (appTermuxSuccess) {
                        Log.d(TAG, "通过am.allow授予运行命令权限成功")
                        notifyStateChanged()
                        return@withContext true
                    }

                    // 如果所有方法都失败，提示用户手动操作
                    withContext(Dispatchers.Main) {
                        val dialog =
                                AlertDialog.Builder(context)
                                        .setTitle("需要手动授权")
                                        .setMessage(
                                                "无法自动授予Termux运行命令权限，请手动操作:\n\n" +
                                                        "1. 打开Termux\n" +
                                                        "2. 长按屏幕，选择'更多'\n" +
                                                        "3. 选择'设置'\n" +
                                                        "4. 启用'允许外部应用访问'"
                                        )
                                        .setPositiveButton("打开Termux") { dialog, _ ->
                                            dialog.dismiss()
                                            TermuxInstaller.openTermux(context)
                                        }
                                        .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                                        .create()

                        dialog.show()
                    }

                    return@withContext false
                }

        /** 请求Termux存储权限 */
        suspend fun requestStoragePermissions(context: Context): Boolean =
                withContext(Dispatchers.IO) {
                    // 检查Termux是否已安装
                    if (!TermuxInstaller.isTermuxInstalled(context)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "请先安装Termux应用", Toast.LENGTH_SHORT).show()
                        }
                        return@withContext false
                    }

                    // 检查存储权限
                    if (checkStoragePermissions()) {
                        return@withContext true
                    }

                    // 尝试方法1: 使用pm grant授予基本存储权限
                    val grantReadResult =
                            AndroidShellExecutor.executeShellCommand(
                                    "pm grant $TERMUX_PACKAGE $PERMISSION_READ_EXTERNAL_STORAGE"
                            )

                    val grantWriteResult =
                            AndroidShellExecutor.executeShellCommand(
                                    "pm grant $TERMUX_PACKAGE $PERMISSION_WRITE_EXTERNAL_STORAGE"
                            )

                    // 检查基本存储权限是否授予成功
                    val basicStorageGranted = grantReadResult.success && grantWriteResult.success

                    if (basicStorageGranted) {
                        Log.d(TAG, "通过pm grant授予Termux基本存储权限成功")
                        notifyStateChanged()
                        return@withContext true
                    }

                    // 对于Android 11+，尝试授予MANAGE_EXTERNAL_STORAGE权限(需要root)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val grantManageResult =
                                AndroidShellExecutor.executeShellCommand(
                                        "su -c 'pm grant $TERMUX_PACKAGE $PERMISSION_MANAGE_EXTERNAL_STORAGE'"
                                )

                        if (grantManageResult.success) {
                            Log.d(TAG, "通过root授予Termux高级存储权限成功")
                            notifyStateChanged()
                            return@withContext true
                        }
                    }

                    // 如果所有方法都失败，提示用户手动操作
                    withContext(Dispatchers.Main) {
                        val dialog =
                                AlertDialog.Builder(context)
                                        .setTitle("需要手动授权")
                                        .setMessage(
                                                "无法自动授予Termux存储权限，请检查:\n\n" +
                                                        "1. 检查是否使用正确的权限组并且shell能够使用，比如adb或者root能够正常使用（shizuku请不要使用root启动）\n" +
                                                        "2. termux确保未内置的termux或者gthub上找来的debug版本的软件版本"
                                        )
                                        .setPositiveButton("打开设置") { dialog, _ ->
                                            dialog.dismiss()
                                            try {
                                                val intent =
                                                        Intent(
                                                                android.provider.Settings
                                                                        .ACTION_APPLICATION_DETAILS_SETTINGS
                                                        )
                                                intent.data =
                                                        android.net.Uri.parse(
                                                                "package:$TERMUX_PACKAGE"
                                                        )
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                                context,
                                                                "无法打开设置",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                            }
                                        }
                                        .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                                        .create()

                        dialog.show()
                    }

                    return@withContext false
                }

        /** 检查Termux的额外权限状态 */
        suspend fun checkTermuxPermissions(context: Context): Triple<Boolean, Boolean, Boolean> =
                withContext(Dispatchers.IO) {
                    val currentTime = System.currentTimeMillis()
                    val cached = permissionCache.get()

                    if (cached != null && (currentTime - cached.lastCheckTime) < CACHE_EXPIRY_MS) {
                        return@withContext Triple(
                                cached.notificationEnabled,
                                cached.floatingWindowEnabled,
                                cached.associationEnabled
                        )
                    }

                    // 检查通知权限
                    val notificationEnabled =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val result =
                                        AndroidShellExecutor.executeShellCommand(
                                                "dumpsys package $TERMUX_PACKAGE | grep permission.$PERMISSION_POST_NOTIFICATIONS"
                                        )
                                result.success && result.stdout.contains("granted=true")
                            } else {
                                true // 低版本Android默认允许通知
                            }

                    // 检查悬浮窗权限
                    val floatingWindowEnabled =
                            AndroidShellExecutor.executeShellCommand(
                                            "dumpsys package $TERMUX_PACKAGE | grep permission.$PERMISSION_SYSTEM_ALERT_WINDOW"
                                    )
                                    .let { result ->
                                        result.success && result.stdout.contains("granted=true")
                                    }

                    // 检查关联启动权限
                    val associationEnabled =
                            AndroidShellExecutor.executeShellCommand(
                                            "dumpsys package $TERMUX_PACKAGE | grep QUERY_ALL_PACKAGES"
                                    )
                                    .let { result ->
                                        result.success && result.stdout.contains("granted=true")
                                    }

                    // 检查运行命令权限
                    val runCommandEnabled = checkRunCommandPermission(context)

                    // 检查存储权限
                    val storageEnabled = checkStoragePermissions()

                    updatePermissionCache(
                            notificationEnabled,
                            floatingWindowEnabled,
                            associationEnabled,
                            runCommandEnabled,
                            storageEnabled
                    )

                    return@withContext Triple(
                            notificationEnabled,
                            floatingWindowEnabled,
                            associationEnabled
                    )
                }

        /** 一键授予Termux全部所需权限 */
        suspend fun grantAllTermuxPermissions(context: Context): Boolean =
                withContext(Dispatchers.IO) {
                    // 允许外部调用
                    var allowExternalApps = authorizeTermux(context)

                    // 授予运行命令权限
                    val runCmdResult = requestRunCommandPermission(context)

                    // 授予存储权限
                    val storageResult = requestStoragePermissions(context)

                    val success = runCmdResult && storageResult && allowExternalApps

                    if (success) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Termux全部权限授权成功", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "部分权限授权失败，请查看日志或手动设置", Toast.LENGTH_LONG).show()
                        }
                    }

                    return@withContext success
                }

        /** 获取应用包名 */
        fun getAppPackageName(context: Context): String {
            return context.packageName
        }

        /** 为应用授权Termux相关权限 */
        suspend fun grantAppTermuxPermissions(context: Context): Boolean =
                withContext(Dispatchers.IO) {
                    val packageName = getAppPackageName(context)

                    if (!TermuxInstaller.isTermuxInstalled(context)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "请先安装Termux应用", Toast.LENGTH_SHORT).show()
                        }
                        return@withContext false
                    }

                    // 先确保Termux已授权
                    val termuxAuthorized = authorizeTermux(context)
                    if (!termuxAuthorized) {
                        return@withContext false
                    }

                    // 添加应用到Termux允许列表
                    try {
                        // 检查AM配置文件是否存在
                        val amAllowPath = "/data/data/com.termux/files/home/.termux/am.allow"
                        val amAllowExistsCommand =
                                "run-as com.termux sh -c 'ls \"$amAllowPath\" 2>/dev/null && echo \"exists\"'"
                        val amAllowExistsResult =
                                AndroidShellExecutor.executeShellCommand(amAllowExistsCommand)

                        if (amAllowExistsResult.success &&
                                        amAllowExistsResult.stdout.contains("exists")
                        ) {
                            // 检查是否已包含应用包名
                            val readAmAllowCommand =
                                    "run-as com.termux sh -c 'cat \"$amAllowPath\"'"
                            val readAmAllowResult =
                                    AndroidShellExecutor.executeShellCommand(readAmAllowCommand)

                            if (readAmAllowResult.success &&
                                            readAmAllowResult.stdout.contains(packageName)
                            ) {
                                Log.d(TAG, "应用已在Termux允许列表中")
                                return@withContext true
                            }

                            // 追加包名
                            val appendResult =
                                    AndroidShellExecutor.executeShellCommand(
                                            "run-as com.termux sh -c \"echo '$packageName' >> $amAllowPath\""
                                    )

                            if (!appendResult.success) {
                                Log.e(TAG, "追加应用到Termux允许列表失败: ${appendResult.stderr}")
                                return@withContext false
                            }
                        } else {
                            // 确保目录存在
                            val mkdirCmd =
                                    "run-as com.termux sh -c 'mkdir -p /data/data/com.termux/files/home/.termux'"
                            try {
                                val mkdirResult = AndroidShellExecutor.executeShellCommand(mkdirCmd)
                                if (mkdirResult.success) {
                                    Log.d(TAG, "成功创建.termux目录")
                                } else {
                                    Log.e(TAG, "创建.termux目录失败: ${mkdirResult.stderr}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "创建目录异常: ${e.message}")
                            }

                            // 创建文件并添加包名
                            val createResult =
                                    AndroidShellExecutor.executeShellCommand(
                                            "run-as com.termux sh -c \"echo '$packageName' > $amAllowPath\""
                                    )

                            if (!createResult.success) {
                                Log.e(TAG, "创建Termux允许列表失败: ${createResult.stderr}")
                                return@withContext false
                            }
                        }

                        // 设置文件权限
                        val chmodCommand = "run-as com.termux sh -c 'chmod 600 $amAllowPath'"
                        val chmodResult = AndroidShellExecutor.executeShellCommand(chmodCommand)

                        if (!chmodResult.success) {
                            Log.e(TAG, "设置文件权限失败: ${chmodResult.stderr}")
                            return@withContext false
                        }

                        // 重启Termux使配置生效
                        AndroidShellExecutor.executeShellCommand("am force-stop com.termux")

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已授权应用与Termux关联", Toast.LENGTH_SHORT).show()
                        }

                        return@withContext true
                    } catch (e: Exception) {
                        Log.e(TAG, "应用授权失败: ${e.message}")
                        return@withContext false
                    }
                }

        /** 声明Termux运行命令权限 需要确保在AndroidManifest.xml中添加相应的<uses-permission>声明 */
        suspend fun ensureTermuxRunCommandPermission(context: Context): Boolean =
                withContext(Dispatchers.IO) {
                    // 调用完整的授权流程
                    if (requestRunCommandPermission(context)) {
                        return@withContext true
                    }

                    // 如果上述方法都失败，尝试使用intent方式手动授权
                    withContext(Dispatchers.Main) {
                        try {
                            // 打开Termux应用，引导用户手动授权
                            val intent = Intent()
                            intent.setClassName(TERMUX_PACKAGE, "com.termux.app.TermuxActivity")
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)

                            Toast.makeText(context, "请在Termux中开启'允许外部应用访问'选项", Toast.LENGTH_LONG)
                                    .show()
                        } catch (e: Exception) {
                            Log.e(TAG, "打开Termux失败: ${e.message}")
                            Toast.makeText(context, "无法打开Termux，请手动设置", Toast.LENGTH_SHORT).show()
                        }
                    }

                    return@withContext false
                }
    }
}

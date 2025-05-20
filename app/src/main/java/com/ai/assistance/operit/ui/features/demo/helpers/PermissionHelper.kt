package com.ai.assistance.operit.ui.features.demo.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxInstaller
import com.ai.assistance.operit.data.repository.UIHierarchyManager

private const val TAG = "PermissionHelper"

/**
 * 刷新应用权限和组件状态
 */
fun refreshPermissionsAndStatus(
    context: Context,
    updateShizukuInstalled: (Boolean) -> Unit,
    updateShizukuRunning: (Boolean) -> Unit,
    updateShizukuPermission: (Boolean) -> Unit,
    updateTermuxInstalled: (Boolean) -> Unit,
    updateTermuxRunning: (Boolean) -> Unit,
    updateStoragePermission: (Boolean) -> Unit,
    updateLocationPermission: (Boolean) -> Unit,
    updateOverlayPermission: (Boolean) -> Unit,
    updateBatteryOptimizationExemption: (Boolean) -> Unit,
    updateAccessibilityServiceEnabled: (Boolean) -> Unit
) {
    Log.d(TAG, "刷新应用权限状态...")

    // 检查Shizuku安装、运行和权限状态
    val isShizukuInstalled = ShizukuAuthorizer.isShizukuInstalled(context)
    val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
    updateShizukuInstalled(isShizukuInstalled)
    updateShizukuRunning(isShizukuRunning)

    // 更明确地检查 moe.shizuku.manager.permission.API_V23 权限
    val hasShizukuPermission =
        if (isShizukuInstalled && isShizukuRunning) {
            ShizukuAuthorizer.hasShizukuPermission()
        } else {
            false
        }
    updateShizukuPermission(hasShizukuPermission)

    // 检查Termux是否安装
    val isTermuxInstalled = TermuxInstaller.isTermuxInstalled(context)
    updateTermuxInstalled(isTermuxInstalled)

    // 检查Termux是否在运行
    val isTermuxRunning = checkTermuxRunning(context)
    updateTermuxRunning(isTermuxRunning)

    // 检查存储权限
    val hasStoragePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    updateStoragePermission(hasStoragePermission)

    // 检查位置权限
    val hasLocationPermission =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    updateLocationPermission(hasLocationPermission)

    // 检查悬浮窗权限
    val hasOverlayPermission = Settings.canDrawOverlays(context)
    updateOverlayPermission(hasOverlayPermission)

    // 检查电池优化豁免
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val hasBatteryOptimizationExemption = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    updateBatteryOptimizationExemption(hasBatteryOptimizationExemption)

    // 检查无障碍服务状态
    val hasAccessibilityServiceEnabled = UIHierarchyManager.isAccessibilityServiceEnabled(context)
    updateAccessibilityServiceEnabled(hasAccessibilityServiceEnabled)
}

/**
 * 检查Termux授权状态
 */
suspend fun checkTermuxAuth(
    context: Context,
    isTermuxInstalled: Boolean,
    updateTermuxAuthorized: (Boolean) -> Unit
): Boolean {
    // 只在Termux已安装时检查
    if (isTermuxInstalled) {
        val isAuthorized = TermuxAuthorizer.isTermuxAuthorized(context)
        updateTermuxAuthorized(isAuthorized)
        return isAuthorized
    }
    updateTermuxAuthorized(false)
    return false
}

/**
 * 检查应用清单是否声明了Termux RUN_COMMAND权限
 */
fun checkTermuxPermissionDeclared(context: Context): Boolean {
    try {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val declaredPermissions = packageInfo.requestedPermissions ?: emptyArray()
        val hasPermissionDeclared = declaredPermissions.any { it == "com.termux.permission.RUN_COMMAND" }

        if (!hasPermissionDeclared) {
            Log.w(TAG, "应用清单中未声明Termux RUN_COMMAND权限，Termux功能将无法正常运行")
        }
        return hasPermissionDeclared
    } catch (e: Exception) {
        Log.e(TAG, "检查应用权限声明时出错: ${e.message}")
        return false
    }
} 
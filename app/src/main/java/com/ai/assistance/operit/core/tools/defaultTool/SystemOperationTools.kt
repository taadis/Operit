package com.ai.assistance.operit.core.tools.defaultTool

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.ai.assistance.operit.core.tools.AppListData
import com.ai.assistance.operit.core.tools.AppOperationData
import com.ai.assistance.operit.core.tools.LocationData
import com.ai.assistance.operit.core.tools.NotificationData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.SystemSettingData
import com.ai.assistance.operit.core.tools.system.AdbCommandExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 提供系统级操作的工具类 包括系统设置修改、应用安装和卸载等 这些操作需要用户明确授权 */
class SystemOperationTools(private val context: Context) {

    companion object {
        private const val TAG = "SystemOperationTools"
    }

    /** 修改系统设置 支持修改各种系统设置，如音量、亮度等 */
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
                val resultData =
                        SystemSettingData(namespace = namespace, setting = setting, value = value)

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

    /** 获取系统设置的当前值 */
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
                val resultData =
                        SystemSettingData(
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

    /** 安装应用程序 需要APK文件的路径 */
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
        val existsResult =
                AdbCommandExecutor.executeAdbCommand(
                        "test -f $apkPath && echo 'exists' || echo 'not exists'"
                )
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
                val resultData =
                        AppOperationData(
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

    /** 卸载应用程序 需要提供包名 */
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
            val command =
                    if (keepData) {
                        "pm uninstall -k $packageName"
                    } else {
                        "pm uninstall $packageName"
                    }

            val result = AdbCommandExecutor.executeAdbCommand(command)

            if (result.success && result.stdout.contains("Success")) {
                val details = if (keepData) "(保留数据)" else ""
                val resultData =
                        AppOperationData(
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

    /** 获取已安装应用列表 */
    suspend fun listInstalledApps(tool: AITool): ToolResult {
        val systemApps =
                tool.parameters.find { it.name == "include_system_apps" }?.value?.toBoolean()
                        ?: false

        return try {
            val command =
                    if (systemApps) {
                        "pm list packages"
                    } else {
                        "pm list packages -3" // 只显示第三方应用
                    }

            val result = AdbCommandExecutor.executeAdbCommand(command)

            if (result.success) {
                // 格式化输出，使其更易读
                val packageList =
                        result.stdout
                                .split("\n")
                                .filter { it.isNotBlank() }
                                .map { it.replace("package:", "") }
                                .sorted()

                val resultData =
                        AppListData(includesSystemApps = systemApps, packages = packageList)

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

    /** 启动应用程序 如果提供了activity参数，将启动指定的活动 否则使用默认启动器启动应用 */
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
            val command =
                    if (activity.isBlank()) {
                        "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
                    } else {
                        "am start -n $packageName/$activity"
                    }

            val result = AdbCommandExecutor.executeAdbCommand(command)

            if (result.success) {
                val details = if (activity.isNotBlank()) "活动: $activity" else ""
                val resultData =
                        AppOperationData(
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

    /** 停止应用程序 */
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
                val resultData =
                        AppOperationData(
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

    /** 读取设备通知内容 获取当前设备上的通知信息 */
    suspend fun getNotifications(tool: AITool): ToolResult {
        val limit = tool.parameters.find { it.name == "limit" }?.value?.toIntOrNull() ?: 10
        val includeOngoing =
                tool.parameters.find { it.name == "include_ongoing" }?.value?.toBoolean() ?: false

        return try {
            // 使用ADB命令获取通知信息
            val command =
                    if (includeOngoing) {
                        "dumpsys notification --noredact | grep -E 'pkg=|text=' | head -${limit * 2}"
                    } else {
                        "dumpsys notification --noredact | grep -v 'ongoing' | grep -E 'pkg=|text=' | head -${limit * 2}"
                    }

            val result = AdbCommandExecutor.executeAdbCommand(command)

            if (result.success) {
                // 解析通知内容
                val lines = result.stdout.split("\n")
                val notifications = mutableListOf<NotificationData.Notification>()

                var currentPackage = ""
                var currentText = ""

                for (line in lines) {
                    when {
                        line.contains("pkg=") -> {
                            // 如果已经有包名和文本，添加到列表中
                            if (currentPackage.isNotEmpty() && currentText.isNotEmpty()) {
                                notifications.add(
                                        NotificationData.Notification(
                                                packageName = currentPackage,
                                                text = currentText,
                                                timestamp = System.currentTimeMillis()
                                        )
                                )
                                currentText = ""
                            }

                            // 提取包名
                            val pkgMatch = Regex("pkg=(\\S+)").find(line)
                            currentPackage = pkgMatch?.groupValues?.getOrNull(1) ?: ""
                        }
                        line.contains("text=") -> {
                            // 提取通知文本
                            val textMatch = Regex("text=(.+)").find(line)
                            currentText = textMatch?.groupValues?.getOrNull(1) ?: ""
                        }
                    }
                }

                // 添加最后一条通知
                if (currentPackage.isNotEmpty() && currentText.isNotEmpty()) {
                    notifications.add(
                            NotificationData.Notification(
                                    packageName = currentPackage,
                                    text = currentText,
                                    timestamp = System.currentTimeMillis()
                            )
                    )
                }

                val resultData =
                        NotificationData(
                                notifications = notifications,
                                timestamp = System.currentTimeMillis()
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
                        error = "获取通知失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取通知时出错", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "获取通知时出错: ${e.message}"
            )
        }
    }

    /** 获取设备位置信息 通过系统API获取当前设备位置 */
    suspend fun getDeviceLocation(tool: AITool): ToolResult {
        val timeout = tool.parameters.find { it.name == "timeout" }?.value?.toIntOrNull() ?: 10
        val highAccuracy =
                tool.parameters.find { it.name == "high_accuracy" }?.value?.toBoolean() ?: false
        val includeAddress =
                tool.parameters.find { it.name == "include_address" }?.value?.toBoolean() ?: true

        return try {
            // 检查位置权限
            val hasFineLocationPermission =
                    context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasCoarseLocationPermission =
                    context.checkSelfPermission(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            // 如果没有任何位置权限，返回错误
            if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "未授予位置权限，请在应用设置中开启位置权限"
                )
            }

            // 根据精度要求和权限情况决定使用哪种精度
            val actualHighAccuracy = highAccuracy && hasFineLocationPermission

            // 使用Dispatchers.Main确保在主线程上执行位置操作
            val locationResult =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        kotlinx.coroutines.suspendCancellableCoroutine<Location?> { continuation ->
                            val locationManager =
                                    context.getSystemService(Context.LOCATION_SERVICE) as
                                            LocationManager

                            // 选择合适的位置提供者
                            val provider =
                                    when {
                                        actualHighAccuracy &&
                                                locationManager.isProviderEnabled(
                                                        LocationManager.GPS_PROVIDER
                                                ) -> LocationManager.GPS_PROVIDER
                                        locationManager.isProviderEnabled(
                                                LocationManager.NETWORK_PROVIDER
                                        ) -> LocationManager.NETWORK_PROVIDER
                                        locationManager.isProviderEnabled(
                                                LocationManager.PASSIVE_PROVIDER
                                        ) -> LocationManager.PASSIVE_PROVIDER
                                        else -> null
                                    }

                            if (provider == null) {
                                continuation.resume(null) { Log.e(TAG, "位置请求取消", it) }
                                return@suspendCancellableCoroutine
                            }

                            // 尝试获取最后已知位置
                            val lastKnownLocation =
                                    try {
                                        if (actualHighAccuracy && hasFineLocationPermission) {
                                            locationManager.getLastKnownLocation(
                                                    LocationManager.GPS_PROVIDER
                                            )
                                                    ?: locationManager.getLastKnownLocation(
                                                            LocationManager.NETWORK_PROVIDER
                                                    )
                                        } else if (hasCoarseLocationPermission) {
                                            locationManager.getLastKnownLocation(
                                                    LocationManager.NETWORK_PROVIDER
                                            )
                                                    ?: locationManager.getLastKnownLocation(
                                                            LocationManager.PASSIVE_PROVIDER
                                                    )
                                        } else {
                                            null
                                        }
                                    } catch (e: SecurityException) {
                                        Log.e(TAG, "获取最后已知位置失败", e)
                                        null
                                    }

                            // 如果有最后已知位置且足够新（10分钟内），直接返回
                            if (lastKnownLocation != null &&
                                            System.currentTimeMillis() - lastKnownLocation.time <
                                                    10 * 60 * 1000
                            ) {
                                continuation.resume(lastKnownLocation) { Log.e(TAG, "位置请求取消", it) }
                                return@suspendCancellableCoroutine
                            }

                            // 否则请求位置更新
                            val locationListener =
                                    object : android.location.LocationListener {
                                        override fun onLocationChanged(location: Location) {
                                            locationManager.removeUpdates(this)
                                            continuation.resume(location) {
                                                Log.e(TAG, "位置请求取消", it)
                                            }
                                        }

                                        override fun onProviderDisabled(provider: String) {
                                            // 如果提供者被禁用，尝试使用最后已知位置
                                            if (!continuation.isCompleted) {
                                                if (lastKnownLocation != null) {
                                                    continuation.resume(lastKnownLocation) {
                                                        Log.e(TAG, "位置请求取消", it)
                                                    }
                                                } else {
                                                    continuation.resume(null) {
                                                        Log.e(TAG, "位置请求取消", it)
                                                    }
                                                }
                                            }
                                        }

                                        override fun onProviderEnabled(provider: String) {
                                            // 不需要处理
                                        }

                                        @Deprecated("Deprecated in Java")
                                        override fun onStatusChanged(
                                                provider: String,
                                                status: Int,
                                                extras: android.os.Bundle
                                        ) {
                                            // 不需要处理
                                        }
                                    }

                            try {
                                // 设置位置请求参数
                                locationManager.requestLocationUpdates(
                                        provider,
                                        0, // 最小时间间隔
                                        0f, // 最小距离变化
                                        locationListener
                                )

                                // 设置超时
                                kotlinx.coroutines.GlobalScope.launch {
                                    delay(timeout * 1000L)
                                    // 在主线程上移除更新和恢复协程
                                    kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.Main
                                    ) {
                                        if (!continuation.isCompleted) {
                                            locationManager.removeUpdates(locationListener)
                                            // 如果超时，尝试使用最后已知位置
                                            continuation.resume(lastKnownLocation) {
                                                Log.e(TAG, "位置请求取消", it)
                                            }
                                        }
                                    }
                                }

                                // 如果协程被取消，移除位置更新
                                continuation.invokeOnCancellation {
                                    try {
                                        // 确保在主线程上移除位置更新
                                        kotlinx.coroutines.runBlocking(
                                                kotlinx.coroutines.Dispatchers.Main
                                        ) { locationManager.removeUpdates(locationListener) }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "移除位置更新失败", e)
                                    }
                                }
                            } catch (e: SecurityException) {
                                continuation.resume(lastKnownLocation) { Log.e(TAG, "位置请求取消", it) }
                                Log.e(TAG, "请求位置更新失败", e)
                            }
                        }
                    }

            // 处理位置结果
            if (locationResult == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "无法获取位置信息，请确保已启用位置服务"
                )
            }

            val resultData =
                    if (includeAddress) {
                        // 获取地址信息
                        val addressInfo =
                                getAddressFromLocation(
                                        locationResult.latitude,
                                        locationResult.longitude
                                )

                        LocationData(
                                latitude = locationResult.latitude,
                                longitude = locationResult.longitude,
                                accuracy = locationResult.accuracy,
                                provider = locationResult.provider ?: "unknown",
                                timestamp = locationResult.time,
                                rawData = locationResult.toString(),
                                city = addressInfo.city,
                                address = addressInfo.address,
                                country = addressInfo.country,
                                province = addressInfo.province
                        )
                    } else {
                        LocationData(
                                latitude = locationResult.latitude,
                                longitude = locationResult.longitude,
                                accuracy = locationResult.accuracy,
                                provider = locationResult.provider ?: "unknown",
                                timestamp = locationResult.time,
                                rawData = locationResult.toString()
                        )
                    }

            return ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            Log.e(TAG, "获取位置信息时出错", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "获取位置信息时出错: ${e.message}"
            )
        }
    }

    /**
     * 从经纬度获取地址信息
     * @param latitude 纬度
     * @param longitude 经度
     * @return 包含地址信息的数据类
     */
    private fun getAddressFromLocation(latitude: Double, longitude: Double): AddressInfo {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            // 尝试获取地址
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]

                return AddressInfo(
                        address = address.getAddressLine(0) ?: "",
                        city = address.locality ?: address.subAdminArea ?: "",
                        province = address.adminArea ?: "",
                        country = address.countryName ?: "",
                        postalCode = address.postalCode ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取地址信息时出错", e)
        }

        // 如果无法获取地址信息，返回空对象
        return AddressInfo("", "", "", "", "")
    }

    /** 地址信息数据类 */
    data class AddressInfo(
            val address: String, // 完整地址
            val city: String, // 城市
            val province: String, // 省/州
            val country: String, // 国家
            val postalCode: String // 邮政编码
    )
}

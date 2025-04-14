package com.ai.assistance.operit.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.system.AdbCommandExecutor
import com.ai.assistance.operit.tools.system.ShizukuInstaller
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.theme.OperitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    // ======== 状态追踪 ========
    private data class PermissionState(
            var basicPermissionsRequested: Boolean = false,
            var storagePermissionRequested: Boolean = false,
            var overlayPermissionRequested: Boolean = false,
            var batteryOptimizationRequested: Boolean = false,
            var accessibilityServiceRequested: Boolean = false
    )

    private val permissionState = PermissionState()

    // ======== 导航状态 ========
    private var navigateToShizukuScreen = false
    private var showPreferencesGuide = false

    // ======== 工具和管理器 ========
    private lateinit var toolHandler: AIToolHandler
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var agreementPreferences: AgreementPreferences
    private lateinit var shizukuStateListener: () -> Unit
    private var updateCheckPerformed = false

    // ======== 权限定义 ========
    private val requiredPermissions = getRequiredPermissionsForAndroidVersion()

    // UpdateManager实例
    private lateinit var updateManager: UpdateManager

    private fun getRequiredPermissionsForAndroidVersion() =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.READ_MEDIA_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS,
                                Manifest.permission.QUERY_ALL_PACKAGES,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.QUERY_ALL_PACKAGES,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                else ->
                        arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        )
            }

    // ======== 权限请求回调 ========
    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                Log.d(TAG, "Basic permission results: $permissions")
                permissionState.basicPermissionsRequested = true

                // 继续下一步权限检查流程
                continuePermissionFlow()
            }

    private val storagePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                Log.d(TAG, "Storage permission result: ${it.resultCode}")
                permissionState.storagePermissionRequested = true

                // 继续下一步权限检查流程
                continuePermissionFlow()
            }

    private val overlayPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                Log.d(TAG, "Overlay permission result: ${it.resultCode}")
                permissionState.overlayPermissionRequested = true

                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "未获得悬浮窗权限，某些功能可能受限", Toast.LENGTH_LONG).show()
                }

                // 继续下一步
                continuePermissionFlow()
            }

    private val batteryOptimizationLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                Log.d(TAG, "Battery optimization exemption result: ${it.resultCode}")
                permissionState.batteryOptimizationRequested = true

                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    Log.d(TAG, "Battery optimization exemption granted")
                    Toast.makeText(this, "已获得电池优化豁免权限", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "Battery optimization exemption denied")
                    Toast.makeText(this, "未获得电池优化豁免权限，后台运行可能受限", Toast.LENGTH_LONG).show()
                }

                // 继续下一步
                continuePermissionFlow()
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")

        initializeComponents()
        setupShizukuListener()
        setupPreferencesListener()
        configureDisplaySettings()

        // 初始化并设置更新管理器
        setupUpdateManager()

        // 启动权限流程
        startPermissionFlow()

        // 设置初始界面
        setAppContent()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")

        // 如果权限流程未完成，继续处理
        continuePermissionFlow()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        // 移除Shizuku状态变化监听器
        try {
            if (::shizukuStateListener.isInitialized) {
                AdbCommandExecutor.removeStateChangeListener(shizukuStateListener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing Shizuku state change listener", e)
        }
    }

    // ======== 初始化组件 ========
    private fun initializeComponents() {
        // 初始化工具处理器
        toolHandler = AIToolHandler.getInstance(this)
        toolHandler.registerDefaultTools()

        // 初始化用户偏好管理器并直接检查初始化状态
        preferencesManager = UserPreferencesManager(this)
        showPreferencesGuide = !preferencesManager.isPreferencesInitialized()
        Log.d(
                TAG,
                "初始化检查: 用户偏好已初始化=${!showPreferencesGuide}，将${if(showPreferencesGuide) "" else "不"}显示引导界面"
        )

        // 初始化协议偏好管理器
        agreementPreferences = AgreementPreferences(this)
    }

    // ======== Shizuku监听器设置 ========
    private fun setupShizukuListener() {
        // 创建Shizuku状态变化监听器
        val listener = {
            Log.d(TAG, "Shizuku状态变化，检查是否可以启用无障碍服务")
            if (isShizukuFullyAvailable() &&
                            !permissionState.accessibilityServiceRequested &&
                            !UIHierarchyManager.isAccessibilityServiceEnabled(this)
            ) {

                // 检查设置并在适当时启用无障碍服务
                lifecycleScope.launch {
                    val apiPreferences = ApiPreferences(this@MainActivity)
                    val autoGrantAccessibility = apiPreferences.autoGrantAccessibilityFlow.first()

                    if (autoGrantAccessibility) {
                        Log.d(TAG, "Shizuku已授权，自动授权设置已开启，尝试启用无障碍服务")
                        tryEnableAccessibilityViaAdb()
                    } else {
                        Log.d(TAG, "Shizuku已授权，但自动授权设置已关闭，不尝试自动启用无障碍服务")
                        permissionState.accessibilityServiceRequested = true
                    }
                }
            }
        }

        // 注册Shizuku状态变化监听器
        AdbCommandExecutor.addStateChangeListener(listener)
        this.shizukuStateListener = listener

        // 检查无障碍服务状态
        checkAccessibilityServiceEnabled()
    }

    // ======== 偏好监听器设置 ========
    private fun setupPreferencesListener() {
        // 监听偏好变化
        lifecycleScope.launch {
            preferencesManager.getUserPreferencesFlow().collect { profile ->
                // 只有当状态变化时才更新UI
                val newValue = !profile.isInitialized
                if (showPreferencesGuide != newValue) {
                    Log.d(TAG, "偏好变更: 从 $showPreferencesGuide 变为 $newValue")
                    showPreferencesGuide = newValue
                    setAppContent()
                }
            }
        }

        // 启动权限状态检查任务
        startPermissionRefreshTask()
    }

    // ======== 显示设置配置 ========
    private fun configureDisplaySettings() {
        // 设置高刷新率
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.preferredDisplayModeId = getHighestRefreshRate()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.attributes.preferredRefreshRate = getDeviceRefreshRate()
        }

        // 硬件加速以获得更流畅的动画
        window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    }

    // ======== 设置应用内容 ========
    private fun setAppContent() {
        setContent {
            OperitTheme {
                OperitApp(
                        initialNavItem =
                                when {
                                    showPreferencesGuide -> NavItem.UserPreferencesGuide
                                    navigateToShizukuScreen -> NavItem.ShizukuCommands
                                    else -> NavItem.AiChat
                                },
                        toolHandler = toolHandler
                )
            }
        }
    }

    // ======== 统一权限流程管理 ========
    private fun startPermissionFlow() {
        Log.d(TAG, "Starting permission flow")
        requestBasicPermissions()
    }

    private fun continuePermissionFlow() {
        when {
            !permissionState.basicPermissionsRequested -> requestBasicPermissions()
            !permissionState.storagePermissionRequested -> checkManageExternalStoragePermission()
            !permissionState.overlayPermissionRequested -> checkOverlayPermission()
            !permissionState.batteryOptimizationRequested -> checkBatteryOptimizationExemption()
            else -> initializeShizuku() // 所有权限流程完成后初始化Shizuku
        }
    }

    private fun requestBasicPermissions() {
        Log.d(TAG, "Requesting basic permissions for SDK ${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // 低于Android 6.0，权限在安装时已授予
            permissionState.basicPermissionsRequested = true
            continuePermissionFlow()
            return
        }

        val permissionsToRequest =
                requiredPermissions
                        .filter {
                            ContextCompat.checkSelfPermission(this, it) !=
                                    PackageManager.PERMISSION_GRANTED
                        }
                        .toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            Log.d(TAG, "All basic permissions already granted")
            permissionState.basicPermissionsRequested = true
            continuePermissionFlow()
        } else {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun checkManageExternalStoragePermission() {
        // Android 11+需要特殊存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "Checking MANAGE_EXTERNAL_STORAGE permission")

            // 检查是否已有足够权限
            if (hasStoragePermissions()) {
                Log.d(TAG, "Storage permissions already sufficient")
                permissionState.storagePermissionRequested = true
                continuePermissionFlow()
                return
            }

            // 请求管理所有文件权限
            try {
                val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            addCategory("android.intent.category.DEFAULT")
                            data = Uri.parse("package:$packageName")
                        }
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting app specific storage permission", e)
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    storagePermissionLauncher.launch(intent)
                } catch (ex: Exception) {
                    Log.e(TAG, "Error requesting general storage permission", ex)
                    Toast.makeText(this, "无法打开存储权限设置", Toast.LENGTH_LONG).show()

                    // 标记为已处理并继续流程
                    permissionState.storagePermissionRequested = true
                    continuePermissionFlow()
                }
            }
        } else {
            // Android 10及以下不需要特殊的存储管理权限
            Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not needed for this Android version")
            permissionState.storagePermissionRequested = true
            continuePermissionFlow()
        }
    }

    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 检查 MANAGE_EXTERNAL_STORAGE 权限
            Environment.isExternalStorageManager() || hasBasicStoragePermissions()
        } else {
            // 低版本 Android 检查基本存储权限
            hasBasicStoragePermissions()
        }
    }

    private fun hasBasicStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) ==
                            PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
        } else {
            // Android 6-12
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkOverlayPermission() {
        Log.d(TAG, "Checking overlay permission")

        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Overlay permission already granted")
            permissionState.overlayPermissionRequested = true
            continuePermissionFlow()
            return
        }

        try {
            val intent =
                    Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                    )
            overlayPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting overlay permission", e)
            Toast.makeText(this, "无法打开悬浮窗权限设置", Toast.LENGTH_LONG).show()

            // 标记为已处理并继续流程
            permissionState.overlayPermissionRequested = true
            continuePermissionFlow()
        }
    }

    private fun checkBatteryOptimizationExemption() {
        Log.d(TAG, "Checking battery optimization exemption")

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Log.d(TAG, "Battery optimization already exempted")
            permissionState.batteryOptimizationRequested = true
            continuePermissionFlow()
            return
        }

        try {
            val intent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }

            if (intent.resolveActivity(packageManager) != null) {
                batteryOptimizationLauncher.launch(intent)
            } else {
                Log.e(TAG, "Device does not support ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                Toast.makeText(this, "您的设备不支持请求电池优化豁免，后台运行可能受限", Toast.LENGTH_LONG).show()

                // 标记为已处理并继续流程
                permissionState.batteryOptimizationRequested = true
                continuePermissionFlow()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting battery optimization exemption", e)
            Toast.makeText(this, "请求电池优化豁免时出错: ${e.message}", Toast.LENGTH_LONG).show()

            // 标记为已处理并继续流程
            permissionState.batteryOptimizationRequested = true
            continuePermissionFlow()
        }
    }

    // ======== Shizuku相关功能 ========
    private fun initializeShizuku() {
        Log.d(TAG, "Initializing Shizuku")

        // 初始化Shizuku绑定
        AdbCommandExecutor.initializeShizuku()

        // 预先提取Shizuku APK以加速安装
        extractShizukuApkIfNeeded()

        // 检查Shizuku状态并决定下一步操作
        updateShizukuStatus()
    }

    private fun extractShizukuApkIfNeeded() {
        try {
            if (!ShizukuInstaller.isApkExtracted(this)) {
                lifecycleScope.launch {
                    Log.d(TAG, "Extracting bundled Shizuku APK...")
                    ShizukuInstaller.extractApkFromAssets(this@MainActivity)
                    Log.d(TAG, "Bundled Shizuku APK extracted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing bundled Shizuku APK", e)
        }
    }

    private fun updateShizukuStatus() {
        if (!AdbCommandExecutor.isShizukuInstalled(this)) {
            Log.d(TAG, "Shizuku not installed")
            Toast.makeText(this, "请先安装Shizuku应用", Toast.LENGTH_LONG).show()
            navigateToShizukuScreen = true
            setAppContent()
            return
        }

        val isRunning = AdbCommandExecutor.isShizukuServiceRunning()
        val hasPermission = AdbCommandExecutor.hasShizukuPermission()
        Log.d(TAG, "Shizuku status: running=$isRunning, permission=$hasPermission")

        if (isRunning && hasPermission) {
            // Shizuku正常运行，尝试启用无障碍服务
            if (!UIHierarchyManager.isAccessibilityServiceEnabled(this) &&
                            !permissionState.accessibilityServiceRequested
            ) {
                checkAndEnableAccessibilityService()
            }
        } else if (!isRunning || !hasPermission) {
            // Shizuku未运行或无权限，跳转到Shizuku页面
            navigateToShizukuScreen = true

            if (!isRunning) {
                Toast.makeText(this, "Shizuku服务未运行，请启动服务", Toast.LENGTH_LONG).show()
            } else if (!hasPermission) {
                Toast.makeText(this, "请授予Shizuku权限", Toast.LENGTH_LONG).show()
            }

            setAppContent()
        }
    }

    private fun isShizukuFullyAvailable(): Boolean {
        return AdbCommandExecutor.isShizukuServiceRunning() &&
                AdbCommandExecutor.hasShizukuPermission()
    }

    private fun checkShizukuStatus() {
        if (AdbCommandExecutor.isShizukuInstalled(this)) {
            val isRunning = AdbCommandExecutor.isShizukuServiceRunning()
            val hasPermission = AdbCommandExecutor.hasShizukuPermission()

            // 仅当状态变化时才更新UI
            if (!isRunning || !hasPermission) {
                navigateToShizukuScreen = true
                setAppContent()
            }
        }
    }

    // ======== 无障碍服务相关功能 ========
    private fun checkAccessibilityServiceEnabled() {
        // 如果服务已启用，直接返回
        if (UIHierarchyManager.isAccessibilityServiceEnabled(this)) {
            Log.d(TAG, "无障碍服务已启用")
            return
        }

        // 检查设置并在适当时尝试启用
        lifecycleScope.launch {
            val apiPreferences = ApiPreferences(this@MainActivity)
            val autoGrantAccessibility = apiPreferences.autoGrantAccessibilityFlow.first()

            if (autoGrantAccessibility &&
                            !permissionState.accessibilityServiceRequested &&
                            isShizukuFullyAvailable()
            ) {
                Log.d(TAG, "自动授权设置已开启，尝试通过ADB启用无障碍服务")
                tryEnableAccessibilityViaAdb()
            } else if (!autoGrantAccessibility) {
                Log.d(TAG, "自动授权设置已关闭，不尝试自动启用无障碍服务")
            }

            permissionState.accessibilityServiceRequested = true
        }
    }

    private fun checkAndEnableAccessibilityService() {
        lifecycleScope.launch {
            val apiPreferences = ApiPreferences(this@MainActivity)
            val autoGrantAccessibility = apiPreferences.autoGrantAccessibilityFlow.first()

            if (autoGrantAccessibility) {
                Log.d(TAG, "Shizuku已可用，自动授权设置已开启，尝试启用无障碍服务")
                tryEnableAccessibilityViaAdb()
            } else {
                Log.d(TAG, "Shizuku已可用，但自动授权设置已关闭，不尝试自动启用无障碍服务")
                permissionState.accessibilityServiceRequested = true
            }
        }
    }

    private fun tryEnableAccessibilityViaAdb() {
        // 如果已启用，避免重复请求
        if (UIHierarchyManager.isAccessibilityServiceEnabled(this)) {
            Log.d(TAG, "无障碍服务已启用，无需再次启用")
            return
        }

        // 如果Shizuku可用，尝试通过ADB启用
        if (isShizukuFullyAvailable()) {
            Log.d(TAG, "通过ADB尝试启用无障碍服务")
            permissionState.accessibilityServiceRequested = true

            lifecycleScope.launch(Dispatchers.IO) {
                val success = UIHierarchyManager.enableAccessibilityServiceViaAdb(this@MainActivity)

                withContext(Dispatchers.Main) {
                    if (success) {
                        Log.d(TAG, "通过ADB成功启用无障碍服务")
                        Toast.makeText(this@MainActivity, "已自动启用无障碍服务", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d(TAG, "通过ADB启用无障碍服务失败")
                        permissionState.accessibilityServiceRequested = false
                    }
                }
            }
        }
    }

    // ======== 工具方法 ========
    private fun startPermissionRefreshTask() {
        lifecycleScope.launch {
            try {
                val hasRequest =
                        toolHandler?.getToolPermissionSystem()?.hasActivePermissionRequest()
                                ?: false
                Log.d(TAG, "Permission check: hasRequest=$hasRequest")
            } catch (e: Exception) {
                Log.e(TAG, "Error in permission check", e)
            }
        }
    }

    // ======== 设置更新管理器 ========
    private fun setupUpdateManager() {
        // 获取UpdateManager实例
        updateManager = UpdateManager.getInstance(this)

        // 观察更新状态
        updateManager.updateStatus.observe(
                this,
                Observer { status ->
                    if (status is UpdateStatus.Available) {
                        showUpdateNotification(status)
                    }
                }
        )

        // 自动检查更新（在权限流程完成后执行）
        lifecycleScope.launch {
            // 延迟几秒，等待应用完全启动
            delay(3000)
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        if (updateCheckPerformed) return
        updateCheckPerformed = true

        val appVersion =
                try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: PackageManager.NameNotFoundException) {
                    "未知"
                }

        // 使用UpdateManager检查更新
        lifecycleScope.launch {
            try {
                updateManager.checkForUpdates(appVersion)
                // 不需要显式处理更新状态，因为我们已经设置了观察者
            } catch (e: Exception) {
                Log.e(TAG, "更新检查失败: ${e.message}")
            }
        }
    }

    private fun showUpdateNotification(updateInfo: UpdateStatus.Available) {
        val currentVersion =
                try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: Exception) {
                    "未知"
                }

        Log.d(TAG, "发现新版本: ${updateInfo.newVersion}，当前版本: $currentVersion")

        // 显示更新提示
        val updateMessage = "发现新版本 ${updateInfo.newVersion}，请前往「关于」页面查看详情"
        Toast.makeText(this, updateMessage, Toast.LENGTH_LONG).show()

        // 可选：显示更详细的通知
        showUpdateDetailNotification(updateInfo)
    }

    // 显示系统通知以提醒用户有更新
    private fun showUpdateDetailNotification(updateInfo: UpdateStatus.Available) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 创建通知渠道
            val channelId = "update_channel"
            val channelName = "应用更新"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel(channelId, channelName, importance)
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            // 创建打开关于页面的Intent
            val aboutIntent =
                    Intent(this, javaClass).apply {
                        putExtra("NAVIGATE_TO_ABOUT", true)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }

            // 创建PendingIntent
            val pendingIntent =
                    android.app.PendingIntent.getActivity(
                            this,
                            0,
                            aboutIntent,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                android.app.PendingIntent.FLAG_IMMUTABLE or
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            } else {
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            }
                    )

            // 创建通知
            val notification =
                    android.app.Notification.Builder(this, channelId)
                            .setContentTitle("发现新版本 ${updateInfo.newVersion}")
                            .setContentText("点击此处前往更新")
                            .setSmallIcon(android.R.drawable.ic_popup_reminder)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build()

            // 显示通知
            notificationManager.notify(100, notification)
        }
    }

    private fun getHighestRefreshRate(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayModes = display?.supportedModes ?: return 0
            var maxRefreshRate = 60f // Default to 60Hz
            var highestModeId = 0

            for (mode in displayModes) {
                if (mode.refreshRate > maxRefreshRate) {
                    maxRefreshRate = mode.refreshRate
                    highestModeId = mode.modeId
                }
            }
            Log.d(TAG, "Selected display mode with refresh rate: $maxRefreshRate Hz")
            return highestModeId
        }
        return 0
    }

    private fun getDeviceRefreshRate(): Float {
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val display =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION") windowManager.defaultDisplay
                }

        var refreshRate = 60f // Default refresh rate

        if (display != null) {
            try {
                @Suppress("DEPRECATION") val modes = display.supportedModes
                for (mode in modes) {
                    if (mode.refreshRate > refreshRate) {
                        refreshRate = mode.refreshRate
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting refresh rate", e)
            }
        }

        Log.d(TAG, "Selected refresh rate: $refreshRate Hz")
        return refreshRate
    }
}

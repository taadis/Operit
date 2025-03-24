package com.ai.assistance.operit

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
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.ui.theme.OperitTheme
import com.ai.assistance.operit.ui.OperitApp
import com.ai.assistance.operit.data.ChatHistoryManager
import com.ai.assistance.operit.data.UserPreferencesManager
import com.ai.assistance.operit.ShizukuInstaller
import com.ai.assistance.operit.navigation.NavItem
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.tools.AIToolHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    // 共享权限状态
    private var permissionsRequested = false
    
    // 悬浮窗权限请求状态
    private var overlayPermissionRequested = false
    
    // 电池优化豁免请求状态
    private var batteryOptimizationExemptionRequested = false
    
    // 无障碍服务请求状态
    private var accessibilityServiceRequested = false
    
    // 初始化导航控制标志
    private var navigateToShizukuScreen = false
    
    // 偏好设置引导标志
    private var showPreferencesGuide = false
    
    // 工具处理器
    private lateinit var toolHandler: AIToolHandler
    
    // 用户偏好管理器
    private lateinit var preferencesManager: UserPreferencesManager
    
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission results: $permissions")
        val allGranted = permissions.entries.all { it.value }
        permissionsRequested = true
        
        // 检查是否已经有管理所有文件的权限（Android 11+）
        val hasAllFilesPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }
        
        if (allGranted || hasAllFilesPermission) {
            // 权限已全部授予或者已有管理所有文件权限，继续检查存储管理权限
            checkManageExternalStoragePermission()
        } else {
            Toast.makeText(this, "需要授予存储权限才能正常使用Shizuku功能", Toast.LENGTH_LONG).show()
            // 尝试继续流程，检查存储权限
            checkManageExternalStoragePermission()
        }
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Storage permission result: ${it.resultCode}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // 所有权限都已授予，检查悬浮窗权限
                checkOverlayPermission()
            } else {
                // 检查用户是否已经授予了基本存储权限
                val hasBasicStoragePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
                
                if (!hasBasicStoragePermissions) {
                    Toast.makeText(this, "未获得所有文件访问权限，某些功能可能受限", Toast.LENGTH_LONG).show()
                }
                
                // 继续检查悬浮窗权限
                checkOverlayPermission()
            }
        } else {
            // 对于低版本Android，继续检查悬浮窗权限
            checkOverlayPermission()
        }
    }
    
    // 悬浮窗权限请求结果
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Overlay permission result: ${it.resultCode}")
        overlayPermissionRequested = true
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Overlay permission granted")
        } else {
            Log.d(TAG, "Overlay permission denied")
            Toast.makeText(this, "未获得悬浮窗权限，某些功能可能受限", Toast.LENGTH_LONG).show()
        }
        // 无论结果如何，继续初始化Shizuku
        initializeShizuku()
    }
    
    // 忽略电池优化权限请求结果
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Battery optimization exemption result: ${it.resultCode}")
        batteryOptimizationExemptionRequested = true
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val packageName = packageName
        
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Log.d(TAG, "Battery optimization exemption granted")
            Toast.makeText(this, "已获得电池优化豁免权限", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, "Battery optimization exemption denied")
            Toast.makeText(this, "未获得电池优化豁免权限，后台运行可能受限", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")
        
        // 初始化工具处理器
        toolHandler = AIToolHandler.getInstance(this)
        toolHandler.registerDefaultTools()
        
        // 初始化用户偏好管理器并直接检查初始化状态
        preferencesManager = UserPreferencesManager(this)
        showPreferencesGuide = !preferencesManager.isPreferencesInitialized()
        Log.d(TAG, "初始化检查: 用户偏好已初始化=${!showPreferencesGuide}，将${if(showPreferencesGuide) "" else "不"}显示引导界面")
        
        // 检查无障碍服务是否已启用
        checkAccessibilityServiceEnabled()
        
        // 监听偏好变化
        lifecycleScope.launch {
            preferencesManager.userPreferencesFlow.collect { preferences ->
                // 只有当状态变化时才更新UI
                val newValue = !preferences.isInitialized
                if (showPreferencesGuide != newValue) {
                    Log.d(TAG, "偏好变更: 从 $showPreferencesGuide 变为 $newValue")
                    showPreferencesGuide = newValue
                    recreateContentView()
                }
            }
        }
        
        // 启动权限状态检查任务
        startPermissionRefreshTask()
        
        // Enable high FPS rendering
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.preferredDisplayModeId = getHighestRefreshRate()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.attributes.preferredRefreshRate = getDeviceRefreshRate()
        }
        
        // For smoother animations with Compose
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        
        // 请求必要的权限
        requestRequiredPermissions()
        
        setContent {
            OperitTheme {
                OperitApp(
                    initialNavItem = when {
                        showPreferencesGuide -> NavItem.UserPreferencesGuide
                        navigateToShizukuScreen -> NavItem.ShizukuCommands
                        else -> NavItem.AiChat
                    },
                    toolHandler = toolHandler
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        
        // 如果还没请求过权限，在onResume中再次尝试请求
        if (!permissionsRequested) {
            requestRequiredPermissions()
        } else if (!overlayPermissionRequested && !Settings.canDrawOverlays(this)) {
            // 如果基本权限已请求但悬浮窗权限尚未请求
            checkOverlayPermission()
        } else if (!batteryOptimizationExemptionRequested) {
            // 检查电池优化豁免
            checkBatteryOptimizationExemption()
        } else {
            // 检查Shizuku状态
            lifecycleScope.launch {
                delay(500) // 稍微延迟，等待界面完全加载
                checkShizukuStatus()
            }
        }
    }
    
    private fun checkShizukuStatus() {
        if (AdbCommandExecutor.isShizukuInstalled(this)) {
            val isRunning = AdbCommandExecutor.isShizukuServiceRunning()
            val hasPermission = AdbCommandExecutor.hasShizukuPermission()
            Log.d(TAG, "Shizuku status: installed=${true}, running=${isRunning}, permission=${hasPermission}")
            
            // 如果已经安装但未运行或没有权限，设置标志以便自动导航到Shizuku页面
            if (!isRunning || !hasPermission) {
                navigateToShizukuScreen = true
                // 重新创建内容视图以应用导航更改
                recreateContentView()
            }
        }
    }
    
    private fun recreateContentView() {
        // 重新设置内容视图以触发导航更改
        setContent {
            OperitTheme {
                OperitApp(
                    initialNavItem = when {
                        showPreferencesGuide -> NavItem.UserPreferencesGuide
                        navigateToShizukuScreen -> NavItem.ShizukuCommands
                        else -> NavItem.AiChat
                    },
                    toolHandler = toolHandler
                )
            }
        }
    }
    
    private fun requestRequiredPermissions() {
        Log.d(TAG, "Requesting permissions for SDK ${Build.VERSION.SDK_INT}")
        permissionsRequested = true
        
        if (Build.VERSION.SDK_INT >= 33) { // Android 13+
            val permissionsToRequest = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
                requestPermissionLauncher.launch(permissionsToRequest)
            } else {
                // 基本权限已授予，检查存储管理权限
                checkManageExternalStoragePermission()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6-12
            val permissionsToRequest = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
                requestPermissionLauncher.launch(permissionsToRequest)
            } else {
                // 基本权限已授予，检查存储管理权限
                checkManageExternalStoragePermission()
            }
        } else {
            // 低于Android 6.0，权限在安装时已授予
            checkManageExternalStoragePermission()
        }
    }
    
    private fun checkManageExternalStoragePermission() {
        // Android 11+需要特殊存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "Checking MANAGE_EXTERNAL_STORAGE permission")
            
            // 检查基本存储权限是否已授予
            val hasBasicStoragePermissions = requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            
            // 如果已经有管理所有文件权限，或已经有足够的基本权限，则跳过请求
            if (Environment.isExternalStorageManager() || hasBasicStoragePermissions) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE permission already granted or basic permissions sufficient")
                // 已有足够的存储权限，检查悬浮窗权限
                checkOverlayPermission()
                return
            }
            
            // 否则，请求管理所有文件权限
            try {
                Log.d(TAG, "Requesting MANAGE_EXTERNAL_STORAGE permission")
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
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
                    // 检查悬浮窗权限
                    checkOverlayPermission()
                }
            }
        } else {
            Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not needed for this Android version")
            // Android 10及以下不需要特殊的存储管理权限，检查悬浮窗权限
            checkOverlayPermission()
        }
    }
    
    /**
     * 检查悬浮窗权限并请求
     */
    private fun checkOverlayPermission() {
        if (!overlayPermissionRequested) {
            Log.d(TAG, "Checking SYSTEM_ALERT_WINDOW permission")
            if (!Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Requesting SYSTEM_ALERT_WINDOW permission")
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting overlay permission", e)
                    Toast.makeText(this, "无法打开悬浮窗权限设置", Toast.LENGTH_LONG).show()
                    // 继续初始化Shizuku
                    initializeShizuku()
                }
            } else {
                Log.d(TAG, "SYSTEM_ALERT_WINDOW permission already granted")
                // 已有悬浮窗权限，继续初始化Shizuku
                initializeShizuku()
            }
        } else {
            Log.d(TAG, "Overlay permission already requested, continuing")
            // 已经请求过悬浮窗权限，继续初始化Shizuku
            initializeShizuku()
        }
    }
    
    private fun initializeShizuku() {
        Log.d(TAG, "Initializing Shizuku")
        // 初始化Shizuku绑定
        AdbCommandExecutor.initializeShizuku()
        
        // 检查是否需要预提取内置的Shizuku APK
        try {
            // 如果文件不存在，预先提取以加速后续安装过程
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
        
        // 如果没有安装Shizuku，提示安装
        if (!AdbCommandExecutor.isShizukuInstalled(this)) {
            Log.d(TAG, "Shizuku not installed")
            Toast.makeText(this, "请先安装Shizuku应用", Toast.LENGTH_LONG).show()
            // 设置导航标志，自动跳转到Shizuku页面
            navigateToShizukuScreen = true
        } else {
            val isRunning = AdbCommandExecutor.isShizukuServiceRunning()
            val hasPermission = AdbCommandExecutor.hasShizukuPermission()
            Log.d(TAG, "Shizuku service running: $isRunning, has permission: $hasPermission")
            
            // 如果已安装但未运行或没有权限，自动跳转到Shizuku页面
            if (!isRunning || !hasPermission) {
                navigateToShizukuScreen = true
                
                if (!isRunning) {
                    Toast.makeText(this, "Shizuku服务未运行，请启动服务", Toast.LENGTH_LONG).show()
                } else if (!hasPermission) {
                    Toast.makeText(this, "请授予Shizuku权限", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // 重新创建内容视图以应用导航更改
        if (navigateToShizukuScreen) {
            lifecycleScope.launch {
                delay(500) // 延迟一下确保初始化完成
                recreateContentView()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
    }
    
    /**
     * Get the highest refresh rate mode available on the device (Android 11+)
     */
    private fun getHighestRefreshRate(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayModes = display?.supportedModes ?: return 0
            var maxRefreshRate = 60f  // Default to 60Hz
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
    
    /**
     * Get the highest refresh rate available on the device (for Android M-Q)
     */
    private fun getDeviceRefreshRate(): Float {
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        
        var refreshRate = 60f // Default refresh rate
        
        if (display != null) {
            try {
                @Suppress("DEPRECATION")
                val modes = display.supportedModes
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
    
    /**
     * 启动权限状态检查任务，确保权限对话框能正常显示
     */
    private fun startPermissionRefreshTask() {
        lifecycleScope.launch {
            try {
                // 检查是否有待处理的权限请求
                val hasRequest = toolHandler?.getToolPermissionSystem()?.hasActivePermissionRequest() ?: false
                Log.d("MainActivity", "Permission check: hasRequest=$hasRequest")
                if (hasRequest) {
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in permission check", e)
            }
        }
    }
    
    /**
     * 检查无障碍服务是否已启用，只在未请求过的情况下显示对话框
     */
    private fun checkAccessibilityServiceEnabled() {
        // 如果服务未启用且未请求过，则显示对话框
        if (!com.ai.assistance.operit.data.UIHierarchyManager.isAccessibilityServiceEnabled(this) && !accessibilityServiceRequested) {
            // 设置标志，表示已经请求过
            accessibilityServiceRequested = true
            
            // 显示对话框提示用户启用无障碍服务
            android.app.AlertDialog.Builder(this)
                .setTitle("需要无障碍服务权限")
                .setMessage("为了提供更快速的UI分析能力，请在设置中启用无障碍服务。这将使AI助手能够更高效地获取屏幕信息，节省您的等待时间。")
                .setPositiveButton("去设置") { _, _ ->
                    com.ai.assistance.operit.data.UIHierarchyManager.openAccessibilitySettings(this)
                }
                .setNegativeButton("稍后再说") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }
    
    /**
     * 检查并请求电池优化豁免
     */
    private fun checkBatteryOptimizationExemption() {
        Log.d(TAG, "Checking battery optimization exemption")
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val packageName = packageName
        
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Log.d(TAG, "Requesting battery optimization exemption")
            try {
                // 创建请求忽略电池优化的Intent
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                
                // 检查Intent是否可以被处理
                if (intent.resolveActivity(packageManager) != null) {
                    batteryOptimizationLauncher.launch(intent)
                } else {
                    Log.e(TAG, "Device does not support ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                    Toast.makeText(
                        this,
                        "您的设备不支持请求电池优化豁免，后台运行可能受限",
                        Toast.LENGTH_LONG
                    ).show()
                    batteryOptimizationExemptionRequested = true
                    
                    // 继续检查Shizuku状态
                    lifecycleScope.launch {
                        delay(500)
                        checkShizukuStatus()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting battery optimization exemption", e)
                Toast.makeText(
                    this,
                    "请求电池优化豁免时出错: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                batteryOptimizationExemptionRequested = true
                
                // 继续检查Shizuku状态
                lifecycleScope.launch {
                    delay(500)
                    checkShizukuStatus()
                }
            }
        } else {
            Log.d(TAG, "Battery optimization already ignored for this app")
            batteryOptimizationExemptionRequested = true
            
            // 已获得豁免，继续检查Shizuku状态
            lifecycleScope.launch {
                delay(500)
                checkShizukuStatus()
            }
        }
    }
}
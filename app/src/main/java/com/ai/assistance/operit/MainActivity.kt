package com.ai.assistance.operit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    // 共享权限状态
    private var permissionsRequested = false
    
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
        
        if (allGranted) {
            // 基本权限授予后，检查存储管理权限
            checkManageExternalStoragePermission()
        } else {
            Toast.makeText(this, "需要授予存储权限才能正常使用Shizuku功能", Toast.LENGTH_LONG).show()
            // 尝试继续初始化，即使某些权限未授予
            initializeShizuku()
        }
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Storage permission result: ${it.resultCode}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // 所有权限都已授予，可以初始化Shizuku
                initializeShizuku()
            } else {
                Toast.makeText(this, "未获得所有文件访问权限，某些功能可能受限", Toast.LENGTH_LONG).show()
                // 尝试继续初始化，即使未授予存储管理权限
                initializeShizuku()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")
        
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
                OperitApp()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        
        // 如果还没请求过权限，在onResume中再次尝试请求
        if (!permissionsRequested) {
            requestRequiredPermissions()
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
            Log.d(TAG, "Shizuku status: installed=${true}, running=${isRunning}")
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
            initializeShizuku()
        }
    }
    
    private fun checkManageExternalStoragePermission() {
        // Android 11+需要特殊存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "Checking MANAGE_EXTERNAL_STORAGE permission")
            if (!Environment.isExternalStorageManager()) {
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
                        // 尝试继续初始化，虽然可能会失败
                        initializeShizuku()
                    }
                }
            } else {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE permission already granted")
                // 已有存储管理权限
                initializeShizuku()
            }
        } else {
            Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not needed for this Android version")
            // Android 10及以下不需要特殊的存储管理权限
            initializeShizuku()
        }
    }
    
    private fun initializeShizuku() {
        Log.d(TAG, "Initializing Shizuku")
        // 初始化Shizuku绑定
        AdbCommandExecutor.initializeShizuku()
        
        // 如果没有安装Shizuku，提示安装
        if (!AdbCommandExecutor.isShizukuInstalled(this)) {
            Log.d(TAG, "Shizuku not installed")
            Toast.makeText(this, "请先安装Shizuku应用", Toast.LENGTH_LONG).show()
        } else {
            // 检查服务是否运行
            val isRunning = AdbCommandExecutor.isShizukuServiceRunning()
            Log.d(TAG, "Shizuku service running: $isRunning")
            if (!isRunning) {
                Toast.makeText(this, "Shizuku服务未运行，请启动服务", Toast.LENGTH_LONG).show()
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
}

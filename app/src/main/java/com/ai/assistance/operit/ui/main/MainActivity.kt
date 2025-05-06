package com.ai.assistance.operit.ui.main

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
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
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingScreenWithState
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingState
import com.ai.assistance.operit.ui.theme.OperitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    // ======== MCP插件状态 ========
    private val pluginLoadingState = PluginLoadingState()

    // ======== 权限定义 ========
    // UpdateManager实例
    private lateinit var updateManager: UpdateManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")

        initializeComponents()
        setupShizukuListener()
        setupPreferencesListener()
        configureDisplaySettings()

        // 设置上下文以便获取插件元数据
        pluginLoadingState.setAppContext(applicationContext)

        // 显示插件加载界面
        pluginLoadingState.show()

        // 初始化并设置更新管理器
        setupUpdateManager()

        // 启动权限流程
        initializeShizuku()

        // 初始化MCP服务器并启动插件
        initializeMCPServer()

        // 设置初始界面
        setAppContent()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
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

    // ======== 初始化MCP服务器并启动插件 ========
    private fun initializeMCPServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 获取MCPLocalServer实例
                val mcpLocalServer = MCPLocalServer.getInstance(applicationContext)

                // 更新状态
                pluginLoadingState.updateMessage("正在启动MCP服务器...")
                pluginLoadingState.updateProgress(0.1f)

                // 启动MCP服务器
                val serverStartSuccess = mcpLocalServer.startServer()

                if (serverStartSuccess) {
                    // 服务器启动成功，更新状态
                    pluginLoadingState.updateMessage("MCP服务器启动成功，正在加载插件...")
                    pluginLoadingState.updateProgress(0.3f)

                    // 等待服务器完全就绪
                    delay(200)

                    try {
                        // 获取MCPRepository实例
                        val mcpRepository = MCPRepository(applicationContext)

                        // 获取已安装的插件列表 (这是一个Set<String>)
                        val installedPluginsSet = mcpRepository.installedPluginIds.first()

                        // 显式转换为List<String>
                        val installedPluginsList = installedPluginsSet.toList()

                        if (installedPluginsSet.isEmpty()) {
                            // 没有安装的插件，直接进入主界面
                            Log.d(TAG, "没有检测到已安装的插件，直接进入主界面")
                            pluginLoadingState.updateMessage("没有检测到已安装的插件")
                            pluginLoadingState.updateProgress(1.0f)

                            // 立即隐藏插件加载界面
                            pluginLoadingState.hide()
                            return@launch
                        }

                        // 设置插件列表，传入List<String>
                        pluginLoadingState.setPlugins(installedPluginsList)

                        // 有安装的插件，使用MCPStarter启动
                        pluginLoadingState.updateMessage("正在启动插件...")
                        pluginLoadingState.updateProgress(0.4f)

                        val mcpStarter = MCPStarter(applicationContext)

                        // 设置启动进度监听器
                        val progressListener =
                                object : MCPStarter.PluginStartProgressListener {
                                    override fun onPluginStarting(
                                            pluginId: String,
                                            index: Int,
                                            total: Int
                                    ) {
                                        // 更新总体状态
                                        pluginLoadingState.updateMessage(
                                                "正在启动插件 ($index/$total)..."
                                        )
                                        pluginLoadingState.updateProgress(
                                                0.4f + 0.6f * (index.toFloat() / total)
                                        )

                                        // 更新特定插件状态
                                        pluginLoadingState.startLoadingPlugin(pluginId)
                                    }

                                    override fun onPluginStarted(
                                            pluginId: String,
                                            success: Boolean,
                                            index: Int,
                                            total: Int
                                    ) {
                                        // 记录插件加载结果
                                        if (success) {
                                            pluginLoadingState.setPluginSuccess(pluginId, "加载成功")
                                        } else {
                                            pluginLoadingState.setPluginFailed(pluginId, "加载失败")
                                        }

                                        // 更新总体进度
                                        pluginLoadingState.updateProgress(
                                                0.4f + 0.6f * (index.toFloat() / total)
                                        )
                                    }

                                    override fun onAllPluginsStarted(
                                            successCount: Int,
                                            totalCount: Int
                                    ) {
                                        // 所有插件加载完成
                                        val successRate =
                                                if (totalCount > 0) {
                                                    (successCount * 100) / totalCount
                                                } else {
                                                    0 // 当没有部署的插件时，成功率为0
                                                }
                                        pluginLoadingState.updateMessage("已完成启动，成功率: $successRate%")
                                        pluginLoadingState.updateProgress(1.0f)

                                        // 延迟一会儿后隐藏进度条
                                        lifecycleScope.launch {
                                            delay(400)
                                            pluginLoadingState.hide()
                                        }
                                    }
                                }

                        // 启动所有插件
                        mcpStarter.startAllDeployedPlugins(progressListener)
                    } catch (e: Exception) {
                        // 处理插件加载过程中的异常
                        Log.e(TAG, "加载插件过程中出错", e)
                        pluginLoadingState.updateMessage("加载插件出错: ${e.message}")
                        pluginLoadingState.updateProgress(1.0f)

                        // 延迟后隐藏
                        lifecycleScope.launch {
                            delay(400)
                            pluginLoadingState.hide()
                        }
                    }
                } else {
                    // 服务器启动失败
                    pluginLoadingState.updateMessage("MCP服务器启动失败")
                    pluginLoadingState.updateProgress(1.0f)

                    // 延迟一会儿后隐藏进度条
                    lifecycleScope.launch {
                        delay(400)
                        pluginLoadingState.hide()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动MCP服务器和插件时出错", e)
                pluginLoadingState.updateMessage("启动过程中出错: ${e.message}")
                pluginLoadingState.updateProgress(1.0f)

                // 延迟一会儿后隐藏进度条
                lifecycleScope.launch {
                    delay(400)
                    pluginLoadingState.hide()
                }
            }
        }
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
                Box {
                    // 主应用界面 (始终存在于底层)
                    OperitApp(
                            initialNavItem =
                                    when {
                                        showPreferencesGuide -> NavItem.UserPreferencesGuide
                                        navigateToShizukuScreen -> NavItem.ShizukuCommands
                                        else -> NavItem.AiChat
                                    },
                            toolHandler = toolHandler
                    )

                    // 插件加载界面 (带有淡出效果)
                    PluginLoadingScreenWithState(
                            loadingState = pluginLoadingState,
                            modifier = Modifier.zIndex(10f) // 确保加载界面在最上层
                    )
                }
            }
        }
    }

    // ======== 统一权限流程管理 ========

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

            // lifecycleScope.launch(Dispatchers.IO) {
            //     val success =
            // UIHierarchyManager.enableAccessibilityServiceViaAdb(this@MainActivity)

            //     withContext(Dispatchers.Main) {
            //         if (success) {
            //             Log.d(TAG, "通过ADB成功启用无障碍服务")
            //             Toast.makeText(this@MainActivity, "已自动启用无障碍服务",
            // Toast.LENGTH_SHORT).show()
            //         } else {
            //             Log.d(TAG, "通过ADB启用无障碍服务失败")
            //             permissionState.accessibilityServiceRequested = false
            //         }
            //     }
            // }
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

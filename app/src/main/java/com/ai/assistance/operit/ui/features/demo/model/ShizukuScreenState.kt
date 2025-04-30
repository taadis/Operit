package com.ai.assistance.operit.ui.features.demo.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** State holder for ShizukuDemoScreen */
data class ShizukuScreenState(
        // Permission states
        val isShizukuInstalled: MutableState<Boolean> = mutableStateOf(false),
        val isShizukuRunning: MutableState<Boolean> = mutableStateOf(false),
        val hasShizukuPermission: MutableState<Boolean> = mutableStateOf(false),
        val isTermuxInstalled: MutableState<Boolean> = mutableStateOf(false),
        val isTermuxAuthorized: MutableState<Boolean> = mutableStateOf(false),
        val hasStoragePermission: MutableState<Boolean> = mutableStateOf(false),
        val hasOverlayPermission: MutableState<Boolean> = mutableStateOf(false),
        val hasBatteryOptimizationExemption: MutableState<Boolean> = mutableStateOf(false),
        val hasAccessibilityServiceEnabled: MutableState<Boolean> = mutableStateOf(false),
        val hasLocationPermission: MutableState<Boolean> = mutableStateOf(false),

        // UI states
        val isRefreshing: MutableState<Boolean> = mutableStateOf(false),
        val showHelp: MutableState<Boolean> = mutableStateOf(false),
        val permissionErrorMessage: MutableState<String?> = mutableStateOf(null),
        val showSampleCommands: MutableState<Boolean> = mutableStateOf(false),
        val showAdbCommandExecutor: MutableState<Boolean> = mutableStateOf(false),
        val showTermuxCommandExecutor: MutableState<Boolean> = mutableStateOf(false),
        val showShizukuWizard: MutableState<Boolean> = mutableStateOf(false),
        val showTermuxWizard: MutableState<Boolean> = mutableStateOf(false),
        val showResultDialogState: MutableState<Boolean> = mutableStateOf(false),

        // Command execution
        val commandText: MutableState<String> = mutableStateOf(""),
        val resultText: MutableState<String> = mutableStateOf("结果将显示在这里"),
        val resultDialogTitle: MutableState<String> = mutableStateOf(""),
        val resultDialogContent: MutableState<String> = mutableStateOf("")
)

// Sample command lists that can be reused
val sampleAdbCommands =
        listOf(
                "getprop ro.build.version.release" to "获取Android版本",
                "pm list packages" to "列出已安装的应用包名",
                "dumpsys battery" to "查看电池状态",
                "settings list system" to "列出系统设置",
                "am start -a android.intent.action.VIEW -d https://www.example.com" to "打开网页",
                "dumpsys activity activities" to "查看活动的Activity",
                "service list" to "列出系统服务",
                "wm size" to "查看屏幕分辨率"
        )

// Predefined Termux commands
val termuxSampleCommands =
        listOf(
                "echo 'Hello Termux'" to "打印Hello Termux",
                "ls -la" to "列出文件和目录",
                "whoami" to "显示当前用户",
                "pkg update" to "更新包管理器",
                "pkg install python" to "安装Python",
                "termux-info" to "显示Termux信息",
                "termux-notification -t '测试通知' -c '这是一条测试通知'" to "发送通知",
                "termux-clipboard-get" to "获取剪贴板内容"
        )

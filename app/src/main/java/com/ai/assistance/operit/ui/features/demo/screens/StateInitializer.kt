package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxInstaller
import com.ai.assistance.operit.ui.features.demo.helpers.*
import com.ai.assistance.operit.ui.features.demo.model.ShizukuScreenState
import com.ai.assistance.operit.ui.features.demo.utils.getTermuxConfigStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "StateInitializer"

/**
 * 初始化状态监听器
 */
fun initStateListeners(
    state: ShizukuScreenState,
    refreshStatus: () -> Unit,
    checkTermuxAuth: () -> Unit
) {
    // 注册Shizuku状态变更监听器
    val shizukuListener: () -> Unit = {
        refreshStatus()
        // 在Shizuku状态变化时检查Termux授权状态
        checkTermuxAuth()
    }
    ShizukuAuthorizer.addStateChangeListener(shizukuListener)

    // 注册Termux状态变更监听器
    val termuxListener: () -> Unit = {
        refreshStatus()
        // 在Termux状态变化时检查授权状态
        checkTermuxAuth()
    }
    TermuxInstaller.addStateChangeListener(termuxListener)
}

/**
 * 移除状态监听器
 */
fun removeStateListeners(
    shizukuListener: () -> Unit,
    termuxListener: () -> Unit
) {
    ShizukuAuthorizer.removeStateChangeListener(shizukuListener)
    TermuxInstaller.removeStateChangeListener(termuxListener)
}

/**
 * 初始化加载状态
 */
fun initializeState(
    context: Context,
    state: ShizukuScreenState,
    scope: CoroutineScope,
    isTunaSourceEnabled: MutableState<Boolean>,
    isPythonInstalled: MutableState<Boolean>,
    isUvInstalled: MutableState<Boolean>,
    isNodeInstalled: MutableState<Boolean>,
    isTermuxBatteryOptimizationExempted: MutableState<Boolean>,
    isTermuxFullyConfigured: MutableState<Boolean>,
    updateConfigStatus: (Boolean) -> Unit,
    checkInstalledComponentsFunc: () -> Unit
) {
    scope.launch {
        Log.d(TAG, "初始化时加载各项权限和配置状态")

        // 刷新基本权限状态，不使用 updateTermuxRunning 回调
        refreshPermissionsAndStatus(
            context = context,
            updateShizukuInstalled = { state.isShizukuInstalled.value = it },
            updateShizukuRunning = { state.isShizukuRunning.value = it },
            updateShizukuPermission = { state.hasShizukuPermission.value = it },
            updateTermuxInstalled = { state.isTermuxInstalled.value = it },
            updateTermuxRunning = { /* 这个回调暂时不使用，在ShizukuDemoScreen中更新isTermuxRunning */ },
            updateStoragePermission = { state.hasStoragePermission.value = it },
            updateLocationPermission = { state.hasLocationPermission.value = it },
            updateOverlayPermission = { state.hasOverlayPermission.value = it },
            updateBatteryOptimizationExemption = { state.hasBatteryOptimizationExemption.value = it },
            updateAccessibilityServiceEnabled = { state.hasAccessibilityServiceEnabled.value = it }
        )

        // 专门检查Shizuku API_V23权限
        if (state.isShizukuInstalled.value && state.isShizukuRunning.value) {
            // 明确检查 moe.shizuku.manager.permission.API_V23 权限
            state.hasShizukuPermission.value = ShizukuAuthorizer.hasShizukuPermission()

            // 如果没有Shizuku权限，强制显示Shizuku向导卡片
            if (!state.hasShizukuPermission.value) {
                Log.d(TAG, "缺少Shizuku API_V23权限，显示Shizuku向导卡片")
                state.showShizukuWizard.value = true
            }
        } else {
            state.hasShizukuPermission.value = false
            state.showShizukuWizard.value = true
        }

        // 检查Termux安装和权限状态
        if (state.isTermuxInstalled.value) {
            // 检查Termux RUN_COMMAND权限
            state.isTermuxAuthorized.value = TermuxAuthorizer.isTermuxAuthorized(context)

            // 如果Termux未授权，显示Termux向导卡片
            if (!state.isTermuxAuthorized.value) {
                Log.d(TAG, "缺少Termux RUN_COMMAND权限或配置，显示Termux向导卡片")
                state.showTermuxWizard.value = true
            } else {
                // 如果Termux已获得权限，进行组件检查
                Log.d(TAG, "Termux权限验证通过，验证组件配置状态")
                
                // 该函数会自动更新持久化状态
                checkInstalledComponentsFunc()
            }
        } else {
            state.isTermuxAuthorized.value = false
            state.showTermuxWizard.value = true
        }
    }
}

/**
 * 检查Termux授权状态
 */
suspend fun checkTermuxAuthState(
    context: Context,
    state: ShizukuScreenState,
    scope: CoroutineScope,
    isTermuxFullyConfigured: MutableState<Boolean>,
    checkInstalledComponentsFunc: () -> Unit
) {
    // 检查Termux授权状态
    if (state.isTermuxInstalled.value) {
        // 检查Termux是否在运行并更新状态
        val isRunning = checkTermuxRunning(context)
        // 这里不使用 state.isTermuxRunning，因为 ShizukuScreenState 中没有这个属性
        // 改为在调用方处理 isTermuxRunning 的更新

        // 更新Termux授权状态
        state.isTermuxAuthorized.value = TermuxAuthorizer.isTermuxAuthorized(context)

        // 如果Termux未授权，确保Termux向导卡片显示
        if (!state.isTermuxAuthorized.value) {
            // 强制显示Termux向导卡片
            state.showTermuxWizard.value = true
        }

        // 只有在授权通过后才检查各项配置
        if (state.isTermuxAuthorized.value) {
            // 即使isTermuxFullyConfigured为true也执行检查，确保状态是最新的
            // 但不会影响初始UI显示，因为isTermuxFullyConfigured已经在初始化时设置
            checkInstalledComponentsFunc()
        }
    }
}

/**
 * 刷新应用权限和组件状态
 */
fun refreshAppStatus(
    context: Context,
    state: ShizukuScreenState,
    scope: CoroutineScope,
    isTermuxFullyConfigured: MutableState<Boolean>,
    checkInstalledComponentsFunc: () -> Unit
) {
    state.isRefreshing.value = true
    
    scope.launch {
        try {
            // 刷新基本权限状态，但不使用 updateTermuxRunning，因为 ShizukuScreenState 没有 isTermuxRunning 属性
            refreshPermissionsAndStatus(
                context = context,
                updateShizukuInstalled = { state.isShizukuInstalled.value = it },
                updateShizukuRunning = { state.isShizukuRunning.value = it },
                updateShizukuPermission = { state.hasShizukuPermission.value = it },
                updateTermuxInstalled = { state.isTermuxInstalled.value = it },
                updateTermuxRunning = { /* 这个回调暂时不使用，在ShizukuDemoScreen中更新isTermuxRunning */ },
                updateStoragePermission = { state.hasStoragePermission.value = it },
                updateLocationPermission = { state.hasLocationPermission.value = it },
                updateOverlayPermission = { state.hasOverlayPermission.value = it },
                updateBatteryOptimizationExemption = { state.hasBatteryOptimizationExemption.value = it },
                updateAccessibilityServiceEnabled = { state.hasAccessibilityServiceEnabled.value = it }
            )

            // 检查Termux授权状态
            if (state.isTermuxInstalled.value) {
                state.isTermuxAuthorized.value = TermuxAuthorizer.isTermuxAuthorized(context)

                // 仅在授权成功时进行组件检查
                if (state.isTermuxAuthorized.value) {
                    // 检查组件状态，该函数会自动更新持久化状态
                    checkInstalledComponentsFunc()
                }
            } else {
                state.isTermuxAuthorized.value = false
            }

            // 给UI更新一些时间
            delay(300)
        } catch (e: Exception) {
            Log.e(TAG, "刷新状态时出错: ${e.message}", e)
        } finally {
            // 刷新完成
            state.isRefreshing.value = false
        }
    }
} 
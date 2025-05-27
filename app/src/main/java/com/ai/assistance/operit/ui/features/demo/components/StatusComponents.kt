package com.ai.assistance.operit.ui.features.demo.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Device info card component */
@Composable
fun DeviceInfoCard(
        androidVersion: String,
        apiLevel: Int,
        deviceModel: String,
        manufacturer: String
) {
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Text("设备信息", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Android版本: $androidVersion (API $apiLevel)")
                        Text("设备型号: $manufacturer $deviceModel")
                }
        }
}

/** Status card that shows all permission statuses */
@Composable
fun PermissionStatusCard(
        isRefreshing: Boolean,
        onRefresh: () -> Unit,
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasAccessibilityServiceEnabled: Boolean,
        hasLocationPermission: Boolean,
        isShizukuInstalled: Boolean,
        isShizukuRunning: Boolean,
        hasShizukuPermission: Boolean,
        isTermuxInstalled: Boolean,
        isTermuxAuthorized: Boolean,
        isTermuxRunning: Boolean = false,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onAccessibilityClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onShizukuClick: () -> Unit,
        onShizukuLongClick: () -> Unit,
        onTermuxClick: () -> Unit,
        onTermuxLongClick: () -> Unit,
        permissionErrorMessage: String?,
        isTunaSourceEnabled: Boolean = false,
        isPythonInstalled: Boolean = false,
        isUvInstalled: Boolean = false,
        isNodeInstalled: Boolean = false
) {
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text("权限状态", style = MaterialTheme.typography.titleMedium)
                                Button(onClick = onRefresh, enabled = !isRefreshing) {
                                        if (isRefreshing) {
                                                Text("正在刷新...")
                                        } else {
                                                Text("刷新")
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 系统权限部分
                        Text(
                                text = "系统权限",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                        )

                        PermissionStatusItem(
                                title = "存储权限",
                                isGranted = hasStoragePermission,
                                onClick = onStoragePermissionClick
                        )

                        PermissionStatusItem(
                                title = "悬浮窗权限",
                                isGranted = hasOverlayPermission,
                                onClick = onOverlayPermissionClick
                        )

                        PermissionStatusItem(
                                title = "电池优化豁免",
                                isGranted = hasBatteryOptimizationExemption,
                                onClick = onBatteryOptimizationClick
                        )

                        PermissionStatusItem(
                                title = "无障碍服务（不建议开）",
                                isGranted = hasAccessibilityServiceEnabled,
                                onClick = onAccessibilityClick
                        )

                        PermissionStatusItem(
                                title = "位置权限",
                                isGranted = hasLocationPermission,
                                onClick = onLocationPermissionClick
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 应用权限部分
                        Text(
                                text = "应用权限",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                        )

                        ShizukuStatusItem(
                                isShizukuInstalled = isShizukuInstalled,
                                isShizukuRunning = isShizukuRunning,
                                hasShizukuPermission = hasShizukuPermission,
                                onClick = onShizukuClick,
                                onLongClick = onShizukuLongClick
                        )

                        // Replace the individual PermissionStatusItems with the TermuxStatusItem
                        TermuxStatusItem(
                                isTermuxInstalled = isTermuxInstalled,
                                isTermuxAuthorized = isTermuxAuthorized,
                                isTermuxRunning = isTermuxRunning,
                                onClick = onTermuxClick,
                                onLongClick = onTermuxLongClick,
                                isTunaSourceEnabled = isTunaSourceEnabled,
                                isPythonInstalled = isPythonInstalled,
                                isUvInstalled = isUvInstalled,
                                isNodeInstalled = isNodeInstalled
                        )

                        // 显示错误消息（如果有）
                        permissionErrorMessage?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = it,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                )
                        }
                }
        }
}

/** Shizuku status item that handles click events */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShizukuStatusItem(
        isShizukuInstalled: Boolean,
        isShizukuRunning: Boolean,
        hasShizukuPermission: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
) {
        val context = LocalContext.current
        
        // 检查Shizuku是否需要更新
        val isShizukuUpdateNeeded = remember {
            try {
                com.ai.assistance.operit.core.tools.system.ShizukuInstaller.isShizukuUpdateNeeded(context)
            } catch (e: Exception) {
                false
            }
        }
        
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                        text = "Shizuku服务",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                )

                val statusText =
                        when {
                                !isShizukuInstalled -> "未安装"
                                !isShizukuRunning -> "未运行"
                                !hasShizukuPermission -> "未授权"
                                isShizukuUpdateNeeded -> "待更新"
                                else -> "已启用"
                        }

                val statusColor =
                        when {
                                !isShizukuInstalled || !isShizukuRunning || !hasShizukuPermission ->
                                        MaterialTheme.colorScheme.error
                                isShizukuUpdateNeeded ->
                                        Color(0xFFFF9800) // 琥珀色表示待更新
                                else -> MaterialTheme.colorScheme.primary
                        }

                Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium
                )
        }
}

/** Termux status item that handles click events */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TermuxStatusItem(
        isTermuxInstalled: Boolean,
        isTermuxAuthorized: Boolean,
        isTermuxRunning: Boolean = false,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        isTunaSourceEnabled: Boolean = false,
        isPythonInstalled: Boolean = false,
        isUvInstalled: Boolean = false,
        isNodeInstalled: Boolean = false
) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                        text = "Termux终端",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                )

                val termuxStatusText =
                        when {
                                !isTermuxInstalled -> "未安装"
                                !isTermuxAuthorized -> "未授权"
                                !isTermuxRunning -> "未运行"
                                isTermuxInstalled &&
                                        isTermuxAuthorized &&
                                        isTermuxRunning &&
                                        (!isTunaSourceEnabled ||
                                                !isPythonInstalled ||
                                                !isUvInstalled ||
                                                !isNodeInstalled) -> "待配置"
                                else -> "已启用"
                        }

                val termuxStatusColor =
                        when {
                                !isTermuxInstalled -> MaterialTheme.colorScheme.error
                                !isTermuxAuthorized -> MaterialTheme.colorScheme.error
                                !isTermuxRunning -> MaterialTheme.colorScheme.error
                                isTermuxInstalled &&
                                        isTermuxAuthorized &&
                                        isTermuxRunning &&
                                        (!isTunaSourceEnabled ||
                                                !isPythonInstalled ||
                                                !isUvInstalled ||
                                                !isNodeInstalled) ->
                                        MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                        }

                Text(
                        text = termuxStatusText,
                        color = termuxStatusColor,
                        style = MaterialTheme.typography.bodyMedium
                )
        }
}

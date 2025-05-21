package com.ai.assistance.operit.ui.features.demo.components

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import kotlinx.coroutines.launch

@Composable
fun PermissionLevelCard(
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
        isTermuxFullyConfigured: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onAccessibilityClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onShizukuClick: () -> Unit,
        onTermuxClick: () -> Unit,
        isRefreshing: Boolean = false,
        onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 获取当前权限级别
    val preferredPermissionLevel =
            androidPermissionPreferences.preferredPermissionLevelFlow.collectAsState(
                    initial = AndroidPermissionLevel.STANDARD
            )

    // 当前显示的权限级别（可能与实际使用的不同）
    var displayedPermissionLevel by remember { mutableStateOf(preferredPermissionLevel.value) }

    // 当组件首次加载时，同步显示级别和实际级别
    LaunchedEffect(Unit) { displayedPermissionLevel = preferredPermissionLevel.value }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题行
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                        onClick = {
                            // 切换到上一个权限级别（仅显示，不设置为活动级别）
                            val levels = AndroidPermissionLevel.values()
                            val currentIndex = levels.indexOf(displayedPermissionLevel)
                            val previousIndex =
                                    if (currentIndex <= 0) levels.size - 1 else currentIndex - 1
                            displayedPermissionLevel = levels[previousIndex]
                        }
                ) { Icon(Icons.Default.ArrowBack, contentDescription = "上一个权限级别") }

                Text(
                        text = "权限级别：${displayedPermissionLevel.name}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                )

                // 添加刷新按钮
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Icon(
                            Icons.Default.Refresh,
                            contentDescription = if (isRefreshing) "正在刷新" else "刷新权限状态"
                    )
                }

                IconButton(
                        onClick = {
                            // 切换到下一个权限级别（仅显示，不设置为活动级别）
                            val levels = AndroidPermissionLevel.values()
                            val currentIndex = levels.indexOf(displayedPermissionLevel)
                            val nextIndex =
                                    if (currentIndex >= levels.size - 1) 0 else currentIndex + 1
                            displayedPermissionLevel = levels[nextIndex]
                        }
                ) { Icon(Icons.Default.ArrowForward, contentDescription = "下一个权限级别") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 权限级别描述
            Text(
                    text = getPermissionLevelDescription(displayedPermissionLevel),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // 显示状态指示条 - 显示当前正在浏览的级别和实际使用的级别
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧显示使用状态
                if (displayedPermissionLevel == preferredPermissionLevel.value) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "当前使用中",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "当前使用中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        "浏览中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 右侧显示激活按钮（仅在当前显示的权限级别不是当前使用的权限级别时）
                if (displayedPermissionLevel != preferredPermissionLevel.value) {
                    OutlinedButton(
                        onClick = {
                            // 将当前选择的权限级别设置为激活状态
                            coroutineScope.launch {
                                androidPermissionPreferences.savePreferredPermissionLevel(displayedPermissionLevel)
                                Toast.makeText(context, "已将${displayedPermissionLevel.name}设为当前活动权限级别", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("设为当前级别", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 根据权限级别显示所需权限，并为当前选中的权限级别添加特殊背景色
            when (displayedPermissionLevel) {
                AndroidPermissionLevel.STANDARD -> {
                    PermissionSectionContainer(
                            isActive =
                                    preferredPermissionLevel.value ==
                                            AndroidPermissionLevel.STANDARD,
                            isCurrentlyDisplayed = true,
                            content = {
                                StandardPermissionSection(
                                        hasStoragePermission = hasStoragePermission,
                                        hasOverlayPermission = hasOverlayPermission,
                                        hasBatteryOptimizationExemption =
                                                hasBatteryOptimizationExemption,
                                        hasLocationPermission = hasLocationPermission,
                                        isTermuxInstalled = isTermuxInstalled,
                                        isTermuxAuthorized = isTermuxAuthorized,
                                        isTermuxFullyConfigured = isTermuxFullyConfigured,
                                        onStoragePermissionClick = onStoragePermissionClick,
                                        onOverlayPermissionClick = onOverlayPermissionClick,
                                        onBatteryOptimizationClick = onBatteryOptimizationClick,
                                        onLocationPermissionClick = onLocationPermissionClick,
                                        onTermuxClick = onTermuxClick
                                )
                            }
                    )
                }
                AndroidPermissionLevel.ACCESSIBILITY -> {
                    PermissionSectionContainer(
                            isActive =
                                    preferredPermissionLevel.value ==
                                            AndroidPermissionLevel.ACCESSIBILITY,
                            isCurrentlyDisplayed = true,
                            content = {
                                AccessibilityPermissionSection(
                                        hasStoragePermission = hasStoragePermission,
                                        hasOverlayPermission = hasOverlayPermission,
                                        hasBatteryOptimizationExemption =
                                                hasBatteryOptimizationExemption,
                                        hasLocationPermission = hasLocationPermission,
                                        isTermuxInstalled = isTermuxInstalled,
                                        isTermuxAuthorized = isTermuxAuthorized,
                                        isTermuxFullyConfigured = isTermuxFullyConfigured,
                                        hasAccessibilityServiceEnabled =
                                                hasAccessibilityServiceEnabled,
                                        onStoragePermissionClick = onStoragePermissionClick,
                                        onOverlayPermissionClick = onOverlayPermissionClick,
                                        onBatteryOptimizationClick = onBatteryOptimizationClick,
                                        onLocationPermissionClick = onLocationPermissionClick,
                                        onTermuxClick = onTermuxClick,
                                        onAccessibilityClick = onAccessibilityClick
                                )
                            }
                    )
                }
                AndroidPermissionLevel.ADMIN -> {
                    PermissionSectionContainer(
                            isActive =
                                    preferredPermissionLevel.value == AndroidPermissionLevel.ADMIN,
                            isCurrentlyDisplayed = true,
                            content = {
                                AdminPermissionSection(
                                        hasStoragePermission = hasStoragePermission,
                                        hasOverlayPermission = hasOverlayPermission,
                                        hasBatteryOptimizationExemption =
                                                hasBatteryOptimizationExemption,
                                        hasLocationPermission = hasLocationPermission,
                                        isTermuxInstalled = isTermuxInstalled,
                                        isTermuxAuthorized = isTermuxAuthorized,
                                        isTermuxFullyConfigured = isTermuxFullyConfigured,
                                        onStoragePermissionClick = onStoragePermissionClick,
                                        onOverlayPermissionClick = onOverlayPermissionClick,
                                        onBatteryOptimizationClick = onBatteryOptimizationClick,
                                        onLocationPermissionClick = onLocationPermissionClick,
                                        onTermuxClick = onTermuxClick
                                )
                            }
                    )
                }
                AndroidPermissionLevel.DEBUGGER -> {
                    PermissionSectionContainer(
                            isActive =
                                    preferredPermissionLevel.value ==
                                            AndroidPermissionLevel.DEBUGGER,
                            isCurrentlyDisplayed = true,
                            content = {
                                DebuggerPermissionSection(
                                        hasStoragePermission = hasStoragePermission,
                                        hasOverlayPermission = hasOverlayPermission,
                                        hasBatteryOptimizationExemption =
                                                hasBatteryOptimizationExemption,
                                        hasLocationPermission = hasLocationPermission,
                                        isTermuxInstalled = isTermuxInstalled,
                                        isTermuxAuthorized = isTermuxAuthorized,
                                        isTermuxFullyConfigured = isTermuxFullyConfigured,
                                        isShizukuInstalled = isShizukuInstalled,
                                        isShizukuRunning = isShizukuRunning,
                                        hasShizukuPermission = hasShizukuPermission,
                                        onStoragePermissionClick = onStoragePermissionClick,
                                        onOverlayPermissionClick = onOverlayPermissionClick,
                                        onBatteryOptimizationClick = onBatteryOptimizationClick,
                                        onLocationPermissionClick = onLocationPermissionClick,
                                        onTermuxClick = onTermuxClick,
                                        onShizukuClick = onShizukuClick
                                )
                            }
                    )
                }
                AndroidPermissionLevel.ROOT -> {
                    PermissionSectionContainer(
                            isActive =
                                    preferredPermissionLevel.value == AndroidPermissionLevel.ROOT,
                            isCurrentlyDisplayed = true,
                            content = {
                                RootPermissionSection(
                                        hasStoragePermission = hasStoragePermission,
                                        hasOverlayPermission = hasOverlayPermission,
                                        hasBatteryOptimizationExemption =
                                                hasBatteryOptimizationExemption,
                                        hasLocationPermission = hasLocationPermission,
                                        isTermuxInstalled = isTermuxInstalled,
                                        isTermuxAuthorized = isTermuxAuthorized,
                                        isTermuxFullyConfigured = isTermuxFullyConfigured,
                                        onStoragePermissionClick = onStoragePermissionClick,
                                        onOverlayPermissionClick = onOverlayPermissionClick,
                                        onBatteryOptimizationClick = onBatteryOptimizationClick,
                                        onLocationPermissionClick = onLocationPermissionClick,
                                        onTermuxClick = onTermuxClick
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionSectionContainer(
        isActive: Boolean,
        isCurrentlyDisplayed: Boolean,
        content: @Composable () -> Unit
) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .let {
                                when {
                                    // 当前活动权限级别用实线边框
                                    isActive ->
                                            it.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(8.dp)
                                            )
                                    // 仅查看的权限级别没有特殊边框
                                    else -> it
                                }
                            }
                            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun StandardPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isTermuxInstalled: Boolean,
        isTermuxAuthorized: Boolean,
        isTermuxFullyConfigured: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onTermuxClick: () -> Unit
) {
    Column {
        Text(
                text = "基础权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                title = "位置权限",
                isGranted = hasLocationPermission,
                onClick = onLocationPermissionClick
        )

        PermissionStatusItem(
                title = "Termux终端",
                isGranted = isTermuxFullyConfigured,
                onClick = onTermuxClick
        )
    }
}

@Composable
private fun AccessibilityPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isTermuxInstalled: Boolean,
        isTermuxAuthorized: Boolean,
        isTermuxFullyConfigured: Boolean,
        hasAccessibilityServiceEnabled: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onTermuxClick: () -> Unit,
        onAccessibilityClick: () -> Unit
) {
    Column {
        Text(
                text = "基础权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                title = "位置权限",
                isGranted = hasLocationPermission,
                onClick = onLocationPermissionClick
        )

        PermissionStatusItem(
                title = "Termux终端",
                isGranted = isTermuxFullyConfigured,
                onClick = onTermuxClick
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
                text = "无障碍权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
        )

        PermissionStatusItem(
                title = "无障碍服务",
                isGranted = hasAccessibilityServiceEnabled,
                onClick = onAccessibilityClick
        )
    }
}

@Composable
private fun AdminPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isTermuxInstalled: Boolean,
        isTermuxAuthorized: Boolean,
        isTermuxFullyConfigured: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onTermuxClick: () -> Unit
) {
    Column {
        Text(
                text = "基础权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                title = "位置权限",
                isGranted = hasLocationPermission,
                onClick = onLocationPermissionClick
        )

        PermissionStatusItem(
                title = "Termux终端",
                isGranted = isTermuxFullyConfigured,
                onClick = onTermuxClick
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
                text = "管理员权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
        )

        // 管理员权限额外要求（现在位置权限已经移动到基础权限）
        Text(
                text = "已具备所有必要的管理员权限",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DebuggerPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isTermuxInstalled: Boolean,
        isTermuxAuthorized: Boolean,
        isTermuxFullyConfigured: Boolean,
        isShizukuInstalled: Boolean,
        isShizukuRunning: Boolean,
        hasShizukuPermission: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onTermuxClick: () -> Unit,
        onShizukuClick: () -> Unit
) {
    Column {
        Text(
                text = "基础权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                title = "位置权限",
                isGranted = hasLocationPermission,
                onClick = onLocationPermissionClick
        )

        PermissionStatusItem(
                title = "Termux终端",
                isGranted = isTermuxFullyConfigured,
                onClick = onTermuxClick
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
                text = "调试权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
        )

        PermissionStatusItem(
                title = "Shizuku服务",
                isGranted = isShizukuInstalled && isShizukuRunning && hasShizukuPermission,
                onClick = onShizukuClick
        )
    }
}

@Composable
private fun RootPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isTermuxInstalled: Boolean,
        isTermuxAuthorized: Boolean,
        isTermuxFullyConfigured: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onTermuxClick: () -> Unit
) {
    Column {
        Text(
                text = "基础权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                title = "位置权限",
                isGranted = hasLocationPermission,
                onClick = onLocationPermissionClick
        )

        PermissionStatusItem(
                title = "Termux终端",
                isGranted = isTermuxFullyConfigured,
                onClick = onTermuxClick
        )

        // Root权限额外说明
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
                text = "Root权限",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
        )

        Text(
                text = "使用Termux终端执行特权命令",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
        )
    }
}

// 获取权限级别的描述
private fun getPermissionLevelDescription(level: AndroidPermissionLevel): String {
    return when (level) {
        AndroidPermissionLevel.STANDARD -> "标准权限：基本的应用运行权限，无需特殊权限"
        AndroidPermissionLevel.ACCESSIBILITY -> "无障碍权限：允许应用模拟用户操作，需要开启无障碍服务"
        AndroidPermissionLevel.ADMIN -> "管理员权限：允许应用访问更多系统功能，如位置服务等"
        AndroidPermissionLevel.DEBUGGER -> "调试权限：允许通过ADB调试功能进行高级操作，需要Shizuku服务"
        AndroidPermissionLevel.ROOT -> "Root权限：允许应用以超级用户身份运行，需要Termux终端支持"
    }
}

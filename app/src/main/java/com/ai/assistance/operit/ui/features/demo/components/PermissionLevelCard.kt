package com.ai.assistance.operit.ui.features.demo.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
        isDeviceRooted: Boolean,
        hasRootAccess: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onAccessibilityClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onShizukuClick: () -> Unit,
        onTermuxClick: () -> Unit,
        onRootClick: () -> Unit,
        isRefreshing: Boolean = false,
        onRefresh: () -> Unit,
        onPermissionLevelChange: (AndroidPermissionLevel) -> Unit = {},
        onPermissionLevelSet: (AndroidPermissionLevel) -> Unit = {}
) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // 获取当前权限级别
        val preferredPermissionLevel =
                androidPermissionPreferences.preferredPermissionLevelFlow.collectAsState(
                        initial = AndroidPermissionLevel.STANDARD
                )

        // 当前显示的权限级别（可能与实际使用的不同）
        var displayedPermissionLevel by remember { 
            mutableStateOf(preferredPermissionLevel.value ?: AndroidPermissionLevel.STANDARD) 
        }

        // 当显示的权限级别变化时，通知父组件
        LaunchedEffect(displayedPermissionLevel) {
                onPermissionLevelChange(displayedPermissionLevel)
        }

        // 动画状态
        val elevation by
                animateDpAsState(
                        targetValue =
                                if (displayedPermissionLevel == preferredPermissionLevel.value) 6.dp
                                else 2.dp,
                        label = "Card Elevation"
                )

        // 当组件首次加载时，同步显示级别和实际级别
        LaunchedEffect(Unit) {
                displayedPermissionLevel = preferredPermissionLevel.value ?: AndroidPermissionLevel.STANDARD
                onPermissionLevelChange(displayedPermissionLevel)
        }

        // 添加一个简单的刷新动画
        var refreshRotation by remember { mutableStateOf(0f) }
        val rotationAngle by
                animateFloatAsState(
                        targetValue = refreshRotation,
                        animationSpec = tween(500),
                        label = "Refresh Rotation"
                )

        // 刷新时触发旋转动画
        LaunchedEffect(isRefreshing) {
                if (isRefreshing) {
                        refreshRotation += 360f
                }
        }

        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                        )
        ) {
                Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // Row with icon and title - modify to left-aligned
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                val icon =
                                        when (displayedPermissionLevel) {
                                                AndroidPermissionLevel.STANDARD ->
                                                        Icons.Default.Shield
                                                AndroidPermissionLevel.ACCESSIBILITY ->
                                                        Icons.Default.Shield
                                                AndroidPermissionLevel.ADMIN -> Icons.Default.Shield
                                                AndroidPermissionLevel.DEBUGGER ->
                                                        Icons.Default.Shield
                                                AndroidPermissionLevel.ROOT -> Icons.Default.Lock
                                                null -> Icons.Default.Shield // 默认使用标准图标
                                        }

                                Icon(
                                        imageVector = icon,
                                        contentDescription = "权限级别图标",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                        text = "权限级别",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                )
                        }

                        // 添加分割线
                        Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )

                        // 权限级别选择器 - 更紧凑的选项卡
                        PermissionLevelSelector(
                                currentLevel = displayedPermissionLevel,
                                activeLevel = preferredPermissionLevel.value,
                                onLevelSelected = { level -> displayedPermissionLevel = level }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 权限级别描述 - 更紧凑的描述区域
                        AnimatedContent(
                                targetState = displayedPermissionLevel,
                                transitionSpec = {
                                        (slideInHorizontally { width -> width } +
                                                fadeIn()) togetherWith
                                                (slideOutHorizontally { width -> -width } +
                                                        fadeOut())
                                },
                                label = "Permission Description Animation"
                        ) { level -> PermissionLevelVisualDescription(level ?: AndroidPermissionLevel.STANDARD) }

                        // 显示状态指示条 - 更紧凑的状态条
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                // 当显示的权限级别与当前活动级别不同时显示设置按钮
                                if (displayedPermissionLevel != preferredPermissionLevel.value) {
                                        Button(
                                                onClick = {
                                                        // 将当前选择的权限级别设置为激活状态
                                                        coroutineScope.launch {
                                                                androidPermissionPreferences
                                                                        .savePreferredPermissionLevel(
                                                                                displayedPermissionLevel
                                                                        )
                                                                Toast.makeText(
                                                                                context,
                                                                                "已将${displayedPermissionLevel.name}设为当前活动权限级别",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()

                                                                // 调用权限级别设置回调，传递刚刚设置的权限级别
                                                                onPermissionLevelSet(
                                                                        displayedPermissionLevel
                                                                )
                                                        }
                                                },
                                                modifier =
                                                        Modifier.align(Alignment.Center)
                                                                .widthIn(max = 120.dp)
                                                                .heightIn(max = 32.dp),
                                                contentPadding =
                                                        PaddingValues(
                                                                horizontal = 8.dp,
                                                                vertical = 4.dp
                                                        ),
                                                shape = RoundedCornerShape(4.dp),
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                        ) { Text("设为当前级别", fontSize = 12.sp) }
                                } else {
                                        Row(
                                                modifier = Modifier.align(Alignment.Center),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "当前使用中",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                        "当前使用中",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Medium
                                                )
                                        }
                                }

                                // 刷新按钮 - 更紧凑
                                IconButton(
                                        onClick = {
                                                refreshRotation += 360f
                                                onRefresh()
                                        },
                                        enabled = !isRefreshing,
                                        modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription =
                                                        if (isRefreshing) "正在刷新" else "刷新权限状态",
                                                modifier =
                                                        Modifier.graphicsLayer(
                                                                        rotationZ = rotationAngle
                                                                )
                                                                .size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                }
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        // 权限内容区域 - 使用动画过渡
                        AnimatedContent(
                                targetState = displayedPermissionLevel,
                                transitionSpec = {
                                        (slideInHorizontally { width -> width } +
                                                fadeIn()) togetherWith
                                                (slideOutHorizontally { width -> -width } +
                                                        fadeOut())
                                },
                                label = "Permission Content Animation"
                        ) { level ->
                                when (level) {
                                        AndroidPermissionLevel.STANDARD -> {
                                                PermissionSectionContainer(
                                                        isActive =
                                                                preferredPermissionLevel.value ==
                                                                        AndroidPermissionLevel
                                                                                .STANDARD,
                                                        isCurrentlyDisplayed = true,
                                                        content = {
                                                                StandardPermissionSection(
                                                                        hasStoragePermission =
                                                                                hasStoragePermission,
                                                                        hasOverlayPermission =
                                                                                hasOverlayPermission,
                                                                        hasBatteryOptimizationExemption =
                                                                                hasBatteryOptimizationExemption,
                                                                        hasLocationPermission =
                                                                                hasLocationPermission,
                                                                        isTermuxInstalled =
                                                                                isTermuxInstalled,
                                                                        isTermuxAuthorized =
                                                                                isTermuxAuthorized,
                                                                        isTermuxFullyConfigured =
                                                                                isTermuxFullyConfigured,
                                                                        onStoragePermissionClick =
                                                                                onStoragePermissionClick,
                                                                        onOverlayPermissionClick =
                                                                                onOverlayPermissionClick,
                                                                        onBatteryOptimizationClick =
                                                                                onBatteryOptimizationClick,
                                                                        onLocationPermissionClick =
                                                                                onLocationPermissionClick,
                                                                        onTermuxClick =
                                                                                onTermuxClick
                                                                )
                                                        }
                                                )
                                        }
                                        AndroidPermissionLevel.ACCESSIBILITY -> {
                                                PermissionSectionContainer(
                                                        isActive =
                                                                preferredPermissionLevel.value ==
                                                                        AndroidPermissionLevel
                                                                                .ACCESSIBILITY,
                                                        isCurrentlyDisplayed = true,
                                                        content = {
                                                                AccessibilityPermissionSection(
                                                                        hasStoragePermission =
                                                                                hasStoragePermission,
                                                                        hasOverlayPermission =
                                                                                hasOverlayPermission,
                                                                        hasBatteryOptimizationExemption =
                                                                                hasBatteryOptimizationExemption,
                                                                        hasLocationPermission =
                                                                                hasLocationPermission,
                                                                        isTermuxInstalled =
                                                                                isTermuxInstalled,
                                                                        isTermuxAuthorized =
                                                                                isTermuxAuthorized,
                                                                        isTermuxFullyConfigured =
                                                                                isTermuxFullyConfigured,
                                                                        hasAccessibilityServiceEnabled =
                                                                                hasAccessibilityServiceEnabled,
                                                                        onStoragePermissionClick =
                                                                                onStoragePermissionClick,
                                                                        onOverlayPermissionClick =
                                                                                onOverlayPermissionClick,
                                                                        onBatteryOptimizationClick =
                                                                                onBatteryOptimizationClick,
                                                                        onLocationPermissionClick =
                                                                                onLocationPermissionClick,
                                                                        onTermuxClick =
                                                                                onTermuxClick,
                                                                        onAccessibilityClick =
                                                                                onAccessibilityClick
                                                                )
                                                        }
                                                )
                                        }
                                        AndroidPermissionLevel.ADMIN -> {
                                                PermissionSectionContainer(
                                                        isActive =
                                                                preferredPermissionLevel.value ==
                                                                        AndroidPermissionLevel
                                                                                .ADMIN,
                                                        isCurrentlyDisplayed = true,
                                                        content = {
                                                                AdminPermissionSection(
                                                                        hasStoragePermission =
                                                                                hasStoragePermission,
                                                                        hasOverlayPermission =
                                                                                hasOverlayPermission,
                                                                        hasBatteryOptimizationExemption =
                                                                                hasBatteryOptimizationExemption,
                                                                        hasLocationPermission =
                                                                                hasLocationPermission,
                                                                        isTermuxInstalled =
                                                                                isTermuxInstalled,
                                                                        isTermuxAuthorized =
                                                                                isTermuxAuthorized,
                                                                        isTermuxFullyConfigured =
                                                                                isTermuxFullyConfigured,
                                                                        onStoragePermissionClick =
                                                                                onStoragePermissionClick,
                                                                        onOverlayPermissionClick =
                                                                                onOverlayPermissionClick,
                                                                        onBatteryOptimizationClick =
                                                                                onBatteryOptimizationClick,
                                                                        onLocationPermissionClick =
                                                                                onLocationPermissionClick,
                                                                        onTermuxClick =
                                                                                onTermuxClick
                                                                )
                                                        }
                                                )
                                        }
                                        AndroidPermissionLevel.DEBUGGER -> {
                                                PermissionSectionContainer(
                                                        isActive =
                                                                preferredPermissionLevel.value ==
                                                                        AndroidPermissionLevel
                                                                                .DEBUGGER,
                                                        isCurrentlyDisplayed = true,
                                                        content = {
                                                                DebuggerPermissionSection(
                                                                        hasStoragePermission =
                                                                                hasStoragePermission,
                                                                        hasOverlayPermission =
                                                                                hasOverlayPermission,
                                                                        hasBatteryOptimizationExemption =
                                                                                hasBatteryOptimizationExemption,
                                                                        hasLocationPermission =
                                                                                hasLocationPermission,
                                                                        isTermuxInstalled =
                                                                                isTermuxInstalled,
                                                                        isTermuxAuthorized =
                                                                                isTermuxAuthorized,
                                                                        isTermuxFullyConfigured =
                                                                                isTermuxFullyConfigured,
                                                                        isShizukuInstalled =
                                                                                isShizukuInstalled,
                                                                        isShizukuRunning =
                                                                                isShizukuRunning,
                                                                        hasShizukuPermission =
                                                                                hasShizukuPermission,
                                                                        onStoragePermissionClick =
                                                                                onStoragePermissionClick,
                                                                        onOverlayPermissionClick =
                                                                                onOverlayPermissionClick,
                                                                        onBatteryOptimizationClick =
                                                                                onBatteryOptimizationClick,
                                                                        onLocationPermissionClick =
                                                                                onLocationPermissionClick,
                                                                        onTermuxClick =
                                                                                onTermuxClick,
                                                                        onShizukuClick =
                                                                                onShizukuClick
                                                                )
                                                        }
                                                )
                                        }
                                        AndroidPermissionLevel.ROOT -> {
                                                PermissionSectionContainer(
                                                        isActive =
                                                                preferredPermissionLevel.value ==
                                                                        AndroidPermissionLevel.ROOT,
                                                        isCurrentlyDisplayed = true,
                                                        content = {
                                                                RootPermissionSection(
                                                                        hasStoragePermission =
                                                                                hasStoragePermission,
                                                                        hasOverlayPermission =
                                                                                hasOverlayPermission,
                                                                        hasBatteryOptimizationExemption =
                                                                                hasBatteryOptimizationExemption,
                                                                        hasLocationPermission =
                                                                                hasLocationPermission,
                                                                        isTermuxInstalled =
                                                                                isTermuxInstalled,
                                                                        isTermuxAuthorized =
                                                                                isTermuxAuthorized,
                                                                        isTermuxFullyConfigured =
                                                                                isTermuxFullyConfigured,
                                                                        isDeviceRooted =
                                                                                isDeviceRooted,
                                                                        hasRootAccess =
                                                                                hasRootAccess,
                                                                        onStoragePermissionClick =
                                                                                onStoragePermissionClick,
                                                                        onOverlayPermissionClick =
                                                                                onOverlayPermissionClick,
                                                                        onBatteryOptimizationClick =
                                                                                onBatteryOptimizationClick,
                                                                        onLocationPermissionClick =
                                                                                onLocationPermissionClick,
                                                                        onTermuxClick =
                                                                                onTermuxClick,
                                                                        onRootClick = onRootClick
                                                                )
                                                        }
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun PermissionLevelSelector(
        currentLevel: AndroidPermissionLevel,
        activeLevel: AndroidPermissionLevel?,
        onLevelSelected: (AndroidPermissionLevel) -> Unit
) {
        val levels = AndroidPermissionLevel.values()

        ScrollableTabRow(
                selectedTabIndex = currentLevel.ordinal,
                edgePadding = 0.dp,
                divider = {},
                contentColor = MaterialTheme.colorScheme.primary,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                        // Draw indicator under the selected tab
                        if (tabPositions.isNotEmpty() && currentLevel.ordinal < tabPositions.size) {
                                Box(
                                        modifier =
                                                Modifier.tabIndicatorOffset(
                                                                tabPositions[currentLevel.ordinal]
                                                        )
                                                        .height(2.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme.primary
                                                        )
                                                        .clip(
                                                                RoundedCornerShape(
                                                                        topStart = 1.dp,
                                                                        topEnd = 1.dp
                                                                )
                                                        )
                                )
                        }
                }
        ) {
                levels.forEach { level ->
                        val isSelected = level == currentLevel
                        val isActive = level == activeLevel

                        Tab(
                                selected = isSelected,
                                onClick = { onLevelSelected(level) },
                                text = {
                                        Text(
                                                text = level.name,
                                                fontSize = 12.sp,
                                                fontWeight =
                                                        if (isSelected) FontWeight.Bold
                                                        else FontWeight.Normal,
                                                color =
                                                        when {
                                                                isSelected ->
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                isActive ->
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.7f
                                                                        )
                                                                else ->
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface.copy(
                                                                                alpha = 0.7f
                                                                        )
                                                        }
                                        )
                                },
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurface
                        )
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
                                                                1.dp,
                                                                MaterialTheme.colorScheme.primary,
                                                                RoundedCornerShape(8.dp)
                                                        )
                                                // 仅查看的权限级别没有特殊边框
                                                else -> it
                                        }
                                }
                                .padding(8.dp)
        ) { content() }
}

// 重新设计权限项，使其更现代和直观
@Composable
fun PermissionStatusItem(title: String, isGranted: Boolean, onClick: () -> Unit) {
        val contentColor =
                if (isGranted) {
                        MaterialTheme.colorScheme.primary
                } else {
                        MaterialTheme.colorScheme.error
                }

        val statusText = if (isGranted) "已授权" else "未授权"

        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                        // 状态指示点
                        Box(
                                modifier =
                                        Modifier.size(6.dp)
                                                .clip(CircleShape)
                                                .background(contentColor)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // 权限名称
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                }

                // 状态文本
                Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                )
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
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                // 使用Surface提供一个轻微的背景色，而不是独立的卡片
                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                PermissionStatusItem(
                                        title = "存储权限",
                                        isGranted = hasStoragePermission,
                                        onClick = onStoragePermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "悬浮窗权限",
                                        isGranted = hasOverlayPermission,
                                        onClick = onOverlayPermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "电池优化豁免",
                                        isGranted = hasBatteryOptimizationExemption,
                                        onClick = onBatteryOptimizationClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "位置权限",
                                        isGranted = hasLocationPermission,
                                        onClick = onLocationPermissionClick
                                )
                        }
                }
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
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                // 添加不支持使用的提示卡片 - 移至顶部
                Surface(
                        color = Color(0xFFFFF8E1), // 浅琥珀色背景
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)) // 琥珀色边框
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800), // 琥珀色图标
                                        modifier = Modifier.size(20.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Text(
                                        text = "现版本不支持使用",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                        ),
                                        color = Color(0xFFE65100) // 深琥珀色文字
                                )
                        }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                PermissionStatusItem(
                                        title = "存储权限",
                                        isGranted = hasStoragePermission,
                                        onClick = onStoragePermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "悬浮窗权限",
                                        isGranted = hasOverlayPermission,
                                        onClick = onOverlayPermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "电池优化豁免",
                                        isGranted = hasBatteryOptimizationExemption,
                                        onClick = onBatteryOptimizationClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "位置权限",
                                        isGranted = hasLocationPermission,
                                        onClick = onLocationPermissionClick
                                )
                        }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = "无障碍权限",
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                PermissionStatusItem(
                                        title = "无障碍服务",
                                        isGranted = hasAccessibilityServiceEnabled,
                                        onClick = onAccessibilityClick
                                )
                        }
                }
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
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // 添加不支持使用的提示卡片 - 移至顶部
                Surface(
                        color = Color(0xFFFFF8E1), // 浅琥珀色背景
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)) // 琥珀色边框
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800), // 琥珀色图标
                                        modifier = Modifier.size(20.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Text(
                                        text = "现版本不支持使用",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                        ),
                                        color = Color(0xFFE65100) // 深琥珀色文字
                                )
                        }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                PermissionStatusItem(
                                        title = "存储权限",
                                        isGranted = hasStoragePermission,
                                        onClick = onStoragePermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "悬浮窗权限",
                                        isGranted = hasOverlayPermission,
                                        onClick = onOverlayPermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "电池优化豁免",
                                        isGranted = hasBatteryOptimizationExemption,
                                        onClick = onBatteryOptimizationClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "位置权限",
                                        isGranted = hasLocationPermission,
                                        onClick = onLocationPermissionClick
                                )
                        }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = "管理员权限",
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
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
        // 获取当前上下文
        val context = LocalContext.current
        
        // 检查Shizuku是否需要更新
        val isShizukuUpdateNeeded = remember {
            try {
                com.ai.assistance.operit.core.tools.system.ShizukuInstaller.isShizukuUpdateNeeded(context)
            } catch (e: Exception) {
                false
            }
        }
        
        Column {
                Text(
                        text = "基础权限",
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                PermissionStatusItem(
                                        title = "存储权限",
                                        isGranted = hasStoragePermission,
                                        onClick = onStoragePermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "悬浮窗权限",
                                        isGranted = hasOverlayPermission,
                                        onClick = onOverlayPermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "电池优化豁免",
                                        isGranted = hasBatteryOptimizationExemption,
                                        onClick = onBatteryOptimizationClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "位置权限",
                                        isGranted = hasLocationPermission,
                                        onClick = onLocationPermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "Termux终端",
                                        isGranted = isTermuxFullyConfigured,
                                        onClick = onTermuxClick
                                )
                        }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = "调试权限",
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                // 自定义Shizuku状态项
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clickable(onClick = onShizukuClick)
                                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                // 状态指示点
                                                Box(
                                                        modifier =
                                                                Modifier.size(6.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                if (isShizukuInstalled && isShizukuRunning && hasShizukuPermission) 
                                                                                        MaterialTheme.colorScheme.primary
                                                                                else 
                                                                                        MaterialTheme.colorScheme.error
                                                                        )
                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // 权限名称
                                                Text(
                                                        text = "Shizuku服务",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                )
                                        }

                                        // 状态文本
                                        val statusText = when {
                                            !isShizukuInstalled -> "未安装"
                                            !isShizukuRunning -> "未运行"
                                            !hasShizukuPermission -> "未授权"
                                            isShizukuUpdateNeeded -> "待更新"
                                            else -> "已授权"
                                        }

                                        Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when {
                                                    !isShizukuInstalled || !isShizukuRunning || !hasShizukuPermission ->
                                                        MaterialTheme.colorScheme.error
                                                    isShizukuUpdateNeeded ->
                                                        Color(0xFFFF9800) // 琥珀色表示待更新
                                                    else ->
                                                        MaterialTheme.colorScheme.primary
                                                }
                                        )
                                }
                        }
                }
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
        isDeviceRooted: Boolean,
        hasRootAccess: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onTermuxClick: () -> Unit,
        onRootClick: () -> Unit
) {
        Column {
                Text(
                        text = "基础权限",
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                PermissionStatusItem(
                                        title = "存储权限",
                                        isGranted = hasStoragePermission,
                                        onClick = onStoragePermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "悬浮窗权限",
                                        isGranted = hasOverlayPermission,
                                        onClick = onOverlayPermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "电池优化豁免",
                                        isGranted = hasBatteryOptimizationExemption,
                                        onClick = onBatteryOptimizationClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "位置权限",
                                        isGranted = hasLocationPermission,
                                        onClick = onLocationPermissionClick
                                )

                                Divider(
                                        thickness = 0.5.dp,
                                        color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.1f
                                                )
                                )

                                PermissionStatusItem(
                                        title = "Termux终端",
                                        isGranted = isTermuxFullyConfigured,
                                        onClick = onTermuxClick
                                )
                        }
                }

                // Root权限额外说明
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = "Root权限",
                        style =
                                MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                // Root权限状态
                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                PermissionStatusItem(
                                        title = "Root访问权限",
                                        isGranted = hasRootAccess,
                                        onClick = onRootClick
                                )
                        }
                }

                // Root权限额外信息
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                        text =
                                                if (hasRootAccess) "应用已获取Root权限，可执行特权命令"
                                                else if (isDeviceRooted) "设备已Root，点击申请应用权限"
                                                else "设备未Root，无法获取Root权限",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                )
                        }
                }
        }
}

// 获取权限级别的描述
private fun getPermissionLevelDescription(level: AndroidPermissionLevel): String {
        return when (level) {
                AndroidPermissionLevel.STANDARD -> "标准权限：基本的应用运行权限，无需特殊权限"
                AndroidPermissionLevel.ACCESSIBILITY -> "无障碍权限：允许应用模拟用户操作，需要开启无障碍服务"
                AndroidPermissionLevel.ADMIN -> "管理员权限：允许应用访问更多系统功能，如位置服务等"
                AndroidPermissionLevel.DEBUGGER -> "调试权限：允许通过ADB调试功能进行高级操作，需要Shizuku服务"
                AndroidPermissionLevel.ROOT -> "Root权限：允许应用以超级用户身份运行，需要设备已Root"
        }
}

@Composable
private fun PermissionLevelVisualDescription(level: AndroidPermissionLevel) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                // 根据不同权限级别显示不同的标题
                val title =
                        when (level) {
                                AndroidPermissionLevel.STANDARD -> "标准权限"
                                AndroidPermissionLevel.ACCESSIBILITY -> "无障碍权限"
                                AndroidPermissionLevel.ADMIN -> "管理员权限"
                                AndroidPermissionLevel.DEBUGGER -> "调试权限"
                                AndroidPermissionLevel.ROOT -> "Root权限"
                                null -> "标准权限" // 默认标题
                        }

                Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                // 权限描述文本
                val description =
                        when (level) {
                                AndroidPermissionLevel.STANDARD -> "基本应用运行权限，无需系统特殊授权"
                                AndroidPermissionLevel.ACCESSIBILITY -> "增加了无障碍服务支持，可模拟屏幕操作"
                                AndroidPermissionLevel.ADMIN -> "添加设备管理员权限，可访问更多系统功能"
                                AndroidPermissionLevel.DEBUGGER -> "通过Shizuku提供ADB级别的系统访问"
                                AndroidPermissionLevel.ROOT -> "超级用户权限，可执行任意系统操作"
                                null -> "基本应用运行权限，无需系统特殊授权" // 默认描述
                        }

                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                )

                // 功能项网格
                FeatureGrid(level)
        }
}

@Composable
private fun FeatureGrid(level: AndroidPermissionLevel) {
        Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                // 在这里定义不同权限级别支持的功能
                val features =
                        listOf(
                                "悬浮窗" to isFeatureSupported(level, true, true, true, true, true),
                                "文件操作" to isFeatureSupported(level, true, true, true, true, true),
                                "Android/data" to
                                        isFeatureSupported(level, false, false, true, true, true),
                                "data/data" to
                                        isFeatureSupported(level, false, false, false, false, true),
                                "屏幕自动点击" to
                                        isFeatureSupported(level, false, true, true, true, true),
                                "系统权限修改" to
                                        isFeatureSupported(level, false, false, false, true, true),
                                "Termux支持" to
                                        isFeatureSupported(level, false, false, true, true, true),
                                "运行JS" to isFeatureSupported(level, true, true, true, true, true),
                                "插件市场MCP" to
                                        isFeatureSupported(level, false, false, false, true, true)
                        )

                // 每行3个功能项
                val rows = features.chunked(3)
                rows.forEach { rowFeatures ->
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                rowFeatures.forEach { (feature, supported) ->
                                        FeatureItem(
                                                name = feature,
                                                isSupported = supported,
                                                modifier = Modifier.weight(1f)
                                        )
                                }
                                // 如果一行不满3个，添加空白占位
                                repeat(3 - rowFeatures.size) { Box(modifier = Modifier.weight(1f)) }
                        }
                }
        }
}

/**
 * 判断特定功能在给定权限级别下是否支持
 *
 * @param level 当前权限级别
 * @param inStandard 在标准权限下是否支持
 * @param inAccessibility 在无障碍权限下是否支持
 * @param inAdmin 在管理员权限下是否支持
 * @param inDebugger 在调试权限下是否支持
 * @param inRoot 在Root权限下是否支持
 * @return 是否支持该功能
 */
private fun isFeatureSupported(
        level: AndroidPermissionLevel?,
        inStandard: Boolean,
        inAccessibility: Boolean,
        inAdmin: Boolean,
        inDebugger: Boolean,
        inRoot: Boolean
): Boolean {
        return when (level) {
                AndroidPermissionLevel.STANDARD -> inStandard
                AndroidPermissionLevel.ACCESSIBILITY -> inAccessibility
                AndroidPermissionLevel.ADMIN -> inAdmin
                AndroidPermissionLevel.DEBUGGER -> inDebugger
                AndroidPermissionLevel.ROOT -> inRoot
                null -> inStandard // 如果级别为null，使用标准权限级别的功能支持情况
        }
}

@Composable
private fun FeatureItem(name: String, isSupported: Boolean, modifier: Modifier = Modifier) {
        Column(
                modifier = modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // 图标背景
                Box(
                        modifier =
                                Modifier.size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                                if (isSupported)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.1f
                                                        )
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.1f
                                                        )
                                        )
                                        .border(
                                                width = 1.dp,
                                                color =
                                                        if (isSupported)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.3f)
                                                        else
                                                                MaterialTheme.colorScheme.onSurface
                                                                        .copy(alpha = 0.3f),
                                                shape = CircleShape
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        // 根据功能名称显示不同的图标
                        val icon =
                                when (name) {
                                        "悬浮窗" -> Icons.Default.Web
                                        "文件操作" -> Icons.Default.Folder
                                        "Android/data" -> Icons.Default.Storage
                                        "data/data" -> Icons.Default.Storage
                                        "屏幕自动点击" -> Icons.Default.TouchApp
                                        "系统权限修改" -> Icons.Default.Settings
                                        "Termux支持" -> Icons.Default.Terminal
                                        "运行JS" -> Icons.Default.Code
                                        "插件市场MCP" -> Icons.Default.Store
                                        else -> Icons.Default.CheckCircle
                                }

                        Icon(
                                imageVector = if (isSupported) icon else Icons.Default.Lock,
                                contentDescription = if (isSupported) "支持" else "不支持",
                                tint =
                                        if (isSupported) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                        )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 功能名称
                Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                                if (isSupported) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        maxLines = 1
                )
        }
}

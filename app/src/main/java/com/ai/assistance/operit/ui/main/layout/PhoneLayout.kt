package com.ai.assistance.operit.ui.main.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.components.AppContent
import com.ai.assistance.operit.ui.main.components.DrawerContent
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Layout for phone devices with a modal navigation drawer */
@Composable
fun PhoneLayout(
        currentScreen: Screen,
        selectedItem: NavItem,
        isLoading: Boolean,
        navItems: List<NavItem>,
        isNetworkAvailable: Boolean,
        networkType: String,
        drawerWidth: Dp,
        navController: androidx.navigation.NavController,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        showFpsCounter: Boolean,
        onScreenChange: (Screen) -> Unit,
        onNavItemChange: (NavItem) -> Unit,
        navigateToTokenConfig: () -> Unit
) {
        // 创建动画化的内容偏移 - 完全匹配抽屉宽度实现无缝滑动
        val animatedOffset by
                animateDpAsState(
                        targetValue =
                                if (drawerState.currentValue == DrawerValue.Open) drawerWidth
                                else 0.dp,
                        animationSpec = tween(durationMillis = 300), // 稍微延长动画时间使其更自然
                        label = "contentOffset"
                )

        // 抽屉动画状态
        val isDrawerOpen =
                drawerState.currentValue == DrawerValue.Open ||
                        drawerState.targetValue == DrawerValue.Open

        // 侧边栏位移动画
        val drawerOffset by
                animateDpAsState(
                        targetValue = if (isDrawerOpen) 0.dp else -drawerWidth,
                        animationSpec = tween(durationMillis = 300),
                        label = "drawerOffset"
                )

        // 阴影大小动画（仅用于侧边栏）
        val sidebarElevation by
                animateDpAsState(
                        targetValue = if (isDrawerOpen) 3.dp else 0.dp, // 减小阴影深度
                        animationSpec = tween(durationMillis = 300),
                        label = "sidebarElevation"
                )

        // 侧边栏相关拖拽状态
        var currentDrag by remember { mutableStateOf(0f) }
        var verticalDrag by remember { mutableStateOf(0f) } // 添加垂直滑动累计值
        val dragThreshold = 40f // 稍微减小滑动阈值，使更容易触发开启

        // 拖拽状态 - 用于控制抽屉拉出和关闭
        val draggableState = rememberDraggableState { delta ->
                currentDrag += delta

                // 如果从屏幕左侧开始拖拽，向右拖动足够距离打开抽屉
                // 并且水平滑动距离大于垂直滑动距离，才触发打开
                if (!isDrawerOpen &&
                                currentDrag > dragThreshold &&
                                Math.abs(currentDrag) > Math.abs(verticalDrag)
                ) {
                        scope.launch {
                                drawerState.open()
                                currentDrag = 0f
                                verticalDrag = 0f
                        }
                }

                // 如果抽屉已打开，向左拖动足够距离关闭抽屉
                if (isDrawerOpen && currentDrag < -dragThreshold) {
                        scope.launch {
                                drawerState.close()
                                currentDrag = 0f
                                verticalDrag = 0f
                        }
                }
        }

        // 使用Box布局来手动控制抽屉和内容的位置关系
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                // 添加全局拖动手势支持，用于处理关闭操作
                                .draggable(
                                        state = draggableState,
                                        orientation = Orientation.Horizontal,
                                        onDragStarted = { startedPosition ->
                                                currentDrag = 0f
                                                verticalDrag = 0f // 重置垂直滑动累计值
                                        },
                                        onDragStopped = {
                                                currentDrag = 0f
                                                verticalDrag = 0f // 重置垂直滑动累计值
                                        }
                                )
                                // 添加垂直方向的手势检测
                                .draggable(
                                        state =
                                                rememberDraggableState { delta ->
                                                        verticalDrag += delta
                                                },
                                        orientation = Orientation.Vertical,
                                        onDragStarted = {
                                                // 不需要额外操作
                                        },
                                        onDragStopped = {
                                                // 不需要额外操作
                                        }
                                )
        ) {
                // 主内容区域，使用动画偏移
                Box(modifier = Modifier.fillMaxSize().offset(x = animatedOffset)) {
                        AppContent(
                                currentScreen = currentScreen,
                                selectedItem = selectedItem,
                                useTabletLayout = false,
                                isTabletSidebarExpanded = false, // Not used in phone layout
                                isLoading = isLoading,
                                navController = navController,
                                scope = scope,
                                drawerState = drawerState,
                                showFpsCounter = showFpsCounter,
                                onScreenChange = onScreenChange,
                                onNavItemChange = onNavItemChange,
                                onToggleSidebar = { /* Not used in phone layout */},
                                navigateToTokenConfig = navigateToTokenConfig
                        )
                }

                // 添加一个小方块，填充圆角和工具栏之间的空隙
                Box(
                        modifier =
                                Modifier.width(16.dp)
                                        .height(64.dp)
                                        .offset(x = drawerOffset + drawerWidth - 16.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .zIndex(1f)
                )

                // 抽屉内容，从左侧滑动进入
                Surface(
                        modifier =
                                Modifier.width(drawerWidth)
                                        .fillMaxHeight()
                                        .offset(x = drawerOffset)
                                        .zIndex(2f),
                        shape =
                                MaterialTheme.shapes.medium.copy(
                                        topEnd = CornerSize(16.dp),
                                        bottomEnd = CornerSize(16.dp),
                                        topStart = CornerSize(0.dp),
                                        bottomStart = CornerSize(0.dp)
                                ),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = sidebarElevation
                ) {
                        DrawerContent(
                                navItems = navItems,
                                currentScreen = currentScreen,
                                selectedItem = selectedItem,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                scope = scope,
                                drawerState = drawerState,
                                onScreenSelected = { screen, item ->
                                        onScreenChange(screen)
                                        onNavItemChange(item)
                                }
                        )
                }

                // 移除黑色遮罩层，改为透明的可点击区域以关闭抽屉
                if (isDrawerOpen) {
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .offset(x = drawerWidth)
                                                .zIndex(0.5f)
                                                .clickable(
                                                        interactionSource =
                                                                remember {
                                                                        MutableInteractionSource()
                                                                },
                                                        indication = null
                                                ) { scope.launch { drawerState.close() } }
                        )
                }
        }
}

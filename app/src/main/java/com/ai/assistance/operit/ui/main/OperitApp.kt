package com.ai.assistance.operit.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen
import com.ai.assistance.operit.ui.main.layout.PhoneLayout
import com.ai.assistance.operit.ui.main.layout.TabletLayout
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OperitApp(initialNavItem: NavItem = NavItem.AiChat, toolHandler: AIToolHandler? = null) {
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Navigation state
        var selectedItem by remember { mutableStateOf<NavItem>(initialNavItem) }
        var currentScreen by remember {
                mutableStateOf<Screen>(
                        when (initialNavItem) {
                                NavItem.AiChat -> Screen.AiChat
                                NavItem.ProblemLibrary -> Screen.ProblemLibrary
                                NavItem.Packages -> Screen.Packages
                                NavItem.Toolbox -> Screen.Toolbox
                                NavItem.ShizukuCommands -> Screen.ShizukuCommands
                                NavItem.Settings -> Screen.Settings
                                NavItem.Help -> Screen.Help
                                NavItem.About -> Screen.About
                                NavItem.TokenConfig -> Screen.TokenConfig
                                NavItem.UserPreferencesGuide -> Screen.UserPreferencesGuide
                                else -> Screen.AiChat
                        }
                )
        }

        // 用于导航到TokenConfig屏幕的函数
        fun navigateToTokenConfig() {
                currentScreen = Screen.TokenConfig
                selectedItem = NavItem.TokenConfig
        }

        // 检查是否为二级界面，并返回上级界面信息
        data class ParentScreenInfo(
                val isSecondaryScreen: Boolean,
                val parentScreen: Screen? = null,
                val parentNavItem: NavItem? = null
        )

        fun getParentScreenInfo(): ParentScreenInfo {
                return when (currentScreen) {
                        // 设置相关的二级界面
                        is Screen.ToolPermission,
                        is Screen.UserPreferencesGuide,
                        is Screen.UserPreferencesSettings,
                        is Screen.ModelParametersSettings,
                        is Screen.ModelPromptsSettings,
                        is Screen.ThemeSettings -> {
                                // 返回到设置主界面
                                ParentScreenInfo(true, Screen.Settings, NavItem.Settings)
                        }
                        // 工具箱相关的二级界面
                        is Screen.FormatConverter,
                        is Screen.FileManager,
                        is Screen.Terminal,
                        is Screen.TerminalAutoConfig,
                        is Screen.AppPermissions -> {
                                // 返回到工具箱主界面
                                ParentScreenInfo(true, Screen.Toolbox, NavItem.Toolbox)
                        }
                        // TokenConfig界面
                        is Screen.TokenConfig -> {
                                // 返回到AI聊天界面
                                ParentScreenInfo(true, Screen.AiChat, NavItem.AiChat)
                        }
                        // 其他主界面不处理返回事件
                        else -> ParentScreenInfo(false)
                }
        }

        // 判断当前是否在二级界面
        val isInSecondaryScreen = getParentScreenInfo().isSecondaryScreen

        // 注册系统返回键处理器，只在二级界面时启用
        BackHandler(
                enabled = isInSecondaryScreen, // 只在二级界面时拦截返回事件
                onBack = {
                        // 处理二级界面的返回导航
                        handleBackNavigation(currentScreen, { screen -> currentScreen = screen })
                }
        )

        var isLoading by remember { mutableStateOf(false) }

        // Tablet mode sidebar state
        var isTabletSidebarExpanded by remember { mutableStateOf(true) }
        var tabletSidebarWidth by remember { mutableStateOf(280.dp) } // 侧边栏默认宽度
        val collapsedTabletSidebarWidth = 64.dp // 收起时的宽度

        // Device screen size calculation
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp

        // Determine if using tablet layout based on screen width
        // Using Material Design 3 guidelines:
        // - Less than 600dp: phone
        // - 600dp and above: tablet
        val useTabletLayout = screenWidthDp >= 600

        // Navigation items
        val navItems =
                listOf(
                        NavItem.AiChat,
                        NavItem.ShizukuCommands,
                        NavItem.Toolbox,
                        NavItem.Settings,
                        NavItem.Packages,
                        NavItem.ProblemLibrary,
                        NavItem.Help,
                        NavItem.About
                )

        val context = LocalContext.current

        // Network state monitoring
        var isNetworkAvailable by remember {
                mutableStateOf(NetworkUtils.isNetworkAvailable(context))
        }
        var networkType by remember { mutableStateOf(NetworkUtils.getNetworkType(context)) }

        // Check user agreement status
        val agreementPreferences = remember { AgreementPreferences(context) }
        var showAgreementScreen by remember {
                mutableStateOf(!agreementPreferences.isAgreementAccepted())
        }

        // Periodically check network status
        LaunchedEffect(Unit) {
                while (true) {
                        isNetworkAvailable = NetworkUtils.isNetworkAvailable(context)
                        networkType = NetworkUtils.getNetworkType(context)
                        delay(10000) // Check every 10 seconds
                }
        }

        // Get FPS counter display setting
        val apiPreferences = remember { ApiPreferences(context) }
        val showFpsCounter = apiPreferences.showFpsCounterFlow.collectAsState(initial = false).value

        // Create an instance of MCPRepository
        val mcpRepository = remember { MCPRepository(context) }

        // Initialize MCP plugin status
        LaunchedEffect(Unit) {
                launch {
                        // First scan local installed plugins
                        mcpRepository.syncInstalledStatus()
                        // Load server list from cache
                        mcpRepository.fetchMCPServers(forceRefresh = false)
                }
        }

        // Display agreement screen if needed
        if (showAgreementScreen) {
                AgreementScreen(
                        onAgreementAccepted = {
                                agreementPreferences.setAgreementAccepted(true)
                                showAgreementScreen = false
                        }
                )
                return
        }

        // Calculate drawer width for phone mode
        val drawerWidth = (screenWidthDp * 0.75).dp // Drawer width is 3/4 of screen width

        // Main app container
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                if (useTabletLayout) {
                        // Tablet layout
                        TabletLayout(
                                currentScreen = currentScreen,
                                selectedItem = selectedItem,
                                isTabletSidebarExpanded = isTabletSidebarExpanded,
                                isLoading = isLoading,
                                navItems = navItems,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                navController = navController,
                                scope = scope,
                                drawerState = drawerState,
                                showFpsCounter = showFpsCounter,
                                tabletSidebarWidth = tabletSidebarWidth,
                                collapsedTabletSidebarWidth = collapsedTabletSidebarWidth,
                                onScreenChange = { screen -> currentScreen = screen },
                                onNavItemChange = { item -> selectedItem = item },
                                onToggleSidebar = {
                                        isTabletSidebarExpanded = !isTabletSidebarExpanded
                                },
                                navigateToTokenConfig = ::navigateToTokenConfig
                        )
                } else {
                        // Phone layout
                        PhoneLayout(
                                currentScreen = currentScreen,
                                selectedItem = selectedItem,
                                isLoading = isLoading,
                                navItems = navItems,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                drawerWidth = drawerWidth,
                                navController = navController,
                                scope = scope,
                                drawerState = drawerState,
                                showFpsCounter = showFpsCounter,
                                onScreenChange = { screen -> currentScreen = screen },
                                onNavItemChange = { item -> selectedItem = item },
                                navigateToTokenConfig = ::navigateToTokenConfig
                        )
                }
        }
}

fun handleBackNavigation(currentScreen: Screen, navigateTo: (Screen) -> Unit): Boolean {
        return when (currentScreen) {
                is Screen.ToolPermission,
                is Screen.UserPreferencesGuide,
                is Screen.UserPreferencesSettings,
                is Screen.ModelParametersSettings,
                is Screen.ModelPromptsSettings -> {
                        navigateTo(Screen.Settings)
                        true
                }
                is Screen.ThemeSettings -> {
                        navigateTo(Screen.UserPreferencesSettings)
                        true
                }
                is Screen.FormatConverter,
                is Screen.FileManager,
                is Screen.Terminal,
                is Screen.TerminalAutoConfig,
                is Screen.AppPermissions,
                is Screen.UIDebugger,
                is Screen.ShellExecutor,
                is Screen.Logcat -> {
                        navigateTo(Screen.Toolbox)
                        true
                }
                else -> false
        }
}

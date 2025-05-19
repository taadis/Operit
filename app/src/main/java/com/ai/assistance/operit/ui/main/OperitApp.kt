package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.common.displays.FpsCounter
import com.ai.assistance.operit.ui.features.about.screens.AboutScreen
import com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen
import com.ai.assistance.operit.ui.features.chat.screens.AIChatScreen
import com.ai.assistance.operit.ui.features.demo.screens.ShizukuDemoScreen
import com.ai.assistance.operit.ui.features.help.screens.HelpScreen
import com.ai.assistance.operit.ui.features.packages.screens.PackageManagerScreen
import com.ai.assistance.operit.ui.features.problems.screens.ProblemLibraryScreen
import com.ai.assistance.operit.ui.features.settings.screens.ModelParametersSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.SettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ThemeSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ToolPermissionSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesGuideScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesSettingsScreen
import com.ai.assistance.operit.ui.features.token.TokenConfigWebViewScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.AppPermissionsToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.FileManagerToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.FormatConverterToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.TerminalAutoConfigToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.TerminalToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ToolboxScreen
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource

// Hierarchical screen representation to replace multiple boolean flags
sealed class Screen {
    // Main screens (primary)
    data object AiChat : Screen()
    data object ProblemLibrary : Screen()
    data object Packages : Screen()
    data object Toolbox : Screen()
    data object ShizukuCommands : Screen()
    data object Settings : Screen()
    data object Help : Screen()
    data object About : Screen()
    data object TokenConfig : Screen() // New screen for token configuration

    // Secondary screens
    data object ToolPermission : Screen()
    data object UserPreferencesGuide : Screen() {
        var profileName: String = ""
        var profileId: String = ""
    }
    data object UserPreferencesSettings : Screen()
    data object ModelParametersSettings : Screen()
    data object ThemeSettings : Screen() // Add new ThemeSettings screen

    // Toolbox secondary screens
    data object FormatConverter : Screen()
    data object FileManager : Screen()
    data object Terminal : Screen()
    data object TerminalAutoConfig : Screen()
    data object AppPermissions : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperitApp(initialNavItem: NavItem = NavItem.AiChat, toolHandler: AIToolHandler? = null) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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

    var isLoading by remember { mutableStateOf(false) }

    // 定义导航抽屉组件
    @Composable
    fun NavigationDrawerItemHeader(title: String) {
        Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
        )
    }

    @Composable
    fun CompactNavigationDrawerItem(
            icon: ImageVector,
            label: String,
            selected: Boolean,
            onClick: () -> Unit
    ) {
        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .height(40.dp),
                onClick = onClick,
                color =
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                shape = MaterialTheme.shapes.small
        ) {
            Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint =
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color =
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 计算窗口尺寸类别 - 用于适配不同屏幕尺寸
    val configuration = LocalConfiguration.current

    // 直接基于屏幕宽度判断设备类型，避免使用WindowSizeClass的私有构造函数
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    // 确定是使用永久导航抽屉（平板）还是模态导航抽屉（手机）
    // 使用Material Design 3指南中的断点：
    // - 小于600dp：手机
    // - 600dp及以上：平板
    val useTabletLayout = screenWidthDp >= 600

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

    // 网络状态 - 使用remember记住状态，避免每次重组时重新获取
    var isNetworkAvailable by remember { mutableStateOf(NetworkUtils.isNetworkAvailable(context)) }
    var networkType by remember { mutableStateOf(NetworkUtils.getNetworkType(context)) }

    // 用户协议状态检查
    val agreementPreferences = remember { AgreementPreferences(context) }
    var showAgreementScreen by remember {
        mutableStateOf(!agreementPreferences.isAgreementAccepted())
    }

    // 周期性检查网络状态 - 每10秒更新一次，而不是每次重组都更新
    LaunchedEffect(Unit) {
        while (true) {
            isNetworkAvailable = NetworkUtils.isNetworkAvailable(context)
            networkType = NetworkUtils.getNetworkType(context)
            delay(10000) // 10秒检查一次
        }
    }

    // 获取聊天历史管理器
    val chatHistoryManager = remember { ChatHistoryManager(context) }
    val chatHistories by chatHistoryManager.chatHistoriesFlow.collectAsState(initial = emptyList())
    val currentChatId by chatHistoryManager.currentChatIdFlow.collectAsState(initial = null)

    // 当前聊天标题
    val currentChatTitle =
            remember(chatHistories, currentChatId) {
                if (currentChatId != null) {
                    chatHistories.find { it.id == currentChatId }?.title ?: ""
                } else {
                    ""
                }
            }

    // 获取FPS显示设置
    val apiPreferences = remember { ApiPreferences(context) }
    val showFpsCounter = apiPreferences.showFpsCounterFlow.collectAsState(initial = false).value

    // Create an instance of MCPRepository
    val mcpRepository = remember { MCPRepository(context) }

    // 应用启动后立即初始化MCP插件状态，确保已安装的插件在首次打开前就被加载
    LaunchedEffect(Unit) {
        launch {
            // 首先扫描本地已安装的插件
            mcpRepository.syncInstalledStatus()
            // 从缓存加载服务器列表
            mcpRepository.fetchMCPServers(forceRefresh = false)
        }
    }

    // 如果需要显示协议页面，先显示协议页面
    if (showAgreementScreen) {
        AgreementScreen(
                onAgreementAccepted = {
                    agreementPreferences.setAgreementAccepted(true)
                    showAgreementScreen = false
                }
        )
        return
    }

    // 声明本地函数，确保它们在OperitApp函数范围内
    @Composable
    fun DrawerContent() {
        // 添加滚动功能的Column
        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(end = if (useTabletLayout) 8.dp else 0.dp) // 在平板模式下增加右边距
        ) {
            // 抽屉标题
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // 网络状态显示
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Icon(
                        imageVector =
                                if (isNetworkAvailable) Icons.Default.Wifi
                                else Icons.Default.WifiOff,
                        contentDescription = "网络状态",
                        tint =
                                if (isNetworkAvailable) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = networkType,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (isNetworkAvailable) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // 分组导航菜单

            // AI功能组
            NavigationDrawerItemHeader("AI功能")

            // AI聊天
            CompactNavigationDrawerItem(
                    icon = NavItem.AiChat.icon,
                    label = stringResource(id = NavItem.AiChat.titleResId),
                    selected = currentScreen is Screen.AiChat,
                    onClick = {
                        selectedItem = NavItem.AiChat
                        currentScreen = Screen.AiChat
                        scope.launch { drawerState.close() }
                    }
            )

            // 问题库
            CompactNavigationDrawerItem(
                    icon = NavItem.ProblemLibrary.icon,
                    label = stringResource(id = NavItem.ProblemLibrary.titleResId),
                    selected = currentScreen is Screen.ProblemLibrary,
                    onClick = {
                        selectedItem = NavItem.ProblemLibrary
                        currentScreen = Screen.ProblemLibrary
                        scope.launch { drawerState.close() }
                    }
            )

            // 包管理
            CompactNavigationDrawerItem(
                    icon = NavItem.Packages.icon,
                    label = stringResource(id = NavItem.Packages.titleResId),
                    selected = currentScreen is Screen.Packages,
                    onClick = {
                        selectedItem = NavItem.Packages
                        currentScreen = Screen.Packages
                        scope.launch { drawerState.close() }
                    }
            )

            // 获取密钥
            CompactNavigationDrawerItem(
                    icon = NavItem.TokenConfig.icon,
                    label = stringResource(id = NavItem.TokenConfig.titleResId),
                    selected = currentScreen is Screen.TokenConfig,
                    onClick = {
                        selectedItem = NavItem.TokenConfig
                        currentScreen = Screen.TokenConfig
                        scope.launch { drawerState.close() }
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 工具组
            NavigationDrawerItemHeader("工具")

            // 工具箱
            CompactNavigationDrawerItem(
                    icon = NavItem.Toolbox.icon,
                    label = stringResource(id = NavItem.Toolbox.titleResId),
                    selected = currentScreen is Screen.Toolbox,
                    onClick = {
                        selectedItem = NavItem.Toolbox
                        currentScreen = Screen.Toolbox
                        scope.launch { drawerState.close() }
                    }
            )

            // Shizuku命令
            CompactNavigationDrawerItem(
                    icon = NavItem.ShizukuCommands.icon,
                    label = stringResource(id = NavItem.ShizukuCommands.titleResId),
                    selected = currentScreen is Screen.ShizukuCommands,
                    onClick = {
                        selectedItem = NavItem.ShizukuCommands
                        currentScreen = Screen.ShizukuCommands
                        scope.launch { drawerState.close() }
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 系统组
            NavigationDrawerItemHeader("系统")

            // 设置
            CompactNavigationDrawerItem(
                    icon = NavItem.Settings.icon,
                    label = stringResource(id = NavItem.Settings.titleResId),
                    selected = currentScreen is Screen.Settings,
                    onClick = {
                        selectedItem = NavItem.Settings
                        currentScreen = Screen.Settings
                        scope.launch { drawerState.close() }
                    }
            )

            // 帮助中心
            CompactNavigationDrawerItem(
                    icon = NavItem.Help.icon,
                    label = stringResource(id = NavItem.Help.titleResId),
                    selected = currentScreen is Screen.Help,
                    onClick = {
                        selectedItem = NavItem.Help
                        currentScreen = Screen.Help
                        scope.launch { drawerState.close() }
                    }
            )

            // 关于
            CompactNavigationDrawerItem(
                    icon = NavItem.About.icon,
                    label = stringResource(id = NavItem.About.titleResId),
                    selected = currentScreen is Screen.About,
                    onClick = {
                        selectedItem = NavItem.About
                        currentScreen = Screen.About
                        scope.launch { drawerState.close() }
                    }
            )

            // 为了在底部留出一些空间，避免最后一个选项贴底
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    fun AppContent() {
        // Get background image state
        val context = LocalContext.current
        val preferencesManager = remember { UserPreferencesManager(context) }
        val useBackgroundImage by
                preferencesManager.useBackgroundImage.collectAsState(initial = false)
        val backgroundImageUri by
                preferencesManager.backgroundImageUri.collectAsState(initial = null)
        val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null

        // 内容区域仅包含顶部应用栏和主内容，不再使用Scaffold
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(
                                        Color.Transparent
                                ) // Explicitly set transparent background
        ) {
            // 单一工具栏 - 使用小型化的设计
            SmallTopAppBar(
                    title = {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                    when (currentScreen) {
                                        is Screen.ToolPermission ->
                                                stringResource(id = R.string.tool_permissions)
                                        is Screen.UserPreferencesGuide ->
                                                stringResource(id = R.string.user_preferences_guide)
                                        is Screen.UserPreferencesSettings ->
                                                stringResource(
                                                        id = R.string.user_preferences_settings
                                                )
                                        is Screen.ModelParametersSettings -> "模型参数设置"
                                        is Screen.ThemeSettings -> "主题设置"
                                        is Screen.FormatConverter -> "万能格式转换"
                                        is Screen.FileManager -> "文件管理器"
                                        is Screen.Terminal -> "命令终端"
                                        is Screen.TerminalAutoConfig -> "终端自动配置"
                                        is Screen.AppPermissions -> "应用权限管理"
                                        else -> stringResource(id = selectedItem.titleResId)
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                            )

                            // 显示当前聊天标题（仅在AI对话页面)
                            if (currentScreen is Screen.AiChat && currentChatTitle.isNotBlank()) {
                                Text(
                                        text = "- $currentChatTitle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                                MaterialTheme.colorScheme.onPrimary.copy(
                                                        alpha = 0.8f
                                                ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        // Updated logic to display back button for all secondary screens, including
                        // ThemeSettings
                        if (!useTabletLayout ||
                                        currentScreen is Screen.ToolPermission ||
                                        currentScreen is Screen.UserPreferencesGuide ||
                                        currentScreen is Screen.UserPreferencesSettings ||
                                        currentScreen is Screen.ModelParametersSettings ||
                                        currentScreen is Screen.ThemeSettings ||
                                        currentScreen is Screen.FormatConverter ||
                                        currentScreen is Screen.FileManager ||
                                        currentScreen is Screen.Terminal ||
                                        currentScreen is Screen.TerminalAutoConfig ||
                                        currentScreen is Screen.AppPermissions
                        ) {
                            IconButton(
                                    onClick = {
                                        when (currentScreen) {
                                            is Screen.ThemeSettings,
                                            is Screen.ToolPermission,
                                            is Screen.UserPreferencesGuide,
                                            is Screen.UserPreferencesSettings,
                                            is Screen.ModelParametersSettings -> {
                                                // Return to settings screen
                                                currentScreen = Screen.Settings
                                                selectedItem = NavItem.Settings
                                            }
                                            is Screen.FormatConverter,
                                            is Screen.FileManager,
                                            is Screen.Terminal,
                                            is Screen.TerminalAutoConfig,
                                            is Screen.AppPermissions -> {
                                                // 如果在工具箱二级页面，返回到工具箱页面
                                                currentScreen = Screen.Toolbox
                                            }
                                            else -> {
                                                // 仅在非平板模式下打开抽屉
                                                if (!useTabletLayout) {
                                                    scope.launch { drawerState.open() }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Icon(
                                        if (currentScreen is Screen.ToolPermission ||
                                                        currentScreen is
                                                                Screen.UserPreferencesGuide ||
                                                        currentScreen is
                                                                Screen.UserPreferencesSettings ||
                                                        currentScreen is
                                                                Screen.ModelParametersSettings ||
                                                        currentScreen is Screen.ThemeSettings ||
                                                        currentScreen is Screen.FormatConverter ||
                                                        currentScreen is Screen.FileManager ||
                                                        currentScreen is Screen.Terminal ||
                                                        currentScreen is
                                                                Screen.TerminalAutoConfig ||
                                                        currentScreen is Screen.AppPermissions
                                        )
                                                Icons.Default.ArrowBack
                                        else Icons.Default.Menu,
                                        contentDescription =
                                                when (currentScreen) {
                                                    is Screen.ThemeSettings -> "返回设置"
                                                    is Screen.ToolPermission,
                                                    is Screen.UserPreferencesGuide,
                                                    is Screen.UserPreferencesSettings,
                                                    is Screen.ModelParametersSettings ->
                                                            stringResource(
                                                                    id = R.string.nav_settings
                                                            )
                                                    is Screen.FormatConverter,
                                                    is Screen.FileManager,
                                                    is Screen.Terminal,
                                                    is Screen.TerminalAutoConfig,
                                                    is Screen.AppPermissions -> "返回工具箱"
                                                    else -> stringResource(id = R.string.menu)
                                                },
                                        tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    },
                    colors =
                            TopAppBarDefaults.smallTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                            )
            )

            // 主内容区域
            Surface(
                    modifier = Modifier.fillMaxSize(),
                    color =
                            if (hasBackgroundImage) Color.Transparent
                            else MaterialTheme.colorScheme.background
            ) {
                if (isLoading) {
                    // 加载中状态 - 使用简单的Text替代CircularProgressIndicator
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            text = "...",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                    text = "正在加载...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            is Screen.ToolPermission -> {
                                // 工具权限设置页面
                                ToolPermissionSettingsScreen(
                                        navigateBack = {
                                            currentScreen = Screen.Settings
                                            selectedItem = NavItem.Settings
                                        }
                                )
                            }
                            is Screen.UserPreferencesGuide -> {
                                // 用户偏好引导页面
                                val screen = currentScreen as Screen.UserPreferencesGuide
                                UserPreferencesGuideScreen(
                                        profileName = screen.profileName,
                                        profileId = screen.profileId,
                                        onComplete = {
                                            currentScreen = Screen.Settings
                                            selectedItem = NavItem.Settings
                                        },
                                        navigateToPermissions = {
                                            currentScreen = Screen.ShizukuCommands
                                            selectedItem = NavItem.ShizukuCommands // 直接跳转到权限授予界面
                                        }
                                )
                            }
                            is Screen.UserPreferencesSettings -> {
                                UserPreferencesSettingsScreen(
                                        onNavigateBack = { currentScreen = Screen.Settings },
                                        onNavigateToGuide = { profileName, profileId ->
                                            // 导航到引导页并传递配置信息
                                            val guide = Screen.UserPreferencesGuide
                                            guide.profileName = profileName
                                            guide.profileId = profileId
                                            currentScreen = guide
                                        }
                                )
                            }
                            is Screen.FormatConverter -> {
                                // 格式转换工具屏幕
                                FormatConverterToolScreen(navController = navController)
                            }
                            is Screen.FileManager -> {
                                // 文件管理器屏幕
                                FileManagerToolScreen(navController = navController)
                            }
                            is Screen.Terminal -> {
                                // 终端工具屏幕
                                TerminalToolScreen(navController = navController)
                            }
                            is Screen.TerminalAutoConfig -> {
                                // 终端自动配置屏幕
                                TerminalAutoConfigToolScreen(navController = navController)
                            }
                            is Screen.AppPermissions -> {
                                // 应用权限管理屏幕
                                AppPermissionsToolScreen(navController = navController)
                            }
                            is Screen.AiChat ->
                                    AIChatScreen(onNavigateToTokenConfig = ::navigateToTokenConfig)
                            is Screen.ShizukuCommands -> ShizukuDemoScreen()
                            is Screen.Toolbox -> {
                                // 工具箱页面
                                ToolboxScreen(
                                        navController = navController,
                                        onFormatConverterSelected = {
                                            currentScreen = Screen.FormatConverter
                                        },
                                        onFileManagerSelected = {
                                            currentScreen = Screen.FileManager
                                        },
                                        onTerminalSelected = { currentScreen = Screen.Terminal },
                                        onTerminalAutoConfigSelected = {
                                            currentScreen = Screen.TerminalAutoConfig
                                        },
                                        onAppPermissionsSelected = {
                                            currentScreen = Screen.AppPermissions
                                        }
                                )
                            }
                            is Screen.Settings ->
                                    SettingsScreen(
                                            navigateToToolPermissions = {
                                                currentScreen = Screen.ToolPermission
                                            },
                                            onNavigateToUserPreferences = {
                                                currentScreen = Screen.UserPreferencesSettings
                                            },
                                            navigateToModelParameters = {
                                                currentScreen = Screen.ModelParametersSettings
                                            },
                                            navigateToThemeSettings = {
                                                currentScreen = Screen.ThemeSettings
                                            }
                                    )
                            is Screen.ModelParametersSettings -> ModelParametersSettingsScreen()
                            is Screen.ThemeSettings -> ThemeSettingsScreen()
                            is Screen.Packages -> PackageManagerScreen()
                            is Screen.ProblemLibrary -> {
                                // 问题库页面
                                ProblemLibraryScreen()
                            }
                            is Screen.About -> AboutScreen()
                            is Screen.Help ->
                                    HelpScreen(
                                            onBackPressed = {
                                                currentScreen = Screen.AiChat
                                                selectedItem = NavItem.AiChat
                                            }
                                    )
                            is Screen.TokenConfig -> {
                                // 显示TokenConfigWebViewScreen
                                TokenConfigWebViewScreen(
                                        onNavigateBack = {
                                            currentScreen = Screen.AiChat
                                            selectedItem = NavItem.AiChat
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 应用整体结构 - 根据屏幕尺寸选择不同的布局
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(Color.Transparent) // Explicitly set transparent background
    ) {
        if (useTabletLayout) {
            // 平板布局 - 使用永久导航抽屉
            PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(modifier = Modifier.width(280.dp)) { DrawerContent() }
                    }
            ) { AppContent() }
        } else {
            // 手机布局 - 使用可滑动的导航抽屉，主内容会向左滑动
            val drawerWidth = (screenWidthDp * 0.75).dp // 设置抽屉宽度为屏幕的3/4
            
            // 创建动画化的内容偏移 - 完全匹配抽屉宽度实现无缝滑动
            val animatedOffset = animateDpAsState(
                targetValue = if (drawerState.currentValue == DrawerValue.Open) drawerWidth else 0.dp,
                animationSpec = tween(durationMillis = 200),
                label = "contentOffset"
            ).value
            
            // 抽屉动画状态
            val isDrawerOpen = drawerState.currentValue == DrawerValue.Open || 
                               drawerState.targetValue == DrawerValue.Open

            // 使用Box布局来手动控制抽屉和内容的位置关系
            Box(modifier = Modifier.fillMaxSize()) {
                // 主内容区域，使用动画偏移 - 确保滑动与抽屉同步
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = animatedOffset)
                ) {
                    AppContent()
                }
                
                // 抽屉内容，从左侧滑动进入
                AnimatedVisibility(
                    visible = isDrawerOpen,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it }, 
                        animationSpec = tween(durationMillis = 200)
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(durationMillis = 200)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .width(drawerWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        DrawerContent()
                    }
                }
                
                // 移除黑色遮罩层，改为透明的可点击区域以关闭抽屉
                if (isDrawerOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = drawerWidth)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null // 无视觉指示
                            ) {
                                scope.launch { drawerState.close() }
                            }
                    )
                }
            }
        }

        // 帧率计数器 - 放在右上角
        if (showFpsCounter) {
            FpsCounter(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp)
            )
        }
    }
}

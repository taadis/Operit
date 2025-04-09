package com.ai.assistance.operit.ui.main

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.common.displays.FpsCounter
import com.ai.assistance.operit.ui.features.about.screens.AboutScreen
import com.ai.assistance.operit.ui.features.chat.screens.AIChatScreen
import com.ai.assistance.operit.ui.features.demo.screens.ShizukuDemoScreen
import com.ai.assistance.operit.ui.features.mcp.screens.MCPScreen
import com.ai.assistance.operit.ui.features.packages.screens.PackageManagerScreen
import com.ai.assistance.operit.ui.features.problems.screens.ProblemLibraryScreen
import com.ai.assistance.operit.ui.features.settings.screens.SettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ToolPermissionSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesGuideScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesSettingsScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.FileManagerToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.FormatConverterToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.TerminalToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ToolboxScreen
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperitApp(initialNavItem: NavItem = NavItem.AiChat, toolHandler: AIToolHandler? = null) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf<NavItem>(initialNavItem) }
    var isToolPermissionScreen by remember { mutableStateOf(false) }
    var isUserPreferencesGuideScreen by remember {
        mutableStateOf(initialNavItem == NavItem.UserPreferencesGuide)
    }
    var isUserPreferencesSettingsScreen by remember { mutableStateOf(false) }
    var isMcpScreen by remember { mutableStateOf(false) }

    // 工具箱相关状态
    var isFormatConverterScreen by remember { mutableStateOf(false) }
    var isFileManagerScreen by remember { mutableStateOf(false) }
    var isTerminalToolScreen by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }

    val navItems =
            listOf(
                    NavItem.AiChat,
                    NavItem.ShizukuCommands,
                    NavItem.Toolbox,
                    NavItem.Mcp,
                    NavItem.Settings,
                    NavItem.Packages,
                    NavItem.ProblemLibrary,
                    NavItem.About
            )
    val context = LocalContext.current

    // 网络状态 - 使用remember记住状态，避免每次重组时重新获取
    var isNetworkAvailable by remember { mutableStateOf(NetworkUtils.isNetworkAvailable(context)) }
    var networkType by remember { mutableStateOf(NetworkUtils.getNetworkType(context)) }

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

    // 用户偏好配置导航参数
    var userPreferencesProfileName by remember { mutableStateOf("") }
    var userPreferencesProfileId by remember { mutableStateOf("") }

    // 获取FPS显示设置
    val apiPreferences = remember { ApiPreferences(context) }
    val showFpsCounter = apiPreferences.showFpsCounterFlow.collectAsState(initial = false).value

    // Create an instance of MCPRepository
    val mcpRepository = remember { MCPRepository(context) }

    // 应用整体结构
    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
                drawerContent = {
                    ModalDrawerSheet {
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
                                            if (isNetworkAvailable)
                                                    MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = networkType,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                            if (isNetworkAvailable)
                                                    MaterialTheme.colorScheme.primary
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
                                selected =
                                        selectedItem == NavItem.AiChat && !isToolPermissionScreen,
                                onClick = {
                                    selectedItem = NavItem.AiChat
                                    scope.launch { drawerState.close() }
                                }
                        )

                        // 问题库
                        CompactNavigationDrawerItem(
                                icon = NavItem.ProblemLibrary.icon,
                                label = stringResource(id = NavItem.ProblemLibrary.titleResId),
                                selected =
                                        selectedItem == NavItem.ProblemLibrary &&
                                                !isToolPermissionScreen,
                                onClick = {
                                    selectedItem = NavItem.ProblemLibrary
                                    scope.launch { drawerState.close() }
                                }
                        )

                        // 包管理
                        CompactNavigationDrawerItem(
                                icon = NavItem.Packages.icon,
                                label = stringResource(id = NavItem.Packages.titleResId),
                                selected =
                                        selectedItem == NavItem.Packages && !isToolPermissionScreen,
                                onClick = {
                                    selectedItem = NavItem.Packages
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
                                selected =
                                        selectedItem == NavItem.Toolbox && !isToolPermissionScreen,
                                onClick = {
                                    selectedItem = NavItem.Toolbox
                                    scope.launch { drawerState.close() }
                                }
                        )

                        // Shizuku命令
                        CompactNavigationDrawerItem(
                                icon = NavItem.ShizukuCommands.icon,
                                label = stringResource(id = NavItem.ShizukuCommands.titleResId),
                                selected =
                                        selectedItem == NavItem.ShizukuCommands &&
                                                !isToolPermissionScreen,
                                onClick = {
                                    selectedItem = NavItem.ShizukuCommands
                                    scope.launch { drawerState.close() }
                                }
                        )

                        // MCP 插件市场
                        CompactNavigationDrawerItem(
                                icon = NavItem.Mcp.icon,
                                label = stringResource(id = NavItem.Mcp.titleResId),
                                selected = selectedItem == NavItem.Mcp && !isToolPermissionScreen,
                                onClick = {
                                    selectedItem = NavItem.Mcp
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
                                selected =
                                        selectedItem == NavItem.Settings && !isToolPermissionScreen,
                                onClick = {
                                    selectedItem = NavItem.Settings
                                    scope.launch { drawerState.close() }
                                }
                        )

                        // 关于
                        CompactNavigationDrawerItem(
                                icon = NavItem.About.icon,
                                label = stringResource(id = NavItem.About.titleResId),
                                selected = selectedItem == NavItem.About && !isToolPermissionScreen,
                                onClick = {
                                    selectedItem = NavItem.About
                                    scope.launch { drawerState.close() }
                                }
                        )
                    }
                },
                drawerState = drawerState
        ) {
            // 内容区域仅包含顶部应用栏和主内容，不再使用Scaffold
            Column {
                // 单一工具栏 - 使用小型化的设计
                SmallTopAppBar(
                        title = {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                        when {
                                            isToolPermissionScreen ->
                                                    stringResource(id = R.string.tool_permissions)
                                            isUserPreferencesGuideScreen ->
                                                    stringResource(
                                                            id = R.string.user_preferences_guide
                                                    )
                                            isUserPreferencesSettingsScreen ->
                                                    stringResource(
                                                            id = R.string.user_preferences_settings
                                                    )
                                            isFormatConverterScreen -> "万能格式转换"
                                            isFileManagerScreen -> "文件管理器"
                                            isTerminalToolScreen -> "命令终端"
                                            else -> stringResource(id = selectedItem.titleResId)
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                )

                                // 显示当前聊天标题（仅在AI对话页面且不在工具权限设置页面）
                                if (selectedItem == NavItem.AiChat &&
                                                !isToolPermissionScreen &&
                                                !isUserPreferencesGuideScreen &&
                                                !isUserPreferencesSettingsScreen &&
                                                !isFormatConverterScreen &&
                                                !isFileManagerScreen &&
                                                !isTerminalToolScreen &&
                                                currentChatTitle.isNotBlank()
                                ) {
                                    Text(
                                            text = "- $currentChatTitle",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                    onClick = {
                                        when {
                                            isToolPermissionScreen -> {
                                                // 如果在工具权限设置页面，点击返回按钮返回到设置页面
                                                isToolPermissionScreen = false
                                                selectedItem = NavItem.Settings
                                            }
                                            isUserPreferencesGuideScreen -> {
                                                // 如果在用户偏好引导页面，点击返回按钮返回到设置页面
                                                isUserPreferencesGuideScreen = false
                                                selectedItem = NavItem.Settings
                                            }
                                            isUserPreferencesSettingsScreen -> {
                                                // 如果在用户偏好设置页面，点击返回按钮返回到设置页面
                                                isUserPreferencesSettingsScreen = false
                                                selectedItem = NavItem.Settings
                                            }
                                            isFormatConverterScreen -> {
                                                // 如果在格式转换工具页面，点击返回按钮返回到工具箱页面
                                                isFormatConverterScreen = false
                                            }
                                            isFileManagerScreen -> {
                                                // 如果在文件管理器工具页面，点击返回按钮返回到工具箱页面
                                                isFileManagerScreen = false
                                            }
                                            isTerminalToolScreen -> {
                                                // 如果在终端工具页面，点击返回按钮返回到工具箱页面
                                                isTerminalToolScreen = false
                                            }
                                            else -> {
                                                // 否则打开导航抽屉
                                                scope.launch { drawerState.open() }
                                            }
                                        }
                                    }
                            ) {
                                Icon(
                                        if (isToolPermissionScreen ||
                                                        isUserPreferencesGuideScreen ||
                                                        isUserPreferencesSettingsScreen ||
                                                        isFormatConverterScreen ||
                                                        isFileManagerScreen ||
                                                        isTerminalToolScreen
                                        )
                                                Icons.Default.ArrowBack
                                        else Icons.Default.Menu,
                                        contentDescription =
                                                when {
                                                    isToolPermissionScreen ->
                                                            stringResource(
                                                                    id = R.string.nav_settings
                                                            )
                                                    isUserPreferencesGuideScreen ->
                                                            stringResource(
                                                                    id = R.string.nav_settings
                                                            )
                                                    isUserPreferencesSettingsScreen ->
                                                            stringResource(
                                                                    id = R.string.nav_settings
                                                            )
                                                    isFormatConverterScreen ||
                                                            isFileManagerScreen ||
                                                            isTerminalToolScreen -> "返回工具箱"
                                                    else -> stringResource(id = R.string.menu)
                                                },
                                        tint = Color.White
                                )
                            }
                        },
                        colors =
                                TopAppBarDefaults.smallTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        titleContentColor = Color.White
                                )
                )

                // 主内容区域
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    if (isLoading) {
                        // 加载中状态 - 使用简单的Text替代CircularProgressIndicator
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) {
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
                            if (isToolPermissionScreen) {
                                // 工具权限设置页面
                                ToolPermissionSettingsScreen(
                                        navigateBack = {
                                            isToolPermissionScreen = false
                                            selectedItem = NavItem.Settings
                                        }
                                )
                            } else if (isUserPreferencesGuideScreen) {
                                // 用户偏好引导页面
                                UserPreferencesGuideScreen(
                                        profileName = userPreferencesProfileName,
                                        profileId = userPreferencesProfileId,
                                        onComplete = {
                                            isUserPreferencesGuideScreen = false
                                            selectedItem = NavItem.Settings
                                        },
                                        navigateToPermissions = {
                                            isUserPreferencesGuideScreen = false
                                            selectedItem = NavItem.ShizukuCommands // 直接跳转到权限授予界面
                                        }
                                )
                            } else if (isUserPreferencesSettingsScreen) {
                                UserPreferencesSettingsScreen(
                                        onNavigateBack = {
                                            isUserPreferencesSettingsScreen = false
                                        },
                                        onNavigateToGuide = { profileName, profileId ->
                                            // 导航到引导页并传递配置信息
                                            isUserPreferencesGuideScreen = true
                                            isUserPreferencesSettingsScreen = false

                                            // 创建一个包含profileName和profileId的导航，
                                            // 这需要在UserPreferencesGuideScreen中接收这些参数
                                            userPreferencesProfileName = profileName
                                            userPreferencesProfileId = profileId
                                        }
                                )
                            } else if (isFormatConverterScreen) {
                                // 格式转换工具屏幕
                                FormatConverterToolScreen(navController = navController)
                            } else if (isFileManagerScreen) {
                                // 文件管理器屏幕
                                FileManagerToolScreen(navController = navController)
                            } else if (isTerminalToolScreen) {
                                // 终端工具屏幕
                                TerminalToolScreen(navController = navController)
                            } else {
                                // 主导航页面
                                when (selectedItem) {
                                    NavItem.AiChat -> AIChatScreen()
                                    NavItem.ShizukuCommands -> ShizukuDemoScreen()
                                    NavItem.Toolbox -> {
                                        // 工具箱页面
                                        ToolboxScreen(
                                                navController = navController,
                                                onFormatConverterSelected = {
                                                    isFormatConverterScreen = true
                                                },
                                                onFileManagerSelected = {
                                                    isFileManagerScreen = true
                                                },
                                                onTerminalSelected = { isTerminalToolScreen = true }
                                        )
                                    }
                                    NavItem.Settings ->
                                            SettingsScreen(
                                                    navigateToToolPermissions = {
                                                        isToolPermissionScreen = true
                                                        isUserPreferencesSettingsScreen = false
                                                    },
                                                    onNavigateToUserPreferences = {
                                                        isUserPreferencesSettingsScreen = true
                                                        isUserPreferencesGuideScreen = false
                                                    }
                                            )
                                    NavItem.Packages -> PackageManagerScreen()
                                    NavItem.ToolPermissions -> {
                                        // 不应该直接导航到这里
                                        selectedItem = NavItem.Settings
                                    }
                                    NavItem.UserPreferencesGuide -> {
                                        // 不应该直接导航到这里
                                        selectedItem = NavItem.Settings
                                    }
                                    NavItem.UserPreferencesSettings -> {
                                        // 不应该直接导航到这里
                                        selectedItem = NavItem.Settings
                                    }
                                    NavItem.ProblemLibrary -> {
                                        // 问题库页面
                                        ProblemLibraryScreen()
                                    }
                                    NavItem.About -> AboutScreen()
                                    NavItem.Terminal -> {
                                        // 转到工具箱中的终端工具
                                        selectedItem = NavItem.Toolbox
                                        isTerminalToolScreen = true
                                    }
                                    NavItem.Mcp -> {
                                        // MCP 屏幕
                                        MCPScreen(mcpRepository = mcpRepository)
                                    }
                                    else -> {
                                        // 处理其他情况
                                        selectedItem = NavItem.AiChat
                                    }
                                }
                            }
                        }
                    }
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

/** 导航抽屉分组标题 */
@Composable
private fun NavigationDrawerItemHeader(title: String) {
    Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** 紧凑型导航抽屉项 */
@Composable
private fun CompactNavigationDrawerItem(
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
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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

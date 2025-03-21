package com.ai.assistance.operit.ui

import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.R
import com.ai.assistance.operit.navigation.NavItem
import com.ai.assistance.operit.ui.features.chat.screens.AIChatScreen
import com.ai.assistance.operit.ui.features.settings.screens.SettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ToolPermissionSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesGuideScreen
import com.ai.assistance.operit.ui.features.demo.screens.ShizukuDemoScreen
import com.ai.assistance.operit.util.NetworkUtils
import com.ai.assistance.operit.data.ChatHistoryManager
import com.ai.assistance.operit.tools.AIToolHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperitApp(
    initialNavItem: NavItem = NavItem.AiChat,
    toolHandler: AIToolHandler? = null
) {
    // 状态定义
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf<NavItem>(initialNavItem) }
    var isToolPermissionScreen by remember { mutableStateOf(false) }
    var isUserPreferencesGuideScreen by remember { mutableStateOf(initialNavItem == NavItem.UserPreferencesGuide) }
    val navItems = listOf(NavItem.AiChat, NavItem.ShizukuCommands, NavItem.Settings, NavItem.ToolPermissions)
    val context = LocalContext.current
    
    // 网络状态 - 使用remember记住状态，避免每次重组时重新获取
    var isNetworkAvailable by remember { mutableStateOf(NetworkUtils.isNetworkAvailable(context)) }
    var networkType by remember { mutableStateOf(NetworkUtils.getNetworkType(context)) }
    
    // 周期性检查网络状态 - 每10秒更新一次，而不是每次重组都更新
    LaunchedEffect(Unit) {
        while(true) {
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
    val currentChatTitle = remember(chatHistories, currentChatId) {
        if (currentChatId != null) {
            chatHistories.find { it.id == currentChatId }?.title ?: ""
        } else {
            ""
        }
    }

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
                            imageVector = if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "网络状态",
                            tint = if (isNetworkAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = networkType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isNetworkAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 导航菜单项
                    navItems.forEach { navItem ->
                        NavigationDrawerItem(
                            icon = { 
                                Icon(
                                    navItem.icon, 
                                    contentDescription = null,
                                    tint = if (navItem == selectedItem && !isToolPermissionScreen) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            },
                            label = { 
                                Text(
                                    stringResource(id = navItem.titleResId),
                                    fontWeight = if (navItem == selectedItem && !isToolPermissionScreen) 
                                        FontWeight.Bold 
                                    else 
                                        FontWeight.Normal
                                ) 
                            },
                            selected = navItem == selectedItem && !isToolPermissionScreen,
                            onClick = {
                                selectedItem = navItem
                                isToolPermissionScreen = navItem == NavItem.ToolPermissions
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
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
                                    isToolPermissionScreen -> stringResource(id = R.string.tool_permissions)
                                    isUserPreferencesGuideScreen -> stringResource(id = R.string.user_preferences_guide)
                                    else -> stringResource(id = selectedItem.titleResId)
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            
                            // 显示当前聊天标题（仅在AI对话页面且不在工具权限设置页面）
                            if (selectedItem == NavItem.AiChat && !isToolPermissionScreen && !isUserPreferencesGuideScreen && currentChatTitle.isNotBlank()) {
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
                                    else -> {
                                        // 否则打开导航抽屉
                                        scope.launch { drawerState.open() }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (isToolPermissionScreen || isUserPreferencesGuideScreen) Icons.Default.ArrowBack else Icons.Default.Menu,
                                contentDescription = when {
                                    isToolPermissionScreen -> stringResource(id = R.string.nav_settings)
                                    isUserPreferencesGuideScreen -> stringResource(id = R.string.nav_settings)
                                    else -> stringResource(id = R.string.menu)
                                },
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
                
                // 主内容区域
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        isToolPermissionScreen -> {
                            // 工具权限设置页面
                            ToolPermissionSettingsScreen(
                                navigateBack = { 
                                    isToolPermissionScreen = false
                                    selectedItem = NavItem.Settings
                                }
                            )
                        }
                        isUserPreferencesGuideScreen -> {
                            // 用户偏好引导页面
                            UserPreferencesGuideScreen(
                                onComplete = {
                                    isUserPreferencesGuideScreen = false
                                    selectedItem = NavItem.Settings
                                },
                                navigateToPermissions = {
                                    isUserPreferencesGuideScreen = false
                                    selectedItem = NavItem.ShizukuCommands  // 直接跳转到权限授予界面
                                }
                            )
                        }
                        else -> {
                            // 主导航页面
                            when (selectedItem) {
                                NavItem.AiChat -> AIChatScreen()
                                NavItem.ShizukuCommands -> ShizukuDemoScreen()
                                NavItem.Settings -> SettingsScreen(
                                    navigateToToolPermissions = { 
                                        isToolPermissionScreen = true 
                                    },
                                    onNavigateToUserPreferences = {
                                        isUserPreferencesGuideScreen = true
                                    }
                                )
                                NavItem.ToolPermissions -> {
                                    // 不应该直接导航到这里
                                    selectedItem = NavItem.Settings
                                }
                                NavItem.UserPreferencesGuide -> {
                                    // 不应该直接导航到这里
                                    selectedItem = NavItem.Settings
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} 
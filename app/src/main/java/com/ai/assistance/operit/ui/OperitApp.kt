package com.ai.assistance.operit.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.navigation.NavItem
import com.ai.assistance.operit.ui.features.chat.screens.AIChatScreen
import com.ai.assistance.operit.ui.features.settings.screens.SettingsScreen
import com.ai.assistance.operit.ui.features.demo.screens.ShizukuDemoScreen
import com.ai.assistance.operit.util.NetworkUtils
import com.ai.assistance.operit.data.ChatHistoryManager
import kotlinx.coroutines.launch

// Reference the screen components that will be created
private const val PLACEHOLDER = "Screens to be implemented"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperitApp() {
    // 状态定义
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf<NavItem>(NavItem.AiChat) }
    val navItems = listOf(NavItem.AiChat, NavItem.ShizukuCommands, NavItem.Settings)
    val context = LocalContext.current
    val isNetworkAvailable = NetworkUtils.isNetworkAvailable(context)
    val networkType = NetworkUtils.getNetworkType(context)
    
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
                                    tint = if (navItem == selectedItem) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            },
                            label = { 
                                Text(
                                    stringResource(id = navItem.titleResId),
                                    fontWeight = if (navItem == selectedItem) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            selected = navItem == selectedItem,
                            onClick = {
                                selectedItem = navItem
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
                                stringResource(id = selectedItem.titleResId),
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            // 显示当前聊天标题（仅在AI对话页面）
                            if (selectedItem == NavItem.AiChat && currentChatTitle.isNotBlank()) {
                                Text(
                                    text = "- $currentChatTitle",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Icon(
                                Icons.Default.Menu, 
                                contentDescription = stringResource(id = R.string.menu),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                
                // 主内容区域
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (selectedItem) {
                        NavItem.AiChat -> AIChatScreen()
                        NavItem.ShizukuCommands -> ShizukuDemoScreen()
                        NavItem.Settings -> SettingsScreen()
                    }
                }
            }
        }
    }
} 
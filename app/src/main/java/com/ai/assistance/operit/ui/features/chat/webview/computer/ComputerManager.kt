package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.Serializable
import android.util.Log

@Serializable
data class ComputerTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String
)

@Composable
fun ComputerManager(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
) {
    val context = LocalContext.current
    val manager = ComputerDesktopManager

    // Sync progress from manager to a local state to trigger recomposition
    val webViewProgress by manager.webViewHandler.observeProgress()

    // Start the computer web server when the manager is composed
    LaunchedEffect(Unit) {
        manager.initialize(context)
        val computerServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.COMPUTER)
        if (!computerServer.isRunning()) {
            try {
                computerServer.start()
            } catch (e: IOException) {
                Log.e("ComputerManager", "Failed to start computer web server", e)
            }
        }
        // Ensure the server is running before creating the first tab
        manager.ensureInitialTab()
    }

    DisposableEffect(Unit) {
        onDispose {
            manager.onUiDispose()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            shadowElevation = 2.dp,
            modifier = Modifier.zIndex(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())) {
                    manager.openTabs.forEachIndexed { index, tab ->
                        ComputerTabComponent(
                            title = tab.title,
                            icon = Icons.Default.Home,
                            isActive = manager.currentTabIndex.value == index,
                            isUnsaved = false,
                            onClose = if (manager.openTabs.size > 1) { { manager.closeTab(index) } } else null,
                            onClick = { manager.switchToTab(index) }
                        )
                    }
                }

                var showMenu by remember { mutableStateOf(false) }

                IconButton(onClick = { manager.openBrowser() }) {
                    Icon(Icons.Default.Add, contentDescription = "New Tab")
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "All Tabs")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        manager.openTabs.forEachIndexed { index, tab ->
                            DropdownMenuItem(
                                text = { Text(tab.title) },
                                onClick = {
                                    manager.switchToTab(index)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (webViewProgress > 0 && webViewProgress < 100) {
            LinearProgressIndicator(
                progress = { webViewProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (manager.openTabs.isNotEmpty()) {
                AndroidView(
                    factory = { ctx -> FrameLayout(ctx) },
                    modifier = Modifier.fillMaxSize(),
                    update = { container ->
                        val currentTab = manager.openTabs.getOrNull(manager.currentTabIndex.value)
                        if (currentTab != null) {
                            val webView = manager.getOrCreateWebView(currentTab)

                            if (webView.parent != container) {
                                (webView.parent as? ViewGroup)?.removeView(webView)
                                container.removeAllViews()
                                container.addView(webView)
                            }
                        } else {
                            container.removeAllViews()
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tabs open")
                }
            }
        }
    }
} 
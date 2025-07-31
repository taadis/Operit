package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.view.MotionEvent
import android.webkit.WebView
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
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.features.chat.webview.WebViewHandler
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.Serializable
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient

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

    val initialTabs = listOf(ComputerTab(title = "Home", url = "http://localhost:${LocalWebServer.COMPUTER_PORT}"))
    var openTabs by rememberLocal<List<ComputerTab>>(key = "computer_tabs_v2", initialTabs)
    var currentTabIndex by rememberLocal(key = "current_tab_index_v2", 0)

    fun newTab(url: String = "http://localhost:${LocalWebServer.COMPUTER_PORT}", title: String = "New Tab") {
        openTabs = openTabs + ComputerTab(title = title, url = url)
        currentTabIndex = openTabs.size - 1
    }

    val webViewHandler = remember(context) {
        WebViewHandler(context).apply {
            onFileChooserRequest = { intent, callback ->
                actualViewModel.startFileChooserForResult(intent) { resultCode, data ->
                    callback(resultCode, data)
                }
            }
            urlLoadingOverrider = { _, request ->
                val url = request?.url?.toString()
                if (url != null) {
                    if (url.startsWith("http://localhost:${LocalWebServer.COMPUTER_PORT}")) {
                        newTab(
                            url = url,
                            title = url.substringAfterLast('/').ifEmpty { "File" }
                        )
                    } else {
                        newTab(url = url, title = "Browser")
                    }
                    true // We've handled the URL override.
                } else {
                    false
                }
            }
        }
    }

    val webView = remember(context) {
        WebView(context).apply {
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            webViewHandler.configureWebView(this)
        }
    }

    // Start the computer web server when the manager is composed
    LaunchedEffect(Unit) {
        val computerServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.COMPUTER)
        if (!computerServer.isRunning()) {
            try {
                computerServer.start()
            } catch (e: IOException) {
                Log.e("ComputerManager", "Failed to start computer web server", e)
            }
        }
    }

    fun closeTab(index: Int) {
        if (index >= 0 && index < openTabs.size) {
            openTabs = openTabs.toMutableList().also { it.removeAt(index) }
            if (currentTabIndex >= openTabs.size) {
                currentTabIndex = openTabs.size - 1
            }
        }
    }
    
    LaunchedEffect(currentTabIndex, openTabs) {
        val currentTab = openTabs.getOrNull(currentTabIndex)
        if (currentTab != null) {
            webView.loadUrl(currentTab.url)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            shadowElevation = 2.dp,
            modifier = Modifier.zIndex(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    openTabs.forEachIndexed { index, tab ->
                        ComputerTabComponent(
                            title = tab.title,
                            icon = Icons.Default.Home,
                            isActive = currentTabIndex == index,
                            isUnsaved = false,
                            onClose = if (openTabs.size > 1) { { closeTab(index) } } else null,
                            onClick = { currentTabIndex = index }
                        )
                    }
                }

                var showMenu by remember { mutableStateOf(false) }

                IconButton(onClick = { newTab() }) {
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
                        openTabs.forEachIndexed { index, tab ->
                            DropdownMenuItem(
                                text = { Text(tab.title) },
                                onClick = {
                                    currentTabIndex = index
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (openTabs.isNotEmpty()) {
                AndroidView({ webView }, modifier = Modifier.fillMaxSize())
            } else {
                // Placeholder for when no tabs are open
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
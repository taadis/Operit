package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.features.chat.webview.WebViewHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
object ComputerDesktopManager : IComputerDesktop {

    private lateinit var context: Context
    internal lateinit var webViewHandler: WebViewHandler

    val openTabs = mutableStateListOf<ComputerTab>()
    val currentTabIndex = mutableStateOf(0)
    val webViewCache = mutableMapOf<String, WebView>()

    fun initialize(context: Context) {
        this.context = context.applicationContext
        this.webViewHandler = WebViewHandler(this.context)
        setupWebViewHandler()
    }

    fun ensureInitialTab() {
        if (openTabs.isEmpty()) {
            openDesktop()
        }
    }

    private fun setupWebViewHandler() {
        webViewHandler.urlLoadingOverrider = { _, request ->
            val url = request?.url?.toString()
            // 只有当导航是由用户手势（如点击）触发时，才在新标签页中打开。
            // 这可以防止我们自己的 `loadUrl` 调用被再次拦截，从而避免创建重复的标签页。
            if (url != null && request?.hasGesture() == true) {
                openBrowser(url)
                true // 我们已经处理了这次用户点击，返回 true。
            } else {
                false // 对于非手势导航（如重定向或程序调用），让 WebView 自行处理。
            }
        }
        webViewHandler.onProgressChanged = { progress ->
            // This is now handled by the UI observation directly.
            // We could add a separate flow/state here if needed for non-UI logic.
        }
        webViewHandler.onTitleReceived = { title ->
            val currentTab = openTabs.getOrNull(currentTabIndex.value)
            if (currentTab != null && title != null && !title.startsWith("http") && currentTab.title != title) {
                val index = currentTabIndex.value
                if (index < openTabs.size) {
                    openTabs[index] = currentTab.copy(title = title)
                }
            }
        }
    }
    
    override fun getTabs(): List<ComputerTab> = openTabs.toList()

    override fun switchToTab(tabId: String) {
        val index = openTabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            currentTabIndex.value = index
        }
    }

    override fun switchToTab(index: Int) {
        if (index >= 0 && index < openTabs.size) {
            currentTabIndex.value = index
        }
    }

    override fun openDesktop() {
        val url = "http://localhost:${LocalWebServer.COMPUTER_PORT}"
        val title = "Home"
        val newTab = ComputerTab(title = title, url = url)
        openTabs.add(newTab)
        currentTabIndex.value = openTabs.lastIndex
    }

    override fun openBrowser(url: String?) {
        val finalUrl = url ?: "http://localhost:${LocalWebServer.COMPUTER_PORT}/browser.html"
        val title = if (url == null) "Browser" else "Loading..."
        val newTab = ComputerTab(title = title, url = finalUrl)
        openTabs.add(newTab)
        currentTabIndex.value = openTabs.lastIndex
    }

    override suspend fun getCurrentPageHtml(): String? = suspendCancellableCoroutine { continuation ->
        val webView = webViewCache[openTabs.getOrNull(currentTabIndex.value)?.id]
        if (webView == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
                continuation.resume(html.trim().removeSurrounding("\""))
            }
        }
    }

    override fun clickElement(selector: String) {
        executeJsOnCurrentWebView("document.querySelector('$selector')?.click();")
    }

    override fun scrollBy(x: Int, y: Int) {
        executeJsOnCurrentWebView("window.scrollBy($x, $y);")
    }

    override fun inputText(selector: String, text: String) {
        executeJsOnCurrentWebView("document.querySelector('$selector')?.value = '$text';")
    }

    override fun closeTab(tabId: String) {
        val index = openTabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            closeTab(index)
        }
    }

    override fun closeTab(index: Int) {
        if (index >= 0 && index < openTabs.size) {
            val closingTab = openTabs.removeAt(index)
            webViewCache.remove(closingTab.id)?.destroy()

            if (currentTabIndex.value >= openTabs.size) {
                currentTabIndex.value = openTabs.size - 1
            }
        }
    }
    
    internal fun getOrCreateWebView(tab: ComputerTab): WebView {
        return webViewCache.getOrPut(tab.id) {
            WebView(context).apply {
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                webViewHandler.configureWebView(this, WebViewHandler.WebViewMode.COMPUTER)
                loadUrl(tab.url)
            }
        }
    }

    private fun executeJsOnCurrentWebView(script: String) {
        val webView = webViewCache[openTabs.getOrNull(currentTabIndex.value)?.id]
        Handler(Looper.getMainLooper()).post {
            webView?.evaluateJavascript(script, null)
        }
    }

    internal fun onUiDispose() {
        webViewCache.values.forEach { it.destroy() }
        webViewCache.clear()
    }
} 
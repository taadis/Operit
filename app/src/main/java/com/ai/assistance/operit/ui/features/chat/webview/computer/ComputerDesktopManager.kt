package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.ai.assistance.operit.core.tools.ComputerPageInfoNode
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.features.chat.webview.WebViewHandler
import com.ai.assistance.operit.util.HtmlParserUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull


@Serializable
private data class ActionResult(val success: Boolean, val message: String)

@SuppressLint("StaticFieldLeak")
object ComputerDesktopManager : IComputerDesktop {

    private lateinit var context: Context
    internal lateinit var webViewHandler: WebViewHandler
    private val interactionMap = mutableMapOf<Int, String>()
    private var nextinteraction_id = 1
    private val mainHandler = Handler(Looper.getMainLooper())
    private var forceNextNavToNewTab = false

    // For page load callbacks
    private val pageLoadContinuations = ConcurrentHashMap<String, Continuation<Boolean>>()
    private val isPageLoading = ConcurrentHashMap<String, Boolean>()


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
        webViewHandler.urlLoadingOverrider = fun(view: WebView?, request: WebResourceRequest?): Boolean {
            val targetUrl = request?.url?.toString() ?: return false

            if (targetUrl == view?.url) return false

            var shouldOpenInNewTab = request?.hasGesture() == true

            if (forceNextNavToNewTab) {
                shouldOpenInNewTab = true
                forceNextNavToNewTab = false // Consume the flag
            }

            if (shouldOpenInNewTab) {
                mainHandler.post { openBrowser(targetUrl) }
                return true
            }

            return false
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
        webViewHandler.onPageStarted = { tabId ->
            isPageLoading[tabId] = true
        }
        webViewHandler.onPageFinished = { tabId ->
            isPageLoading[tabId] = false
            pageLoadContinuations.remove(tabId)?.resume(true)
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

    override suspend fun awaitPageLoaded(timeoutMillis: Long): Boolean {
        val currentTabId = openTabs.getOrNull(currentTabIndex.value)?.id ?: return true

        // If the page is not currently loading, we can return immediately.
        if (isPageLoading[currentTabId] != true) {
            return true
        }

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                pageLoadContinuations[currentTabId] = continuation
                continuation.invokeOnCancellation {
                    pageLoadContinuations.remove(currentTabId)
                }
            }
        } ?: false // Return false if timeout occurs
    }

    override fun goBack() {
        val activeWebView = webViewCache[openTabs.getOrNull(currentTabIndex.value)?.id]
        mainHandler.post {
            if (activeWebView?.canGoBack() == true) {
                activeWebView.goBack()
            } else {
                // If we can't go back in the current tab's history,
                // assume the user wants to close this tab to "go back"
                // to the previous one.
                val currentIndex = currentTabIndex.value
                if (currentIndex >= 0 && openTabs.size > 1) { // Only close if it's not the last tab
                    closeTab(currentIndex)
                }
            }
        }
    }

    override suspend fun clickElement(selector: String): Pair<Boolean, String> = suspendCancellableCoroutine { continuation ->
        Log.d("ComputerDesktopManager", "Attempting to click element with selector: $selector")
        val script = """
            (function() {
                try {
                    const el = document.querySelector('$selector');
                    if (!el) {
                        return JSON.stringify({ success: false, message: 'Element not found.' });
                    }
                    
                    // Scroll into view to ensure visibility
                    el.scrollIntoView({ block: 'center', inline: 'center' });

                    // Dispatch a sequence of events to more closely mimic a user click
                    const dispatchMouseEvent = (type) => {
                        const event = new MouseEvent(type, {
                            bubbles: true,
                            cancelable: true,
                            view: window
                        });
                        el.dispatchEvent(event);
                    };

                    dispatchMouseEvent('mouseover');
                    dispatchMouseEvent('mousedown');
                    dispatchMouseEvent('mouseup');
                    
                    if (typeof el.click === 'function') {
                        el.click();
                    } else {
                        // Fallback for elements without a click method
                         dispatchMouseEvent('click');
                    }

                    return JSON.stringify({ success: true, message: 'Element clicked successfully.' });
                } catch (e) {
                    return JSON.stringify({ success: false, message: 'An exception occurred: ' + e.message });
                }
            })();
        """.trimIndent()
        executeJsOnCurrentWebView(script) { result ->
            @Serializable
            data class ClickResult(val success: Boolean, val message: String)

            try {
                val json = Json { ignoreUnknownKeys = true }
                val jsonString = json.decodeFromString<String>(result)
                val actionResult = json.decodeFromString<ActionResult>(jsonString)
                Log.d("ComputerDesktopManager", "Click element with selector '$selector' returned: success=${actionResult.success}, message=${actionResult.message}")
                continuation.resume(Pair(actionResult.success, actionResult.message))
            } catch (e: Exception) {
                Log.e("ComputerDesktopManager", "Failed to parse click result: $result", e)
                continuation.resume(Pair(false, "Failed to parse result from JavaScript."))
            }
        }
    }

    override suspend fun inputText(selector: String, text: String): Pair<Boolean, String> = suspendCancellableCoroutine { continuation ->
        val escapedText = text.replace("'", "\\'").replace("\n", "\\n")
        Log.d("ComputerDesktopManager", "Attempting to input text into selector: $selector")
        val script = """
            (function() {
                try {
                    const el = document.querySelector('$selector');
                    if (!el) {
                        return JSON.stringify({ success: false, message: 'Element not found.' });
                    }
                    
                    // Scroll into view and focus the element before typing
                    el.scrollIntoView({ block: 'center', inline: 'center' });
                    el.focus();
                    
                    const tagName = el.tagName.toUpperCase();
                    if (tagName !== 'INPUT' && tagName !== 'TEXTAREA' && !el.isContentEditable) {
                        return JSON.stringify({ success: false, message: 'Element is not an input field or content-editable.' });
                    }

                    if (el.isContentEditable) {
                        el.textContent = '$escapedText';
                    } else {
                        el.value = '$escapedText';
                    }
                    
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    return JSON.stringify({ success: true, message: 'Text input successful.' });

                } catch (e) {
                    return JSON.stringify({ success: false, message: 'An exception occurred: ' + e.message });
                }
            })();
        """.trimIndent()
        executeJsOnCurrentWebView(script) { result ->
            @Serializable
            data class InputResult(val success: Boolean, val message: String)

            try {
                val json = Json { ignoreUnknownKeys = true }
                val jsonString = json.decodeFromString<String>(result)
                val actionResult = json.decodeFromString<ActionResult>(jsonString)
                Log.d("ComputerDesktopManager", "Input text into selector '$selector' returned: success=${actionResult.success}, message=${actionResult.message}")
                continuation.resume(Pair(actionResult.success, actionResult.message))
            } catch (e: Exception) {
                Log.e("ComputerDesktopManager", "Failed to parse input result: $result", e)
                continuation.resume(Pair(false, "Failed to parse result from JavaScript."))
            }
        }
    }
    
    suspend fun getPageInfo(): ComputerPageInfoNode? {
        val jsonResult = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String> { continuation ->
                val script = HtmlParserUtil.getExtractionScript()
                executeJsOnCurrentWebView(script) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }
        }

        if (jsonResult.isBlank() || jsonResult == "null") {
            Log.w("ComputerDesktopManager", "getPageInfo received null or blank json from WebView.")
            return null
        }

        val unwrappedJson = try {
            Json.decodeFromString<String>(jsonResult)
        } catch (e: Exception) {
            Log.e("ComputerDesktopManager", "Failed to unwrap page info JSON", e)
            // Fallback for when it's not double-encoded
            jsonResult
        }

        return withContext(Dispatchers.Default) {
            Log.d("ComputerDesktopManager", "Parsing page info on background thread.")
            try {
                HtmlParserUtil.parseAndSimplify(unwrappedJson) { newMap ->
                    interactionMap.clear()
                    interactionMap.putAll(newMap)
                }
            } catch (e: Exception) {
                Log.e("ComputerDesktopManager", "Failed to parse page info on background thread. JSON length: ${unwrappedJson.length}", e)
                null
            }
        }
    }

    override suspend fun getCurrentPageHtml(): String? = suspendCancellableCoroutine { continuation ->
        val webView = webViewCache[openTabs.getOrNull(currentTabIndex.value)?.id]
        if (webView == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { jsonString ->
                 if (jsonString == null || jsonString.equals("null", ignoreCase = true)) {
                    continuation.resume(null)
                } else {
                    try {
                        // The result from evaluateJavascript is a JSON-encoded string.
                        // We need to decode it to get the raw HTML.
                        val rawHtml = Json.decodeFromString<String>(jsonString)
                        continuation.resume(rawHtml)
                    } catch (e: Exception) {
                        // Fallback for cases where the string might not be a valid JSON string
                        // (e.g., older Android versions might not quote it).
                        continuation.resume(jsonString.trim().removeSurrounding("\""))
            }
        }
    }
        }
    }

    suspend fun clickElement(interaction_id: Int): Pair<Boolean, String> {
        val selector = interactionMap[interaction_id]
        return if (selector != null) {
            mainHandler.post {
                forceNextNavToNewTab = true
            }
            val result = clickElement(selector)
            mainHandler.postDelayed({ forceNextNavToNewTab = false }, 500)
            result
        } else {
            val message = "No selector found for interaction_id: $interaction_id"
            Log.w("ComputerDesktopManager", message)
            Pair(false, message)
        }
    }

    override fun scrollBy(x: Int, y: Int) {
        executeJsOnCurrentWebView("window.scrollBy($x, $y);") {}
    }

    suspend fun inputText(interaction_id: Int, text: String): Pair<Boolean, String> {
        val selector = interactionMap[interaction_id]
        return if (selector != null) {
            inputText(selector, text)
        } else {
            val message = "No selector found for interaction_id: $interaction_id"
            Log.w("ComputerDesktopManager", message)
            Pair(false, message)
        }
    }

    override fun closeTab(tabId: String) {
        val index = openTabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            val webView = webViewCache[tabId]
            webView?.let {
                mainHandler.post {
                    it.destroy()
                }
            }
            webViewCache.remove(tabId)
            openTabs.removeAt(index)

            // Ensure currentTabIndex is valid
            if (currentTabIndex.value >= openTabs.size) {
                currentTabIndex.value = openTabs.size - 1
            }
            if (openTabs.isEmpty()) {
                currentTabIndex.value = -1
                // isDesktopManagerVisible.value = false // This line was removed from the original file
            } else {
                val newActiveTabId = openTabs[currentTabIndex.value].id
                val script = "console.log('Tab closed, new active tab is $newActiveTabId');"
                executeJsOnCurrentWebView(script) {}
            }
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
                webViewHandler.configureWebView(this, WebViewHandler.WebViewMode.COMPUTER, tab.id)
                loadUrl(tab.url)
            }
        }
    }

    private fun executeJsOnCurrentWebView(script: String, callback: (String) -> Unit) {
        val activeWebView = webViewCache[openTabs.getOrNull(currentTabIndex.value)?.id]
        if (activeWebView == null) {
            Log.e("ComputerDesktopManager", "No active WebView found to execute script.")
            return
        }
        mainHandler.post {
            activeWebView.evaluateJavascript(script, callback)
        }
    }

    private fun updateInteractionMap(newMap: Map<Int, String>) {
        interactionMap.clear()
        interactionMap.putAll(newMap)
    }

    internal fun onUiDispose() {
        webViewCache.values.forEach { it.destroy() }
        webViewCache.clear()
    }
} 
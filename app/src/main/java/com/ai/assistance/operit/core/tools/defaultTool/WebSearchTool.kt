package com.ai.assistance.operit.core.tools.defaultTool

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.core.tools.WebSearchResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient

/** Tool for web search functionality 实现百度搜索爬虫，无需API密钥 */
class WebSearchTool(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "WebSearchTool"
        private const val BAIDU_SEARCH_URL = "https://www.baidu.com/s?wd="
        private const val USER_AGENT =
                "Mozilla/5.0 (Linux; Android 12; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Mobile Safari/537.36"
        private const val MAX_RESULTS = 10
    }

    // 创建OkHttpClient实例，配置超时
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

    // 标记是否处于人机验证状态
    private var needsCaptchaVerification = false

    // 存储WebView引用，用于在不同方法间共享
    private var webViewReference: WebView? = null

    override fun invoke(tool: AITool): ToolResult {
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""

        if (query.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Query parameter cannot be empty"
            )
        }

        return try {
            val searchResults = performSearch(query)

            ToolResult(toolName = tool.name, success = true, result = searchResults, error = null)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing web search", e)

            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error performing web search: ${e.message}"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val query = tool.parameters.find { it.name == "query" }?.value

        return if (query.isNullOrBlank()) {
            ToolValidationResult(valid = false, errorMessage = "Query parameter is required")
        } else {
            ToolValidationResult(valid = true)
        }
    }

    /** Perform the web search and return results */
    private fun performSearch(query: String): WebSearchResultData {
        // Use WebView-based search implementation instead of direct HTTP request
        val searchResults = runBlocking { performWebViewSearch(query) }

        // Convert the search results to WebSearchResultData format
        return if (searchResults.isNotEmpty()) {
            // Map the search results
            val results =
                    searchResults.map { resultMap ->
                        WebSearchResultData.SearchResult(
                                title = resultMap["title"] ?: "无标题",
                                url = resultMap["link"] ?: "",
                                snippet = resultMap["snippet"] ?: "无摘要内容",
                                extraInfo = resultMap["extraInfo"]
                        )
                    }

            WebSearchResultData(query = query, results = results.take(MAX_RESULTS))
        } else {
            // Return fallback results
            WebSearchResultData(
                    query = query,
                    results =
                            generateGenericResults(query, MAX_RESULTS, "未找到搜索结果").map { resultMap ->
                                WebSearchResultData.SearchResult(
                                        title = resultMap["title"] ?: "无标题",
                                        url = resultMap["link"] ?: "",
                                        snippet = resultMap["snippet"] ?: "无摘要内容"
                                )
                            }
            )
        }
    }

    /** 使用WebView执行搜索，返回结果 */
    private suspend fun performWebViewSearch(query: String): List<Map<String, String>> {
        return try {
            Log.d(TAG, "Starting WebView-based search for: $query")
            // 使用WebView执行搜索
            val results = showSearchBrowserWindow(query)
            Log.d(TAG, "WebView search completed with ${results.size} results")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error in WebView search", e)
            emptyList()
        }
    }

    /** Displays a browser window to perform the search and extract results */
    private suspend fun showSearchBrowserWindow(query: String): List<Map<String, String>> {
        Log.d(TAG, "Showing search browser for query: $query")
        val url = "https://www.baidu.com/s?wd=${URLEncoder.encode(query, "UTF-8")}&rn=50"

        return suspendCancellableCoroutine { continuation ->
            // 确保UI操作在主线程上执行
            Handler(Looper.getMainLooper()).post {
                try {
                    val windowManager =
                            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val params = setupWindowParams()

                    // 创建生命周期管理器而不是尝试将context转换为LifecycleOwner
                    // 现在在主线程上创建和初始化
                    val lifecycleOwner =
                            ServiceLifecycleOwner().apply {
                                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                                handleLifecycleEvent(Lifecycle.Event.ON_START)
                                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                            }

                    val composeView =
                            ComposeView(context).apply {
                                // 设置视图组合策略
                                setViewCompositionStrategy(
                                        ViewCompositionStrategy.DisposeOnDetachedFromWindow
                                )

                                // 使用自定义的生命周期所有者
                                setViewTreeLifecycleOwner(lifecycleOwner)
                                setViewTreeViewModelStoreOwner(lifecycleOwner)
                                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                            }

                    composeView.setContent {
                        SearchBrowserUI(
                                url = url,
                                query = query,
                                onWebViewCreated = { webView ->
                                    // 存储WebView引用以便清理
                                    webViewReference = webView
                                },
                                onSearchComplete = { results ->
                                    Log.d(TAG, "Search complete with ${results.size} results")
                                    // 在完成搜索后进行资源清理
                                    try {
                                        // 清理生命周期资源
                                        lifecycleOwner.handleLifecycleEvent(
                                                Lifecycle.Event.ON_PAUSE
                                        )
                                        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                                        lifecycleOwner.handleLifecycleEvent(
                                                Lifecycle.Event.ON_DESTROY
                                        )

                                        // 使用统一的清理方法
                                        cleanupWebView(webViewReference)

                                        // 清理Compose View并移除窗口
                                        CoroutineScope(Dispatchers.Main).launch {
                                            // 首先设置内容为空，释放组合资源
                                            composeView.setContent {}

                                            // 使用try-catch以确保即使移除窗口失败也能继续
                                            try {
                                                windowManager.removeView(composeView)
                                                Log.d(TAG, "ComposeView removed from window")
                                            } catch (e: Exception) {
                                                Log.e(
                                                        TAG,
                                                        "Error removing ComposeView: ${e.message}"
                                                )
                                            }

                                            // 恢复协程
                                            if (continuation.isActive) {
                                                continuation.resume(results)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error cleaning up WebView: ${e.message}")
                                        if (continuation.isActive) {
                                            continuation.resume(results)
                                        }
                                    }
                                }
                        )
                    }

                    try {
                        windowManager.addView(composeView, params)
                        Log.d(TAG, "Search browser window added")

                        // 添加取消回调以处理协程取消
                        continuation.invokeOnCancellation {
                            // 确保在主线程上执行UI操作
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    // 清理生命周期资源
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

                                    // 清理WebView资源
                                    cleanupWebView(webViewReference)

                                    // 移除视图
                                    composeView.setContent {}
                                    windowManager.removeView(composeView)
                                    Log.d(TAG, "Search browser window removed on cancellation")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error removing view on cancellation: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing search browser: ${e.message}")
                        continuation.resume(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in showSearchBrowserWindow: ${e.message}")
                    continuation.resume(emptyList())
                }
            }
        }
    }

    /** Compose UI for the search browser */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SearchBrowserUI(
            url: String,
            query: String,
            onWebViewCreated: (WebView) -> Unit,
            onSearchComplete: (List<Map<String, String>>) -> Unit
    ) {
        // State to track search results
        val results = remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
        val isLoading = remember { mutableStateOf(true) }
        val hasExtractedResults = remember { mutableStateOf(false) }
        val pageLoaded = remember { mutableStateOf(false) }
        val currentUrl = remember { mutableStateOf(url) }

        MaterialTheme {
            Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            ) {
                Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Text(
                            text = "Search in Progress",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Query info
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .primaryContainer,
                                                    shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                    text = "Search Query:",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                    text = "\"$query\"",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val statusText =
                                    when {
                                        !pageLoaded.value -> "Loading search page..."
                                        isLoading.value ->
                                                "Waiting for results to load completely..."
                                        results.value.isEmpty() -> "Extracting search results..."
                                        else ->
                                                "Found ${results.value.size} results. Automatically continuing..."
                                    }

                            Text(
                                    text = statusText,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            if (currentUrl.value != url) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text =
                                                "Page loaded: ${currentUrl.value.take(50)}${if (currentUrl.value.length > 50) "..." else ""}",
                                        fontSize = 12.sp,
                                        color =
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                        alpha = 0.7f
                                                )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // WebView - with improved lifecycle management
                    AndroidView(
                            factory = { context ->
                                createOptimizedWebView(context, url) { webView ->
                                    // Register created WebView instance for later cleanup
                                    onWebViewCreated(webView)

                                    // Configure WebViewClient
                                    webView.webViewClient =
                                            object : WebViewClient() {
                                                // Flag to track if we've seen the verification page
                                                private var hasSeenVerificationPage = false
                                                private var isFirstLoad = true

                                                override fun onPageFinished(
                                                        view: WebView,
                                                        loadedUrl: String
                                                ) {
                                                    super.onPageFinished(view, loadedUrl)
                                                    Log.d(TAG, "Loaded URL: $loadedUrl")
                                                    currentUrl.value = loadedUrl

                                                    pageLoaded.value = true

                                                    // If it's a search results page, extract
                                                    // results
                                                    if (loadedUrl.contains("www.baidu.com/s?") &&
                                                                    !hasExtractedResults.value
                                                    ) {
                                                        // Set a delay to ensure the page is fully
                                                        // rendered
                                                        Handler(Looper.getMainLooper())
                                                                .postDelayed(
                                                                        {
                                                                            if (!view.isAttachedToWindow
                                                                            ) {
                                                                                Log.d(
                                                                                        TAG,
                                                                                        "WebView is no longer attached, skipping extraction"
                                                                                )
                                                                                return@postDelayed
                                                                            }

                                                                            // Extract search
                                                                            // results from the page
                                                                            extractSearchResults(
                                                                                    view,
                                                                                    query
                                                                            ) { extractedResults ->
                                                                                results.value =
                                                                                        extractedResults
                                                                                isLoading.value =
                                                                                        false
                                                                                hasExtractedResults
                                                                                        .value =
                                                                                        true
                                                                                Log.d(
                                                                                        TAG,
                                                                                        "Extracted ${extractedResults.size} search results"
                                                                                )

                                                                                // Automatically
                                                                                // continue with
                                                                                // results after a
                                                                                // short delay
                                                                                Handler(
                                                                                                Looper.getMainLooper()
                                                                                        )
                                                                                        .postDelayed(
                                                                                                {
                                                                                                    if (view.isAttachedToWindow
                                                                                                    ) {
                                                                                                        Log.d(
                                                                                                                TAG,
                                                                                                                "Auto-continuing with ${extractedResults.size} results"
                                                                                                        )
                                                                                                        onSearchComplete(
                                                                                                                extractedResults
                                                                                                        )
                                                                                                    }
                                                                                                },
                                                                                                1000
                                                                                        ) // 1
                                                                                // second
                                                                                // delay
                                                                                // before
                                                                                // auto-continue
                                                                            }
                                                                        },
                                                                        2000
                                                                ) // Wait 2 seconds for page to
                                                        // fully render
                                                    }
                                                }

                                                override fun shouldOverrideUrlLoading(
                                                        view: WebView,
                                                        request: WebResourceRequest
                                                ): Boolean {
                                                    // Allow all navigation, log URL changes
                                                    currentUrl.value = request.url.toString()
                                                    return false
                                                }

                                                override fun onReceivedError(
                                                        view: WebView,
                                                        errorCode: Int,
                                                        description: String,
                                                        failingUrl: String
                                                ) {
                                                    Log.e(
                                                            TAG,
                                                            "WebView error: $errorCode - $description"
                                                    )
                                                    super.onReceivedError(
                                                            view,
                                                            errorCode,
                                                            description,
                                                            failingUrl
                                                    )
                                                }
                                            }

                                    return@createOptimizedWebView webView
                                }
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            // Add update callback to handle WebView lifecycle properly
                            update = { webView ->
                                // Store latest WebView reference
                                webViewReference = webView
                                // Only update essential properties to avoid recreation
                                webView.requestFocus()
                            }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                                onClick = { onSearchComplete(emptyList()) },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                        )
                        ) { Text("Cancel") }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Continue button
                        Button(
                                onClick = { onSearchComplete(results.value) },
                                enabled = !isLoading.value || pageLoaded.value,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                    if (results.value.isNotEmpty())
                                            "Continue with ${results.value.size} Results"
                                    else "Continue"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Help text
                    Text(
                            text =
                                    if (!pageLoaded.value) {
                                        "Loading search page. Please wait..."
                                    } else if (isLoading.value) {
                                        "If search is taking too long, you can click Continue to proceed with current results."
                                    } else if (results.value.isEmpty()) {
                                        "No results could be extracted. Search will complete automatically."
                                    } else {
                                        "Search complete. Results will be automatically processed in a moment."
                                    },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    /** 创建优化的WebView实例，配置所有必要的设置 */
    private fun createOptimizedWebView(
            context: Context,
            url: String,
            configure: (WebView) -> WebView
    ): WebView {
        return WebView(context).apply {
            layoutParams =
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.0f
                    )

            // Configure WebView settings
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = USER_AGENT

                // Add additional settings to help with memory management
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = true // Reduce automatic media loading
            }

            // Enable interaction
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()

            // Improve memory management
            setWillNotDraw(false)

            // Load the URL
            loadUrl(url)

            // Apply additional configuration
            configure(this)
        }
    }

    /** 清理WebView资源的辅助方法 - 统一封装清理逻辑 */
    private fun cleanupWebView(webView: WebView?) {
        webView?.apply {
            // 先停止加载
            stopLoading()

            // 清理WebView状态
            clearHistory()
            clearCache(true)
            clearFormData()
            clearSslPreferences()

            // 清理JavaScript状态
            evaluateJavascript("javascript:void(0);", null)

            // 加载空白页以释放资源
            loadUrl("about:blank")

            // 暂停WebView
            onPause()

            // 移除回调和消息
            handler?.removeCallbacksAndMessages(null)

            // 销毁WebView
            destroy()
        }

        // 请求GC回收资源
        System.gc()
    }

    /** Extract search results from loaded WebView */
    private fun extractSearchResults(
            webView: WebView,
            query: String,
            callback: (List<Map<String, String>>) -> Unit
    ) {
        // Check if WebView is still valid
        if (!webView.isAttachedToWindow) {
            Log.e(TAG, "WebView is not attached to window, cannot extract results")
            callback(emptyList())
            return
        }

        // Log extraction start
        Log.d(TAG, "Starting to extract search results for query: $query")

        // JavaScript to extract search results from Baidu
        val extractionScript =
                """
            (function() {
                try {
                    console.log("Starting extraction script");
                    var results = [];
                    var isSpecialCard = false;
                    var specialCardType = "";
                    var detailLink = null;
                    var pageUrl = window.location.href;
                    
                    // Check if the page is a translation page
                    if (pageUrl.includes('fanyi.baidu.com') || document.title.includes('百度翻译') || document.title.includes('Baidu Translate')) {
                        console.log("Identified as translation page");
                        isSpecialCard = true;
                        specialCardType = "translation";
                        
                        // Extract source and target text for translation
                        var sourceText = "";
                        var targetText = "";
                        
                        // Try to get source text from different possible elements
                        var sourceSelectors = [
                            '#baidu_translate_input', 
                            '.textarea-bg-text',
                            '[data-role="source-text"]',
                            '.trans-left textarea'
                        ];
                        
                        for (var i = 0; i < sourceSelectors.length; i++) {
                            var sourceElement = document.querySelector(sourceSelectors[i]);
                            if (sourceElement && sourceElement.value) {
                                sourceText = sourceElement.value.trim();
                                break;
                            } else if (sourceElement && sourceElement.innerText) {
                                sourceText = sourceElement.innerText.trim();
                                break;
                            }
                        }
                        
                        // Try to get target text from different possible elements
                        var targetSelectors = [
                            '#baidu_translate_result', 
                            '.trans-right p',
                            '[data-role="target-text"]',
                            '.target-output'
                        ];
                        
                        for (var i = 0; i < targetSelectors.length; i++) {
                            var targetElement = document.querySelector(targetSelectors[i]);
                            if (targetElement) {
                                targetText = targetElement.innerText.trim();
                                break;
                            }
                        }
                        
                        // Create result for translation
                        if (sourceText || targetText) {
                            detailLink = pageUrl;
                            var resultTitle = "翻译结果 / Translation Result";
                            var resultContent = "";
                            
                            if (sourceText) {
                                resultContent += "原文 / Source: " + sourceText + "\\n\\n";
                            }
                            
                            if (targetText) {
                                resultContent += "译文 / Target: " + targetText;
                            }
                            
                            results.push({
                                title: resultTitle,
                                link: pageUrl,
                                snippet: resultContent,
                                isSpecialCard: true,
                                specialCardType: "translation",
                                detailLink: pageUrl,
                                extraInfo: {
                                    sourceText: sourceText,
                                    targetText: targetText
                                }
                            });
                        }
                    }
                    
                    // Check for weather cards using multiple possible selectors
                    var weatherSelectors = [
                        '.op-weather-channel', 
                        '[data-component="weather"]', 
                        '.weather-card',
                        '[data-weather]'
                    ];
                    
                    for (var i = 0; i < weatherSelectors.length && !isSpecialCard; i++) {
                        var weatherCard = document.querySelector(weatherSelectors[i]);
                        if (weatherCard) {
                            console.log("Found weather card with selector: " + weatherSelectors[i]);
                            isSpecialCard = true;
                            specialCardType = "weather";
                            
                            // Try to extract city name
                            var cityName = "";
                            var cityElements = weatherCard.querySelectorAll('h3, .city-name, .location');
                            for (var j = 0; j < cityElements.length; j++) {
                                var city = cityElements[j].innerText.trim();
                                if (city && city.length < 20) {
                                    cityName = city;
                                    break;
                                }
                            }
                            
                            // Extract temperature
                            var temperature = "";
                            var tempElements = weatherCard.querySelectorAll('.temp, .temperature, [data-temp]');
                            for (var j = 0; j < tempElements.length; j++) {
                                var temp = tempElements[j].innerText.trim();
                                if (temp && temp.includes('°')) {
                                    temperature = temp;
                                    break;
                                }
                            }
                            
                            // Extract other weather info
                            var weatherInfo = weatherCard.innerText.trim();
                            detailLink = weatherCard.querySelector('a') ? weatherCard.querySelector('a').href : pageUrl;
                            
                            results.push({
                                title: cityName ? cityName + '天气' : '天气预报',
                                link: detailLink,
                                snippet: weatherInfo,
                                isSpecialCard: true,
                                specialCardType: "weather",
                                detailLink: detailLink
                            });
                            
                            // Add weather-specific structure if we can find more detailed elements
                            try {
                                var weatherData = {};
                                weatherData.city = cityName;
                                weatherData.temperature = temperature;
                                
                                // Try to find weather condition
                                var conditionElements = weatherCard.querySelectorAll('.weather-icon + span, .condition, .weather-desc');
                                if (conditionElements.length > 0) {
                                    weatherData.condition = conditionElements[0].innerText.trim();
                                }
                                
                                // Try to extract forecast days
                                var forecastDays = weatherCard.querySelectorAll('.weather-forecast-day, .forecast-item');
                                var forecast = [];
                                for (var j = 0; j < forecastDays.length && j < 7; j++) {
                                    var day = forecastDays[j].innerText.trim();
                                    forecast.push(day);
                                }
                                if (forecast.length > 0) {
                                    weatherData.forecast = forecast;
                                }
                                
                                // Add the structured data to extraInfo
                                results[0].extraInfo = weatherData;
                            } catch (e) {
                                console.error("Error extracting detailed weather data", e);
                            }
                        }
                    }
                    
                    // Process standard search results if no special card found
                    if (results.length === 0) {
                        console.log("No special cards found, extracting standard search results");
                        
                        // Gather all possible containers for search results using multiple selectors
                        var resultsElements = [];
                        
                        // Try multiple possible result container selectors
                        var containerSelectors = [
                            '.result', 
                            '.c-container', 
                            '.result-op',
                            '[srcid]',
                            '.new-pmd',
                            '.new-pmd .c-container',
                            '[tpl="se_com_default"]',
                            '.se-result-item',
                            '[id^="content_left"] > div',
                            '[class*="result"]'
                        ];
                        
                        for (var i = 0; i < containerSelectors.length; i++) {
                            var elements = document.querySelectorAll(containerSelectors[i]);
                            if (elements && elements.length > 0) {
                                console.log("Found " + elements.length + " results with selector: " + containerSelectors[i]);
                                resultsElements = elements;
                                break;
                            }
                        }
                        
                        // If no results found with container selectors, try a broader approach
                        if (resultsElements.length === 0) {
                            console.log("No results found with container selectors, trying broader approach");
                            
                            // Look for any div that contains an anchor with href
                            var contentArea = document.getElementById('content_left') || document.getElementById('results') || document.body;
                            var potentialResults = contentArea.querySelectorAll('div');
                            
                            for (var i = 0; i < potentialResults.length; i++) {
                                var div = potentialResults[i];
                                var anchors = div.querySelectorAll('a[href]');
                                
                                // Check if this div has anchors and reasonable content
                                if (anchors.length > 0 && div.innerText.length > 50) {
                                    resultsElements = Array.prototype.slice.call(potentialResults, i, i + 10);
                                    console.log("Found potential results using broader approach: " + resultsElements.length);
                                    break;
                                }
                            }
                        }
                        
                        // Process the results
                        console.log("Processing " + resultsElements.length + " result elements");
                        
                        for (var i = 0; i < resultsElements.length && i < 10; i++) {
                            try {
                                var result = resultsElements[i];
                                
                                // Skip non-meaningful elements
                                if (!result.innerText || result.innerText.length < 10) {
                                    continue;
                                }
                                
                                // Try to extract title - check for several possible selectors
                                var titleElement = null;
                                var titleSelectors = [
                                    'h3', '.t', '.title', '[data-title]', '.c-title', 
                                    'a.header', 'a.result-title', 'a[data-click]',
                                    '.c-title-text', '.new-pmd .t', '.new-pmd .c-title',
                                    '.result-title', '[class*="title"]'
                                ];
                                
                                for (var j = 0; j < titleSelectors.length; j++) {
                                    titleElement = result.querySelector(titleSelectors[j]);
                                    if (titleElement && titleElement.innerText.trim()) {
                                        break;
                                    }
                                }
                                
                                var title = "";
                                // If no direct title element found, try to find the first anchor with content
                                if (!titleElement || !titleElement.innerText.trim()) {
                                    var anchors = result.querySelectorAll('a');
                                    for (var j = 0; j < anchors.length; j++) {
                                        if (anchors[j].innerText.trim() && anchors[j].innerText.length > 5) {
                                            titleElement = anchors[j];
                                            break;
                                        }
                                    }
                                }
                                
                                if (titleElement) {
                                    title = titleElement.innerText.trim();
                                } else if (result.innerText.split('\\n')[0]) {
                                    // If still no title found, use the first line of text
                                    title = result.innerText.split('\\n')[0].trim();
                                }
                                
                                // Extract link - try multiple approaches
                                var link = "";
                                
                                // First try: link from the title element if it's an anchor
                                if (titleElement && titleElement.tagName === 'A' && titleElement.href) {
                                    link = titleElement.href;
                    } else {
                                    // Second try: first meaningful link in the result
                                    var links = result.querySelectorAll('a[href]');
                                    for (var j = 0; j < links.length; j++) {
                                        var href = links[j].href;
                                        if (href && 
                                            href.startsWith('http') && 
                                            !href.includes('baidu.com/s?') &&
                                            !href.includes('baidu.com/link?')) {
                                            link = href;
                                            break;
                                        }
                                    }
                                    
                                    // Third try: any baidu redirect link
                                    if (!link) {
                                        for (var j = 0; j < links.length; j++) {
                                            var href = links[j].href;
                                            if (href && href.startsWith('http')) {
                                                link = href;
                                                break;
                                            }
                                        }
                                    }
                                }
                                
                                // Extract snippet
                                var snippet = "";
                                
                                // Try various selectors for snippet content
                                var snippetSelectors = [
                                    '.c-abstract', '.abstract', '.content', '.c-content',
                                    '.description', '.result-text', '.res-desc',
                                    '[class*="abstract"]', '[class*="snippet"]', '[class*="description"]'
                                ];
                                
                                for (var j = 0; j < snippetSelectors.length; j++) {
                                    var snippetElement = result.querySelector(snippetSelectors[j]);
                                    if (snippetElement && snippetElement.innerText.trim()) {
                                        snippet = snippetElement.innerText.trim();
                                        break;
                                    }
                                }
                                
                                // If no snippet found with selectors, extract from the result text
                                if (!snippet) {
                                    var resultText = result.innerText;
                                    
                                    // Remove the title from the text if we found it
                                    if (title) {
                                        resultText = resultText.replace(title, '');
                                    }
                                    
                                    // Take the first 150 characters as snippet
                                    snippet = resultText.trim().substring(0, 150);
                                    
                                    // If snippet is too short, use more of the text
                                    if (snippet.length < 50 && resultText.length > 50) {
                                        snippet = resultText.trim();
                                    }
                                }
                                
                                // Only add the result if it has at least a title
                                if (title) {
                                    var resultObject = {
                                        title: title,
                                        link: link || window.location.href,
                                        snippet: snippet || "No description available.",
                                        isSpecialCard: false
                                    };
                                    
                                    results.push(resultObject);
                                } else {
                                    console.log("Skipped result " + i + " due to missing title");
                                }
                            } catch (e) {
                                console.error("Error processing result " + i, e);
                            }
                        }
                    }
                    
                    console.log("Extraction complete, found " + results.length + " results");
                    
                    // Add information about special card if found
                    if (isSpecialCard) {
                        console.log("Special card found: " + specialCardType);
                        return JSON.stringify({
                            results: results,
                            meta: {
                                isSpecialCard: isSpecialCard,
                                specialCardType: specialCardType,
                                detailLink: detailLink,
                                query: "${query}"
                            }
                        });
                    }
                    
                    return JSON.stringify(results);
                } catch(e) {
                    return JSON.stringify({error: e.toString()});
                }
            })();
        """.trimIndent()

        try {
            webView.evaluateJavascript(extractionScript) { resultJson ->
                try {
                    Log.d(
                            TAG,
                            "Detailed content extraction result received. Length: ${resultJson.length}"
                    )

                    if (resultJson.contains("error")) {
                        Log.e(TAG, "Error in detailed extraction script: $resultJson")
                        callback(emptyList())
                        return@evaluateJavascript
                    }

                    // 处理JSON字符串
                    val processedJson =
                            if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                                resultJson
                                        .substring(1, resultJson.length - 1)
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\")
                            } else {
                                resultJson
                            }

                    // 解析结果
                    val results = parseJsonResults(processedJson)

                    if (results.isEmpty()) {
                        Log.w(TAG, "No detailed content extracted from page")

                        // 检查当前URL是否包含翻译相关关键字
                        val currentUrl = webView.url ?: ""
                        if (currentUrl.contains("fanyi") || currentUrl.contains("translate")) {
                            // 翻译页面但无法提取结果，尝试从页面整体提取
                            performEmergencyExtraction(webView) { emergencyResults ->
                                if (emergencyResults.isNotEmpty()) {
                                    callback(emergencyResults)
                                } else {
                                    callback(emptyList())
                                }
                            }
                        } else {
                            callback(emptyList())
                        }
                    } else {
                        Log.d(TAG, "Successfully extracted detailed content: ${results.size} items")
                        callback(results)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing detailed extraction results", e)
                    callback(emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during detailed JavaScript evaluation", e)
            callback(emptyList())
        }
    }

    /** 当常规提取失败时，尝试从页面提取可能的翻译内容 */
    private fun performEmergencyExtraction(
            webView: WebView,
            callback: (List<Map<String, String>>) -> Unit
    ) {
        val script =
                """
            (function() {
                try {
                    // 获取页面文本内容
                    var pageText = document.body.innerText;
                    var pageTitle = document.title;
                    var currentUrl = window.location.href;
                    
                    // 创建一个简单的结果
                    var result = {
                        title: pageTitle || "百度翻译内容",
                        link: currentUrl,
                        snippet: "从页面提取的可能翻译内容:\n\n"
                    };
                    
                    // 获取所有段落
                    var paragraphs = [];
                    var pElems = document.querySelectorAll('p, div, span');
                    for (var i = 0; i < pElems.length; i++) {
                        var text = pElems[i].innerText.trim();
                        if (text.length > 20 && 
                            !text.includes("百度") && 
                            !text.includes("©") && 
                            !text.includes("版权") &&
                            !text.includes("使用") &&
                            !text.includes("cookie")) {
                            
                            paragraphs.push(text);
                            if (paragraphs.length >= 5) break; // 最多提取5个段落
                        }
                    }
                    
                    // 如果找到任何段落
                    if (paragraphs.length > 0) {
                        result.snippet += paragraphs.join("\n\n");
                    } else {
                        // 如果没有找到有意义的段落，提取页面的前2000个字符
                        result.snippet += pageText.substring(0, 2000);
                        if (pageText.length > 2000) {
                            result.snippet += "...（内容已截断）";
                        }
                    }
                    
                    return JSON.stringify([result]);
                } catch(e) {
                    return JSON.stringify({error: e.toString()});
                }
            })();
        """.trimIndent()

        try {
            webView.evaluateJavascript(script) { resultJson ->
                try {
                    Log.d(TAG, "Emergency extraction result received. Length: ${resultJson.length}")

                    if (resultJson.contains("error")) {
                        Log.e(TAG, "Error in emergency extraction script: $resultJson")
                        callback(emptyList())
                        return@evaluateJavascript
                    }

                    // 处理JSON字符串
                    val processedJson =
                            if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                                resultJson
                                        .substring(1, resultJson.length - 1)
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\")
                            } else {
                                resultJson
                            }

                    // 解析结果
                    val results = parseJsonResults(processedJson)
                    callback(results)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing emergency extraction results", e)
                    callback(emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during emergency extraction", e)
            callback(emptyList())
        }
    }

    /** Parse JSON results from JavaScript */
    private fun parseJsonResults(json: String): List<Map<String, String>> {
        try {
            // Preparar la lista de resultados
            val resultsList = mutableListOf<Map<String, String>>()

            // Registrar longitud del JSON para diagnóstico
            Log.d(TAG, "Parsing JSON results, length: ${json.length}")

            // Comprobar si es una respuesta JSON válida
            if (json.isBlank() || json == "null") {
                Log.e(TAG, "Empty or null JSON response")
                return emptyList()
            }

            // Manejar formato con metadatos y resultados encapsulados
            if (json.contains("\"results\":") && json.contains("\"meta\":")) {
                Log.d(TAG, "Detected metadata format JSON")
                try {
                    val jsonObject = org.json.JSONObject(json)

                    // Extraer metadatos
                    val meta =
                            if (jsonObject.has("meta")) jsonObject.getJSONObject("meta") else null
                    val isSpecialCard = meta?.optBoolean("isSpecialCard", false) ?: false
                    val specialCardType = meta?.optString("specialCardType", "") ?: ""
                    val detailLink = meta?.optString("detailLink", "") ?: ""

                    Log.d(TAG, "Metadata: isSpecialCard=$isSpecialCard, type=$specialCardType")

                    // Extraer los resultados del array anidado
                    if (jsonObject.has("results")) {
                        val resultsArray = jsonObject.getJSONArray("results")
                        Log.d(TAG, "Found ${resultsArray.length()} items in results array")

                        for (i in 0 until resultsArray.length()) {
                            try {
                                val item = resultsArray.getJSONObject(i)
                                val result = processJsonResultItem(item)

                                // Añadir información de metadatos si no está incluida en el
                                // resultado
                                if (!result.containsKey("isSpecialCard") && isSpecialCard) {
                                    result["isSpecialCard"] = isSpecialCard.toString()
                                }

                                if (!result.containsKey("specialCardType") &&
                                                specialCardType.isNotEmpty()
                                ) {
                                    result["specialCardType"] = specialCardType
                                }

                                if (!result.containsKey("detailLink") && detailLink.isNotEmpty()) {
                                    result["detailLink"] = detailLink
                                }

                                resultsList.add(result)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing result item at index $i", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing metadata format JSON", e)
                }
            }
            // Manejar formato de array simple
            else if (json.startsWith("[") && json.endsWith("]")) {
                try {
                    val jsonArray = org.json.JSONArray(json)
                    Log.d(TAG, "Found ${jsonArray.length()} items in JSON array")

                    for (i in 0 until jsonArray.length()) {
                        try {
                            val item = jsonArray.getJSONObject(i)
                            val result = processJsonResultItem(item)
                            resultsList.add(result)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing JSON item at index $i", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing JSON array", e)
                }
            }
            // Manejar objeto JSON único (generalmente para errores)
            else if (json.startsWith("{") && json.endsWith("}")) {
                try {
                    val jsonObject = org.json.JSONObject(json)
                    Log.d(TAG, "Processing single JSON object")

                    // Verificar si es un objeto de error
                    if (jsonObject.has("error")) {
                        val error = jsonObject.getString("error")
                        Log.e(TAG, "Error reported in JSON: $error")

                        // Añadir como resultado de depuración
                        val result = mutableMapOf<String, String>()
                        result["title"] = "Error de extracción"
                        result["link"] = "https://www.baidu.com"
                        result["snippet"] = "Error durante la extracción de resultados: $error"

                        if (jsonObject.has("location")) {
                            result["snippet"] += "\n\nDetalles: ${jsonObject.getString("location")}"
                        }

                        resultsList.add(result)
                    } else {
                        // Intentar procesar como un resultado normal
                        resultsList.add(processJsonResultItem(jsonObject))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing single JSON object", e)
                }
            } else {
                Log.e(TAG, "Invalid JSON format. First 100 chars: ${json.take(100)}")
            }

            Log.d(TAG, "Successfully parsed ${resultsList.size} results from JSON")
            return resultsList
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error parsing JSON results", e)
            return emptyList()
        }
    }

    /** Procesa un único objeto JSON de resultado y lo convierte en un mapa de cadenas */
    private fun processJsonResultItem(item: org.json.JSONObject): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()

        // Campos básicos requeridos
        if (item.has("title")) result["title"] = item.getString("title")
        if (item.has("link")) result["link"] = item.getString("link")
        if (item.has("snippet")) result["snippet"] = item.getString("snippet")

        // Campos especiales
        if (item.has("isSpecialCard")) {
            result["isSpecialCard"] = item.getBoolean("isSpecialCard").toString()
        }

        if (item.has("specialCardType")) {
            result["specialCardType"] = item.getString("specialCardType")
        }

        if (item.has("detailLink")) {
            result["detailLink"] = item.getString("detailLink")
        }

        // Manejo de extraInfo como JSON anidado
        if (item.has("extraInfo")) {
            try {
                val extraInfo = item.getJSONObject("extraInfo")
                result["extraInfo"] = extraInfo.toString()

                // También extraer campos clave de extraInfo para facilitar su uso
                val keys = extraInfo.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val extraInfoKey = "extraInfo_$key"
                    result[extraInfoKey] = extraInfo.get(key).toString()
                }
            } catch (e: Exception) {
                // Si extraInfo no es un objeto JSON, guardarlo como string
                try {
                    result["extraInfo"] = item.getString("extraInfo")
                } catch (e2: Exception) {
                    Log.e(TAG, "Error processing extraInfo field", e2)
                }
            }
        }

        // Procesar pageInfo si existe
        if (item.has("pageInfo")) {
            result["pageInfo"] = item.getString("pageInfo")
        }

        // Información de depuración
        if (item.has("debug")) {
            try {
                val debug = item.getJSONObject("debug")
                val debugInfo = mutableListOf<String>()
                val keys = debug.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    debugInfo.add("$key: ${debug.getString(key)}")
                }
                result["debugInfo"] = debugInfo.joinToString(", ")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing debug info", e)
            }
        }

        // Verificar que el resultado contenga al menos la información básica
        if (result["title"].isNullOrBlank() && result["snippet"].isNullOrBlank()) {
            Log.w(TAG, "Result missing basic fields: $result")
        }

        return result
    }

    /**
     * Generate generic results when search fails
     * @param query The search query
     * @param numResults Number of results to generate
     * @param reason Optional reason for the failure
     */
    private fun generateGenericResults(
            query: String,
            numResults: Int,
            reason: String? = null
    ): List<Map<String, String>> {
        val genericResponses =
                if (reason != null) {
                    listOf(
                            "Sorry, unable to get search results for \"$query\". Reason: $reason",
                            "Failed to retrieve Baidu search results for \"$query\". Reason: $reason",
                            "Problem occurred while searching for \"$query\". Reason: $reason"
                    )
                } else {
                    listOf(
                            "Sorry, unable to get search results for \"$query\". This may be due to network issues or the search engine being temporarily unavailable.",
                            "Failed to retrieve Baidu search results for \"$query\". Please check your network connection or try again later.",
                            "Problem occurred while searching for \"$query\". Please ensure your device is connected to the internet and Baidu search services are available.",
                            "Unable to complete the search request for \"$query\". It might be a network connectivity issue or the search term needs adjustment."
                    )
                }

        return (1..numResults.coerceAtMost(3)).map { index ->
            mapOf(
                    "title" to "Unable to retrieve search results for \"$query\"",
                    "link" to "https://www.baidu.com/s?wd=${URLEncoder.encode(query, "UTF-8")}",
                    "snippet" to genericResponses.random()
            )
        }
    }

    /** Setup window parameters for the WebView dialog */
    private fun setupWindowParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            // Set window type
            type =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }

            // Important: Do not set FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE
            // as they would prevent interaction with the WebView
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

            // Make window transparent to properly display the Compose UI
            format = PixelFormat.TRANSLUCENT

            // Full screen window
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT

            // Center the window
            gravity = Gravity.CENTER
        }
    }

    /** Navigate to a specific URL in the WebView */
    private fun navigateToPage(url: String, webView: WebView?) {
        Log.d(TAG, "Navigating to: $url")
        webView?.loadUrl(url)
    }
}

/** 服务生命周期所有者 - 为Compose UI提供生命周期支持 必须在主线程上初始化 */
private class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreField = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        // 确保在主线程上初始化
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 在主线程上，直接初始化
            savedStateRegistryController.performRestore(null)
        } else {
            // 如果不在主线程上，则记录警告（我们应该确保在主线程上创建实例）
            Log.w(
                    "ServiceLifecycleOwner",
                    "Initializing not on main thread. This may cause issues."
            )
            // 在实际使用时应该避免这种情况，代码已经通过 Handler(Looper.getMainLooper()).post 确保在主线程上创建
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreField

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        // 确保生命周期事件在主线程上处理
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycleRegistry.handleLifecycleEvent(event)
        } else {
            // 如果不在主线程上，使用Handler将调用转到主线程
            Handler(Looper.getMainLooper()).post { lifecycleRegistry.handleLifecycleEvent(event) }
        }
    }
}

package com.ai.assistance.operit.ui.features.token

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.features.token.network.DeepseekApiConstants
import com.ai.assistance.operit.ui.features.token.webview.WebViewConfig
import kotlinx.coroutines.launch

/**
 * Deepseek Token配置屏幕
 */
@Composable
fun TokenConfigWebViewScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableStateOf(0) }

    // 创建WebView实例
    val webView = remember { WebViewConfig.createWebView(context) }

    // 导航链接
    data class NavDestination(val title: String, val url: String, val icon: ImageVector)

    val navDestinations = listOf(
        NavDestination(
            "API 密钥",
            DeepseekApiConstants.DEEPSEEK_API_KEYS_URL,
            Icons.Default.Key
        ),
        NavDestination(
            "用量",
            DeepseekApiConstants.DEEPSEEK_USAGE_URL,
            Icons.Default.Dashboard
        ),
        NavDestination(
            "充值",
            "https://platform.deepseek.com/top_up",
            Icons.Default.CreditCard
        ),
        NavDestination(
            "个人资料",
            "https://platform.deepseek.com/profile",
            Icons.Default.Person
        )
    )

    // 导航到指定URL
    fun navigateTo(url: String, index: Int) {
        isLoading = true
        webView.loadUrl(url)
        selectedTabIndex = index
    }

    // 创建WebViewClient
    val webViewClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
            ): Boolean {
                request?.url?.let { uri ->
                    val url = uri.toString()
                    
                    // 检查是否是需要外部应用处理的URL scheme
                    if (url.startsWith("alipay:") || url.startsWith("weixin:") || 
                        url.startsWith("alipays:") || !url.startsWith("http")) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            Log.e("TokenConfigWebView", "无法打开外部应用: ${e.message}")
                            return false
                        }
                    }
                }
                // 允许WebView内的导航
                return false
            }

            // 允许所有SSL证书 - 用于开发环境
            override fun onReceivedSslError(
                    view: WebView?,
                    handler: android.webkit.SslErrorHandler?,
                    error: android.net.http.SslError?
            ) {
                // 在生产环境中应该移除此处理或根据需要处理SSL错误
                handler?.proceed() // 忽略SSL错误，继续加载
            }

            // 允许混合内容（HTTP和HTTPS同时存在）
            @Suppress("deprecation")
            override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(
                        "TokenConfigWebView",
                        "Web error: $errorCode - $description, URL: $failingUrl"
                )
            }

            // Android 6.0以上的错误处理
            override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    Log.e(
                            "TokenConfigWebView",
                            "Web error: ${error?.errorCode} - ${error?.description}, URL: ${request?.url}"
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { finishedUrl ->
                    Log.d("TokenConfigWebView", "Page finished loading: $finishedUrl")

                    // 页面加载完成后配置cookies
                    try {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(view, true)
                        cookieManager.flush()
                    } catch (e: Exception) {
                        Log.e("TokenConfigWebView", "Cookie error: ${e.message}")
                    }

                    // 更新状态
                    scope.launch {
                        isLoading = false
                        currentUrl = finishedUrl

                        // 更新当前选中的标签
                        navDestinations.forEachIndexed { index, destination ->
                            if (finishedUrl.contains(destination.url)) {
                                selectedTabIndex = index
                            }
                        }
                    }
                }
            }
        }
    }

    // 将WebViewClient应用到WebView
    DisposableEffect(webView) {
        webView.webViewClient = webViewClient

        // 加载初始URL
        webView.loadUrl(DeepseekApiConstants.DEEPSEEK_SIGNIN_URL)

        onDispose {
            // 清理资源
            webView.stopLoading()
            webView.destroy()
        }
    }

    // UI布局
    Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // 超简洁导航栏
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shadowElevation = 2.dp // 降低阴影
                ) {
                    Column {
                        HorizontalDivider(
                                color = Color.LightGray.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                        )

                        // 行式导航栏
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(60.dp)
                                                .background(Color.White),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            navDestinations.forEachIndexed { index, destination ->
                                val isSelected = selectedTabIndex == index
                                Column(
                                        modifier =
                                                Modifier.weight(1f)
                                                        .fillMaxSize()
                                                        .clickable {
                                                            navigateTo(destination.url, index)
                                                        }
                                                        .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.title,
                                            modifier = Modifier.size(24.dp),
                                            tint =
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Gray
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text = destination.title,
                                            fontSize = 12.sp,
                                            fontWeight =
                                                    if (isSelected) FontWeight.Medium
                                                    else FontWeight.Normal,
                                            color =
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 确保WebView始终占满整个屏幕宽度，使用更简洁的指针拦截方法
            AndroidView(
                factory = { webView }, 
                modifier = Modifier.fillMaxSize()
                // 移除指针拦截，因为我们已经在WebView自身设置了更可靠的触摸事件处理
            )            // 加载指示器
            if (isLoading) {
                LinearProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                )
            }
        }
    }
}

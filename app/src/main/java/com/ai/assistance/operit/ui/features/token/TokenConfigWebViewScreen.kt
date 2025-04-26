package com.ai.assistance.operit.ui.features.token

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val DEEPSEEK_PLATFORM_URL = "https://platform.deepseek.com/api_keys"

// Helper function to create a preconfigured WebView
@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, onPageFinished: () -> Unit): WebView {
    // Initialize the WebView
    return WebView(context).apply {
        // Configure WebView settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            allowContentAccess = true
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        // Configure WebView client
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Allow all navigations within the WebView
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // Only try to configure cookies after the page has loaded
                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this@apply, true)
                    cookieManager.flush()
                } catch (e: Exception) {
                    // Ignore cookie-related errors
                }
                
                // Notify page finished loading
                onPageFinished()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenConfigWebViewScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    
    // Create WebView instance outside of AndroidView factory
    val webView = remember { 
        createWebView(context) { 
            isLoading = false 
        }
    }
    
    // Load URL only once after WebView is created
    remember(webView) {
        webView.loadUrl(DEEPSEEK_PLATFORM_URL)
        true // Return a value to satisfy remember's requirement
    }

    // 简化布局，移除顶部工具栏
    Box(modifier = Modifier.fillMaxSize()) {
        // Use the pre-created WebView
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        // Show loading indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}

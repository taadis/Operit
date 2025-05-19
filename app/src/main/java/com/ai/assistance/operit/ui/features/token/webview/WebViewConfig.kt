package com.ai.assistance.operit.ui.features.token.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView

/** WebView配置相关工具类 */
object WebViewConfig {
    /** 创建一个预配置的WebView实例 */
    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(context: Context): WebView {
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
                
                // 设置默认的User-Agent
                val originalUserAgent = userAgentString
                userAgentString = originalUserAgent
                
                // 启用缩放控制
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 对于Android 8.0及以上版本的安全Webview优化
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }

            // 配置Cookie
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            
            // Enable WebView debugging
            WebView.setWebContentsDebuggingEnabled(true)

            // Add console logger
            setWebChromeClient(
                    object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Log.d(
                                    "WebViewConsole",
                                    "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                            )
                            return true
                        }
                        
                        // 支持HTML5视频全屏播放
                        override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                            super.onShowCustomView(view, callback)
                        }
                        
                        override fun onHideCustomView() {
                            super.onHideCustomView()
                        }
                        
                        // 支持地理位置请求
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String,
                            callback: android.webkit.GeolocationPermissions.Callback
                        ) {
                            callback.invoke(origin, true, false)
                        }
                        
                        // 处理JavaScript警告和错误
                        override fun onJsAlert(
                            view: WebView?, 
                            url: String?, 
                            message: String?, 
                            result: android.webkit.JsResult?
                        ): Boolean {
                            Log.d("WebViewJS", "Alert: $message")
                            result?.confirm()
                            return true
                        }
                        
                        override fun onJsConfirm(
                            view: WebView?, 
                            url: String?, 
                            message: String?, 
                            result: android.webkit.JsResult?
                        ): Boolean {
                            Log.d("WebViewJS", "Confirm: $message")
                            result?.confirm()
                            return true
                        }
                        
                        override fun onJsPrompt(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            defaultValue: String?,
                            result: android.webkit.JsPromptResult?
                        ): Boolean {
                            Log.d("WebViewJS", "Prompt: $message, Default: $defaultValue")
                            result?.confirm(defaultValue)
                            return true
                        }
                    }
            )
        }
    }
}

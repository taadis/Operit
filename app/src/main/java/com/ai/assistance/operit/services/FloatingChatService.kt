package com.ai.assistance.operit.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.core.model.ChatMessage
import com.ai.assistance.operit.service.ui.FloatingChatWindow
import com.ai.assistance.operit.service.ui.FloatingWindowTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class FloatingChatService : Service() {
    private val TAG = "FloatingChatService"
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var isViewAdded = false
    private val binder = LocalBinder()
    
    // 前台服务ID和通知渠道ID
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "floating_chat_channel"
    
    // 保存服务状态的SharedPreferences
    private lateinit var prefs: SharedPreferences
    
    // WakeLock实例，用于保持CPU运行
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 在60秒超时前切换到普通服务的Handler
    private val serviceTimeoutHandler = Handler(Looper.getMainLooper())
    private val serviceTimeoutRunnable = Runnable {
        try {
            Log.d(TAG, "短期前台服务即将超时，切换到普通服务模式")
            // 停止前台服务，但保持服务运行
            stopForeground(STOP_FOREGROUND_DETACH)
        } catch (e: Exception) {
            Log.e(TAG, "切换服务模式时出错", e)
        }
    }
    
    // 为ComposeView创建生命周期所有者
    private lateinit var lifecycleOwner: ServiceLifecycleOwner
    
    // 消息列表状态
    private val chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    
    // 初始位置坐标
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    
    // 添加大小调整相关的状态
    private val windowWidth = mutableStateOf(300.dp)
    private val windowHeight = mutableStateOf(400.dp)
    private val isResizing = mutableStateOf(false)
    private var initialWidth: Int = 0
    private var initialHeight: Int = 0
    
    // 添加悬浮球模式相关状态
    private val isBallMode = mutableStateOf(false)
    private val ballSize = mutableStateOf(56.dp) // 悬浮球的默认大小
    
    // 崩溃恢复
    private var lastCrashTime = 0L
    private var crashCount = 0
    
    // 全局异常处理器
    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val customExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        handleServiceCrash(thread, throwable)
    }
    
    // Add SharedFlow for message updates
    private val messageUpdateFlow = MutableSharedFlow<List<ChatMessage>>(replay = 1)
    
    // Add coroutine scope for the service
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    inner class LocalBinder : Binder() {
        fun getService(): FloatingChatService = this@FloatingChatService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    // 处理服务崩溃
    private fun handleServiceCrash(thread: Thread, throwable: Throwable) {
        try {
            // 记录崩溃信息
            Log.e(TAG, "Service crashed: ${throwable.message}", throwable)
            
            // 记录崩溃时间和计数
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCrashTime > 60000) { // 重置计数器（如果超过1分钟）
                crashCount = 0
            }
            lastCrashTime = currentTime
            crashCount++
            
            // 如果短时间内崩溃次数过多，停止重试
            if (crashCount > 3) {
                Log.e(TAG, "Too many crashes in short time, stopping service")
                prefs.edit().putBoolean("service_disabled_due_to_crashes", true).apply()
                stopSelf()
                return
            }
            
            // 保存当前状态
            saveWindowState()
            
            // 尝试重启服务
            val intent = Intent(applicationContext, FloatingChatService::class.java)
            intent.setPackage(packageName)
            startService(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling crash", e)
        } finally {
            // 如果仍然崩溃，让默认处理器处理
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        
        // 设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler)
        
        // 检查是否因为崩溃太多而被禁用
        prefs = getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service_disabled_due_to_crashes", false)) {
            Log.w(TAG, "Service was disabled due to frequent crashes")
            stopSelf()
            return
        }
        
        try {
            // 初始化WakeLock
            acquireWakeLock()
            
            // 初始化WindowManager
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            // 初始化生命周期所有者
            lifecycleOwner = ServiceLifecycleOwner()
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            
            // 尝试恢复上次的位置和大小
            restoreWindowState()
            
            // 创建通知渠道
            createNotificationChannel()
            
            // 开始前台服务，确保服务在后台稳定运行
            startForeground(NOTIFICATION_ID, createNotification())
            
            // 设置在前台服务超时前切换到普通服务的定时任务
            // 设置为55秒，留出足够的缓冲时间
            serviceTimeoutHandler.postDelayed(serviceTimeoutRunnable, 55 * 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }
    
    /**
     * 获取WakeLock，保持CPU运行
     */
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "OperitApp:FloatingChatServiceWakeLock"
                )
                wakeLock?.setReferenceCounted(false)
            }
            
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10*60*1000L) // 10分钟超时
                Log.d(TAG, "WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }
    
    /**
     * 释放WakeLock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }
    
    // 创建通知渠道
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AI助手悬浮窗"
            val descriptionText = "显示AI助手的悬浮窗服务"
            val importance = NotificationManager.IMPORTANCE_LOW // 使用LOW级别减少视觉干扰
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false) // 不在启动器图标上显示通知角标
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    // 创建前台服务所需的通知
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info) // 替换为您的应用图标
        .setContentTitle("AI助手悬浮窗")
        .setContentText("AI助手正在后台运行")
        .setPriority(NotificationCompat.PRIORITY_LOW) // 低优先级减少视觉干扰
        .setOngoing(true) // 用户不能滑动删除
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setTimeoutAfter(60 * 1000) // 必须为短期服务设置超时，这里设置为60秒
        // 添加一个返回主应用的Intent
        .setContentIntent(getPendingIntent())
        .build()
        
    // 创建一个返回主应用的PendingIntent
    private fun getPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this, 0, intent, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE 
            else 0
        )
    }
    
    // 保存窗口状态
    private fun saveWindowState() {
        prefs.edit().apply {
            putInt("window_x", initialX)
            putInt("window_y", initialY)
            putFloat("window_width", windowWidth.value.value)
            putFloat("window_height", windowHeight.value.value)
            putBoolean("is_ball_mode", isBallMode.value)
            putFloat("ball_size", ballSize.value.value)
            apply()
        }
    }
    
    // 恢复窗口状态
    private fun restoreWindowState() {
        initialX = prefs.getInt("window_x", 0)
        initialY = prefs.getInt("window_y", 100)
        windowWidth.value = Dp(prefs.getFloat("window_width", 300f))
        windowHeight.value = Dp(prefs.getFloat("window_height", 400f))
        isBallMode.value = prefs.getBoolean("is_ball_mode", false)
        ballSize.value = Dp(prefs.getFloat("ball_size", 56f))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        try {
            // 确保WakeLock处于活动状态
            acquireWakeLock()
            
            // 从Intent获取聊天消息
            if (intent?.hasExtra("CHAT_MESSAGES") == true) {
                val messagesArray = intent.getParcelableArrayExtra("CHAT_MESSAGES")
                if (messagesArray != null) {
                    val messages = mutableListOf<ChatMessage>()
                    for (i in messagesArray.indices) {
                        val message = messagesArray[i] as? ChatMessage
                        if (message != null) {
                            messages.add(message)
                        }
                    }
                    updateChatMessages(messages)
                }
            }
            
            // 如果悬浮窗还没有添加，则创建并添加
            if (!isViewAdded) {
                createFloatingView()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }
        
        // 使用START_STICKY确保如果系统终止服务，会尝试重新创建它
        return START_STICKY
    }
    
    // 如果系统由于内存不足而终止服务并随后重新创建，恢复上次状态
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved")
        // 用户在最近任务中滑动应用时重启服务
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }
    
    // 处理低内存情况
    override fun onLowMemory() {
        super.onLowMemory()
        Log.d(TAG, "onLowMemory: 系统内存不足")
        // 保存当前状态，便于之后恢复
        saveWindowState()
    }
    
    // 处理系统回收内存的请求
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory: level=$level")
        
        when (level) {
            // 如果处于后台且内存紧张，保存状态
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                saveWindowState() 
            }
            // 如果已进入后台状态，也保存状态
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                saveWindowState()
            }
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        try {
            // 创建ComposeView并设置生命周期所有者
            val view = ComposeView(this)
            
            // 为View设置必要的生命周期所有者
            view.setViewTreeLifecycleOwner(lifecycleOwner as LifecycleOwner)
            view.setViewTreeViewModelStoreOwner(lifecycleOwner as ViewModelStoreOwner)
            view.setViewTreeSavedStateRegistryOwner(lifecycleOwner as SavedStateRegistryOwner)
            
            view.setContent {
                // 使用自定义悬浮窗主题而不是应用主题，避免依赖Activity上下文
                FloatingWindowTheme {
                    FloatingChatWindow(
                        messages = chatMessages.value,
                        width = windowWidth.value,
                        height = windowHeight.value,
                        onClose = { 
                            Log.d(TAG, "Close button clicked, stopping service")
                            // 延迟200毫秒后关闭
                            Handler(Looper.getMainLooper()).postDelayed({
                                // 先移除视图，再停止服务
                                if (isViewAdded) {
                                    try {
                                        composeView?.let {
                                            windowManager.removeView(it)
                                        }
                                        isViewAdded = false
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error removing floating view", e)
                                    }
                                }
                                // 停止服务 不了
                                stopSelf()
                            }, 200)
                        },
                        onResize = { newWidth, newHeight ->
                            windowWidth.value = newWidth
                            windowHeight.value = newHeight
                            
                            // 更新窗口大小
                            val params = view.layoutParams as WindowManager.LayoutParams
                            params.width = WindowManager.LayoutParams.WRAP_CONTENT
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            windowManager.updateViewLayout(view, params)
                        },
                        isBallMode = isBallMode.value,
                        ballSize = ballSize.value,
                        onToggleBallMode = { 
                            isBallMode.value = !isBallMode.value
                            saveWindowState()
                            
                            // 更新窗口大小
                            val params = view.layoutParams as WindowManager.LayoutParams
                            params.width = WindowManager.LayoutParams.WRAP_CONTENT
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            windowManager.updateViewLayout(view, params)
                        }
                    )
                }
            }
            this.composeView = view
            
            // 设置WindowManager参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            // 使用恢复的位置
            params.gravity = Gravity.TOP or Gravity.START
            params.x = initialX
            params.y = initialY
            
            // 设置触摸监听以实现拖动和调整大小
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        
                        // 记录当前窗口大小
                        initialWidth = windowWidth.value.value.toInt()
                        initialHeight = windowHeight.value.value.toInt()
                        
                        // 默认返回false以允许其他触摸事件处理（如滚动）
                        // 除非点击的是窗口边缘区域
                        val isEdgeTouch = isEdgeTouch(event.x, event.y, view.width, view.height)
                        isResizing.value = isEdgeTouch
                        
                        isEdgeTouch
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isResizing.value) {
                            // 调整大小逻辑由Compose内部处理
                            true
                        } else {
                            // 移动窗口 - 简单地让所有非边缘的触摸处理为拖动
                            // 这样整个窗口表面都可以拖动
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(v, params)
                            true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 当触摸结束时，保存当前位置
                        initialX = params.x
                        initialY = params.y
                        isResizing.value = false
                        // 保存当前状态到SharedPreferences
                        saveWindowState()
                        true
                    }
                    else -> false
                }
            }
            
            // 添加到WindowManager
            windowManager.addView(view, params)
            isViewAdded = true
            Log.d(TAG, "Floating Compose view created and added")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating view", e)
            
            // 尝试重建视图最多3次
            val retryCount = prefs.getInt("view_creation_retry", 0)
            if (retryCount < 3) {
                prefs.edit().putInt("view_creation_retry", retryCount + 1).apply()
                
                // 延迟一秒后重试
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (isViewAdded) return@postDelayed
                        Log.d(TAG, "Retrying view creation, attempt: ${retryCount + 1}")
                        createFloatingView()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in retry", e)
                    }
                }, 1000)
            } else {
                // 重试次数过多，停止服务
                stopSelf()
            }
        }
    }
    
    // 检查是否点击的是窗口边缘区域（用于调整大小）
    private fun isEdgeTouch(x: Float, y: Float, width: Int, height: Int): Boolean {
        val edgeSize = 24f // 边缘区域大小
        return x >= width - edgeSize && y >= height - edgeSize // 右下角
    }
    
    fun updateChatMessages(messages: List<ChatMessage>) {
        CoroutineScope(Dispatchers.Main).launch {
            // Remove the messageUpdateFlow emit since we're not using collectAsState
            // messageUpdateFlow.emit(messages)
            chatMessages.value = messages
        }
    }
    
    override fun onDestroy() {
        try {
            // 释放WakeLock
            releaseWakeLock()
            
            // Cancel all coroutines when service is destroyed
            serviceScope.cancel()
            
            // 在销毁前保存窗口状态
            saveWindowState()
            
            // 移除超时处理
            serviceTimeoutHandler.removeCallbacks(serviceTimeoutRunnable)
            
            super.onDestroy()
            Log.d(TAG, "onDestroy")
            
            // 更新生命周期状态
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            
            if (isViewAdded) {
                try {
                    composeView?.let {
                        windowManager.removeView(it)
                    }
                    isViewAdded = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing floating view", e)
                }
            }
            
            // 重置异常处理器
            Thread.setDefaultUncaughtExceptionHandler(defaultExceptionHandler)
            
            // 重置重试计数
            prefs.edit().putInt("view_creation_retry", 0).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
} 
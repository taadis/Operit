package com.ai.assistance.operit.services

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
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
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
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.floating.FloatingChatWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Observer
import android.view.inputmethod.InputMethodManager

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

    // 记录窗口<->球转换的原始位置
    private var lastWindowPositionX: Int = 0
    private var lastWindowPositionY: Int = 0
    private var lastBallPositionX: Int = 0
    private var lastBallPositionY: Int = 0

    // 用于计算中心对齐的转换
    private fun calculateCenteredPosition(
        fromX: Int,
        fromY: Int,
        fromWidth: Int,
        fromHeight: Int,
        toWidth: Int,
        toHeight: Int
    ): Pair<Int, Int> {
        // 计算原始形状的中心点
        val centerX = fromX + fromWidth / 2
        val centerY = fromY + fromHeight / 2

        // 计算新位置，保持中心点对齐
        val newX = centerX - toWidth / 2
        val newY = centerY - toHeight / 2

        return Pair(newX, newY)
    }

    // 转换状态 - 用于防止多次快速切换
    private var isTransitioning = false
    private val transitionDebounceTime = 500L // 防抖时间

    // 崩溃恢复
    private var lastCrashTime = 0L
    private var crashCount = 0

    // 全局异常处理器
    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val customExceptionHandler =
        Thread.UncaughtExceptionHandler { thread, throwable ->
            handleServiceCrash(thread, throwable)
        }

    // Add SharedFlow for message updates
    private val messageUpdateFlow = MutableSharedFlow<List<ChatMessage>>(replay = 1)

    // Add coroutine scope for the service
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 添加窗口缩放值
    private val windowScale = mutableStateOf(1.0f)

    // Fix the shared flow for sending messages from the floating window to the ViewModel
    private val _messageToSend = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageToSend: SharedFlow<String> get() = _messageToSend

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

    /** 获取WakeLock，保持CPU运行 */
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock =
                    powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "OperitApp:FloatingChatServiceWakeLock"
                    )
                wakeLock?.setReferenceCounted(false)
            }

            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L) // 10分钟超时
                Log.d(TAG, "WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    /** 释放WakeLock */
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
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    setShowBadge(false) // 不在启动器图标上显示通知角标
                }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 创建前台服务所需的通知
    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
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
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
            else 0
        )
    }

    // 保存窗口状态
    private fun saveWindowState() {
        prefs.edit().apply {
            putInt("window_x", initialX)
            putInt("window_y", initialY)
            putFloat("window_width", windowWidth.value.value.coerceAtLeast(200f))
            putFloat("window_height", windowHeight.value.value.coerceAtLeast(250f))
            putBoolean("is_ball_mode", isBallMode.value)
            putFloat("ball_size", ballSize.value.value)
            // 保存当前缩放比例，确保在合理范围内
            putFloat("window_scale", windowScale.value.coerceIn(0.5f, 1.0f))
            apply()
        }
    }

    // 恢复窗口状态
    private fun restoreWindowState() {
        initialX = prefs.getInt("window_x", 0)
        initialY = prefs.getInt("window_y", 100)
        windowWidth.value = Dp(prefs.getFloat("window_width", 300f).coerceAtLeast(200f))
        windowHeight.value = Dp(prefs.getFloat("window_height", 400f).coerceAtLeast(250f))
        isBallMode.value = prefs.getBoolean("is_ball_mode", false)
        ballSize.value = Dp(prefs.getFloat("ball_size", 56f).coerceAtLeast(40f))
        // 恢复缩放比例，确保在合理范围内
        windowScale.value = prefs.getFloat("window_scale", 1.0f).coerceIn(0.5f, 1.0f)
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
                // Use standard theme instead of FloatingWindowTheme to maintain consistent
                // appearance
                MaterialTheme {
                    FloatingChatWindow(
                        messages = chatMessages.value,
                        width = windowWidth.value,
                        height = windowHeight.value,
                        initialWindowScale = windowScale.value,
                        onClose = {
                            Log.d(TAG, "Close button clicked, stopping service")
                            // 延迟200毫秒后关闭
                            Handler(Looper.getMainLooper())
                                .postDelayed(
                                    {
                                        // 先移除视图，再停止服务
                                        if (isViewAdded) {
                                            try {
                                                composeView?.let {
                                                    windowManager.removeView(it)
                                                }
                                                isViewAdded = false
                                            } catch (e: Exception) {
                                                Log.e(
                                                    TAG,
                                                    "Error removing floating view",
                                                    e
                                                )
                                            }
                                        }
                                        // 停止服务 不了
                                        stopSelf()
                                    },
                                    200
                                )
                        },
                        onResize = { newWidth, newHeight ->
                            windowWidth.value = newWidth
                            windowHeight.value = newHeight

                            // 更新窗口大小
                            val params = view.layoutParams as WindowManager.LayoutParams
                            params.width = WindowManager.LayoutParams.WRAP_CONTENT
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            // 确保窗口参数仍然包含FLAG_LAYOUT_NO_LIMITS标志
                            params.flags =
                                params.flags or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            windowManager.updateViewLayout(view, params)

                            // 保存调整后的窗口状态
                            saveWindowState()
                        },
                        isBallMode = isBallMode.value,
                        ballSize = ballSize.value,
                        onToggleBallMode = {
                            // 转换前判断防抖
                            if (isTransitioning) {
                                Log.d(TAG, "转换中，忽略切换请求")
                                return@FloatingChatWindow
                            }

                            isTransitioning = true

                            // 获取当前参数
                            val currentParams = view.layoutParams as WindowManager.LayoutParams

                            // 保存当前模式的位置
                            if (isBallMode.value) {
                                lastBallPositionX = currentParams.x
                                lastBallPositionY = currentParams.y
                            } else {
                                lastWindowPositionX = currentParams.x
                                lastWindowPositionY = currentParams.y
                            }

                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val screenHeight = displayMetrics.heightPixels
                            val density = displayMetrics.density

                            // 计算当前视图的尺寸
                            val currentWidth: Int
                            val currentHeight: Int

                            if (isBallMode.value) {
                                // 当前是球模式，计算球的尺寸
                                currentWidth = (ballSize.value.value * density).toInt()
                                currentHeight = currentWidth
                            } else {
                                // 当前是窗口模式，计算窗口的尺寸
                                currentWidth =
                                    (windowWidth.value.value * density * windowScale.value).toInt()
                                currentHeight =
                                    (windowHeight.value.value * density * windowScale.value).toInt()
                            }

                            // 切换模式
                            isBallMode.value = !isBallMode.value

                            // 保存新状态
                            saveWindowState()

                            // 准备更新窗口参数
                            val params = currentParams
                            params.width = WindowManager.LayoutParams.WRAP_CONTENT
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT

                            // 计算新模式的尺寸
                            val newWidth: Int
                            val newHeight: Int

                            if (isBallMode.value) {
                                // 切换到球模式
                                val ballSizeInPx = (ballSize.value.value * density).toInt()
                                newWidth = ballSizeInPx
                                newHeight = ballSizeInPx

                                // 如果有之前保存的球位置，优先使用
                                if (lastBallPositionX != 0 && lastBallPositionY != 0) {
                                    params.x = lastBallPositionX
                                    params.y = lastBallPositionY
                                } else {
                                    // 否则计算保持中心对齐的位置
                                    val (centeredX, centeredY) = calculateCenteredPosition(
                                        params.x,
                                        params.y,
                                        currentWidth,
                                        currentHeight,
                                        newWidth,
                                        newHeight
                                    )
                                    params.x = centeredX
                                    params.y = centeredY
                                }

                                // 确保球完全在屏幕内可见，预留边距
                                val margin = (16 * density).toInt()
                                params.x =
                                    params.x.coerceIn(
                                        margin,
                                        screenWidth - ballSizeInPx - margin
                                    )
                                params.y =
                                    params.y.coerceIn(
                                        margin,
                                        screenHeight - ballSizeInPx - margin
                                    )

                                // 更新保存的位置
                                initialX = params.x
                                initialY = params.y

                                // 调整窗口缩放以允许更小的球模式
                                windowScale.value = windowScale.value.coerceIn(0.5f, 1.0f)

                                Log.d(
                                    TAG,
                                    "切换到球模式，位置: x=${params.x}, y=${params.y}, 缩放: ${windowScale.value}"
                                )
                            } else {
                                // 切换到窗口模式
                                newWidth =
                                    (windowWidth.value.value * density * windowScale.value).toInt()
                                newHeight =
                                    (windowHeight.value.value * density * windowScale.value).toInt()

                                // 使用上一次保存的窗口位置，如果有的话
                                if (lastWindowPositionX != 0 && lastWindowPositionY != 0) {
                                    params.x = lastWindowPositionX
                                    params.y = lastWindowPositionY
                                } else {
                                    // 否则计算保持中心对齐的位置
                                    val (centeredX, centeredY) = calculateCenteredPosition(
                                        params.x,
                                        params.y,
                                        currentWidth,
                                        currentHeight,
                                        newWidth,
                                        newHeight
                                    )
                                    params.x = centeredX
                                    params.y = centeredY
                                }

                                // 确保窗口位置合理
                                val minVisibleWidth = newWidth / 3
                                val minVisibleHeight = newHeight / 3

                                params.x = params.x.coerceIn(
                                    -newWidth + minVisibleWidth,
                                    screenWidth - minVisibleWidth
                                )

                                params.y = params.y.coerceIn(
                                    0, // 不允许超出顶部
                                    screenHeight - minVisibleHeight
                                )

                                // 更新保存的位置
                                initialX = params.x
                                initialY = params.y

                                // 确保窗口模式下的缩放合理
                                windowScale.value = windowScale.value.coerceAtLeast(0.6f)

                                Log.d(
                                    TAG,
                                    "切换到窗口模式，位置: x=${params.x}, y=${params.y}, 缩放: ${windowScale.value}"
                                )
                            }

                            // 确保窗口参数仍然包含FLAG_LAYOUT_NO_LIMITS标志
                            params.flags =
                                params.flags or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            windowManager.updateViewLayout(view, params)

                            // 延迟重置转换标志以防止快速多次切换
                            Handler(Looper.getMainLooper()).postDelayed({
                                isTransitioning = false
                            }, transitionDebounceTime)
                        },
                        // 处理移动回调
                        onMove = { dx, dy, currentScale ->
                            val params = view.layoutParams as WindowManager.LayoutParams

                            try {
                                // 获取屏幕尺寸
                                val displayMetrics = resources.displayMetrics
                                val screenWidth = displayMetrics.widthPixels
                                val screenHeight = displayMetrics.heightPixels
                                val density = displayMetrics.density

                                // 更新存储的缩放值
                                windowScale.value = currentScale

                                // 记录旧位置
                                val oldX = params.x
                                val oldY = params.y

                                // 根据模式设置不同的移动灵敏度
                                val sensitivity =
                                    if (isBallMode.value) {
                                        0.9f // 球模式使用固定灵敏度，不受缩放影响
                                    } else {
                                        currentScale // 窗口模式灵敏度与缩放比例成正比
                                    }

                                // 应用灵敏度并更新位置
                                params.x += (dx * sensitivity).toInt()
                                params.y += (dy * sensitivity).toInt()

                                // 根据模式计算可移动边界
                                if (isBallMode.value) {
                                    // 球模式：确保球至少有一半在屏幕内
                                    val ballSize = (ballSize.value.value * density).toInt()
                                    val minVisible = ballSize / 2 // 确保至少一半球可见

                                    // 更严格的边界限制
                                    params.x =
                                        params.x.coerceIn(
                                            -ballSize + minVisible, // 左边界
                                            screenWidth - minVisible // 右边界
                                        )

                                    params.y =
                                        params.y.coerceIn(
                                            0, // 上边界 - 限制不超出顶部
                                            screenHeight - minVisible // 下边界
                                        )
                                } else {
                                    // 窗口模式：严格限制
                                    val windowWidth =
                                        (windowWidth.value.value * density * currentScale)
                                            .toInt()
                                    val windowHeight =
                                        (windowHeight.value.value * density * currentScale)
                                            .toInt()

                                    // 确保2/3窗口内容可见
                                    val minVisibleWidth = (windowWidth * 2 / 3)
                                    val minVisibleHeight = (windowHeight * 2 / 3)

                                    // 对称的边界限制 - 左右两边限制一致
                                    params.x =
                                        params.x.coerceIn(
                                            -(windowWidth -
                                                minVisibleWidth), // 左边界 - 允许1/3移出
                                            screenWidth -
                                                minVisibleWidth / 2 // 右边界 - 允许1/3移出
                                        )

                                    params.y =
                                        params.y.coerceIn(
                                            0, // 上边界 - 不允许超出顶部
                                            screenHeight -
                                                minVisibleHeight // 下边界 - 允许1/3移出
                                        )
                                }

                                // 确保FLAG_LAYOUT_NO_LIMITS设置
                                params.flags =
                                    params.flags or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

                                // 更新窗口位置
                                windowManager.updateViewLayout(view, params)

                                // 保存当前位置
                                initialX = params.x
                                initialY = params.y

                                // 日志记录
                                Log.d(
                                    TAG,
                                    "窗口移动: 当前位置(${params.x},${params.y}), 缩放:$currentScale, 灵敏度:$sensitivity"
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "移动窗口失败: ${e.message}")
                            }
                        },
                        // 添加保存窗口状态的回调
                        saveWindowState = {
                            // 保存当前窗口状态
                            this@FloatingChatService.saveWindowState()
                        },
                        // Add message sending callback
                        onSendMessage = { message ->
                            sendMessage(message)
                        },
                        // 添加输入焦点切换回调
                        onInputFocusRequest = { needsFocus ->
                            Log.d(TAG, "输入框焦点请求: $needsFocus")

                            try {
                                // 获取当前窗口参数
                                val params = view.layoutParams as WindowManager.LayoutParams

                                if (needsFocus) {
                                    // 如果需要焦点，移除不可聚焦标志
                                    params.flags =
                                        params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                                    Log.d(TAG, "移除不可聚焦标志，允许输入法显示")

                                    // 确保窗口参数仍然包含FLAG_LAYOUT_NO_LIMITS标志
                                    params.flags =
                                        params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

                                    // 更新窗口参数
                                    windowManager.updateViewLayout(view, params)

                                    // 延迟处理确保布局更新完成后再请求焦点和显示键盘
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        try {
                                            // 请求焦点
                                            view.requestFocus()

                                            // 查找当前获得焦点的视图并显示键盘
                                            view.findFocus()?.let { focused ->
                                                val imm =
                                                    getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                imm?.showSoftInput(
                                                    focused,
                                                    InputMethodManager.SHOW_IMPLICIT
                                                )
                                                Log.d(TAG, "请求显示输入法")
                                            } ?: run {
                                                // 如果没有找到焦点视图，使用备用方式尝试显示键盘
                                                val imm =
                                                    getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                imm?.toggleSoftInput(
                                                    InputMethodManager.SHOW_FORCED,
                                                    0
                                                )
                                                Log.d(TAG, "使用备用方式请求显示输入法")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "请求焦点或显示输入法失败", e)
                                        }
                                    }, 200) // 增加延迟确保窗口参数已更新
                                } else {
                                    // 先隐藏输入法，再恢复不可聚焦标志
                                    val imm =
                                        getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                                    Log.d(TAG, "隐藏输入法")

                                    // 短暂延迟后恢复不可聚焦标志，确保输入法有时间隐藏
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        // 恢复不可聚焦标志
                                        params.flags =
                                            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                        Log.d(TAG, "恢复不可聚焦标志")

                                        // 确保窗口参数仍然包含FLAG_LAYOUT_NO_LIMITS标志
                                        params.flags =
                                            params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

                                        // 更新窗口参数
                                        windowManager.updateViewLayout(view, params)
                                    }, 100)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "切换焦点模式失败", e)
                            }
                        }
                    )
                }
            }
            this.composeView = view

            // 设置WindowManager参数
            val params =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams
                            .FLAG_LAYOUT_NO_LIMITS, // 添加FLAG_LAYOUT_NO_LIMITS允许窗口超出屏幕边界
                    PixelFormat.TRANSLUCENT
                )

            // 使用恢复的位置，但增加安全检查
            params.gravity = Gravity.TOP or Gravity.START

            // 安全检查：确保窗口位置在屏幕范围内
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            if (isBallMode.value) {
                // 球模式安全检查
                val ballSizeInPx = (ballSize.value.value * density).toInt()
                val safeMargin = (16 * density).toInt()

                // 确保球至少1/2在屏幕内可见
                val minVisible = ballSizeInPx / 2
                initialX =
                    initialX.coerceIn(
                        -ballSizeInPx + minVisible + safeMargin,
                        screenWidth - minVisible - safeMargin
                    )
                initialY = initialY.coerceIn(safeMargin, screenHeight - minVisible - safeMargin)

                Log.d(TAG, "安全检查：确保球模式位置在屏幕内: x=${initialX}, y=${initialY}")
            } else {
                // 窗口模式安全检查
                val windowWidth = (windowWidth.value.value * density * windowScale.value).toInt()
                val windowHeight = (windowHeight.value.value * density * windowScale.value).toInt()
                val minVisibleWidth = (windowWidth * 2 / 3)
                val safeMargin = (20 * density).toInt()

                // 确保窗口至少2/3在屏幕内可见
                initialX =
                    initialX.coerceIn(
                        -(windowWidth - minVisibleWidth) + safeMargin,
                        screenWidth - minVisibleWidth - safeMargin
                    )
                initialY =
                    initialY.coerceIn(
                        safeMargin,
                        screenHeight - (windowHeight / 2) - safeMargin
                    )

                Log.d(TAG, "安全检查：确保窗口模式位置在屏幕内: x=${initialX}, y=${initialY}")
            }

            params.x = initialX
            params.y = initialY

            // 设置触摸监听以实现拖动和调整大小
            // 注意：直接使用Compose内部的拖动处理，不要在这里添加触摸监听器
            view.setOnTouchListener { _, _ -> false }

            // 添加到WindowManager
            windowManager.addView(view, params)
            isViewAdded = true
            Log.d(
                TAG,
                "Floating Compose view created and added at position x=${params.x}, y=${params.y}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating view", e)

            // 尝试重建视图最多3次
            val retryCount = prefs.getInt("view_creation_retry", 0)
            if (retryCount < 3) {
                prefs.edit().putInt("view_creation_retry", retryCount + 1).apply()

                // 延迟一秒后重试
                Handler(Looper.getMainLooper())
                    .postDelayed(
                        {
                            try {
                                if (isViewAdded) return@postDelayed
                                Log.d(
                                    TAG,
                                    "Retrying view creation, attempt: ${retryCount + 1}"
                                )
                                createFloatingView()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in retry", e)
                            }
                        },
                        1000
                    )
            } else {
                // 重试次数过多，停止服务
                stopSelf()
            }
        }
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
                    composeView?.let { windowManager.removeView(it) }
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

    // Add a function to send a message from the floating window
    fun sendMessage(message: String) {
        Log.d(TAG, "Sending message from floating window: $message")
        serviceScope.launch {
            _messageToSend.emit(message)
        }
    }
}

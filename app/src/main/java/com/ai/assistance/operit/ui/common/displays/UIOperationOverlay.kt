package com.ai.assistance.operit.ui.common.displays

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import kotlinx.coroutines.delay

/**
 * 显示UI操作视觉反馈的悬浮窗
 * 可以显示点击、滑动、文本输入等操作的位置指示器
 */
class UIOperationOverlay(private val context: Context) {
    private val TAG = "UIOperationOverlay"
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    
    // UI状态
    private val operationType = mutableStateOf<OperationType>(OperationType.None)
    private val tapPosition = mutableStateOf(Pair(0, 0))
    private val swipeStart = mutableStateOf(Pair(0, 0))
    private val swipeEnd = mutableStateOf(Pair(0, 0))
    private val textInputPosition = mutableStateOf(Pair(0, 0))
    private val textValue = mutableStateOf("")
    
    // 自动隐藏计时器
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    
    // 操作类型
    sealed class OperationType {
        object None : OperationType()
        object Tap : OperationType()
        object Swipe : OperationType()
        object TextInput : OperationType()
    }
    
    /**
     * 检查是否有悬浮窗权限
     */
    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission() {
        if (!hasOverlayPermission()) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting overlay permission", e)
            }
        }
    }
    
    /**
     * 确保在主线程上执行操作
     */
    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
    
    /**
     * 初始化和显示悬浮窗
     */
    private fun initOverlay() {
        if (overlayView != null) return
        
        if (!hasOverlayPermission()) {
            Log.e(TAG, "Cannot show overlay without permission")
            requestOverlayPermission()
            return
        }
        
        // 确保在主线程上初始化UI组件
        runOnMainThread {
            try {
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                
                val params = WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    // 关键flags：不接受焦点，不阻挡触摸事件传递到下层应用
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.TOP or Gravity.START
                    // 设置足够高的层级确保可见性
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        windowAnimations = android.R.style.Animation_Toast
                    }
                }
                
                lifecycleOwner = ServiceLifecycleOwner().apply {
                    handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    handleLifecycleEvent(Lifecycle.Event.ON_START)
                    handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }
                
                overlayView = ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    
                    // 设置生命周期所有者
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeViewModelStoreOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    
                    setContent {
                        MaterialTheme {
                            OperationFeedbackContent(
                                operationType = operationType.value,
                                tapPosition = tapPosition.value,
                                swipeStart = swipeStart.value,
                                swipeEnd = swipeEnd.value,
                                textInputPosition = textInputPosition.value,
                                textValue = textValue.value
                            )
                        }
                    }
                }
                
                windowManager?.addView(overlayView, params)
                Log.d(TAG, "Overlay view added successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding overlay view", e)
                // 清理资源
                lifecycleOwner = null
                overlayView = null
                windowManager = null
            }
        }
    }
    
    /**
     * 显示点击操作反馈
     */
    fun showTap(x: Int, y: Int, autoHideDelayMs: Long = 1500) {
        Log.d(TAG, "Showing tap at ($x, $y)")
        
        // 更新状态
        tapPosition.value = Pair(x, y)
        
        runOnMainThread {
            // 先初始化悬浮窗
            initOverlay()
            
            // 更新操作类型
            operationType.value = OperationType.Tap
            
            // 设置自动隐藏计时器
            scheduleHide(autoHideDelayMs)
        }
    }
    
    /**
     * 显示滑动操作反馈
     */
    fun showSwipe(startX: Int, startY: Int, endX: Int, endY: Int, autoHideDelayMs: Long = 1500) {
        Log.d(TAG, "Showing swipe from ($startX, $startY) to ($endX, $endY)")
        
        // 更新状态
        swipeStart.value = Pair(startX, startY)
        swipeEnd.value = Pair(endX, endY)
        
        runOnMainThread {
            // 先初始化悬浮窗
            initOverlay()
            
            // 更新操作类型
            operationType.value = OperationType.Swipe
            
            // 设置自动隐藏计时器
            scheduleHide(autoHideDelayMs)
        }
    }
    
    /**
     * 显示文本输入操作反馈
     */
    fun showTextInput(x: Int, y: Int, text: String, autoHideDelayMs: Long = 2000) {
        Log.d(TAG, "Showing text input at ($x, $y): $text")
        
        // 更新状态
        textInputPosition.value = Pair(x, y)
        textValue.value = text
        
        runOnMainThread {
            // 先初始化悬浮窗
            initOverlay()
            
            // 更新操作类型
            operationType.value = OperationType.TextInput
            
            // 设置自动隐藏计时器
            scheduleHide(autoHideDelayMs)
        }
    }
    
    /**
     * 安排自动隐藏
     */
    private fun scheduleHide(delayMs: Long) {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, delayMs)
    }
    
    /**
     * 隐藏反馈悬浮窗
     */
    fun hide() {
        runOnMainThread {
            try {
                handler.removeCallbacks(hideRunnable)
                operationType.value = OperationType.None
                
                // 只在完全关闭时移除视图
                if (overlayView != null) {
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    
                    try {
                        windowManager?.removeView(overlayView)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing overlay view", e)
                    }
                    
                    overlayView = null
                    lifecycleOwner = null
                    windowManager = null
                    Log.d(TAG, "Overlay view dismissed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing overlay view", e)
            }
        }
    }
}

/**
 * 操作反馈UI内容
 */
@Composable
private fun OperationFeedbackContent(
    operationType: UIOperationOverlay.OperationType,
    tapPosition: Pair<Int, Int>,
    swipeStart: Pair<Int, Int>,
    swipeEnd: Pair<Int, Int>,
    textInputPosition: Pair<Int, Int>,
    textValue: String
) {
    // 获取屏幕密度用于坐标转换
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // 添加一个半透明背景以确保内容可见
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (operationType) {
                is UIOperationOverlay.OperationType.Tap -> TapIndicator(tapPosition.first, tapPosition.second, density)
                is UIOperationOverlay.OperationType.Swipe -> SwipeIndicator(
                    swipeStart.first, swipeStart.second,
                    swipeEnd.first, swipeEnd.second,
                    density
                )
                is UIOperationOverlay.OperationType.TextInput -> TextInputIndicator(
                    textInputPosition.first,
                    textInputPosition.second,
                    textValue,
                    density
                )
                else -> { /* 不显示任何内容 */ }
            }
        }
    }
}

/**
 * 点击指示器UI
 */
@Composable
private fun TapIndicator(
    x: Int, 
    y: Int,
    density: androidx.compose.ui.unit.Density
) {
    // 使用无限循环动画
    val infiniteTransition = rememberInfiniteTransition(label = "tap")
    
    // 脉冲缩放动画
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    
    // 淡入淡出透明度动画
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    
    // 使用原始像素坐标，不转换为dp
    Canvas(modifier = Modifier.fillMaxSize()) {
        with(density) {
            // 使用原始像素坐标
            val centerX = x.toFloat()
            val centerY = y.toFloat()
            val maxRadius = 60.dp.toPx()
            
            // 1. 绘制静态背景辐射效果
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        Color(0x33FFFFFF),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 1.5f
                ),
                radius = maxRadius * 1.5f,
                center = Offset(centerX, centerY)
            )
            
            // 2. 绘制动画外圈
            drawCircle(
                color = Color(0xFF2196F3), // 更鲜艳的蓝色
                radius = maxRadius * scale,
                center = Offset(centerX, centerY),
                style = Stroke(width = 5.dp.toPx()),
                alpha = alpha
            )
            
            // 3. 绘制静态内圈
            drawCircle(
                color = Color(0xFF1976D2),
                radius = 15.dp.toPx(),
                center = Offset(centerX, centerY),
                alpha = 0.8f
            )
            
            // 4. 绘制静态中心点
            drawCircle(
                color = Color(0xFFFF9800), // 鲜艳的橙色中心点
                radius = 6.dp.toPx(),
                center = Offset(centerX, centerY),
                alpha = 0.9f
            )
        }
    }
}

/**
 * 滑动指示器UI
 */
@Composable
private fun SwipeIndicator(
    startX: Int, 
    startY: Int, 
    endX: Int, 
    endY: Int,
    density: androidx.compose.ui.unit.Density
) {
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(startX, startY, endX, endY) {
        // 动画滑动轨迹
        progress = 0f
        while (progress < 1f) {
            progress += 0.02f
            delay(10)
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        with(density) {
            // 计算动画位置
            val currentEndX = startX + (endX - startX) * progress
            val currentEndY = startY + (endY - startY) * progress
            
            // 绘制滑动轨迹背景（增强可见性）
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(startX.toFloat(), startY.toFloat()),
                end = Offset(endX.toFloat(), endY.toFloat()),
                strokeWidth = 12.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // 绘制滑动轨迹
            drawLine(
                color = Color(0xFFFFA726).copy(alpha = 0.8f),
                start = Offset(startX.toFloat(), startY.toFloat()),
                end = Offset(currentEndX.toFloat(), currentEndY.toFloat()),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // 绘制起点圆点
            drawCircle(
                color = Color(0xFFEF6C00).copy(alpha = 0.9f),
                radius = 12.dp.toPx(),
                center = Offset(startX.toFloat(), startY.toFloat())
            )
            
            // 绘制当前位置
            drawCircle(
                color = Color(0xFFFF9800),
                radius = 16.dp.toPx(),
                center = Offset(currentEndX.toFloat(), currentEndY.toFloat())
            )
        }
    }
}

/**
 * 文本输入指示器UI
 */
@Composable
private fun TextInputIndicator(
    x: Int, 
    y: Int, 
    text: String,
    density: androidx.compose.ui.unit.Density
) {
    // 使用状态动画
    val fadeIn by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "fadeIn"
    )
    
    // 使用无限循环动画实现呼吸效果
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // 绘制气泡背景和指向箭头，使用像素坐标
    Canvas(modifier = Modifier.fillMaxSize()) {
        with(density) {
            val centerX = x.toFloat()
            val centerY = y.toFloat() - 60.dp.toPx() // 稍微上移一点
            val bubbleWidth = 220.dp.toPx()
            val bubbleHeight = 50.dp.toPx()
            val cornerRadius = 12.dp.toPx()
            
            // 1. 绘制背景阴影提升可见性
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.25f),
                topLeft = Offset(centerX - bubbleWidth/2 + 4.dp.toPx(), 
                               centerY - bubbleHeight/2 + 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(bubbleWidth, bubbleHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                alpha = fadeIn * pulseAlpha
            )
            
            // 2. 绘制气泡背景
            drawRoundRect(
                color = Color(0xEE000000), // 更不透明的黑色
                topLeft = Offset(centerX - bubbleWidth/2, centerY - bubbleHeight/2),
                size = androidx.compose.ui.geometry.Size(bubbleWidth, bubbleHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                alpha = fadeIn * pulseAlpha
            )
            
            // 3. 绘制底部箭头指向输入位置
            val arrowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX, centerY + bubbleHeight/2 + 10.dp.toPx())
                lineTo(centerX - 10.dp.toPx(), centerY + bubbleHeight/2)
                lineTo(centerX + 10.dp.toPx(), centerY + bubbleHeight/2)
                close()
            }
            
            drawPath(
                path = arrowPath,
                color = Color(0xEE000000),
                alpha = fadeIn * pulseAlpha
            )
            
            // 4. 绘制亮边框增加可见性
            drawRoundRect(
                color = Color(0x77FFFFFF),
                topLeft = Offset(centerX - bubbleWidth/2, centerY - bubbleHeight/2),
                size = androidx.compose.ui.geometry.Size(bubbleWidth, bubbleHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                style = Stroke(width = 1.5.dp.toPx()),
                alpha = fadeIn * pulseAlpha
            )
        }
    }
    
    // 单独绘制文本内容，位置精确调整
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        with(density) {
            val bubbleWidth = 220.dp.toPx()
            val targetY = y.toFloat() - 85.dp.toPx()
            
            // 使用精确的像素计算，然后转换为dp
            Text(
                text = "\"$text\"",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(
                        (x.toFloat() - bubbleWidth/2).toDp(),
                        targetY.toDp()
                    )
                    .width(bubbleWidth.toDp())
                    .alpha(fadeIn * pulseAlpha),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
} 
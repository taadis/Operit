package com.ai.assistance.operit.services.floating

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class FloatingWindowState(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)

    // Window position
    var x: Int = 0
    var y: Int = 100

    // Window size
    val windowWidth = mutableStateOf(300.dp)
    val windowHeight = mutableStateOf(400.dp)
    val windowScale = mutableStateOf(1.0f)

    // Ball mode state
    val isBallMode = mutableStateOf(false)
    val ballSize = mutableStateOf(56.dp)

    // Transition state
    var lastWindowPositionX: Int = 0
    var lastWindowPositionY: Int = 0
    var lastBallPositionX: Int = 0
    var lastBallPositionY: Int = 0
    var isTransitioning = false
    val transitionDebounceTime = 500L // 防抖时间

    init {
        restoreState()
    }

    fun saveState() {
        prefs.edit().apply {
            putInt("window_x", x)
            putInt("window_y", y)
            putFloat("window_width", windowWidth.value.value.coerceAtLeast(200f))
            putFloat("window_height", windowHeight.value.value.coerceAtLeast(250f))
            putBoolean("is_ball_mode", isBallMode.value)
            putFloat("ball_size", ballSize.value.value)
            putFloat("window_scale", windowScale.value.coerceIn(0.5f, 1.0f))
            apply()
        }
    }

    fun restoreState() {
        x = prefs.getInt("window_x", 0)
        y = prefs.getInt("window_y", 100)
        windowWidth.value = Dp(prefs.getFloat("window_width", 300f).coerceAtLeast(200f))
        windowHeight.value = Dp(prefs.getFloat("window_height", 400f).coerceAtLeast(250f))
        isBallMode.value = prefs.getBoolean("is_ball_mode", false)
        ballSize.value = Dp(prefs.getFloat("ball_size", 56f).coerceAtLeast(40f))
        windowScale.value = prefs.getFloat("window_scale", 1.0f).coerceIn(0.5f, 1.0f)
    }
} 
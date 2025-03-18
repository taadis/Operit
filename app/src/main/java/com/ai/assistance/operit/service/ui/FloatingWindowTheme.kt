package com.ai.assistance.operit.service.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme

/**
 * 为悬浮窗提供的独立主题
 * 使用静态颜色，避免对Activity上下文的依赖
 */
@Composable
fun FloatingWindowTheme(content: @Composable () -> Unit) {
    // 使用静态颜色，匹配动态主题的默认值
    val colorScheme = lightColorScheme(
        // 主要颜色
        primary = Color(0xFF6650a4),                // Purple40 - 与主应用默认主色匹配
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),       // 浅紫色容器
        onPrimaryContainer = Color(0xFF21005E),     // 深紫色文本

        // 次要颜色
        secondary = Color(0xFF625b71),              // PurpleGrey40 - 次要色
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8DEF8),     // 浅灰紫色容器
        onSecondaryContainer = Color(0xFF1E192B),   // 深灰紫色文本

        // 第三颜色
        tertiary = Color(0xFF7D5260),               // Pink40 - 第三色
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8E4),      // 浅粉色容器
        onTertiaryContainer = Color(0xFF370B1E),    // 深粉色文本

        // 错误颜色
        error = Color(0xFFB3261E),                  // 标准Material错误色
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),         // 浅红色容器
        onErrorContainer = Color(0xFF410E0B),       // 深红色文本

        // 背景和表面
        background = Color(0xFFFFFBFE),             // 几乎白色背景
        onBackground = Color(0xFF1C1B1F),           // 深色文本
        surface = Color(0xFFFFFBFE),                // 表面与背景相同
        onSurface = Color(0xFF1C1B1F),              // 表面上的文本
        surfaceVariant = Color(0xFFE7E0EC),         // 浅灰色变体表面
        onSurfaceVariant = Color(0xFF49454F),       // 变体表面上的文本

        // 轮廓
        outline = Color(0xFF79747E)                 // 标准轮廓色
    )
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
} 
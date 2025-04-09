package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.utils.TerminalColors

/**
 * 为终端提示符添加闪烁效果
 */
@Composable
fun BlinkingCursor(fontSize: Int) {
    val alpha = remember { Animatable(1f) }
    
    LaunchedEffect(Unit) {
        while(true) {
            alpha.animateTo(
                targetValue = 0.2f,
                animationSpec = tween(600, easing = LinearEasing)
            )
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = LinearEasing)
            )
        }
    }
    
    Text(
        text = "▌",
        color = TerminalColors.ParrotAccent.copy(alpha = alpha.value),
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp)
    )
} 
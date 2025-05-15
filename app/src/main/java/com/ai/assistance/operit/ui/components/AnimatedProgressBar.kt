package com.ai.assistance.operit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 超平滑进度条
 *
 * 相比标准的LinearProgressIndicator，提供了超平滑的进度过渡效果，
 * 即使进度值变化很快，也能实现平滑的插值动画效果。
 * 内部使用智能插值系统，确保进度条始终平滑过渡，不出现跳跃。
 *
 * @param progress 当前进度值 (0.0-1.0)
 * @param modifier 修饰符
 * @param height 进度条高度
 * @param trackColor 轨道颜色
 * @param progressColor 进度颜色
 * @param intermediateSteps 中间过渡步数
 * @param stepDuration 每步动画持续时间(毫秒)
 */
@Composable
fun SmoothLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    intermediateSteps: Int = 10,
    stepDuration: Int = 100
) {
    // 记住上一次的目标进度值 
    var lastTargetProgress by remember { mutableFloatStateOf(0f) }
    
    // 当前动画进度值
    val animatedProgress = remember { Animatable(0f) }
    
    // 是否是首次渲染
    var isFirstRender by remember { mutableStateOf(true) }
    
    // 记住当前目标进度和中间插值步骤
    var currentIntermediateStep by remember { mutableFloatStateOf(0f) }
    var intermediateProgressSteps by remember { mutableStateOf(generateSteps(0f, 0f, 0)) }
    
    LaunchedEffect(progress) {
        // 如果是首次渲染，直接设置到初始值
        if (isFirstRender) {
            animatedProgress.snapTo(max(0f, min(1f, progress)))
            lastTargetProgress = progress
            isFirstRender = false
            return@LaunchedEffect
        }
        
        // 计算进度变化量
        val progressDelta = progress - lastTargetProgress
        
        // 如果变化很小，直接使用简单动画
        if (abs(progressDelta) < 0.05f) {
            animatedProgress.animateTo(
                targetValue = max(0f, min(1f, progress)),
                animationSpec = tween(
                    durationMillis = stepDuration * 2,
                    easing = FastOutSlowInEasing
                )
            )
        } else {
            // 生成一系列中间插值步骤
            intermediateProgressSteps = generateSteps(lastTargetProgress, progress, intermediateSteps)
            
            // 逐步执行中间过渡动画
            for (step in intermediateProgressSteps) {
                currentIntermediateStep = step
                animatedProgress.animateTo(
                    targetValue = max(0f, min(1f, step)),
                    animationSpec = tween(
                        durationMillis = stepDuration,
                        easing = LinearEasing
                    )
                )
            }
        }
        
        // 更新上一次目标进度
        lastTargetProgress = progress
    }
    
    // 使用MaterialDesign的LinearProgressIndicator
    LinearProgressIndicator(
        progress = { animatedProgress.value },
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        trackColor = trackColor,
        color = progressColor,
        strokeCap = StrokeCap.Round
    )
}

/**
 * 生成一系列从起始进度到目标进度的平滑过渡步骤
 */
private fun generateSteps(start: Float, end: Float, steps: Int): List<Float> {
    if (steps <= 1) return listOf(end)
    
    val result = mutableListOf<Float>()
    val stepSize = (end - start) / steps
    
    // 使用缓动函数使过渡更自然
    for (i in 1..steps) {
        val t = i.toFloat() / steps
        val easedT = easeInOutCubic(t) // 使用三次方缓动函数
        val stepValue = start + (end - start) * easedT
        result.add(stepValue)
    }
    
    return result
}

/**
 * 三次方缓动函数 - 使动画加速然后减速，看起来更自然
 */
private fun easeInOutCubic(t: Float): Float {
    return if (t < 0.5f) {
        4 * t * t * t
    } else {
        1 - (-2 * t + 2).pow(3) / 2
    }
}

/**
 * 浮点数三次方
 */
private fun Float.pow(exponent: Int): Float {
    var result = 1f
    repeat(exponent) {
        result *= this
    }
    return result
} 
package com.chatwaifu.live2d

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.data.model.Live2DConfig
import com.ai.assistance.operit.data.model.Live2DModel

/**
 * Compose版Live2D视图组件
 *
 * 遵循生命周期管理原则：
 * 1. 使用 `remember` 来持有 `Live2DView` 实例，确保其在重组之间保持稳定。
 * 2. 使用 `DisposableEffect` 来管理 `Live2DView` 的生命周期，例如暂停、恢复和销毁。
 * 3. 使用 `LaunchedEffect` 来响应外部状态（如模型、配置）的变化，并更新 `Live2DView`。
 *
 * 这种方法避免了不必要的视图重建，提高了性能和用户体验。
 */
@Composable
fun Live2DViewCompose(
        modifier: Modifier = Modifier,
        modelPath: String = "",
        modelJsonFileName: String = "",
        config: Live2DConfig? = null,
        model: Live2DModel? = null,
        expressionToApply: String? = null,
        onExpressionApplied: () -> Unit = {},
        triggerRandomTap: Long? = null,
        onRandomTapHandled: () -> Unit = {},
        onViewCreated: (Live2DView) -> Unit = {},
        onError: (String) -> Unit = {},
        useZOrderOnTop: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onError)
    var creationError by remember { mutableStateOf<String?>(null) }

    val live2DView: Live2DView? = remember {
        try {
            createLive2DView(context, useZOrderOnTop)
        } catch (e: IllegalStateException) {
            creationError = e.message ?: "创建Live2DView时发生未知错误"
            null
        }
    }

    if (live2DView != null) {
        val currentConfig by rememberUpdatedState(config)

        LaunchedEffect(model, modelPath, modelJsonFileName) {
            try {
                when {
                    model != null -> {
                        val path = model.folderPath.let { if (!it.endsWith("/")) "$it/" else it }
                        live2DView.loadModel(path, model.jsonFileName)
                    }
                    modelPath.isNotEmpty() && modelJsonFileName.isNotEmpty() -> {
                        live2DView.loadModel(modelPath, modelJsonFileName)
                    }
                }
            } catch (e: Exception) {
                currentOnError(e.message ?: "加载模型时发生未知错误")
            }
        }

        LaunchedEffect(config) {
            try {
                config?.let {
                    live2DView.setModelScale(it.scale)
                    live2DView.setModelTranslateX(it.translateX)
                    live2DView.setModelTranslateY(it.translateY)
                    live2DView.setMouthForm(it.mouthForm)
                    live2DView.setMouthOpenY(it.mouthOpenY)
                    live2DView.setAutoBlinkEnabled(it.autoBlinkEnabled)
                    live2DView.setRenderBack(it.renderBack)
                }
            } catch (e: Exception) {
                currentOnError(e.message ?: "应用配置时发生未知错误")
            }
        }

        LaunchedEffect(expressionToApply) {
            if (expressionToApply != null) {
                try {
                    val expressionName = expressionToApply.substringBeforeLast(':')
                    live2DView.applyExpression(expressionName)
                } catch (e: Exception) {
                    currentOnError(e.message ?: "应用表情时发生未知错误")
                } finally {
                    onExpressionApplied()
                }
            }
        }

        LaunchedEffect(triggerRandomTap) {
            if (triggerRandomTap != null) {
                try {
                    val x = (0..live2DView.width).random().toFloat()
                    val y = (0..live2DView.height).random().toFloat()
                    live2DView.performTap(x, y)
                } catch (e: Exception) {
                    currentOnError(e.message ?: "模拟点击时发生未知错误")
                } finally {
                    onRandomTapHandled()
                }
            }
        }

        DisposableEffect(lifecycleOwner, live2DView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> live2DView.onPause()
                    Lifecycle.Event.ON_RESUME -> {
                        live2DView.onResume()
                        try {
                            currentConfig?.let {
                                live2DView.setModelScale(it.scale)
                                live2DView.setModelTranslateX(it.translateX)
                                live2DView.setModelTranslateY(it.translateY)
                                live2DView.setMouthForm(it.mouthForm)
                                live2DView.setMouthOpenY(it.mouthOpenY)
                                live2DView.setAutoBlinkEnabled(it.autoBlinkEnabled)
                                live2DView.setRenderBack(it.renderBack)
                            }
                        } catch (e: Exception) {
                            currentOnError(e.message ?: "恢复状态时发生未知错误")
                        }
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                JniBridgeJava.nativeOnStop()
                live2DView.destroy()
            }
        }

        AndroidView(
                modifier = modifier,
                factory = {
                    onViewCreated(live2DView)
                    live2DView
                }
        )
    } else if (creationError != null) {
        LaunchedEffect(creationError) { currentOnError(creationError!!) }
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = creationError!!, color = Color.Red, textAlign = TextAlign.Center)
        }
    }
}

private fun createLive2DView(context: Context, useZOrderOnTop: Boolean = false): Live2DView {
    JniBridgeJava.SetContext(context)
    return Live2DView(context, null, useZOrderOnTop)
}

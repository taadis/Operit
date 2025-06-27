package com.chatwaifu.live2d

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.data.model.Live2DConfig
import com.ai.assistance.operit.data.model.Live2DModel

/**
 * Compose版Live2D视图组件
 *
 * 遵循最简单的生命周期管理原则：
 * 1. 每次组合时，创建一个全新的Live2DView实例。
 * 2. 每次视图离开屏幕并被销毁时（onDispose），调用destroy()来彻底释放所有C++层和GL资源。
 *
 * 这确保了每次显示都是一次干净的、从零开始的渲染，从根本上避免了因状态残留和生命周期冲突导致的崩溃。
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
        onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val live2DView = remember { createLive2DView(context) }

    LaunchedEffect(model, modelPath, modelJsonFileName) {
        try {
            when {
                model != null -> {
                    var path = if (model.isBuiltIn) model.folderPath else model.folderPath
                    if (!path.endsWith("/")) {
                        path += "/"
                    }
                    live2DView.loadModel(path, model.jsonFileName)
                }
                modelPath.isNotEmpty() && modelJsonFileName.isNotEmpty() -> {
                    live2DView.loadModel(modelPath, modelJsonFileName)
                }
            }
        } catch (e: Exception) {
            onError(e.message ?: "未知错误")
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
            onError(e.message ?: "未知错误")
        }
    }

    LaunchedEffect(expressionToApply) {
        if (expressionToApply != null) {
            try {
                val expressionName = expressionToApply.substringBeforeLast(':')
                live2DView.applyExpression(expressionName)
            } catch (e: Exception) {
                onError(e.message ?: "应用表情时发生未知错误")
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
                onError(e.message ?: "模拟点击时发生未知错误")
            } finally {
                onRandomTapHandled()
            }
        }
    }

    DisposableEffect(lifecycleOwner, config) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> live2DView.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    live2DView.onResume()
                    // 重新应用配置以确保状态恢复
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
                        onError(e.message ?: "未知错误")
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

    AndroidView(modifier = modifier, factory = { live2DView.also { onViewCreated(it) } })
}

private fun createLive2DView(context: Context): Live2DView {
    JniBridgeJava.SetContext(context)
    return Live2DView(context)
}

package com.ai.assistance.dragonbones

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** DragonBones 骨骼动画视图组件 使用 C++ JNI 和 OpenGL ES 进行渲染，以获得高性能 */
class DragonBonesView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        GLSurfaceView(context, attrs) {

    private val renderer: DragonBonesRenderer

    init {
        JniBridge.init(context.assets)

        // 1. 设置EGL配置以支持Alpha通道，实现透明背景
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)

        // 2. 设置OpenGL ES 2.0上下文
        setEGLContextClientVersion(2)

        // 3. 创建并设置渲染器
        renderer = DragonBonesRenderer()
        setRenderer(renderer)

        // 4. 设置渲染模式为连续渲染，以驱动动画
        renderMode = RENDERMODE_CONTINUOUSLY

        // 5. 将视图的背景设置为透明
        setBackgroundColor(0x00000000)

        setZOrderOnTop(true)
    }

    /** GL渲染器，负责调用JNI代码执行实际的OpenGL绘制 */
    private class DragonBonesRenderer : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            JniBridge.onSurfaceCreated()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            JniBridge.onSurfaceChanged(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            JniBridge.onDrawFrame()
        }
    }

    // --- 公开的API方法，用于控制动画 ---

    fun loadModel(modelPath: String, texturePath: String) {
        queueEvent { JniBridge.loadDragonBones(modelPath, texturePath) }
    }

    override fun onPause() {
        queueEvent {
            super.onPause()
            JniBridge.onPause()
        }
    }

    override fun onResume() {
        queueEvent {
            super.onResume()
            JniBridge.onResume()
        }
    }

    fun destroy() {
        queueEvent { JniBridge.onDestroy() }
    }
}

/** DragonBones 模型配置 */
data class DragonBonesConfig(val model: String, val texture: String)

/** DragonBones 模型信息 */
data class DragonBonesModel(
        val skeletonPath: String,
        val texturePath: String,
        val textureImagePath: String,
        val armatureName: String = ""
)

@Composable
fun DragonBonesViewCompose(modifier: Modifier = Modifier, config: DragonBonesConfig) {
    val context = LocalContext.current
    val dragonBonesView = remember {
        DragonBonesView(context).apply { loadModel(config.model, config.texture) }
    }

    AndroidView(factory = { dragonBonesView }, modifier = modifier)

    DisposableEffect(Unit) { onDispose { dragonBonesView.destroy() } }
}

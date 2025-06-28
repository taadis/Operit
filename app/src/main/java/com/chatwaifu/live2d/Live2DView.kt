package com.chatwaifu.live2d

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import java.lang.ref.WeakReference

/** Live2D模型显示视图 负责处理OpenGL渲染和触摸事件 */
class Live2DView
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        private val useZOrderOnTop: Boolean = false // 添加Z顺序控制参数
) : GLSurfaceView(context, attrs) {

    private val renderer: GLRenderer

    companion object {
        // 使用WeakReference防止内存泄漏
        private var activeInstance: WeakReference<Live2DView>? = null

        fun isInstanceActive(): Boolean {
            return activeInstance?.get() != null
        }
    }

    init {
        if (isInstanceActive()) {
            throw IllegalStateException("该形象再别处已展示.直到销毁")
        }

        // 关键：确保GLSurfaceView本身是透明的
        // 1. 设置EGL配置以支持Alpha通道
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        // 2. 设置Z顺序，使其位于窗口顶层，这对于透明度至关重要
        if (useZOrderOnTop) {
            setZOrderOnTop(true)
        }

        // 3. 设置holder格式为半透明
        holder.setFormat(PixelFormat.TRANSLUCENT)

        // 4. 将视图的背景色也设置为透明
        setBackgroundColor(Color.TRANSPARENT)

        // 设置OpenGL ES 2.0上下文
        setEGLContextClientVersion(2)

        // 创建渲染器
        renderer = GLRenderer()
        setRenderer(renderer)

        // 设置渲染模式为连续渲染
        renderMode = RENDERMODE_CONTINUOUSLY

        // 设置保持屏幕常亮
        keepScreenOn = true

        // 设置保持EGL上下文
        setPreserveEGLContextOnPause(true)

        activeInstance = WeakReference(this)
    }

    /**
     * 加载模型
     * @param modelPath 模型路径
     * @param jsonFileName 模型配置文件名
     */
    fun loadModel(modelPath: String, jsonFileName: String) {
        queueEvent { JniBridgeJava.nativeProjectChangeTo(modelPath, jsonFileName) }
    }

    /**
     * 应用表情
     * @param expressionName 表情名称
     */
    fun applyExpression(expressionName: String) {
        queueEvent { JniBridgeJava.nativeApplyExpression(expressionName) }
    }

    /**
     * 设置模型缩放
     * @param scale 缩放比例
     */
    fun setModelScale(scale: Float) {
        queueEvent { JniBridgeJava.nativeProjectScale(scale) }
    }

    /**
     * 设置模型水平位移
     * @param x 水平位移量
     */
    fun setModelTranslateX(x: Float) {
        queueEvent { JniBridgeJava.nativeProjectTransformX(x) }
    }

    /**
     * 设置模型垂直位移
     * @param y 垂直位移量
     */
    fun setModelTranslateY(y: Float) {
        queueEvent { JniBridgeJava.nativeProjectTransformY(y) }
    }

    /**
     * 设置嘴部形状
     * @param value 嘴部形状值 (0.0-1.0)
     */
    fun setMouthForm(value: Float) {
        queueEvent { JniBridgeJava.nativeProjectMouthForm(value) }
    }

    /**
     * 设置嘴部开合程度
     * @param value 嘴部开合值 (0.0-1.0)
     */
    fun setMouthOpenY(value: Float) {
        queueEvent { JniBridgeJava.nativeProjectMouthOpenY(value) }
    }

    /**
     * 设置是否启用自动眨眼
     * @param enabled 是否启用
     */
    fun setAutoBlinkEnabled(enabled: Boolean) {
        queueEvent { JniBridgeJava.nativeAutoBlinkEyes(enabled) }
    }

    /**
     * 以编程方式在指定坐标执行一次点击
     * @param x 点击的X坐标
     * @param y 点击的Y坐标
     */
    fun performTap(x: Float, y: Float) {
        // 在GL线程上安全地执行触摸事件
        queueEvent {
            JniBridgeJava.nativeOnTouchesBegan(x, y)
            JniBridgeJava.nativeOnTouchesEnded(x, y)
        }
    }

    /**
     * 设置是否渲染背景
     * @param enabled 是否启用
     */
    fun setRenderBack(enabled: Boolean) {
        queueEvent { JniBridgeJava.needRenderBack(enabled) }
    }

    /** 处理触摸事件 */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                queueEvent {
                    JniBridgeJava.nativeOnTouchesBegan(x, y)
                    JniBridgeJava.nativeOnTouchesMoved(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                queueEvent { JniBridgeJava.nativeOnTouchesMoved(x, y) }
            }
            MotionEvent.ACTION_UP -> {
                queueEvent { JniBridgeJava.nativeOnTouchesEnded(x, y) }
            }
        }
        return true
    }

    /** 暂停渲染 */
    override fun onPause() {
        super.onPause()
        queueEvent { JniBridgeJava.nativeOnPause() }
    }

    /** 恢复渲染 */
    override fun onResume() {
        super.onResume()
        queueEvent { JniBridgeJava.nativeOnStart() }
    }

    /** 销毁资源 */
    fun destroy() {
        queueEvent { JniBridgeJava.nativeOnDestroy() }
        if (activeInstance?.get() == this) {
            activeInstance?.clear()
        }
    }
}

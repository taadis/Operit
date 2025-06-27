/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license that can be found at
 * https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */
package com.chatwaifu.live2d

import android.app.Activity
import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.jvm.JvmStatic

object JniBridgeJava {

    private const val LIBRARY_NAME = "chatwaifu-live2d"
    private var _activityInstance: Activity? = null
    private var _context: Context? = null
    private var _loadCallback: Live2DLoadInterface? = null
    private var _isLibraryLoaded = false

    init {
        try {
            System.loadLibrary(LIBRARY_NAME)
            _isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            _isLibraryLoaded = false
            e.printStackTrace()
        }
    }

    // 检查库是否加载成功
    @JvmStatic
    fun isLibraryLoaded(): Boolean {
        return _isLibraryLoaded
    }

    // Native -----------------------------------------------------------------

    @JvmStatic external fun nativeOnStart()

    @JvmStatic external fun nativeOnPause()

    @JvmStatic external fun nativeOnStop()

    @JvmStatic external fun nativeOnDestroy()

    @JvmStatic external fun nativeOnSurfaceCreated()

    @JvmStatic external fun nativeOnSurfaceChanged(width: Int, height: Int)

    @JvmStatic external fun nativeOnDrawFrame()

    @JvmStatic external fun nativeOnTouchesBegan(pointX: Float, pointY: Float)

    @JvmStatic external fun nativeOnTouchesEnded(pointX: Float, pointY: Float)

    @JvmStatic external fun nativeOnTouchesMoved(pointX: Float, pointY: Float)

    @JvmStatic external fun nativeProjectChangeTo(modelPath: String, modelJsonFileName: String)

    @JvmStatic external fun nativeApplyExpression(expressionName: String)

    @JvmStatic external fun needRenderBack(back: Boolean)

    @JvmStatic external fun nativeProjectScale(scale: Float)

    @JvmStatic external fun nativeProjectTransformX(transform: Float)

    @JvmStatic external fun nativeProjectTransformY(transform: Float)

    @JvmStatic external fun nativeAutoBlinkEyes(enabled: Boolean)

    @JvmStatic external fun nativeProjectMouthForm(value: Float)

    @JvmStatic external fun nativeProjectMouthOpenY(value: Float)

    // Java -----------------------------------------------------------------

    @JvmStatic
    fun SetContext(context: Context) {
        _context = context
    }

    @JvmStatic
    fun SetActivityInstance(activity: Activity) {
        _activityInstance = activity
    }

    @JvmStatic
    fun setLive2DLoadInterface(loadInterface: Live2DLoadInterface) {
        _loadCallback = loadInterface
    }

    @JvmStatic
    fun LoadFile(filePath: String): ByteArray? {
        try {
            val file: File
            if (filePath.startsWith("/")) {
                // 我们通过为模型json添加前导"/"来修复C++的拼接错误，
                // 这会导致它被视为绝对路径，所以我们需要在这里进行修正。
                val externalPath = System.getProperty("LIVE2D_BASE_RESOURCES_PATH")
                if (externalPath == null) {
                    android.util.Log.e("JniBridgeJava", "LIVE2D_BASE_RESOURCES_PATH not set, cannot load absolute path fix: $filePath")
                    return null
                }
                // 将错误的"绝对路径"修正为基于我们根目录的正确路径
                file = File(filePath)

            } else {
                // 恢复对UI资源和模型资源的路径区分处理
                val externalPath = System.getProperty("LIVE2D_BASE_RESOURCES_PATH")
                if (externalPath == null) {
                    android.util.Log.e("JniBridgeJava", "LIVE2D_BASE_RESOURCES_PATH not set, cannot load relative path: $filePath")
                    return null
                }
                
                // 关键逻辑恢复：
                // 如果路径已经是 "live2d/..." (模型资源)，直接从根目录拼接
                // 如果路径只是文件名 (UI资源)，则拼接到 "live2d" 子目录下
                val finalPath = if (filePath.startsWith("live2d")) {
                    File(externalPath, filePath).absolutePath
                } else {
                    File(externalPath, "live2d/$filePath").absolutePath
                }
                file = File(finalPath)
            }

            if (!file.exists()) {
                android.util.Log.e("JniBridgeJava", "File not found in external storage: ${file.absolutePath}")
                return null
            }
            
            android.util.Log.d("JniBridgeJava", "Loading file from external storage: ${file.absolutePath}")
            return FileInputStream(file).use { input ->
                val fileSize = input.available()
                val fileBuffer = ByteArray(fileSize)
                input.read(fileBuffer, 0, fileSize)
                fileBuffer
            }

        } catch (e: IOException) {
            android.util.Log.e("JniBridgeJava", "IOException while loading file: $filePath", e)
            return null
        }
    }

    @JvmStatic
    fun MoveTaskToBack() {
        _activityInstance?.moveTaskToBack(true)
    }

    @JvmStatic
    fun OnLoadError() {
        _loadCallback?.onLoadError()
    }

    @JvmStatic
    fun OnLoadDone() {
        _loadCallback?.onLoadDone()
    }

    @JvmStatic
    fun OnLoadOneMotion(motionGroup: String, index: Int, motionName: String) {
        _loadCallback?.onLoadOneMotion(motionGroup, index, motionName)
    }

    @JvmStatic
    fun OnLoadOneExpression(expressionName: String, index: Int) {
        _loadCallback?.onLoadOneExpression(expressionName, index)
    }

    interface Live2DLoadInterface {
        fun onLoadError()

        fun onLoadDone()

        fun onLoadOneMotion(motionGroup: String, index: Int, motionName: String)

        fun onLoadOneExpression(expressionName: String, index: Int)
    }
}

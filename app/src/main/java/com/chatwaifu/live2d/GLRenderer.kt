/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license that can be found at
 * https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */
package com.chatwaifu.live2d

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        JniBridgeJava.nativeOnSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        JniBridgeJava.nativeOnSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        JniBridgeJava.nativeOnDrawFrame()
    }
}

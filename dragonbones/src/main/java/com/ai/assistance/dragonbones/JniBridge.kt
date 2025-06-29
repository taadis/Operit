package com.ai.assistance.dragonbones

import android.content.res.AssetManager

object JniBridge {
    init {
        System.loadLibrary("dragonbones_native")
    }

    @JvmStatic external fun init(assetManager: AssetManager)

    @JvmStatic external fun loadDragonBones(modelPath: String, texturePath: String)

    @JvmStatic external fun onPause()

    @JvmStatic external fun onResume()

    @JvmStatic external fun onDestroy()

    @JvmStatic external fun onSurfaceCreated()

    @JvmStatic external fun onSurfaceChanged(width: Int, height: Int)

    @JvmStatic external fun onDrawFrame()
}

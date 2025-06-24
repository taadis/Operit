package com.whispercppdemo.whisper

import android.content.res.AssetManager
import java.io.InputStream

/**
 * JNI bindings for whisper.cpp.
 * This object is responsible for loading the native library and declaring the native methods.
 */
object WhisperLib {
    init {
        try {
            System.loadLibrary("whisper")
        } catch (e: UnsatisfiedLinkError) {
            // This is a common error when the .so file is not found.
            // Log or handle it as needed.
            e.printStackTrace()
        }
    }

    // Initialize a whisper_context from a model file specified by path
    external fun initContext(modelPath: String): Long

    // Initialize a whisper_context from an asset file
    external fun initContextFromAsset(assetManager: AssetManager, modelPath: String): Long
    
    // Initialize a whisper_context from an InputStream
    external fun initContextFromInputStream(inputStream: InputStream): Long

    // Free a whisper_context
    external fun freeContext(contextPtr: Long)

    // Run a full transcription on audio data
    external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)

    // Get the number of text segments from the last transcription
    external fun getTextSegmentCount(contextPtr: Long): Int

    // Get a text segment from the last transcription
    external fun getTextSegment(contextPtr: Long, index: Int): String

    // Get system information
    external fun getSystemInfo(): String
} 
package com.whispercppdemo.whisper

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * A high-level wrapper for the whisper.cpp library.
 * This class manages the native context and provides a safe way to interact with the library.
 */
class WhisperContext private constructor(private val contextPtr: Long) {
    /**
     * Transcribes audio data. This is a suspending function that runs on the IO dispatcher.
     * 
     * @param audioData 音频PCM数据的浮点数组
     * @param numThreads 使用的线程数，影响识别速度
     * @return 识别结果文本
     */
    suspend fun transcribe(audioData: FloatArray, numThreads: Int = 4): String {
        return withContext(Dispatchers.IO) {
            WhisperLib.fullTranscribe(contextPtr, numThreads, audioData)
            val textCount = WhisperLib.getTextSegmentCount(contextPtr)
            val transcribedText = StringBuilder()
            for (i in 0 until textCount) {
                transcribedText.append(WhisperLib.getTextSegment(contextPtr, i))
            }
            transcribedText.toString()
        }
    }

    /**
     * Releases the native context. This must be called when the context is no longer needed.
     */
    fun release() {
        if (contextPtr != 0L) {
            WhisperLib.freeContext(contextPtr)
        }
    }

    companion object {
        /**
         * Creates a new WhisperContext from a model file path.
         * This is a suspending function that runs on the IO dispatcher.
         * Returns null if context creation fails.
         */
        suspend fun createContextFromFile(filePath: String): WhisperContext? {
            return withContext(Dispatchers.IO) {
                if (!File(filePath).exists()) {
                    null
                } else {
                    val contextPtr = WhisperLib.initContext(filePath)
                    if (contextPtr == 0L) null else WhisperContext(contextPtr)
                }
            }
        }

        /**
         * Creates a new WhisperContext from an asset file.
         * This is a suspending function that runs on the IO dispatcher.
         * Returns null if context creation fails.
         */
        suspend fun createContextFromAsset(
            assetManager: AssetManager,
            assetPath: String
        ): WhisperContext? {
            return withContext(Dispatchers.IO) {
                val contextPtr = WhisperLib.initContextFromAsset(assetManager, assetPath)
                if (contextPtr == 0L) null else WhisperContext(contextPtr)
            }
        }
        
        /**
         * Creates a new WhisperContext from an InputStream.
         * This is a suspending function that runs on the IO dispatcher.
         * Returns null if context creation fails.
         */
        suspend fun createContextFromInputStream(
            inputStream: InputStream
        ): WhisperContext? {
            return withContext(Dispatchers.IO) {
                val contextPtr = WhisperLib.initContextFromInputStream(inputStream)
                if (contextPtr == 0L) null else WhisperContext(contextPtr)
            }
        }
    }
} 
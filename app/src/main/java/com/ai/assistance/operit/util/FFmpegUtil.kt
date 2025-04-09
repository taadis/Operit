package com.ai.assistance.operit.util

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.ReturnCode

/**
 * Utility class for FFmpeg operations
 */
object FFmpegUtil {
    private const val TAG = "FFmpegUtil"

    /**
     * Execute an FFmpeg command and return if it was successful
     */
    fun executeCommand(command: String): Boolean {
        try {
            Log.d(TAG, "Executing FFmpeg command: $command")
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                Log.d(TAG, "FFmpeg command executed successfully")
                return true
            } else {
                Log.e(
                    TAG,
                    "FFmpeg failed with return code: ${returnCode.value}, output: ${session.output}"
                )
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing FFmpeg command", e)
            return false
        }
    }

    /**
     * Get media information for a file
     */
    fun getMediaInfo(filePath: String): MediaInformation? {
        return try {
            val mediaInfoSession = FFprobeKit.getMediaInformation(filePath)
            mediaInfoSession.mediaInformation
        } catch (e: Exception) {
            Log.e(TAG, "Error getting media info: ${e.message}")
            null
        }
    }
} 
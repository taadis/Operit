package com.ai.assistance.operit.util

import android.util.Log
import java.io.File

/** Utility class for media (audio, video, image) conversion operations */
object MediaConversionUtil {
    private const val TAG = "MediaConversionUtil"

    /** Convert image files with quality options */
    fun convertImage(
            sourceFile: File,
            targetFile: File,
            sourceExt: String,
            targetExt: String,
            quality: String,
            extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Converting image from $sourceExt to $targetExt (quality: $quality)")

        try {
            // Determine quality value based on level
            val qualityValue =
                    when (quality.lowercase()) {
                        "low" -> "70"
                        "medium" -> "85"
                        "high" -> "95"
                        "lossless" -> "100"
                        else -> "85" // default medium
                    }

            // Determine the right FFmpeg conversion flags
            val formatSpecificOptions =
                    when (targetExt) {
                        "jpg", "jpeg" -> "-q:v $qualityValue" // JPEG quality (1-100)
                        "png" -> "-compression_level ${9 - (qualityValue.toInt() / 12)}" // PNG
                        // compression (0-9, inverted)
                        "webp" -> "-q:v $qualityValue" // WebP quality (1-100)
                        "gif" -> "-loop 0" // GIF settings (infinite loop)
                        "pdf" -> "-compress jpeg" // PDF compression method
                        "ico" -> "-vf scale=256:256" // ICO typical size
                        else -> ""
                    }

            // Add any extra parameters provided by the user
            val extraOptions = extraParams?.let { " $it " } ?: " "

            // Construct the FFmpeg command
            val command =
                    "-i ${sourceFile.absolutePath}$extraOptions$formatSpecificOptions ${targetFile.absolutePath}"

            return FFmpegUtil.executeCommand(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image", e)
            return false
        }
    }

    /** Convert audio files with quality options */
    fun convertAudio(
            sourceFile: File,
            targetFile: File,
            sourceExt: String,
            targetExt: String,
            quality: String,
            extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Converting audio from $sourceExt to $targetExt (quality: $quality)")

        try {
            // Get audio information
            val mediaInfo = FFmpegUtil.getMediaInfo(sourceFile.absolutePath)

            // Determine bitrate based on quality level
            val bitrate =
                    when (quality.lowercase()) {
                        "low" -> "96k"
                        "medium" -> "192k"
                        "high" -> "320k"
                        else -> "192k" // default medium
                    }

            // Determine codec and options based on target format
            val formatSpecificOptions =
                    when (targetExt) {
                        "mp3" -> "-c:a libmp3lame -b:a $bitrate"
                        "wav" -> "-c:a pcm_s16le" // 16-bit PCM
                        "ogg" -> "-c:a libvorbis -b:a $bitrate"
                        "m4a" -> "-c:a aac -b:a $bitrate -strict experimental"
                        "flac" -> "-c:a flac"
                        "aac" -> "-c:a aac -b:a $bitrate -strict experimental"
                        else -> "-b:a $bitrate"
                    }

            // Add any extra parameters provided by the user
            val extraOptions = extraParams?.let { " $it " } ?: " "

            // Construct the FFmpeg command
            val command =
                    "-i ${sourceFile.absolutePath}$extraOptions$formatSpecificOptions ${targetFile.absolutePath}"

            return FFmpegUtil.executeCommand(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting audio", e)
            return false
        }
    }

    /** Convert video files with quality options */
    fun convertVideo(
            sourceFile: File,
            targetFile: File,
            sourceExt: String,
            targetExt: String,
            quality: String,
            extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Converting video from $sourceExt to $targetExt (quality: $quality)")

        try {
            // Get video information
            val mediaInfo = FFmpegUtil.getMediaInfo(sourceFile.absolutePath)

            // Determine video and audio settings based on quality
            val videoSettings =
                    when (quality.lowercase()) {
                        "low" -> "-c:v libx264 -crf 28 -preset fast"
                        "medium" -> "-c:v libx264 -crf 23 -preset medium"
                        "high" -> "-c:v libx264 -crf 18 -preset slow"
                        else -> "-c:v libx264 -crf 23 -preset medium" // default medium
                    }

            // Determine format-specific settings
            val formatSpecificOptions =
                    when (targetExt) {
                        "mp4" -> "$videoSettings -c:a aac -b:a 192k -pix_fmt yuv420p"
                        "avi" -> "$videoSettings -c:a aac -b:a 192k"
                        "mov" -> "$videoSettings -c:a aac -b:a 192k"
                        "webm" -> "-c:v libvpx -crf 10 -b:v 1M -c:a libvorbis"
                        "mkv" -> "$videoSettings -c:a aac -b:a 192k"
                        "gif" -> "-vf \"fps=10,scale=320:-1:flags=lanczos\" -loop 0"
                        else -> videoSettings
                    }

            // Add any extra parameters provided by the user
            val extraOptions = extraParams?.let { " $it " } ?: " "

            // Construct the FFmpeg command
            val command =
                    "-i ${sourceFile.absolutePath}$extraOptions$formatSpecificOptions ${targetFile.absolutePath}"

            return FFmpegUtil.executeCommand(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting video", e)
            return false
        }
    }

    /** Extract audio from video file */
    fun extractAudioFromVideo(
            sourceFile: File,
            targetFile: File,
            sourceExt: String,
            targetExt: String,
            quality: String,
            extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Extracting audio from video: $sourceExt to $targetExt")

        try {
            // Determine bitrate based on quality level
            val bitrate =
                    when (quality.lowercase()) {
                        "low" -> "96k"
                        "medium" -> "192k"
                        "high" -> "320k"
                        else -> "192k" // default medium
                    }

            // Determine codec based on target format
            val audioCodec =
                    when (targetExt) {
                        "mp3" -> "libmp3lame"
                        "wav" -> "pcm_s16le"
                        "ogg" -> "libvorbis"
                        "m4a", "aac" -> "aac"
                        "flac" -> "flac"
                        else -> "copy"
                    }

            // Add any extra parameters provided by the user
            val extraOptions = extraParams?.let { " $it " } ?: " "

            // Construct the FFmpeg command
            val command =
                    "-i ${sourceFile.absolutePath}$extraOptions-vn -c:a $audioCodec -b:a $bitrate ${targetFile.absolutePath}"

            return FFmpegUtil.executeCommand(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio from video", e)
            return false
        }
    }

    /** Extract image/thumbnail from video */
    fun extractImageFromVideo(
            sourceFile: File,
            targetFile: File,
            sourceExt: String,
            targetExt: String,
            quality: String,
            extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Extracting image from video: $sourceExt to $targetExt")

        try {
            // Check if we need a specific timestamp
            val timestamp =
                    extraParams?.let {
                        if (it.contains("time=")) {
                            val timeRegex = Regex("time=([0-9:.]+)")
                            val matchResult = timeRegex.find(it)
                            matchResult?.groupValues?.get(1) ?: "00:00:01"
                        } else {
                            "00:00:01" // Default to 1 second
                        }
                    }
                            ?: "00:00:01"

            // Use FFmpeg to extract a frame
            val command =
                    "-i ${sourceFile.absolutePath} -ss $timestamp -frames:v 1 ${targetFile.absolutePath}"

            return FFmpegUtil.executeCommand(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting image from video", e)
            return false
        }
    }
}

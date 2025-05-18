package com.ai.assistance.operit.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object FileUtils {

    private const val TAG = "FileUtils"
    private const val BACKGROUND_IMAGES_DIR = "background_images"
    private const val BACKGROUND_VIDEOS_DIR = "background_videos"

    // List of common video file extensions
    private val VIDEO_EXTENSIONS = listOf("mp4", "3gp", "webm", "mkv", "avi", "mov", "flv", "wmv")

    /**
     * Check if a URI points to a video file
     * @param context The application context
     * @param uri The URI to check
     * @return true if the URI is a video file, false otherwise
     */
    fun isVideoFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: return false
        // Check if MIME type starts with "video/"
        if (mimeType.startsWith("video/")) return true

        // If MIME type doesn't tell us, check the file extension
        val extension = getFileExtension(context, uri)
        return extension != null && extension.lowercase() in VIDEO_EXTENSIONS
    }

    /**
     * Get the file extension from a URI
     * @param context The application context
     * @param uri The URI to get the extension from
     * @return The file extension or null if it couldn't be determined
     */
    fun getFileExtension(context: Context, uri: Uri): String? {
        // First try to get from content resolver
        val mimeType = context.contentResolver.getType(uri)
        return if (mimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        } else {
            // Fallback to path parsing
            val path = uri.path
            return path?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Copy a file from external storage to app's internal storage
     * @param context The application context
     * @param sourceUri The URI of the source file to copy
     * @return The URI of the copied file or null if copying failed
     */
    fun copyFileToInternalStorage(context: Context, sourceUri: Uri): Uri? {
        try {
            // Determine if this is a video file
            val isVideo = isVideoFile(context, sourceUri)

            // Choose appropriate directory
            val mediaDir =
                    File(
                            context.filesDir,
                            if (isVideo) BACKGROUND_VIDEOS_DIR else BACKGROUND_IMAGES_DIR
                    )
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }

            // Determine appropriate file extension
            val extension = getFileExtension(context, sourceUri) ?: if (isVideo) "mp4" else "jpg"

            // Create a unique filename to avoid conflicts
            val fileName = "bg_media_${UUID.randomUUID()}.$extension"
            val destFile = File(mediaDir, fileName)

            // Copy the file
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }

            // Clean up old media files (keeping the most recent one)
            cleanOldBackgroundFiles(mediaDir, destFile.name)

            // Return the URI for the internal file
            return Uri.fromFile(destFile)
        } catch (e: IOException) {
            Log.e(TAG, "Error copying file to internal storage", e)
            return null
        }
    }

    /**
     * Clean up old background media files to prevent using too much storage Keeps only the most
     * recent file
     */
    private fun cleanOldBackgroundFiles(directory: File, currentFileName: String) {
        try {
            val files = directory.listFiles()
            if (files != null && files.size > 1) {
                // Delete all files except the current one
                files.forEach { file ->
                    if (file.name != currentFileName) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old background files", e)
        }
    }

    /**
     * 检查视频文件大小是否超过限制
     * @param context 上下文
     * @param uri 视频文件URI
     * @param maxSizeMB 最大允许大小，单位MB
     * @return 如果视频大小在限制内返回true，否则返回false
     */
    fun checkVideoSize(context: Context, uri: Uri, maxSizeMB: Int = 30): Boolean {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fileSize = pfd.statSize
                val maxSizeBytes = maxSizeMB * 1024 * 1024L
                return fileSize <= maxSizeBytes
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "检查视频大小时出错", e)
        }
        // 如果无法检查大小，返回true以避免阻止用户选择
        return true
    }
}

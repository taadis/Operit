package com.ai.assistance.operit.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object FileUtils {

    private const val TAG = "FileUtils"
    private const val BACKGROUND_IMAGES_DIR = "background_images"

    /**
     * Copy a file from external storage to app's internal storage
     * @param context The application context
     * @param sourceUri The URI of the source file to copy
     * @return The URI of the copied file or null if copying failed
     */
    fun copyFileToInternalStorage(context: Context, sourceUri: Uri): Uri? {
        try {
            // Create a directory for background images if it doesn't exist
            val imagesDir = File(context.filesDir, BACKGROUND_IMAGES_DIR)
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            // Create a unique filename to avoid conflicts
            val fileName = "bg_image_${UUID.randomUUID()}.jpg"
            val destFile = File(imagesDir, fileName)

            // Copy the file
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Clean up old background images (keeping the most recent one)
            cleanOldBackgroundImages(imagesDir, destFile.name)

            // Return the URI for the internal file
            return Uri.fromFile(destFile)
        } catch (e: IOException) {
            Log.e(TAG, "Error copying file to internal storage", e)
            return null
        }
    }
    
    /**
     * Clean up old background images to prevent using too much storage
     * Keeps only the most recent file
     */
    private fun cleanOldBackgroundImages(directory: File, currentFileName: String) {
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
            Log.e(TAG, "Error cleaning old background images", e)
        }
    }
} 
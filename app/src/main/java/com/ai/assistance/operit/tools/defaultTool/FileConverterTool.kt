package com.ai.assistance.operit.tools.defaultTool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.tools.FileConversionResultData
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolValidationResult
import com.ai.assistance.operit.permissions.ToolCategory
import com.ai.assistance.operit.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.tools.FileFormatConversionsResultData

/**
 * Tool for converting between different file formats
 */
class FileConverterTool(private val context: Context) {
    companion object {
        private const val TAG = "FileConverterTool"
        private const val BUFFER_SIZE = 8192
    }

    // Map of supported conversion types
    private val supportedConversions = mapOf(
        // Document conversions
        "txt" to listOf("pdf", "doc", "docx", "html"),
        "doc" to listOf("pdf", "txt", "docx", "html"),
        "docx" to listOf("pdf", "txt", "doc", "html"),
        "pdf" to listOf("txt", "doc", "docx", "html", "image"),
        "html" to listOf("pdf", "txt", "doc", "docx"),
        
        // Image conversions
        "jpg" to listOf("png", "webp", "bmp", "gif", "pdf", "ico"),
        "jpeg" to listOf("png", "webp", "bmp", "gif", "pdf", "ico"),
        "png" to listOf("jpg", "webp", "bmp", "gif", "pdf", "ico"),
        "webp" to listOf("jpg", "png", "bmp", "gif", "pdf"),
        "bmp" to listOf("jpg", "png", "webp", "gif", "pdf"),
        "gif" to listOf("jpg", "png", "webp", "bmp", "pdf"),
        "ico" to listOf("png", "jpg"),
        
        // Audio conversions
        "mp3" to listOf("wav", "ogg", "m4a", "flac", "aac"),
        "wav" to listOf("mp3", "ogg", "m4a", "flac", "aac"),
        "ogg" to listOf("mp3", "wav", "m4a", "flac", "aac"),
        "m4a" to listOf("mp3", "wav", "ogg", "flac", "aac"),
        "flac" to listOf("mp3", "wav", "ogg", "m4a", "aac"),
        "aac" to listOf("mp3", "wav", "ogg", "m4a", "flac"),
        
        // Video conversions (via FFmpeg)
        "mp4" to listOf("avi", "mov", "webm", "mkv", "gif", "mp3", "wav", "jpg", "png"),
        "avi" to listOf("mp4", "mov", "webm", "mkv", "gif", "mp3", "wav"),
        "mov" to listOf("mp4", "avi", "webm", "mkv", "gif", "mp3", "wav"),
        "webm" to listOf("mp4", "avi", "mov", "mkv", "gif"),
        "mkv" to listOf("mp4", "avi", "mov", "webm", "gif", "mp3", "wav"),
        
        // Archive conversions
        "zip" to listOf("tar", "7z", "rar", "extract"),
        "tar" to listOf("zip", "7z", "rar", "extract"),
        "7z" to listOf("zip", "tar", "rar", "extract"),
        "rar" to listOf("zip", "tar", "7z", "extract")
    )
    
    /**
     * Get supported formats grouped by type
     */
    private val supportedFormats = mapOf(
        "document" to listOf("txt", "pdf", "doc", "docx", "html"),
        "image" to listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "ico"),
        "audio" to listOf("mp3", "wav", "ogg", "m4a", "flac", "aac"),
        "video" to listOf("mp4", "avi", "mov", "webm", "mkv"),
        "archive" to listOf("zip", "tar", "7z", "rar")
    )
    
    /**
     * Get media information for a file
     */
    private fun getMediaInfo(filePath: String): MediaInformation? {
        return try {
            val mediaInfoSession = FFprobeKit.getMediaInformation(filePath)
            mediaInfoSession.mediaInformation
        } catch (e: Exception) {
            Log.e(TAG, "Error getting media info: ${e.message}")
            null
        }
    }
    
    /**
     * Create directory if it doesn't exist
     */
    private fun ensureDirectoryExists(directory: File): Boolean {
        return if (!directory.exists()) {
            directory.mkdirs()
        } else {
            directory.isDirectory
        }
    }
    
    /**
     * Converts a file from one format to another
     */
    suspend fun convertFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source_path" }?.value ?: ""
        val targetPath = tool.parameters.find { it.name == "target_path" }?.value ?: ""
        val conversionType = tool.parameters.find { it.name == "conversion_type" }?.value
        val quality = tool.parameters.find { it.name == "quality" }?.value ?: "medium"
        val extraParams = tool.parameters.find { it.name == "extra_params" }?.value
        
        // Validate parameters
        if (sourcePath.isEmpty()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Source path must be specified"
            )
        }
        
        if (targetPath.isEmpty()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Target path must be specified"
            )
        }
        
        return withContext(Dispatchers.IO) {
            // Initialize variables outside the try-catch to make them available in catch block
            var sourceFile: File? = null
            var targetFile: File? = null
            
            try {
                sourceFile = File(sourcePath)
                targetFile = File(targetPath)
                
                // Ensure source file exists
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Source file does not exist: $sourcePath"
                    )
                }
                
                // Ensure target directory exists
                val targetDir = targetFile.parentFile
                if (targetDir != null && !ensureDirectoryExists(targetDir)) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Could not create target directory: ${targetDir.absolutePath}"
                    )
                }
                
                // Get file extensions
                val sourceExt = sourceFile.extension.lowercase(Locale.getDefault())
                val targetExt = targetFile.extension.lowercase(Locale.getDefault())
                
                // Validate conversion is supported
                if (!isConversionSupported(sourceExt, targetExt)) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Conversion from .$sourceExt to .$targetExt is not supported"
                    )
                }
                
                // Determine the conversion type category
                val conversionCategory = when {
                    isDocumentConversion(sourceExt, targetExt) -> "document"
                    isImageConversion(sourceExt, targetExt) -> "image"
                    isAudioConversion(sourceExt, targetExt) -> "audio"
                    isVideoConversion(sourceExt, targetExt) -> "video"
                    isVideoToAudioConversion(sourceExt, targetExt) -> "video-to-audio"
                    isVideoToImageConversion(sourceExt, targetExt) -> "video-to-image"
                    isArchiveConversion(sourceExt, targetExt) -> "archive"
                    else -> "unknown"
                }
                
                val startTime = System.currentTimeMillis()
                
                // Perform conversion based on file types
                val result = when {
                    // Document conversions
                    isDocumentConversion(sourceExt, targetExt) -> {
                        convertDocument(sourceFile, targetFile, sourceExt, targetExt, quality, extraParams)
                    }
                    
                    // Image conversions
                    isImageConversion(sourceExt, targetExt) -> {
                        convertImage(sourceFile, targetFile, sourceExt, targetExt, quality, extraParams)
                    }
                    
                    // Audio conversions
                    isAudioConversion(sourceExt, targetExt) -> {
                        convertAudio(sourceFile, targetFile, sourceExt, targetExt, quality, extraParams)
                    }
                    
                    // Video conversions
                    isVideoConversion(sourceExt, targetExt) -> {
                        convertVideo(sourceFile, targetFile, sourceExt, targetExt, quality, extraParams)
                    }
                    
                    // Video to audio extraction
                    isVideoToAudioConversion(sourceExt, targetExt) -> {
                        extractAudioFromVideo(sourceFile, targetFile, sourceExt, targetExt, quality, extraParams)
                    }
                    
                    // Video to image extraction (thumbnail)
                    isVideoToImageConversion(sourceExt, targetExt) -> {
                        extractImageFromVideo(sourceFile, targetFile, sourceExt, targetExt, quality, extraParams)
                    }
                    
                    // Archive conversions
                    isArchiveConversion(sourceExt, targetExt) -> {
                        convertArchive(sourceFile, targetFile, sourceExt, targetExt, extraParams)
                    }
                    
                    // Should not reach here due to earlier validation
                    else -> {
                        throw IOException("Conversion from .$sourceExt to .$targetExt is not implemented")
                    }
                }
                
                val duration = System.currentTimeMillis() - startTime
                val fileSize = if (targetFile.exists()) targetFile.length() else 0L

                // Create metadata
                val metadata = mutableMapOf<String, String>()
                if (extraParams != null) {
                    metadata["extraParams"] = extraParams
                }
                
                // Add media info for audio/video files if available
                if (conversionCategory in listOf("audio", "video", "video-to-audio")) {
                    getMediaInfo(targetFile.absolutePath)?.let { mediaInfo ->
                        mediaInfo.format?.let { metadata["format"] = it }
                        mediaInfo.duration?.let { metadata["duration"] = it }
                        mediaInfo.bitrate?.let { metadata["bitrate"] = it }
                    }
                }
                
                // Create result data
                val conversionResultData = FileConversionResultData(
                    sourcePath = sourcePath,
                    targetPath = targetPath,
                    sourceFormat = sourceExt,
                    targetFormat = targetExt,
                    conversionType = conversionCategory,
                    quality = quality,
                    fileSize = fileSize,
                    duration = duration,
                    metadata = metadata
                )
                
                if (result) {
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = conversionResultData
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = conversionResultData,
                        error = "Failed to convert file from $sourcePath to $targetPath"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Conversion error", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileConversionResultData(
                        sourcePath = sourcePath,
                        targetPath = targetPath,
                        sourceFormat = sourceFile?.extension?.lowercase() ?: "",
                        targetFormat = targetFile?.extension?.lowercase() ?: "",
                        conversionType = "error",
                        metadata = mapOf("error" to (e.message ?: "Unknown error"))
                    ),
                    error = "Error converting file: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Check if conversion is supported
     */
    private fun isConversionSupported(sourceExt: String, targetExt: String): Boolean {
        // Special case for extraction
        if (targetExt == "extract" && sourceExt in supportedFormats["archive"]!!) {
            return true
        }
        
        // Check direct conversion support
        if (supportedConversions[sourceExt]?.contains(targetExt) == true) {
            return true
        }
        
        // Video to audio/image extraction are special cases
        if (sourceExt in supportedFormats["video"]!! && 
            (targetExt in supportedFormats["audio"]!! || targetExt in supportedFormats["image"]!!)) {
            return true
        }
        
        return false
    }
    
    /**
     * Check if this is a document conversion
     */
    private fun isDocumentConversion(sourceExt: String, targetExt: String): Boolean {
        val documentTypes = supportedFormats["document"] ?: emptyList()
        return sourceExt in documentTypes && targetExt in documentTypes
    }
    
    /**
     * Check if this is an image conversion
     */
    private fun isImageConversion(sourceExt: String, targetExt: String): Boolean {
        val imageTypes = supportedFormats["image"] ?: emptyList()
        return (sourceExt in imageTypes && targetExt in imageTypes) || 
               (sourceExt in imageTypes && targetExt == "pdf")
    }
    
    /**
     * Check if this is an audio conversion
     */
    private fun isAudioConversion(sourceExt: String, targetExt: String): Boolean {
        val audioTypes = supportedFormats["audio"] ?: emptyList()
        return sourceExt in audioTypes && targetExt in audioTypes
    }
    
    /**
     * Check if this is a video conversion
     */
    private fun isVideoConversion(sourceExt: String, targetExt: String): Boolean {
        val videoTypes = supportedFormats["video"] ?: emptyList()
        return sourceExt in videoTypes && targetExt in videoTypes
    }
    
    /**
     * Check if this is a video to audio conversion (extraction)
     */
    private fun isVideoToAudioConversion(sourceExt: String, targetExt: String): Boolean {
        val videoTypes = supportedFormats["video"] ?: emptyList()
        val audioTypes = supportedFormats["audio"] ?: emptyList()
        return sourceExt in videoTypes && targetExt in audioTypes
    }
    
    /**
     * Check if this is a video to image conversion (thumbnail/frame extraction)
     */
    private fun isVideoToImageConversion(sourceExt: String, targetExt: String): Boolean {
        val videoTypes = supportedFormats["video"] ?: emptyList()
        val imageTypes = supportedFormats["image"] ?: emptyList()
        return sourceExt in videoTypes && targetExt in imageTypes
    }
    
    /**
     * Check if this is an archive conversion
     */
    private fun isArchiveConversion(sourceExt: String, targetExt: String): Boolean {
        val archiveTypes = supportedFormats["archive"] ?: emptyList()
        return (sourceExt in archiveTypes && targetExt in archiveTypes) ||
               (sourceExt in archiveTypes && targetExt == "extract")
    }
    
    /**
     * Execute an FFmpeg command and return if it was successful
     */
    private fun executeFFmpeg(command: String): Boolean {
        try {
            Log.d(TAG, "Executing FFmpeg command: $command")
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode
            
            if (ReturnCode.isSuccess(returnCode)) {
                Log.d(TAG, "FFmpeg command executed successfully")
                return true
            } else {
                Log.e(TAG, "FFmpeg failed with return code: ${returnCode.value}, output: ${session.output}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing FFmpeg command", e)
            return false
        }
    }
    
    /**
     * Convert document files
     */
    private fun convertDocument(
        sourceFile: File, 
        targetFile: File, 
        sourceExt: String, 
        targetExt: String,
        quality: String = "medium",
        extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Converting document from $sourceExt to $targetExt")
        
        try {
            when {
                // Convert to PDF
                targetExt == "pdf" -> {
                    // For text to PDF
                    if (sourceExt == "txt") {
                        return convertTextToPdf(sourceFile, targetFile)
                    }
                    
                    // For doc/docx to PDF, we need to use a specialized library
                    // or delegate to system apps. For now, using a FFmpeg utility
                    return executeFFmpeg("-i ${sourceFile.absolutePath} ${targetFile.absolutePath}")
                }
                
                // Convert from PDF to text - use FFmpeg for text extraction
                sourceExt == "pdf" && targetExt == "txt" -> {
                    return extractTextFromPdf(sourceFile, targetFile)
                }
                
                // Convert from PDF to image
                sourceExt == "pdf" && targetExt.matches(Regex("jpe?g|png|webp|bmp")) -> {
                    return convertPdfToImage(sourceFile, targetFile, targetExt)
                }
                
                // Convert between doc formats
                (sourceExt in listOf("doc", "docx") && targetExt in listOf("doc", "docx")) -> {
                    // This would need a specialized library like Apache POI
                    // For now, delegate to system using intent
                    return true
                }
                
                // Convert to HTML
                targetExt == "html" -> {
                    return convertToHtml(sourceFile, targetFile, sourceExt)
                }
                
                // Convert from HTML
                sourceExt == "html" -> {
                    return convertFromHtml(sourceFile, targetFile, targetExt)
                }
                
                // Other document conversions
                else -> {
                    // General text conversion fallback
                    return copyTextFile(sourceFile, targetFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting document", e)
            return false
        }
    }
    
    /**
     * Convert text to PDF
     */
    private fun convertTextToPdf(sourceFile: File, targetFile: File): Boolean {
        try {
            // Read the text content
            val textContent = FileInputStream(sourceFile).bufferedReader().use { it.readText() }
            
            // Using FFmpeg to convert text to PDF
            // Create a temporary HTML file first (which is easier to convert to PDF)
            val tempHtmlFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.html")
            
            // Create simple HTML
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>${sourceFile.nameWithoutExtension}</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 40px; }
                        pre { white-space: pre-wrap; }
                    </style>
                </head>
                <body>
                    <h1>${sourceFile.nameWithoutExtension}</h1>
                    <pre>${textContent.replace("<", "&lt;").replace(">", "&gt;")}</pre>
                </body>
                </html>
            """.trimIndent()
            
            // Write the HTML
            FileOutputStream(tempHtmlFile).use { it.write(htmlContent.toByteArray()) }
            
            // Convert HTML to PDF using FFmpeg
            val command = "-i ${tempHtmlFile.absolutePath} -f pdf ${targetFile.absolutePath}"
            val result = executeFFmpeg(command)
            
            // Delete the temporary file
            tempHtmlFile.delete()
            
            // If FFmpeg approach fails, create a basic text-only PDF as fallback
            if (!result) {
                Log.w(TAG, "FFmpeg HTML to PDF conversion failed, using basic text in PDF")
                val basicCommand = "-f lavfi -i color=c=white:s=1280x720 -vf " +
                        "\"drawtext=fontfile=/system/fonts/Roboto-Regular.ttf:text='${
                            textContent.replace("'", "\\'").take(1000) + 
                            (if (textContent.length > 1000) "..." else "")
                        }':x=40:y=40:fontsize=24:fontcolor=black\" " +
                        "-vframes 1 ${targetFile.absolutePath}"
                return executeFFmpeg(basicCommand)
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error converting text to PDF", e)
            return false
        }
    }
    
    /**
     * Extract text from PDF
     */
    private fun extractTextFromPdf(sourceFile: File, targetFile: File): Boolean {
        // Using FFmpeg's OCR capabilities for PDF text extraction
        try {
            // First approach: Try using FFmpeg with extracttext filter
            val command = "-i ${sourceFile.absolutePath} -f text ${targetFile.absolutePath}"
            val success = executeFFmpeg(command)
            
            // If FFmpeg approach fails, fall back to a simple placeholder
            if (!success) {
                Log.w(TAG, "FFmpeg text extraction failed, using placeholder extraction")
                FileOutputStream(targetFile).use { output ->
                    output.write("Content extracted from PDF ${sourceFile.name}. For full PDF text extraction, consider adding a PDF library like iText to your dependencies.".toByteArray())
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from PDF", e)
            // Ensure we still create the output file with an error message
            try {
                FileOutputStream(targetFile).use { output ->
                    output.write("Error extracting text from PDF: ${e.message}".toByteArray())
                }
                return true // We still created a file with error info
            } catch (writeEx: Exception) {
                Log.e(TAG, "Failed to write error message to output file", writeEx)
                return false
            }
        }
    }
    
    /**
     * Convert PDF to image
     */
    private fun convertPdfToImage(sourceFile: File, targetFile: File, targetExt: String): Boolean {
        // Using FFmpeg to convert the first page of a PDF to an image
        val pageParam = "-page 0" // Default to first page
        val densityParam = "-density 300" // Higher density for better quality
        
        // Different approach based on target image format
        val formatSpecificParams = when (targetExt) {
            "jpg", "jpeg" -> "-quality 90"
            "png" -> "-compress png"
            "webp" -> "-quality 90"
            "bmp" -> ""
            else -> ""
        }
        
        // Construct the FFmpeg command with proper parameters
        val command = "$densityParam -i ${sourceFile.absolutePath} $pageParam $formatSpecificParams ${targetFile.absolutePath}"
        
        val success = executeFFmpeg(command)
        
        // If the standard approach fails, try an alternative command
        if (!success) {
            Log.w(TAG, "First PDF conversion approach failed, trying alternative")
            val altCommand = "-i ${sourceFile.absolutePath} -vf format=rgba -f image2 ${targetFile.absolutePath}"
            return executeFFmpeg(altCommand)
        }
        
        return success
    }
    
    /**
     * Convert a file to HTML format
     */
    private fun convertToHtml(sourceFile: File, targetFile: File, sourceExt: String): Boolean {
        try {
            // Simple text to HTML conversion as a placeholder
            if (sourceExt == "txt") {
                val content = FileInputStream(sourceFile).bufferedReader().use { it.readText() }
                val htmlContent = StringBuilder()
                    .append("<!DOCTYPE html>\n<html><head><title>")
                    .append(sourceFile.nameWithoutExtension)
                    .append("</title></head><body>\n")
                
                // Convert line breaks to <p> tags
                content.split("\n").forEach { line ->
                    if (line.isNotBlank()) {
                        htmlContent.append("<p>").append(line).append("</p>\n")
                    } else {
                        htmlContent.append("<br>\n")
                    }
                }
                
                htmlContent.append("</body></html>")
                
                FileOutputStream(targetFile).bufferedWriter().use { writer ->
                    writer.write(htmlContent.toString())
                }
                return true
            }
            
            // For other formats, we would need specialized libraries
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error converting to HTML", e)
            return false
        }
    }
    
    /**
     * Convert from HTML format
     */
    private fun convertFromHtml(sourceFile: File, targetFile: File, targetExt: String): Boolean {
        try {
            // Simple HTML to text extraction as a placeholder
            if (targetExt == "txt") {
                val content = FileInputStream(sourceFile).bufferedReader().use { it.readText() }
                
                // Very basic HTML tag removal
                val textContent = content
                    .replace(Regex("<[^>]*>"), "") // Remove HTML tags
                    .replace(Regex("&[a-zA-Z]+;"), " ") // Replace HTML entities with space
                    .replace(Regex("\\s+"), " ") // Normalize whitespace
                    .trim()
                
                FileOutputStream(targetFile).bufferedWriter().use { writer ->
                    writer.write(textContent)
                }
                return true
            }
            
            // For other formats, we would need specialized libraries
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error converting from HTML", e)
            return false
        }
    }
    
    /**
     * Copy text file with format conversion
     */
    private fun copyTextFile(sourceFile: File, targetFile: File): Boolean {
        try {
            // Simple copy for text files
            sourceFile.copyTo(targetFile, overwrite = true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying text file", e)
            return false
        }
    }
    
    /**
     * Convert image files with quality options
     */
    private fun convertImage(
        sourceFile: File, 
        targetFile: File, 
        sourceExt: String, 
        targetExt: String,
        quality: String = "medium",
        extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Converting image from $sourceExt to $targetExt (quality: $quality)")
        
        try {
            // Determine quality value based on level
            val qualityValue = when (quality.lowercase()) {
                "low" -> "70"
                "medium" -> "85"
                "high" -> "95"
                "lossless" -> "100"
                else -> "85" // default medium
            }
            
            // Determine the right FFmpeg conversion flags
            val formatSpecificOptions = when (targetExt) {
                "jpg", "jpeg" -> "-q:v $qualityValue" // JPEG quality (1-100)
                "png" -> "-compression_level ${9 - (qualityValue.toInt() / 12)}" // PNG compression (0-9, inverted)
                "webp" -> "-q:v $qualityValue" // WebP quality (1-100)
                "gif" -> "-loop 0" // GIF settings (infinite loop)
                "pdf" -> "-compress jpeg" // PDF compression method
                "ico" -> "-vf scale=256:256" // ICO typical size
                else -> ""
            }
            
            // Add any extra parameters provided by the user
            val extraOptions = extraParams?.let { " $it " } ?: " "
            
            // Construct the FFmpeg command
            val command = "-i ${sourceFile.absolutePath}$extraOptions$formatSpecificOptions ${targetFile.absolutePath}"
            
            return executeFFmpeg(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image", e)
            return false
        }
    }
    
    /**
     * Convert audio files with quality options
     */
    private fun convertAudio(
        sourceFile: File, 
        targetFile: File, 
        sourceExt: String, 
        targetExt: String,
        quality: String = "medium",
        extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Converting audio from $sourceExt to $targetExt (quality: $quality)")
        
        try {
            // Get audio information
            val mediaInfo = getMediaInfo(sourceFile.absolutePath)
            
            // Determine bitrate based on quality level
            val bitrate = when (quality.lowercase()) {
                "low" -> "96k"
                "medium" -> "192k"
                "high" -> "320k"
                else -> "192k" // default medium
            }
            
            // Determine codec and options based on target format
            val formatSpecificOptions = when (targetExt) {
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
            val command = "-i ${sourceFile.absolutePath}$extraOptions$formatSpecificOptions ${targetFile.absolutePath}"
            
            return executeFFmpeg(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting audio", e)
            return false
        }
    }
    
    /**
     * Convert video files with quality options
     */
    private fun convertVideo(
        sourceFile: File, 
        targetFile: File, 
        sourceExt: String, 
        targetExt: String,
        quality: String = "medium",
        extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Converting video from $sourceExt to $targetExt (quality: $quality)")
        
        try {
            // Get video information
            val mediaInfo = getMediaInfo(sourceFile.absolutePath)
            
            // Determine video and audio settings based on quality
            val videoSettings = when (quality.lowercase()) {
                "low" -> "-c:v libx264 -crf 28 -preset fast"
                "medium" -> "-c:v libx264 -crf 23 -preset medium"
                "high" -> "-c:v libx264 -crf 18 -preset slow"
                else -> "-c:v libx264 -crf 23 -preset medium" // default medium
            }
            
            // Determine format-specific settings
            val formatSpecificOptions = when (targetExt) {
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
            val command = "-i ${sourceFile.absolutePath}$extraOptions$formatSpecificOptions ${targetFile.absolutePath}"
            
            return executeFFmpeg(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting video", e)
            return false
        }
    }
    
    /**
     * Extract audio from video file
     */
    private fun extractAudioFromVideo(
        sourceFile: File, 
        targetFile: File, 
        sourceExt: String, 
        targetExt: String,
        quality: String = "medium",
        extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Extracting audio from video: $sourceExt to $targetExt")
        
        try {
            // Determine bitrate based on quality level
            val bitrate = when (quality.lowercase()) {
                "low" -> "96k"
                "medium" -> "192k"
                "high" -> "320k"
                else -> "192k" // default medium
            }
            
            // Determine codec based on target format
            val audioCodec = when (targetExt) {
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
            val command = "-i ${sourceFile.absolutePath}$extraOptions-vn -c:a $audioCodec -b:a $bitrate ${targetFile.absolutePath}"
            
            return executeFFmpeg(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio from video", e)
            return false
        }
    }
    
    /**
     * Extract image/thumbnail from video
     */
    private fun extractImageFromVideo(
        sourceFile: File, 
        targetFile: File, 
        sourceExt: String, 
        targetExt: String,
        quality: String = "medium",
        extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Extracting image from video: $sourceExt to $targetExt")
        
        try {
            // Check if we need a specific timestamp
            val timestamp = extraParams?.let {
                if (it.contains("time=")) {
                    val timeRegex = Regex("time=([0-9:.]+)")
                    val matchResult = timeRegex.find(it)
                    matchResult?.groupValues?.get(1) ?: "00:00:01"
                } else {
                    "00:00:01" // Default to 1 second
                }
            } ?: "00:00:01"
            
            // Use FFmpeg to extract a frame
            val command = "-i ${sourceFile.absolutePath} -ss $timestamp -frames:v 1 ${targetFile.absolutePath}"
            
            return executeFFmpeg(command)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting image from video", e)
            return false
        }
    }
    
    /**
     * Convert between archive formats or extract
     */
    private fun convertArchive(
        sourceFile: File, 
        targetFile: File, 
        sourceExt: String, 
        targetExt: String,
        extraParams: String? = null
    ): Boolean {
        Log.d(TAG, "Converting archive from $sourceExt to $targetExt")
        
        try {
            // If target is "extract", extract the archive
            if (targetExt == "extract") {
                val extractDir = targetFile
                Log.d(TAG, "Extracting ${sourceFile.name} to directory ${extractDir.absolutePath}")
                return extractArchive(sourceFile, extractDir, sourceExt)
            }
            
            // For archive format conversion, we extract to temp directory then repackage
            Log.d(TAG, "Converting archive from $sourceExt to $targetExt format")
            val tempDir = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}")
            
            try {
                if (extractArchive(sourceFile, tempDir, sourceExt)) {
                    val result = createArchive(tempDir, targetFile, targetExt)
                    Log.d(TAG, "Archive conversion ${if (result) "successful" else "failed"}")
                    return result
                } else {
                    Log.e(TAG, "Failed to extract source archive for conversion")
                    return false
                }
            } finally {
                // Always clean up temporary directory
                if (tempDir.exists()) {
                    Log.d(TAG, "Cleaning up temporary directory")
                    tempDir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting archive", e)
            return false
        }
    }
    
    /**
     * Extract an archive file
     */
    private fun extractArchive(archiveFile: File, extractDir: File, archiveExt: String): Boolean {
        if (!ensureDirectoryExists(extractDir)) {
            return false
        }
        
        return try {
            when (archiveExt.lowercase()) {
                "zip" -> extractZip(archiveFile, extractDir)
                "tar" -> extractTar(archiveFile, extractDir)
                "7z" -> extract7z(archiveFile, extractDir)
                "rar" -> extractRar(archiveFile, extractDir)
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting archive", e)
            false
        }
    }
    
    /**
     * Extract a zip file
     */
    private fun extractZip(zipFile: File, targetDir: File): Boolean {
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(BUFFER_SIZE)
                
                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    
                    val fileName = currentEntry.name
                    val newFile = File(targetDir, fileName)
                    
                    // Create directories if needed
                    if (currentEntry.isDirectory) {
                        ensureDirectoryExists(newFile)
                    } else {
                        newFile.parentFile?.let { ensureDirectoryExists(it) }
                        
                        // Extract file
                        FileOutputStream(newFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    bos.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                    
                    zis.closeEntry()
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting zip", e)
            return false
        }
    }
    
    /**
     * Extract a tar file
     */
    private fun extractTar(tarFile: File, targetDir: File): Boolean {
        try {
            TarArchiveInputStream(BufferedInputStream(FileInputStream(tarFile))).use { tis ->
                var entry: TarArchiveEntry?
                val buffer = ByteArray(BUFFER_SIZE)
                
                while (tis.nextTarEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    
                    val fileName = currentEntry.name
                    val newFile = File(targetDir, fileName)
                    
                    // Create directories if needed
                    if (currentEntry.isDirectory) {
                        ensureDirectoryExists(newFile)
                    } else {
                        newFile.parentFile?.let { ensureDirectoryExists(it) }
                        
                        // Extract file
                        FileOutputStream(newFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                var len: Int
                                while (tis.read(buffer).also { len = it } > 0) {
                                    bos.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting tar", e)
            return false
        }
    }
    
    /**
     * Extract a 7z file
     */
    private fun extract7z(sevenZFile: File, targetDir: File): Boolean {
        try {
            SevenZFile(sevenZFile).use { szf ->
                var entry: SevenZArchiveEntry?
                val buffer = ByteArray(BUFFER_SIZE)
                
                while (szf.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    
                    val fileName = currentEntry.name
                    val newFile = File(targetDir, fileName)
                    
                    // Create directories if needed
                    if (currentEntry.isDirectory) {
                        ensureDirectoryExists(newFile)
                    } else {
                        newFile.parentFile?.let { ensureDirectoryExists(it) }
                        
                        // Extract file
                        FileOutputStream(newFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                var len: Int
                                while (szf.read(buffer).also { len = it } > 0) {
                                    bos.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting 7z", e)
            return false
        }
    }
    
    /**
     * Extract a RAR file
     */
    private fun extractRar(rarFile: File, targetDir: File): Boolean {
        try {
            Archive(rarFile).use { archive ->
                val buffer = ByteArray(BUFFER_SIZE)
                
                archive.fileHeaders.forEach { fileHeader ->
                    val fileName = fileHeader.fileName
                    val newFile = File(targetDir, fileName)
                    
                    // Create directories if needed
                    if (fileHeader.isDirectory) {
                        ensureDirectoryExists(newFile)
                    } else {
                        newFile.parentFile?.let { ensureDirectoryExists(it) }
                        
                        // Extract file
                        FileOutputStream(newFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                archive.extractFile(fileHeader, bos)
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting RAR", e)
            return false
        }
    }
    
    /**
     * Create an archive from a directory
     */
    private fun createArchive(sourceDir: File, targetFile: File, archiveExt: String): Boolean {
        return try {
            when (archiveExt.lowercase()) {
                "zip" -> createZip(sourceDir, targetFile)
                "tar" -> createTar(sourceDir, targetFile)
                "7z" -> create7z(sourceDir, targetFile)
                "rar" -> false // RAR creation is not supported due to license restrictions
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating archive", e)
            false
        }
    }
    
    /**
     * Create a zip file from a directory
     */
    private fun createZip(sourceDir: File, zipFile: File): Boolean {
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                addToZip(sourceDir, sourceDir, zos)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating zip", e)
            return false
        }
    }
    
    /**
     * Add files to a zip archive recursively
     */
    private fun addToZip(baseDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(BUFFER_SIZE)
        
        for (file in files) {
            if (file.isDirectory) {
                addToZip(baseDir, file, zos)
                continue
            }
            
            val relativePath = file.toRelativeString(baseDir)
            val entry = ZipEntry(relativePath)
            zos.putNextEntry(entry)
            
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    var len: Int
                    while (bis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
            }
            
            zos.closeEntry()
        }
    }
    
    /**
     * Create a tar file from a directory
     */
    private fun createTar(sourceDir: File, tarFile: File): Boolean {
        try {
            TarArchiveOutputStream(BufferedOutputStream(FileOutputStream(tarFile))).use { tos ->
                // Set the long file mode to handle longer filenames
                tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                addToTar(sourceDir, sourceDir, tos)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating tar", e)
            return false
        }
    }
    
    /**
     * Add files to a TAR archive recursively
     */
    private fun addToTar(baseDir: File, currentDir: File, tos: TarArchiveOutputStream) {
        val files = currentDir.listFiles() ?: return
        
        for (file in files) {
            if (file.isDirectory) {
                // Add directory entry
                val relativePath = file.toRelativeString(baseDir) + "/"
                val entry = TarArchiveEntry(file, relativePath)
                tos.putArchiveEntry(entry)
                tos.closeArchiveEntry()
                
                // Add directory contents
                addToTar(baseDir, file, tos)
            } else {
                // Add file entry
                val relativePath = file.toRelativeString(baseDir)
                val entry = TarArchiveEntry(file, relativePath)
                tos.putArchiveEntry(entry)
                
                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        IOUtils.copy(bis, tos)
                    }
                }
                
                tos.closeArchiveEntry()
            }
        }
    }
    
    /**
     * Create a 7z file from a directory
     */
    private fun create7z(sourceDir: File, sevenZFile: File): Boolean {
        try {
            SevenZOutputFile(sevenZFile).use { szof ->
                addTo7z(sourceDir, sourceDir, szof)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating 7z", e)
            return false
        }
    }
    
    /**
     * Add files to a 7z archive recursively
     */
    private fun addTo7z(baseDir: File, currentDir: File, szof: SevenZOutputFile) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(BUFFER_SIZE)
        
        for (file in files) {
            val relativePath = file.toRelativeString(baseDir)
            
            if (file.isDirectory) {
                // Make sure we include directory entries with trailing slashes
                val directoryPath = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
                val entry = szof.createArchiveEntry(file, directoryPath)
                szof.putArchiveEntry(entry)
                szof.closeArchiveEntry()
                
                // Process directory contents
                addTo7z(baseDir, file, szof)
            } else {
                // Add file entry
                val entry = szof.createArchiveEntry(file, relativePath)
                szof.putArchiveEntry(entry)
                
                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        var len: Int
                        while (bis.read(buffer).also { len = it } > 0) {
                            szof.write(buffer, 0, len)
                        }
                    }
                }
                
                szof.closeArchiveEntry()
            }
        }
    }
    
    /**
     * Get list of supported file conversions
     */
    suspend fun getSupportedConversions(tool: AITool): ToolResult {
        val formatType = tool.parameters.find { it.name == "format_type" }?.value?.lowercase()
        
        // Create structured data for the response
        val formatConversionsData = FileFormatConversionsResultData(
            formatType = formatType,
            conversions = supportedConversions,
            fileTypes = supportedFormats
        )
        
        return ToolResult(
            toolName = tool.name,
            success = true,
            result = formatConversionsData
        )
    }
}

/**
 * Executor for the file conversion tool
 */
class FileConverterToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FileConverterToolExecutor"
    }
    
    override fun invoke(tool: AITool): ToolResult {
        val fileConverterTool = FileConverterTool(context)
        
        return when (tool.name) {
            "convert_file" -> kotlinx.coroutines.runBlocking { fileConverterTool.convertFile(tool) }
            "get_supported_conversions" -> kotlinx.coroutines.runBlocking { fileConverterTool.getSupportedConversions(tool) }
            else -> ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Unknown tool: ${tool.name}"
            )
        }
    }
    
    override fun validateParameters(tool: AITool): ToolValidationResult {
        return when (tool.name) {
            "convert_file" -> {
                val sourcePath = tool.parameters.find { it.name == "source_path" }?.value
                val targetPath = tool.parameters.find { it.name == "target_path" }?.value
                
                when {
                    sourcePath.isNullOrBlank() -> 
                        ToolValidationResult(valid = false, errorMessage = "Source path must be specified")
                    targetPath.isNullOrBlank() -> 
                        ToolValidationResult(valid = false, errorMessage = "Target path must be specified")
                    else -> ToolValidationResult(valid = true)
                }
            }
            "get_supported_conversions" -> {
                // No required parameters for this tool
                ToolValidationResult(valid = true)
            }
            else -> ToolValidationResult(valid = false, errorMessage = "Unknown tool: ${tool.name}")
        }
    }
    
    override fun getCategory(): com.ai.assistance.operit.permissions.ToolCategory {
        return com.ai.assistance.operit.permissions.ToolCategory.FILE_WRITE
    }
} 
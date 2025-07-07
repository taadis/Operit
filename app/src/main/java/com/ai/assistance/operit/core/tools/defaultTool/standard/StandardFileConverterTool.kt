package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.FileConversionResultData
import com.ai.assistance.operit.core.tools.FileFormatConversionsResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.ui.permissions.ToolCategory
import com.ai.assistance.operit.util.ArchiveUtil
import com.ai.assistance.operit.util.DocumentConversionUtil
import com.ai.assistance.operit.util.FFmpegUtil
import com.ai.assistance.operit.util.FileFormatUtil
import com.ai.assistance.operit.util.MediaConversionUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument

/** Tool for converting between different file formats */
class StandardFileConverterTool(private val context: Context) {
        companion object {
                private const val TAG = "FileConverterTool"
        }

        /** Converts a file from one format to another */
        suspend fun convertFile(tool: AITool): ToolResult {
                val sourcePath = tool.parameters.find { it.name == "source_path" }?.value ?: ""
                val targetPath = tool.parameters.find { it.name == "target_path" }?.value ?: ""
                val quality = tool.parameters.find { it.name == "quality" }?.value ?: "medium"
                val extraParams = tool.parameters.find { it.name == "extra_params" }?.value
                val password = tool.parameters.find { it.name == "password" }?.value

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
                        // Initialize variables outside the try-catch to make them available in
                        // catch block
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
                                if (targetDir != null &&
                                                !ArchiveUtil.ensureDirectoryExists(targetDir)
                                ) {
                                        return@withContext ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error =
                                                        "Could not create target directory: ${targetDir.absolutePath}"
                                        )
                                }

                                // Get file extensions
                                val sourceExt = sourceFile.extension.lowercase(Locale.getDefault())
                                val targetExt = targetFile.extension.lowercase(Locale.getDefault())

                                // Validate conversion is supported
                                if (!FileFormatUtil.isConversionSupported(sourceExt, targetExt)) {
                                        return@withContext ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error =
                                                        "Conversion from .$sourceExt to .$targetExt is not supported"
                                        )
                                }

                                // Determine the conversion type category
                                val conversionCategory =
                                        FileFormatUtil.getConversionType(sourceExt, targetExt)

                                val startTime = System.currentTimeMillis()

                                // Perform conversion based on file types
                                val result =
                                        when (conversionCategory) {
                                                // Document conversions
                                                "document" -> {
                                                        convertDocument(
                                                                sourceFile,
                                                                targetFile,
                                                                sourceExt,
                                                                targetExt,
                                                                quality,
                                                                extraParams
                                                        )
                                                }

                                                // Image conversions
                                                "image" -> {
                                                        MediaConversionUtil.convertImage(
                                                                sourceFile,
                                                                targetFile,
                                                                sourceExt,
                                                                targetExt,
                                                                quality,
                                                                extraParams
                                                        )
                                                }

                                                // Audio conversions
                                                "audio" -> {
                                                        MediaConversionUtil.convertAudio(
                                                                sourceFile,
                                                                targetFile,
                                                                sourceExt,
                                                                targetExt,
                                                                quality,
                                                                extraParams
                                                        )
                                                }

                                                // Video conversions
                                                "video" -> {
                                                        MediaConversionUtil.convertVideo(
                                                                sourceFile,
                                                                targetFile,
                                                                sourceExt,
                                                                targetExt,
                                                                quality,
                                                                extraParams
                                                        )
                                                }

                                                // Video to audio extraction
                                                "video-to-audio" -> {
                                                        MediaConversionUtil.extractAudioFromVideo(
                                                                sourceFile,
                                                                targetFile,
                                                                sourceExt,
                                                                targetExt,
                                                                quality,
                                                                extraParams
                                                        )
                                                }

                                                // Video to image extraction (thumbnail)
                                                "video-to-image" -> {
                                                        MediaConversionUtil.extractImageFromVideo(
                                                                sourceFile,
                                                                targetFile,
                                                                sourceExt,
                                                                targetExt,
                                                                quality,
                                                                extraParams
                                                        )
                                                }

                                                // Archive conversions
                                                "archive" -> {
                                                        ArchiveUtil.convertArchive(
                                                                context,
                                                                sourceFile,
                                                                targetFile,
                                                                sourceExt,
                                                                targetExt,
                                                                extraParams,
                                                                password
                                                        )
                                                }

                                                // Should not reach here due to earlier validation
                                                else -> {
                                                        throw IOException(
                                                                "Conversion from .$sourceExt to .$targetExt is not implemented"
                                                        )
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
                                if (conversionCategory in listOf("audio", "video", "video-to-audio")
                                ) {
                                        FFmpegUtil.getMediaInfo(targetFile.absolutePath)?.let {
                                                mediaInfo ->
                                                mediaInfo.format?.let { metadata["format"] = it }
                                                mediaInfo.duration?.let {
                                                        metadata["duration"] = it
                                                }
                                                mediaInfo.bitrate?.let { metadata["bitrate"] = it }
                                        }
                                }

                                // Create result data
                                val conversionResultData =
                                        FileConversionResultData(
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
                                                error =
                                                        "Failed to convert file from $sourcePath to $targetPath"
                                        )
                                }
                        } catch (e: IOException) {
                                // Check specifically for encrypted archive error
                                if (e.message?.contains("encrypted", ignoreCase = true) == true ||
                                                e.message?.contains(
                                                        "password-protected",
                                                        ignoreCase = true
                                                ) == true
                                ) {
                                        val errorMsg =
                                                if (password == null) {
                                                        "The archive appears to be password-protected. Please provide a password to extract this archive."
                                                } else {
                                                        "The archive appears to be password-protected, but the provided password was incorrect or the encryption format is not supported."
                                                }

                                        Log.e(TAG, "Encrypted archive error", e)
                                        return@withContext ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result =
                                                        FileConversionResultData(
                                                                sourcePath = sourcePath,
                                                                targetPath = targetPath,
                                                                sourceFormat =
                                                                        sourceFile?.extension
                                                                                ?.lowercase()
                                                                                ?: "",
                                                                targetFormat =
                                                                        targetFile?.extension
                                                                                ?.lowercase()
                                                                                ?: "",
                                                                conversionType = "error",
                                                                metadata =
                                                                        mapOf(
                                                                                "error" to
                                                                                        "Encrypted Archive",
                                                                                "passwordProvided" to
                                                                                        (password !=
                                                                                                        null)
                                                                                                .toString()
                                                                        )
                                                        ),
                                                error = errorMsg
                                        )
                                } else {
                                        // Handle other IO exceptions
                                        Log.e(TAG, "IO error during conversion", e)
                                        return@withContext ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result =
                                                        FileConversionResultData(
                                                                sourcePath = sourcePath,
                                                                targetPath = targetPath,
                                                                sourceFormat =
                                                                        sourceFile?.extension
                                                                                ?.lowercase()
                                                                                ?: "",
                                                                targetFormat =
                                                                        targetFile?.extension
                                                                                ?.lowercase()
                                                                                ?: "",
                                                                conversionType = "error",
                                                                metadata =
                                                                        mapOf(
                                                                                "error" to
                                                                                        (e.message
                                                                                                ?: "Unknown IO error")
                                                                        )
                                                        ),
                                                error = "Error converting file: ${e.message}"
                                        )
                                }
                        } catch (e: Exception) {
                                Log.e(TAG, "Conversion error", e)
                                ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileConversionResultData(
                                                        sourcePath = sourcePath,
                                                        targetPath = targetPath,
                                                        sourceFormat =
                                                                sourceFile?.extension?.lowercase()
                                                                        ?: "",
                                                        targetFormat =
                                                                targetFile?.extension?.lowercase()
                                                                        ?: "",
                                                        conversionType = "error",
                                                        metadata =
                                                                mapOf(
                                                                        "error" to
                                                                                (e.message
                                                                                        ?: "Unknown error")
                                                                )
                                                ),
                                        error = "Error converting file: ${e.message}"
                                )
                        }
                }
        }

        /** Handle document format conversions */
        private fun convertDocument(
                sourceFile: File,
                targetFile: File,
                sourceExt: String,
                targetExt: String,
                quality: String,
                extraParams: String? = null
        ): Boolean {
                Log.d(TAG, "Converting document from .$sourceExt to .$targetExt")

                return try {
                        when (sourceExt to targetExt) {
                                // PDF Conversions
                                "pdf" to "txt" -> DocumentConversionUtil.extractTextFromPdf(context, sourceFile, targetFile)
                                "pdf" to "docx" -> DocumentConversionUtil.convertPdfToDocx(context, sourceFile, targetFile)
                                "pdf" to "html" -> DocumentConversionUtil.convertToHtml(context, sourceFile, targetFile, sourceExt)
                                "pdf" to "jpg", "pdf" to "jpeg", "pdf" to "png", "pdf" to "webp" ->
                                        DocumentConversionUtil.convertPdfToImage(sourceFile, targetFile, targetExt)

                                // Word (DOC/DOCX) Conversions
                                "doc" to "docx", "docx" to "doc" ->
                                        DocumentConversionUtil.convertBetweenDocFormats(context, sourceFile, targetFile, sourceExt, targetExt)
                                "doc" to "txt", "docx" to "txt" ->
                                        DocumentConversionUtil.extractTextFromWord(sourceFile, targetFile, sourceExt)
                                "doc" to "pdf", "docx" to "pdf" ->
                                        DocumentConversionUtil.convertWordToPdf(context, sourceFile, targetFile, sourceExt)
                                "doc" to "html", "docx" to "html" ->
                                        DocumentConversionUtil.convertToHtml(context, sourceFile, targetFile, sourceExt)

                                // Text Conversions
                                "txt" to "pdf" -> DocumentConversionUtil.convertTextToPdf(context, sourceFile, targetFile)
                                "txt" to "html" -> DocumentConversionUtil.convertToHtml(context, sourceFile, targetFile, sourceExt)
                                "txt" to "docx" -> {
                                    val content = sourceFile.readText()
                                    XWPFDocument().use { docx ->
                                        val para = docx.createParagraph()
                                        para.createRun().setText(content)
                                        FileOutputStream(targetFile).use { fos ->
                                            docx.write(fos)
                                        }
                                    }
                                    true
                                }
                                
                                // HTML Conversions
                                "html" to "txt", "html" to "pdf", "html" to "docx", "html" to "doc" ->
                                        DocumentConversionUtil.convertFromHtml(context, sourceFile, targetFile, targetExt)

                                // Simple Copy for compatible text-based formats
                                else -> if (FileFormatUtil.isTextBased(sourceExt) && FileFormatUtil.isTextBased(targetExt)) {
                                    DocumentConversionUtil.copyTextFile(sourceFile, targetFile)
                                } else {
                                    Log.e(TAG, "Unsupported document conversion from .$sourceExt to .$targetExt")
                                    false
                                }
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error converting document", e)
                        false
                }
        }

        /** Get list of supported file conversions */
        suspend fun getSupportedConversions(tool: AITool): ToolResult {
                val formatType =
                        tool.parameters.find { it.name == "format_type" }?.value?.lowercase()

                // Create structured data for the response
                val formatConversionsData =
                        FileFormatConversionsResultData(
                                formatType = formatType,
                                conversions = FileFormatUtil.supportedConversions,
                                fileTypes = FileFormatUtil.supportedFormats
                        )

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = formatConversionsData
                )
        }
}

/** Executor for the file conversion tool */
class FileConverterToolExecutor(private val context: Context) : ToolExecutor {
        companion object {
                private const val TAG = "FileConverterToolExecutor"
        }

        override fun invoke(tool: AITool): ToolResult {
                val fileConverterTool = StandardFileConverterTool(context)

                return when (tool.name) {
                        "convert_file" ->
                                kotlinx.coroutines.runBlocking {
                                        fileConverterTool.convertFile(tool)
                                }
                        "get_supported_conversions" ->
                                kotlinx.coroutines.runBlocking {
                                        fileConverterTool.getSupportedConversions(tool)
                                }
                        else ->
                                ToolResult(
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
                                val sourcePath =
                                        tool.parameters.find { it.name == "source_path" }?.value
                                val targetPath =
                                        tool.parameters.find { it.name == "target_path" }?.value

                                when {
                                        sourcePath.isNullOrBlank() ->
                                                ToolValidationResult(
                                                        valid = false,
                                                        errorMessage =
                                                                "Source path must be specified"
                                                )
                                        targetPath.isNullOrBlank() ->
                                                ToolValidationResult(
                                                        valid = false,
                                                        errorMessage =
                                                                "Target path must be specified"
                                                )
                                        else -> ToolValidationResult(valid = true)
                                }
                        }
                        "get_supported_conversions" -> {
                                // No required parameters for this tool
                                ToolValidationResult(valid = true)
                        }
                        else ->
                                ToolValidationResult(
                                        valid = false,
                                        errorMessage = "Unknown tool: ${tool.name}"
                                )
                }
        }

        override fun getCategory(): ToolCategory {
                return ToolCategory.FILE_WRITE
        }
}

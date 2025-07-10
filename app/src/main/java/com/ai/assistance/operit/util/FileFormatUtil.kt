package com.ai.assistance.operit.util

/**
 * Utility class for file format operations
 */
object FileFormatUtil {
    // Map of supported conversion types
    val supportedConversions = mapOf(
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

    // Get supported formats grouped by type
    val supportedFormats = mapOf(
        "document" to listOf("txt", "pdf", "doc", "docx", "html"),
        "image" to listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "ico"),
        "audio" to listOf("mp3", "wav", "ogg", "m4a", "flac", "aac"),
        "video" to listOf("mp4", "avi", "mov", "webm", "mkv"),
        "archive" to listOf("zip", "tar", "7z", "rar")
    )

    /**
     * Check if conversion is supported
     */
    fun isConversionSupported(sourceExt: String, targetExt: String): Boolean {
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
            (targetExt in supportedFormats["audio"]!! ||
                    targetExt in supportedFormats["image"]!!)
        ) {
            return true
        }

        return false
    }

    /**
     * Determine the conversion type based on source and target extensions
     */
    fun getConversionType(sourceExt: String, targetExt: String): String {
        return when {
            isDocumentConversion(sourceExt, targetExt) -> "document"
            isImageConversion(sourceExt, targetExt) -> "image"
            isAudioConversion(sourceExt, targetExt) -> "audio"
            isVideoConversion(sourceExt, targetExt) -> "video"
            isVideoToAudioConversion(sourceExt, targetExt) -> "video-to-audio"
            isVideoToImageConversion(sourceExt, targetExt) -> "video-to-image"
            isArchiveConversion(sourceExt, targetExt) -> "archive"
            else -> "unknown"
        }
    }

    /**
     * Check if this is a document conversion
     */
    fun isDocumentConversion(sourceExt: String, targetExt: String): Boolean {
        val documentTypes = supportedFormats["document"] ?: emptyList()
        return sourceExt in documentTypes && targetExt in documentTypes
    }

    /**
     * Check if this is an image conversion
     */
    fun isImageConversion(sourceExt: String, targetExt: String): Boolean {
        val imageTypes = supportedFormats["image"] ?: emptyList()
        return (sourceExt in imageTypes && targetExt in imageTypes) ||
                (sourceExt in imageTypes && targetExt == "pdf")
    }

    /**
     * Check if this is an audio conversion
     */
    fun isAudioConversion(sourceExt: String, targetExt: String): Boolean {
        val audioTypes = supportedFormats["audio"] ?: emptyList()
        return sourceExt in audioTypes && targetExt in audioTypes
    }

    /**
     * Check if this is a video conversion
     */
    fun isVideoConversion(sourceExt: String, targetExt: String): Boolean {
        val videoTypes = supportedFormats["video"] ?: emptyList()
        return sourceExt in videoTypes && targetExt in videoTypes
    }

    /**
     * Check if this is a video to audio conversion (extraction)
     */
    fun isVideoToAudioConversion(sourceExt: String, targetExt: String): Boolean {
        val videoTypes = supportedFormats["video"] ?: emptyList()
        val audioTypes = supportedFormats["audio"] ?: emptyList()
        return sourceExt in videoTypes && targetExt in audioTypes
    }

    /**
     * Check if this is a video to image conversion (thumbnail/frame extraction)
     */
    fun isVideoToImageConversion(sourceExt: String, targetExt: String): Boolean {
        val videoTypes = supportedFormats["video"] ?: emptyList()
        val imageTypes = supportedFormats["image"] ?: emptyList()
        return sourceExt in videoTypes && targetExt in imageTypes
    }

    /**
     * Check if this is an archive conversion
     */
    fun isArchiveConversion(sourceExt: String, targetExt: String): Boolean {
        val archiveTypes = supportedFormats["archive"] ?: emptyList()
        return (sourceExt in archiveTypes && targetExt in archiveTypes) ||
                (sourceExt in archiveTypes && targetExt == "extract")
    }

    /**
     * Check if the file format is text-based
     */
    fun isTextBased(ext: String): Boolean {
        return ext.lowercase() in listOf("txt", "md", "csv", "json", "xml", "html", "css", "js", "ts")
    }
} 
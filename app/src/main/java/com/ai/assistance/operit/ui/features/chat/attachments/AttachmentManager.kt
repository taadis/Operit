package com.ai.assistance.operit.ui.features.chat.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.tools.AIToolHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Manages attachment operations for the chat feature Handles adding, removing, and referencing
 * attachments
 */
class AttachmentManager(private val context: Context, private val toolHandler: AIToolHandler) {
    companion object {
        private const val TAG = "AttachmentManager"
    }

    // State for attachments
    private val _attachments = MutableStateFlow<List<AttachmentInfo>>(emptyList())
    val attachments: StateFlow<List<AttachmentInfo>> = _attachments

    // Events
    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent

    /**
     * Inserts a reference to an attachment at the current cursor position in the user's message
     * @return the formatted reference string
     */
    fun createAttachmentReference(attachment: AttachmentInfo): String {
        // Generate XML reference for the attachment
        val attachmentRef = StringBuilder("<attachment ")
        attachmentRef.append("id=\"${attachment.filePath}\" ")
        attachmentRef.append("filename=\"${attachment.fileName}\" ")
        attachmentRef.append("type=\"${attachment.mimeType}\" ")

        // Add size property
        if (attachment.fileSize > 0) {
            attachmentRef.append("size=\"${attachment.fileSize}\" ")
        }

        // Add content property (if exists)
        if (attachment.content.isNotEmpty()) {
            attachmentRef.append("content=\"${attachment.content}\" ")
        }

        attachmentRef.append("/>")

        return attachmentRef.toString()
    }

    /** Handles a file or image attachment selected by the user */
    suspend fun handleAttachment(filePath: String) {
        try {
            // Check if it's a content URI path
            if (filePath.startsWith("content://")) {
                // Handle as URI
                val uri = Uri.parse(filePath)
                val contentResolver = context.contentResolver

                // Get file name from ContentResolver
                val fileName = getFileNameFromUri(uri)

                // Get file size from ContentResolver
                val fileSize = getFileSizeFromUri(uri)

                // Infer MIME type
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                val attachmentInfo =
                        AttachmentInfo(
                                filePath = filePath, // Keep original URI string
                                fileName = fileName,
                                mimeType = mimeType,
                                fileSize = fileSize
                        )

                // Add to attachment list
                val currentList = _attachments.value
                if (!currentList.any { it.filePath == filePath }) {
                    _attachments.value = currentList + attachmentInfo
                }

                _toastEvent.emit("已添加附件: $fileName")
            } else {
                // Handle as regular file path
                val file = java.io.File(filePath)
                if (!file.exists()) {
                    _toastEvent.emit("文件不存在")
                    return
                }

                val fileName = file.name
                val fileSize = file.length()
                val mimeType = getMimeTypeFromPath(filePath) ?: "application/octet-stream"

                val attachmentInfo =
                        AttachmentInfo(
                                filePath = filePath,
                                fileName = fileName,
                                mimeType = mimeType,
                                fileSize = fileSize
                        )

                // Add to attachment list
                val currentList = _attachments.value
                if (!currentList.any { it.filePath == filePath }) {
                    _attachments.value = currentList + attachmentInfo
                }

                _toastEvent.emit("已添加附件: $fileName")
            }
        } catch (e: Exception) {
            _toastEvent.emit("添加附件失败: ${e.message}")
            Log.e(TAG, "添加附件错误", e)
        }
    }

    /** Removes an attachment by its file path */
    fun removeAttachment(filePath: String) {
        val currentList = _attachments.value
        _attachments.value = currentList.filter { it.filePath != filePath }
    }

    /** Clear all attachments */
    fun clearAttachments() {
        _attachments.value = emptyList()
    }

    /** Update attachments with a new list */
    fun updateAttachments(newAttachments: List<AttachmentInfo>) {
        _attachments.value = newAttachments
    }

    /**
     * Captures the current screen content and attaches it to the message
     * Uses the get_page_info AITool to retrieve UI structure
     */
    suspend fun captureScreenContent() {
        try {
            // Create a tool to get page info
            val pageInfoTool = AITool(
                name = "get_page_info",
                parameters = emptyList()
            )
            
            // Execute the tool
            val result = toolHandler.executeTool(pageInfoTool)
            
            if (result.success) {
                // Generate a unique ID for this screen capture
                val captureId = "screen_${System.currentTimeMillis()}"
                val screenContent = result.result.toString()
                
                // Create attachment info with content as filePath
                val attachmentInfo = AttachmentInfo(
                    filePath = captureId,  // Use ID as virtual path
                    fileName = "screen_content.json",
                    mimeType = "text/json",
                    fileSize = screenContent.length.toLong(),
                    content = screenContent  // Add content field to store actual data
                )
                
                // Add to attachments list
                val currentList = _attachments.value
                _attachments.value = currentList + attachmentInfo
                
                _toastEvent.emit("已添加屏幕内容")
            } else {
                _toastEvent.emit("获取屏幕内容失败: ${result.error ?: "未知错误"}")
            }
        } catch (e: Exception) {
            _toastEvent.emit("获取屏幕内容失败: ${e.message}")
            Log.e(TAG, "Error capturing screen content", e)
        }
    }

    /**
     * 获取设备当前通知并作为附件添加到消息
     * 使用get_notifications AITool获取通知数据
     */
    suspend fun captureNotifications(limit: Int = 10) {
        try {
            // 创建工具参数
            val toolParams = listOf(
                ToolParameter("limit", limit.toString()),
                ToolParameter("include_ongoing", "true")
            )
            
            // 创建工具
            val notificationsToolTask = AITool(
                name = "get_notifications",
                parameters = toolParams
            )
            
            // 执行工具
            val result = toolHandler.executeTool(notificationsToolTask)
            
            if (result.success) {
                // 生成唯一ID
                val captureId = "notifications_${System.currentTimeMillis()}"
                val notificationsContent = result.result.toString()
                
                // 创建附件信息
                val attachmentInfo = AttachmentInfo(
                    filePath = captureId,
                    fileName = "notifications.json",
                    mimeType = "application/json",
                    fileSize = notificationsContent.length.toLong(),
                    content = notificationsContent
                )
                
                // 添加到附件列表
                val currentList = _attachments.value
                _attachments.value = currentList + attachmentInfo
                
                _toastEvent.emit("已添加当前通知")
            } else {
                _toastEvent.emit("获取通知失败: ${result.error ?: "未知错误"}")
            }
        } catch (e: Exception) {
            _toastEvent.emit("获取通知失败: ${e.message}")
            Log.e(TAG, "Error capturing notifications", e)
        }
    }
    
    /**
     * 获取设备当前位置并作为附件添加到消息
     * 使用get_device_location AITool获取位置数据
     */
    suspend fun captureLocation(highAccuracy: Boolean = true) {
        try {
            // 创建工具参数
            val toolParams = listOf(
                ToolParameter("high_accuracy", highAccuracy.toString()),
                ToolParameter("timeout", "10")  // 10秒超时
            )
            
            // 创建工具
            val locationToolTask = AITool(
                name = "get_device_location",
                parameters = toolParams
            )
            
            // 执行工具
            val result = toolHandler.executeTool(locationToolTask)
            
            if (result.success) {
                // 生成唯一ID
                val captureId = "location_${System.currentTimeMillis()}"
                val locationContent = result.result.toString()
                
                // 创建附件信息
                val attachmentInfo = AttachmentInfo(
                    filePath = captureId,
                    fileName = "location.json",
                    mimeType = "application/json",
                    fileSize = locationContent.length.toLong(),
                    content = locationContent
                )
                
                // 添加到附件列表
                val currentList = _attachments.value
                _attachments.value = currentList + attachmentInfo
                
                _toastEvent.emit("已添加当前位置")
            } else {
                _toastEvent.emit("获取位置失败: ${result.error ?: "未知错误"}")
            }
        } catch (e: Exception) {
            _toastEvent.emit("获取位置失败: ${e.message}")
            Log.e(TAG, "Error capturing location", e)
        }
    }

    /** Get file name from content URI */
    private suspend fun getFileNameFromUri(uri: Uri): String =
            withContext(Dispatchers.IO) {
                val contentResolver = context.contentResolver
                var fileName = "未知文件"

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex =
                                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) {
                                fileName = cursor.getString(displayNameIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting file name from URI", e)
                }

                return@withContext fileName
            }

    /** Get file size from content URI */
    private suspend fun getFileSizeFromUri(uri: Uri): Long =
            withContext(Dispatchers.IO) {
                val contentResolver = context.contentResolver
                var fileSize = 0L

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting file size from URI", e)
                }

                return@withContext fileSize
            }

    /** Get MIME type from file path */
    private fun getMimeTypeFromPath(filePath: String): String? {
        val extension = filePath.substringAfterLast('.', "")
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "zip" -> "application/zip"
            else -> null
        }
    }
}

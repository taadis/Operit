package com.ai.assistance.operit.tools.defaultTool

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.AdbCommandExecutor
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolResultData
import com.ai.assistance.operit.model.StringResultData
import com.ai.assistance.operit.tools.DirectoryListingData
import com.ai.assistance.operit.tools.FileContentData
import com.ai.assistance.operit.tools.FileOperationData
import com.ai.assistance.operit.tools.FileExistsData
import com.ai.assistance.operit.tools.FindFilesResultData
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Collection of file system operation tools for the AI assistant
 * These tools leverage the AdbCommandExecutor to perform operations safely
 */
class FileSystemTools(private val context: Context) {
    companion object {
        private const val TAG = "FileSystemTools"
        
        // Maximum allowed file size for operations
        private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    }
    
    
    
    /**
     * List files in a directory
     */
    suspend fun listFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }
        
        return try {
            val result = AdbCommandExecutor.executeAdbCommand("ls -la $path")
            
            if (result.success) {
                // Parse the directory listing output
                val entries = parseDirectoryListing(result.stdout, path)
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = DirectoryListingData(path, entries),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to list directory: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error listing directory: ${e.message}"
            )
        }
    }
    
    /**
     * Parse the output of the ls -la command into structured data
     */
    private fun parseDirectoryListing(output: String, path: String): List<DirectoryListingData.FileEntry> {
        val lines = output.trim().split("\n")
        val entries = mutableListOf<DirectoryListingData.FileEntry>()
        
        for (line in lines) {
            try {
                if (line.isBlank() || line.startsWith("total")) continue
                
                // Parse ls -la format: permissions links owner group size date time filename
                // Example: drwxr-xr-x 2 root root 4096 Jan 1 12:34 foldername
                val parts = line.trim().split(Regex("\\s+"), 9) // Max 9 parts to keep filename intact
                
                if (parts.size >= 9) {
                    val permissions = parts[0].substring(1) // Remove the first character (d or -)
                    val isDirectory = parts[0].startsWith("d")
                    val size = parts[4].toLongOrNull() ?: 0
                    val lastModified = "${parts[5]} ${parts[6]} ${parts[7]}"
                    val name = parts[8]
                    
                    // Skip . and .. entries
                    if (name != "." && name != "..") {
                        entries.add(
                            DirectoryListingData.FileEntry(
                                name = name,
                                isDirectory = isDirectory,
                                size = size,
                                permissions = permissions,
                                lastModified = lastModified
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing directory entry: $line", e)
                // Skip this entry but continue processing others
            }
        }
        
        return entries
    }
    
    /**
     * Read file content
     */
    suspend fun readFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }
        
        return try {
            // First check if the file exists
            val existsResult = AdbCommandExecutor.executeAdbCommand("test -f $path && echo 'exists' || echo 'not exists'")
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }
            
            // Check file size before reading
            val sizeResult = AdbCommandExecutor.executeAdbCommand("stat -c %s $path")
            if (sizeResult.success) {
                val size = sizeResult.stdout.trim().toLongOrNull() ?: 0
                if (size > MAX_FILE_SIZE_BYTES) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File is too large (${size / 1024} KB). Maximum allowed size is ${MAX_FILE_SIZE_BYTES / 1024} KB."
                    )
                }
            }
            
            // Read the file content
            val result = AdbCommandExecutor.executeAdbCommand("cat $path")
            
            if (result.success) {
                val size = sizeResult.stdout.trim().toLongOrNull() ?: result.stdout.length.toLong()
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(
                        path = path,
                        content = result.stdout,
                        size = size
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to read file: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }
    
    /**
     * Write content to a file
     */
    suspend fun writeFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append = tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "write",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }
        
        return try {
            // 直接使用echo命令写入内容
            // 对内容进行base64编码，避免特殊字符问题
            val contentBase64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            
            // 使用base64命令解码并写入文件
            val redirectOperator = if (append) ">>" else ">"
            val result = AdbCommandExecutor.executeAdbCommand("echo '$contentBase64' | base64 -d $redirectOperator '$path'")
            
            if (result.success) {
                val operation = if (append) "append" else "write"
                val details = if (append) "Content appended to $path" else "Content written to $path"
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(
                        operation = operation,
                        path = path,
                        successful = true,
                        details = details
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = if (append) "append" else "write",
                        path = path,
                        successful = false,
                        details = "Failed to write to file: ${result.stderr}"
                    ),
                    error = "Failed to write to file: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file", e)
            
            // 提供更具体的错误信息
            val errorMessage = when {
                e is InterruptedException || e.message?.contains("interrupted", ignoreCase = true) == true -> 
                    "ADB连接被中断，可能是网络不稳定导致。请检查ADB连接并重试。错误详情: ${e.message}"
                e is java.net.SocketException || e.message?.contains("socket", ignoreCase = true) == true -> 
                    "ADB网络连接异常，请检查设备是否仍然连接并重试。错误详情: ${e.message}"
                e is java.io.IOException -> 
                    "文件IO错误: ${e.message}。请检查文件路径是否有写入权限。"
                e.message?.contains("permission", ignoreCase = true) == true ->
                    "权限拒绝，无法写入文件: ${e.message}。请检查应用是否有适当的权限。"
                else -> "写入文件时出错: ${e.message}"
            }
            
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = if (append) "append" else "write",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }
    
    /**
     * Delete a file or directory
     */
    suspend fun deleteFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "delete",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }
        
        // Don't allow deleting system directories
        val restrictedPaths = listOf("/system", "/data", "/proc", "/dev")
        if (restrictedPaths.any { path.startsWith(it) }) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "delete",
                    path = path,
                    successful = false,
                    details = "Deleting system directories is not allowed"
                ),
                error = "Deleting system directories is not allowed"
            )
        }
        
        return try {
            val deleteCommand = if (recursive) "rm -rf $path" else "rm -f $path"
            val result = AdbCommandExecutor.executeAdbCommand(deleteCommand)
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = true,
                        details = "Successfully deleted $path"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = false,
                        details = "Failed to delete: ${result.stderr}"
                    ),
                    error = "Failed to delete: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file/directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "delete",
                    path = path,
                    successful = false,
                    details = "Error deleting file/directory: ${e.message}"
                ),
                error = "Error deleting file/directory: ${e.message}"
            )
        }
    }
    
    /**
     * Check if a file or directory exists
     */
    suspend fun fileExists(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }
        
        return try {
            // Check if the path exists
            val existsResult = AdbCommandExecutor.executeAdbCommand("test -e '$path' && echo 'exists' || echo 'not exists'")
            val exists = existsResult.success && existsResult.stdout.trim() == "exists"
            
            if (!exists) {
                // If it doesn't exist, return a simple FileExistsData with exists=false
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileExistsData(
                        path = path,
                        exists = false
                    ),
                    error = ""
                )
            }
            
            // If it exists, check if it's a directory
            val isDirResult = AdbCommandExecutor.executeAdbCommand("test -d '$path' && echo 'true' || echo 'false'")
            val isDirectory = isDirResult.success && isDirResult.stdout.trim() == "true"
            
            // Get the size
            val sizeResult = AdbCommandExecutor.executeAdbCommand("stat -c %s '$path' 2>/dev/null || echo '0'")
            val size = sizeResult.stdout.trim().toLongOrNull() ?: 0
            
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileExistsData(
                    path = path,
                    exists = true,
                    isDirectory = isDirectory,
                    size = size
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file existence", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileExistsData(
                    path = path,
                    exists = false,
                    isDirectory = false,
                    size = 0
                ),
                error = "Error checking file existence: ${e.message}"
            )
        }
    }
    
    /**
     * Move or rename a file or directory
     */
    suspend fun moveFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        
        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details = "Source and destination parameters are required"
                ),
                error = "Source and destination parameters are required"
            )
        }
        
        // Don't allow moving system directories
        val restrictedPaths = listOf("/system", "/data", "/proc", "/dev")
        if (restrictedPaths.any { sourcePath.startsWith(it) }) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details = "Moving system directories is not allowed"
                ),
                error = "Moving system directories is not allowed"
            )
        }
        
        return try {
            val result = AdbCommandExecutor.executeAdbCommand("mv '$sourcePath' '$destPath'")
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = true,
                        details = "Successfully moved $sourcePath to $destPath"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = false,
                        details = "Failed to move file: ${result.stderr}"
                    ),
                    error = "Failed to move file: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details = "Error moving file: ${e.message}"
                ),
                error = "Error moving file: ${e.message}"
            )
        }
    }
    
    /**
     * Copy a file or directory
     */
    suspend fun copyFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false
        
        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = "Source and destination parameters are required"
                ),
                error = "Source and destination parameters are required"
            )
        }
        
        return try {
            val copyCommand = if (recursive) "cp -r '$sourcePath' '$destPath'" else "cp '$sourcePath' '$destPath'"
            val result = AdbCommandExecutor.executeAdbCommand(copyCommand)
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = true,
                        details = "Successfully copied $sourcePath to $destPath"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = false,
                        details = "Failed to copy file: ${result.stderr}"
                    ),
                    error = "Failed to copy file: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = "Error copying file: ${e.message}"
                ),
                error = "Error copying file: ${e.message}"
            )
        }
    }
    
    /**
     * Create a directory
     */
    suspend fun makeDirectory(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val createParents = tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean() ?: false
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "mkdir",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }
        
        return try {
            val mkdirCommand = if (createParents) "mkdir -p '$path'" else "mkdir '$path'"
            val result = AdbCommandExecutor.executeAdbCommand(mkdirCommand)
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(
                        operation = "mkdir",
                        path = path,
                        successful = true,
                        details = "Successfully created directory $path"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "mkdir",
                        path = path,
                        successful = false,
                        details = "Failed to create directory: ${result.stderr}"
                    ),
                    error = "Failed to create directory: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "mkdir",
                    path = path,
                    successful = false,
                    details = "Error creating directory: ${e.message}"
                ),
                error = "Error creating directory: ${e.message}"
            )
        }
    }
    
    /**
     * Search for files matching a pattern
     */
    suspend fun findFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
        
        if (path.isBlank() || pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = emptyList()
                ),
                error = "Path and pattern parameters are required"
            )
        }
        
        return try {
            // Add options for different search modes
            val usePathPattern = tool.parameters.find { it.name == "use_path_pattern" }?.value?.toBoolean() ?: false
            val caseInsensitive = tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean() ?: false
            
            // Add depth control parameter (default to -1 for unlimited depth/fully recursive)
            val maxDepth = tool.parameters.find { it.name == "max_depth" }?.value?.toIntOrNull() ?: -1

            // Determine which search option to use
            val searchOption = if (usePathPattern) {
                if (caseInsensitive) "-ipath" else "-path"
            } else {
                if (caseInsensitive) "-iname" else "-name"
            }
            
            // Properly escape the pattern if quotes are required
            val escapedPattern = pattern.replace("'", "'\\''") 
            val patternForCommand = "'$escapedPattern'"
            
            // Build the command with depth control if specified
            val depthOption = if (maxDepth >= 0) "-maxdepth $maxDepth" else ""
            val command = "find $path $depthOption $searchOption $patternForCommand"
            
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            // Always consider the command successful, and check the output
            val fileList = result.stdout.trim()
            
            // 将结果转换为字符串列表
            val files = if (fileList.isBlank()) {
                emptyList()
            } else {
                fileList.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            }
            
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = files
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for files", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FindFilesResultData(
                    path = path,
                    pattern = pattern,
                    files = emptyList()
                ),
                error = "Error searching for files: ${e.message}"
            )
        }
    }
    
    /**
     * Get file information
     */
    suspend fun fileInfo(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }
        
        return try {
            // Check if file exists
            val existsResult = AdbCommandExecutor.executeAdbCommand("test -e '$path' && echo 'exists' || echo 'not exists'")
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File or directory does not exist: $path"
                )
            }
            
            // Get file details using stat
            val result = AdbCommandExecutor.executeAdbCommand("stat '$path'")
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("File information for $path:\n${result.stdout}"),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get file information: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file information", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting file information: ${e.message}"
            )
        }
    }
    
    /**
     * Zip files or directories
     */
    suspend fun zipFiles(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val zipPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        
        if (sourcePath.isBlank() || zipPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Source and destination parameters are required"
            )
        }
        
        return try {
            // Make sure zip utility is available
            val zipCheckResult = AdbCommandExecutor.executeAdbCommand("which zip")
            if (!zipCheckResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "The zip utility is not available on this device"
                )
            }
            
            // Compress the file/directory
            val result = AdbCommandExecutor.executeAdbCommand("cd '${File(sourcePath).parent}' && zip -r '$zipPath' '${File(sourcePath).name}'")
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(
                        operation = "zip",
                        path = sourcePath,
                        successful = true,
                        details = "Successfully compressed $sourcePath to $zipPath"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to compress: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing files", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error compressing files: ${e.message}"
            )
        }
    }
    
    /**
     * Unzip a zip file
     */
    suspend fun unzipFiles(tool: AITool): ToolResult {
        val zipPath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        
        if (zipPath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Source and destination parameters are required"
            )
        }
        
        return try {
            // Make sure unzip utility is available
            val unzipCheckResult = AdbCommandExecutor.executeAdbCommand("which unzip")
            if (!unzipCheckResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "The unzip utility is not available on this device"
                )
            }
            
            // Create destination directory if it doesn't exist
            AdbCommandExecutor.executeAdbCommand("mkdir -p '$destPath'")
            
            // Extract the zip file
            val result = AdbCommandExecutor.executeAdbCommand("unzip '$zipPath' -d '$destPath'")
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileOperationData(
                        operation = "unzip",
                        path = zipPath,
                        successful = true,
                        details = "Successfully extracted $zipPath to $destPath"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to extract: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting zip file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error extracting zip file: ${e.message}"
            )
        }
    }
    
    /**
     * 打开文件
     * 使用系统默认应用打开文件
     */
    suspend fun openFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供path参数"
            )
        }
        
        return try {
            // 首先检查文件是否存在
            val existsResult = AdbCommandExecutor.executeAdbCommand("test -f '$path' && echo 'exists' || echo 'not exists'")
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "文件不存在: $path"
                )
            }
            
            // 获取文件MIME类型
            val mimeTypeResult = AdbCommandExecutor.executeAdbCommand("file --mime-type -b '$path'")
            val mimeType = if (mimeTypeResult.success) mimeTypeResult.stdout.trim() else "application/octet-stream"
            
            // 使用Android intent打开文件
            val command = "am start -a android.intent.action.VIEW -d 'file://$path' -t '$mimeType'"
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("已使用系统应用打开文件: $path"),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "打开文件失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开文件时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "打开文件时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 分享文件
     * 调用系统分享功能
     */
    suspend fun shareFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val title = tool.parameters.find { it.name == "title" }?.value ?: "分享文件"
        
        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供path参数"
            )
        }
        
        return try {
            // 首先检查文件是否存在
            val existsResult = AdbCommandExecutor.executeAdbCommand("test -f '$path' && echo 'exists' || echo 'not exists'")
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "文件不存在: $path"
                )
            }
            
            // 获取文件MIME类型
            val mimeTypeResult = AdbCommandExecutor.executeAdbCommand("file --mime-type -b '$path'")
            val mimeType = if (mimeTypeResult.success) mimeTypeResult.stdout.trim() else "application/octet-stream"
            
            // 使用Android intent分享文件
            val command = "am start -a android.intent.action.SEND -t '$mimeType' --es android.intent.extra.SUBJECT '$title' --es android.intent.extra.STREAM 'file://$path' --ez android.intent.extra.STREAM_REFERENCE true"
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("已打开分享界面，分享文件: $path"),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "分享文件失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享文件时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "分享文件时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 下载文件
     * 从网络URL下载文件到指定路径
     */
    suspend fun downloadFile(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        
        if (url.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供url和destination参数"
            )
        }
        
        // 验证URL格式
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "URL必须以http://或https://开头"
            )
        }
        
        return try {
            // 确保目标目录存在
            val directory = File(destPath).parent
            if (directory != null) {
                val mkdirResult = AdbCommandExecutor.executeAdbCommand("mkdir -p '$directory'")
                if (!mkdirResult.success) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "无法创建目标目录: ${mkdirResult.stderr}"
                    )
                }
            }
            
            // 使用wget或curl下载文件
            // 首先检查是否有wget
            val wgetCheckResult = AdbCommandExecutor.executeAdbCommand("which wget")
            
            val downloadCommand = if (wgetCheckResult.success) {
                "wget '$url' -O '$destPath' --no-check-certificate -q"
            } else {
                // 如果没有wget，尝试使用curl
                val curlCheckResult = AdbCommandExecutor.executeAdbCommand("which curl")
                if (!curlCheckResult.success) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "系统中没有wget或curl工具，无法下载文件"
                    )
                }
                "curl -L '$url' -o '$destPath' -s"
            }
            
            val result = AdbCommandExecutor.executeAdbCommand(downloadCommand)
            
            if (result.success) {
                // 验证文件是否已下载
                val checkFileResult = AdbCommandExecutor.executeAdbCommand("test -f '$destPath' && echo 'exists' || echo 'not exists'")
                if (checkFileResult.stdout.trim() != "exists") {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "下载似乎已完成，但文件未被创建"
                    )
                }
                
                // 获取文件大小
                val fileSizeResult = AdbCommandExecutor.executeAdbCommand("stat -c %s '$destPath'")
                val fileSize = if (fileSizeResult.success) {
                    val size = fileSizeResult.stdout.trim().toLongOrNull() ?: 0
                    if (size > 1024 * 1024) {
                        String.format("%.2f MB", size / (1024.0 * 1024.0))
                    } else if (size > 1024) {
                        String.format("%.2f KB", size / 1024.0)
                    } else {
                        "$size bytes"
                    }
                } else {
                    "未知大小"
                }
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("文件下载成功: $url -> $destPath (文件大小: $fileSize)"),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "下载失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载文件时出错", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "下载文件时出错: ${e.message}"
            )
        }
    }
} 
package com.ai.assistance.operit.core.tools.defaultTool

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.tools.ADBResultData
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.tools.system.AdbCommandExecutor
import kotlinx.coroutines.runBlocking

/**
 * Tool for executing ADB commands directly. This provides direct access to ADB shell commands for
 * system operations. Note: This requires Shizuku service to be running with proper permissions.
 */
class ADBToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ADBToolExecutor"
        private const val DEFAULT_TIMEOUT = 15000L // 15 seconds
    }

    fun invoke(tool: AITool): ToolResult {
        // Validate parameters
        val validationResult = validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = validationResult.errorMessage
            )
        }

        val command = tool.parameters.find { it.name == "command" }?.value ?: ""
        // Timeout parameter is kept for API compatibility but not used by AdbCommandExecutor

        return try {
            // Use AdbCommandExecutor to execute the command
            val result = runBlocking {
                AdbCommandExecutor.executeAdbCommand(command)
            }
            
            if (result.success) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = ADBResultData(
                                command = command,
                                output = result.stdout,
                                exitCode = result.exitCode
                        )
                )
            } else {
                // Combine stdout and stderr for error reporting
                val errorOutput = if (result.stderr.isNotEmpty()) {
                    "${result.stderr.trim()}\n${result.stdout.trim()}"
                } else {
                    result.stdout.trim()
                }
                
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "ADB command execution failed (exit code: ${result.exitCode}): $errorOutput"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing ADB command", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "ADB command execution failed: ${e.message}"
            )
        }
    }

    /** Validates the parameters for the ADB tool. */
    fun validateParameters(tool: AITool): ToolValidationResult {
        val command = tool.parameters.find { it.name == "command" }?.value

        return when {
            command.isNullOrBlank() -> {
                ToolValidationResult(valid = false, errorMessage = "Command parameter is required")
            }
            command.contains("rm -rf") || command.contains("format") -> {
                ToolValidationResult(
                        valid = false,
                        errorMessage = "Potentially dangerous command detected"
                )
            }
            else -> {
                // Check if Shizuku service is available
                if (!AdbCommandExecutor.isShizukuServiceRunning()) {
                    return ToolValidationResult(
                        valid = false, 
                        errorMessage = "Shizuku service is not running. ${AdbCommandExecutor.getShizukuStartupInstructions()}"
                    )
                }
                
                // Check if we have Shizuku permission
                if (!AdbCommandExecutor.hasShizukuPermission()) {
                    return ToolValidationResult(
                        valid = false,
                        errorMessage = "Shizuku permission not granted. Please grant permission to use ADB commands."
                    )
                }
                
                ToolValidationResult(valid = true)
            }
        }
    }
}

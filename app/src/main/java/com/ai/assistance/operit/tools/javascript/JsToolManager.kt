package com.ai.assistance.operit.tools.javascript

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolParameter
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.packTool.PackageManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import com.ai.assistance.operit.tools.StringResultData

/**
 * Manages JavaScript tool execution using JsEngine
 * This class handles the execution of JavaScript code in package tools
 * and coordinates tool calls from JavaScript back to native Android code.
 */
class JsToolManager private constructor(
    private val context: Context,
    private val packageManager: PackageManager
) {
    companion object {
        private const val TAG = "JsToolManager"
        private const val SCRIPT_TIMEOUT_MS = 60000L // 60 seconds timeout
        
        @Volatile
        private var INSTANCE: JsToolManager? = null
        
        fun getInstance(context: Context, packageManager: PackageManager): JsToolManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JsToolManager(context.applicationContext, packageManager).also { INSTANCE = it }
            }
        }
    }
    
    // JavaScript engine for executing code
    private val jsEngine = JsEngine(context)
    
    // Tool handler for executing tools
    private val toolHandler = AIToolHandler.getInstance(context)
    
    /**
     * Execute a specific JavaScript tool
     * @param toolName The name of the tool to execute (format: packageName.functionName)
     * @param params Parameters to pass to the tool function
     * @return The result of tool execution
     */
    fun executeScript(toolName: String, params: Map<String, String>): String {
        try {
            // Split the tool name to get package and function names
            val parts = toolName.split(".")
            if (parts.size < 2) {
                return "Invalid tool name format: $toolName. Expected format: packageName.functionName"
            }
            
            val packageName = parts[0]
            val functionName = parts[1]
            
            // Get the package script
            val script = packageManager.getPackageScript(packageName)
                ?: return "Package not found: $packageName"
            
            Log.d(TAG, "Executing function $functionName in package $packageName")
            
            // Execute the function in the script
            val result = jsEngine.executeScriptFunction(script, functionName, params)
            
            return result?.toString() ?: "null"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing script: ${e.message}", e)
            return "Error: ${e.message}"
        }
    }
    
    /**
     * Execute a JavaScript script with the given tool parameters
     * @param script The JavaScript code to execute
     * @param tool The tool being executed (provides parameters)
     * @return The result of script execution
     */
    suspend fun executeScript(script: String, tool: AITool): ToolResult {
        try {
            Log.d(TAG, "Executing script for tool: ${tool.name}")
            
            // Extract the function name from the tool name (packageName:toolName)
            val parts = tool.name.split(":")
            if (parts.size != 2) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid tool name format. Expected 'packageName:toolName'"
                )
            }
            
            val functionName = parts[1]
            
            // Convert tool parameters to map for the script
            val params = tool.parameters.associate { it.name to it.value }
            
            // Execute the script with timeout
            val result = try {
                withTimeout(SCRIPT_TIMEOUT_MS) {
                    Log.d(TAG, "Starting script execution for function: $functionName")
                    
                    // 记录关键脚本部分，以便调试
                    try {
                        // 提取脚本中与函数名相关的代码片段
                        val functionSignature = "(?:function\\s+$functionName|$functionName\\s*=\\s*function|exports\\.$functionName\\s*=\\s*function)"
                        val pattern = Pattern.compile(functionSignature, Pattern.MULTILINE)
                        val matcher = pattern.matcher(script)
                        
                        if (matcher.find()) {
                            val position = matcher.start()
                            val linesBefore = 2
                            val linesAfter = 3
                            
                            // 简单的行数计算
                            var startLine = 0
                            var endLine = 0
                            var currentLine = 1
                            var currentPos = 0
                            
                            for (i in script.indices) {
                                if (i == position) {
                                    startLine = maxOf(1, currentLine - linesBefore)
                                }
                                
                                if (script[i] == '\n') {
                                    currentLine++
                                    if (currentLine > startLine + linesAfter && startLine > 0) {
                                        endLine = currentLine
                                        break
                                    }
                                }
                            }
                            
                            if (startLine > 0) {
                                val lines = script.split("\n")
                                val codeSnippet = lines.subList(startLine - 1, minOf(lines.size, startLine + linesAfter)).joinToString("\n")
                                Log.d(TAG, "Function code snippet (lines $startLine-${startLine+linesAfter}):\n$codeSnippet")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error analyzing script for debugging: ${e.message}")
                    }
                    
                    val startTime = System.currentTimeMillis()
                    val scriptResult = try {
                        jsEngine.executeScriptFunction(script, functionName, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception executing JavaScript: ${e.message}", e)
                        throw e
                    }
                    
                    val executionTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Script execution completed in ${executionTime}ms with result type: ${scriptResult?.javaClass?.name ?: "null"}")
                    
                    // Handle different types of results
                    when {
                        scriptResult == null -> "Script returned null result"
                        scriptResult is String && scriptResult.startsWith("Error:") -> {
                            // 尝试提取更详细的错误信息
                            val errorMsg = scriptResult.substring("Error:".length).trim()
                            Log.e(TAG, "Script execution error: $errorMsg")
                            throw Exception(errorMsg)
                        }
                        else -> {
                            Log.d(TAG, "Parsing script result: ${scriptResult.toString().take(100)}${if (scriptResult.toString().length > 100) "..." else ""}")
                            scriptResult.toString()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle coroutine cancellation specifically
                Log.w(TAG, "Script execution was cancelled: ${e.message}")
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Script execution was cancelled: ${e.message}"
                )
            } catch (e: Exception) {
                // 捕获并记录其他执行异常
                Log.e(TAG, "Exception during script execution: ${e.message}", e)
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Script execution failed: ${e.message}"
                )
            }
            
            // Check if the result is an error message
            if (result.startsWith("Error:") || result.startsWith("Script error:") || result.startsWith("Async error:")) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = result
                )
            }
            
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(result)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing script for tool ${tool.name}: ${e.message}", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Script execution error: ${e.message}"
            )
        }
    }

    /**
     * Clean up resources when the manager is no longer needed
     */
    fun destroy() {
        jsEngine.destroy()
    }
} 
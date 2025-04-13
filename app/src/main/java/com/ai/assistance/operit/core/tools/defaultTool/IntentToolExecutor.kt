package com.ai.assistance.operit.core.tools.defaultTool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.tools.IntentResultData
import com.ai.assistance.operit.tools.StringResultData
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for executing Android Intents. This provides the ability to create and launch Android
 * intents for various operations like starting activities, services, or broadcasts.
 */
class IntentToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "IntentToolExecutor"
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

        val action = tool.parameters.find { it.name == "action" }?.value
        val uri = tool.parameters.find { it.name == "uri" }?.value
        val packageName = tool.parameters.find { it.name == "package" }?.value
        val flags = tool.parameters.find { it.name == "flags" }?.value
        val extras = tool.parameters.find { it.name == "extras" }?.value
        val componentName = tool.parameters.find { it.name == "component" }?.value

        return try {
            // Create the intent
            val intent = Intent()

            // Set action if provided
            if (!action.isNullOrBlank()) {
                intent.action = action
            }

            // Set data URI if provided
            if (!uri.isNullOrBlank()) {
                intent.data = Uri.parse(uri)
            }

            // Set package if provided
            if (!packageName.isNullOrBlank()) {
                intent.`package` = packageName
            }

            // Set component if provided
            if (!componentName.isNullOrBlank()) {
                val parts = componentName.split("/")
                if (parts.size == 2) {
                    intent.setClassName(parts[0], parts[1])
                }
            }

            // Set flags if provided
            if (!flags.isNullOrBlank()) {
                try {
                    val flagsJson = JSONArray(flags)
                    var combinedFlags = 0
                    for (i in 0 until flagsJson.length()) {
                        val flag = flagsJson.getInt(i)
                        combinedFlags = combinedFlags or flag
                    }
                    intent.flags = combinedFlags
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing flags", e)
                    // Try to parse as a single integer value
                    try {
                        intent.flags = flags.toInt()
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error parsing flags as integer", e2)
                    }
                }
            }

            // Set extras if provided
            if (!extras.isNullOrBlank()) {
                try {
                    val extrasJson = JSONObject(extras)
                    val keys = extrasJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = extrasJson.get(key)

                        when (value) {
                            is String -> intent.putExtra(key, value)
                            is Int -> intent.putExtra(key, value)
                            is Boolean -> intent.putExtra(key, value)
                            is Float -> intent.putExtra(key, value)
                            is Double -> intent.putExtra(key, value)
                            is Long -> intent.putExtra(key, value)
                            else -> {
                                // Try to detect array types
                                if (value is JSONArray) {
                                    // Handle various array types
                                    if (value.length() > 0) {
                                        val firstItem = value.get(0)
                                        when (firstItem) {
                                            is String -> {
                                                val stringArray =
                                                    Array(value.length()) { i ->
                                                        value.getString(i)
                                                    }
                                                intent.putExtra(key, stringArray)
                                            }

                                            is Int -> {
                                                val intArray =
                                                    IntArray(value.length()) { i ->
                                                        value.getInt(i)
                                                    }
                                                intent.putExtra(key, intArray)
                                            }

                                            else -> {
                                                // Convert to string if type is unsupported
                                                intent.putExtra(key, value.toString())
                                            }
                                        }
                                    }
                                } else {
                                    // Convert to string if type is unsupported
                                    intent.putExtra(key, value.toString())
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing extras", e)
                }
            }

            // Check if intent is valid
            if (intent.action == null && componentName.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Intent must have either an action or component specified"
                )
            }

            // Add FLAG_ACTIVITY_NEW_TASK for safety if not already set
            // This is needed when starting activities from non-activity contexts
            if (intent.action?.startsWith("android.intent.action.") == true &&
                intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0
            ) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Execute the intent
            try {
                val result =
                    when {
                        action?.startsWith("android.intent.action.SEND") == true ||
                            action?.startsWith("android.intent.action.VIEW") == true ||
                            action?.startsWith("android.intent.action.MAIN") == true ||
                            action?.startsWith("android.intent.action.DIAL") == true ||
                            action?.startsWith("android.intent.action.CALL") == true ||
                            action?.startsWith("android.intent.action.INSERT") == true -> {
                            // Activities that should be shown to the user
                            context.startActivity(intent)
                            "Activity started successfully"
                        }

                        action?.startsWith("android.intent.action.PICK") == true ||
                            action?.startsWith("android.intent.action.GET_CONTENT") ==
                            true -> {
                            // For content pickers, we can't easily get the result in this
                            // context
                            context.startActivity(intent)
                            "Picker activity started"
                        }

                        else -> {
                            // For other intents, we can use sendBroadcast
                            context.sendBroadcast(intent)
                            "Broadcast sent successfully"
                        }
                    }

                // Bundle up the intent details for the response
                val extras = Bundle()
                intent.extras?.let { extras.putAll(it) }

                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                    IntentResultData(
                        action = intent.action ?: "null",
                        uri = intent.data?.toString() ?: "null",
                        package_name = intent.`package` ?: "null",
                        component = intent.component?.flattenToString() ?: "null",
                        flags = intent.flags,
                        extras_count = extras.size(),
                        result = result
                    )
                )
            } catch (e: Exception) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Intent execution failed: ${e.message}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Intent", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Intent execution failed: ${e.message}"
            )
        }
    }

    /** Validates the parameters for the Intent tool. */
    fun validateParameters(tool: AITool): ToolValidationResult {
        val action = tool.parameters.find { it.name == "action" }?.value
        val component = tool.parameters.find { it.name == "component" }?.value

        if (action.isNullOrBlank() && component.isNullOrBlank()) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Either action or component parameter is required"
            )
        }

        return ToolValidationResult(valid = true)
    }
}

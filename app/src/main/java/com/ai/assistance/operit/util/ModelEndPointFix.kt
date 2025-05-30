package com.ai.assistance.operit.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ai.assistance.operit.data.preferences.ApiPreferences
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** API端点修复工具类 */
object ModelEndPointFix {
    private const val TAG = "ModelEndPointFix"
    private const val REQUIRED_ENDPOINT_SUFFIX = "v1/chat/completions"

    /** 常见的补全路径片段 */
    private val COMPLETION_PATH_PATTERNS =
            listOf(
                    "/v1/chat/completions",
                    "/chat/completions",
                    "/v1/completions",
                    "/completions",
                    "/generate"
            )

    /** 检查端点是否包含补全路径 */
    fun containsCompletionsPath(endpoint: String): Boolean {
        val normalizedEndpoint = endpoint.trim().lowercase()
        return COMPLETION_PATH_PATTERNS.any { normalizedEndpoint.contains(it) }
    }

    /**
     * Validates if the given API endpoint is properly formatted and ends with the required path.
     *
     * @param endpoint The API endpoint to validate
     * @return True if the endpoint is valid and ends with the required path, false otherwise
     */
    fun isValidEndpoint(endpoint: String): Boolean {
        if (endpoint.isBlank()) return false

        try {
            // Try parsing as URL to validate basic format
            val url = URL(endpoint)

            // Check if the endpoint ends with the required path
            return endpoint.endsWith(REQUIRED_ENDPOINT_SUFFIX)
        } catch (e: MalformedURLException) {
            Log.e(TAG, "Invalid URL format: $endpoint", e)
            return false
        }
    }

    /** 如果需要，添加补全路径 */
    fun fixEndpointIfNeeded(endpoint: String): String {
        val trimmedEndpoint = endpoint.trim()

        if (trimmedEndpoint.isEmpty()) return trimmedEndpoint
        if (containsCompletionsPath(trimmedEndpoint)) return trimmedEndpoint

        // 移除尾部斜杠
        val baseEndpoint =
                if (trimmedEndpoint.endsWith("/")) {
                    trimmedEndpoint.substring(0, trimmedEndpoint.length - 1)
                } else {
                    trimmedEndpoint
                }

        // 检查是否是OpenAI端点
        return if (baseEndpoint.contains("openai.com")) {
            "$baseEndpoint/v1/chat/completions"
        } else {
            "$baseEndpoint/v1/chat/completions"
        }
    }

    /**
     * Attempts to fix an API endpoint to ensure it ends with the required path.
     *
     * @param endpoint The API endpoint to fix
     * @return The fixed endpoint, or null if it couldn't be fixed
     */
    fun fixEndpoint(endpoint: String): String? {
        if (endpoint.isBlank()) return null

        try {
            // Try parsing as URL to validate basic format
            val url = URL(endpoint)

            // If already valid, return as is
            if (endpoint.endsWith(REQUIRED_ENDPOINT_SUFFIX)) {
                return endpoint
            }

            // Remove trailing slashes if any
            var baseUrl = endpoint.trimEnd('/')

            // If endpoint doesn't contain the required path at all, append it
            if (!baseUrl.contains(REQUIRED_ENDPOINT_SUFFIX)) {
                baseUrl = "$baseUrl/$REQUIRED_ENDPOINT_SUFFIX"
                return baseUrl
            }

            // If endpoint contains part of the path but not correctly formatted, fix it
            val suffixParts = REQUIRED_ENDPOINT_SUFFIX.split("/")

            // If endpoint contains "v1" but not the complete path
            if (baseUrl.contains("/v1") && !baseUrl.endsWith(REQUIRED_ENDPOINT_SUFFIX)) {
                // Extract the base part (before /v1)
                val basePart = baseUrl.substringBefore("/v1")
                return "$basePart/$REQUIRED_ENDPOINT_SUFFIX"
            }

            return baseUrl
        } catch (e: MalformedURLException) {
            Log.e(TAG, "Failed to fix endpoint, invalid URL format: $endpoint", e)
            return null
        }
    }

    /**
     * Automatically fixes and updates the API endpoint in settings if needed.
     *
     * @param context Android context
     * @param endpoint Current endpoint value
     * @param coroutineScope Coroutine scope for performing the update operation
     * @param onEndpointFixed Callback with the fixed endpoint (optional)
     * @return True if an attempt to fix was made, false if no fix was needed
     */
    fun autoFixAndUpdateEndpoint(
            context: Context,
            endpoint: String,
            coroutineScope: CoroutineScope,
            onEndpointFixed: ((String) -> Unit)? = null
    ): Boolean {
        if (isValidEndpoint(endpoint)) {
            // Already valid, no fix needed
            return false
        }

        val fixedEndpoint = fixEndpoint(endpoint) ?: return false

        // If successfully fixed, update in preferences
        if (fixedEndpoint != endpoint) {
            val apiPreferences = ApiPreferences(context)

            coroutineScope.launch(Dispatchers.IO) {
                apiPreferences.saveApiEndpoint(fixedEndpoint)

                // Notify on main thread that endpoint was fixed
                launch(Dispatchers.Main) {
                    Log.d(TAG, "Fixed endpoint from '$endpoint' to '$fixedEndpoint'")
                    Toast.makeText(
                                    context,
                                    "已修复API端点格式: 添加了 v1/chat/completions 路径",
                                    Toast.LENGTH_SHORT
                            )
                            .show()

                    onEndpointFixed?.invoke(fixedEndpoint)
                }
            }
            return true
        }

        return false
    }

    /**
     * Checks the API endpoint and returns a warning message if it doesn't contain completions path.
     * Does not perform any auto-fixing.
     *
     * @param endpoint Current endpoint value
     * @return Warning message if the endpoint might need attention, null otherwise
     */
    fun checkEndpointAndWarn(endpoint: String): String? {
        if (endpoint.isBlank()) {
            return null
        }

        // Only check if it contains "completions" path
        if (!containsCompletionsPath(endpoint)) {
            Log.d(TAG, "Warning: API endpoint '$endpoint' might need completions path")
            return "提示: API地址可能需要包含补全路径(如v1/chat/completions)"
        }

        return null
    }
}

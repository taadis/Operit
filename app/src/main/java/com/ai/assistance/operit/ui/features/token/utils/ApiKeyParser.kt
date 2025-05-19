package com.ai.assistance.operit.ui.features.token.utils

import android.util.Log
import com.ai.assistance.operit.ui.features.token.models.ApiKey
import org.json.JSONArray
import org.json.JSONObject

/** API密钥数据解析工具类 */
object ApiKeyParser {
    /** 解析API密钥响应 */
    fun parseApiKeysResponse(response: String): List<ApiKey> {
        try {
            Log.d("ApiKeyParser", "Starting to parse API key response: ${response.take(100)}...")
            val jsonObject = JSONObject(response)
            Log.d(
                    "ApiKeyParser",
                    "JSON Keys at root level: ${jsonObject.keys().asSequence().toList()}"
            )

            // 首先尝试直接的api_keys路径（来自我们简化的JS）
            if (jsonObject.has("api_keys")) {
                val apiKeysArray = jsonObject.getJSONArray("api_keys")
                Log.d("ApiKeyParser", "API keys array length: ${apiKeysArray.length()}")
                return parseApiKeyArray(apiKeysArray)
            }

            // 尝试标准路径: data -> api_keys
            if (jsonObject.has("data")) {
                val data = jsonObject.getJSONObject("data")
                Log.d(
                        "ApiKeyParser",
                        "JSON Keys in data: ${data.keys().asSequence().toList()}"
                )

                if (data.has("api_keys")) {
                    val apiKeysArray = data.getJSONArray("api_keys")
                    Log.d("ApiKeyParser", "API keys array length: ${apiKeysArray.length()}")
                    return parseApiKeyArray(apiKeysArray)
                }

                // 尝试次级路径: data -> biz_data -> api_keys
                if (data.has("biz_data") && !data.isNull("biz_data")) {
                    val bizData = data.getJSONObject("biz_data")
                    if (bizData.has("api_keys")) {
                        val apiKeysArray = bizData.getJSONArray("api_keys")
                        Log.d(
                                "ApiKeyParser",
                                "API keys array length (from biz_data): ${apiKeysArray.length()}"
                        )
                        return parseApiKeyArray(apiKeysArray)
                    }
                }
            }

            Log.e("ApiKeyParser", "Could not find api_keys in response structure")
            return emptyList()
        } catch (e: Exception) {
            Log.e("ApiKeyParser", "Error parsing API key response: ${e.message}")
            Log.e("ApiKeyParser", "Raw response: $response")
            throw e  // 重新抛出异常，让调用者知道出了问题
        }
    }

    /** 解析API密钥数组 */
    private fun parseApiKeyArray(apiKeysArray: JSONArray): List<ApiKey> {
        val result = mutableListOf<ApiKey>()

        for (i in 0 until apiKeysArray.length()) {
            try {
                val keyObject = apiKeysArray.getJSONObject(i)
                val apiKey =
                        ApiKey(
                                name = keyObject.getString("name"),
                                sensitiveId = keyObject.getString("sensitive_id"),
                                createdAt = keyObject.getLong("created_at"),
                                lastUse =
                                        if (keyObject.isNull("last_use")) null
                                        else keyObject.getLong("last_use"),
                                trackingId = keyObject.getString("tracking_id")
                        )
                result.add(apiKey)
            } catch (e: Exception) {
                Log.e("ApiKeyParser", "Error parsing API key at index $i: ${e.message}")
            }
        }

        Log.d("ApiKeyParser", "Successfully parsed ${result.size} API keys")
        return result
    }
}

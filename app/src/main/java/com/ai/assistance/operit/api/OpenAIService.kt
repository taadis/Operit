package com.ai.assistance.operit.api

import android.util.Log
import com.ai.assistance.operit.data.model.ModelOption
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OpenAI API服务 */
class OpenAIService {
    private val TAG = "OpenAIService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 从完整URL提取基本URL (v1之前的部分) 
     * 例如: https://api.openai.com/v1/chat/completions -> https://api.openai.com
     */
    private fun extractBaseUrl(fullUrl: String): String {
        return try {
            val url = URL(fullUrl)
            val path = url.path
            val v1Index = path.indexOf("/v1")
            if (v1Index >= 0) {
                val baseUrl = "${url.protocol}://${url.host}"
                if (url.port != -1 && url.port != 80 && url.port != 443) {
                    "$baseUrl:${url.port}"
                } else {
                    baseUrl
                }
            } else {
                fullUrl.substringBefore("/v1")
            }
        } catch (e: Exception) {
            Log.e(TAG, "URL解析错误: $e")
            fullUrl
        }
    }

    /**
     * 获取OpenAI模型列表
     * @param apiKey OpenAI API密钥
     * @param apiEndpoint 完整的API端点URL (如 https://api.openai.com/v1/chat/completions)
     * @return 模型列表
     */
    suspend fun getModels(apiKey: String, apiEndpoint: String): Result<List<ModelOption>> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = extractBaseUrl(apiEndpoint)
                val modelsUrl = "$baseUrl/v1/models"
                Log.d(TAG, "获取模型列表: $modelsUrl")

                val request = Request.Builder()
                    .url(modelsUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("API请求失败: ${response.code}"))
                }
                
                val responseBody = response.body?.string() ?: 
                    return@withContext Result.failure(IOException("响应体为空"))
                
                // 手动解析JSON，提取模型列表
                val modelOptions = parseModelResponse(responseBody)
                Result.success(modelOptions)
                
            } catch (e: Exception) {
                Log.e(TAG, "获取模型列表失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 解析API响应，提取模型列表
     */
    private fun parseModelResponse(jsonResponse: String): List<ModelOption> {
        val modelList = mutableListOf<ModelOption>()
        
        try {
            val jsonObject = JSONObject(jsonResponse)
            val dataArray = jsonObject.getJSONArray("data")
            
            for (i in 0 until dataArray.length()) {
                val modelObj = dataArray.getJSONObject(i)
                val id = modelObj.getString("id")
                modelList.add(ModelOption(id = id, name = id))
            }
        } catch (e: JSONException) {
            Log.e(TAG, "解析JSON失败: ${e.message}", e)
        }
        
        // 按照模型名称排序
        return modelList.sortedBy { it.id }
    }
}

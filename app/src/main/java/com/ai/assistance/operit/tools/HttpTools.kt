package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * HTTP网络请求工具
 * 提供直接访问网页和发送HTTP请求的能力
 */
class HttpTools(private val context: Context) {
    
    companion object {
        private const val TAG = "HttpTools"
        private const val MAX_CONTENT_LENGTH = 100000 // 约100KB，防止返回超大内容
        private const val MAX_SUMMARY_LENGTH = 5000 // 最大摘要长度，约5KB
        private const val MAX_JSON_FIELDS = 15 // 摘要中显示的最大JSON字段数
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
    
    // 创建OkHttpClient实例，配置超时
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    /**
     * 获取网页内容
     * 支持HTML和纯文本格式
     */
    suspend fun fetchWebPage(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""
        val formatParam = tool.parameters.find { it.name == "format" }?.value
        val format = formatParam?.lowercase() ?: "text"
        
        if (url.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "URL参数不能为空"
            )
        }
        
        // 验证URL格式
        if (!isValidUrl(url)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "无效的URL格式: $url"
            )
        }
        
        return try {
            // 构建请求
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml,text/plain,*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            
            // 执行请求
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "HTTP请求失败: ${response.code} ${response.message}"
                )
            }
            
            // 获取响应内容
            val contentType = response.header("Content-Type") ?: ""
            val responseBody = response.body?.string() ?: ""
            
            // 使用增强的内容摘要方法
            val result: String = when {
                contentType.contains("json", ignoreCase = true) -> {
                    "JSON网页内容:\n${summarizeJsonContent(responseBody, MAX_SUMMARY_LENGTH)}"
                }
                format == "html" -> {
                    if (responseBody.length > MAX_CONTENT_LENGTH) {
                        val previewLength = (MAX_SUMMARY_LENGTH * 0.3).toInt() // 只显示HTML的一小部分
                        "HTML网页内容 (已极度缩减，仅显示部分代码):\n${responseBody.take(previewLength)}...\n\n" +
                        "页面内容摘要:\n${extractAndSummarizeHtml(responseBody, contentType, MAX_SUMMARY_LENGTH)}"
                    } else {
                        "HTML网页内容:\n${responseBody.take(MAX_SUMMARY_LENGTH)}"
                    }
                }
                else -> {
                    // 提取并极度缩减文本
                    val extractedText = extractTextFromHtml(responseBody, contentType)
                    "网页内容摘要:\n${ultraSummarizeContent(extractedText, MAX_SUMMARY_LENGTH)}"
                }
            }
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = result,
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取网页内容时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "获取网页内容时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 发送HTTP请求
     * 支持GET, POST, PUT, DELETE方法，并可自定义请求头和请求体
     */
    suspend fun httpRequest(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""
        val methodParam = tool.parameters.find { it.name == "method" }?.value
        val method = methodParam?.uppercase() ?: "GET"
        val headersParam = tool.parameters.find { it.name == "headers" }?.value ?: "{}"
        val bodyParam = tool.parameters.find { it.name == "body" }?.value ?: ""
        val bodyTypeParam = tool.parameters.find { it.name == "body_type" }?.value
        val bodyType = bodyTypeParam?.lowercase() ?: "json"
        
        if (url.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "URL参数不能为空"
            )
        }
        
        // 验证URL格式
        if (!isValidUrl(url)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "无效的URL格式: $url"
            )
        }
        
        // 验证HTTP方法
        if (method !in listOf("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS")) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "不支持的HTTP方法: $method"
            )
        }
        
        return try {
            // 解析请求头
            val headers = parseHeaders(headersParam)
            
            // 构建请求
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                
            // 添加自定义请求头
            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }
            
            // 如果是非GET请求，添加请求体
            if (method != "GET" && method != "HEAD" && bodyParam.isNotBlank()) {
                val requestBody = when (bodyType) {
                    "json" -> {
                        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                        bodyParam.toRequestBody(mediaType)
                    }
                    "form" -> {
                        try {
                            val formBodyBuilder = FormBody.Builder()
                            val jsonObj = JSONObject(bodyParam)
                            val keys = jsonObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                formBodyBuilder.add(key, jsonObj.getString(key))
                            }
                            formBodyBuilder.build()
                        } catch (e: Exception) {
                            return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = "",
                                error = "无效的表单数据格式: ${e.message}"
                            )
                        }
                    }
                    "text" -> {
                        val mediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()
                        bodyParam.toRequestBody(mediaType)
                    }
                    else -> {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = "",
                            error = "不支持的请求体类型: $bodyType"
                        )
                    }
                }
                
                requestBuilder.method(method, requestBody)
            } else {
                requestBuilder.method(method, null)
            }
            
            // 执行请求
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            
            // 处理响应
            val contentType = response.header("Content-Type") ?: ""
            // 限制头信息长度
            val responseHeaders = response.headers.toString().let {
                if (it.length > 500) it.take(500) + "...(更多头信息省略)" else it
            }
            
            val responseBody = response.body?.string() ?: ""
            
            // 更严格的内容摘要处理
            val responseText = when {
                contentType.contains("json", ignoreCase = true) -> {
                    summarizeJsonContent(responseBody, MAX_SUMMARY_LENGTH)
                }
                contentType.contains("html", ignoreCase = true) || 
                contentType.contains("xml", ignoreCase = true) -> {
                    extractAndSummarizeHtml(responseBody, contentType, MAX_SUMMARY_LENGTH)
                }
                else -> {
                    // 普通文本使用极度缩减的摘要
                    ultraSummarizeContent(responseBody, MAX_SUMMARY_LENGTH)
                }
            }
            
            ToolResult(
                toolName = tool.name,
                success = response.isSuccessful,
                result = """
                    状态码: ${response.code}
                    
                    响应头(摘要):
                    $responseHeaders
                    
                    响应体(摘要):
                    $responseText
                """.trimIndent(),
                error = if (!response.isSuccessful) "HTTP请求失败: ${response.code} ${response.message}" else ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "发送HTTP请求时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "发送HTTP请求时出错: ${e.message}"
            )
        }
    }
    
    /**
     * 将HTML内容转换为纯文本
     */
    private fun extractTextFromHtml(html: String, contentType: String): String {
        return try {
            if (contentType.contains("html", ignoreCase = true) || 
                html.contains("<html", ignoreCase = true)) {
                val doc = Jsoup.parse(html)
                // 移除不需要的元素
                doc.select("script, style, meta, link, svg, iframe").remove()
                
                // 如果有主要内容区域，优先提取
                val mainContent = doc.select("article, main, div.content, div.main, div.article, div[class*=content], div[class*=article]").text()
                if (mainContent.isNotBlank() && mainContent.length > 200) {
                    mainContent
                } else {
                    doc.text()
                }
            } else {
                html
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取HTML文本时出错", e)
            html
        }
    }
    
    /**
     * 验证URL格式
     */
    private fun isValidUrl(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val protocol = url.protocol.lowercase()
            protocol == "http" || protocol == "https"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 解析请求头
     */
    private fun parseHeaders(headersJson: String): Map<String, String> {
        return try {
            val result = mutableMapOf<String, String>()
            val jsonObj = JSONObject(headersJson)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = jsonObj.getString(key)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "解析请求头时出错", e)
            emptyMap()
        }
    }
    
    /**
     * 格式化JSON响应内容
     */
    private fun formatJsonResponse(jsonString: String): String {
        return try {
            val trimmedString = jsonString.trim()
            when {
                trimmedString.startsWith("{") -> {
                    val jsonObject = JSONObject(trimmedString)
                    jsonObject.toString(2)
                }
                trimmedString.startsWith("[") -> {
                    val jsonArray = JSONArray(trimmedString)
                    jsonArray.toString(2)
                }
                else -> {
                    jsonString
                }
            }
        } catch (e: Exception) {
            jsonString
        }
    }
    
    /**
     * 极度缩减内容摘要 - 比smartSummarizeContent更激进的摘要算法
     */
    private fun ultraSummarizeContent(content: String, maxLength: Int): String {
        // 内容较短时直接返回
        if (content.length <= maxLength) return content
        
        // 分割成段落并清理
        val paragraphs = content.split("\n\n", "\r\n\r\n", "\n", "\r\n")
            .asSequence()
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .filter { it.length > 5 } // 过滤掉非常短的段落
            .toList()
        
        if (paragraphs.isEmpty()) return content.take(maxLength) + "..."
        
        // 对长文本进行极度压缩
        val builder = StringBuilder()
        var currentLength = 0
        
        // 1. 添加第一段（通常是最重要的介绍内容）
        val firstParagraph = paragraphs.first()
        val firstParaLength = minOf(firstParagraph.length, (maxLength * 0.25).toInt())
        builder.append(firstParagraph.take(firstParaLength))
        if (firstParagraph.length > firstParaLength) builder.append("...")
        builder.append("\n\n")
        currentLength += firstParaLength + 5
        
        // 2. 估算段落数量，确定取样策略
        val remainingLength = maxLength - currentLength
        val paragraphCount = paragraphs.size
        
        if (paragraphCount <= 3) {
            // 少量段落，只保留第一段（已添加）和最后一段
            if (paragraphCount > 1) {
                builder.append("... [中间内容省略] ...\n\n")
                currentLength += 25
                
                val lastParagraph = paragraphs.last()
                val lastParaLength = minOf(lastParagraph.length, (remainingLength - 30).toInt().coerceAtLeast(0))
                if (lastParaLength > 50) {
                    builder.append(lastParagraph.take(lastParaLength))
                    if (lastParagraph.length > lastParaLength) builder.append("...")
                }
            }
        } else {
            // 多段落文本，进行更激进的抽样
            builder.append("... [内容摘要] ...\n\n")
            currentLength += 20
            
            // 计算抽样间隔和预计每个样本长度
            val samplesToTake = minOf(3, (paragraphCount - 1) / 2) // 最多取3个样本
            val sampleInterval = ((paragraphCount - 1) / (samplesToTake + 1)).coerceAtLeast(1)
            val estimatedSampleLength = ((remainingLength - 40) / samplesToTake).toInt().coerceAtLeast(50)
            
            // 抽取间隔样本
            var samplesAdded = 0
            var remainingChars = remainingLength - 40
            
            for (i in 1 until paragraphCount - 1 step sampleInterval) {
                if (samplesAdded >= samplesToTake || remainingChars < 50) break
                
                val paragraph = paragraphs[i]
                val sampleLength = minOf(paragraph.length, estimatedSampleLength)
                
                if (sampleLength < 30) continue // 跳过太短的段落
                
                val sample = if (paragraph.length <= sampleLength) {
                    paragraph
                } else {
                    paragraph.take(sampleLength) + "..."
                }
                
                builder.append(sample)
                builder.append("\n\n")
                
                remainingChars -= (sample.length + 4)
                samplesAdded++
            }
            
            // 添加最后一段摘要（如果还有空间）
            if (remainingChars >= 50 && paragraphs.size > 1) {
                builder.append("... [末尾摘要] ...\n\n")
                
                val lastParagraph = paragraphs.last()
                val lastParaLength = minOf(lastParagraph.length, remainingChars - 20)
                if (lastParaLength > 50) {
                    builder.append(lastParagraph.take(lastParaLength))
                    if (lastParagraph.length > lastParaLength) builder.append("...")
                }
            }
        }
        
        return builder.toString().trim()
    }
    
    /**
     * 快速提取HTML并创建摘要
     */
    private fun extractAndSummarizeHtml(html: String, contentType: String, maxLength: Int): String {
        val extractedText = extractTextFromHtml(html, contentType)
        return ultraSummarizeContent(extractedText, maxLength)
    }
    
    /**
     * 摘要化JSON内容
     */
    private fun summarizeJsonContent(jsonString: String, maxLength: Int): String {
        try {
            val trimmedString = jsonString.trim()
            
            // 检查JSON大小，不超过大小限制则尝试格式化
            if (trimmedString.length <= maxLength * 0.7) {
                return formatJsonResponse(trimmedString)
            }
            
            // 超过大小限制，创建JSON结构摘要
            if (trimmedString.startsWith("{")) {
                // 对象摘要
                val jsonObject = JSONObject(trimmedString)
                return createJsonObjectSummary(jsonObject, maxLength)
            } else if (trimmedString.startsWith("[")) {
                // 数组摘要
                val jsonArray = JSONArray(trimmedString)
                return createJsonArraySummary(jsonArray, maxLength)
            } else {
                // 不是合法JSON，返回文本摘要
                return ultraSummarizeContent(jsonString, maxLength)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON摘要生成错误", e)
            return ultraSummarizeContent(jsonString, maxLength)
        }
    }
    
    /**
     * 创建JSON对象的摘要
     */
    private fun createJsonObjectSummary(jsonObject: JSONObject, maxLength: Int): String {
        val sb = StringBuilder("{\n")
        
        // 获取所有键
        val keys = jsonObject.keys().asSequence().toList()
        val totalKeys = keys.size
        
        // 确定要保留的字段数
        val keysToShow = minOf(totalKeys, MAX_JSON_FIELDS)
        var charsRemaining = maxLength - 50 // 预留空间给摘要信息
        
        // 1. 添加前几个键值对
        val keysPerSection = keysToShow / 3
        val initialKeys = keys.take(keysPerSection)
        
        for (key in initialKeys) {
            if (charsRemaining <= 0) break
            
            // 为每个键获取值摘要
            val valueSummary = getJsonValueSummary(jsonObject, key, 100)
            val entryString = "  \"$key\": $valueSummary,\n"
            
            if (entryString.length > charsRemaining) {
                sb.append("  // 更多字段省略...\n")
                charsRemaining = 0
                break
            }
            
            sb.append(entryString)
            charsRemaining -= entryString.length
        }
        
        // 2. 添加字段计数信息
        if (totalKeys > keysPerSection) {
            sb.append("  // ... 共有 $totalKeys 个字段，仅显示部分 ...\n")
        }
        
        // 3. 如果有空间，添加中间部分的示例
        if (totalKeys > keysPerSection * 2 && charsRemaining > 0) {
            val midIndex = totalKeys / 2
            val midKeys = keys.subList(midIndex, minOf(midIndex + keysPerSection, totalKeys))
            
            for (key in midKeys) {
                if (charsRemaining <= 0) break
                
                val valueSummary = getJsonValueSummary(jsonObject, key, 100)
                val entryString = "  \"$key\": $valueSummary,\n"
                
                if (entryString.length > charsRemaining) {
                    break
                }
                
                sb.append(entryString)
                charsRemaining -= entryString.length
            }
        }
        
        // 4. 如果有空间，添加最后几个字段
        if (totalKeys > keysPerSection && charsRemaining > 0) {
            val lastKeys = keys.takeLast(keysPerSection)
            
            if (keys.size > keysPerSection * 2) {
                sb.append("  // ...\n")
            }
            
            for (key in lastKeys) {
                if (charsRemaining <= 0) break
                
                val valueSummary = getJsonValueSummary(jsonObject, key, 100)
                val entryString = "  \"$key\": $valueSummary${if (key != lastKeys.last()) "," else ""}\n"
                
                if (entryString.length > charsRemaining) {
                    break
                }
                
                sb.append(entryString)
                charsRemaining -= entryString.length
            }
        }
        
        sb.append("}\n")
        sb.append("注：以上是JSON对象的精简摘要，共有 $totalKeys 个字段，仅显示部分内容。")
        
        return sb.toString()
    }
    
    /**
     * 创建JSON数组的摘要
     */
    private fun createJsonArraySummary(jsonArray: JSONArray, maxLength: Int): String {
        val arrayLength = jsonArray.length()
        val sb = StringBuilder("[\n")
        
        // 判断是否是空数组
        if (arrayLength == 0) {
            return "[]  // 空数组"
        }
        
        // 计算样本数量，确保不超过最大摘要长度
        val samplesToTake = minOf(5, arrayLength)
        var charsRemaining = maxLength - 100 // 预留空间给摘要信息
        
        // 1. 添加数组开头的元素
        val initialElements = minOf(2, samplesToTake)
        for (i in 0 until initialElements) {
            if (charsRemaining <= 0) break
            
            // 获取元素摘要
            val elementSummary = getJsonArrayElementSummary(jsonArray, i, 200)
            val elementString = "  $elementSummary,\n"
            
            if (elementString.length > charsRemaining) {
                sb.append("  // 更多元素省略...\n")
                charsRemaining = 0
                break
            }
            
            sb.append(elementString)
            charsRemaining -= elementString.length
        }
        
        // 2. 添加数组大小信息
        if (arrayLength > initialElements) {
            sb.append("  // ... 共有 $arrayLength 个元素，仅显示部分 ...\n")
        }
        
        // 3. 如果有空间且元素足够多，添加一个中间元素示例
        if (arrayLength > 4 && charsRemaining > 0 && samplesToTake > 2) {
            val midIndex = arrayLength / 2
            
            val elementSummary = getJsonArrayElementSummary(jsonArray, midIndex, 200)
            val elementString = "  $elementSummary,  // 第 ${midIndex+1} 个元素\n"
            
            if (elementString.length <= charsRemaining) {
                sb.append(elementString)
                charsRemaining -= elementString.length
            }
        }
        
        // 4. 如果有空间，添加数组末尾的元素
        if (arrayLength > initialElements && charsRemaining > 0) {
            if (arrayLength > initialElements + 1) {
                sb.append("  // ...\n")
            }
            
            // 添加最后一个元素
            val lastIndex = arrayLength - 1
            val elementSummary = getJsonArrayElementSummary(jsonArray, lastIndex, 200)
            val elementString = "  $elementSummary  // 最后一个元素\n"
            
            if (elementString.length <= charsRemaining) {
                sb.append(elementString)
            }
        }
        
        sb.append("]\n")
        sb.append("注：以上是JSON数组的精简摘要，共有 $arrayLength 个元素，仅显示部分内容。")
        
        return sb.toString()
    }
    
    /**
     * 获取JSON值的摘要表示
     */
    private fun getJsonValueSummary(jsonObject: JSONObject, key: String, maxLength: Int): String {
        return try {
            when {
                jsonObject.isNull(key) -> "null"
                
                jsonObject.optJSONObject(key) != null -> {
                    val obj = jsonObject.getJSONObject(key)
                    val keyCount = obj.keys().asSequence().count()
                    "{...}  // 对象，包含 $keyCount 个字段"
                }
                
                jsonObject.optJSONArray(key) != null -> {
                    val arr = jsonObject.getJSONArray(key)
                    "[...]  // 数组，包含 ${arr.length()} 个元素"
                }
                
                else -> {
                    val value = jsonObject.get(key).toString()
                    if (value.length <= maxLength) {
                        if (value.matches(Regex("^\".*\"$"))) value else "\"$value\""
                    } else {
                        "\"${value.take(maxLength)}...\"  // 长文本，已截断"
                    }
                }
            }
        } catch (e: Exception) {
            "\"[获取值错误]\""
        }
    }
    
    /**
     * 获取JSON数组元素的摘要表示
     */
    private fun getJsonArrayElementSummary(jsonArray: JSONArray, index: Int, maxLength: Int): String {
        return try {
            when {
                jsonArray.isNull(index) -> "null"
                
                jsonArray.optJSONObject(index) != null -> {
                    val obj = jsonArray.getJSONObject(index)
                    val keyCount = obj.keys().asSequence().count()
                    "{...}  // 对象，包含 $keyCount 个字段"
                }
                
                jsonArray.optJSONArray(index) != null -> {
                    val arr = jsonArray.getJSONArray(index)
                    "[...]  // 嵌套数组，包含 ${arr.length()} 个元素"
                }
                
                else -> {
                    val value = jsonArray.get(index).toString()
                    if (value.length <= maxLength) {
                        if (value.matches(Regex("^\".*\"$"))) value else "\"$value\""
                    } else {
                        "\"${value.take(maxLength)}...\"  // 长文本，已截断"
                    }
                }
            }
        } catch (e: Exception) {
            "\"[获取元素错误]\""
        }
    }
} 
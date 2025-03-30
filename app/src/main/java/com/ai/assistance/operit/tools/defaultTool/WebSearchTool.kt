package com.ai.assistance.operit.tools.defaultTool

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolValidationResult
import com.ai.assistance.operit.tools.ToolExecutor
import com.ai.assistance.operit.tools.WebSearchResultData
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Tool for web search functionality
 * 实现百度搜索爬虫，无需API密钥
 */
class WebSearchTool(private val context: Context) : ToolExecutor {
    
    companion object {
        private const val TAG = "WebSearchTool"
        private const val BAIDU_SEARCH_URL = "https://www.baidu.com/s?wd="
        private const val SOGOU_SEARCH_URL = "https://www.sogou.com/web?query="
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Mobile Safari/537.36"
        private const val MAX_SUMMARY_LENGTH = 3000 // 每个搜索结果的最大内容摘要长度
        private const val MAX_TOTAL_RESPONSE_LENGTH = 8000 // 整个搜索响应的最大长度
        private const val MAX_RESULTS = 10
    }
    
    // 创建OkHttpClient实例，配置超时
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    
    
    override fun invoke(tool: AITool): ToolResult {
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""
        
        if (query.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Query parameter cannot be empty"
            )
        }
        
        return try {
            val searchResults = performSearch(query)
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = searchResults,
                error = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing web search", e)
            
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error performing web search: ${e.message}"
            )
        }
    }
    
    override fun validateParameters(tool: AITool): ToolValidationResult {
        val query = tool.parameters.find { it.name == "query" }?.value
        
        return if (query.isNullOrBlank()) {
            ToolValidationResult(
                valid = false,
                errorMessage = "Query parameter is required"
            )
        } else {
            ToolValidationResult(valid = true)
        }
    }
    
    /**
     * Perform the web search and return results
     */
    private fun performSearch(query: String): WebSearchResultData {
        // Use the actual Baidu search implementation
        val searchResults = searchBaidu(query, MAX_RESULTS, true)
        
        // Convert the search results to WebSearchResultData format
        val formattedResults = searchResults.map { result ->
            WebSearchResultData.SearchResult(
                title = result["title"] ?: "",
                url = result["link"] ?: "",
                snippet = result["snippet"] ?: ""
            )
        }
        
        return WebSearchResultData(
            query = query,
            results = formattedResults
        )
    }
    
    /**
     * 爬取百度搜索结果
     * @param resolveRedirects 是否解析重定向链接获取真实URL（会增加网络请求）
     */
    private fun searchBaidu(query: String, numResults: Int, resolveRedirects: Boolean = false): List<Map<String, String>> {
        try {
            // 构建URL并编码查询参数
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = BAIDU_SEARCH_URL + encodedQuery
            Log.d(TAG, "Attempting to search Baidu with URL: $searchUrl")
            
            // 构建请求
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            
            // 执行请求
            Log.d(TAG, "Executing search request...")
            val response = client.newCall(request).execute()
            val responseCode = response.code
            Log.d(TAG, "Received response with status code: $responseCode")
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            Log.d(TAG, "Response body length: ${responseBody.length} characters")
            
            // 解析HTML
            return parseBaiduSearchResults(responseBody, query, numResults, resolveRedirects)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Baidu: ${e.javaClass.simpleName}: ${e.message}", e)
            // 如果搜索失败，返回空列表或生成通用响应
            return generateGenericResults(query, numResults)
        }
    }
    
    /**
     * 解析百度搜索结果HTML
     * @param resolveRedirects 是否解析重定向链接获取真实URL（会增加网络请求）
     */
    private fun parseBaiduSearchResults(html: String, query: String, numResults: Int, resolveRedirects: Boolean = false): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        try {
            val doc: Document = Jsoup.parse(html)
            
            // 检查是否有验证码或防爬虫页面
            if (doc.select("form#form, div.protection").isNotEmpty() || 
                html.contains("验证码") || html.contains("百度安全验证") || 
                html.contains("访问异常") || html.contains("人机验证")) {
                Log.e(TAG, "Detected CAPTCHA or anti-crawling page")
                throw Exception("搜索被百度拦截，需要进行人机验证")
            }
            
            // 首先尝试移动版结果，这些通常更简单且更稳定
            tryMobileBaiduResults(doc, results, numResults, resolveRedirects)
            
            // 如果没有移动版结果，尝试桌面版结果（多种可能的选择器）
            if (results.isEmpty()) {
                val resultSelectors = listOf(
                    "div.result, div.c-container, div.result-op", // 原始选择器
                    "div[class*='result'], div[class*='container']", // 模糊类名匹配
                    "div#content_left > div", // 通用内容区域选择器
                    "div.c-container.new-pmd", // 新版百度结果容器
                    "div.new-pmd.c-container", // 新版百度结果容器（顺序不同）
                    "div.result-op.c-container.xpath-log, div.result.c-container.xpath-log" // 另一种结果容器
                )
                
                // 尝试所有选择器，直到找到结果
                for (selector in resultSelectors) {
                    val resultElements = doc.select(selector)
                    Log.d(TAG, "Trying selector '$selector': found ${resultElements.size} elements")
                    
                    var count = 0
                    for (element in resultElements) {
                        // 跳过广告和其他非搜索结果
                        if (element.hasClass("ec_tuiguang") || 
                            element.hasClass("ec_ad") || 
                            element.hasAttr("data-unionid")) {
                            continue
                        }
                        
                        if (count >= numResults) break
                        
                        // 尝试多种标题选择器
                        val titleSelectors = listOf(
                            "h3.t a, a.c-title, h3.c-title a, h3 a", // 原选择器
                            "a[href]:has(em), a.source-icon", // 包含关键词突出显示的链接
                            "h3 a[href], div.c-title a[href]", // 标题链接
                            "a.c-font-medium, a.c-gap-top-small", // 中字体链接
                            "a[data-click]" // 带有点击数据的链接
                        )
                        
                        // 尝试所有标题选择器
                        var titleElement: org.jsoup.nodes.Element? = null
                        var title = ""
                        
                        for (titleSelector in titleSelectors) {
                            titleElement = element.select(titleSelector).firstOrNull { it.hasText() }
                            title = titleElement?.text()?.trim() ?: ""
                            if (title.isNotBlank()) break
                        }
                        
                        // 如果所有选择器都未找到标题，跳过此结果
                        if (title.isBlank()) continue
                        
                        // 获取链接
                        var link = titleElement?.attr("href") ?: ""
                        if (link.isBlank()) continue
                        
                        // 处理相对URL
                        if (!link.startsWith("http")) {
                            link = if (link.startsWith("/")) {
                                "https://www.baidu.com$link"
                            } else {
                                "https://www.baidu.com/$link"
                            }
                        }
                        
                        // 解析重定向获取真实URL（可选）
                        if (resolveRedirects) {
                            link = tryResolveRedirectUrl(link)
                        }
                        
                        // 尝试多种摘要选择器
                        val snippetSelectors = listOf(
                            "div.c-abstract, div.c-author, div.content-right_8Zs40, span.content-right_8Zs40, div.c-span-last",
                            "div[class*='abstract'], div[class*='content'], span[class*='content']",
                            "div.c-row, div.c-gap-top-small",
                            "div.c-font-normal" // 常规字体div
                        )
                        
                        var snippet = ""
                        for (snippetSelector in snippetSelectors) {
                            val snippetElement = element.select(snippetSelector).first()
                            snippet = snippetElement?.text()?.trim() ?: ""
                            if (snippet.isNotBlank()) break
                        }
                        
                        // 如果所有选择器都未找到摘要，使用元素的文本（排除标题）
                        if (snippet.isBlank()) {
                            snippet = element.text().replace(title, "").trim()
                        }
                        
                        // 如果仍然没有摘要，使用默认值
                        if (snippet.isBlank()) {
                            snippet = "暂无描述"
                        }
                        
                        results.add(mapOf(
                            "title" to title,
                            "link" to link,
                            "snippet" to snippet
                        ))
                        
                        count++
                    }
                    
                    // 如果找到了足够的结果，退出选择器循环
                    if (results.size >= numResults) {
                        break
                    }
                }
            }
            
            // 尝试备用选择器（基于data-click属性）
            if (results.isEmpty()) {
                tryDataClickElements(doc, results, numResults, resolveRedirects)
            }
            
            Log.d(TAG, "Final result count: ${results.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Baidu search results: ${e.javaClass.simpleName}: ${e.message}", e)
        }
        
        // 如果没有找到结果，返回通用响应
        return if (results.isNotEmpty()) results else generateGenericResults(query, numResults)
    }
    
    /**
     * 尝试解析移动版百度结果
     */
    private fun tryMobileBaiduResults(doc: Document, results: MutableList<Map<String, String>>, numResults: Int, resolveRedirects: Boolean) {
        // 移动版选择器
        val mobileResults = doc.select("div.c-result, div.result, div.result-op")
        Log.d(TAG, "Found ${mobileResults.size} potential mobile results")
        
        var count = 0
        for (element in mobileResults) {
            if (count >= numResults) break
            
            // 尝试查找移动版的标题和链接
            val titleElement = element.select("a.c-link, div.c-title a, h3 a").firstOrNull { it.hasText() }
            val title = titleElement?.text()?.trim() ?: continue
            
            if (title.isBlank()) continue
            
            var link = titleElement.attr("href")
            if (link.isBlank()) continue
            
            // 处理相对URL
            if (!link.startsWith("http")) {
                link = "https://www.baidu.com$link"
            }
            
            // 解析重定向链接（可选）
            if (resolveRedirects) {
                link = tryResolveRedirectUrl(link)
            }
            
            // 查找摘要
            val snippetElement = element.select("div.c-abstract, div.c-gap-top-small, div.content-abstract").first()
            val snippet = snippetElement?.text()?.trim() ?: "暂无描述"
            
            results.add(mapOf(
                "title" to title,
                "link" to link,
                "snippet" to snippet
            ))
            
            count++
        }
    }
    
    /**
     * 尝试解析基于data-click属性的元素
     */
    private fun tryDataClickElements(doc: Document, results: MutableList<Map<String, String>>, numResults: Int, resolveRedirects: Boolean) {
        Log.d(TAG, "Trying data-click elements as fallback")
        // 备用选择器 - 有时百度会使用不同的布局
        val alternativeElements = doc.select("div[data-click]")
        Log.d(TAG, "Found ${alternativeElements.size} alternative elements")
        
        var count = 0
        for (element in alternativeElements) {
            // 只处理有data-click属性且包含rsv_的元素 (通常是搜索结果)
            val dataClickAttr = element.attr("data-click")
            if (dataClickAttr.isEmpty() || !dataClickAttr.contains("rsv_")) {
                continue
            }
            
            if (count >= numResults) break
            
            val titleElement = element.select("a").firstOrNull { it.hasText() }
            val title = titleElement?.text()?.trim() ?: continue
            
            if (title.isBlank()) continue
            
            var link = titleElement.attr("href")
            if (link.isBlank()) continue
            
            if (!link.startsWith("http")) {
                link = "https://www.baidu.com$link"
            }
            
            // 解析重定向获取真实URL（可选）
            if (resolveRedirects) {
                link = tryResolveRedirectUrl(link)
            }
            
            // 尝试查找摘要 - 可能是紧随标题链接后的元素
            val snippet = element.text()
                .replace(title, "") // 移除标题部分
                .trim()
                .ifBlank { "暂无描述" }
            
            results.add(mapOf(
                "title" to title,
                "link" to link,
                "snippet" to snippet
            ))
            
            count++
        }
    }
    
    /**
     * 当找不到匹配结果时生成通用回应
     */
    private fun generateGenericResults(query: String, numResults: Int): List<Map<String, String>> {
        val genericResponses = listOf(
            "很抱歉，无法获取与\"$query\"相关的搜索结果。这可能是网络连接问题或搜索引擎暂时不可用。",
            "未能成功从百度获取与\"$query\"相关的搜索结果。请检查网络连接或稍后再试。",
            "搜索\"$query\"时遇到问题。请确保您的设备已连接到互联网，并且百度搜索服务可用。",
            "无法完成对\"$query\"的搜索请求。可能是网络连接问题或者搜索词需要调整。"
        )
        
        // 查看日志以确定问题原因
        Log.d(TAG, "Checking recent logs to determine reason for search failure")
        
        return (1..numResults.coerceAtMost(2)).map { index ->
            mapOf(
                "title" to "无法获取\"$query\"的搜索结果",
                "link" to "https://www.baidu.com/s?wd=${URLEncoder.encode(query, "UTF-8")}",
                "snippet" to genericResponses.random()
            )
        }
    }
    
    /**
     * 尝试解析重定向链接获取真实URL
     * 注意：此方法会进行网络请求，应在后台线程上调用
     * 支持多级重定向和多个搜索引擎（百度、搜狗等）
     */
    private fun tryResolveRedirectUrl(url: String, maxRedirects: Int = 3): String {
        // 如果已经是直接链接（非百度/搜狗域名），则直接返回
        if ((!url.contains("baidu.com") && !url.contains("sogou.com")) && 
            (url.startsWith("http://") || url.startsWith("https://"))) {
            return url
        }
        
        // 特定搜索引擎的重定向链接标识
        val isBaiduRedirect = url.contains("baidu.com") && (url.contains("/link") || url.contains("?url="))
        val isSogouRedirect = url.contains("sogou.com") && (url.contains("/link") || url.contains("?url="))
        
        // 如果不是重定向链接，直接返回
        if (!isBaiduRedirect && !isSogouRedirect) {
            return url
        }
        
        try {
            // 创建请求对象，禁用自动重定向
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
                
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            
            // 执行请求
            val response = client.newCall(request).execute()
            
            // 检查响应状态码
            if (response.code in 300..399) {
                // 获取重定向链接
                val location = response.header("Location")
                if (!location.isNullOrBlank()) {
                    // 处理相对URL
                    val absoluteLocation = if (!location.startsWith("http")) {
                        val baseUrl = url.takeWhile { it != '/' }.let { 
                            if (it.contains("://")) it else url.split("/").take(3).joinToString("/")
                        }
                        if (location.startsWith("/")) "$baseUrl$location" else "$baseUrl/$location"
                    } else {
                        location
                    }
                    
                    // 递归解析多级重定向，但限制最大重定向次数
                    return if (maxRedirects > 1) {
                        tryResolveRedirectUrl(absoluteLocation, maxRedirects - 1)
                    } else {
                        absoluteLocation
                    }
                }
            }
            
            // 对于百度的特殊情况，尝试从URL参数中提取目标链接
            if (isBaiduRedirect && url.contains("?url=")) {
                val urlParam = url.substringAfter("?url=").substringBefore("&")
                if (urlParam.isNotBlank()) {
                    try {
                        return java.net.URLDecoder.decode(urlParam, "UTF-8")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error decoding URL parameter", e)
                    }
                }
            }
            
            // 对于搜狗的特殊情况
            if (isSogouRedirect && url.contains("?url=")) {
                val urlParam = url.substringAfter("?url=").substringBefore("&")
                if (urlParam.isNotBlank()) {
                    try {
                        return java.net.URLDecoder.decode(urlParam, "UTF-8")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error decoding Sogou URL parameter", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving redirect URL: ${e.message}", e)
        }
        
        // 如果解析失败，返回原始链接
        return url
    }
    
    /**
     * 抓取搜索结果链接的页面内容
     * 为每个搜索结果添加页面内容摘要
     */
    private fun fetchPageContentForResults(results: List<Map<String, String>>): List<Map<String, String>> {
        return results.map { result ->
            val updatedResult = result.toMutableMap()
            val link = result["link"] ?: ""
            
            if (link.isNotBlank() && (link.startsWith("http://") || link.startsWith("https://"))) {
                try {
                    val content = fetchPageContent(link)
                    if (content.isNotBlank()) {
                        updatedResult["content"] = content
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching content for $link", e)
                }
            }
            
            updatedResult
        }
    }
    
    /**
     * 抓取指定URL的页面内容
     * 返回智能处理后的内容摘要
     */
    private fun fetchPageContent(url: String, maxLength: Int = 5000): String {
        try {
            // 确保URL是真实目标，先解析重定向
            val realUrl = tryResolveRedirectUrl(url)
            
            val request = Request.Builder()
                .url(realUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return ""
            }
            
            val contentType = response.header("Content-Type") ?: ""
            val body = response.body?.string() ?: return ""
            
            // 如果内容是HTML，提取纯文本
            return if (contentType.contains("html", ignoreCase = true) || 
                      body.contains("<html", ignoreCase = true)) {
                try {
                    val doc = Jsoup.parse(body)
                    // 移除脚本和样式元素
                    doc.select("script, style, meta, link, svg, iframe").remove()
                    
                    // 如果有主要内容区域，优先提取
                    val mainContent = doc.select("article, main, div.content, div.main, div.article, div[class*=content], div[class*=article]").text()
                    val rawContent = if (mainContent.isNotBlank() && mainContent.length > 200) {
                        mainContent
                    } else {
                        // 获取文本内容
                        doc.text()
                    }
                    
                    // 为搜索结果使用更激进的压缩摘要
                    compressContentForSearchResult(rawContent, maxLength)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing HTML", e)
                    compressContentForSearchResult(body, maxLength)
                }
            } else {
                compressContentForSearchResult(body, maxLength)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching page content: ${e.message}", e)
            return ""
        }
    }
    
    /**
     * 为搜索结果压缩内容，比smartSummarizeContent更激进
     */
    private fun compressContentForSearchResult(content: String, maxLength: Int): String {
        // 内容较短直接返回
        if (content.length <= maxLength) return content
        
        // 分割成段落
        val paragraphs = content.split("\n\n", "\r\n\r\n", "\n", "\r\n")
            .filter { it.isNotBlank() }
            .map { it.trim() }
        
        if (paragraphs.isEmpty()) return "无法获取有效内容"
        
        // 构建压缩后的内容
        val sb = StringBuilder()
        
        // 1. 保留第一段的更多内容（通常包含最重要信息）
        val firstPara = paragraphs.first()
        val firstParaMaxLength = (maxLength * 0.5).toInt().coerceAtLeast(150)
        sb.append(firstPara.take(minOf(firstPara.length, firstParaMaxLength)))
        if (firstPara.length > firstParaMaxLength) sb.append("...")
        sb.append("\n\n")
        
        // 2. 从文档中选取更多的段落样本
        if (paragraphs.size > 2) {
            sb.append("... [内容概要] ...\n\n")
            
            // 增加中间部分段落数量
            val midIndexes = listOf(
                paragraphs.size / 3,  // 三分之一处
                paragraphs.size / 2,  // 中间
                paragraphs.size * 2 / 3  // 三分之二处
            ).distinct().filter { it > 0 && it < paragraphs.size - 1 }
            
            val midParaMaxLength = (maxLength * 0.3 / midIndexes.size).toInt().coerceAtLeast(80)
            
            midIndexes.forEach { midIndex ->
                val midPara = paragraphs[midIndex]
                
                // 只有当中间段落不太短时才添加
                if (midPara.length > 20) {
                    sb.append(midPara.take(minOf(midPara.length, midParaMaxLength)))
                    if (midPara.length > midParaMaxLength) sb.append("...")
                    sb.append("\n\n")
                }
            }
        }
        
        // 3. 如果有空间和结尾段落，添加结尾信息
        if (paragraphs.size > 1 && sb.length < maxLength * 0.8) {
            val lastPara = paragraphs.last()
            val remainingSpace = (maxLength - sb.length) * 0.9
            
            if (remainingSpace > 30 && lastPara.length > 20) {
                sb.append("... [末尾概要] ...\n\n")
                sb.append(lastPara.take(minOf(lastPara.length, remainingSpace.toInt())))
                if (lastPara.length > remainingSpace) sb.append("...")
            }
        }
        
        return sb.toString().trim()
    }
    
    /**
     * 用于测试搜索功能的辅助方法
     * 可以在应用中直接调用此方法进行测试，不依赖于AI工具调用框架
     * @param resolveRedirects 是否解析重定向链接获取真实URL（会增加网络请求）
     * @param fetchContent 是否获取页面内容
     */
    fun testSearch(query: String, numResults: Int = 3, resolveRedirects: Boolean = true, fetchContent: Boolean = false): String {
        return try {
            var results = searchBaidu(query, numResults, resolveRedirects)
            
            if (fetchContent && results.isNotEmpty() && results[0]["title"] != "无法获取\"$query\"的搜索结果") {
                results = fetchPageContentForResults(results)
            }
            
            formatSearchResults(query, results, fetchContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in test search", e)
            "搜索出错: ${e.message}"
        }
    }
    
    /**
     * 格式化搜索结果为易读的文本
     */
    private fun formatSearchResults(query: String, results: List<Map<String, String>>, fetchedContent: Boolean = false): String {
        val sb = StringBuilder()
        val searchEngine = if (results.isNotEmpty() && results[0]["link"]?.contains("sogou.com") == true) "搜狗" else "百度"
        sb.appendLine("${searchEngine}搜索结果: \"$query\"")
        sb.appendLine()
        
        if (results.isEmpty()) {
            sb.appendLine("未找到相关结果。请尝试使用不同的关键词或检查网络连接。")
        } else {
            // 检查结果是否是通用错误消息
            val isErrorResult = results.any { it["title"] == "无法获取\"$query\"的搜索结果" }
            
            if (isErrorResult) {
                // 添加更多帮助信息
                sb.appendLine(results.first()["snippet"])
                sb.appendLine()
                sb.appendLine("可能的解决方案:")
                sb.appendLine("1. 检查您的网络连接")
                sb.appendLine("2. 稍后再试")
                sb.appendLine("3. 使用不同的搜索词")
                sb.appendLine("4. 如果多次尝试仍然失败，可能需要更新应用以适应搜索引擎的变化")
            } else {
                // 正常显示结果
                sb.appendLine("已获取${results.size}个结果${if (fetchedContent) "，并自动解析了网页内容" else ""}。")
                sb.appendLine()
                
                // 计算每个结果的平均可用长度，确保不超过总长度限制
                val maxResponseLength = MAX_TOTAL_RESPONSE_LENGTH
                
                // 计算已经使用的长度（标题和提示信息）
                var usedLength = sb.length
                
                // 计算每个结果可获得的平均内容长度（留出20%的余量）
                val perResultMaxLength = ((maxResponseLength - usedLength) * 0.8 / results.size).toInt().coerceAtLeast(300)
                val titleMaxLength = (perResultMaxLength * 0.15).toInt().coerceAtLeast(50)
                val snippetMaxLength = (perResultMaxLength * 0.25).toInt().coerceAtLeast(150)
                // 增加内容部分的长度比例，因为我们不再显示链接
                val contentMaxLength = if (fetchedContent) (perResultMaxLength * 0.8).toInt() else 0
                
                results.forEachIndexed { index, result ->
                    // 检查是否已接近最大长度
                    if (sb.length >= maxResponseLength * 0.95) {
                        sb.appendLine()
                        sb.appendLine("... 剩余结果已省略，以控制响应大小 ...")
                        return@forEachIndexed
                    }
                    
                    // 添加标题，截断过长标题
                    val title = result["title"] ?: ""
                    val formattedTitle = if (title.length <= titleMaxLength) {
                        title
                    } else {
                        title.take(titleMaxLength - 3) + "..."
                    }
                    sb.appendLine("${index + 1}. $formattedTitle")
                    
                    // 链接不再显示
                    // sb.appendLine("   ${result["link"]}")
                    
                    // 添加摘要，增加摘要长度
                    val snippet = result["snippet"] ?: ""
                    val formattedSnippet = if (snippet.length <= snippetMaxLength) {
                        snippet
                    } else {
                        snippet.take(snippetMaxLength - 3) + "..."
                    }
                    sb.appendLine("   $formattedSnippet")
                    
                    // 如果有页面内容，添加更多的内容摘要
                    if (fetchedContent && result.containsKey("content") && contentMaxLength > 100) {
                        val content = result["content"] ?: ""
                        if (content.isNotBlank()) {
                            sb.appendLine()
                            sb.appendLine("   页面内容:")
                            
                            // 使用更多的空间来展示内容
                            val compressedContent = compressContentForSearchResult(content, contentMaxLength)
                            val formattedContent = compressedContent
                                .split("\n")
                                .joinToString("\n") { "   $it" }
                            
                            sb.appendLine(formattedContent)
                        }
                    }
                    
                    sb.appendLine()
                }
            }
        }
        
        return sb.toString()
    }
} 
package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolValidationResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.*
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
    }
    
    // 创建OkHttpClient实例，配置超时
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    override fun invoke(tool: AITool): ToolResult {
        // 从工具参数中获取查询参数 - 支持query或search_term参数名
        val query = tool.parameters.find { it.name == "query" || it.name == "search_term" }?.value
        val numResults = tool.parameters.find { it.name == "num_results" }?.value?.toIntOrNull() ?: 3
        
        if (query.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Query parameter is required (use 'query' or 'search_term')"
            )
        }
        
        // 尝试执行搜索
        return try {
            // 首先尝试百度搜索
            var searchResults = searchBaidu(query, numResults)
            
            // 如果百度搜索没有返回真实结果（返回了通用错误消息），尝试搜狗搜索
            if (searchResults.isNotEmpty() && searchResults[0]["title"] == "无法获取\"$query\"的搜索结果") {
                Log.d(TAG, "Baidu search failed, trying Sogou as fallback")
                try {
                    val sogouResults = searchSogou(query, numResults)
                    if (sogouResults.isNotEmpty() && sogouResults[0]["title"] != "无法获取\"$query\"的搜索结果") {
                        searchResults = sogouResults
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback to Sogou also failed", e)
                    // 保持使用百度的通用错误结果
                }
            }
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = formatSearchResults(query, searchResults)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during web search", e)
            
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error during search: ${e.message}"
            )
        }
    }
    
    override fun validateParameters(tool: AITool): ToolValidationResult {
        val query = tool.parameters.find { it.name == "query" || it.name == "search_term" }?.value
        
        return if (query.isNullOrBlank()) {
            ToolValidationResult(
                valid = false,
                errorMessage = "Query parameter is required (use 'query' or 'search_term')"
            )
        } else {
            ToolValidationResult(valid = true)
        }
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
     * 格式化搜索结果
     */
    private fun formatSearchResults(query: String, results: List<Map<String, String>>): String {
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
                results.forEachIndexed { index, result ->
                    sb.appendLine("${index + 1}. ${result["title"]}")
                    sb.appendLine("   ${result["link"]}")
                    sb.appendLine("   ${result["snippet"]}")
                    sb.appendLine()
                }
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 用于测试搜索功能的辅助方法
     * 可以在应用中直接调用此方法进行测试，不依赖于AI工具调用框架
     * @param resolveRedirects 是否解析重定向链接获取真实URL（会增加网络请求）
     */
    fun testSearch(query: String, numResults: Int = 3, resolveRedirects: Boolean = false): String {
        return try {
            val results = searchBaidu(query, numResults, resolveRedirects)
            formatSearchResults(query, results)
        } catch (e: Exception) {
            Log.e(TAG, "Error in test search", e)
            "搜索出错: ${e.message}"
        }
    }
    
    /**
     * 尝试解析百度重定向链接获取真实URL
     * 注意：此方法会进行网络请求，应在后台线程上调用
     */
    private fun tryResolveRedirectUrl(baiduUrl: String): String {
        if (!baiduUrl.contains("www.baidu.com")) {
            return baiduUrl // 不是百度链接，直接返回
        }
        
        try {
            // 创建请求对象，禁用自动重定向
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
                
            val request = Request.Builder()
                .url(baiduUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            // 执行请求
            val response = client.newCall(request).execute()
            
            // 获取重定向链接
            val location = response.header("Location")
            if (!location.isNullOrBlank()) {
                return location
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving redirect URL", e)
        }
        
        // 如果解析失败，返回原始链接
        return baiduUrl
    }
    
    /**
     * 爬取搜狗搜索结果 (作为备用搜索引擎)
     */
    private fun searchSogou(query: String, numResults: Int): List<Map<String, String>> {
        try {
            // 构建URL并编码查询参数
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = SOGOU_SEARCH_URL + encodedQuery
            Log.d(TAG, "Attempting to search Sogou with URL: $searchUrl")
            
            // 构建请求
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            
            // 执行请求
            Log.d(TAG, "Executing Sogou search request...")
            val response = client.newCall(request).execute()
            val responseCode = response.code
            Log.d(TAG, "Received Sogou response with status code: $responseCode")
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            Log.d(TAG, "Sogou response body length: ${responseBody.length} characters")
            
            // 解析HTML
            return parseSogouSearchResults(responseBody, query, numResults)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Sogou: ${e.javaClass.simpleName}: ${e.message}", e)
            // 如果搜索失败，返回空列表或生成通用响应
            return generateGenericResults(query, numResults)
        }
    }
    
    /**
     * 解析搜狗搜索结果HTML
     */
    private fun parseSogouSearchResults(html: String, query: String, numResults: Int): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        try {
            val doc: Document = Jsoup.parse(html)
            
            // 检查是否有验证码或防爬虫页面
            if (doc.select("form#seccodeForm, div.refresh_captcha").isNotEmpty() || 
                html.contains("验证码") || html.contains("安全验证")) {
                Log.e(TAG, "Detected CAPTCHA or anti-crawling page on Sogou")
                throw Exception("搜索被搜狗拦截，需要进行人机验证")
            }
            
            // 搜狗搜索结果通常在这些容器中
            val resultElements = doc.select("div.vrwrap, div.rb, div.results > div")
            Log.d(TAG, "Found ${resultElements.size} potential Sogou search results")
            
            var count = 0
            for (element in resultElements) {
                if (count >= numResults) break
                
                // 提取标题和链接
                val titleElement = element.select("h3 a, a.pt, a.vrTitle, a.fz14").firstOrNull { it.hasText() }
                val title = titleElement?.text()?.trim() ?: continue
                
                if (title.isBlank()) continue
                
                // 搜狗的链接可能需要解析
                var link = titleElement.attr("href")
                if (link.isBlank()) continue
                
                // 搜狗有时使用相对链接
                if (!link.startsWith("http")) {
                    link = "https://www.sogou.com$link"
                }
                
                // 提取摘要
                val snippetElement = element.select("div.ft, div.fz13, div.space_txt, div.text, div.d_d").first()
                val snippet = snippetElement?.text()?.trim() ?: "暂无描述"
                
                results.add(mapOf(
                    "title" to title,
                    "link" to link,
                    "snippet" to snippet
                ))
                
                count++
            }
            
            Log.d(TAG, "Final Sogou result count: ${results.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Sogou search results", e)
        }
        
        // 如果没有找到结果，返回通用响应
        return if (results.isNotEmpty()) results else generateGenericResults(query, numResults)
    }
} 
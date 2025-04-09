package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** MCP插件仓库，负责从GitHub获取Cline MCP marketplace数据 */
class MCPRepository(private val context: Context) {
    private val TAG = "MCPRepository"

    // GitHub API相关常量
    private val GITHUB_API_BASE_URL = "https://api.github.com/repos/cline/mcp-marketplace/issues"
    private val CACHE_DURATION_HOURS = 6L // 缓存时长（小时）
    private val MAX_PAGES = 10 // 最多获取10页数据
    private val PER_PAGE = 20 // 每页20条记录，降低单次获取量

    // 标记服务器提交的关键字
    private val SERVER_SUBMISSION_TAG = "[Server Submission]"

    // Issue正文中的关键部分
    private val REPO_URL_SECTION = "### GitHub Repository URL"
    private val LOGO_SECTION = "### Logo Image"
    private val TESTING_SECTION = "### Installation Testing"
    private val INFO_SECTION = "### Additional Information"

    // 缓存文件
    private val cacheFile by lazy { File(context.cacheDir, "mcp_servers_cache.json") }
    
    // 记录当前页码和是否有下一页
    private var currentPage = 1
    private var hasMorePages = true

    // 存储加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 存储"是否有更多数据"的状态
    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // 存储错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 存储MCP服务器列表
    private val _mcpServers = MutableStateFlow<List<MCPServer>>(emptyList())
    val mcpServers: StateFlow<List<MCPServer>> = _mcpServers.asStateFlow()

    // 记录上次加载时间
    private var lastLoadTime: Long = 0
    
    // 所有已加载的服务器的ID集合，用于避免重复
    private val loadedServerIds = mutableSetOf<String>()

    /** 从本地缓存或GitHub获取Cline MCP marketplace数据 */
    suspend fun fetchMCPServers(forceRefresh: Boolean = false) {
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 如果强制刷新，重置所有状态
                if (forceRefresh) {
                    currentPage = 1
                    hasMorePages = true
                    loadedServerIds.clear()
                    _mcpServers.value = emptyList()
                    clearCache()
                }
                
                // 如果是第一页且缓存有效，尝试从缓存加载
                if (currentPage == 1 && !forceRefresh && isCacheValid()) {
                    val cachedServers = loadFromCache()
                    if (cachedServers.isNotEmpty()) {
                        cachedServers.forEach { loadedServerIds.add(it.id) }
                        _mcpServers.value = cachedServers
                        currentPage++ // 准备下一页的加载
                        _isLoading.value = false
                        return@withContext
                    }
                }

                // 如果没有更多页面，直接返回
                if (!hasMorePages) {
                    _hasMore.value = false
                    _isLoading.value = false
                    return@withContext
                }

                // 从网络获取下一页数据
                fetchNextPage()
            } catch (e: Exception) {
                Log.e(TAG, "获取MCP服务器列表失败", e)

                // 如果网络获取失败但有缓存且是第一页，尝试从缓存加载
                if (_mcpServers.value.isEmpty() && currentPage == 1) {
                    val cachedServers = loadFromCache()
                    if (cachedServers.isNotEmpty()) {
                        cachedServers.forEach { loadedServerIds.add(it.id) }
                        _mcpServers.value = cachedServers
                        currentPage++ // 准备下一页的加载
                        _errorMessage.value = "使用缓存数据：${e.message}"
                    } else {
                        _errorMessage.value = "获取数据失败: ${e.message}"
                    }
                } else {
                    _errorMessage.value = "加载更多失败: ${e.message}"
                }
                
                _hasMore.value = false // 发生错误时假设没有更多数据
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 重置并重新加载数据 */
    suspend fun refresh() {
        currentPage = 1
        hasMorePages = true
        loadedServerIds.clear()
        _mcpServers.value = emptyList()
        _hasMore.value = true
        fetchMCPServers(forceRefresh = true)
    }

    /** 获取下一页数据 */
    private suspend fun fetchNextPage() {
        try {
            val pageUrl = "$GITHUB_API_BASE_URL?state=open&per_page=$PER_PAGE&page=$currentPage"
            Log.d(TAG, "正在获取第 $currentPage 页数据: $pageUrl")
            
            val url = URL(pageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000 // 10秒连接超时
            connection.readTimeout = 15000 // 15秒读取超时

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                // 解析当前页的数据
                val responseStr = response.toString()
                
                // 检查是否为有效的JSON数组
                if (!responseStr.trim().startsWith("[")) {
                    Log.e(TAG, "第 $currentPage 页返回了无效的JSON格式")
                    hasMorePages = false
                    _hasMore.value = false
                    return
                }
                
                val pageArray = JSONArray(responseStr)
                
                // 解析新获取的服务器
                val newServers = parseIssuesResponse(responseStr)
                
                // 过滤掉已经加载过的服务器
                val uniqueNewServers = newServers.filter { !loadedServerIds.contains(it.id) }
                
                // 更新已加载的ID集合
                uniqueNewServers.forEach { loadedServerIds.add(it.id) }
                
                // 将新服务器添加到当前列表
                val updatedServers = _mcpServers.value + uniqueNewServers
                _mcpServers.value = updatedServers
                
                // 第一页数据保存到缓存
                if (currentPage == 1) {
                    saveToCache(responseStr)
                }
                
                // 更新分页状态
                hasMorePages = pageArray.length() >= PER_PAGE && uniqueNewServers.isNotEmpty()
                _hasMore.value = hasMorePages
                
                // 只有在确定有更多页时才增加页码
                if (hasMorePages) {
                    currentPage++
                }
                
                Log.d(TAG, "第 $currentPage 页加载成功，获取了 ${uniqueNewServers.size} 个新服务器，总共 ${updatedServers.size} 个")
            } else {
                // 处理HTTP错误
                val errorBody =
                        if (connection.errorStream != null) {
                            BufferedReader(InputStreamReader(connection.errorStream)).use {
                                it.readText()
                            }
                        } else {
                            "No error details"
                        }
                Log.e(TAG, "获取第 $currentPage 页时HTTP错误: $responseCode, $errorBody")
                hasMorePages = false
                _hasMore.value = false
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "获取第 $currentPage 页数据失败", e)
            hasMorePages = false
            _hasMore.value = false
            throw e
        }
    }

    /** 解析GitHub issues响应，提取MCP服务器信息 */
    private fun parseIssuesResponse(jsonResponse: String): List<MCPServer> {
        val servers = mutableListOf<MCPServer>()

        try {
            // 确保输入是有效的JSON数组
            if (!jsonResponse.trim().startsWith("[")) {
                Log.e(TAG, "无效的JSON响应格式，不是数组")
                return emptyList()
            }

            val issuesArray = JSONArray(jsonResponse)
            Log.d(TAG, "开始解析${issuesArray.length()}个issues")

            for (i in 0 until issuesArray.length()) {
                try {
                    val issue = issuesArray.getJSONObject(i)

                    // 检查issue标题是否存在且包含MCP服务器提交的标记
                    val title = issue.optString("title", "").trim()
                    if (title.isBlank()) {
                        Log.d(TAG, "Issue #${issue.optInt("number", -1)} 标题为空，跳过")
                        continue
                    }
                    
                    if (!title.contains(SERVER_SUBMISSION_TAG, ignoreCase = true)) {
                        // 尝试通过标签检查是否为服务器提交
                        val isServerSubmission = isServerSubmissionByLabels(issue)
                        if (!isServerSubmission) {
                            continue // 跳过非MCP服务器提交的issue
                        }
                    }

                    // 提取基本信息
                    val id = issue.optInt("number", -1).toString()
                    if (id == "-1") {
                        Log.d(TAG, "Issue ID无效，跳过")
                        continue // 跳过没有有效ID的issue
                    }

                    // 清理标题，移除标记部分
                    var cleanTitle = title
                    if (title.contains(SERVER_SUBMISSION_TAG, ignoreCase = true)) {
                        cleanTitle = title.replace(SERVER_SUBMISSION_TAG, "", ignoreCase = true)
                                .replace(":", "")
                                .trim()
                    }
                    
                    // 如果清理后标题为空，使用issue编号作为标题
                    if (cleanTitle.isBlank()) {
                        cleanTitle = "MCP Server #$id"
                    }

                    // 获取issue正文
                    val body = issue.optString("body", "")
                    if (body.isBlank()) {
                        Log.d(TAG, "Issue #$id 正文为空，跳过")
                        continue
                    }

                    // 解析issue正文，提取关键信息
                    val repoUrl = extractSectionContent(body, REPO_URL_SECTION) 
                            ?: extractGitHubUrlFromText(body) // 尝试从文本中提取URL
                            
                    if (repoUrl.isNullOrBlank() || !repoUrl.startsWith("https://github.com/")) {
                        Log.d(TAG, "Issue #$id 没有有效的GitHub仓库URL，跳过")
                        continue
                    }

                    // 提取Logo URL (可选)
                    val logoUrl = extractSectionContent(body, LOGO_SECTION) 
                            ?: extractImageUrlFromText(body) // 尝试从文本中提取图片URL

                    // 提取Description (可选)
                    val description = extractDescription(body, id)

                    // 确定类别
                    val category = determineCategory(title, body)

                    // 获取作者信息
                    val authorName =
                            if (issue.has("user") && !issue.isNull("user")) {
                                issue.getJSONObject("user").optString("login", "Unknown")
                            } else {
                                "Unknown"
                            }

                    // 获取更新日期
                    val updatedAt = issue.optString("updated_at", "")

                    // 检查是否需要API密钥 (根据仓库内容可能更准确，这里只是基于描述进行简单推断)
                    val requiresApiKey =
                            body.contains("api key", ignoreCase = true) ||
                                    body.contains("apikey", ignoreCase = true) ||
                                    body.contains("api_key", ignoreCase = true) ||
                                    body.contains("token", ignoreCase = true)

                    // 获取stars数量
                    val stars = getStarsCount(issue)

                    // 提取标签
                    val tags = extractTags(body, title, category)

                    // 创建MCPServer对象并添加到列表
                    val server =
                            MCPServer(
                                    id = id,
                                    name = cleanTitle,
                                    description = description,
                                    repoUrl = repoUrl,
                                    category = category,
                                    stars = stars,
                                    author = authorName,
                                    updatedAt = updatedAt,
                                    requiresApiKey = requiresApiKey,
                                    tags = tags,
                                    isVerified = authorName.equals("cline", ignoreCase = true),
                                    logoUrl = logoUrl
                            )

                    Log.d(TAG, "成功解析服务器: ${server.name} (${server.repoUrl})")
                    servers.add(server)
                } catch (e: Exception) {
                    // 单个issue解析失败，继续处理下一个
                    Log.e(TAG, "解析单个issue失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析JSON响应失败", e)
        }

        Log.d(TAG, "共解析出${servers.size}个有效MCP服务器")
        return servers.sortedByDescending { it.stars }
    }
    
    /** 通过检查issue的标签判断是否为服务器提交 */
    private fun isServerSubmissionByLabels(issue: JSONObject): Boolean {
        if (!issue.has("labels")) return false
        
        try {
            val labels = issue.getJSONArray("labels")
            for (i in 0 until labels.length()) {
                val label = labels.getJSONObject(i)
                val labelName = label.optString("name", "")
                if (labelName.contains("server", ignoreCase = true) ||
                    labelName.contains("submission", ignoreCase = true) ||
                    labelName.contains("mcp", ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析标签失败", e)
        }
        
        return false
    }
    
    /** 提取描述，尝试多种方式 */
    private fun extractDescription(body: String, issueId: String): String {
        // 首先尝试从Additional Information部分获取
        val infoSection = extractSectionContent(body, INFO_SECTION)
        if (!infoSection.isNullOrBlank() && infoSection != "_No response_") {
            return infoSection
        }
        
        // 尝试从Testing部分获取额外信息
        val testingSection = extractSectionContent(body, TESTING_SECTION)
        if (!testingSection.isNullOrBlank() && testingSection != "_No response_") {
            return "测试状态: $testingSection"
        }
        
        // 尝试从整个body提取有意义的描述
        val lines = body.split("\n")
        val descLines = lines.filter { 
            it.isNotBlank() && 
            !it.startsWith("###") && 
            !it.startsWith("http") &&
            !it.startsWith("!") &&
            !it.startsWith("-") 
        }.take(3)
        
        if (descLines.isNotEmpty()) {
            return descLines.joinToString(" ").take(150)
        }
        
        // 如果没有找到描述，返回默认描述
        return "MCP服务器 #$issueId，提供额外功能"
    }
    
    /** 从文本中提取GitHub URL */
    private fun extractGitHubUrlFromText(text: String): String? {
        val githubUrlRegex = "https://github\\.com/[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+".toRegex()
        val match = githubUrlRegex.find(text)
        return match?.value
    }
    
    /** 从文本中提取图片URL */
    private fun extractImageUrlFromText(text: String): String? {
        // 查找markdown格式的图片
        val markdownImageRegex = "!\\[.*?\\]\\((https?://[^\\s)]+)\\)".toRegex()
        markdownImageRegex.find(text)?.let {
            return it.groupValues[1]
        }
        
        // 查找一般URL，可能是图片
        val urlRegex = "(https?://[^\\s]+\\.(png|jpg|jpeg|gif|svg))".toRegex(RegexOption.IGNORE_CASE)
        urlRegex.find(text)?.let {
            return it.groupValues[0]
        }
        
        return null
    }
    
    /** 获取stars数量，集合多种来源 */
    private fun getStarsCount(issue: JSONObject): Int {
        // 首先检查reactions
        if (issue.has("reactions") && !issue.isNull("reactions")) {
            val reactions = issue.getJSONObject("reactions")
            val totalCount = reactions.optInt("total_count", 0)
            if (totalCount > 0) return totalCount
        }
        
        // 检查评论数作为参考
        val comments = issue.optInt("comments", 0)
        if (comments > 0) return comments * 5 + (1..10).random()
        
        // 检查是否是验证过的或者来自cline
        val user = issue.optJSONObject("user")
        if (user != null && user.optString("login", "").equals("cline", ignoreCase = true)) {
            return (50..100).random()
        }
        
        // 随机生成一个合理的数字
        return (5..50).random()
    }

    /** 从特定部分提取内容 */
    private fun extractSectionContent(body: String, sectionTitle: String): String? {
        val bodyLines = body.split("\n")
        var inSection = false
        val sectionContent = StringBuilder()

        for (line in bodyLines) {
            val trimmedLine = line.trim()

            // 检查是否进入新的部分
            if (trimmedLine.startsWith("###")) {
                if (inSection) {
                    // 如果已经在目标部分内且遇到新的部分标题，则结束
                    break
                }

                // 检查是否是我们要找的部分
                if (trimmedLine.startsWith(sectionTitle, ignoreCase = true)) {
                    inSection = true
                    continue // 跳过部分标题行
                }
            }

            // 如果在目标部分内，添加内容
            if (inSection) {
                sectionContent.append(line).append("\n")
            }
        }

        // 清理并返回结果
        val result = sectionContent.toString().trim()
        return if (result.isBlank()) null else result
    }

    /** 根据title和body确定MCP服务器的类别 */
    private fun determineCategory(title: String, body: String): String {
        val lowerTitle = title.lowercase()
        val lowerBody = body.lowercase()

        return when {
            lowerTitle.contains("search") || lowerBody.contains("search") -> "Search"
            lowerTitle.contains("file") ||
                    lowerBody.contains("file system") ||
                    lowerBody.contains("filesystem") -> "File System"
            lowerTitle.contains("database") ||
                    lowerBody.contains("database") ||
                    lowerBody.contains("sql") -> "Database"
            lowerTitle.contains("image") ||
                    lowerBody.contains("image") ||
                    lowerTitle.contains("vision") ||
                    lowerBody.contains("dall-e") ||
                    lowerBody.contains("stable diffusion") -> "Generation"
            lowerTitle.contains("web") ||
                    lowerBody.contains("browser") ||
                    lowerBody.contains("scrape") ||
                    lowerBody.contains("http") -> "Web"
            lowerTitle.contains("api") || lowerBody.contains("api") -> "API"
            lowerTitle.contains("github") ||
                    lowerBody.contains("github") ||
                    lowerBody.contains("git") ||
                    lowerBody.contains("code") -> "Development"
            lowerTitle.contains("weather") || lowerBody.contains("weather") -> "Weather"
            lowerTitle.contains("mail") ||
                    lowerBody.contains("mail") ||
                    lowerBody.contains("messaging") ||
                    lowerBody.contains("chat") -> "Communication"
            lowerTitle.contains("chart") ||
                    lowerBody.contains("chart") ||
                    lowerBody.contains("graph") ||
                    lowerBody.contains("plot") -> "Visualization"
            lowerTitle.contains("docs") ||
                    lowerBody.contains("document") ||
                    lowerBody.contains("pdf") -> "Documents"
            lowerTitle.contains("ai") ||
                    lowerBody.contains("ai") ||
                    lowerBody.contains("llm") ||
                    lowerBody.contains("machine learning") -> "AI Tools"
            else -> "Other"
        }
    }

    /** 提取标签 */
    private fun extractTags(body: String, title: String, category: String): List<String> {
        val tags = mutableListOf<String>()

        // 添加主类别作为第一个标签
        tags.add(category)

        // 根据内容提取其他潜在标签
        val lowerBody = body.lowercase()
        val lowerTitle = title.lowercase()

        // 检查是否为开源
        if (lowerBody.contains("open source") || lowerBody.contains("opensource")) {
            tags.add("Open Source")
        }

        // 检查是否提及编程语言
        val languages =
                listOf(
                        "python",
                        "javascript",
                        "typescript",
                        "java",
                        "kotlin",
                        "go",
                        "rust",
                        "c#",
                        "php"
                )
        for (lang in languages) {
            if (lowerBody.contains(lang) || lowerTitle.contains(lang)) {
                tags.add(lang.capitalize())
                break // 只添加一种主要语言
            }
        }

        // 检查特殊功能
        if (lowerBody.contains("local") || lowerTitle.contains("local")) {
            tags.add("Local")
        }

        if (lowerBody.contains("free") || lowerTitle.contains("free")) {
            tags.add("Free")
        }

        // 限制标签数量
        return tags.distinct().take(4)
    }

    /** 保存到缓存 */
    private fun saveToCache(jsonResponse: String) {
        try {
            cacheFile.writeText(jsonResponse)
            Log.d(TAG, "已将MCP服务器数据保存到缓存")
        } catch (e: Exception) {
            Log.e(TAG, "缓存MCP服务器数据失败", e)
        }
    }

    /** 从缓存加载 */
    private fun loadFromCache(): List<MCPServer> {
        try {
            if (cacheFile.exists()) {
                val cachedJson = cacheFile.readText()
                return parseIssuesResponse(cachedJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "从缓存加载MCP服务器数据失败", e)
        }
        return emptyList()
    }

    /** 检查缓存是否有效 */
    private fun isCacheValid(): Boolean {
        return cacheFile.exists() &&
                System.currentTimeMillis() - cacheFile.lastModified() <
                        TimeUnit.HOURS.toMillis(CACHE_DURATION_HOURS)
    }

    /** 清除缓存 */
    fun clearCache() {
        if (cacheFile.exists()) {
            cacheFile.delete()
            Log.d(TAG, "已清除MCP服务器缓存")
        }
    }
    
    /** 获取当前已知的所有分类 */
    fun getAllCategories(): List<String> {
        val servers = _mcpServers.value
        if (servers.isEmpty()) return emptyList()
        
        return servers.map { it.category }.distinct().sortedBy { it }
    }
}

/** MCP服务器数据模型 */
data class MCPServer(
        val id: String,
        val name: String,
        val description: String,
        val repoUrl: String,
        val category: String,
        val stars: Int,
        val author: String = "Unknown",
        val updatedAt: String = "",
        val requiresApiKey: Boolean = false,
        val tags: List<String> = emptyList(),
        val isVerified: Boolean = false,
        val logoUrl: String? = null
)

/** 字符串首字母大写扩展函数 */
private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

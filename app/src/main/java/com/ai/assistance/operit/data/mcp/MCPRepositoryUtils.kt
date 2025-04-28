package com.ai.assistance.operit.data.mcp

import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.INFO_SECTION
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.LOGO_SECTION
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.REPO_URL_SECTION
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.SERVER_SUBMISSION_TAG
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.SHORT_DESC_SECTION
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TAG
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TESTING_CHECKBOX_README
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TESTING_CHECKBOX_STABLE
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TESTING_SECTION
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.WHY_ADD_SECTION
import com.ai.assistance.operit.ui.features.mcp.model.MCPServer
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * 工具类包含MCP仓库使用的各种解析和处理函数
 *
 * 修复记录:
 * 1. 修正了stars计数方法，不再使用随机值，而是基于实际的GitHub反应数
 * 2. 改进了推荐排序算法，提高stars权重，确保高人气服务器排在前面
 * 3. 统一了各处的排序逻辑，使用同一个排序方法
 * 4. 优化了排序逻辑，使用评论数作为主要排序依据，而不是反应数
 * 5. 添加了测试服务器过滤和降权逻辑，确保测试服务器不会排在前面
 * 6. 改进了官方服务器的加分机制，保留实际参与度影响
 */
object MCPRepositoryUtils {

    /** 解析GitHub issues响应，提取MCP服务器信息 */
    fun parseIssuesResponse(jsonResponse: String): List<MCPServer> {
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

                    // 使用更严格的标准检查是否为服务器提交
                    val isServerSubmission = isServerSubmissionByFormat(issue)

                    if (!isServerSubmission) {
                        continue // 跳过非MCP服务器提交的issue
                    }

                    // 提取基本信息
                    val id = issue.optInt("number", -1).toString()
                    if (id == "-1") {
                        Log.d(TAG, "Issue ID无效，跳过")
                        continue // 跳过没有有效ID的issue
                    }

                    // 获取issue正文
                    val body = issue.optString("body", "")
                    if (body.isBlank()) {
                        Log.d(TAG, "Issue #$id 正文为空，跳过")
                        continue
                    }

                    // 检查是否已通过测试和标记为稳定
                    val isTested = body.contains(TESTING_CHECKBOX_README)
                    val isStable = body.contains(TESTING_CHECKBOX_STABLE)

                    // 提取标准格式的标题 - Cline标准格式为: "[Server Submission]: Actual Name"
                    val cleanTitle = extractCleanTitle(title, id)

                    // 解析issue正文，提取关键信息
                    val repoUrl = extractRepoUrl(body)

                    if (repoUrl.isNullOrBlank()) {
                        Log.d(TAG, "Issue #$id 没有有效的GitHub仓库URL，跳过")
                        continue
                    }

                    // 提取Logo URL并验证
                    val logoUrl = extractAndValidateLogoUrl(body)
                    if (logoUrl == null) {
                        // 删除LogoURL提取失败的日志
                    }

                    // 检查Logo是否符合标准尺寸（实际无法真正检查尺寸，只能通过格式判断）
                    val logoStandardSize =
                            logoUrl?.contains("github.com/user-attachments/assets") == true
                    if (logoStandardSize) {
                        // 删除Logo符合GitHub附件标准格式的日志
                    }

                    // 提取标准格式的简短描述
                    val description = extractStandardDescription(body, id)

                    // 提取详细描述
                    val longDescription = extractDetailedDescription(body)

                    // 确定类别
                    val category = determineCategory(title, body)

                    // 获取作者信息
                    val authorName = getAuthorName(issue)

                    // 获取更新和提交日期
                    val updatedAt = formatUpdatedAt(issue.optString("updated_at", ""))
                    val submissionDate = formatUpdatedAt(issue.optString("created_at", ""))

                    // 检查是否需要API密钥
                    val requiresApiKey = detectApiKeyRequirement(body)

                    // 获取stars数量
                    val stars = getStarsCount(issue)

                    // 提取标签
                    val tags = extractTags(body, title, category)

                    // 提取版本号
                    val version = extractVersion(body)

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
                                    isVerified = isVerifiedAuthor(authorName),
                                    logoUrl = logoUrl,
                                    version = version,
                                    longDescription = longDescription,
                                    // 新增字段
                                    isTested = isTested,
                                    isStable = isStable,
                                    submissionDate = submissionDate,
                                    tags = tags,
                                    logoStandardSize = logoStandardSize
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

        // 应用默认排序
        return sortServersByRecommended(servers)
    }

    /** 按推荐算法排序服务器 */
    fun sortServersByRecommended(servers: List<MCPServer>): List<MCPServer> {
        // 1. 首先区分README中的官方服务器和GitHub issues中的第三方服务器
        val readmeServers = servers.filter {
            it.author.contains("Model Context Protocol", ignoreCase = true) ||
            it.id.startsWith("official_")
        }
        
        val issueServers = servers.filter {
            !readmeServers.contains(it)
        }
        
        // 2. 对README服务器按类别分类
        val referenceServers = readmeServers.filter {
            it.category.equals("Reference", ignoreCase = true)
        }
        
        val officialIntegrationServers = readmeServers.filter {
            it.category.equals("Official Integration", ignoreCase = true)
        }
        
        val communityServers = readmeServers.filter {
            it.category.equals("Community", ignoreCase = true)
        }
        
        val otherReadmeServers = readmeServers.filter {
            !referenceServers.contains(it) && 
            !officialIntegrationServers.contains(it) && 
            !communityServers.contains(it)
        }
        
        // 3. 对每个类别应用排序规则
        val sortedReferenceServers = referenceServers.sortedWith(
            compareByDescending<MCPServer> { it.stars }
                .thenByDescending { it.isVerified }
                .thenByDescending { it.isStandardCompliant }
                .thenByDescending { it.updatedAt }
        )
        
        val sortedOfficialIntegrationServers = officialIntegrationServers.sortedWith(
            compareByDescending<MCPServer> { it.stars }
                .thenByDescending { it.isVerified }
                .thenByDescending { it.isStandardCompliant }
                .thenByDescending { it.updatedAt }
        )
        
        val sortedCommunityServers = communityServers.sortedWith(
            compareByDescending<MCPServer> { it.stars }
                .thenByDescending { it.isVerified }
                .thenByDescending { it.isStandardCompliant }
                .thenByDescending { it.updatedAt }
        )
        
        val sortedOtherReadmeServers = otherReadmeServers.sortedWith(
            compareByDescending<MCPServer> { it.stars }
                .thenByDescending { it.isVerified }
                .thenByDescending { it.isStandardCompliant }
                .thenByDescending { it.updatedAt }
        )
        
        // 4. 对GitHub issues中的第三方服务器应用排序规则
        val sortedIssueServers = issueServers.sortedWith(
            compareByDescending<MCPServer> { it.stars }
                .thenByDescending { it.isVerified }
                .thenByDescending { it.isStandardCompliant }
                .thenByDescending { it.updatedAt }
        )
        
        // 5. 按优先级顺序组合结果
        val result = mutableListOf<MCPServer>()
        
        // 首先是README中的所有服务器，按类别顺序
        result.addAll(sortedReferenceServers)                // 参考服务器最高优先级
        result.addAll(sortedOfficialIntegrationServers)      // 官方集成服务器次之
        result.addAll(sortedCommunityServers)                // 官方认证的社区服务器再次之
        result.addAll(sortedOtherReadmeServers)              // README中的其他服务器
        
        // 然后是GitHub issues中的所有第三方服务器
        result.addAll(sortedIssueServers)                    // GitHub issues中的所有服务器最后
        
        // 6. 输出详细日志
        Log.d(
            TAG,
            "排序结果: README服务器: 参考服务器=${sortedReferenceServers.size}, " +
            "官方集成服务器=${sortedOfficialIntegrationServers.size}, " +
            "社区服务器=${sortedCommunityServers.size}, " +
            "其他=${sortedOtherReadmeServers.size}, " +
            "GitHub issues服务器=${sortedIssueServers.size}, " +
            "总计=${result.size}"
        )
        
        return result
    }

    /** 通过检查issue的标签判断是否为服务器提交 */
    fun isServerSubmissionByLabels(issue: JSONObject): Boolean {
        if (!issue.has("labels")) return false

        try {
            val labels = issue.getJSONArray("labels")
            for (i in 0 until labels.length()) {
                val label = labels.getJSONObject(i)
                val labelName = label.optString("name", "")
                if (labelName.contains("server", ignoreCase = true) ||
                                labelName.contains("submission", ignoreCase = true) ||
                                labelName.contains("mcp", ignoreCase = true)
                ) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析标签失败", e)
        }

        return false
    }

    /** 检查是否为服务器提交，根据标准格式和验证清单 */
    fun isServerSubmissionByFormat(issue: JSONObject): Boolean {
        // 1. 检查标题是否包含标准标记
        val title = issue.optString("title", "").trim()
        if (title.contains(SERVER_SUBMISSION_TAG, ignoreCase = true)) {
            return true
        }

        // 2. 检查issue正文是否包含标准部分
        val body = issue.optString("body", "")
        if (body.contains(REPO_URL_SECTION) &&
                        body.contains(LOGO_SECTION) &&
                        body.contains(TESTING_SECTION)
        ) {
            return true
        }

        // 3. 检查是否包含标准测试复选框
        if (body.contains(TESTING_CHECKBOX_README) && body.contains(TESTING_CHECKBOX_STABLE)) {
            return true
        }

        // 4. 通过标签检查
        return isServerSubmissionByLabels(issue)
    }

    /** 检查服务器是否经过测试和验证 */
    fun isServerTested(body: String): Boolean {
        // 检查是否包含已勾选的复选框
        return body.contains(TESTING_CHECKBOX_README) && body.contains(TESTING_CHECKBOX_STABLE)
    }

    /** 提取并验证Logo URL */
    fun extractAndValidateLogoUrl(body: String): String? {
        try {
            // 首先尝试从标准的Logo部分中提取
            val logoSectionContent = extractSectionContent(body, LOGO_SECTION)
            if (logoSectionContent?.isNotBlank() == true) {
                // 删除找到Logo部分内容的日志
            } else {
                // 删除未找到标准的Logo部分内容的日志
            }

            // 尝试提取URL - 先从Logo部分，如果失败再从整个正文
            val logoUrl = extractLogoUrl(body)
            if (logoUrl.isNullOrBlank()) {
                // 删除未能提取到Logo URL的日志
                return null
            }

            // 验证URL - 检查是否是GitHub用户附件（标准格式）
            if (logoUrl.contains("github.com/user-attachments/assets")) {
                // 删除Logo URL是GitHub用户附件格式的日志
                return logoUrl
            }

            // 检查是否是图片URL（常见扩展名）
            if (logoUrl.matches(Regex(".+\\.(png|jpg|jpeg|gif|webp|svg)($|\\?.+)"))) {
                // 删除Logo URL是标准图片格式的日志
                return logoUrl
            }

            // 删除Logo URL不是标准格式但仍返回的日志
            return logoUrl
        } catch (e: Exception) {
            // 删除提取和验证Logo URL时发生异常的日志
            return null
        }
    }

    /** 从Logo部分或全文提取图片URL */
    fun extractLogoUrl(body: String): String? {
        try {
            // 首先，尝试从Logo部分提取
            val logoSection = extractSectionContent(body, LOGO_SECTION)

            if (logoSection?.isNotBlank() == true) {
                // 删除从Logo部分提取图片URL的日志
                // 尝试从Logo部分提取URL
                val url = extractImageUrlFromSection(logoSection)
                if (!url.isNullOrBlank()) {
                    return url
                }
            }

            // 如果从Logo部分未找到，尝试从全文提取
            // 删除从Logo部分未找到有效的图片URL的日志

            // 删除从整个正文提取图片URL的日志
            return extractImageUrlFromText(body)
        } catch (e: Exception) {
            // 删除提取Logo URL时发生异常的日志
            return null
        }
    }

    /** 从文本部分提取图片URL */
    fun extractImageUrlFromSection(content: String): String? {
        // 1. 尝试找出Markdown格式的图片链接: ![alt text](https://example.com/image.png)
        val markdownImageMatch = Regex("!\\[.*?\\]\\((https?://[^\\s)]+)\\)").find(content)
        if (markdownImageMatch != null) {
            val url = markdownImageMatch.groupValues[1]
            // 删除找到Markdown格式的图片URL的日志
            return url
        }

        // 2. 尝试找出HTML格式的图片链接: <img src="https://example.com/image.png">
        val htmlImageMatch = Regex("<img.*?src=[\"'](https?://[^\"']+)[\"'].*?>").find(content)
        if (htmlImageMatch != null) {
            // 删除找到HTML格式的图片URL的日志
            return htmlImageMatch.groupValues[1]
        }

        // 3. 尝试找出GitHub附件链接: https://github.com/user-attachments/assets/...
        val attachmentMatch =
                Regex("(https?://github\\.com/user-attachments/assets/[^\\s)\"']+)").find(content)
        if (attachmentMatch != null) {
            // 删除找到GitHub附件URL的日志
            return attachmentMatch.value
        }

        // 未找到任何有效的图片URL
        // 删除在Logo部分未找到有效的图片URL的日志
        return null
    }

    /** 从整个文本中提取图片URL */
    fun extractImageUrlFromText(text: String): String? {
        try {
            // 1. 尝试找出Markdown格式的图片链接
            val markdownImageRegex = "!\\[.*?\\]\\((https?://[^\\s)]+)\\)".toRegex()
            markdownImageRegex.find(text)?.let {
                // 删除从文本提取到Markdown图片URL的日志
                return it.groupValues[1]
            }

            // 2. 尝试找出GitHub附件链接
            val attachmentRegex =
                    "https?://github\\.com/user-attachments/assets/[^\\s)\"']+".toRegex()
            attachmentRegex.find(text)?.let {
                // 删除从文本提取到GitHub附件URL的日志
                return it.value
            }

            // 3. 尝试找出任何图片URL
            val imageUrlRegex =
                    "https?://[^\\s)\"']+\\.(png|jpg|jpeg|gif|webp|svg)($|\\?[^\\s)\"']+)".toRegex()
            imageUrlRegex.find(text)?.let {
                // 删除从文本提取到常规图片URL的日志
                return it.groupValues[0]
            }

            // 删除未能从文本中提取到任何图片URL的日志
            return null
        } catch (e: Exception) {
            // 删除从文本提取图片URL时发生异常的日志
            return null
        }
    }

    /** 提取标准格式的干净标题 根据Cline marketplace标准，标题格式为: "[Server Submission]: Actual Title" */
    fun extractCleanTitle(title: String, issueId: String): String {
        // 移除标准前缀
        var cleanTitle = title
        if (title.contains(SERVER_SUBMISSION_TAG, ignoreCase = true)) {
            // 移除 "[Server Submission]:" 部分
            cleanTitle =
                    title.replaceFirst(
                                    Regex("\\[Server Submission\\]:?", RegexOption.IGNORE_CASE),
                                    ""
                            )
                            .trim()
        }

        // 如果还有其他方括号，尝试提取主标题
        val bracketMatch = Regex("\\[([^\\]]+)\\]").find(cleanTitle)
        if (bracketMatch != null) {
            // 提取方括号内的内容作为标题
            val bracketContent = bracketMatch.groupValues[1].trim()
            if (bracketContent.isNotBlank() && bracketContent.length > 3) {
                return bracketContent
            }
        }

        // 限制标题长度并确保有意义
        if (cleanTitle.length > 50) {
            cleanTitle = cleanTitle.take(50) + "..."
        }

        // 如果清理后标题为空或太短，使用issue编号和仓库名称作为标题
        if (cleanTitle.isBlank() || cleanTitle.length < 3) {
            return "MCP Server #$issueId"
        }

        return cleanTitle
    }

    /** 提取标准格式的描述 根据Cline marketplace标准，应优先使用Short Description部分 */
    fun extractStandardDescription(body: String, issueId: String): String {
        // 1. 首先尝试从Short Description部分获取
        val shortDescContent = extractSectionContent(body, SHORT_DESC_SECTION)
        if (shortDescContent != null && shortDescContent.isNotBlank()) {
            val cleanDesc = cleanDescription(shortDescContent)
            if (cleanDesc.isNotBlank() && cleanDesc.length > 10) {
                return cleanDesc.take(150) // 限制长度
            }
        }

        // 2. 尝试从"Why add this server"部分获取
        val whyAddContent = extractSectionContent(body, WHY_ADD_SECTION)
        if (whyAddContent != null && whyAddContent.isNotBlank()) {
            val cleanDesc = cleanDescription(whyAddContent)
            if (cleanDesc.isNotBlank() && cleanDesc.length > 10) {
                // 只取第一句作为简短描述
                val firstSentence = cleanDesc.split(Regex("[.!?]"), 2).firstOrNull()
                if (firstSentence != null && firstSentence.isNotBlank() && firstSentence.length > 10
                ) {
                    return firstSentence.take(150) // 限制长度
                }
                return cleanDesc.take(150) // 限制长度
            }
        }

        // 3. 尝试从Additional Information部分获取
        val infoSection = extractSectionContent(body, INFO_SECTION)
        if (infoSection != null && infoSection.isNotBlank()) {
            val cleanDesc = cleanDescription(infoSection)
            if (cleanDesc.isNotBlank() && cleanDesc.length > 10) {
                // 只取第一段作为简短描述
                val firstParagraph = cleanDesc.split("\n\n", limit = 2).firstOrNull()
                if (firstParagraph != null &&
                                firstParagraph.isNotBlank() &&
                                firstParagraph.length > 10
                ) {
                    return firstParagraph.take(150) // 限制长度
                }
                return cleanDesc.take(150) // 限制长度
            }
        }

        // 4. 尝试从整个body提取有意义的描述
        val lines = body.split("\n")
        val descLines =
                lines
                        .filter {
                            it.isNotBlank() &&
                                    !it.startsWith("###") &&
                                    !it.startsWith("http") &&
                                    !it.startsWith("!") &&
                                    !it.startsWith("-")
                        }
                        .take(3)

        if (descLines.isNotEmpty()) {
            val combinedDesc = descLines.joinToString(" ")
            val cleanDesc = cleanDescription(combinedDesc)
            if (cleanDesc.isNotBlank()) {
                return cleanDesc.take(150) // 限制长度
            }
        }

        // 最后的后备方案
        val repoUrl = extractRepoUrl(body)
        if (!repoUrl.isNullOrBlank()) {
            val repoName = repoUrl.split("/").lastOrNull() ?: ""
            if (repoName.isNotBlank()) {
                return "$repoName MCP Server"
            }
        }

        // 如果仍找不到描述，返回默认描述
        return "MCP Server #$issueId"
    }

    /** 清理描述文本，移除Markdown标记和多余空白 */
    fun cleanDescription(text: String): String {
        return text.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1") // 替换链接为纯文本
                .replace(Regex("[\\*_~`]+"), "") // 移除Markdown格式符号
                .replace(Regex("\\s+"), " ") // 替换多个空白字符为单个空格
                .trim()
    }

    /** 提取详细描述 根据Cline marketplace标准，应结合多个部分提供更完整的描述 */
    fun extractDetailedDescription(body: String): String {
        val sections = mutableListOf<String>()

        // 提取Short Description
        extractSectionContent(body, SHORT_DESC_SECTION)?.let {
            if (it.isNotBlank()) sections.add(it)
        }

        // 提取Why Add This Server
        extractSectionContent(body, WHY_ADD_SECTION)?.let { if (it.isNotBlank()) sections.add(it) }

        // 提取Additional Information
        extractSectionContent(body, INFO_SECTION)?.let {
            // 检查是否已经包含了前两个部分，以避免重复
            if (it.isNotBlank() && !sections.any { section -> it.contains(section) }) {
                sections.add(it)
            }
        }

        // 组合所有部分
        return if (sections.isNotEmpty()) {
            sections.joinToString("\n\n")
        } else {
            // 如果没有找到详细描述，尝试使用body中的非标题、非链接、非图片部分
            val cleanLines =
                    body.split("\n")
                            .filter {
                                it.isNotBlank() &&
                                        !it.startsWith("#") &&
                                        !it.startsWith("http") &&
                                        !it.startsWith("!") &&
                                        !it.startsWith("-") &&
                                        !it.matches(
                                                Regex("\\s*[\\-\\*]\\s+\\[([xX\\s])\\].*")
                                        ) // 排除复选框
                            }
                            .joinToString("\n")

            if (cleanLines.isNotBlank()) cleanLines else ""
        }
    }

    /** 提取仓库URL */
    fun extractRepoUrl(body: String): String? {
        // 尝试从专门的部分提取
        extractSectionContent(body, REPO_URL_SECTION)?.let { content ->
            extractGitHubUrlFromText(content)?.let {
                return it
            }
        }

        // 尝试从整个正文提取
        return extractGitHubUrlFromText(body)
    }

    /** 提取版本号 */
    fun extractVersion(body: String): String {
        val versionRegex = "v?(\\d+\\.\\d+\\.\\d+)".toRegex()
        val match = versionRegex.find(body)
        return match?.groupValues?.get(1) ?: "1.0.0"
    }

    /** 格式化更新日期 */
    fun formatUpdatedAt(isoDate: String): String {
        if (isoDate.isBlank()) return ""

        try {
            // ISO 8601格式：2025-04-15T05:40:47Z
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = parser.parse(isoDate) ?: return isoDate

            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            return formatter.format(date)
        } catch (e: Exception) {
            Log.e(TAG, "解析日期失败: $isoDate", e)
            return isoDate
        }
    }

    /** 检查作者是否为验证过的作者 */
    fun isVerifiedAuthor(author: String): Boolean {
        return author.equals("cline", ignoreCase = true) ||
                author.equals("pashpashpash", ignoreCase = true) ||
                author.equals("saoudrizwan", ignoreCase = true)
    }

    /** 获取作者姓名 */
    fun getAuthorName(issue: JSONObject): String {
        return if (issue.has("user") && !issue.isNull("user")) {
            issue.getJSONObject("user").optString("login", "Unknown")
        } else {
            "Unknown"
        }
    }

    /** 检测是否需要API密钥 */
    fun detectApiKeyRequirement(body: String): Boolean {
        val requiresApiKeyIndicators =
                listOf(
                        "api key",
                        "apikey",
                        "api_key",
                        "token",
                        "credentials",
                        "authentication",
                        "api token",
                        "secret key",
                        "access key",
                        "auth key",
                        "requires key"
                )

        return requiresApiKeyIndicators.any { body.contains(it, ignoreCase = true) }
    }

    /** 从特定部分提取内容 */
    fun extractSectionContent(body: String, sectionTitle: String): String? {
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
        return if (result.isBlank() || result == "_No response_") null else result
    }

    /** 从文本中提取GitHub URL */
    fun extractGitHubUrlFromText(text: String): String? {
        // 尝试匹配完整的GitHub仓库URL（包含可能的协议、子路径等）
        val fullGithubUrlRegex =
                "https?://(?:www\\.)?github\\.com/([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)(?:/[a-zA-Z0-9_.-/]*)?".toRegex()

        fullGithubUrlRegex.find(text)?.let { match ->
            // 提取仓库所有者和名称
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]

            // 格式化为标准GitHub仓库URL
            return "https://github.com/$owner/$repo"
        }

        // 尝试匹配简单格式如 owner/repo
        val simpleRepoRegex =
                "(?<![a-zA-Z0-9])([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)(?![a-zA-Z0-9])".toRegex()
        simpleRepoRegex.find(text)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]

            // 检查是否看起来像有效的GitHub仓库命名
            if (owner.length > 1 &&
                            repo.length > 1 &&
                            !owner.contains(".") &&
                            !repo.endsWith(".") &&
                            !owner.contains("http") &&
                            !repo.contains("http")
            ) {
                return "https://github.com/$owner/$repo"
            }
        }

        return null
    }

    /** 获取stars数量，集合多种来源 */
    fun getStarsCount(issue: JSONObject): Int {
        // 优先使用评论数作为主要指标
        val comments = issue.optInt("comments", 0)

        // 检查reactions作为次要指标
        var reactionScore = 0
        if (issue.has("reactions") && !issue.isNull("reactions")) {
            val reactions = issue.getJSONObject("reactions")
            // 集合所有类型的反应计数
            reactionScore = reactions.optInt("total_count", 0)

            // 如果total_count不可用或为0，手动计算所有反应类型的总和
            if (reactionScore <= 0) {
                reactionScore =
                        reactions.optInt("+1", 0) +
                                reactions.optInt("-1", 0) +
                                reactions.optInt("laugh", 0) +
                                reactions.optInt("hooray", 0) +
                                reactions.optInt("confused", 0) +
                                reactions.optInt("heart", 0) +
                                reactions.optInt("rocket", 0) +
                                reactions.optInt("eyes", 0)
            }
        }

        // 计算综合得分 - 评论权重更高
        val baseScore = comments * 3 + reactionScore

        // 检查是否是验证过的或者来自cline官方
        val user = issue.optJSONObject("user")
        if (user != null) {
            val login = user.optString("login", "")
            if (login.equals("cline", ignoreCase = true) ||
                            login.equals("pashpashpash", ignoreCase = true) ||
                            login.equals("saoudrizwan", ignoreCase = true)
            ) {
                // 官方发布的服务器基础得分额外提高，但不完全覆盖用户参与度
                // 使用至少20的基础分，然后加上实际得分
                val officialBonus = 20
                val finalScore = Math.max(baseScore, officialBonus) + officialBonus / 2
                Log.d(
                        TAG,
                        "Issue #${issue.optInt("number")} 来自官方账户 $login，得分: $finalScore (基础=$baseScore + 官方加成)"
                )
                return finalScore
            }
        }

        // 标题和说明里不应该包含"test"
        val title = issue.optString("title", "").lowercase()
        val body = issue.optString("body", "").lowercase()
        if ((title.contains("test") ||
                        body.contains("for test") ||
                        body.contains("test purpose")) &&
                        !title.contains("testing framework") &&
                        !title.contains("test data")
        ) {
            // 测试服务器的分数减半
            val reducedScore = Math.max(1, baseScore / 2)
            Log.d(
                    TAG,
                    "Issue #${issue.optInt("number")} 似乎是测试服务器，分数减半: $reducedScore (原分数=$baseScore)"
            )
            return reducedScore
        }

        // 记录并返回最终分数
        Log.d(
                TAG,
                "Issue #${issue.optInt("number")} 最终得分: $baseScore (评论=$comments, 反应=$reactionScore)"
        )
        return Math.max(1, baseScore) // 确保至少有1分
    }

    /** 根据title和body确定MCP服务器的类别 */
    fun determineCategory(title: String, body: String): String {
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
    fun extractTags(body: String, title: String, category: String): List<String> {
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
                tags.add(lang.capitalize(java.util.Locale.getDefault()))
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

    /** 解析日期字符串为Date对象 */
    fun parseDate(dateStr: String): java.util.Date? {
        if (dateStr.isBlank()) return null

        try {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            return formatter.parse(dateStr)
        } catch (e: Exception) {
            return null
        }
    }

    /** 解析并处理可能的URL重定向，特别是GitHub的用户附件格式 */
    fun resolveUrlRedirects(originalUrl: String): String? {
        if (originalUrl.isBlank()) return null

        try {
            Log.d(TAG, "开始处理可能的URL重定向: $originalUrl")

            // 特别针对GitHub用户附件格式 https://github.com/user-attachments/assets/...
            if (originalUrl.contains("github.com/user-attachments/assets")) {
                val url = URL(originalUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false // 禁用自动重定向以便我们可以记录并返回重定向URL
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                                responseCode == HttpURLConnection.HTTP_SEE_OTHER
                ) {

                    val newUrl = connection.getHeaderField("Location")
                    Log.d(TAG, "URL被重定向: $originalUrl -> $newUrl")
                    connection.disconnect()
                    return newUrl
                }

                connection.disconnect()
            }

            // 如果不需要特殊处理或未发生重定向，则返回原始URL
            return originalUrl
        } catch (e: Exception) {
            Log.e(TAG, "处理URL重定向时发生异常: ${e.message}", e)
            return originalUrl // 发生错误时返回原始URL
        }
    }
}

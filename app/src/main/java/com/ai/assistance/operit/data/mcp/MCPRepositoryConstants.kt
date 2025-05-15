package com.ai.assistance.operit.data.mcp

import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer

/** 储存 MCPRepository 模块使用的常量 */
object MCPRepositoryConstants {
        const val TAG = "MCPRepository"
        const val CACHE_DURATION_HOURS = 24
        const val MAX_PAGES = 10

        // Cline Marketplace 常量
        object GitHubConstants {
                const val API_BASE_URL = "https://api.github.com"
                const val ISSUES_ENDPOINT = "$API_BASE_URL/repos/cline/mcp-marketplace/issues"
                const val SEARCH_ENDPOINT = "$API_BASE_URL/search/issues"

                const val QUERY_PARAM = "q"
                const val PER_PAGE_PARAM = "per_page"
                const val PAGE_PARAM = "page"
                const val SORT_PARAM = "sort"
                const val DIRECTION_PARAM = "direction"
                const val STATE_PARAM = "state"

                const val ACCEPT_HEADER = "Accept"
                const val ACCEPT_JSON_VALUE = "application/vnd.github.v3+json"

                const val DEFAULT_PAGE_SIZE = 30
                const val DEFAULT_STATE = "open"
        }

        // MCP 官方仓库常量
        object OfficialMCPConstants {
                const val API_BASE_URL = "https://api.github.com"
                const val REPO_URL = "https://github.com/modelcontextprotocol/servers"
                const val README_API_URL =
                        "$API_BASE_URL/repos/modelcontextprotocol/servers/contents/README.md"
                const val CACHE_FILE_NAME = "official_mcp_servers_cache.json"

                /** 官方服务器Logo映射 为官方服务器提供默认的Logo URL，以提高显示效果 */
                object OfficialServerLogos {
                        private const val BASE_GITHUB_LOGO =
                                "https://raw.githubusercontent.com/modelcontextprotocol/servers/main/assets/logos/"
                        private const val DEFAULT_LOGO =
                                "https://raw.githubusercontent.com/modelcontextprotocol/servers/main/assets/logo.png"

                        // 简化后的Logo映射表，仅保留必要的项目
                        val LOGO_MAP =
                                mapOf(
                                        "Word" to
                                                "https://upload.wikimedia.org/wikipedia/commons/f/fd/Microsoft_Office_Word_%282019%E2%80%93present%29.svg",
                                        "Excel" to
                                                "https://upload.wikimedia.org/wikipedia/commons/3/34/Microsoft_Office_Excel_%282019%E2%80%93present%29.svg",
                                        "PowerPoint" to
                                                "https://upload.wikimedia.org/wikipedia/commons/0/0d/Microsoft_Office_PowerPoint_%282019%E2%80%93present%29.svg",
                                        "Fetch" to "$BASE_GITHUB_LOGO/fetch.png",
                                        "12306" to "https://www.12306.cn/index/images/favicon.ico",
                                        "DuckDuckGo" to "https://duckduckgo.com/favicon.ico",
                                        "Playwright" to
                                                "https://playwright.dev/img/playwright-logo.svg",
                                        "Tavily" to "https://tavily.com/favicon.ico",
                                        "MarkItDown" to "https://markitdown.dev/favicon.ico"
                                )

                        /**
                         * 获取官方服务器的Logo URL
                         *
                         * @param serverName 服务器名称
                         * @param category 服务器类别
                         * @return Logo URL，如果没有指定的Logo，则返回默认Logo
                         */
                        fun getLogoUrl(serverName: String, category: String): String {
                                // 0. 直接处理已知固定图片的服务器，避免任何处理逻辑，减少延迟
                                val cleanServerName = serverName.trim()

                                // 常用服务的直接映射，保证快速响应
                                when (cleanServerName.lowercase()) {
                                        "tavily" ->
                                                return "https://storage.googleapis.com/cline_public_images/tavily.jpg"
                                        "duckduckgo" -> return "https://duckduckgo.com/favicon.ico"
                                        "word" ->
                                                return "https://upload.wikimedia.org/wikipedia/commons/f/fd/Microsoft_Office_Word_%282019%E2%80%93present%29.svg"
                                        "excel" ->
                                                return "https://upload.wikimedia.org/wikipedia/commons/3/34/Microsoft_Office_Excel_%282019%E2%80%93present%29.svg"
                                        "powerpoint" ->
                                                return "https://upload.wikimedia.org/wikipedia/commons/0/0d/Microsoft_Office_PowerPoint_%282019%E2%80%93present%29.svg"
                                        "12306" ->
                                                return "https://www.12306.cn/index/images/favicon.ico"
                                        "playwright" ->
                                                return "https://playwright.dev/img/playwright-logo.svg"
                                        "markitdown" -> return "https://markitdown.dev/favicon.ico"
                                        "fetch" -> return "$BASE_GITHUB_LOGO/fetch.png"
                                }

                                // 1. 先检查明确的映射，这是最优先的
                                val explicitLogo = LOGO_MAP[serverName]
                                if (explicitLogo != null) {
                                        return explicitLogo
                                }

                                // 2. 使用默认Logo
                                return DEFAULT_LOGO
                        }

                        /**
                         * Attempts to extract a domain name from a service name For example,
                         * "GitHub" would return "github.com"
                         */
                        private fun extractDomainName(serverName: String): String {
                                // Remove common suffixes that aren't part of the domain
                                val cleanName =
                                        serverName
                                                .replace(" API", "")
                                                .replace(" MCP", "")
                                                .replace(" Server", "")
                                                .lowercase()
                                                .trim()

                                // Try to match known domains
                                return when {
                                        cleanName == "duckduckgo" -> "duckduckgo.com"
                                        cleanName == "tavily" -> "tavily.com"
                                        cleanName == "12306" -> "12306.cn"
                                        cleanName == "markitdown" -> "markitdown.dev"
                                        cleanName == "playwright" -> "playwright.dev"
                                        cleanName.contains("word") -> "microsoft.com"
                                        cleanName.contains("excel") -> "microsoft.com"
                                        cleanName.contains("powerpoint") -> "microsoft.com"
                                        // Fallback empty
                                        else -> ""
                                }
                        }

                        /** Try to guess a domain based on company name patterns */
                        private fun guessDomain(name: String): String {
                                // Return empty as we will fall back to defaults
                                return ""
                        }
                }

                /** 推荐的MCP服务器列表 提供一份固定的推荐MCP服务器列表，每个服务器都有独立的GitHub仓库链接 */
                object RecommendedServers {
                        /** 获取固定的推荐MCP服务器列表 */
                        fun getRecommendedMCPServers(): List<MCPServer> {
                                val servers = mutableListOf<MCPServer>()

                                // 添加核心推荐服务器
                                addCoreRecommendedServers(servers)

                                // 添加其他常用服务器
                                addCommonServers(servers)

                                return servers
                        }

                        /** 添加核心推荐服务器 */
                        private fun addCoreRecommendedServers(servers: MutableList<MCPServer>) {
                                // 核心服务器列表，根据用户要求添加
                                addServer(
                                        servers,
                                        "Word",
                                        "微软 Word 文档处理工具，支持创建、编辑和格式化各类文档，提供丰富的文本处理功能",
                                        "Microsoft Office",
                                        true,
                                        "https://github.com/GongRzhe/Office-Word-MCP-Server"
                                )
                                addServer(
                                        servers,
                                        "Excel",
                                        "强大的电子表格处理工具，可进行数据分析、图表创建、公式计算，无需安装 Microsoft Excel 即可操作",
                                        "Microsoft Office",
                                        true,
                                        "https://github.com/haris-musa/excel-mcp-server"
                                )
                                addServer(
                                        servers,
                                        "PowerPoint",
                                        "幻灯片演示文稿创建工具，支持添加文本、图片、图表，以及丰富的动画效果和模板应用",
                                        "Microsoft Office",
                                        true,
                                        "https://github.com/jenstangen1/pptx-xlsx-mcp"
                                )
                                addServer(
                                        servers,
                                        "Fetch",
                                        "网络数据获取工具，用于从网页抓取信息并进行格式化处理，支持多种数据格式转换",
                                        "网络工具",
                                        false,
                                        "https://github.com/modelcontextprotocol/servers/tree/main/src/fetch"
                                )
                                addServer(
                                        servers,
                                        "Tavily",
                                        "专为AI代理设计的搜索引擎，提供网页搜索、内容提取、网站地图和网站爬取等功能",
                                        "搜索引擎",
                                        true,
                                        "https://github.com/tavily-ai/tavily-mcp"
                                )
                        }

                        /** 添加其他常用服务器 */
                        private fun addCommonServers(servers: MutableList<MCPServer>) {
                                // 其他常用服务器列表，根据用户要求添加
                                addServer(
                                        servers,
                                        "12306",
                                        "基于MCP的12306铁路购票信息查询服务器，支持车票查询、列车信息过滤和过站查询等功能，为AI模型提供中国铁路出行数据支持",
                                        "交通出行",
                                        false,
                                        "https://github.com/Joooook/12306-mcp"
                                )
                                addServer(
                                        servers,
                                        "DuckDuckGo",
                                        "注重隐私保护的搜索引擎MCP服务器，提供网络搜索和内容获取功能，支持查询限制和安全搜索选项，确保无追踪的搜索体验",
                                        "搜索工具",
                                        false,
                                        "https://github.com/nickclyde/duckduckgo-mcp-server"
                                )
                                addServer(
                                        servers,
                                        "Playwright",
                                        "基于Microsoft Playwright的MCP服务器，提供浏览器自动化、网页交互、截图和内容抓取功能，支持多种浏览器引擎和无头模式",
                                        "网页自动化",
                                        false,
                                        "https://github.com/microsoft/playwright-mcp"
                                )
                                addServer(
                                        servers,
                                        "MarkItDown",
                                        "强大的文档转换MCP服务器，可将PDF、Word、Excel、PowerPoint、图像和网页内容转换为Markdown格式，支持文档结构保留和格式优化",
                                        "文档工具",
                                        false,
                                        "https://github.com/microsoft/markitdown"
                                )
                        }

                        /** 添加单个服务器到列表 */
                        private fun addServer(
                                servers: MutableList<MCPServer>,
                                name: String,
                                description: String,
                                category: String,
                                requiresApiKey: Boolean,
                                repoUrl: String
                        ) {
                                // 生成唯一ID (基于名称)
                                val idBase =
                                        "recommended_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"

                                // 检查是否已存在相同ID的服务器
                                val id =
                                        if (servers.any { it.id == idBase }) {
                                                "${idBase}_${servers.size + System.currentTimeMillis() % 1000}"
                                        } else {
                                                idBase
                                        }

                                // 获取Logo URL
                                val logoUrl = OfficialServerLogos.getLogoUrl(name, category)

                                // 创建服务器对象
                                val server =
                                        MCPServer(
                                                id = id,
                                                name = name,
                                                description = description,
                                                logoUrl = logoUrl,
                                                stars = 50, // 给推荐服务器一个较高的默认星级
                                                category = category,
                                                requiresApiKey = requiresApiKey,
                                                author = "Model Context Protocol (Official)",
                                                isVerified = true, // 推荐的都标记为已验证
                                                isInstalled = false, // 初始未安装
                                                version = "latest",
                                                updatedAt = "",
                                                longDescription =
                                                        "$description\n\n*这是推荐的模型上下文协议服务器。*\n\nGitHub 仓库: $repoUrl",
                                                repoUrl = repoUrl
                                        )

                                servers.add(server)
                        }
                }
        }

        enum class SortOptions(val value: String) {
                CREATED("created"),
                UPDATED("updated"),
                COMMENTS("comments"),
                REACTIONS("reactions"),
                RECOMMENDED("recommended"), // This is a local sort option, not part of GitHub API
        }

        enum class SortDirection(val value: String) {
                ASC("asc"),
                DESC("desc")
        }

        // ModelContextProtocol GitHub 常量
        object MCPGitHubConstants {
                const val API_BASE_URL = "https://api.github.com"
                const val REPO_ENDPOINT = "$API_BASE_URL/repos/modelcontextprotocol/servers"
                const val CONTENTS_ENDPOINT = "$REPO_ENDPOINT/contents"
                const val README_URL =
                        "https://raw.githubusercontent.com/modelcontextprotocol/servers/main/README.md"

                // 主要目录
                const val REFERENCE_SERVERS_DIR = "reference-servers"

                // 请求头
                const val ACCEPT_HEADER = "Accept"
                const val ACCEPT_JSON_VALUE = "application/vnd.github.v3+json"
        }

        // MCP 服务器类型
        enum class MCPServerType {
                CLINE_MARKETPLACE, // Cline Marketplace 服务器
                MCP_REFERENCE, // 官方参考服务器
                MCP_THIRD_PARTY // 第三方服务器
        }

        // 安装命令类型
        enum class InstallCommandType {
                NPX, // 基于TypeScript的npm/npx安装
                UVX, // 基于Python的uvx安装
                PIP, // 基于Python的pip安装
                OTHER // 其他安装方式
        }

        // 标记服务器提交的关键字 - 按照标准格式
        const val SERVER_SUBMISSION_TAG = "[Server Submission]"

        // Issue正文中的标准部分 - 按照官方提交格式
        const val REPO_URL_SECTION = "### GitHub Repository URL"
        const val LOGO_SECTION = "### Logo Image"
        const val TESTING_SECTION = "### Installation Testing"
        const val INFO_SECTION = "### Additional Information"
        const val SHORT_DESC_SECTION = "#### Short Description"
        const val WHY_ADD_SECTION = "#### Why add this server"

        // 官方提交格式的复选框
        const val TESTING_CHECKBOX_README =
                "- [x] I have tested that Cline can successfully set up this server using only the README.md and/or llms-install.md file"
        const val TESTING_CHECKBOX_STABLE = "- [x] The server is stable and ready for public use"

        // Logo标准尺寸 (根据提交标准)
        const val LOGO_STANDARD_SIZE = 400 // 400x400 PNG
}

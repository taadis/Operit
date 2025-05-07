package com.ai.assistance.operit.data.mcp

import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.OfficialMCPConstants
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TAG
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer

/** Manages official MCP servers from the official repo and backup sources */
class MCPOfficialSourceManager(
        private val networkClient: MCPNetworkClient,
        private val cacheManager: MCPCacheManager
) {

    /**
     * Fetches official MCP servers with pagination support
     *
     * @param forceRefresh Whether to force refresh from network
     * @param page Page number to fetch (starting from 1)
     * @param pageSize Number of servers per page
     * @return Pair of servers for the requested page and total number of servers
     */
    suspend fun fetchOfficialServers(
            forceRefresh: Boolean = false,
            page: Int = 1,
            pageSize: Int = 50
    ): Pair<List<MCPServer>, Int> {
        // First try loading from cache if not forcing refresh
        if (!forceRefresh && cacheManager.isOfficialCacheValid()) {
            val cachedServers = cacheManager.loadOfficialFromCache()
            if (cachedServers.isNotEmpty()) {
                Log.d(
                        TAG,
                        "Successfully loaded ${cachedServers.size} servers from official repo cache"
                )
                // Return paginated results
                val totalServers = cachedServers.size
                val paginatedServers = cachedServers.paginateServers(page, pageSize)
                Log.d(
                        TAG,
                        "Returning page $page (${paginatedServers.size} servers) out of $totalServers total servers"
                )
                return Pair(paginatedServers, totalServers)
            }
        }

        // Try fetching from GitHub API
        val readmeContent = networkClient.fetchOfficialRepoReadme()
        if (readmeContent != null) {
            Log.d(TAG, "Successfully fetched README content from official repo, parsing...")
            val servers = parseReadmeContent(readmeContent)

            // Cache the results
            if (servers.isNotEmpty()) {
                cacheManager.saveOfficialToCache(servers)
                Log.d(TAG, "Parsed and cached ${servers.size} servers from official repo")

                // Return paginated results
                val totalServers = servers.size
                val paginatedServers = servers.paginateServers(page, pageSize)
                Log.d(
                        TAG,
                        "Returning page $page (${paginatedServers.size} servers) out of $totalServers total servers"
                )
                return Pair(paginatedServers, totalServers)
            }
        }

        // Fall back to hardcoded servers if network fetch fails
        Log.d(TAG, "Unable to fetch official servers from GitHub, using hardcoded fallback")
        val hardcodedServers = getHardcodedOfficialServers()

        // Cache the hardcoded servers
        if (hardcodedServers.isNotEmpty()) {
            cacheManager.saveOfficialToCache(hardcodedServers)
            Log.d(TAG, "Cached ${hardcodedServers.size} hardcoded official servers")
        }

        // Return paginated results
        val totalServers = hardcodedServers.size
        val paginatedServers = hardcodedServers.paginateServers(page, pageSize)
        Log.d(
                TAG,
                "Returning page $page (${paginatedServers.size} servers) out of $totalServers total servers"
        )
        return Pair(paginatedServers, totalServers)
    }

    /**
     * Filters official servers based on search query
     *
     * @param servers List of official servers to filter
     * @param query Search query
     * @return Filtered list of servers
     */
    fun filterOfficialServers(servers: List<MCPServer>, query: String): List<MCPServer> {
        // If no query, return all official servers
        if (query.isBlank()) {
            return servers
        }

        // Filter by search query
        return servers.filter { server ->
            server.name.contains(query, ignoreCase = true) ||
                    server.description.contains(query, ignoreCase = true) ||
                    server.longDescription.contains(query, ignoreCase = true)
        }
    }

    /**
     * Parses README.md content to extract server information
     *
     * @param readmeContent The README content as string
     * @return List of parsed MCPServer objects
     */
    private fun parseReadmeContent(readmeContent: String): List<MCPServer> {
        val servers = mutableListOf<MCPServer>()

        try {
            // Find reference servers section
            val referenceServersStart = readmeContent.indexOf("## 🌟 Reference Servers")
            val thirdPartyServersStart = readmeContent.indexOf("## 🤝 Third-Party Servers")
            val resourcesStart = readmeContent.indexOf("## 📚 Resources")

            Log.d(
                    TAG,
                    "README section markers: Reference=${referenceServersStart}, ThirdParty=${thirdPartyServersStart}, Resources=${resourcesStart}"
            )

            if (referenceServersStart != -1 && thirdPartyServersStart != -1) {
                // Extract reference servers section
                val referenceServersSection =
                        readmeContent.substring(referenceServersStart, thirdPartyServersStart)

                // Parse reference servers
                parseServerSection(referenceServersSection, "Reference", servers)
                Log.d(TAG, "Parsed Reference servers section, current total: ${servers.size}")

                // Extract third party servers section
                val thirdPartyServersSection =
                        readmeContent.substring(
                                thirdPartyServersStart,
                                if (resourcesStart != -1) resourcesStart else readmeContent.length
                        )

                // Parse official integrations
                val officialIntegrationsStart =
                        thirdPartyServersSection.indexOf("### 🎖️ Official Integrations")
                val communityServersStart =
                        thirdPartyServersSection.indexOf("### 🧩 Community Servers")

                Log.d(
                        TAG,
                        "Third-Party subsections: OfficialIntegrations=${officialIntegrationsStart}, CommunityServers=${communityServersStart}"
                )

                if (officialIntegrationsStart != -1) {
                    // Calculate end boundary for official integrations
                    val officialIntegrationsEnd =
                            if (communityServersStart != -1) {
                                communityServersStart
                            } else {
                                thirdPartyServersSection.length
                            }

                    // Extract official integrations section
                    val officialIntegrationsSection =
                            thirdPartyServersSection.substring(
                                    officialIntegrationsStart,
                                    officialIntegrationsEnd
                            )

                    // Parse official integration servers
                    parseServerSection(officialIntegrationsSection, "Official Integration", servers)
                    Log.d(
                            TAG,
                            "Parsed Official Integration servers section, current total: ${servers.size}"
                    )
                } else {
                    Log.d(TAG, "Official Integrations section not found")
                }

                // Extract and parse community servers if present
                if (communityServersStart != -1) {
                    // Extract community servers section
                    val communityServersSection =
                            thirdPartyServersSection.substring(communityServersStart)

                    // Parse community servers
                    parseServerSection(communityServersSection, "Community", servers)
                    Log.d(TAG, "Parsed Community servers section, current total: ${servers.size}")
                } else {
                    Log.d(TAG, "Community Servers section not found")
                }
            }

            Log.d(
                    TAG,
                    "Parsed ${servers.size} servers from README with categories: ${servers.groupBy { it.category }.mapValues { it.value.size }}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse README content", e)
        }

        return servers
    }

    /**
     * Parses a section of the README to extract server information
     *
     * @param section The section to parse
     * @param category The category to assign to servers in this section
     * @param servers The mutable list to add parsed servers to
     */
    private fun parseServerSection(
            section: String,
            category: String,
            servers: MutableList<MCPServer>
    ) {
        try {
            // Different regex patterns for different sections
            val regex =
                    if (category == "Reference") {
                        // For Reference servers format: * **ServerName** \- Description
                        """[*-]\s+\*\*([^*]+?)\*\*\s+-\s+(.+?)(?:\.|$)""".toRegex(
                                RegexOption.MULTILINE
                        )
                    } else if (category == "Official Integration") {
                        // For Official Integration format: * LogoName Logo **ServerName** \-
                        // Description
                        // or sometimes: * **ServerName** \- Description
                        """[*-]\s+(?:(?:[^*]+?)\s+Logo\s+)?\*\*([^*]+?)\*\*\s+-\s+(.+?)(?:\.|$)""".toRegex(
                                RegexOption.MULTILINE
                        )
                    } else if (category == "Community") {
                        // For Community servers format: * [ServerName](URL) - Description
                        """[*-]\s+\[([^\]]+?)\]\([^)]+?\)\s+-\s+(.+?)(?:\.|$)""".toRegex(
                                RegexOption.MULTILINE
                        )
                    } else {
                        // More general pattern for other formats
                        """[*-]\s+\*\*([^*]+?)\*\*\s+-\s+(.+?)(?:\.|$)""".toRegex(
                                RegexOption.MULTILINE
                        )
                    }

            // Find all server lines with regex
            val matches = regex.findAll(section)
            var matchCount = 0

            for (match in matches) {
                matchCount++
                try {
                    // Get server name and description based on regex groups
                    var name = match.groupValues[1].trim()
                    val description = match.groupValues[2].trim()

                    // Clean up name - extract name from markdown link if present
                    val markdownLinkPattern = """\[([^\]]+)\]\([^)]+\)""".toRegex()
                    val markdownLinkMatch = markdownLinkPattern.find(name)
                    if (markdownLinkMatch != null) {
                        name = markdownLinkMatch.groupValues[1].trim()
                    }

                    // Skip empty or invalid server names
                    if (name.isBlank() || name.length < 2) continue

                    // Generate unique ID (use name's hashCode with timestamp prefix)
                    val idBase = "official_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
                    val id =
                            if (servers.any { it.id == idBase }) {
                                "${idBase}_${System.currentTimeMillis() % 10000L + matchCount}"
                            } else {
                                idBase
                            }

                    // Get logo URL from mappings in OfficialMCPConstants
                    val logoUrl =
                            OfficialMCPConstants.OfficialServerLogos.getLogoUrl(name, category)

                    // Ensure category matches exactly what we expect in sorting function
                    val normalizedCategory =
                            when (category) {
                                "Reference" -> "Reference"
                                "Official Integration" -> "Official Integration"
                                "Community" -> "Community"
                                else -> category
                            }

                    // First try to extract GitHub repository URL from the description
                    var repoUrl = ""

                    // Look for GitHub links in the description
                    val githubUrlPattern = """https?://(?:www\.)?github\.com/[^)\s]+""".toRegex()
                    val githubUrlMatch = githubUrlPattern.find(description)

                    if (githubUrlMatch != null) {
                        // Direct GitHub URL found in description
                        repoUrl = githubUrlMatch.value
                        Log.d(TAG, "Found GitHub URL in description for ${name}: ${repoUrl}")
                    } else {
                        // Check if there's a reference to modelcontextprotocol/servers in the
                        // README
                        val srcFolderName = name.lowercase().replace(" ", "-")

                        // For reference servers, we know they're in the src directory
                        if (category == "Reference") {
                            repoUrl = "mcp-official:${srcFolderName}"
                            Log.d(TAG, "Using reference path for ${name}: ${repoUrl}")
                        } else {
                            // For other servers, try the root directory or the src directory
                            repoUrl = "mcp-official:${srcFolderName}"
                            Log.d(TAG, "Using constructed path for ${name}: ${repoUrl}")
                        }
                    }

                    // Create server object
                    val server =
                            MCPServer(
                                    id = id,
                                    name = name,
                                    description = description,
                                    logoUrl = logoUrl,
                                    stars = 0,
                                    category = normalizedCategory,
                                    requiresApiKey =
                                            description.contains("API") ||
                                                    description.contains("key"),
                                    author = "Model Context Protocol (Official)",
                                    isVerified = true,
                                    isInstalled = false,
                                    version = "latest",
                                    updatedAt = "",
                                    longDescription =
                                            "$description\n\n*This server is from the official Model Context Protocol repository.*",
                                    repoUrl = repoUrl
                            )

                    Log.d(
                            TAG,
                            "Parsed server from README: ${server.name} (category: ${server.category}) - ${server.description.take(50)}..."
                    )
                    servers.add(server)
                } catch (e: Exception) {
                    Log.e(
                            TAG,
                            "Failed to parse server line in ${category} section: ${match.value}",
                            e
                    )
                }
            }

            // If no matches were found with the primary regex, try a fallback approach
            if (matchCount == 0) {
                Log.d(
                        TAG,
                        "No matches found with primary regex for ${category}, trying fallback pattern"
                )

                // Fallback regex for entries without proper formatting
                val fallbackRegex =
                        if (category == "Community") {
                            // Try to catch Community entries with markdown links
                            """[*-]\s+\[([^\]]+?)\]\([^)]+?\)\s+-\s+(.+?)(?:\.|$)""".toRegex(
                                    RegexOption.MULTILINE
                            )
                        } else {
                            """[*-]\s+([^-\n]+?)\s+-\s+(.+?)(?:\.|$)""".toRegex(
                                    RegexOption.MULTILINE
                            )
                        }

                val fallbackMatches = fallbackRegex.findAll(section)

                for (match in fallbackMatches) {
                    try {
                        var name = match.groupValues[1].trim()
                        val description = match.groupValues[2].trim()

                        // Clean up name: extract from markdown link, remove Logo mentions and
                        // asterisks
                        val markdownLinkPattern = """\[([^\]]+)\]\([^)]+\)""".toRegex()
                        val markdownLinkMatch = markdownLinkPattern.find(name)
                        if (markdownLinkMatch != null) {
                            name = markdownLinkMatch.groupValues[1].trim()
                        }

                        name = name.replace(Regex("\\s+Logo\\s+"), " ")
                        name = name.replace("**", "")

                        // Skip if name is too short or contains unwanted text
                        if (name.isBlank() || name.length < 2 || name.startsWith("This MCP"))
                                continue

                        val idBase = "official_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
                        val id =
                                if (servers.any { it.id == idBase }) {
                                    "${idBase}_${System.currentTimeMillis() % 10000L + matchCount}"
                                } else {
                                    idBase
                                }

                        val logoUrl =
                                OfficialMCPConstants.OfficialServerLogos.getLogoUrl(name, category)

                        // First try to extract GitHub repository URL from the description
                        var repoUrl = ""

                        // Look for GitHub links in the description
                        val githubUrlPattern =
                                """https?://(?:www\.)?github\.com/[^)\s]+""".toRegex()
                        val githubUrlMatch = githubUrlPattern.find(description)

                        if (githubUrlMatch != null) {
                            // Direct GitHub URL found in description
                            repoUrl = githubUrlMatch.value
                            Log.d(
                                    TAG,
                                    "Fallback: Found GitHub URL in description for ${name}: ${repoUrl}"
                            )
                        } else {
                            // Check if there's a reference to modelcontextprotocol/servers in the
                            // README
                            val srcFolderName = name.lowercase().replace(" ", "-")

                            // For reference servers, we know they're in the src directory
                            if (category == "Reference") {
                                repoUrl = "mcp-official:${srcFolderName}"
                                Log.d(TAG, "Fallback: Using reference path for ${name}: ${repoUrl}")
                            } else {
                                // For other servers, try the root directory or the src directory
                                repoUrl = "mcp-official:${srcFolderName}"
                                Log.d(
                                        TAG,
                                        "Fallback: Using constructed path for ${name}: ${repoUrl}"
                                )
                            }
                        }

                        val server =
                                MCPServer(
                                        id = id,
                                        name = name,
                                        description = description,
                                        logoUrl = logoUrl,
                                        stars = 0,
                                        category = category,
                                        requiresApiKey =
                                                description.contains("API") ||
                                                        description.contains("key"),
                                        author = "Model Context Protocol (Official)",
                                        isVerified = true,
                                        isInstalled = false,
                                        version = "latest",
                                        updatedAt = "",
                                        longDescription =
                                                "$description\n\n*This server is from the official Model Context Protocol repository.*",
                                        repoUrl = repoUrl
                                )

                        Log.d(
                                TAG,
                                "Parsed server from README using fallback: ${server.name} (category: ${server.category}) - ${server.description.take(50)}..."
                        )
                        servers.add(server)
                    } catch (e: Exception) {
                        Log.e(
                                TAG,
                                "Failed to parse server line with fallback regex: ${match.value}",
                                e
                        )
                    }
                }
            }

            Log.d(TAG, "Parsed ${servers.size} servers from ${category} section")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ${category} section", e)
        }
    }

    /**
     * Returns a list of hardcoded official servers as a backup
     *
     * @return List of hardcoded official MCPServer objects
     */
    private fun getHardcodedOfficialServers(): List<MCPServer> {
        val hardcodedServers = OfficialMCPConstants.OfficialServers.getEssentialOfficialServers()

        // Convert to UI model
        return hardcodedServers.map { dataServer ->
            MCPServer(
                    id = dataServer.id,
                    name = dataServer.name,
                    description = dataServer.description,
                    logoUrl = dataServer.logoUrl,
                    stars = dataServer.stars,
                    category = dataServer.category,
                    requiresApiKey = dataServer.requiresApiKey,
                    author = dataServer.author,
                    isVerified = dataServer.isVerified,
                    isInstalled = dataServer.isInstalled,
                    version = dataServer.version,
                    updatedAt = dataServer.updatedAt,
                    longDescription = dataServer.longDescription,
                    repoUrl = dataServer.repoUrl
            )
        }
    }

    /**
     * Extension function to paginate a list of servers
     *
     * @param page Page number (1-based)
     * @param pageSize Number of servers per page
     * @return Paginated list of servers
     */
    private fun List<MCPServer>.paginateServers(page: Int, pageSize: Int): List<MCPServer> {
        if (this.isEmpty()) return emptyList()
        if (page < 1) return emptyList()

        val startIndex = (page - 1) * pageSize
        if (startIndex >= this.size) return emptyList()

        val endIndex = minOf(startIndex + pageSize, this.size)
        return this.subList(startIndex, endIndex)
    }
}

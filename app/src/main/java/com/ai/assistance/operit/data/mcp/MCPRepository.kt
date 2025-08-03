package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.GitHubConstants
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.MAX_PAGES
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.SortDirection
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.SortOptions
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TAG
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer as UIMCPServer
import com.google.gson.Gson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP Repository - responsible for managing MCP server data from the GitHub Marketplace
 *
 * This class has been refactored to use specialized manager classes for different responsibilities:
 * - MCPNetworkClient: Handles all network requests
 * - MCPCacheManager: Handles caching of server data
 * - MCPOfficialSourceManager: Manages official MCP servers
 * - MCPPluginManager: Manages plugin installation and tracking
 *
 * The loading process follows a strict order:
 * 1. Official plugins are loaded first
 * 2. Third-party plugins from GitHub issues are loaded second
 * 3. No merging logic is used - just sequential loading in priority order
 */
class MCPRepository(private val context: Context) {

    // Specialized components
    private val networkClient = MCPNetworkClient()
    private val cacheManager = MCPCacheManager(context)
    private val officialSourceManager = MCPOfficialSourceManager(cacheManager)
    private val pluginManager = MCPPluginManager(cacheManager, MCPInstaller(context))

    // Current page for third-party content (after official content)
    private var currentPage = 1
    private var hasMorePages = true
    private val loadedServerIds = mutableSetOf<String>()

    // Official servers pagination
    private var officialCurrentPage = 1
    private var totalOfficialServers = 0
    private var hasMoreOfficialPages = true
    private val PAGE_SIZE = 50

    // State flows
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<UIMCPServer>>(emptyList())
    val mcpServers: StateFlow<List<UIMCPServer>> = _mcpServers.asStateFlow()

    // Installed plugin IDs
    val installedPluginIds: StateFlow<Set<String>> = pluginManager.installedPluginIds

    /**
     * Main method to fetch MCP servers - first loads official plugins, then third-party Uses a
     * unified approach for initial load and pagination with no merging logic
     *
     * @param forceRefresh Whether to force a refresh from the network
     * @param query Optional search query to filter results
     * @param sortBy How to sort the results
     * @param sortDirection Direction of sorting (ASC/DESC)
     */
    suspend fun fetchMCPServers(
            forceRefresh: Boolean = false,
            query: String = "",
            sortBy: SortOptions = SortOptions.RECOMMENDED,
            sortDirection: SortDirection = SortDirection.DESC
    ) {
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Reset state if force refreshing
                if (forceRefresh) {
                    Log.d(TAG, "Force refreshing MCP plugin data")
                    currentPage = 1
                    officialCurrentPage = 1
                    hasMorePages = true
                    hasMoreOfficialPages = true
                    totalOfficialServers = 0
                    loadedServerIds.clear()
                    _mcpServers.value = emptyList()
                    cacheManager.clearAllCaches()
                }

                // Step 1: Load official plugins with pagination
                if (hasMoreOfficialPages && (currentPage == 1 || forceRefresh)) {
                    Log.d(TAG, "Step 1: Loading official MCP plugins, page: $officialCurrentPage")
                    val (officialServersPage, totalServers) =
                            officialSourceManager.fetchOfficialServers(
                                    forceRefresh,
                                    officialCurrentPage,
                                    PAGE_SIZE
                            )

                    totalOfficialServers = totalServers

                    // Check if there are more official pages
                    hasMoreOfficialPages = (officialCurrentPage * PAGE_SIZE) < totalOfficialServers

                    // Filter official servers if there's a search query
                    val filteredOfficialServers =
                            if (query.isNotBlank()) {
                                officialSourceManager.filterOfficialServers(
                                        officialServersPage,
                                        query
                                )
                            } else {
                                officialServersPage
                            }

                    if (filteredOfficialServers.isNotEmpty()) {
                        // Add official plugins to the list
                        filteredOfficialServers.forEach { loadedServerIds.add(it.id) }

                        // If it's the first page, just set the value
                        if (officialCurrentPage == 1) {
                            _mcpServers.value = filteredOfficialServers
                        } else {
                            // Otherwise append to existing list
                            val updatedServers = _mcpServers.value + filteredOfficialServers
                            _mcpServers.value = updatedServers
                        }

                        Log.d(
                                TAG,
                                "Loaded ${filteredOfficialServers.size} official plugins (page $officialCurrentPage of ${(totalOfficialServers + PAGE_SIZE - 1) / PAGE_SIZE})"
                        )

                        // Move to next page of official servers
                        officialCurrentPage++

                        // Update installation status
                        updateInstalledStatus()

                        // If we've shown a reasonable number of servers already, we can stop for
                        // this fetch
                        if (_mcpServers.value.size >= PAGE_SIZE) {
                            _isLoading.value = false
                            _hasMore.value = hasMoreOfficialPages || hasMorePages
                            return@withContext
                        }
                    }
                }

                // If we still have more official pages, we should stop here and let the user load
                // more
                if (hasMoreOfficialPages) {
                    _isLoading.value = false
                    _hasMore.value = true
                    return@withContext
                }

                // Step 2: Only proceed to load third-party plugins if we've exhausted all official
                // pages
                if (hasMorePages) {
                    Log.d(
                            TAG,
                            "Step 2: Loading third-party plugins from GitHub issues, page: $currentPage"
                    )

                    // Try cache first if it's first page and not forcing refresh
                    if (currentPage == 1 && !forceRefresh && cacheManager.isMarketplaceCacheValid()
                    ) {
                        Log.d(TAG, "Trying to load third-party plugins from cache")
                        val cachedServers = cacheManager.loadMarketplaceFromCache()

                        if (cachedServers.isNotEmpty()) {
                            // Filter cached servers that aren't already loaded (to avoid duplicates
                            // with official servers)
                            val newCachedServers =
                                    cachedServers.filter { !loadedServerIds.contains(it.id) }

                            if (newCachedServers.isNotEmpty()) {
                                // Filter by search query if needed
                                val filteredCachedServers =
                                        if (query.isNotBlank()) {
                                            newCachedServers.filter {
                                                it.name.contains(query, ignoreCase = true) ||
                                                        it.description.contains(
                                                                query,
                                                                ignoreCase = true
                                                        ) ||
                                                        it.author.contains(query, ignoreCase = true)
                                            }
                                        } else {
                                            newCachedServers
                                        }

                                // Add filtered cached servers to the list
                                filteredCachedServers.forEach { loadedServerIds.add(it.id) }
                                val updatedServers = _mcpServers.value + filteredCachedServers

                                // Apply sort if needed
                                val sortedServers =
                                        if (sortBy == SortOptions.RECOMMENDED) {
                                            MCPRepositoryUtils.sortServersByRecommended(
                                                    updatedServers
                                            )
                                        } else {
                                            updatedServers
                                        }

                                _mcpServers.value = sortedServers
                                currentPage++ // Prepare for next page load

                                Log.d(
                                        TAG,
                                        "Added ${filteredCachedServers.size} cached third-party plugins"
                                )
                            }

                            // Update installation status
                            updateInstalledStatus()

                            // If we have enough data from cache, we can skip network fetch for now
                            if (_mcpServers.value.size >= totalOfficialServers + PAGE_SIZE) {
                                _isLoading.value = false
                                return@withContext
                            }
                        }
                    }

                    // Fetch third-party plugins directly from GitHub issues
                    fetchThirdPartyServers(query, sortBy, sortDirection)
                } else {
                    _hasMore.value = false
                }

                // Update installation status for all loaded plugins
                updateInstalledStatus()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch MCP plugin list", e)

                // If network fetch fails but we have cache and it's the first page, try loading
                // from cache
                if (_mcpServers.value.isEmpty() && currentPage == 1 && officialCurrentPage == 1) {
                    Log.d(TAG, "Network fetch failed, trying to load from cache")
                    val cachedServers = cacheManager.loadMarketplaceFromCache()
                    if (cachedServers.isNotEmpty()) {
                        // Apply local sorting
                        val sortedServers =
                                MCPRepositoryUtils.sortServersByRecommended(cachedServers)

                        sortedServers.forEach { loadedServerIds.add(it.id) }
                        _mcpServers.value = sortedServers
                        currentPage++ // Prepare for next page load

                        // Update installation status
                        updateInstalledStatus()

                        _errorMessage.value = "Using cached data: ${e.message}"
                    } else {
                        Log.d(
                                TAG,
                                "Failed to load from cache, trying to load hardcoded official plugins"
                        )

                        // Try loading hardcoded official plugins
                        val (hardcodedServers, _) = officialSourceManager.fetchOfficialServers()
                        if (hardcodedServers.isNotEmpty()) {
                            // Apply local sorting
                            val sortedServers =
                                    MCPRepositoryUtils.sortServersByRecommended(hardcodedServers)

                            sortedServers.forEach { loadedServerIds.add(it.id) }
                            _mcpServers.value = sortedServers

                            // Update installation status
                            updateInstalledStatus()

                            Log.d(TAG, "Loaded ${hardcodedServers.size} hardcoded official plugins")
                            _errorMessage.value =
                                    "Unable to fetch data from network, showing official plugin list"
                        } else {
                            Log.e(TAG, "Cache load failed, no data available")
                            _errorMessage.value = "Failed to get data: ${e.message}"
                        }
                    }
                } else {
                    _errorMessage.value = "Failed to load more: ${e.message}"
                }

                _hasMore.value = hasMoreOfficialPages || hasMorePages // Still might have more data
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Fetches third-party servers from GitHub issues */
    private suspend fun fetchThirdPartyServers(
            query: String = "",
            sortBy: SortOptions = SortOptions.RECOMMENDED,
            sortDirection: SortDirection = SortDirection.DESC
    ) {
        try {
            // If there are no more pages, return
            if (!hasMorePages) {
                Log.d(TAG, "No more pages, stopping fetch")
                _hasMore.value = false
                return
            }

            // For regular listings or search, fetch directly from the network client
            val responseStr =
                    if (query.isNotBlank() || sortBy != SortOptions.RECOMMENDED) {
                        // Use direct network client search for custom queries
                        networkClient.searchMCPServers(
                                query = query,
                                sortBy = sortBy.value,
                                sortDirection = sortDirection.value,
                                page = currentPage
                        )
                    } else {
                        // For default sorting without query, fetch page directly
                        networkClient.fetchMCPServersPage(
                                currentPage,
                                GitHubConstants.DEFAULT_PAGE_SIZE
                        )
                    }

            // Check response validity
            if (responseStr.isNullOrBlank()) {
                Log.e(TAG, "Received empty or null response for page $currentPage")
                hasMorePages = false
                _hasMore.value = false
                return
            }

            // Parse the response - it might be a search result or direct issue list
            val (parsedServers, hasMore) =
                    if (query.isNotBlank() || sortBy != SortOptions.RECOMMENDED) {
                        // Parse search results from search API
                        try {
                            val jsonObject = JSONObject(responseStr)
                            val totalCount = jsonObject.optInt("total_count", 0)
                            val itemsArray = jsonObject.optJSONArray("items") ?: JSONArray()

                            if (itemsArray.length() == 0) {
                                Pair(emptyList<UIMCPServer>(), false)
                            } else {
                                // Parse search results into server objects
                                val itemsStr = itemsArray.toString()
                                val servers = MCPRepositoryUtils.parseIssuesResponse(itemsStr)
                                Pair(
                                        servers,
                                        itemsArray.length() >= GitHubConstants.DEFAULT_PAGE_SIZE &&
                                                currentPage < MAX_PAGES
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse search results", e)
                            Pair(emptyList<UIMCPServer>(), false)
                        }
                    } else {
                        // Direct GitHub issues API response
                        if (!responseStr.trim().startsWith("[")) {
                            Log.e(TAG, "Page $currentPage returned invalid JSON format")
                            Pair(emptyList<UIMCPServer>(), false)
                        } else {
                            val pageArray = JSONArray(responseStr)
                            val servers = MCPRepositoryUtils.parseIssuesResponse(responseStr)

                            // Cache if it's the first page
                            if (currentPage == 1) {
                                cacheManager.saveMarketplaceToCache(responseStr)
                            }

                            Pair(
                                    servers,
                                    pageArray.length() >= GitHubConstants.DEFAULT_PAGE_SIZE &&
                                            currentPage < MAX_PAGES
                            )
                        }
                    }

            // Filter out servers that have already been loaded
            val uniqueNewServers = parsedServers.filter { !loadedServerIds.contains(it.id) }

            if (uniqueNewServers.isNotEmpty()) {
                // Update loaded IDs set
                uniqueNewServers.forEach { loadedServerIds.add(it.id) }

                // Add new servers to current list
                val updatedServers = _mcpServers.value + uniqueNewServers

                // Apply sorting if needed
                val sortedServers =
                        if (sortBy == SortOptions.RECOMMENDED) {
                            MCPRepositoryUtils.sortServersByRecommended(updatedServers)
                        } else {
                            updatedServers
                        }

                _mcpServers.value = sortedServers

                // Update pagination state
                hasMorePages = hasMore && currentPage < MAX_PAGES

                // Only increase page number if there are more pages
                if (hasMorePages) {
                    currentPage++
                }

                Log.d(
                        TAG,
                        "Added ${uniqueNewServers.size} new third-party plugins, total: ${sortedServers.size}"
                )
            } else {
                // No new unique servers were found
                if (hasMore && currentPage < MAX_PAGES) {
                    // Try next page if there might be more data
                    currentPage++
                    // Recursive call to fetch next page
                    fetchThirdPartyServers(query, sortBy, sortDirection)
                    return
                } else {
                    // No more unique servers and no more pages
                    hasMorePages = false
                }
            }

            // Update the hasMore state
            _hasMore.value = hasMorePages
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch third-party plugins for page $currentPage", e)
            hasMorePages = false
            _hasMore.value = false
            throw e
        }
    }

    /**
     * Simplified refresh method - resets state and fetches plugins again Maintains the same loading
     * order: official plugins first, then third-party from GitHub issues
     */
    suspend fun refresh(
            query: String = "",
            sortBy: SortOptions = SortOptions.RECOMMENDED,
            sortDirection: SortDirection = SortDirection.DESC
    ) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting refresh operation for MCP plugins")

            // Reset state
            currentPage = 1
            hasMorePages = true
            loadedServerIds.clear()
            _mcpServers.value = emptyList()
            _hasMore.value = true

            // Reload data with force refresh - this will load official plugins first,
            // then third-party plugins from GitHub issues
            fetchMCPServers(
                    forceRefresh = true,
                    query = query,
                    sortBy = sortBy,
                    sortDirection = sortDirection
            )

            // Update installation status
            syncInstalledStatus()

            Log.d(TAG, "Refresh complete, now have ${_mcpServers.value.size} MCP plugins")
        }
    }

    /**
     * Gets all known categories
     *
     * @return List of unique categories
     */
    fun getAllCategories(): List<String> {
        val servers = _mcpServers.value
        if (servers.isEmpty()) return emptyList()

        return servers.map { it.category }.distinct().sortedBy { it }
    }

    /**
     * Installs an MCP plugin
     *
     * @param pluginId ID of the plugin to install
     * @param progressCallback Callback for installation progress
     * @return Installation result
     */
    suspend fun installMCPServer(
            pluginId: String,
            progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return withContext(Dispatchers.IO) {
            // Find corresponding server info
            val server = _mcpServers.value.find { it.id == pluginId }
            if (server == null) {
                Log.e(TAG, "Couldn't find server with ID $pluginId")
                return@withContext InstallResult.Error(
                        "Couldn't find corresponding server information"
                )
            }

            // Use plugin manager to install
            val result = pluginManager.installPlugin(pluginId, server, progressCallback)

            // Update installation status if needed
            if (result is InstallResult.Success) {
                updateInstalledStatus()
            }

            return@withContext result
        }
    }

    /**
     * Uninstalls an MCP plugin
     *
     * @param pluginId ID of the plugin to uninstall
     * @return true if uninstallation was successful, false otherwise
     */
    suspend fun uninstallMCPServer(pluginId: String): Boolean {
        val uninstallSuccess = pluginManager.uninstallPlugin(pluginId)

        // Update installation status if needed
        if (uninstallSuccess) {
            updateInstalledStatus()
        }

        return uninstallSuccess
    }

    /**
     * Gets the installed path of a plugin
     *
     * @param pluginId ID of the plugin
     * @return Installed path or null if not installed
     */
    fun getInstalledPluginPath(pluginId: String): String? {
        return pluginManager.getInstalledPluginPath(pluginId)
    }

    /**
     * Checks if a plugin is installed
     *
     * @param pluginId ID of the plugin to check
     * @return true if installed, false otherwise
     */
    fun isPluginInstalled(pluginId: String): Boolean {
        return pluginManager.isPluginInstalled(pluginId)
    }

    /** Synchronizes installation status from physical state to memory state */
    suspend fun syncInstalledStatus() {
        withContext(Dispatchers.IO) {
            try {
                pluginManager.scanInstalledPlugins()
                updateInstalledStatus()
                Log.d(
                        TAG,
                        "Synchronized plugin installation status, ${pluginManager.installedPluginIds.value.size} installed plugins"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync installation status", e)
            }
        }
    }

    /** Updates installation status for all servers in the list */
    private suspend fun updateInstalledStatus() {
        try {
            // Get current installed plugin IDs
            val installedIds = pluginManager.installedPluginIds.value

            // Update in-memory installation status
            val updatedServers =
                    _mcpServers.value.map { server ->
                        val isInstalled = installedIds.contains(server.id)

                        // Check installation status changed
                        if (server.isInstalled != isInstalled) {
                            // Installation status changed
                            if (isInstalled) {
                                // Server was installed - try to get original name and description
                                // from metadata
                                val pluginInfo = pluginManager.getInstalledPluginInfo(server.id)
                                if (pluginInfo?.metadata != null) {
                                    // Use original metadata from installation
                                    server.copy(
                                            name = pluginInfo.getOriginalName() ?: server.name,
                                            description = pluginInfo.getOriginalDescription()
                                                            ?: server.description,
                                            isInstalled = true
                                    )
                                } else {
                                    // No metadata, just update installation status
                                    server.copy(isInstalled = true)
                                }
                            } else {
                                // Server was uninstalled
                                server.copy(isInstalled = false)
                            }
                        } else if (isInstalled) {
                            // Already installed - make sure to use original metadata if available
                            val pluginInfo = pluginManager.getInstalledPluginInfo(server.id)
                            if (pluginInfo?.metadata != null) {
                                // Use original metadata from installation
                                server.copy(
                                        name = pluginInfo.getOriginalName() ?: server.name,
                                        description = pluginInfo.getOriginalDescription()
                                                        ?: server.description
                                )
                            } else {
                                server
                            }
                        } else {
                            // Not installed, no change needed
                            server
                        }
                    }

            _mcpServers.value = updatedServers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update installation status", e)
        }
    }

    /**
     * Gets all installed plugins
     *
     * @return List of installed MCPServer objects
     */
    fun getInstalledPlugins(): List<UIMCPServer> {
        return pluginManager.getInstalledPlugins(_mcpServers.value)
    }

    /**
     * Fetches an MCP server by its issue ID
     *
     * @param issueId The GitHub issue ID
     * @return The MCPServer object or null if not found
     */
    suspend fun fetchMCPServerByIssueId(issueId: String): UIMCPServer? {
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // First check already loaded servers
                _mcpServers.value.find { it.id == issueId }?.let {
                    Log.d(TAG, "Found server with ID $issueId in already loaded servers")
                    return@withContext
                }

                // Use network client to fetch the issue
                val responseStr = networkClient.fetchMCPServerByIssueId(issueId)
                if (responseStr == null) {
                    Log.e(TAG, "Failed to get issue $issueId from GitHub")
                    _errorMessage.value = "Failed to find the specified MCP server: $issueId"
                    return@withContext
                }

                // Wrap single issue response as an array to reuse existing parsing logic
                val wrappedResponse = "[$responseStr]"
                val parsedServers = MCPRepositoryUtils.parseIssuesResponse(wrappedResponse)

                if (parsedServers.isNotEmpty()) {
                    // Found server, add to list and update installation status
                    val server = parsedServers.first()
                    Log.d(TAG, "Successfully parsed server: ${server.name}")

                    // Check if we already have this server ID
                    if (!loadedServerIds.contains(server.id)) {
                        loadedServerIds.add(server.id)

                        // Add to current list
                        val updatedServers = _mcpServers.value + server
                        _mcpServers.value = updatedServers

                        // Update installation status
                        updateInstalledStatus()
                    }
                } else {
                    Log.e(TAG, "Failed to parse server information from response")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch issue ID $issueId", e)
                _errorMessage.value = "Failed to get server information: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }

        // Return found server
        return _mcpServers.value.find { it.id == issueId }
    }

    /**
     * Extracts issue ID from a GitHub URL
     *
     * @param url GitHub issue URL
     * @return Extracted issue ID or null if not a valid URL
     */
    fun extractIssueIdFromUrl(url: String): String? {
        val issueUrlRegex = ".+/issues/(\\d+)".toRegex()
        val match = issueUrlRegex.find(url)
        return match?.groupValues?.get(1)
    }

    /**
     * Fetches repository information
     *
     * @param repoUrl GitHub repository URL
     * @return Repository information or null if fetch failed
     */
    suspend fun fetchRepositoryInfo(repoUrl: String): RepoInfo? {
        withContext(Dispatchers.IO) {
            try {
                val responseStr =
                        networkClient.fetchRepositoryInfo(repoUrl) ?: return@withContext null

                // Parse repository information
                val repoJson = JSONObject(responseStr)

                // Extract repository information
                val starCount = repoJson.optInt("stargazers_count", 0)
                val watchCount = repoJson.optInt("watchers_count", 0)
                val forkCount = repoJson.optInt("forks_count", 0)
                val defaultBranch = repoJson.optString("default_branch", "main")
                val description = repoJson.optString("description", "")
                val lastUpdated =
                        MCPRepositoryUtils.formatUpdatedAt(repoJson.optString("updated_at", ""))
                val ownerName = repoJson.optJSONObject("owner")?.optString("login") ?: ""

                return@withContext RepoInfo(
                        owner = ownerName,
                        name = repoJson.optString("name", ""),
                        stars = starCount,
                        watchers = watchCount,
                        forks = forkCount,
                        defaultBranch = defaultBranch,
                        description = description,
                        lastUpdated = lastUpdated,
                        url = repoUrl
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch repository information: $repoUrl", e)
            }
        }

        return null
    }

    /**
     * Checks if an MCP server has updates
     *
     * @param mcpServer The server to check
     * @return Update information
     */
    suspend fun checkForUpdates(mcpServer: UIMCPServer): UpdateInfo {
        val repoInfo =
                fetchRepositoryInfo(mcpServer.repoUrl)
                        ?: return UpdateInfo(
                                hasUpdate = false,
                                currentVersion = mcpServer.version,
                                latestVersion = mcpServer.version,
                                updateUrl = mcpServer.repoUrl
                        )

        // Check if this server's repository has been updated
        val serverLastUpdated = MCPRepositoryUtils.parseDate(mcpServer.updatedAt)
        val repoLastUpdated = MCPRepositoryUtils.parseDate(repoInfo.lastUpdated)

        val hasUpdate =
                serverLastUpdated != null &&
                        repoLastUpdated != null &&
                        repoLastUpdated.after(serverLastUpdated)

        return UpdateInfo(
                hasUpdate = hasUpdate,
                currentVersion = mcpServer.version,
                latestVersion = repoInfo.defaultBranch, // Use default branch as latest version
                updateUrl = mcpServer.repoUrl,
                updateInfo = repoInfo
        )
    }

    /**
     * Fetches an MCP server by URL
     *
     * @param url GitHub issue URL
     * @return The MCPServer object or null if not found
     */
    suspend fun fetchMCPServerByUrl(url: String): UIMCPServer? {
        val issueId = extractIssueIdFromUrl(url)
        return if (issueId != null) {
            fetchMCPServerByIssueId(issueId)
        } else {
            _errorMessage.value = "Invalid GitHub Issue URL: $url"
            null
        }
    }

    /**
     * Tests if a logo URL is accessible
     *
     * @param logoUrl The URL to test
     * @return true if accessible, false otherwise
     */
    suspend fun testLogoUrl(logoUrl: String): Boolean {
        return networkClient.testLogoUrl(logoUrl)
    }

    /** Cleans up orphaned plugins */
    suspend fun cleanupOrphanedPlugins() {
        pluginManager.cleanupOrphanedPlugins()
    }

    /**
     * Load more servers when user scrolls to bottom This handles both official and third-party
     * servers in the correct order
     */
    suspend fun loadMoreServers(
            query: String = "",
            sortBy: SortOptions = SortOptions.RECOMMENDED,
            sortDirection: SortDirection = SortDirection.DESC
    ) {
        withContext(Dispatchers.IO) {
            // Don't do anything if already loading
            if (_isLoading.value) return@withContext

            _isLoading.value = true

            try {
                // First check if we have more official pages to load
                if (hasMoreOfficialPages) {
                    Log.d(TAG, "Loading more official servers, page: $officialCurrentPage")
                    val (officialServersPage, totalServers) =
                            officialSourceManager.fetchOfficialServers(
                                    forceRefresh = false,
                                    page = officialCurrentPage,
                                    pageSize = PAGE_SIZE
                            )

                    totalOfficialServers = totalServers

                    // Check if there are more official pages after this one
                    hasMoreOfficialPages = (officialCurrentPage * PAGE_SIZE) < totalOfficialServers

                    // Filter official servers if there's a search query
                    val filteredOfficialServers =
                            if (query.isNotBlank()) {
                                officialSourceManager.filterOfficialServers(
                                        officialServersPage,
                                        query
                                )
                            } else {
                                officialServersPage
                            }

                    if (filteredOfficialServers.isNotEmpty()) {
                        // Add to loaded IDs
                        filteredOfficialServers.forEach { loadedServerIds.add(it.id) }

                        // Append to existing list
                        val updatedServers = _mcpServers.value + filteredOfficialServers

                        // Apply sort if needed
                        val sortedServers =
                                if (sortBy == SortOptions.RECOMMENDED) {
                                    MCPRepositoryUtils.sortServersByRecommended(updatedServers)
                                } else {
                                    updatedServers
                                }

                        _mcpServers.value = sortedServers

                        // Increment page for next load
                        officialCurrentPage++

                        Log.d(
                                TAG,
                                "Added ${filteredOfficialServers.size} more official servers (page ${officialCurrentPage-1} of ${(totalOfficialServers + PAGE_SIZE - 1) / PAGE_SIZE})"
                        )
                    }

                    // Update _hasMore based on whether there are more pages of either type
                    _hasMore.value = hasMoreOfficialPages || hasMorePages

                    // Update installation status
                    updateInstalledStatus()
                } else if (hasMorePages) {
                    // If no more official pages, load third-party servers
                    Log.d(TAG, "Loading more third-party servers, page: $currentPage")

                    // Use existing fetchThirdPartyServers method
                    fetchThirdPartyServers(query, sortBy, sortDirection)
                } else {
                    // No more data to load
                    _hasMore.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load more servers", e)
                _errorMessage.value = "Failed to load more: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Adds a new remote server to the repository.
     *
     * @param server The remote server to add.
     */
    suspend fun addRemoteServer(server: MCPServer) {
        withContext(Dispatchers.IO) {
            // Ensure this is a remote server
            if (server.type != "remote") {
                Log.e(TAG, "addRemoteServer called with a non-remote server: ${server.id}")
                return@withContext
            }

            // Add to the main list if not already present
            if (_mcpServers.value.none { it.id == server.id }) {
                val uiServer = UIMCPServer(
                    id = server.id,
                    name = server.name,
                    description = server.description,
                    logoUrl = server.logoUrl,
                    stars = server.stars,
                    category = server.category,
                    requiresApiKey = server.requiresApiKey,
                    author = server.author,
                    isVerified = server.isVerified,
                    isInstalled = server.isInstalled,
                    version = server.version,
                    updatedAt = server.updatedAt,
                    longDescription = server.longDescription,
                    repoUrl = server.repoUrl,
                    type = server.type,
                    host = server.host,
                    port = server.port
                )
                _mcpServers.value = _mcpServers.value + uiServer
                loadedServerIds.add(server.id)
            }
            
            // Use the plugin manager to "install" the remote server, which just saves its metadata
            pluginManager.installRemotePlugin(server)
            
            // a little delay to ensure the UI updates
            syncInstalledStatus()
        }
    }

    // Initialize method to be called at app startup
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            // First scan for locally installed plugins
            syncInstalledStatus()

            // Then try to load MCP server list from cache
            if (cacheManager.isMarketplaceCacheValid()) {
                val cachedServers = cacheManager.loadMarketplaceFromCache()
                if (cachedServers.isNotEmpty()) {
                    loadedServerIds.addAll(cachedServers.map { it.id })
                    _mcpServers.value = MCPRepositoryUtils.sortServersByRecommended(cachedServers)
                    updateInstalledStatus()
                    Log.d(
                            TAG,
                            "Loaded ${cachedServers.size} servers from cache during initialization"
                    )
                }
            }
        }
    }

    // Read metadata from installed plugin directory
    private fun readPluginMetadata(installPath: String, serverId: String): UIMCPServer? {
        try {
            Log.d(TAG, "Reading metadata from installed plugin: $serverId at $installPath")
            val pluginDir = File(installPath)
            if (!pluginDir.exists() || !pluginDir.isDirectory) {
                Log.e(TAG, "Plugin directory doesn't exist: $installPath")
                return null
            }

            // Default values
            var name = serverId
            var description = ""
            var longDescription = ""
            var version = "local"
            var author = "Local Installation"
            var category = "Installed"
            var repoUrl = ""

            // Check for metadata.json first (added by MCPInstaller)
            val metadataFile = File(pluginDir, "metadata.json")
            if (metadataFile.exists()) {
                try {
                    Log.d(TAG, "Reading metadata.json for plugin $serverId")
                    val metadataJson = metadataFile.readText()
                    val metadata = Gson().fromJson(metadataJson, PluginMetadata::class.java)

                    // Use values from metadata.json if available
                    if (metadata.originalTitle.isNotEmpty()) {
                        name = metadata.originalTitle
                        Log.d(TAG, "Using original title from metadata.json: $name")
                    }

                    if (metadata.description.isNotEmpty()) {
                        description = metadata.description
                        Log.d(TAG, "Using description from metadata.json")
                    }

                    if (metadata.version.isNotEmpty()) {
                        version = metadata.version
                        Log.d(TAG, "Using version from metadata.json: $version")
                    }

                    if (metadata.author.isNotEmpty()) {
                        author = metadata.author
                        Log.d(TAG, "Using author from metadata.json: $author")
                    }

                    if (metadata.repoUrl.isNotEmpty()) {
                        repoUrl = metadata.repoUrl
                        Log.d(TAG, "Using repository URL from metadata.json: $repoUrl")
                    }

                    if (metadata.longDescription.isNotEmpty()) {
                        longDescription = metadata.longDescription
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing metadata.json for plugin $serverId", e)
                }
            }

            // Try to read README.md to get basic info if values are still default
            val readmeFile = File(pluginDir, "README.md")
            if (readmeFile.exists()) {
                Log.d(TAG, "Reading README.md for plugin $serverId")
                val readmeContent = readmeFile.readText()

                // Extract title (plugin name) if not set from metadata
                if (name == serverId) {
                    val titleMatch = Regex("# (.+)").find(readmeContent)
                    if (titleMatch != null) {
                        name = titleMatch.groupValues[1].trim()
                        Log.d(TAG, "Extracted plugin name from README: $name")
                    }
                }

                // Extract first paragraph as description if not set from metadata
                if (description.isEmpty()) {
                    val descMatch =
                            Regex("# .+\\s+(.+?)\\s*(?:##|$)", RegexOption.DOT_MATCHES_ALL)
                                    .find(readmeContent)
                    if (descMatch != null) {
                        description = descMatch.groupValues[1].trim()
                        Log.d(TAG, "Extracted plugin description from README")
                    }
                }

                // Try to extract version if present and not set from metadata
                if (version == "local") {
                    val versionMatch =
                            Regex("(?:version|版本)[:\\s]*(\\S+)", RegexOption.IGNORE_CASE)
                                    .find(readmeContent)
                    if (versionMatch != null) {
                        version = versionMatch.groupValues[1].trim()
                        Log.d(TAG, "Extracted plugin version from README: $version")
                    }
                }

                // Try to extract author if present and not set from metadata
                if (author == "Local Installation") {
                    val authorMatch =
                            Regex(
                                            "(?:author|作者)[:\\s]*(\\S+(?:\\s+\\S+)*)",
                                            RegexOption.IGNORE_CASE
                                    )
                                    .find(readmeContent)
                    if (authorMatch != null) {
                        author = authorMatch.groupValues[1].trim()
                        Log.d(TAG, "Extracted plugin author from README: $author")
                    }
                }

                // Try to extract repository URL if present and not set from metadata
                if (repoUrl.isEmpty()) {
                    val repoMatch =
                            Regex(
                                            "(?:(?:github|gitlab|repo|repository)\\s*(?:url|link)?)[:\\s]*(https?://\\S+)",
                                            RegexOption.IGNORE_CASE
                                    )
                                    .find(readmeContent)
                    if (repoMatch != null) {
                        repoUrl = repoMatch.groupValues[1].trim()
                        Log.d(TAG, "Extracted repository URL from README: $repoUrl")
                    }
                }

                // The full README content becomes the long description if not set from metadata
                if (longDescription.isEmpty()) {
                    longDescription = readmeContent
                }
            } else {
                Log.d(TAG, "README.md not found for plugin $serverId")
            }

            // Try to read package.json if it exists
            val packageJsonFile = File(pluginDir, "package.json")
            if (packageJsonFile.exists()) {
                try {
                    Log.d(TAG, "Reading package.json for plugin $serverId")
                    val packageJsonContent = packageJsonFile.readText()
                    val jsonObject = org.json.JSONObject(packageJsonContent)

                    // Override values if found in package.json and not set from metadata or README
                    if (jsonObject.has("name") && name == serverId) {
                        name = jsonObject.getString("name")
                        Log.d(TAG, "Using name from package.json: $name")
                    }

                    if (jsonObject.has("description") && description.isEmpty()) {
                        description = jsonObject.getString("description")
                        Log.d(TAG, "Using description from package.json")
                    }

                    if (jsonObject.has("version") && version == "local") {
                        version = jsonObject.getString("version")
                        Log.d(TAG, "Using version from package.json: $version")
                    }

                    if (jsonObject.has("author") && author == "Local Installation") {
                        val authorObj = jsonObject.opt("author")
                        author =
                                when {
                                    authorObj is org.json.JSONObject && authorObj.has("name") ->
                                            authorObj.getString("name")
                                    authorObj is String -> authorObj
                                    else -> author
                                }
                        Log.d(TAG, "Using author from package.json: $author")
                    }

                    if (jsonObject.has("repository") && repoUrl.isEmpty()) {
                        val repoObj = jsonObject.opt("repository")
                        repoUrl =
                                when {
                                    repoObj is org.json.JSONObject && repoObj.has("url") ->
                                            repoObj.getString("url")
                                    repoObj is String -> repoObj
                                    else -> repoUrl
                                }
                        Log.d(TAG, "Using repository URL from package.json: $repoUrl")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing package.json for plugin $serverId", e)
                }
            }

            // Build locally discovered server object
            Log.d(TAG, "Creating UIMCPServer object for local plugin $serverId")
            return UIMCPServer(
                    id = serverId,
                    name = name,
                    description = description.ifEmpty { "Locally installed server" },
                    logoUrl = "", // Locally discovered servers typically have no logo
                    stars = 0,
                    category = category,
                    requiresApiKey = false,
                    author = author,
                    isVerified = false,
                    isInstalled = true,
                    version = version,
                    updatedAt = "",
                    longDescription = longDescription,
                    repoUrl = repoUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read plugin metadata: $serverId", e)
            return null
        }
    }

    // 暴露插件安装器的功能
    /**
     * 获取已安装插件的信息
     *
     * @param pluginId 插件ID
     * @return 插件信息，包含路径和元数据信息
     */
    fun getInstalledPluginInfo(pluginId: String): MCPInstaller.InstalledPluginInfo? {
        return pluginManager.getInstalledPluginInfo(pluginId)
    }

    /**
     * 从本地ZIP文件安装MCP服务器插件
     *
     * @param serverId 自定义服务器ID
     * @param zipUri ZIP文件URI
     * @param name 插件名称
     * @param description 插件描述
     * @param author 插件作者
     * @param progressCallback 安装进度回调
     * @return 安装结果
     */
    suspend fun installMCPServerFromZip(
            serverId: String,
            zipUri: android.net.Uri,
            name: String,
            description: String,
            author: String,
            progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                Log.d(TAG, "从本地ZIP安装插件, ID: $serverId, Name: $name")

                // 创建临时Server对象
                val server =
                        com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer(
                                id = serverId,
                                name = name,
                                description = description,
                                logoUrl = "",
                                stars = 0,
                                category = "导入插件",
                                requiresApiKey = false,
                                author = author,
                                isVerified = false,
                                isInstalled = false,
                                version = "1.0.0",
                                updatedAt = "",
                                longDescription = description,
                                repoUrl = ""
                        )

                // 调用插件管理器从ZIP安装
                val result =
                        pluginManager.installPluginFromZip(
                                serverId,
                                zipUri,
                                server,
                                progressCallback
                        )

                // 安装完成后刷新已安装状态
                if (result is InstallResult.Success) {
                    syncInstalledStatus()
                }

                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "从本地ZIP安装插件失败", e)
                return@withContext InstallResult.Error("安装时出错: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }

        return InstallResult.Error("未知错误") // 这行代码不会执行，但需要保持语法完整性
    }
}

/** Plugin metadata saved during installation to preserve original metadata */
data class PluginMetadata(
        val originalTitle: String = "",
        val description: String = "",
        val version: String = "",
        val author: String = "",
        val repoUrl: String = "",
        val longDescription: String = ""
)

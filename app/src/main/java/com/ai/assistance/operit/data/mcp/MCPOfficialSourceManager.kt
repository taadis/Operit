package com.ai.assistance.operit.data.mcp

import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.OfficialMCPConstants
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.OfficialMCPConstants.RecommendedServers
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TAG
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer

/** Manages official MCP servers from fixed recommended sources */
class MCPOfficialSourceManager(private val cacheManager: MCPCacheManager) {

    /**
     * Fetches official MCP servers with pagination support
     *
     * @param forceRefresh Whether to force refresh from cache
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

        // Use hardcoded recommended servers
        Log.d(TAG, "Using recommended MCP servers list")
        val recommendedServers = getRecommendedMCPServers()

        // Cache the recommended servers
        if (recommendedServers.isNotEmpty()) {
            cacheManager.saveOfficialToCache(recommendedServers)
            Log.d(TAG, "Cached ${recommendedServers.size} recommended official servers")
        }

        // Return paginated results
        val totalServers = recommendedServers.size
        val paginatedServers = recommendedServers.paginateServers(page, pageSize)
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
     * Returns a list of recommended MCP servers
     *
     * @return List of recommended MCPServer objects
     */
    private fun getRecommendedMCPServers(): List<MCPServer> {
        // Get the hardcoded recommended servers from the constants
        return OfficialMCPConstants.RecommendedServers.getRecommendedMCPServers()
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

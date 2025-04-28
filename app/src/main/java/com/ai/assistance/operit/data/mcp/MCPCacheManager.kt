package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.os.Environment
import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.CACHE_DURATION_HOURS
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.OfficialMCPConstants
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TAG
import com.ai.assistance.operit.ui.features.mcp.model.MCPServer
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Manages caching operations for MCP servers */
class MCPCacheManager(private val context: Context) {

    companion object {
        private const val OPERIT_DIR_NAME = "Operit"
        private const val CACHE_DIR_NAME = "cache"
        private const val MARKETPLACE_CACHE_FILENAME = "mcp_servers_cache.json"
        private const val INSTALLED_PLUGINS_FILENAME = "installed_mcp_plugins.json"
    }

    // Get Operit directory
    private val operitDir by lazy {
        val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, OPERIT_DIR_NAME)
        if (!operitDir.exists()) {
            operitDir.mkdirs()
        }

        // Create cache subdirectory
        val cacheDir = File(operitDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        operitDir
    }

    // Cache directory
    private val operitCacheDir by lazy {
        File(operitDir, CACHE_DIR_NAME).also { if (!it.exists()) it.mkdirs() }
    }

    // Marketplace cache file
    private val marketplaceCacheFile by lazy {
        // Try to use Downloads/Operit/cache directory
        val cacheFile = File(operitCacheDir, MARKETPLACE_CACHE_FILENAME)

        // Make sure the directory is writable, otherwise fall back to app private cache
        if (operitCacheDir.canWrite()) {
            cacheFile
        } else {
            Log.w(TAG, "Cannot use external cache directory, using app private cache")
            File(context.cacheDir, MARKETPLACE_CACHE_FILENAME)
        }
    }

    // Official MCP repository cache file
    private val officialCacheFile by lazy {
        File(operitCacheDir, OfficialMCPConstants.CACHE_FILE_NAME).also {
            if (!operitCacheDir.exists()) operitCacheDir.mkdirs()
        }
    }

    // Installed plugins file
    private val installedPluginsFile by lazy {
        // Try to use Downloads/Operit directory
        val pluginsFile = File(operitDir, INSTALLED_PLUGINS_FILENAME)

        // Make sure the directory is writable, otherwise fall back to app private directory
        if (operitDir.canWrite()) {
            pluginsFile
        } else {
            Log.w(TAG, "Cannot use external storage directory, using app private directory")
            File(context.filesDir, INSTALLED_PLUGINS_FILENAME)
        }
    }

    /**
     * Saves marketplace servers data to cache
     *
     * @param jsonResponse The JSON response string to cache
     */
    fun saveMarketplaceToCache(jsonResponse: String) {
        try {
            marketplaceCacheFile.writeText(jsonResponse)
            Log.d(TAG, "Saved MCP server data to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache MCP server data", e)
        }
    }

    /**
     * Loads marketplace servers from cache
     *
     * @return List of parsed MCPServer objects
     */
    fun loadMarketplaceFromCache(): List<MCPServer> {
        try {
            if (marketplaceCacheFile.exists()) {
                val cachedJson = marketplaceCacheFile.readText()
                return MCPRepositoryUtils.parseIssuesResponse(cachedJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MCP server data from cache", e)
        }
        return emptyList()
    }

    /**
     * Checks if marketplace cache is valid (not expired)
     *
     * @return true if cache is valid, false otherwise
     */
    fun isMarketplaceCacheValid(): Boolean {
        return marketplaceCacheFile.exists() &&
                System.currentTimeMillis() - marketplaceCacheFile.lastModified() <
                        TimeUnit.HOURS.toMillis(CACHE_DURATION_HOURS.toLong())
    }

    /**
     * Saves official MCP servers to cache
     *
     * @param servers List of official MCP servers to cache
     */
    fun saveOfficialToCache(servers: List<MCPServer>) {
        try {
            val jsonArray = JSONArray()
            servers.forEach { server ->
                val jsonObject =
                        JSONObject().apply {
                            put("id", server.id)
                            put("name", server.name)
                            put("description", server.description)
                            put("logoUrl", server.logoUrl ?: JSONObject.NULL)
                            put("stars", server.stars)
                            put("category", server.category)
                            put("requiresApiKey", server.requiresApiKey)
                            put("author", server.author)
                            put("isVerified", server.isVerified)
                            put("isInstalled", server.isInstalled)
                            put("version", server.version)
                            put("updatedAt", server.updatedAt)
                            put("longDescription", server.longDescription)
                            put("repoUrl", server.repoUrl)
                        }
                jsonArray.put(jsonObject)
            }

            officialCacheFile.writeText(jsonArray.toString())
            Log.d(TAG, "Saved official MCP server data to cache: ${servers.size} servers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache official MCP server data", e)
        }
    }

    /**
     * Loads official MCP servers from cache
     *
     * @return List of parsed MCPServer objects
     */
    fun loadOfficialFromCache(): List<MCPServer> {
        try {
            if (officialCacheFile.exists()) {
                val cachedJson = officialCacheFile.readText()
                val jsonArray = JSONArray(cachedJson)
                val servers = mutableListOf<MCPServer>()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val server =
                            MCPServer(
                                    id = jsonObject.getString("id"),
                                    name = jsonObject.getString("name"),
                                    description = jsonObject.getString("description"),
                                    logoUrl =
                                            if (jsonObject.isNull("logoUrl")) null
                                            else jsonObject.getString("logoUrl"),
                                    stars = jsonObject.getInt("stars"),
                                    category = jsonObject.getString("category"),
                                    requiresApiKey = jsonObject.getBoolean("requiresApiKey"),
                                    author = jsonObject.getString("author"),
                                    isVerified = jsonObject.getBoolean("isVerified"),
                                    isInstalled = jsonObject.getBoolean("isInstalled"),
                                    version = jsonObject.getString("version"),
                                    updatedAt = jsonObject.getString("updatedAt"),
                                    longDescription = jsonObject.getString("longDescription"),
                                    repoUrl = jsonObject.getString("repoUrl")
                            )
                    servers.add(server)
                }

                return servers
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load official MCP server data from cache", e)
        }
        return emptyList()
    }

    /**
     * Checks if official MCP server cache is valid (not expired)
     *
     * @return true if cache is valid, false otherwise
     */
    fun isOfficialCacheValid(): Boolean {
        return officialCacheFile.exists() &&
                System.currentTimeMillis() - officialCacheFile.lastModified() <
                        TimeUnit.HOURS.toMillis(CACHE_DURATION_HOURS.toLong())
    }

    /**
     * Saves installed plugins list to file
     *
     * @param installedIds Set of installed plugin IDs
     */
    fun saveInstalledPlugins(installedIds: Set<String>) {
        try {
            val jsonArray = JSONArray()
            installedIds.forEach { jsonArray.put(it) }

            installedPluginsFile.writeText(jsonArray.toString())
            Log.d(TAG, "Saved installed plugins list: ${installedIds.size} plugins")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save installed plugins list", e)
        }
    }

    /**
     * Loads installed plugins list from file
     *
     * @return Set of installed plugin IDs
     */
    fun loadInstalledPlugins(): Set<String> {
        try {
            if (installedPluginsFile.exists()) {
                val jsonStr = installedPluginsFile.readText()
                val jsonArray = JSONArray(jsonStr)

                val installedIds = mutableSetOf<String>()
                for (i in 0 until jsonArray.length()) {
                    installedIds.add(jsonArray.getString(i))
                }

                Log.d(TAG, "Loaded installed plugins list: ${installedIds.size} plugins")
                return installedIds
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load installed plugins list", e)
        }
        return emptySet()
    }

    /** Clears all caches */
    fun clearAllCaches() {
        if (marketplaceCacheFile.exists()) {
            marketplaceCacheFile.delete()
            Log.d(TAG, "Cleared MCP server cache")
        }

        if (officialCacheFile.exists()) {
            officialCacheFile.delete()
            Log.d(TAG, "Cleared official MCP server cache")
        }
    }
}

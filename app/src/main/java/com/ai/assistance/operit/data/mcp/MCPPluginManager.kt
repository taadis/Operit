package com.ai.assistance.operit.data.mcp

import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TAG
import com.ai.assistance.operit.ui.features.mcp.model.MCPServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manages MCP plugin installation, uninstallation, and tracking */
class MCPPluginManager(
        private val cacheManager: MCPCacheManager,
        private val mcpInstaller: MCPInstaller
) {
    /** Set of installed plugin IDs */
    private val _installedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installedPluginIds: StateFlow<Set<String>> = _installedPluginIds.asStateFlow()

    init {
        // Load installed plugins on initialization
        scanInstalledPlugins()
    }

    /** Scans the file system for installed plugins and updates the internal state */
    fun scanInstalledPlugins() {
        try {
            val installedIds = mutableSetOf<String>()

            // First try loading saved installation records from file
            val cachedIds = cacheManager.loadInstalledPlugins()
            if (cachedIds.isNotEmpty()) {
                Log.d(TAG, "Loaded installed plugin records from cache: ${cachedIds.size}")
                installedIds.addAll(cachedIds)
            }

            // Then scan file system to verify and update
            val pluginsBaseDir = mcpInstaller.pluginsBaseDir
            if (pluginsBaseDir.exists() && pluginsBaseDir.isDirectory) {
                // Clear previous cached records to ensure we only keep actually existing plugins
                installedIds.clear()

                pluginsBaseDir.listFiles()?.forEach { pluginDir ->
                    if (pluginDir.isDirectory) {
                        // Directory name is the plugin ID
                        val pluginId = pluginDir.name
                        // Check if it's actually installed (has content)
                        if (mcpInstaller.isPluginInstalled(pluginId)) {
                            installedIds.add(pluginId)
                        }
                    }
                }
            }

            // Update in-memory installation state
            _installedPluginIds.value = installedIds

            // Save to file for next startup
            cacheManager.saveInstalledPlugins(installedIds)

            Log.d(TAG, "Scanned installed plugins: ${installedIds.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan installed plugins", e)
        }
    }

    /**
     * Installs an MCP plugin
     *
     * @param pluginId The ID of the plugin to install
     * @param server The server object containing installation details
     * @param progressCallback Callback to report installation progress
     * @return Result of the installation
     */
    suspend fun installPlugin(
            pluginId: String,
            server: MCPServer,
            progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        try {
            // Convert UI model to data model
            val dataServer =
                    com.ai.assistance.operit.data.mcp.MCPServer(
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
                            repoUrl = server.repoUrl
                    )

            // Perform the installation
            val result = mcpInstaller.installPlugin(dataServer, progressCallback)

            if (result is InstallResult.Success) {
                // Rescan installed plugins after successful installation
                scanInstalledPlugins()
                Log.d(TAG, "Plugin $pluginId installed successfully, path: ${result.pluginPath}")
            } else if (result is InstallResult.Error) {
                Log.e(TAG, "Failed to install plugin $pluginId: ${result.message}")
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception while installing plugin $pluginId", e)
            return InstallResult.Error("Installation exception: ${e.message}")
        }
    }

    /**
     * Uninstalls an MCP plugin
     *
     * @param pluginId The ID of the plugin to uninstall
     * @return true if uninstallation was successful, false otherwise
     */
    suspend fun uninstallPlugin(pluginId: String): Boolean {
        try {
            // Perform physical uninstallation
            val success = mcpInstaller.uninstallPlugin(pluginId)

            if (success) {
                // Rescan installed plugins after successful uninstallation
                scanInstalledPlugins()
                Log.d(TAG, "Plugin $pluginId uninstalled successfully")
            } else {
                Log.e(TAG, "Failed to uninstall plugin $pluginId")
            }

            return success
        } catch (e: Exception) {
            Log.e(TAG, "Exception while uninstalling plugin $pluginId", e)
            return false
        }
    }

    /**
     * Updates the installed status of a list of servers
     *
     * @param servers The list of servers to update
     * @return A new list with updated installation status
     */
    fun updateInstalledStatus(servers: List<MCPServer>): List<MCPServer> {
        val installedIds = _installedPluginIds.value
        return servers.map { server ->
            val isInstalled = installedIds.contains(server.id)
            if (server.isInstalled != isInstalled) {
                server.copy(isInstalled = isInstalled)
            } else {
                server
            }
        }
    }

    /**
     * Checks if a plugin is installed
     *
     * @param pluginId The ID of the plugin to check
     * @return true if the plugin is installed, false otherwise
     */
    fun isPluginInstalled(pluginId: String): Boolean {
        return mcpInstaller.isPluginInstalled(pluginId)
    }

    /**
     * Gets the installed path of a plugin
     *
     * @param pluginId The ID of the plugin
     * @return The installed path or null if not installed
     */
    fun getInstalledPluginPath(pluginId: String): String? {
        return mcpInstaller.getInstalledPluginPath(pluginId)
    }

    /**
     * Gets all installed plugins
     *
     * @param allServers List of all known servers
     * @return List of installed servers
     */
    fun getInstalledPlugins(allServers: List<MCPServer>): List<MCPServer> {
        val installedIds = _installedPluginIds.value
        return allServers.filter { installedIds.contains(it.id) }
    }

    /**
     * Cleans up orphaned plugins (empty directories)
     *
     * @return Number of cleaned plugins
     */
    suspend fun cleanupOrphanedPlugins(): Int {
        try {
            val pluginsBaseDir = mcpInstaller.pluginsBaseDir
            var removed = 0

            if (pluginsBaseDir.exists() && pluginsBaseDir.isDirectory) {
                pluginsBaseDir.listFiles()?.forEach { pluginDir ->
                    if (pluginDir.isDirectory) {
                        // Check if directory is empty or only contains zero-length files
                        val files = pluginDir.listFiles() ?: emptyArray()
                        if (files.isEmpty() || files.all { it.isFile && it.length() == 0L }) {
                            // Empty directory or only contains zero-length files, can be deleted
                            if (pluginDir.deleteRecursively()) {
                                removed++
                                Log.d(TAG, "Deleted empty plugin directory: ${pluginDir.name}")
                            }
                        }
                    }
                }
            }

            // If any plugins were removed, rescan
            if (removed > 0) {
                scanInstalledPlugins()
                Log.d(TAG, "Cleaned up $removed orphaned plugin records")
            }

            return removed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up orphaned plugins", e)
            return 0
        }
    }
}

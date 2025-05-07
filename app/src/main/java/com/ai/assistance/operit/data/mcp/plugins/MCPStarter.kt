package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.data.mcp.MCPConfigPreferences
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.MCPVscodeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP Plugin Starter
 *
 * Handles starting deployed MCP plugins via the bridge
 */
class MCPStarter(private val context: Context) {
    companion object {
        private const val TAG = "MCPStarter"
        private var bridgeInitialized = false
    }

    // Coroutine scope for async operations
    private val starterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Plugin start progress listener interface */
    interface PluginStartProgressListener {
        fun onPluginStarting(pluginId: String, index: Int, total: Int) {}
        fun onPluginStarted(pluginId: String, success: Boolean, index: Int, total: Int) {}
        fun onAllPluginsStarted(successCount: Int, totalCount: Int) {}
        fun onAllPluginsVerified(verificationResults: List<VerificationResult>) {}
    }

    /** Initialize and start the bridge */
    private suspend fun initBridge(): Boolean {
        if (bridgeInitialized) {
            val pingResult = MCPBridge.ping()
            if (pingResult != null) return true
        }

        // Deploy bridge to Termux
        if (!MCPBridge.deployBridge(context)) {
            Log.e(TAG, "Failed to deploy bridge")
            return false
        }

        // Start bridge
        if (!MCPBridge.startBridge(context)) {
            Log.e(TAG, "Failed to start bridge")
            return false
        }

        bridgeInitialized = true
        return true
    }

    /** Check if a server is running */
    private fun isServerRunning(serverName: String): Boolean {
        try {
            // Create a bridge instance
            val bridge = MCPBridge(context)

            // Get ping response
            val pingResult = kotlinx.coroutines.runBlocking { bridge.pingMcpService(serverName) }

            if (pingResult != null) {
                val result = pingResult.optJSONObject("result")
                val status = result?.optString("status")
                val mcpName = result?.optString("mcpName")

                // Only return true if this specific service is active
                return status == "ok" && mcpName == serverName
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking server status: ${e.message}")
            return false
        }
    }

    /** Start a plugin using the bridge */
    suspend fun startPlugin(pluginId: String, statusCallback: (StartStatus) -> Unit): Boolean {
        try {
            val mcpConfigPreferences = MCPConfigPreferences(context)

            // Check if plugin is deployed
            val isDeployed = mcpConfigPreferences.getDeploySuccessFlow(pluginId).first()
            if (!isDeployed) {
                statusCallback(StartStatus.Error("Plugin not deployed: $pluginId"))
                return false
            }

            statusCallback(StartStatus.InProgress("Starting plugin: $pluginId"))

            // Get plugin config
            val mcpLocalServer =
                    com.ai.assistance.operit.data.mcp.MCPLocalServer.getInstance(context)
            val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
            val config = MCPVscodeConfig.fromJson(pluginConfig)
            val serverName =
                    extractServerNameFromConfig(pluginConfig)
                            ?: pluginId.split("/").last().lowercase()

            // Check if plugin service is already running
            if (isServerRunning(serverName)) {
                statusCallback(StartStatus.Success("Plugin $pluginId is already running"))
                return true
            }

            // Get server command and args
            val serverConfig = config?.mcpServers?.get(serverName)
            if (serverConfig == null) {
                statusCallback(StartStatus.Error("Invalid plugin config: $pluginId"))
                return false
            }

            // Initialize bridge
            if (!initBridge()) {
                statusCallback(StartStatus.Error("Failed to initialize bridge"))
                return false
            }

            statusCallback(StartStatus.InProgress("Starting plugin via bridge..."))

            // Use MCPBridge instance
            val bridge = MCPBridge(context)
            val termuxPluginDir =
                    "/data/data/com.termux/files/home/mcp_plugins/${pluginId.split("/").last()}"

            // Register MCP service
            val registerResult =
                    bridge.registerMcpService(
                            name = serverName,
                            command = serverConfig.command,
                            args = serverConfig.args,
                            description = "MCP Server: $pluginId",
                            env = serverConfig.env
                    )

            if (registerResult == null || !registerResult.optBoolean("success", false)) {
                statusCallback(StartStatus.Error("Failed to register MCP service"))
                return false
            }

            // Start MCP service
            val cdCommand = "cd $termuxPluginDir"
            val bridgeCommand =
                    JSONObject().apply {
                        put("command", "spawn")
                        put("id", java.util.UUID.randomUUID().toString())
                        put(
                                "params",
                                JSONObject().apply {
                                    put("name", serverName)
                                    put("command", "bash")
                                    put(
                                            "args",
                                            JSONArray().apply {
                                                put("-c")
                                                put(
                                                        "$cdCommand && ${serverConfig.command} ${serverConfig.args.joinToString(" ")}"
                                                )
                                            }
                                    )

                                    // Add environment variables
                                    if (serverConfig.env.isNotEmpty()) {
                                        val envObj = JSONObject()
                                        serverConfig.env.forEach { (key, value) ->
                                            envObj.put(key, value)
                                        }
                                        put("env", envObj)
                                    }
                                }
                        )
                    }

            val spawnResult = MCPBridge.sendCommand(bridgeCommand)
            if (spawnResult == null || !spawnResult.optBoolean("success", false)) {
                statusCallback(StartStatus.Error("Failed to spawn MCP service"))
                return false
            }

            // Register server
            registerServerIfNeeded(serverName, serverConfig, pluginId)

            // Check service status
            delay(3000) // Wait for service to start

            // Final check
            val pingResult = kotlinx.coroutines.runBlocking { bridge.pingMcpService(serverName) }

            if (pingResult != null) {
                val result = pingResult.optJSONObject("result")
                val status = result?.optString("status")
                val mcpName = result?.optString("mcpName")

                if (status == "ok" && mcpName == serverName) {
                    statusCallback(StartStatus.Success("Plugin $pluginId started successfully"))
                    return true
                } else {
                    statusCallback(
                            StartStatus.Success(
                                    "Plugin $pluginId registered but may not be fully active. Current service: $mcpName"
                            )
                    )
                    return true
                }
            } else {
                statusCallback(StartStatus.Error("Failed to verify plugin status after launch"))
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting plugin", e)
            statusCallback(StartStatus.Error("Start error: ${e.message}"))
            return false
        }
    }

    /** Start all deployed plugins */
    fun startAllDeployedPlugins(
            progressListener: PluginStartProgressListener = object : PluginStartProgressListener {}
    ) {
        starterScope.launch {
            try {
                // Initialize bridge
                if (!initBridge()) {
                    progressListener.onAllPluginsStarted(0, 0)
                    return@launch
                }

                val mcpRepository = MCPRepository(context)
                val mcpConfigPreferences = MCPConfigPreferences(context)

                // Get deployed plugins
                val pluginList = mcpRepository.installedPluginIds.first()
                val deployedPlugins =
                        pluginList.filter { mcpConfigPreferences.getDeploySuccessFlow(it).first() }

                if (deployedPlugins.isEmpty()) {
                    progressListener.onAllPluginsStarted(0, 0)
                    return@launch
                }

                var successCount = 0

                // Start each plugin
                deployedPlugins.forEachIndexed { index, pluginId ->
                    progressListener.onPluginStarting(pluginId, index + 1, deployedPlugins.size)

                    val success =
                            startPlugin(pluginId) {
                                // Status callback is not used here
                            }

                    if (success) successCount++
                    progressListener.onPluginStarted(
                            pluginId,
                            success,
                            index + 1,
                            deployedPlugins.size
                    )
                }

                // Notify all plugins started
                progressListener.onAllPluginsStarted(successCount, deployedPlugins.size)

                // Verify plugins
                verifyPlugins(progressListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting plugins", e)
                progressListener.onAllPluginsStarted(0, 0)
            }
        }
    }

    /** Verify plugin statuses */
    private fun verifyPlugins(progressListener: PluginStartProgressListener) {
        starterScope.launch {
            try {
                delay(5000) // Wait for services to initialize
                val results = verifyAllMcpPlugins()
                progressListener.onAllPluginsVerified(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying plugins", e)
                progressListener.onAllPluginsVerified(emptyList())
            }
        }
    }

    /** Verify all MCP plugins */
    suspend fun verifyAllMcpPlugins(): List<VerificationResult> {
        val results = mutableListOf<VerificationResult>()

        try {
            val mcpRepository = MCPRepository(context)
            val mcpConfigPreferences = MCPConfigPreferences(context)
            val mcpLocalServer =
                    com.ai.assistance.operit.data.mcp.MCPLocalServer.getInstance(context)

            // Get deployed plugins
            val pluginList = mcpRepository.installedPluginIds.first()
            val deployedPlugins =
                    pluginList.filter { mcpConfigPreferences.getDeploySuccessFlow(it).first() }

            // Get registered services
            val bridge = MCPBridge(context)
            val listResponse = bridge.listMcpServices()
            val servicesList = mutableListOf<String>()

            if (listResponse?.optBoolean("success", false) == true) {
                val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                if (services != null) {
                    for (i in 0 until services.length()) {
                        val name = services.optJSONObject(i)?.optString("name", "")
                        if (!name.isNullOrEmpty()) {
                            servicesList.add(name)
                        }
                    }
                }
            }

            // Verify each plugin
            for (pluginId in deployedPlugins) {
                val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
                val serverName =
                        extractServerNameFromConfig(pluginConfig)
                                ?: pluginId.split("/").last().lowercase()

                if (!servicesList.contains(serverName)) {
                    results.add(
                            VerificationResult(
                                    pluginId = pluginId,
                                    serviceName = serverName,
                                    isResponding = false,
                                    responseTime = 0,
                                    details = "Service not registered"
                            )
                    )
                    continue
                }

                // Verify service status
                val client = MCPBridgeClient(context, serverName)
                val startTime = System.currentTimeMillis()
                val pingSuccess = client.pingSync()
                val responseTime = System.currentTimeMillis() - startTime

                if (pingSuccess) {
                    results.add(
                            VerificationResult(
                                    pluginId = pluginId,
                                    serviceName = serverName,
                                    isResponding = true,
                                    responseTime = responseTime,
                                    details = "Service is responding"
                            )
                    )
                } else {
                    results.add(
                            VerificationResult(
                                    pluginId = pluginId,
                                    serviceName = serverName,
                                    isResponding = false,
                                    responseTime = 0,
                                    details = "Service not responding"
                            )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying plugins", e)
        }

        return results
    }

    /** Extract server name from config */
    private fun extractServerNameFromConfig(configJson: String): String? {
        return MCPVscodeConfig.extractServerName(configJson)
    }

    /** Register server if needed */
    private fun registerServerIfNeeded(
            serverName: String,
            serverConfig: MCPVscodeConfig.ServerConfig,
            pluginId: String
    ) {
        try {
            val mcpManager = MCPManager.getInstance(context)

            val extraDataMap = mutableMapOf<String, String>()
            extraDataMap["command"] = serverConfig.command
            extraDataMap["args"] = serverConfig.args.joinToString(",")

            serverConfig.env.forEach { (key, value) -> extraDataMap["env_$key"] = value }

            val mcpServerConfig =
                    MCPServerConfig(
                            name = serverName,
                            endpoint = "mcp://plugin/$serverName",
                            description = "MCP Server from plugin: $pluginId",
                            capabilities = listOf("tools", "resources"),
                            extraData = extraDataMap
                    )

            mcpManager.registerServer(serverName, mcpServerConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register server", e)
        }
    }

    /** Start status */
    sealed class StartStatus {
        object NotStarted : StartStatus()
        data class InProgress(val message: String) : StartStatus()
        data class Success(val message: String) : StartStatus()
        data class Error(val message: String) : StartStatus()
    }

    /** Verification result */
    data class VerificationResult(
            val pluginId: String,
            val serviceName: String,
            val isResponding: Boolean,
            val responseTime: Long,
            val details: String = ""
    )
}

package com.ai.assistance.operit.data.mcp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define the DataStore at the module level
private val Context.mcpConfigDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "mcp_config")

/**
 * MCPConfigPreferences - 管理MCP配置的数据存储
 *
 * 负责存储和管理与MCP相关的配置项，如API密钥、缓存设置、默认服务器等
 */
class MCPConfigPreferences(private val context: Context) {

    // Define preference keys
    companion object {
        // API相关配置
        val MCP_API_KEY = stringPreferencesKey("mcp_api_key")
        val MCP_API_ENDPOINT = stringPreferencesKey("mcp_api_endpoint")
        val DEFAULT_MCP_API_ENDPOINT = "https://api.modelcontextprotocol.org"

        // 缓存配置
        val CACHE_DURATION_HOURS = intPreferencesKey("cache_duration_hours")
        val DEFAULT_CACHE_DURATION_HOURS = 24

        // 显示选项
        val SHOW_VERIFIED_ONLY = booleanPreferencesKey("show_verified_only")
        val DEFAULT_SHOW_VERIFIED_ONLY = false

        // 网络配置
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 2

        // 默认排序
        val DEFAULT_SORT_OPTION = stringPreferencesKey("default_sort_option")
        val DEFAULT_DEFAULT_SORT_OPTION = "RECOMMENDED"

        // 自动更新
        val AUTO_UPDATE_PLUGINS = booleanPreferencesKey("auto_update_plugins")
        val DEFAULT_AUTO_UPDATE_PLUGINS = true

        // 自动部署开关状态前缀 - 用于每个插件的自动部署开关状态
        const val AUTO_DEPLOY_PREFIX = "auto_deploy_plugin_"
        val DEFAULT_AUTO_DEPLOY = false

        // 部署成功状态前缀 - 用于标记插件是否已成功部署
        const val DEPLOY_SUCCESS_PREFIX = "deploy_success_plugin_"
        val DEFAULT_DEPLOY_SUCCESS = false

        // 最后部署时间前缀 - 记录插件最后一次成功部署的时间
        const val LAST_DEPLOY_TIME_PREFIX = "last_deploy_time_plugin_"
    }

    // API Key Flow
    val mcpApiKeyFlow: Flow<String> =
            context.mcpConfigDataStore.data.map { preferences -> preferences[MCP_API_KEY] ?: "" }

    // API Endpoint Flow
    val mcpApiEndpointFlow: Flow<String> =
            context.mcpConfigDataStore.data.map { preferences ->
                preferences[MCP_API_ENDPOINT] ?: DEFAULT_MCP_API_ENDPOINT
            }

    // Cache Duration Flow
    val cacheDurationHoursFlow: Flow<Int> =
            context.mcpConfigDataStore.data.map { preferences ->
                preferences[CACHE_DURATION_HOURS] ?: DEFAULT_CACHE_DURATION_HOURS
            }

    // Show Verified Only Flow
    val showVerifiedOnlyFlow: Flow<Boolean> =
            context.mcpConfigDataStore.data.map { preferences ->
                preferences[SHOW_VERIFIED_ONLY] ?: DEFAULT_SHOW_VERIFIED_ONLY
            }

    // Max Concurrent Downloads Flow
    val maxConcurrentDownloadsFlow: Flow<Int> =
            context.mcpConfigDataStore.data.map { preferences ->
                preferences[MAX_CONCURRENT_DOWNLOADS] ?: DEFAULT_MAX_CONCURRENT_DOWNLOADS
            }

    // Default Sort Option Flow
    val defaultSortOptionFlow: Flow<String> =
            context.mcpConfigDataStore.data.map { preferences ->
                preferences[DEFAULT_SORT_OPTION] ?: DEFAULT_DEFAULT_SORT_OPTION
            }

    // Auto Update Plugins Flow
    val autoUpdatePluginsFlow: Flow<Boolean> =
            context.mcpConfigDataStore.data.map { preferences ->
                preferences[AUTO_UPDATE_PLUGINS] ?: DEFAULT_AUTO_UPDATE_PLUGINS
            }

    // 获取插件的自动部署状态
    fun getAutoDeployFlow(pluginId: String): Flow<Boolean> {
        val key = booleanPreferencesKey("${AUTO_DEPLOY_PREFIX}${pluginId}")
        return context.mcpConfigDataStore.data.map { preferences ->
            preferences[key] ?: DEFAULT_AUTO_DEPLOY
        }
    }

    // 保存插件的自动部署状态
    suspend fun saveAutoDeploy(pluginId: String, autoDeploy: Boolean) {
        val key = booleanPreferencesKey("${AUTO_DEPLOY_PREFIX}${pluginId}")
        context.mcpConfigDataStore.edit { preferences -> preferences[key] = autoDeploy }
    }

    // Save API Key
    suspend fun saveMcpApiKey(apiKey: String) {
        context.mcpConfigDataStore.edit { preferences -> preferences[MCP_API_KEY] = apiKey }
    }

    // Save API Endpoint
    suspend fun saveMcpApiEndpoint(endpoint: String) {
        context.mcpConfigDataStore.edit { preferences -> preferences[MCP_API_ENDPOINT] = endpoint }
    }

    // Save Cache Duration
    suspend fun saveCacheDurationHours(hours: Int) {
        context.mcpConfigDataStore.edit { preferences -> preferences[CACHE_DURATION_HOURS] = hours }
    }

    // Save Show Verified Only
    suspend fun saveShowVerifiedOnly(showVerifiedOnly: Boolean) {
        context.mcpConfigDataStore.edit { preferences ->
            preferences[SHOW_VERIFIED_ONLY] = showVerifiedOnly
        }
    }

    // Save Max Concurrent Downloads
    suspend fun saveMaxConcurrentDownloads(maxDownloads: Int) {
        context.mcpConfigDataStore.edit { preferences ->
            preferences[MAX_CONCURRENT_DOWNLOADS] = maxDownloads
        }
    }

    // Save Default Sort Option
    suspend fun saveDefaultSortOption(sortOption: String) {
        context.mcpConfigDataStore.edit { preferences ->
            preferences[DEFAULT_SORT_OPTION] = sortOption
        }
    }

    // Save Auto Update Plugins
    suspend fun saveAutoUpdatePlugins(autoUpdate: Boolean) {
        context.mcpConfigDataStore.edit { preferences ->
            preferences[AUTO_UPDATE_PLUGINS] = autoUpdate
        }
    }

    // Reset to defaults
    suspend fun resetToDefaults() {
        context.mcpConfigDataStore.edit { preferences ->
            preferences[MCP_API_ENDPOINT] = DEFAULT_MCP_API_ENDPOINT
            preferences[CACHE_DURATION_HOURS] = DEFAULT_CACHE_DURATION_HOURS
            preferences[SHOW_VERIFIED_ONLY] = DEFAULT_SHOW_VERIFIED_ONLY
            preferences[MAX_CONCURRENT_DOWNLOADS] = DEFAULT_MAX_CONCURRENT_DOWNLOADS
            preferences[DEFAULT_SORT_OPTION] = DEFAULT_DEFAULT_SORT_OPTION
            preferences[AUTO_UPDATE_PLUGINS] = DEFAULT_AUTO_UPDATE_PLUGINS
            // API Key is not reset
        }
    }

    // 获取插件的部署成功状态
    fun getDeploySuccessFlow(pluginId: String): Flow<Boolean> {
        val key = booleanPreferencesKey("${DEPLOY_SUCCESS_PREFIX}${pluginId}")
        return context.mcpConfigDataStore.data.map { preferences ->
            preferences[key] ?: DEFAULT_DEPLOY_SUCCESS
        }
    }

    // 保存插件的部署成功状态
    suspend fun saveDeploySuccess(pluginId: String, deploySuccess: Boolean) {
        val key = booleanPreferencesKey("${DEPLOY_SUCCESS_PREFIX}${pluginId}")
        context.mcpConfigDataStore.edit { preferences -> preferences[key] = deploySuccess }

        // 如果部署成功，同时更新最后部署时间
        if (deploySuccess) {
            saveLastDeployTime(pluginId, System.currentTimeMillis())
        }
    }

    // 获取插件最后部署时间
    fun getLastDeployTimeFlow(pluginId: String): Flow<Long> {
        val key = stringPreferencesKey("${LAST_DEPLOY_TIME_PREFIX}${pluginId}")
        return context.mcpConfigDataStore.data.map { preferences ->
            preferences[key]?.toLongOrNull() ?: 0L
        }
    }

    // 保存插件最后部署时间
    private suspend fun saveLastDeployTime(pluginId: String, timestamp: Long) {
        val key = stringPreferencesKey("${LAST_DEPLOY_TIME_PREFIX}${pluginId}")
        context.mcpConfigDataStore.edit { preferences -> preferences[key] = timestamp.toString() }
    }
}

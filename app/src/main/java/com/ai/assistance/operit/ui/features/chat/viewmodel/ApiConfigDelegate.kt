package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 委托类，负责管理API配置相关功能 */
class ApiConfigDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val onConfigChanged: (EnhancedAIService) -> Unit
) {
    // Preferences
    private val apiPreferences = ApiPreferences(context)

    // State flows
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _apiEndpoint = MutableStateFlow(ApiPreferences.DEFAULT_API_ENDPOINT)
    val apiEndpoint: StateFlow<String> = _apiEndpoint.asStateFlow()

    private val _modelName = MutableStateFlow(ApiPreferences.DEFAULT_MODEL_NAME)
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _showThinking = MutableStateFlow(ApiPreferences.DEFAULT_SHOW_THINKING)
    val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()

    private val _enableAiPlanning = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_AI_PLANNING)
    val enableAiPlanning: StateFlow<Boolean> = _enableAiPlanning.asStateFlow()

    private val _memoryOptimization = MutableStateFlow(ApiPreferences.DEFAULT_MEMORY_OPTIMIZATION)
    val memoryOptimization: StateFlow<Boolean> = _memoryOptimization.asStateFlow()

    init {
        // Load settings from preferences
        initializeSettingsCollection()
    }

    private fun initializeSettingsCollection() {
        // Load API key
        viewModelScope.launch {
            apiPreferences.apiKeyFlow.collect { key ->
                _apiKey.value = key
                checkAndInitializeService()
            }
        }

        // Load API endpoint
        viewModelScope.launch {
            apiPreferences.apiEndpointFlow.collect { endpoint ->
                _apiEndpoint.value = endpoint
                checkAndInitializeService()
            }
        }

        // Load model name
        viewModelScope.launch {
            apiPreferences.modelNameFlow.collect { model ->
                _modelName.value = model
                checkAndInitializeService()
            }
        }

        // Collect show thinking preference
        viewModelScope.launch {
            apiPreferences.showThinkingFlow.collect { showThinkingValue ->
                _showThinking.value = showThinkingValue
            }
        }

        // Collect AI planning setting
        viewModelScope.launch {
            apiPreferences.enableAiPlanningFlow.collect { enableAiPlanningValue ->
                _enableAiPlanning.value = enableAiPlanningValue
            }
        }

        // Collect memory optimization preference
        viewModelScope.launch {
            apiPreferences.memoryOptimizationFlow.collect { enabled ->
                _memoryOptimization.value = enabled
            }
        }
    }

    /** 检查API配置是否完整，如果是，则初始化AI服务 */
    private fun checkAndInitializeService() {
        val key = _apiKey.value
        val endpoint = _apiEndpoint.value
        val model = _modelName.value

        if (key.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank()) {
            // 创建新的AI服务实例
            val enhancedAiService = EnhancedAIService(endpoint, key, model, context)

            // 通知ViewModel配置已更改
            onConfigChanged(enhancedAiService)

            // 更新已配置状态
            _isConfigured.value = true
        }
    }

    /** 更新API密钥 */
    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    /** 更新API端点 */
    fun updateApiEndpoint(endpoint: String) {
        _apiEndpoint.value = endpoint
    }

    /** 更新模型名称 */
    fun updateModelName(model: String) {
        _modelName.value = model
    }

    /** 保存API设置 */
    fun saveApiSettings() {
        viewModelScope.launch {
            apiPreferences.saveApiSettings(_apiKey.value, _apiEndpoint.value, _modelName.value)

            // 重新初始化服务
            val enhancedAiService =
                    EnhancedAIService(_apiEndpoint.value, _apiKey.value, _modelName.value, context)

            // 通知ViewModel配置已更改
            onConfigChanged(enhancedAiService)

            // 更新已配置状态
            _isConfigured.value = true
        }
    }

    /**
     * 使用默认配置继续，不保存用户输入
     * @return 是否成功使用默认配置
     */
    fun useDefaultConfig(): Boolean {
        // 检查是否有可用的默认配置
        if (_apiEndpoint.value.isNotBlank() &&
                        _apiKey.value.isNotBlank() &&
                        _modelName.value.isNotBlank()
        ) {
            // 直接标记为已配置
            _isConfigured.value = true

            // 初始化AI服务
            val enhancedAiService =
                    EnhancedAIService(_apiEndpoint.value, _apiKey.value, _modelName.value, context)

            // 通知ViewModel配置已更改
            onConfigChanged(enhancedAiService)

            return true
        }
        return false
    }

    /** 切换AI计划功能 */
    fun toggleAiPlanning() {
        viewModelScope.launch {
            val newValue = !_enableAiPlanning.value
            apiPreferences.saveEnableAiPlanning(newValue)
            _enableAiPlanning.value = newValue
        }
    }

    /** 切换显示思考过程 */
    fun toggleShowThinking() {
        viewModelScope.launch {
            val newValue = !_showThinking.value
            apiPreferences.saveShowThinking(newValue)
            _showThinking.value = newValue
        }
    }

    /** 切换内存优化 */
    fun toggleMemoryOptimization() {
        viewModelScope.launch {
            val newValue = !_memoryOptimization.value
            apiPreferences.saveMemoryOptimization(newValue)
            _memoryOptimization.value = newValue
        }
    }
}

package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 委托类，负责管理用户偏好配置和API密钥 */
class ApiConfigDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val onConfigChanged: (EnhancedAIService) -> Unit
) {
    companion object {
        private const val TAG = "ApiConfigDelegate"
        private const val DEFAULT_CONFIG_ID = "default" // 默认配置ID
    }

    // Preferences
    private val apiPreferences = ApiPreferences(context)
    private val modelConfigManager = ModelConfigManager(context)

    // State flows
    private val _isConfigured = MutableStateFlow(true) // 默认已配置
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _enableAiPlanning = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_AI_PLANNING)
    val enableAiPlanning: StateFlow<Boolean> = _enableAiPlanning.asStateFlow()

    private val _memoryOptimization = MutableStateFlow(ApiPreferences.DEFAULT_MEMORY_OPTIMIZATION)
    val memoryOptimization: StateFlow<Boolean> = _memoryOptimization.asStateFlow()

    // 为了兼容现有代码，添加API密钥状态流
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    init {
        // 初始化ModelConfigManager
        viewModelScope.launch {
            modelConfigManager.initializeIfNeeded()

            // 加载默认配置中的API密钥
            try {
                val defaultConfig = modelConfigManager.getModelConfigFlow(DEFAULT_CONFIG_ID).first()
                _apiKey.value = defaultConfig.apiKey

                Log.d(TAG, "已从ModelConfigManager加载默认API密钥")
            } catch (e: Exception) {
                Log.e(TAG, "加载默认API密钥失败: ${e.message}", e)
            }
        }

        // 加载用户偏好设置
        initializeSettingsCollection()

        // 创建新的AI服务实例并通知
        val enhancedAiService = EnhancedAIService(context)
        onConfigChanged(enhancedAiService)
    }

    private fun initializeSettingsCollection() {
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

    /**
     * 使用默认配置继续
     * @return 总是返回true，因为无需特定配置
     */
    fun useDefaultConfig(): Boolean {
        // 在多AI提供商架构下，总是可以使用默认配置
        Log.d(TAG, "使用默认配置初始化服务")
        val enhancedAiService = EnhancedAIService(context)

        // 通知ViewModel配置已更改
        onConfigChanged(enhancedAiService)
        return true
    }

    /** 更新API密钥 */
    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    /** 保存API设置 */
    fun saveApiSettings() {
        viewModelScope.launch {
            try {
                // 获取当前配置
                val currentConfig = modelConfigManager.getModelConfigFlow(DEFAULT_CONFIG_ID).first()

                // 只更新API密钥，保留其他现有配置
                modelConfigManager.updateModelConfig(
                        DEFAULT_CONFIG_ID,
                        _apiKey.value,
                        currentConfig.apiEndpoint, // 保留现有端点
                        currentConfig.modelName, // 保留现有模型名称
                        currentConfig.apiProviderType // 保留现有提供商类型
                )

                Log.d(TAG, "API密钥已保存到ModelConfigManager")

                // 直接调用初始化服务
                val enhancedAiService = EnhancedAIService(context)

                // 通知ViewModel配置已更改
                onConfigChanged(enhancedAiService)

                // 更新已配置状态
                _isConfigured.value = true
            } catch (e: Exception) {
                Log.e(TAG, "保存API密钥失败: ${e.message}", e)
            }
        }
    }

    /** 切换AI计划功能 */
    fun toggleAiPlanning() {
        viewModelScope.launch {
            val newValue = !_enableAiPlanning.value
            apiPreferences.saveEnableAiPlanning(newValue)
            _enableAiPlanning.value = newValue
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

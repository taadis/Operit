package com.ai.assistance.operit.ui.features.assistant.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Live2DConfig
import com.ai.assistance.operit.data.model.Live2DModel
import com.ai.assistance.operit.data.repository.Live2DRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Live2D视图模型 负责管理Live2D模型的UI状态和业务逻辑 */
class Live2DViewModel(private val repository: Live2DRepository) : ViewModel() {

    // UI状态
    data class UiState(
            val isLoading: Boolean = false,
            val models: List<Live2DModel> = emptyList(),
            val currentModel: Live2DModel? = null,
            val config: Live2DConfig? = null,
            val errorMessage: String? = null,
            val operationSuccess: Boolean = false,
            val isScanning: Boolean = false,
            val expressionToApply: String? = null,
            val manualExpression: String = "",
            val triggerRandomTap: Long? = null,
            val scrollPosition: Int = 0, // 添加滚动位置状态
            val isImporting: Boolean = false // 导入模型状态
    )

    // 当前UI状态
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // 监听Repository中的数据变化
        viewModelScope.launch {
            repository.models.collectLatest { models -> updateUiState(models = models) }
        }

        viewModelScope.launch {
            repository.currentConfig.collectLatest { config ->
                // 当配置更新时，同时更新当前模型
                val currentModel =
                        if (config != null) {
                            repository.models.value.find { it.id == config.modelId }
                        } else null

                updateUiState(config = config, currentModel = currentModel)
            }
        }
    }

    /** 更新UI状态 */
    private fun updateUiState(
            isLoading: Boolean? = null,
            models: List<Live2DModel>? = null,
            currentModel: Live2DModel? = null,
            config: Live2DConfig? = null,
            errorMessage: String? = null,
            operationSuccess: Boolean? = null,
            isScanning: Boolean? = null,
            expressionToApply: String? = null,
            manualExpression: String? = null,
            triggerRandomTap: Long? = null,
            isImporting: Boolean? = null
    ) {
        val currentState = _uiState.value
        _uiState.value =
                currentState.copy(
                        isLoading = isLoading ?: currentState.isLoading,
                        models = models ?: currentState.models,
                        currentModel = currentModel ?: currentState.currentModel,
                        config = config ?: currentState.config,
                        errorMessage = errorMessage,
                        operationSuccess = operationSuccess ?: currentState.operationSuccess,
                        isScanning = isScanning ?: currentState.isScanning,
                        expressionToApply = expressionToApply,
                        manualExpression = manualExpression ?: currentState.manualExpression,
                        triggerRandomTap = triggerRandomTap,
                        scrollPosition = currentState.scrollPosition,
                        isImporting = isImporting ?: currentState.isImporting
                )
    }

    /** 切换模型 */
    fun switchModel(modelId: String) {
        viewModelScope.launch { repository.switchModel(modelId) }
    }

    /** 更新缩放比例 */
    fun updateScale(scale: Float) {
        val currentConfig = _uiState.value.config ?: return
        val updatedConfig = currentConfig.copy(scale = scale)
        viewModelScope.launch { repository.updateConfig(updatedConfig) }
    }

    /** 更新X轴偏移 */
    fun updateTranslateX(translateX: Float) {
        val currentConfig = _uiState.value.config ?: return
        val updatedConfig = currentConfig.copy(translateX = translateX)
        viewModelScope.launch { repository.updateConfig(updatedConfig) }
    }

    /** 更新Y轴偏移 */
    fun updateTranslateY(translateY: Float) {
        val currentConfig = _uiState.value.config ?: return
        val updatedConfig = currentConfig.copy(translateY = translateY)
        viewModelScope.launch { repository.updateConfig(updatedConfig) }
    }

    /** 更新嘴部形状 */
    fun updateMouthForm(value: Float) {
        val currentConfig = _uiState.value.config ?: return
        val updatedConfig = currentConfig.copy(mouthForm = value)
        viewModelScope.launch { repository.updateConfig(updatedConfig) }
    }

    /** 更新嘴部开合度 */
    fun updateMouthOpenY(value: Float) {
        val currentConfig = _uiState.value.config ?: return
        val updatedConfig = currentConfig.copy(mouthOpenY = value)
        viewModelScope.launch { repository.updateConfig(updatedConfig) }
    }

    /** 设置自动眨眼 */
    fun setAutoBlinkEnabled(enabled: Boolean) {
        val currentConfig = _uiState.value.config ?: return
        val updatedConfig = currentConfig.copy(autoBlinkEnabled = enabled)
        viewModelScope.launch { repository.updateConfig(updatedConfig) }
    }

    /** 设置是否渲染背景 */
    fun setRenderBack(enabled: Boolean) {
        val currentConfig = _uiState.value.config ?: return
        val updatedConfig = currentConfig.copy(renderBack = enabled)
        viewModelScope.launch { repository.updateConfig(updatedConfig) }
    }

    /** 扫描用户模型 */
    fun scanUserModels() {
        updateUiState(isScanning = true)
        viewModelScope.launch {
            try {
                val success = repository.scanUserModels()
                updateUiState(
                        isScanning = false,
                        operationSuccess = success,
                        errorMessage = if (!success) "扫描用户模型失败" else null
                )
            } catch (e: Exception) {
                updateUiState(
                        isScanning = false,
                        operationSuccess = false,
                        errorMessage = "扫描用户模型出错: ${e.message}"
                )
            }
        }
    }

    /** 删除用户模型 */
    fun deleteUserModel(modelId: String) {
        updateUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val success = repository.deleteUserModel(modelId)
                updateUiState(
                        isLoading = false,
                        operationSuccess = success,
                        errorMessage = if (!success) "删除模型失败" else null
                )
            } catch (e: Exception) {
                updateUiState(
                        isLoading = false,
                        operationSuccess = false,
                        errorMessage = "删除模型出错: ${e.message}"
                )
            }
        }
    }

    /** 导入Live2D模型ZIP文件 */
    fun importModelFromZip(uri: Uri) {
        updateUiState(isLoading = true, isImporting = true)
        viewModelScope.launch {
            try {
                val success = repository.importModelFromZip(uri)
                updateUiState(
                        isLoading = false,
                        isImporting = false,
                        operationSuccess = success,
                        errorMessage = if (!success) "导入Live2D模型失败" else null
                )
            } catch (e: Exception) {
                updateUiState(
                        isLoading = false,
                        isImporting = false,
                        operationSuccess = false,
                        errorMessage = "导入模型出错: ${e.message}"
                )
            }
        }
    }

    /** 清除错误消息 */
    fun clearErrorMessage() {
        updateUiState(errorMessage = null)
    }

    /** 清除操作成功状态 */
    fun clearOperationSuccess() {
        updateUiState(operationSuccess = false)
    }

    /** 应用表情 */
    fun applyExpression(expression: String) {
        // 使用时间戳确保即使是同一个表情也能被触发
        updateUiState(expressionToApply = "$expression:${System.currentTimeMillis()}")
    }

    /** 表情已应用（由UI调用） */
    fun onExpressionApplied() {
        updateUiState(expressionToApply = null)
    }

    /** 更新手动输入的表情名称 */
    fun updateManualExpression(name: String) {
        updateUiState(manualExpression = name)
    }

    /** 触发一次随机点击 */
    fun triggerRandomTap() {
        updateUiState(triggerRandomTap = System.currentTimeMillis())
    }

    /** 随机点击已处理（由UI调用） */
    fun onRandomTapHandled() {
        updateUiState(triggerRandomTap = null)
    }

    /** 更新错误消息 */
    fun updateErrorMessage(message: String?) {
        updateUiState(errorMessage = message)
    }

    /** 更新滚动位置 */
    fun updateScrollPosition(position: Int) {
        _uiState.value = _uiState.value.copy(scrollPosition = position)
    }

    /** ViewModel工厂类 */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(Live2DViewModel::class.java)) {
                val repository = Live2DRepository.getInstance(context)
                return Live2DViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

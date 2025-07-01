package com.ai.assistance.operit.ui.features.assistant.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.DragonBonesConfig
import com.ai.assistance.operit.data.model.DragonBonesModel
import com.ai.assistance.operit.data.repository.DragonBonesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.ai.assistance.operit.data.model.ModelType

/** 助手配置视图模型 负责管理DragonBones模型的UI状态和业务逻辑 */
class AssistantConfigViewModel(private val repository: DragonBonesRepository) : ViewModel() {

    // UI状态
    data class UiState(
            val isLoading: Boolean = false,
            val models: List<DragonBonesModel> = emptyList(),
            val currentModel: DragonBonesModel? = null,
            val config: DragonBonesConfig? = null,
            val errorMessage: String? = null,
            val operationSuccess: Boolean = false,
            val isScanning: Boolean = false,
            val scrollPosition: Int = 0,
            val isImporting: Boolean = false
    )

    // 当前UI状态
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // 合并模型和配置流，以确保UI状态的一致性
        viewModelScope.launch {
            combine(repository.models, repository.currentConfig) { models, config ->
                // 当配置或模型列表更新时，重新查找当前模型
                val currentModel = config?.let { cfg -> models.find { it.id == cfg.modelId } }
                // 将所有相关状态捆绑在一起发出
                Triple(models, config, currentModel)
            }.collectLatest { (models, config, currentModel) ->
                updateUiState(
                    models = models,
                    config = config,
                    currentModel = currentModel
                )
            }
        }
    }

    /** 更新UI状态 */
    private fun updateUiState(
            isLoading: Boolean? = null,
            models: List<DragonBonesModel>? = null,
            currentModel: DragonBonesModel? = null,
            config: DragonBonesConfig? = null,
            errorMessage: String? = null,
            operationSuccess: Boolean? = null,
            isScanning: Boolean? = null,
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

    /** 导入DragonBones模型ZIP文件 */
    fun importModelFromZip(uri: Uri) {
        updateUiState(isLoading = true, isImporting = true)
        viewModelScope.launch {
            try {
                val success = repository.importModelFromZip(uri)
                updateUiState(
                        isLoading = false,
                        isImporting = false,
                        operationSuccess = success,
                        errorMessage = if (!success) "导入DragonBones模型失败" else null
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
            if (modelClass.isAssignableFrom(AssistantConfigViewModel::class.java)) {
                val repository = DragonBonesRepository.getInstance(context)
                return AssistantConfigViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PlanItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 委托类，负责管理计划项相关功能 */
class PlanItemsDelegate(
        private val viewModelScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?
) {
    companion object {
        private const val TAG = "PlanItemsDelegate"
    }

    // 保存最后一次的有效计划项列表，用于在意外清除时恢复
    private var lastKnownValidPlanItems: List<PlanItem> = emptyList()

    // State flows
    private val _planItems = MutableStateFlow<List<PlanItem>>(emptyList())
    val planItems: StateFlow<List<PlanItem>> = _planItems.asStateFlow()

    /** 设置计划项流收集 */
    fun setupPlanItemsCollection() {
        viewModelScope.launch {
            getEnhancedAiService()?.planItems?.collect { items: List<PlanItem> ->
                handlePlanItemsUpdate(items)
            }
        }
    }

    /** 处理计划项更新 */
    private fun handlePlanItemsUpdate(items: List<PlanItem>) {
        viewModelScope.launch {
            val oldItems = _planItems.value

            // 收到计划项更新，只保留关键日志
            if (items.isNotEmpty()) {
                Log.d(TAG, "收到计划项更新: ${items.size}项")
            }

            // 检查是否收到空列表
            if (items.isEmpty()) {
                val service = getEnhancedAiService()
                // 保留现有的计划项，如果我们处于完成状态且收到空列表
                val isCompletedWithEmptyPlanItems =
                        service?.inputProcessingState?.value is InputProcessingState.Completed &&
                                oldItems.isNotEmpty()

                if (isCompletedWithEmptyPlanItems) {
                    Log.d(TAG, "对话已完成，收到空计划项列表，但保留现有计划项")
                    return@launch
                } else if (lastKnownValidPlanItems.isNotEmpty()) {
                    // 有之前保存的有效计划项，使用它们
                    Log.d(TAG, "收到空计划项但有之前保存的有效计划项，将使用保存的计划项")
                    _planItems.value = lastKnownValidPlanItems
                    return@launch
                }
            }

            // 更新UI
            if (oldItems != items) {
                // 保存有效的计划项列表(不为空)
                if (items.isNotEmpty()) {
                    lastKnownValidPlanItems = items
                }

                _planItems.value = items
            }
        }
    }

    /** 清空计划项 */
    fun clearPlanItems() {
        _planItems.value = emptyList()
        lastKnownValidPlanItems = emptyList()
        getEnhancedAiService()?.clearPlanItems()
        Log.d(TAG, "计划项已清空")
    }

    /**
     * 在检测到计划项丢失时尝试恢复之前保存的有效计划项
     * @return 是否成功恢复
     */
    fun tryRestorePlanItemsIfNeeded(): Boolean {
        val currentItems = _planItems.value

        // 如果当前计划项为空但我们有保存的有效计划项
        if (currentItems.isEmpty() && lastKnownValidPlanItems.isNotEmpty()) {
            val service = getEnhancedAiService()
            // 检查当前对话状态
            val isCompleted = service?.inputProcessingState?.value is InputProcessingState.Completed

            if (isCompleted) {
                Log.d(TAG, "检测到计划项丢失，恢复之前保存的计划项")
                _planItems.value = lastKnownValidPlanItems
                return true
            }
        }
        return false
    }

    /** 强制使用保存的计划项覆盖当前计划项 */
    fun forceRestoreSavedPlanItems() {
        if (lastKnownValidPlanItems.isNotEmpty()) {
            Log.d(TAG, "强制恢复保存的计划项")
            _planItems.value = lastKnownValidPlanItems
        } else {
            Log.d(TAG, "没有可用的保存计划项，无法恢复")
        }
    }
}

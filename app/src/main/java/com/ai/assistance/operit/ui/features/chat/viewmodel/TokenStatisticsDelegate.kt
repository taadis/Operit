package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.util.Log
import com.ai.assistance.operit.api.EnhancedAIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 委托类，负责管理token统计相关功能 */
class TokenStatisticsDelegate(
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val updateUiStatistics:
                (contextSize: Int, inputTokens: Int, outputTokens: Int) -> Unit
) {
    companion object {
        private const val TAG = "TokenStatisticsDelegate"
    }

    // State flows - For internal tracking only
    private val _contextWindowSize = MutableStateFlow(0)
    val contextWindowSize: StateFlow<Int> = _contextWindowSize.asStateFlow()

    private val _inputTokenCount = MutableStateFlow(0)
    val inputTokenCount: StateFlow<Int> = _inputTokenCount.asStateFlow()

    private val _outputTokenCount = MutableStateFlow(0)
    val outputTokenCount: StateFlow<Int> = _outputTokenCount.asStateFlow()

    // 累计token计数
    private var cumulativeInputTokens = 0
    private var cumulativeOutputTokens = 0

    /** 重置token统计 */
    fun resetTokenStatistics() {
        cumulativeInputTokens = 0
        cumulativeOutputTokens = 0
        _inputTokenCount.value = 0
        _outputTokenCount.value = 0
        _contextWindowSize.value = 0

        // 同时重置服务中的token计数
        getEnhancedAiService()?.resetTokenCounters()

        // 更新UI
        updateUiStatistics(0, 0, 0)

        Log.d(TAG, "token统计已重置")
    }

    /** 更新token统计信息 */
    fun updateChatStatistics(): Pair<Int, Int> {
        val service = getEnhancedAiService()

        service?.let {
            try {
                // 从AI服务获取最新的token统计
                val currentInputTokens = it.getCurrentInputTokenCount()
                val currentOutputTokens = it.getCurrentOutputTokenCount()

                // 更新上下文窗口大小
                _contextWindowSize.value = currentInputTokens

                // 更新累计token数
                cumulativeInputTokens += currentInputTokens
                cumulativeOutputTokens += currentOutputTokens

                // 更新内部状态
                _inputTokenCount.value = cumulativeInputTokens
                _outputTokenCount.value = cumulativeOutputTokens

                // 更新UI
                updateUiStatistics(
                        _contextWindowSize.value,
                        cumulativeInputTokens,
                        cumulativeOutputTokens
                )

                Log.d(
                        TAG,
                        "Token stats updated - Context: $currentInputTokens, " +
                                "Cumulative Input: $cumulativeInputTokens, Output: $cumulativeOutputTokens"
                )

                return Pair(cumulativeInputTokens, cumulativeOutputTokens)
            } catch (e: Exception) {
                Log.e(TAG, "获取token计数时出错: ${e.message}", e)
            }
        }

        return Pair(cumulativeInputTokens, cumulativeOutputTokens)
    }

    /** 设置累计token计数 */
    fun setTokenCounts(inputTokens: Int, outputTokens: Int) {
        cumulativeInputTokens = inputTokens
        cumulativeOutputTokens = outputTokens
        _inputTokenCount.value = inputTokens
        _outputTokenCount.value = outputTokens

        // 更新UI
        updateUiStatistics(_contextWindowSize.value, inputTokens, outputTokens)

        Log.d(TAG, "Token统计已设置 - 输入: $inputTokens, 输出: $outputTokens")
    }

    /** 获取当前累计token计数 */
    fun getCurrentTokenCounts(): Pair<Int, Int> {
        return Pair(cumulativeInputTokens, cumulativeOutputTokens)
    }
}

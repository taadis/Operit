package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService

/** 委托类，负责管理token统计相关功能 */
class TokenStatisticsDelegate(
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val updateUiTokenCounts: (inputTokens: Int, outputTokens: Int) -> Unit
) {
    companion object {
        private const val TAG = "TokenStatisticsDelegate"
    }

    // 累计token计数
    private var cumulativeInputTokens = 0
    private var cumulativeOutputTokens = 0
    private var lastCurrentWindowSize = 0

    /** 重置token统计 */
    fun resetTokenStatistics() {
        cumulativeInputTokens = 0
        cumulativeOutputTokens = 0

        // 同时重置服务中的token计数
        getEnhancedAiService()?.resetTokenCounters()

        // 更新UI
        updateUiTokenCounts(0, 0)

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
                lastCurrentWindowSize = currentInputTokens // 实际窗口大小是输入token数

                // 更新累计token数
                cumulativeInputTokens += currentInputTokens
                cumulativeOutputTokens += currentOutputTokens

                // 更新UI
                updateUiTokenCounts(
                        cumulativeInputTokens,
                        cumulativeOutputTokens
                )

                Log.d(
                        TAG,
                        "Token stats updated - " +
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
    fun setTokenCounts(inputTokens: Int, outputTokens: Int, windowSize: Int) {
        cumulativeInputTokens = inputTokens
        cumulativeOutputTokens = outputTokens
        lastCurrentWindowSize = windowSize

        // 更新UI
        updateUiTokenCounts(inputTokens, outputTokens)
    }

    /** 获取当前累计token计数 */
    fun getCurrentTokenCounts(): Pair<Int, Int> {
        return Pair(cumulativeInputTokens, cumulativeOutputTokens)
    }

    /** 获取最近一次的实际上下文窗口大小 */
    fun getLastCurrentWindowSize(): Int {
        return lastCurrentWindowSize
    }
}

package com.ai.assistance.operit.ui.features.demo.utils

import android.content.Context
import android.util.Log

private const val TAG = "TermuxConfigUtils"
private const val TERMUX_CONFIG_PREFS = "termux_config_preferences"
private const val KEY_TERMUX_FULLY_CONFIGURED = "termux_fully_configured"

/**
 * 保存Termux配置状态到持久化存储
 * @param context 上下文
 * @param isFullyConfigured 是否已完全配置
 */
fun saveTermuxConfigStatus(context: Context, isFullyConfigured: Boolean) {
    context.getSharedPreferences(TERMUX_CONFIG_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TERMUX_FULLY_CONFIGURED, isFullyConfigured)
            .apply()
    Log.d(TAG, "保存Termux配置状态: $isFullyConfigured")
}

/**
 * 获取Termux配置状态
 * @param context 上下文
 * @return 是否已完全配置
 */
fun getTermuxConfigStatus(context: Context): Boolean {
    val status =
            context.getSharedPreferences(TERMUX_CONFIG_PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_TERMUX_FULLY_CONFIGURED, false)
    Log.d(TAG, "获取Termux配置状态: $status")
    return status
} 
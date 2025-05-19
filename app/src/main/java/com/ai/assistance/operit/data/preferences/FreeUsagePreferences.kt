package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manages free API usage limits */
class FreeUsagePreferences(context: Context) {
    private val PREFS_NAME = "free_usage_preferences"
    private val KEY_LAST_USAGE_DATE = "last_usage_date"
    private val KEY_DAILY_USAGE_COUNT = "daily_usage_count"
    private val MAX_DAILY_USAGE = 10

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dailyUsageCountFlow = MutableStateFlow(getDailyUsageCount())
    val dailyUsageCountFlow: StateFlow<Int> = _dailyUsageCountFlow.asStateFlow()

    private val _remainingUsagesFlow = MutableStateFlow(getRemainingUsages())
    val remainingUsagesFlow: StateFlow<Int> = _remainingUsagesFlow.asStateFlow()

    /** Check if the user has used the free API today and how many times */
    private fun getDailyUsageCount(): Int {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastUsageDate = prefs.getString(KEY_LAST_USAGE_DATE, "") ?: ""

        // If last usage was not today, reset count
        if (lastUsageDate != today) {
            prefs.edit()
                    .putString(KEY_LAST_USAGE_DATE, today)
                    .putInt(KEY_DAILY_USAGE_COUNT, 0)
                    .apply()
            return 0
        }

        return prefs.getInt(KEY_DAILY_USAGE_COUNT, 0)
    }

    /** Get remaining free usages for today */
    fun getRemainingUsages(): Int {
        return MAX_DAILY_USAGE - getDailyUsageCount()
    }

    /** Check if the user can still use the free API today */
    fun canUseFreeTier(): Boolean {
        return getRemainingUsages() > 0
    }

    /**
     * Record a usage of the free API
     * @return The number of remaining usages
     */
    fun recordUsage(): Int {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastUsageDate = prefs.getString(KEY_LAST_USAGE_DATE, "") ?: ""
        var count = prefs.getInt(KEY_DAILY_USAGE_COUNT, 0)

        // If last usage was not today, reset
        if (lastUsageDate != today) {
            count = 0
        }

        // Increment usage count
        count++

        // Update preferences
        prefs.edit()
                .putString(KEY_LAST_USAGE_DATE, today)
                .putInt(KEY_DAILY_USAGE_COUNT, count)
                .apply()

        // Update flows
        _dailyUsageCountFlow.value = count
        val remaining = MAX_DAILY_USAGE - count
        _remainingUsagesFlow.value = remaining

        return remaining
    }

    /** Get the maximum allowed usages per day */
    fun getMaxDailyUsage(): Int {
        return MAX_DAILY_USAGE
    }
}

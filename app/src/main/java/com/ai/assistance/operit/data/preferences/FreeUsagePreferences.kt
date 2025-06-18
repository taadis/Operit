package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manages free API usage limits */
class FreeUsagePreferences(private val context: Context) {
    private val PREFS_NAME = "free_usage_preferences"
    private val KEY_LAST_USAGE_DATE = "last_usage_date"
    private val KEY_DAILY_USAGE_COUNT = "daily_usage_count"
    private val KEY_TOTAL_USAGE_COUNT = "total_usage_count"
    private val KEY_NEXT_AVAILABLE_DATE = "next_available_date"
    private val MAX_DAILY_USAGE = 1

    // 外部文件存储相关
    private val EXTERNAL_VERIFY_FILENAME = "usage_verification.dat"
    private val EXTERNAL_DIRECTORY = "Operit"

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dailyUsageCountFlow = MutableStateFlow(getDailyUsageCount())
    val dailyUsageCountFlow: StateFlow<Int> = _dailyUsageCountFlow.asStateFlow()

    private val _remainingUsagesFlow = MutableStateFlow(getRemainingUsages())
    val remainingUsagesFlow: StateFlow<Int> = _remainingUsagesFlow.asStateFlow()

    private val _nextAvailableDateFlow = MutableStateFlow(getNextAvailableDate())
    val nextAvailableDateFlow: StateFlow<LocalDate> = _nextAvailableDateFlow.asStateFlow()

    init {
        // 初始化时检查外部验证文件
        checkExternalVerification()
    }

    /** 获取下次可用日期 */
    private fun getNextAvailableDate(): LocalDate {
        val dateStr = prefs.getString(KEY_NEXT_AVAILABLE_DATE, "") ?: ""
        return if (dateStr.isNotBlank()) {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
            LocalDate.now()
        }
    }

    /** 检查外部验证文件与SharedPreferences是否一致 */
    private fun checkExternalVerification() {
        try {
            val externalFile = getExternalVerificationFile()

            if (externalFile.exists()) {
                val externalData = FileInputStream(externalFile).use { it.readBytes() }
                val totalUsage = prefs.getInt(KEY_TOTAL_USAGE_COUNT, 0)
                val lastDate = prefs.getString(KEY_LAST_USAGE_DATE, "") ?: ""
                val nextDate = prefs.getString(KEY_NEXT_AVAILABLE_DATE, "") ?: ""

                val expectedHash = generateVerificationHash(totalUsage, lastDate, nextDate)
                val actualHash = externalData.toString(Charsets.UTF_8)

                // 如果哈希不匹配，可能是用户清除了应用数据
                if (actualHash != expectedHash) {
                    // 使用外部文件的数据恢复
                    val parts = actualHash.split("|")
                    if (parts.size >= 3) {
                        val extTotalUsage = parts[0].toIntOrNull() ?: 0
                        val extLastDate = parts[1]
                        val extNextDate = parts[2]

                        // 用更严格的数据更新SharedPreferences
                        val currentTotalUsage = prefs.getInt(KEY_TOTAL_USAGE_COUNT, 0)
                        val updatedTotalUsage = maxOf(extTotalUsage, currentTotalUsage)

                        // 更新SharedPreferences
                        prefs.edit()
                                .putInt(KEY_TOTAL_USAGE_COUNT, updatedTotalUsage)
                                .putString(KEY_LAST_USAGE_DATE, extLastDate)
                                .putString(KEY_NEXT_AVAILABLE_DATE, extNextDate)
                                .apply()

                        // 更新Flow
                        _dailyUsageCountFlow.value = getDailyUsageCount()
                        _remainingUsagesFlow.value = getRemainingUsages()
                        _nextAvailableDateFlow.value = getNextAvailableDate()
                    }
                }
            } else {
                // 如果文件不存在，创建它
                updateExternalVerificationFile()
            }
        } catch (e: Exception) {
            // 出错时尝试重新创建验证文件
            try {
                updateExternalVerificationFile()
            } catch (ex: Exception) {
                // 忽略错误，应用仍然可以运行
            }
        }
    }

    /** 获取外部验证文件 */
    private fun getExternalVerificationFile(): File {
        val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, EXTERNAL_DIRECTORY)
        if (!operitDir.exists()) {
            operitDir.mkdirs()
        }
        return File(operitDir, EXTERNAL_VERIFY_FILENAME)
    }

    /** 更新外部验证文件 */
    private fun updateExternalVerificationFile() {
        try {
            val totalUsage = prefs.getInt(KEY_TOTAL_USAGE_COUNT, 0)
            val lastDate = prefs.getString(KEY_LAST_USAGE_DATE, "") ?: ""
            val nextDate = prefs.getString(KEY_NEXT_AVAILABLE_DATE, "") ?: ""

            val hash = generateVerificationHash(totalUsage, lastDate, nextDate)

            val externalFile = getExternalVerificationFile()
            FileOutputStream(externalFile).use {
                it.write(hash.toByteArray(Charsets.UTF_8))
                it.flush()
            }
        } catch (e: Exception) {
            // 忽略错误，应用仍然可以运行
        }
    }

    /** 生成验证哈希值 */
    private fun generateVerificationHash(
            totalUsage: Int,
            lastDate: String,
            nextDate: String
    ): String {
        // 简单存储为分隔的字符串
        val dataString = "$totalUsage|$lastDate|$nextDate"
        return dataString
    }

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
        // 检查今天是否是下次可用日期
        val today = LocalDate.now()
        val nextAvailableDate = getNextAvailableDate()

        // 如果今天不是可用日期，返回0
        if (today.isBefore(nextAvailableDate)) {
            return 0
        }

        // 如果今天可用，正常返回剩余次数
        return MAX_DAILY_USAGE - getDailyUsageCount()
    }

    /** Check if the user can still use the free API today */
    fun canUseFreeTier(): Boolean {
        // 检查今天是否是下次可用日期
        val today = LocalDate.now()
        val nextAvailableDate = getNextAvailableDate()

        // 如果今天在可用日期之前，不能使用
        if (today.isBefore(nextAvailableDate)) {
            return false
        }

        // 如果今天可用，检查是否还有剩余使用次数
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

        // If last usage was not today, reset daily count
        if (lastUsageDate != today) {
            count = 0
        }

        // Increment usage count
        count++

        // 获取并增加总使用次数
        val totalUsageCount = prefs.getInt(KEY_TOTAL_USAGE_COUNT, 0) + 1

        // 计算下次可用日期（第1次后等1天，第2次后等2天，第3次后等4天...）
        val waitDays = if (totalUsageCount == 1) 1 else (1 shl (totalUsageCount - 1)) / 2
        val nextAvailableDate =
                LocalDate.now().plusDays(waitDays.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Update preferences
        prefs.edit()
                .putString(KEY_LAST_USAGE_DATE, today)
                .putInt(KEY_DAILY_USAGE_COUNT, count)
                .putInt(KEY_TOTAL_USAGE_COUNT, totalUsageCount)
                .putString(KEY_NEXT_AVAILABLE_DATE, nextAvailableDate)
                .apply()

        // 更新外部验证文件
        updateExternalVerificationFile()

        // Update flows
        _dailyUsageCountFlow.value = count
        _nextAvailableDateFlow.value =
                LocalDate.parse(nextAvailableDate, DateTimeFormatter.ISO_LOCAL_DATE)

        val remaining = if (count >= MAX_DAILY_USAGE) 0 else MAX_DAILY_USAGE - count
        _remainingUsagesFlow.value = remaining

        return remaining
    }

    /** Get the maximum allowed usages per day */
    fun getMaxDailyUsage(): Int {
        return MAX_DAILY_USAGE
    }

    /** 获取下次可使用日期 */
    fun getNextAvailableDateString(): String {
        val nextDate = getNextAvailableDate()
        return nextDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /** 重置使用记录（仅用于调试） */
    fun resetUsage() {
        prefs.edit()
                .putString(KEY_LAST_USAGE_DATE, "")
                .putInt(KEY_DAILY_USAGE_COUNT, 0)
                .putInt(KEY_TOTAL_USAGE_COUNT, 0)
                .putString(KEY_NEXT_AVAILABLE_DATE, "")
                .apply()

        // 更新外部验证文件
        updateExternalVerificationFile()

        // 更新Flow
        _dailyUsageCountFlow.value = 0
        _remainingUsagesFlow.value = MAX_DAILY_USAGE
        _nextAvailableDateFlow.value = LocalDate.now()
    }

    /** 获取等待天数 */
    fun getWaitDays(): Int {
        val today = LocalDate.now()
        val nextAvailable = getNextAvailableDate()

        if (today.isEqual(nextAvailable) || today.isAfter(nextAvailable)) {
            return 0
        }

        return today.until(nextAvailable).days
    }
}

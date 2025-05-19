package com.ai.assistance.operit.ui.features.token.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日期格式化工具类 */
object DateFormatter {
    /** 将Unix时间戳格式化为可读日期 */
    fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return format.format(date)
    }
}

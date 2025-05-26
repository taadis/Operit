package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** 日志记录数据类 */
data class LogRecord(
        val message: String,
        val level: LogLevel,
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String? = null,
        val pid: String? = null,
        val tid: String? = null
)

/** 日志级别 */
enum class LogLevel(val displayName: String, val symbol: String, val color: Color) {
    VERBOSE("详细", "V", Color(0xFF9E9E9E)),
    DEBUG("调试", "D", Color(0xFF2196F3)),
    INFO("信息", "I", Color(0xFF4CAF50)),
    WARNING("警告", "W", Color(0xFFFFC107)),
    ERROR("错误", "E", Color(0xFFF44336)),
    FATAL("致命", "F", Color(0xFF9C27B0)),
    SILENT("静默", "S", Color(0xFF607D8B)),
    UNKNOWN("未知", "?", Color(0xFF9E9E9E))
}

/** 预设过滤分类 */
enum class FilterCategory(val displayName: String) {
    LEVEL("日志级别"),
    SYSTEM("系统过滤"),
    APP("应用过滤"),
    CUSTOM("自定义过滤"),
    TAG("标签过滤")
}

/** 预设过滤器 */
data class PresetFilter(
        val name: String,
        val filter: String,
        val description: String,
        val category: FilterCategory,
        val icon: ImageVector
)

/** 标签统计信息 */
data class TagStats(
    val tag: String,
    var count: Int = 0,
    val isFiltered: Boolean = false
)

/** 过滤操作类型 */
enum class FilterAction {
    INCLUDE,   // 包含
    EXCLUDE,   // 排除
    ONLY      // 仅显示
} 
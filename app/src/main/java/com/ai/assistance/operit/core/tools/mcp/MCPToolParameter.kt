package com.ai.assistance.operit.core.tools.mcp

import kotlinx.serialization.Serializable

/** Represents a parameter for an MCP tool */
@Serializable
data class MCPToolParameter(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean = false,
        val defaultValue: String? = null
) {
    /**
     * 尝试根据参数类型自动转换字符串值
     *
     * @param value 输入值（通常是字符串）
     * @return 经过类型转换的值
     */
    fun convertParameterValue(value: Any): Any {
        if (value !is String) return value

        return when (type.lowercase()) {
            "number" -> {
                try {
                    if (value.contains(".")) value.toDouble() else value.toLong()
                } catch (e: Exception) {
                    value // 无法转换时返回原始值
                }
            }
            "boolean" -> value.lowercase() == "true"
            "integer" -> {
                try {
                    value.toInt()
                } catch (e: Exception) {
                    value
                }
            }
            "float", "double" -> {
                try {
                    value.toDouble()
                } catch (e: Exception) {
                    value
                }
            }
            else -> value // 其他类型保持原样
        }
    }

    companion object {
        /**
         * 尝试智能转换参数类型（无需MCPToolParameter实例）
         *
         * @param value 输入值（通常是字符串）
         * @param typeName 类型名称
         * @return 转换后的值
         */
        fun smartConvert(value: Any, typeName: String?): Any {
            if (value !is String) return value

            return when (typeName?.lowercase()) {
                "number" -> {
                    try {
                        if (value.contains(".")) value.toDouble() else value.toLong()
                    } catch (e: Exception) {
                        value
                    }
                }
                "boolean" -> value.lowercase() == "true"
                "integer" -> {
                    try {
                        value.toInt()
                    } catch (e: Exception) {
                        value
                    }
                }
                "float", "double" -> {
                    try {
                        value.toDouble()
                    } catch (e: Exception) {
                        value
                    }
                }
                else -> {
                    // 如果未指定类型，尝试智能猜测
                    if (value.matches(Regex("-?\\d+(\\.\\d+)?"))) {
                        try {
                            if (value.contains(".")) value.toDouble() else value.toLong()
                        } catch (e: Exception) {
                            value
                        }
                    } else if (value.lowercase() == "true" || value.lowercase() == "false") {
                        value.lowercase() == "true"
                    } else {
                        value
                    }
                }
            }
        }
    }
}

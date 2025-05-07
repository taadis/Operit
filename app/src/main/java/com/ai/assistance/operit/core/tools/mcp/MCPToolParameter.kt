package com.ai.assistance.operit.core.tools.mcp

import kotlinx.serialization.Serializable

/**
 * Represents a parameter for an MCP tool
 */
@Serializable
data class MCPToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false,
    val defaultValue: String? = null
) 
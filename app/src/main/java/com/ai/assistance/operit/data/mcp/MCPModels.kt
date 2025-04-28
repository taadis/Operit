package com.ai.assistance.operit.data.mcp

// Note: The MCPServer class has been moved to 
// app/src/main/java/com/ai/assistance/operit/ui/features/mcp/model/MCPServer.kt

/** GitHub仓库信息数据类 */
data class RepoInfo(
    val owner: String,
    val name: String,
    val stars: Int,
    val watchers: Int,
    val forks: Int,
    val defaultBranch: String,
    val description: String,
    val lastUpdated: String,
    val url: String
)

/** MCP更新信息数据类 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val updateUrl: String,
    val updateInfo: RepoInfo? = null
) 
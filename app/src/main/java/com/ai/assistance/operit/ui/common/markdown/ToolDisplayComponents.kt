package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 简洁样式的工具调用显示组件 使用箭头图标+工具名+参数的简洁行样式 */
@Composable
fun CompactToolDisplay(
        toolName: String,
        params: String = "",
        textColor: Color,
        modifier: Modifier = Modifier
) {
    Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 工具图标
        Icon(
                imageVector = getToolIcon(toolName),
                contentDescription = "工具调用",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 工具名称
        Text(
                text = toolName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 80.dp, max = 120.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )

        // 参数内容（如果有）
        if (params.isNotBlank()) {
            Text(
                    text = params,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 卡片式工具显示组件 用于显示较长内容的工具调用，支持流式渲染 */
@Composable
fun DetailedToolDisplay(
        toolName: String,
        params: String = "",
        textColor: Color,
        modifier: Modifier = Modifier
) {
    Card(
            modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
            border =
                    BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
            shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 工具标题行
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                // 工具图标 - 与CompactToolDisplay保持一致的大小和位置
                Icon(
                        imageVector = getToolIcon(toolName),
                        contentDescription = "工具调用",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 工具名称
                Text(
                        text = toolName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                )
            }

            // 参数内容 - 使用键控列表渲染，优化流式体验
            if (params.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                // 按行拆分参数文本
                val lines = params.lines()

                // 使用Column进行流式渲染
                Column {
                    lines.forEachIndexed { index, line ->
                        key(index) { // 为每行添加key，优化重组
                            if (line.isNotBlank()) {
                                Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.8f),
                                        modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 根据工具名称选择合适的图标 */
private fun getToolIcon(toolName: String): ImageVector {
    return when {
        // 文件工具
        toolName.contains("file") || toolName.contains("read") || toolName.contains("write") ->
                Icons.Default.FileOpen

        // 搜索工具
        toolName.contains("search") || toolName.contains("find") || toolName.contains("query") ->
                Icons.Default.Search

        // 命令行工具
        toolName.contains("terminal") ||
                toolName.contains("exec") ||
                toolName.contains("command") ||
                toolName.contains("shell") -> Icons.Default.Terminal

        // 代码工具
        toolName.contains("code") || toolName.contains("ffmpeg") -> Icons.Default.Code

        // 网络工具
        toolName.contains("http") || toolName.contains("web") || toolName.contains("visit") ->
                Icons.Default.Web

        // 默认图标
        else -> Icons.Default.ArrowForward
    }
}

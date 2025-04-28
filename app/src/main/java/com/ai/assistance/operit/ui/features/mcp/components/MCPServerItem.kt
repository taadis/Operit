package com.ai.assistance.operit.ui.features.mcp.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.mcp.MCPImageCache
import com.ai.assistance.operit.ui.features.mcp.model.MCPServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A composable that displays an MCP server item in a list.
 *
 * @param server The MCP server to display
 * @param onClick Callback to be invoked when the item is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPServerItem(server: MCPServer, onClick: () -> Unit) {
        var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        val coroutineScope = rememberCoroutineScope()
        var imageLoadJob by remember { mutableStateOf<Job?>(null) }

        LaunchedEffect(server.logoUrl) {
                if (!server.logoUrl.isNullOrBlank()) {
                        imageLoadJob?.cancel()
                        val cachedBitmap = MCPImageCache.getBitmapFromCache(server.logoUrl)
                        if (cachedBitmap != null) {
                                imageBitmap = cachedBitmap
                        } else {
                                imageLoadJob =
                                        coroutineScope.launch {
                                                try {
                                                        val bitmap =
                                                                MCPImageCache.loadImage(
                                                                        server.logoUrl
                                                                )
                                                        if (bitmap != null) {
                                                                imageBitmap = bitmap
                                                        }
                                                } catch (e: CancellationException) {
                                                        // 正常取消，不需要记录
                                                } catch (e: Exception) {
                                                        Log.w(
                                                                "MCPServerItem",
                                                                "加载图标失败: ${server.name.take(30)}"
                                                        )
                                                }
                                        }
                        }
                }
        }

        Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (server.isInstalled)
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.05f
                                                )
                                        else MaterialTheme.colorScheme.surface
                        ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(4.dp),
                border =
                        if (server.isInstalled) {
                                BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                        } else null,
                onClick = onClick
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Logo部分
                        Box(
                                modifier =
                                        Modifier.size(36.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.5f)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                if (imageBitmap != null) {
                                        Image(
                                                bitmap = imageBitmap!!.asImageBitmap(),
                                                contentDescription = "${server.name} logo",
                                                contentScale = ContentScale.Fit,
                                                modifier =
                                                        Modifier.size(30.dp)
                                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                } else {
                                        // 根据类别显示不同的默认图标
                                        val icon =
                                                when {
                                                        server.category.contains(
                                                                "AI",
                                                                ignoreCase = true
                                                        ) -> Icons.Default.Search
                                                        server.category.contains(
                                                                "Code",
                                                                ignoreCase = true
                                                        ) -> Icons.Default.Code
                                                        server.category.contains(
                                                                "Database",
                                                                ignoreCase = true
                                                        ) -> Icons.Default.Storage
                                                        server.category.contains(
                                                                "Web",
                                                                ignoreCase = true
                                                        ) -> Icons.Default.Language
                                                        server.category.contains(
                                                                "File",
                                                                ignoreCase = true
                                                        ) -> Icons.Default.Folder
                                                        server.category.contains(
                                                                "Generation",
                                                                ignoreCase = true
                                                        ) -> Icons.Default.Create
                                                        server.category.contains(
                                                                "Document",
                                                                ignoreCase = true
                                                        ) -> Icons.Default.Description
                                                        server.requiresApiKey -> Icons.Default.Lock
                                                        else -> Icons.Default.Extension
                                                }

                                        Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 内容部分
                        Column(modifier = Modifier.weight(1f)) {
                                // 顶部信息行：名称和状态标签
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                // 名称
                                                Text(
                                                        text = server.name,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )

                                                // 简短描述
                                                Text(
                                                        text = server.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme.onSurface
                                                                        .copy(alpha = 0.7f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(top = 1.dp)
                                                )
                                        }

                                        // 标签区
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                // 官方标签
                                                if (server.author.contains("Official")) {
                                                        Surface(
                                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                                                shape = RoundedCornerShape(2.dp),
                                                                modifier = Modifier.padding(end = 4.dp)
                                                        ) {
                                                                Text(
                                                                        text = "官方",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onTertiary,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                        }
                                                }
                                                
                                                // 认证标记
                                                if (server.isVerified) {
                                                        Icon(
                                                                imageVector = Icons.Default.Verified,
                                                                contentDescription = "已认证",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp).padding(end = 2.dp)
                                                        )
                                                }
                                                
                                                // 已安装标记
                                                if (server.isInstalled) {
                                                        Surface(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = RoundedCornerShape(2.dp),
                                                                modifier = Modifier.padding(start = 4.dp)
                                                        ) {
                                                                Text(
                                                                        text = "已安装",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                        }
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // 底部信息行
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // 左侧 - 作者和分类
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                        ) {
                                                // 作者
                                                if (server.author.isNotBlank() &&
                                                                server.author != "Unknown"
                                                ) {
                                                        Text(
                                                                text = server.author,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )

                                                        Text(
                                                                text = " · ",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }

                                                // 分类标签
                                                Text(
                                                        text = server.category,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )

                                                // API Key 需求
                                                if (server.requiresApiKey) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = "需要API密钥",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .error,
                                                                modifier = Modifier.size(10.dp)
                                                        )
                                                }
                                        }

                                        // 右侧信息区 - 版本和星星
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                // 版本信息
                                                if (server.version.isNotBlank()) {
                                                        Text(
                                                                text = "v${server.version}",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                                modifier =
                                                                        Modifier.padding(end = 6.dp)
                                                        )
                                                }

                                                // 评分
                                                Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(12.dp)
                                                )

                                                Spacer(modifier = Modifier.width(2.dp))

                                                Text(
                                                        text = "${server.stars}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                }
                        }
                }
        }
}

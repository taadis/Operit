package com.ai.assistance.operit.ui.features.packages.screens.mcp.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.data.mcp.MCPImageCache
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A dialog that displays detailed information about an MCP server.
 *
 * @param server The MCP server to display details for
 * @param onDismiss Callback to be invoked when the dialog is dismissed
 * @param onInstall Callback to be invoked when the install button is clicked
 * @param onUninstall Callback to be invoked when the uninstall button is clicked
 * @param installedPath 已安装插件的路径，如果未安装则为null
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPServerDetailsDialog(
    server: MCPServer,
    onDismiss: () -> Unit,
    onInstall: (MCPServer) -> Unit,
    onUninstall: (MCPServer) -> Unit,
    installedPath: String? = null
) {
        // Local state for loaded README content
        var readmeContent by remember { mutableStateOf<String?>(null) }
        val isInstalled = server.isInstalled && installedPath != null
        val coroutineScope = rememberCoroutineScope()

        // Load README content for installed plugins
        LaunchedEffect(server.id, installedPath) {
                if (isInstalled && installedPath != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                                try {
                                        val readmeFile = File(installedPath, "README.md")
                                        if (readmeFile.exists()) {
                                                readmeContent = readmeFile.readText()
                                        } else {
                                                // Try to find any markdown file
                                                val mdFiles =
                                                        File(installedPath).listFiles { file ->
                                                                file.extension.equals(
                                                                        "md",
                                                                        ignoreCase = true
                                                                )
                                                        }
                                                if (mdFiles?.isNotEmpty() == true) {
                                                        readmeContent = mdFiles[0].readText()
                                                }
                                        }
                                } catch (e: Exception) {
                                        Log.e(
                                                "MCPServerDetails",
                                                "Error reading README for ${server.id}",
                                                e
                                        )
                                }
                        }
                }
        }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 头部区域 - 更平衡的设计
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.3f
                                                ),
                    tonalElevation = 0.dp
                ) {
                    Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 12.dp
                                                                ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Logo
                                                var imageBitmap by remember {
                                                        mutableStateOf<android.graphics.Bitmap?>(
                                                                null
                                                        )
                                                }
                                                var imageLoadJob by remember {
                                                        mutableStateOf<Job?>(null)
                                                }

                        LaunchedEffect(server.logoUrl) {
                            if (!server.logoUrl.isNullOrBlank()) {
                                imageLoadJob?.cancel()
                                                                val cachedBitmap =
                                                                        MCPImageCache
                                                                                .getBitmapFromCache(
                                                                                        server.logoUrl
                                                                                )
                                if (cachedBitmap != null) {
                                    imageBitmap = cachedBitmap
                                } else {
                                                                        imageLoadJob =
                                                                                coroutineScope
                                                                                        .launch {
                                                                                                try {
                                                                                                        val bitmap =
                                                                                                                MCPImageCache
                                                                                                                        .loadImage(
                                                                                                                                server.logoUrl
                                                                                                                        )
                                                                                                        if (bitmap !=
                                                                                                                        null
                                                                                                        ) {
                                                                                                                imageBitmap =
                                                                                                                        bitmap
                                                                                                        }
                                                                                                } catch (
                                                                                                        e:
                                                                                                                Exception) {
                                                                                                        Log.w(
                                                                                                                "MCPServerDetailsDialog",
                                                                                                                "加载图标失败: ${server.name.take(30)}"
                                                                                                        )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                                                        modifier =
                                                                Modifier.size(44.dp)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                )
                                                                        )
                                                                        .border(
                                                                                1.dp,
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .outlineVariant,
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant
                                                                        ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageBitmap != null) {
                                Image(
                                                                        bitmap =
                                                                                imageBitmap!!
                                                                                        .asImageBitmap(),
                                                                        contentDescription =
                                                                                "${server.name} logo",
                                                                        contentScale =
                                                                                ContentScale.Fit,
                                                                        modifier =
                                                                                Modifier.size(36.dp)
                                                                                        .padding(
                                                                                                4.dp
                                                                                        )
                                )
                            } else {
                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .Extension,
                                    contentDescription = null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant,
                                                                        modifier =
                                                                                Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // 信息区 - 更合理的垂直布局
                        Column(modifier = Modifier.weight(1f)) {
                            // 标题行 - 拆分标题和关闭按钮，解决挤压问题
                            Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                                                Column(
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                ) {
                                    // 名称
                                    Text(
                                        text = server.name,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleMedium,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                overflow =
                                                                                        TextOverflow
                                                                                                .Ellipsis,
                                        maxLines = 1
                                    )

                                    // 作者和版本信息放一行
                                    Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically,
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth()
                                                                        ) {
                                                                                if (server.author
                                                                                                .isNotBlank() &&
                                                                                                server.author !=
                                                                                                        "Unknown"
                                                                                ) {
                                            Text(
                                                                                                text =
                                                                                                        "by ${server.author}",
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .bodySmall,
                                                                                                color =
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .primary,
                                                                                                maxLines =
                                                                                                        1,
                                                                                                overflow =
                                                                                                        TextOverflow
                                                                                                                .Ellipsis,
                                                                                                modifier =
                                                                                                        Modifier.weight(
                                                                                                                1f,
                                                                                                                fill =
                                                                                                                        false
                                                                                                        )
                                            )
                                        }

                                        // 版本信息放在作者旁边
                                                                                if (server.version
                                                                                                .isNotBlank()
                                                                                ) {
                                                                                        if (server.author
                                                                                                        .isNotBlank() &&
                                                                                                        server.author !=
                                                                                                                "Unknown"
                                                                                        ) {
                                                Text(
                                                                                                        text =
                                                                                                                " · ",
                                                                                                        style =
                                                                                                                MaterialTheme
                                                                                                                        .typography
                                                                                                                        .bodySmall,
                                                                                                        color =
                                                                                                                MaterialTheme
                                                                                                                        .colorScheme
                                                                                                                        .onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                                                                text =
                                                                                                        "v${server.version}",
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .bodySmall,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Medium,
                                                                                                color =
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .primary
                                            )
                                        }
                                    }
                                }

                                // 关闭按钮
                                IconButton(
                                    onClick = onDismiss,
                                                                        modifier =
                                                                                Modifier.size(28.dp)
                                ) {
                                    Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Close,
                                                                                contentDescription =
                                                                                        "关闭",
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                18.dp
                                                                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 徽章行 - 合理的间距，移除版本信息
                            Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.Start
                            ) {
                                // 分类徽章
                                Surface(
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                ),
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .secondaryContainer,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        end = 6.dp
                                                                                )
                                ) {
                                    Text(
                                                                                text =
                                                                                        server.category,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                horizontal =
                                                                                                        6.dp,
                                                                                                vertical =
                                                                                                        2.dp
                                                                                        ),
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSecondaryContainer
                                    )
                                }

                                // 星星评分
                                Surface(
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                ),
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.7f
                                                                                        ),
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        end = 6.dp
                                                                                )
                                ) {
                                    Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                horizontal =
                                                                                                        6.dp,
                                                                                                vertical =
                                                                                                        2.dp
                                                                                        )
                                    ) {
                                        Icon(
                                                                                        imageVector =
                                                                                                Icons.Default
                                                                                                        .Star,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        12.dp
                                                                                                )
                                                                                )
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.width(
                                                                                                        2.dp
                                                                                                )
                                                                                )
                                        Text(
                                                                                        text =
                                                                                                "${server.stars}",
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .labelSmall,
                                                                                        color =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onPrimaryContainer
                                        )
                                    }
                                }

                                // 认证徽章
                                if (server.isVerified) {
                                    Surface(
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                4.dp
                                                                                        ),
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .tertiaryContainer,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                end =
                                                                                                        6.dp
                                                                                        )
                                    ) {
                                        Row(
                                                                                        verticalAlignment =
                                                                                                Alignment
                                                                                                        .CenterVertically,
                                                                                        modifier =
                                                                                                Modifier.padding(
                                                                                                        horizontal =
                                                                                                                6.dp,
                                                                                                        vertical =
                                                                                                                2.dp
                                                                                                )
                                        ) {
                                            Icon(
                                                                                                imageVector =
                                                                                                        Icons.Default
                                                                                                                .Verified,
                                                                                                contentDescription =
                                                                                                        null,
                                                                                                tint =
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .tertiary,
                                                                                                modifier =
                                                                                                        Modifier.size(
                                                                                                                12.dp
                                                                                                        )
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.width(
                                                                                                                2.dp
                                                                                                        )
                                                                                        )
                                            Text(
                                                                                                text =
                                                                                                        "已认证",
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .labelSmall,
                                                                                                color =
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .onTertiaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 内容区域
                LazyColumn(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .weight(1f)
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    // API key 需求警告
                    if (server.requiresApiKey) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .errorContainer
                                                                                .copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .padding(
                                                                                                10.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                ) {
                                    Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Lock,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .error,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                18.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                10.dp
                                                                                        )
                                                                        )
                                    Text(
                                        text = "需要API密钥",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelMedium,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Medium,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // 描述内容
                    item {
                                                // Display full README content for installed plugins
                                                if (isInstalled && readmeContent != null) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 8.dp
                                                                        )
                            ) {
                                MarkdownTextComposable(
                                                                        text = readmeContent!!,
                                                                        textColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                )
                                                        }
                                                } else if (server.longDescription.isNotBlank()) {
                                                        // Fall back to server description from
                                                        // network
                                                        Box(
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 8.dp
                                                                        )
                                                        ) {
                                                                MarkdownTextComposable(
                                                                        text =
                                                                                server.longDescription,
                                                                        textColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                )
                                                        }
                                                } else {
                                                        // No description available
                                                        Text(
                                                                text =
                                                                        "No detailed description available for this plugin.",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 8.dp
                                                                        )
                                                        )
                        }
                    }
                }

                // 底部操作栏
                                Divider(
                                        color =
                                                MaterialTheme.colorScheme.outlineVariant.copy(
                                                        alpha = 0.5f
                                                )
                                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                horizontalArrangement =
                                                        Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // GitHub仓库链接
                        if (server.repoUrl.isNotBlank()) {
                            val uriHandler = LocalUriHandler.current
                            Button(
                                onClick = {
                                    try {
                                                                                uriHandler.openUri(
                                                                                        server.repoUrl
                                                                                )
                                    } catch (e: Exception) {
                                                                                Log.e(
                                                                                        "MCPServerDetailsDialog",
                                                                                        "打开仓库链接失败",
                                                                                        e
                                                                                )
                                                                        }
                                                                },
                                                                colors =
                                                                        ButtonDefaults.buttonColors(
                                                                                containerColor =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .secondaryContainer,
                                                                                contentColor =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSecondaryContainer
                                                                        ),
                                                                contentPadding =
                                                                        PaddingValues(
                                                                                horizontal = 12.dp,
                                                                                vertical = 8.dp
                                                                        ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "查看仓库",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium
                                )
                            }
                        }

                        // 安装/卸载按钮
                        Button(
                            onClick = {
                                if (server.isInstalled) {
                                    onUninstall(server)
                                } else {
                                    onInstall(server)
                                }
                            },
                                                        colors =
                                                                ButtonDefaults.buttonColors(
                                                                        containerColor =
                                                                                if (server.isInstalled
                                                                                )
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .error
                                                                                else
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                ),
                                                        contentPadding =
                                                                PaddingValues(
                                                                        horizontal = 16.dp,
                                                                        vertical = 8.dp
                                                                ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(
                                                                imageVector =
                                                                        if (server.isInstalled)
                                                                                Icons.Default.Close
                                                                        else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                                                text =
                                                                        if (server.isInstalled)
                                                                                "卸载插件"
                                                                        else "安装插件",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.ai.assistance.operit.ui.features.packages.components.dialogs.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer

/**
 * Details content component for the MCP server details dialog.
 * 
 * @param server The MCP server to display details for
 * @param isInstalled Whether the server is installed
 * @param readmeContent The README content for installed plugins
 * @param modifier Optional modifier for the component
 */
@Composable
fun MCPServerDetailsContent(
    server: MCPServer,
    isInstalled: Boolean,
    readmeContent: String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        // API key 需求警告
        if (server.requiresApiKey) {
            item {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "需要API密钥",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
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
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    MarkdownTextComposable(
                        text = readmeContent,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (server.longDescription.isNotBlank()) {
                // Fall back to server description from network
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    MarkdownTextComposable(
                        text = server.longDescription,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // No description available
                Text(
                    text = "No detailed description available for this plugin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
} 
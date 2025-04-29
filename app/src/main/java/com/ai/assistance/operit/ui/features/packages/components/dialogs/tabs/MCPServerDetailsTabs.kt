package com.ai.assistance.operit.ui.features.packages.components.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Tabs component for the MCP server details dialog.
 * 
 * @param selectedTabIndex The currently selected tab index
 * @param onTabSelected Callback to be invoked when a tab is selected
 */
@Composable
fun MCPServerDetailsTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column {
            // 轻量级自定义TabRow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 详情选项卡
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .clickable { onTabSelected(0) },
                    color = if (selectedTabIndex == 0)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (selectedTabIndex == 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "插件详情",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedTabIndex == 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 配置选项卡
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .clickable { onTabSelected(1) },
                    color = if (selectedTabIndex == 1)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (selectedTabIndex == 1)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "配置设置",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedTabIndex == 1)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 选项卡下方的指示线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width((LocalConfiguration.current.screenWidthDp.dp - 32.dp) / 2 - 2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(if (selectedTabIndex == 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .offset(
                            x = if (selectedTabIndex == 0)
                                16.dp
                            else
                                16.dp + (LocalConfiguration.current.screenWidthDp.dp - 32.dp) / 2 + 4.dp
                        )
                )
            }
        }
    }
} 
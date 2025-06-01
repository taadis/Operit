package com.ai.assistance.operit.ui.features.packages.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PackageItem(
        name: String,
        description: String,
        isImported: Boolean,
        onClick: () -> Unit,
        onToggleImport: (Boolean) -> Unit
) {
        ElevatedCard(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onClick),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                colors =
                        CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                        )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        // 图标部分
                        Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                                Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Extension,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                }
                        }

                        // 文本内容部分
                        Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                                Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        // 开关组件
                        Switch(
                                checked = isImported,
                                onCheckedChange = onToggleImport,
                                colors =
                                        SwitchDefaults.colors(
                                                checkedThumbColor =
                                                        MaterialTheme.colorScheme.primary,
                                                checkedTrackColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                checkedBorderColor =
                                                        MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor =
                                                        MaterialTheme.colorScheme.outline,
                                                uncheckedTrackColor =
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                uncheckedBorderColor =
                                                        MaterialTheme.colorScheme.outline
                                        )
                        )
                }
        }
}

package com.ai.assistance.operit.ui.features.packages.screens.mcp.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.data.mcp.MCPDeployer.DeploymentStatus

/** MCP插件部署进度对话框 */
@Composable
fun MCPDeployProgressDialog(
        deploymentStatus: DeploymentStatus,
        onDismissRequest: () -> Unit,
        onRetry: (() -> Unit)? = null,
        pluginName: String,
        outputMessages: List<String> = emptyList()
) {
    Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                    DialogProperties(
                            dismissOnBackPress = deploymentStatus !is DeploymentStatus.InProgress,
                            dismissOnClickOutside = deploymentStatus !is DeploymentStatus.InProgress
                    )
    ) {
        Card(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // 标题栏
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "部署插件: $pluginName",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                    )

                    // 只有在非进行中状态才显示关闭按钮
                    if (deploymentStatus !is DeploymentStatus.InProgress) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 状态显示
                when (deploymentStatus) {
                    is DeploymentStatus.NotStarted -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(text = "准备部署...", modifier = Modifier.padding(vertical = 8.dp))
                    }
                    is DeploymentStatus.InProgress -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                                text = deploymentStatus.message,
                                modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    is DeploymentStatus.Success -> {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = deploymentStatus.message,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is DeploymentStatus.Error -> {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = deploymentStatus.message,
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // 输出日志区域
                if (outputMessages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "部署日志:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Column(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                            ) {
                                outputMessages.forEach { message ->
                                    Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部按钮
                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                ) {
                    if (deploymentStatus is DeploymentStatus.Error && onRetry != null) {
                        Button(onClick = onRetry, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重试")
                        }
                    }

                    if (deploymentStatus !is DeploymentStatus.InProgress) {
                        Button(onClick = onDismissRequest) { Text("关闭") }
                    }
                }
            }
        }
    }
}

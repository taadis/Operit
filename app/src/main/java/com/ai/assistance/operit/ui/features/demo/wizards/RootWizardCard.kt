package com.ai.assistance.operit.ui.features.demo.wizards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Root权限设置向导卡片
 *
 * @param isDeviceRooted 设备是否已Root
 * @param hasRootAccess 应用是否拥有Root权限
 * @param showWizard 是否显示向导详情
 * @param onToggleWizard 切换向导显示状态的回调
 * @param onRequestRoot 请求Root权限的回调
 * @param onWatchTutorial 查看教程的回调
 */
@Composable
fun RootWizardCard(
        isDeviceRooted: Boolean,
        hasRootAccess: Boolean,
        showWizard: Boolean,
        onToggleWizard: () -> Unit,
        onRequestRoot: () -> Unit,
        onWatchTutorial: () -> Unit
) {
    Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            "Root权限设置向导",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(
                        onClick = onToggleWizard,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                            if (showWizard) "收起" else "展开",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度和状态
            val isComplete = hasRootAccess
            val progress =
                    when {
                        !isDeviceRooted -> 0f
                        !hasRootAccess -> 0.5f
                        else -> 1f
                    }

            Surface(
                    color =
                            if (isComplete) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 进度指示器
                    LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 当前状态
                    val statusText =
                            when {
                                !isDeviceRooted -> "步骤1：Root设备"
                                !hasRootAccess -> "步骤2：授予Root权限"
                                else -> "Root权限已配置完成"
                            }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isComplete) {
                            Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                                text = statusText,
                                style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                        ),
                                color =
                                        if (isComplete) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 详细设置内容，仅在展开时显示
            if (showWizard) {
                Spacer(modifier = Modifier.height(16.dp))

                when {
                    // 已获取Root权限
                    hasRootAccess -> {
                        Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                        "恭喜！Root权限已获取，您现在可以执行需要特权的操作。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 测试命令按钮
                        OutlinedButton(
                                onClick = {
                                    // 这里直接使用 onRequestRoot 来执行一个示例命令
                                    onRequestRoot()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                        ) { Text("测试Root命令", fontSize = 14.sp) }
                    }

                    // 设备已Root但应用未获授权
                    isDeviceRooted -> {
                        Column {
                            Text(
                                    "检测到您的设备已Root，但应用尚未获得Root权限。请点击下方按钮向ROOT管理器（如Magisk、SuperSU等）申请权限。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                    color =
                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.3f
                                            ),
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                            text = "如何获取Root权限：",
                                            style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text =
                                                    "1. 点击下方\"请求Root权限\"按钮\n" +
                                                            "2. 在弹出的ROOT管理器窗口中选择\"允许\"\n" +
                                                            "3. 如未看到弹窗，请检查ROOT管理器设置",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                    onClick = onRequestRoot,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) { Text("请求Root权限", fontSize = 14.sp) }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                    onClick = onWatchTutorial,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) { Text("查看Root教程", fontSize = 14.sp) }
                        }
                    }

                    // 设备未Root
                    else -> {
                        Column {
                            Text(
                                    "未检测到您的设备已Root。ROOT需要特定的系统修改，且可能会导致设备保修失效。如需使用ROOT功能，请先完成设备ROOT操作。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                            text = "注意：Root操作风险提示",
                                            style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                            text =
                                                    "1. ROOT操作具有一定风险，可能导致设备保修失效\n" +
                                                            "2. 部分应用可能会拒绝在ROOT设备上运行\n" +
                                                            "3. 不当的ROOT操作可能导致系统不稳定",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            ElevatedButton(
                                    onClick = onWatchTutorial,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) { Text("查看Root教程", fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}

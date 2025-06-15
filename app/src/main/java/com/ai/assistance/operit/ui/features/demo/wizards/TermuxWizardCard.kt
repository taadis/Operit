package com.ai.assistance.operit.ui.features.demo.wizards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@Composable
fun TermuxWizardCard(
        isTermuxInstalled: Boolean,
        isTermuxAuthorized: Boolean,
        showWizard: Boolean,
        onToggleWizard: (Boolean) -> Unit,
        onInstallBundled: () -> Unit,
        onOpenTermux: () -> Unit,
        onAuthorizeTermux: () -> Unit,
        isTunaSourceEnabled: Boolean = false,
        isPythonInstalled: Boolean = false,
        isUvInstalled: Boolean = false,
        isNodeInstalled: Boolean = false,
        isTermuxRunning: Boolean = false,
        isTermuxBatteryOptimizationExempted: Boolean = false,
        onRequestTermuxBatteryOptimization: () -> Unit = {},
        onConfigureTunaSource: () -> Unit = {},
        onSkipTunaSource: () -> Unit = {},
        onInstallPythonEnv: () -> Unit = {},
        onInstallUvEnv: () -> Unit = {},
        onInstallNodeEnv: () -> Unit = {},
        onDeleteConfig: () -> Unit = {}
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
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            stringResource(R.string.termux_wizard_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(
                        onClick = { onToggleWizard(!showWizard) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                            if (showWizard) stringResource(R.string.wizard_collapse)
                            else stringResource(R.string.wizard_expand),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度和状态
            val isComplete =
                    isTermuxInstalled &&
                            isTermuxAuthorized &&
                            isTermuxRunning &&
                            isTermuxBatteryOptimizationExempted &&
                            isTunaSourceEnabled &&
                            isPythonInstalled &&
                            isUvInstalled &&
                            isNodeInstalled

            Surface(
                    color =
                            if (isComplete) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 进度指示器
                    LinearProgressIndicator(
                            progress =
                                    when {
                                        !isTermuxInstalled -> 0f
                                        !isTermuxAuthorized -> 0.125f
                                        !isTermuxRunning -> 0.25f
                                        !isTermuxBatteryOptimizationExempted -> 0.375f
                                        !isTunaSourceEnabled -> 0.5f
                                        !isPythonInstalled -> 0.625f
                                        !isUvInstalled -> 0.75f
                                        !isNodeInstalled -> 0.875f
                                        else -> 1f
                                    },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 当前状态
                    val statusText =
                            when {
                                !isTermuxInstalled -> stringResource(R.string.termux_wizard_step1)
                                !isTermuxAuthorized -> stringResource(R.string.termux_wizard_step2)
                                !isTermuxRunning -> stringResource(R.string.termux_wizard_step3)
                                !isTermuxBatteryOptimizationExempted ->
                                        stringResource(R.string.termux_wizard_step4)
                                !isTunaSourceEnabled -> stringResource(R.string.termux_wizard_step5)
                                !isPythonInstalled -> stringResource(R.string.termux_wizard_step6)
                                !isUvInstalled -> stringResource(R.string.termux_wizard_step7)
                                !isNodeInstalled -> stringResource(R.string.termux_wizard_step8)
                                else -> stringResource(R.string.termux_wizard_completed)
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

                // 展示当前步骤内容
                when {
                    // 第一步：安装Termux
                    !isTermuxInstalled -> {
                        Column {
                            Text(
                                    stringResource(R.string.termux_wizard_install_message),
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
                                Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string.termux_wizard_install_note
                                                    ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                    onClick = onInstallBundled,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                        stringResource(R.string.termux_wizard_install),
                                        fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // 第二步：授权Termux
                    !isTermuxAuthorized -> {
                        Column {
                            Text(
                                    stringResource(R.string.termux_wizard_auth_message),
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
                                            text = stringResource(R.string.termux_wizard_auth_note),
                                            style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string.termux_wizard_auth_note_details
                                                    ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                        onClick = onDeleteConfig,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                            stringResource(R.string.termux_wizard_start),
                                            fontSize = 14.sp
                                    )
                                }

                                Button(
                                        onClick = onAuthorizeTermux,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                            stringResource(R.string.termux_wizard_check_auth),
                                            fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // 第三步：启动Termux
                    isTermuxAuthorized && !isTermuxRunning -> {
                        Column {
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                        onClick = onDeleteConfig,
                                        modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription =
                                                    stringResource(
                                                            R.string.termux_wizard_reinstall_auth
                                                    ),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                        stringResource(R.string.termux_wizard_reinstall_auth),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                    stringResource(R.string.termux_wizard_start_message),
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
                                            text =
                                                    stringResource(
                                                            R.string.termux_wizard_start_note
                                                    ),
                                            style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string
                                                                    .termux_wizard_start_note_details
                                                    ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                    onClick = onOpenTermux,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                        stringResource(R.string.termux_wizard_start_termux),
                                        fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // 其余步骤用类似的设计风格处理
                    !isComplete -> {
                        // 确定当前要显示的步骤
                        val (title, description, buttonText, action) =
                                when {
                                    !isTermuxBatteryOptimizationExempted ->
                                            Quadruple(
                                                    stringResource(
                                                            R.string.termux_wizard_battery_title
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_battery_message
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_battery_optimize
                                                    ),
                                                    onRequestTermuxBatteryOptimization
                                            )
                                    !isTunaSourceEnabled ->
                                            Quadruple(
                                                    stringResource(
                                                            R.string.termux_wizard_tuna_title
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_tuna_message
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_tuna_config
                                                    ),
                                                    onConfigureTunaSource
                                            )
                                    !isPythonInstalled ->
                                            Quadruple(
                                                    stringResource(
                                                            R.string.termux_wizard_python_title
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_python_message
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_python_install
                                                    ),
                                                    onInstallPythonEnv
                                            )
                                    !isUvInstalled ->
                                            Quadruple(
                                                    stringResource(R.string.termux_wizard_uv_title),
                                                    stringResource(
                                                            R.string.termux_wizard_uv_message
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_uv_install
                                                    ),
                                                    onInstallUvEnv
                                            )
                                    else ->
                                            Quadruple(
                                                    stringResource(
                                                            R.string.termux_wizard_node_title
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_node_message
                                                    ),
                                                    stringResource(
                                                            R.string.termux_wizard_node_install
                                                    ),
                                                    onInstallNodeEnv
                                            )
                                }

                        Column {
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                        onClick = onDeleteConfig,
                                        modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription =
                                                    stringResource(
                                                            R.string.termux_wizard_reinstall_auth
                                                    ),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                        stringResource(R.string.termux_wizard_reinstall_auth),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Surface(
                                    color =
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.3f
                                            ),
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                            text = title,
                                            style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.SemiBold
                                                    ),
                                            color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 配置清华源步骤特殊处理，添加跳过按钮
                            if (!isTunaSourceEnabled &&
                                            isTermuxBatteryOptimizationExempted &&
                                            !isPythonInstalled
                            ) {
                                Button(
                                        onClick = onConfigureTunaSource,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                            stringResource(R.string.termux_wizard_tuna_config),
                                            fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                        onClick = onSkipTunaSource,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                            stringResource(R.string.termux_wizard_tuna_skip),
                                            fontSize = 14.sp
                                    )
                                }
                            } else {
                                Button(
                                        onClick = action,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                ) { Text(buttonText, fontSize = 14.sp) }
                            }
                        }
                    }

                    // 完成状态
                    else -> {
                        Column {
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                        onClick = onDeleteConfig,
                                        modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription =
                                                    stringResource(
                                                            R.string.termux_wizard_reinstall_auth
                                                    ),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                        stringResource(R.string.termux_wizard_reinstall_auth),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }

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

                                    Column {
                                        Text(
                                                stringResource(
                                                        R.string.termux_wizard_success_title
                                                ),
                                                style =
                                                        MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.SemiBold
                                                        ),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        Text(
                                                stringResource(
                                                        R.string.termux_wizard_success_message
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                                .copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            FilledTonalButton(
                                    onClick = onOpenTermux,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(stringResource(R.string.termux_wizard_open), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 辅助类，用于组织数据
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

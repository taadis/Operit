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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                            "Termux 设置向导",
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
                            if (showWizard) "收起" else "展开",
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
                                !isTermuxInstalled -> "步骤1：安装Termux"
                                !isTermuxAuthorized -> "步骤2：授权Termux"
                                !isTermuxRunning -> "步骤3：启动Termux"
                                !isTermuxBatteryOptimizationExempted -> "步骤4：设置电池优化豁免"
                                !isTunaSourceEnabled -> "步骤5：配置清华源"
                                !isPythonInstalled -> "步骤6：安装Python环境"
                                !isUvInstalled -> "步骤7：安装UV包管理器"
                                !isNodeInstalled -> "步骤8：安装Node.js环境"
                                else -> "Termux配置完成"
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
                                    "Termux是一个功能强大的终端模拟器，通过它您可以使用各种命令行工具。我们需要先安装这个应用。",
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
                                            text = "安装完成后，请首次打开Termux并等待初始化完成，然后返回本应用完成授权步骤。",
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
                            ) { Text("安装Termux", fontSize = 14.sp) }
                        }
                    }

                    // 第二步：授权Termux
                    !isTermuxAuthorized -> {
                        Column {
                            Text(
                                    "Termux已安装，现在需要授予Termux必要的权限，这样我们才能执行命令。",
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
                                            text = "授权前请确保：",
                                            style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                            text = "1. Termux已安装并至少打开过一次\n2. 点击「授权Termux」按钮完成授权",
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
                                ) { Text("启动", fontSize = 14.sp) }

                                Button(
                                        onClick = onAuthorizeTermux,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                ) { Text("检查并授权", fontSize = 14.sp) }
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
                                            contentDescription = "重新授权",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                        "重新授权",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                    "Termux已安装并授权，但当前未运行。配置操作需要Termux在后台运行。",
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
                                            text = "注意：",
                                            style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                            text = "Termux已完成授权但未运行。请启动Termux以继续后续配置步骤。",
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
                            ) { Text("启动Termux", fontSize = 14.sp) }
                        }
                    }

                    // 其余步骤用类似的设计风格处理
                    !isComplete -> {
                        // 确定当前要显示的步骤
                        val (title, description, buttonText, action) =
                                when {
                                    !isTermuxBatteryOptimizationExempted ->
                                            Quadruple(
                                                    "设置电池优化豁免",
                                                    "Termux已启动，现在需要为Termux设置电池优化豁免，这样它才能在后台长时间稳定运行。Android系统的电池优化功能可能会在后台关闭Termux，影响脚本和服务的正常运行。",
                                                    "设置电池优化豁免",
                                                    onRequestTermuxBatteryOptimization
                                            )
                                    !isTunaSourceEnabled ->
                                            Quadruple(
                                                    "配置清华源",
                                                    "Termux已设置电池优化豁免，现在需要配置清华源以加速软件包下载。清华源是国内镜像源，可以大幅提高Termux软件包下载速度。",
                                                    "配置清华源",
                                                    onConfigureTunaSource
                                            )
                                    !isPythonInstalled ->
                                            Quadruple(
                                                    "安装Python环境",
                                                    "清华源已配置，现在需要安装Python环境。Python是一种流行的编程语言，许多AI工具和自动化脚本需要它。",
                                                    "安装Python",
                                                    onInstallPythonEnv
                                            )
                                    !isUvInstalled ->
                                            Quadruple(
                                                    "安装UV包管理器",
                                                    "Python已安装，现在需要安装UV包管理器。UV是一个快速的Python包管理器和解析器，可以替代pip，提供更好的依赖管理。",
                                                    "安装UV",
                                                    onInstallUvEnv
                                            )
                                    else ->
                                            Quadruple(
                                                    "安装Node.js环境",
                                                    "UV已安装，现在需要安装Node.js环境。Node.js是一个JavaScript运行环境，用于运行JavaScript代码和Web应用。",
                                                    "安装Node.js",
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
                                            contentDescription = "重新授权",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                        "重新授权",
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

                            Button(
                                    onClick = action,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) { Text(buttonText, fontSize = 14.sp) }
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
                                            contentDescription = "重新授权",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                        "重新授权",
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
                                                "恭喜！Termux环境已完全配置好",
                                                style =
                                                        MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.SemiBold
                                                        ),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        Text(
                                                "现在您可以使用Termux进行各种命令行操作",
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
                            ) { Text("打开Termux", fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}

// 辅助类，用于组织数据
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

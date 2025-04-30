package com.ai.assistance.operit.ui.features.demo.wizards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        isNodeInstalled: Boolean = false,
        onConfigureTunaSource: () -> Unit = {},
        onInstallPythonEnv: () -> Unit = {},
        onInstallNodeEnv: () -> Unit = {}
) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        // 向导标题与折叠/展开按钮
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        "Termux 设置向导",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                OutlinedButton(
                                        onClick = { onToggleWizard(!showWizard) },
                                        colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                        contentColor =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer
                                                )
                                ) { Text(if (showWizard) "收起" else "展开") }
                        }

                        // 显示当前进度
                        LinearProgressIndicator(
                                progress =
                                        when {
                                                !isTermuxInstalled -> 0f
                                                !isTermuxAuthorized -> 0.2f
                                                !isTunaSourceEnabled -> 0.4f
                                                !isPythonInstalled -> 0.6f
                                                !isNodeInstalled -> 0.8f
                                                else -> 1f
                                        },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        // 当前状态文字
                        val statusText =
                                when {
                                        !isTermuxInstalled -> "步骤1：安装 Termux 应用"
                                        !isTermuxAuthorized -> "步骤2：授权 Termux 使用权限"
                                        !isTunaSourceEnabled -> "步骤3：配置清华源"
                                        !isPythonInstalled -> "步骤4：安装 Python 环境"
                                        !isNodeInstalled -> "步骤5：安装 Node.js 环境"
                                        else -> "Termux 已完全设置"
                                }

                        Text(
                                text = statusText,
                                style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 详细设置内容，仅在展开时显示
                        if (showWizard) {
                                when {
                                        // 第一步：安装Termux
                                        !isTermuxInstalled -> {
                                                Text(
                                                        "Termux是一个功能强大的终端模拟器，通过它您可以使用各种命令行工具。我们需要先安装这个应用。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                // 安装说明
                                                Card(
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface
                                                                ),
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(bottom = 8.dp)
                                                ) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                                Text(
                                                                        text = "Termux 安装说明",
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        bottom =
                                                                                                4.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text =
                                                                                "安装完成后，请首次打开Termux并等待初始化完成，然后返回本应用完成授权步骤。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                OutlinedButton(
                                                        onClick = onInstallBundled,
                                                        modifier = Modifier.fillMaxWidth()
                                                ) { Text("安装内置版本") }
                                        }

                                        // 第二步：授权Termux
                                        !isTermuxAuthorized -> {
                                                Text(
                                                        "Termux已安装，现在需要授予Termux必要的权限，这样我们才能执行命令。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                // 授权说明
                                                Card(
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface
                                                                ),
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(bottom = 8.dp)
                                                ) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                                Text(
                                                                        text = "授权说明",
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        bottom =
                                                                                                4.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text =
                                                                                "点击下方按钮自动授权Termux。授权过程需要使用Shizuku服务，请确保Shizuku服务已正确配置。\n\n" +
                                                                                        "如果授权失败，请先打开Termux应用运行一次，然后重试授权。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween
                                                ) {
                                                        OutlinedButton(
                                                                onClick = onOpenTermux,
                                                                modifier =
                                                                        Modifier.weight(1f)
                                                                                .padding(end = 4.dp)
                                                        ) { Text("打开Termux") }

                                                        OutlinedButton(
                                                                onClick = onAuthorizeTermux,
                                                                modifier =
                                                                        Modifier.weight(1f)
                                                                                .padding(
                                                                                        start = 4.dp
                                                                                )
                                                        ) { Text("授权Termux") }
                                                }
                                        }

                                        // 第三步：配置清华源
                                        !isTunaSourceEnabled -> {
                                                Text(
                                                        "Termux已授权，现在需要配置清华源以加速软件包下载。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                // 清华源配置说明
                                                Card(
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface
                                                                ),
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(bottom = 8.dp)
                                                ) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                                Text(
                                                                        text = "清华源配置说明",
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        bottom =
                                                                                                4.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text =
                                                                                "配置清华源可以大幅提高软件包下载速度。点击下方按钮自动配置。\n\n" +
                                                                                        "配置过程需要一段时间，请耐心等待。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                Button(
                                                        onClick = onConfigureTunaSource,
                                                        modifier = Modifier.fillMaxWidth()
                                                ) { Text("配置清华源") }
                                        }

                                        // 第四步：安装Python环境
                                        !isPythonInstalled -> {
                                                Text(
                                                        "清华源已配置，接下来安装Python环境。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                // Python环境安装说明
                                                Card(
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface
                                                                ),
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(bottom = 8.dp)
                                                ) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                                Text(
                                                                        text = "Python环境安装说明",
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        bottom =
                                                                                                4.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text =
                                                                                "安装Python环境，包括Python解释器和pip包管理器。\n\n" +
                                                                                        "安装过程可能需要几分钟，请耐心等待。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                Button(
                                                        onClick = onInstallPythonEnv,
                                                        modifier = Modifier.fillMaxWidth()
                                                ) { Text("安装Python环境") }
                                        }

                                        // 第五步：安装Node.js环境
                                        !isNodeInstalled -> {
                                                Text(
                                                        "Python环境已安装，接下来安装Node.js环境。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                // Node.js环境安装说明
                                                Card(
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface
                                                                ),
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(bottom = 8.dp)
                                                ) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                                Text(
                                                                        text = "Node.js环境安装说明",
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        bottom =
                                                                                                4.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text =
                                                                                "安装Node.js环境，包括Node.js运行时和npm包管理器。\n\n" +
                                                                                        "安装过程可能需要几分钟，请耐心等待。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                Button(
                                                        onClick = onInstallNodeEnv,
                                                        modifier = Modifier.fillMaxWidth()
                                                ) { Text("安装Node.js环境") }
                                        }

                                        // 全部完成
                                        else -> {
                                                Text(
                                                        "恭喜！Termux已完全设置，各个环境已安装完成，您现在可以使用全部功能。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                )

                                                OutlinedButton(
                                                        onClick = onOpenTermux,
                                                        modifier = Modifier.fillMaxWidth()
                                                ) { Text("打开Termux应用") }
                                        }
                                }
                        }
                }
        }
}

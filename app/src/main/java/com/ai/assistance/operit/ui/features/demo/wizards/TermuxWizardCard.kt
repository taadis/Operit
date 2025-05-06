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
        isTermuxRunning: Boolean = false,
        onStartTermux: () -> Unit = {},
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
                                                !isTermuxRunning -> 0.4f
                                                !isTunaSourceEnabled -> 0.55f
                                                !isPythonInstalled -> 0.7f
                                                !isNodeInstalled -> 0.85f
                                                else -> 1f
                                        },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        // 显示当前配置状态
                        Text(
                                text =
                                        when {
                                                !isTermuxInstalled -> "步骤1：安装Termux"
                                                !isTermuxAuthorized -> "步骤2：授权Termux"
                                                !isTermuxRunning -> "步骤3：启动Termux"
                                                !isTunaSourceEnabled -> "步骤4：配置清华源"
                                                !isPythonInstalled -> "步骤5：安装Python环境"
                                                !isNodeInstalled -> "步骤6：安装Node.js环境"
                                                else -> "Termux配置完成"
                                        },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(bottom = 16.dp)
                        )

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

                                                // 安装按钮
                                                Button(
                                                        onClick = onInstallBundled,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 8.dp)
                                                ) { Text("安装内置Termux") }
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
                                                                                "授权前请确保：\n1. Shizuku服务已正常运行并授权\n2. Termux已安装并至少打开过一次\n3. 点击「授权Termux」按钮完成授权",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                // 授权按钮
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 8.dp)
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

                                        // 第三步：启动Termux（新增步骤）
                                        !isTermuxRunning -> {
                                                Text(
                                                        "Termux已安装并授权，但当前未运行。配置操作需要Termux在后台运行。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                // 启动说明
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
                                                                        text = "启动说明",
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
                                                                                "请点击「启动Termux」按钮打开Termux应用，并等待它完成初始化。初始化完成后，Termux会在后台保持运行，您可以继续进行配置操作。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                // 启动按钮
                                                Button(
                                                        onClick = onStartTermux,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 8.dp)
                                                ) { Text("启动Termux") }
                                        }

                                        // 第四步：配置清华源
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
                                                                                "清华源是国内镜像源，可以大幅提高Termux软件包下载速度。点击「配置清华源」按钮自动完成配置。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                // 配置按钮
                                                Button(
                                                        onClick = onConfigureTunaSource,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 8.dp)
                                                ) { Text("配置清华源") }
                                        }

                                        // 第五步：安装Python环境
                                        !isPythonInstalled -> {
                                                Text(
                                                        "清华源已配置，现在需要安装Python环境。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                // Python环境说明
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
                                                                        text = "Python环境说明",
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
                                                                                "Python是一种流行的编程语言，许多AI工具和自动化脚本需要它。点击「安装Python」按钮进行安装。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                // 安装按钮
                                                Button(
                                                        onClick = onInstallPythonEnv,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 8.dp)
                                                ) { Text("安装Python") }
                                        }

                                        // 第六步：安装Node.js环境
                                        !isNodeInstalled -> {
                                                Text(
                                                        "Python已安装，现在需要安装Node.js环境。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                // Node.js环境说明
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
                                                                        text = "Node.js环境说明",
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
                                                                                "Node.js是一个JavaScript运行环境，用于运行JavaScript代码和Web应用。点击「安装Node.js」按钮进行安装。",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )
                                                        }
                                                }

                                                // 安装按钮
                                                Button(
                                                        onClick = onInstallNodeEnv,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 8.dp)
                                                ) { Text("安装Node.js") }
                                        }

                                        // 完成状态
                                        else -> {
                                                Text(
                                                        "恭喜！Termux环境已完全配置好，现在您可以使用Termux进行各种命令行操作。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                Button(
                                                        onClick = onOpenTermux,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 8.dp)
                                                ) { Text("打开Termux") }
                                        }
                                }
                        }
                }
        }
}

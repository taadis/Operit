package com.ai.assistance.operit.ui.features.demo.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.demo.state.sampleAdbCommands
import com.ai.assistance.operit.ui.features.demo.state.termuxSampleCommands

/** ADB command execution component */
@Composable
fun AdbCommandExecutor(
        commandText: String,
        onCommandTextChange: (String) -> Unit,
        resultText: String,
        showSampleCommands: Boolean,
        onToggleSampleCommands: () -> Unit,
        onExecuteCommand: () -> Unit,
        onSampleCommandSelected: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                    text = "ADB命令执行器",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // 命令输入
            OutlinedTextField(
                    value = commandText,
                    onValueChange = onCommandTextChange,
                    label = { Text("输入ADB命令") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // 示例命令按钮
            OutlinedButton(
                    onClick = onToggleSampleCommands,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) { Text(if (showSampleCommands) "隐藏示例命令" else "显示示例命令") }

            // 示例命令列表
            if (showSampleCommands) {
                SampleCommandsCard(
                        commands = sampleAdbCommands,
                        onCommandSelected = onSampleCommandSelected
                )
            }

            // 执行按钮
            Button(
                    onClick = onExecuteCommand,
                    enabled = commandText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) { Text("执行命令") }

            // 结果显示
            Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(text = resultText, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Termux command execution component */
@Composable
fun TermuxCommandExecutor(
        isTermuxAuthorized: Boolean,
        commandText: String,
        onCommandTextChange: (String) -> Unit,
        showSampleCommands: Boolean,
        onToggleSampleCommands: () -> Unit,
        onSampleCommandSelected: (String) -> Unit,
        onAuthorizeTermux: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                    text = "Termux命令执行器",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // 自动授权按钮 - 如果Termux未授权
            if (!isTermuxAuthorized) {
                Button(
                        onClick = onAuthorizeTermux,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) { Text("自动授权Termux (需要Shizuku)") }
            }

            // 命令输入
            OutlinedTextField(
                    value = commandText,
                    onValueChange = onCommandTextChange,
                    label = { Text("输入Termux命令") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // 示例命令按钮
            OutlinedButton(
                    onClick = onToggleSampleCommands,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) { Text(if (showSampleCommands) "隐藏示例命令" else "显示示例命令") }

            // 示例命令列表
            if (showSampleCommands) {
                SampleCommandsCard(
                        commands = termuxSampleCommands,
                        onCommandSelected = onSampleCommandSelected
                )
            }
        }
    }
}

package com.ai.assistance.operit.ui.features.demo.wizards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ShizukuWizardCard(
        isShizukuInstalled: Boolean,
        isShizukuRunning: Boolean,
        hasShizukuPermission: Boolean,
        showWizard: Boolean,
        onToggleWizard: (Boolean) -> Unit,
        onInstallFromStore: () -> Unit,
        onInstallBundled: () -> Unit,
        onOpenShizuku: () -> Unit,
        onWatchTutorial: () -> Unit,
        onRequestPermission: () -> Unit
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
                                        "Shizuku 设置向导",
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
                                                !isShizukuInstalled -> 0f
                                                !isShizukuRunning -> 0.33f
                                                !hasShizukuPermission -> 0.66f
                                                else -> 1f
                                        },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        // 当前状态文字
                        val statusText =
                                when {
                                        !isShizukuInstalled -> "步骤1：安装 Shizuku 应用"
                                        !isShizukuRunning -> "步骤2：启动 Shizuku 服务"
                                        !hasShizukuPermission -> "步骤3：授予 Shizuku 权限"
                                        else -> "Shizuku 已完全设置"
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
                                        // 第一步：安装Shizuku
                                        !isShizukuInstalled -> {
                                                Text(
                                                        "Shizuku是一个授予应用高级权限的工具，不需要root。我们需要先安装这个应用。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

                                                OutlinedButton(
                                                        onClick = onInstallFromStore,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(bottom = 8.dp)
                                                ) { Text("从应用商店安装") }

                                                OutlinedButton(
                                                        onClick = onInstallBundled,
                                                        modifier = Modifier.fillMaxWidth()
                                                ) { Text("安装内置版本") }
                                        }

                                        // 第二步：启动Shizuku服务
                                        !isShizukuRunning -> {
                                                Text(
                                                        "Shizuku已安装，现在需要启动Shizuku服务。请按照以下方法之一操作：",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

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
                                                                        text = "方法1：使用应用内启动（推荐）",
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
                                                                                "1. 点击下方\"打开Shizuku\"按钮\n" +
                                                                                        "2. 在Shizuku应用中选择\"通过无线调试启动\"\n" +
                                                                                        "3. 根据提示开启\"开发者选项\"和\"无线调试\"\n" +
                                                                                        "4. 允许Shizuku获取无线调试权限",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        8.dp
                                                                                )
                                                                )

                                                                Text(
                                                                        text = "方法2：使用USB启动",
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
                                                                                "1. 在电脑上安装ADB工具\n" +
                                                                                        "2. 通过USB连接手机和电脑\n" +
                                                                                        "3. 在电脑命令行执行：\n" +
                                                                                        "   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh",
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
                                                                onClick = onOpenShizuku,
                                                                modifier =
                                                                        Modifier.weight(1f)
                                                                                .padding(end = 4.dp)
                                                        ) { Text("打开Shizuku") }

                                                        OutlinedButton(
                                                                onClick = onWatchTutorial,
                                                                modifier =
                                                                        Modifier.weight(1f)
                                                                                .padding(
                                                                                        start = 4.dp
                                                                                )
                                                        ) { Text("官方文档") }
                                                }
                                        }

                                        // 第三步：授予权限
                                        !hasShizukuPermission -> {
                                                Text(
                                                        "Shizuku服务已启动，现在需要授予权限给本应用。点击下方按钮，在弹出的对话框中选择\"允许\"。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )

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
                                                        Text(
                                                                text =
                                                                        "注意：如果没有看到授权弹窗，请检查：\n" +
                                                                                "1. Shizuku服务是否正在运行\n" +
                                                                                "2. 重启Shizuku服务后再尝试\n" +
                                                                                "3. 确认允许显示悬浮窗权限",
                                                                modifier = Modifier.padding(16.dp),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )
                                                }

                                                OutlinedButton(
                                                        onClick = onRequestPermission,
                                                        modifier = Modifier.fillMaxWidth()
                                                ) { Text("授予Shizuku权限") }
                                        }

                                        // 全部完成
                                        else -> {
                                                Text(
                                                        "恭喜！Shizuku已完全设置，您现在可以使用全部功能。",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onPrimaryContainer,
                                                )
                                        }
                                }
                        }
                }
        }
}

package com.ai.assistance.operit.ui.features.demo.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.termux.TermuxAuthorizer
import com.ai.assistance.operit.ui.features.demo.components.AdbCommandExecutor
import com.ai.assistance.operit.ui.features.demo.components.FeatureErrorCard
import com.ai.assistance.operit.ui.features.demo.components.TermuxCommandExecutor
import com.ai.assistance.operit.ui.features.demo.model.ShizukuScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ShizukuDemoScreenCommandUI(
    state: ShizukuScreenState,
    scope: CoroutineScope,
    isTermuxAuthorized: Boolean
) {
    val context = LocalContext.current
    
    // 命令输入部分 - 只在用户长按Shizuku权限状态时显示
    if (state.showAdbCommandExecutor.value &&
                        state.isShizukuRunning.value &&
                        state.hasShizukuPermission.value
    ) {
        AdbCommandExecutor(
                commandText = state.commandText.value,
                onCommandTextChange = { state.commandText.value = it },
                resultText = state.resultText.value,
                showSampleCommands = state.showSampleCommands.value,
                onToggleSampleCommands = {
                    state.showSampleCommands.value = !state.showSampleCommands.value
                },
                onExecuteCommand = {
                    if (state.commandText.value.isNotBlank()) {
                        scope.launch {
                            state.resultText.value = "执行中..."
                            val result =
                                    AndroidShellExecutor.executeAdbCommand(
                                            state.commandText.value
                                    )
                            state.resultText.value =
                                    if (result.success) {
                                        "命令执行成功:\n${result.stdout}"
                                    } else {
                                        "命令执行失败 (退出码: ${result.exitCode}):\n${result.stderr}"
                                    }
                        }
                    }
                },
                onSampleCommandSelected = { state.commandText.value = it }
        )
    } else if (state.showAdbCommandExecutor.value) {
        FeatureErrorCard(
                message =
                        if (!state.isShizukuRunning.value) "请先启动Shizuku服务"
                        else if (!state.hasShizukuPermission.value) "请授予Shizuku权限"
                        else "无法使用ADB命令功能"
        )
    }

    // Termux命令执行器 - 只在用户长按Termux状态时显示
    if (state.showTermuxCommandExecutor.value && state.isTermuxInstalled.value) {
        TermuxCommandExecutor(
                isTermuxAuthorized = isTermuxAuthorized,
                commandText = state.commandText.value,
                onCommandTextChange = { state.commandText.value = it },
                showSampleCommands = state.showSampleCommands.value,
                onToggleSampleCommands = {
                    state.showSampleCommands.value = !state.showSampleCommands.value
                },
                onSampleCommandSelected = { state.commandText.value = it },
                onAuthorizeTermux = {
                    scope.launch {
                        state.resultText.value = "正在授权Termux..."
                        val authorized = TermuxAuthorizer.grantAllTermuxPermissions(context)
                        if (authorized) {
                            state.resultText.value = "Termux授权成功！"
                            state.isTermuxAuthorized.value = true
                            
                            // 显示提示
                            Toast.makeText(context, "Termux授权成功", Toast.LENGTH_SHORT).show()
                        } else {
                            state.resultText.value = "Termux授权失败，请检查Shizuku权限和应用权限"
                            Toast.makeText(context, "Termux授权失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        )
    } else if (state.showTermuxCommandExecutor.value && !state.isTermuxInstalled.value) {
        FeatureErrorCard(message = "请先安装Termux应用")
    }
} 
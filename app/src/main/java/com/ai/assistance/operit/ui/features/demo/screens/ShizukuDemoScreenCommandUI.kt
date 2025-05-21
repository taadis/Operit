package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.termux.TermuxAuthorizer
import com.ai.assistance.operit.ui.features.demo.components.AdbCommandExecutor
import com.ai.assistance.operit.ui.features.demo.components.FeatureErrorCard
import com.ai.assistance.operit.ui.features.demo.components.TermuxCommandExecutor
import com.ai.assistance.operit.ui.features.demo.viewmodel.ShizukuDemoViewModel
import kotlinx.coroutines.launch

@Composable
fun ShizukuDemoScreenCommandUI(
    viewModel: ShizukuDemoViewModel,
    context: Context
) {
    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    
    // 命令输入部分 - 只在用户长按Shizuku权限状态时显示
    if (uiState.showAdbCommandExecutor.value &&
                        uiState.isShizukuRunning.value &&
                        uiState.hasShizukuPermission.value
    ) {
        AdbCommandExecutor(
                commandText = uiState.commandText.value,
                onCommandTextChange = { 
                    viewModel.updateCommandText(it)
                },
                resultText = uiState.resultText.value,
                showSampleCommands = uiState.showSampleCommands.value,
                onToggleSampleCommands = {
                    viewModel.toggleSampleCommands()
                },
                onExecuteCommand = {
                    if (uiState.commandText.value.isNotBlank()) {
                        viewModel.executeAdbCommand()
                    }
                },
                onSampleCommandSelected = { 
                    viewModel.updateCommandText(it)
                }
        )
    } else if (uiState.showAdbCommandExecutor.value) {
        FeatureErrorCard(
                message =
                        if (!uiState.isShizukuRunning.value) "请先启动Shizuku服务"
                        else if (!uiState.hasShizukuPermission.value) "请授予Shizuku权限"
                        else "无法使用ADB命令功能"
        )
    }
    
    // Termux命令输入部分 - 只在用户长按Termux权限状态时显示
    if (uiState.showTermuxCommandExecutor.value &&
                        uiState.isTermuxInstalled.value &&
                        uiState.isTermuxAuthorized.value &&
                        viewModel.isTermuxRunning.value
    ) {
        TermuxCommandExecutor(
                isTermuxAuthorized = uiState.isTermuxAuthorized.value,
                commandText = uiState.commandText.value,
                onCommandTextChange = {
                    viewModel.updateCommandText(it)
                },
                showSampleCommands = uiState.showSampleCommands.value,
                onToggleSampleCommands = {
                    viewModel.toggleSampleCommands()
                },
                onSampleCommandSelected = {
                    viewModel.updateCommandText(it)
                },
                onAuthorizeTermux = {
                    viewModel.authorizeTermux(context)
                }
        )
    } else if (uiState.showTermuxCommandExecutor.value) {
        FeatureErrorCard(
                message =
                        if (!uiState.isTermuxInstalled.value) "请先安装Termux"
                        else if (!uiState.isTermuxAuthorized.value) "请先授权Termux"
                        else if (!viewModel.isTermuxRunning.value) "请先启动Termux"
                        else "无法使用Termux命令功能"
        )
    }
} 
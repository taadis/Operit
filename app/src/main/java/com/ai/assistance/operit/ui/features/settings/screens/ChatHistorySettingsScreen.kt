package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.legacy.LegacyProblemImporterExporter
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ChatHistoryOperation {
    IDLE,
    EXPORTING,
    EXPORTED,
    IMPORTING,
    IMPORTED,
    DELETING,
    DELETED,
    FAILED,
    EXPORTING_PROBLEMS,
    EXPORTED_PROBLEMS,
    IMPORTING_PROBLEMS,
    IMPORTED_PROBLEMS
}

@Composable
fun ChatHistorySettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val legacyProblemHandler = remember { LegacyProblemImporterExporter(context) }

    var totalChatCount by remember { mutableStateOf(0) }
    var totalProblemCount by remember { mutableStateOf(0) }
    var operationState by remember { mutableStateOf(ChatHistoryOperation.IDLE) }
    var operationMessage by remember { mutableStateOf("") }
    var exportedFilePath by remember { mutableStateOf("") }
    var importedCount by remember { mutableStateOf(0) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteProblemsConfirmDialog by remember { mutableStateOf(false) }

    // 文件选择器
    val chatFilePickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    uri?.let {
                        scope.launch {
                            operationState = ChatHistoryOperation.IMPORTING
                            try {
                                val importResult = importChatHistoriesFromUri(context, uri)
                                if (importResult.total > 0) {
                                    importedCount = importResult.total
                                    operationState = ChatHistoryOperation.IMPORTED
                                    operationMessage =
                                            "导入成功：\n" +
                                                    "- 新增记录：${importResult.new}条\n" +
                                                    "- 更新记录：${importResult.updated}条\n" +
                                                    (if (importResult.skipped > 0)
                                                            "- 跳过无效记录：${importResult.skipped}条"
                                                    else "")
                                } else {
                                    operationState = ChatHistoryOperation.FAILED
                                    operationMessage = "导入失败：未找到有效的聊天记录，请确保选择了正确的备份文件"
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                operationState = ChatHistoryOperation.FAILED
                                operationMessage =
                                        "导入失败：${e.localizedMessage ?: e.toString()}\n" +
                                                "请确保选择了有效的Operit聊天记录备份文件"
                            }
                        }
                    }
                }
            }

    // 问题库文件选择器
    val problemFilePickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    uri?.let {
                        scope.launch {
                            operationState = ChatHistoryOperation.IMPORTING_PROBLEMS
                            try {
                                val importResult = legacyProblemHandler.importProblems(context, uri)
                                if (importResult.total > 0) {
                                    importedCount = importResult.total
                                    operationState = ChatHistoryOperation.IMPORTED_PROBLEMS
                                    operationMessage =
                                            "导入成功：\n" +
                                                    "- 新增记录：${importResult.new}条\n" +
                                                    "- 更新记录：${importResult.updated}条\n" +
                                                    (if (importResult.skipped > 0)
                                                            "- 跳过无效记录：${importResult.skipped}条"
                                                    else "")
                                } else {
                                    operationState = ChatHistoryOperation.FAILED
                                    operationMessage = "导入失败：未找到有效的问题库记录，请确保选择了正确的备份文件"
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                operationState = ChatHistoryOperation.FAILED
                                operationMessage =
                                        "导入失败：${e.localizedMessage ?: e.toString()}\n" +
                                                "请确保选择了有效的Operit问题库备份文件"
                            }
                        }
                    }
                }
            }

    // 获取聊天记录数量
    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { chatHistories ->
            totalChatCount = chatHistories.size
        }
    }

    // 获取问题库数量
    LaunchedEffect(Unit) {
        scope.launch {
            totalProblemCount = legacyProblemHandler.getProblemCount()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        // 聊天记录统计信息
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "数据统计", style = MaterialTheme.typography.titleMedium)
                }

                Text(
                        text = "当前共有 $totalChatCount 条聊天记录",
                        style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                        text = "当前共有 $totalProblemCount 条问题库记录",
                        style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 导入导出操作卡片
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (operationState == ChatHistoryOperation.IDLE ||
                                operationState == ChatHistoryOperation.EXPORTED ||
                                operationState == ChatHistoryOperation.IMPORTED ||
                                operationState == ChatHistoryOperation.DELETED ||
                                operationState == ChatHistoryOperation.FAILED ||
                                operationState == ChatHistoryOperation.EXPORTED_PROBLEMS ||
                                operationState == ChatHistoryOperation.IMPORTED_PROBLEMS
                ) {

                    Text(
                            text = "数据备份与恢复",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 操作说明
                    Text(
                            text = "您可以备份聊天记录和问题库数据，或从备份文件中导入数据。导出的备份文件将保存在「下载/Operit」文件夹中。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 警告提示
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.errorContainer.copy(
                                                            alpha = 0.2f
                                                    )
                                    )
                    ) {
                        Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                    text = "注意：清除操作无法撤销，请先备份重要数据！",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // 聊天记录操作
                    Text(
                            text = "聊天记录",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 聊天记录操作按钮 - 改为使用Column布局，按钮更简洁
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            operationState = ChatHistoryOperation.EXPORTING
                                            try {
                                                val filePath = exportChatHistories(context)
                                                if (filePath != null) {
                                                    exportedFilePath = filePath
                                                    operationState = ChatHistoryOperation.EXPORTED
                                                    val chatHistoriesBasic =
                                                            chatHistoryManager.chatHistoriesFlow
                                                                    .first()
                                                    operationMessage =
                                                            "成功导出 ${chatHistoriesBasic.size} 条聊天记录到：\n$filePath"
                                                } else {
                                                    operationState = ChatHistoryOperation.FAILED
                                                    operationMessage = "导出失败：无法创建文件"
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                operationState = ChatHistoryOperation.FAILED
                                                operationMessage =
                                                        "导出失败：${e.localizedMessage ?: e.toString()}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "导出",
                                        modifier = Modifier.padding(end = 4.dp)
                                )
                                Text("导出")
                            }

                            OutlinedButton(
                                    onClick = {
                                        val intent =
                                                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                                    addCategory(Intent.CATEGORY_OPENABLE)
                                                    type = "application/json"
                                                }
                                        chatFilePickerLauncher.launch(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "导入",
                                        modifier = Modifier.padding(end = 4.dp)
                                )
                                Text("导入")
                            }

                            OutlinedButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                    contentColor =
                                                            MaterialTheme.colorScheme.onSurface
                                                                    .copy(alpha = 0.7f)
                                            )
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.padding(end = 4.dp),
                                        tint =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.7f
                                                )
                                )
                                Text("清除")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 问题库操作
                    Text(
                            text = "问题库",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 问题库操作按钮 - 类似地改为使用Column布局
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            operationState = ChatHistoryOperation.EXPORTING_PROBLEMS
                                            try {
                                                val filePath = legacyProblemHandler.exportProblems(context)
                                                if (filePath != null) {
                                                    exportedFilePath = filePath
                                                    operationState =
                                                            ChatHistoryOperation.EXPORTED_PROBLEMS
                                                    val problemCount = legacyProblemHandler.getProblemCount()
                                                    operationMessage =
                                                            "成功导出 $problemCount 条问题库记录到：\n$filePath"
                                                } else {
                                                    operationState = ChatHistoryOperation.FAILED
                                                    operationMessage = "导出失败：问题库为空或无法创建文件"
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                operationState = ChatHistoryOperation.FAILED
                                                operationMessage =
                                                        "导出失败：${e.localizedMessage ?: e.toString()}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "导出",
                                        modifier = Modifier.padding(end = 4.dp)
                                )
                                Text("导出")
                            }

                            OutlinedButton(
                                    onClick = {
                                        val intent =
                                                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                                    addCategory(Intent.CATEGORY_OPENABLE)
                                                    type = "application/json"
                                                }
                                        problemFilePickerLauncher.launch(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "导入",
                                        modifier = Modifier.padding(end = 4.dp)
                                )
                                Text("导入")
                            }

                            OutlinedButton(
                                    onClick = { showDeleteProblemsConfirmDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                    contentColor =
                                                            MaterialTheme.colorScheme.onSurface
                                                                    .copy(alpha = 0.7f)
                                            )
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.padding(end = 4.dp),
                                        tint =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.7f
                                                )
                                )
                                Text("清除")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 显示操作结果
                    if (operationState == ChatHistoryOperation.EXPORTED) {
                        OperationResultCard(
                                title = "导出成功",
                                message = operationMessage,
                                icon = Icons.Default.CloudDownload
                        )
                    } else if (operationState == ChatHistoryOperation.IMPORTED) {
                        OperationResultCard(
                                title = "导入成功",
                                message = operationMessage,
                                icon = Icons.Default.CloudUpload
                        )
                    } else if (operationState == ChatHistoryOperation.DELETED) {
                        OperationResultCard(
                                title = "删除成功",
                                message = operationMessage,
                                icon = Icons.Default.Delete
                        )
                    } else if (operationState == ChatHistoryOperation.FAILED) {
                        OperationResultCard(
                                title = "操作失败",
                                message = operationMessage,
                                icon = Icons.Default.Info,
                                isError = true
                        )
                    } else if (operationState == ChatHistoryOperation.EXPORTED_PROBLEMS) {
                        OperationResultCard(
                                title = "导出成功",
                                message = operationMessage,
                                icon = Icons.Default.CloudDownload
                        )
                    } else if (operationState == ChatHistoryOperation.IMPORTED_PROBLEMS) {
                        OperationResultCard(
                                title = "导入成功",
                                message = operationMessage,
                                icon = Icons.Default.CloudUpload
                        )
                    }
                } else if (operationState == ChatHistoryOperation.EXPORTING) {
                    OperationProgressView(message = "正在导出聊天记录...")
                } else if (operationState == ChatHistoryOperation.IMPORTING) {
                    OperationProgressView(message = "正在导入聊天记录...")
                } else if (operationState == ChatHistoryOperation.DELETING) {
                    OperationProgressView(message = "正在删除聊天记录...")
                } else if (operationState == ChatHistoryOperation.EXPORTING_PROBLEMS) {
                    OperationProgressView(message = "正在导出问题库...")
                } else if (operationState == ChatHistoryOperation.IMPORTING_PROBLEMS) {
                    OperationProgressView(message = "正在导入问题库...")
                }
            }
        }

        // 使用说明卡片
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = "常见问题",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                        text = "为什么要备份数据？",
                        style =
                                MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                        text = "备份聊天记录和问题库可以防止应用卸载或数据丢失时，您的重要内容丢失。定期备份是个好习惯！",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                        text = "导出的文件保存在哪里？",
                        style =
                                MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                        text = "导出的备份文件会保存在您手机的「下载/Operit」文件夹中，文件名包含导出的数据类型、日期和时间。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                        text = "导入后会出现重复的数据吗？",
                        style =
                                MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                        text = "系统会根据记录ID判断，相同ID的记录会被更新而不是重复导入。不同ID的记录会作为新记录添加。",
                        style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // 确认删除对话框
    if (showDeleteConfirmDialog) {
        AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp).size(24.dp)
                        )
                        Text("确认清除聊天记录") 
                    }
                },
                text = { 
                    Text("您确定要清除所有聊天记录吗？此操作无法撤销。\n\n建议您先导出备份再进行清除操作。") 
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showDeleteConfirmDialog = false
                                scope.launch {
                                    operationState = ChatHistoryOperation.DELETING
                                    try {
                                        val deletedCount = deleteAllChatHistories(context)
                                        operationState = ChatHistoryOperation.DELETED
                                        operationMessage = "成功清除 $deletedCount 条聊天记录"
                                    } catch (e: Exception) {
                                        operationState = ChatHistoryOperation.FAILED
                                        operationMessage =
                                                "清除失败：${e.localizedMessage ?: e.toString()}"
                                    }
                                }
                            }
                    ) { Text("确认清除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("取消") }
                }
        )
    }
    
    // 确认删除问题库对话框
    if (showDeleteProblemsConfirmDialog) {
        AlertDialog(
                onDismissRequest = { showDeleteProblemsConfirmDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp).size(24.dp)
                        )
                        Text("确认清除问题库") 
                    }
                },
                text = { 
                    Text("您确定要清除所有问题库记录吗？此操作无法撤销。\n\n建议您先导出备份再进行清除操作。") 
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showDeleteProblemsConfirmDialog = false
                                scope.launch {
                                    operationState = ChatHistoryOperation.DELETING
                                    try {
                                        val deletedCount = legacyProblemHandler.deleteAllProblems()
                                        operationState = ChatHistoryOperation.DELETED
                                        operationMessage = "成功清除 $deletedCount 条问题库记录"
                                    } catch (e: Exception) {
                                        operationState = ChatHistoryOperation.FAILED
                                        operationMessage =
                                                "清除失败：${e.localizedMessage ?: e.toString()}"
                                    }
                                }
                            }
                    ) { Text("确认清除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteProblemsConfirmDialog = false }) { Text("取消") }
                }
        )
    }
}

@Composable
private fun OperationResultCard(
        title: String,
        message: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isError: Boolean = false
) {
    Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isError)
                                            MaterialTheme.colorScheme.errorContainer.copy(
                                                    alpha = 0.2f
                                            )
                                    else
                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.2f
                                            )
                    )
    ) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                            if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                            if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
            )

            Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OperationProgressView(message: String) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))

        Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
        )
    }
}

// 导出聊天记录
private suspend fun exportChatHistories(context: Context): String? =
        withContext(Dispatchers.IO) {
            try {
                // 获取聊天记录管理器
                val chatHistoryManager = ChatHistoryManager.getInstance(context)

                // 获取所有聊天历史ID
                val chatHistoriesBasic = chatHistoryManager.chatHistoriesFlow.first()

                // 创建完整的聊天记录列表，包含所有消息
                val completeHistories = mutableListOf<ChatHistory>()
                for (chatHistory in chatHistoriesBasic) {
                    // 为每个聊天记录加载完整消息
                    val messages = chatHistoryManager.loadChatMessages(chatHistory.id)
                    val completeHistory = chatHistory.copy(messages = messages)
                    completeHistories.add(completeHistory)
                }

                // 准备导出目录
                val downloadDir =
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS
                        )
                val exportDir = File(downloadDir, "Operit")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                // 创建导出文件名
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val exportFile = File(exportDir, "chat_backup_$timestamp.json")

                // 将聊天记录转换为JSON
                val json = Json {
                    prettyPrint = true
                    encodeDefaults = true
                }

                // 写入文件
                val jsonString = json.encodeToString(completeHistories)
                exportFile.writeText(jsonString)

                return@withContext exportFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }

// 导入结果数据类
data class ImportResult(
        val new: Int, // 新增记录数
        val updated: Int, // 更新记录数
        val skipped: Int // 跳过记录数
) {
    val total: Int // 总导入数量
        get() = new + updated
}

// 从URI导入聊天记录
private suspend fun importChatHistoriesFromUri(context: Context, uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                // 获取聊天记录管理器
                val chatHistoryManager = ChatHistoryManager.getInstance(context)

                // 读取URI内容
                val inputStream =
                        context.contentResolver.openInputStream(uri)
                                ?: return@withContext ImportResult(0, 0, 0)
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                if (jsonString.isBlank()) {
                    throw Exception("导入的文件为空")
                }

                // 使用与导出相同的JSON库解析
                val json = Json {
                    ignoreUnknownKeys = true // 添加容错处理
                    isLenient = true // 更宽松的解析
                    encodeDefaults = true
                }

                // 解析JSON
                val chatHistories =
                        try {
                            json.decodeFromString<List<ChatHistory>>(jsonString)
                        } catch (e: Exception) {
                            Log.e("ChatHistorySettings", "使用kotlinx.serialization解析失败", e)
                            // 尝试旧版格式解析
                            try {
                                val gson =
                                        GsonBuilder()
                                                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                                                .create()
                                val type = object : TypeToken<List<ChatHistory>>() {}.type
                                gson.fromJson<List<ChatHistory>>(jsonString, type)
                            } catch (e2: Exception) {
                                Log.e("ChatHistorySettings", "使用Gson解析也失败", e2)
                                throw Exception("无法解析备份文件：${e.message}\n备份文件可能已损坏或格式不兼容")
                            }
                        }

                if (chatHistories.isEmpty()) {
                    return@withContext ImportResult(0, 0, 0)
                }

                // 获取现有聊天ID，用于检查冲突
                val existingChats = chatHistoryManager.chatHistoriesFlow.first()
                val existingIds = existingChats.map { it.id }.toSet()

                // 分类统计
                var newCount = 0 // 新导入数量
                var updatedCount = 0 // 更新数量
                var skippedCount = 0 // 跳过数量（无效记录）

                // 保存到数据库
                for (chatHistory in chatHistories) {
                    // 验证记录有效性
                    if (chatHistory.messages.isEmpty()) {
                        skippedCount++
                        continue
                    }

                    // 检查是否已存在
                    if (existingIds.contains(chatHistory.id)) {
                        // 更新现有记录
                        updatedCount++
                    } else {
                        // 新导入记录
                        newCount++
                    }

                    // 保存或更新记录
                    chatHistoryManager.saveChatHistory(chatHistory)
                }

                // 返回导入结果
                return@withContext ImportResult(newCount, updatedCount, skippedCount)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

// 删除所有聊天记录
private suspend fun deleteAllChatHistories(context: Context): Int =
        withContext(Dispatchers.IO) {
            try {
                // 获取聊天记录管理器
                val chatHistoryManager = ChatHistoryManager.getInstance(context)

                // 获取所有聊天历史
                val chatHistories = chatHistoryManager.chatHistoriesFlow.first()
                val count = chatHistories.size

                // 删除每个聊天历史
                for (chatHistory in chatHistories) {
                    chatHistoryManager.deleteChatHistory(chatHistory.id)
                }

                return@withContext count
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

// No longer need problem-related functions here, they are in LegacyProblemImporterExporter

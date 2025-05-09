package com.ai.assistance.operit.ui.features.chat.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.tools.AIToolHandler
import kotlinx.coroutines.launch

/** 简约风格的附件选择器组件 */
@Composable
fun AttachmentSelectorPanel(
        visible: Boolean,
        onAttachImage: (String) -> Unit,
        onAttachFile: (String) -> Unit,
        onAttachScreenContent: () -> Unit,
        onAttachNotifications: () -> Unit = {},
        onAttachLocation: () -> Unit = {},
        onAttachProblemMemory: (String, String) -> Unit = { _, _ -> },
        userQuery: String = "",
        onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 文件/图片选择器启动器
    val imagePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                uri?.let {
                    // 将Uri转换为绝对路径
                    val filePath = getFilePathFromUri(context, it)
                    filePath?.let { path ->
                        onAttachImage(path)
                        onDismiss()
                    }
                }
            }

    val filePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                uri?.let {
                    // 将Uri转换为绝对路径
                    val filePath = getFilePathFromUri(context, it)
                    filePath?.let { path ->
                        onAttachFile(path)
                        onDismiss()
                    }
                }
            }

    // 附件选择面板 - 使用展开动画，从下方向上展开
    AnimatedVisibility(
            visible = visible,
            enter =
                    expandVertically(
                            animationSpec = tween(200),
                            expandFrom = androidx.compose.ui.Alignment.Bottom
                    ) + fadeIn(),
            exit =
                    shrinkVertically(
                            animationSpec = tween(200),
                            shrinkTowards = androidx.compose.ui.Alignment.Bottom
                    ) + fadeOut()
    ) {
        Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                // 顶部指示器
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Divider(
                            modifier =
                                    Modifier.width(32.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp)),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 第一行选项 - 4个选项
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 照片选项
                    AttachmentOption(
                            icon = Icons.Default.Image,
                            label = "照片",
                            onClick = { imagePickerLauncher.launch("image/*") }
                    )

                    // 视频选项
                    AttachmentOption(
                            icon = Icons.Default.VideoCameraBack,
                            label = "视频",
                            onClick = { imagePickerLauncher.launch("video/*") }
                    )

                    // 音频选项
                    AttachmentOption(
                            icon = Icons.Default.AudioFile,
                            label = "音频",
                            onClick = { imagePickerLauncher.launch("audio/*") }
                    )

                    // 文件选项
                    AttachmentOption(
                            icon = Icons.Default.Description,
                            label = "文件",
                            onClick = { filePickerLauncher.launch("*/*") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 第二行选项 - 4个选项
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 屏幕内容选项
                    AttachmentOption(
                            icon = Icons.Default.ScreenshotMonitor,
                            label = "屏幕内容",
                            onClick = {
                                onAttachScreenContent()
                                onDismiss()
                            }
                    )
                    
                    // 当前通知选项
                    AttachmentOption(
                            icon = Icons.Default.Notifications,
                            label = "当前通知",
                            onClick = {
                                onAttachNotifications()
                                onDismiss()
                            }
                    )
                    
                    // 当前位置选项
                    AttachmentOption(
                            icon = Icons.Default.LocationOn,
                            label = "当前位置",
                            onClick = {
                                onAttachLocation()
                                onDismiss()
                            }
                    )
                    
                    // 问题记忆选项
                    AttachmentOption(
                            icon = Icons.Default.Memory,
                            label = "问题记忆",
                            onClick = {
                                // 使用用户查询通过AITool查询问题库
                                if (userQuery.isNotBlank()) {
                                    coroutineScope.launch {
                                        // 获取AIToolHandler实例
                                        val toolHandler = AIToolHandler.getInstance(context)
                                        
                                        // 创建查询问题库的工具
                                        val queryTool = AITool(
                                            name = "query_problem_library",
                                            parameters = listOf(ToolParameter("query", userQuery))
                                        )
                                        
                                        try {
                                            // 执行工具查询问题库
                                            val result = toolHandler.executeTool(queryTool)
                                            
                                            if (result.success) {
                                                // 查询成功，获取结果
                                                val queryResult = result.result.toString()
                                                
                                                // 创建文件名
                                                val fileName = "问题库查询结果.txt"
                                                
                                                // 将查询结果作为内容传递
                                                onAttachProblemMemory(queryResult, fileName)
                                            } else {
                                                // 查询失败，返回错误信息
                                                val errorMsg = "查询问题库失败: ${result.error ?: "未知错误"}"
                                                onAttachProblemMemory(errorMsg, "查询错误.txt")
                                            }
                                        } catch (e: Exception) {
                                            // 处理异常
                                            val errorMsg = "查询问题库出错: ${e.message}"
                                            onAttachProblemMemory(errorMsg, "查询错误.txt")
                                        }
                                    }
                                }
                                onDismiss()
                            }
                    )
                }
            }
        }
    }
}

// 添加Uri转换为文件路径的工具函数
private fun getFilePathFromUri(context: Context, uri: Uri): String? {
    // 使用ContentResolver获取真实文件路径
    val contentResolver = context.contentResolver

    // 文件URI直接返回路径
    if (uri.scheme == "file") {
        return uri.path
    }

    // 处理content URI
    if (uri.scheme == "content") {
        try {
            // 尝试通过DocumentFile获取路径
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // 尝试获取_data列（真实路径）
                    val dataIndex = it.getColumnIndex("_data")
                    if (dataIndex != -1) {
                        return it.getString(dataIndex)
                    }
                }
            }

            // 如果使用_data列无法获取路径，则直接返回URI的字符串表示
            // 这样应用可以通过ContentResolver直接使用这个URI访问文件
            Log.d("AttachmentSelector", "使用URI字符串: ${uri.toString()}")
            return uri.toString()
        } catch (e: Exception) {
            Log.e("AttachmentSelector", "获取文件路径错误", e)
        }
    }

    return null
}

/** 简约的附件选项组件 */
@Composable
private fun AttachmentOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                    Modifier.clickable(onClick = onClick)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // 图标区域 - 改为圆角方形
        Box(
                modifier =
                        Modifier.size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(
                                                alpha = 0.7f
                                        )
                                ),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 标签
        Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
        )
    }
}

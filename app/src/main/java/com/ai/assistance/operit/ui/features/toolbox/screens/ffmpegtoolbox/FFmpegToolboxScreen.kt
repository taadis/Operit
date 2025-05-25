package com.ai.assistance.operit.ui.features.toolbox.screens.ffmpegtoolbox

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.FFmpegResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.launch

/**
 * FFmpeg工具箱主屏幕 - 直接提供自定义命令功能
 * 简化后的界面，移除了所有导航功能，以避免导航问题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegToolboxScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val aiToolHandler = AIToolHandler.getInstance(context)

    // 状态
    var ffmpegCommand by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var commandResult by remember { mutableStateOf<ToolResult?>(null) }
    var showCommandTemplates by remember { mutableStateOf(false) }

    // 命令模板
    val commandTemplates = listOf(
        CommandTemplate(
            name = "视频转MP4",
            description = "将视频转换为MP4格式",
            command = "-i input.mp4 -c:v libx264 -c:a aac output.mp4"
        ),
        CommandTemplate(
            name = "视频压缩",
            description = "压缩视频文件大小",
            command = "-i input.mp4 -vf scale=1280:-1 -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 128k output.mp4"
        ),
        CommandTemplate(
            name = "视频裁剪",
            description = "裁剪视频的特定片段",
            command = "-i input.mp4 -ss 00:00:30 -t 00:00:10 -c:v copy -c:a copy output.mp4"
        ),
        CommandTemplate(
            name = "提取音频",
            description = "从视频中提取音频",
            command = "-i input.mp4 -vn -acodec copy output.aac"
        ),
        CommandTemplate(
            name = "GIF制作",
            description = "将视频转换为GIF",
            command = "-i input.mp4 -vf \"fps=10,scale=320:-1:flags=lanczos\" -c:v gif output.gif"
        )
    )

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 命令输入区域
        Column {
            Text(
                text = "FFmpeg命令",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = ffmpegCommand,
                onValueChange = { ffmpegCommand = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("输入FFmpeg命令，不需要包含'ffmpeg'前缀") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                maxLines = 5
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showCommandTemplates = !showCommandTemplates },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) { Text("命令模板") }

                Button(
                    onClick = {
                        if (ffmpegCommand.isEmpty()) {
                            Toast.makeText(context, "请输入命令", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isProcessing = true
                        commandResult = null

                        scope.launch {
                            val tool = AITool(
                                name = "ffmpeg_execute",
                                parameters = listOf(
                                    ToolParameter("command", ffmpegCommand)
                                )
                            )

                            try {
                                val result = aiToolHandler.executeTool(tool)
                                commandResult = result
                            } catch (e: Exception) {
                                commandResult = ToolResult(
                                    toolName = "ffmpeg_execute",
                                    success = false,
                                    result = com.ai.assistance.operit.core.tools.StringResultData(""),
                                    error = "执行失败: ${e.message}"
                                )
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing && ffmpegCommand.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text(if (isProcessing) "执行中..." else "执行命令") }
            }
        }

        // 命令模板区域
        if (showCommandTemplates) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "常用命令模板",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    commandTemplates.forEach { template ->
                        TemplateItem(
                            template = template,
                            onSelect = {
                                ffmpegCommand = template.command
                                showCommandTemplates = false
                            }
                        )
                    }

                    Text(
                        text = "注意: 使用模板时，请将input.mp4和output.mp4替换为实际文件路径",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // FFmpeg信息区域
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FFmpeg帮助",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "FFmpeg是一个强大的音视频处理工具。在此界面，您可以直接执行FFmpeg命令。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "常用参数:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "-i: 输入文件\n" +
                            "-c:v: 视频编码器\n" +
                            "-c:a: 音频编码器\n" +
                            "-b:v: 视频比特率\n" +
                            "-b:a: 音频比特率\n" +
                            "-s: 分辨率\n" +
                            "-r: 帧率",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        scope.launch {
                            val tool = AITool(name = "ffmpeg_info", parameters = listOf())
                            try {
                                val result = aiToolHandler.executeTool(tool)
                                commandResult = result
                            } catch (e: Exception) {
                                commandResult = ToolResult(
                                    toolName = "ffmpeg_info",
                                    success = false,
                                    result = com.ai.assistance.operit.core.tools.StringResultData(""),
                                    error = "获取信息失败: ${e.message}"
                                )
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) { Text("查看更多信息") }
            }
        }

        // 结果显示
        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Text(
                text = "正在执行命令，请稍候...",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        commandResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.success)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (result.success) "命令执行成功" else "命令执行失败",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (result.success)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 显示错误信息或执行结果
                    if (!result.success && result.error != null) {
                        Text(
                            text = result.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (result.result is FFmpegResultData) {
                        val ffmpegResult = result.result as FFmpegResultData

                        Text(
                            text = "命令: ${ffmpegResult.command}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        Text(
                            text = "返回码: ${ffmpegResult.returnCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        Text(
                            text = "处理时间: ${ffmpegResult.duration}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        if (ffmpegResult.output.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "输出:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ffmpegResult.output,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateItem(template: CommandTemplate, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = template.command,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            IconButton(onClick = onSelect) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "使用此模板",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

data class CommandTemplate(val name: String, val description: String, val command: String)

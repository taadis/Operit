package com.ai.assistance.operit.ui.features.toolbox.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.tools.AIToolHandler
import kotlinx.coroutines.launch

/** 万能格式转换屏幕，用于各种文件格式之间的转换 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatConverterScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = AIToolHandler.getInstance(context)

    // 状态变量
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var targetFormat by remember { mutableStateOf<String?>(null) }
    var supportedFormats by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val tool = AITool(name = "get_supported_conversions", parameters = emptyList())
            val result = toolHandler.executeTool(tool)
            if (result.success) {
                supportedFormats =
                        result.result.toString().split(",").map { it.trim() }.associate { format ->
                            format to listOf("pdf", "docx", "txt")
                        }
            } else {
                error = result.error ?: "Unknown error"
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    // 执行文件转换
    fun convertFile() {
        if (selectedFile == null || targetFormat == null) {
            error = "请完成所有必要的选择"
            return
        }

        isLoading = true
        error = null

        coroutineScope.launch {
            try {
                val convertFileTool =
                        AITool(
                                name = "convert_file",
                                parameters =
                                        listOf(
                                                ToolParameter("source_path", selectedFile!!),
                                                ToolParameter("target_format", targetFormat!!)
                                        )
                        )

                val result = toolHandler.executeTool(convertFileTool)

                if (!result.success) {
                    error = result.error ?: "转换失败，请重试"
                }
            } catch (e: Exception) {
                Log.e("FormatConverterScreen", "Error converting file", e)
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // 格式转换界面
    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp)
    ) {
        // 文件选择卡片
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        text = "选择文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                        onClick = {
                            // TODO: Implement file picker
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "选择文件",
                            modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择文件")
                }

                selectedFile?.let { file ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                                    RoundedCornerShape(4.dp)
                                            )
                                            .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = file,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 格式选择卡片
        Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                        text = "选择目标格式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                ) {
                    supportedFormats.forEach { (sourceFormat, targetFormats) ->
                        item(span = { GridItemSpan(3) }) {
                            Text(
                                    text = "源格式: $sourceFormat",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(targetFormats) { format ->
                            FormatItem(
                                    format = format,
                                    isSelected = targetFormat == format,
                                    onClick = { targetFormat = format }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 转换按钮
        Button(
                onClick = {
                    if (selectedFile != null && targetFormat != null) {
                        isLoading = true
                        convertFile()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedFile != null && targetFormat != null && !isLoading,
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                        imageVector = Icons.Default.Transform,
                        contentDescription = "转换",
                        modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始转换", style = MaterialTheme.typography.titleMedium)
            }
        }

        error?.let { errorMessage ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/** 文件类别列表项 */
@Composable
fun CategoryItem(category: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(
                                    when {
                                        isSelected ->
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else -> Color.Transparent
                                    }
                            )
                            .clickable(onClick = onClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 类别图标
        Icon(
                imageVector = getCategoryIcon(category),
                contentDescription = null,
                tint =
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        },
                modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 类别名称
        Text(
                text = getCategoryDisplayName(category),
                style = MaterialTheme.typography.bodyMedium,
                color =
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
        )
    }
}

/** 格式选择项 */
@Composable
fun FormatItem(format: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.aspectRatio(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else ->
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                    }
                            )
                            .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color =
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onClick)
                            .padding(4.dp),
            contentAlignment = Alignment.Center
    ) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = getFormatIcon(format),
                    contentDescription = null,
                    tint =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                    text = format.uppercase(),
                    style =
                            MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight =
                                            if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                    color =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )
        }
    }
}

/** 获取类别图标 */
fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "document" -> Icons.Default.Description
        "image" -> Icons.Default.Image
        "audio" -> Icons.Default.AudioFile
        "video" -> Icons.Default.VideoFile
        "archive" -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}

/** 获取类别显示名称 */
fun getCategoryDisplayName(category: String): String {
    return when (category.lowercase()) {
        "document" -> "文档"
        "image" -> "图片"
        "audio" -> "音频"
        "video" -> "视频"
        "archive" -> "压缩包"
        else -> category
    }
}

/** 获取格式图标 */
fun getFormatIcon(format: String): ImageVector {
    return when (format.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "docx" -> Icons.Default.Description
        "txt" -> Icons.Default.TextSnippet
        else -> Icons.Default.InsertDriveFile
    }
}

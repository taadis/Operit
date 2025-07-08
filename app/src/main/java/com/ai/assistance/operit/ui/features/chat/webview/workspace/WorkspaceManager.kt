package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.WebViewHandler
import com.ai.assistance.operit.ui.features.chat.webview.workspace.OpenFileInfo
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.text.font.FontFamily
import com.ai.assistance.operit.ui.features.chat.webview.workspace.getFileIcon
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.LanguageDetector

/**
 * VSCode风格的工作区管理器组件
 * 集成了WebView预览和文件管理功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceManager(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory,
    workspacePath: String,
    onExportClick: (workDir: File) -> Unit
) {
    val context = LocalContext.current
    val webViewNeedsRefresh by actualViewModel.webViewNeedsRefresh.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    
    // 将 webViewHandler 和 webView 实例提升到 remember 中，使其在重组中保持稳定
    val webViewHandler = remember(context) {
        WebViewHandler(context).apply {
            onFileChooserRequest = { intent, callback ->
                actualViewModel.startFileChooserForResult(intent) { resultCode, data ->
                    callback(resultCode, data)
                }
            }
        }
    }
    
    val webView = remember(context) {
        WebView(context).apply {
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            webViewHandler.configureWebView(this)
            loadUrl("http://localhost:8080")
        }
    }

    // 文件管理和标签状态 - 使用 rememberLocal 进行持久化
    var showFileManager by remember { mutableStateOf(false) }
    var openFiles by rememberLocal<List<OpenFileInfo>>(key = "open_files_${currentChat.id}", emptyList())
    var currentFileIndex by rememberLocal(key = "current_file_index_${currentChat.id}", -1)
    var filePreviewStates by remember { mutableStateOf(mapOf<String, Boolean>()) }
    
    // 保存文件函数
    fun saveFile(fileInfo: OpenFileInfo, content: String) {
        coroutineScope.launch {
            val tool = AITool("write_file", listOf(
                ToolParameter("path", fileInfo.path),
                ToolParameter("content", content)
            ))
            
            // 使用toolHandler代替actualViewModel.executeAITool
            val result = toolHandler.executeTool(tool)
            
            // 更新文件列表中的内容
            val index = openFiles.indexOfFirst { it.path == fileInfo.path }
            if (index != -1) {
                val updatedFiles = openFiles.toMutableList()
                updatedFiles[index] = fileInfo.copy(content = content)
                openFiles = updatedFiles
            }
            
            // 如果是HTML文件且正在预览，刷新WebView
            if (fileInfo.isHtml && filePreviewStates[fileInfo.path] == true) {
                actualViewModel.refreshWebView()
            }
        }
    }
    
    // 关闭文件标签
    fun closeFile(index: Int) {
        if (index >= 0 && index < openFiles.size) {
            val updatedFiles = openFiles.toMutableList()
            updatedFiles.removeAt(index)
            openFiles = updatedFiles
            
            // 更新当前选中的标签
            currentFileIndex = when {
                updatedFiles.isEmpty() -> -1
                index >= updatedFiles.size -> updatedFiles.size - 1
                else -> index
            }
        }
    }
    
    // 切换HTML文件预览状态
    fun togglePreview(path: String) {
        filePreviewStates = filePreviewStates.toMutableMap().apply {
            this[path] = !(this[path] ?: false)
        }
    }
    
    // 打开文件
    fun openFile(fileInfo: OpenFileInfo) {
        // 检查文件是否已经打开
        val existingIndex = openFiles.indexOfFirst { it.path == fileInfo.path }
        
        if (existingIndex != -1) {
            // 如果文件已经打开，切换到该标签
            currentFileIndex = existingIndex
        } else {
            // 否则添加到打开的文件列表
            // 注意：直接追加到 rememberLocal 管理的状态上
            openFiles = openFiles + fileInfo
            currentFileIndex = openFiles.size - 1
            
            // 初始化预览状态（HTML文件默认预览）
            if (fileInfo.isHtml) {
                filePreviewStates = filePreviewStates.toMutableMap().apply {
                    this[fileInfo.path] = true
                }
            }
        }
    }
    
    // 新的布局根节点，使用Box来支持FAB和底部面板的覆盖
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 整合后的顶部栏：标签 + 动态操作
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                shadowElevation = 2.dp,
                modifier = Modifier.zIndex(1f) // 强制将标签栏置于顶层，防止被WebView覆盖
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 文件标签栏
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // 预览标签
                        VSCodeTab(
                            title = "预览",
                            icon = Icons.Default.Visibility,
                            isActive = currentFileIndex == -1,
                            onClose = null,
                            onClick = { currentFileIndex = -1 }
                        )
                        
                        // 打开的文件标签
                        openFiles.forEachIndexed { index, fileInfo ->
                            VSCodeTab(
                                title = fileInfo.name,
                                icon = getFileIcon(fileInfo.name), // 使用统一的 getFileIcon
                                isActive = currentFileIndex == index,
                                onClose = { closeFile(index) },
                                onClick = { currentFileIndex = index }
                            )
                        }
                    }

                    // 动态操作区域
                    val currentFile = openFiles.getOrNull(currentFileIndex)
                    if (currentFile != null && currentFile.isHtml) {
                        val isPreview = filePreviewStates[currentFile.path] ?: false
                        IconButton(
                            onClick = { togglePreview(currentFile.path) },
                            // 限制按钮大小，使其与标签高度(40.dp)保持一致，防止撑开父布局
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (isPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                contentDescription = "Toggle Preview"
                            )
                        }
                    }
                }
            }

            // 主内容区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface) // 添加背景色防止闪烁
            ) {
                 when {
                    // 显示WebView预览
                    currentFileIndex == -1 -> {
                        AndroidView(
                            factory = { webView }, // 使用 remember 的实例
                            update = {
                                if (webViewNeedsRefresh) {
                                    it.reload()
                                    actualViewModel.resetWebViewRefreshState()
                                }
                                webViewHandler.currentWebView = it
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 显示打开的文件
                    currentFileIndex in openFiles.indices -> {
                        val fileInfo = openFiles[currentFileIndex]
                        val isPreviewMode = filePreviewStates[fileInfo.path] ?: false

                        if (isPreviewMode && fileInfo.isHtml) {
                            // HTML文件的预览模式也使用WebView
                            AndroidView(
                                factory = { context -> WebView(context) },
                                update = { webView ->
                                    val baseUrl = "file://${File(fileInfo.path).parent}/"
                                    webView.loadDataWithBaseURL(
                                        baseUrl,
                                        fileInfo.content, // 使用最新的文件内容
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // 文件编辑器 - 使用新的CodeEditor组件
                            val fileLanguage = LanguageDetector.detectLanguage(fileInfo.name)
                            
                            CodeEditor(
                                code = fileInfo.content,
                                language = fileLanguage,
                                onCodeChange = { newContent -> 
                                    // 保存文件
                                    val updatedFileInfo = fileInfo.copy(content = newContent)
                                    // 更新列表以触发UI和持久化
                                    val updatedList = openFiles.toMutableList()
                                    updatedList[currentFileIndex] = updatedFileInfo
                                    openFiles = updatedList
                                    
                                    saveFile(updatedFileInfo, newContent) 
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // 从底部弹出的文件管理器面板
        AnimatedVisibility(
            visible = showFileManager,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            // 使用一个Box来正确地将文件管理器对齐到底部
            // 之前的 align 修饰符在 Surface 上不起作用，因为它不是 Box 的直接子元素
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f), // 占据屏幕60%的高度
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column {
                        // 文件管理器标题栏 - 优化样式
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp), // 减少垂直padding
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "文件浏览器",
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = { showFileManager = false },
                                modifier = Modifier.size(36.dp) // 缩小关闭按钮
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        }
                        
                        Divider()
                        
                        // 嵌入文件浏览器组件
                        Box(modifier = Modifier.weight(1f)) {
                            FileBrowser(
                                initialPath = workspacePath,
                                onCancel = { showFileManager = false },
                                isManageMode = true,
                                onFileOpen = { fileInfo ->
                                    openFile(fileInfo)
                                    showFileManager = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 右下角的快捷操作按钮
        if (!showFileManager) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { onExportClick(File(workspacePath)) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "打包并导出工作区"
                    )
                }

                FloatingActionButton(
                    onClick = { showFileManager = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "打开文件管理器"
                    )
                }
            }
        }
    }
}

/**
 * VSCode风格的标签组件
 */
@Composable
fun VSCodeTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClose: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) 
        MaterialTheme.colorScheme.surface 
    else 
        Color.Transparent // 非活动标签背景透明
    
    val contentColor = if (isActive) 
        MaterialTheme.colorScheme.primary 
    else 
        MaterialTheme.colorScheme.onSurfaceVariant
    
    val bottomBorderColor = if (isActive) contentColor else Color.Transparent

    Box(
        modifier = Modifier
            .height(40.dp) // 增加高度
            .background(backgroundColor, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (onClose != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(22.dp).padding(2.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(14.dp),
                            tint = contentColor.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp)) // 保持对齐
                }
            }
            // 活动标签下划线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(bottomBorderColor)
            )
        }
    }
} 
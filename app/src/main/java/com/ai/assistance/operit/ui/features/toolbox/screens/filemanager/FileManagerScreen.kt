package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.DirectoryListingData
import com.ai.assistance.operit.tools.FileInfoData
import com.ai.assistance.operit.tools.FindFilesResultData
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.DisplayMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileContextMenu
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileListItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.TabItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.getFileIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = AIToolHandler.getInstance(context)

    // 状态变量
    var currentPath by remember { mutableStateOf("/sdcard") }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<FileItem?>(null) }
    var selectedFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) } // 多选状态
    var isMultiSelectMode by remember { mutableStateOf(false) } // 多选模式状态

    // 复制/剪切状态
    var clipboardFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isCutOperation by remember { mutableStateOf(false) }
    var clipboardSourcePath by remember { mutableStateOf<String?>(null) }

    // 文件列表项大小状态
    var itemSize by remember { mutableStateOf(1f) }
    val minItemSize = 0.5f
    val maxItemSize = 1.3f
    val itemSizeStep = 0.1f

    // 显示模式状态
    var displayMode by remember { mutableStateOf(DisplayMode.SINGLE_COLUMN) }

    // 记住每个路径的滚动位置
    val scrollPositions = remember { mutableStateMapOf<String, Int>() }

    // 为当前目录创建LazyListState
    val listState = rememberLazyListState()

    // 当前可见的第一个项目的索引
    val firstVisibleItemIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // 用于跟踪是否需要恢复滚动位置
    var pendingScrollPosition by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // 标签页支持
    var tabs by remember { mutableStateOf(listOf(TabItem("/sdcard", "主目录"))) }
    var activeTabIndex by remember { mutableStateOf(0) }

    // 底部操作菜单
    var showBottomActionMenu by remember { mutableStateOf(false) }
    var contextMenuFile by remember { mutableStateOf<FileItem?>(null) }

    // 对话框状态
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showCompressDialog by remember { mutableStateOf(false) }
    var compressFileName by remember { mutableStateOf("") }

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchDialogQuery by remember { mutableStateOf("") }
    var showSearchResultsDialog by remember { mutableStateOf(false) }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var useWildcard by remember { mutableStateOf(true) }

    // 粘贴文件函数
    fun pasteFile() {
        if (clipboardFiles.isNotEmpty()) {
            clipboardSourcePath?.let { sourcePath ->
                coroutineScope.launch {
                    try {
                        isLoading = true
                        error = null

                        withContext(Dispatchers.IO) {
                            clipboardFiles.forEach { file ->
                                val fullSourcePath = "$sourcePath/${file.name}"
                                val fullTargetPath = "$currentPath/${file.name}"

                                val copyTool =
                                        AITool(
                                                name = "copy_file",
                                                parameters =
                                                        listOf(
                                                                ToolParameter(
                                                                        "source",
                                                                        fullSourcePath
                                                                ),
                                                                ToolParameter(
                                                                        "destination",
                                                                        fullTargetPath
                                                                )
                                                        )
                                        )

                                val result = toolHandler.executeTool(copyTool)

                                if (result.success) {
                                    if (isCutOperation) {
                                        val deleteTool =
                                                AITool(
                                                        name = "delete_file",
                                                        parameters =
                                                                listOf(
                                                                        ToolParameter(
                                                                                "path",
                                                                                fullSourcePath
                                                                        )
                                                                )
                                                )
                                        toolHandler.executeTool(deleteTool)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        error = result.error ?: "Unknown error"
                                    }
                                }
                            }

                            // 刷新当前目录
                            val listFilesTool =
                                    AITool(
                                            name = "list_files",
                                            parameters = listOf(ToolParameter("path", currentPath))
                                    )
                            val listResult = toolHandler.executeTool(listFilesTool)
                            if (listResult.success) {
                                val directoryListing = listResult.result as DirectoryListingData
                                val fileList =
                                        directoryListing.entries.map { entry ->
                                            FileItem(
                                                    name = entry.name,
                                                    isDirectory = entry.isDirectory,
                                                    size = entry.size,
                                                    lastModified = entry.lastModified.toLongOrNull() ?: 0
                                            )
                                        }
                                withContext(Dispatchers.Main) {
                                    files = listOf(FileItem("..", true, 0, 0)) + fileList
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FileManagerScreen", "Error performing file operation", e)
                        withContext(Dispatchers.Main) { error = "Error: ${e.message}" }
                    } finally {
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                }
            }
        }
    }

    // 监听滚动位置变化，保存到scrollPositions
    LaunchedEffect(firstVisibleItemIndex.value) {
        if (files.isNotEmpty() && pendingScrollPosition == null) {
            scrollPositions[currentPath] = firstVisibleItemIndex.value
        }
    }

    // 加载当前目录内容
    LaunchedEffect(currentPath) {
        isLoading = true
        error = null

        withContext(Dispatchers.IO) {
            try {
                val listFilesTool =
                        AITool(
                                name = "list_files",
                                parameters = listOf(ToolParameter("path", currentPath))
                        )
                val result = toolHandler.executeTool(listFilesTool)
                if (result.success) {
                    val directoryListing = result.result as DirectoryListingData
                    val fileList =
                            directoryListing.entries.map { entry ->
                                FileItem(
                                        name = entry.name,
                                        isDirectory = entry.isDirectory,
                                        size = entry.size,
                                        lastModified = entry.lastModified.toLongOrNull() ?: 0
                                )
                            }
                    withContext(Dispatchers.Main) {
                        files = listOf(FileItem("..", true, 0, 0)) + fileList

                        // 文件列表加载完成后，检查是否需要恢复滚动位置
                        pendingScrollPosition?.let { (path, position) ->
                            if (path == currentPath && position < files.size) {
                                listState.scrollToItem(position)
                                pendingScrollPosition = null
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { error = result.error ?: "Unknown error" }
                }
            } catch (e: Exception) {
                Log.e("FileManagerScreen", "Error loading directory", e)
                withContext(Dispatchers.Main) { error = "Error: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    // 导航到父目录
    fun navigateUp() {
        if (currentPath != "/sdcard") {
            val parentPath = currentPath.substringBeforeLast("/")
            val currentDirName = currentPath.substringAfterLast("/")

            // 保存当前滚动位置
            if (files.isNotEmpty()) {
                scrollPositions[currentPath] = firstVisibleItemIndex.value
            }

            // 设置需要恢复的滚动位置
            pendingScrollPosition = parentPath to (scrollPositions[parentPath] ?: 0)

            currentPath = parentPath
            if (activeTabIndex < tabs.size) {
                tabs =
                        tabs.toMutableList().apply {
                            this[activeTabIndex] = this[activeTabIndex].copy(path = parentPath)
                        }
            }
        }
    }

    // 导航到子目录
    fun navigateToDirectory(dir: FileItem) {
        if (dir.isDirectory) {
            // 保存当前滚动位置
            if (files.isNotEmpty()) {
                scrollPositions[currentPath] = firstVisibleItemIndex.value
            }

            val newPath =
                    if (dir.name == "..") {
                        navigateUp()
                        return
                    } else {
                        "$currentPath/${dir.name}"
                    }

            // 设置需要恢复的滚动位置
            pendingScrollPosition = newPath to (scrollPositions[newPath] ?: 0)

            currentPath = newPath
            if (activeTabIndex < tabs.size) {
                tabs =
                        tabs.toMutableList().apply {
                            this[activeTabIndex] = this[activeTabIndex].copy(path = newPath)
                        }
            }
        }
    }

    // 添加新标签
    fun addTab(path: String = "/sdcard", title: String = "新标签") {
        tabs = tabs + TabItem(path, title)
        activeTabIndex = tabs.size - 1
        currentPath = path
    }

    // 关闭标签
    fun closeTab(index: Int) {
        if (tabs.size > 1) {
            tabs = tabs.toMutableList().apply { removeAt(index) }
            if (activeTabIndex >= tabs.size) {
                activeTabIndex = tabs.size - 1
            }
            currentPath = tabs[activeTabIndex].path
        }
    }

    // 切换标签
    fun switchTab(index: Int) {
        if (index < tabs.size) {
            // 保存当前滚动位置
            if (files.isNotEmpty()) {
                scrollPositions[currentPath] = firstVisibleItemIndex.value
            }

            // 设置需要恢复的滚动位置
            pendingScrollPosition = tabs[index].path to (scrollPositions[tabs[index].path] ?: 0)

            activeTabIndex = index
            currentPath = tabs[index].path
        }
    }

    // 创建新文件夹
    fun createNewFolder(folderName: String, currentPath: String) {
        coroutineScope.launch {
            try {
                val fullPath = "$currentPath/$folderName"

                val createFolderTool =
                        AITool(
                                name = "create_directory",
                                parameters = listOf(ToolParameter("path", fullPath))
                        )

                val result = toolHandler.executeTool(createFolderTool)

                if (result.success) {
                    // 刷新当前目录
                    isLoading = true
                    val listFilesTool =
                            AITool(
                                    name = "list_files",
                                    parameters = listOf(ToolParameter("path", currentPath))
                            )
                    val listResult = toolHandler.executeTool(listFilesTool)
                    if (listResult.success) {
                        val directoryListing = listResult.result as DirectoryListingData
                        val fileList =
                                directoryListing.entries.map { entry ->
                                    FileItem(
                                            name = entry.name,
                                            isDirectory = entry.isDirectory,
                                            size = entry.size,
                                            lastModified = entry.lastModified.toLongOrNull() ?: 0
                                    )
                                }
                        files = listOf(FileItem("..", true, 0, 0)) + fileList
                    }
                } else {
                    error = result.error ?: "Unknown error"
                }
            } catch (e: Exception) {
                Log.e("FileManagerScreen", "Error creating folder", e)
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // 搜索文件函数
    fun searchFiles(query: String) {
        if (query.isBlank()) {
            isSearching = false
            searchResults = emptyList()
            return
        }

        coroutineScope.launch {
            isLoading = true
            error = null
            isSearching = true

            withContext(Dispatchers.IO) {
                try {
                    // 处理通配符
                    val searchPattern = if (useWildcard) "*$query*" else query
                    
                    val searchTool =
                            AITool(
                                    name = "find_files",
                                    parameters =
                                            listOf(
                                                    ToolParameter("path", currentPath),
                                                    ToolParameter("pattern", searchPattern),
                                                    ToolParameter("case_sensitive", isCaseSensitive.toString())
                                            )
                            )

                    val result = toolHandler.executeTool(searchTool)
                    if (result.success) {
                        // 使用正确的FindFilesResultData类型解析结果
                        val findResult = result.result as FindFilesResultData
                        val fileList = findResult.files.map { filePath ->
                            // 从完整路径中提取文件名
                            val fileName = filePath.substringAfterLast("/")
                            // 检查是否为目录
                            val isDir = try {
                                val fileInfoTool = AITool(
                                    name = "file_info",
                                    parameters = listOf(ToolParameter("path", filePath))
                                )
                                val fileInfoResult = toolHandler.executeTool(fileInfoTool)
                                if (fileInfoResult.success) {
                                    val fileInfo = fileInfoResult.result as FileInfoData
                                    fileInfo.fileType == "directory"
                                } else {
                                    false
                                }
                            } catch (e: Exception) {
                                false
                            }
                            
                            // 创建FileItem，保存完整路径
                            FileItem(
                                name = fileName,
                                isDirectory = isDir,
                                size = 0, // 大小信息暂时不获取
                                lastModified = 0, // 修改时间暂时不获取
                                fullPath = filePath // 保存完整路径
                            )
                        }
                        withContext(Dispatchers.Main) { 
                            searchResults = fileList
                            showSearchResultsDialog = true
                        }
                    } else {
                        withContext(Dispatchers.Main) { error = result.error ?: "搜索失败" }
                    }
                } catch (e: Exception) {
                    Log.e("FileManagerScreen", "Error searching files", e)
                    withContext(Dispatchers.Main) { error = "搜索错误: ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    // 导航到文件所在目录
    fun navigateToFileDirectory(filePath: String) {
        val directoryPath = filePath.substringBeforeLast("/")
        if (directoryPath.isNotEmpty()) {
            // 保存当前滚动位置
            if (files.isNotEmpty()) {
                scrollPositions[currentPath] = firstVisibleItemIndex.value
            }

            // 设置需要恢复的滚动位置
            pendingScrollPosition = directoryPath to (scrollPositions[directoryPath] ?: 0)

            currentPath = directoryPath
            if (activeTabIndex < tabs.size) {
                tabs =
                        tabs.toMutableList().apply {
                            this[activeTabIndex] = this[activeTabIndex].copy(path = directoryPath)
                        }
            }
            
            // 关闭搜索结果对话框
            showSearchResultsDialog = false
            isSearching = false
        }
    }

    // Windows 11风格的文件管理器界面
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 工具栏
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
            ) {
                Column {
                    // 导航按钮行 - 改为可水平滚动
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val scrollState = rememberScrollState()
                        Row(
                                modifier =
                                        Modifier.horizontalScroll(scrollState)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 返回按钮
                            IconButton(
                                    onClick = {
                                        if (currentPath != "/sdcard") {
                                            navigateUp()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "返回上级目录",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 前进按钮
                            IconButton(onClick = { /* 前进功能 */}, modifier = Modifier.size(36.dp)) {
                                Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "前进",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 向上按钮
                            IconButton(
                                    onClick = {
                                        if (currentPath != "/sdcard") {
                                            navigateUp()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "向上",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 刷新按钮
                            IconButton(
                                    onClick = {
                                        isLoading = true
                                        error = null
                                        coroutineScope.launch {
                                            try {
                                                val listFilesTool =
                                                        AITool(
                                                                name = "list_files",
                                                                parameters =
                                                                        listOf(
                                                                                ToolParameter(
                                                                                        "path",
                                                                                        currentPath
                                                                                )
                                                                        )
                                                        )
                                                val result = toolHandler.executeTool(listFilesTool)
                                                if (result.success) {
                                                    val directoryListing =
                                                            result.result as DirectoryListingData
                                                    val fileList =
                                                            directoryListing.entries.map { entry ->
                                                                FileItem(
                                                                        name = entry.name,
                                                                        isDirectory =
                                                                                entry.isDirectory,
                                                                        size = entry.size,
                                                                        lastModified =
                                                                                entry.lastModified
                                                                                        .toLongOrNull()
                                                                                        ?: 0
                                                                )
                                                            }
                                                    files = listOf(FileItem("..", true, 0, 0)) + fileList
                                                } else {
                                                    error = result.error ?: "Unknown error"
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                        "FileManagerScreen",
                                                        "Error loading directory",
                                                        e
                                                )
                                                error = "Error: ${e.message}"
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "刷新",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 分隔线
                            Divider(
                                    modifier = Modifier.height(24.dp).width(1.dp),
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // 缩小按钮
                            IconButton(
                                    onClick = {
                                        if (itemSize > minItemSize) {
                                            itemSize -= itemSizeStep
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    enabled = itemSize > minItemSize
                            ) {
                                Icon(
                                        imageVector = Icons.Default.ZoomOut,
                                        contentDescription = "缩小",
                                        tint =
                                                if (itemSize > minItemSize)
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.38f)
                                )
                            }

                            // 放大按钮
                            IconButton(
                                    onClick = {
                                        if (itemSize < maxItemSize) {
                                            itemSize += itemSizeStep
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    enabled = itemSize < maxItemSize
                            ) {
                                Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "放大",
                                        tint =
                                                if (itemSize < maxItemSize)
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.38f)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 分隔线
                            Divider(
                                    modifier = Modifier.height(24.dp).width(1.dp),
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // 多选模式切换按钮
                            IconButton(
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            isMultiSelectMode = false
                                            selectedFiles = emptyList()
                                        } else {
                                            isMultiSelectMode = true
                                            selectedFiles = emptyList()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                        imageVector =
                                                if (isMultiSelectMode) Icons.Default.CheckBox
                                                else Icons.Default.CheckBoxOutlineBlank,
                                        contentDescription = "切换多选模式",
                                        tint =
                                                if (isMultiSelectMode)
                                                        MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 粘贴按钮
                            IconButton(
                                    onClick = { pasteFile() },
                                    modifier = Modifier.size(36.dp),
                                    enabled = clipboardFiles.isNotEmpty()
                            ) {
                                Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "粘贴",
                                        tint =
                                                if (clipboardFiles.isNotEmpty())
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.38f)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 分隔线
                            Divider(
                                    modifier = Modifier.height(24.dp).width(1.dp),
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // 显示模式选择按钮
                            IconButton(
                                    onClick = {
                                        displayMode =
                                                when (displayMode) {
                                                    DisplayMode.SINGLE_COLUMN ->
                                                            DisplayMode.TWO_COLUMNS
                                                    DisplayMode.TWO_COLUMNS ->
                                                            DisplayMode.THREE_COLUMNS
                                                    DisplayMode.THREE_COLUMNS ->
                                                            DisplayMode.SINGLE_COLUMN
                                                }
                                    },
                                    modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                        imageVector =
                                                when (displayMode) {
                                                    DisplayMode.SINGLE_COLUMN ->
                                                            Icons.Default.ViewList
                                                    DisplayMode.TWO_COLUMNS ->
                                                            Icons.Default.ViewModule
                                                    DisplayMode.THREE_COLUMNS ->
                                                            Icons.Default.ViewComfy
                                                },
                                        contentDescription = "切换显示模式",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 分隔线
                            Divider(
                                    modifier = Modifier.height(24.dp).width(1.dp),
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // 搜索按钮 - 替换搜索框
                            IconButton(
                                    onClick = { 
                                        searchDialogQuery = ""
                                        showSearchDialog = true
                                    },
                                    modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "搜索文件",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 返回按钮 - 在搜索模式下显示
                            if (isSearching) {
                                IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            isSearching = false
                                            searchResults = emptyList()
                                        },
                                        modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "返回文件夹视图",
                                            tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // 新建文件夹按钮
                            IconButton(
                                    onClick = {
                                        newFolderName = ""
                                        showNewFolderDialog = true
                                    },
                                    modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CreateNewFolder,
                                        contentDescription = "新建文件夹",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 路径导航栏
                    Surface(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 1.dp
                    ) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                    text = currentPath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 标签栏
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    ScrollableTabRow(
                            selectedTabIndex = activeTabIndex,
                            edgePadding = 0.dp,
                            modifier = Modifier.weight(1f),
                            indicator = { tabPositions ->
                                if (tabPositions.isNotEmpty() && activeTabIndex < tabPositions.size
                                ) {
                                    TabRowDefaults.Indicator(
                                            modifier =
                                                    Modifier.tabIndicatorOffset(
                                                            tabPositions[activeTabIndex]
                                                    ),
                                            height = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            divider = {}
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                    selected = index == activeTabIndex,
                                    onClick = { switchTab(index) },
                                    text = {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint =
                                                            if (index == activeTabIndex)
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                            else
                                                                    MaterialTheme.colorScheme
                                                                            .onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                            )

                                            Text(
                                                    text = tab.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color =
                                                            if (index == activeTabIndex)
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                            else
                                                                    MaterialTheme.colorScheme
                                                                            .onSurfaceVariant
                                            )

                                            if (tabs.size > 1) {
                                                IconButton(
                                                        onClick = { closeTab(index) },
                                                        modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "关闭标签",
                                                            tint =
                                                                    if (index == activeTabIndex)
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primary
                                                                    else
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    // 添加新标签按钮 - 移动到标签选择的右侧
                    IconButton(onClick = { addTab() }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加新标签",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 主内容区域
            Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (error != null) {
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    tonalElevation = 2.dp
                            ) {
                                Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                            text = error!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                                state = listState,
                                modifier =
                                        Modifier.fillMaxSize()
                                                .combinedClickable(
                                                        onClick = { /* 点击空白区域不做任何操作 */},
                                                        onLongClick = {
                                                            if (isMultiSelectMode &&
                                                                            selectedFiles
                                                                                    .isNotEmpty()
                                                            ) {
                                                                showBottomActionMenu = true
                                                            }
                                                        }
                                                ),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val displayFiles = if (isSearching) searchResults else files

                            when (displayMode) {
                                DisplayMode.SINGLE_COLUMN -> {
                                    items(displayFiles) { file ->
                                        FileListItem(
                                                file = file,
                                                isSelected =
                                                        if (isMultiSelectMode)
                                                                selectedFiles.contains(file)
                                                        else selectedFile == file,
                                                onItemClick = {
                                                    if (isMultiSelectMode) {
                                                        selectedFiles =
                                                                if (selectedFiles.contains(file)) {
                                                                    selectedFiles - file
                                                                } else {
                                                                    selectedFiles + file
                                                                }
                                                    } else {
                                                        if (file.isDirectory) {
                                                            navigateToDirectory(file)
                                                        } else {
                                                            selectedFile = file
                                                        }
                                                    }
                                                },
                                                onItemLongClick = {
                                                    if (isMultiSelectMode) {
                                                        if (selectedFiles.contains(file)) {
                                                            showBottomActionMenu = true
                                                        } else {
                                                            selectedFiles = selectedFiles + file
                                                        }
                                                    } else {
                                                        contextMenuFile = file
                                                        showBottomActionMenu = true
                                                    }
                                                },
                                                itemSize = itemSize,
                                                displayMode = displayMode
                                        )
                                    }
                                }
                                DisplayMode.TWO_COLUMNS -> {
                                    items(displayFiles.chunked(2)) { row ->
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            row.forEach { file ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    FileListItem(
                                                            file = file,
                                                            isSelected =
                                                                    if (isMultiSelectMode)
                                                                            selectedFiles.contains(
                                                                                    file
                                                                            )
                                                                    else selectedFile == file,
                                                            onItemClick = {
                                                                if (isMultiSelectMode) {
                                                                    selectedFiles =
                                                                            if (selectedFiles
                                                                                            .contains(
                                                                                                    file
                                                                                            )
                                                                            ) {
                                                                                selectedFiles - file
                                                                            } else {
                                                                                selectedFiles + file
                                                                            }
                                                                } else {
                                                                    if (file.isDirectory) {
                                                                        navigateToDirectory(file)
                                                                    } else {
                                                                        selectedFile = file
                                                                    }
                                                                }
                                                            },
                                                            onItemLongClick = {
                                                                if (isMultiSelectMode) {
                                                                    if (selectedFiles.contains(file)
                                                                    ) {
                                                                        showBottomActionMenu = true
                                                                    } else {
                                                                        selectedFiles =
                                                                                selectedFiles + file
                                                                    }
                                                                } else {
                                                                    contextMenuFile = file
                                                                    showBottomActionMenu = true
                                                                }
                                                            },
                                                            itemSize = itemSize,
                                                            displayMode = displayMode
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                DisplayMode.THREE_COLUMNS -> {
                                    items(displayFiles.chunked(3)) { row ->
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            row.forEach { file ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    FileListItem(
                                                            file = file,
                                                            isSelected =
                                                                    if (isMultiSelectMode)
                                                                            selectedFiles.contains(
                                                                                    file
                                                                            )
                                                                    else selectedFile == file,
                                                            onItemClick = {
                                                                if (isMultiSelectMode) {
                                                                    selectedFiles =
                                                                            if (selectedFiles
                                                                                            .contains(
                                                                                                    file
                                                                                            )
                                                                            ) {
                                                                                selectedFiles - file
                                                                            } else {
                                                                                selectedFiles + file
                                                                            }
                                                                } else {
                                                                    if (file.isDirectory) {
                                                                        navigateToDirectory(file)
                                                                    } else {
                                                                        selectedFile = file
                                                                    }
                                                                }
                                                            },
                                                            onItemLongClick = {
                                                                if (isMultiSelectMode) {
                                                                    if (selectedFiles.contains(file)
                                                                    ) {
                                                                        showBottomActionMenu = true
                                                                    } else {
                                                                        selectedFiles =
                                                                                selectedFiles + file
                                                                    }
                                                                } else {
                                                                    contextMenuFile = file
                                                                    showBottomActionMenu = true
                                                                }
                                                            },
                                                            itemSize = itemSize,
                                                            displayMode = displayMode
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 状态栏
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
            ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isMultiSelectMode) {
                        Text(
                                text = "已选择 ${selectedFiles.size} 个项目",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedFiles = emptyList()
                                }
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "取消选择",
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                                text = "${files.size} 个项目",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        selectedFile?.let { file ->
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                            Modifier.clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                            MaterialTheme.colorScheme
                                                                    .primaryContainer
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                        imageVector = getFileIcon(file),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 全屏加载覆盖层
        if (isLoading) {
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.7f
                                            )
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Box(
                        modifier =
                                Modifier.background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(16.dp)
                                        )
                                        .padding(24.dp)
                ) {
                    Text(
                            text = "正在加载文件...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 底部操作菜单
    FileContextMenu(
            showMenu = showBottomActionMenu,
            onDismissRequest = { showBottomActionMenu = false },
            contextMenuFile = contextMenuFile,
            isMultiSelectMode = isMultiSelectMode,
            selectedFiles = selectedFiles,
            currentPath = currentPath,
            onFilesUpdated = {
                // 刷新当前目录
                isLoading = true
                error = null
                coroutineScope.launch {
                    try {
                        val listFilesTool =
                                AITool(
                                        name = "list_files",
                                        parameters = listOf(ToolParameter("path", currentPath))
                                )
                        val result = toolHandler.executeTool(listFilesTool)
                        if (result.success) {
                            val directoryListing = result.result as DirectoryListingData
                            val fileList =
                                    directoryListing.entries.map { entry ->
                                        FileItem(
                                                name = entry.name,
                                                isDirectory = entry.isDirectory,
                                                size = entry.size,
                                                lastModified = entry.lastModified.toLongOrNull()
                                                                ?: 0
                                        )
                                    }
                            files = listOf(FileItem("..", true, 0, 0)) + fileList
                        } else {
                            error = result.error ?: "Unknown error"
                        }
                    } catch (e: Exception) {
                        Log.e("FileManagerScreen", "Error loading directory", e)
                        error = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            toolHandler = toolHandler,
            onPaste = { pasteFile() },
            onCopy = { files ->
                if (files.isNotEmpty()) {
                    clipboardFiles = files
                    clipboardSourcePath = currentPath
                    isCutOperation = false
                }
            },
            onCut = { files ->
                if (files.isNotEmpty()) {
                    clipboardFiles = files
                    clipboardSourcePath = currentPath
                    isCutOperation = true
                }
            },
            onOpen = { file ->
                // 打开文件
                coroutineScope.launch {
                    try {
                        isLoading = true
                        error = null

                        withContext(Dispatchers.IO) {
                            val fullPath = "$currentPath/${file.name}"

                            val openTool =
                                    AITool(
                                            name = "open_file",
                                            parameters = listOf(ToolParameter("path", fullPath))
                                    )

                            val result = toolHandler.executeTool(openTool)

                            if (!result.success) {
                                withContext(Dispatchers.Main) { error = result.error ?: "打开文件失败" }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FileManagerScreen", "Error opening file", e)
                        withContext(Dispatchers.Main) { error = "打开文件错误: ${e.message}" }
                    } finally {
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                }
            },
            onShare = { file ->
                // 分享文件
                coroutineScope.launch {
                    try {
                        isLoading = true
                        error = null

                        withContext(Dispatchers.IO) {
                            val fullPath = "$currentPath/${file.name}"

                            val shareTool =
                                    AITool(
                                            name = "share_file",
                                            parameters = listOf(ToolParameter("path", fullPath))
                                    )

                            val result = toolHandler.executeTool(shareTool)

                            if (!result.success) {
                                withContext(Dispatchers.Main) { error = result.error ?: "分享文件失败" }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FileManagerScreen", "Error sharing file", e)
                        withContext(Dispatchers.Main) { error = "分享文件错误: ${e.message}" }
                    } finally {
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                }
            }
    )

    // 搜索对话框
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("搜索文件") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchDialogQuery,
                        onValueChange = { searchDialogQuery = it },
                        placeholder = { Text("输入搜索关键词...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // 搜索选项
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isCaseSensitive,
                            onCheckedChange = { isCaseSensitive = it }
                        )
                        Text("区分大小写")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = useWildcard,
                            onCheckedChange = { useWildcard = it }
                        )
                        Text("使用通配符 (*关键词*)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        searchQuery = searchDialogQuery
                        showSearchDialog = false
                        if (searchDialogQuery.isNotBlank()) {
                            searchFiles(searchDialogQuery)
                        }
                    }
                ) {
                    Text("搜索")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 搜索结果对话框
    if (showSearchResultsDialog) {
        AlertDialog(
            onDismissRequest = { showSearchResultsDialog = false },
            title = { 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("搜索结果: ${searchResults.size} 个文件")
                    IconButton(onClick = { showSearchResultsDialog = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                }
            },
            text = {
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("未找到匹配的文件")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(searchResults) { file ->
                            Surface(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { 
                                        file.fullPath?.let { path -> navigateToFileDirectory(path) }
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (file.isDirectory) Icons.Default.Folder else getFileIcon(file),
                                        contentDescription = null,
                                        tint = if (file.isDirectory) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = file.fullPath ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchResultsDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

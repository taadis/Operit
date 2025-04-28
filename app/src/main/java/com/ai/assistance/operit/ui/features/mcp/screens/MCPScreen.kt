package com.ai.assistance.operit.ui.features.mcp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.SortDirection
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.SortOptions
import com.ai.assistance.operit.ui.features.mcp.components.LoadingItem
import com.ai.assistance.operit.ui.features.mcp.components.MCPInstallProgressDialog
import com.ai.assistance.operit.ui.features.mcp.components.MCPServerDetailsDialog
import com.ai.assistance.operit.ui.features.mcp.components.MCPServerItem
import com.ai.assistance.operit.ui.features.mcp.components.RefreshingIndicator
import com.ai.assistance.operit.ui.features.mcp.model.MCPServer
import com.ai.assistance.operit.ui.features.mcp.viewmodel.MCPViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The main screen for displaying and managing MCP servers.
 *
 * @param mcpRepository The repository for fetching and managing MCP servers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPScreen(mcpRepository: MCPRepository) {
    // 创建 ViewModel
    val viewModel = remember {
        MCPViewModel.Factory(mcpRepository).create(MCPViewModel::class.java)
    }

    // 安装状态
    val installProgress by viewModel.installProgress.collectAsState()
    val installResult by viewModel.installResult.collectAsState()
    val currentInstallingServer by viewModel.currentServer.collectAsState()

    // Convert data.mcp.MCPServer to model.MCPServer
    val dataServers by mcpRepository.mcpServers.collectAsState()

    // Convert data MCPServer to model MCPServer
    val mcpServers =
            remember(dataServers) {
                dataServers.map { dataServer ->
                    MCPServer(
                            id = dataServer.id,
                            name = dataServer.name,
                            description = dataServer.description,
                            logoUrl = dataServer.logoUrl,
                            stars = dataServer.stars,
                            category = dataServer.category,
                            requiresApiKey = dataServer.requiresApiKey,
                            author = dataServer.author,
                            isVerified = dataServer.isVerified,
                            isInstalled = dataServer.isInstalled,
                            version = dataServer.version,
                            updatedAt = dataServer.updatedAt,
                            longDescription = dataServer.longDescription,
                            repoUrl = dataServer.repoUrl
                    )
                }
            }

    val isLoading by mcpRepository.isLoading.collectAsState()
    val hasMore by mcpRepository.hasMore.collectAsState()
    val error by mcpRepository.errorMessage.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 搜索和筛选状态
    val searchText = remember { mutableStateOf("") }
    var showInstalledOnly by remember { mutableStateOf(false) }

    // Add debounced search text state
    var debouncedSearchText by remember { mutableStateOf("") }

    // Add search state
    var isSearchActive by remember { mutableStateOf(false) }

    // 排序状态
    var showSortMenu by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOptions.RECOMMENDED) }
    var sortDirection by remember { mutableStateOf(SortDirection.DESC) }

    // Selected server for details dialog
    var selectedServer by remember { mutableStateOf<MCPServer?>(null) }

    // 仅在本地过滤已安装的插件
    val displayedServers =
            remember(mcpServers, showInstalledOnly, debouncedSearchText) {
                val filtered =
                        if (showInstalledOnly) {
                            mcpServers.filter { it.isInstalled }
                        } else {
                            mcpServers
                        }

                // Apply local search if text is present
                if (debouncedSearchText.isNotBlank()) {
                    val searchTerms = debouncedSearchText.lowercase().split(" ")
                    filtered.filter { server ->
                        searchTerms.all { term ->
                            server.name.lowercase().contains(term) ||
                            server.description.lowercase().contains(term) ||
                            server.author.lowercase().contains(term) ||
                            server.category.lowercase().contains(term) ||
                            server.id.lowercase().contains(term)
                        }
                    }
                } else {
                    filtered
                }
            }

    // Debounce search text changes
    LaunchedEffect(searchText.value) {
        delay(300) // Wait for 300ms of inactivity before updating
        debouncedSearchText = searchText.value

        // Only trigger network search if actively searching and text is not empty
        if (isSearchActive && debouncedSearchText.isNotBlank()) {
            // Call the search function
            coroutineScope.launch {
                mcpRepository.fetchMCPServers(
                        forceRefresh = true,
                        query = debouncedSearchText,
                        sortBy = sortOption,
                        sortDirection = sortDirection
                )
            }
        }
    }

    // 执行搜索 - local filtering happens automatically
    fun performSearch() {
        isSearchActive = true
        // Perform network search
        coroutineScope.launch {
            mcpRepository.fetchMCPServers(
                    forceRefresh = true,
                    query = debouncedSearchText,
                    sortBy = sortOption,
                    sortDirection = sortDirection
            )
        }
    }

    // Initialize fetch on first load
    LaunchedEffect(Unit) {
        // 检查是否已有加载的服务器数据
        if (mcpServers.isEmpty()) {
            // 首先扫描已安装的插件状态
            mcpRepository.syncInstalledStatus()
            // 获取服务器列表（默认会先加载官方插件，再加载第三方插件）
            mcpRepository.fetchMCPServers(forceRefresh = true)
        } else {
            // 如果已有数据，只确保安装状态是最新的
            mcpRepository.syncInstalledStatus()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部搜索栏、排序和过滤选项
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 搜索栏和排序按钮
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 搜索输入框
                OutlinedTextField(
                        value = searchText.value,
                        onValueChange = {
                            searchText.value = it
                            // If text is cleared, reset search active state
                            if (it.isEmpty()) {
                                isSearchActive = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("搜索MCP插件...") },
                        trailingIcon = {
                            IconButton(onClick = { performSearch() }) {
                                Icon(
                                        Icons.Default.Search,
                                        contentDescription = "执行搜索",
                                        tint =
                                                if (isSearchActive)
                                                        MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                )

                // 排序按钮
                Box {
                    IconButton(
                            onClick = { showSortMenu = true },
                            modifier =
                                    Modifier.size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(
                                                            alpha = 0.7f
                                                    )
                                            )
                    ) {
                        Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "排序选项",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // 排序下拉菜单
                    DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                    ) {
                        Text(
                                text = "排序方式",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // 排序选项
                        SortMenuItem("推荐", SortOptions.RECOMMENDED, sortOption, sortDirection) {
                                newOption,
                                newDirection ->
                            sortOption = newOption
                            sortDirection = newDirection
                            showSortMenu = false
                            performSearch()
                        }

                        SortMenuItem("创建时间", SortOptions.CREATED, sortOption, sortDirection) {
                                newOption,
                                newDirection ->
                            sortOption = newOption
                            sortDirection = newDirection
                            showSortMenu = false
                            performSearch()
                        }

                        SortMenuItem("更新时间", SortOptions.UPDATED, sortOption, sortDirection) {
                                newOption,
                                newDirection ->
                            sortOption = newOption
                            sortDirection = newDirection
                            showSortMenu = false
                            performSearch()
                        }

                        SortMenuItem("评论数", SortOptions.COMMENTS, sortOption, sortDirection) {
                                newOption,
                                newDirection ->
                            sortOption = newOption
                            sortDirection = newDirection
                            showSortMenu = false
                            performSearch()
                        }

                        SortMenuItem("反应数", SortOptions.REACTIONS, sortOption, sortDirection) {
                                newOption,
                                newDirection ->
                            sortOption = newOption
                            sortDirection = newDirection
                            showSortMenu = false
                            performSearch()
                        }
                    }
                }

                // 刷新按钮
                IconButton(
                        onClick = {
                            coroutineScope.launch {
                                // 使用统一的刷新接口
                                mcpRepository.refresh(
                                        query = searchText.value,
                                        sortBy = sortOption,
                                        sortDirection = sortDirection
                                )
                            }
                        },
                        modifier =
                                Modifier.size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.7f
                                                )
                                        )
                ) {
                    Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 安装状态筛选器
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                            text = "查看:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                            modifier =
                                    Modifier.clip(RoundedCornerShape(24.dp))
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.3f
                                                    )
                                            )
                                            .padding(4.dp)
                    ) {
                        FilterChip(
                                selected = !showInstalledOnly,
                                onClick = { showInstalledOnly = false },
                                label = { Text("全部") },
                                colors =
                                        FilterChipDefaults.filterChipColors(
                                                selectedContainerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor =
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                modifier = Modifier.height(36.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        FilterChip(
                                selected = showInstalledOnly,
                                onClick = {
                                    showInstalledOnly = true
                                    // 已安装的筛选在本地完成，不需要调用API
                                },
                                label = { Text("已安装") },
                                leadingIcon =
                                        if (showInstalledOnly) {
                                            {
                                                Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                colors =
                                        FilterChipDefaults.filterChipColors(
                                                selectedContainerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor =
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                modifier = Modifier.height(36.dp)
                        )
                    }
                }

                // 显示排序和搜索信息
                if (sortOption != SortOptions.RECOMMENDED ||
                                sortDirection != SortDirection.DESC ||
                                debouncedSearchText.isNotBlank()
                ) {
                    val searchInfo =
                            if (debouncedSearchText.isNotBlank()) {
                                "搜索: \"$debouncedSearchText\" · "
                            } else {
                                ""
                            }

                    Text(
                            text =
                                    searchInfo +
                                            "排序: ${getSortDisplayName(sortOption)} ${if (sortDirection == SortDirection.DESC) "↓" else "↑"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // 显示插件数量
                    Text(
                            text = "${displayedServers.size} 个插件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 内容区域
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (isLoading && mcpServers.isEmpty()) {
                // 加载中状态
                Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) {
                            Text(
                                    text = "...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                            text = "加载中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (error != null && mcpServers.isEmpty()) {
                // 错误状态
                Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                            text = "加载MCP插件失败",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                    )
                    Text(
                            text = error ?: "未知错误",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                    )
                    Button(
                            onClick = {
                                coroutineScope.launch {
                                    mcpRepository.refresh(
                                            query = debouncedSearchText,
                                            sortBy = sortOption,
                                            sortDirection = sortDirection
                                    )
                                }
                            },
                            modifier = Modifier.padding(top = 16.dp)
                    ) { Text("重试") }
                }
            } else if (displayedServers.isEmpty()) {
                // 空结果状态
                Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                            text = "未找到符合条件的插件",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                    )

                    Text(
                            text = "试试不同的搜索条件或分类",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                    )

                    Button(
                            onClick = {
                                searchText.value = ""
                                debouncedSearchText = ""
                                isSearchActive = false
                                showInstalledOnly = false
                                sortOption = SortOptions.RECOMMENDED
                                sortDirection = SortDirection.DESC
                                // Perform search with reset values
                                coroutineScope.launch {
                                    mcpRepository.fetchMCPServers(
                                        forceRefresh = true,
                                        query = "",
                                        sortBy = SortOptions.RECOMMENDED,
                                        sortDirection = SortDirection.DESC
                                    )
                                }
                            },
                            modifier = Modifier.padding(top = 16.dp)
                    ) { Text("清除筛选") }
                }
            } else {
                // 显示列表
                val listState = rememberLazyListState()

                // 下拉刷新状态
                var refreshing by remember { mutableStateOf(false) }
                var pullOffset by remember { mutableStateOf(0f) }
                val pullThreshold = 100f // 下拉触发刷新的阈值

                // 监听列表滑动，加载更多数据
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val layoutInfo = listState.layoutInfo
                        val totalItemsCount = layoutInfo.totalItemsCount
                        val visibleItemsInfo = layoutInfo.visibleItemsInfo

                        if (visibleItemsInfo.isNotEmpty()) {
                            val lastVisibleItem = visibleItemsInfo.last()
                            lastVisibleItem.index >= totalItemsCount - 2 && hasMore && !isLoading
                        } else false
                    }
                            .distinctUntilChanged()
                            .collect { shouldLoadMore ->
                                if (shouldLoadMore) {
                                    // 使用统一的 API 接口加载更多，不强制刷新
                                    mcpRepository.fetchMCPServers(
                                            forceRefresh = false,
                                            query = debouncedSearchText,
                                            sortBy = sortOption,
                                            sortDirection = sortDirection
                                    )
                                }
                            }
                }

                // 处理刷新完成
                LaunchedEffect(isLoading) {
                    if (!isLoading && refreshing) {
                        refreshing = false
                        pullOffset = 0f
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 下拉刷新提示
                    if (pullOffset > 0) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height((pullOffset / 2).roundToInt().dp)
                                                .align(Alignment.TopCenter),
                                contentAlignment = Alignment.Center
                        ) {
                            val progress = (pullOffset / pullThreshold).coerceIn(0f, 1f)

                            if (refreshing) {
                                Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RefreshingIndicator()
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                            "刷新中...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Text(
                                        text = if (progress >= 1f) "释放刷新" else "下拉刷新",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    LazyColumn(
                            state = listState,
                            modifier =
                                    Modifier.fillMaxSize()
                                            .offset(y = pullOffset.roundToInt().dp)
                                            .pointerInput(Unit) {
                                                detectVerticalDragGestures(
                                                        onVerticalDrag = { change, dragAmount ->
                                                            change.consumePositionChange()
                                                            if (listState.firstVisibleItemIndex ==
                                                                            0 &&
                                                                            (listState
                                                                                    .firstVisibleItemScrollOffset ==
                                                                                    0 ||
                                                                                    pullOffset > 0)
                                                            ) {
                                                                if (!refreshing) {
                                                                    pullOffset =
                                                                            (pullOffset +
                                                                                            dragAmount)
                                                                                    .coerceAtLeast(
                                                                                            0f
                                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            if (pullOffset > pullThreshold &&
                                                                            !refreshing
                                                            ) {
                                                                refreshing = true
                                                                coroutineScope.launch {
                                                                    mcpRepository.refresh(
                                                                            query =
                                                                                    searchText
                                                                                            .value,
                                                                            sortBy = sortOption,
                                                                            sortDirection =
                                                                                    sortDirection
                                                                    )
                                                                }
                                                            } else if (!refreshing) {
                                                                pullOffset = 0f
                                                            }
                                                        }
                                                )
                                            },
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = displayedServers, key = { server -> server.id }) { server ->
                            MCPServerItem(server = server, onClick = { selectedServer = server })
                        }

                        // 列表底部加载中提示
                        if (hasMore) {
                            item { LoadingItem(isLoading = isLoading) }
                        }
                    }
                }
            }
        }
    }

    // 显示安装进度对话框
    if (installProgress != null || installResult != null) {
        MCPInstallProgressDialog(
                installProgress = installProgress,
                result = installResult,
                serverName = currentInstallingServer?.name ?: "MCP 服务器",
                onDismissRequest = { viewModel.resetInstallState() }
        )
    }

    // 显示详情对话框
    selectedServer?.let { server ->
        val installedPath = remember(server.id) { viewModel.getInstalledPath(server.id) }

        MCPServerDetailsDialog(
                server = server,
                onDismiss = { selectedServer = null },
                onInstall = {
                    viewModel.installServer(it)
                    selectedServer = null // 关闭详情对话框
                },
                onUninstall = {
                    viewModel.uninstallServer(it)
                    selectedServer = null // 关闭详情对话框
                },
                installedPath = installedPath
        )
    }
}

/** 排序选项菜单项 */
@Composable
private fun SortMenuItem(
        label: String,
        option: SortOptions,
        currentOption: SortOptions,
        currentDirection: SortDirection,
        onClick: (SortOptions, SortDirection) -> Unit
) {
    val isSelected = currentOption == option
    var direction = currentDirection

    // 如果点击当前选中的选项，则切换方向
    if (isSelected) {
        direction =
                if (currentDirection == SortDirection.DESC) SortDirection.ASC
                else SortDirection.DESC
    } else {
        // 新选项默认为降序
        direction = SortDirection.DESC
    }

    DropdownMenuItem(
            text = {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color =
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                    )

                    if (isSelected) {
                        Text(
                                text = if (currentDirection == SortDirection.DESC) "↓" else "↑",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            onClick = { onClick(option, direction) }
    )
}

/** 获取排序选项的显示名称 */
@Composable
fun getSortDisplayName(sortOption: SortOptions): String {
    return when (sortOption) {
        SortOptions.RECOMMENDED -> "推荐"
        SortOptions.CREATED -> "创建时间"
        SortOptions.UPDATED -> "更新时间"
        SortOptions.COMMENTS -> "评论数"
        SortOptions.REACTIONS -> "反应数"
    }
}

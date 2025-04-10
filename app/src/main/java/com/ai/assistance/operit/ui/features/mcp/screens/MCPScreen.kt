package com.ai.assistance.operit.ui.features.mcp.screens

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.MCPServer
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// Define category strings for filtering
object MCPCategories {
    const val ALL = "All"
    const val FILE = "File"
    const val CODE = "Code"
    const val SEARCH = "Search"
    const val DATABASE = "Database"
    const val WEB = "Web"
    const val API = "API"
    const val GENERATION = "Generation"
    const val DOCUMENT = "Document"
    const val OTHER = "Other"
    
    // List of all categories for the tab row
    val values = listOf(ALL, FILE, CODE, SEARCH, DATABASE, WEB, API, GENERATION, DOCUMENT, OTHER)
}

// Extend the MCPServer class with the necessary properties if not already defined
data class MCPServer(
    val id: String,
    val name: String,
    val description: String,
    val logoUrl: String?,
    val stars: Int,
    val category: String,
    val requiresApiKey: Boolean = false,
    val author: String = "Unknown",
    val isVerified: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPScreen(mcpRepository: MCPRepository) {
    val mcpServers by mcpRepository.mcpServers.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val categories = remember { MCPCategories.values }
    val isLoading by mcpRepository.isLoading.collectAsState()
    val hasMore by mcpRepository.hasMore.collectAsState()
    val error by mcpRepository.errorMessage.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val searchText = remember { mutableStateOf("") }
    val filteredServers =
            remember(mcpServers, searchText.value, selectedTabIndex) {
                mcpServers.filter { server: MCPServer ->
                    val matchesCategory =
                            selectedTabIndex == 0 ||
                                    server.category.equals(categories[selectedTabIndex], ignoreCase = true)
                    val matchesSearch =
                            searchText.value.isEmpty() ||
                                    server.name.contains(
                                            searchText.value,
                                            ignoreCase = true
                                    ) ||
                                    server.description.contains(
                                            searchText.value,
                                            ignoreCase = true
                                    )
                    matchesCategory && matchesSearch
                }
            }

    // Initialize fetch on first load
    LaunchedEffect(Unit) { mcpRepository.fetchMCPServers() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
                value = searchText.value,
                onValueChange = { searchText.value = it },
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search MCP servers...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
        )

        // Enhanced TabBar with Refresh Button
        Box(modifier = Modifier.fillMaxWidth()) {
                // Modern tab design with better visual styling
                Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                ScrollableTabRow(
                                        selectedTabIndex = selectedTabIndex,
                                        modifier =
                                                Modifier.weight(1f)
                                                        .padding(vertical = 4.dp),
                                        edgePadding = 16.dp,
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        indicator = { tabPositions ->
                                                Box(
                                                        Modifier.tabIndicatorOffset(
                                                                        tabPositions[
                                                                                selectedTabIndex]
                                                                )
                                                                .height(3.dp)
                                                                .padding(horizontal = 16.dp)
                                                                .clip(
                                                                        RoundedCornerShape(
                                                                                topStart =
                                                                                        3.dp,
                                                                                topEnd =
                                                                                        3.dp
                                                                        )
                                                                )
                                                                .background(
                                                                        MaterialTheme
                                                                                .colorScheme
                                                                                .primary
                                                                )
                                                )
                                        },
                                        divider = {} // Remove divider for cleaner look
                                ) {
                                        categories.forEachIndexed { index: Int, category: String ->
                                                Tab(
                                                        selected =
                                                                selectedTabIndex == index,
                                                        onClick = {
                                                                selectedTabIndex = index
                                                        },
                                                        modifier =
                                                                Modifier.height(44.dp)
                                                                                .padding(
                                                                                        horizontal =
                                                                                                4.dp
                                                                                )
                                                ) {
                                                        Text(
                                                                text = category,
                                                                fontWeight =
                                                                        if (selectedTabIndex ==
                                                                                        index
                                                                        )
                                                                                FontWeight
                                                                                        .Bold
                                                                        else
                                                                                FontWeight
                                                                                        .Medium,
                                                                maxLines = 1,
                                                                overflow =
                                                                        TextOverflow
                                                                                .Ellipsis
                                                        )
                                                }
                                        }
                                }

                                // Refresh Button - integrated with tabs
                                IconButton(
                                        onClick = {
                                                coroutineScope.launch {
                                                        mcpRepository.refresh()
                                                }
                                        },
                                        modifier =
                                                Modifier.padding(end = 8.dp)
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer
                                                                        .copy(alpha = 0.7f)
                                                        )
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Refresh",
                                                tint =
                                                        MaterialTheme.colorScheme
                                                                .onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                        )
                                }
                        }
                }
        }

        // Content Area
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (isLoading && mcpServers.isEmpty()) {
                        // Replace CircularProgressIndicator with a simpler custom loading indicator
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
                        Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Text(
                                        text = "Error loading MCP servers",
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                )
                                Text(
                                        text = error ?: "Unknown error",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 8.dp)
                                )
                                Button(
                                        onClick = {
                                                coroutineScope.launch {
                                                        mcpRepository.refresh()
                                                }
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                ) { Text("Retry") }
                        }
                } else if (filteredServers.isEmpty()) {
                        Text(
                                text = "No MCP servers found for this category or search",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                        )
                } else {
                        val listState = rememberLazyListState()
                        
                        // Variable to track pull-to-refresh state
                        var refreshing by remember { mutableStateOf(false) }
                        var pullOffset by remember { mutableStateOf(0f) }
                        val pullThreshold = 100f // Pull distance needed to trigger refresh
                        
                        // Check if we're at the end of the list to trigger loading more data
                        LaunchedEffect(listState) {
                            // Monitor list scroll position
                            snapshotFlow {
                                val layoutInfo = listState.layoutInfo
                                val totalItemsCount = layoutInfo.totalItemsCount
                                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                                
                                if (visibleItemsInfo.isNotEmpty()) {
                                    val lastVisibleItem = visibleItemsInfo.last()
                                    lastVisibleItem.index >= totalItemsCount - 2 && // Load more when user is within 2 items of the end
                                            hasMore && !isLoading
                                } else false
                            }.distinctUntilChanged().collect { shouldLoadMore ->
                                if (shouldLoadMore) {
                                    mcpRepository.fetchMCPServers()
                                }
                            }
                        }
                        
                        // Handle refresh completion
                        LaunchedEffect(isLoading) {
                            if (!isLoading && refreshing) {
                                refreshing = false
                                pullOffset = 0f
                            }
                        }
                        
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Custom pull-to-refresh header
                            if (pullOffset > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((pullOffset / 2).roundToInt().dp)
                                        .align(Alignment.TopCenter),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val progress = (pullOffset / pullThreshold).coerceIn(0f, 1f)
                                    
                                    // Show different UI based on pull progress
                                    if (refreshing) {
                                        // Show loading indicator when refreshing
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
                                        // Show pull progress
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
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(y = pullOffset.roundToInt().dp)
                                        .pointerInput(Unit) {
                                            // Only allow pull gesture when at the top of the list
                                            detectVerticalDragGestures(
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consumePositionChange()
                                                    if (listState.firstVisibleItemIndex == 0 && 
                                                        (listState.firstVisibleItemScrollOffset == 0 || pullOffset > 0)) {
                                                        if (!refreshing) {
                                                            // Update pull offset, ensuring it doesn't go negative
                                                            pullOffset = (pullOffset + dragAmount).coerceAtLeast(0f)
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    // Check if pulled enough to trigger refresh
                                                    if (pullOffset > pullThreshold && !refreshing) {
                                                        refreshing = true
                                                        coroutineScope.launch {
                                                            mcpRepository.refresh()
                                                        }
                                                    } else if (!refreshing) {
                                                        // Reset pull offset if not refreshing
                                                        pullOffset = 0f
                                                    }
                                                }
                                            )
                                        },
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                    items(filteredServers) { server: MCPServer ->
                                            MCPServerItem(server = server)
                                    }
                                    
                                    // Add a loading item at the bottom if more items are available
                                    if (hasMore) {
                                        item {
                                            LoadingItem(isLoading = isLoading)
                                        }
                                    }
                            }
                        }
                }
        }
    }
}

@Composable
private fun RefreshingIndicator() {
    val rotation by rememberInfiniteTransition(label = "refreshRotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshRotation"
    )
    
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refreshing",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { 
                    rotationZ = rotation
                }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPServerItem(server: MCPServer) {
    // 使用记忆化存储加载的图像位图
    var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // 尝试加载图像
    LaunchedEffect(server.logoUrl) {
        if (!server.logoUrl.isNullOrBlank()) {
                val executor = Executors.newSingleThreadExecutor()
                val handler = Handler(Looper.getMainLooper())

                executor.execute {
                        try {
                                val url = URL(server.logoUrl)
                                val connection = url.openConnection()
                                connection.connectTimeout = 5000
                                connection.readTimeout = 5000
                                val input: InputStream = connection.getInputStream()
                                val bitmap = BitmapFactory.decodeStream(input)
                                handler.post { imageBitmap = bitmap }
                        } catch (e: Exception) {
                                e.printStackTrace()
                        }
                }
        }
    }

    Card(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
            Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.Top
            ) {
                    // 服务器Logo
                    Box(
                            modifier =
                                    Modifier.size(48.dp)
                                            .clip(CircleShape)
                                            .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                    CircleShape
                                            )
                                            .background(
                                                    MaterialTheme.colorScheme.primaryContainer
                                            ),
                            contentAlignment = Alignment.Center
                    ) {
                            if (imageBitmap != null) {
                                    // 如果图像加载成功，显示图像
                                    Image(
                                            bitmap = imageBitmap!!.asImageBitmap(),
                                            contentDescription = "${server.name} logo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                    )
                            } else {
                                    // 如果没有图像或加载失败，显示默认图标
                                    val icon =
                                            when {
                                                    server.category.contains(
                                                            "Search",
                                                            ignoreCase = true
                                                    ) -> Icons.Default.Search
                                                    server.category.contains(
                                                            "File",
                                                            ignoreCase = true
                                                    ) -> Icons.Default.Folder
                                                    server.category.contains(
                                                            "API",
                                                            ignoreCase = true
                                                    ) -> Icons.Default.Code
                                                    server.category.contains(
                                                            "Database",
                                                            ignoreCase = true
                                                    ) -> Icons.Default.Storage
                                                    server.category.contains(
                                                            "Web",
                                                            ignoreCase = true
                                                    ) -> Icons.Default.Language
                                                    server.category.contains(
                                                            "Generation",
                                                            ignoreCase = true
                                                    ) -> Icons.Default.Create
                                                    server.category.contains(
                                                            "Document",
                                                            ignoreCase = true
                                                    ) -> Icons.Default.Description
                                                    server.requiresApiKey -> Icons.Default.Lock
                                                    else -> Icons.Default.Extension
                                            }

                                    Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp)
                                    )
                            }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 右侧内容区
                    Column(modifier = Modifier.weight(1f)) {
                            // 标题行
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                    Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                    )

                                    if (server.isVerified) {
                                            Surface(
                                                    modifier =
                                                            Modifier.size(20.dp)
                                                                    .clip(
                                                                            RoundedCornerShape(
                                                                                    4.dp
                                                                            )
                                                                    )
                                                                    .padding(start = 4.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = MaterialTheme.shapes.small
                                            ) {
                                                    Text(
                                                            text = "✓",
                                                            style =
                                                                    MaterialTheme.typography
                                                                            .labelSmall,
                                                            color =
                                                                    MaterialTheme.colorScheme
                                                                            .onPrimary,
                                                            modifier = Modifier.padding(2.dp),
                                                            textAlign = TextAlign.Center
                                                    )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                    }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 描述
                            Text(
                                    text = server.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 底部标签和星星
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                    // 分类标签
                                    Surface(
                                            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.small,
                                            tonalElevation = 1.dp
                                    ) {
                                            Text(
                                                    text = server.category,
                                                    style =
                                                            MaterialTheme.typography
                                                                    .labelMedium,
                                                    modifier =
                                                            Modifier.padding(
                                                                    horizontal = 12.dp,
                                                                    vertical = 6.dp
                                                            ),
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .onSurfaceVariant
                                            )
                                    }

                                    Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                    ) {
                                            // 星星数量
                                            Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                    text = "${server.stars}",
                                                    style =
                                                            MaterialTheme.typography
                                                                    .labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                            )
                                    }
                            }

                            // 作者信息
                            if (server.author.isNotBlank() && server.author != "Unknown") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                            text = "by ${server.author}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            }
                    }
            }
    }
}

@Composable
private fun LoadingItem(isLoading: Boolean) {
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val animatedAlpha by rememberInfiniteTransition(label = "loadingAlpha").animateFloat(
                initialValue = 0.4f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "loadingAlpha"
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Custom loading animation with three dots
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Three animated dots
                    for (i in 0..2) {
                        val dotDelay = i * 200
                        val dotAlpha by rememberInfiniteTransition(label = "dot$i").animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing, delayMillis = dotDelay),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot$i"
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha))
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "加载更多内容中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = animatedAlpha)
                    )
                }
            }
        }
    } else {
        Spacer(modifier = Modifier.height(8.dp))
    }
}

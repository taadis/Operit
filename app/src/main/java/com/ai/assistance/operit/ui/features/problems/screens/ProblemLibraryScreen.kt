package com.ai.assistance.operit.ui.features.problems.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.library.ProblemLibraryTool
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.ui.features.problems.viewmodel.ProblemLibraryViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemLibraryScreen() {
    val context = LocalContext.current

    // 传统方式创建ViewModel
    val viewModel = remember {
        val factory = ProblemLibraryViewModelFactory(context)
        val activity = context as ComponentActivity
        ViewModelProvider(owner = activity, factory = factory)
                .get(ProblemLibraryViewModel::class.java)
    }

    val problems by viewModel.problems.collectAsState()
    val selectedProblem by viewModel.selectedProblem.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchInfo by viewModel.searchInfo.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val editedSummary by viewModel.editedSummary.collectAsState()
    val editedSolution by viewModel.editedSolution.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedProblem == null) {
                // 问题列表界面
                Column(modifier = Modifier.fillMaxSize()) {
                    // 搜索栏
                    SearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            onSearch = { viewModel.searchProblems() },
                            searchInfo = searchInfo
                    )

                    // 问题列表
                    if (problems.isEmpty() && !isLoading) {
                        EmptyLibraryView()
                    } else {
                        ProblemList(
                                problems = problems,
                                onProblemClick = { viewModel.selectProblem(it) }
                        )
                    }
                }
            } else {
                // 问题详情界面
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部导航栏
                    TopAppBar(
                            title = {
                                Text(
                                        if (isEditMode) stringResource(id = R.string.edit_problem)
                                        else stringResource(id = R.string.problem_details)
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                        onClick = {
                                            if (isEditMode) {
                                                // 取消编辑模式
                                                viewModel.toggleEditMode(false)
                                            } else {
                                                // 返回列表
                                                viewModel.clearSelectedProblem()
                                            }
                                        }
                                ) {
                                    Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription =
                                                    stringResource(id = R.string.back_to_list)
                                    )
                                }
                            },
                            actions = {
                                if (!isEditMode) {
                                    // 编辑按钮
                                    IconButton(onClick = { viewModel.toggleEditMode(true) }) {
                                        Icon(
                                                Icons.Default.Edit,
                                                contentDescription =
                                                        stringResource(id = R.string.edit_problem)
                                        )
                                    }

                                    // 删除按钮
                                    IconButton(
                                            onClick = {
                                                selectedProblem?.let { problem ->
                                                    viewModel.deleteProblem(problem)
                                                    scope.launch {
                                                        // 使用默认参数，无需指定duration
                                                        snackbarHostState.showSnackbar("问题已删除")
                                                    }
                                                }
                                            }
                                    ) {
                                        Icon(
                                                Icons.Default.Delete,
                                                contentDescription =
                                                        stringResource(
                                                                id = R.string.delete_problem
                                                        ),
                                                tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    // 保存按钮
                                    Button(
                                            onClick = {
                                                viewModel.saveEditedProblem()
                                                scope.launch {
                                                    // 使用默认参数，无需指定duration
                                                    snackbarHostState.showSnackbar("更改已保存")
                                                }
                                            },
                                            modifier = Modifier.padding(end = 8.dp)
                                    ) { Text(stringResource(id = R.string.save_changes)) }
                                }
                            },
                            colors =
                                    TopAppBarDefaults.topAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                            navigationIconContentColor =
                                                    MaterialTheme.colorScheme.onPrimary,
                                            actionIconContentColor =
                                                    MaterialTheme.colorScheme.onPrimary
                                    )
                    )

                    // 问题详情内容
                    if (isEditMode) {
                        ProblemEditView(
                                summary = editedSummary,
                                solution = editedSolution,
                                onSummaryChange = { viewModel.updateEditedSummary(it) },
                                onSolutionChange = { viewModel.updateEditedSolution(it) }
                        )
                    } else {
                        ProblemDetailView(problem = selectedProblem!!)
                    }
                }
            }

            // 加载指示器
            if (isLoading) {
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                    Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                    ) {
                        // 使用简单的Text替代可能不兼容的CircularProgressIndicator
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
                                text = "正在加载...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Snackbar主机
            SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

// ViewModel Factory
class ProblemLibraryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProblemLibraryViewModel::class.java)) {
            // 获取 AIToolHandler 实例
            val toolHandler = getToolHandler(context)

            // 创建 ViewModel
            return ProblemLibraryViewModel(toolHandler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    // 获取 AIToolHandler 实例
    private fun getToolHandler(context: Context): AIToolHandler {
        // 从应用级别获取 AIToolHandler 实例
        // 这里假设 AIToolHandler 是一个应用级单例
        return (context.applicationContext as?
                        com.ai.assistance.operit.core.application.OperitApplication)
                ?.let {
                    // 如果应用实例是 OperitApplication，这里应该提供一个适当的 getter 方法
                    // 如果 OperitApplication 没有 getToolHandler 方法，直接使用单例方法
                    AIToolHandler.getInstance(context.applicationContext)
                }
                ?: AIToolHandler.getInstance(context.applicationContext)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
        query: String,
        onQueryChange: (String) -> Unit,
        onSearch: () -> Unit,
        searchInfo: String? = null // 添加搜索信息参数
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(id = R.string.search_problems),
                        modifier = Modifier.padding(start = 8.dp)
                )
                TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        placeholder = { Text(stringResource(id = R.string.search_problems)) },
                        singleLine = true,
                        colors =
                                TextFieldDefaults.textFieldColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                        unfocusedIndicatorColor =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.12f
                                                )
                                ),
                        keyboardOptions =
                                KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                                }
                            }
                        }
                )
            }
        }

        // 显示搜索信息（如分词结果）
        searchInfo?.let {
            Card(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
            ) {
                Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyLibraryView() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                    text = stringResource(id = R.string.empty_problem_library),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text = "当您通过AI对话解决问题时，系统会自动记录并添加到此问题库中",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProblemList(
    problems: List<ProblemLibraryTool.ProblemRecord>,
    onProblemClick: (ProblemLibraryTool.ProblemRecord) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(problems) { problem ->
            ProblemItem(problem = problem, onClick = { onProblemClick(problem) })
        }
    }
}

@Composable
fun ProblemItem(problem: ProblemLibraryTool.ProblemRecord, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(problem.timestamp))

    Card(
            modifier =
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // 摘要
            Text(
                    text = problem.summary.ifEmpty { problem.query.take(100) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
            )

            // 时间信息
            Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 工具标签 - 使用Row代替FlowRow
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(4.dp)) {
                // 最多显示3个标签
                problem.tools.take(3).forEach { tool -> Chip(label = tool, modifier = Modifier) }

                if (problem.tools.size > 3) {
                    Text(
                            text = "+${problem.tools.size - 3}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                    Modifier.align(Alignment.CenterVertically).padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, modifier: Modifier = Modifier) {
    Surface(
            modifier = modifier.height(24.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun ProblemDetailView(problem: ProblemLibraryTool.ProblemRecord) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(problem.timestamp))

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    // 问题标题
                    Text(
                            text = "问题摘要",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 问题摘要
                    Text(
                            text = problem.summary.ifEmpty { "无摘要信息" },
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 原始查询信息
            Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    // 详情标题
                    Text(
                            text = "原始查询",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 查询内容
                    Text(text = problem.query, style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 记录时间信息
                    Text(
                            text = "记录时间: $formattedDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 解决方案信息
            Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    // 解决方案标题
                    Text(
                            text = "解决方案",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 解决方案内容 - 可滚动区域
                    Text(text = problem.solution, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 使用工具信息
            Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    // 工具标题
                    Text(
                            text = "使用的工具",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 工具列表
                    if (problem.tools.isEmpty()) {
                        Text(
                                text = "未使用任何工具",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = spacedBy(4.dp)) {
                            problem.tools.forEach { tool ->
                                Chip(label = tool, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProblemEditView(
        summary: String,
        solution: String,
        onSummaryChange: (String) -> Unit,
        onSolutionChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 摘要编辑
        Text(
                text = "问题摘要",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
                value = summary,
                onValueChange = onSummaryChange,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 解决方案编辑
        Text(
                text = "解决方案",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
                value = solution,
                onValueChange = onSolutionChange,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                maxLines = 20
        )
    }
}

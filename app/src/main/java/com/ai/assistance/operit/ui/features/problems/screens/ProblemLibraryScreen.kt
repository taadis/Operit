package com.ai.assistance.operit.ui.features.problems.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.activity.ComponentActivity
import com.ai.assistance.operit.R
import com.ai.assistance.operit.tools.AIToolHandler
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
    val isEditMode by viewModel.isEditMode.collectAsState()
    val editedSummary by viewModel.editedSummary.collectAsState()
    val editedSolution by viewModel.editedSolution.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedProblem == null) {
                // 问题列表界面
                Column(modifier = Modifier.fillMaxSize()) {
                    // 搜索栏
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearch = { viewModel.searchProblems() }
                    )
                    
                    // 问题列表
                    if (problems.isEmpty()) {
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
                                if (isEditMode) 
                                    stringResource(id = R.string.edit_problem) 
                                else 
                                    stringResource(id = R.string.problem_details)
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                if (isEditMode) {
                                    // 取消编辑模式
                                    viewModel.toggleEditMode(false)
                                } else {
                                    // 返回列表
                                    viewModel.clearSelectedProblem()
                                }
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(id = R.string.back_to_list))
                            }
                        },
                        actions = {
                            if (!isEditMode) {
                                // 编辑按钮
                                IconButton(onClick = { viewModel.toggleEditMode(true) }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(id = R.string.edit_problem))
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
                                        contentDescription = stringResource(id = R.string.delete_problem),
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
                                ) {
                                    Text(stringResource(id = R.string.save_changes))
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            
            // Snackbar主机
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

// ViewModel Factory
class ProblemLibraryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProblemLibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProblemLibraryViewModel(context.applicationContext as android.app.Application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(id = R.string.search_problems),
                modifier = Modifier.padding(start = 8.dp)
            )
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = { Text(stringResource(id = R.string.search_problems)) },
                singleLine = true,
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch() }
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清除搜索"
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun EmptyLibraryView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp),
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
    problems: List<AIToolHandler.ProblemRecord>,
    onProblemClick: (AIToolHandler.ProblemRecord) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        items(problems) { problem ->
            ProblemItem(problem = problem, onClick = { onProblemClick(problem) })
        }
    }
}

@Composable
fun ProblemItem(
    problem: AIToolHandler.ProblemRecord,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(problem.timestamp))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 摘要
            Text(
                text = problem.summary.ifEmpty { problem.query.take(100) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 工具和时间信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 工具标签
                Row {
                    problem.tools.take(2).forEachIndexed { index, tool ->
                        if (index > 0) Spacer(modifier = Modifier.width(4.dp))
                        Chip(
                            label = tool,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    
                    if (problem.tools.size > 2) {
                        Text(
                            text = "+${problem.tools.size - 2}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
                
                // 时间信息
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Chip(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(24.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun ProblemDetailView(problem: AIToolHandler.ProblemRecord) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(problem.timestamp))
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            // 问题UUID
            Text(
                text = "UUID: ${problem.uuid}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 问题摘要
            Section(title = stringResource(id = R.string.problem_summary)) {
                Text(
                    text = problem.summary.ifEmpty { problem.query },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            // 原始问题
            if (problem.summary.isNotEmpty()) {
                Section(title = "原始问题") {
                    Text(
                        text = problem.query,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // 使用工具
            Section(title = stringResource(id = R.string.problem_tools)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    problem.tools.forEach { tool ->
                        Chip(label = tool)
                    }
                }
            }
            
            // 创建时间
            Section(title = stringResource(id = R.string.problem_time)) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 解决方案
            Section(title = stringResource(id = R.string.problem_solution)) {
                Text(
                    text = problem.solution,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun Section(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                content()
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            // 编辑摘要
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.problem_summary),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = summary,
                    onValueChange = onSummaryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = { Text("输入问题摘要...") }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 编辑解决方案
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.problem_solution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = solution,
                    onValueChange = onSolutionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp),
                    placeholder = { Text("输入解决方案...") }
                )
            }
        }
    }
} 
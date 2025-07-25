package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModelFactory
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val profileList by preferencesManager.profileListFlow.collectAsState(initial = emptyList())
    val activeProfileId by
            preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    
    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = preferencesManager.getUserPreferencesFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    
    LaunchedEffect(activeProfileId) { selectedProfileId = activeProfileId }

    val viewModel: MemoryViewModel =
            viewModel(
        key = selectedProfileId, // Recreate ViewModel when profile changes
        factory = MemoryViewModelFactory(context, selectedProfileId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MemoryAppBar(
                    profileList = profileList,
                    profileNameMap = profileNameMap,
                    selectedProfileId = selectedProfileId,
                            onProfileSelected = { profileId -> selectedProfileId = profileId },
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.searchMemories()
                    },
                    onClear = {
                        viewModel.onSearchQueryChange("")
                        viewModel.loadMemoryGraph()
                    }
                )

                    // 紧凑的说明卡片
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "信息",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "记忆库与用户偏好配置绑定，新建请到\"设置→用户偏好\"，激活可在聊天菜单设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
            }
        },
        floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.toggleLinkingMode(!uiState.isLinkingMode) },
                        containerColor = if (uiState.isLinkingMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Toggle Linking Mode")
                    }
                    FloatingActionButton(
                        onClick = { viewModel.startEditing(null) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Memory")
                    }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                // 直接使用GraphVisualizer占满整个空间
                GraphVisualizer(
                    graph = uiState.graph,
                    modifier = Modifier.fillMaxSize(),
                    selectedNodeId = uiState.selectedNodeId,
                    linkingNodeIds = uiState.linkingNodeIds,
                    selectedEdgeId = uiState.selectedEdge?.id,
                    onNodeClick = { node -> viewModel.selectNode(node) },
                    onEdgeClick = { edge -> viewModel.selectEdge(edge) }
                )
            }

            if (uiState.selectedMemory != null) {
                MemoryInfoDialog(
                    memory = uiState.selectedMemory!!,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = { viewModel.startEditing(uiState.selectedMemory) },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) }
                )
            }

            val selectedEdge = uiState.selectedEdge
            if (selectedEdge != null) {
                EdgeInfoDialog(
                    edge = selectedEdge,
                    graph = uiState.graph,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = { viewModel.startEditingEdge(selectedEdge) },
                    onDelete = { viewModel.deleteEdge(selectedEdge.id) }
                )
            }

            if (uiState.linkingNodeIds.size == 2) {
                val sourceNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[0] }
                val targetNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[1] }
                if (sourceNode != null && targetNode != null) {
                    LinkMemoryDialog(
                        sourceNodeLabel = sourceNode.label,
                        targetNodeLabel = targetNode.label,
                        onDismiss = { viewModel.toggleLinkingMode(false) },
                        onLink = { type, weight, description ->
                            viewModel.linkMemories(sourceNode.id, targetNode.id, type, weight, description)
                        }
                    )
                }
            }

            if (uiState.isEditing) {
                EditMemoryDialog(
                    memory = uiState.editingMemory,
                    onDismiss = { viewModel.cancelEditing() },
                    onSave = { title, content, contentType ->
                        if (uiState.editingMemory == null) {
                            viewModel.createMemory(title, content, contentType)
                        } else {
                            viewModel.updateMemory(
                                uiState.editingMemory!!,
                                title,
                                content,
                                contentType
                            )
                        }
                    }
                )
            }

            val editingEdge = uiState.editingEdge
            if (uiState.isEditingEdge && editingEdge != null) {
                EditEdgeDialog(
                    edge = editingEdge,
                    onDismiss = { viewModel.cancelEditingEdge() },
                    onSave = { type, weight, description ->
                        viewModel.updateEdge(editingEdge, type, weight, description)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryAppBar(
    profileList: List<String>,
    profileNameMap: Map<String, String>,
    selectedProfileId: String,
        onProfileSelected: (String) -> Unit,
        query: String,
        onQueryChange: (String) -> Unit,
        onSearch: () -> Unit,
        onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProfileName = profileNameMap[selectedProfileId] ?: selectedProfileId

    TopAppBar(
        title = {
                Text("记忆库", style = MaterialTheme.typography.titleMedium)
            },
            actions = {
                // 集成搜索栏
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .width(180.dp)
                        .height(46.dp),
                    placeholder = { Text("搜索记忆", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search Icon",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = onClear,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear Search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 用户偏好选择器
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { expanded = true }
                    ) {
                        Text(
                            text = selectedProfileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 100.dp)
                        )
                         Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Profile")
                     }

                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                         profileList.forEach { profileId ->
                             val profileName = profileNameMap[profileId] ?: profileId
                             DropdownMenuItem(
                                 text = { Text(profileName) },
                                 onClick = {
                                     onProfileSelected(profileId)
                                     expanded = false
                                 }
                             )
                         }
                     }
                 }
             }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryInfoDialog(
        memory: Memory,
        onDismiss: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "记忆详情") },
            text = {
                Column(
                        modifier = Modifier.verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("标题: ${memory.title}", style = MaterialTheme.typography.titleMedium)
                    Divider()
                    Text("内容:", style = MaterialTheme.typography.titleSmall)
                    Text(memory.content)
                    Divider()
                    Text("UUID: ${memory.uuid}", style = MaterialTheme.typography.bodySmall)
                    Text("来源: ${memory.source}", style = MaterialTheme.typography.bodySmall)
                    Text(
                            "重要性: ${String.format("%.2f", memory.importance)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "可信度: ${String.format("%.2f", memory.credibility)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "创建时间: ${dateFormat.format(memory.createdAt)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "更新时间: ${dateFormat.format(memory.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.Center
                ) {
                    Button(onClick = onEdit) { Text("编辑") }
                    Button(
                            onClick = onDelete,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text("删除") }
                    OutlinedButton(onClick = onDismiss) { Text("关闭") }
                }
            }
    )
}

@Composable
fun EditMemoryDialog(
        memory: Memory?,
        onDismiss: () -> Unit,
        onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(memory?.title ?: "") }
    var content by remember { mutableStateOf(memory?.content ?: "") }
    var contentType by remember { mutableStateOf(memory?.contentType ?: "text/plain") }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (memory == null) "创建记忆" else "编辑记忆") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("标题") }
                    )
                    OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("内容") },
                            minLines = 3
                    )
                    OutlinedTextField(
                            value = contentType,
                            onValueChange = { contentType = it },
                            label = { Text("内容类型") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onSave(title, content, contentType) }) { Text("保存") }
            },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EdgeInfoDialog(
    edge: Edge,
    graph: com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceNode = graph.nodes.find { it.id == edge.sourceId }
    val targetNode = graph.nodes.find { it.id == edge.targetId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("从: ${sourceNode?.label ?: "未知"}")
                Text("到: ${targetNode?.label ?: "未知"}")
                Divider()
                Text("类型: ${edge.label}")
                Text("权重: ${edge.weight}")
                // 这里可以显示description，如果MemoryLink里有的话
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = onEdit) { Text("编辑") }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
                OutlinedButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
fun EditEdgeDialog(
    edge: Edge,
    onDismiss: () -> Unit,
    onSave: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf(edge.label ?: "related") }
    var weight by remember { mutableStateOf(edge.weight.toString()) }
    var description by remember { mutableStateOf("") } // 假设需要编辑description

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑连接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("类型") })
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("权重") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("描述") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(type, weight.toFloatOrNull() ?: 1.0f, description)
            }) { Text("保存") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun LinkMemoryDialog(
    sourceNodeLabel: String,
    targetNodeLabel: String,
    onDismiss: () -> Unit,
    onLink: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf("related") }
    var weight by remember { mutableStateOf("1.0") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接 '$sourceNodeLabel' 到 '$targetNodeLabel'") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("类型") }
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("权重") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: 1.0f
                    onLink(type, w, description)
                }
            ) { Text("创建连接") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuBox(
        label: String,
        options: List<Pair<String, String>>,
        selected: String,
        onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: ""

    ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
                modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                readOnly = true,
                value = selectedLabel,
                onValueChange = {},
                label = { Text(label) },
                placeholder = { Text("选择...")},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
        ) {
            options.forEach { (id, itemLabel) ->
                DropdownMenuItem(
                        text = { Text(itemLabel) },
                        onClick = {
                            onSelected(id)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

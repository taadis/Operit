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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
                Column {
                    MemoryAppBar(
                            profileList = profileList,
                            profileNameMap = profileNameMap,
                            selectedProfileId = selectedProfileId,
                            onProfileSelected = { profileId -> selectedProfileId = profileId }
                    )
                    SearchBar(
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
                }
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.toggleLinkingMode(!uiState.isLinkingMode) },
                        containerColor = if (uiState.isLinkingMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Toggle Linking Mode")
                    }
                    FloatingActionButton(onClick = { viewModel.startEditing(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Memory")
                    }
                }
            }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
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

@Composable
fun SearchBar(
        query: String,
        onQueryChange: (String) -> Unit,
        onSearch: () -> Unit,
        onClear: () -> Unit
) {
    OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Search Memories") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
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
            title = { Text(text = "Memory Details") },
            text = {
                Column(
                        modifier = Modifier.verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Title: ${memory.title}", style = MaterialTheme.typography.titleMedium)
                    Divider()
                    Text("Content:", style = MaterialTheme.typography.titleSmall)
                    Text(memory.content)
                    Divider()
                    Text("UUID: ${memory.uuid}", style = MaterialTheme.typography.bodySmall)
                    Text("Source: ${memory.source}", style = MaterialTheme.typography.bodySmall)
                    Text(
                            "Importance: ${String.format("%.2f", memory.importance)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "Credibility: ${String.format("%.2f", memory.credibility)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "Created: ${dateFormat.format(memory.createdAt)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "Updated: ${dateFormat.format(memory.updatedAt)}",
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
                    Button(onClick = onEdit) { Text("Edit") }
                    Button(
                            onClick = onDelete,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text("Delete") }
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryAppBar(
        profileList: List<String>,
        profileNameMap: Map<String, String>,
        selectedProfileId: String,
        onProfileSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProfileName = profileNameMap[selectedProfileId] ?: selectedProfileId

    TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI Memory Graph")
                    Box {
                        IconButton(onClick = { expanded = true }) {
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
            },
            actions = {
                Text(
                        text = "Profile: $selectedProfileName",
                        modifier = Modifier.padding(end = 16.dp)
                )
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
            title = { Text(if (memory == null) "Create Memory" else "Edit Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") }
                    )
                    OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Content") },
                            minLines = 3
                    )
                    OutlinedTextField(
                            value = contentType,
                            onValueChange = { contentType = it },
                            label = { Text("Content Type") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onSave(title, content, contentType) }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
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
        title = { Text("Link Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From: ${sourceNode?.label ?: "Unknown"}")
                Text("To: ${targetNode?.label ?: "Unknown"}")
                Divider()
                Text("Type: ${edge.label}")
                Text("Weight: ${edge.weight}")
                // 这里可以显示description，如果MemoryLink里有的话
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = onEdit) { Text("Edit") }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
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
        title = { Text("Edit Link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") })
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(type, weight.toFloatOrNull() ?: 1.0f, description)
            }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
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
        title = { Text("Link '$sourceNodeLabel' to '$targetNodeLabel'") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type") }
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: 1.0f
                    onLink(type, w, description)
                }
            ) { Text("Create Link") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
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
                placeholder = { Text("Select...")},
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

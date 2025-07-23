package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
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
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModelFactory
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val profileList by preferencesManager.profileListFlow.collectAsState(initial = emptyList())
    val activeProfileId by preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    
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
    
    LaunchedEffect(activeProfileId) {
        selectedProfileId = activeProfileId
    }

    val viewModel: MemoryViewModel = viewModel(
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
                    onProfileSelected = { profileId ->
                        selectedProfileId = profileId 
                    }
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
            FloatingActionButton(onClick = { viewModel.createSampleMemories() }) {
                Icon(Icons.Default.Add, contentDescription = "Generate Sample Memories")
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
                    onNodeClick = { node ->
                        viewModel.selectNode(node)
                    }
                )
            }

            uiState.selectedMemory?.let { memory ->
                MemoryInfoDialog(
                    memory = memory,
                    onDismiss = { viewModel.clearSelectedMemory() }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
fun MemoryInfoDialog(memory: Memory, onDismiss: () -> Unit) {
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
                Text("Importance: ${String.format("%.2f", memory.importance)}", style = MaterialTheme.typography.bodySmall)
                Text("Credibility: ${String.format("%.2f", memory.credibility)}", style = MaterialTheme.typography.bodySmall)
                Text("Created: ${dateFormat.format(memory.createdAt)}", style = MaterialTheme.typography.bodySmall)
                Text("Updated: ${dateFormat.format(memory.updatedAt)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
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
                     DropdownMenu(
                         expanded = expanded,
                         onDismissRequest = { expanded = false }
                     ) {
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
            Text(text = "Profile: $selectedProfileName", modifier = Modifier.padding(end = 16.dp))
        }
    )
} 
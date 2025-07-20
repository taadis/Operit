package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModelFactory
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.Memory

@Composable
fun MemoryScreen() {
    val repository = remember { MemoryRepository() }
    val viewModel: MemoryViewModel = viewModel(
        factory = MemoryViewModelFactory(repository)
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            MemoryAppBar(
                uiState = uiState,
                onBack = { viewModel.clearSelectedMemory() }
            )
        },
        floatingActionButton = {
            // This button now generates data for the graph
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

            // Show dialog when a memory is selected
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
    uiState: MemoryUiState,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
             Text("AI Memory Graph")
        },
        navigationIcon = {
            // The back button is not needed here anymore as we use a dialog.
            // If we were navigating to a separate screen, this would be appropriate.
            /*
            if (uiState.selectedMemory != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
            */
        }
    )
} 
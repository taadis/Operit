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
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
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
            if (uiState.selectedMemory != null) { // Keep for potential detail view later
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        }
    )
} 
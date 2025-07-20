package com.ai.assistance.operit.ui.features.memory.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Memory UI State
 * Represents the current state of the Memory screen.
 */
data class MemoryUiState(
    val memories: List<Memory> = emptyList(), // Keep for potential list view
    val graph: Graph = Graph(emptyList(), emptyList()),
    val selectedMemory: Memory? = null,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null
)

/**
 * ViewModel for the Memory/Knowledge Base screen.
 * It handles the business logic for interacting with the MemoryRepository.
 */
class MemoryViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        // Initially load the graph
        loadMemoryGraph()
    }

    /**
     * Loads the entire memory graph from the repository.
     */
    fun loadMemoryGraph() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val graphData = repository.getMemoryGraph()
                _uiState.update {
                    it.copy(
                        graph = graphData,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load memory graph: ${e.message}") }
            }
        }
    }

    /**
     * Searches memories based on the current query.
     * If the query is empty, it fetches all memories.
     */
    fun searchMemories(query: String = _uiState.value.searchQuery) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = repository.searchMemories(query)
                _uiState.update {
                    it.copy(
                        memories = result,
                        isLoading = false,
                        searchQuery = query
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load memories: ${e.message}") }
            }
        }
    }

    /**
     * Updates the search query in the state.
     */
    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    /**
     * Selects a memory to view its details.
     */
    fun selectMemory(memory: Memory) {
        _uiState.update { it.copy(selectedMemory = memory) }
    }

    /**
     * Clears the selected memory, returning to the list view.
     */
    fun clearSelectedMemory() {
        _uiState.update { it.copy(selectedMemory = null) }
    }
    
    /**
     * Deletes a memory.
     */
    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
             _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteMemory(memoryId)
                // Refresh the graph after deletion
                loadMemoryGraph()
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, error = "Failed to delete memory: ${e.message}") }
            }
        }
    }

    /**
     * Creates sample memories and refreshes the list.
     */
    fun createSampleMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.createSampleMemories()
                // Refresh the graph to show the new memories
                loadMemoryGraph()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to create sample memories: ${e.message}") }
            }
        }
    }
}

/**
 * Factory for creating MemoryViewModel instances with dependencies.
 */
class MemoryViewModelFactory(private val repository: MemoryRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            return MemoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 
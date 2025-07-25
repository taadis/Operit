package com.ai.assistance.operit.ui.features.memory.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context

/**
 * Memory UI State
 * Represents the current state of the Memory screen.
 */
data class MemoryUiState(
    val memories: List<Memory> = emptyList(), // Keep for potential list view
    val graph: Graph = Graph(emptyList(), emptyList()),
    val selectedMemory: Memory? = null,
    val selectedNodeId: String? = null,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null,
    val editingMemory: Memory? = null, // 新增：用于编辑/新建
    val isEditing: Boolean = false // 新增：是否处于编辑/新建状态
)

/**
 * ViewModel for the Memory/Knowledge Base screen.
 * It handles the business logic for interacting with the MemoryRepository.
 */
class MemoryViewModel(
    private val repository: MemoryRepository
) : ViewModel() {

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
     * Searches memories and updates the graph with the results.
     * If the query is empty, it reloads the full graph.
     */
    fun searchMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val query = _uiState.value.searchQuery
                val memories = if (query.isBlank()) {
                    repository.searchMemories("") // Get all
                } else {
                    repository.searchMemories(query)
                }
                val graphData = repository.getGraphForMemories(memories)
                _uiState.update {
                    it.copy(
                        graph = graphData,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to search memories: ${e.message}") }
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
     * Selects a node in the graph.
     * Fetches the full memory details for the selected node.
     */
    fun selectNode(node: com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node) {
        viewModelScope.launch {
            // No full-screen loading for this, to prevent UI flashing.
            // The operation should be fast enough.
            val memory = repository.getMemoryByUuid(node.id)
            _uiState.update {
                it.copy(
                    selectedNodeId = node.id,
                    selectedMemory = memory
                )
            }
        }
    }

    /**
     * Clears the selected memory, returning to the list view.
     */
    fun clearSelectedMemory() {
        _uiState.update { it.copy(selectedMemory = null, selectedNodeId = null) }
    }
    
    /** 新建记忆 */
    fun createMemory(title: String, content: String, contentType: String = "text/plain") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.createMemory(title, content, contentType)
                loadMemoryGraph()
                _uiState.update { it.copy(isLoading = false, isEditing = false, editingMemory = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to create memory: ${e.message}") }
            }
        }
    }
    /** 编辑记忆 */
    fun updateMemory(memory: Memory, newTitle: String, newContent: String, newContentType: String = memory.contentType) {
        viewModelScope.launch {
             _uiState.update { it.copy(isLoading = true) }
            try {
                repository.updateMemory(memory, newTitle, newContent, newContentType)
                loadMemoryGraph()
                _uiState.update { it.copy(isLoading = false, isEditing = false, editingMemory = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to update memory: ${e.message}") }
            }
        }
    }
    /** 删除记忆 */
    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteMemoryAndIndex(memoryId)
                loadMemoryGraph()
                _uiState.update { it.copy(isLoading = false, selectedMemory = null, selectedNodeId = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to delete memory: ${e.message}") }
            }
        }
    }
    /** 进入新建/编辑状态 */
    fun startEditing(memory: Memory? = null) {
        _uiState.update { it.copy(isEditing = true, editingMemory = memory) }
    }
    /** 取消编辑 */
    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, editingMemory = null) }
    }
}

/**
 * Factory for creating MemoryViewModel instances with dependencies.
 */
class MemoryViewModelFactory(
    private val context: Context,
    private val profileId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val repository = MemoryRepository(context, profileId)
            return MemoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 
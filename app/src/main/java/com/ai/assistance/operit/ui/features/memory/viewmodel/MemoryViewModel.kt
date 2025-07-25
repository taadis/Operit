package com.ai.assistance.operit.ui.features.memory.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Memory UI State Represents the current state of the Memory screen. */
data class MemoryUiState(
        val memories: List<Memory> = emptyList(), // Keep for potential list view
        val graph: Graph = Graph(emptyList(), emptyList()),
        val selectedMemory: Memory? = null,
        val selectedNodeId: String? = null,
        val isLoading: Boolean = false,
        val searchQuery: String = "",
        val error: String? = null,
        val editingMemory: Memory? = null, // 新增：用于编辑/新建
        val isEditing: Boolean = false, // 新增：是否处于编辑/新建状态
        val isLinkingMode: Boolean = false, // 是否处于连接模式
        val linkingNodeIds: List<String> = emptyList(), // 已选择的连接节点
        val selectedEdge: Edge? = null,
        val editingEdge: Edge? = null,
        val isEditingEdge: Boolean = false
)

/**
 * ViewModel for the Memory/Knowledge Base screen. It handles the business logic for interacting
 * with the MemoryRepository.
 */
class MemoryViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        // Initially load the graph
        loadMemoryGraph()
    }

    private suspend fun refreshGraph(): Graph {
        return repository.getMemoryGraph()
    }

    /** Loads the entire memory graph from the repository. */
    fun loadMemoryGraph() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val graphData = refreshGraph()
                _uiState.update { it.copy(graph = graphData, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load memory graph: ${e.message}")
                }
            }
        }
    }

    /**
     * Searches memories and updates the graph with the results. If the query is empty, it reloads
     * the full graph.
     */
    fun searchMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val query = _uiState.value.searchQuery
                val memories =
                    if (query.isBlank()) {
                        repository.searchMemories("") // Get all
                    } else {
                        repository.searchMemories(query)
                    }
                val graphData = repository.getGraphForMemories(memories)
                _uiState.update { it.copy(graph = graphData, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to search memories: ${e.message}")
                }
            }
        }
    }

    /** Updates the search query in the state. */
    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    /** Selects a memory to view its details. */
    fun selectMemory(memory: Memory) {
        _uiState.update { it.copy(selectedMemory = memory) }
    }

    /** Selects a node in the graph. Fetches the full memory details for the selected node. */
    fun selectNode(node: Node) {
        viewModelScope.launch {
            if (_uiState.value.isLinkingMode) {
                // 连接模式
                val currentLinkingIds = _uiState.value.linkingNodeIds.toMutableList()
                if (node.id !in currentLinkingIds) {
                    currentLinkingIds.add(node.id)
                }
                _uiState.update { it.copy(linkingNodeIds = currentLinkingIds) }
            } else {
                // 普通模式
                val memory = repository.getMemoryByUuid(node.id)
                _uiState.update { it.copy(selectedNodeId = node.id, selectedMemory = memory, selectedEdge = null) }
            }
        }
    }

    /** Selects an edge in the graph. */
    fun selectEdge(edge: Edge) {
        _uiState.update { it.copy(selectedEdge = edge, selectedNodeId = null, selectedMemory = null) }
    }

    /** Clears any selection (node or edge). */
    fun clearSelection() {
        _uiState.update { it.copy(selectedMemory = null, selectedNodeId = null, selectedEdge = null) }
    }

    /** 新建记忆 */
    fun createMemory(title: String, content: String, contentType: String = "text/plain") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.createMemory(title, content, contentType)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(isLoading = false, isEditing = false, editingMemory = null, graph = updatedGraph)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to create memory: ${e.message}")
                }
            }
        }
    }
    /** 编辑记忆 */
    fun updateMemory(
            memory: Memory,
            newTitle: String,
            newContent: String,
            newContentType: String = memory.contentType
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.updateMemory(memory, newTitle, newContent, newContentType)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(isLoading = false, isEditing = false, editingMemory = null, graph = updatedGraph)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to update memory: ${e.message}")
                }
            }
        }
    }
    /** 删除记忆 */
    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteMemoryAndIndex(memoryId)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(isLoading = false, selectedMemory = null, selectedNodeId = null, graph = updatedGraph)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to delete memory: ${e.message}")
                }
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

    /** 进入/退出边的编辑状态 */
    fun startEditingEdge(edge: Edge) {
        _uiState.update { it.copy(isEditingEdge = true, editingEdge = edge) }
    }
    fun cancelEditingEdge() {
        _uiState.update { it.copy(isEditingEdge = false, editingEdge = null) }
    }

    /** 更新边的信息 */
    fun updateEdge(edge: Edge, type: String, weight: Float, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isEditingEdge = false, editingEdge = null) }
            try {
                repository.updateLink(edge.id, type, weight, description)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedEdge = null, // 彻底清空选中状态
                        graph = updatedGraph
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to update link: ${e.message}") }
            }
        }
    }

    /** 删除边 */
    fun deleteEdge(edgeId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteLink(edgeId)
                val updatedGraph = refreshGraph()
                _uiState.update { it.copy(isLoading = false, selectedEdge = null, graph = updatedGraph) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to delete link: ${e.message}") }
            }
        }
    }

    /** 切换连接模式 */
    fun toggleLinkingMode(enabled: Boolean) {
        _uiState.update { it.copy(isLinkingMode = enabled, linkingNodeIds = emptyList()) }
    }

    /** 创建两个记忆之间的连接 */
    fun linkMemories(
            sourceUuid: String,
            targetUuid: String,
            type: String = "related",
            weight: Float = 1.0f,
            description: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val source = repository.findMemoryByUuid(sourceUuid)
                val target = repository.findMemoryByUuid(targetUuid)
                if (source != null && target != null) {
                    repository.linkMemories(source, target, type, weight, description)
                    val updatedGraph = refreshGraph()
                    _uiState.update { it.copy(isLoading = false, isLinkingMode = false, linkingNodeIds = emptyList(), graph = updatedGraph) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to link memories: ${e.message}")
                }
            }
        }
    }
}

/** Factory for creating MemoryViewModel instances with dependencies. */
class MemoryViewModelFactory(private val context: Context, private val profileId: String) :
        ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") val repository = MemoryRepository(context, profileId)
            return MemoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

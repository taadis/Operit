package com.ai.assistance.operit.ui.features.problems.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.tools.AIToolHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 问题库ViewModel，负责管理问题库的数据和操作
 */
class ProblemLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ProblemLibraryViewModel"
    
    // AIToolHandler实例，用于访问问题库数据
    private val toolHandler = AIToolHandler.getInstance(application)
    
    // 问题库数据
    private val _problems = MutableStateFlow<List<AIToolHandler.ProblemRecord>>(emptyList())
    val problems: StateFlow<List<AIToolHandler.ProblemRecord>> = _problems.asStateFlow()
    
    // 当前选中的问题
    private val _selectedProblem = MutableStateFlow<AIToolHandler.ProblemRecord?>(null)
    val selectedProblem: StateFlow<AIToolHandler.ProblemRecord?> = _selectedProblem.asStateFlow()
    
    // 搜索查询
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 编辑模式状态
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()
    
    // 编辑中的摘要
    private val _editedSummary = MutableStateFlow("")
    val editedSummary: StateFlow<String> = _editedSummary.asStateFlow()
    
    // 编辑中的解决方案
    private val _editedSolution = MutableStateFlow("")
    val editedSolution: StateFlow<String> = _editedSolution.asStateFlow()
    
    init {
        // 初始化加载问题库数据
        loadProblems()
    }
    
    /**
     * 加载问题库数据
     */
    private fun loadProblems() {
        viewModelScope.launch {
            try {
                val allProblems = toolHandler.getAllProblemRecords()
                // 按时间降序排序
                _problems.value = allProblems.sortedByDescending { it.timestamp }
                Log.d(TAG, "加载了 ${allProblems.size} 条问题记录")
            } catch (e: Exception) {
                Log.e(TAG, "加载问题库失败", e)
                _problems.value = emptyList()
            }
        }
    }
    
    /**
     * 搜索问题
     */
    fun searchProblems() {
        viewModelScope.launch {
            try {
                val query = _searchQuery.value.trim()
                if (query.isEmpty()) {
                    // 如果搜索查询为空，显示所有问题
                    loadProblems()
                    return@launch
                }
                
                // 使用AIToolHandler的queryProblemLibrary方法搜索问题
                val searchResults = toolHandler.searchProblemLibrary(query)
                _problems.value = searchResults
                Log.d(TAG, "搜索到 ${searchResults.size} 条问题记录")
            } catch (e: Exception) {
                Log.e(TAG, "搜索问题失败", e)
            }
        }
    }
    
    /**
     * 更新搜索查询
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            // 如果查询为空，重新加载所有问题
            loadProblems()
        }
    }
    
    /**
     * 选择问题
     */
    fun selectProblem(problem: AIToolHandler.ProblemRecord) {
        _selectedProblem.value = problem
        // 初始化编辑字段
        _editedSummary.value = problem.summary
        _editedSolution.value = problem.solution
        // 重置编辑模式
        _isEditMode.value = false
    }
    
    /**
     * 清除选中的问题
     */
    fun clearSelectedProblem() {
        _selectedProblem.value = null
        _isEditMode.value = false
    }
    
    /**
     * 切换编辑模式
     */
    fun toggleEditMode(isEdit: Boolean) {
        _isEditMode.value = isEdit
        if (!isEdit) {
            // 退出编辑模式，恢复原始值
            _selectedProblem.value?.let { problem ->
                _editedSummary.value = problem.summary
                _editedSolution.value = problem.solution
            }
        }
    }
    
    /**
     * 更新编辑中的摘要
     */
    fun updateEditedSummary(summary: String) {
        _editedSummary.value = summary
    }
    
    /**
     * 更新编辑中的解决方案
     */
    fun updateEditedSolution(solution: String) {
        _editedSolution.value = solution
    }
    
    /**
     * 保存编辑的问题
     */
    fun saveEditedProblem() {
        viewModelScope.launch {
            try {
                val currentProblem = _selectedProblem.value ?: return@launch
                
                // 创建更新后的问题记录
                val updatedProblem = AIToolHandler.ProblemRecord(
                    uuid = currentProblem.uuid,
                    query = currentProblem.query,
                    solution = _editedSolution.value,
                    tools = currentProblem.tools,
                    summary = _editedSummary.value,
                    timestamp = currentProblem.timestamp
                )
                
                // 保存更新后的问题
                toolHandler.saveProblemRecord(updatedProblem)
                
                // 更新选中的问题和问题列表
                _selectedProblem.value = updatedProblem
                loadProblems()
                
                // 退出编辑模式
                _isEditMode.value = false
                
                Log.d(TAG, "问题已更新: ${updatedProblem.uuid}")
            } catch (e: Exception) {
                Log.e(TAG, "保存问题失败", e)
            }
        }
    }
    
    /**
     * 删除问题
     */
    fun deleteProblem(problem: AIToolHandler.ProblemRecord) {
        viewModelScope.launch {
            try {
                // 调用AIToolHandler删除问题记录
                toolHandler.deleteProblemRecord(problem.uuid)
                
                // 清除选中的问题
                clearSelectedProblem()
                
                // 重新加载问题列表
                loadProblems()
                
                Log.d(TAG, "问题已删除: ${problem.uuid}")
            } catch (e: Exception) {
                Log.e(TAG, "删除问题失败", e)
            }
        }
    }
} 
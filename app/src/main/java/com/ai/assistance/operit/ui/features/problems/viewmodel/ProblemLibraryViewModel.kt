package com.ai.assistance.operit.ui.features.problems.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.defaultTool.ProblemLibraryTool
import com.ai.assistance.operit.util.TextSegmenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 问题库ViewModel，负责管理问题库的数据和操作
 */
class ProblemLibraryViewModel(
    private val toolHandler: AIToolHandler
) : ViewModel() {
    private val TAG = "ProblemLibraryViewModel"
    
    // 获取问题库工具实例
    private val problemLibraryTool: ProblemLibraryTool? = toolHandler.getProblemLibraryTool()
    
    // 问题库数据
    private val _problems = MutableStateFlow<List<ProblemLibraryTool.ProblemRecord>>(emptyList())
    val problems: StateFlow<List<ProblemLibraryTool.ProblemRecord>> = _problems.asStateFlow()
    
    // 当前选中的问题
    private val _selectedProblem = MutableStateFlow<ProblemLibraryTool.ProblemRecord?>(null)
    val selectedProblem: StateFlow<ProblemLibraryTool.ProblemRecord?> = _selectedProblem.asStateFlow()
    
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
    
    // 搜索状态
    private val _searchInfo = MutableStateFlow<String?>(null)
    val searchInfo = _searchInfo.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        // 初始化加载问题库数据
        loadProblems()
    }
    
    /**
     * 加载问题库数据
     */
    private fun loadProblems() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allProblems = withContext(Dispatchers.IO) {
                    problemLibraryTool?.getAllProblemRecords() ?: emptyList()
                }
                // 按时间降序排序
                _problems.value = allProblems.sortedByDescending { it.timestamp }
                Log.d(TAG, "加载了 ${allProblems.size} 条问题记录")
            } catch (e: Exception) {
                Log.e(TAG, "加载问题库失败", e)
                _problems.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 搜索问题
     */
    fun searchProblems() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val query = _searchQuery.value.trim()
                if (query.isEmpty()) {
                    // 如果搜索查询为空，显示所有问题
                    loadProblems()
                    _searchInfo.value = null
                    return@launch
                }
                
                // 将耗时操作移至IO线程
                val results = withContext(Dispatchers.IO) {
                    // 获取分词结果用于显示
                    val segmentedTerms = TextSegmenter.segment(query)
                    
                    // 使用问题库工具的搜索方法
                    // 注意：现在searchProblemLibrary已经是挂起函数，会自动在IO线程执行
                    val searchResults = problemLibraryTool?.searchProblemLibrary(query) ?: emptyList()
                    
                    Pair(segmentedTerms, searchResults)
                }
                
                val (segmentedTerms, searchResults) = results
                _problems.value = searchResults
                
                // 更新搜索信息
                if (segmentedTerms.isNotEmpty()) {
                    _searchInfo.value = "分词搜索: ${segmentedTerms.joinToString(", ")}"
                } else {
                    _searchInfo.value = "关键词搜索: $query"
                }
                
                Log.d(TAG, "搜索到 ${searchResults.size} 条问题记录")
            } catch (e: Exception) {
                Log.e(TAG, "搜索问题失败", e)
                _searchInfo.value = "搜索失败: ${e.message}"
            } finally {
                _isLoading.value = false
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
            _searchInfo.value = null
        }
    }
    
    /**
     * 选择问题
     */
    fun selectProblem(problem: ProblemLibraryTool.ProblemRecord) {
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
                val updatedProblem = ProblemLibraryTool.ProblemRecord(
                    uuid = currentProblem.uuid,
                    query = currentProblem.query,
                    solution = _editedSolution.value,
                    tools = currentProblem.tools,
                    summary = _editedSummary.value,
                    timestamp = currentProblem.timestamp
                )
                
                // 保存更新后的问题
                problemLibraryTool?.saveProblemRecord(updatedProblem)
                
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
    fun deleteProblem(problem: ProblemLibraryTool.ProblemRecord) {
        viewModelScope.launch {
            try {
                // 调用问题库工具删除问题记录
                val success = problemLibraryTool?.deleteProblemRecord(problem.uuid) ?: false
                
                if (success) {
                    // 清除选中的问题
                    clearSelectedProblem()
                    
                    // 重新加载问题列表
                    loadProblems()
                    
                    Log.d(TAG, "问题已删除: ${problem.uuid}")
                } else {
                    Log.e(TAG, "删除问题失败: 操作未成功")
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除问题失败", e)
            }
        }
    }
} 
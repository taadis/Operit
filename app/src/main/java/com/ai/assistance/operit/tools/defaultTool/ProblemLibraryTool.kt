package com.ai.assistance.operit.tools.defaultTool

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.library.ProblemLibrary
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ProblemEntity
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.tools.ToolExecutor
import com.ai.assistance.operit.ui.permissions.ToolCategory
import com.ai.assistance.operit.util.TextSegmenter
import com.ai.assistance.operit.util.VectorDatabaseHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 顶级常量，用于顶级函数中的日志
private const val PROBLEM_LIBRARY_TOOL_TAG = "ProblemLibraryTool_Reg"

/**
 * 问题库工具类 - 负责问题库的管理、查询和搜索功能
 * 使用Room数据库存储，支持向量搜索
 */
class ProblemLibraryTool private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ProblemLibraryTool"
        
        @Volatile
        private var INSTANCE: ProblemLibraryTool? = null
        
        /**
         * 获取ProblemLibraryTool单例实例
         * 确保整个应用中只有一个实例
         */
        fun getInstance(context: Context): ProblemLibraryTool {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProblemLibraryTool(context.applicationContext).also { 
                    INSTANCE = it
                    
                    // 初始化向量索引（仅在第一次创建实例时调用）
                    it.initVectorIndex()
                    Log.d(TAG, "ProblemLibraryTool 单例实例已创建并初始化")
                }
            }
        }
    }

    // 问题记录数据类
    data class ProblemRecord(
        val uuid: String,
        val query: String,
        val solution: String,
        val tools: List<String>,
        val summary: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    // 数据库访问
    private val database = AppDatabase.getDatabase(context)
    private val problemDao = database.problemDao()
    
    // 向量数据库帮助类
    private val vectorDB = VectorDatabaseHelper(context)
    
    // 是否使用向量搜索
    private var useVectorSearch = true
    
    // 向量索引是否准备好
    private var vectorIndexReady = false

    // 初始化向量索引
    private fun initVectorIndex() {
        try {
            Log.d(TAG, "开始初始化向量索引")
            
            // 尝试加载已有的索引
            val loadStartTime = System.currentTimeMillis()
            val loaded = vectorDB.loadIndex()
            val loadTime = System.currentTimeMillis() - loadStartTime
            
            if (!loaded) {
                Log.d(TAG, "没有找到已有索引，开始从数据库构建新索引")
                
                // 从数据库构建向量索引
                kotlinx.coroutines.runBlocking {
                    val buildStartTime = System.currentTimeMillis()
                    vectorDB.buildIndexFromDatabase()
                    val buildTime = System.currentTimeMillis() - buildStartTime
                    
                    vectorIndexReady = true
                    Log.d(TAG, "向量索引已准备完毕，构建耗时: ${buildTime}ms")
                }
            } else {
                vectorIndexReady = true
                Log.d(TAG, "从文件加载向量索引成功，加载耗时: ${loadTime}ms")
            }
            
            // 向量索引准备好后，检查索引大小
            kotlinx.coroutines.runBlocking {
                val count = problemDao.getProblemCount()
                Log.d(TAG, "数据库中的问题记录数: $count")
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化向量索引失败: ${e.message}", e)
            useVectorSearch = false
        }
    }

    // 保存问题记录
    fun saveProblemRecord(record: ProblemRecord) {
        // 保存到Room数据库，同时会更新向量索引
        kotlinx.coroutines.runBlocking {
            vectorDB.addRecord(record)
            Log.d(TAG, "问题记录已保存到数据库: ${record.uuid}")
        }
    }

    // 获取所有问题记录
    fun getAllProblemRecords(): List<ProblemRecord> {
        // 从Room数据库获取所有记录
        return kotlinx.coroutines.runBlocking {
            val entities = problemDao.getAllProblems()
            entities.map { it.toProblemRecord() }
        }
    }
    
    // 通过ID列表获取问题记录
    private suspend fun getRecordsByIds(ids: List<String>): List<ProblemRecord> {
        val entities = problemDao.getProblemsByIds(ids)
        return entities.map { it.toProblemRecord() }
    }

    // 搜索问题库
    suspend fun searchProblemLibrary(query: String): List<ProblemRecord> = withContext(Dispatchers.IO) {
        // 首先检查数据库中的记录数
        val count = problemDao.getProblemCount()
        Log.d(TAG, "开始搜索问题库, 查询: '$query', 数据库记录数: $count")
        
        if (count == 0) {
            Log.d(TAG, "数据库中没有记录，返回空结果")
            return@withContext emptyList()
        }

        // 如果查询为空，返回所有记录
        if (query.isBlank()) {
            Log.d(TAG, "查询为空，返回所有记录")
            val entities = problemDao.getAllProblems()
            return@withContext entities.map { it.toProblemRecord() }
        }
        
        // 如果向量索引已准备好且启用了向量搜索，使用向量搜索
        if (useVectorSearch && vectorIndexReady) {
            try {
                Log.d(TAG, "使用向量搜索")
                val startTime = System.currentTimeMillis()
                
                // 使用向量搜索获取相似记录
                val result = vectorDB.searchSimilar(query, 20)
                
                val endTime = System.currentTimeMillis()
                Log.d(TAG, "向量搜索完成，耗时: ${endTime - startTime}ms，结果数: ${result.size}")
                
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "向量搜索失败，回退到传统搜索: ${e.message}", e)
                // 出错时回退到传统搜索
            }
        } else {
            Log.d(TAG, "向量搜索未启用或未准备好，useVectorSearch=$useVectorSearch, vectorIndexReady=$vectorIndexReady")
        }
        
        // 传统搜索方法（作为备选）
        Log.d(TAG, "使用传统分词搜索")
        val startTime = System.currentTimeMillis()
        
        val queryLower = query.lowercase()
        
        // 首先尝试基本的SQL LIKE搜索
        val directMatches = problemDao.searchByText(queryLower)
        
        // 如果直接匹配到了内容，直接返回结果，避免昂贵的分词操作
        if (directMatches.size >= 5) {
            return@withContext directMatches.map { it.toProblemRecord() }
        }

        // 对查询文本进行分词
        val segmentedQuery = TextSegmenter.segment(queryLower)
        Log.d(TAG, "分词结果: $segmentedQuery")

        // 如果分词结果为空，回退到简单的分词方式
        val keywords = if (segmentedQuery.isEmpty()) {
            query.split(Regex("\\s+|,|，|\\.|。"))
                .filter { it.length > 1 }
                .map { it.lowercase() }
        } else {
            segmentedQuery
        }

        // 如果没有有效关键词，返回所有记录
        if (keywords.isEmpty()) {
            val entities = problemDao.getAllProblems()
            return@withContext entities.map { it.toProblemRecord() }
        }
        
        // 限制问题库的搜索规模，避免处理过多数据
        val allEntities = problemDao.getAllProblems()
        val recordsToSearch = if (allEntities.size > 100) {
            // 如果问题库超过100条记录，随机选择100条进行搜索
            allEntities.shuffled().take(100)
        } else {
            allEntities
        }

        // 按相关性排序的记录
        val result = recordsToSearch.map { entity ->
            val record = entity.toProblemRecord()
            
            // 使用文本分词器计算相关性得分
            val queryRelevance = TextSegmenter.calculateRelevance(record.query, keywords) * 3.0
            
            // 如果查询得分为0，且摘要得分可能也为0，则跳过其他计算以提高性能
            if (queryRelevance == 0.0 && record.summary.isBlank()) {
                return@map Pair(record, 0.0)
            }
            
            val summaryRelevance = TextSegmenter.calculateRelevance(record.summary, keywords) * 2.0
            
            // 只有当前分数较低时才计算解决方案相关性，因为解决方案通常文本较长
            val solutionRelevance = if (queryRelevance + summaryRelevance < 0.5) {
                TextSegmenter.calculateRelevance(record.solution, keywords) * 1.0
            } else {
                0.0
            }
            
            // 只有当前分数较低时才计算工具相关性
            val toolsRelevance = if (queryRelevance + summaryRelevance + solutionRelevance < 0.8) {
                record.tools.sumOf { tool ->
                    TextSegmenter.calculateRelevance(tool, keywords) * 0.5
                }
            } else {
                0.0
            }
            
            // 计算总相关性得分
            val totalScore = queryRelevance + summaryRelevance + solutionRelevance + toolsRelevance
            
            Pair(record, totalScore)
        }
        .filter { it.second > 0 } // 只返回有相关性的记录
        .sortedByDescending { it.second } // 按相关性排序
        .map { it.first }
        
        val endTime = System.currentTimeMillis()
        Log.d(TAG, "分词搜索完成，耗时: ${endTime - startTime}ms，结果数: ${result.size}")
        
        return@withContext result
    }

    // 删除问题记录
    fun deleteProblemRecord(uuid: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                // 从Room数据库删除
                problemDao.deleteProblem(uuid)
                
                // 从向量索引中移除（通过重新构建索引）
                if (useVectorSearch && vectorIndexReady) {
                    vectorDB.buildIndexFromDatabase()
                }
                
                Log.d(TAG, "问题记录已删除: $uuid")
                true
            } catch (e: Exception) {
                Log.e(TAG, "删除问题记录失败: ${e.message}")
                false
            }
        }
    }

    // 查询问题库并格式化结果，用于AI工具返回
    suspend fun queryProblemLibrary(query: String): String = withContext(Dispatchers.IO) {
        // 检查数据库中的记录数
        val count = problemDao.getProblemCount()
        if (count == 0) {
            return@withContext "问题库为空，尚无记录"
        }

        // 搜索问题库
        val searchResults = searchProblemLibrary(query).take(5) // 最多返回5条记录

        if (searchResults.isEmpty()) {
            return@withContext "未找到相关记录"
        }

        return@withContext formatProblemLibraryResults(searchResults)
    }

    // 格式化问题库查询结果
    private fun formatProblemLibraryResults(records: List<ProblemRecord>): String {
        val result = StringBuilder()
        result.appendLine("找到 ${records.size} 条相关记录:")
        
        records.forEach { record ->
            result.appendLine("\nUUID: ${record.uuid}")
            
            // 优先显示摘要，如果没有则显示原始查询
            if (record.summary.isNotEmpty()) {
                result.appendLine("摘要: ${record.summary}")
            } else {
                result.appendLine("问题: ${record.query}")
            }
            
            // 显示使用的工具
            result.appendLine("使用工具: ${record.tools.joinToString(", ")}")
            
            // 显示时间
            result.appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(record.timestamp))}")
        }

        return result.toString()
    }
    
    // 释放资源
    fun release() {
        if (useVectorSearch) {
            vectorDB.release()
        }
    }

    // 向AIToolHandler注册工具
    fun registerTools(toolHandler: AIToolHandler) {
        // 查询问题库工具
        toolHandler.registerTool(
            name = "query_problem_library",
            category = ToolCategory.FILE_READ,
            dangerCheck = null,
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                "查询问题库: $query"
            },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                    
                    val result = kotlinx.coroutines.runBlocking {
                        queryProblemLibrary(query)
                    }
                    
                    return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(result)
                    )
                }
                
                override fun validateParameters(tool: AITool): ToolValidationResult {
                    val query = tool.parameters.find { it.name == "query" }?.value
                    if (query == null) {
                        return ToolValidationResult(
                            valid = false,
                            errorMessage = "缺少必要参数: query"
                        )
                    }
                    return ToolValidationResult(valid = true)
                }
                
                override fun getCategory(): ToolCategory {
                    return ToolCategory.FILE_READ
                }
            }
        )
    }
}

/**
 * 向AIToolHandler注册问题库工具
 */
fun registerProblemLibraryTool(toolHandler: AIToolHandler, context: Context) {
    // 添加顶级函数自己的TAG常量
    val TAG = "ProblemLibraryTool_Reg"
    
    // 获取单例实例
    val problemLibraryTool = ProblemLibraryTool.getInstance(context)
    
    // 防止多次注册
    if (toolHandler.getProblemLibraryTool() != null) {
        Log.d(TAG, "ProblemLibraryTool 已经被注册，跳过注册")
        return
    }
    
    // 向AIToolHandler中注册问题库工具
    problemLibraryTool.registerTools(toolHandler)
    
    // 保存ProblemLibraryTool实例到AIToolHandler中
    toolHandler.setProblemLibraryTool(problemLibraryTool)
    Log.d(TAG, "ProblemLibraryTool 已注册到 AIToolHandler")
} 
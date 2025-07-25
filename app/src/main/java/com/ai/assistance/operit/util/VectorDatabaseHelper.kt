package com.ai.assistance.operit.util

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.library.ProblemLibraryTool
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ProblemEntity
import com.github.jelmerk.hnswlib.core.Item
import com.github.jelmerk.hnswlib.core.SearchResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/** 向量数据库帮助类，用于管理问题库的向量索引和搜索功能 与Room数据库集成 */
class VectorDatabaseHelper(private val context: Context) {
    companion object {
        private const val TAG = "VectorDatabaseHelper"
        private const val VECTOR_SIZE = 128 // 向量维度
        private const val INDEX_FILENAME = "problem_vector_index.bin"
        private const val MAX_ITEM_COUNT = 2000 // 最大保存的记录数，超过后将释放一些旧的索引
    }

    // 数据库访问
    private val database = AppDatabase.getDatabase(context)
    private val problemDao = database.problemDao()

    // 记录项实现
    data class ProblemItem(val id: String, private val vector: FloatArray) :
        Item<String, FloatArray> {
        override fun id(): String = id
        override fun vector(): FloatArray = vector

        // FloatArray需要自定义equals和hashCode
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ProblemItem
            if (id != other.id) return false
            if (!vector.contentEquals(other.vector)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + vector.contentHashCode()
            return result
        }

        // 重要: 实现dimensions方法，返回向量维度
        override fun dimensions(): Int = vector.size
    }

    // 创建简单的索引实例，避免使用高级API
    private val index = SimpleVectorIndex<String, ProblemItem>()

    // 执行器服务，用于异步索引构建
    private val executorService = Executors.newSingleThreadExecutor()

    // 简单的词频向量化，将在稍后替换为TF模型生成
    private fun textToSimpleVector(text: String): FloatArray {
        val result = FloatArray(VECTOR_SIZE) { 0f }

        // 对文本进行分词
        val words = TextSegmenter.segment(text.lowercase())

        // 计算词频，并归一化到向量空间
        val wordMap = mutableMapOf<String, Int>()
        words.forEach { word -> wordMap[word] = (wordMap[word] ?: 0) + 1 }

        // 简单哈希映射到向量空间
        wordMap.forEach { (word, count) ->
            val hash = abs(word.hashCode())
            val index = hash % VECTOR_SIZE
            // 词频权重
            result[index] += count.toFloat()
        }

        // 向量归一化
        var sum = 0f
        for (i in result.indices) {
            sum += result[i] * result[i]
        }

        if (sum > 0) {
            val norm = Math.sqrt(sum.toDouble()).toFloat()
            for (i in result.indices) {
                result[i] /= norm
            }
        }

        return result
    }

    // 自定义简单向量索引实现，避免HnswIndex的API兼容性问题
    class SimpleVectorIndex<TId, TItem : Item<TId, FloatArray>> {
        private val items = mutableMapOf<TId, TItem>()

        fun add(item: TItem) {
            items[item.id()] = item
        }

        fun remove(id: TId) {
            items.remove(id)
        }

        fun contains(id: TId): Boolean {
            return items.containsKey(id)
        }

        fun items(): List<TItem> {
            return items.values.toList()
        }

        fun size(): Int {
            return items.size
        }

        fun findNearest(queryVector: FloatArray, k: Int): List<SearchResult<TItem, Float>> {
            // 计算所有项目与查询向量的余弦相似度
            val results =
                    items.values.map { item ->
                        val similarity = cosineSimilarity(queryVector, item.vector())
                        val distance = 1f - similarity // 距离 = 1 - 相似度
                        // 使用SearchResult.create工厂方法创建结果对象
                        val searchResult = SearchResult.create(item, distance)
                        @Suppress("UNCHECKED_CAST") searchResult as SearchResult<TItem, Float>
                    }

            // 按相似度排序（距离从小到大）并返回前k个结果
            return results.sortedBy { it.distance() as Float }.take(k)
        }

        // 计算余弦相似度
        private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f

            var dotProduct = 0f
            var normA = 0f
            var normB = 0f

            for (i in a.indices) {
                dotProduct += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }

            return if (normA <= 0 || normB <= 0) 0f
            else dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
        }
    }

    // 生成问题记录的向量表示
    fun generateVector(record: ProblemLibraryTool.ProblemRecord): FloatArray {
        // 构建文本表示，权重不同字段
        val textContent =
                StringBuilder()
                        .apply {
                            // 摘要权重最高
                            append(record.summary.repeat(3))
                            append(" ")
                            // 查询权重次之
                            append(record.query.repeat(2))
                            append(" ")
                            // 工具、解决方案权重低
                            append(record.tools.joinToString(" "))
                            append(" ")
                            append(record.solution.take(min(500, record.solution.length)))
                        }
                        .toString()

        return textToSimpleVector(textContent)
    }

    // 将记录添加到索引中，并存储到Room数据库
    suspend fun addRecord(record: ProblemLibraryTool.ProblemRecord) {
        try {
            val vector = generateVector(record)
            val item = ProblemItem(record.uuid, vector)

            // 如果已存在，先移除旧的
            if (index.contains(record.uuid)) {
                index.remove(record.uuid)
            }

            // 添加到索引
            index.add(item)

            // 将向量序列化为ByteArray
            val vectorData =
                    ByteBuffer.allocate(VECTOR_SIZE * 4)
                            .apply {
                                order(ByteOrder.LITTLE_ENDIAN)
                                for (value in vector) {
                                    putFloat(value)
                                }
                            }
                            .array()

            // 保存到Room数据库
            val entity = ProblemEntity.fromProblemRecord(record, vectorData)
            problemDao.insertProblem(entity)

            // 检查是否需要保存索引（可以根据需要设置条件）
            if (index.size() % 10 == 0) {
                saveIndexAsync()
            }

            Log.d(TAG, "向量索引添加成功: ${record.uuid}, 当前索引大小: ${index.size()}")
        } catch (e: Exception) {
            Log.e(TAG, "向量索引添加失败: ${e.message}")
        }
    }

    // 搜索相似记录
    suspend fun searchSimilar(
            query: String,
            maxResults: Int = 10
    ): List<ProblemLibraryTool.ProblemRecord> {
        try {
            Log.d(TAG, "开始向量搜索: '$query', 最大结果数: $maxResults, 当前索引大小: ${index.size()}")

            if (index.size() == 0) {
                Log.w(TAG, "向量索引为空，尝试构建索引...")
                buildIndexFromDatabase()

                if (index.size() == 0) {
                    Log.e(TAG, "构建索引后仍为空，可能数据库中没有记录")
                    return emptyList()
                }
            }

            val queryVector = textToSimpleVector(query)
            Log.d(TAG, "生成查询向量成功")

            val results = index.findNearest(queryVector, maxResults)
            Log.d(TAG, "找到 ${results.size} 条相似结果")

            // 获取相似记录的UUID列表
            val uuids = results.map { it.item().id() }
            Log.d(TAG, "获取到的UUID列表: $uuids")

            // 从数据库中获取完整记录
            val entities = problemDao.getProblemsByIds(uuids)
            Log.d(TAG, "从数据库检索到 ${entities.size} 条记录")

            // 转换为ProblemRecord并保持原搜索结果的顺序
            val idToEntityMap = entities.associateBy { it.uuid }
            val finalResults =
                    uuids.mapNotNull { uuid ->
                        val entity = idToEntityMap[uuid]
                        if (entity == null) {
                            Log.w(TAG, "数据库中找不到UUID: $uuid")
                        }
                        entity?.toProblemRecord()
                    }

            Log.d(TAG, "最终返回 ${finalResults.size} 条搜索结果")
            return finalResults
        } catch (e: Exception) {
            Log.e(TAG, "向量搜索失败: ${e.message}", e)
            return emptyList()
        }
    }

    // 异步保存索引
    private fun saveIndexAsync() {
        executorService.submit(Callable { saveIndex() })
    }

    // 保存索引到文件
    fun saveIndex() {
        try {
            val indexFile = File(context.filesDir, INDEX_FILENAME)

            ObjectOutputStream(FileOutputStream(indexFile)).use { oos ->
                // 保存为简单的ID到向量映射
                val vectorMap = index.items().associate { it.id() to it.vector() }
                oos.writeObject(vectorMap)
            }

            Log.d(TAG, "向量索引保存成功，条目数: ${index.size()}")
        } catch (e: Exception) {
            Log.e(TAG, "向量索引保存失败: ${e.message}")
        }
    }

    // 从文件加载索引
    fun loadIndex(): Boolean {
        try {
            val indexFile = File(context.filesDir, INDEX_FILENAME)

            if (!indexFile.exists()) {
                Log.d(TAG, "向量索引文件不存在，使用新索引")
                return false
            }

            // 文件存在，尝试加载
            ObjectInputStream(FileInputStream(indexFile)).use { ois ->
                @Suppress("UNCHECKED_CAST")
                val vectorMap = ois.readObject() as Map<String, FloatArray>

                // 将加载的索引中的项目添加到当前索引
                vectorMap.forEach { (id, vector) -> index.add(ProblemItem(id, vector)) }
            }

            Log.d(TAG, "向量索引加载成功，条目数: ${index.size()}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "向量索引加载失败: ${e.message}")
            return false
        }
    }

    // 从Room数据库构建索引
    suspend fun buildIndexFromDatabase() {
        try {
            Log.d(TAG, "从数据库开始构建向量索引")

            // 清空当前索引
            val currentItems = index.items().toList()
            for (item in currentItems) {
                index.remove(item.id())
            }

            // 从数据库获取所有记录
            val allProblems = problemDao.getAllProblems()

            // 添加所有问题到索引
            for (entity in allProblems) {
                val vectorData = entity.vectorData
                if (vectorData != null) {
                    // 从ByteArray反序列化为FloatArray
                    val vector =
                            ByteBuffer.wrap(vectorData).order(ByteOrder.LITTLE_ENDIAN).let { buffer
                                ->
                                FloatArray(VECTOR_SIZE) { buffer.getFloat() }
                            }

                    // 添加到索引
                    index.add(ProblemItem(entity.uuid, vector))
                } else {
                    // 如果没有预计算的向量数据，从记录生成
                    val record = entity.toProblemRecord()
                    val vector = generateVector(record)

                    // 添加到索引
                    index.add(ProblemItem(record.uuid, vector))

                    // 更新数据库中的向量数据
                    val newVectorData =
                            ByteBuffer.allocate(VECTOR_SIZE * 4)
                                    .apply {
                                        order(ByteOrder.LITTLE_ENDIAN)
                                        for (value in vector) {
                                            putFloat(value)
                                        }
                                    }
                                    .array()

                    problemDao.updateVectorData(entity.uuid, newVectorData)
                }
            }

            // 保存索引
            saveIndex()

            Log.d(TAG, "向量索引构建完成，索引大小: ${index.size()}")
        } catch (e: Exception) {
            Log.e(TAG, "向量索引构建失败: ${e.message}")
        }
    }

    // 释放资源
    fun release() {
        try {
            saveIndex()
            executorService.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败: ${e.message}")
        }
    }
}

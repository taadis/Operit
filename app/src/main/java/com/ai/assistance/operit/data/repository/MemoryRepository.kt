package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryLink
import com.ai.assistance.operit.data.model.MemoryTag
import com.ai.assistance.operit.data.model.MemoryTag_
import com.ai.assistance.operit.data.model.Memory_
import com.ai.assistance.operit.data.model.DocumentChunk
import com.ai.assistance.operit.data.model.DocumentChunk_
import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.data.model.ChunkReference
import com.ai.assistance.operit.services.EmbeddingService
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import com.ai.assistance.operit.util.vector.IndexItem
import com.ai.assistance.operit.util.vector.VectorIndexManager
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.objectbox.query.QueryCondition
import java.io.IOException
import java.util.UUID

/**
 * Repository for handling Memory data operations. It abstracts the data source (ObjectBox) from the
 * rest of the application.
 */
class MemoryRepository(private val context: Context, profileId: String) {

    private val store = ObjectBoxManager.get(context, profileId)
    private val memoryBox: Box<Memory> = store.boxFor()
    private val tagBox = store.boxFor<MemoryTag>()
    private val linkBox = store.boxFor<MemoryLink>()
    private val chunkBox = store.boxFor<DocumentChunk>()

    // --- HNSW向量索引集成 ---
    private val vectorIndexManager: VectorIndexManager<IndexItem<Memory>, String> by lazy {
        val indexFile = File(context.filesDir, "memory_hnsw_${profileId}.idx")
        val manager =
                VectorIndexManager<IndexItem<Memory>, String>(
                        dimensions = 100, // 实际embedding维度为100
                        maxElements = 100_000,
                        indexFile = indexFile
                )
        manager.initIndex()
        // 首次构建索引
        memoryBox.all.filter { it.embedding != null }.forEach { memory ->
            manager.addItem(IndexItem(memory.uuid, memory.embedding!!.vector, memory))
        }
        manager
    }

    /**
     * 从外部文档创建记忆。
     * @param title 文档记忆的标题。
     * @param filePath 文档的路径。
     * @param fileContent 文档的文本内容。
     * @return 创建的Memory对象。
     */
    suspend fun createMemoryFromDocument(documentName: String, originalPath: String, text: String): Memory = withContext(Dispatchers.IO) {
        // 1. 为文档本身生成嵌入
        val documentEmbedding = EmbeddingService.generateEmbedding(documentName)?.vector ?: FloatArray(100)

        // 2. 创建一个初始的Memory对象并立即保存以获得ID
        val documentMemory = Memory(
            title = documentName,
            content = "这是一个文档节点，包含了文件 '${documentName}' 的内容。",
            uuid = UUID.randomUUID().toString()
        ).apply {
            this.embedding = Embedding(documentEmbedding)
            this.isDocumentNode = true
            this.documentPath = originalPath
        }
        memoryBox.put(documentMemory)

        // 3. 为文档块创建专用的HNSW索引，确保从干净的状态开始
        val indexFile = context.getFileStreamPath("doc_index_${documentMemory.id}.hnsw")
        if (indexFile.exists()) {
            indexFile.delete()
        }
        documentMemory.chunkIndexFilePath = indexFile.absolutePath
        val chunkIndexManager = VectorIndexManager<IndexItem<ChunkReference>, String>(
            dimensions = 100, // Or your model's embedding dimension
            maxElements = 20000,
            indexFile = indexFile
        )

        // 4. 分割、清洗和处理文本块
        val chunks = text.split(Regex("(\\r?\\n[\\t ]*){2,}"))
            .mapNotNull { chunkText ->
                val cleanedText = chunkText.replace(Regex("(?m)^[\\*\\-=_]{3,}\\s*$"), "").trim()
                if (cleanedText.isNotBlank()) {
                    DocumentChunk(content = cleanedText, chunkIndex = 0) // chunkIndex will be set later
                } else {
                    null
                }
            }.mapIndexed { index, chunk ->
                chunk.apply { this.chunkIndex = index }
            }

        // 5. 为所有块生成嵌入并添加到索引和数据库
        if (chunks.isNotEmpty()) {
            // 首先，将所有块链接到父级Memory
            chunks.forEach { it.memory.target = documentMemory }
            // 其次，将chunks存入数据库以获取它们的永久ID
            chunkBox.put(chunks)

            // 然后为所有块生成嵌入
            val embeddings = chunks.map { EmbeddingService.generateEmbedding(it.content) }

            // 最后，用有效ID和嵌入更新块，并将它们添加到索引管理器中
            chunks.forEachIndexed { index, chunk ->
                if (index < embeddings.size) {
                    val embedding = embeddings[index]
                    if (embedding != null) {
                        chunk.embedding = embedding
                        // 此时 chunk.id 是有效的, 存入ChunkReference而不是整个chunk
                        val reference = ChunkReference(chunk.id)
                        chunkIndexManager.addItem(IndexItem("chunk_${chunk.id}", embedding.vector, reference))
                    }
                }
            }
            // 因为embedding被设置了，再次put来更新它们
            chunkBox.put(chunks)
        }

        // 保存索引到文件
        chunkIndexManager.save()
        android.util.Log.d("MemoryRepo", "Chunk index saved to: ${indexFile.absolutePath}. File exists: ${indexFile.exists()}")

        // 更新父Memory以保存ToMany关系和索引路径
        android.util.Log.d("MemoryRepo", "Saving memory ${documentMemory.id} with chunkIndexFilePath: ${documentMemory.chunkIndexFilePath}")
        memoryBox.put(documentMemory)
        documentMemory
    }

    // --- Memory CRUD Operations ---

    /**
     * Creates or updates a memory, automatically generating its embedding.
     * @param memory The memory object to be saved.
     * @return The ID of the saved memory.
     */
    suspend fun saveMemory(memory: Memory): Long = withContext(Dispatchers.IO){
        // Generate embedding before saving
        if (memory.content.isNotBlank()) {
            memory.embedding = EmbeddingService.generateEmbedding(memory.content)
        }
        memoryBox.put(memory)
    }

    /**
     * Finds a memory by its ID.
     * @param id The ID of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun findMemoryById(id: Long): Memory? = withContext(Dispatchers.IO) {
        memoryBox.get(id)
    }

    /**
     * Finds a memory by its UUID.
     * @param uuid The UUID of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun findMemoryByUuid(uuid: String): Memory? = withContext(Dispatchers.IO) {
        memoryBox.query(Memory_.uuid.equal(uuid)).build().findFirst()
    }

    /**
     * Finds a memory by its exact title.
     * @param title The title of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun findMemoryByTitle(title: String): Memory? = withContext(Dispatchers.IO) {
        memoryBox.query(Memory_.title.equal(title)).build().findFirst()
    }

    /**
     * Deletes a memory and all its links. This is a critical operation and should be handled with
     * care.
     * @param memoryId The ID of the memory to delete.
     * @return True if deletion was successful, false otherwise.
     */
    suspend fun deleteMemory(memoryId: Long): Boolean = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId) ?: return@withContext false

        // 如果是文档节点，删除其专属的区块索引文件和所有区块
        if (memory.isDocumentNode) {
            if (memory.chunkIndexFilePath != null) {
                try {
                    val indexFile = File(memory.chunkIndexFilePath!!)
                    if (indexFile.exists()) {
                        if (indexFile.delete()) {
                            android.util.Log.d("MemoryRepo", "Deleted chunk index file: ${indexFile.path}")
                        } else {
                            android.util.Log.w("MemoryRepo", "Failed to delete chunk index file: ${indexFile.path}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MemoryRepo", "Error deleting chunk index file for memory ID $memoryId", e)
                }
            }
            // 删除关联的区块
            val chunkIds = memory.documentChunks.map { it.id }
            if (chunkIds.isNotEmpty()) {
                chunkBox.removeByIds(chunkIds)
                android.util.Log.d("MemoryRepo", "Deleted ${chunkIds.size} associated chunks for document.")
            }
        }

        // Before deleting the memory, we must clean up its links.
        // This prevents dangling references.
        memory.links.forEach { linkBox.remove(it) }
        memory.backlinks.forEach { linkBox.remove(it) }
        memoryBox.remove(memory)
    }

    // --- Link CRUD Operations ---
    suspend fun findLinkById(linkId: Long): MemoryLink? = withContext(Dispatchers.IO) {
        linkBox.get(linkId)
    }

    suspend fun updateLink(linkId: Long, type: String, weight: Float, description: String): MemoryLink? = withContext(Dispatchers.IO) {
        val link = findLinkById(linkId) ?: return@withContext null
        val sourceMemory = link.source.target

        link.type = type
        link.weight = weight
        link.description = description
        linkBox.put(link)

        // 在更新link后，同样put其所属的source memory。
        // 这是为了向ObjectBox明确指出，这个父实体的关系集合“脏了”，
        // 以此来避免后续查询时拿到缓存的旧数据。
        if (sourceMemory != null) {
            memoryBox.put(sourceMemory)
        }

        link
    }

    suspend fun deleteLink(linkId: Long): Boolean = withContext(Dispatchers.IO) {
        // 为了健壮性，在删除链接后，也更新其父实体。
        val link = findLinkById(linkId)
        val sourceMemory = link?.source?.target

        val wasRemoved = linkBox.remove(linkId)

        if (wasRemoved && sourceMemory != null) {
            // 通过put源实体，我们确保它的ToMany关系缓存在其他线程或未来的查询中得到更新。
            memoryBox.put(sourceMemory)
        }
        wasRemoved
    }

    // --- Tagging Operations ---

    /**
     * Adds a tag to a memory.
     * @param memory The memory to tag.
     * @param tagName The name of the tag.
     * @return The MemoryTag object.
     */
    suspend fun addTagToMemory(memory: Memory, tagName: String): MemoryTag = withContext(Dispatchers.IO) {
        // Find existing tag or create a new one
        val tag =
                tagBox.query()
                        .equal(MemoryTag_.name, tagName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build()
                        .findFirst()
                        ?: MemoryTag(name = tagName).also { tagBox.put(it) }

        if (!memory.tags.any { it.id == tag.id }) {
            memory.tags.add(tag)
            memoryBox.put(memory)
        }
        tag
    }

    // --- Linking Operations ---

    /**
     * Creates a link between two memories.
     * @param source The source memory.
     * @param target The target memory.
     * @param type The type of the link (e.g., "causes", "explains").
     * @param weight The strength of the link.
     * @param description A description of the link.
     */
    suspend fun linkMemories(
            source: Memory,
            target: Memory,
            type: String,
            weight: Float = 1.0f,
            description: String = ""
    ) = withContext(Dispatchers.IO) {
        val link = MemoryLink(type = type, weight = weight, description = description)
        link.source.target = source
        link.target.target = target

        source.links.add(link)
        memoryBox.put(source)
    }

    /** Gets all outgoing links from a memory. */
    suspend fun getOutgoingLinks(memoryId: Long): List<MemoryLink> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        memory?.links ?: emptyList()
    }

    /** Gets all incoming links to a memory. */
    suspend fun getIncomingLinks(memoryId: Long): List<MemoryLink> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        memory?.backlinks ?: emptyList()
    }

    // --- Complex Queries ---

    /**
     * Searches memories using semantic search if a query is provided, otherwise returns all
     * memories.
     * @param query The search query string.
     * @return A list of matching Memory objects, sorted by relevance.
     */
    suspend fun searchMemories(query: String): List<Memory> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext memoryBox.all

        val scores = mutableMapOf<Long, Double>()
        val k = 60.0 // RRF constant for result fusion
        val allMemories = memoryBox.all // Fetch all memories once for efficiency

        // 1. Keyword-based search (Memory title/content contains query)
        val titleCondition =
                Memory_.title.contains(query, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        val contentCondition =
                Memory_.content.contains(query, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        val keywordResults = memoryBox.query(titleCondition.or(contentCondition)).build().find()

        keywordResults.forEachIndexed { index, memory ->
            val rank = index + 1
            scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + (1.0 / (k + rank))
        }

        // 2. Reverse Containment Search (Query contains Memory Title)
        // This is crucial for finding "长安大学" within the query "长安大学在西安".
        val reverseContainmentResults =
                allMemories.filter { memory -> query.contains(memory.title, ignoreCase = true) }
        reverseContainmentResults.forEachIndexed { index, memory ->
            val rank = index + 1
            // Use the same RRF formula to add to the score
            scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + (1.0 / (k + rank))
        }

        // 3. Semantic search (for conceptual matches)
        val queryEmbedding = EmbeddingService.generateEmbedding(query)
        if (queryEmbedding != null) {
            val allMemoriesWithEmbedding = allMemories.filter { it.embedding != null }

            val semanticResults =
                    allMemoriesWithEmbedding
                            .mapNotNull { memory ->
                                memory.embedding?.let { memoryEmbedding ->
                                    val similarity =
                                            EmbeddingService.cosineSimilarity(
                                                    queryEmbedding,
                                                    memoryEmbedding
                                            )
                                    Pair(memory, similarity)
                                }
                            }
                            .sortedByDescending { it.second }
                            .map { it.first } // We only need the ranked list of memories

            semanticResults.forEachIndexed { index, memory ->
                val rank = index + 1
                scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + (1.0 / (k + rank))
            }
        }

        // 4. Fuse results using RRF and return sorted list
        if (scores.isEmpty()) {
            return@withContext emptyList()
        }
        val sortedMemoryIds = scores.entries.sortedByDescending { it.value }.map { it.key }

        // Fetch the sorted entities from the database
        val sortedMemories = memoryBox.get(sortedMemoryIds)

        // 7. Semantic Deduplication
        deduplicateBySemantics(sortedMemories)
    }

    /**
     * 获取指定记忆的所有文档区块。
     * @param memoryId 父记忆的ID。
     * @return 该记忆关联的DocumentChunk列表。
     */
    suspend fun getChunksForMemory(memoryId: Long): List<DocumentChunk> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        // 从数据库关系中获取，并按原始顺序排序
        memory?.documentChunks?.sortedBy { it.chunkIndex } ?: emptyList()
    }

    /**
     * 在指定文档的区块内进行搜索。
     * @param memoryId 父记忆的ID。
     * @param query 搜索查询。
     * @return 匹配的DocumentChunk列表。
     */
    suspend fun searchChunksInDocument(memoryId: Long, query: String): List<DocumentChunk> = withContext(Dispatchers.IO) {
        android.util.Log.d("MemoryRepo", "--- Starting search in document (Memory ID: $memoryId) for query: '$query' ---")
        val memory = findMemoryById(memoryId) ?: return@withContext emptyList<DocumentChunk>().also {
            android.util.Log.w("MemoryRepo", "Document with ID $memoryId not found.")
        }

        if (query.isBlank()) {
            android.util.Log.d("MemoryRepo", "Query is blank, returning all chunks sorted by index.")
            return@withContext getChunksForMemory(memoryId) // 返回有序的全部区块
        }

        // --- 关键词搜索（作为补充） ---
        val keywordResults = getChunksForMemory(memoryId)
            .filter { it.content.contains(query, ignoreCase = true) }
            .toMutableList()

        // 2. 向量语义搜索
        val semanticResults = mutableListOf<DocumentChunk>()
        val documentMemory = memoryBox.get(memoryId)
        if (documentMemory?.chunkIndexFilePath != null && File(documentMemory.chunkIndexFilePath!!).exists()) {
            val queryEmbedding = EmbeddingService.generateEmbedding(query)?.vector
            if (queryEmbedding != null) {
                android.util.Log.d("MemoryRepo", "Generated query embedding successfully. Starting semantic search.")
                try {
                    val chunkIndexManager = VectorIndexManager<IndexItem<ChunkReference>, String>(
                        dimensions = 100,
                        maxElements = 20000,
                        indexFile = File(documentMemory.chunkIndexFilePath!!)
                    )
                    val searchResults = chunkIndexManager.findNearest(queryEmbedding, 20)
                    val chunkIds = searchResults.map { it.value.chunkId }
                    // 从数据库批量获取完整的chunk对象
                    semanticResults.addAll(chunkBox.get(chunkIds))

                    android.util.Log.d("MemoryRepo", "Semantic search found ${semanticResults.size} results (similarity > 0.82).")
                } catch (e: Exception) {
                    android.util.Log.e("MemoryRepo", "Error during semantic search in document", e)
                }
            }
        } else {
            android.util.Log.w("MemoryRepo", "Chunk index file not found or path is null for document ID $memoryId. Skipping semantic search.")
        }

        // 3. 合并并去重结果
        val combinedResults = (keywordResults + semanticResults).distinctBy { it.id }
        android.util.Log.d("MemoryRepo", "Combined and deduplicated results count: ${combinedResults.size}. Results are now ordered by relevance.")
        android.util.Log.d("MemoryRepo", "--- Search in document finished ---")

        combinedResults
    }

    /**
     * 更新单个文档区块的内容。
     * @param chunkId 要更新的区块ID。
     * @param newContent 新的文本内容。
     */
    suspend fun updateChunk(chunkId: Long, newContent: String) = withContext(Dispatchers.IO) {
        val chunk = chunkBox.get(chunkId) ?: return@withContext
        val memory = chunk.memory.target ?: return@withContext

        chunk.content = newContent
        val newEmbeddingVector = EmbeddingService.generateEmbedding(newContent)?.vector ?: return@withContext // 重新生成向量并获取vector
        chunk.embedding = Embedding(newEmbeddingVector)
        chunkBox.put(chunk)

        // 同时更新专用索引文件中的向量
        val parentMemory = memory
        if (parentMemory?.chunkIndexFilePath != null) {
            val indexFile = File(parentMemory.chunkIndexFilePath!!)
            if (indexFile.exists()) {
                val chunkIndexManager = VectorIndexManager<IndexItem<ChunkReference>, String>(100, 20_000, indexFile)
                chunkIndexManager.initIndex() // 加载
                chunkIndexManager.addItem(IndexItem(chunk.id.toString(), newEmbeddingVector, ChunkReference(chunk.id)))
                chunkIndexManager.save() // 保存更改
                android.util.Log.d("MemoryRepo", "Updated chunk ${chunk.id} in index file: ${indexFile.path}")
            }
        }
    }

    suspend fun addMemoryToIndex(memory: Memory) = withContext(Dispatchers.IO) {
        if (memory.embedding != null) {
            vectorIndexManager.addItem(IndexItem(memory.uuid, memory.embedding!!.vector, memory))
        }
    }
    suspend fun removeMemoryFromIndex(memory: Memory) = withContext(Dispatchers.IO) {
        // hnswlib支持removeEnabled时可用，若不支持可忽略
        // vectorIndexManager.removeItem(memory.uuid)
    }

    /** 使用HNSW索引的高效语义检索。 */
    suspend fun searchMemoriesPrecise(query: String, similarityThreshold: Float = 0.95f): List<Memory> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val queryEmbedding = EmbeddingService.generateEmbedding(query) ?: return@withContext emptyList()
        // 取前100个最相近的记忆，再按阈值过滤
        val candidates = vectorIndexManager.findNearest(queryEmbedding.vector, 100)
        candidates.mapNotNull {
            val memory = it.value
            if (memory.embedding != null && EmbeddingService.cosineSimilarity(queryEmbedding, memory.embedding!!) >= similarityThreshold) {
                memory
            } else {
                null
            }
        }
    }

    /**
     * Deduplicates a list of memories based on semantic similarity. If two memories are very
     * similar, only the first one in the list (higher rank) is kept.
     */
    private fun deduplicateBySemantics(sortedMemories: List<Memory>): List<Memory> {
        if (sortedMemories.size < 2) {
            return sortedMemories
        }

        val deduplicatedList = mutableListOf<Memory>()
        val memoriesToProcess = sortedMemories.toMutableList()

        while (memoriesToProcess.isNotEmpty()) {
            val current = memoriesToProcess.removeAt(0)
            deduplicatedList.add(current)

            // Remove other memories that are too similar to the 'current' one
            memoriesToProcess.removeAll { other ->
                val similarity =
                        if (current.embedding != null && other.embedding != null) {
                            EmbeddingService.cosineSimilarity(
                                    current.embedding!!,
                                    other.embedding!!
                            )
                        } else {
                            0f
                        }
                // Use a slightly lower threshold for de-duping search results
                val isSimilar = similarity > 0.90f
                if (isSimilar) {
                    android.util.Log.d(
                            "MemoryRepo",
                            "Deduplicating '${other.title}' (similar to '${current.title}', similarity: $similarity)"
                    )
                }
                isSimilar
            }
        }
        android.util.Log.d(
                "MemoryRepo",
                "Deduplication complete. Initial: ${sortedMemories.size}, Final: ${deduplicatedList.size}"
        )
        return deduplicatedList
    }

    /**
     * Builds a Graph object from a given list of memories. This is used to display a subset of the
     * entire memory graph, e.g., after a search.
     * @param memories The list of memories to include in the graph.
     * @return A Graph object.
     */
    suspend fun getGraphForMemories(memories: List<Memory>): Graph = withContext(Dispatchers.IO) {
        // Expand the initial list of memories to include direct neighbors
        val expandedMemories = mutableSetOf<Memory>()
        expandedMemories.addAll(memories)

        memories.forEach { memory ->
            memory.links.forEach { link -> link.target.target?.let { expandedMemories.add(it) } }
            memory.backlinks.forEach { backlink ->
                backlink.source.target?.let { expandedMemories.add(it) }
            }
        }

        android.util.Log.d(
                "MemoryRepo",
                "Initial memories: ${memories.size}, Expanded memories: ${expandedMemories.size}"
        )
        buildGraphFromMemories(expandedMemories.toList())
    }

    /** Retrieves a single memory by its UUID. */
    suspend fun getMemoryByUuid(uuid: String): Memory? =
            withContext(Dispatchers.IO) {
                memoryBox.query(Memory_.uuid.equal(uuid)).build().findUnique()
            }

    /**
     * 创建新记忆并自动生成embedding，保存到数据库并同步索引。
     */
    suspend fun createMemory(title: String, content: String, contentType: String = "text/plain", source: String = "user_input"): Memory? = withContext(Dispatchers.IO) {
        val embedding = EmbeddingService.generateEmbedding(content) ?: return@withContext null
        val memory = Memory(
            title = title,
            content = content,
            contentType = contentType,
            source = source,
            embedding = embedding
        )
        saveMemory(memory)
        addMemoryToIndex(memory)
        memory
    }

    /**
     * 更新已有记忆内容（title/content等），自动更新embedding和索引。
     */
    suspend fun updateMemory(memory: Memory, newTitle: String, newContent: String, newContentType: String = memory.contentType): Memory? = withContext(Dispatchers.IO) {
        val newEmbedding = EmbeddingService.generateEmbedding(newContent) ?: return@withContext null
        val updated = memory.copy(
            title = newTitle,
            content = newContent,
            contentType = newContentType,
            embedding = newEmbedding,
            updatedAt = java.util.Date()
        )
        saveMemory(updated)
        addMemoryToIndex(updated)
        updated
    }

    /**
     * 删除记忆并同步索引。
     */
    suspend fun deleteMemoryAndIndex(memoryId: Long): Boolean = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId) ?: return@withContext false
        removeMemoryFromIndex(memory)
        deleteMemory(memoryId)
    }

    /**
     * 根据UUID批量删除记忆及其所有关联。
     * @param uuids 要删除的记忆的UUID集合。
     * @return 如果操作成功，返回true。
     */
    suspend fun deleteMemoriesByUuids(uuids: Set<String>): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.d("MemoryRepo", "Attempting to delete memories with UUIDs: $uuids")
        if (uuids.isEmpty()) {
            android.util.Log.d("MemoryRepo", "UUID set is empty, nothing to delete.")
            return@withContext true
        }

        // 使用QueryBuilder动态构建OR查询
        val builder = memoryBox.query()
        // ObjectBox的QueryBuilder.equal()不支持字符串，我们必须从Property本身开始构建条件
        if (uuids.isNotEmpty()) {
            var finalCondition: QueryCondition<Memory> = Memory_.uuid.equal(uuids.first())
            uuids.drop(1).forEach { uuid ->
                finalCondition = finalCondition.or(Memory_.uuid.equal(uuid))
            }
            builder.apply(finalCondition)
        }
        val memoriesToDelete = builder.build().find()

        android.util.Log.d("MemoryRepo", "Found ${memoriesToDelete.size} memories to delete.")
        if (memoriesToDelete.isEmpty()) {
            return@withContext true
        }

        // 在一个事务中执行所有数据库写入操作
        try {
            store.runInTx {
                // 1. 收集所有相关链接和区块的ID
                val linkIdsToDelete = mutableSetOf<Long>()
                val chunkIdsToDelete = mutableSetOf<Long>()
                for (memory in memoriesToDelete) {
                    memory.links.reset()
                    memory.backlinks.reset()
                    memory.links.forEach { link -> linkIdsToDelete.add(link.id) }
                    memory.backlinks.forEach { link -> linkIdsToDelete.add(link.id) }

                    if (memory.isDocumentNode) {
                        memory.documentChunks.reset()
                        memory.documentChunks.forEach { chunk -> chunkIdsToDelete.add(chunk.id) }
                    }
                }
                android.util.Log.d("MemoryRepo", "Found ${linkIdsToDelete.size} unique links and ${chunkIdsToDelete.size} chunks to delete.")

                // 2. 批量删除链接和区块
                if (linkIdsToDelete.isNotEmpty()) {
                    linkBox.removeByIds(linkIdsToDelete)
                    android.util.Log.d("MemoryRepo", "Bulk-deleted ${linkIdsToDelete.size} links.")
                }
                if (chunkIdsToDelete.isNotEmpty()) {
                    chunkBox.removeByIds(chunkIdsToDelete)
                    android.util.Log.d("MemoryRepo", "Bulk-deleted ${chunkIdsToDelete.size} chunks.")
                }

                // 3. 批量删除记忆本身
                val memoryIdsToDelete = memoriesToDelete.map { it.id }
                memoryBox.removeByIds(memoryIdsToDelete)
                android.util.Log.d("MemoryRepo", "Bulk-deleted ${memoriesToDelete.size} memories.")
            }
            android.util.Log.d("MemoryRepo", "Transaction completed successfully.")
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepo", "Error during bulk delete transaction.", e)
            return@withContext false
        }

        // 4. 在事务外处理向量索引和文件
        for (memory in memoriesToDelete) {
            removeMemoryFromIndex(memory)
            // 删除文档的专属索引文件
            if (memory.isDocumentNode && memory.chunkIndexFilePath != null) {
                try {
                    val indexFile = File(memory.chunkIndexFilePath!!)
                    if (indexFile.exists() && indexFile.delete()) {
                         android.util.Log.d("MemoryRepo", "Deleted chunk index file: ${indexFile.path}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MemoryRepo", "Error deleting chunk index file for memory UUID ${memory.uuid}", e)
                }
            }
        }
        android.util.Log.d("MemoryRepo", "Removed memories from vector index and cleaned up chunk index files.")

        return@withContext true
    }

    // --- Graph Export ---

    /** Fetches all memories and their links, and converts them into a Graph data structure. */
    suspend fun getMemoryGraph(): Graph = withContext(Dispatchers.IO) {
        buildGraphFromMemories(memoryBox.all)
    }

    /**
     * Private helper to construct a graph from a specific list of memories. Ensures that edges are
     * only created if both source and target nodes are in the list.
     */
    private fun buildGraphFromMemories(memories: List<Memory>): Graph {
        val memoryUuids = memories.map { it.uuid }.toSet()

        val nodes =
                memories.map { memory ->
                    Node(
                            id = memory.uuid,
                            label = memory.title,
                            color =
                                    if (memory.isDocumentNode) {
                                        Color(0xFF9575CD) // Purple for documents
                                    } else {
                                    when (memory.tags.firstOrNull()?.name) {
                                        "Person" -> Color(0xFF81C784) // Green
                                        "Concept" -> Color(0xFF64B5F6) // Blue
                                        else -> Color.LightGray
                                        }
                                    }
                    )
                }

        val edges = mutableListOf<Edge>()
        memories.forEach { memory ->
            // 关键：重置关系缓存，确保获取最新的连接信息
            memory.links.reset()
            memory.links.forEach { link ->
                val sourceId = link.source.target?.uuid
                val targetId = link.target.target?.uuid
                // Only add edges if both source and target are in the filtered list
                if (sourceId != null &&
                    targetId != null &&
                    sourceId in memoryUuids &&
                    targetId in memoryUuids
                ) {
                    edges.add(
                        Edge(
                            id = link.id,
                            sourceId = sourceId,
                            targetId = targetId,
                            label = link.type,
                            weight = link.weight
                        )
                    )
                } else if (sourceId != null && targetId != null) {
                    // Log discarded edges for debugging
                    // android.util.Log.d("MemoryRepo", "Discarding edge: $sourceId -> $targetId
                    // (Not in filtered list)")
                }
            }
        }
        android.util.Log.d(
                "MemoryRepo",
                "Built graph with ${nodes.size} nodes and ${edges.distinct().size} edges."
        )
        return Graph(nodes = nodes, edges = edges.distinct())
    }
}

package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.db.ObjectBox
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryLink
import com.ai.assistance.operit.data.model.MemoryTag
import com.ai.assistance.operit.data.model.MemoryTag_
import com.ai.assistance.operit.data.model.Memory_
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import androidx.compose.ui.graphics.Color
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * 仓库类，用于管理 Memory 实体和相关对象的数据库操作。
 * This class is the central point for all data operations related to the AI's memory.
 */
class MemoryRepository {

    private val memoryBox = ObjectBox.store.boxFor<Memory>()
    private val tagBox = ObjectBox.store.boxFor<MemoryTag>()
    private val linkBox = ObjectBox.store.boxFor<MemoryLink>()

    // --- Memory CRUD Operations ---

    /**
     * 创建或更新一个记忆。
     * @param memory The memory object to be saved.
     * @return The ID of the saved memory.
     */
    fun saveMemory(memory: Memory): Long {
        return memoryBox.put(memory)
    }

    /**
     * 根据ID查找一个记忆。
     * @param id The ID of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    fun findMemoryById(id: Long): Memory? {
        return memoryBox.get(id)
    }
    
    /**
     * 根据UUID查找一个记忆。
     * @param uuid The UUID of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    fun findMemoryByUuid(uuid: String): Memory? {
        return memoryBox.query(Memory_.uuid.equal(uuid)).build().findFirst()
    }

    /**
     * 删除一个记忆及其所有关联。
     * This is a critical operation and should be handled with care.
     * @param memoryId The ID of the memory to delete.
     * @return True if deletion was successful, false otherwise.
     */
    fun deleteMemory(memoryId: Long): Boolean {
        val memory = findMemoryById(memoryId) ?: return false
        // Before deleting the memory, we must clean up its links.
        // This prevents dangling references.
        memory.links.forEach { linkBox.remove(it) }
        memory.backlinks.forEach { linkBox.remove(it) }
        return memoryBox.remove(memory)
    }

    // --- Tagging Operations ---

    /**
     * 为记忆添加一个标签。
     * @param memory The memory to tag.
     * @param tagName The name of the tag.
     * @return The MemoryTag object.
     */
    fun addTagToMemory(memory: Memory, tagName: String): MemoryTag {
        // Find existing tag or create a new one
        val tag = tagBox.query().equal(MemoryTag_.name, tagName, QueryBuilder.StringOrder.CASE_SENSITIVE).build().findFirst()
            ?: MemoryTag(name = tagName).also { tagBox.put(it) }

        if (!memory.tags.any { it.id == tag.id }) {
            memory.tags.add(tag)
            memoryBox.put(memory)
        }
        return tag
    }

    // --- Linking Operations ---

    /**
     * 在两个记忆之间创建关联。
     * @param source The source memory.
     * @param target The target memory.
     * @param type The type of the link (e.g., "causes", "explains").
     * @param weight The strength of the link.
     */
    fun linkMemories(source: Memory, target: Memory, type: String, weight: Float = 1.0f) {
        val link = MemoryLink(type = type, weight = weight)
        link.source.target = source
        link.target.target = target
        
        source.links.add(link)
        memoryBox.put(source)
    }
    
     /**
     * 获取一个记忆的所有出站链接 (links from this memory)。
     */
    fun getOutgoingLinks(memoryId: Long): List<MemoryLink> {
        val memory = findMemoryById(memoryId)
        return memory?.links ?: emptyList()
    }

    /**
     * 获取一个记忆的所有入站链接 (links to this memory)。
     */
    fun getIncomingLinks(memoryId: Long): List<MemoryLink> {
        val memory = findMemoryById(memoryId)
        return memory?.backlinks ?: emptyList()
    }
    
    // --- Complex Queries ---

    /**
     * 根据标题或内容进行全文搜索。
     * @param query The search query string.
     * @return A list of matching Memory objects.
     */
    fun searchMemories(query: String): List<Memory> {
        if (query.isBlank()) return memoryBox.all
        
        val titleCondition = Memory_.title.contains(query, io.objectbox.query.QueryBuilder.StringOrder.CASE_INSENSITIVE)
        val contentCondition = Memory_.content.contains(query, io.objectbox.query.QueryBuilder.StringOrder.CASE_INSENSITIVE)
        
        return memoryBox.query(titleCondition.or(contentCondition)).build().find()
    }

    /**
     * Retrieves a single memory by its UUID.
     */
    suspend fun getMemoryByUuid(uuid: String): Memory? = withContext(Dispatchers.IO) {
        memoryBox.query(Memory_.uuid.equal(uuid)).build().findUnique()
    }

    // --- Sample Data ---

    /**
     * Creates a set of sample memories for demonstration purposes.
     * This will create interconnected memories about Isaac Newton.
     */
    fun createSampleMemories() {
        // To avoid creating duplicates, check if a key memory already exists.
        if (searchMemories("Isaac Newton").isNotEmpty()) {
            return // Sample data probably exists already.
        }

        // 1. Create Memory for "Isaac Newton"
        val newton = Memory(
            title = "Isaac Newton",
            content = "An English mathematician, physicist, astronomer, alchemist, theologian, and author widely recognised as one of the most influential scientists of all time.",
            contentType = "text/plain",
            source = "system_generated"
        )
        saveMemory(newton)
        addTagToMemory(newton, "Person")
        addTagToMemory(newton, "Scientist")
        addTagToMemory(newton, "Physics")


        // 2. Create Memory for "Laws of Motion"
        val lawsOfMotion = Memory(
            title = "Laws of Motion",
            content = "Newton's laws of motion are three basic laws of classical mechanics that describe the relationship between the motion of an object and the forces acting on it.",
            contentType = "text/plain",
            source = "system_generated"
        )
        saveMemory(lawsOfMotion)
        addTagToMemory(lawsOfMotion, "Concept")
        addTagToMemory(lawsOfMotion, "Physics")

        // 3. Create Memory for "Law of Universal Gravitation"
        val lawOfGravitation = Memory(
            title = "Law of Universal Gravitation",
            content = "States that every particle attracts every other particle in the universe with a force that is directly proportional to the product of their masses and inversely proportional to the square of the distance between their centers.",
            contentType = "text/plain",
            source = "system_generated"
        )
        saveMemory(lawOfGravitation)
        addTagToMemory(lawOfGravitation, "Concept")
        addTagToMemory(lawOfGravitation, "Physics")
        addTagToMemory(lawOfGravitation, "Astronomy")

        // 4. Link the memories
        linkMemories(newton, lawsOfMotion, "FORMULATED")
        linkMemories(newton, lawOfGravitation, "FORMULATED")
        linkMemories(lawsOfMotion, lawOfGravitation, "RELATED_TO")
    }

    // --- Graph Export ---

    /**
     * Fetches all memories and their links, and converts them into a Graph data structure.
     */
    fun getMemoryGraph(): Graph {
        val allMemories = memoryBox.all
        val nodes = allMemories.map { memory ->
            Node(
                id = memory.uuid,
                label = memory.title,
                color = when (memory.tags.firstOrNull()?.name) {
                    "Person" -> Color(0xFF81C784) // Green
                    "Concept" -> Color(0xFF64B5F6) // Blue
                    else -> Color.LightGray
                }
            )
        }

        val edges = mutableListOf<Edge>()
        allMemories.forEach { memory ->
            memory.links.forEach { link ->
                val sourceId = link.source.target?.uuid
                val targetId = link.target.target?.uuid
                if (sourceId != null && targetId != null) {
                    edges.add(
                        Edge(
                            sourceId = sourceId,
                            targetId = targetId,
                            label = link.type
                        )
                    )
                }
            }
        }
        return Graph(nodes = nodes, edges = edges.distinct())
    }
} 
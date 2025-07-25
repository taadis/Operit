package com.ai.assistance.operit.util.vector

import com.ai.assistance.operit.data.model.Memory
import com.github.jelmerk.hnswlib.core.Item

/** 用于HNSW索引的记忆包装类。 */
data class MemoryItem(
        private val _id: String,
        private val _vector: FloatArray,
        val memoryRef: Memory
) : Item<String, FloatArray> {
    override fun id(): String = _id
    override fun vector(): FloatArray = _vector
    override fun dimensions(): Int = _vector.size
}

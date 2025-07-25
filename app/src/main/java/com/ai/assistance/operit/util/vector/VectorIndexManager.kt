package com.ai.assistance.operit.util.vector

import com.github.jelmerk.hnswlib.core.DistanceFunctions
import com.github.jelmerk.hnswlib.core.Item
import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * 精简的HNSW向量索引管理器，支持初始化、添加、查询、保存、加载。
 */
class VectorIndexManager<T : Item<Id, FloatArray>, Id : Any>(
    private val dimensions: Int,
    private val maxElements: Int,
    private val indexFile: File? = null
) {
    private var index: HnswIndex<Id, FloatArray, T, Float>? = null

    /** 初始化索引（新建或加载） */
    fun initIndex() {
        index = if (indexFile != null && indexFile.exists()) {
            ObjectInputStream(indexFile.inputStream()).use { it.readObject() as HnswIndex<Id, FloatArray, T, Float> }
        } else {
            HnswIndex
                .newBuilder(dimensions, DistanceFunctions.FLOAT_COSINE_DISTANCE, maxElements)
                .build()
        }
    }

    /** 添加一个向量项 */
    fun addItem(item: T) {
        index?.add(item)
    }

    /** 查询最近的K个邻居 */
    fun findNearest(query: FloatArray, k: Int): List<T> {
        return index?.findNearest(query, k)?.map { it.item() } ?: emptyList()
    }

    /** 保存索引到文件 */
    fun save() {
        if (indexFile != null && index != null) {
            ObjectOutputStream(indexFile.outputStream()).use { it.writeObject(index) }
        }
    }

    /** 关闭索引（可选） */
    fun close() {
        index = null
    }
} 
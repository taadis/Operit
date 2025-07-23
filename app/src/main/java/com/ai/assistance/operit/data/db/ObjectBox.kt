package com.ai.assistance.operit.data.db

import android.content.Context
import com.ai.assistance.operit.data.model.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ObjectBoxManager {
    private val stores = ConcurrentHashMap<String, BoxStore>()

    fun get(context: Context, profileId: String): BoxStore {
        return stores.getOrPut(profileId) {
            // 如果profileId是"default"，我们使用旧的数据库位置以实现向后兼容
            val dbName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
            val dbDir = File(context.filesDir, dbName)

            MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .directory(dbDir)
                .build()
        }
    }

    fun close(profileId: String) {
        stores.remove(profileId)?.close()
    }

    fun closeAll() {
        stores.values.forEach { it.close() }
        stores.clear()
    }
} 
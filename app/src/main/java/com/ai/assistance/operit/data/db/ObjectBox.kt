package com.ai.assistance.operit.data.db

import android.content.Context
import com.ai.assistance.operit.data.model.MyObjectBox
import io.objectbox.BoxStore

object ObjectBox {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        if (::store.isInitialized && !store.isClosed) {
            return
        }
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }
} 
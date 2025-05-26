package com.ai.assistance.operit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.dao.MessageDao
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.MessageEntity

/** 应用数据库，包含问题记录表、聊天表和消息表 */
@Database(
        entities = [ProblemEntity::class, ChatEntity::class, MessageEntity::class],
        version = 2,
        exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    /** 获取问题记录DAO */
    abstract fun problemDao(): ProblemDao

    /** 获取聊天DAO */
    abstract fun chatDao(): ChatDao

    /** 获取消息DAO */
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** 获取数据库实例，单例模式 */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "app_database"
                                        )
                                        .fallbackToDestructiveMigration() // 当版本升级时，销毁旧表
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}

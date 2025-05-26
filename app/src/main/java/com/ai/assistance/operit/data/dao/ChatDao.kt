package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.assistance.operit.data.model.ChatEntity
import kotlinx.coroutines.flow.Flow

/** 聊天DAO接口，定义对聊天表的数据访问方法 */
@Dao
interface ChatDao {
    /** 获取所有聊天，按更新时间降序排列 */
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC") fun getAllChats(): Flow<List<ChatEntity>>

    /** 获取所有聊天（挂起函数版本） */
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    suspend fun getAllChatsDirectly(): List<ChatEntity>

    /** 根据ID获取单个聊天 */
    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    /** 插入或更新聊天 */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChat(chat: ChatEntity)

    /** 删除聊天 */
    @Query("DELETE FROM chats WHERE id = :chatId") suspend fun deleteChat(chatId: String)

    /** 更新聊天元数据 */
    @Query(
            "UPDATE chats SET updatedAt = :timestamp, title = :title, inputTokens = :inputTokens, outputTokens = :outputTokens WHERE id = :chatId"
    )
    suspend fun updateChatMetadata(
            chatId: String,
            title: String,
            timestamp: Long,
            inputTokens: Int,
            outputTokens: Int
    )
}

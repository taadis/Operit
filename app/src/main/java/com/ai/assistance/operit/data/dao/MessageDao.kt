package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.assistance.operit.data.model.MessageEntity

/** 消息DAO接口，定义对消息表的数据访问方法 */
@Dao
interface MessageDao {
    /** 获取指定聊天的所有消息，按序号排序 */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY orderIndex ASC")
    suspend fun getMessagesForChat(chatId: String): List<MessageEntity>

    /** 插入单条消息并返回消息ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    /** 批量插入消息 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /** 更新消息内容 */
    @Query("UPDATE messages SET content = :content WHERE messageId = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    /** 获取指定聊天中最大的序号 */
    @Query("SELECT MAX(orderIndex) FROM messages WHERE chatId = :chatId")
    suspend fun getMaxOrderIndex(chatId: String): Int?

    /** 删除指定聊天的所有消息 */
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesForChat(chatId: String)
    
    /** 根据时间戳查找消息 */
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp = :timestamp LIMIT 1")
    suspend fun getMessageByTimestamp(chatId: String, timestamp: Long): MessageEntity?
}

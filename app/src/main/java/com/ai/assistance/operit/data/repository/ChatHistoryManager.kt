package com.ai.assistance.operit.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// 仅保留这个DataStore用于存储当前聊天ID
private val Context.currentChatIdDataStore by preferencesDataStore(name = "current_chat_id")

class ChatHistoryManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ChatHistoryManager"

        @Volatile private var INSTANCE: ChatHistoryManager? = null

        fun getInstance(context: Context): ChatHistoryManager {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance = ChatHistoryManager(context.applicationContext)
                        INSTANCE = instance
                        instance
                    }
        }
    }

    // 使用Room数据库
    private val database = AppDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()

    init {
        // 确保数据库被初始化
        Log.d(TAG, "ChatHistoryManager初始化，预加载数据库")
        // 使用独立的协程作用域触发数据库初始化
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 预先尝试执行一个简单查询
                val chats = chatDao.getAllChats().first()
                Log.d(TAG, "数据库预加载完成，现有聊天数：${chats.size}")
            } catch (e: Exception) {
                Log.e(TAG, "数据库预加载失败", e)
            }
        }
    }

    // 互斥锁用于同步操作
    private val mutex = Mutex()

    // DataStore键
    private object PreferencesKeys {
        val CURRENT_CHAT_ID = stringPreferencesKey("current_chat_id")
    }

    // 获取所有聊天历史（转换为UI层需要的ChatHistory对象）
    private val _chatHistoriesFlow: Flow<List<ChatHistory>> =
    // 使用原始的Flow方式，这样可以确保数据库变化时会自动刷新
    chatDao.getAllChats().map { chatEntities ->
                // Log.d(TAG, "加载聊天列表，共 ${chatEntities.size} 个聊天")

                // 使用withContext将处理移至IO线程
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    chatEntities.map { chatEntity ->
                        // 不再加载消息，只转换元数据
                        // 将时间戳转换为LocalDateTime
                        val createdAt =
                                Instant.ofEpochMilli(chatEntity.createdAt)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime()

                        val updatedAt =
                                Instant.ofEpochMilli(chatEntity.updatedAt)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime()

                        // 创建ChatHistory对象（消息为空列表）
                        ChatHistory(
                                id = chatEntity.id,
                                title = chatEntity.title,
                                messages = emptyList(), // 不加载消息，提高性能
                                createdAt = createdAt,
                                updatedAt = updatedAt,
                                inputTokens = chatEntity.inputTokens,
                                outputTokens = chatEntity.outputTokens
                        )
                    }
                }
            }

    // 转换为StateFlow以便共享
    val chatHistoriesFlow =
            _chatHistoriesFlow.stateIn(
                    CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    SharingStarted.Lazily,
                    emptyList()
            )

    // 获取当前聊天ID
    private val _currentChatIdFlow: Flow<String?> =
            context.currentChatIdDataStore.data
                    .catch { exception ->
                        if (exception is IOException) {
                            emit(emptyPreferences())
                        } else {
                            throw exception
                        }
                    }
                    .map { preferences -> preferences[PreferencesKeys.CURRENT_CHAT_ID] }

    // 转换为StateFlow以便共享
    val currentChatIdFlow =
            _currentChatIdFlow.stateIn(
                    CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    SharingStarted.Lazily,
                    null
            )

    // 保存聊天历史
    suspend fun saveChatHistory(history: ChatHistory) {
        mutex.withLock {
            try {
                // 创建聊天实体
                val chatEntity = ChatEntity.fromChatHistory(history)

                // 保存聊天实体
                chatDao.insertChat(chatEntity)

                // 先删除该聊天的所有现有消息
                messageDao.deleteAllMessagesForChat(chatEntity.id)

                // 批量插入所有消息
                val messageEntities =
                        history.messages.mapIndexed { index, message ->
                            MessageEntity.fromChatMessage(chatEntity.id, message, index)
                        }
                messageDao.insertMessages(messageEntities)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 添加单条消息
    suspend fun addMessage(chatId: String, message: ChatMessage) {
        mutex.withLock {
            try {
                // 获取当前最大序号
                val maxOrderIndex = messageDao.getMaxOrderIndex(chatId) ?: -1

                // 创建消息实体
                val messageEntity =
                        MessageEntity.fromChatMessage(
                                chatId = chatId,
                                message = message,
                                orderIndex = maxOrderIndex + 1
                        )

                // 保存消息
                messageDao.insertMessage(messageEntity)

                // 更新聊天元数据
                val chat = chatDao.getChatById(chatId)
                if (chat != null) {
                    chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = chat.inputTokens,
                            outputTokens = chat.outputTokens
                    )
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 更新现有消息
    suspend fun updateMessage(chatId: String, message: ChatMessage) {
        mutex.withLock {
            try {
                // 找到相应的消息实体
                val existingMessage = messageDao.getMessageByTimestamp(chatId, message.timestamp)

                if (existingMessage != null) {
                    // 更新现有消息
                    messageDao.updateMessageContent(existingMessage.messageId, message.content)

                    // 更新聊天元数据时间戳
                    val chat = chatDao.getChatById(chatId)
                    if (chat != null) {
                        chatDao.updateChatMetadata(
                                chatId = chatId,
                                title = chat.title,
                                timestamp = System.currentTimeMillis(),
                                inputTokens = chat.inputTokens,
                                outputTokens = chat.outputTokens
                        )
                    }
                } else {
                    // 如果找不到现有消息，则添加新消息
                    addMessage(chatId, message)
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 更新聊天标题
    suspend fun updateChatTitle(chatId: String, title: String) {
        mutex.withLock {
            try {
                val chat = chatDao.getChatById(chatId)
                if (chat != null) {
                    chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = chat.inputTokens,
                            outputTokens = chat.outputTokens
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update chat title for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新聊天的token计数
    suspend fun updateChatTokenCounts(chatId: String, inputTokens: Int, outputTokens: Int) {
        mutex.withLock {
            try {
                val chat = chatDao.getChatById(chatId)
                if (chat != null) {
                    chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = inputTokens,
                            outputTokens = outputTokens
                    )
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 设置当前聊天ID
    suspend fun setCurrentChatId(chatId: String) {
        context.currentChatIdDataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_CHAT_ID] = chatId
        }
    }

    // 删除聊天历史
    suspend fun deleteChatHistory(chatId: String) {
        mutex.withLock {
            try {
                // 删除聊天实体（级联删除所有消息）
                chatDao.deleteChat(chatId)

                // 如果删除的是当前聊天，清除当前聊天ID
                val currentChatId = currentChatIdFlow.first()
                if (currentChatId == chatId) {
                    context.currentChatIdDataStore.edit { preferences ->
                        preferences.remove(PreferencesKeys.CURRENT_CHAT_ID)
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 创建新对话
    suspend fun createNewChat(): ChatHistory {
        val dateTime = LocalDateTime.now()
        val formattedTime =
                "${dateTime.hour}:${dateTime.minute.toString().padStart(2, '0')}:${dateTime.second.toString().padStart(2, '0')}"

        val newHistory =
                ChatHistory(
                        title = "新对话 $formattedTime",
                        messages = listOf<ChatMessage>(),
                        inputTokens = 0,
                        outputTokens = 0
                )

        // 保存新聊天
        val chatEntity = ChatEntity.fromChatHistory(newHistory)
        chatDao.insertChat(chatEntity)

        // 设置为当前聊天
        setCurrentChatId(newHistory.id)

        return newHistory
    }

    // 直接加载聊天消息
    suspend fun loadChatMessages(chatId: String): List<ChatMessage> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                // Log.d(TAG, "直接从数据库加载聊天 $chatId 的消息")
                val messages = messageDao.getMessagesForChat(chatId)
                // Log.d(TAG, "聊天 $chatId 共加载 ${messages.size} 条消息")
                messages.map { it.toChatMessage() }
            } catch (e: Exception) {
                Log.e(TAG, "加载聊天消息失败", e)
                emptyList()
            }
        }
    }
}

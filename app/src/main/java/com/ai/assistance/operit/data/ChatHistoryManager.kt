package com.ai.assistance.operit.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.model.ChatHistory
import com.ai.assistance.operit.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import kotlinx.coroutines.flow.catch
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.chatHistoryDataStore by preferencesDataStore(name = "chat_histories")

class ChatHistoryManager(private val context: Context) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = false // Change to false for better performance
    }
    
    // Use a mutex to prevent concurrent modifications
    private val mutex = Mutex()
    
    // Add in-memory cache to reduce reads from DataStore
    private var cachedHistories: List<ChatHistory>? = null
    
    private object PreferencesKeys {
        val CHAT_HISTORIES = stringPreferencesKey("chat_histories")
        val CURRENT_CHAT_ID = stringPreferencesKey("current_chat_id")
    }
    
    // 获取所有聊天历史
    val chatHistoriesFlow: Flow<List<ChatHistory>> = context.chatHistoryDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val historiesJson = preferences[PreferencesKeys.CHAT_HISTORIES] ?: "[]"
            try {
                val histories = json.decodeFromString<List<ChatHistory>>(historiesJson)
                cachedHistories = histories // Update cache with latest data
                histories
            } catch (e: Exception) {
                emptyList<ChatHistory>().also { cachedHistories = it }
            }
        }
    
    // 获取当前聊天ID
    val currentChatIdFlow: Flow<String?> = context.chatHistoryDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CURRENT_CHAT_ID]
        }
    
    // 保存聊天历史
    suspend fun saveChatHistory(history: ChatHistory) {
        // Use mutex to ensure thread safety
        mutex.withLock {
            context.chatHistoryDataStore.edit { preferences ->
                // First try to use cached data to avoid reading from preferences
                val existingHistories = cachedHistories?.toMutableList() ?: run {
                    val existingHistoriesJson = preferences[PreferencesKeys.CHAT_HISTORIES] ?: "[]"
                    try {
                        json.decodeFromString<List<ChatHistory>>(existingHistoriesJson).toMutableList()
                    } catch (e: Exception) {
                        mutableListOf()
                    }
                }
                
                // 查找是否已存在该ID的历史记录
                val existingIndex = existingHistories.indexOfFirst { it.id == history.id }
                
                // 如果存在则更新，不存在则添加
                if (existingIndex != -1) {
                    existingHistories[existingIndex] = history
                } else {
                    existingHistories.add(history) // 添加到列表末尾，而不是列表开头
                }
                
                // Update the cache
                cachedHistories = existingHistories
                
                // Save to preferences
                preferences[PreferencesKeys.CHAT_HISTORIES] = json.encodeToString(existingHistories)
            }
        }
    }
    
    // 判断一个聊天历史是否为空对话（只有系统消息，没有用户交互）
    private fun isEmptyChat(history: ChatHistory): Boolean {
        // 如果有任何用户或AI消息，则不是空对话
        val hasUserOrAIMessage = history.messages.any { it.sender == "user" || it.sender == "ai" }
        return !hasUserOrAIMessage
    }

    // 设置当前聊天ID
    suspend fun setCurrentChatId(chatId: String) {
        context.chatHistoryDataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_CHAT_ID] = chatId
        }
    }
    
    // 创建新对话
    suspend fun createNewChat(): ChatHistory {
        val welcomeMessage = ChatMessage(
            "system",
            "AI Assistant v1.0\n欢迎使用AI助手！请在下方输入您的问题。"
        )
        
        val newHistory = ChatHistory(
            title = "新对话 ${LocalDateTime.now()}",
            messages = listOf(welcomeMessage)
        )
        saveChatHistory(newHistory)
        setCurrentChatId(newHistory.id)
        return newHistory
    }

} 
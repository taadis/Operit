package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

// 全局单例实例
lateinit var preferencesManager: UserPreferencesManager
    private set

fun initUserPreferencesManager(context: Context) {
    preferencesManager = UserPreferencesManager(context)
}

class UserPreferencesManager(private val context: Context) {
    companion object {
        private val PREFERENCES = stringPreferencesKey("preferences")
        private val GENDER = stringPreferencesKey("gender")
        private val OCCUPATION = stringPreferencesKey("occupation")
        private val AGE = intPreferencesKey("age")
        private val IS_INITIALIZED_KEY = booleanPreferencesKey("is_initialized")
    }

    // 获取用户偏好
    val userPreferencesFlow: Flow<UserPreferences> = context.userPreferencesDataStore.data.map { preferences ->
        UserPreferences(
            preferences = preferences[PREFERENCES] ?: "",
            gender = preferences[GENDER] ?: "",
            occupation = preferences[OCCUPATION] ?: "",
            age = preferences[AGE] ?: 0,
            isInitialized = preferences[IS_INITIALIZED_KEY] ?: false
        )
    }

    // 同步检查偏好是否已初始化
    fun isPreferencesInitialized(): Boolean {
        return runBlocking {
            val preferences = context.userPreferencesDataStore.data.map { prefs ->
                prefs[IS_INITIALIZED_KEY] ?: false
            }.first()
            
            preferences
        }
    }

    // 更新用户偏好
    suspend fun updatePreferences(
        preferencesText: String = "",
        gender: String = "",
        occupation: String = "",
        age: Int = 0,
        isInitialized: Boolean = true
    ) {
        context.userPreferencesDataStore.edit { preferences ->
            if (preferencesText.isNotEmpty()) preferences[PREFERENCES] = preferencesText
            if (gender.isNotEmpty()) preferences[GENDER] = gender
            if (occupation.isNotEmpty()) preferences[OCCUPATION] = occupation
            if (age > 0) preferences[AGE] = age
            preferences[IS_INITIALIZED_KEY] = isInitialized
        }
    }

    // 更新偏好描述
    suspend fun updatePreferencesText(text: String) {
        if (text.length <= 200) {  // 确保不超200字
            context.userPreferencesDataStore.edit { preferences ->
                preferences[PREFERENCES] = text
            }
        }
    }

    // 重置用户偏好
    suspend fun resetPreferences() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences.clear()
        }
    }
} 
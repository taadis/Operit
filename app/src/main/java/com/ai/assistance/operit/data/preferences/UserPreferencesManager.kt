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
import com.ai.assistance.operit.data.model.PreferenceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

// 全局单例实例
lateinit var preferencesManager: UserPreferencesManager
    private set

fun initUserPreferencesManager(context: Context) {
    preferencesManager = UserPreferencesManager(context)
    
    // 在后台初始化默认配置
    GlobalScope.launch {
        val profiles = preferencesManager.profileListFlow.first()
        if (profiles.isEmpty() || !profiles.contains("default")) {
            preferencesManager.createProfile("默认配置", isDefault = true)
        }
    }
}

class UserPreferencesManager(private val context: Context) {
    companion object {
        // 基本偏好相关键
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private val PROFILE_LIST = stringPreferencesKey("profile_list")
        
        // 分类锁定状态
        private val AGE_LOCKED = booleanPreferencesKey("age_locked")
        private val PERSONALITY_LOCKED = booleanPreferencesKey("personality_locked")
        private val IDENTITY_LOCKED = booleanPreferencesKey("identity_locked")
        private val OCCUPATION_LOCKED = booleanPreferencesKey("occupation_locked")
        private val AI_STYLE_LOCKED = booleanPreferencesKey("ai_style_locked")
        
        // 兼容旧版本的键
        private val PREFERENCES = stringPreferencesKey("preferences")
        private val GENDER = stringPreferencesKey("gender")
        private val OCCUPATION = stringPreferencesKey("occupation")
        private val AGE = intPreferencesKey("age")
        private val IS_INITIALIZED_KEY = booleanPreferencesKey("is_initialized")
        private val PREFERENCES_LOCKED = booleanPreferencesKey("preferences_locked")
        
        // 默认配置文件ID
        private const val DEFAULT_PROFILE_ID = "default"
    }

    // 获取当前激活的用户偏好配置文件ID
    val activeProfileIdFlow: Flow<String> = context.userPreferencesDataStore.data.map { preferences ->
        preferences[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
    }
    
    // 获取配置文件列表
    val profileListFlow: Flow<List<String>> = context.userPreferencesDataStore.data.map { preferences ->
        val profileListJson = preferences[PROFILE_LIST] ?: "[]"
        try {
            val profileList = Json.decodeFromString<List<String>>(profileListJson).toMutableList()
            // 确保默认配置总是在列表中，即使在存储中不存在
            if (!profileList.contains(DEFAULT_PROFILE_ID)) {
                profileList.add(0, DEFAULT_PROFILE_ID)
            }
            profileList
        } catch (e: Exception) {
            // 如果解析失败，至少返回包含默认配置的列表
            listOf(DEFAULT_PROFILE_ID)
        }
    }
    
    // 获取指定配置文件的用户偏好
    fun getUserPreferencesFlow(profileId: String = ""): Flow<PreferenceProfile> {
        return context.userPreferencesDataStore.data.map { preferences ->
            val targetProfileId = if (profileId.isEmpty()) {
                preferences[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
            } else {
                profileId
            }
            
            val profileKey = stringPreferencesKey("profile_$targetProfileId")
            val profileJson = preferences[profileKey]
            
            if (profileJson != null) {
                try {
                    Json.decodeFromString<PreferenceProfile>(profileJson)
                } catch (e: Exception) {
                    createDefaultProfile(targetProfileId)
                }
            } else {
                // 如果是默认配置且不存在，尝试从旧版本迁移
                if (targetProfileId == DEFAULT_PROFILE_ID) {
                    migrateFromOldPreferences(preferences)
                } else {
                    createDefaultProfile(targetProfileId)
                }
            }
        }
    }
    
    // 从旧版本迁移偏好设置
    private fun migrateFromOldPreferences(preferences: Preferences): PreferenceProfile {
        val oldPreferences = preferences[PREFERENCES] ?: ""
        val oldGender = preferences[GENDER] ?: ""
        val oldOccupation = preferences[OCCUPATION] ?: ""
        val oldAge = preferences[AGE] ?: 0
        val isInitialized = preferences[IS_INITIALIZED_KEY] ?: false
        
        return PreferenceProfile(
            id = DEFAULT_PROFILE_ID,
            name = "默认配置",
            age = oldAge,
            gender = oldGender,
            occupation = oldOccupation,
            personality = "",
            identity = "",
            aiStyle = "",
            isInitialized = isInitialized
        )
    }
    
    // 创建默认的配置文件
    private fun createDefaultProfile(profileId: String): PreferenceProfile {
        return PreferenceProfile(
            id = profileId,
            name = if (profileId == DEFAULT_PROFILE_ID) "默认配置" else profileId,
            age = 0,
            gender = "",
            occupation = "",
            personality = "",
            identity = "",
            aiStyle = "",
            isInitialized = false
        )
    }
    
    // 获取分类锁定状态
    val categoryLockStatusFlow: Flow<Map<String, Boolean>> = context.userPreferencesDataStore.data.map { preferences ->
        mapOf(
            "age" to (preferences[AGE_LOCKED] ?: false),
            "personality" to (preferences[PERSONALITY_LOCKED] ?: false),
            "identity" to (preferences[IDENTITY_LOCKED] ?: false),
            "occupation" to (preferences[OCCUPATION_LOCKED] ?: false),
            "aiStyle" to (preferences[AI_STYLE_LOCKED] ?: false)
        )
    }
    
    // 检查指定分类是否被锁定
    fun isCategoryLocked(category: String): Boolean {
        return runBlocking {
            val lockStatusMap = categoryLockStatusFlow.first()
            lockStatusMap[category] ?: false
        }
    }
    
    // 设置分类锁定状态
    suspend fun setCategoryLocked(category: String, locked: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            when (category) {
                "age" -> preferences[AGE_LOCKED] = locked
                "personality" -> preferences[PERSONALITY_LOCKED] = locked
                "identity" -> preferences[IDENTITY_LOCKED] = locked
                "occupation" -> preferences[OCCUPATION_LOCKED] = locked
                "aiStyle" -> preferences[AI_STYLE_LOCKED] = locked
            }
        }
    }
    
    // 同步检查偏好是否已初始化
    fun isPreferencesInitialized(): Boolean {
        return runBlocking {
            val activeProfile = getUserPreferencesFlow().first()
            activeProfile.isInitialized
        }
    }
    
    // 创建新的配置文件
    suspend fun createProfile(name: String, isDefault: Boolean = false): String {
        val profileId = if (isDefault && name == "默认配置") DEFAULT_PROFILE_ID else "profile_${System.currentTimeMillis()}"
        val newProfile = PreferenceProfile(
            id = profileId,
            name = name,
            age = 0,
            gender = "",
            occupation = "",
            personality = "",
            identity = "",
            aiStyle = "",
            isInitialized = false
        )
        
        context.userPreferencesDataStore.edit { preferences ->
            // 添加到配置文件列表
            val currentList = try {
                val listJson = preferences[PROFILE_LIST] ?: "[]"
                Json.decodeFromString<List<String>>(listJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            
            if (!currentList.contains(profileId)) {
                currentList.add(profileId)
            }
            
            preferences[PROFILE_LIST] = Json.encodeToString(currentList)
            
            // 保存配置文件内容
            val profileKey = stringPreferencesKey("profile_$profileId")
            preferences[profileKey] = Json.encodeToString(newProfile)
        }
        
        return profileId
    }
    
    // 设置激活的配置文件
    suspend fun setActiveProfile(profileId: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[ACTIVE_PROFILE_ID] = profileId
        }
    }
    
    // 更新指定配置文件
    suspend fun updateProfile(profile: PreferenceProfile) {
        context.userPreferencesDataStore.edit { preferences ->
            val profileKey = stringPreferencesKey("profile_${profile.id}")
            preferences[profileKey] = Json.encodeToString(profile)
        }
    }
    
    // 更新配置文件中的特定分类
    suspend fun updateProfileCategory(
        profileId: String = "",
        age: Int? = null,
        gender: String? = null,
        personality: String? = null,
        identity: String? = null,
        occupation: String? = null,
        aiStyle: String? = null
    ) {
        val targetProfileId = if (profileId.isEmpty()) {
            context.userPreferencesDataStore.data.first()[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
        } else {
            profileId
        }
        
        val currentProfile = getUserPreferencesFlow(targetProfileId).first()
        
        // 检查每个分类的锁定状态，如果锁定则不更新
        val updatedProfile = currentProfile.copy(
            age = if (age != null && !isCategoryLocked("age")) age else currentProfile.age,
            gender = if (gender != null && !isCategoryLocked("gender")) gender else currentProfile.gender,
            personality = if (personality != null && !isCategoryLocked("personality")) personality else currentProfile.personality,
            identity = if (identity != null && !isCategoryLocked("identity")) identity else currentProfile.identity,
            occupation = if (occupation != null && !isCategoryLocked("occupation")) occupation else currentProfile.occupation,
            aiStyle = if (aiStyle != null && !isCategoryLocked("aiStyle")) aiStyle else currentProfile.aiStyle,
            isInitialized = true
        )
        
        updateProfile(updatedProfile)
    }
    
    // 删除配置文件
    suspend fun deleteProfile(profileId: String) {
        if (profileId == DEFAULT_PROFILE_ID) {
            // 不允许删除默认配置
            return
        }
        
        context.userPreferencesDataStore.edit { preferences ->
            // 从列表中删除
            val currentList = try {
                val listJson = preferences[PROFILE_LIST] ?: "[]"
                Json.decodeFromString<List<String>>(listJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            
            currentList.remove(profileId)
            preferences[PROFILE_LIST] = Json.encodeToString(currentList)
            
            // 删除配置文件内容
            val profileKey = stringPreferencesKey("profile_$profileId")
            preferences.remove(profileKey)
            
            // 如果当前活动的是被删除的配置文件，则切换到默认配置
            if (preferences[ACTIVE_PROFILE_ID] == profileId) {
                preferences[ACTIVE_PROFILE_ID] = DEFAULT_PROFILE_ID
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
package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.PromptProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.promptPreferencesDataStore by preferencesDataStore(
    name = "prompt_preferences"
)

/**
 * Manager for handling prompt profiles preferences
 */
class PromptPreferencesManager(private val context: Context) {

    private val dataStore = context.promptPreferencesDataStore

    // Keys
    companion object {
        private val PROMPT_PROFILE_LIST = stringSetPreferencesKey("prompt_profile_list")
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_prompt_profile_id")
        
        // Helper function to create profile-specific keys
        private fun profileNameKey(id: String) = stringPreferencesKey("prompt_profile_${id}_name")
        private fun profileIntroPromptKey(id: String) = stringPreferencesKey("prompt_profile_${id}_intro_prompt")
        private fun profileTonePromptKey(id: String) = stringPreferencesKey("prompt_profile_${id}_tone_prompt")
        private fun profileIsDefaultKey(id: String) = booleanPreferencesKey("prompt_profile_${id}_is_default")
    }

    // Default prompt values
    val defaultIntroPrompt = "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。你有各种工具可以调用，以高效完成复杂的请求。"
    val defaultTonePrompt = "保持有帮助的语气，并清楚地传达限制。使用问题库根据用户的风格、偏好和过去的信息个性化响应。"

    // Flow of prompt profile list
    val profileListFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[PROMPT_PROFILE_LIST]?.toList() ?: listOf("default")
    }

    // Flow of active profile ID
    val activeProfileIdFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[ACTIVE_PROFILE_ID] ?: "default"
    }

    // Get prompt profile by ID
    fun getPromptProfileFlow(profileId: String): Flow<PromptProfile> = dataStore.data.map { preferences ->
        val name = preferences[profileNameKey(profileId)] ?: "默认提示词"
        val introPrompt = preferences[profileIntroPromptKey(profileId)] ?: defaultIntroPrompt
        val tonePrompt = preferences[profileTonePromptKey(profileId)] ?: defaultTonePrompt
        val isDefault = preferences[profileIsDefaultKey(profileId)] ?: (profileId == "default")
        val isActive = preferences[ACTIVE_PROFILE_ID] == profileId

        PromptProfile(
            id = profileId,
            name = name,
            introPrompt = introPrompt,
            tonePrompt = tonePrompt,
            isActive = isActive,
            isDefault = isDefault
        )
    }

    // Create a new prompt profile
    suspend fun createProfile(name: String, isDefault: Boolean = false): String {
        val id = if (isDefault) "default" else UUID.randomUUID().toString()

        dataStore.edit { preferences ->
            // Add to profile list if not default (default is always in the list)
            val currentList = preferences[PROMPT_PROFILE_LIST]?.toMutableSet() ?: mutableSetOf("default")
            if (!currentList.contains(id)) {
                currentList.add(id)
                preferences[PROMPT_PROFILE_LIST] = currentList
            }

            // Set profile data
            preferences[profileNameKey(id)] = name
            preferences[profileIntroPromptKey(id)] = defaultIntroPrompt
            preferences[profileTonePromptKey(id)] = defaultTonePrompt
            preferences[profileIsDefaultKey(id)] = isDefault

            // If this is the first profile or is default, make it active
            if (isDefault || preferences[ACTIVE_PROFILE_ID] == null) {
                preferences[ACTIVE_PROFILE_ID] = id
            }
        }

        return id
    }

    // Delete a profile
    suspend fun deleteProfile(profileId: String) {
        // Don't allow deleting the default profile
        if (profileId == "default") return

        dataStore.edit { preferences ->
            // Remove from list
            val currentList = preferences[PROMPT_PROFILE_LIST]?.toMutableSet() ?: mutableSetOf("default")
            currentList.remove(profileId)
            preferences[PROMPT_PROFILE_LIST] = currentList

            // Clear profile data
            preferences.remove(profileNameKey(profileId))
            preferences.remove(profileIntroPromptKey(profileId))
            preferences.remove(profileTonePromptKey(profileId))
            preferences.remove(profileIsDefaultKey(profileId))

            // If this was the active profile, switch to default
            if (preferences[ACTIVE_PROFILE_ID] == profileId) {
                preferences[ACTIVE_PROFILE_ID] = "default"
            }
        }
    }

    // Set active profile
    suspend fun setActiveProfile(profileId: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_PROFILE_ID] = profileId
        }
    }

    // Update prompt profile
    suspend fun updatePromptProfile(
        profileId: String,
        name: String? = null,
        introPrompt: String? = null,
        tonePrompt: String? = null
    ) {
        dataStore.edit { preferences ->
            name?.let { preferences[profileNameKey(profileId)] = it }
            introPrompt?.let { preferences[profileIntroPromptKey(profileId)] = it }
            tonePrompt?.let { preferences[profileTonePromptKey(profileId)] = it }
        }
    }

    // Initialize with default profile if needed
    suspend fun initializeIfNeeded() {
        dataStore.edit { preferences ->
            if (preferences[PROMPT_PROFILE_LIST] == null) {
                preferences[PROMPT_PROFILE_LIST] = setOf("default")
                preferences[ACTIVE_PROFILE_ID] = "default"
                preferences[profileNameKey("default")] = "默认提示词"
                preferences[profileIntroPromptKey("default")] = defaultIntroPrompt
                preferences[profileTonePromptKey("default")] = defaultTonePrompt
                preferences[profileIsDefaultKey("default")] = true
            }
        }
    }
} 
package com.ai.assistance.operit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define the DataStore at the module level
private val Context.apiDataStore: DataStore<Preferences> by preferencesDataStore(name = "api_settings")

class ApiPreferences(private val context: Context) {
    
    // Define our preferences keys
    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val MODEL_NAME = stringPreferencesKey("model_name")
        
        // Default values
        const val DEFAULT_API_ENDPOINT = "https://xuedingmao.online/v1/chat/completions"
        const val DEFAULT_MODEL_NAME = "claude-3-7-sonnet-thinking"
        const val DEFAULT_API_KEY = "sk-vI7omFy9Wvyr25tc7gxKtI3bbRCJt3mQBW5Buvy5qoCx7lC2"
    }
    
    // Get API Key as Flow
    val apiKeyFlow: Flow<String> = context.apiDataStore.data.map { preferences ->
        preferences[API_KEY] ?: DEFAULT_API_KEY
    }
    
    // Get API Endpoint as Flow
    val apiEndpointFlow: Flow<String> = context.apiDataStore.data.map { preferences ->
        preferences[API_ENDPOINT] ?: DEFAULT_API_ENDPOINT
    }
    
    // Get Model Name as Flow
    val modelNameFlow: Flow<String> = context.apiDataStore.data.map { preferences ->
        preferences[MODEL_NAME] ?: DEFAULT_MODEL_NAME
    }
    
    // Save API Key
    suspend fun saveApiKey(apiKey: String) {
        context.apiDataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }
    
    // Save API Endpoint
    suspend fun saveApiEndpoint(endpoint: String) {
        context.apiDataStore.edit { preferences ->
            preferences[API_ENDPOINT] = endpoint
        }
    }
    
    // Save Model Name
    suspend fun saveModelName(modelName: String) {
        context.apiDataStore.edit { preferences ->
            preferences[MODEL_NAME] = modelName
        }
    }
    
    // Save all settings at once
    suspend fun saveApiSettings(apiKey: String, endpoint: String, modelName: String) {
        context.apiDataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
            preferences[API_ENDPOINT] = endpoint
            preferences[MODEL_NAME] = modelName
        }
    }
} 
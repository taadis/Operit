package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define the DataStore at the module level
private val Context.apiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "api_settings")

class ApiPreferences(private val context: Context) {

    // Define our preferences keys
    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val SHOW_THINKING = booleanPreferencesKey("show_thinking")
        val MEMORY_OPTIMIZATION = booleanPreferencesKey("memory_optimization")
        val PREFERENCE_ANALYSIS_INPUT_TOKENS = intPreferencesKey("preference_analysis_input_tokens")
        val PREFERENCE_ANALYSIS_OUTPUT_TOKENS =
                intPreferencesKey("preference_analysis_output_tokens")
        val SHOW_FPS_COUNTER = booleanPreferencesKey("show_fps_counter")
        val ENABLE_AI_PLANNING = booleanPreferencesKey("enable_ai_planning")
        val COLLAPSE_EXECUTION = booleanPreferencesKey("collapse_execution")
        val AUTO_GRANT_ACCESSIBILITY = booleanPreferencesKey("auto_grant_accessibility")

        // Default values
        const val DEFAULT_API_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        // const val DEFAULT_API_ENDPOINT =
        // "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        const val DEFAULT_MODEL_NAME = "deepseek-chat"
        const val DEFAULT_API_KEY = "sk-e565390c164c4cfa8820624ef47d68bf"
        // const val DEFAULT_API_KEY = "sk-bcf2cbfa8c9144119de247db8fdb7af5"
        const val DEFAULT_SHOW_THINKING = true
        const val DEFAULT_MEMORY_OPTIMIZATION = true
        const val DEFAULT_SHOW_FPS_COUNTER = false
        const val DEFAULT_ENABLE_AI_PLANNING = false
        const val DEFAULT_COLLAPSE_EXECUTION = true
        const val DEFAULT_AUTO_GRANT_ACCESSIBILITY = true
    }

    // Get API Key as Flow
    val apiKeyFlow: Flow<String> =
            context.apiDataStore.data.map { preferences -> preferences[API_KEY] ?: DEFAULT_API_KEY }

    // Get API Endpoint as Flow
    val apiEndpointFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[API_ENDPOINT] ?: DEFAULT_API_ENDPOINT
            }

    // Get Model Name as Flow
    val modelNameFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[MODEL_NAME] ?: DEFAULT_MODEL_NAME
            }

    // Get Show Thinking as Flow
    val showThinkingFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[SHOW_THINKING] ?: DEFAULT_SHOW_THINKING
            }

    // Get Memory Optimization as Flow
    val memoryOptimizationFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[MEMORY_OPTIMIZATION] ?: DEFAULT_MEMORY_OPTIMIZATION
            }

    // 获取偏好分析输入token计数
    val preferenceAnalysisInputTokensFlow: Flow<Int> =
            context.apiDataStore.data.map { preferences ->
                preferences[PREFERENCE_ANALYSIS_INPUT_TOKENS] ?: 0
            }

    // 获取偏好分析输出token计数
    val preferenceAnalysisOutputTokensFlow: Flow<Int> =
            context.apiDataStore.data.map { preferences ->
                preferences[PREFERENCE_ANALYSIS_OUTPUT_TOKENS] ?: 0
            }

    // Get FPS Counter Display setting as Flow
    val showFpsCounterFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[SHOW_FPS_COUNTER] ?: DEFAULT_SHOW_FPS_COUNTER
            }

    // Get AI Planning setting as Flow
    val enableAiPlanningFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[ENABLE_AI_PLANNING] ?: DEFAULT_ENABLE_AI_PLANNING
            }

    // Get Collapse Execution setting as Flow
    val collapseExecutionFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[COLLAPSE_EXECUTION] ?: DEFAULT_COLLAPSE_EXECUTION
            }

    // Get Auto Grant Accessibility setting as Flow
    val autoGrantAccessibilityFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[AUTO_GRANT_ACCESSIBILITY] ?: DEFAULT_AUTO_GRANT_ACCESSIBILITY
            }

    // Save API Key
    suspend fun saveApiKey(apiKey: String) {
        context.apiDataStore.edit { preferences -> preferences[API_KEY] = apiKey }
    }

    // Save API Endpoint
    suspend fun saveApiEndpoint(endpoint: String) {
        context.apiDataStore.edit { preferences -> preferences[API_ENDPOINT] = endpoint }
    }

    // Save Model Name
    suspend fun saveModelName(modelName: String) {
        context.apiDataStore.edit { preferences -> preferences[MODEL_NAME] = modelName }
    }

    // Save Show Thinking setting
    suspend fun saveShowThinking(showThinking: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[SHOW_THINKING] = showThinking }
    }

    // Save Memory Optimization setting
    suspend fun saveMemoryOptimization(memoryOptimization: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[MEMORY_OPTIMIZATION] = memoryOptimization
        }
    }

    // Save FPS Counter Display setting
    suspend fun saveShowFpsCounter(showFpsCounter: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[SHOW_FPS_COUNTER] = showFpsCounter }
    }

    // Save AI Planning setting
    suspend fun saveEnableAiPlanning(enablePlanning: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[ENABLE_AI_PLANNING] = enablePlanning
        }
    }

    // Save Collapse Execution setting
    suspend fun saveCollapseExecution(collapseExecution: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[COLLAPSE_EXECUTION] = collapseExecution
        }
    }

    // Save Auto Grant Accessibility setting
    suspend fun saveAutoGrantAccessibility(autoGrantAccessibility: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[AUTO_GRANT_ACCESSIBILITY] = autoGrantAccessibility
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

    // Update the saveAllSettings method to include the Auto Grant Accessibility setting
    suspend fun saveAllSettings(
            apiKey: String,
            endpoint: String,
            modelName: String,
            showThinking: Boolean,
            memoryOptimization: Boolean,
            showFpsCounter: Boolean,
            enableAiPlanning: Boolean,
            collapseExecution: Boolean,
            autoGrantAccessibility: Boolean = DEFAULT_AUTO_GRANT_ACCESSIBILITY
    ) {
        context.apiDataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
            preferences[API_ENDPOINT] = endpoint
            preferences[MODEL_NAME] = modelName
            preferences[SHOW_THINKING] = showThinking
            preferences[MEMORY_OPTIMIZATION] = memoryOptimization
            preferences[SHOW_FPS_COUNTER] = showFpsCounter
            preferences[ENABLE_AI_PLANNING] = enableAiPlanning
            preferences[COLLAPSE_EXECUTION] = collapseExecution
            preferences[AUTO_GRANT_ACCESSIBILITY] = autoGrantAccessibility
        }
    }

    // 更新偏好分析token计数
    suspend fun updatePreferenceAnalysisTokens(inputTokens: Int, outputTokens: Int) {
        context.apiDataStore.edit { preferences ->
            // 累加而不是覆盖
            val currentInputTokens = preferences[PREFERENCE_ANALYSIS_INPUT_TOKENS] ?: 0
            val currentOutputTokens = preferences[PREFERENCE_ANALYSIS_OUTPUT_TOKENS] ?: 0

            preferences[PREFERENCE_ANALYSIS_INPUT_TOKENS] = currentInputTokens + inputTokens
            preferences[PREFERENCE_ANALYSIS_OUTPUT_TOKENS] = currentOutputTokens + outputTokens
        }
    }

    // 重置偏好分析token计数
    suspend fun resetPreferenceAnalysisTokens() {
        context.apiDataStore.edit { preferences ->
            preferences[PREFERENCE_ANALYSIS_INPUT_TOKENS] = 0
            preferences[PREFERENCE_ANALYSIS_OUTPUT_TOKENS] = 0
        }
    }
}

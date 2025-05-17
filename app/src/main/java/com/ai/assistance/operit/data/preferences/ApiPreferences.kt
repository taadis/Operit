package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
        val AUTO_GRANT_ACCESSIBILITY = booleanPreferencesKey("auto_grant_accessibility")

        // Custom Prompt Settings
        val CUSTOM_INTRO_PROMPT = stringPreferencesKey("custom_intro_prompt")
        val CUSTOM_TONE_PROMPT = stringPreferencesKey("custom_tone_prompt")

        // DeepSeek Model Parameters
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val TOP_K = intPreferencesKey("top_k")
        val PRESENCE_PENALTY = floatPreferencesKey("presence_penalty")
        val FREQUENCY_PENALTY = floatPreferencesKey("frequency_penalty")
        val REPETITION_PENALTY = floatPreferencesKey("repetition_penalty")

        // Default values
        const val DEFAULT_API_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_MODEL_NAME = "deepseek-chat"
        const val DEFAULT_API_KEY = "sk-e565390c164c4cfa8820624ef47d68bf"
        const val DEFAULT_SHOW_THINKING = true
        const val DEFAULT_MEMORY_OPTIMIZATION = true
        const val DEFAULT_SHOW_FPS_COUNTER = false
        const val DEFAULT_ENABLE_AI_PLANNING = false
        const val DEFAULT_AUTO_GRANT_ACCESSIBILITY = false

        // Default values for custom prompts
        const val DEFAULT_INTRO_PROMPT = "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。你有各种工具可以调用，以高效完成复杂的请求。"
        const val DEFAULT_TONE_PROMPT = "保持有帮助的语气，并清楚地传达限制。使用问题库根据用户的风格、偏好和过去的信息个性化响应。"

        // Default values for DeepSeek model parameters
        const val DEFAULT_MAX_TOKENS = 4096
        const val DEFAULT_TEMPERATURE = 1.0f
        const val DEFAULT_TOP_P = 1.0f
        const val DEFAULT_TOP_K = 0
        const val DEFAULT_PRESENCE_PENALTY = 0.0f
        const val DEFAULT_FREQUENCY_PENALTY = 0.0f
        const val DEFAULT_REPETITION_PENALTY = 1.0f
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

    // DeepSeek Model Parameter Flows
    val maxTokensFlow: Flow<Int> =
            context.apiDataStore.data.map { preferences ->
                preferences[MAX_TOKENS] ?: DEFAULT_MAX_TOKENS
            }

    val temperatureFlow: Flow<Float> =
            context.apiDataStore.data.map { preferences ->
                preferences[TEMPERATURE] ?: DEFAULT_TEMPERATURE
            }

    val topPFlow: Flow<Float> =
            context.apiDataStore.data.map { preferences -> preferences[TOP_P] ?: DEFAULT_TOP_P }

    val topKFlow: Flow<Int> =
            context.apiDataStore.data.map { preferences -> preferences[TOP_K] ?: DEFAULT_TOP_K }

    val presencePenaltyFlow: Flow<Float> =
            context.apiDataStore.data.map { preferences ->
                preferences[PRESENCE_PENALTY] ?: DEFAULT_PRESENCE_PENALTY
            }

    val frequencyPenaltyFlow: Flow<Float> =
            context.apiDataStore.data.map { preferences ->
                preferences[FREQUENCY_PENALTY] ?: DEFAULT_FREQUENCY_PENALTY
            }

    val repetitionPenaltyFlow: Flow<Float> =
            context.apiDataStore.data.map { preferences ->
                preferences[REPETITION_PENALTY] ?: DEFAULT_REPETITION_PENALTY
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

    // Get Auto Grant Accessibility setting as Flow
    val autoGrantAccessibilityFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[AUTO_GRANT_ACCESSIBILITY] ?: DEFAULT_AUTO_GRANT_ACCESSIBILITY
            }

    // Custom Prompt Flows
    val customIntroPromptFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[CUSTOM_INTRO_PROMPT] ?: DEFAULT_INTRO_PROMPT
            }

    val customTonePromptFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[CUSTOM_TONE_PROMPT] ?: DEFAULT_TONE_PROMPT
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

    // Save DeepSeek Model Parameters
    suspend fun saveMaxTokens(maxTokens: Int) {
        context.apiDataStore.edit { preferences -> preferences[MAX_TOKENS] = maxTokens }
    }

    suspend fun saveTemperature(temperature: Float) {
        context.apiDataStore.edit { preferences -> preferences[TEMPERATURE] = temperature }
    }

    suspend fun saveTopP(topP: Float) {
        context.apiDataStore.edit { preferences -> preferences[TOP_P] = topP }
    }

    suspend fun saveTopK(topK: Int) {
        context.apiDataStore.edit { preferences -> preferences[TOP_K] = topK }
    }

    suspend fun savePresencePenalty(presencePenalty: Float) {
        context.apiDataStore.edit { preferences -> preferences[PRESENCE_PENALTY] = presencePenalty }
    }

    suspend fun saveFrequencyPenalty(frequencyPenalty: Float) {
        context.apiDataStore.edit { preferences ->
            preferences[FREQUENCY_PENALTY] = frequencyPenalty
        }
    }

    suspend fun saveRepetitionPenalty(repetitionPenalty: Float) {
        context.apiDataStore.edit { preferences ->
            preferences[REPETITION_PENALTY] = repetitionPenalty
        }
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

    // Update the saveAllSettings method to include all model parameters
    suspend fun saveAllSettings(
            apiKey: String,
            endpoint: String,
            modelName: String,
            showThinking: Boolean,
            memoryOptimization: Boolean,
            showFpsCounter: Boolean,
            enableAiPlanning: Boolean,
            autoGrantAccessibility: Boolean = DEFAULT_AUTO_GRANT_ACCESSIBILITY,
            maxTokens: Int = DEFAULT_MAX_TOKENS,
            temperature: Float = DEFAULT_TEMPERATURE,
            topP: Float = DEFAULT_TOP_P,
            topK: Int = DEFAULT_TOP_K,
            presencePenalty: Float = DEFAULT_PRESENCE_PENALTY,
            frequencyPenalty: Float = DEFAULT_FREQUENCY_PENALTY,
            repetitionPenalty: Float = DEFAULT_REPETITION_PENALTY
    ) {
        context.apiDataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
            preferences[API_ENDPOINT] = endpoint
            preferences[MODEL_NAME] = modelName
            preferences[SHOW_THINKING] = showThinking
            preferences[MEMORY_OPTIMIZATION] = memoryOptimization
            preferences[SHOW_FPS_COUNTER] = showFpsCounter
            preferences[ENABLE_AI_PLANNING] = enableAiPlanning
            preferences[AUTO_GRANT_ACCESSIBILITY] = autoGrantAccessibility
            preferences[MAX_TOKENS] = maxTokens
            preferences[TEMPERATURE] = temperature
            preferences[TOP_P] = topP
            preferences[TOP_K] = topK
            preferences[PRESENCE_PENALTY] = presencePenalty
            preferences[FREQUENCY_PENALTY] = frequencyPenalty
            preferences[REPETITION_PENALTY] = repetitionPenalty
        }
    }

    // Save model parameters only
    suspend fun saveModelParameters(
            maxTokens: Int = DEFAULT_MAX_TOKENS,
            temperature: Float = DEFAULT_TEMPERATURE,
            topP: Float = DEFAULT_TOP_P,
            topK: Int = DEFAULT_TOP_K,
            presencePenalty: Float = DEFAULT_PRESENCE_PENALTY,
            frequencyPenalty: Float = DEFAULT_FREQUENCY_PENALTY,
            repetitionPenalty: Float = DEFAULT_REPETITION_PENALTY
    ) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_TOKENS] = maxTokens
            preferences[TEMPERATURE] = temperature
            preferences[TOP_P] = topP
            preferences[TOP_K] = topK
            preferences[PRESENCE_PENALTY] = presencePenalty
            preferences[FREQUENCY_PENALTY] = frequencyPenalty
            preferences[REPETITION_PENALTY] = repetitionPenalty
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

    // Save custom prompts
    suspend fun saveCustomPrompts(introPrompt: String, tonePrompt: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_INTRO_PROMPT] = introPrompt
            preferences[CUSTOM_TONE_PROMPT] = tonePrompt
        }
    }

    // Reset custom prompts to default values
    suspend fun resetCustomPrompts() {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_INTRO_PROMPT] = DEFAULT_INTRO_PROMPT
            preferences[CUSTOM_TONE_PROMPT] = DEFAULT_TONE_PROMPT
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

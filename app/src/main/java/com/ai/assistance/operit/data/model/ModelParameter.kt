package com.ai.assistance.operit.data.model

/**
 * Represents a configurable parameter for AI model requests
 * @param id Unique identifier for the parameter
 * @param name Human-readable name of the parameter
 * @param apiName The name of the parameter in the API request
 * @param description Description of what the parameter does
 * @param defaultValue Default value for the parameter
 * @param currentValue Current value of the parameter
 * @param isEnabled Whether the parameter will be included in API requests
 * @param valueType The type of value (INT, FLOAT, STRING)
 * @param minValue Minimum allowed value (for numeric parameters)
 * @param maxValue Maximum allowed value (for numeric parameters)
 * @param icon Icon representing this parameter type (optional)
 * @param category The category this parameter belongs to (for UI grouping)
 * @param isCustom Whether this parameter is user-defined custom parameter
 */
data class ModelParameter<T>(
        val id: String,
        val name: String,
        val apiName: String,
        val description: String,
        val defaultValue: T,
        var currentValue: T,
        var isEnabled: Boolean = false,
        val valueType: ParameterValueType,
        val minValue: T? = null,
        val maxValue: T? = null,
        val icon: String? = null,
        val category: ParameterCategory = ParameterCategory.GENERATION,
        val isCustom: Boolean = false
)

/** The type of value for a parameter */
enum class ParameterValueType {
    INT,
    FLOAT,
    STRING,
    BOOLEAN
}

/** Categories for model parameters to group them in the UI */
enum class ParameterCategory {
    GENERATION, // Parameters related to text generation amount (tokens, length)
    CREATIVITY, // Parameters affecting randomness/creativity (temperature, top_p)
    REPETITION, // Parameters controlling repetition behavior
    ADVANCED // Other advanced parameters
}

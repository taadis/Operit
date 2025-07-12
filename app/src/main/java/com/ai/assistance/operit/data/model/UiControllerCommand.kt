package com.ai.assistance.operit.data.model

/**
 * Represents a single command for UI automation.
 * @param type The type of action to perform (e.g., "tap", "swipe", "complete").
 * @param arg The arguments for the action. This can be a Map for complex arguments (like coordinates) or a String for simple ones.
 */
data class UiControllerCommand(
    val type: String,
    val arg: Any
) 
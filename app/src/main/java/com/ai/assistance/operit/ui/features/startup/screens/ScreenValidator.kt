package com.ai.assistance.operit.ui.features.startup.screens

import androidx.compose.runtime.Composable

/**
 * This is a simple validator class to ensure that all the screen functions are correctly defined
 * and accessible.
 */
object ScreenValidator {

    @Composable
    fun ValidatePluginLoadingScreen() {
        // Validate that PluginLoadingScreenWithState is correctly defined and accessible
        val state = PluginLoadingState()
        PluginLoadingScreenWithState(loadingState = state)
    }
}

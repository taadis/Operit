package com.ai.assistance.operit.ui.main.screens

// Hierarchical screen representation to replace multiple boolean flags
sealed class Screen {
    // Main screens (primary)
    data object AiChat : Screen()
    data object ProblemLibrary : Screen()
    data object Packages : Screen()
    data object Toolbox : Screen()
    data object ShizukuCommands : Screen()
    data object Settings : Screen()
    data object Help : Screen()
    data object About : Screen()
    data object TokenConfig : Screen() // New screen for token configuration

    // Secondary screens
    data object ToolPermission : Screen()
    data object UserPreferencesGuide : Screen() {
        var profileName: String = ""
        var profileId: String = ""
    }
    data object UserPreferencesSettings : Screen()
    data object ModelParametersSettings : Screen()
    data object ModelPromptsSettings : Screen() // Add new ModelPromptsSettings screen
    data object ThemeSettings : Screen() // Add new ThemeSettings screen

    // Toolbox secondary screens
    data object FormatConverter : Screen()
    data object FileManager : Screen()
    data object Terminal : Screen()
    data object TerminalAutoConfig : Screen()
    data object AppPermissions : Screen()
}
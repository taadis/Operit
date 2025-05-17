sealed class SettingsDestination(val route: String) {
    object Settings : SettingsDestination("settings")
    object UserPreferencesSettings : SettingsDestination("user_preferences_settings")
    object ToolPermissionSettings : SettingsDestination("tool_permission_settings")
    object ModelParametersSettings : SettingsDestination("model_parameters_settings")
    object ThemeSettings : SettingsDestination("theme_settings")
}

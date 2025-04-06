package com.ai.assistance.operit.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Face
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R

// 应用导航项
sealed class NavItem(val route: String, val titleResId: Int, val icon: ImageVector) {
    object AiChat : NavItem("ai_chat", R.string.nav_ai_chat, Icons.Default.Email)
    object ShizukuCommands : NavItem("shizuku_commands", R.string.shizuku_commands, Icons.Default.Build)
    object Settings : NavItem("settings", R.string.nav_settings, Icons.Default.Settings)
    object ToolPermissions : NavItem("tool_permissions", R.string.tool_permissions, Icons.Default.Security)
    object UserPreferencesGuide : NavItem("user_preferences_guide", R.string.user_preferences_guide, Icons.Default.Person)
    object UserPreferencesSettings : NavItem("user_preferences_settings", R.string.user_preferences_settings, Icons.Default.Face)
    object Packages : NavItem("packages", R.string.nav_packages, Icons.Default.Extension)
    object ProblemLibrary : NavItem("problem_library", R.string.nav_problem_library, Icons.Default.Storage)
    object Terminal : NavItem("terminal", R.string.terminal, Icons.Default.Terminal)
    object About : NavItem("about", R.string.nav_about, Icons.Default.Info)
} 
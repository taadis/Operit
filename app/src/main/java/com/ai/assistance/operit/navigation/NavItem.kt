package com.ai.assistance.operit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R

// 应用导航项
sealed class NavItem(val route: String, val titleResId: Int, val icon: ImageVector) {
    object AiChat : NavItem("ai_chat", R.string.nav_ai_chat, Icons.Default.Email)
    object ShizukuCommands : NavItem("shizuku_commands", R.string.nav_adb_commands, Icons.Default.Build)
    object Settings : NavItem("settings", R.string.nav_settings, Icons.Default.Settings)
} 
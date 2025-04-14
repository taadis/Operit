package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.features.toolbox.screens.apppermissions.AppPermissionsScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.FileManagerScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.screens.TerminalScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.terminalconfig.TerminalAutoConfigScreen

data class Tool(
        val name: String,
        val icon: ImageVector,
        val description: String,
        val onClick: () -> Unit
)

/** 工具箱屏幕，展示可用的各种工具 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(
        navController: NavController,
        onFormatConverterSelected: () -> Unit,
        onFileManagerSelected: () -> Unit,
        onTerminalSelected: () -> Unit,
        onTerminalAutoConfigSelected: () -> Unit,
        onAppPermissionsSelected: () -> Unit
) {
    val tools =
            listOf(
                    Tool(
                            name = "万能格式转换",
                            icon = Icons.Default.Transform,
                            description = "支持多种文件格式之间的转换",
                            onClick = onFormatConverterSelected
                    ),
                    Tool(
                            name = "文件管理器",
                            icon = Icons.Default.Folder,
                            description = "浏览和管理设备文件",
                            onClick = onFileManagerSelected
                    ),
                    Tool(
                            name = "命令终端",
                            icon = Icons.Default.Terminal,
                            description = "工具箱内置的命令行终端",
                            onClick = onTerminalSelected
                    ),
                    Tool(
                            name = "终端自动配置",
                            icon = Icons.Default.Build,
                            description = "自动安装配置Python、PIP等开发工具",
                            onClick = onTerminalAutoConfigSelected
                    ),
                    Tool(
                            name = "应用权限管理",
                            icon = Icons.Default.Security,
                            description = "管理手机各个应用的权限情况",
                            onClick = onAppPermissionsSelected
                    )
            )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
        ) { items(tools) { tool -> ToolCard(tool = tool) } }
    }
}

/** 显示格式转换工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatConverterToolScreen(navController: NavController) {
    Scaffold() { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            FormatConverterScreen(navController = navController)
        }
    }
}

/** 显示文件管理器工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerToolScreen(navController: NavController) {
    Scaffold() { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            FileManagerScreen(navController = navController)
        }
    }
}

/** 显示终端工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalToolScreen(navController: NavController) {
    Scaffold() { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) { TerminalScreen() }
    }
}

/** 显示终端自动配置工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAutoConfigToolScreen(navController: NavController) {
    Scaffold() { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            TerminalAutoConfigScreen(navController = navController)
        }
    }
}

/** 显示应用权限管理工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionsToolScreen(navController: NavController) {
    Scaffold() { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AppPermissionsScreen(navController = navController)
        }
    }
}

/** 工具项卡片 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(tool: Tool) {
    Card(
            onClick = tool.onClick,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.name,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

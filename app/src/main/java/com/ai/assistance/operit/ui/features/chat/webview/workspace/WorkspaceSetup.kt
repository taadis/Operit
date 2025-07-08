package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.chat.webview.createAndGetDefaultWorkspace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource

/**
 * VSCode风格的工作区设置组件
 * 用于初始绑定工作区
 */
@Composable
fun WorkspaceSetup(chatId: String, onBindWorkspace: (String) -> Unit) {
    val context = LocalContext.current
    var showFileBrowser by remember { mutableStateOf(false) }

    if (showFileBrowser) {
        FileBrowser(
            initialPath = "/sdcard/Download/", // 默认下载目录
            onBindWorkspace = onBindWorkspace,
            onCancel = { showFileBrowser = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // 移除点击时的涟漪效果
                    enabled = true,
                    onClick = {}
                ) // 添加点击拦截
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // VSCode风格的图标
            Icon(
                imageVector = Icons.Default.Widgets, // 使用更通用的图标
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "设置工作区", // 更改标题
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "为您的AI项目提供一个专属的文件环境",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // VSCode风格的选项卡
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkspaceOption(
                    icon = Icons.Default.CreateNewFolder,
                    title = "创建默认",
                    description = "在应用内创建新工作区",
                    onClick = {
                        val workspaceDir = createAndGetDefaultWorkspace(context, chatId)
                        onBindWorkspace(workspaceDir.absolutePath)
                    }
                )
                
                WorkspaceOption(
                    icon = Icons.Default.FolderOpen,
                    title = "选择现有",
                    description = "从设备中选择一个文件夹",
                    onClick = { showFileBrowser = true }
                )
            }
        }
    }
}

/**
 * 工作区选项卡组件
 */
@Composable
fun WorkspaceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp) // 调整大小
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp)) // 更圆的角
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp, // 移除阴影
            pressedElevation = 0.dp
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
} 
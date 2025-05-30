package com.ai.assistance.operit.ui.features.chat.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.permissions.PermissionLevel

@Composable
fun ChatScreenHeader(
    actualViewModel: ChatViewModel,
    showChatHistorySelector: Boolean,
    chatHistories: List<ChatHistory>,
    currentChatId: String,
    isEditMode: MutableState<Boolean>,
    showWebView: Boolean = false,
    onWebDevClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentChatTitle = chatHistories.find { it.id == currentChatId }?.title

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 左侧：聊天历史按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChatHeader(
                showChatHistorySelector = showChatHistorySelector,
                onToggleChatHistorySelector = {
                    actualViewModel.toggleChatHistorySelector()
                },
                currentChatTitle = currentChatTitle,
                modifier = Modifier,
                isFloatingMode = actualViewModel.isFloatingMode.value,
                onLaunchFloatingWindow = {
                    // Check if we can draw overlays first
                    if (!Settings.canDrawOverlays(context)) {
                        // Show message to user
                        android.widget.Toast.makeText(
                            context,
                            "需要悬浮窗权限。请前往设置授予权限",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                        // Launch settings to grant permission
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        // Toggle floating mode
                        actualViewModel.toggleFloatingMode()

                        // 根据当前悬浮窗状态显示不同的提示
                        val isFloating = actualViewModel.isFloatingMode.value
                        val message = if (isFloating) "悬浮窗已开启" else "悬浮窗已关闭"
                        android.widget.Toast.makeText(
                            context,
                            message,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                showWebView = showWebView,
                onWebDevClick = onWebDevClick
            )

            // 添加编辑按钮 - 使用与悬浮窗按钮相同的样式
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (isEditMode.value)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                IconButton(
                    onClick = {
                        isEditMode.value = !isEditMode.value
                        if (!isEditMode.value) {
                            // 退出编辑模式时清空状态
                            // 直接在这里更新会引起组件内循环依赖，需要通过回调通知父组件
                        }
                    },
                    modifier = Modifier.matchParentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = if (isEditMode.value) "退出编辑模式" else "进入编辑模式",
                        tint = if (isEditMode.value)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 右侧：统计信息
        val contextWindowSize = actualViewModel.contextWindowSize.value
        val inputTokenCount = actualViewModel.inputTokenCount.value
        val outputTokenCount = actualViewModel.outputTokenCount.value

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 统计项 - 使用水平排列的小标签
            StatItem(label = "请求", value = "$contextWindowSize")

            StatItem(label = "累计入", value = "$inputTokenCount")

            Divider(
                modifier = Modifier.height(16.dp).width(1.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )

            StatItem(label = "累计出", value = "$outputTokenCount")

            Divider(
                modifier = Modifier.height(16.dp).width(1.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )

            StatItem(
                label = "总计",
                value = "${inputTokenCount + outputTokenCount}",
                isHighlighted = true
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    isHighlighted: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isHighlighted)
                    androidx.compose.ui.text.font.FontWeight.Bold
                else androidx.compose.ui.text.font.FontWeight.Normal
            ),
            color = if (isHighlighted) 
                MaterialTheme.colorScheme.primary
            else 
                MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ChatSettingsBar(
    actualViewModel: ChatViewModel,
    memoryOptimization: Boolean,
    masterPermissionLevel: PermissionLevel,
    enableAiPlanning: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 自动批准开关 - 左侧第一个开关
        Row(
            modifier = Modifier
                .background(
                    color = if (masterPermissionLevel == PermissionLevel.ALLOW)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .clickable {
                    actualViewModel.toggleMasterPermission()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "自动批准:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = if (masterPermissionLevel == PermissionLevel.ALLOW) "已开启" else "询问",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = if (masterPermissionLevel == PermissionLevel.ALLOW)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // AI计划模式开关 - 更详细的文本
        Row(
            modifier = Modifier
                .background(
                    color = if (enableAiPlanning)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .clickable {
                    actualViewModel.toggleAiPlanning()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "AI计划模式:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = if (enableAiPlanning) "已开启" else "已关闭",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = if (enableAiPlanning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
} 
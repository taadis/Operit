package com.ai.assistance.operit.ui.features.terminal.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.terminal.model.TerminalSessionManager
import com.ai.assistance.operit.ui.features.terminal.utils.TerminalColors

/**
 * 会话选项卡行
 */
@Composable
fun SessionTabsRow(
    sessionManager: TerminalSessionManager,
    onAddSession: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // 显示所有会话选项卡
        items(sessionManager.sessions) { session ->
            val isActive = sessionManager.activeSessionId.value == session.id
            val sessionHasRootUser = session.currentUser == "root"
            
            Card(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable {
                        sessionManager.switchSession(session.id)
                    }
                    .widthIn(min = 120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isActive && sessionHasRootUser -> TerminalColors.ParrotRed // Root会话红色
                        isActive -> TerminalColors.ParrotAccent.copy(alpha = 0.2f) // 活动会话的青蓝色背景
                        sessionHasRootUser -> TerminalColors.ParrotRedDark.copy(alpha = 0.7f) // 非活动Root会话深红色
                        else -> TerminalColors.ParrotBgLight
                    }
                ),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isActive) 4.dp else 1.dp
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isActive) TerminalColors.ParrotAccent else TerminalColors.ParrotAccent.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (sessionHasRootUser) "${session.name} (root)" else session.name,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isActive && sessionHasRootUser -> Color.White
                            isActive -> Color.White
                            sessionHasRootUser -> Color.White.copy(alpha = 0.8f)
                            else -> TerminalColors.ParrotAccent.copy(alpha = 0.7f)
                        }
                    )
                    
                    // 如果有多个会话，显示关闭按钮
                    if (sessionManager.getSessionCount() > 1) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    sessionManager.closeSession(session.id)
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭会话",
                                    tint = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 添加新会话按钮
        item {
            Card(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { onAddSession() },
                colors = CardDefaults.cardColors(
                    containerColor = TerminalColors.ParrotBgLight.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = TerminalColors.ParrotAccent.copy(alpha = 0.3f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加新会话",
                        tint = TerminalColors.ParrotAccent
                    )
                }
            }
        }
    }
}
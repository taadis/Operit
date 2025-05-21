package com.ai.assistance.operit.ui.features.demo.wizards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShizukuWizardCard(
        isShizukuInstalled: Boolean,
        isShizukuRunning: Boolean,
        hasShizukuPermission: Boolean,
        showWizard: Boolean,
        onToggleWizard: (Boolean) -> Unit,
        onInstallFromStore: () -> Unit,
        onInstallBundled: () -> Unit,
        onOpenShizuku: () -> Unit,
        onWatchTutorial: () -> Unit,
        onRequestPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            "Shizuku 设置向导",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(
                        onClick = { onToggleWizard(!showWizard) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                ) { 
                    Text(
                        if (showWizard) "收起" else "展开", 
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    ) 
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 进度和状态
            val isComplete = isShizukuInstalled && isShizukuRunning && hasShizukuPermission
            
            Surface(
                color = if (isComplete) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 进度指示器
                    LinearProgressIndicator(
                        progress = when {
                            !isShizukuInstalled -> 0f
                            !isShizukuRunning -> 0.33f
                            !hasShizukuPermission -> 0.66f
                            else -> 1f
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 当前状态
                    val statusText = when {
                        !isShizukuInstalled -> "步骤1：安装 Shizuku 应用"
                        !isShizukuRunning -> "步骤2：启动 Shizuku 服务"
                        !hasShizukuPermission -> "步骤3：授予 Shizuku 权限"
                        else -> "Shizuku 已完全设置"
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isComplete) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isComplete) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 详细设置内容，仅在展开时显示
            if (showWizard) {
                Spacer(modifier = Modifier.height(16.dp))
                
                when {
                    // 第一步：安装Shizuku
                    !isShizukuInstalled -> {
                        Column {
                            Text(
                                "Shizuku是一个授予应用高级权限的工具，不需要root。我们需要先安装这个应用。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ElevatedButton(
                                    onClick = onInstallBundled,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { 
                                    Text("安装内置版本", fontSize = 14.sp) 
                                }
                            }
                        }
                    }

                    // 第二步：启动Shizuku服务
                    !isShizukuRunning -> {
                        Column {
                            Text(
                                "Shizuku已安装，现在需要启动Shizuku服务。请按照以下方法之一操作：",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "方法1：使用应用内启动（推荐）",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = "1. 点击下方\"打开Shizuku\"按钮\n" +
                                              "2. 在Shizuku应用中选择\"通过无线调试启动\"\n" +
                                              "3. 根据提示开启\"开发者选项\"和\"无线调试\"\n" +
                                              "4. 允许Shizuku获取无线调试权限",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "方法2：使用USB启动",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = "1. 在电脑上安装ADB工具\n" +
                                              "2. 通过USB连接手机和电脑\n" +
                                              "3. 在电脑命令行执行：\n" +
                                              "   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onWatchTutorial,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { 
                                    Text("官方文档", fontSize = 14.sp) 
                                }
                                
                                FilledTonalButton(
                                    onClick = onOpenShizuku,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { 
                                    Text("打开Shizuku", fontSize = 14.sp) 
                                }
                            }
                        }
                    }

                    // 第三步：授予权限
                    !hasShizukuPermission -> {
                        Column {
                            Text(
                                "Shizuku服务已启动，现在需要授予权限给本应用。点击下方按钮，在弹出的对话框中选择\"允许\"。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "注意：如果没有看到授权弹窗，请检查：",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "1. Shizuku服务是否正在运行\n" +
                                              "2. 重启Shizuku服务后再尝试\n" +
                                              "3. 确认允许显示悬浮窗权限",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                    onClick = onRequestPermission,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) { 
                                Text("授予Shizuku权限", fontSize = 14.sp) 
                            }
                        }
                    }

                    // 全部完成
                    else -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Text(
                                    "恭喜！Shizuku已完全设置，您现在可以使用调试权限功能。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

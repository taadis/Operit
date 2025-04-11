package com.ai.assistance.operit.ui.features.help.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HelpScreen(onBackPressed: () -> Unit = {}) {
        Column(
                modifier =
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
        ) {
                // 标题部分
                Text(
                        text = "Operit AI 使用指南",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                // 介绍
                Text(
                        text = "Operit AI 是一款智能助手应用，可以帮助您更高效地使用设备，理解和分析屏幕内容，执行各种智能任务。",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 24.dp)
                )

                // 功能模块部分 - 使用网格布局
                Column(modifier = Modifier.fillMaxWidth()) {
                        // 第一行：AI对话和工具箱
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                FeatureCard(
                                        title = "AI 对话",
                                        description =
                                                "支持使用工具和设备交互，能对话也能计划。\n\n· 识别并分析当前屏幕内容\n· 执行多步骤的复杂任务\n· 连续多轮对话并保持记忆\n· 调用多种工具拓展能力",
                                        icon = Icons.Default.Chat,
                                        modifier = Modifier.weight(1f)
                                )

                                FeatureCard(
                                        title = "工具箱",
                                        description =
                                                "提供多样化的系统工具，增强操作能力。\n\n· 执行系统命令与自动化\n· 界面元素自动识别点击\n· 文件管理与内容处理\n· 实时设备状态监控",
                                        icon = Icons.Default.Build,
                                        modifier = Modifier.weight(1f)
                                )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 第二行：问题库和包管理
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                FeatureCard(
                                        title = "问题库",
                                        description =
                                                "记录解决方案，一定程度上作为AI记忆的使用。\n\n· 永久保存重要对话内容\n· 分类整理解决过的问题\n· 快速查找历史解决方案\n· 帮助AI形成长期记忆",
                                        icon = Icons.Default.Storage,
                                        modifier = Modifier.weight(1f)
                                )

                                FeatureCard(
                                        title = "包管理",
                                        description =
                                                "可以接入官方或者第三方的拓展包，让AI对话中支持更多的功能。\n\n· 一键安装官方扩展包\n· 导入自定义JavaScript工具\n· 管理第三方扩展模块\n· 为AI增加专业领域能力",
                                        icon = Icons.Default.Extension,
                                        modifier = Modifier.weight(1f)
                                )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 第三行：权限授予和设置
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                FeatureCard(
                                        title = "权限授予",
                                        description =
                                                "安全且可控的权限管理中心。\n\n· 根据需要开启必要功能\n· 清晰解释每项权限用途\n· 随时可查看或关闭权限\n· 保护用户隐私和安全",
                                        icon = Icons.Default.Security,
                                        modifier = Modifier.weight(1f)
                                )

                                FeatureCard(
                                        title = "设置",
                                        description =
                                                "根据个人喜好自定义应用体验。\n\n· 调整显示与界面选项\n· 设置个性化使用习惯\n· 配置应用工作模式\n· 管理消息通知方式",
                                        icon = Icons.Default.Settings,
                                        modifier = Modifier.weight(1f)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 基本使用指南
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = "基本使用步骤",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        StepItem(number = "1", text = "先进行授权（前往权限授予页面，授予必要的权限）")
                                        StepItem(
                                                number = "2",
                                                text = "在包管理加入需要的包（前往包管理页面，导入和管理扩展包）"
                                        )
                                        StepItem(
                                                number = "3",
                                                text = "修改为自己的API（目前使用的是开发者的Token，用完为止，仅供免费体验）"
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 使用技巧
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = "使用技巧",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TipItem(text = "安装包和使用mcp来让自己的ai支持更多工具")
                                        TipItem(text = "更改设置的偏好来让ai更加个性化")
                                        TipItem(text = "如果不希望ai更改，可以锁定偏好，多个偏好一键切换")
                                        TipItem(text = "ai对话的计划模式不是一定开着好，对于计划性不强的任务直接对话效率更高")
                                        TipItem(text = "通过更改权限设置，来让ai每次请求的时候不会都弹出权限请求")
                                        TipItem(text = "为了保证安全，请确保软件Github的开源版本")
                                        TipItem(text = "及时更新软件以获取用户体验")
                                        TipItem(text = "推广软件以让更多的人知道，加入软件的开源开发来支持更多的功能")
                                }
                        }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 联系我们
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Text(
                                text = "如需更多帮助，请联系我们",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                        )
                        Text(
                                text = "support@operit.ai",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                        )
                }
        }
}

@Composable
fun FeatureCard(
        title: String,
        description: String,
        icon: ImageVector,
        modifier: Modifier = Modifier
) {
        Surface(
                modifier = modifier.heightIn(min = 180.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                )
                        }

                        Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp
                        )
                }
        }
}

@Composable
fun StepItem(number: String, text: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                        modifier = Modifier.size(24.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                ) {
                        Box(contentAlignment = Alignment.Center) {
                                Text(
                                        text = number,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
}

@Composable
fun TipItem(text: String) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 0.5.dp).size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
}

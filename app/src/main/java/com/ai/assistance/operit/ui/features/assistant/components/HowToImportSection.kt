package com.ai.assistance.operit.ui.features.assistant.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun HowToImportSection() {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val newEditorUrl = "https://www.loongbones.com/"
    val oldEditorUrl = "https://www.egret.uk/download/"

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, start = 4.dp, top = 12.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                    "如何制作和导入模型?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
            )
            Icon(
                    imageVector =
                    if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "折叠" else "展开"
            )
        }
    }

    if (expanded) {
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                        text = """
                                DragonBones现已更名为 LoongBones，并推出了新版在线编辑器。
                                
                                1. 制作模型:
                                   - 推荐: 使用新版在线编辑器。
                                   - 备选: 从第三方存档网站下载旧版v5.6.3离线编辑器。
                                
                                2. 导出文件:
                                   - 无论使用哪个版本，导出数据时类型请选择 `JSON`。
                                   - 导出的核心文件为: `*_ske.json`, `*_tex.json`, `*_tex.png`。
                                
                                3. 配置交互:
                                   - (可选) 如需实现头部跟随，请添加IK约束，并将其目标骨骼命名为 `ik_target`。
                                   
                                4. 打包与导入:
                                   - 将导出的三个文件打包成一个 `.zip` 文件后导入。
                                """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                            text = "重要提示：旧版编辑器下载页面中，Windows和Mac的下载链接放反了，请注意选择正确的链接下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(all = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(oldEditorUrl))
                        context.startActivity(intent)
                    }) {
                        Text("下载旧版编辑器")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newEditorUrl))
                        context.startActivity(intent)
                    }) {
                        Text("访问在线编辑器")
                    }
                }
            }
        }
    }

    // Animation Specification Section
    var specExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, start = 4.dp, top = 12.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth().clickable { specExpanded = !specExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                    "桌面宠物动画规范",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
            )
            Icon(
                    imageVector =
                    if (specExpanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = if (specExpanded) "折叠" else "展开"
            )
        }
    }

    if (specExpanded) {
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
        ) {
            val titleColor = MaterialTheme.colorScheme.primary
            val annotatedString = buildAnnotatedString {
                withStyle(style = SpanStyle(color = titleColor, fontWeight = FontWeight.Bold)) {
                    append("核心交互 (必需)\n")
                }
                append("• ik_target: 用于头部跟随的IK目标骨骼，应添加IK约束并正确命名。\n\n")

                withStyle(style = SpanStyle(color = titleColor, fontWeight = FontWeight.Bold)) {
                    append("核心状态动画 (建议)\n")
                }
                append("• idle: (循环) 基础待机/呼吸动画，将在底层(layer 0)持续播放。\n")
                append("• blink: (不循环) 单次眨眼，将在idle之上的中层(layer 1)随机叠加。\n\n")

                withStyle(style = SpanStyle(color = titleColor, fontWeight = FontWeight.Bold)) {
                    append("核心用户交互动画 (建议)\n")
                }
                append("• tap_reaction: (不循环) 对用户点击的反应，将在高层(layer 2)播放，不打断其他动画。\n\n")
                
                withStyle(style = SpanStyle(color = titleColor, fontWeight = FontWeight.Bold)) {
                    append("动画制作建议\n")
                }
                append("• 基础动画: 尽量将待机(idle)动画设计为循环播放的形式，确保平滑过渡。\n")
                append("• 分层设计: 眨眼(blink)等短暂表情应设计为可叠加在基础动画上的独立动画。\n")
                append("• IK设置: 头部跟随效果需要在模型中创建一个名为'ik_target'的骨骼，并设置相应IK约束。\n")
            }
            Text(
                    text = annotatedString,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            )
        }
    }
} 
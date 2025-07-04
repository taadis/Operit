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
                val primaryColor = MaterialTheme.colorScheme.primary
                val annotatedString = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("DragonBones现已更名为 LoongBones，并推出了新版在线编辑器。\n")
                        append("LoongBones支持IK反向动力学、网格形变、骨骼绑定等专业级动画功能。\n\n")
                    }
                    
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append("1. 制作模型：\n")
                    }
                    append("    · 推荐：使用新版在线编辑器。\n")
                    append("    · 备选：从第三方存档网站下载旧版 v5.6.3 离线编辑器。\n\n")
                    
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append("2. 导出文件：\n")
                    }
                    append("    · 无论使用哪个版本，导出数据时类型请选择 JSON，版本请选择 5.5（当前还不支持 6.0 运行时）。\n")
                    append("    · 导出的核心文件为：*_ske.json、*_tex.json、*_tex.png。\n\n")
                    
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append("3. 配置交互：\n")
                    }
                    append("    · 如需实现头部跟随，请添加 IK 约束，并将其目标骨骼命名为 ik_target。\n")
                    append("    · idle 动画：请确保有一个命名为 idle 的动画，作为基础待机动画，会自动循环播放。\n")
                    append("    · 随机动画：可添加如 blink（眨眼）、shake_head（摇头）、wag_tail（摇尾巴）等动画，\n")
                    append("      系统会随机自动触发这些动画，丰富宠物表现。\n\n")
                    
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append("4. 打包与导入：\n")
                    }
                    append("    · 将导出的三个文件打包成一个 .zip 文件后导入。")
                }
                
                Text(
                    text = annotatedString,
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
} 
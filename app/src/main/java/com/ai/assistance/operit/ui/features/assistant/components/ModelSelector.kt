package com.ai.assistance.operit.ui.features.assistant.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.Live2DModel

/** Live2D 模型选择器 (下拉菜单样式) */
@Composable
fun ModelSelector(
    models: List<Live2DModel>,
    selectedModelId: String?,
    onModelSelected: (String) -> Unit,
    onDeleteModel: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.find { it.id == selectedModelId }

    Box {
        // 显示当前选中的模型，点击后展开
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            onClick = { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedModel?.name ?: "请选择模型",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "展开模型列表"
                )
            }
        }

        // 下拉菜单
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.8f) // 避免菜单过宽
        ) {
            if (models.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("没有可用的模型") },
                    onClick = { expanded = false }
                )
            } else {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = model.name,
                                    fontWeight = if (model.id == selectedModelId) FontWeight.Bold else FontWeight.Normal,
                                    color = if (model.id == selectedModelId) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                                if (!model.isBuiltIn) {
                                    IconButton(
                                        onClick = {
                                            onDeleteModel(model.id)
                                            expanded = false // 可以选择关闭菜单
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除模型",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            onModelSelected(model.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
} 
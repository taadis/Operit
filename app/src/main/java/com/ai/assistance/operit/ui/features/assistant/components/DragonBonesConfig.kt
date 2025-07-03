package com.ai.assistance.operit.ui.features.assistant.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel

@Composable
fun DragonBonesConfigSection(
        controller: com.ai.assistance.dragonbones.DragonBonesController,
        viewModel: AssistantConfigViewModel,
        uiState: AssistantConfigViewModel.UiState,
        onImportClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, start = 4.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("龙骨配置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Icon(
                    imageVector =
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        ModelSelector(
                                models = uiState.models,
                                currentModelId = uiState.currentModel?.id,
                                onModelSelected = { viewModel.switchModel(it) },
                                onModelDelete = { viewModel.deleteUserModel(it) }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onImportClick) {
                        Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "导入模型"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scale Slider
                Text(
                        text = "缩放: ${String.format("%.2f", controller.scale)}",
                        style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                        value = controller.scale,
                        onValueChange = { controller.scale = it },
                        valueRange = 0.1f..2.0f
                )

                // TranslationX Slider
                Text(
                        text = "X轴位移: ${String.format("%.1f", controller.translationX)}",
                        style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                        value = controller.translationX,
                        onValueChange = { controller.translationX = it },
                        valueRange = -500f..500f
                )

                // TranslationY Slider
                Text(
                        text = "Y轴位移: ${String.format("%.1f", controller.translationY)}",
                        style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                        value = controller.translationY,
                        onValueChange = { controller.translationY = it },
                        valueRange = -500f..500f
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
        models: List<com.ai.assistance.operit.data.model.DragonBonesModel>,
        currentModelId: String?,
        onModelSelected: (String) -> Unit,
        onModelDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = models.find { it.id == currentModelId }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    if (showDeleteDialog != null) {
        AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("确认删除模型？") },
                text = { Text("删除后不可恢复，确定要删除该模型吗？") },
                confirmButton = {
                    TextButton(
                            onClick = {
                                onModelDelete(showDeleteDialog!!)
                                showDeleteDialog = null
                            }
                    ) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消") } }
        )
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
                value = currentModel?.name ?: "选择模型",
                onValueChange = {},
                readOnly = true,
                label = { Text("当前模型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                        text = { Text(model.name) },
                        onClick = {
                            onModelSelected(model.id)
                            expanded = false
                        },
                        trailingIcon = {
                            IconButton(onClick = { showDeleteDialog = model.id }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                )
            }
        }
    }
}

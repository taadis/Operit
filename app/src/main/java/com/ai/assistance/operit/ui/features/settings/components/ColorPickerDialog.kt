package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.ui.theme.isHighContrast
import com.github.skydoves.colorpicker.compose.*
import kotlinx.coroutines.launch

/**
 * Material colors for the color picker
 */
val materialColors = listOf(
    // Material Design primary colors
    Color(0xFF6200EE), // Purple 500 (Material primary)
    Color(0xFF3700B3), // Purple 700 (Material primary variant)
    Color(0xFF03DAC6), // Teal 200 (Material secondary)
    Color(0xFF018786), // Teal 700 (Material secondary variant)
    Color(0xFF1976D2), // Blue 700
    Color(0xFF0D47A1), // Blue 900
    Color(0xFF1E88E5), // Blue 600

    // More Material colors with good contrast
    Color(0xFFD32F2F), // Red 700
    Color(0xFF7B1FA2), // Purple 700
    Color(0xFF388E3C), // Green 700
    Color(0xFFE64A19), // Deep Orange 700
    Color(0xFFF57C00), // Orange 700
    Color(0xFF5D4037), // Brown 700
    Color(0xFF455A64)  // Blue Grey 700
)

/**
 * A dialog with HSV color picker
 */
@Composable
fun ColorPickerDialog(
    showColorPicker: Boolean,
    currentColorPickerMode: String,
    primaryColorInput: Int,
    secondaryColorInput: Int,
    onColorSelected: (primaryColor: Int?, secondaryColor: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showColorPicker) return
    
    val currentColorForPicker =
        if (currentColorPickerMode == "primary") primaryColorInput else secondaryColorInput
    val currentColor = Color(currentColorForPicker)
    val pickerController = rememberColorPickerController()
    val scope = rememberCoroutineScope()

    // Set initial color
    LaunchedEffect(pickerController) {
        pickerController.setWheelColor(currentColor)
    }

    // 创建一个可变状态，用于实时预览颜色
    var previewColor by remember { mutableStateOf(currentColor) }

    // 监听颜色变化
    LaunchedEffect(currentColor) { previewColor = currentColor }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (currentColorPickerMode == "primary") "选择主色" else "选择次色",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                // Live color preview - use solid backgrounds
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color sample
                        Box(
                            modifier = Modifier.size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(previewColor)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Text preview
                        Column {
                            // Show contrast example
                            val textColor = getTextColorForBackground(previewColor)
                            Surface(
                                modifier = Modifier.width(120.dp).height(40.dp),
                                color = previewColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "示例文本",
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // Add contrast rating
                            val contrastRating =
                                if (isHighContrast(previewColor)) "高对比度 ✓" else "低对比度 ⚠"
                            val contrastColor =
                                if (isHighContrast(previewColor)) Color(0xFF388E3C)
                                else Color(0xFFD32F2F)

                            Text(
                                text = contrastRating,
                                style = MaterialTheme.typography.bodySmall,
                                color = contrastColor,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Color display preview with alpha tiles
                AlphaTile(
                    modifier = Modifier.fillMaxWidth()
                        .height(60.dp)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    controller = pickerController
                )

                // HSV Color Picker
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 8.dp),
                    controller = pickerController,
                    onColorChanged = { colorEnvelope: ColorEnvelope ->
                        if (colorEnvelope.fromUser) {
                            val newColor = colorEnvelope.color.toArgb()
                            if (currentColorPickerMode == "primary") {
                                onColorSelected(newColor, null)
                            } else {
                                onColorSelected(null, newColor)
                            }
                            previewColor = colorEnvelope.color
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Brightness slider
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth()
                        .height(30.dp)
                        .padding(vertical = 4.dp),
                    controller = pickerController
                )

                // Alpha slider
                AlphaSlider(
                    modifier = Modifier.fillMaxWidth()
                        .height(30.dp)
                        .padding(vertical = 4.dp),
                    controller = pickerController,
                    tileOddColor = Color.White,
                    tileEvenColor = Color.LightGray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Preset colors title
                Text(
                    text = "推荐颜色",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Preset colors grid
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    materialColors.take(7).forEach { color ->
                        PresetColorItem(color) {
                            if (currentColorPickerMode == "primary") {
                                onColorSelected(it.toArgb(), null)
                            } else {
                                onColorSelected(null, it.toArgb())
                            }
                            pickerController.setWheelColor(it)
                            previewColor = it
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    materialColors.takeLast(7).forEach { color ->
                        PresetColorItem(color) {
                            if (currentColorPickerMode == "primary") {
                                onColorSelected(it.toArgb(), null)
                            } else {
                                onColorSelected(null, it.toArgb())
                            }
                            pickerController.setWheelColor(it)
                            previewColor = it
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        onDismiss()
                    }
                }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 
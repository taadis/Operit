package com.ai.assistance.operit.ui.features.assistant.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.Live2DConfig

/** Live2D 控制组件 (新样式) */
@Composable
fun Live2DControls(
        config: Live2DConfig?,
        onScaleChanged: (Float) -> Unit,
        onTranslateXChanged: (Float) -> Unit,
        onTranslateYChanged: (Float) -> Unit,
        onMouthFormChanged: (Float) -> Unit,
        onMouthOpenYChanged: (Float) -> Unit,
        onAutoBlinkChanged: (Boolean) -> Unit,
        onRenderBackChanged: (Boolean) -> Unit
) {
    if (config == null) return

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlSliderItem(
                label = "缩放",
                value = config.scale,
                onValueChange = onScaleChanged,
                valueRange = 0.1f..5.0f
        )
        ControlSliderItem(
                label = "水平位置",
                value = config.translateX,
                onValueChange = onTranslateXChanged,
                valueRange = -2.0f..2.0f
        )
        ControlSliderItem(
                label = "垂直位置",
                value = config.translateY,
                onValueChange = onTranslateYChanged,
                valueRange = -2.0f..2.0f
        )
        ControlSliderItem(
                label = "嘴部形状",
                value = config.mouthForm,
                onValueChange = onMouthFormChanged,
                valueRange = 0.0f..2.0f
        )
        ControlSliderItem(
                label = "嘴部开合",
                value = config.mouthOpenY,
                onValueChange = onMouthOpenYChanged,
                valueRange = 0.0f..2.0f
        )

        Divider(modifier = Modifier.padding(vertical = 4.dp))

        ControlSwitchItem(
                label = "自动眨眼",
                checked = config.autoBlinkEnabled,
                onCheckedChange = onAutoBlinkChanged
        )

        ControlSwitchItem(
                label = "渲染背景",
                checked = config.renderBack,
                onCheckedChange = onRenderBackChanged
        )
    }
}

/** 风格化的滑块控件项 */
@Composable
private fun ControlSliderItem(
        label: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>
) {
    Column {
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format("%.2f", value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }
}

/** 风格化的开关控件项 */
@Composable
private fun ControlSwitchItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

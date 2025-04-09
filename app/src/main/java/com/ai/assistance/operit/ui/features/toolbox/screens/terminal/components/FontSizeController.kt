package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.utils.TerminalColors

/**
 * 字体大小控制器组件
 */
@Composable
fun FontSizeController(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    isVisible: Boolean,
    minFontSize: Int,
    maxFontSize: Int,
    defaultFontSize: Int
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TerminalColors.ParrotBgLight.copy(alpha = 0.9f))
                .border(1.dp, TerminalColors.ParrotAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 减小字体按钮
            IconButton(
                onClick = { 
                    if (fontSize > minFontSize) {
                        onFontSizeChange(fontSize - 1)
                    }
                },
                enabled = fontSize > minFontSize,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (fontSize > minFontSize) TerminalColors.ParrotBgLight else Color.DarkGray.copy(alpha = 0.3f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.TextDecrease,
                    contentDescription = "减小字体",
                    tint = if (fontSize > minFontSize) TerminalColors.ParrotAccent else Color.Gray
                )
            }
            
            // 当前字体大小显示
            Text(
                text = "$fontSize sp",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            // 增大字体按钮
            IconButton(
                onClick = { 
                    if (fontSize < maxFontSize) {
                        onFontSizeChange(fontSize + 1)
                    }
                },
                enabled = fontSize < maxFontSize,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (fontSize < maxFontSize) TerminalColors.ParrotBgLight else Color.DarkGray.copy(alpha = 0.3f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.TextIncrease,
                    contentDescription = "增大字体",
                    tint = if (fontSize < maxFontSize) TerminalColors.ParrotAccent else Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 重置字体大小按钮
            TextButton(
                onClick = { onFontSizeChange(defaultFontSize) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = TerminalColors.ParrotAccent
                )
            ) {
                Text("重置")
            }
        }
    }
} 
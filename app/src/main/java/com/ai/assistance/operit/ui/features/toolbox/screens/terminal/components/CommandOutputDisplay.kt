package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalLine
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSession
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.utils.TerminalColors
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.utils.highlightCommandText

/**
 * 命令输出显示区域
 */
@Composable
fun CommandOutputDisplay(
    session: TerminalSession,
    fontSize: Int,
    isZooming: Boolean,
    zoomScale: Float,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()
    
    // 当有新的输出时，自动滚动到底部
    LaunchedEffect(session.commandHistory.size) {
        if (session.commandHistory.isNotEmpty()) {
            scrollState.animateScrollToItem(session.commandHistory.size - 1)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        TerminalColors.ParrotBgLight,
                        TerminalColors.ParrotBg
                    )
                )
            )
            .border(
                width = 1.dp,
                color = TerminalColors.ParrotAccent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        // 缩放反馈指示器
        if (isZooming) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .scale(zoomScale)
                    .alpha(0.2f)
                    .clip(CircleShape)
                    .background(TerminalColors.ParrotAccent)
            )
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            state = scrollState
        ) {
            itemsIndexed(
                items = session.commandHistory
            ) { index, line ->
                val textColor = when (line) {
                    is TerminalLine.Input -> TerminalColors.ParrotGreen // ParrotOS风格的绿色命令
                    is TerminalLine.Output -> Color.White // 白色输出
                }
                
                when (line) {
                    is TerminalLine.Input -> {
                        // ParrotOS风格的命令输入显示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 使用行记录的提示符，而不是当前会话状态
                            Text(
                                text = line.prompt,
                                color = TerminalColors.ParrotAccent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // 实际命令 - 使用语法高亮
                            Text(
                                text = highlightCommandText(line.text),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize + 2).sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                            )
                        }
                    }
                    is TerminalLine.Output -> {
                        // 普通输出文本
                        Text(
                            text = line.text,
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 2).sp,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
} 
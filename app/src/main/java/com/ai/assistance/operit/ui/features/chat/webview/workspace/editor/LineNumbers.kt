package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.EditorTheme

/**
 * 显示行号的组件，经过优化，仅渲染可见部分
 * @param lineCount 总行数
 * @param theme 编辑器主题
 * @param fontSize 动态字体大小
 * @param scrollPosition 编辑器的垂直滚动像素位置
 * @param lineHeight 行高
 * @param modifier 修饰符
 */
@Composable
fun LineNumbers(
    lineCount: Int,
    theme: EditorTheme,
    fontSize: TextUnit,
    scrollPosition: Int,
    lineHeight: Dp,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val lineHeightPx = with(density) { lineHeight.toPx() }
        val componentHeight = constraints.maxHeight.toFloat()

        // 计算可见的行范围
        val firstVisibleLine = (scrollPosition / lineHeightPx).toInt().coerceAtLeast(0)
        val visibleLineCount = (componentHeight / lineHeightPx).toInt() + 2 // +2作为缓冲
        val lastVisibleLine = (firstVisibleLine + visibleLineCount).coerceAtMost(lineCount - 1)

        Box(modifier = Modifier.fillMaxSize()) {
            // 使用Layout进行手动布局，实现虚拟化
            Layout(
                content = {
                    // 仅为可见行创建Composable
                    for (i in firstVisibleLine..lastVisibleLine) {
                        Text(
                            text = (i + 1).toString(), // 行号从1开始
                            style = TextStyle(
                                color = theme.lineNumberColor,
                                fontSize = fontSize, // 使用动态字体大小
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    var yPosition = -scrollPosition % lineHeightPx.toInt()

                    placeables.forEach { placeable ->
                        placeable.placeRelative(x = 0, y = yPosition)
                        yPosition += lineHeightPx.toInt()
                    }
                }
            }

            // 绘制右侧的分隔线
            Canvas(modifier = Modifier.fillMaxHeight().align(Alignment.TopEnd)) {
                drawLine(
                    color = theme.gutterBorder,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
} 
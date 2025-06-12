package com.ai.assistance.operit.ui.common.displays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.util.stream.stream

/** 新一代流式Markdown+LaTeX渲染器，完全替换原有实现。 兼容原有API，支持所有Markdown和LaTeX混排。 */
@Composable
fun MarkdownTextComposable(
        text: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = Unspecified,
        textAlign: TextAlign? = null,
        isSelectable: Boolean = true, // 保留参数，暂不处理
        onLinkClicked: ((String) -> Unit)? = null
) {
        // 记住流，仅当文本更改时才重新创建
        val markdownStream = remember(text) { text.stream() }

        // 直接用流式渲染器替换
        StreamMarkdownRenderer(
                markdownStream = markdownStream,
                modifier = modifier,
                textColor = textColor,
                // 其它参数可扩展
                onLinkClick = onLinkClicked
        )
}

package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.EditorTheme

/**
 * 底部符号输入栏
 * @param onSymbolClick 符号点击事件回调
 * @param theme 编辑器主题
 */
@Composable
fun SymbolBar(
    onSymbolClick: (String) -> Unit,
    theme: EditorTheme
) {
    val symbols = listOf(
        "  ", "{", "}", "[", "]", "(", ")", "<", ">",
        "=", "+", "-", "*", "/", "%",
        ".", ",", ";", ":", "?", "!",
        "&", "|", "^", "~",
        "\"", "'", "`"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.gutterBackground)
            .padding(vertical = 4.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(symbols) { symbol ->
                SymbolButton(
                    symbol = symbol,
                    onClick = { onSymbolClick(symbol) },
                    theme = theme
                )
            }
        }
    }
}

@Composable
private fun SymbolButton(
    symbol: String,
    onClick: () -> Unit,
    theme: EditorTheme
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(theme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = theme.textColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
} 
package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.EditorTheme
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.getThemeForLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 代码编辑器组件，支持语法高亮、行号显示和代码补全
 * @param code 初始代码内容
 * @param language 代码语言，用于语法高亮
 * @param onCodeChange 代码变更回调
 * @param modifier 修饰符
 * @param readOnly 是否只读模式
 * @param showLineNumbers 是否显示行号
 * @param enableCompletion 是否启用代码补全
 */
@Composable
fun CodeEditor(
    code: String,
    language: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
    enableCompletion: Boolean = true
) {
    // 编辑器主题
    val theme = getThemeForLanguage(language)
    
    // 编辑器状态
    var textFieldValue by remember { mutableStateOf(TextFieldValue(code)) }
    LaunchedEffect(code) {
        if (textFieldValue.text != code) {
            textFieldValue = TextFieldValue(code)
        }
    }
    
    // 字体大小和缩放状态
    var fontSize by remember { mutableStateOf(theme.fontSize) }
    
    // 滚动状态
    val verticalScrollState = rememberScrollState()
    
    // 文本布局结果
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    
    // 代码补全状态
    var showCompletion by remember { mutableStateOf(false) }
    var completionItems by remember { mutableStateOf(listOf<String>()) }
    var completionPosition by remember { mutableStateOf(0) }
    
    // 协程作用域
    val coroutineScope = rememberCoroutineScope()
    
    val focusRequester = remember { FocusRequester() }
    
    // 延迟保存，避免频繁调用
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text != code) {
            delay(500)
            onCodeChange(textFieldValue.text)
        }
    }
    
    // 行号 - 现在从TextLayoutResult动态获取
    val lineCount = textLayoutResult?.lineCount ?: (textFieldValue.text.count { it == '\n' } + 1)
    val maxLineDigits = lineCount.toString().length.coerceAtLeast(1)

    // 行高 - 优先从TextLayoutResult获取精确值，回退到估算值
    val density = LocalDensity.current
    val lineHeight = remember(textLayoutResult, fontSize, density) {
        val actualLineHeight = textLayoutResult?.getLineBottom(0)
        if (actualLineHeight != null) {
            with(density) { actualLineHeight.toDp() }
        } else {
            // 回退估算值
            with(density) { (fontSize.value * 1.5).sp.toDp() }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(), // 使用官方Modifier处理键盘insets
        bottomBar = {
            SymbolBar(
                theme = theme,
                onSymbolClick = { symbol ->
                    val originalText = textFieldValue.text
                    val selection = textFieldValue.selection
                    val newText = originalText.substring(0, selection.start) +
                                  symbol +
                                  originalText.substring(selection.end)
                    val newSelection = selection.start + symbol.length
                    textFieldValue = textFieldValue.copy(
                        text = newText,
                        selection = TextRange(newSelection)
                    )
                    focusRequester.requestFocus()
                }
            )
        }
    ) { paddingValues ->
        // 编辑器布局
        Row(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(theme.background) // 将背景移到外层以便手势检测
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        fontSize = (fontSize.value * zoom).coerceIn(8f, 24f).sp
                    }
                }
        ) {
            // 行号区域
            if (showLineNumbers) {
                LineNumbers(
                    lineCount = lineCount,
                    theme = theme,
                    fontSize = fontSize, // 传递动态字体大小
                    scrollPosition = verticalScrollState.value,
                    lineHeight = lineHeight,
                    modifier = Modifier
                        .padding(top = 8.dp) // 添加顶部内边距以对齐
                        .width((maxLineDigits * fontSize.value * 0.6f).dp + 16.dp) // 根据字体和位数动态计算宽度
                        .fillMaxHeight()
                        .background(theme.gutterBackground)
                )
            }
            
            // 代码编辑区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SelectionContainer {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            
                            // 简单的代码补全触发逻辑
                            if (enableCompletion) {
                                val lastChar = newValue.text.getOrNull(newValue.selection.start - 1)
                                if (lastChar == '.' || lastChar == '(' || Character.isWhitespace(lastChar ?: ' ')) {
                                    coroutineScope.launch {
                                        val suggestions = getSuggestionsForLanguage(
                                            language,
                                            newValue.text,
                                            newValue.selection.start
                                        )
                                        if (suggestions.isNotEmpty()) {
                                            completionItems = suggestions
                                            showCompletion = true
                                            completionPosition = newValue.selection.start
                                        }
                                    }
                                } else {
                                    showCompletion = false
                                }
                            }
                        },
                        onTextLayout = { result ->
                            textLayoutResult = result
                        },
                        readOnly = readOnly,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize, // 使用动态字体大小
                            color = theme.textColor,
                            lineHeight = lineHeight.value.sp // 确保行高一致
                        ),
                        cursorBrush = SolidColor(theme.cursorColor),
                        visualTransformation = VisualTransformation { text ->
                            highlightSyntax(text, language, theme.copy(fontSize = fontSize))
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester) // 关联focusRequester
                            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                            .verticalScroll(verticalScrollState) // 移除横向滚动，实现自动换行
                    )
                }
                
                // 代码补全弹窗
                if (showCompletion && completionItems.isNotEmpty()) {
                    CodeCompletionPopup(
                        suggestions = completionItems,
                        onSuggestionSelected = { suggestion ->
                            val currentText = textFieldValue.text
                            val insertPos = completionPosition
                            
                            // 找到最后一个'.'或' '来确定替换范围
                            val lastTokenStart = currentText.substring(0, insertPos).takeLastWhile { it != '.' && !Character.isWhitespace(it) }.length
                            val replaceStart = insertPos - lastTokenStart
                            
                            val before = currentText.substring(0, replaceStart)
                            val after = currentText.substring(insertPos)
                            
                            val newText = before + suggestion + after
                            
                            textFieldValue = TextFieldValue(
                                text = newText,
                                selection = TextRange((before + suggestion).length)
                            )
                            showCompletion = false
                        },
                        onDismiss = { showCompletion = false }
                    )
                }
            }
        }
    }
}

/**
 * 语法高亮转换函数
 */
private fun highlightSyntax(text: AnnotatedString, language: String, theme: EditorTheme): TransformedText {
    val highlightedText = applySyntaxHighlighting(text, language, theme)
    return TransformedText(highlightedText, OffsetMapping.Identity)
}

/**
 * 获取代码补全建议
 */
private suspend fun getSuggestionsForLanguage(language: String, code: String, position: Int): List<String> {
    // 根据语言和上下文提供补全建议
    return when (language.lowercase()) {
        "kotlin" -> getKotlinSuggestions(code, position)
        "java" -> getJavaSuggestions(code, position)
        "javascript", "js" -> getJavaScriptSuggestions(code, position)
        "typescript", "ts" -> getTypeScriptSuggestions(code, position)
        "html" -> getHtmlSuggestions(code, position)
        "css" -> getCssSuggestions(code, position)
        else -> emptyList()
    }
} 
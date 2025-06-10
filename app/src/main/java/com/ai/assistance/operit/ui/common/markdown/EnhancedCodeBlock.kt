package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 增强型代码块组件
 *
 * 具有以下功能：
 * 1. 代码语法高亮
 * 2. 复制按钮
 * 3. 行号显示
 * 4. 夜间模式风格
 */
@Composable
fun EnhancedCodeBlock(code: String, language: String = "", modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showCopiedToast by remember { mutableStateOf(false) }

    // 处理复制事件
    val handleCopy: () -> Unit = {
        clipboardManager.setText(AnnotatedString(code))
        scope.launch {
            showCopiedToast = true
            delay(1500)
            showCopiedToast = false
        }
    }

    // 暗色代码块背景颜色
    val codeBlockBackground = Color(0xFF1E1E1E) // VS Code 暗色主题背景色
    val toolbarBackground = Color(0xFF252526) // 比背景稍亮一点的颜色

    Surface(
            modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
            color = codeBlockBackground,
            shape = RoundedCornerShape(4.dp)
    ) {
        Column {
            // 顶部工具栏
            Row(
                    modifier = Modifier.fillMaxWidth().background(toolbarBackground).padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // 语言标记（如果有）
                if (language.isNotEmpty()) {
                    Text(
                            text = language,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFAAAAAA),
                            modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // 复制按钮
                IconButton(onClick = handleCopy, modifier = Modifier.size(28.dp)) {
                    Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制代码",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 代码内容
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                // 行号列
                val lines = code.lines()
                val digits = lines.size.toString().length.coerceAtLeast(2) // 至少2位数的宽度

                // 行号栏
                Column(
                        modifier =
                                Modifier.background(Color(0xFF252526))
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                ) {
                    lines.forEachIndexed { index, _ ->
                        Text(
                                text = (index + 1).toString().padStart(digits),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFF6A737D),
                                modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                // 代码内容
                Box(modifier = Modifier.weight(1f).padding(end = 8.dp, top = 8.dp)) {
                    Text(
                            text = highlightSyntax(code, language),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color.White
                    )

                    // 显示复制成功提示
                    if (showCopiedToast) {
                        Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                color = Color(0xFF0366D6), // GitHub 蓝色
                                shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                    text = "已复制",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 改进的语法高亮函数，支持夜间模式配色 解决了空格丢失问题 */
@Composable
private fun highlightSyntax(code: String, language: String): AnnotatedString {
    // 夜间模式语法高亮颜色
    val keywordColor = Color(0xFF569CD6) // 蓝色 - 关键字
    val stringColor = Color(0xFFCE9178) // 橙红色 - 字符串
    val commentColor = Color(0xFF6A9955) // 绿色 - 注释
    val numberColor = Color(0xFFB5CEA8) // 淡绿色 - 数字
    val typeColor = Color(0xFF4EC9B0) // 青色 - 类型
    val functionColor = Color(0xFFDCDCAA) // 黄色 - 函数
    val textColor = Color(0xFFD4D4D4) // 浅灰色 - 普通文本

    // 语法高亮逻辑
    return buildAnnotatedString {
        when (language.lowercase()) {
            "kotlin", "java", "swift", "typescript", "javascript", "dart" -> {
                // 关键字列表
                val keywords =
                        setOf(
                                "fun",
                                "val",
                                "var",
                                "class",
                                "interface",
                                "object",
                                "return",
                                "if",
                                "else",
                                "when",
                                "for",
                                "while",
                                "do",
                                "break",
                                "continue",
                                "package",
                                "import",
                                "public",
                                "private",
                                "protected",
                                "internal",
                                "const",
                                "final",
                                "static",
                                "abstract",
                                "override",
                                "suspend",
                                "true",
                                "false",
                                "null",
                                "function",
                                "let",
                                "const",
                                "export",
                                "import",
                                "async",
                                "await",
                                "void",
                                "int",
                                "double"
                        )

                val types =
                        setOf(
                                "String",
                                "Int",
                                "Double",
                                "Float",
                                "Boolean",
                                "List",
                                "Map",
                                "Set",
                                "Array",
                                "Number",
                                "Object",
                                "Promise",
                                "void",
                                "any",
                                "never"
                        )

                // 处理每一行代码
                code.lines().forEachIndexed { index, line ->
                    if (index > 0) append('\n')

                    // 处理注释行
                    if (line.trim().startsWith("//")) {
                        withStyle(SpanStyle(color = commentColor)) { append(line) }
                        return@forEachIndexed
                    }

                    // 处理包含内联注释的行
                    val commentIndex = line.indexOf("//")
                    if (commentIndex > 0) {
                        // 处理注释前的代码
                        processCodePart(
                                line.substring(0, commentIndex),
                                keywords,
                                types,
                                keywordColor,
                                typeColor,
                                functionColor,
                                stringColor,
                                numberColor,
                                textColor
                        )

                        // 处理注释部分
                        withStyle(SpanStyle(color = commentColor)) {
                            append(line.substring(commentIndex))
                        }
                    } else {
                        // 处理完整行的代码
                        processCodePart(
                                line,
                                keywords,
                                types,
                                keywordColor,
                                typeColor,
                                functionColor,
                                stringColor,
                                numberColor,
                                textColor
                        )
                    }
                }
            }
            else -> {
                // 对于未知语言，使用默认颜色
                withStyle(SpanStyle(color = textColor)) { append(code) }
            }
        }
    }
}

/** 处理代码段，保留空格和标点符号 */
private fun AnnotatedString.Builder.processCodePart(
        code: String,
        keywords: Set<String>,
        types: Set<String>,
        keywordColor: Color,
        typeColor: Color,
        functionColor: Color,
        stringColor: Color,
        numberColor: Color,
        textColor: Color
) {
    var inString = false
    var currentWord = ""
    var currentStringContent = ""

    fun appendWord() {
        if (currentWord.isEmpty()) return

        when {
            currentWord in keywords ->
                    withStyle(SpanStyle(color = keywordColor)) { append(currentWord) }
            currentWord in types -> withStyle(SpanStyle(color = typeColor)) { append(currentWord) }
            currentWord.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(")) -> {
                val functionName = currentWord.substring(0, currentWord.indexOfFirst { it == '(' })
                val params = currentWord.substring(functionName.length)
                withStyle(SpanStyle(color = functionColor)) { append(functionName) }
                withStyle(SpanStyle(color = textColor)) { append(params) }
            }
            currentWord.matches(Regex("\\d+(\\.\\d+)?")) ->
                    withStyle(SpanStyle(color = numberColor)) { append(currentWord) }
            else -> withStyle(SpanStyle(color = textColor)) { append(currentWord) }
        }
        currentWord = ""
    }

    for (i in code.indices) {
        val c = code[i]

        // 处理字符串
        if (c == '"' || c == '\'') {
            if (!inString) {
                // 开始字符串
                appendWord()
                inString = true
                currentStringContent = c.toString()
            } else {
                // 结束字符串
                currentStringContent += c
                withStyle(SpanStyle(color = stringColor)) { append(currentStringContent) }
                currentStringContent = ""
                inString = false
            }
            continue
        }

        if (inString) {
            currentStringContent += c
            continue
        }

        // 处理非字符串内容
        when {
            c.isLetterOrDigit() || c == '_' -> currentWord += c
            c.isWhitespace() -> {
                appendWord()
                append(c.toString())
            }
            else -> {
                appendWord()
                withStyle(SpanStyle(color = textColor)) { append(c.toString()) }
            }
        }
    }

    // 处理最后一个单词
    appendWord()

    // 如果有未闭合的字符串
    if (currentStringContent.isNotEmpty()) {
        withStyle(SpanStyle(color = stringColor)) { append(currentStringContent) }
    }
}

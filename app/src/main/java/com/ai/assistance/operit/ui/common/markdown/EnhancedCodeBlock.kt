package com.ai.assistance.operit.ui.common.markdown

import android.util.Log
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

private const val TAG = "CodeBlock"

/**
 * 增强型代码块组件
 *
 * 具有以下功能：
 * 1. 代码语法高亮
 * 2. 复制按钮
 * 3. 行号显示
 * 4. 夜间模式风格
 * 5. 行渲染保护（使用key机制避免重复渲染）
 * 6. 记忆优化（减少不必要的重组）
 */
@Composable
fun EnhancedCodeBlock(code: String, language: String = "", modifier: Modifier = Modifier) {
    // 使用remember记住组件标识，避免不必要的重组
    val componentId = remember { "codeblock-${System.identityHashCode(code)}" }
    Log.d(TAG, "组件初始化: id=$componentId 代码长度=${code.length}, 语言=$language")
    
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

    // 使用derivedStateOf智能派生代码行，只在内容真正变化时才会重新计算
    val codeLines by remember(code) {
        derivedStateOf { 
            val lines = code.lines()
            Log.d(TAG, "派生代码行: id=$componentId 共${lines.size}行")
            lines 
        }
    }
    
    // 缓存已计算过的行，避免重复创建
    val lineCache = remember { mutableMapOf<String, AnnotatedString>() }

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
                val digits = codeLines.size.toString().length.coerceAtLeast(2) // 至少2位数的宽度
                Log.d(TAG, "行号宽度: id=$componentId $digits 位")

                // 行号栏
                Column(
                        modifier = Modifier
                                .background(Color(0xFF252526))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                ) {
                    codeLines.forEachIndexed { index, _ ->
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp, top = 8.dp)
                ) {
                    // 使用key为每行建立记忆
                    codeLines.forEachIndexed { index, line ->
                        // 添加日志记录每行渲染
                        val lineHash = line.hashCode()
                        
                        // 更智能的行渲染，使用复合key（行内容+位置）
                        val lineKey = "$index:$lineHash"
                        Log.d(TAG, "准备渲染行 #${index+1}: id=$componentId hash=$lineHash, 长度=${line.length}")
                        
                        key(lineKey) {  // 使用更稳定的key策略
                            Log.d(TAG, "✓ 渲染行 #${index+1}: id=$componentId key=$lineKey")
                            
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            
                            // 渲染单行代码，利用行缓存机制
                            CachedCodeLine(
                                line = line, 
                                language = language, 
                                index = index,
                                lineCache = lineCache
                            )
                        }
                    }
                    
                    // 显示复制成功提示
                    if (showCopiedToast) {
                        Log.d(TAG, "显示已复制提示: id=$componentId")
                        Surface(
                                modifier = Modifier.align(Alignment.End).padding(4.dp),
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

/**
 * 带缓存的单行代码组件，进一步减少重组和计算
 */
@Composable
private fun CachedCodeLine(
    line: String, 
    language: String, 
    index: Int,
    lineCache: MutableMap<String, AnnotatedString>
) {
    // 添加日志记录
    val lineStart = if (line.length > 15) line.substring(0, 15) + "..." else line
    Log.d(TAG, "★ CodeLine组件 #${index+1}: 内容=\"$lineStart\"")
    
    // 计算缓存key
    val cacheKey = "$language:$line"
    
    // 使用缓存或重新计算高亮
    val highlightedLine = if (lineCache.containsKey(cacheKey)) {
        Log.d(TAG, "✅ 使用缓存的高亮 #${index+1}")
        lineCache[cacheKey]!!
    } else {
        Log.d(TAG, "⚡ 计算行高亮 #${index+1}: 语言=$language")
        val result = highlightSyntaxLine(line, language)
        lineCache[cacheKey] = result
        result
    }
    
    Text(
        text = highlightedLine,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Color.White
    )
}

/** 处理单行代码的语法高亮 */
private fun highlightSyntaxLine(line: String, language: String): AnnotatedString {
    // 夜间模式语法高亮颜色
    val keywordColor = Color(0xFF569CD6) // 蓝色 - 关键字
    val stringColor = Color(0xFFCE9178) // 橙红色 - 字符串
    val commentColor = Color(0xFF6A9955) // 绿色 - 注释
    val numberColor = Color(0xFFB5CEA8) // 淡绿色 - 数字
    val typeColor = Color(0xFF4EC9B0) // 青色 - 类型
    val functionColor = Color(0xFFDCDCAA) // 黄色 - 函数
    val textColor = Color(0xFFD4D4D4) // 浅灰色 - 普通文本
    
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

                // 处理注释行
                if (line.trim().startsWith("//")) {
                    withStyle(SpanStyle(color = commentColor)) { append(line) }
                    return@buildAnnotatedString
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
            else -> {
                // 对于未知语言，使用默认颜色
                withStyle(SpanStyle(color = textColor)) { append(line) }
            }
        }
    }
}

/** 原始的语法高亮函数，现在只用于向后兼容 */
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

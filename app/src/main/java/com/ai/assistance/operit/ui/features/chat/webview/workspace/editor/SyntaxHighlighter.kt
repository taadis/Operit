package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.EditorTheme

/**
 * 对代码应用语法高亮
 * @param text 原始文本
 * @param language 编程语言
 * @param theme 编辑器主题
 * @return 高亮后的文本
 */
fun applySyntaxHighlighting(
    text: AnnotatedString,
    language: String,
    theme: EditorTheme
): AnnotatedString {
    return when (language.lowercase()) {
        "kotlin" -> highlightKotlin(text.text, theme)
        "java" -> highlightJava(text.text, theme)
        "javascript", "js" -> highlightJavaScript(text.text, theme)
        "typescript", "ts" -> highlightTypeScript(text.text, theme)
        "html" -> highlightHtml(text.text, theme)
        "css" -> highlightCss(text.text, theme)
        "xml" -> highlightXml(text.text, theme)
        "json" -> highlightJson(text.text, theme)
        "markdown", "md" -> highlightMarkdown(text.text, theme)
        else -> AnnotatedString(text.text) // 不支持的语言返回原始文本
    }
}

/**
 * Kotlin语法高亮
 */
private fun highlightKotlin(code: String, theme: EditorTheme): AnnotatedString {
    // Kotlin关键字
    val keywords = setOf(
        "package", "import", "class", "interface", "fun", "val", "var", "if", "else", 
        "when", "for", "while", "do", "break", "continue", "return", "object", "companion",
        "override", "private", "protected", "public", "internal", "open", "final", "abstract",
        "sealed", "data", "enum", "annotation", "suspend", "lateinit", "infix", "operator",
        "inline", "tailrec", "external", "reified", "noinline", "crossinline", "const",
        "typealias", "this", "super", "true", "false", "null", "is", "in", "as", "by"
    )
    
    // 类型
    val types = setOf(
        "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char", "String",
        "Array", "List", "Map", "Set", "Pair", "Triple", "Unit", "Nothing", "Any"
    )
    
    return buildAnnotatedString {
        val text = code
        var currentPosition = 0
        
        // 简单的词法分析
        val regex = Regex("([a-zA-Z_][a-zA-Z0-9_]*)|(/\\*[\\s\\S]*?\\*/)|(//.*)|(\"(?:\\\\.|[^\\\\\"])*\")|('.')")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            // 添加未匹配的文本
            if (match.range.first > currentPosition) {
                append(text.substring(currentPosition, match.range.first))
            }
            
            val matchedText = match.value
            
            when {
                // 多行注释
                matchedText.startsWith("/*") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 单行注释
                matchedText.startsWith("//") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 字符串
                matchedText.startsWith("\"") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 字符
                matchedText.startsWith("'") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 关键字
                matchedText in keywords -> {
                    withStyle(SpanStyle(color = theme.keywordColor)) {
                        append(matchedText)
                    }
                }
                // 类型
                matchedText in types -> {
                    withStyle(SpanStyle(color = theme.typeColor)) {
                        append(matchedText)
                    }
                }
                // 普通标识符
                else -> {
                    append(matchedText)
                }
            }
            
            currentPosition = match.range.last + 1
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

/**
 * Java语法高亮
 */
private fun highlightJava(code: String, theme: EditorTheme): AnnotatedString {
    // Java关键字
    val keywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
    )
    
    return buildAnnotatedString {
        val text = code
        var currentPosition = 0
        
        // 简单的词法分析
        val regex = Regex("([a-zA-Z_][a-zA-Z0-9_]*)|(/\\*[\\s\\S]*?\\*/)|(//.*)|(\"(?:\\\\.|[^\\\\\"])*\")|('.')")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            // 添加未匹配的文本
            if (match.range.first > currentPosition) {
                append(text.substring(currentPosition, match.range.first))
            }
            
            val matchedText = match.value
            
            when {
                // 多行注释
                matchedText.startsWith("/*") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 单行注释
                matchedText.startsWith("//") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 字符串
                matchedText.startsWith("\"") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 字符
                matchedText.startsWith("'") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 关键字
                matchedText in keywords -> {
                    withStyle(SpanStyle(color = theme.keywordColor)) {
                        append(matchedText)
                    }
                }
                // 普通标识符
                else -> {
                    append(matchedText)
                }
            }
            
            currentPosition = match.range.last + 1
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

/**
 * JavaScript语法高亮
 */
private fun highlightJavaScript(code: String, theme: EditorTheme): AnnotatedString {
    // JavaScript关键字
    val keywords = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
        "do", "else", "export", "extends", "finally", "for", "function", "if", "import", "in",
        "instanceof", "new", "return", "super", "switch", "this", "throw", "try", "typeof", "var",
        "void", "while", "with", "yield", "let", "static", "enum", "await", "async", "true",
        "false", "null", "undefined"
    )
    
    return buildAnnotatedString {
        val text = code
        var currentPosition = 0
        
        // 简单的词法分析
        val regex = Regex("([a-zA-Z_][a-zA-Z0-9_]*)|(/\\*[\\s\\S]*?\\*/)|(//.*)|(\"(?:\\\\.|[^\\\\\"])*\")|('(?:\\\\.|[^\\\\'])*')|(`(?:\\\\.|[^\\\\`])*`)")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            // 添加未匹配的文本
            if (match.range.first > currentPosition) {
                append(text.substring(currentPosition, match.range.first))
            }
            
            val matchedText = match.value
            
            when {
                // 多行注释
                matchedText.startsWith("/*") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 单行注释
                matchedText.startsWith("//") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 模板字符串
                matchedText.startsWith("`") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 字符串
                matchedText.startsWith("\"") || matchedText.startsWith("'") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 关键字
                matchedText in keywords -> {
                    withStyle(SpanStyle(color = theme.keywordColor)) {
                        append(matchedText)
                    }
                }
                // 普通标识符
                else -> {
                    append(matchedText)
                }
            }
            
            currentPosition = match.range.last + 1
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

/**
 * TypeScript语法高亮
 */
private fun highlightTypeScript(code: String, theme: EditorTheme): AnnotatedString {
    // TypeScript关键字
    val keywords = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
        "do", "else", "export", "extends", "finally", "for", "function", "if", "import", "in",
        "instanceof", "new", "return", "super", "switch", "this", "throw", "try", "typeof", "var",
        "void", "while", "with", "yield", "let", "static", "enum", "await", "async", "true",
        "false", "null", "undefined", "interface", "type", "implements", "namespace", "readonly",
        "private", "protected", "public", "abstract", "as", "any", "boolean", "number", "string",
        "symbol", "unknown", "never", "declare"
    )
    
    return buildAnnotatedString {
        val text = code
        var currentPosition = 0
        
        // 简单的词法分析
        val regex = Regex("([a-zA-Z_][a-zA-Z0-9_]*)|(/\\*[\\s\\S]*?\\*/)|(//.*)|(\"(?:\\\\.|[^\\\\\"])*\")|('(?:\\\\.|[^\\\\'])*')|(`(?:\\\\.|[^\\\\`])*`)")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            // 添加未匹配的文本
            if (match.range.first > currentPosition) {
                append(text.substring(currentPosition, match.range.first))
            }
            
            val matchedText = match.value
            
            when {
                // 多行注释
                matchedText.startsWith("/*") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 单行注释
                matchedText.startsWith("//") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 模板字符串
                matchedText.startsWith("`") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 字符串
                matchedText.startsWith("\"") || matchedText.startsWith("'") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 关键字
                matchedText in keywords -> {
                    withStyle(SpanStyle(color = theme.keywordColor)) {
                        append(matchedText)
                    }
                }
                // 普通标识符
                else -> {
                    append(matchedText)
                }
            }
            
            currentPosition = match.range.last + 1
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

/**
 * HTML语法高亮
 */
private fun highlightHtml(code: String, theme: EditorTheme): AnnotatedString {
    return buildAnnotatedString {
        val text = code
        var currentPosition = 0
        
        // HTML标签和属性的正则表达式
        val regex = Regex("</?([a-zA-Z][a-zA-Z0-9:]*)|([a-zA-Z][a-zA-Z0-9:-]*)=|\"([^\"]*)\"|(<!--[\\s\\S]*?-->)")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            // 添加未匹配的文本
            if (match.range.first > currentPosition) {
                append(text.substring(currentPosition, match.range.first))
            }
            
            val matchedText = match.value
            
            when {
                // 注释
                matchedText.startsWith("<!--") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 标签
                matchedText.startsWith("<") -> {
                    withStyle(SpanStyle(color = theme.keywordColor)) {
                        append(matchedText)
                    }
                }
                // 属性
                match.groups[2] != null -> {
                    withStyle(SpanStyle(color = theme.attributeColor)) {
                        append(matchedText)
                    }
                }
                // 属性值
                matchedText.startsWith("\"") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 其他
                else -> {
                    append(matchedText)
                }
            }
            
            currentPosition = match.range.last + 1
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

/**
 * CSS语法高亮
 */
private fun highlightCss(code: String, theme: EditorTheme): AnnotatedString {
    return buildAnnotatedString {
        val text = code
        var currentPosition = 0
        
        // CSS选择器、属性和值的正则表达式
        val regex = Regex("([.#]?[a-zA-Z0-9_-]+)|(:[a-zA-Z-]+)|([{};])|(/\\*[\\s\\S]*?\\*/)|([a-zA-Z-]+\\s*:)")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            // 添加未匹配的文本
            if (match.range.first > currentPosition) {
                append(text.substring(currentPosition, match.range.first))
            }
            
            val matchedText = match.value
            
            when {
                // 注释
                matchedText.startsWith("/*") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 选择器
                matchedText.startsWith(".") || matchedText.startsWith("#") || matchedText.matches(Regex("[a-zA-Z0-9_-]+")) -> {
                    withStyle(SpanStyle(color = theme.selectorColor)) {
                        append(matchedText)
                    }
                }
                // 伪类
                matchedText.startsWith(":") -> {
                    withStyle(SpanStyle(color = theme.keywordColor)) {
                        append(matchedText)
                    }
                }
                // 属性
                matchedText.contains(":") && !matchedText.startsWith(":") -> {
                    withStyle(SpanStyle(color = theme.attributeColor)) {
                        append(matchedText)
                    }
                }
                // 分隔符
                matchedText == "{" || matchedText == "}" || matchedText == ";" -> {
                    append(matchedText)
                }
                // 其他
                else -> {
                    append(matchedText)
                }
            }
            
            currentPosition = match.range.last + 1
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

/**
 * XML语法高亮
 */
private fun highlightXml(code: String, theme: EditorTheme): AnnotatedString {
    return buildAnnotatedString {
        val text = code
        var currentPosition = 0
        
        // XML标签和属性的正则表达式
        val regex = Regex("</?([a-zA-Z][a-zA-Z0-9:]*)|([a-zA-Z][a-zA-Z0-9:-]*)=|\"([^\"]*)\"|(<!--[\\s\\S]*?-->)|(<\\?[\\s\\S]*?\\?>)")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            // 添加未匹配的文本
            if (match.range.first > currentPosition) {
                append(text.substring(currentPosition, match.range.first))
            }
            
            val matchedText = match.value
            
            when {
                // 处理指令
                matchedText.startsWith("<?") -> {
                    withStyle(SpanStyle(color = theme.processingColor)) {
                        append(matchedText)
                    }
                }
                // 注释
                matchedText.startsWith("<!--") -> {
                    withStyle(SpanStyle(color = theme.commentColor)) {
                        append(matchedText)
                    }
                }
                // 标签
                matchedText.startsWith("<") -> {
                    withStyle(SpanStyle(color = theme.keywordColor)) {
                        append(matchedText)
                    }
                }
                // 属性
                match.groups[2] != null -> {
                    withStyle(SpanStyle(color = theme.attributeColor)) {
                        append(matchedText)
                    }
                }
                // 属性值
                matchedText.startsWith("\"") -> {
                    withStyle(SpanStyle(color = theme.stringColor)) {
                        append(matchedText)
                    }
                }
                // 其他
                else -> {
                    append(matchedText)
                }
            }
            
            currentPosition = match.range.last + 1
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

/**
 * JSON语法高亮
 */
private fun highlightJson(code: String, theme: EditorTheme): AnnotatedString {
    return buildAnnotatedString {
        val text = code
        var currentPosition = 0
        
        // JSON键、值和分隔符的正则表达式
        val regex = Regex("\"([^\"]*)\"|([{\\[\\]},:])|([0-9]+(?:\\.[0-9]+)?)|\\b(true|false|null)\\b")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            // 添加未匹配的文本
            if (match.range.first > currentPosition) {
                append(text.substring(currentPosition, match.range.first))
            }
            
            val matchedText = match.value
            
            when {
                // 字符串
                matchedText.startsWith("\"") -> {
                    // 检查是否是键
                    val isKey = text.substring(match.range.last + 1).trimStart().startsWith(":")
                    if (isKey) {
                        withStyle(SpanStyle(color = theme.attributeColor)) {
                            append(matchedText)
                        }
                    } else {
                        withStyle(SpanStyle(color = theme.stringColor)) {
                            append(matchedText)
                        }
                    }
                }
                // 数字
                matchedText.matches(Regex("[0-9]+(\\.[0-9]+)?")) -> {
                    withStyle(SpanStyle(color = theme.numberColor)) {
                        append(matchedText)
                    }
                }
                // 布尔值和null
                matchedText in setOf("true", "false", "null") -> {
                    withStyle(SpanStyle(color = theme.keywordColor)) {
                        append(matchedText)
                    }
                }
                // 分隔符
                else -> {
                    append(matchedText)
                }
            }
            
            currentPosition = match.range.last + 1
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
}

/**
 * Markdown语法高亮
 */
private fun highlightMarkdown(code: String, theme: EditorTheme): AnnotatedString {
    return buildAnnotatedString {
        val lines = code.lines()
        
        for ((index, line) in lines.withIndex()) {
            when {
                // 标题
                line.startsWith("#") -> {
                    val headerLevel = line.takeWhile { it == '#' }.length
                    withStyle(SpanStyle(color = theme.headingColor)) {
                        append(line.substring(0, headerLevel))
                    }
                    append(line.substring(headerLevel))
                }
                // 引用
                line.startsWith(">") -> {
                    withStyle(SpanStyle(color = theme.quoteColor)) {
                        append(">")
                    }
                    append(line.substring(1))
                }
                // 列表
                line.matches(Regex("^\\s*[*+-]\\s.*")) -> {
                    val listMarkerIndex = line.indexOfFirst { it in listOf('*', '+', '-') }
                    append(line.substring(0, listMarkerIndex))
                    withStyle(SpanStyle(color = theme.listItemColor)) {
                        append(line[listMarkerIndex])
                    }
                    append(line.substring(listMarkerIndex + 1))
                }
                // 有序列表
                line.matches(Regex("^\\s*[0-9]+\\.\\s.*")) -> {
                    val dotIndex = line.indexOf('.')
                    withStyle(SpanStyle(color = theme.listItemColor)) {
                        append(line.substring(0, dotIndex + 1))
                    }
                    append(line.substring(dotIndex + 1))
                }
                // 代码块
                line.startsWith("```") -> {
                    withStyle(SpanStyle(color = theme.codeColor)) {
                        append(line)
                    }
                }
                // 普通文本
                else -> {
                    append(line)
                }
            }
            
            // 添加换行符，除了最后一行
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
} 
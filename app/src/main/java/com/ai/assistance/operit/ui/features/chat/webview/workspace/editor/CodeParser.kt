package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.graphics.Color
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 代码解析器，用于语法高亮
 */
class CodeParser(private val codeText: ColorsText) : Runnable {
    companion object {
        private val parserThreadPool: ExecutorService = Executors.newSingleThreadExecutor()
        
        // 定义语法高亮颜色
        val KEYWORD_COLOR = Color.parseColor("#FF6188")
        val STRING_COLOR = Color.parseColor("#FFD866")
        val COMMENT_COLOR = Color.parseColor("#75715E")
        val NUMBER_COLOR = Color.parseColor("#AB9DF2")
        val FUNCTION_COLOR = Color.parseColor("#A9DC76")
        val DEFAULT_COLOR = Color.parseColor("#F8F8F2")

        private val JS_KEYWORDS = setOf(
            "function", "var", "let", "const", "if", "else", "for", "while", "do", "switch",
            "case", "break", "continue", "return", "try", "catch", "finally", "throw",
            "new", "delete", "typeof", "instanceof", "void", "this", "super", "class",
            "extends", "import", "export", "default", "async", "await", "true", "false", "null", "undefined"
        )
        private val KOTLIN_KEYWORDS = setOf(
            "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for",
            "null", "true", "false", "is", "in", "throw", "return", "break", "continue", "object",
            "if", "else", "while", "do", "try", "when", "interface", "typeof", "by", "catch",
            "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import",
            "init", "param", "property", "receiver", "set", "setparam", "where", "actual",
            "abstract", "annotation", "companion", "const", "crossinline", "data", "enum",
            "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit",
            "noinline", "open", "operator", "out", "override", "private", "protected", "public",
            "reified", "sealed", "suspend", "tailrec", "vararg"
        )
        private val JAVA_KEYWORDS = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null"
        )
        private val HTML_TAGS = setOf(
            "html", "head", "title", "body", "div", "p", "span", "a", "img", "ul", "ol", "li",
            "h1", "h2", "h3", "h4", "h5", "h6", "strong", "em", "br", "hr", "meta", "link",
            "script", "style", "table", "tr", "td", "th", "form", "input", "button", "textarea",
            "select", "option", "label"
        )
    }

    private var language = "javascript"
    private var running = false
    private var reparse = false
    
    // 代码变化的位置信息
    private var start = 0
    private var before = 0
    private var count = 0

    /**
     * 解析代码
     */
    fun parse(start: Int, before: Int, count: Int) {
        if (running) {
            reparse = true
            return
        }
        running = true
        this.start = start
        this.before = before
        this.count = count
        parserThreadPool.execute(this)
    }

    fun setLanguage(lang: String) {
        this.language = lang.lowercase()
        // 重新解析以应用新的高亮规则
        parse(0, codeText.length(), codeText.length())
    }

    private fun getKeywords(): Set<String> {
        return when (language) {
            "kotlin" -> KOTLIN_KEYWORDS
            "java" -> JAVA_KEYWORDS
            "javascript", "typescript" -> JS_KEYWORDS
            else -> emptySet()
        }
    }

    override fun run() {
        try {
            // 获取颜色数组
            val codeColors = codeText.getCodeColors()
            
            // 移动颜色位置
            if (count > before) {
                // 添加内容，右移动
                val offset = count - before
                for (i in codeColors.size - 1 downTo start + offset + 1) {
                    if (reparse) break
                    if (i - offset >= 0) {
                        codeColors[i] = codeColors[i - offset]
                    }
                }
            } else {
                // 删除内容，左移动
                val offset = before - count
                for (i in start until codeColors.size - offset) {
                    if (reparse) break
                    if (i + offset < codeColors.size) {
                        codeColors[i] = codeColors[i + offset]
                    }
                }
            }
            
            if (running) {
                codeText.postInvalidate()
            }
            
            val keywords = getKeywords()
            // 简单的语法高亮逻辑
            val text = codeText.text.toString()
            var i = 0
            while (i < text.length && running) {
                if (reparse) break
                if (i >= codeColors.size) break // 添加边界检查

                // 优先处理HTML，因为它有独特的语法结构
                if (language == "html" && text[i] == '<') {
                    if (i < codeColors.size) codeColors[i] = KEYWORD_COLOR // Color '<'
                    val tagStart = i + 1
                    var tagEnd = text.indexOf('>', tagStart)
                    if (tagEnd == -1) tagEnd = text.length

                    // 查找标签名
                    var tagNameEnd = tagStart
                    while (tagNameEnd < tagEnd && !Character.isWhitespace(text[tagNameEnd]) && text[tagNameEnd] != '>') {
                        tagNameEnd++
                    }

                    var tagName = text.substring(tagStart, tagNameEnd).removePrefix("/")
                    if (HTML_TAGS.contains(tagName)) {
                        for (j in tagStart..tagNameEnd) {
                             if (j < codeColors.size) codeColors[j] = KEYWORD_COLOR
                        }
                    }

                    // 为属性和字符串着色
                    var inQuote: Char? = null
                    for (j in tagNameEnd until tagEnd) {
                        if (j >= codeColors.size) break
                        if (inQuote != null) {
                            codeColors[j] = STRING_COLOR
                            if (text[j] == inQuote) {
                                inQuote = null
                            }
                        } else if (text[j] == '\'' || text[j] == '"') {
                            inQuote = text[j]
                            codeColors[j] = STRING_COLOR
                        } else if (text[j] == '=') {
                            codeColors[j] = KEYWORD_COLOR
                        } else {
                            // 属性名
                            codeColors[j] = FUNCTION_COLOR
                        }
                    }


                    if (tagEnd < text.length && tagEnd < codeColors.size) {
                        codeColors[tagEnd] = KEYWORD_COLOR // Color '>'
                    }
                    i = tagEnd + 1
                    continue
                }


                when {
                    // 注释
                    text.startsWith("//", i) -> {
                        val end = text.indexOf('\n', i)
                        val commentEnd = if (end == -1) text.length else end
                        for (j in i until commentEnd) {
                            if (j < codeColors.size) codeColors[j] = COMMENT_COLOR
                        }
                        i = commentEnd
                    }
                    // 多行注释
                    text.startsWith("/*", i) -> {
                        val end = text.indexOf("*/", i + 2)
                        val commentEnd = if (end == -1) text.length else end + 2
                        for (j in i until commentEnd) {
                            if (j < codeColors.size) codeColors[j] = COMMENT_COLOR
                        }
                        i = commentEnd
                    }
                    // 字符串
                    text[i] == '"' || text[i] == '\'' -> {
                        val quote = text[i]
                        if (i < codeColors.size) codeColors[i] = STRING_COLOR
                        i++
                        while (i < text.length && text[i] != quote) {
                            if (text[i] == '\\' && i + 1 < text.length) {
                                if (i < codeColors.size) codeColors[i] = STRING_COLOR
                                if (i + 1 < codeColors.size) codeColors[i + 1] = STRING_COLOR
                                i += 2
                            } else {
                                if (i < codeColors.size) codeColors[i] = STRING_COLOR
                                i++
                            }
                        }
                        if (i < text.length) {
                            if (i < codeColors.size) codeColors[i] = STRING_COLOR
                            i++
                        }
                    }
                    // 数字
                    Character.isDigit(text[i]) -> {
                        val start = i
                        while (i < text.length && (Character.isDigit(text[i]) || text[i] == '.')) {
                            if (i < codeColors.size) codeColors[i] = NUMBER_COLOR
                            i++
                        }
                    }
                    // 关键字或标识符
                    Character.isJavaIdentifierStart(text[i]) -> {
                        val start = i
                        while (i < text.length && Character.isJavaIdentifierPart(text[i])) {
                            i++
                        }
                        val word = text.substring(start, i)
                        val color = if (keywords.contains(word)) {
                            KEYWORD_COLOR
                        } else if (i < text.length && text[i] == '(') {
                            FUNCTION_COLOR
                        } else {
                            DEFAULT_COLOR
                        }
                        for (j in start until i) {
                            if (j < codeColors.size) codeColors[j] = color
                        }
                    }
                    else -> {
                        if (i < codeColors.size) codeColors[i] = DEFAULT_COLOR
                        i++
                    }
                }
            }
            
            codeText.postInvalidate()
            
        } catch (e: Exception) {
            Log.e("CodeParser", "Error parsing code", e)
        }
        
        if (reparse) {
            reparse = false
            parse(start, before, count)
        }
        
        running = false
    }
} 
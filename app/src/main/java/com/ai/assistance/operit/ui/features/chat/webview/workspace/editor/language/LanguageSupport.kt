package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language

import android.graphics.Color

/**
 * 语言支持接口，定义了语言高亮和语法检查的基本功能
 */
interface LanguageSupport {
    /**
     * 获取语言名称
     */
    fun getName(): String
    
    /**
     * 获取语言的关键字集合
     */
    fun getKeywords(): Set<String>
    
    /**
     * 获取语言的内置函数集合
     */
    fun getBuiltInFunctions(): Set<String> = emptySet()
    
    /**
     * 获取语言的内置类型集合
     */
    fun getBuiltInTypes(): Set<String> = emptySet()

    /**
     * 获取语言的内置变量集合
     */
    fun getBuiltInVariables(): Set<String> = emptySet()

    /**
     * 获取语言的注释起始标记
     * 如 // 或
     */
    fun getCommentStart(): List<String>
    
    /**
     * 获取语言的多行注释结束标记
     * 如
     */
    fun getMultiLineCommentEnd(): String?
    
    /**
     * 检查是否是字符串开始
     */
    fun isStringDelimiter(char: Char): Boolean
    
    /**
     * 获取语言的字符串转义字符
     */
    fun getStringEscapeChar(): Char
    
    /**
     * 执行语法检查
     * @param code 要检查的代码
     * @return 错误信息列表
     */
    fun checkSyntax(code: String): List<SyntaxError>
    
    /**
     * 获取语言的文件扩展名
     */
    fun getFileExtensions(): List<String>
    
    /**
     * 语法错误信息类
     */
    data class SyntaxError(
        val position: Int,
        val message: String,
        val line: Int,
        val column: Int
    )
    
    /**
     * 语法高亮颜色
     */
    companion object {
        // Monokai Pro Theme Colors - A vibrant and high-contrast theme
        val KEYWORD_COLOR = Color.parseColor("#FF6188")      // Pink for keywords and operators
        val FUNCTION_COLOR = Color.parseColor("#A9DC76")     // Green for functions
        val STRING_COLOR = Color.parseColor("#FFD866")       // Yellow for strings
        val NUMBER_COLOR = Color.parseColor("#AB9DF2")       // Purple for numbers
        val COMMENT_COLOR = Color.parseColor("#75715E")      // Grey for comments
        val TYPE_COLOR = Color.parseColor("#78DCE8")         // Cyan for types/classes
        val VARIABLE_COLOR = Color.parseColor("#9CDCFE")     // Light blue for variables
        val OPERATOR_COLOR = Color.parseColor("#FF6188")     // Pink, same as keywords
        val DEFAULT_COLOR = Color.parseColor("#F8F8F2")      // Off-white for default text
    }
} 
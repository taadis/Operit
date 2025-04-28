package com.ai.assistance.operit.ui.common.displays

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.scilab.forge.jlatexmath.ParseException
import ru.noties.jlatexmath.JLatexMathDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.text.HtmlCompat

/**
 * An integrated Markdown and LaTeX renderer composable that renders both standard Markdown features
 * and LaTeX equations in the same content.
 *
 * This composable supports:
 * - All standard Markdown features (headings, lists, bold, italic, links, etc.)
 * - LaTeX equations embedded in Markdown content using:
 *   - Inline equations with `$...$` or `\\(...\\)`
 *   - Block equations with `$$...$$` or `\\[...\\]`
 *   - Custom block equations with `[...]` 
 *   - Math expressions with recognized symbols like subscripts, integrals, etc.
 *
 * The implementation uses Markwon for Markdown rendering and JLatexMath for LaTeX rendering,
 * combining them seamlessly in a single TextView.
 */
@Composable
fun MarkdownTextComposable(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    isSelectable: Boolean = true,
    onLinkClicked: ((String) -> Unit)? = null
) {
    val customTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = if (fontSize != TextUnit.Unspecified) fontSize else MaterialTheme.typography.bodyMedium.fontSize,
        // Use null coalescence for textAlign as it's nullable
        textAlign = textAlign ?: MaterialTheme.typography.bodyMedium.textAlign
    )
    
    // Always use the integrated renderer for both Markdown and LaTeX
        IntegratedMarkdownLatexRenderer(
            content = text,
            textColor = textColor,
            modifier = modifier.fillMaxWidth(),
            textStyle = customTextStyle,
            fontSize = fontSize,
            textAlign = textAlign,
        isSelectable = isSelectable,
        onLinkClicked = onLinkClicked
    )
}

/**
 * A composable that renders both Markdown and LaTeX content in the same TextView.
 * This handles all markdown content, with or without LaTeX equations.
 */
@Composable
private fun IntegratedMarkdownLatexRenderer(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    isSelectable: Boolean = true,
    onLinkClicked: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val latexSize = with(density) { 
        if (fontSize != TextUnit.Unspecified) {
            fontSize.toPx() 
        } else {
            textStyle.fontSize.toPx()
        }
    }
    val colorInt = textColor.toArgb()

    // Create Markwon instance with all needed plugins
    val markwon = remember { 
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES))
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .apply {
                onLinkClicked?.let { linkClickHandler ->
                    usePlugin(object : AbstractMarkwonPlugin() {
                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.linkResolver { _, link ->
                                linkClickHandler(link)
                            }
                        }
                    })
                }
            }
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                // Set up the TextView
                setTextColor(colorInt)
                setLinkTextColor(colorInt)
                setLineSpacing(0f, 1.2f) // Set line spacing multiplier to 1.2x
                if (textAlign != null) {
                    textAlignment = when (textAlign) {
                        TextAlign.Left, TextAlign.Start -> View.TEXT_ALIGNMENT_TEXT_START
                        TextAlign.Right, TextAlign.End -> View.TEXT_ALIGNMENT_TEXT_END
                        TextAlign.Center -> View.TEXT_ALIGNMENT_CENTER
                        else -> View.TEXT_ALIGNMENT_TEXT_START
                    }
                }
                
                if (fontSize != TextUnit.Unspecified) {
                    this.textSize = fontSize.value
                } else if (textStyle.fontSize != TextUnit.Unspecified) {
                    this.textSize = textStyle.fontSize.value
                }
                
                // Enable link clicking and text selection
                if (isSelectable) {
                    setTextIsSelectable(true)
                }
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            try {
                // 首先对内容进行预处理，简化后续的渲染过程
                val preprocessed = preprocessLatexInMarkdown(content)
                
                // 让Markwon渲染处理后的内容
                val renderedMarkdown = markwon.toMarkdown(preprocessed)
                
                // 在渲染后的内容中查找和替换LaTeX表达式
                val finalSpannable = renderLatexInMarkdown(
                    renderedMarkdown, 
                    textView, 
                    colorInt, 
                    latexSize
                )
                
                textView.text = finalSpannable
                
            } catch (e: Exception) {
                Log.e("MarkdownLatex", "Error rendering content: ${e.message}", e)
                // 发生错误时，至少显示原始文本
                textView.text = markwon.toMarkdown(content)
            }
        }
    )
}

/**
 * 预处理内容，确保LaTeX表达式能被正确渲染
 * 使用直接字符遍历而非正则表达式，提高性能
 */
private fun preprocessLatexInMarkdown(content: String): String {
    val result = StringBuilder(content.length + 50) // 预分配一些额外空间
    
    // 存储已知的Markdown链接位置
    val markdownLinkRanges = mutableListOf<IntRange>()
    
    // 存储处理过程中的各种状态
    var i = 0
    var isInsideCodeBlock = false
    var isInsideInlineCode = false
    var isInsideLatexBlock = false
    var latexStartType = "" // 记录LaTeX开始分隔符类型: $$, $, \[, \(, [
    var latexStartPos = -1
    
    // 记录方括号状态，用于检测Markdown链接
    var squareBracketStartPos = -1
    var hasPotentialMdLink = false
    
    // 快速检查是否有可能是Markdown链接的起始
    var lastExclamationPos = -1
    
    // 第一次遍历：标记所有Markdown链接
    while (i < content.length) {
        val c = content[i]
        
        // 检测代码块，避免在代码块内处理LaTeX
        if (i + 2 < content.length && c == '`' && content[i+1] == '`' && content[i+2] == '`') {
            isInsideCodeBlock = !isInsideCodeBlock
            i += 3
            continue
        }
        
        // 检测行内代码，避免在代码内处理LaTeX
        if (!isInsideCodeBlock && c == '`') {
            isInsideInlineCode = !isInsideInlineCode
            i++
            continue
        }
        
        // 在代码块或行内代码内，跳过LaTeX处理
        if (isInsideCodeBlock || isInsideInlineCode) {
            i++
            continue
        }
        
        // 记录感叹号位置（用于检测图片语法 ![...](...)）
        if (c == '!') {
            lastExclamationPos = i
        }
        
        // 检测方括号开始
        if (c == '[') {
            squareBracketStartPos = i
            hasPotentialMdLink = (lastExclamationPos == i - 1) // 可能是图片语法的开始
        } 
        // 检测方括号结束，后跟圆括号开始（确认为Markdown链接或图片）
        else if (c == ']' && squareBracketStartPos != -1 && i + 1 < content.length && content[i+1] == '(') {
            // 查找对应的圆括号结束位置
            var parenDepth = 0
            var closeParenPos = -1
            
            for (j in i + 1 until content.length) {
                when (content[j]) {
                    '(' -> parenDepth++
                    ')' -> {
                        parenDepth--
                        if (parenDepth == 0) {
                            closeParenPos = j
                            break
                        }
                    }
                }
            }
            
            if (closeParenPos != -1) {
                // 确认为Markdown链接，记录整个链接范围
                val linkStart = if (hasPotentialMdLink) lastExclamationPos else squareBracketStartPos
                markdownLinkRanges.add(linkStart..closeParenPos)
                
                // 直接跳到链接结束位置
                i = closeParenPos + 1
                squareBracketStartPos = -1
                hasPotentialMdLink = false
                continue
            }
        }
        
        i++
    }
    
    // 重置索引，开始第二次遍历，实际处理文本
    i = 0
    
    // 辅助函数：检查当前位置是否在Markdown链接内
    fun isInsideMarkdownLink(pos: Int): Boolean {
        return markdownLinkRanges.any { pos in it }
    }
    
    // 辅助函数：处理找到的LaTeX块
    fun processLatexBlock(startPos: Int, endPos: Int, startDelim: String, endDelim: String, content: String): String {
        // 提取LaTeX内容（不含分隔符）
        val latexContent = content.substring(
            startPos + startDelim.length,
            endPos - endDelim.length + 1
        )
        
        // 根据分隔符类型检查是否真的是LaTeX
        if ((startDelim == "[" && endDelim == "]") || (startDelim == "(" && endDelim == ")")) {
            if (!isLikelyLatexContent(latexContent)) {
                return content.substring(startPos, endPos + 1) // 原样返回
            }
        }
        
        // 清理和平衡LaTeX内容
        val cleanedContent = latexContent
            .replace("\n", " ") // 换行符替换为空格
            .replace("\\\\", " \\\\ ") // 确保换行命令前后有空格
            .replace(Regex("\\s+"), " ") // 多个空白符替换为单个空格
            .trim() // 去除开头和结尾的空白
            .let { balanceLeftRightCommands(it) } // 平衡\left和\right命令
        
        // 根据LaTeX类型选择合适的输出格式
        return when (startDelim) {
            "$$" -> "$$${cleanedContent}$$"
            "$" -> "$${cleanedContent}$"
            "\\[" -> "\\[${cleanedContent}\\]"
            "\\(" -> "\\(${cleanedContent}\\)"
            "[" -> {
                if (cleanedContent.contains("\\begin{cases}") || cleanedContent.contains("\\end{cases}")) {
                    "$$${cleanedContent}$$" // 复杂的情况使用块级公式
                } else {
                    "$${cleanedContent}$" // 简单公式使用行内公式
                }
            }
            "(" -> {
                // 小括号格式转换为标准LaTeX，使用行内公式格式
                "$${cleanedContent}$"
            }
            else -> "$${cleanedContent}$" // 默认使用行内公式格式
        }
    }
    
    // 第二次遍历：处理文本内容
    while (i < content.length) {
        // 如果当前位置在已知的Markdown链接内，直接复制字符而不处理
        if (isInsideMarkdownLink(i)) {
            result.append(content[i])
            i++
            continue
        }
        
        // 处理代码块标记
        if (i + 2 < content.length && content[i] == '`' && content[i+1] == '`' && content[i+2] == '`') {
            isInsideCodeBlock = !isInsideCodeBlock
            result.append("```")
            i += 3
            continue
        }
        
        // 处理行内代码标记
        if (!isInsideCodeBlock && content[i] == '`') {
            isInsideInlineCode = !isInsideInlineCode
            result.append('`')
            i++
            continue
        }
        
        // 在代码块或行内代码内，原样复制字符
        if (isInsideCodeBlock || isInsideInlineCode) {
            result.append(content[i])
            i++
            continue
        }
        
        // 检测LaTeX块开始
        if (!isInsideLatexBlock) {
            // 检查各种LaTeX分隔符
            when {
                // 块级公式: $$...$$
                i + 1 < content.length && content[i] == '$' && content[i+1] == '$' -> {
                    isInsideLatexBlock = true
                    latexStartType = "$$"
                    latexStartPos = i
                    i += 2
                }
                // 行内公式: $...$
                content[i] == '$' -> {
                    isInsideLatexBlock = true
                    latexStartType = "$"
                    latexStartPos = i
                    i++
                }
                // 块级公式: \[...\]
                i + 1 < content.length && content[i] == '\\' && content[i+1] == '[' -> {
                    isInsideLatexBlock = true
                    latexStartType = "\\["
                    latexStartPos = i
                    i += 2
                }
                // 行内公式: \(...\)
                i + 1 < content.length && content[i] == '\\' && content[i+1] == '(' -> {
                    isInsideLatexBlock = true
                    latexStartType = "\\("
                    latexStartPos = i
                    i += 2
                }
                // 可能的LaTeX表达式: [...] (需要后续检查内容)
                content[i] == '[' && !isInsideMarkdownLink(i) -> {
                    // 先记录位置，稍后再判断内容是否为LaTeX
                    isInsideLatexBlock = true
                    latexStartType = "["
                    latexStartPos = i
                    i++
                }
                // 可能的LaTeX表达式: (...) - 直接处理而不是通过processParenthesisLatex
                content[i] == '(' && !isInsideMarkdownLink(i) -> {
                    // 检查是否可能是LaTeX内容（而不是普通小括号）
                    if (i + 1 < content.length) {
                        val nextChar = content[i + 1]
                        // 快速检查是否可能是LaTeX表达式
                        val mightBeLaTeX = nextChar == '\\' || nextChar == '{' || 
                                         (nextChar.isLetter() && nextChar.isLowerCase()) || 
                                         "\\{}_^".contains(nextChar)
                        
                        if (mightBeLaTeX) {
                            isInsideLatexBlock = true
                            latexStartType = "("
                            latexStartPos = i
                            i++
                            continue
                        }
                    }
                    // 不是LaTeX，原样添加
                    result.append(content[i])
                    i++
                }
                else -> {
                    result.append(content[i])
                    i++
                }
            }
        } 
        // 在LaTeX块内寻找结束分隔符
        else {
            val endFound = when (latexStartType) {
                "$$" -> i + 1 < content.length && content[i] == '$' && content[i+1] == '$'
                "$" -> content[i] == '$'
                "\\[" -> i + 1 < content.length && content[i] == '\\' && content[i+1] == ']'
                "\\(" -> i + 1 < content.length && content[i] == '\\' && content[i+1] == ')'
                "[" -> content[i] == ']'
                "(" -> content[i] == ')'
                else -> false
            }
            
            if (endFound) {
                // 根据分隔符类型确定结束位置
                val endPos = when (latexStartType) {
                    "$$", "\\[", "\\(" -> i + 1  // 双字符分隔符
                    else -> i  // 单字符分隔符
                }
                
                // 获取处理后的LaTeX块
                val endDelim = when (latexStartType) {
                    "$$" -> "$$"
                    "$" -> "$"
                    "\\[" -> "\\]"
                    "\\(" -> "\\)"
                    "[" -> "]"
                    "(" -> ")"
                    else -> ""
                }
                
                val processedLatex = processLatexBlock(latexStartPos, endPos, latexStartType, endDelim, content)
                result.append(processedLatex)
                
                // 更新位置
                i = when (latexStartType) {
                    "$$", "\\[", "\\(" -> endPos + 1
                    else -> endPos + 1
                }
                
                // 重置LaTeX状态
                isInsideLatexBlock = false
                latexStartType = ""
                latexStartPos = -1
            } else {
                // 继续寻找结束分隔符
                i++
            }
        }
    }
    
    // 检查是否有未闭合的LaTeX块
    if (isInsideLatexBlock && latexStartPos != -1) {
        // 将未闭合的LaTeX块原样复制
        result.append(content.substring(latexStartPos))
    }
    
    return result.toString()
}

/**
 * 使用字符遍历方法处理小括号格式的LaTeX表达式
 * 直接遍历字符而非使用StringBuilder的索引操作，提高性能
 */
private fun processParenthesisLatex(text: String): String {
    // 如果文本不包含括号，直接返回
    if (!text.contains('(')) {
        return text
    }
    
    val result = StringBuilder(text.length + 10) // 预分配一些额外空间
    var i = 0
    
    // 跟踪代码块和内联代码状态
    var isInsideCodeBlock = false
    var isInsideInlineCode = false
    
    while (i < text.length) {
        val currentChar = text[i]
        
        // 检测代码块
        if (i + 2 < text.length && currentChar == '`' && text[i+1] == '`' && text[i+2] == '`') {
            isInsideCodeBlock = !isInsideCodeBlock
            result.append("```")
            i += 3
            continue
        }
        
        // 检测行内代码
        if (!isInsideCodeBlock && currentChar == '`') {
            isInsideInlineCode = !isInsideInlineCode
            result.append('`')
            i++
            continue
        }
        
        // 在代码内部，直接复制字符
        if (isInsideCodeBlock || isInsideInlineCode) {
            result.append(currentChar)
            i++
            continue
        }
        
        // 检查是否是可能的LaTeX开始括号
        if (currentChar == '(' && i + 1 < text.length) {
            val openParenPos = i
            val nextChar = text[i + 1]
            
            // 快速检查括号后的字符是否可能是LaTeX
            val mightBeLaTeX = nextChar == '\\' || nextChar == '{' || 
                              (nextChar.isLetter() && nextChar.isLowerCase()) || 
                              "\\{}_^".contains(nextChar)
            
            if (mightBeLaTeX) {
                // 查找对应的闭括号，考虑嵌套
                var parenDepth = 1
                var closeParenPos = -1
                var j = openParenPos + 1
                
                while (j < text.length) {
                    when (text[j]) {
                        '(' -> parenDepth++
                        ')' -> {
                            parenDepth--
                            if (parenDepth == 0) {
                                closeParenPos = j
                                break
                            }
                        }
                    }
                    j++
                }
                
                if (closeParenPos != -1) {
                    // 提取括号内容并检查是否确实是LaTeX
                    val possibleLatex = text.substring(openParenPos + 1, closeParenPos)
                    
                    if (isLikelyLatexContent(possibleLatex)) {
                        // 确认为LaTeX，添加转义的LaTeX分隔符
                        result.append("\\(")
                        result.append(possibleLatex)
                        result.append("\\)")
                        
                        // 跳到闭括号之后
                        i = closeParenPos + 1
                        continue
                    }
                }
            }
        }
        
        // 正常字符，直接添加
        result.append(currentChar)
        i++
    }
    
    return result.toString()
}

/**
 * 检查内容是否可能是LaTeX表达式
 * 更严格地识别LaTeX内容，避免误判普通Markdown语法
 */
private fun isLikelyLatexContent(content: String): Boolean {
    // 如果内容为空或只是普通文本，不可能是LaTeX
    if (content.isBlank() || content.length < 2) {
        return false
    }
    
    // 检查是否是Markdown链接或者图片
    // 如果内容包含URL格式的字符，很可能是链接而非LaTeX
    if (content.contains("http") || content.contains("www.") || content.contains(".com") || 
        content.contains(".org") || content.contains(".net") || content.contains(".io")) {
        return false
    }
    
    // 检查是否包含LaTeX特定命令前缀 - 这是最强有力的证据
    if (content.contains("\\")) {
        return true
    }
    
    // 检查数学符号和结构
    // 至少需要包含以下一种特定的LaTeX数学符号或结构
    val mathStructures = listOf(
        "{", "}", // 花括号
        "^", "_",  // 上标和下标
        "\\frac", "\\sqrt", "\\sum", "\\int", "\\prod", // 常见数学函数
        "\\mathbf", "\\mathrm", "\\mathcal", // 数学字体命令
        "\\begin", "\\end", // 环境
        "\\alpha", "\\beta", "\\gamma", "\\delta", "\\theta", "\\pi", // 希腊字母
        "\\infty", "\\partial", "\\nabla", "\\times", "\\div", // 数学符号
        "=", "+", "-", "*", "/", ">", "<", "\\leq", "\\geq" // 基本运算符
    )
    
    // 检查是否包含多个数学特征 - 一个特征可能是偶然的，但多个的话很可能是LaTeX
    var mathFeatureCount = 0
    for (structure in mathStructures) {
        if (content.contains(structure)) {
            mathFeatureCount++
            if (mathFeatureCount >= 2) { // 至少需要两个数学特征
                return true
            }
        }
    }
    
    // 不太可能是LaTeX表达式
    return false
}

/**
 * 清理LaTeX表达式中的换行符和特殊字符
 */
private fun cleanLatexExpression(latex: String): String {
    var cleaned = latex
    
    // 提取LaTeX内容（不含分隔符）
    val content = when {
        cleaned.startsWith("$$") && cleaned.endsWith("$$") -> 
            cleaned.substring(2, cleaned.length - 2)
        cleaned.startsWith("$") && cleaned.endsWith("$") -> 
            cleaned.substring(1, cleaned.length - 1)
        cleaned.startsWith("\\[") && cleaned.endsWith("\\]") -> 
            cleaned.substring(2, cleaned.length - 2)
        cleaned.startsWith("\\(") && cleaned.endsWith("\\)") -> 
            cleaned.substring(2, cleaned.length - 2)
        cleaned.startsWith("[") && cleaned.endsWith("]") -> 
            cleaned.substring(1, cleaned.length - 1)
        cleaned.startsWith("(") && cleaned.endsWith(")") ->
            cleaned.substring(1, cleaned.length - 1)
        else -> cleaned
    }
    
    // 清理内容中的换行符和多余空白
    var cleanedContent = content
        .replace("\n", " ") // 换行符替换为空格
        .replace("\\\\", " \\\\ ") // 确保换行命令前后有空格
        .replace(Regex("\\s+"), " ") // 多个空白符替换为单个空格
        .trim() // 去除开头和结尾的空白
    
    // 检测并修复不匹配的 \left 和 \right 命令
    cleanedContent = balanceLeftRightCommands(cleanedContent)
    
    // 根据原始分隔符重新组装
    cleaned = when {
        latex.startsWith("$$") && latex.endsWith("$$") -> "$$${cleanedContent}$$"
        latex.startsWith("$") && latex.endsWith("$") -> "$${cleanedContent}$"
        latex.startsWith("\\[") && latex.endsWith("\\]") -> "\\[${cleanedContent}\\]"
        latex.startsWith("\\(") && latex.endsWith("\\)") -> "\\(${cleanedContent}\\)"
        latex.startsWith("[") && latex.endsWith("]") -> "[${cleanedContent}]"
        latex.startsWith("(") && latex.endsWith(")") -> "\\(${cleanedContent}\\)" // 转换为标准LaTeX格式
        else -> cleanedContent
    }
    
    return cleaned
}

/**
 * 平衡LaTeX中的\left和\right命令
 */
private fun balanceLeftRightCommands(latex: String): String {
    // 检查是否有不匹配的\left和\right命令
    val leftCount = "\\\\left".toRegex().findAll(latex).count()
    val rightCount = "\\\\right".toRegex().findAll(latex).count()
    
    var balanced = latex
    
    if (leftCount > rightCount) {
        // 有多余的\left，添加缺失的\right
        for (i in 1..(leftCount - rightCount)) {
            balanced += " \\right."
        }
        Log.d("MarkdownLatex", "修复了不匹配的\\left命令，添加了${leftCount - rightCount}个\\right.")
    } else if (rightCount > leftCount) {
        // 有多余的\right，尝试修复它们
        // 如果有单独的\right，将其替换为空或添加相应的\left
        val pattern = "(^|[^\\\\])\\\\right".toRegex()
        var matches = pattern.findAll(balanced).toList()
        
        // 只处理多余的\right
        val toReplace = matches.take(rightCount - leftCount)
        
        // 创建StringBuilder以便进行多次替换
        val builder = StringBuilder(balanced)
        
        // 从后向前替换，以避免索引问题
        for (match in toReplace.reversed()) {
            // 获取匹配的范围（不包括第一个捕获组）
            val startIndex = match.range.first + match.groupValues[1].length
            val endIndex = startIndex + 6 // "\\right"的长度
            
            // 在原位置前插入\left.
            builder.insert(startIndex, "\\left. ")
        }
        
        balanced = builder.toString()
        Log.d("MarkdownLatex", "修复了不匹配的\\right命令，添加了${rightCount - leftCount}个\\left.")
    }
    
    // 处理单独出现的\right命令（不在\right.或其他有效形式中）
    val standaloneRight = "\\\\right([^\\.]|$)".toRegex()
    balanced = standaloneRight.replace(balanced) { matchResult ->
        // 将\right替换为\right.以确保有效性
        "\\right." + matchResult.groupValues[1]
    }
    
    return balanced
}

/**
 * 在渲染后的Markdown内容中处理LaTeX表达式
 * 使用字符遍历方法而非正则表达式，提高性能
 */
private fun renderLatexInMarkdown(
    markdownContent: CharSequence,
    textView: TextView,
    @androidx.annotation.ColorInt textColor: Int,
    textSize: Float
): Spannable {
    val spannableContent = SpannableStringBuilder(markdownContent)
    
    // 已处理范围集合，用于防止重复处理
    val processedRanges = mutableSetOf<IntRange>()
    
    // 跟踪LaTeX分隔符的结构
    data class LatexDelimiterInfo(
        val startIndex: Int,
        val endIndex: Int,
        val startDelimiter: String,
        val endDelimiter: String,
        val isBlockFormula: Boolean
    )
    
    // 保存找到的所有LaTeX分隔符
    val foundLatexBlocks = mutableListOf<LatexDelimiterInfo>()
    
    // 用于检测Markdown链接的状态变量
    val potentialMarkdownLinks = mutableListOf<Pair<Int, Int>>() // (startIndex, endIndex)
    
    // 先识别所有可能的Markdown链接，避免误识别为LaTeX
    run {
        var i = 0
        val text = markdownContent.toString()
        var squareBracketStart = -1
        
        while (i < text.length) {
            when {
                // 检测开方括号，可能是链接/图片的开始
                text[i] == '[' -> {
                    squareBracketStart = i
                    i++
                }
                // 检测闭方括号后跟开圆括号，确认为Markdown链接
                squareBracketStart != -1 && text[i] == ']' && i + 1 < text.length && text[i + 1] == '(' -> {
                    // 查找对应的闭圆括号
                    var depth = 0
                    var linkEnd = -1
                    
                    for (j in (i + 1) until text.length) {
                        when (text[j]) {
                            '(' -> depth++
                            ')' -> {
                                depth--
                                if (depth == 0) {
                                    linkEnd = j
                                    break
                                }
                            }
                        }
                    }
                    
                    if (linkEnd != -1) {
                        // 记录整个链接的范围
                        potentialMarkdownLinks.add(Pair(squareBracketStart, linkEnd))
                        i = linkEnd + 1
                        squareBracketStart = -1
                        continue
                    }
                    i++
                }
                else -> i++
            }
        }
    }
    
    // 字符遍历扫描LaTeX块
    var i = 0
    val text = markdownContent.toString()
    var isInsideCodeBlock = false
    var isInsideInlineCode = false
    
    // 辅助函数：检查位置是否在Markdown链接内
    fun isInsideMarkdownLink(pos: Int): Boolean {
        return potentialMarkdownLinks.any { (start, end) -> pos in start..end }
    }
    
    while (i < text.length) {
        // 代码块和代码内容不处理LaTeX
        if (i + 2 < text.length && text[i] == '`' && text[i+1] == '`' && text[i+2] == '`') {
            isInsideCodeBlock = !isInsideCodeBlock
            i += 3
            continue
        }
        
        if (!isInsideCodeBlock && text[i] == '`') {
            isInsideInlineCode = !isInsideInlineCode
            i++
            continue
        }
        
        if (isInsideCodeBlock || isInsideInlineCode || isInsideMarkdownLink(i)) {
            i++
            continue
        }
        
        // 检查是否是LaTeX的开始标记
        when {
            // $$...$$
            i + 1 < text.length && text[i] == '$' && text[i+1] == '$' -> {
                val startPos = i
                i += 2  // 跳过开始分隔符
                
                // 寻找对应的结束标记
                var endPos = -1
                while (i + 1 < text.length) {
                    if (text[i] == '$' && text[i+1] == '$') {
                        endPos = i
                        i += 2  // 跳过结束分隔符
                        break
                    }
                    i++
                }
                
                if (endPos != -1) {
                    // 提取内容，确保真实是LaTeX而不是其他语义
                    val content = text.substring(startPos + 2, endPos)
                    if (content.isNotBlank() && !isInsideMarkdownLink(startPos)) {
                        foundLatexBlocks.add(LatexDelimiterInfo(
                            startPos, endPos + 2,
                            "$$", "$$", true
                        ))
                    }
                }
            }
            // $...$
            text[i] == '$' -> {
                val startPos = i
                i++  // 跳过开始分隔符
                
                // 寻找对应的结束标记
                var endPos = -1
                while (i < text.length) {
                    if (text[i] == '$') {
                        endPos = i
                        i++  // 跳过结束分隔符
                        break
                    }
                    i++
                }
                
                if (endPos != -1) {
                    // 提取内容，确保真实是LaTeX而不是价格标记
                    val content = text.substring(startPos + 1, endPos)
                    if (content.isNotBlank() && !isInsideMarkdownLink(startPos)) {
                        foundLatexBlocks.add(LatexDelimiterInfo(
                            startPos, endPos + 1,
                            "$", "$", false
                        ))
                    }
                }
            }
            // \[...\]
            i + 1 < text.length && text[i] == '\\' && text[i+1] == '[' -> {
                val startPos = i
                i += 2  // 跳过开始分隔符
                
                // 寻找对应的结束标记
                var endPos = -1
                while (i + 1 < text.length) {
                    if (text[i] == '\\' && text[i+1] == ']') {
                        endPos = i
                        i += 2  // 跳过结束分隔符
                        break
                    }
                    i++
                }
                
                if (endPos != -1 && !isInsideMarkdownLink(startPos)) {
                    foundLatexBlocks.add(LatexDelimiterInfo(
                        startPos, endPos + 2,
                        "\\[", "\\]", true
                    ))
                }
            }
            // \(...\)
            i + 1 < text.length && text[i] == '\\' && text[i+1] == '(' -> {
                val startPos = i
                i += 2  // 跳过开始分隔符
                
                // 寻找对应的结束标记
                var endPos = -1
                while (i + 1 < text.length) {
                    if (text[i] == '\\' && text[i+1] == ')') {
                        endPos = i
                        i += 2  // 跳过结束分隔符
                        break
                    }
                    i++
                }
                
                if (endPos != -1 && !isInsideMarkdownLink(startPos)) {
                    foundLatexBlocks.add(LatexDelimiterInfo(
                        startPos, endPos + 2,
                        "\\(", "\\)", false
                    ))
                }
            }
            // [...] - 特殊格式，只有在内容确实是LaTeX时才处理
            text[i] == '[' && !isInsideMarkdownLink(i) -> {
                val startPos = i
                i++  // 跳过开始分隔符
                
                // 先寻找对应的结束方括号
                var squareDepth = 1
                var endPos = -1
                
                while (i < text.length) {
                    when (text[i]) {
                        '[' -> squareDepth++
                        ']' -> {
                            squareDepth--
                            if (squareDepth == 0) {
                                endPos = i
                                i++  // 跳过结束分隔符
                                break
                            }
                        }
                    }
                    i++
                }
                
                if (endPos != -1) {
                    // 如果闭方括号后面紧跟'('，这可能是Markdown链接的一部分
                    if (i < text.length && text[i] == '(') {
                        // 跳过，这是Markdown链接，不是LaTeX
                        continue
                    }
                    
                    // 检查方括号内的内容是否为LaTeX
                    val bracketContent = text.substring(startPos + 1, endPos)
                    if (isLikelyLatexContent(bracketContent)) {
                        foundLatexBlocks.add(LatexDelimiterInfo(
                            startPos, endPos + 1,
                            "[", "]", false
                        ))
                    }
                }
            }
            // (...) - 行内小括号，检查是否包含LaTeX表达式
            text[i] == '(' && !isInsideMarkdownLink(i) -> {
                val startPos = i
                i++  // 跳过开始分隔符
                
                // 先检查下一个字符是否可能是LaTeX表达式的开始
                if (i < text.length) {
                    val nextChar = text[i]
                    val mightBeLaTeX = nextChar == '\\' || nextChar == '{' || 
                                    (nextChar.isLetter() && nextChar.isLowerCase()) || 
                                    "\\{}_^".contains(nextChar)
                    
                    if (!mightBeLaTeX) {
                        // 不太可能是LaTeX，继续查找
                        continue
                    }
                }
                
                // 查找对应的闭括号
                var parenDepth = 1
                var endPos = -1
                
                while (i < text.length) {
                    when (text[i]) {
                        '(' -> parenDepth++
                        ')' -> {
                            parenDepth--
                            if (parenDepth == 0) {
                                endPos = i
                                i++  // 跳过结束分隔符
                                break
                            }
                        }
                    }
                    i++
                }
                
                if (endPos != -1) {
                    // 检查内容是否为LaTeX
                    val content = text.substring(startPos + 1, endPos)
                    if (isLikelyLatexContent(content)) {
                        foundLatexBlocks.add(LatexDelimiterInfo(
                            startPos, endPos + 1,
                            "(", ")", false
                        ))
                    }
                }
            }
            else -> i++
        }
    }
    
    // 按起始位置排序所有找到的LaTeX块
    val sortedBlocks = foundLatexBlocks.sortedBy { it.startIndex }
    
    // 处理找到的所有LaTeX块
    for (block in sortedBlocks) {
        val start = block.startIndex
        val end = block.endIndex
        val range = start until end
        
        // 检查是否已处理过该范围或与之重叠的范围
        if (processedRanges.any { it.intersect(range).isNotEmpty() }) {
            continue
        }
        
        // 提取LaTeX表达式
        val expr = text.substring(start, end)
        
        try {
            // 获取公式内容 (去除分隔符)
            val formulaContent = text.substring(
                start + block.startDelimiter.length,
                end - block.endDelimiter.length
            )
            
            // 额外的安全检查：确保公式内容是有效的
            val cleanedFormula = balanceLeftRightCommands(formulaContent.trim())
            
            // 创建LaTeX渲染器
            val builder = JLatexMathDrawable.builder(cleanedFormula)
                .textSize(textSize)
                .color(textColor)
            
            // 为块级公式添加额外的样式
            if (block.isBlockFormula) {
                builder.padding(12) // 块级公式使用更大的内边距
                    .background(0x10000000) // 轻微的背景色
                    .align(JLatexMathDrawable.ALIGN_CENTER) // 居中对齐
            } else {
                builder.padding(4) // 行内公式使用较小的内边距
            }
            
            val drawable = builder.build()
            
            // 设置绘图边界
            drawable.setBounds(
                0, 
                0, 
                drawable.intrinsicWidth, 
                drawable.intrinsicHeight
            )
            
            // 替换文本为LaTeX绘制的Span
            spannableContent.setSpan(
                LatexDrawableSpan(drawable, block.isBlockFormula),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // 标记该范围已处理
            processedRanges.add(range)
            
            // 记录日志
            Log.d("MarkdownLatex", "渲染LaTeX公式: '${cleanedFormula.take(20)}${if (cleanedFormula.length > 20) "..." else ""}'")
            
        } catch (e: ParseException) {
            // 特殊处理ParseException - 尝试修复公式并重新渲染
            try {
                Log.w("MarkdownLatex", "LaTeX解析错误: ${e.message} 尝试修复公式...")
                
                // 提取原始公式内容
                val originalContent = text.substring(
                    start + block.startDelimiter.length,
                    end - block.endDelimiter.length
                )
                
                // 尝试更激进的修复方法
                val fixedContent = repairLatexExpression(originalContent, e.message ?: "")
                
                // 创建修复后的LaTeX渲染器
                val builder = JLatexMathDrawable.builder(fixedContent)
                    .textSize(textSize)
                    .color(textColor)
                
                if (block.isBlockFormula) {
                    builder.padding(12)
                        .background(0x10000000)
                        .align(JLatexMathDrawable.ALIGN_CENTER)
                } else {
                    builder.padding(4)
                }
                
                val drawable = builder.build()
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                
                // 替换文本为修复后的LaTeX Span
                spannableContent.setSpan(
                    LatexDrawableSpan(drawable, block.isBlockFormula),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                // 标记该范围已处理
                processedRanges.add(range)
                
                Log.d("MarkdownLatex", "成功修复并渲染LaTeX公式: '$fixedContent'")
                
            } catch (e2: Exception) {
                // 如果修复也失败，记录错误并继续处理其他表达式
                Log.e("MarkdownLatex", "修复LaTeX失败: ${e2.message}", e2)
                
                // 显示原始文本而不是渲染的LaTeX
                // 这里不做任何操作，将保留原始文本
            }
        } catch (e: Exception) {
            // 处理其他类型的错误
            Log.e("MarkdownLatex", "渲染LaTeX出错: ${e.message} in '${expr.take(30)}...'", e)
            // 保留原始文本
        }
    }
    
    return spannableContent
}

/**
 * 根据错误消息尝试修复LaTeX表达式
 */
private fun repairLatexExpression(latex: String, errorMessage: String): String {
    var repaired = latex
    
    // 处理常见的错误类型
    when {
        // 处理未知符号或命令错误
        errorMessage.contains("Unknown symbol or command") -> {
            val errorPattern = "Unknown symbol or command or predefined TeXFormula: '(.*?)'".toRegex()
            val match = errorPattern.find(errorMessage)
            
            if (match != null) {
                val problematicCommand = match.groupValues[1]
                
                // 根据具体问题进行修复
                when (problematicCommand) {
                    "right" -> {
                        // 修复单独的\right命令
                        if (repaired.contains("\\right") && !repaired.contains("\\left")) {
                            // 添加匹配的\left.
                            repaired = "\\left. " + repaired
                            Log.d("MarkdownLatex", "添加了缺失的\\left命令以匹配\\right")
                        }
                        // 确保\right后有有效的分隔符
                        repaired = repaired.replace("\\right([^\\s.\\)\\]\\}]|$)".toRegex()) { 
                            "\\right." + (it.groupValues[1].takeIf { it.isNotEmpty() } ?: "")
                        }
                    }
                    "left" -> {
                        // 修复单独的\left命令
                        if (repaired.contains("\\left") && !repaired.contains("\\right")) {
                            // 添加匹配的\right.
                            repaired = repaired + " \\right."
                            Log.d("MarkdownLatex", "添加了缺失的\\right命令以匹配\\left")
                        }
                        // 确保\left后有有效的分隔符
                        repaired = repaired.replace("\\left([^\\s.\\(\\[\\{]|$)".toRegex()) { 
                            "\\left." + (it.groupValues[1].takeIf { it.isNotEmpty() } ?: "")
                        }
                    }
                    else -> {
                        // 移除其他未知命令
                        repaired = repaired.replace("\\\\$problematicCommand".toRegex(), "")
                        Log.d("MarkdownLatex", "移除了未知命令: \\$problematicCommand")
                    }
                }
            }
        }
        
        // 处理其他类型的错误
        errorMessage.contains("ParseException") -> {
            // 应用通用的修复方法
            repaired = balanceLeftRightCommands(repaired)
            
            // 检查其他常见错误，如缺少的花括号等
            repaired = repaired.replace("\\\\frac([^{]|$)".toRegex()) { 
                "\\frac{" + it.groupValues[1] + "}{}"
            }
        }
    }
    
    return repaired
}

/**
 * 用于显示LaTeX公式的自定义Span实现
 */
private class LatexDrawableSpan(
    private val drawable: Drawable, 
    private val isBlockFormula: Boolean = false
) : ReplacementSpan() {
    
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val rect = drawable.bounds
        
        if (fm != null) {
            val fontHeight = fm.descent - fm.ascent
            val imageHeight = rect.height()
            
            if (isBlockFormula) {
                // 块级公式使用全高度并添加上下空间
                fm.ascent = -imageHeight
                fm.descent = 0
                fm.top = fm.ascent
                fm.bottom = fm.descent
            } else {
                // 行内公式垂直居中对齐
                val centerY = fm.ascent + fontHeight / 2
                fm.ascent = centerY - imageHeight / 2
                fm.descent = centerY + imageHeight / 2
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
        }
        
        return rect.width()
    }
    
    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        canvas.save()
        
        if (isBlockFormula) {
            // 块级公式绘制在自己的行上
            canvas.translate(x, top.toFloat())
        } else {
            // 行内公式垂直居中对齐
            val fm = paint.fontMetricsInt
            val fontHeight = fm.descent - fm.ascent
            val centerY = y + fm.descent - fontHeight / 2
            val imageHeight = drawable.bounds.height()
            val transY = centerY - imageHeight / 2
            
            canvas.translate(x, transY.toFloat()) // 确保transY是Float类型
        }
        
        drawable.draw(canvas)
        canvas.restore()
    }
}


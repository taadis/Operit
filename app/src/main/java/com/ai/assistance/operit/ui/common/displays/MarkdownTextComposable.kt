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
 * 预处理内容，确保Markdown列表项中的LaTeX表达式能被正确渲染
 */
private fun preprocessLatexInMarkdown(content: String): String {
    var processed = content
    
    // 更复杂的括号匹配方法
    processed = processParenthesisLatex(processed)
    
    // 首先提取所有可能包含LaTeX的块 (支持嵌套的模式匹配)
    // 移除小括号格式的匹配，因为已在上面处理过
    val latexBlockFinder = Regex("(\\$\\$.*?\\$\\$|\\$.*?\\$|\\\\\\[.*?\\\\\\]|\\\\\\(.*?\\\\\\)|\\[((?!\\]\\()[^\\[\\]]+)\\])", RegexOption.DOT_MATCHES_ALL)
    val latexBlocks = mutableListOf<MatchResult>()
    latexBlockFinder.findAll(processed).forEach { 
        latexBlocks.add(it)
    }
    
    // 按照在原文本中的位置排序
    val sortedBlocks = latexBlocks.sortedBy { it.range.first }
    
    // 创建一个StringBuilder来处理处理后的内容
    val resultBuilder = StringBuilder(processed)
    
    // 从后向前处理，以避免修改后影响索引
    for (i in sortedBlocks.indices.reversed()) {
        val match = sortedBlocks[i]
        val start = match.range.first
        val end = match.range.last + 1
        
        val originalLatex = match.value
        
        // 处理LaTeX块，清理换行符和特殊字符
        val cleanedLatex = cleanLatexExpression(originalLatex)
        
        // 如果清理后的内容与原始内容不同，需要替换
        if (cleanedLatex != originalLatex) {
            resultBuilder.replace(start, end, cleanedLatex)
        }
    }
    
    processed = resultBuilder.toString()
    
    // 处理列表项中的LaTeX表达式
    val listItemLatexPattern = Regex("(^|\\n)\\s*[-*+]\\s+.*?(\\$\\$.*?\\$\\$|\\$.*?\\$|\\\\\\[.*?\\\\\\]|\\\\\\(.*?\\\\\\)|\\[((?!\\]\\()[^\\[\\]]+)\\])", RegexOption.DOT_MATCHES_ALL)
    processed = listItemLatexPattern.replace(processed) { matchResult ->
        val fullMatch = matchResult.value
        
        // 检查这是否真的是一个LaTeX表达式，而不是Markdown链接
        if (!fullMatch.contains("](") && !fullMatch.contains("![")) {
            fullMatch
        } else {
            // 这是一个Markdown链接或图片，保持原样
            fullMatch
        }
    }
    
    // 处理[...] 格式的LaTeX块
    processed = processed.replace(Regex("\\[((?!\\]\\().*?)\\]", RegexOption.DOT_MATCHES_ALL)) { matchResult ->
        val content = matchResult.groupValues[1]
        // 清理内容中的换行符和空白符
        val cleanedContent = cleanLatexExpression(content)
        
        if (cleanedContent.contains("\\begin{cases}") || cleanedContent.contains("\\end{cases}")) {
            "$$${cleanedContent}$$"
        } else {
            "$${cleanedContent}$"
        }
    }
    
    return processed
}

/**
 * 使用更细致的方法处理小括号格式的LaTeX表达式
 */
private fun processParenthesisLatex(text: String): String {
    val result = StringBuilder(text)
    var searchIndex = 0
    
    // 使用迭代方法处理所有小括号
    while (searchIndex < result.length) {
        // 寻找可能的LaTeX开始小括号
        val openIndex = result.indexOf('(', searchIndex)
        if (openIndex == -1 || openIndex + 1 >= result.length) {
            break  // 没有找到更多的开括号
        }
        
        // 检查括号内的第一个字符，判断是否为LaTeX表达式
        val firstChar = result[openIndex + 1]
        val isLikelyLatex = firstChar == '\\' || firstChar == '{' || 
                            (firstChar.isLetter() && "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".contains(firstChar))
        
        if (!isLikelyLatex) {
            searchIndex = openIndex + 1
            continue  // 不是LaTeX表达式，继续搜索
        }
        
        // 寻找匹配的闭括号，同时考虑嵌套括号和花括号
        var depth = 1
        var closeIndex = -1
        var i = openIndex + 1
        
        while (i < result.length) {
            val currentChar = result[i]
            
            when (currentChar) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        closeIndex = i
                        break
                    }
                }
            }
            
            i++
        }
        
        if (closeIndex == -1) {
            // 没有找到匹配的闭括号
            searchIndex = openIndex + 1
            continue
        }
        
        // 提取可能的LaTeX内容
        val content = result.substring(openIndex + 1, closeIndex)
        
        // 进行更详细的检查，确认是否为LaTeX表达式
        if (isLikelyLatexContent(content)) {
            // 将小括号替换为LaTeX标准格式 \(...\)
            result.replace(openIndex, openIndex + 1, "\\(")
            // 调整closeIndex，因为替换后字符串长度变化了
            closeIndex += 1
            result.replace(closeIndex, closeIndex + 1, "\\)")
            
            // 更新搜索位置
            searchIndex = closeIndex + 2
        } else {
            // 不是LaTeX表达式，继续搜索
            searchIndex = openIndex + 1
        }
    }
    
    return result.toString()
}

/**
 * 检查内容是否可能是LaTeX表达式
 */
private fun isLikelyLatexContent(content: String): Boolean {
    // 检查一些常见的LaTeX命令和结构
    return content.contains("\\") || 
           content.contains("{") || 
           content.contains("}") ||
           content.contains("^") ||
           content.contains("_") ||
           content.contains("frac") ||
           content.contains("sum") ||
           content.contains("int") ||
           content.contains("sqrt") ||
           // 添加更多LaTeX特定标记的检查
           content.contains("\\mathbf") ||
           content.contains("\\mathrm") ||
           content.contains("\\mathcal") ||
           content.contains("\\begin") ||
           content.contains("\\end") ||
           content.contains("\\alpha") ||
           content.contains("\\beta") ||
           content.contains("\\gamma") ||
           content.contains("\\delta") ||
           content.contains("\\theta") ||
           content.contains("\\pi")
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
 */
private fun renderLatexInMarkdown(
    markdownContent: CharSequence,
    textView: TextView,
    @androidx.annotation.ColorInt textColor: Int,
    textSize: Float
): Spannable {
    val spannableContent = SpannableStringBuilder(markdownContent)
    
    // LaTeX表达式模式，按照优先级排序（从高到低）
    val patterns = listOf(
        Regex("\\$\\$(.*?)\\$\\$", RegexOption.DOT_MATCHES_ALL),  // 块级公式: $$...$$
        Regex("\\\\\\[(.*?)\\\\\\]", RegexOption.DOT_MATCHES_ALL), // 块级公式: \[...\]
        Regex("\\\\\\((.*?)\\\\\\)", RegexOption.DOT_MATCHES_ALL), // 行内公式: \(...\)
        Regex("\\$(.*?)\\$", RegexOption.DOT_MATCHES_ALL)         // 行内公式: $...$
        // 移除小括号模式，因为在预处理阶段已经转换为 \(...\) 格式
    )
    
    // 已处理范围集合，用于防止重复处理
    val processedRanges = mutableSetOf<IntRange>()
    
    // 对每种模式进行处理
    for (pattern in patterns) {
        val matches = pattern.findAll(spannableContent)
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            val range = start..end-1
            
            // 检查是否已处理过该范围或与之重叠的范围
            if (processedRanges.any { it.intersect(range).isNotEmpty() }) {
                continue
            }
            
            // 提取LaTeX表达式
            val expr = match.value
            
            try {
                // 渲染LaTeX表达式
                val isBlockFormula = expr.startsWith("$$") || expr.startsWith("\\[")
                
                // 获取公式内容 (去除分隔符)
                val formulaContent = when {
                    expr.startsWith("$$") && expr.endsWith("$$") -> expr.substring(2, expr.length - 2)
                    expr.startsWith("$") && expr.endsWith("$") -> expr.substring(1, expr.length - 1)
                    expr.startsWith("\\[") && expr.endsWith("\\]") -> expr.substring(2, expr.length - 2)
                    expr.startsWith("\\(") && expr.endsWith("\\)") -> expr.substring(2, expr.length - 2)
                    else -> expr
                }
                
                // 额外的安全检查：确保公式内容是有效的
                val cleanedFormula = balanceLeftRightCommands(formulaContent.trim())
                
                // 创建LaTeX渲染器
                val builder = JLatexMathDrawable.builder(cleanedFormula)
                    .textSize(textSize)
                    .color(textColor)
                
                // 为块级公式添加额外的样式
                if (isBlockFormula) {
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
                    LatexDrawableSpan(drawable, isBlockFormula),
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
                    val originalContent = when {
                        expr.startsWith("$$") && expr.endsWith("$$") -> expr.substring(2, expr.length - 2)
                        expr.startsWith("$") && expr.endsWith("$") -> expr.substring(1, expr.length - 1)
                        expr.startsWith("\\[") && expr.endsWith("\\]") -> expr.substring(2, expr.length - 2)
                        expr.startsWith("\\(") && expr.endsWith("\\)") -> expr.substring(2, expr.length - 2)
                        else -> expr
                    }
                    
                    // 尝试更激进的修复方法
                    val fixedContent = repairLatexExpression(originalContent, e.message ?: "")
                    
                    // 创建修复后的LaTeX渲染器
                    val isBlockFormula = expr.startsWith("$$") || expr.startsWith("\\[")
                    val builder = JLatexMathDrawable.builder(fixedContent)
                        .textSize(textSize)
                        .color(textColor)
                    
                    if (isBlockFormula) {
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
                        LatexDrawableSpan(drawable, isBlockFormula),
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

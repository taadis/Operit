package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/**
 * 代码补全弹出窗口
 * @param suggestions 补全建议列表
 * @param onSuggestionSelected 选中建议的回调
 * @param onDismiss 取消补全的回调
 */
@Composable
fun CodeCompletionPopup(
    suggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            ),
            modifier = Modifier
                .width(280.dp)
                .heightIn(min = 40.dp, max = 300.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(4.dp)
            ) {
                items(suggestions) { suggestion ->
                    CompletionItem(
                        text = suggestion,
                        onClick = { onSuggestionSelected(suggestion) }
                    )
                }
            }
        }
    }
}

/**
 * 单个补全项
 */
@Composable
private fun CompletionItem(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 获取Kotlin语言的补全建议
 */
fun getKotlinSuggestions(code: String, position: Int): List<String> {
    // 简单的Kotlin补全建议
    val commonKeywords = listOf(
        "fun", "val", "var", "if", "else", "when", "for", "while", "return", "class", "object",
        "interface", "override", "private", "protected", "public", "internal", "companion",
        "suspend", "inline", "data", "sealed", "enum"
    )
    
    // 常用方法和属性
    val commonMethods = mapOf(
        "String" to listOf("length", "isEmpty()", "isNotEmpty()", "trim()", "substring()", "replace()", "split()"),
        "List" to listOf("size", "isEmpty()", "contains()", "add()", "remove()", "first()", "last()", "forEach{}"),
        "Map" to listOf("size", "isEmpty()", "containsKey()", "get()", "put()", "remove()", "forEach{}"),
        "File" to listOf("exists()", "isDirectory", "isFile", "name", "path", "delete()", "listFiles()")
    )
    
    // 分析上下文
    val context = getContextBeforeCursor(code, position)
    
    // 如果是点操作，尝试推断类型并提供相应的方法
    if (context.endsWith(".")) {
        val varName = context.substringBeforeLast(".").trim()
        
        // 简单的类型推断
        val type = when {
            varName.endsWith("\"") -> "String"
            varName.endsWith("]") -> "List"
            varName.contains("List") -> "List"
            varName.contains("Map") -> "Map"
            varName.contains("File") -> "File"
            else -> null
        }
        
        return type?.let { commonMethods[it] } ?: emptyList()
    }
    
    // 如果是空格，提供关键字建议
    if (context.endsWith(" ")) {
        return commonKeywords
    }
    
    return emptyList()
}

/**
 * 获取Java语言的补全建议
 */
fun getJavaSuggestions(code: String, position: Int): List<String> {
    // 简单的Java补全建议
    val commonKeywords = listOf(
        "public", "private", "protected", "static", "final", "abstract", "class", "interface",
        "extends", "implements", "void", "return", "if", "else", "for", "while", "do", "switch",
        "case", "break", "continue", "try", "catch", "finally", "throw", "throws", "new", "this",
        "super", "null", "true", "false"
    )
    
    // 常用方法和属性
    val commonMethods = mapOf(
        "String" to listOf("length()", "isEmpty()", "trim()", "substring()", "replace()", "split()"),
        "List" to listOf("size()", "isEmpty()", "contains()", "add()", "remove()", "get()", "iterator()"),
        "Map" to listOf("size()", "isEmpty()", "containsKey()", "get()", "put()", "remove()", "keySet()"),
        "File" to listOf("exists()", "isDirectory()", "isFile()", "getName()", "getPath()", "delete()", "listFiles()")
    )
    
    // 分析上下文
    val context = getContextBeforeCursor(code, position)
    
    // 如果是点操作，尝试推断类型并提供相应的方法
    if (context.endsWith(".")) {
        val varName = context.substringBeforeLast(".").trim()
        
        // 简单的类型推断
        val type = when {
            varName.endsWith("\"") -> "String"
            varName.endsWith("]") -> "List"
            varName.contains("List") -> "List"
            varName.contains("Map") -> "Map"
            varName.contains("File") -> "File"
            else -> null
        }
        
        return type?.let { commonMethods[it] } ?: emptyList()
    }
    
    // 如果是空格，提供关键字建议
    if (context.endsWith(" ")) {
        return commonKeywords
    }
    
    return emptyList()
}

/**
 * 获取JavaScript语言的补全建议
 */
fun getJavaScriptSuggestions(code: String, position: Int): List<String> {
    // 简单的JavaScript补全建议
    val commonKeywords = listOf(
        "function", "const", "let", "var", "if", "else", "for", "while", "do", "switch",
        "case", "break", "continue", "return", "try", "catch", "finally", "throw", "new",
        "this", "class", "extends", "super", "import", "export", "from", "default", "async",
        "await", "true", "false", "null", "undefined"
    )
    
    // 常用方法和属性
    val commonMethods = mapOf(
        "String" to listOf("length", "trim()", "substring()", "replace()", "split()", "toLowerCase()", "toUpperCase()"),
        "Array" to listOf("length", "push()", "pop()", "shift()", "unshift()", "slice()", "map()", "filter()", "reduce()", "forEach()"),
        "Object" to listOf("keys()", "values()", "entries()", "assign()", "hasOwnProperty()", "toString()"),
        "Promise" to listOf("then()", "catch()", "finally()", "all()", "race()", "resolve()", "reject()")
    )
    
    // 分析上下文
    val context = getContextBeforeCursor(code, position)
    
    // 如果是点操作，尝试推断类型并提供相应的方法
    if (context.endsWith(".")) {
        val varName = context.substringBeforeLast(".").trim()
        
        // 简单的类型推断
        val type = when {
            varName.endsWith("\"") || varName.endsWith("'") || varName.endsWith("`") -> "String"
            varName.endsWith("]") -> "Array"
            varName.contains("Array") -> "Array"
            varName.contains("Object") -> "Object"
            varName.contains("Promise") -> "Promise"
            else -> null
        }
        
        return type?.let { commonMethods[it] } ?: emptyList()
    }
    
    // 如果是空格，提供关键字建议
    if (context.endsWith(" ")) {
        return commonKeywords
    }
    
    return emptyList()
}

/**
 * 获取TypeScript语言的补全建议
 */
fun getTypeScriptSuggestions(code: String, position: Int): List<String> {
    // 简单的TypeScript补全建议
    val commonKeywords = listOf(
        "function", "const", "let", "var", "if", "else", "for", "while", "do", "switch",
        "case", "break", "continue", "return", "try", "catch", "finally", "throw", "new",
        "this", "class", "extends", "super", "import", "export", "from", "default", "async",
        "await", "true", "false", "null", "undefined", "interface", "type", "enum", "namespace",
        "readonly", "private", "protected", "public", "implements", "as", "any", "unknown", "never"
    )
    
    // 常用类型
    val commonTypes = listOf(
        "string", "number", "boolean", "any", "void", "null", "undefined", "never", "unknown",
        "object", "Array<>", "Promise<>", "Map<>", "Set<>", "Record<>", "Partial<>", "Required<>",
        "Pick<>", "Omit<>", "Exclude<>", "Extract<>", "NonNullable<>", "ReturnType<>", "Parameters<>"
    )
    
    // 常用方法和属性
    val commonMethods = mapOf(
        "string" to listOf("length", "trim()", "substring()", "replace()", "split()", "toLowerCase()", "toUpperCase()"),
        "Array" to listOf("length", "push()", "pop()", "shift()", "unshift()", "slice()", "map()", "filter()", "reduce()", "forEach()"),
        "Object" to listOf("keys()", "values()", "entries()", "assign()", "hasOwnProperty()", "toString()"),
        "Promise" to listOf("then()", "catch()", "finally()", "all()", "race()", "resolve()", "reject()")
    )
    
    // 分析上下文
    val context = getContextBeforeCursor(code, position)
    
    // 如果是点操作，尝试推断类型并提供相应的方法
    if (context.endsWith(".")) {
        val varName = context.substringBeforeLast(".").trim()
        
        // 简单的类型推断
        val type = when {
            varName.endsWith("\"") || varName.endsWith("'") || varName.endsWith("`") -> "string"
            varName.endsWith("]") -> "Array"
            varName.contains("Array") -> "Array"
            varName.contains("Object") -> "Object"
            varName.contains("Promise") -> "Promise"
            else -> null
        }
        
        return type?.let { commonMethods[it] } ?: emptyList()
    }
    
    // 如果是冒号后，提供类型建议
    if (context.endsWith(": ")) {
        return commonTypes
    }
    
    // 如果是空格，提供关键字建议
    if (context.endsWith(" ")) {
        return commonKeywords
    }
    
    return emptyList()
}

/**
 * 获取HTML语言的补全建议
 */
fun getHtmlSuggestions(code: String, position: Int): List<String> {
    // 常用HTML标签
    val commonTags = listOf(
        "div", "span", "p", "h1", "h2", "h3", "h4", "h5", "h6", "a", "img", "ul", "ol", "li",
        "table", "tr", "td", "th", "form", "input", "button", "select", "option", "textarea",
        "header", "footer", "nav", "main", "section", "article", "aside", "canvas", "video", "audio"
    )
    
    // 常用HTML属性
    val commonAttributes = mapOf(
        "div" to listOf("id", "class", "style"),
        "a" to listOf("href", "target", "rel"),
        "img" to listOf("src", "alt", "width", "height"),
        "input" to listOf("type", "name", "value", "placeholder", "required"),
        "button" to listOf("type", "disabled", "onclick"),
        "form" to listOf("action", "method", "enctype")
    )
    
    // 分析上下文
    val context = getContextBeforeCursor(code, position)
    
    // 如果是在标签中
    if (context.endsWith("<")) {
        return commonTags.map { "$it>" }
    }
    
    // 如果是在属性中
    val tagMatch = Regex("<([a-zA-Z][a-zA-Z0-9]*)\\s+[^>]*$").find(context)
    if (tagMatch != null) {
        val tag = tagMatch.groupValues[1]
        return commonAttributes[tag] ?: commonAttributes["div"] ?: emptyList()
    }
    
    return emptyList()
}

/**
 * 获取CSS语言的补全建议
 */
fun getCssSuggestions(code: String, position: Int): List<String> {
    // 常用CSS属性
    val commonProperties = listOf(
        "color", "background-color", "font-size", "font-weight", "margin", "padding",
        "display", "position", "width", "height", "border", "text-align", "flex", "grid",
        "transition", "animation", "box-shadow", "border-radius", "opacity", "z-index"
    )
    
    // 常用CSS值
    val commonValues = mapOf(
        "display" to listOf("block", "inline", "inline-block", "flex", "grid", "none"),
        "position" to listOf("static", "relative", "absolute", "fixed", "sticky"),
        "font-weight" to listOf("normal", "bold", "lighter", "bolder", "100", "200", "300", "400", "500", "600", "700", "800", "900"),
        "text-align" to listOf("left", "right", "center", "justify")
    )
    
    // 分析上下文
    val context = getContextBeforeCursor(code, position)
    
    // 如果是在属性值中
    val propertyMatch = Regex("([a-zA-Z-]+)\\s*:\\s*$").find(context)
    if (propertyMatch != null) {
        val property = propertyMatch.groupValues[1]
        return commonValues[property] ?: emptyList()
    }
    
    // 如果是在选择器内部
    if (context.contains("{") && !context.contains("}")) {
        return commonProperties.map { "$it: " }
    }
    
    return emptyList()
}

/**
 * 获取光标前的上下文
 */
private fun getContextBeforeCursor(code: String, position: Int): String {
    // 获取光标前的最近一行或一定数量的字符
    val start = maxOf(0, position - 100)
    return code.substring(start, position)
} 
package com.ai.assistance.operit.ui.features.terminal.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * 为命令历史中添加突出效果的特殊关键字
 */
fun highlightCommandText(command: String): AnnotatedString {
    val builderFactory = AnnotatedString.Builder(command)
    
    // 关键词和颜色映射
    val keywords = mapOf(
        "sudo" to TerminalColors.ParrotRed,
        "apt" to TerminalColors.ParrotYellow,
        "install" to TerminalColors.ParrotOrange,
        "update" to TerminalColors.ParrotOrange,
        "ls" to TerminalColors.ParrotPurple,
        "cd" to TerminalColors.ParrotPurple,
        "pwd" to TerminalColors.ParrotPurple,
        "cat" to TerminalColors.ParrotPurple,
        "grep" to TerminalColors.ParrotPurple,
        "git" to TerminalColors.ParrotAccent,
        "python" to TerminalColors.ParrotGreen,
        "rm" to TerminalColors.ParrotRed,
        "mkdir" to TerminalColors.ParrotPurple,
        "chmod" to TerminalColors.ParrotRed,
        "chown" to TerminalColors.ParrotRed,
        "ssh" to TerminalColors.ParrotAccent,
        "nmap" to TerminalColors.ParrotRed,
        "ping" to TerminalColors.ParrotYellow
    )
    
    // 检查命令中的关键词，并高亮它们
    keywords.forEach { (keyword, color) ->
        // 查找完整的关键字（前后有空格或位于开头/结尾）
        var startIndex = 0
        while (true) {
            val wordStartIndex = command.indexOf(keyword, startIndex)
            if (wordStartIndex == -1) break
            
            val wordEndIndex = wordStartIndex + keyword.length
            val isValidWord = (wordStartIndex == 0 || command[wordStartIndex - 1].isWhitespace()) && 
                             (wordEndIndex >= command.length || command[wordEndIndex].isWhitespace() || command[wordEndIndex] == ':')
            
            if (isValidWord) {
                builderFactory.addStyle(
                    style = SpanStyle(
                        color = color,
                        fontWeight = FontWeight.Bold
                    ),
                    start = wordStartIndex,
                    end = wordEndIndex
                )
            }
            
            startIndex = wordEndIndex
            if (startIndex >= command.length) break
        }
    }
    
    // 高亮选项(以 - 或 -- 开头)
    val optionPattern = Regex("(\\s|^)(-{1,2}[\\w-]+)")
    optionPattern.findAll(command).forEach { matchResult ->
        val option = matchResult.groups[2]!!
        builderFactory.addStyle(
            style = SpanStyle(
                color = TerminalColors.ParrotYellow,
                fontWeight = FontWeight.Bold
            ),
            start = option.range.first,
            end = option.range.last + 1
        )
    }
    
    // 高亮路径(/开头或包含/ 的词)
    val pathPattern = Regex("(\\s|^)(/{1,2}[\\w./\\-_]+)")
    pathPattern.findAll(command).forEach { matchResult ->
        val path = matchResult.groups[2]!!
        builderFactory.addStyle(
            style = SpanStyle(
                color = TerminalColors.ParrotPurple,
                fontWeight = FontWeight.Bold
            ),
            start = path.range.first,
            end = path.range.last + 1
        )
    }
    
    return builderFactory.toAnnotatedString()
} 
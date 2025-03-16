package com.ai.assistance.operit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.model.ChatMessage
import com.ai.assistance.operit.model.ConversationMarkupManager

@Composable
fun CursorStyleChatMessage(
    message: ChatMessage,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    supportToolMarkup: Boolean = true // New parameter to enable/disable XML tool markup support
) {
    when (message.sender) {
        "user" -> {
            // 用户提问 - Cursor IDE 风格
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = userMessageColor
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Prompt",
                        style = MaterialTheme.typography.labelSmall,
                        color = userTextColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = message.content,
                        color = userTextColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        "ai" -> {
            // AI 回复 - Cursor IDE 风格
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = aiMessageColor
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Response",
                        style = MaterialTheme.typography.labelSmall,
                        color = aiTextColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 检测内容中是否有工具执行部分，并特殊处理
                    val toolExecutionPattern = Regex("\\*\\*Tool Execution \\[(.*?)\\]\\*\\*\n_Processing\\.\\.\\._")
                    val toolResultPattern = Regex("\\*\\*Tool Result \\[(.*?)\\]\\*\\*\n```\n([\\s\\S]*?)\n```")
                    val toolErrorPattern = Regex("\\*\\*Tool Error \\[(.*?)\\]\\*\\*\n```\n([\\s\\S]*?)\n```")
                    
                    // 新增：XML标记模式匹配 - 适配ConversationMarkupManager格式
                    val xmlStatusPattern = Regex("<status\\s+type=\"([^\"]+)\"(?:\\s+tool=\"([^\"]+)\")?(?:\\s+success=\"([^\"]+)\")?>([\\s\\S]*?)</status>")
                    val xmlToolResultPattern = Regex("<tool_result\\s+name=\"([^\"]+)\"\\s+status=\"([^\"]+)\">\\s*<content>([\\s\\S]*?)</content>\\s*</tool_result>")
                    val xmlToolErrorPattern = Regex("<tool_result\\s+name=\"([^\"]+)\"\\s+status=\"error\">\\s*<e>([\\s\\S]*?)</e>\\s*</tool_result>")
                    
                    // 工具请求XML标记 - 使用标准<tool>标签
                    val xmlToolRequestPattern = Regex("<tool\\s+name=\"([^\"]+)\"(?:\\s+description=\"([^\"]+)\")?>([\\s\\S]*?)</tool>")
                    
                    var hasToolContent = false
                    
                    if (supportToolMarkup) {
                        // 将完整内容分解为文本段和工具段
                        val contentSegments = mutableListOf<Pair<Boolean, String>>() // Boolean表示是否为工具内容
                        
                        // 处理文本，将标记位置记录下来，然后按顺序重建文本和工具框
                        var remainingContent = message.content
                        
                        // 查找所有匹配的标记位置
                        val allMatches = mutableListOf<Triple<Int, Int, String>>() // start, end, type
                        
                        // 收集所有工具标记
                        val toolRequests = mutableMapOf<String, Triple<Int, Int, String>>() // 工具名称 -> (start, end, content)
                        val toolExecutions = mutableMapOf<String, Triple<Int, Int, String>>() // 工具名称 -> (start, end, content)
                        val toolResults = mutableMapOf<String, Pair<Triple<Int, Int, String>, Boolean>>() // 工具名称 -> ((start, end, content), 是否成功)
                        
                        // 检查工具请求
                        xmlToolRequestPattern.findAll(remainingContent).forEach { match ->
                            val toolName = match.groupValues[1]
                            toolRequests[toolName] = Triple(match.range.first, match.range.last + 1, match.value)
                        }
                        
                        // 检查工具执行状态
                        xmlStatusPattern.findAll(remainingContent).forEach { match ->
                            val statusType = match.groupValues[1]
                            val toolName = match.groupValues[2]
                            if (statusType == "executing" && toolName.isNotBlank()) {
                                toolExecutions[toolName] = Triple(match.range.first, match.range.last + 1, match.value)
                            } else if (statusType == "result" && toolName.isNotBlank()) {
                                val success = match.groupValues[3].toBoolean()
                                toolResults[toolName] = Pair(Triple(match.range.first, match.range.last + 1, match.value), success)
                            }
                        }
                        
                        // 检查工具结果（旧格式和新格式）
                        xmlToolResultPattern.findAll(remainingContent).forEach { match ->
                            val toolName = match.groupValues[1]
                            val status = match.groupValues[2]
                            toolResults[toolName] = Pair(Triple(match.range.first, match.range.last + 1, match.value), status == "success")
                        }
                        
                        // 计算要隐藏的区域（工具请求和执行区域，已有结果的情况下）
                        val hiddenRanges = mutableListOf<Pair<Int, Int>>() // Pair(start, end)
                        
                        // 如果工具有结果，且不是错误结果，则隐藏工具请求和执行部分
                        toolResults.forEach { (toolName, resultData) ->
                            val (_, isSuccess) = resultData
                            
                            if (isSuccess) {
                                // 如果有工具请求，隐藏请求
                                if (toolRequests.containsKey(toolName)) {
                                    val (start, end, _) = toolRequests[toolName]!!
                                    hiddenRanges.add(Pair(start, end))
                                }
                                
                                // 如果有工具执行，隐藏执行
                                if (toolExecutions.containsKey(toolName)) {
                                    val (start, end, _) = toolExecutions[toolName]!!
                                    hiddenRanges.add(Pair(start, end))
                                }
                            }
                        }
                        
                        // 如果工具正在执行，隐藏工具请求
                        toolExecutions.forEach { (toolName, _) ->
                            if (toolRequests.containsKey(toolName)) {
                                val (start, end, _) = toolRequests[toolName]!!
                                hiddenRanges.add(Pair(start, end))
                            }
                        }
                        
                        // 合并重叠的隐藏区域
                        val mergedHiddenRanges = mutableListOf<Pair<Int, Int>>()
                        if (hiddenRanges.isNotEmpty()) {
                            // 按起始位置排序
                            val sortedRanges = hiddenRanges.sortedBy { it.first }
                            var currentRange = sortedRanges.first()
                            
                            sortedRanges.drop(1).forEach { range ->
                                if (range.first <= currentRange.second) {
                                    // 重叠，合并
                                    currentRange = Pair(currentRange.first, maxOf(currentRange.second, range.second))
                                } else {
                                    // 不重叠，添加当前区域并开始新区域
                                    mergedHiddenRanges.add(currentRange)
                                    currentRange = range
                                }
                            }
                            
                            // 添加最后一个区域
                            mergedHiddenRanges.add(currentRange)
                        }
                        
                        // 处理所有标记，确定要显示哪些标记
                        val displayableMarks = mutableListOf<Triple<Int, Int, String>>()
                        
                        // 所有状态标记，但排除已隐藏的区域
                        xmlStatusPattern.findAll(remainingContent).forEach { match ->
                            val statusType = match.groupValues[1]
                            val toolName = match.groupValues[2]
                            val markStart = match.range.first
                            val markEnd = match.range.last + 1
                            
                            // 检查是否在隐藏区域内
                            val isHidden = mergedHiddenRanges.any { range ->
                                markStart >= range.first && markEnd <= range.second
                            }
                            
                            if (!isHidden) {
                                // 跳过工具执行标记，如果有对应的结果标记
                                if (statusType == "executing" && toolName.isNotBlank() && toolResults.containsKey(toolName)) {
                                    // 不添加执行中标记，因为已有结果
                                } else {
                                    displayableMarks.add(Triple(markStart, markEnd, "status:${statusType}:${toolName}"))
                                }
                            }
                        }
                        
                        // 添加工具结果标记
                        xmlToolResultPattern.findAll(remainingContent).forEach { match ->
                            displayableMarks.add(Triple(match.range.first, match.range.last + 1, "result:${match.groupValues[1]}:${match.groupValues[2]}"))
                        }
                        
                        // 添加工具错误标记
                        xmlToolErrorPattern.findAll(remainingContent).forEach { match ->
                            displayableMarks.add(Triple(match.range.first, match.range.last + 1, "error:${match.groupValues[1]}"))
                        }
                        
                        // 添加工具请求标记，但跳过已执行或已有结果的工具和隐藏区域
                        xmlToolRequestPattern.findAll(remainingContent).forEach { match ->
                            val toolName = match.groupValues[1]
                            val markStart = match.range.first
                            val markEnd = match.range.last + 1
                            
                            // 检查是否在隐藏区域内
                            val isHidden = mergedHiddenRanges.any { range ->
                                markStart >= range.first && markEnd <= range.second
                            }
                            
                            // 如果工具已执行或已有结果，则不显示请求
                            if (!isHidden && !toolExecutions.containsKey(toolName) && !toolResults.containsKey(toolName)) {
                                displayableMarks.add(Triple(markStart, markEnd, "request:${match.groupValues[1]}:${match.groupValues[2]}"))
                            }
                        }
                        
                        // 添加旧格式标记（如果有），同样避开隐藏区域
                        toolExecutionPattern.findAll(remainingContent).forEach { match ->
                            val toolName = match.groupValues[1]
                            val markStart = match.range.first
                            val markEnd = match.range.last + 1
                            
                            // 检查是否在隐藏区域内
                            val isHidden = mergedHiddenRanges.any { range ->
                                markStart >= range.first && markEnd <= range.second
                            }
                            
                            if (!isHidden) {
                                // 检查是否有对应的结果
                                val hasResult = toolResultPattern.findAll(remainingContent)
                                    .any { it.groupValues[1] == toolName }
                                if (!hasResult) {
                                    displayableMarks.add(Triple(markStart, markEnd, "oldexec:${toolName}"))
                                }
                            }
                        }
                        
                        toolResultPattern.findAll(remainingContent).forEach { match ->
                            displayableMarks.add(Triple(match.range.first, match.range.last + 1, "oldresult:${match.groupValues[1]}:${match.groupValues[2]}"))
                        }
                        
                        toolErrorPattern.findAll(remainingContent).forEach { match ->
                            displayableMarks.add(Triple(match.range.first, match.range.last + 1, "olderror:${match.groupValues[1]}:${match.groupValues[2]}"))
                        }
                        
                        // 按照出现位置排序
                        val sortedMarks = displayableMarks.sortedBy { it.first }
                        
                        // 构建内容段落，跳过隐藏区域
                        var lastEnd = 0
                        for (mark in sortedMarks) {
                            // 添加前面的普通文本（如果有），但跳过隐藏区域
                            if (mark.first > lastEnd) {
                                // 检查从lastEnd到mark.first之间是否有隐藏区域
                                val textSegment = Pair(lastEnd, mark.first)
                                var currentPos = lastEnd
                                
                                // 处理隐藏区域可能切分文本段落的情况
                                mergedHiddenRanges.forEach { hiddenRange ->
                                    if (hiddenRange.first >= currentPos && hiddenRange.first < mark.first) {
                                        // 添加隐藏区域前的文本
                                        if (hiddenRange.first > currentPos) {
                                            val textBeforeHidden = remainingContent.substring(currentPos, hiddenRange.first)
                                            if (textBeforeHidden.isNotBlank()) {
                                                contentSegments.add(Pair(false, textBeforeHidden))
                                            }
                                        }
                                        // 跳过隐藏区域
                                        currentPos = maxOf(currentPos, hiddenRange.second)
                                    }
                                }
                                
                                // 添加最后一段文本（如果有）
                                if (currentPos < mark.first) {
                                    val finalText = remainingContent.substring(currentPos, mark.first)
                                    if (finalText.isNotBlank()) {
                                        contentSegments.add(Pair(false, finalText))
                                    }
                                }
                            }
                            
                            // 添加工具内容
                            val toolMarkup = remainingContent.substring(mark.first, mark.second)
                            contentSegments.add(Pair(true, toolMarkup))
                            
                            lastEnd = mark.second
                            hasToolContent = true
                        }
                        
                        // 添加最后的普通文本（如果有），但跳过隐藏区域
                        if (lastEnd < remainingContent.length) {
                            var currentPos = lastEnd
                            
                            // 处理隐藏区域可能切分文本段落的情况
                            mergedHiddenRanges.forEach { hiddenRange ->
                                if (hiddenRange.first >= currentPos && hiddenRange.first < remainingContent.length) {
                                    // 添加隐藏区域前的文本
                                    if (hiddenRange.first > currentPos) {
                                        val textBeforeHidden = remainingContent.substring(currentPos, hiddenRange.first)
                                        if (textBeforeHidden.isNotBlank()) {
                                            contentSegments.add(Pair(false, textBeforeHidden))
                                        }
                                    }
                                    // 跳过隐藏区域
                                    currentPos = maxOf(currentPos, hiddenRange.second)
                                }
                            }
                            
                            // 添加最后一段文本（如果有）
                            if (currentPos < remainingContent.length) {
                                val finalText = remainingContent.substring(currentPos)
                                if (finalText.isNotBlank()) {
                                    contentSegments.add(Pair(false, finalText))
                                }
                            }
                        }
                        
                        // 按顺序渲染每个段落
                        for (segment in contentSegments) {
                            val isToolContent = segment.first
                            val content = segment.second
                            
                            if (isToolContent) {
                                // 处理工具内容
                                
                                // 检查XML状态标记
                                val statusMatch = xmlStatusPattern.find(content)
                                if (statusMatch != null) {
                                    val statusType = statusMatch.groupValues[1]
                                    val toolName = statusMatch.groupValues[2]
                                    val success = statusMatch.groupValues[3].toBoolean()
                                    val statusContent = statusMatch.groupValues[4]
                                    
                                    when (statusType) {
                                        "thinking" -> {
                                            // 思考状态 - 不需要显示为工具框
                                        }
                                        "executing" -> {
                                            // 显示工具执行中状态
                                            ToolExecutionBox(
                                                toolName = toolName,
                                                isProcessing = true,
                                                result = null,
                                                isError = false
                                            )
                                        }
                                        "result" -> {
                                            // 显示工具执行结果
                                            ToolExecutionBox(
                                                toolName = toolName,
                                                isProcessing = false,
                                                result = statusContent,
                                                isError = !success
                                            )
                                        }
                                        "error" -> {
                                            // 显示工具执行错误
                                            ToolExecutionBox(
                                                toolName = toolName,
                                                isProcessing = false,
                                                result = statusContent,
                                                isError = true
                                            )
                                        }
                                        "warning" -> {
                                            // 显示警告信息
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = statusContent,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(12.dp)
                                                )
                                            }
                                        }
                                        "complete" -> {
                                            // 任务完成标记，可以显示一个完成指示
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = "✓ 任务已完成",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    continue
                                }
                                
                                // 处理工具请求标记
                                val toolRequestMatch = xmlToolRequestPattern.find(content)
                                if (toolRequestMatch != null) {
                                    val toolName = toolRequestMatch.groupValues[1]
                                    val toolDescription = toolRequestMatch.groupValues[2]
                                    val requestParams = toolRequestMatch.groupValues[3]
                                    
                                    // 显示工具请求框
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ExpandMore,
                                                    contentDescription = "请求使用工具",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                
                                                Spacer(modifier = Modifier.width(8.dp))
                                                
                                                Text(
                                                    text = if (toolDescription.isNotBlank()) 
                                                        "请求使用工具: $toolName ($toolDescription)" 
                                                    else 
                                                        "请求使用工具: $toolName",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            
                                            if (requestParams.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "参数:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                )
                                                
                                                // 解析参数，查找<param>标签
                                                val paramPattern = Regex("<param\\s+name=\"([^\"]+)\">([\\s\\S]*?)</param>")
                                                val paramMatches = paramPattern.findAll(requestParams).toList()
                                                
                                                if (paramMatches.isNotEmpty()) {
                                                    // 有结构化参数，使用表格风格显示
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f))
                                                            .padding(12.dp)
                                                    ) {
                                                        paramMatches.forEach { match ->
                                                            val paramName = match.groupValues[1]
                                                            val paramValue = match.groupValues[2].trim()
                                                            
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 4.dp),
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                // 参数名称 - 使用强调色
                                                                Text(
                                                                    text = paramName,
                                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                                                    ),
                                                                    color = MaterialTheme.colorScheme.secondary,
                                                                    modifier = Modifier
                                                                        .padding(end = 8.dp)
                                                                        .width(80.dp)
                                                                )
                                                                
                                                                // 参数值 - 使用代码样式
                                                                val valueStyle = when {
                                                                    // 检测值是否为数字
                                                                    paramValue.matches(Regex("^\\d+(\\.\\d+)?$")) -> 
                                                                        MaterialTheme.typography.bodySmall.copy(
                                                                            color = MaterialTheme.colorScheme.tertiary
                                                                        )
                                                                    // 检测值是否为布尔值
                                                                    paramValue.matches(Regex("^(true|false)$")) -> 
                                                                        MaterialTheme.typography.bodySmall.copy(
                                                                            color = MaterialTheme.colorScheme.tertiary
                                                                        )
                                                                    // 检测值是否为JSON或复杂结构
                                                                    paramValue.contains("{") || paramValue.contains("[") -> 
                                                                        MaterialTheme.typography.bodySmall.copy(
                                                                            fontFamily = FontFamily.Monospace,
                                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                                        )
                                                                    // 默认文本样式
                                                                    else -> 
                                                                        MaterialTheme.typography.bodySmall.copy(
                                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                                        )
                                                                }
                                                                
                                                                // 参数值显示
                                                                Text(
                                                                    text = paramValue,
                                                                    style = valueStyle,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .padding(start = 4.dp)
                                                                )
                                                            }
                                                            
                                                            // 在参数之间添加分隔线，但不在最后一个参数后添加
                                                            if (match != paramMatches.last()) {
                                                                Divider(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = 4.dp),
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
                                                                    thickness = 0.5.dp
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    // 没有结构化参数，直接显示原始内容
                                                    Text(
                                                        text = requestParams,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))
                                                            .padding(8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    continue
                                }
                                
                                // 处理XML工具结果
                                val toolResultMatch = xmlToolResultPattern.find(content)
                                if (toolResultMatch != null) {
                                    val toolName = toolResultMatch.groupValues[1]
                                    val status = toolResultMatch.groupValues[2]
                                    val toolContent = toolResultMatch.groupValues[3]
                                    
                                    ToolExecutionBox(
                                        toolName = toolName,
                                        isProcessing = false,
                                        result = toolContent,
                                        isError = status != "success"
                                    )
                                    continue
                                }
                                
                                // 处理XML工具错误
                                val toolErrorMatch = xmlToolErrorPattern.find(content)
                                if (toolErrorMatch != null) {
                                    val toolName = toolErrorMatch.groupValues[1]
                                    val errorContent = toolErrorMatch.groupValues[2]
                                    
                                    ToolExecutionBox(
                                        toolName = toolName,
                                        isProcessing = false,
                                        result = errorContent,
                                        isError = true
                                    )
                                    continue
                                }
                                
                                // 处理旧版工具执行标记
                                val oldExecMatch = toolExecutionPattern.find(content)
                                if (oldExecMatch != null) {
                                    val toolName = oldExecMatch.groupValues[1]
                                    
                                    ToolExecutionBox(
                                        toolName = toolName,
                                        isProcessing = true,
                                        result = null,
                                        isError = false
                                    )
                                    continue
                                }
                                
                                // 处理旧版工具结果
                                val oldResultMatch = toolResultPattern.find(content)
                                if (oldResultMatch != null) {
                                    val toolName = oldResultMatch.groupValues[1]
                                    val resultContent = oldResultMatch.groupValues[2]
                                    
                                    ToolExecutionBox(
                                        toolName = toolName,
                                        isProcessing = false,
                                        result = resultContent,
                                        isError = false
                                    )
                                    continue
                                }
                                
                                // 处理旧版工具错误
                                val oldErrorMatch = toolErrorPattern.find(content)
                                if (oldErrorMatch != null) {
                                    val toolName = oldErrorMatch.groupValues[1]
                                    val errorContent = oldErrorMatch.groupValues[2]
                                    
                                    ToolExecutionBox(
                                        toolName = toolName,
                                        isProcessing = false,
                                        result = errorContent,
                                        isError = true
                                    )
                                    continue
                                }
                            } else {
                                // 处理普通文本内容
                                if (content.isNotBlank()) {
                                    // 规范化文本内容，删除多余的空行
                                    val normalizedContent = content
                                        // 将连续的多个空行替换为单个空行
                                        .replace(Regex("\n\\s*\n\\s*\n+"), "\n\n")
                                        // 删除开头的空行
                                        .replace(Regex("^\\s*\n+"), "")
                                        // 删除结尾的空行
                                        .replace(Regex("\n+\\s*$"), "")
                                    
                                    // 解析内容中可能的代码块
                                    val codeBlockPattern = Regex("```([\\s\\S]*?)```")
                                    val segments = codeBlockPattern.split(normalizedContent)
                                    
                                    // 将匹配的内容添加到段落中
                                    val formattedContent = segments.joinToString("") { segment ->
                                        if (segment.startsWith("```") && segment.endsWith("```")) {
                                            val code = segment.substring(3, segment.length - 3)
                                            "<code>$code</code>"
                                        } else {
                                            segment
                                        }
                                    }
                                    
                                    Text(
                                        text = formattedContent,
                                        color = aiTextColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    } else {
                        // 如果不支持工具标记，直接显示内容
                        Text(
                            text = message.content,
                            color = aiTextColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        "think" -> {
            // 思考过程 - Cursor IDE 风格的折叠面板
            var expanded by remember { mutableStateOf(false) } // 默认折叠
            
            // 检查是否包含XML格式的思考状态标记
            val thinkingContent = if (supportToolMarkup) {
                // 移除XML标记，只保留实际内容
                val statusPattern = Regex("<status\\s+type=\"thinking\">([\\s\\S]*?)</status>")
                val match = statusPattern.find(message.content)
                if (match != null) {
                    // 提取思考内容，去除标记
                    match.groupValues[1]
                } else {
                    message.content
                }
            } else {
                message.content
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = thinkingBackgroundColor
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Thinking Process",
                            style = MaterialTheme.typography.labelMedium,
                            color = thinkingTextColor
                        )
                        
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "折叠" else "展开",
                            tint = thinkingTextColor
                        )
                    }
                    
                    AnimatedVisibility(visible = expanded) {
                        Text(
                            text = thinkingContent,
                            color = thinkingTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }
        "system" -> {
            // 系统消息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = systemMessageColor
                ),
                shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = message.content,
                        color = systemTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                    )
            }
        }
    }
} 
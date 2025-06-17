package com.ai.assistance.operit.api.enhance

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 处理会话相关功能的服务类，包括会话总结、偏好处理和对话切割准备 */
class ConversationService(private val context: Context) {

    companion object {
        private const val TAG = "ConversationService"
    }

    private val apiPreferences = ApiPreferences(context)
    private val conversationMutex = Mutex()

    /**
     * 生成对话总结
     * @param messages 要总结的消息列表
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            multiServiceManager: MultiServiceManager
    ): String {
        return generateSummary(messages, null, multiServiceManager)
    }

    /**
     * 生成对话总结，并且包含上一次的总结内容
     * @param messages 要总结的消息列表
     * @param previousSummary 上一次的总结内容，可以为null
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            previousSummary: String?,
            multiServiceManager: MultiServiceManager
    ): String {
        try {
            // 使用更结构化、更详细的提示词
            var systemPrompt =
                    """
            请对以下对话内容进行简洁但全面的总结。遵循以下格式：
            
            1. 以"对话摘要"作为标题
            2. 用1-2个简短段落概述主要内容和交互
            3. 明确列出对理解后续对话至关重要的关键信息点（如用户提到的具体问题、需求、限制条件等）
            4. 特别标注用户明确表达的意图或情感，如有
            5. 避免使用复杂的标题结构和Markdown格式，使用简单段落
            
            总结应该尽量保留对后续对话有价值的上下文信息，但不要包含无关细节。内容应该简洁明了，便于AI快速理解历史对话的要点。
            """

            // 如果存在上一次的摘要，将其添加到系统提示中
            if (previousSummary != null && previousSummary.isNotBlank()) {
                systemPrompt +=
                        """
                
                以下是之前对话的摘要，请参考它来生成新的总结，确保新总结融合之前的要点，并包含新的信息：
                
                ${previousSummary.trim()}
                """
                Log.d(TAG, "添加上一条摘要内容到系统提示")
            }

            val finalMessages = listOf(Pair("system", systemPrompt)) + messages

            // Get all model parameters from preferences (with enabled state)
            val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }

            // 获取SUMMARY功能类型的AIService实例
            val summaryService = multiServiceManager.getServiceForFunction(FunctionType.SUMMARY)

            // 使用summaryService发送请求，收集完整响应
            val contentBuilder = StringBuilder()

            // 使用新的Stream API
            val stream =
                    summaryService.sendMessage(
                            message = "请按照要求总结对话内容",
                            chatHistory = finalMessages,
                            modelParameters = modelParameters
                    )

            // 收集流中的所有内容
            stream.collect { content -> contentBuilder.append(content) }

            // 获取完整的总结内容
            val summaryContent = contentBuilder.toString().trim()

            // 如果内容为空，返回默认消息
            if (summaryContent.isBlank()) {
                return "对话摘要：未能生成有效摘要。"
            }

            // 获取本次总结生成的token统计
            val inputTokens = summaryService.inputTokenCount
            val outputTokens = summaryService.outputTokenCount

            // 将总结token计数添加到用户偏好分析的token统计中
            try {
                Log.d(TAG, "总结生成使用了输入token: $inputTokens, 输出token: $outputTokens")
                apiPreferences.updatePreferenceAnalysisTokens(inputTokens, outputTokens)
                Log.d(TAG, "已将总结token统计添加到用户偏好分析token计数中")
            } catch (e: Exception) {
                Log.e(TAG, "更新token统计失败", e)
            }

            return summaryContent
        } catch (e: Exception) {
            Log.e(TAG, "生成总结时出错", e)
            return "对话摘要：生成摘要时出错，但对话仍在继续。"
        }
    }

    /**
     * 为聊天准备对话历史记录
     * @param chatHistory 原始聊天历史
     * @param processedInput 处理后的用户输入
     * @param currentChatId 当前聊天ID
     * @param conversationHistory 存储修改后的对话历史
     * @param packageManager 包管理器
     * @return 准备好的对话历史列表
     */
    suspend fun prepareConversationHistory(
            chatHistory: List<Pair<String, String>>,
            processedInput: String,
            currentChatId: String?,
            conversationHistory: MutableList<Pair<String, String>>,
            packageManager: PackageManager
    ): MutableList<Pair<String, String>> {
        conversationMutex.withLock {
            conversationHistory.clear()

            // Add system prompt if not already present
            if (!chatHistory.any { it.first == "system" }) {
                val activeProfile = preferencesManager.getUserPreferencesFlow().first()
                val preferencesText = buildPreferencesText(activeProfile)

                // Check if planning is enabled
                val planningEnabled = apiPreferences.enableAiPlanningFlow.first()

                // Get custom prompts if available
                val customIntroPrompt = apiPreferences.customIntroPromptFlow.first()
                val customTonePrompt = apiPreferences.customTonePromptFlow.first()

                // 获取系统提示词，并替换{CHAT_ID}为当前聊天ID
                var systemPrompt =
                        if (customIntroPrompt.isNotEmpty() || customTonePrompt.isNotEmpty()) {
                            // Use custom prompts if they are set
                            SystemPromptConfig.getSystemPromptWithCustomPrompts(
                                    packageManager,
                                    planningEnabled,
                                    customIntroPrompt,
                                    customTonePrompt
                            )
                        } else {
                            // Use default system prompt
                            SystemPromptConfig.getSystemPrompt(packageManager, planningEnabled)
                        }

                // 替换{CHAT_ID}为当前聊天ID
                if (currentChatId != null) {
                    systemPrompt = systemPrompt.replace("{CHAT_ID}", currentChatId)
                } else {
                    // 如果没有聊天ID，使用一个默认值
                    systemPrompt = systemPrompt.replace("{CHAT_ID}", "default")
                }

                if (preferencesText.isNotEmpty()) {
                    conversationHistory.add(
                            0,
                            Pair(
                                    "system",
                                    "$systemPrompt\n\nUser preference description: $preferencesText"
                            )
                    )
                } else {
                    conversationHistory.add(0, Pair("system", systemPrompt))
                }
            }

            // Process each message in chat history
            for (message in chatHistory) {
                val role = message.first
                val content = message.second

                // If it's an assistant message, check for tool results
                if (role == "assistant") {
                    val xmlTags = splitXmlTag(content)
                    if (xmlTags.isNotEmpty()) {
                        // Process the message with tool results
                        processChatMessageWithTools(content, xmlTags, conversationHistory)
                    } else {
                        // Add the message as is
                        conversationHistory.add(message)
                    }
                } else {
                    // Add user or system messages as is
                    conversationHistory.add(message)
                }
            }
        }
        return conversationHistory
    }

    /**
     * 提取内容中的XML标签
     * @param content 要处理的内容
     * @return 提取的XML标签列表，每项包含[标签名称, 标签内容]
     */
    fun splitXmlTag(content: String): List<List<String>> {
        val results = mutableListOf<List<String>>()

        // 使用StreamXmlPlugin处理XML标签
        val plugins = listOf(StreamXmlPlugin(includeTagsInOutput = true))

        try {
            // 将内容转换为Stream<Char>然后用插件拆分
            val contentStream = content.stream()
            val tagContents = mutableListOf<String>() // 标签内容
            val tagNames = mutableListOf<String>() // 标签名称

            // 使用协程作用域收集拆分结果
            kotlinx.coroutines.runBlocking {
                contentStream.splitBy(plugins).collect { group ->
                    if (group.tag is StreamXmlPlugin) {
                        val sb = StringBuilder()
                        var isFirstChar = true

                        // 收集完整的XML元素内容
                        group.stream.collect { charString ->
                            if (isFirstChar) {
                                isFirstChar = false
                            }
                            sb.append(charString)
                        }

                        val fullContent = sb.toString()

                        // 提取标签名称
                        val tagNameMatch = Regex("<([a-zA-Z0-9_]+)[\\s>]").find(fullContent)
                        val tagName = tagNameMatch?.groupValues?.getOrNull(1) ?: "unknown"

                        tagNames.add(tagName)
                        tagContents.add(fullContent)
                    } else {
                        // 处理纯文本内容
                        val sb = StringBuilder()

                        // 收集纯文本内容
                        group.stream.collect { charString -> sb.append(charString) }

                        val textContent = sb.toString()
                        if (textContent.isNotBlank()) {
                            // 对于纯文本，将其作为text标签处理
                            tagNames.add("text")
                            tagContents.add(textContent)
                        }
                    }
                }
            }

            // 将收集到的XML标签转换为二维列表
            for (i in tagNames.indices) {
                results.add(listOf(tagNames[i], tagContents[i]))
            }
        } catch (e: Exception) {
            Log.e(TAG, "使用Stream解析XML标签时出错", e)
        }

        return results
    }

    /** 处理包含工具结果的聊天消息，并按顺序重新组织消息 任务完成和等待用户响应的status标签算作AI消息，其他status和warning算作用户消息 工具结果为用户消息 */
    suspend fun processChatMessageWithTools(
            content: String,
            xmlTags: List<List<String>>,
            conversationHistory: MutableList<Pair<String, String>>
    ) {
        if (xmlTags.isEmpty()) {
            // 如果没有XML标签，直接添加为AI消息
            conversationHistory.add(Pair("assistant", content))
            return
        }

        // 获取内存优化设置
        val memoryOptimizationEnabled = apiPreferences.memoryOptimizationFlow.first()

        // 按顺序处理标签
        val segments = mutableListOf<Pair<String, String>>() // 角色, 内容

        for (tag in xmlTags) {
            val tagName = tag[0]
            var tagContent = tag[1]

            // 对于text类型（纯文本），直接作为AI消息
            if (tagName == "text") {
                if (tagContent.isNotBlank()) {
                    segments.add(Pair("assistant", tagContent))
                }
                continue
            }

            // 应用内存优化（如果启用）
            if (memoryOptimizationEnabled && tagContent.length > 1000 && tagName == "tool_result") {
                tagContent = optimizeToolResult(tagContent)
            }

            // 根据标签类型分配角色
            when (tagName) {
                "status" -> {
                    // 判断status类型
                    if (tagContent.contains("type=\"complete\"") ||
                                    tagContent.contains("type=\"wait_for_user_need\"")
                    ) {
                        segments.add(Pair("assistant", tagContent))
                    } else {
                        segments.add(Pair("user", tagContent))
                    }
                }
                "tool_result" -> {
                    segments.add(Pair("user", tagContent))
                }
                else -> {
                    segments.add(Pair("assistant", tagContent))
                }
            }
        }

        // 合并连续的相同角色消息
        val mergedSegments = mutableListOf<Pair<String, String>>()
        var currentRole = ""
        var currentContent = StringBuilder()

        for (segment in segments) {
            if (segment.first == currentRole) {
                // 如果角色与当前角色相同，则合并内容
                currentContent.append("\n").append(segment.second)
            } else {
                // 角色不同，先保存当前内容（如果有）
                if (currentContent.isNotEmpty()) {
                    mergedSegments.add(Pair(currentRole, currentContent.toString().trim()))
                    currentContent.clear()
                }
                // 更新当前角色和内容
                currentRole = segment.first
                currentContent.append(segment.second)
            }
        }

        // 添加最后一条消息
        if (currentContent.isNotEmpty()) {
            mergedSegments.add(Pair(currentRole, currentContent.toString().trim()))
        }

        // 将合并后的消息添加到对话历史
        conversationHistory.addAll(mergedSegments)
    }

    /**
     * Optimize tool result by selecting the most important parts This helps with memory management
     * for long tool outputs
     */
    fun optimizeToolResult(toolResult: String): String {
        // 如果结果不够长，直接返回
        if (toolResult.length <= 1000) return toolResult

        // 提取工具名称
        val nameMatch = Regex("name=\"([^\"]+)\"").find(toolResult)
        val toolName = nameMatch?.groupValues?.getOrNull(1) ?: "unknown"

        // 为特定工具类型保留完整内容
        if (toolName == "use_package") {
            return toolResult
        }

        // 提取内容
        val tagContent =
                Regex("<[^>]*>(.*?)</[^>]*>", RegexOption.DOT_MATCHES_ALL)
                        .find(toolResult)
                        ?.groupValues
                        ?.getOrNull(1)

        val sb = StringBuilder()

        // 添加工具名称前缀
        sb.append("<tool_result name=\"$toolName\">")

        // 处理提取的内容
        if (!tagContent.isNullOrEmpty()) {
            // 对于XML内容，最多保留800个字符
            val maxContentLength = 800
            val content =
                    if (tagContent.length > maxContentLength) {
                        tagContent.substring(0, 400) +
                                "\n... [content truncated for memory optimization] ...\n" +
                                tagContent.substring(tagContent.length - 400)
                    } else {
                        tagContent
                    }
            sb.append(content)
        } else {
            // 对于非XML内容，从头部和尾部保留重要部分
            sb.append(toolResult.substring(0, 400))
            sb.append("\n... [content truncated for memory optimization] ...\n")
            sb.append(toolResult.substring(toolResult.length - 400))
        }

        // 添加结束标签
        sb.append("</tool_result>")

        return sb.toString()
    }

    /** Build a formatted preferences text string from a PreferenceProfile */
    fun buildPreferencesText(profile: PreferenceProfile): String {
        val parts = mutableListOf<String>()

        if (profile.gender.isNotEmpty()) {
            parts.add("性别: ${profile.gender}")
        }

        if (profile.birthDate > 0) {
            // Convert timestamp to age and format as text
            val today = Calendar.getInstance()
            val birthCal = Calendar.getInstance().apply { timeInMillis = profile.birthDate }
            var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            // Adjust age if birthday hasn't occurred yet this year
            if (today.get(Calendar.MONTH) < birthCal.get(Calendar.MONTH) ||
                            (today.get(Calendar.MONTH) == birthCal.get(Calendar.MONTH) &&
                                    today.get(Calendar.DAY_OF_MONTH) <
                                            birthCal.get(Calendar.DAY_OF_MONTH))
            ) {
                age--
            }
            parts.add("年龄: ${age}岁")

            // Also add birth date for more precise information
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            parts.add("出生日期: ${dateFormat.format(java.util.Date(profile.birthDate))}")
        }

        if (profile.personality.isNotEmpty()) {
            parts.add("性格特点: ${profile.personality}")
        }

        if (profile.identity.isNotEmpty()) {
            parts.add("身份认同: ${profile.identity}")
        }

        if (profile.occupation.isNotEmpty()) {
            parts.add("职业: ${profile.occupation}")
        }

        if (profile.aiStyle.isNotEmpty()) {
            parts.add("期待的AI风格: ${profile.aiStyle}")
        }

        return parts.joinToString("; ")
    }

    /**
     * 处理文件绑定操作，智能合并AI生成的代码和原始文件内容
     * @param originalContent 原始文件内容
     * @param aiGeneratedCode AI生成的代码（包含"//existing code"标记）
     * @param multiServiceManager 服务管理器，用于获取文件绑定专用的服务
     * @return 合并后的文件内容
     */
    suspend fun processFileBinding(
            originalContent: String,
            aiGeneratedCode: String,
            multiServiceManager: MultiServiceManager
    ): Pair<String, String> {
        try {
            // 创建结构化提示词，让AI返回差异指令而不是整个文件
            val prompt =
                    """
                任务：分析原始文件和包含"//existing code"标记的AI生成代码，生成精确的差异修改指令。
                
                原始文件：
                ```
                $originalContent
                ```
                
                AI生成的代码（包含"//existing code"注释）：
                ```
                $aiGeneratedCode
                ```
                
                请以下面的结构化格式返回修改指令，而不是返回整个文件内容：
                
                1. 对于替换操作：
                REPLACE:
                ```[原文件中存在的上下文代码，足够确定位置的片段，约5-10行]```
                替换为:
                ```[新的代码]```
                
                2. 对于插入操作：
                INSERT_AFTER:
                ```[定位代码片段，确保这段代码在原文件中唯一或者可明确定位]```
                插入:
                ```[要插入的新代码]```
                
                3. 对于删除操作：
                DELETE:
                ```[要删除的代码片段，包含足够上下文以精确定位]```
                
                可以包含多个操作指令，每个指令之间用空行分隔。
                确保上下文代码片段足够独特，能在原文件中精确定位。
                对于包含"//existing code"的部分，应转换为REPLACE或保持原样。
            """.trimIndent()

            // 获取全部模型参数
            val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }

            // 获取文件绑定专用的AI服务
            val fileBindingService =
                    multiServiceManager.getServiceForFunction(FunctionType.FILE_BINDING)

            // 使用Stream API处理响应
            val contentBuilder = StringBuilder()

            // 发送消息并收集完整响应
            val stream =
                    fileBindingService.sendMessage(
                            message = prompt,
                            chatHistory = emptyList(), // 无需历史记录，这是单次处理
                            modelParameters = modelParameters
                    )

            // 收集流中的所有内容
            stream.collect { content -> contentBuilder.append(content) }

            // 获取完整的AI返回内容
            val aiResponse = contentBuilder.toString().trim()

            // 如果内容为空，返回原始内容
            if (aiResponse.isBlank()) {
                Log.w(TAG, "文件绑定处理返回空内容，保留原始内容")
                return Pair(originalContent, "")
            }

            // 解析AI返回的差异指令
            val mergedContent = applyFileBindingInstructions(originalContent, aiResponse)

            // 获取本次处理生成的token统计
            val inputTokens = fileBindingService.inputTokenCount
            val outputTokens = fileBindingService.outputTokenCount

            // 记录token统计
            try {
                Log.d(TAG, "文件绑定处理使用了输入token: $inputTokens, 输出token: $outputTokens")
                apiPreferences.updatePreferenceAnalysisTokens(inputTokens, outputTokens)
            } catch (e: Exception) {
                Log.e(TAG, "更新token统计失败", e)
            }

            return Pair(mergedContent, aiResponse)
        } catch (e: Exception) {
            Log.e(TAG, "处理文件绑定时出错", e)
            // 出错时返回原始内容，确保不会丢失数据
            return Pair(originalContent, "Error processing file binding: ${e.message}")
        }
    }

    /**
     * 解析AI返回的差异指令并应用到原始文件
     * @param originalContent 原始文件内容
     * @param aiInstructions AI返回的差异指令
     * @return 应用指令后的文件内容
     */
    private fun applyFileBindingInstructions(
            originalContent: String,
            aiInstructions: String
    ): String {
        // 将原始内容分割成行，便于操作
        val originalLines = originalContent.lines().toMutableList()

        try {
            // 分离出各个修改指令块
            val instructionBlocks = extractInstructionBlocks(aiInstructions)

            // 对每个指令块进行处理
            for (block in instructionBlocks) {
                when {
                    block.startsWith("REPLACE:") -> {
                        processReplaceInstruction(block, originalLines)
                    }
                    block.startsWith("INSERT_AFTER:") -> {
                        processInsertInstruction(block, originalLines)
                    }
                    block.startsWith("DELETE:") -> {
                        processDeleteInstruction(block, originalLines)
                    }
                }
            }

            // 将修改后的行重新组合成文件内容
            return originalLines.joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "应用文件绑定指令时出错: ${e.message}", e)
            // 出现错误时返回原始内容
            return originalContent
        }
    }

    /**
     * 从AI响应中提取指令块
     * @param aiInstructions AI返回的完整指令文本
     * @return 指令块列表
     */
    private fun extractInstructionBlocks(aiInstructions: String): List<String> {
        // 使用正则表达式来分割指令块，每个块都以指令关键字开头
        val instructionKeywords = listOf("REPLACE:", "INSERT_AFTER:", "DELETE:")
        val pattern = instructionKeywords.joinToString("|", prefix = "(?=", postfix = ")").toRegex()

        // 分割字符串，并过滤掉空字符串
        val blocks = aiInstructions.split(pattern).map { it.trim() }.filter { it.isNotEmpty() }

        return blocks
    }

    /**
     * 处理替换指令
     * @param instruction 替换指令块
     * @param originalLines 原始文件行列表
     */
    private fun processReplaceInstruction(instruction: String, originalLines: MutableList<String>) {
        // 解析替换指令
        val parts = instruction.split("替换为:", limit = 2)
        if (parts.size != 2) {
            Log.e(TAG, "替换指令格式不正确: $instruction")
            return
        }

        // 提取上下文代码和替换代码
        val contextPart = parts[0].removePrefix("REPLACE:").trim()
        val replacementPart = parts[1].trim()

        // 从上下文部分提取代码片段
        val contextCode = extractCodeFromMarkdown(contextPart)
        val replacementCode = extractCodeFromMarkdown(replacementPart)

        if (contextCode.isEmpty() || replacementCode.isEmpty()) {
            Log.e(TAG, "上下文代码或替换代码为空: $instruction")
            return
        }

        // 在原文件中查找上下文代码的位置
        val (startIndex, endIndex) = findCodeRange(contextCode, originalLines)

        if (startIndex == -1 || endIndex == -1) {
            Log.e(TAG, "无法在原文件中找到上下文代码: $contextCode")
            return
        }

        // 执行替换
        originalLines.subList(startIndex, endIndex + 1).clear()
        originalLines.addAll(startIndex, replacementCode.lines())
    }

    /**
     * 处理插入指令
     * @param instruction 插入指令块
     * @param originalLines 原始文件行列表
     */
    private fun processInsertInstruction(instruction: String, originalLines: MutableList<String>) {
        // 解析插入指令
        val parts = instruction.split("插入:", limit = 2)
        if (parts.size != 2) {
            Log.e(TAG, "插入指令格式不正确: $instruction")
            return
        }

        // 提取定位代码和插入代码
        val locationPart = parts[0].removePrefix("INSERT_AFTER:").trim()
        val insertPart = parts[1].trim()

        // 从定位部分提取代码片段
        val locationCode = extractCodeFromMarkdown(locationPart)
        val insertCode = extractCodeFromMarkdown(insertPart)

        if (locationCode.isEmpty() || insertCode.isEmpty()) {
            Log.e(TAG, "定位代码或插入代码为空: $instruction")
            return
        }

        // 在原文件中查找定位代码的位置
        val (_, lineIndex) = findCodeRange(locationCode, originalLines)

        if (lineIndex == -1) {
            Log.e(TAG, "无法在原文件中找到定位代码: $locationCode")
            return
        }

        // 在定位代码后插入新代码
        originalLines.addAll(lineIndex + 1, insertCode.lines())
    }

    /**
     * 处理删除指令
     * @param instruction 删除指令块
     * @param originalLines 原始文件行列表
     */
    private fun processDeleteInstruction(instruction: String, originalLines: MutableList<String>) {
        // 提取要删除的代码
        val deletePart = instruction.removePrefix("DELETE:").trim()
        val deleteCode = extractCodeFromMarkdown(deletePart)

        if (deleteCode.isEmpty()) {
            Log.e(TAG, "删除代码为空: $instruction")
            return
        }

        // 在原文件中查找要删除的代码位置
        val (startIndex, endIndex) = findCodeRange(deleteCode, originalLines)

        if (startIndex == -1 || endIndex == -1) {
            Log.e(TAG, "无法在原文件中找到删除代码: $deleteCode")
            return
        }

        // 执行删除
        originalLines.subList(startIndex, endIndex + 1).clear()
    }

    /**
     * 从Markdown代码块中提取代码
     * @param markdownText 可能包含Markdown格式的代码文本
     * @return 提取的代码
     */
    private fun extractCodeFromMarkdown(markdownText: String): String {
        // 匹配 ```code``` 格式
        val codePattern = Regex("```(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val match = codePattern.find(markdownText)

        return if (match != null) {
            // 提取代码块内容
            match.groupValues[1].trim()
        } else {
            // 如果没有匹配到代码块，返回原文本
            markdownText.trim()
        }
    }

    /**
     * 在原文件中查找代码片段的范围
     * @param codeSnippet 要查找的代码片段
     * @param fileLines 文件行列表
     * @return 匹配的起始和结束行索引对，如果未找到则为(-1, -1)
     */
    private fun findCodeRange(codeSnippet: String, fileLines: List<String>): Pair<Int, Int> {
        // 准备用于比较的片段行：过滤空行并去除前后空格
        val snippetLines = codeSnippet.lines().filter { it.isNotBlank() }.map { it.trim() }

        if (snippetLines.isEmpty()) {
            return Pair(-1, -1)
        }

        // 准备用于比较的文件行
        val trimmedFileLines = fileLines.map { it.trim() }

        // 1. 尝试精确定位整个代码片段
        for (i in 0..(trimmedFileLines.size - snippetLines.size)) {
            // 取出与代码片段同样大小的窗口进行比较
            var match = true
            var snippetIndex = 0
            var fileIndex = i
            while (snippetIndex < snippetLines.size && fileIndex < trimmedFileLines.size) {
                // 跳过文件中的空行
                if (trimmedFileLines[fileIndex].isBlank()) {
                    fileIndex++
                    continue
                }
                // 比较非空行
                if (trimmedFileLines[fileIndex] != snippetLines[snippetIndex]) {
                    match = false
                    break
                }
                fileIndex++
                snippetIndex++
            }

            if (match) {
                // 精确匹配成功，返回范围
                val startIndex = i
                // 这里的 endIndex 是我们扫描结束的位置（不含）
                val endIndex = fileIndex - 1
                Log.d(TAG, "findCodeRange: 精准匹配成功，范围: $startIndex - $endIndex")
                return Pair(startIndex, endIndex)
            }
        }

        // 2. 如果精确匹配失败，使用模糊匹配（基于第一行和最后一行）
        if (snippetLines.size >= 2) {
            val firstLine = snippetLines.first()
            val lastLine = snippetLines.last()

            val firstLineIndexes =
                    trimmedFileLines.mapIndexedNotNull { index, line ->
                        if (line == firstLine) index else null
                    }
            val lastLineIndexes =
                    trimmedFileLines.mapIndexedNotNull { index, line ->
                        if (line == lastLine) index else null
                    }

            // 寻找最合理的起止配对
            for (startIndex in firstLineIndexes) {
                // 从所有可能的结束行中，找到最接近且在起始行之后的那个
                val bestEndIndex = lastLineIndexes.filter { it >= startIndex }.minOrNull()
                if (bestEndIndex != null) {
                    Log.w(TAG, "findCodeRange: 使用模糊匹配定位代码范围 $startIndex - $bestEndIndex. 这可能不精确。")
                    return Pair(startIndex, bestEndIndex)
                }
            }
        }

        // 3. 终极备用方案：只匹配第一行
        val firstLine = snippetLines.first()
        val firstLineIndex = trimmedFileLines.indexOf(firstLine)
        if (firstLineIndex != -1) {
            Log.w(TAG, "findCodeRange: 模糊匹配失败, 使用单行匹配定位到行 $firstLineIndex. 风险较高。")
            // 假设代码块就是这么多行，这当然可能不准
            val endIndex = (firstLineIndex + snippetLines.size - 1).coerceAtMost(fileLines.size - 1)
            return Pair(firstLineIndex, endIndex)
        }

        Log.e(TAG, "findCodeRange: 无法在文件中定位代码片段:\n$codeSnippet")
        return Pair(-1, -1)
    }
}

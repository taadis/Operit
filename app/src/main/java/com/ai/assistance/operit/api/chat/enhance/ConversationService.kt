package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.UiControllerCommand
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalPromptManager
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import com.ai.assistance.operit.core.tools.ComputerDesktopActionResultData
import com.ai.assistance.operit.core.config.WebAutomationPrompts
import com.ai.assistance.operit.data.model.WebAutomationTaskType

/** Represents the result of a single step in a UI automation task. */
data class AutomationStepResult(
    /** A natural language explanation of the action taken. Null for the final state report. */
    val explanation: String?,
    /** The actual command executed. Null for the final state report. */
    val command: UiControllerCommand?,
    /** The final UI page info. Only present for the very last event in the flow. */
    val finalUiState: UIPageResultData? = null
)

/** 处理会话相关功能的服务类，包括会话总结、偏好处理和对话切割准备 */
class ConversationService(private val context: Context) {

    companion object {
        private const val TAG = "ConversationService"
    }

    private val apiPreferences = ApiPreferences(context)
    private val functionalPromptManager = FunctionalPromptManager(context)
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
            var systemPrompt = FunctionalPrompts.SUMMARY_PROMPT.trimIndent()

            // 如果存在上一次的摘要，将其添加到系统提示中，为模型提供更明确的上下文。
            if (previousSummary != null && previousSummary.isNotBlank()) {
                systemPrompt +=
                        """

                上一次的摘要（用于继承上下文）：
                ${previousSummary.trim()}
                请将以上摘要中的关键信息，与本次新的对话内容相融合，生成一份全新的、更完整的摘要。
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
            val summaryContent = ChatUtils.removeThinkingContent(contentBuilder.toString().trim())

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
                apiPreferences.updateTokensForFunction(FunctionType.SUMMARY, inputTokens, outputTokens)
                Log.d(TAG, "已将总结token统计添加到用户偏好分析token计数中")
            } catch (e: Exception) {
                Log.e(TAG, "更新token统计失败", e)
            }

            return summaryContent
        } catch (e: Exception) {
            Log.e(TAG, "生成总结时出错", e)
            // return "对话摘要：生成摘要时出错，但对话仍在继续。"
            throw e
        }
    }

    /**
     * 为聊天准备对话历史记录
     * @param chatHistory 原始聊天历史
     * @param processedInput 处理后的用户输入
     * @param workspacePath 当前绑定的工作区路径，可以为null
     * @param packageManager 包管理器
     * @param promptFunctionType 提示函数类型
     * @param thinkingGuidance 是否需要思考指导
     * @return 准备好的对话历史列表
     */
    suspend fun prepareConversationHistory(
            chatHistory: List<Pair<String, String>>,
            processedInput: String,
            workspacePath: String?,
            packageManager: PackageManager,
            promptFunctionType: PromptFunctionType,
            thinkingGuidance: Boolean = false
    ): List<Pair<String, String>> {
        val preparedHistory = mutableListOf<Pair<String, String>>()
        conversationMutex.withLock {
            // Add system prompt if not already present
            if (!chatHistory.any { it.first == "system" }) {
                val activeProfile = preferencesManager.getUserPreferencesFlow().first()
                val preferencesText = buildPreferencesText(activeProfile)

                // Check if planning is enabled
                val planningEnabled = apiPreferences.enableAiPlanningFlow.first()

                // Get prompts for the specific function type from FunctionalPromptManager
                val (introPrompt, tonePrompt) =
                        functionalPromptManager.getPromptForFunction(promptFunctionType)

                // 获取系统提示词，现在传入workspacePath
                val systemPrompt =
                        SystemPromptConfig.getSystemPromptWithCustomPrompts(
                        packageManager,
                        workspacePath,
                        planningEnabled,
                        introPrompt,
                                tonePrompt,
                                thinkingGuidance
                )

                if (preferencesText.isNotEmpty()) {
                    preparedHistory.add(
                            0,
                            Pair(
                                    "system",
                                    "$systemPrompt\n\nUser preference description: $preferencesText"
                            )
                    )
                } else {
                    preparedHistory.add(0, Pair("system", systemPrompt))
                }
            }

            // Process each message in chat history
            chatHistory.forEachIndexed { index, message ->
                val role = message.first
                val content = message.second

                // If it's an assistant message, check for tool results
                if (role == "assistant") {
                    val xmlTags = splitXmlTag(content)
                    if (xmlTags.isNotEmpty()) {
                        // Process the message with tool results
                        processChatMessageWithTools(content, xmlTags, preparedHistory, index, chatHistory.size)
                    } else {
                        // Add the message as is
                        preparedHistory.add(message)
                    }
                } else {
                    // Add user or system messages as is
                    preparedHistory.add(message)
                }
            }
        }
        return preparedHistory
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
            conversationHistory: MutableList<Pair<String, String>>,
            messageIndex: Int,
            totalMessages: Int
    ) {
        if (xmlTags.isEmpty()) {
            // 如果没有XML标签，直接添加为AI消息
            conversationHistory.add(Pair("assistant", content))
            return
        }

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

            // 应用内存优化: 只有当消息不是最近25条时才触发
            val distanceFromEnd = totalMessages - 1 - messageIndex
            if (distanceFromEnd > 25 && tagContent.length > 1000 && tagName == "tool_result") {
                 Log.d(TAG, "Optimizing tool result for message at index $messageIndex (distance from end: $distanceFromEnd)")
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

    /** Data class for search-replace operations, used for JSON deserialization. */
    private data class SearchReplaceOperation(val search: String, val replace: String)

    /**
     * Processes file binding using a two-step approach to balance token cost and reliability.
     * 1. Attempts a custom, low-token "loose text patch" that is whitespace-insensitive.
     * 2. If that fails, falls back to a robust but more token-intensive full-content merge.
     *
     * @param originalContent The original content of the file.
     * @param aiGeneratedCode The AI-generated code with placeholders, representing the desired
     * changes.
     * @param multiServiceManager The service manager for AI communication.
     * @return A Pair containing the final merged content and a diff string representing the
     * changes.
     */
    suspend fun processFileBinding(
            originalContent: String,
            aiGeneratedCode: String,
            multiServiceManager: MultiServiceManager
    ): Pair<String, String> {
        // --- Attempt 1: Custom Loose Text Patch (Low Token Cost) ---
        Log.d(TAG, "Attempt 1: Trying Custom Loose Text Patch...")
        try {
            val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
            val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()

            val systemPrompt = FunctionalPrompts.FILE_BINDING_PATCH_PROMPT.trimIndent()

            val userPrompt =
"""
**Original File Content:**
```
$normalizedOriginalContent
```
**AI's Edit Request:**
```
$normalizedAiGeneratedCode
```
Now, generate ONLY the patch in the custom format based on all the rules.
""".trimIndent()
            val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }
            val fileBindingService =
                    multiServiceManager.getServiceForFunction(FunctionType.FILE_BINDING)

            val contentBuilder = StringBuilder()
            fileBindingService.sendMessage(
                            userPrompt,
                            listOf(Pair("system", systemPrompt)),
                            modelParameters
                    )
                    .collect { content -> contentBuilder.append(content) }

            val patchResponse = ChatUtils.removeThinkingContent(contentBuilder.toString())

            val (success, patchedContent) =
                    applyLooseTextPatch(normalizedOriginalContent, patchResponse)

            if (success) {
                Log.d(TAG, "Attempt 1: Custom Loose Text Patch succeeded.")
                apiPreferences.updateTokensForFunction(
                        FunctionType.FILE_BINDING,
                        fileBindingService.inputTokenCount,
                        fileBindingService.outputTokenCount
                )

                val finalDiff =
                        UnifiedDiffUtils.generateUnifiedDiff(
                                        "a/file",
                                        "b/file",
                                        normalizedOriginalContent.lines(),
                                        DiffUtils.diff(
                                                normalizedOriginalContent.lines(),
                                                patchedContent.lines()
                                        ),
                                        3
                                )
                                .joinToString("\n")

                return Pair(patchedContent, finalDiff)
            } else {
                Log.w(
                        TAG,
                        "Attempt 1: Custom Loose Text Patch failed. Falling back to robust full merge."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 1: Error during Custom Loose Text Patch. Falling back...", e)
        }

        // --- Attempt 2: Robust Full-Content Merge (Fallback) ---
        Log.d(TAG, "Attempt 2 (Fallback): Trying robust full-content merge...")
        try {
            val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
            val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()
            val mergeSystemPrompt = FunctionalPrompts.FILE_BINDING_MERGE_PROMPT.trimIndent()

            val mergeUserPrompt =
"""
**Original File Content:**
```
$normalizedOriginalContent
```
**AI-Generated Code (with placeholders):**
```
$normalizedAiGeneratedCode
```
Now, generate ONLY the complete and final merged file content.
""".trimIndent()

            val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }
            val fileBindingService =
                    multiServiceManager.getServiceForFunction(FunctionType.FILE_BINDING)

            val contentBuilder = StringBuilder()
            fileBindingService.sendMessage(
                            mergeUserPrompt,
                            listOf(Pair("system", mergeSystemPrompt)),
                            modelParameters
                    )
                    .collect { content -> contentBuilder.append(content) }

            val mergedContentFromAI =
                    ChatUtils.removeThinkingContent(contentBuilder.toString().trim())

            if (mergedContentFromAI.isBlank()) {
                Log.w(TAG, "Attempt 2: Full merge returned empty content. Returning original.")
                return Pair(originalContent, "")
            }

            val diffString =
                    UnifiedDiffUtils.generateUnifiedDiff(
                                    "a/file",
                                    "b/file",
                                    normalizedOriginalContent.lines(),
                                    DiffUtils.diff(
                                            normalizedOriginalContent.lines(),
                                            mergedContentFromAI.lines()
                                    ),
                                    3
                            )
                            .joinToString("\n")

            Log.d(TAG, "Attempt 2: Robust full-content merge successful.")
            apiPreferences.updateTokensForFunction(
                    FunctionType.FILE_BINDING,
                    fileBindingService.inputTokenCount,
                    fileBindingService.outputTokenCount
            )
            return Pair(mergedContentFromAI, diffString)
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 2: Error during robust full-merge fallback.", e)
            return Pair(originalContent, "Error during fallback file binding: ${e.message}")
        }
    }

    /**
     * Normalizes a block of text for a "loose" comparison. This makes the comparison insensitive to
     * leading/trailing whitespace on each line, and also to the amount of internal whitespace
     * between non-whitespace characters. It preserves the number of lines (including blank ones) to
     * ensure an unambiguous replacement.
     */
    private fun normalizeBlock(block: String): String {
        return block.lines().joinToString("\n") { it.trim().replace("\\s+".toRegex(), " ") }
    }

    /**
     * Applies a series of search-and-replace operations from a custom patch format using a "loose"
     * matching algorithm that ignores leading/trailing whitespace on each line.
     *
     * @return A Pair of (Boolean, String) indicating success and the modified content.
     */
    private fun applyLooseTextPatch(
            originalContent: String,
            patchText: String
    ): Pair<Boolean, String> {
        var modifiedContent = originalContent
        try {
            val patchRegex =
                    """(?s)<<<<<<< SEARCH\n(.*?)\n=======\n(.*?)\n>>>>>>> REPLACE""".toRegex()
            val operations =
                    patchRegex
                            .findAll(patchText)
                            .map {
                                // groupValues[1] is the SEARCH block, groupValues[2] is the REPLACE
                                // block
                                it.groupValues[1].trim() to it.groupValues[2].trim()
                            }
                            .toList()

            if (operations.isEmpty()) {
                Log.w(TAG, "Custom patch was empty or did not match expected format.")
                return Pair(false, originalContent)
            }

            for ((searchBlock, replaceBlock) in operations) {
                val normalizedSearch = normalizeBlock(searchBlock)
                if (normalizedSearch.isEmpty()) continue

                val originalLines = modifiedContent.lines()
                val searchLinesCount = searchBlock.lines().size
                var matchIndex = -1

                // Find a unique loose match
                for (i in 0..(originalLines.size - searchLinesCount)) {
                    val windowBlock =
                            originalLines.subList(i, i + searchLinesCount).joinToString("\n")
                    if (normalizeBlock(windowBlock) == normalizedSearch) {
                        if (matchIndex != -1) { // Ambiguous match
                            Log.w(
                                    TAG,
                                    "Loose Patch failed: ambiguous match for search block:\n$searchBlock"
                            )
                            return Pair(false, originalContent)
                        }
                        matchIndex = i
                    }
                }

                if (matchIndex != -1) { // Unique match found
                    val modifiedLinesList = originalLines.toMutableList()
                    repeat(searchLinesCount) { modifiedLinesList.removeAt(matchIndex) }
                    modifiedLinesList.addAll(matchIndex, replaceBlock.lines())
                    modifiedContent = modifiedLinesList.joinToString("\n")
                } else { // No match found
                    Log.w(
                            TAG,
                            "Loose Patch failed: could not find a unique match for block:\n$searchBlock"
                    )
                    return Pair(false, originalContent)
                }
            }
            return Pair(true, modifiedContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply custom loose text patch.", e)
            return Pair(false, originalContent)
        }
    }

    /**
     * Gets a UI control command from the specialized AI model.
     *
     * @param uiState A description of the current UI elements.
     * @param taskGoal The objective for the current step.
     * @param history A list of previous commands and their outcomes.
     * @param multiServiceManager The service manager for AI communication.
     * @return A raw string response from the AI.
     */
    suspend fun getUiControllerCommand(
        uiState: String,
        taskGoal: String,
        history: List<Pair<String, String>>,
        multiServiceManager: MultiServiceManager
    ): String {
        val systemPrompt = FunctionalPrompts.UI_CONTROLLER_PROMPT.trimIndent()

        val userPrompt = """
**Current UI State:**
```json
$uiState
```

**Task Goal:**
"$taskGoal"

**Execution History:**
```
$history
```

Provide the next action as a single JSON object.
""".trimIndent()

        val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }
        val uiControllerService =
            multiServiceManager.getServiceForFunction(FunctionType.UI_CONTROLLER)

        val contentBuilder = StringBuilder()
        val stream = uiControllerService.sendMessage(
            message = userPrompt,
            chatHistory = listOf(Pair("system", systemPrompt)),
            modelParameters = modelParameters
        )

        stream.collect { content -> contentBuilder.append(content) }

        try {
            val inputTokens = uiControllerService.inputTokenCount
            val outputTokens = uiControllerService.outputTokenCount
            Log.d(TAG, "UI Controller使用了输入token: $inputTokens, 输出token: $outputTokens")
            apiPreferences.updateTokensForFunction(
                    FunctionType.UI_CONTROLLER,
                    inputTokens,
                    outputTokens
            )
            Log.d(TAG, "已将UI Controller token统计添加到用户偏好分析token计数中")
        } catch (e: Exception) {
            Log.e(TAG, "更新token统计失败", e)
        }

        return ChatUtils.removeThinkingContent(contentBuilder.toString().trim())
    }

    /**
     * Gets a web control command from the specialized AI model for web automation.
     *
     * @param webState A description of the current web page elements.
     * @param taskGoal The objective for the current step.
     * @param history A list of previous commands and their outcomes.
     * @param multiServiceManager The service manager for AI communication.
     * @return A raw string response from the AI, expected to be a JSON object.
     */
    suspend fun getWebControllerCommand(
        webState: String,
        taskGoal: String,
        history: List<Pair<String, String>>,
        multiServiceManager: MultiServiceManager,
        taskType: WebAutomationTaskType,
        collectedNotes: List<String>
    ): String {
        val systemPrompt = WebAutomationPrompts.getWebAutomationSystemPrompt(taskType)

        val userPrompt = """
        **Current Web Page State:**
        ```
        $webState
        ```

        **Task Goal:**
        "$taskGoal"

        **Execution History:**
        ```
        $history
        ```
        
        **Collected Notes:**
        ```
        ${collectedNotes.joinToString("\n- ")}
        ```

        Provide the next action as a single JSON object.
        """.trimIndent()

        val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }
        val webControllerService =
            multiServiceManager.getServiceForFunction(FunctionType.UI_CONTROLLER)

        val contentBuilder = StringBuilder()
        webControllerService.sendMessage(
            message = userPrompt,
            chatHistory = listOf(Pair("system", systemPrompt)),
            modelParameters = modelParameters
        ).collect { content -> contentBuilder.append(content) }

        try {
            val inputTokens = webControllerService.inputTokenCount
            val outputTokens = webControllerService.outputTokenCount
            Log.d(TAG, "Web Controller used input tokens: $inputTokens, output tokens: $outputTokens")
            apiPreferences.updateTokensForFunction(
                    FunctionType.UI_CONTROLLER,
                    inputTokens,
                    outputTokens
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update token stats for Web Controller", e)
        }

        return ChatUtils.removeThinkingContent(contentBuilder.toString().trim())
    }

        /**
     * Executes a full web automation task by repeatedly querying the web sub-agent and executing its commands.
     *
     * @param taskGoal The high-level goal for the entire task.
     * @param multiServiceManager The service manager to get the correct AI service.
     * @param toolHandler The handler to execute UI commands.
     * @return A Flow of automation step results.
     */
    suspend fun executeWebAutomationTask(
        taskGoal: String,
        multiServiceManager: MultiServiceManager,
        toolHandler: AIToolHandler
    ): Flow<AutomationStepResult> = flow {
        val executionHistory = mutableListOf<Pair<String, String>>()
        val collectedNotes = mutableListOf<String>()
        val maxAttempts = 10
        var loopShouldBreak = false

        // Step 0: Determine the task type from the goal
        val taskType = determineWebTaskType(taskGoal, multiServiceManager)
        Log.d(TAG, "Determined Web Automation Task Type: $taskType")


        for (attempt in 1..maxAttempts) {
            if (loopShouldBreak) break

            // 1. Get current web page state
            val pageInfoResult = toolHandler.executeTool(AITool("computer_get_page_info", emptyList()))
            if (!pageInfoResult.success || pageInfoResult.result !is ComputerDesktopActionResultData) {
                emit(AutomationStepResult("Failed to get web page state.", UiControllerCommand("interrupt", "Failed to get web page state."), null))
                break
            }
            val resultData = pageInfoResult.result as ComputerDesktopActionResultData
            val webStateBuilder = StringBuilder()

            // Append tab information to the state
            resultData.tabs?.let { tabs ->
                webStateBuilder.appendLine("Open Tabs (${tabs.size}):")
                tabs.forEachIndexed { index, tab ->
                    webStateBuilder.appendLine("- [${if (tab.isActive) "*" else " "}] Tab ${index} (ID: ${tab.id}): ${tab.title} - ${tab.url}")
                }
                webStateBuilder.appendLine()
            }

            // Append page content information
            webStateBuilder.appendLine("--- Active Page Content ---")
            webStateBuilder.append(resultData.pageContent?.toTreeString() ?: "Page is empty or content could not be retrieved.")

            val webState = webStateBuilder.toString()

            // 2. Get next command from the sub-agent
            var commandJson = getWebControllerCommand(
                webState,
                taskGoal,
                executionHistory,
                multiServiceManager,
                taskType,
                collectedNotes
            )

            Log.d(TAG, "Web sub-agent command response (attempt $attempt): $commandJson")

            // Attempt to extract JSON from markdown code blocks
            if (commandJson.contains("```")) {
                val startIndex = commandJson.indexOf('{')
                val endIndex = commandJson.lastIndexOf('}')
                if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                    commandJson = commandJson.substring(startIndex, endIndex + 1)
                }
            }

            try {
                val responseJson = JSONObject(commandJson)
                val explanation = responseJson.getString("explanation")

                val toolJson: JSONObject
                val rawTool = responseJson.opt("tool") // Use opt to avoid exception if missing

                when (rawTool) {
                    is String -> {
                        // Handle cases like "tool": "complete" or "tool": "interrupt"
                        toolJson = JSONObject().apply {
                            put("name", rawTool)
                            put("parameters", JSONArray()) // Assume no parameters for string tools
                        }
                        Log.d(TAG, "AI returned a string for 'tool'. Converted to JSON: $toolJson")
                    }
                    is JSONObject -> {
                        // This is the expected format
                        toolJson = rawTool
                    }
                    else -> {
                        // This case handles null (missing 'tool') or other unexpected types
                        throw JSONException("'tool' field is missing or has an invalid type.")
                    }
                }
                
                val toolName = toolJson.getString("name")

                // New: Check for and process notes from the AI
                if (responseJson.has("notes")) {
                    val notes = responseJson.getString("notes")
                    if (notes.isNotBlank()) {
                        collectedNotes.add(notes)
                        Log.d(TAG, "AI added a note: $notes")
                    }
                }

                if (toolName == "complete") {
                    // When the task is complete, the explanation should contain the final summary.
                    // We can also include all collected notes for a full report.
                    val finalReport = """
                        Task completed.
                        Final Explanation: $explanation
                        ---
                        Collected Notes:
                        - ${collectedNotes.joinToString("\n- ")}
                    """.trimIndent()
                    emit(AutomationStepResult(finalReport, UiControllerCommand(toolName, ""), null))
                    loopShouldBreak = true
                    continue
                }
                
                if (toolName == "interrupt") {
                    emit(AutomationStepResult(explanation, UiControllerCommand(toolName, ""), null))
                    loopShouldBreak = true
                    continue
                }

                val parameters = toolJson.getJSONArray("parameters")
                val toolParams = mutableListOf<ToolParameter>()
                for (i in 0 until parameters.length()) {
                    val param = parameters.getJSONObject(i)
                    toolParams.add(ToolParameter(param.getString("name"), param.getString("value")))
                }

                val toolToExecute = AITool(toolName, toolParams)
                emit(AutomationStepResult(explanation, UiControllerCommand(toolName, toolParams.associate { it.name to it.value }), null))
                executionHistory.add(Pair("action", explanation))

                // 3. Execute the command
                val result = toolHandler.executeTool(toolToExecute)
                val outcome = if (result.success) "Success. ${result.result}" else "Failed. ${result.error}"
                executionHistory.add(Pair("outcome", outcome))

                if (!result.success) {
                    emit(AutomationStepResult("Action failed: ${result.error}", UiControllerCommand("interrupt", "Action failed: ${result.error}"), null))
                    loopShouldBreak = true
                } else {
                    // After a successful action that might navigate, wait for the page to load.
                    if (toolName == "computer_open_browser" || toolName == "computer_click_element" || toolName == "computer_go_back") {
                        Log.d(TAG, "Navigation action successful, waiting for page to load...")
                        val awaitResult = toolHandler.executeTool(AITool("computer_await_page_load", emptyList()))
                        if (!awaitResult.success) {
                            emit(AutomationStepResult("Page failed to load after action.", UiControllerCommand("interrupt", "Page failed to load after action."), null))
                            loopShouldBreak = true
                        }
                    }
                }

            } catch (e: JSONException) {
                val errorMessage = "Failed to parse web sub-agent JSON response: $commandJson"
                Log.e(TAG, errorMessage, e)
                executionHistory.add(Pair("error", errorMessage))
            }
        }

        if (!loopShouldBreak) {
             val finalReport = """
                Reached max attempts ($maxAttempts).
                ---
                Collected Notes:
                - ${collectedNotes.joinToString("\n- ")}
            """.trimIndent()
            emit(AutomationStepResult(finalReport, UiControllerCommand("interrupt", "Reached max attempts."), null))
        }
    }

    /**
     * Determines the WebAutomationTaskType by asking an AI to classify the user's goal.
     */
    private suspend fun determineWebTaskType(taskGoal: String, multiServiceManager: MultiServiceManager): WebAutomationTaskType {
        val systemPrompt = FunctionalPrompts.WEB_TASK_CLASSIFICATION_PROMPT.trimIndent()

        val userPrompt = "Task Goal: \"$taskGoal\""

        val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }
        val classificationService = multiServiceManager.getServiceForFunction(FunctionType.UI_CONTROLLER) // Use a general-purpose model

        val contentBuilder = StringBuilder()
        classificationService.sendMessage(
            message = userPrompt,
            chatHistory = listOf(Pair("system", systemPrompt)),
            modelParameters = modelParameters
        ).collect { content -> contentBuilder.append(content) }

        val response = ChatUtils.removeThinkingContent(contentBuilder.toString().trim())

        return when {
            response.contains("INFORMATION_GATHERING", ignoreCase = true) -> WebAutomationTaskType.INFORMATION_GATHERING
            response.contains("EXPLORATION", ignoreCase = true) -> WebAutomationTaskType.EXPLORATION
            response.contains("TRANSACTIONAL", ignoreCase = true) -> WebAutomationTaskType.TRANSACTIONAL
            else -> WebAutomationTaskType.GENERAL_INTERACTION
        }
    }

    /**
     * Executes a full UI automation task by repeatedly querying the AI and executing its commands.
     *
     * @param initialUiState The initial description of the UI.
     * @param taskGoal The high-level goal for the entire task.
     * @param multiServiceManager The service manager to get the correct AI service.
     * @param toolHandler The handler to execute UI commands.
     * @return A Flow of UiControllerCommands representing the actions taken.
     */
    suspend fun executeUiAutomationTask(
        initialUiState: String,
        taskGoal: String,
        multiServiceManager: MultiServiceManager,
        toolHandler: AIToolHandler
    ): Flow<AutomationStepResult> = flow {
        var currentUiState = initialUiState
        val executionHistory = mutableListOf<Pair<String, String>>()
        val maxAttempts = 5
        var loopShouldBreak = false

        for (attempt in 1..maxAttempts) {
            if (loopShouldBreak) break

            var commandJson = getUiControllerCommand(
                currentUiState,
                taskGoal,
                executionHistory,
                multiServiceManager
            )

            Log.d(TAG, "AI command response (attempt $attempt): $commandJson")

            // Attempt to extract JSON from markdown code blocks
            if (commandJson.contains("```")) {
                val startIndex = commandJson.indexOf('{')
                val endIndex = commandJson.lastIndexOf('}')
                if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                    commandJson = commandJson.substring(startIndex, endIndex + 1)
                }
            }

            try {
                val responseJson = JSONObject(commandJson)
                val explanation = responseJson.getString("explanation")
                val command = responseJson.getJSONObject("command")
                val type = command.getString("type")
                
                // Robustly find the arguments, whether they are in an "arg" object or at the top level.
                var rawArg = command.opt("arg")
                if (rawArg == null) {
                    val potentialArgKeys = setOf("x", "y", "start_x", "start_y", "end_x", "end_y", "text", "key_code", "package_name", "include_system_apps")
                    val argObject = JSONObject()
                    var keyFoundInRoot = false
                    command.keys().forEach { key ->
                        if (potentialArgKeys.contains(key)) {
                            argObject.put(key, command.get(key))
                            keyFoundInRoot = true
                        }
                    }
                    if (keyFoundInRoot) {
                        rawArg = argObject
                        Log.d(TAG, "Reconstructed 'arg' from root: $rawArg")
                    } else if (command.has("type") && command.length() == 1) {
                         // This case handles simple commands like `{"type": "complete"}` that have no args.
                         // Do nothing, rawArg remains null, which will become an empty string.
                    }
                }

                val arg: Any = when (rawArg) {
                    is JSONObject -> rawArg.toMap()
                    else -> rawArg?.toString() ?: ""
                }

                val uiCommand = UiControllerCommand(type, arg)
                Log.d(TAG, "Parsed UI Command: $uiCommand, Explanation: $explanation")
                // Always emit the step result.
                emit(AutomationStepResult(explanation, uiCommand))

                executionHistory.add(Pair("action", explanation))

                when (type) {
                    "complete", "interrupt" -> {
                        Log.d(TAG, "Task flow ending due to '$type' command. Reason: $explanation")
                        loopShouldBreak = true // Set flag to break after this iteration.
                    }
                    "tap", "swipe", "set_input_text", "press_key", "start_app", "list_installed_apps" -> {
                        val tool = createToolFromJson(type, arg)
                        Log.d(TAG, "Executing tool: ${tool.name} with params: ${tool.parameters}")
                        val result = toolHandler.executeTool(tool)
                        Log.d(TAG, "Tool execution result: $result")

                        val outcomeDescription = if (result.success) {
                            val resultString = result.result?.toString()
                            // For app lists, allow more content to ensure the AI gets full context.
                            val maxLength = if (type == "list_installed_apps") 2000 else 150
                            val truncatedResult = resultString?.take(maxLength)
                            "Outcome: Success. ${truncatedResult}"
                        } else {
                            "Outcome: Failed. ${result.error}"
                        }
                        executionHistory.add(Pair("outcome", outcomeDescription))

                        if (!result.success) {
                            emit(AutomationStepResult("Action failed: ${result.error}", UiControllerCommand("interrupt", "Action failed: ${result.error}")))
                            loopShouldBreak = true
                        } else {
                             val pageInfoResult = toolHandler.executeTool(AITool("get_page_info", emptyList()))
                             if (pageInfoResult.success && pageInfoResult.result is UIPageResultData) {
                                 currentUiState = flattenUiInfo(pageInfoResult.result)
                                 Log.d(TAG, "Successfully updated UI state.")
                             } else {
                                 Log.w(TAG, "Failed to get updated UI state after action.")
                                 emit(AutomationStepResult("Failed to get updated UI state.", UiControllerCommand("interrupt", "Failed to get updated UI state.")))
                                 loopShouldBreak = true
                             }
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unknown command type '$type' received.")
                        emit(AutomationStepResult("Unknown command type '$type' received from AI.", UiControllerCommand("interrupt", "Unknown command type '$type' received from AI.")))
                        loopShouldBreak = true
                    }
                }
            } catch (e: JSONException) {
                val errorMessage = "Failed to parse AI response as JSON. Raw response: \"$commandJson\". Error: ${e.message}"
                Log.e(TAG, errorMessage)
                executionHistory.add(Pair("error", errorMessage))
                // Continue to the next attempt, allowing the AI to correct itself.
            }
        }

        // If the loop finished due to max attempts, emit a final interrupt step.
        if (!loopShouldBreak) {
            Log.w(TAG, "Task flow ending due to max attempts reached.")
            emit(AutomationStepResult("Reached max attempts ($maxAttempts).", UiControllerCommand("interrupt", "Reached max attempts ($maxAttempts).")))
        }

        // --- Final Step: Always report the final UI state ---
        Log.d(TAG, "Automation flow finished. Getting final page info.")
        val finalPageInfoResult = toolHandler.executeTool(AITool("get_page_info", emptyList()))
        if (finalPageInfoResult.success && finalPageInfoResult.result is UIPageResultData) {
            // Emit the final UI state as the last item in the flow.
            emit(AutomationStepResult(explanation = "Final screen state.", command = null, finalUiState = finalPageInfoResult.result))
        } else {
            Log.w(TAG, "Failed to get final UI state: ${finalPageInfoResult.error}")
            // Emit a final failure state.
            val finalUiState = UIPageResultData("Unknown", "Unknown", SimplifiedUINode(null, "Failed to get final state: ${finalPageInfoResult.error}", null, null, null, false, emptyList()))
            emit(AutomationStepResult(explanation = "Could not get final screen state.", command = null, finalUiState = finalUiState))
        }
    }

    /**
     * Flattens the hierarchical UI node structure into a simple, flat list of key elements.
     * This provides a much cleaner context for the AI to make decisions.
     */
    private fun flattenUiInfo(pageInfo: UIPageResultData): String {
        val clickableElements = mutableListOf<String>()
        val screenTexts = mutableListOf<String>()

        fun traverse(node: SimplifiedUINode) {
            // If the node is clickable, treat it as an atomic unit. We'll gather all text from
            // its entire subtree to form a comprehensive description for the AI.
            if (node.isClickable) {
                val parts = mutableListOf<String>()
                
                // Start by collecting standard properties like resource ID, class, and bounds.
                node.resourceId?.takeIf { it.isNotBlank() }?.let { parts.add("id: $it") }

                // --- NEW: Recursively find all text and content descriptions in the subtree ---
                val descriptiveTexts = mutableListOf<String>()
                fun findTextsRecursively(n: SimplifiedUINode) {
                    n.text?.takeIf { it.isNotBlank() }?.let { descriptiveTexts.add(it) }
                    n.contentDesc?.takeIf { it.isNotBlank() }?.let { descriptiveTexts.add(it) }
                    n.children.forEach(::findTextsRecursively)
                }
                findTextsRecursively(node)

                // Combine all found texts into a single descriptive string. This is crucial for
                // elements where the text label is in a child node of the clickable area.
                val combinedText = descriptiveTexts.distinct().joinToString(" | ")
                if (combinedText.isNotBlank()) {
                    // Using "desc" to signify this is a constructed description. Increased length.
                    parts.add("desc: \"${combinedText.replace("\"", "'").take(80)}\"")
                }
                // --- END NEW ---

                node.className?.let { parts.add("class: ${it.substringAfterLast('.')}") }
                node.bounds?.let { parts.add("bounds: ${it.replace(' ', ',')}") }

                // Only add the element if it has some identifiable information.
                if (parts.isNotEmpty()) {
                    clickableElements.add("[${parts.joinToString(", ")}]")
                }
                // Once an element is identified as clickable, we don't process its children separately.
            } else {
                // If the node is not clickable, add its text for general context and continue traversal.
                node.text?.takeIf { it.isNotBlank() }?.let {
                    screenTexts.add("\"${it.replace("\"", "'").take(70)}\"")
                }
                node.children.forEach(::traverse)
            }
        }

        traverse(pageInfo.uiElements)

        // Use distinct to remove duplicate text entries from non-clickable elements.
        val distinctScreenTexts = screenTexts.distinct()

        return """
        Package: ${pageInfo.packageName}
        Activity: ${pageInfo.activityName}
        Clickable Elements:
        ${clickableElements.joinToString("\n")}
        Screen Text (
        for context):
        ${distinctScreenTexts.joinToString("\n")}
        """.trimIndent()
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keysItr = this.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value = this.get(key)
            if (value is JSONObject) {
                value = value.toMap()
            }
            if (value is JSONArray) {
                value = value.toList()
            }
            map[key] = value
        }
        return map
    }

    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until this.length()) {
            var value = this.get(i)
            if (value is JSONObject) {
                value = value.toMap()
            }
            if (value is JSONArray) {
                value = value.toList()
            }
            list.add(value)
        }
        return list
    }

    private fun createToolFromJson(type: String, arg: Any): AITool {
        val parameters = mutableListOf<ToolParameter>()
        when (arg) {
            is Map<*, *> -> {
                arg.forEach { (key, value) ->
                    val stringValue = when (value) {
                        is Double -> {
                            // If the double has no fractional part, convert to Int string to avoid parse errors.
                            if (value % 1.0 == 0.0) {
                                value.toInt().toString()
                            } else {
                                value.toString()
                            }
                        }
                        else -> value.toString()
                    }
                    parameters.add(ToolParameter(key.toString(), stringValue))
                }
            }
            is String -> {
                 // Fallback for when the AI returns a raw string instead of a JSON object.
                 when (type) {
                     "press_key" -> parameters.add(ToolParameter("key_code", arg))
                     "set_input_text" -> parameters.add(ToolParameter("text", arg))
                     "start_app" -> parameters.add(ToolParameter("package_name", arg))
                 }
            }
        }
        return AITool(type, parameters)
    }
}

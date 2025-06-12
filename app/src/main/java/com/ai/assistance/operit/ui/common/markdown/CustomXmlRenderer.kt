package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 支持多种 XML 标签的自定义渲染器 包含高效的前缀检测，直接解析标签类型 */
class CustomXmlRenderer(private val fallback: XmlContentRenderer = DefaultXmlRenderer()) :
        XmlContentRenderer {
    // 定义渲染器能够处理的内置标签集合
    private val builtInTags = setOf("think", "tool", "status", "plan_item", "plan_update")

    @Composable
    override fun RenderXmlContent(xmlContent: String, modifier: Modifier, textColor: Color) {
        val trimmedContent = xmlContent.trim()
        val tagName = extractTagName(trimmedContent)

        // 如果无法识别为有效的XML标签，则交由默认渲染器处理
        if (tagName == null) {
            fallback.RenderXmlContent(xmlContent, modifier, textColor)
            return
        }

        // 根据新规则处理未闭合的标签
        if (!isXmlFullyClosed(trimmedContent)) {
            if (tagName in builtInTags && tagName != "tool") {
                // 是内置标签但未闭合，则不显示任何内容，等待其闭合
                return
            } else if (!(tagName in builtInTags)) {
                // 是未知标签且未闭合，则交由默认渲染器处理
                fallback.RenderXmlContent(xmlContent, modifier, textColor)
                return
            }
        }

        // 标签已正确闭合，根据标签名分发到对应的渲染函数
        when (tagName) {
            "think" -> renderThinkContent(trimmedContent, modifier, textColor)
            "tool" -> renderToolRequest(trimmedContent, modifier, textColor)
            "status" -> renderStatus(trimmedContent, modifier, textColor)
            "plan_item" -> renderPlanItem(trimmedContent, modifier, textColor)
            "plan_update" -> renderPlanUpdate(trimmedContent, modifier, textColor)
            else -> fallback.RenderXmlContent(xmlContent, modifier, textColor)
        }
    }

    /**
     * 从XML字符串中提取第一个标签的名称。
     * 例如: "<think>...</think>" -> "think"
     */
    private fun extractTagName(content: String): String? {
        val openTagRegex = "<([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
        return openTagRegex.find(content)?.groupValues?.getOrNull(1)
    }

    /**
     * 检查XML标签是否完全闭合。
     * 支持标准配对标签 (<tag>...</tag>) 和自闭合标签 (<tag/>)。
     */
    private fun isXmlFullyClosed(content: String): Boolean {
        val tagName = extractTagName(content) ?: return false

        // 处理自闭合标签，例如 <status type="completion"/>
        if (content.endsWith("/>")) {
            return true
        }

        // 处理标准配对标签
        val closeTag = "</$tagName>"
        return content.contains(closeTag)
    }

    /** 从XML内容中提取纯文本内容 */
    private fun extractContentFromXml(content: String, tagName: String): String {
        val startTag = "<$tagName"
        val endTag = "</$tagName>"

        // 找到开始标签的结束位置
        val startTagEnd = content.indexOf('>', content.indexOf(startTag)) + 1
        val endIndex = content.lastIndexOf(endTag)

        return if (startTagEnd > 0 && endIndex > startTagEnd) {
            content.substring(startTagEnd, endIndex).trim()
        } else {
            // 如果无法正确提取，返回原始内容
            content
        }
    }

    /** 从工具调用XML提取参数内容 */
    private fun extractParamsFromTool(content: String): Map<String, String> {
        val params = mutableMapOf<String, String>()

        // 查找所有参数标签
        val paramRegex =
                "<param\\s+name=\"([^\"]+)\">(.*?)</param>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matches = paramRegex.findAll(content)

        for (match in matches) {
            val name = match.groupValues[1]
            val value = match.groupValues[2].trim()
            params[name] = value
        }

        return params
    }

    /** 渲染 <think> 标签内容 */
    @Composable
    private fun renderThinkContent(content: String, modifier: Modifier, textColor: Color) {
        val startTag = "<think>"
        val endTag = "</think>"
        val startIndex = content.indexOf(startTag) + startTag.length

        // 提取思考内容，即使没有结束标签
        val thinkText =
                if (content.contains(endTag)) {
                    val endIndex = content.lastIndexOf(endTag)
                    content.substring(startIndex, endIndex).trim()
                } else {
                    // 没有结束标签，直接使用startIndex后的所有内容
                    content.substring(startIndex).trim()
                }

        var expanded by remember { mutableStateOf(false) }

        Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector =
                                if (expanded) Icons.Default.KeyboardArrowDown
                                else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                        text = stringResource(id = R.string.thinking_process),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                    visible = expanded,
                    enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = tween(200))
            ) {
                if (thinkText.isNotBlank()) {
                    Text(
                            text = thinkText,
                            color = textColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, start = 24.dp)
                    )
                }
            }
        }
    }

    /** 渲染标准工具请求标签 <tool name="..."><param name="param_name">param_value</param></tool> */
    @Composable
    private fun renderToolRequest(content: String, modifier: Modifier, textColor: Color) {
        // 提取工具名称
        val nameRegex = "name=\"([^\"]+)\"".toRegex()
        val nameMatch = nameRegex.find(content)
        val toolName = nameMatch?.groupValues?.get(1) ?: "未知工具"

        // 提取参数
        val params = extractParamsFromTool(content)

        // 构建参数显示文本
        val paramText = extractContentFromXml(content, "tool").trim()
        val paramsText = params.entries.joinToString("\n") { (name, value) -> "$name: $value" }

        // 定义短内容和长内容的阈值
        val contentLengthThreshold = 100
        val isLongContent = paramText.length > contentLengthThreshold

        if (isLongContent) {
            // 使用详细工具显示组件
            DetailedToolDisplay(
                    toolName = toolName,
                    params = paramText,
                    textColor = textColor,
                    modifier = modifier
            )
        } else {
            // 使用简洁工具显示组件
            CompactToolDisplay(
                    toolName = toolName,
                    params = paramsText,
                    textColor = textColor,
                    modifier = modifier
            )
        }
    }

    /**
     * 渲染状态信息标签 <status type="..." tool="..." uuid="..." success="..." title="..."
     * subtitle="...">...</status>
     */
    @Composable
    private fun renderStatus(content: String, modifier: Modifier, textColor: Color) {
        // 提取状态属性
        val typeRegex = "type=\"([^\"]+)\"".toRegex()
        val toolRegex = "tool=\"([^\"]+)\"".toRegex()
        val uuidRegex = "uuid=\"([^\"]+)\"".toRegex()
        val successRegex = "success=\"([^\"]+)\"".toRegex()
        val titleRegex = "title=\"([^\"]+)\"".toRegex()
        val subtitleRegex = "subtitle=\"([^\"]+)\"".toRegex()

        val typeMatch = typeRegex.find(content)
        val toolMatch = toolRegex.find(content)
        val successMatch = successRegex.find(content)

        val statusType = typeMatch?.groupValues?.get(1) ?: "info"
        val toolName = toolMatch?.groupValues?.getOrNull(1) ?: ""
        val isSuccess = successMatch?.groupValues?.get(1)?.toLowerCase() != "false"

        // 提取状态内容 - 只有在非特殊状态类型时才需要
        val statusContent =
                if (statusType !in listOf("completion", "complete", "wait_for_user_need", "warning")
                ) {
                    extractContentFromXml(content, "status")
                } else {
                    "" // 特殊状态类型不需要内容
                }

        // 如果有工具名称，这是工具相关的状态
        if (toolName.isNotBlank()) {
            val clipboardManager = LocalClipboardManager.current

            when (statusType) {
                "executing" -> {
                    // 使用CustomToolDisplay来展示工具执行状态
                    CustomToolDisplay(
                            toolName = toolName,
                            isProcessing = true,
                            isError = false,
                            result = null,
                            params = "",
                            onShowResult = {},
                            onCopyResult = {},
                            isSimulated = false
                    )
                }
                "result" -> {
                    // 使用CustomToolDisplay来展示工具结果状态
                    CustomToolDisplay(
                            toolName = toolName,
                            isProcessing = false,
                            isError = !isSuccess,
                            result = statusContent,
                            params = "",
                            onShowResult = {},
                            onCopyResult = {
                                if (statusContent.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(statusContent))
                                }
                            },
                            isSimulated = false
                    )
                }
                else -> {
                    // 其他工具状态，默认显示
                    CustomToolDisplay(
                            toolName = toolName,
                            isProcessing = false,
                            isError = false,
                            result = statusContent,
                            params = "",
                            onShowResult = {},
                            onCopyResult = {},
                            isSimulated = false
                    )
                }
            }
        } else {
            // 非工具相关的状态信息
            val bgColor =
                    when (statusType) {
                        "completion", "complete" ->
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        "wait_for_user_need" ->
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        "warning" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    }

            val borderColor =
                    when (statusType) {
                        "completion", "complete" ->
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        "wait_for_user_need" ->
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                        "warning" -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }

            // 使用硬编码的文本，不管标签内有无内容
            val statusText =
                    when (statusType) {
                        "completion", "complete" -> "✓ Task completed"
                        "wait_for_user_need" -> "✓ Ready for further assistance"
                        "warning" -> "警告：发现潜在问题，请检查配置文件"
                        else -> statusContent
                    }

            Card(
                    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = BorderStroke(width = 1.dp, color = borderColor),
                    shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                                when (statusType) {
                                    "completion", "complete" -> MaterialTheme.colorScheme.primary
                                    "wait_for_user_need" -> MaterialTheme.colorScheme.tertiary
                                    "warning" -> MaterialTheme.colorScheme.error
                                    else -> textColor
                                },
                        modifier = Modifier.padding(12.dp)
                )
            }
        }
    }

    /** 渲染计划项标签 <plan_item id="..." status="...">...</plan_item> */
    @Composable
    private fun renderPlanItem(content: String, modifier: Modifier, textColor: Color) {
        // 提取计划ID和状态
        val idRegex = "id=\"([^\"]+)\"".toRegex()
        val statusRegex = "status=\"([^\"]+)\"".toRegex()

        val idMatch = idRegex.find(content)
        val statusMatch = statusRegex.find(content)

        val planId = idMatch?.groupValues?.get(1) ?: "unknown"
        val planStatus = statusMatch?.groupValues?.get(1)?.lowercase() ?: "todo"

        // 提取计划内容
        val planContent = extractContentFromXml(content, "plan_item")

        // 使用CustomSimplePlanDisplay显示计划项
        CustomSimplePlanDisplay(
                id = planId,
                status = planStatus,
                content = planContent,
                isUpdate = false,
                modifier = modifier
        )
    }

    /** 渲染计划更新标签 <plan_update id="..." status="...">...</plan_update> */
    @Composable
    private fun renderPlanUpdate(content: String, modifier: Modifier, textColor: Color) {
        // 提取计划ID和状态
        val idRegex = "id=\"([^\"]+)\"".toRegex()
        val statusRegex = "status=\"([^\"]+)\"".toRegex()

        val idMatch = idRegex.find(content)
        val statusMatch = statusRegex.find(content)

        val planId = idMatch?.groupValues?.get(1) ?: "unknown"
        val planStatus = statusMatch?.groupValues?.get(1)?.lowercase() ?: "info"

        // 提取更新内容
        val updateContent = extractContentFromXml(content, "plan_update")

        // 使用CustomSimplePlanDisplay显示计划更新
        CustomSimplePlanDisplay(
                id = planId,
                status = planStatus,
                content = updateContent,
                isUpdate = true,
                modifier = modifier
        )
    }
}

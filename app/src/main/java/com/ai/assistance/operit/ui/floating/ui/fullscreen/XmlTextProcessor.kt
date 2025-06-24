package com.ai.assistance.operit.ui.floating.ui.fullscreen

import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.flatMap
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream

/**
 * An object responsible for processing a character stream containing XML tags and converting them
 * into a human-readable plain text character stream in a fully streaming manner.
 */
object XmlTextProcessor {

    // A simple state enum for the parser
    private enum class XmlParserState {
        Scanning,
        InTag,
        InContent
    }

    /**
     * Processes a stream of strings, finds XML blocks, converts them to plain text, and returns a
     * new stream of characters.
     *
     * @param sourceStream The original stream of strings, which may contain XML.
     * @return A stream of characters with XML blocks replaced by plain text.
     */
    fun processStreamToText(sourceStream: Stream<String>): Stream<Char> {
        val xmlPlugin = StreamXmlPlugin(includeTagsInOutput = true)

        // 直接使用Stream<String>的splitBy方法
        return sourceStream.splitBy(listOf(xmlPlugin)).flatMap { group ->
            if (group.tag == xmlPlugin) {
                // It's an XML group, transform its stream to plain text chars
                transformXmlGroup(group.stream)
            } else {
                // It's a plain text group, convert each string to chars
                stringStreamToCharStream(group.stream)
            }
        }
    }

    /**
     * Converts a Stream<String> to a Stream<Char> by flattening each string into its characters.
     */
    private fun stringStreamToCharStream(stringStream: Stream<String>): Stream<Char> = stream {
        var lastCharWasNewline = true
        stringStream.collect { str ->
            str.forEach { char ->
                val isCurrentCharNewline = (char == '\n')
                if (isCurrentCharNewline) {
                    if (!lastCharWasNewline) {
                        emit('\n')
                        lastCharWasNewline = true
                    }
                } else if (char.isWhitespace() && lastCharWasNewline) {
                    // Skip other whitespace if it follows a newline
                } else {
                    emit(char)
                    lastCharWasNewline = false
                }
            }
        }
    }

    /**
     * Transforms a stream of strings representing an XML block into a stream of characters
     * representing human-readable text. This works as a state machine to process the stream on the
     * fly.
     */
    private fun transformXmlGroup(xmlStream: Stream<String>): Stream<Char> = stream {
        val collector = this
        var state: XmlParserState = XmlParserState.Scanning
        val buffer = StringBuilder()
        var lastCharWasNewline = true // Start with true to suppress leading newlines

        suspend fun emit(text: String) {
            text.forEach { char ->
                val isCurrentCharNewline = (char == '\n')
                if (isCurrentCharNewline) {
                    if (!lastCharWasNewline) {
                        collector.emit('\n')
                        lastCharWasNewline = true
                    }
                } else if (char.isWhitespace() && lastCharWasNewline) {
                    // Skip other whitespace if it follows a newline
                } else {
                    collector.emit(char)
                    lastCharWasNewline = false
                }
            }
        }

        xmlStream.collect { str ->
            for (char in str) {
                when (state) {
                    XmlParserState.Scanning -> {
                        if (char == '<') {
                            buffer.clear()
                            buffer.append(char)
                            state = XmlParserState.InTag
                        }
                        // Ignore other characters when scanning for a tag start
                    }
                    XmlParserState.InTag -> {
                        buffer.append(char)
                        if (char == '>') {
                            val tagText = buffer.toString()
                            if (tagText.startsWith("</")) { // It's an end tag
                                handleEndTag(::emit, tagText)
                            } else { // It's a start tag
                                handleStartTag(::emit, tagText)
                            }

                            buffer.clear()
                            state =
                                    if (tagText.endsWith("/>") || tagText.startsWith("</")) {
                                        XmlParserState.Scanning
                                    } else {
                                        XmlParserState.InContent
                                    }
                        }
                    }
                    XmlParserState.InContent -> {
                        if (char == '<') {
                            // A new tag is starting. The content before it is in the buffer.
                            emit(buffer.toString())
                            buffer.clear()
                            buffer.append(char)
                            state = XmlParserState.InTag
                        } else {
                            buffer.append(char)
                        }
                    }
                }
            }
        }

        // Emit any remaining content in the buffer
        if (state == XmlParserState.InContent && buffer.isNotEmpty()) {
            emit(buffer.toString())
        }
    }

    private suspend fun handleStartTag(emit: suspend (String) -> Unit, tagText: String) {
        val tagName = extractTagName(tagText) ?: return
        val text =
                when (tagName) {
                    "think" -> "正在思考...\n"
                    "tool" -> "\n正在调用工具: ${extractAttribute(tagText, "name") ?: "未知"}"
                    "param" -> "\n- ${extractAttribute(tagText, "name") ?: "参数"}: "
                    "tool_result" ->
                            "\n工具 '${extractAttribute(tagText, "name") ?: "未知"}' 执行${if (extractAttribute(tagText, "status") == "success") "成功" else "失败"}: "
                    "status" -> ""
                    "plan_item" -> "\n计划项 (${extractAttribute(tagText, "status") ?: "todo"}): "
                    "plan_update" -> "\n计划更新 (${extractAttribute(tagText, "status") ?: "info"}): "
                    "content", "error" -> "" // These tags just wrap content, no prefix needed
                    else -> tagText // For unknown tags, show them as is
                }
        emit(text)
    }

    private suspend fun handleEndTag(emit: suspend (String) -> Unit, tagText: String) {
        val tagName = extractTagName(tagText) ?: return
        val text =
                when (tagName) {
                    "tool", "think", "tool_result" -> "\n"
                    else -> "" // Most end tags don't need a suffix
                }
        emit(text)
    }

    private fun extractTagName(tagContent: String): String? {
        val regex = Regex("</?([a-zA-Z_][a-zA-Z0-9_]*)")
        return regex.find(tagContent)?.groupValues?.getOrNull(1)
    }

    private fun extractAttribute(tagText: String, attributeName: String): String? {
        val regex = "$attributeName=\"([^\"]+)\"".toRegex()
        return regex.find(tagText)?.groupValues?.getOrNull(1)
    }
}

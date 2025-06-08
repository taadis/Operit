package com.ai.assistance.operit.util.stream.plugins

import com.ai.assistance.operit.util.stream.*

/**
 * A collection of StreamPlugins for parsing various Markdown constructs.
 *
 * To use these plugins, create instances of the desired plugins and pass them as a list to the
 * `splitBy` stream operator.
 *
 * ### Important Note on Plugin Order: For Markdown features with overlapping delimiters (e.g., `*`
 * for italic and `**` for bold), the order of plugins in the list passed to `splitBy` is crucial.
 * The plugin for the longer delimiter must come before the plugin for the shorter one.
 *
 * **Correct Order Example:**
 * ```kotlin
 * val markdownPlugins = listOf(
 *     StreamMarkdownBoldPlugin(),      // For **...**
 *     StreamMarkdownItalicPlugin()     // For *...*
 * )
 * myCharStream.splitBy(markdownPlugins)
 * ```
 * This ensures that `**text**` is correctly identified as bold, not as two consecutive italics.
 */
private const val GROUP_DELIMITER = 1
private const val GROUP_HEADER_HASHES = 1

/**
 * A stream plugin for identifying Markdown fenced code blocks. This plugin identifies a block
 * starting with three or more backticks and ending with a matching fence. It does not currently
 * parse language identifiers.
 * @param includeFences If true, the fences are included in the output.
 */
class StreamMarkdownFencedCodeBlockPlugin(private val includeFences: Boolean = true) :
        StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    // Pattern to find the start of a fenced code block (3 or more backticks).
    private var startMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                group(GROUP_DELIMITER) {
                                    repeat(3) { char('`') }
                                    greedyStar { char('`') }
                                }
                                greedyStar { notChar('\n') }
                            }
                    )
    private var endMatcher: StreamKmpGraph? = null

    override fun processChar(c: Char): Boolean {
        if (state == PluginState.PROCESSING) {
            val matcher = endMatcher!!
            when (matcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeFences
                }
                is StreamKmpMatchResult.InProgress -> return includeFences
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE or TRYING
            when (val result = startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    val fence = result.groups[GROUP_DELIMITER]
                    if (fence != null) {
                        state = PluginState.PROCESSING
                        // Dynamically build the end matcher for the exact opening fence
                        endMatcher = StreamKmpGraphBuilder().build(kmpPattern { literal(fence) })
                        startMatcher.reset()
                    } else {
                        reset()
                    }
                    return includeFences
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                }
            }
            return includeFences
        }
        return true // Should be unreachable
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        startMatcher.reset()
        endMatcher = null
    }
}

/**
 * A stream plugin for identifying Markdown inline code snippets (`code` or ``code``). It matches a
 * starting sequence of backticks and looks for a closing sequence of the same length. The snippet
 * cannot contain newlines.
 *
 * @param includeTicks If true, the ` backticks are included in the output.
 */
class StreamMarkdownInlineCodePlugin(private val includeTicks: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    // Pattern to capture one or more backticks.
    private var startMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                group(GROUP_DELIMITER) { char('`') }
                                noneOf('`', '\n')
                            }
                    )
    private var endMatcher: StreamKmpGraph? = null

    override fun processChar(c: Char): Boolean {
        // As per original logic, inline code cannot span multiple lines.
        // If we see a newline while processing, the match is considered failed.
        if (state == PluginState.PROCESSING && c == '\n') {
            // This will cause `splitBy` to reprocess the buffered content as default text.
            reset()
            return true // let the newline be processed by the default stream
        }

        if (state == PluginState.PROCESSING) {
            val matcher = endMatcher!!
            when (matcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeTicks
                }
                is StreamKmpMatchResult.InProgress -> return includeTicks
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE or TRYING
            when (val result = startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    val ticks = result.groups[GROUP_DELIMITER]
                    if (ticks != null) {
                        state = PluginState.PROCESSING
                        endMatcher = StreamKmpGraphBuilder().build(kmpPattern { literal(ticks) })
                        startMatcher.reset()
                    } else {
                        reset()
                    }
                    return includeTicks
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                }
            }
            return includeTicks
        }
        return true // Should be unreachable
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        startMatcher.reset()
        endMatcher = null
    }
}

/**
 * A stream plugin for identifying bold text using double asterisks (`**text**`).
 *
 * @param includeAsterisks If true, the `**` delimiters are included in the output.
 */
class StreamMarkdownBoldPlugin(private val includeAsterisks: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        internal set

    private var startMatcher: StreamKmpGraph
    private var endMatcher: StreamKmpGraph

    init {
        val builder = StreamKmpGraphBuilder()
        startMatcher =
                builder.build(
                        kmpPattern {
                            literal("**")
                            noneOf('*', '\n')
                        }
                )
        endMatcher = builder.build(kmpPattern { literal("**") })
        reset()
    }

    override fun processChar(c: Char): Boolean {
        if (state == PluginState.PROCESSING) {
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeAsterisks
                }
                is StreamKmpMatchResult.InProgress -> return includeAsterisks
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE or TRYING
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    endMatcher.reset()
                    startMatcher.reset()
                    return true
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeAsterisks
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    return true
                }
            }
        }
        return true // Should be unreachable
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        startMatcher.reset()
        endMatcher.reset()
        state = PluginState.IDLE
    }
}

/**
 * A stream plugin for identifying italic text using single asterisks (`*text*`)。
 *
 * @param includeAsterisks If true, the `*` delimiters are included in the output.
 */
class StreamMarkdownItalicPlugin(private val includeAsterisks: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        internal set

    private var startMatcher: StreamKmpGraph
    private var endMatcher: StreamKmpGraph
    private var lastChar: Char? = null

    init {
        val builder = StreamKmpGraphBuilder()
        startMatcher =
                builder.build(
                        kmpPattern {
                            literal("*")
                            noneOf('*', '\n')
                        }
                )
        endMatcher = builder.build(kmpPattern { literal("*") })
        reset()
    }

    override fun processChar(c: Char): Boolean {
        if (lastChar == '*' && c == '*') {
            lastChar = null
            reset()

            return true
        }
        lastChar = c

        if (state == PluginState.PROCESSING) {
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeAsterisks
                }
                is StreamKmpMatchResult.InProgress -> return includeAsterisks
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE or TRYING
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    endMatcher.reset()
                    startMatcher.reset()
                    return true
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeAsterisks
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    return true
                }
            }
        }

        return true // Should be unreachable
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        startMatcher.reset()
        endMatcher.reset()
        state = PluginState.IDLE
    }
}

/**
 * Identifies ATX-style Markdown headers (e.g., "# My Header"). A header is defined as 1-6 '#'
 * characters at the beginning of a line, followed by a space, and extends to the end of the line.
 *
 * @param includeMarker If true, includes the '#' characters and the following space in the group.
 */
class StreamMarkdownHeaderPlugin(private val includeMarker: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    private var atStartOfLine = true

    private val headerMatcher =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                group(GROUP_HEADER_HASHES) {
                                    char('#')
                                    greedyStar { char('#') }
                                }
                                char(' ')
                            }
                    )

    override fun processChar(c: Char): Boolean {
        if (state == PluginState.PROCESSING) {
            if (c == '\n') {
                reset() // This will call our new reset(), which is just resetInternal()
                atStartOfLine = true
            }
            return true // Return true to emit the character in the header content
        }

        if (c == '\n') {
            reset() // reset if we are in TRYING state
            atStartOfLine = true // We are now at the start of a new line.
            return true
        }

        if (atStartOfLine) {
            // Once we are no longer at the start of the line, we stop trying to match a header
            // for the rest of the line, unless we are already in a TRYING state.
            atStartOfLine = false
            return handleMatch(c)
        } else if (state == PluginState.TRYING) {
            // We are already in the middle of a potential match, continue feeding.
            return handleMatch(c)
        }

        // Not at start of line and not trying, so just pass through.
        return true
    }

    private fun handleMatch(c: Char): Boolean {
        return when (val result = headerMatcher.processChar(c)) {
            is StreamKmpMatchResult.Match -> {
                val hashes = result.groups[GROUP_HEADER_HASHES]
                if (hashes != null && hashes.length in 1..6) {
                    state = PluginState.PROCESSING
                    includeMarker
                } else {
                    // e.g. "####### " is not a valid header. Fail the match.
                    resetInternal()
                    true // The buffered chars in splitBy will be re-processed as default.
                }
            }
            is StreamKmpMatchResult.InProgress -> {
                state = PluginState.TRYING
                includeMarker
            }
            is StreamKmpMatchResult.NoMatch -> {
                // e.g. "#foo" or "##bar"
                resetInternal()
                true // The buffered chars will be re-processed as default.
            }
        }
    }

    override fun initPlugin(): Boolean {
        reset()
        atStartOfLine = true
        return true
    }

    override fun destroy() {}

    // The problematic call from splitBy will now only perform a soft reset of the
    // matching state, without incorrectly assuming it's at the start of a line.
    override fun reset() {
        resetInternal()
    }

    // Internal reset that doesn't touch atStartOfLine
    private fun resetInternal() {
        state = PluginState.IDLE
        headerMatcher.reset()
    }
}

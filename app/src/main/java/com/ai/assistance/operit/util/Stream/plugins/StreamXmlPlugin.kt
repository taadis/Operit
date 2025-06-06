package com.ai.assistance.operit.util.Stream.plugins

import com.ai.assistance.operit.util.Stream.*

private const val GROUP_TAG_NAME = 1
private const val GROUP_CONTENT = 2

/**
 * A stream processing plugin to identify and process XML-formatted data streams. This
 * implementation uses the group capturing mechanism of the StreamKmpGraph.
 */
class StreamXmlPlugin : StreamPlugin {

    override val isProcessing: Boolean
        get() = endTagMatcher != null

    override val isTryingToStart: Boolean
        get() = !isProcessing && startTagMatcher.getCurrentNode() != startTagMatcher.getStartNode()

    private var startTagMatcher: StreamKmpGraph
    private var endTagMatcher: StreamKmpGraph? = null

    init {
        startTagMatcher =
                StreamKmpGraphBuilder()
                        .build(
                                kmpPattern {
                                    char('<')
                                    // Group 1: Capture the tag name
                                    group(GROUP_TAG_NAME) {
                                        // Tag names cannot start with these chars
                                        noneOf('>', '/', '!', ' ')
                                        // Then, match anything until a space or '>'
                                        greedyStar { noneOf(' ', '>') }
                                    }
                                    // Match attributes until the tag closes
                                    greedyStar { notChar('>') }
                                    char('>')
                                }
                        )

        reset()
    }

    /** Process a single character for XML stream parsing. */
    override fun processChar(c: Char): Boolean {
        return if (isProcessing) {
            // If we are already processing, we are looking for the end tag.
            val matcher = endTagMatcher!!
            val result = matcher.processChar(c)
            if (result is StreamKmpMatchResult.Match && result.isFullMatch) {
                reset() // End tag found, reset to initial state.
            } else if (result is StreamKmpMatchResult.NoMatch) {
                // The character did not match the end tag pattern, which is an error.
                reset()
            }
            true // Always consume all chars while processing.
        } else {
            // If not processing, we are looking for a start tag.
            when (val result = startTagMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    // This case should now only be triggered on a full "<tag...>" match.
                    val tagName = result.groups[GROUP_TAG_NAME]
                    if (tagName != null) {
                        // We have a full start tag. Start processing.
                        endTagMatcher =
                                StreamKmpGraphBuilder()
                                        .build(
                                                kmpPattern {
                                                    literal("</")
                                                    literal(tagName)
                                                    char('>')
                                                }
                                        )
                        startTagMatcher.reset()
                    } else {
                        // Should not happen with the current pattern, but as a safeguard:
                        reset()
                    }
                    true
                }
                is StreamKmpMatchResult.InProgress -> {
                    // We are in the middle of matching a start tag (e.g., after '<' or '<t').
                    true // Character consumed.
                }
                is StreamKmpMatchResult.NoMatch -> {
                    // The character did not fit the pattern. The matcher has reset itself.
                    false // Character not consumed.
                }
            }
        }
    }

    /** Initializes the plugin to its default state. */
    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    /** Destroys the plugin. No-op as listener is removed. */
    override fun destroy() {}

    /** Resets the plugin state. */
    override fun reset() {
        endTagMatcher = null
        startTagMatcher.reset()
    }
}

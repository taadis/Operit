package com.ai.assistance.operit.data.model

/**
 * Defines the category of a web automation task to guide the AI's strategy.
 */
enum class WebAutomationTaskType {
    /** The goal is to find and extract specific information from web pages. */
    INFORMATION_GATHERING,

    /** The goal is to browse websites, follow links, and summarize findings on a topic. */
    EXPLORATION,

    /** The goal is to perform a stateful action like booking, purchasing, or submitting a form. */
    TRANSACTIONAL,

    /** A default type for tasks that don't fit other categories or are simple, direct actions. */
    GENERAL_INTERACTION
} 
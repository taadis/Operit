package com.ai.assistance.operit.data.model

/**
 * Data model representing an item in the AI's execution plan.
 */
data class PlanItem(
    val id: String,
    val description: String,
    val status: PlanItemStatus,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

/**
 * Status options for a plan item.
 */
enum class PlanItemStatus {
    TODO,       // Task is pending
    IN_PROGRESS, // Task is currently being worked on
    COMPLETED,  // Task completed successfully
    FAILED,     // Task failed to complete
    CANCELLED   // Task was cancelled
}
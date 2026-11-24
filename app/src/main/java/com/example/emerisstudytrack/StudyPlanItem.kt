package com.example.emerisstudytrack

/**
 * Represents the amount of today's selected study session assigned to one task.
 * Allocation is stored in minutes so the plan can show 30-minute and 90-minute slots.
 */
data class StudyPlanItem(
    val task: Task,
    val allocatedMinutes: Int
)

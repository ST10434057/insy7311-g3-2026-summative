package com.example.emerisstudytrack

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val moduleName: String,
    val taskTitle: String,
    val category: String,
    val dueDate: String,
    val difficulty: String,
    val estimatedHours: Int,
    val studiedMinutes: Int = 0,
    val isCompleted: Boolean = false,
    val completedDate: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalMinutes: Int
        get() = estimatedHours.coerceAtLeast(0) * 60

    val safeStudiedMinutes: Int
        get() = if (isCompleted) totalMinutes else studiedMinutes.coerceIn(0, totalMinutes)

    val remainingMinutes: Int
        get() = (totalMinutes - safeStudiedMinutes).coerceAtLeast(0)

    val studyProgressPercent: Int
        get() = if (totalMinutes == 0) 0 else ((safeStudiedMinutes * 100.0) / totalMinutes).toInt()
}
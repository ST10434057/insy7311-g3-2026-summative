package com.example.emerisstudytrack

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil

object TaskUtils {

    data class PlanAllocation(
        val task: Task,
        val allocatedMinutes: Int
    )

    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val BLOCK_MINUTES = 15

    private val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
        isLenient = false
    }

    fun categoryWeight(category: String): Int = when (category) {
        "ICE Task" -> 1
        "Assessment" -> 2
        "Test" -> 3
        "Summative Assessment", "Exam" -> 4
        else -> 0
    }

    fun difficultyWeight(difficulty: String): Int = when (difficulty) {
        "Easy" -> 1
        "Medium" -> 2
        "Hard" -> 3
        else -> 0
    }

    fun urgencyWeight(daysLeft: Long): Int = when {
        daysLeft <= 1 -> 4
        daysLeft <= 3 -> 3
        daysLeft <= 7 -> 2
        else -> 1
    }

    fun effortWeight(hours: Int): Int = when {
        hours >= 7 -> 3
        hours >= 3 -> 2
        hours >= 1 -> 1
        else -> 0
    }

    /**
     * Deadline-sensitive priority score.
     *
     * The due date carries most of the score so that a task due in a few days
     * always ranks above a similar task due several months from now.
     */
    fun priorityScore(task: Task): Int {
        if (task.isCompleted || task.remainingMinutes == 0) return 0

        val daysLeft = daysBetweenTodayAnd(task.dueDate)
        val remainingHours = ceil(task.remainingMinutes / 60.0).toInt()

        val deadlinePoints = when {
            daysLeft < 0 -> 65
            daysLeft <= 1 -> 60
            daysLeft <= 3 -> 50
            daysLeft <= 7 -> 40
            daysLeft <= 14 -> 30
            daysLeft <= 30 -> 22
            daysLeft <= 60 -> 14
            daysLeft <= 120 -> 8
            else -> 4
        }

        val categoryPoints = categoryWeight(task.category) * 5
        val difficultyPoints = difficultyWeight(task.difficulty) * 4
        val effortPoints = effortWeight(remainingHours) * 3

        return deadlinePoints + categoryPoints + difficultyPoints + effortPoints
    }

    fun priorityLabel(task: Task): String {
        if (task.isCompleted || task.remainingMinutes == 0) return "Completed"
        return when {
            priorityScore(task) >= 65 -> "High priority"
            priorityScore(task) >= 35 -> "Medium priority"
            else -> "Low priority"
        }
    }

    /**
     * Squaring the score makes urgent work receive a clearly larger share.
     * Example: two identical hard summatives due in 3 days and 102 days receive
     * approximately a 3:1 split instead of 50:50.
     */
    fun allocationWeight(task: Task): Int {
        val score = priorityScore(task).coerceAtLeast(1)
        return score * score
    }

    fun priorityReason(task: Task): String {
        if (task.isCompleted || task.remainingMinutes == 0) {
            return "All estimated study time has been completed."
        }
        val score = priorityScore(task)
        return "Score $score/106: ${task.category}; ${task.difficulty.lowercase(Locale.US)} difficulty; " +
                "${daysLeftText(task.dueDate).lowercase(Locale.US)}; ${formatMinutes(task.remainingMinutes)} remaining."
    }

    fun workSummary(task: Task): String {
        return "${formatMinutes(task.safeStudiedMinutes)} studied • " +
                "${formatMinutes(task.remainingMinutes)} remaining • " +
                "${formatMinutes(task.totalMinutes)} total"
    }

    fun formatMinutes(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours > 0 && mins > 0 -> "$hours h $mins min"
            hours > 0 -> "$hours h"
            else -> "$mins min"
        }
    }

    /**
     * Splits the available time into 15-minute blocks.
     * Time is distributed using each task's deadline-sensitive priority score.
     * Near deadlines have the strongest influence, and a task can never receive
     * more time than it still needs.
     */
    fun allocateStudyTime(tasks: List<Task>, availableMinutes: Int): List<PlanAllocation> {
        var blocksLeft = (availableMinutes.coerceAtLeast(0) / BLOCK_MINUTES)
        if (blocksLeft == 0) return emptyList()

        val candidates = sortedByPriority(tasks)
            .filter { !it.isCompleted && it.remainingMinutes > 0 }

        if (candidates.isEmpty()) return emptyList()

        val allocations = IntArray(candidates.size)
        val capacities = IntArray(candidates.size) { index ->
            ceil(candidates[index].remainingMinutes / BLOCK_MINUTES.toDouble()).toInt()
        }
        val weights = IntArray(candidates.size) { index -> allocationWeight(candidates[index]) }

        // Give every pending task at least 15 minutes when the session is long enough.
        if (blocksLeft >= candidates.size) {
            candidates.indices.forEach { index ->
                if (capacities[index] > 0) {
                    allocations[index] = 1
                    blocksLeft--
                }
            }
        }

        // D'Hondt-style distribution using deadline-sensitive weights.
        // This keeps all allocations in practical 15-minute blocks.
        while (blocksLeft > 0) {
            val eligible = candidates.indices.filter { allocations[it] < capacities[it] }
            if (eligible.isEmpty()) break

            val nextIndex = eligible.maxWithOrNull(
                compareBy<Int> { weights[it].toDouble() / (allocations[it] + 1) }
                    .thenBy { priorityScore(candidates[it]) }
                    .thenBy { -dateMillisOrMax(candidates[it].dueDate) }
            ) ?: break

            allocations[nextIndex]++
            blocksLeft--
        }

        return candidates.indices
            .filter { allocations[it] > 0 }
            .map { index ->
                val allocated = (allocations[index] * BLOCK_MINUTES)
                    .coerceAtMost(candidates[index].remainingMinutes)
                PlanAllocation(candidates[index], allocated)
            }
    }

    // Kept for compatibility with the existing unit test and any older code.
    fun buildTodayPlan(tasks: List<Task>, availableHours: Int): List<Task> {
        if (availableHours <= 0) return emptyList()
        val pending = sortedByPriority(tasks).filterNot { it.isCompleted }
        val selected = mutableListOf<Task>()
        var remainingHours = availableHours
        pending.forEach { task ->
            val wholeHoursNeeded = ceil(task.remainingMinutes / 60.0).toInt()
            if (wholeHoursNeeded in 1..remainingHours) {
                selected += task
                remainingHours -= wholeHoursNeeded
            }
        }
        if (selected.isEmpty() && pending.isNotEmpty()) selected += pending.first()
        return selected.take(5)
    }

    fun daysLeftText(dueDate: String): String {
        val days = daysBetweenTodayAnd(dueDate)
        return when {
            days < 0 -> "Overdue by ${abs(days)} day(s)"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            else -> "Due in $days day(s)"
        }
    }

    fun daysBetweenTodayAnd(dueDate: String): Long {
        val today = startOfDay(Date())
        val due = parseDate(dueDate) ?: return Long.MAX_VALUE / 2
        return TimeUnit.MILLISECONDS.toDays(due.time - today.time)
    }

    fun isValidDate(dueDate: String): Boolean = parseDate(dueDate) != null

    fun isPastDate(dueDate: String): Boolean = !isValidDate(dueDate) || daysBetweenTodayAnd(dueDate) < 0

    fun isValidModuleCode(value: String): Boolean {
        return Regex("^[A-Za-z]{3,6}[0-9]{3,4}$").matches(value.trim())
    }

    fun sortedByPriority(tasks: List<Task>): List<Task> {
        return tasks.sortedWith(
            compareBy<Task> { it.isCompleted || it.remainingMinutes == 0 }
                .thenByDescending { priorityScore(it) }
                .thenBy { dateMillisOrMax(it.dueDate) }
                .thenBy { it.taskTitle.lowercase(Locale.US) }
        )
    }

    fun sortedByDueDate(tasks: List<Task>): List<Task> {
        return tasks.sortedWith(
            compareBy<Task> { it.isCompleted || it.remainingMinutes == 0 }
                .thenBy { dateMillisOrMax(it.dueDate) }
                .thenByDescending { priorityScore(it) }
        )
    }

    fun sortedByModule(tasks: List<Task>): List<Task> {
        return tasks.sortedWith(
            compareBy<Task> { it.isCompleted || it.remainingMinutes == 0 }
                .thenBy { it.moduleName.lowercase(Locale.US) }
                .thenBy { dateMillisOrMax(it.dueDate) }
        )
    }

    fun completionPercentage(tasks: List<Task>): Int {
        if (tasks.isEmpty()) return 0
        return ((tasks.count { it.isCompleted || it.remainingMinutes == 0 } * 100.0) / tasks.size).toInt()
    }

    fun currentStreak(tasks: List<Task>): Int {
        val completedDates = tasks.mapNotNull { it.completedDate }.toSet()
        if (completedDates.isEmpty()) return 0
        val calendar = Calendar.getInstance()
        var streak = 0
        while (completedDates.contains(formatDate(calendar.time))) {
            streak++
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun completedOnDate(tasks: List<Task>, date: String): Int =
        tasks.count { it.completedDate == date }

    fun lastSevenDates(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        val labelFormat = SimpleDateFormat("EEE", Locale.getDefault())
        repeat(7) {
            result += formatDate(calendar.time) to labelFormat.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    fun todayString(): String = formatDate(Date())

    fun formatFriendlyDate(date: Date = Date()): String =
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(date)

    private fun parseDate(text: String): Date? = synchronized(dateFormat) {
        try {
            dateFormat.parse(text)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatDate(date: Date): String = synchronized(dateFormat) {
        dateFormat.format(date)
    }

    private fun startOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun dateMillisOrMax(text: String): Long = parseDate(text)?.time ?: Long.MAX_VALUE
}
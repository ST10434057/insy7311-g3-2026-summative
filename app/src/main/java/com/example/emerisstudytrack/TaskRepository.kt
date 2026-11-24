package com.example.emerisstudytrack

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TaskRepository {

    data class StudyLogResult(
        val before: Task,
        val after: Task,
        val loggedMinutes: Int
    )

    private const val PREFS_NAME = "emeris_study_track"
    private const val KEY_TASKS = "tasks"
    private const val KEY_SELECTED_HOURS = "selected_study_hours"
    private const val KEY_LOG_DATE = "study_log_date"
    private const val KEY_DAILY_LOGS = "daily_study_logs"

    private var initialized = false
    private val mutableTasks = mutableListOf<Task>()

    val tasks: List<Task>
        @Synchronized get() = mutableTasks.toList()

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return
        runCatching { load(context.applicationContext) }
            .onFailure { mutableTasks.clear() }
        initialized = true
    }

    @Synchronized
    fun addTask(context: Context, task: Task) {
        ensureInitialized(context)
        mutableTasks += normalize(task)
        save(context.applicationContext)
    }

    @Synchronized
    fun updateTask(context: Context, task: Task): Boolean {
        ensureInitialized(context)
        val index = mutableTasks.indexOfFirst { it.id == task.id }
        if (index == -1) return false
        mutableTasks[index] = normalize(task)
        save(context.applicationContext)
        return true
    }

    @Synchronized
    fun getTask(id: String): Task? = mutableTasks.firstOrNull { it.id == id }

    @Synchronized
    fun deleteTask(context: Context, taskId: String): Task? {
        ensureInitialized(context)
        val task = mutableTasks.firstOrNull { it.id == taskId } ?: return null
        mutableTasks.remove(task)
        save(context.applicationContext)
        return task
    }

    @Synchronized
    fun restoreTask(context: Context, task: Task) {
        ensureInitialized(context)
        if (mutableTasks.none { it.id == task.id }) {
            mutableTasks += normalize(task)
            save(context.applicationContext)
        }
    }

    /**
     * This is the manual complete/pending button on the View Tasks page.
     * Study time logged from Today's Plan uses logStudyTime() instead.
     */
    @Synchronized
    fun setCompleted(context: Context, taskId: String, completed: Boolean): Task? {
        ensureInitialized(context)
        val index = mutableTasks.indexOfFirst { it.id == taskId }
        if (index == -1) return null

        val old = mutableTasks[index]
        val updated = if (completed) {
            old.copy(
                studiedMinutes = old.totalMinutes,
                isCompleted = true,
                completedDate = TaskUtils.todayString()
            )
        } else {
            old.copy(
                studiedMinutes = 0,
                isCompleted = false,
                completedDate = null
            )
        }

        mutableTasks[index] = updated
        save(context.applicationContext)
        return old
    }

    /**
     * Logs only the allocated study time. A task remains pending until its
     * remaining time reaches zero.
     */
    @Synchronized
    fun logStudyTime(context: Context, taskId: String, requestedMinutes: Int): StudyLogResult? {
        ensureInitialized(context)
        if (requestedMinutes <= 0) return null

        val index = mutableTasks.indexOfFirst { it.id == taskId }
        if (index == -1) return null

        val before = mutableTasks[index]
        if (before.isCompleted || before.remainingMinutes <= 0) return null

        val actualMinutes = requestedMinutes.coerceAtMost(before.remainingMinutes)
        val newStudied = (before.safeStudiedMinutes + actualMinutes).coerceAtMost(before.totalMinutes)
        val nowComplete = newStudied >= before.totalMinutes

        val after = before.copy(
            studiedMinutes = newStudied,
            isCompleted = nowComplete,
            completedDate = if (nowComplete) TaskUtils.todayString() else null
        )

        mutableTasks[index] = after
        addToTodayLog(context.applicationContext, taskId, actualMinutes)
        save(context.applicationContext)
        return StudyLogResult(before, after, actualMinutes)
    }

    @Synchronized
    fun undoStudyLog(context: Context, result: StudyLogResult) {
        ensureInitialized(context)
        val index = mutableTasks.indexOfFirst { it.id == result.before.id }
        if (index >= 0) {
            mutableTasks[index] = normalize(result.before)
        }
        addToTodayLog(context.applicationContext, result.before.id, -result.loggedMinutes)
        save(context.applicationContext)
    }

    fun totalCount(): Int = tasks.size
    fun pendingCount(): Int = tasks.count { !it.isCompleted && it.remainingMinutes > 0 }
    fun completedCount(): Int = tasks.count { it.isCompleted || it.remainingMinutes == 0 }
    fun highPriorityPendingCount(): Int = tasks.count {
        !it.isCompleted && it.remainingMinutes > 0 && TaskUtils.priorityLabel(it) == "High priority"
    }

    @Synchronized
    fun clearAll(context: Context) {
        mutableTasks.clear()
        save(context.applicationContext)
    }

    @Synchronized
    fun loadSampleData(context: Context) {
        ensureInitialized(context)
        if (mutableTasks.isNotEmpty()) return

        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fun plusDays(days: Int): String {
            val calendar = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, days)
            }
            return date.format(calendar.time)
        }

        mutableTasks += listOf(
            Task(
                moduleName = "INSY7311",
                taskTitle = "Summative prototype report",
                category = "Summative Assessment",
                dueDate = plusDays(1),
                difficulty = "Hard",
                estimatedHours = 8
            ),
            Task(
                moduleName = "CLDV6212",
                taskTitle = "Cloud test revision",
                category = "Test",
                dueDate = plusDays(2),
                difficulty = "Medium",
                estimatedHours = 4
            ),
            Task(
                moduleName = "PROG7311",
                taskTitle = "Complete ICE task",
                category = "ICE Task",
                dueDate = plusDays(5),
                difficulty = "Easy",
                estimatedHours = 2
            )
        )
        save(context.applicationContext)
    }

    fun getSelectedStudyHours(context: Context): Int {
        val appContext = context.applicationContext
        ensureTodaySession(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = runCatching { prefs.all[KEY_SELECTED_HOURS] }.getOrNull()
        val hours = when (stored) {
            is Number -> stored.toInt()
            is String -> stored.toIntOrNull()
            else -> null
        }
        return hours?.takeIf { it in setOf(2, 4, 6, 8) } ?: 4
    }

    fun setSelectedStudyHours(context: Context, hours: Int) {
        val safeHours = if (hours in setOf(2, 4, 6, 8)) hours else 4
        val appContext = context.applicationContext
        ensureTodaySession(appContext)
        runCatching {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putInt(KEY_SELECTED_HOURS, safeHours)
            }
        }
    }

    fun loggedMinutesToday(context: Context): Int {
        ensureTodaySession(context.applicationContext)
        val map = readTodayLog(context.applicationContext)
        var total = 0
        val keys = map.keys()
        while (keys.hasNext()) {
            total += map.optInt(keys.next(), 0).coerceAtLeast(0)
        }
        return total
    }

    fun remainingSessionMinutes(context: Context): Int {
        return (getSelectedStudyHours(context) * 60 - loggedMinutesToday(context)).coerceAtLeast(0)
    }

    private fun normalize(task: Task): Task {
        val safeHours = task.estimatedHours.coerceIn(1, 24)
        val total = safeHours * 60
        val studied = task.studiedMinutes.coerceIn(0, total)
        val completed = task.isCompleted || studied >= total
        return task.copy(
            estimatedHours = safeHours,
            studiedMinutes = if (completed) total else studied,
            isCompleted = completed,
            completedDate = if (completed) task.completedDate ?: TaskUtils.todayString() else null
        )
    }

    private fun load(context: Context) {
        mutableTasks.clear()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = runCatching { prefs.all[KEY_TASKS] }.getOrNull()
        val text = stored as? String ?: return

        runCatching {
            val array = JSONArray(text)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue

                val module = item.optString("moduleName", "").trim()
                val title = item.optString("taskTitle", "").trim()
                val category = item.optString("category", "Assessment").trim()
                val dueDate = item.optString("dueDate", TaskUtils.todayString()).trim()
                val difficulty = item.optString("difficulty", "Medium").trim()
                val estimatedHours = item.optInt("estimatedHours", 1).coerceIn(1, 24)
                val completed = item.optBoolean("isCompleted", false)

                if (module.isBlank() || title.isBlank()) continue

                val migratedStudiedMinutes = if (item.has("studiedMinutes")) {
                    item.optInt("studiedMinutes", 0)
                } else if (completed) {
                    estimatedHours * 60
                } else {
                    0
                }

                mutableTasks += normalize(
                    Task(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        moduleName = module,
                        taskTitle = title,
                        category = category,
                        dueDate = dueDate,
                        difficulty = difficulty,
                        estimatedHours = estimatedHours,
                        studiedMinutes = migratedStudiedMinutes,
                        isCompleted = completed,
                        completedDate = item.optString("completedDate")
                            .takeIf { it.isNotBlank() && it != "null" },
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }.onFailure {
            // Corrupt saved JSON must never crash a screen. Start with an empty list.
            mutableTasks.clear()
        }
    }

    private fun save(context: Context) {
        val array = JSONArray()
        mutableTasks.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id)
                put("moduleName", task.moduleName)
                put("taskTitle", task.taskTitle)
                put("category", task.category)
                put("dueDate", task.dueDate)
                put("difficulty", task.difficulty)
                put("estimatedHours", task.estimatedHours)
                put("studiedMinutes", task.safeStudiedMinutes)
                put("isCompleted", task.isCompleted)
                put("completedDate", task.completedDate ?: JSONObject.NULL)
                put("createdAt", task.createdAt)
            })
        }

        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(KEY_TASKS, array.toString())
            }
        }
    }

    private fun ensureTodaySession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = TaskUtils.todayString()
        val storedDate = runCatching { prefs.all[KEY_LOG_DATE] as? String }.getOrNull()
        if (storedDate != today) {
            runCatching {
                prefs.edit {
                    putString(KEY_LOG_DATE, today)
                    remove(KEY_DAILY_LOGS)
                }
            }
        }
    }

    private fun readTodayLog(context: Context): JSONObject {
        ensureTodaySession(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val text = runCatching { prefs.all[KEY_DAILY_LOGS] as? String }.getOrNull()
        return runCatching { if (text.isNullOrBlank()) JSONObject() else JSONObject(text) }
            .getOrDefault(JSONObject())
    }

    private fun addToTodayLog(context: Context, taskId: String, deltaMinutes: Int) {
        ensureTodaySession(context)
        val log = readTodayLog(context)
        val updated = (log.optInt(taskId, 0) + deltaMinutes).coerceAtLeast(0)
        if (updated == 0) log.remove(taskId) else log.put(taskId, updated)
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(KEY_DAILY_LOGS, log.toString())
            }
        }
    }
}

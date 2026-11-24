package com.example.emerisstudytrack

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar

@SuppressLint("SetTextI18n")
class AllTasksActivity : AppCompatActivity() {

    private enum class Filter {
        ALL,
        PENDING,
        COMPLETED
    }

    private lateinit var root: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var taskContainer: LinearLayout
    private lateinit var taskScrollView: ScrollView
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView
    private lateinit var taskCount: TextView
    private lateinit var btnAll: MaterialButton
    private lateinit var btnPending: MaterialButton
    private lateinit var btnCompleted: MaterialButton

    private var currentFilter = Filter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            TaskRepository.ensureInitialized(this)
            setContentView(R.layout.activity_all_tasks)

            root = findViewById(R.id.rootTasks)
            bottomNav = findViewById(R.id.bottomNav)
            taskContainer = findViewById(R.id.taskContainer)
            taskScrollView = findViewById(R.id.taskScrollView)
            emptyState = findViewById(R.id.layoutEmptyTasks)
            emptyTitle = findViewById(R.id.tvEmptyTitle)
            emptyMessage = findViewById(R.id.tvEmptyMessage)
            taskCount = findViewById(R.id.tvTaskCount)
            btnAll = findViewById(R.id.btnShowAll)
            btnPending = findViewById(R.id.btnShowPending)
            btnCompleted = findViewById(R.id.btnShowCompleted)

            findViewById<View>(R.id.btnEmptyAddTask).setOnClickListener {
                openAddTask()
            }

            findViewById<ExtendedFloatingActionButton>(R.id.fabAddTask)
                .setOnClickListener {
                    openAddTask()
                }

            btnAll.setOnClickListener {
                selectFilter(Filter.ALL)
            }

            btnPending.setOnClickListener {
                selectFilter(Filter.PENDING)
            }

            btnCompleted.setOnClickListener {
                selectFilter(Filter.COMPLETED)
            }

            NavigationHelper.setup(
                this,
                bottomNav,
                R.id.nav_tasks
            )

            updateFilterButtons()
            showResultMessage(intent)

        } catch (error: Throwable) {
            ScreenRecovery.show(
                this,
                "Tasks",
                error
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showResultMessage(intent)
    }

    override fun onResume() {
        super.onResume()

        if (!::taskContainer.isInitialized) {
            return
        }

        NavigationHelper.syncSelection(
            bottomNav,
            R.id.nav_tasks
        )

        renderTasks()
    }

    private fun selectFilter(filter: Filter) {
        currentFilter = filter
        updateFilterButtons()
        renderTasks()
    }

    private fun updateFilterButtons() {
        val selectedBackground =
            ContextCompat.getColor(
                this,
                R.color.primary_container
            )

        val selectedText =
            ContextCompat.getColor(
                this,
                R.color.on_primary_container
            )

        val normalBackground =
            ContextCompat.getColor(
                this,
                android.R.color.transparent
            )

        val normalText =
            ContextCompat.getColor(
                this,
                R.color.primary
            )

        listOf(
            btnAll to (currentFilter == Filter.ALL),
            btnPending to (currentFilter == Filter.PENDING),
            btnCompleted to (currentFilter == Filter.COMPLETED)
        ).forEach { (button, selected) ->

            button.setBackgroundColor(
                if (selected) {
                    selectedBackground
                } else {
                    normalBackground
                }
            )

            button.setTextColor(
                if (selected) {
                    selectedText
                } else {
                    normalText
                }
            )

            button.strokeWidth = dp(1)

            button.strokeColor =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this,
                        R.color.outline_variant
                    )
                )
        }
    }

    private fun renderTasks() {
        try {
            val allTasks = TaskRepository.tasks

            val visibleTasks = when (currentFilter) {
                Filter.ALL -> allTasks

                Filter.PENDING ->
                    allTasks.filter {
                        !isCompleted(it)
                    }

                Filter.COMPLETED ->
                    allTasks.filter {
                        isCompleted(it)
                    }
            }.let {
                TaskUtils.sortedByPriority(it)
            }

            taskCount.text =
                "${allTasks.size} tasks • " +
                        "${TaskRepository.pendingCount()} pending • " +
                        "${TaskRepository.completedCount()} completed"

            taskContainer.removeAllViews()

            visibleTasks.forEach { task ->
                taskContainer.addView(
                    buildTaskCard(task)
                )
            }

            val isEmpty = visibleTasks.isEmpty()

            taskScrollView.visibility =
                if (isEmpty) View.GONE else View.VISIBLE

            emptyState.visibility =
                if (isEmpty) View.VISIBLE else View.GONE

            if (isEmpty) {
                when (currentFilter) {
                    Filter.ALL -> {
                        emptyTitle.text = "No tasks yet"
                        emptyMessage.text =
                            "Add your first academic task. It will appear here and be included in Today’s study plan."
                    }

                    Filter.PENDING -> {
                        emptyTitle.text = "No pending tasks"
                        emptyMessage.text =
                            "You have completed everything currently saved."
                    }

                    Filter.COMPLETED -> {
                        emptyTitle.text = "No completed tasks"
                        emptyMessage.text =
                            "Completed tasks will appear here."
                    }
                }
            }

        } catch (error: Throwable) {
            taskContainer.removeAllViews()
            taskScrollView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE

            emptyTitle.text =
                "Tasks could not be displayed"

            emptyMessage.text =
                "Your saved data is safe. Return to Home and try again."

            Snackbar.make(
                root,
                error.message ?: "Unable to display tasks",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun buildTaskCard(task: Task): View {
        val completed = isCompleted(task)
        val priority = TaskUtils.priorityLabel(task)

        val card = MaterialCardView(this).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.setMargins(
                        0,
                        dp(8),
                        0,
                        dp(8)
                    )
                }

            radius = dp(18).toFloat()
            cardElevation = dp(1).toFloat()
            strokeWidth = dp(1)

            strokeColor =
                ContextCompat.getColor(
                    this@AllTasksActivity,
                    R.color.outline_variant
                )

            setCardBackgroundColor(
                ContextCompat.getColor(
                    this@AllTasksActivity,
                    R.color.surface
                )
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(17),
                dp(18),
                dp(16)
            )
        }

        card.addView(content)

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val module = TextView(this).apply {
            text =
                task.moduleName.ifBlank {
                    "Module"
                }

            textSize = 13f

            setTextColor(
                ContextCompat.getColor(
                    this@AllTasksActivity,
                    R.color.primary
                )
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
        }

        topRow.addView(module)

        val status = TextView(this).apply {
            text =
                if (completed) {
                    "COMPLETED"
                } else {
                    priority.uppercase()
                }

            textSize = 11f

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            gravity = Gravity.CENTER

            setPadding(
                dp(10),
                dp(5),
                dp(10),
                dp(5)
            )

            background =
                ContextCompat.getDrawable(
                    this@AllTasksActivity,
                    if (completed) {
                        R.drawable.bg_status_success
                    } else {
                        R.drawable.bg_status_primary
                    }
                )

            setTextColor(
                ContextCompat.getColor(
                    this@AllTasksActivity,
                    if (completed) {
                        R.color.success_text
                    } else {
                        R.color.on_primary_container
                    }
                )
            )
        }

        topRow.addView(status)
        content.addView(topRow)

        val title = TextView(this).apply {
            text =
                task.taskTitle.ifBlank {
                    "Untitled task"
                }

            textSize = 20f

            setTextColor(
                ContextCompat.getColor(
                    this@AllTasksActivity,
                    R.color.on_surface
                )
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            setPadding(
                0,
                dp(10),
                0,
                0
            )

            if (completed) {
                paintFlags =
                    paintFlags or
                            Paint.STRIKE_THRU_TEXT_FLAG
            }
        }

        content.addView(title)

        content.addView(
            detailText(
                "${task.category} • ${task.difficulty}"
            )
        )

        content.addView(
            detailText(
                "Due ${task.dueDate} • " +
                        TaskUtils.daysLeftText(task.dueDate)
            )
        )

        content.addView(
            detailText(
                TaskUtils.workSummary(task)
            )
        )

        val progress =
            LinearProgressIndicator(this).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(8)
                    ).also {
                        it.setMargins(
                            0,
                            dp(13),
                            0,
                            dp(7)
                        )
                    }

                max = 100

                setProgressCompat(
                    task.studyProgressPercent
                        .coerceIn(0, 100),
                    false
                )

                setIndicatorColor(
                    ContextCompat.getColor(
                        this@AllTasksActivity,
                        R.color.secondary
                    )
                )

                trackColor =
                    ContextCompat.getColor(
                        this@AllTasksActivity,
                        R.color.primary_container
                    )
            }

        content.addView(progress)

        val progressText =
            detailText(
                "${task.studyProgressPercent.coerceIn(0, 100)}% studied"
            ).apply {
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        content.addView(progressText)

        val actions =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL

                setPadding(
                    0,
                    dp(12),
                    0,
                    0
                )
            }

        val completeButton =
            createOutlinedButton().apply {

                text =
                    if (completed) {
                        "Mark as pending"
                    } else {
                        "Mark complete"
                    }

                icon =
                    ContextCompat.getDrawable(
                        this@AllTasksActivity,
                        if (completed) {
                            R.drawable.ic_undo
                        } else {
                            R.drawable.ic_check
                        }
                    )

                isAllCaps = false
                textSize = 14f

                setOnClickListener {
                    toggleCompleted(
                        task,
                        completed
                    )
                }

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                    )
            }

        actions.addView(completeButton)

        val secondaryActions =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END

                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }

        val editButton =
            createOutlinedButton().apply {

                text = "Edit task"
                isAllCaps = false

                setOnClickListener {
                    editTask(task)
                }

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1f
                    )
            }

        secondaryActions.addView(editButton)

        val deleteButton =
            createOutlinedButton().apply {

                text = "Delete"
                isAllCaps = false

                setTextColor(
                    ContextCompat.getColor(
                        this@AllTasksActivity,
                        R.color.danger
                    )
                )

                setOnClickListener {
                    confirmDelete(task)
                }

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1f
                    ).also {
                        it.marginStart = dp(8)
                    }
            }

        secondaryActions.addView(deleteButton)
        actions.addView(secondaryActions)
        content.addView(actions)

        return card
    }


    private fun createOutlinedButton(): MaterialButton {
        return MaterialButton(this).apply {
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@AllTasksActivity,
                        android.R.color.transparent
                    )
                )

            strokeColor =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@AllTasksActivity,
                        R.color.outline_variant
                    )
                )

            strokeWidth = dp(1)
            cornerRadius = dp(24)
            insetTop = 0
            insetBottom = 0

            setTextColor(
                ContextCompat.getColor(
                    this@AllTasksActivity,
                    R.color.primary
                )
            )
        }
    }

    private fun detailText(
        value: String
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = 14f

            setTextColor(
                ContextCompat.getColor(
                    this@AllTasksActivity,
                    R.color.on_surface_variant
                )
            )

            setPadding(
                0,
                dp(6),
                0,
                0
            )
        }
    }

    private fun isCompleted(
        task: Task
    ): Boolean {
        return task.isCompleted ||
                task.remainingMinutes == 0
    }

    private fun toggleCompleted(
        task: Task,
        currentlyCompleted: Boolean
    ) {
        val oldTask =
            TaskRepository.setCompleted(
                this,
                task.id,
                !currentlyCompleted
            ) ?: return

        renderTasks()

        Snackbar.make(
            root,
            if (currentlyCompleted) {
                "Task returned to pending"
            } else {
                "Task marked complete"
            },
            Snackbar.LENGTH_LONG
        ).setAction("Undo") {
            TaskRepository.updateTask(
                this,
                oldTask
            )

            renderTasks()
        }.show()
    }

    private fun editTask(
        task: Task
    ) {
        startActivity(
            Intent(
                this,
                AddTaskActivity::class.java
            ).apply {
                putExtra(
                    AddTaskActivity.EXTRA_TASK_ID,
                    task.id
                )
            }
        )
    }

    private fun confirmDelete(
        task: Task
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete task?")
            .setMessage(
                "Delete “${task.taskTitle}”? " +
                        "You can undo immediately afterwards."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton("Delete") { _, _ ->

                val deletedTask =
                    TaskRepository.deleteTask(
                        this,
                        task.id
                    ) ?: return@setPositiveButton

                renderTasks()

                Snackbar.make(
                    root,
                    "Task deleted",
                    Snackbar.LENGTH_LONG
                ).setAction("Undo") {
                    TaskRepository.restoreTask(
                        this,
                        deletedTask
                    )

                    renderTasks()
                }.show()
            }
            .show()
    }

    private fun openAddTask() {
        startActivity(
            Intent(
                this,
                AddTaskActivity::class.java
            )
        )
    }

    private fun showResultMessage(
        sourceIntent: Intent
    ) {
        if (!::root.isInitialized) {
            return
        }

        val message =
            sourceIntent.getStringExtra(
                AddTaskActivity.EXTRA_RESULT_MESSAGE
            ) ?: return

        root.post {
            Snackbar.make(
                root,
                message,
                Snackbar.LENGTH_LONG
            ).show()
        }

        sourceIntent.removeExtra(
            AddTaskActivity.EXTRA_RESULT_MESSAGE
        )
    }

    private fun dp(
        value: Int
    ): Int {
        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }
}
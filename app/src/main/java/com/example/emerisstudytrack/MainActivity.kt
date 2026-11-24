package com.example.emerisstudytrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var progressOverall: LinearProgressIndicator
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvTotalValue: TextView
    private lateinit var tvPendingValue: TextView
    private lateinit var tvHighValue: TextView
    private lateinit var tvStreakValue: TextView
    private lateinit var tvNextTaskTitle: TextView
    private lateinit var tvNextTaskMeta: TextView
    private lateinit var cardNextTask: MaterialCardView
    private lateinit var layoutDashboard: View
    private lateinit var layoutEmpty: View

    private var screenReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            TaskRepository.ensureInitialized(this)
            setContentView(R.layout.activity_main)

            findViewById<TextView>(R.id.tvTodayDate).text =
                TaskUtils.formatFriendlyDate()

            bottomNav = findViewById(R.id.bottomNav)
            progressOverall = findViewById(R.id.progressOverall)
            tvProgressPercent = findViewById(R.id.tvProgressPercent)
            tvTotalValue = findViewById(R.id.tvTotalValue)
            tvPendingValue = findViewById(R.id.tvPendingValue)
            tvHighValue = findViewById(R.id.tvHighValue)
            tvStreakValue = findViewById(R.id.tvStreakValue)
            tvNextTaskTitle = findViewById(R.id.tvNextTaskTitle)
            tvNextTaskMeta = findViewById(R.id.tvNextTaskMeta)
            cardNextTask = findViewById(R.id.cardNextTask)
            layoutDashboard = findViewById(R.id.layoutDashboard)
            layoutEmpty = findViewById(R.id.layoutEmpty)

            findViewById<MaterialButton>(R.id.btnAddTaskHome)
                .setOnClickListener {
                    openAddTask()
                }

            findViewById<MaterialButton>(R.id.btnAddFirstTask)
                .setOnClickListener {
                    openAddTask()
                }

            findViewById<MaterialButton>(R.id.btnLoadSampleData)
                .setOnClickListener {
                    TaskRepository.loadSampleData(this)
                    renderDashboardSafely()
                }

            findViewById<MaterialButton>(R.id.btnViewTasks)
                .setOnClickListener {
                    NavigationHelper.navigateTo(this, R.id.nav_tasks)
                }

            findViewById<MaterialButton>(R.id.btnOpenPlan)
                .setOnClickListener {
                    NavigationHelper.navigateTo(this, R.id.nav_today)
                }

            NavigationHelper.setup(
                this,
                bottomNav,
                R.id.nav_home
            )

            screenReady = true

        } catch (error: Throwable) {
            screenReady = false
            ScreenRecovery.show(
                this,
                getString(R.string.home),
                error
            )
        }
    }

    override fun onResume() {
        super.onResume()

        if (!screenReady) {
            return
        }

        NavigationHelper.syncSelection(
            bottomNav,
            R.id.nav_home
        )

        renderDashboardSafely()
    }

    private fun openAddTask() {
        runCatching {
            startActivity(
                Intent(
                    this,
                    AddTaskActivity::class.java
                )
            )
        }.onFailure {
            ScreenRecovery.show(
                this,
                getString(R.string.add_task),
                it
            )

            screenReady = false
        }
    }

    private fun renderDashboardSafely() {
        runCatching {
            renderDashboard()
        }.onFailure { error ->
            ScreenRecovery.show(
                this,
                getString(R.string.home),
                error
            )

            screenReady = false
        }
    }

    private fun renderDashboard() {
        val tasks = TaskRepository.tasks
        val hasTasks = tasks.isNotEmpty()

        layoutDashboard.visibility =
            if (hasTasks) View.VISIBLE else View.GONE

        layoutEmpty.visibility =
            if (hasTasks) View.GONE else View.VISIBLE

        if (!hasTasks) {
            return
        }

        val completion =
            TaskUtils.completionPercentage(tasks)

        progressOverall.progress = completion

        tvProgressPercent.text =
            getString(
                R.string.progress_percent,
                completion
            )

        tvTotalValue.text =
            TaskRepository.totalCount().toString()

        tvPendingValue.text =
            TaskRepository.pendingCount().toString()

        tvHighValue.text =
            TaskRepository.highPriorityPendingCount().toString()

        tvStreakValue.text =
            TaskUtils.currentStreak(tasks).toString()

        val nextTask =
            TaskUtils.sortedByPriority(tasks)
                .firstOrNull { !it.isCompleted }

        if (nextTask == null) {
            tvNextTaskTitle.text =
                getString(R.string.all_tasks_complete)

            tvNextTaskMeta.text =
                getString(R.string.well_done_message)

            cardNextTask.setOnClickListener {
                NavigationHelper.navigateTo(
                    this,
                    R.id.nav_progress
                )
            }

        } else {
            tvNextTaskTitle.text =
                nextTask.taskTitle

            tvNextTaskMeta.text =
                getString(
                    R.string.next_task_meta,
                    nextTask.moduleName,
                    TaskUtils.priorityLabel(nextTask),
                    TaskUtils.daysLeftText(nextTask.dueDate)
                )

            cardNextTask.setOnClickListener {
                NavigationHelper.navigateTo(
                    this,
                    R.id.nav_today
                )
            }
        }
    }
}
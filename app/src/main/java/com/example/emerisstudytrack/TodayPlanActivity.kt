package com.example.emerisstudytrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar

class TodayPlanActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StudyPlanAdapter
    private lateinit var tvPlanSummary: TextView
    private lateinit var tvPlanHours: TextView
    private lateinit var emptyState: View
    private lateinit var hoursToggleGroup: MaterialButtonToggleGroup

    private var availableHours: Int = 4
    private var screenReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            TaskRepository.ensureInitialized(this)
            setContentView(R.layout.activity_today_plan)

            bottomNav = findViewById(R.id.bottomNav)
            recyclerView = findViewById(R.id.recyclerViewPlan)
            tvPlanSummary = findViewById(R.id.tvPlanSummary)
            tvPlanHours = findViewById(R.id.tvPlanHours)
            emptyState = findViewById(R.id.layoutEmptyPlan)
            hoursToggleGroup = findViewById(R.id.hoursToggleGroup)

            adapter = StudyPlanAdapter(onLogStudyTime = ::logAllocatedTime)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
            recyclerView.setHasFixedSize(false)

            hoursToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener

                availableHours = when (checkedId) {
                    R.id.btnHours2 -> 2
                    R.id.btnHours4 -> 4
                    R.id.btnHours6 -> 6
                    R.id.btnHours8 -> 8
                    else -> 4
                }

                TaskRepository.setSelectedStudyHours(this, availableHours)
                renderPlan()
            }

            findViewById<View>(R.id.btnPlanAddTask).setOnClickListener {
                runCatching {
                    startActivity(Intent(this, AddTaskActivity::class.java))
                }.onFailure { error ->
                    ScreenRecovery.show(this, getString(R.string.add_task), error)
                    screenReady = false
                }
            }

            NavigationHelper.setup(this, bottomNav, R.id.nav_today)
            screenReady = true
        } catch (error: Throwable) {
            screenReady = false
            ScreenRecovery.show(this, getString(R.string.today), error)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!screenReady) return
        NavigationHelper.syncSelection(bottomNav, R.id.nav_today)

        availableHours = TaskRepository.getSelectedStudyHours(this)
        val selectedButton = when (availableHours) {
            2 -> R.id.btnHours2
            6 -> R.id.btnHours6
            8 -> R.id.btnHours8
            else -> R.id.btnHours4
        }

        if (hoursToggleGroup.checkedButtonId != selectedButton) {
            hoursToggleGroup.check(selectedButton)
        } else {
            renderPlan()
        }
    }

    private fun renderPlan() {
        runCatching {
            val pendingTasks = TaskRepository.tasks.filter {
                !it.isCompleted && it.remainingMinutes > 0
            }

            val loggedToday = TaskRepository.loggedMinutesToday(this)
            val remainingSessionMinutes = TaskRepository.remainingSessionMinutes(this)
            val allocations = TaskUtils.allocateStudyTime(
                tasks = pendingTasks,
                availableMinutes = remainingSessionMinutes
            )

            val planItems = allocations.map {
                StudyPlanItem(
                    task = it.task,
                    allocatedMinutes = it.allocatedMinutes
                )
            }

            adapter.submitPlan(
                newItems = planItems,
                totalSessionMinutes = remainingSessionMinutes
            )

            val allocatedNow = allocations.sumOf { it.allocatedMinutes }

            tvPlanSummary.text = when {
                pendingTasks.isEmpty() -> {
                    getString(R.string.plan_empty_summary)
                }
                remainingSessionMinutes <= 0 -> {
                    getString(
                        R.string.plan_session_finished,
                        TaskUtils.formatMinutes(loggedToday)
                    )
                }
                allocations.isEmpty() -> {
                    getString(R.string.plan_empty_summary)
                }
                else -> {
                    getString(
                        R.string.plan_summary_with_logged,
                        allocations.size,
                        pendingTasks.size,
                        availableHours,
                        TaskUtils.formatMinutes(loggedToday)
                    )
                }
            }

            tvPlanHours.text = when {
                remainingSessionMinutes <= 0 -> {
                    getString(R.string.plan_no_time_remaining)
                }
                else -> {
                    getString(
                        R.string.plan_hours_remaining,
                        TaskUtils.formatMinutes(allocatedNow),
                        TaskUtils.formatMinutes(remainingSessionMinutes)
                    )
                }
            }

            val showEmpty = planItems.isEmpty()
            emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
            recyclerView.visibility = if (showEmpty) View.GONE else View.VISIBLE
        }.onFailure { error ->
            adapter.submitPlan(emptyList(), 1)
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            tvPlanSummary.text = getString(R.string.plan_empty_summary)
            tvPlanHours.text = error.message ?: getString(R.string.plan_hours_empty)
        }
    }

    private fun logAllocatedTime(item: StudyPlanItem) {
        if (item.allocatedMinutes <= 0) return

        val result = TaskRepository.logStudyTime(
            context = this,
            taskId = item.task.id,
            requestedMinutes = item.allocatedMinutes
        ) ?: return

        renderPlan()

        val message = if (result.after.isCompleted) {
            getString(
                R.string.study_time_logged_completed,
                TaskUtils.formatMinutes(result.loggedMinutes)
            )
        } else {
            getString(
                R.string.study_time_logged_remaining,
                TaskUtils.formatMinutes(result.loggedMinutes),
                TaskUtils.formatMinutes(result.after.remainingMinutes)
            )
        }

        Snackbar.make(findViewById(R.id.rootToday), message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                TaskRepository.undoStudyLog(this, result)
                renderPlan()
            }
            .show()
    }
}

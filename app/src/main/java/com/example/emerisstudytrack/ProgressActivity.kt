package com.example.emerisstudytrack

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator

class ProgressActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var progressOverall: LinearProgressIndicator
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvCompletedValue: TextView
    private lateinit var tvPendingValue: TextView
    private lateinit var tvHighValue: TextView
    private lateinit var tvStreakValue: TextView
    private lateinit var tvMotivation: TextView
    private lateinit var content: View
    private lateinit var emptyState: View
    private var screenReady = false

    private val barIds = intArrayOf(
        R.id.progressDay1, R.id.progressDay2, R.id.progressDay3, R.id.progressDay4,
        R.id.progressDay5, R.id.progressDay6, R.id.progressDay7
    )
    private val labelIds = intArrayOf(
        R.id.tvDay1, R.id.tvDay2, R.id.tvDay3, R.id.tvDay4,
        R.id.tvDay5, R.id.tvDay6, R.id.tvDay7
    )
    private val countIds = intArrayOf(
        R.id.tvDayCount1, R.id.tvDayCount2, R.id.tvDayCount3, R.id.tvDayCount4,
        R.id.tvDayCount5, R.id.tvDayCount6, R.id.tvDayCount7
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            TaskRepository.ensureInitialized(this)
            setContentView(R.layout.activity_progress)

            bottomNav = findViewById(R.id.bottomNav)
            progressOverall = findViewById(R.id.progressOverall)
            tvProgressPercent = findViewById(R.id.tvProgressPercent)
            tvCompletedValue = findViewById(R.id.tvCompletedValue)
            tvPendingValue = findViewById(R.id.tvPendingValue)
            tvHighValue = findViewById(R.id.tvHighValue)
            tvStreakValue = findViewById(R.id.tvStreakValue)
            tvMotivation = findViewById(R.id.tvMotivation)
            content = findViewById(R.id.layoutProgressContent)
            emptyState = findViewById(R.id.layoutEmptyProgress)

            findViewById<View>(R.id.btnProgressAddTask).setOnClickListener {
                runCatching {
                    startActivity(android.content.Intent(this, AddTaskActivity::class.java))
                }.onFailure { error ->
                    ScreenRecovery.show(this, getString(R.string.add_task), error)
                    screenReady = false
                }
            }

            NavigationHelper.setup(this, bottomNav, R.id.nav_progress)
            screenReady = true
        } catch (error: Throwable) {
            screenReady = false
            ScreenRecovery.show(this, getString(R.string.progress), error)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!screenReady) return
        NavigationHelper.syncSelection(bottomNav, R.id.nav_progress)
        renderProgressSafely()
    }

    private fun renderProgressSafely() {
        runCatching { renderProgress() }
            .onFailure { error ->
                ScreenRecovery.show(this, getString(R.string.progress), error)
                screenReady = false
            }
    }

    private fun renderProgress() {
        val tasks = TaskRepository.tasks
        val hasTasks = tasks.isNotEmpty()
        content.visibility = if (hasTasks) View.VISIBLE else View.GONE
        emptyState.visibility = if (hasTasks) View.GONE else View.VISIBLE
        if (!hasTasks) return

        val percent = TaskUtils.completionPercentage(tasks)
        progressOverall.progress = percent
        tvProgressPercent.text = getString(R.string.progress_percent, percent)
        tvCompletedValue.text = TaskRepository.completedCount().toString()
        tvPendingValue.text = TaskRepository.pendingCount().toString()
        tvHighValue.text = TaskRepository.highPriorityPendingCount().toString()
        tvStreakValue.text = TaskUtils.currentStreak(tasks).toString()

        tvMotivation.text = when {
            percent == 100 -> getString(R.string.motivation_complete)
            percent >= 75 -> getString(R.string.motivation_almost)
            percent >= 40 -> getString(R.string.motivation_progress)
            else -> getString(R.string.motivation_start)
        }

        TaskUtils.lastSevenDates().forEachIndexed { index, (date, label) ->
            val count = TaskUtils.completedOnDate(tasks, date)
            findViewById<TextView>(labelIds[index]).text = label
            findViewById<TextView>(countIds[index]).text = count.toString()
            findViewById<LinearProgressIndicator>(barIds[index]).apply {
                max = 3
                progress = count.coerceAtMost(3)
                contentDescription = getString(R.string.day_completion_description, label, count)
            }
        }
    }
}

package com.example.emerisstudytrack

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator

class StudyPlanAdapter(
    private var items: List<StudyPlanItem> = emptyList(),
    private var sessionMinutes: Int = 1,
    private val onLogStudyTime: (StudyPlanItem) -> Unit
) : RecyclerView.Adapter<StudyPlanAdapter.PlanViewHolder>() {

    fun submitPlan(newItems: List<StudyPlanItem>, totalSessionMinutes: Int) {
        items = newItems.toList()
        sessionMinutes = totalSessionMinutes.coerceAtLeast(1)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_study_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PlanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvModule: TextView = itemView.findViewById(R.id.tvPlanModule)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvPlanTaskTitle)
        private val tvDueDate: TextView = itemView.findViewById(R.id.tvPlanDueDate)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvPlanTaskDetails)
        private val tvAllocatedTime: TextView = itemView.findViewById(R.id.tvAllocatedTime)
        private val tvExplanation: TextView = itemView.findViewById(R.id.tvAllocationExplanation)
        private val chipPriority: Chip = itemView.findViewById(R.id.chipPlanPriority)
        private val progressShare: LinearProgressIndicator = itemView.findViewById(R.id.progressPlanShare)
        private val btnLogTime: MaterialButton = itemView.findViewById(R.id.btnPlanCompleteTask)

        fun bind(item: StudyPlanItem) {
            val task = item.task
            val context = itemView.context
            val priority = TaskUtils.priorityLabel(task)

            tvModule.text = task.moduleName
            tvTitle.text = task.taskTitle
            tvDueDate.text = "${TaskUtils.daysLeftText(task.dueDate)} • ${task.dueDate}"
            tvDetails.text = "${task.category} • ${task.difficulty}\n${TaskUtils.workSummary(task)}"
            tvAllocatedTime.text = context.getString(
                R.string.study_today_value,
                TaskUtils.formatMinutes(item.allocatedMinutes)
            )

            val sharePercent = if (item.allocatedMinutes <= 0) {
                0
            } else {
                ((item.allocatedMinutes * 100.0) / sessionMinutes)
                    .toInt()
                    .coerceIn(1, 100)
            }

            progressShare.progress = sharePercent
            tvExplanation.text = when {
                item.allocatedMinutes <= 0 -> {
                    context.getString(R.string.not_scheduled_explanation)
                }
                priority == "High priority" -> {
                    context.getString(R.string.high_allocation_explanation, sharePercent)
                }
                priority == "Medium priority" -> {
                    context.getString(R.string.medium_allocation_explanation, sharePercent)
                }
                else -> {
                    context.getString(R.string.low_allocation_explanation, sharePercent)
                }
            }

            chipPriority.text = priority
            val (background, foreground) = when (priority) {
                "High priority" -> R.color.priority_high_bg to R.color.priority_high_text
                "Medium priority" -> R.color.priority_medium_bg to R.color.priority_medium_text
                else -> R.color.priority_low_bg to R.color.priority_low_text
            }
            chipPriority.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(context, background)
            )
            chipPriority.setTextColor(ContextCompat.getColor(context, foreground))

            val canLog = item.allocatedMinutes > 0 &&
                    !task.isCompleted &&
                    task.remainingMinutes > 0

            btnLogTime.isEnabled = canLog
            btnLogTime.text = if (canLog) {
                context.getString(
                    R.string.log_study_time_button,
                    TaskUtils.formatMinutes(item.allocatedMinutes)
                )
            } else {
                context.getString(R.string.no_time_to_log)
            }

            btnLogTime.setOnClickListener {
                if (canLog) onLogStudyTime(item)
            }
        }
    }
}

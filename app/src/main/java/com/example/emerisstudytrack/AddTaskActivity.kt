package com.example.emerisstudytrack

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddTaskActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_RESULT_MESSAGE = "result_message"
    }

    private lateinit var tilModule: TextInputLayout
    private lateinit var tilTitle: TextInputLayout
    private lateinit var tilCategory: TextInputLayout
    private lateinit var tilDueDate: TextInputLayout
    private lateinit var tilDifficulty: TextInputLayout
    private lateinit var tilHours: TextInputLayout

    private lateinit var etModule: TextInputEditText
    private lateinit var etTitle: TextInputEditText
    private lateinit var dropdownCategory: MaterialAutoCompleteTextView
    private lateinit var etDueDate: TextInputEditText
    private lateinit var dropdownDifficulty: MaterialAutoCompleteTextView
    private lateinit var etHours: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var tvPriorityPreview: TextView
    private lateinit var tvPriorityReason: TextView
    private lateinit var previewCard: View

    private var editingTask: Task? = null
    private var screenReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            TaskRepository.ensureInitialized(this)
            setContentView(R.layout.activity_add_task)

            editingTask = intent.getStringExtra(EXTRA_TASK_ID)
                ?.let { TaskRepository.getTask(it) }

            bindViews()
            setupToolbar()
            setupDropdowns()
            setupValidationListeners()
            editingTask?.let(::populateForEditing)

            etDueDate.setOnClickListener { showDatePicker() }
            tilDueDate.setEndIconOnClickListener { showDatePicker() }
            btnSave.setOnClickListener { saveTask() }
            findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { finish() }

            screenReady = true
            updateSaveStateAndPreview()
        } catch (error: Throwable) {
            screenReady = false
            ScreenRecovery.show(this, getString(R.string.add_task), error)
        }
    }

    private fun bindViews() {
        tilModule = findViewById(R.id.tilModule)
        tilTitle = findViewById(R.id.tilTitle)
        tilCategory = findViewById(R.id.tilCategory)
        tilDueDate = findViewById(R.id.tilDueDate)
        tilDifficulty = findViewById(R.id.tilDifficulty)
        tilHours = findViewById(R.id.tilHours)
        etModule = findViewById(R.id.etModuleName)
        etTitle = findViewById(R.id.etTaskTitle)
        dropdownCategory = findViewById(R.id.dropdownCategory)
        etDueDate = findViewById(R.id.etDueDate)
        dropdownDifficulty = findViewById(R.id.dropdownDifficulty)
        etHours = findViewById(R.id.etEstimatedHours)
        btnSave = findViewById(R.id.btnSaveTask)
        tvPriorityPreview = findViewById(R.id.tvPriorityPreview)
        tvPriorityReason = findViewById(R.id.tvPriorityReason)
        previewCard = findViewById(R.id.cardPriorityPreview)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = getString(
            if (editingTask == null) R.string.add_task else R.string.edit_task
        )
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDropdowns() {
        dropdownCategory.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.task_categories)
            )
        )
        dropdownDifficulty.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.task_difficulties)
            )
        )
    }

    private fun setupValidationListeners() {
        etModule.doAfterTextChanged {
            tilModule.error = null
            updateSaveStateAndPreview()
        }
        etTitle.doAfterTextChanged {
            tilTitle.error = null
            updateSaveStateAndPreview()
        }
        etDueDate.doAfterTextChanged {
            tilDueDate.error = null
            updateSaveStateAndPreview()
        }
        etHours.doAfterTextChanged {
            tilHours.error = null
            updateSaveStateAndPreview()
        }
        dropdownCategory.setOnItemClickListener { _, _, _, _ ->
            tilCategory.error = null
            updateSaveStateAndPreview()
        }
        dropdownDifficulty.setOnItemClickListener { _, _, _, _ ->
            tilDifficulty.error = null
            updateSaveStateAndPreview()
        }
    }

    private fun populateForEditing(task: Task) {
        etModule.setText(task.moduleName)
        etTitle.setText(task.taskTitle)
        dropdownCategory.setText(task.category, false)
        etDueDate.setText(task.dueDate)
        dropdownDifficulty.setText(task.difficulty, false)
        etHours.setText(task.estimatedHours.toString())
        btnSave.text = getString(R.string.update_task)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentText = etDueDate.text?.toString().orEmpty()
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(currentText)
        }.getOrNull()?.let { calendar.time = it }

        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                etDueDate.setText(
                    String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        dialog.datePicker.minDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        dialog.show()
    }

    private fun updateSaveStateAndPreview() {
        val module = etModule.text?.toString()?.trim().orEmpty()
        val title = etTitle.text?.toString()?.trim().orEmpty()
        val category = dropdownCategory.text?.toString()?.trim().orEmpty()
        val dueDate = etDueDate.text?.toString()?.trim().orEmpty()
        val difficulty = dropdownDifficulty.text?.toString()?.trim().orEmpty()
        val hours = etHours.text?.toString()?.toIntOrNull()

        val valid = TaskUtils.isValidModuleCode(module) &&
                title.length >= 3 &&
                category in resources.getStringArray(R.array.task_categories) &&
                dueDate.isNotBlank() && !TaskUtils.isPastDate(dueDate) &&
                difficulty in resources.getStringArray(R.array.task_difficulties) &&
                hours != null && hours in 1..24

        btnSave.isEnabled = valid

        if (!valid || hours == null) {
            previewCard.visibility = View.GONE
            return
        }

        val original = editingTask
        val newTotalMinutes = hours * 60
        val preservedStudied = original?.safeStudiedMinutes
            ?.coerceAtMost(newTotalMinutes) ?: 0

        val preview = Task(
            id = original?.id ?: "preview",
            moduleName = module.uppercase(Locale.US),
            taskTitle = title,
            category = category,
            dueDate = dueDate,
            difficulty = difficulty,
            estimatedHours = hours,
            studiedMinutes = preservedStudied,
            isCompleted = preservedStudied >= newTotalMinutes,
            completedDate = original?.completedDate,
            createdAt = original?.createdAt ?: System.currentTimeMillis()
        )

        previewCard.visibility = View.VISIBLE
        tvPriorityPreview.text = TaskUtils.priorityLabel(preview)
        tvPriorityReason.text = "${TaskUtils.priorityReason(preview)}\n${TaskUtils.workSummary(preview)}"
    }

    private fun saveTask() {
        if (!validateAllFields()) return

        val hours = etHours.text?.toString()?.toIntOrNull() ?: return
        val original = editingTask
        val newTotalMinutes = hours * 60
        val preservedStudied = original?.safeStudiedMinutes
            ?.coerceAtMost(newTotalMinutes) ?: 0
        val completed = preservedStudied >= newTotalMinutes

        val task = Task(
            id = original?.id ?: UUID.randomUUID().toString(),
            moduleName = etModule.text.toString().trim().uppercase(Locale.US),
            taskTitle = etTitle.text.toString().trim(),
            category = dropdownCategory.text.toString().trim(),
            dueDate = etDueDate.text.toString().trim(),
            difficulty = dropdownDifficulty.text.toString().trim(),
            estimatedHours = hours,
            studiedMinutes = preservedStudied,
            isCompleted = completed,
            completedDate = if (completed) {
                original?.completedDate ?: TaskUtils.todayString()
            } else {
                null
            },
            createdAt = original?.createdAt ?: System.currentTimeMillis()
        )

        val message = if (original == null) {
            TaskRepository.addTask(this, task)
            getString(R.string.task_saved_message)
        } else {
            TaskRepository.updateTask(this, task)
            getString(R.string.task_updated_message)
        }

        runCatching {
            startActivity(Intent(this, AllTasksActivity::class.java).apply {
                putExtra(EXTRA_RESULT_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }.onFailure { error ->
            ScreenRecovery.show(this, getString(R.string.tasks), error)
            screenReady = false
        }
    }

    private fun validateAllFields(): Boolean {
        var valid = true
        val module = etModule.text?.toString()?.trim().orEmpty()
        val title = etTitle.text?.toString()?.trim().orEmpty()
        val category = dropdownCategory.text?.toString()?.trim().orEmpty()
        val dueDate = etDueDate.text?.toString()?.trim().orEmpty()
        val difficulty = dropdownDifficulty.text?.toString()?.trim().orEmpty()
        val hours = etHours.text?.toString()?.toIntOrNull()

        if (!TaskUtils.isValidModuleCode(module)) {
            tilModule.error = getString(R.string.module_error)
            valid = false
        }
        if (title.length < 3) {
            tilTitle.error = getString(R.string.title_error)
            valid = false
        }
        if (category !in resources.getStringArray(R.array.task_categories)) {
            tilCategory.error = getString(R.string.category_error)
            valid = false
        }
        if (dueDate.isBlank() || TaskUtils.isPastDate(dueDate)) {
            tilDueDate.error = getString(R.string.due_date_error)
            valid = false
        }
        if (difficulty !in resources.getStringArray(R.array.task_difficulties)) {
            tilDifficulty.error = getString(R.string.difficulty_error)
            valid = false
        }
        if (hours == null || hours !in 1..24) {
            tilHours.error = getString(R.string.hours_error)
            valid = false
        }
        return valid
    }
}
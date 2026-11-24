package com.example.emerisstudytrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskUtilsTest {

    @Test
    fun categoryWeightsReflectAssessmentImportance() {
        assertEquals(1, TaskUtils.categoryWeight("ICE Task"))
        assertEquals(3, TaskUtils.categoryWeight("Test"))
        assertEquals(4, TaskUtils.categoryWeight("Summative Assessment"))
        assertEquals(4, TaskUtils.categoryWeight("Exam"))
    }

    @Test
    fun urgencyWeightsIncreaseNearDeadline() {
        assertEquals(4, TaskUtils.urgencyWeight(-1))
        assertEquals(4, TaskUtils.urgencyWeight(1))
        assertEquals(3, TaskUtils.urgencyWeight(3))
        assertEquals(2, TaskUtils.urgencyWeight(7))
        assertEquals(1, TaskUtils.urgencyWeight(8))
    }

    @Test
    fun moduleCodeValidationRejectsUnstructuredValues() {
        assertTrue(TaskUtils.isValidModuleCode("INSY7311"))
        assertTrue(TaskUtils.isValidModuleCode("USAB7311"))
        assertFalse(TaskUtils.isValidModuleCode("information systems"))
        assertFalse(TaskUtils.isValidModuleCode("7311"))
    }

    @Test
    fun dateValidationRejectsMalformedDates() {
        assertTrue(TaskUtils.isValidDate("2099-12-31"))
        assertFalse(TaskUtils.isValidDate("31/12/2099"))
        assertFalse(TaskUtils.isValidDate("not-a-date"))
    }

    @Test
    fun completionPercentageHandlesEmptyAndMixedLists() {
        assertEquals(0, TaskUtils.completionPercentage(emptyList()))
        val tasks = listOf(
            task("A", completed = true),
            task("B", completed = false),
            task("C", completed = true),
            task("D", completed = false)
        )
        assertEquals(50, TaskUtils.completionPercentage(tasks))
    }

    @Test
    fun todayPlanAllocatesTheSelectedTimeAcrossTasks() {
        val tasks = listOf(
            task("High", category = "Summative Assessment", difficulty = "Hard", hours = 20),
            task("Medium", category = "Test", difficulty = "Medium", hours = 20),
            task("Low", category = "ICE Task", difficulty = "Easy", hours = 20)
        )
        val plan = TaskUtils.buildTodayPlan(tasks, availableHours = 8)
        assertEquals(480, plan.sumOf { it.allocatedMinutes })
        assertEquals(360, plan.first { it.task.taskTitle == "High" }.allocatedMinutes)
        assertEquals(90, plan.first { it.task.taskTitle == "Medium" }.allocatedMinutes)
        assertEquals(30, plan.first { it.task.taskTitle == "Low" }.allocatedMinutes)
    }

    @Test
    fun todayPlanUsesRemainingTimeInsteadOfOriginalEstimate() {
        val task = Task(
            moduleName = "INSY7311",
            taskTitle = "Partially studied task",
            category = "Summative Assessment",
            dueDate = "2099-12-31",
            difficulty = "Hard",
            estimatedHours = 20,
            remainingMinutes = 16 * 60
        )

        assertEquals("4 h studied • 16 h remaining • 20 h total", TaskUtils.taskWorkSummary(task))
        val plan = TaskUtils.buildTodayPlanMinutes(listOf(task), 8 * 60)
        assertEquals(8 * 60, plan.single().allocatedMinutes)
    }

    @Test
    fun todayPlanDoesNotExceedTaskEstimate() {
        val tasks = listOf(
            task("High", category = "Summative Assessment", difficulty = "Hard", hours = 1),
            task("Medium", category = "Test", difficulty = "Medium", hours = 20)
        )
        val plan = TaskUtils.buildTodayPlan(tasks, availableHours = 8)
        assertEquals(60, plan.first { it.task.taskTitle == "High" }.allocatedMinutes)
        assertEquals(420, plan.first { it.task.taskTitle == "Medium" }.allocatedMinutes)
    }

    private fun task(
        title: String,
        category: String = "Assessment",
        difficulty: String = "Medium",
        hours: Int = 1,
        completed: Boolean = false
    ): Task = Task(
        moduleName = "INSY7311",
        taskTitle = title,
        category = category,
        dueDate = "2099-12-31",
        difficulty = difficulty,
        estimatedHours = hours,
        isCompleted = completed
    )
}

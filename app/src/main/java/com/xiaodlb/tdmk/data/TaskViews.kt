package com.xiaodlb.tdmk.data

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Pure date/list results. Supply all stored candidates, including captured completed records. */
class TaskViews(private val clock: Clock) {
    fun today(tasks: List<Task>): List<Task> {
        val date = LocalDate.now(clock)
        return select(tasks, date) { it.scheduledDate == date }
    }

    fun overdue(tasks: List<Task>): List<Task> {
        val date = LocalDate.now(clock)
        return tasks.filter { it.completedAt == null && it.scheduledDate != null && it.scheduledDate < date }.sortedWith(ordering)
    }

    fun anytime(tasks: List<Task>): List<Task> = select(tasks, LocalDate.now(clock)) { it.scheduledDate == null }

    fun inList(tasks: List<Task>, listId: String): List<Task> = select(tasks, LocalDate.now(clock)) { it.listId == listId }

    fun upcoming(tasks: List<Task>): List<Task> {
        val date = LocalDate.now(clock)
        return select(tasks, date) { it.scheduledDate != null && it.scheduledDate > date && it.scheduledDate <= date.plusDays(7) }
    }

    fun homeUpcoming(tasks: List<Task>): List<Task> {
        val date = LocalDate.now(clock)
        val sunday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return select(tasks, date) { it.scheduledDate != null && it.scheduledDate > date && it.scheduledDate <= sunday }
    }

    private fun select(tasks: List<Task>, today: LocalDate, matches: (Task) -> Boolean): List<Task> =
        tasks.filter { isActiveEligible(it, today) && matches(it) }.sortedWith(ordering)

    companion object {
        /** Reusable #11 boundary: future captured days stay active after a backward local-day jump. */
        fun isActiveEligible(task: Task, today: LocalDate): Boolean = task.completedAt == null ||
            requireNotNull(task.completionDate) { "Completed task requires a captured day" } >= today

        val ordering: Comparator<Task> = compareBy<Task> {
            when (it.priority) { "high" -> 0; "medium" -> 1; "low" -> 2; else -> error("Unknown task priority") }
        }.thenBy { it.createdAt }.thenBy { it.id }
    }
}

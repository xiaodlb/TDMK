package com.xiaodlb.tdmk

import com.xiaodlb.tdmk.data.Task
import com.xiaodlb.tdmk.data.TaskViews
import java.time.Clock

/** Host context controls results and list defaults, never invents a scheduled date. */
sealed interface TaskContext {
    data object Anytime : TaskContext
    data object Today : TaskContext
    data object Upcoming : TaskContext
    data object Home : TaskContext
    data class InList(val id: String) : TaskContext
}

fun TaskContext.results(tasks: List<Task>, clock: Clock): List<Task> {
    val views = TaskViews(clock)
    return when (this) {
        TaskContext.Anytime -> views.anytime(tasks)
        TaskContext.Today -> views.today(tasks)
        TaskContext.Upcoming -> views.upcoming(tasks)
        TaskContext.Home -> views.homeOverdueAndToday(tasks)
        is TaskContext.InList -> views.inList(tasks, id)
    }
}

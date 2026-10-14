package com.xiaodlb.tdmk

import com.xiaodlb.tdmk.data.TaskList

/** Stable selection keys outlive list renames; a removed list falls back to Today. */
internal data class TaskTab(val key: String, val label: String, val context: TaskContext?)

internal object TaskTabs {
    const val TODAY = "today"
    const val UPCOMING = "upcoming"
    const val ANYTIME = "anytime"
    const val MANAGE = "manage"
    fun listKey(id: String) = "list:$id"

    fun available(lists: List<TaskList>): List<TaskTab> = listOf(
        TaskTab(TODAY, "Today", TaskContext.Today),
        TaskTab(UPCOMING, "Upcoming", TaskContext.Upcoming),
        TaskTab(ANYTIME, "Anytime", TaskContext.Anytime),
    ) + lists.sortedWith(compareBy<TaskList> { !it.isDefault }.thenBy { it.order }).map { TaskTab(listKey(it.id), it.name, TaskContext.InList(it.id)) } +
        TaskTab(MANAGE, "Manage lists", null)

    fun resolve(requested: String, lists: List<TaskList>): String =
        if (available(lists).any { it.key == requested }) requested else TODAY
}

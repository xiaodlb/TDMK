package com.xiaodlb.tdmk

import com.xiaodlb.tdmk.data.TaskList
import org.junit.Assert.*
import org.junit.Test

class TaskTabsTest {
    private fun list(id: String, name: String, order: Long, default: Boolean = false) =
        TaskList(id, name, default, order, 1, 1)

    @Test fun labelsFollowPersistedOrderWhileKeysSurviveRenameAndDeletionFallsBack() {
        val inbox = list("inbox-id", "General", 0, true)
        val work = list("work-id", "Work", 1)
        val home = list("home-id", "Home", 2)
        val tabs = TaskTabs.available(listOf(home, inbox, work))
        assertEquals(listOf("Today", "Upcoming", "Anytime", "General", "Work", "Home", "Manage lists"), tabs.map { it.label })
        assertEquals(listOf(TaskTabs.TODAY, TaskTabs.UPCOMING, TaskTabs.ANYTIME, TaskTabs.listKey(inbox.id),
            TaskTabs.listKey(work.id), TaskTabs.listKey(home.id), TaskTabs.MANAGE), tabs.map { it.key })
        assertEquals(TaskTabs.listKey(inbox.id), TaskTabs.resolve(TaskTabs.listKey(inbox.id), listOf(inbox.copy(name = "Renamed"), work)))
        assertEquals(TaskTabs.TODAY, TaskTabs.resolve(TaskTabs.listKey(work.id), listOf(inbox, home)))
        assertEquals(TaskTabs.TODAY, TaskTabs.resolve("unknown", listOf(inbox)))
        assertEquals(TaskContext.Upcoming, tabs[1].context)
        assertEquals(TaskContext.InList(inbox.id), tabs[3].context)
    }
}

package com.xiaodlb.tdmk

import com.xiaodlb.tdmk.data.Task
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskContextTest {
    @Test fun contextsKeepExistingEligibilityAndCompletionDayRules() {
        val day = LocalDate.of(2026, 10, 13)
        val clock = Clock.fixed(day.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        fun task(id: String, date: LocalDate?, completed: LocalDate? = null) = Task(id, "work", id, date, "medium", null, null, completed?.atStartOfDay(ZoneOffset.UTC)?.toInstant(), completed, Instant.EPOCH, Instant.EPOCH)
        val data = listOf(task("undated", null), task("today", day), task("done", null, day), task("history", null, day.minusDays(1)))
        assertEquals(listOf("done", "undated"), TaskContext.Anytime.results(data, clock).map { it.id })
        assertEquals(listOf("today"), TaskContext.Today.results(data, clock).map { it.id })
        assertEquals(listOf("today"), TaskContext.Home.results(data, clock).map { it.id })
        assertEquals(setOf("undated", "today", "done"), TaskContext.InList("work").results(data, clock).map { it.id }.toSet())
        assertTrue(TaskContext.InList("other").results(data, clock).isEmpty())
    }
}

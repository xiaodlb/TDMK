package com.xiaodlb.tdmk.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class CompletionViewsTest {
    private val today = LocalDate.of(2026, 10, 12)
    private fun views(day: LocalDate) = TaskViews(Clock.fixed(day.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC))
    private fun task(id: String, scheduled: LocalDate?, completed: LocalDate? = null, priority: String = "medium", created: Long = 1) =
        Task(id, "default", id, scheduled, priority, null, null, completed?.atStartOfDay(ZoneOffset.UTC)?.toInstant(), completed, Instant.ofEpochMilli(created), Instant.ofEpochMilli(100 - created))

    @Test fun homeRetainsFormerlyOverdueCompletionsWithoutChangingUnfinishedOverdue() {
        val records = listOf(task("unfinished-overdue", today.minusDays(1)), task("done-formerly-overdue", today.minusDays(1), today),
            task("older", today.minusDays(1), today.minusDays(1)), task("future-captured", today.minusDays(1), today.plusDays(1)),
            task("done-today", today, today), task("undated", null, today), task("upcoming", today.plusDays(1)))
        val results = views(today)
        assertEquals(setOf("unfinished-overdue", "done-formerly-overdue", "future-captured", "done-today"), results.homeOverdueAndToday(records).map { it.id }.toSet())
        assertEquals(listOf("unfinished-overdue"), results.overdue(records).map { it.id })
        assertNotNull(results.today(records).single().completedAt)
        assertEquals(listOf("undated"), results.anytime(records).map { it.id })
        assertEquals(listOf("older"), results.history(records).flatMap { it.tasks }.map { it.id })
        assertEquals(7, records.size)
    }

    @Test fun historyUsesCapturedDaysAndExistingPriorityCreationIdentityOrder() {
        val newer = today.minusDays(1); val older = today.minusDays(2)
        val records = listOf(task("new-b", today.plusDays(10), newer, "high", 1), task("new-a", null, newer, "high", 1),
            task("new-oldest", today, newer, "high", 0), task("new-low", older, newer, "low", 0),
            task("old-medium", today.plusDays(1), older), task("same-day", today, today),
            task("future-day", today, today.plusDays(1)), task("unfinished", today),
            task("reopened", today, older).copy(completedAt = null, completionDate = null))
        repeat(20) { seed ->
            val groups = views(today).history(records.shuffled(Random(seed)))
            assertEquals(listOf(newer, older), groups.map { it.day })
            assertEquals(listOf("new-oldest", "new-a", "new-b", "new-low"), groups.first().tasks.map { it.id })
            assertEquals(listOf("old-medium"), groups.last().tasks.map { it.id })
            val ids = groups.flatMap { it.tasks }.map { it.id }
            assertEquals(5, ids.size); assertEquals(ids.size, ids.toSet().size)
        }
    }

    @Test fun midnightRestartClockJumpsAndTravelDeriveVisibilityWithoutWrites() {
        val zone = ZoneId.of("Asia/Qyzylorda")
        val before = today.atTime(23, 59, 30).atZone(zone).toInstant()
        val after = today.plusDays(1).atTime(0, 0, 30).atZone(zone).toInstant()
        val first = task("before-midnight", today.minusDays(1), today).copy(completedAt = before)
        val second = task("after-midnight", today.plusDays(1), today.plusDays(1)).copy(completedAt = after)
        val records = listOf(first, second)
        assertEquals(listOf(first), TaskViews(Clock.fixed(before, zone)).homeOverdueAndToday(records))
        val restarted = TaskViews(Clock.fixed(after, zone))
        assertEquals(listOf(first), restarted.history(records).single().tasks)
        assertEquals(listOf(second), restarted.today(records))
        assertEquals(listOf(today.plusDays(1), today), views(today.plusDays(2)).history(records).map { it.day })
        assertTrue(views(today.minusDays(1)).history(records).isEmpty())
        assertEquals(listOf(first), views(today.minusDays(1)).homeOverdueAndToday(records))
        assertTrue(TaskViews(Clock.fixed(after, ZoneId.of("Etc/GMT+12"))).history(records).isEmpty())
        assertEquals(today, first.completionDate); assertEquals(today.minusDays(1), first.scheduledDate)
        assertEquals(listOf(first, second), records)
    }
}

package com.xiaodlb.tdmk.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class TaskViewsTest {
    private val monday = LocalDate.of(2026, 10, 12)
    private fun views(date: LocalDate) = TaskViews(Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC))
    private fun task(id: String, date: LocalDate?, priority: String = "medium", created: Long = 1, list: String = "default", completed: LocalDate? = null) =
        Task(id, list, id, date, priority, null, null, completed?.let { Instant.ofEpochMilli(100) }, completed, Instant.ofEpochMilli(created), Instant.ofEpochMilli(1000 - created))
    private fun ids(tasks: List<Task>) = tasks.map { it.id }

    @Test fun dateViewsSpanListsPreserveRecordsAndUseStoredEligibility() {
        val data = listOf(task("today-a", monday), task("today-b", monday, list = "work"),
            task("yesterday", monday.minusDays(1)), task("undated", null), task("future", monday.plusDays(8)),
            task("completed-today", monday, completed = monday), task("older-completed", monday, completed = monday.minusDays(1)),
            task("future-completion", null, list = "work", completed = monday.plusDays(1)),
            task("formerly-overdue", monday.minusDays(1), completed = monday))
        val original = data.toList()
        val filter = views(monday)
        assertEquals(setOf("today-a", "today-b", "completed-today"), ids(filter.today(data)).toSet())
        assertEquals(listOf("yesterday"), ids(filter.overdue(data)))
        assertEquals(setOf("undated", "future-completion"), ids(filter.anytime(data)).toSet())
        assertEquals(setOf("today-b", "future-completion"), ids(filter.inList(data, "work")).toSet())
        assertTrue(filter.inList(data, "missing").isEmpty())
        assertTrue(TaskViews.isActiveEligible(data.last(), monday)) // #11 may retain formerly-overdue completions on Home.
        assertEquals(original, data)
    }

    @Test fun upcomingEndpointsAndSundayMondayRangesStayDistinct() {
        val data = (0L..8L).map { task("d$it", monday.plusDays(it)) }
        assertEquals((1..7).map { "d$it" }, ids(views(monday).upcoming(data)))
        assertEquals((1..6).map { "d$it" }, ids(views(monday).homeUpcoming(data)))
        val sunday = monday.plusDays(6)
        val nextWeek = (0L..8L).map { task("d$it", sunday.plusDays(it)) }
        assertTrue(views(sunday).homeUpcoming(nextWeek).isEmpty())
        assertEquals((1..7).map { "d$it" }, ids(views(sunday).upcoming(nextWeek)))
        assertEquals((2..7).map { "d$it" }, ids(views(sunday.plusDays(1)).homeUpcoming(nextWeek)))
    }

    @Test fun priorityCreationAndStableIdentitySortIgnoreOtherTimes() {
        val data = listOf(task("low", null, "low", 0), task("medium", null, "medium", 0),
            task("high-z", monday, "high", 2, completed = monday), task("high-b", null, "high", 1),
            task("high-a", monday.plusDays(1), "high", 1).copy(reminderTime = LocalTime.of(23, 59)))
        val expected = listOf("high-a", "high-b", "high-z", "medium", "low")
        repeat(30) { seed -> assertEquals(expected, ids(views(monday).inList(data.shuffled(Random(seed)), "default"))) }
        assertEquals(expected, ids(views(monday).inList(data.map { it.copy(updatedAt = Instant.ofEpochMilli(Long.MAX_VALUE / 1000000)) }, "default")))
    }

    @Test fun midnightTimezoneAndCalendarRolloversReclassifyWithoutChangingDateOnlyValues() {
        val zone = ZoneId.of("Asia/Qyzylorda")
        val before = monday.atTime(23, 59).atZone(zone).toInstant()
        val after = monday.plusDays(1).atStartOfDay(zone).toInstant()
        val data = listOf(task("same-day", monday).copy(reminderTime = LocalTime.of(23, 59)), task("next-day", monday.plusDays(1)), task("null", null))
        assertEquals(listOf("same-day"), ids(TaskViews(Clock.fixed(before, zone)).today(data)))
        assertEquals(listOf("next-day"), ids(TaskViews(Clock.fixed(after, zone)).today(data)))
        assertEquals(listOf("same-day"), ids(TaskViews(Clock.fixed(after, zone)).overdue(data)))
        assertEquals(listOf("same-day"), ids(TaskViews(Clock.fixed(after, ZoneId.of("Etc/GMT+12"))).today(data)))
        assertEquals(monday, data.first().scheduledDate)
        listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 12, 31), LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29)).forEach { date ->
            val cases = (-1L..8L).map { task("d$it", date.plusDays(it)) } + task("null", null)
            assertEquals(listOf("d0"), ids(views(date).today(cases)))
            assertEquals(listOf("d-1"), ids(views(date).overdue(cases)))
            assertEquals(listOf("null"), ids(views(date).anytime(cases)))
            assertEquals((1..7).map { "d$it" }, ids(views(date).upcoming(cases)))
        }
    }

    @Test fun capturedCompletionDayBoundaryIsReusableAndDoesNotPurgeHistory() {
        val data = listOf(task("unfinished", monday), task("done-yesterday", monday, completed = monday.minusDays(1)),
            task("done-today", monday, completed = monday), task("done-future", monday, completed = monday.plusDays(1)))
        assertEquals(setOf("unfinished", "done-today", "done-future"), ids(views(monday).today(data)).toSet())
        assertEquals(setOf("unfinished", "done-yesterday", "done-today", "done-future"), ids(views(monday.minusDays(1)).inList(data, "default")).toSet())
        assertEquals(listOf("unfinished"), ids(views(monday.plusDays(2)).overdue(data)))
        assertEquals(4, data.size)
    }
}

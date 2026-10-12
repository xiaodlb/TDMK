package com.xiaodlb.tdmk.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecurrenceCalendarTest {
    private fun d(value: String) = LocalDate.parse(value)

    @Test fun dailyWeeklyCalendarAndInclusiveBoundsAcrossYear() {
        assertEquals(listOf(d("2026-12-30"), d("2026-12-31"), d("2027-01-01"), d("2027-01-02")),
            RecurrenceCalendar.slots(d("2026-12-30"), "daily", d("2026-12-01"), d("2027-01-02")))
        assertEquals(listOf(d("2026-12-28"), d("2027-01-04"), d("2027-01-11")),
            RecurrenceCalendar.slots(d("2026-12-21"), "weekly", d("2026-12-22"), d("2027-01-11")))
        assertTrue(RecurrenceCalendar.slots(d("2026-12-21"), "weekly", d("2026-12-22"), d("2026-12-27")).isEmpty())
    }

    @Test fun monthlyUsesOriginalDayAfterClampsAndAcrossYears() {
        val anchor = d("2026-01-31")
        assertEquals(listOf(anchor, d("2026-02-28"), d("2026-03-31"), d("2026-04-30")),
            RecurrenceCalendar.slots(anchor, "monthly", anchor, d("2026-04-30")))
        assertEquals(listOf(d("2028-01-31"), d("2028-02-29"), d("2028-03-31"), d("2028-04-30")),
            RecurrenceCalendar.slots(anchor, "monthly", d("2028-01-01"), d("2028-04-30")))
        assertEquals(listOf(d("2026-12-31"), d("2027-01-31")),
            RecurrenceCalendar.slots(anchor, "monthly", d("2026-12-01"), d("2027-01-31")))
        assertEquals(listOf(d("2026-03-31")), RecurrenceCalendar.slots(anchor, "monthly", d("2026-03-29"), d("2026-04-29")))
        assertEquals(d("2026-01-31"), anchor)
    }

    @Test fun futureEmptyAndContractYearLimitsDoNotOverflow() {
        assertTrue(RecurrenceCalendar.slots(d("2026-10-20"), "daily", d("2026-10-13"), d("2026-10-19")).isEmpty())
        assertTrue(RecurrenceCalendar.slots(d("2026-10-12"), "daily", d("2026-10-15"), d("2026-10-14")).isEmpty())
        for (frequency in listOf("daily", "weekly", "monthly")) {
            assertEquals(listOf(d("9999-12-31")), RecurrenceCalendar.slots(d("9999-12-31"), frequency, d("9999-12-31"), d("9999-12-31")))
            assertEquals(listOf(d("0001-01-01")), RecurrenceCalendar.slots(d("0001-01-01"), frequency, d("0001-01-01"), d("0001-01-01")))
        }
        assertThrows(IllegalArgumentException::class.java) { RecurrenceCalendar.slots(d("2026-01-01"), "yearly", d("2026-01-01"), d("2026-01-01")) }
        assertThrows(IllegalArgumentException::class.java) { RecurrenceCalendar.slots(LocalDate.of(10000, 1, 1), "daily", d("2026-01-01"), d("2026-01-01")) }
    }

    @Test fun longMissedRangeIsCompleteAndNonalignedWeeklyStillUsesAnchor() {
        val slots = RecurrenceCalendar.slots(d("2020-01-01"), "daily", d("2020-01-01"), d("2026-10-15"))
        assertEquals(2480, slots.size)
        assertEquals(d("2020-01-01"), slots.first()); assertEquals(d("2026-10-15"), slots.last())
        assertEquals(listOf(d("2026-10-19"), d("2026-10-26")),
            RecurrenceCalendar.slots(d("2026-10-12"), "weekly", d("2026-10-14"), d("2026-10-30")))
    }
}

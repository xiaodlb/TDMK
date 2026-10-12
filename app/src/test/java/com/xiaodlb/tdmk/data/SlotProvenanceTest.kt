package com.xiaodlb.tdmk.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SlotProvenanceTest {
    @Test fun dailyWeeklyAndMonthlyOriginalAnchorRules() {
        val monday = LocalDate.of(2026, 10, 12)
        assertTrue(SlotProvenance.matches(monday, "daily", monday.plusDays(3)))
        assertFalse(SlotProvenance.matches(monday, "daily", monday.minusDays(1)))
        assertTrue(SlotProvenance.matches(monday, "weekly", monday.plusDays(14)))
        assertFalse(SlotProvenance.matches(monday, "weekly", monday.plusDays(8)))
        val anchor = LocalDate.of(2026, 1, 31)
        listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)).forEach {
            assertTrue(SlotProvenance.matches(anchor, "monthly", it))
        }
        assertFalse(SlotProvenance.matches(anchor, "monthly", LocalDate.of(2026, 3, 28)))
        assertTrue(SlotProvenance.matches(LocalDate.of(2028, 1, 31), "monthly", LocalDate.of(2028, 2, 29)))
        assertFalse(SlotProvenance.matches(LocalDate.of(2028, 1, 31), "monthly", LocalDate.of(2028, 2, 28)))
        assertThrows(IllegalArgumentException::class.java) { SlotProvenance.matches(anchor, "yearly", anchor) }
        assertEquals(LocalDate.of(2026, 1, 31), anchor)
    }
}

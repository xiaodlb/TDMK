package com.xiaodlb.tdmk.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Inclusive nominal dates. Revision boundaries and the captured due day are supplied by storage. */
object RecurrenceCalendar {
    fun slots(anchor: LocalDate, frequency: String, from: LocalDate, through: LocalDate): List<LocalDate> {
        require(frequency in setOf("daily", "weekly", "monthly")) { "Unknown frequency" }
        require(listOf(anchor, from, through).all { it.year in 1..9999 }) { "Date outside years 0001–9999" }
        val start = maxOf(anchor, from)
        if (start > through) return emptyList()
        return buildList {
            if (frequency == "monthly") {
                var month = YearMonth.from(start)
                val last = YearMonth.from(through)
                while (month <= last) {
                    val slot = month.atDay(minOf(anchor.dayOfMonth, month.lengthOfMonth()))
                    if (slot >= start && slot <= through) add(slot)
                    if (month == last) break
                    month = month.plusMonths(1)
                }
            } else {
                val step = if (frequency == "weekly") 7L else 1L
                val remainder = ChronoUnit.DAYS.between(anchor, start) % step
                val offset = if (remainder == 0L) 0L else step - remainder
                if (ChronoUnit.DAYS.between(start, through) < offset) return@buildList
                var slot = start.plusDays(offset)
                while (slot <= through) {
                    add(slot)
                    if (ChronoUnit.DAYS.between(slot, through) < step) break
                    slot = slot.plusDays(step)
                }
            }
        }
    }
}

package com.xiaodlb.tdmk

import com.xiaodlb.tdmk.data.Task
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskDraftTest {
    private val task = Task("id", "list", "Saved", LocalDate.of(2026,10,13), "medium", null, LocalTime.of(18,0), null, null, Instant.EPOCH, Instant.EPOCH)
    @Test fun noChangeRetainsNullOrEmptyNoteAndReminderMetadata() {
        for(note in listOf(null,"")) {
            val saved = task.copy(shortNote=note)
            assertEquals(saved.content(), TaskDraft.from(saved).content(saved,setOf("list")))
        }
        assertEquals(LocalTime.of(18,0), TaskDraft.from(task).copy(date="2026-10-14").content(task,setOf("list")).reminderTime)
        val undated = TaskDraft.from(task).copy(date="").content(task,setOf("list"))
        assertNull(undated.scheduledDate); assertNull(undated.reminderTime)
        val removed = task.copy(scheduledDate=null,reminderTime=null)
        assertNull(TaskDraft.from(removed).copy(date="2026-10-15").content(removed,setOf("list")).reminderTime)
        val withNote=task.copy(shortNote="old")
        assertEquals("",TaskDraft.from(withNote).copy(note="").content(withNote,setOf("list")).shortNote)
    }
    @Test fun rejectsBlankUnicodeMalformedDateAndMissingList() {
        val draft=TaskDraft.from(task)
        for(invalid in listOf(draft.copy(title="\u0085\u00a0"),draft.copy(title="bad\uD800"), draft.copy(note="bad\uDC00"),
            draft.copy(date="2026-02-30"),draft.copy(date="0000-01-01"),draft.copy(date="10000-01-01"),draft.copy(date="2026-1-2"),draft.copy(listId="missing"),draft.copy(priority="urgent"))) {
            try { invalid.content(task,setOf("list")); fail("Accepted invalid draft") } catch(_:IllegalArgumentException) {}
        }
        assertEquals(LocalDate.of(2024,2,29),draft.copy(date="2024-02-29").content(task,setOf("list")).scheduledDate)
    }
}

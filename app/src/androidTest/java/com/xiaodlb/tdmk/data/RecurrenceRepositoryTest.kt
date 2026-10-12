package com.xiaodlb.tdmk.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecurrenceRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "recurrence-${UUID.randomUUID()}.db"
    private val provider = DatabaseProvider(context, name)
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TdmkDatabase::class.java)
    private class TestClock(var day: LocalDate) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
        override fun instant(): Instant = day.atTime(12, 0).toInstant(ZoneOffset.UTC)
    }
    @After fun cleanup() { provider.close(); context.deleteDatabase(name) }
    private fun date(value: String) = LocalDate.parse(value)

    @Test fun metadataKnownSlotsReservationsInvalidsAndFailedCursorBatchPersist() {
        val clock = TestClock(date("2028-03-31"))
        val db = provider.getDatabase()
        val repo = RecurrenceRepository(db, clock)
        val spec = RecurrenceSpec(date("2028-03-29"), "daily", "\u00A0 Daily 🌿 \u3000", shortNote = "", reminderTime = LocalTime.of(9, 5), checklist = listOf(Prototype(0, "A"), Prototype(1, "B")))
        val daily = repo.create(spec)
        val weekly = repo.create(spec.copy(anchorDate = date("2028-03-17"), frequency = "weekly", title = "Weekly"))
        val monthly = repo.create(spec.copy(anchorDate = date("2028-01-31"), frequency = "monthly", title = "Monthly"))
        val dailyTasks = repo.writeKnownBatch(daily.template.id, 1, listOf(date("2028-03-29"), date("2028-03-30"), date("2028-03-31")), clock.day)
        repo.writeKnownBatch(weekly.template.id, 1, listOf(date("2028-03-17"), date("2028-03-24"), date("2028-03-31")), clock.day)
        val monthTasks = repo.writeKnownBatch(monthly.template.id, 1, listOf(date("2028-01-31"), date("2028-02-29"), date("2028-03-31")), clock.day)
        assertEquals(3, monthTasks.size); assertEquals(date("2028-01-31"), repo.read(monthly.template.id).revisions.first().revision.anchorDate)
        assertTrue(repo.writeKnownBatch(daily.template.id, 1, listOf(clock.day), clock.day).isEmpty())
        dailyTasks.forEach { assertFalse(it.isOverride); assertEquals(1, it.sourceRevision); assertEquals(it.scheduledDate, it.slotDate); assertEquals(2, provider.getSubtaskRepository().readForParent(it.id).size) }
        val sql = db.openHelper.writableDatabase
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.tasks().insert(dailyTasks.first().copy(id = UUID.randomUUID().toString())) }
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.recurrence().exclude(RecurrenceExclusion(daily.template.id, dailyTasks.first().slotDate!!)) }
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.tasks().update(dailyTasks.first().copy(slotDate = date("2028-03-28"))) }
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.tasks().insert(dailyTasks.first().copy(id = UUID.randomUUID().toString(), sourceRevision = null)) }
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.tasks().insert(dailyTasks.first().copy(id = UUID.randomUUID().toString(), seriesId = "missing", slotDate = date("2028-03-27"))) }
        listOf(spec.copy(frequency = "yearly"), spec.copy(anchorDate = LocalDate.of(10000, 1, 1)), spec.copy(priority = "urgent"),
            spec.copy(title = "\u0085\u3000"), spec.copy(listId = "missing"), spec.copy(checklist = listOf(Prototype(1, "Wrong"))),
            spec.copy(checklist = listOf(Prototype(0, "bad\uD800"))), spec.copy(reminderTime = LocalTime.of(1, 2, 3))).forEach {
            assertThrows(IllegalArgumentException::class.java) { repo.create(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { repo.writeKnownBatch(weekly.template.id, 1, listOf(date("2028-03-18")), clock.day) }
        assertThrows(IllegalArgumentException::class.java) { repo.writeKnownBatch(daily.template.id, 999, emptyList(), clock.day) }
        assertThrows(IllegalArgumentException::class.java) { repo.writeKnownBatch(daily.template.id, 1, listOf(clock.day, clock.day), clock.day) }
        assertThrows(NoSuchElementException::class.java) { repo.read("missing") }
        assertThrows(NoSuchElementException::class.java) { repo.edit("missing", spec) }
        assertThrows(NoSuchElementException::class.java) { repo.setEnabled("missing", false) }
        assertThrows(NoSuchElementException::class.java) { repo.delete("missing") }
        assertThrows(NoSuchElementException::class.java) { repo.deleteOccurrence("missing") }
        val batch = repo.create(spec.copy(title = "Failure"))
        sql.execSQL("CREATE TRIGGER fail_batch BEFORE UPDATE ON tasks WHEN NEW.seriesId = '${batch.template.id}' AND NEW.slotDate = '2028-03-30' BEGIN SELECT RAISE(ABORT, 'injected second slot failure'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { repo.writeKnownBatch(batch.template.id, 1, listOf(date("2028-03-29"), date("2028-03-30")), date("2028-03-30")) }
        assertEquals(batch, repo.read(batch.template.id)); assertTrue(db.tasks().forSeries(batch.template.id).isEmpty())
        sql.execSQL("DROP TRIGGER fail_batch")
        val disabled = repo.setEnabled(weekly.template.id, false)
        assertEquals(weekly.revisions.first().revision.anchorDate, disabled.revisions.first().revision.anchorDate)
        assertThrows(IllegalArgumentException::class.java) { repo.writeKnownBatch(weekly.template.id, 1, emptyList(), clock.day) }
        repo.setEnabled(weekly.template.id, true)
        val snapshots = listOf(daily, weekly, monthly, batch).map { repo.read(it.template.id) }
        val rows = snapshots.associate { it.template.id to db.tasks().forSeries(it.template.id) }
        provider.close()
        val reopened = RecurrenceRepository(provider.getDatabase(), clock)
        snapshots.forEach { assertEquals(it, reopened.read(it.template.id)); assertEquals(rows[it.template.id], provider.getDatabase().tasks().forSeries(it.template.id)) }
    }

    @Test fun prospectiveRevisionsOverridesExclusionsAndSupersessionRetainProvenance() {
        val clock = TestClock(date("2026-10-14"))
        val db = provider.getDatabase(); val repo = RecurrenceRepository(db, clock)
        val tasks = provider.getTaskRepository(); val lists = provider.getListRepository()
        val work = lists.create("Work"); val personal = lists.create("Personal")
        val spec = RecurrenceSpec(date("2026-10-10"), "daily", "Original", work.id, reminderTime = LocalTime.of(9, 0))
        val series = repo.create(spec)
        val occurrences = repo.writeKnownBatch(series.template.id, 1, (10..14).map { date("2026-10-$it") }, clock.day)
        val completed = tasks.complete(occurrences[0].id); val past = occurrences[1]
        val moved = tasks.update(occurrences[2].id, occurrences[2].content().copy(listId = personal.id, scheduledDate = date("2026-10-16")))
        val undated = tasks.setScheduledDate(occurrences[3].id, null)
        assertTrue(moved.isOverride); assertEquals(date("2026-10-12"), moved.slotDate)
        assertTrue(undated.isOverride); assertNull(undated.reminderTime)
        val content = spec.copy(title = "Changed", listId = personal.id, checklist = listOf(Prototype(0, "A"), Prototype(1, "B")))
        val edited = repo.edit(series.template.id, content)
        assertEquals(2, edited.revisions.size); assertEquals(clock.day, edited.revisions[0].revision.effectiveUntil)
        assertEquals(date("2026-10-13"), edited.revisions[0].revision.reconciledThrough)
        val today = tasks.read(occurrences[4].id)
        assertEquals(occurrences[4].createdAt, today.createdAt); assertEquals("Changed", today.title); assertEquals(personal.id, today.listId)
        assertEquals(2, today.sourceRevision); assertFalse(today.isOverride)
        val firstChildren = provider.getSubtaskRepository().readForParent(today.id)
        assertEquals(2, firstChildren.size); assertTrue(firstChildren.none { it.isCompleted })
        val sameDay = repo.edit(series.template.id, content.copy(title = "Again", checklist = listOf(Prototype(0, "Fresh"))))
        assertEquals(3, sameDay.revisions.size); assertEquals(clock.day, sameDay.revisions[1].revision.effectiveFrom); assertEquals(clock.day, sameDay.revisions[1].revision.effectiveUntil)
        assertEquals(spec.anchorDate, sameDay.revisions.last().revision.anchorDate)
        assertTrue(provider.getSubtaskRepository().readForParent(today.id).none { row -> firstChildren.any { it.id == row.id } })
        assertEquals(completed, tasks.read(completed.id)); assertEquals(past, tasks.read(past.id)); assertEquals(moved, tasks.read(moved.id)); assertEquals(undated, tasks.read(undated.id))
        val schedule = repo.edit(series.template.id, content.copy(anchorDate = date("2026-10-16"), frequency = "weekly", title = "Weekly"))
        assertEquals(4, schedule.revisions.size)
        assertThrows(NoSuchElementException::class.java) { tasks.read(today.id) }; assertTrue(repo.exclusions(series.template.id).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { repo.edit(series.template.id, content.copy(anchorDate = date("2026-10-13"), frequency = "weekly")) }
        clock.day = date("2026-10-16")
        val fresh = repo.writeKnownBatch(series.template.id, 4, listOf(clock.day), clock.day).single()
        assertEquals(moved.scheduledDate, fresh.scheduledDate); assertNotEquals(moved.slotDate, fresh.slotDate)
        tasks.reopen(completed.id); assertTrue(tasks.read(completed.id).isOverride)
        val todaySeries = repo.create(RecurrenceSpec(clock.day, "daily", "Completed today"))
        val old = repo.writeKnownBatch(todaySeries.template.id, 1, listOf(clock.day), clock.day).single()
        tasks.complete(old.id)
        repo.edit(todaySeries.template.id, RecurrenceSpec(clock.day, "daily", "New"))
        assertEquals(1, tasks.read(old.id).sourceRevision) // Preserved slot lies outside now-empty revision1 cutoff.
        tasks.delete(old.id)
        assertEquals(listOf(RecurrenceExclusion(todaySeries.template.id, clock.day)), repo.exclusions(todaySeries.template.id))
        assertTrue(repo.writeKnownBatch(todaySeries.template.id, 2, listOf(clock.day), clock.day).isEmpty())
        val before = repo.read(series.template.id); val beforeRows = db.tasks().forSeries(series.template.id)
        repo.setEnabled(series.template.id, false)
        assertEquals(beforeRows, db.tasks().forSeries(series.template.id)); assertEquals(before.revisions, repo.read(series.template.id).revisions)
        provider.close(); assertEquals(beforeRows, provider.getDatabase().tasks().forSeries(series.template.id))
    }

    @Test fun futureStartEditsUseCreationBoundaryWithoutCreatingEarlySlots() {
        val clock = TestClock(date("2026-10-13")); val repo = RecurrenceRepository(provider.getDatabase(), clock)
        val spec = RecurrenceSpec(date("2026-10-20"), "daily", "Future")
        val created = repo.create(spec)
        assertEquals(clock.day, created.revisions.first().revision.effectiveFrom)
        assertEquals(spec.anchorDate, created.revisions.first().revision.anchorDate)
        clock.day = date("2026-10-14")
        val edited = repo.edit(created.template.id, spec.copy(title = "Edited before start"))
        assertEquals(2, edited.revisions.size); assertEquals(clock.day, edited.revisions.last().revision.effectiveFrom)
        assertEquals(spec.anchorDate, edited.revisions.last().revision.anchorDate)
        assertThrows(IllegalArgumentException::class.java) { repo.writeKnownBatch(created.template.id, 2, listOf(clock.day), clock.day) }
        assertTrue(provider.getDatabase().tasks().forSeries(created.template.id).isEmpty())
        val changed = repo.edit(created.template.id, spec.copy(anchorDate = clock.day, title = "Start today"))
        assertEquals(3, changed.revisions.size)
        val task = repo.writeKnownBatch(created.template.id, 3, listOf(clock.day), clock.day).single()
        provider.close(); assertEquals(task, provider.getTaskRepository().read(task.id))
        assertEquals(changed.revisions.map { it.revision.anchorDate }, provider.getRecurrenceRepository().read(created.template.id).revisions.map { it.revision.anchorDate })
    }

    @Test fun allMutationPathsAndOwnedExternalListCascadesRollbackAtomically() {
        val clock = TestClock(date("2026-10-14")); val db = provider.getDatabase(); val repo = RecurrenceRepository(db, clock)
        val lists = provider.getListRepository(); val tasks = provider.getTaskRepository(); val children = provider.getSubtaskRepository()
        val work = lists.create("Work"); val personal = lists.create("Personal")
        val note = provider.getNoteRepository().create("Keep", RichBodyCodec.encode(RichBody(emptyList())))!!
        val owned = repo.create(RecurrenceSpec(date("2026-10-03"), "daily", "Owned", work.id, checklist = listOf(Prototype(0, "A"), Prototype(1, "B"))))
        val occurrences = repo.writeKnownBatch(owned.template.id, 1, (3..9).map { LocalDate.of(2026, 10, it) }, date("2026-10-09"))
        tasks.update(occurrences[0].id, occurrences[0].content().copy(listId = personal.id))
        children.edit(children.readForParent(occurrences[1].id).first().id, "Edited")
        children.reorder(occurrences[2].id, children.readForParent(occurrences[2].id).map { it.id }.reversed())
        children.setChecked(children.readForParent(occurrences[3].id).first().id, true)
        children.delete(children.readForParent(occurrences[4].id).first().id)
        children.readForParent(occurrences[5].id).forEach { children.setChecked(it.id, true) }; tasks.complete(occurrences[5].id)
        children.create(occurrences[5].id, "Unchecked"); assertNull(tasks.read(occurrences[5].id).completedAt)
        children.readForParent(occurrences[6].id).forEach { children.setChecked(it.id, true) }; tasks.complete(occurrences[6].id)
        children.setChecked(children.readForParent(occurrences[6].id).first().id, false); assertNull(tasks.read(occurrences[6].id).completedAt)
        occurrences.forEach { assertTrue(tasks.read(it.id).isOverride); assertEquals(it.slotDate, tasks.read(it.id).slotDate) }
        val external = repo.create(RecurrenceSpec(date("2026-10-13"), "daily", "External", personal.id))
        val externalRows = repo.writeKnownBatch(external.template.id, 1, listOf(date("2026-10-13"), clock.day), clock.day)
        val movedExternal = tasks.update(externalRows[0].id, externalRows[0].content().copy(listId = work.id))
        val beforeLists = lists.readAll(); val beforeSeries = repo.read(owned.template.id); val beforeExternal = repo.read(external.template.id)
        val beforeRows = db.tasks().forSeries(owned.template.id); val beforeChildren = beforeRows.associate { it.id to children.readForParent(it.id) }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_series_list BEFORE DELETE ON lists WHEN OLD.id = '${work.id}' BEGIN SELECT RAISE(ABORT, 'injected after series and exclusion changes'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { lists.delete(work.id) }
        assertEquals(beforeLists, lists.readAll()); assertEquals(beforeSeries, repo.read(owned.template.id)); assertEquals(beforeExternal, repo.read(external.template.id))
        assertEquals(beforeRows, db.tasks().forSeries(owned.template.id)); beforeRows.forEach { assertEquals(beforeChildren[it.id], children.readForParent(it.id)) }
        assertEquals(movedExternal, tasks.read(movedExternal.id)); assertTrue(repo.exclusions(external.template.id).isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_series_list")
        lists.delete(work.id)
        assertThrows(NoSuchElementException::class.java) { repo.read(owned.template.id) }
        beforeRows.forEach { assertThrows(NoSuchElementException::class.java) { tasks.read(it.id) } }
        assertThrows(NoSuchElementException::class.java) { tasks.read(movedExternal.id) }
        assertEquals(listOf(RecurrenceExclusion(external.template.id, movedExternal.slotDate!!)), repo.exclusions(external.template.id))
        assertEquals(externalRows[1], tasks.read(externalRows[1].id)); assertEquals(note, provider.getNoteRepository().read(note.id))
        assertEquals(1L, lists.read(personal.id).order)
        val default = lists.initialize(); lists.rename(default.id, "General")
        assertThrows(IllegalArgumentException::class.java) { lists.delete(default.id) }
        repo.delete(external.template.id)
        assertThrows(NoSuchElementException::class.java) { tasks.read(externalRows[1].id) }
        assertTrue(db.recurrence().exclusions(external.template.id).isEmpty())
        provider.close(); assertEquals(note, provider.getNoteRepository().read(note.id))
    }

    @Test fun exportedSchemaFiveMigrationPreservesAllFieldsAndFurtherRecurrenceWrites() {
        val listId = UUID.randomUUID().toString(); val taskId = UUID.randomUUID().toString(); val childId = UUID.randomUUID().toString(); val noteId = UUID.randomUUID().toString()
        val completion = Instant.parse("2026-10-12T07:00:00Z").toEpochMilli()
        val rich = RichBody(listOf(RichBlock("bullet", null, listOf(RichRun("план 🌿", true)))))
        val raw = " \n" + RichBodyCodec.encode(rich) + "\n "
        migration.createDatabase(name, 5).apply {
            execSQL("INSERT INTO settings VALUES(1,'dark')")
            execSQL("INSERT INTO lists VALUES('$listId','General',1,0,1000,2000)")
            execSQL("INSERT INTO tasks VALUES('$taskId','$listId','Completed','2026-10-12','high','note','09:05',$completion,'2026-10-12',3000,$completion)")
            execSQL("INSERT INTO subtasks VALUES('$childId','$taskId','Checked',0,1)")
            execSQL("INSERT INTO notes VALUES(?,?,?,?,?,?)", arrayOf<Any>(noteId, "Note", raw, "план 🌿", 4000L, 5000L))
            close()
        }
        migration.runMigrationsAndValidate(name, 6, true, RecurrenceSchema.migration5To6).close()
        val expectedTask = Task(taskId, listId, "Completed", date("2026-10-12"), "high", "note", LocalTime.of(9, 5), Instant.ofEpochMilli(completion), date("2026-10-12"), Instant.ofEpochMilli(3000), Instant.ofEpochMilli(completion))
        val expectedList = TaskList(listId,"General",true,0,1000,2000); val expectedChild = Subtask(childId,taskId,"Checked",0,true)
        val expectedNote = Note(noteId,"Note",rich,"план 🌿",Instant.ofEpochMilli(4000),Instant.ofEpochMilli(5000))
        assertEquals(expectedTask, provider.getTaskRepository().read(taskId)); assertEquals(expectedChild, provider.getSubtaskRepository().read(childId))
        assertEquals(expectedList, provider.getListRepository().initialize()); assertEquals(expectedNote, provider.getNoteRepository().read(noteId)); assertEquals(raw, provider.getDatabase().notes().find(noteId)!!.body)
        val clock = TestClock(date("2026-10-14")); val repo = RecurrenceRepository(provider.getDatabase(), clock)
        val series = repo.create(RecurrenceSpec(clock.day, "daily", "After upgrade", checklist = listOf(Prototype(0,"Fresh"))))
        val occurrence = repo.writeKnownBatch(series.template.id,1,listOf(clock.day),clock.day).single()
        provider.close()
        assertEquals(occurrence, provider.getTaskRepository().read(occurrence.id)); assertEquals(1, provider.getSubtaskRepository().readForParent(occurrence.id).size)
        assertEquals(expectedTask, provider.getTaskRepository().read(taskId)); assertEquals(expectedChild, provider.getSubtaskRepository().read(childId)); assertEquals(expectedList,provider.getListRepository().initialize())
        assertEquals(expectedNote,provider.getNoteRepository().read(noteId)); assertEquals(raw,provider.getDatabase().notes().find(noteId)!!.body); assertEquals(Settings(1,Appearance.DARK),provider.getDatabase().settings().read())
        assertEquals(6,provider.getDatabase().openHelper.readableDatabase.version)
    }
}

package com.xiaodlb.tdmk.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RecurrenceReconciliationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "reconcile-${UUID.randomUUID()}.db"
    private val provider = DatabaseProvider(context, name)
    private class TestClock(var day: LocalDate) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
        override fun instant(): Instant = day.atTime(12, 0).toInstant(ZoneOffset.UTC)
    }
    private fun d(value: String) = LocalDate.parse(value)
    @After fun cleanup() { provider.close(); context.deleteDatabase(name) }

    @Test fun missedDaysCopyFreshMetadataPreservePredecessorsAndCalendarClampsAfterReopen() {
        val clock = TestClock(d("2026-10-12"))
        var db = provider.getDatabase(); var repo = RecurrenceRepository(db, clock)
        val work = provider.getListRepository().create("Work")
        val spec = RecurrenceSpec(clock.day, "daily", "Daily", work.id, "high", "Note 🌿", LocalTime.of(9, 5), listOf(Prototype(0,"A"),Prototype(1,"B")))
        val id = repo.create(spec).template.id
        assertEquals(1, repo.reconcile(id))
        val first = db.tasks().forSeries(id).single()
        val checked = provider.getSubtaskRepository().setChecked(provider.getSubtaskRepository().readForParent(first.id)[0].id, true)
        val predecessor = provider.getTaskRepository().read(first.id)
        provider.close(); clock.day = d("2026-10-15")
        db = provider.getDatabase(); repo = RecurrenceRepository(db, clock)
        assertEquals(3, repo.reconcile(id))
        val rows = db.tasks().forSeries(id).sortedBy { it.slotDate }
        assertEquals((12..15).map { d("2026-10-$it") }, rows.map { it.slotDate })
        assertEquals(predecessor, rows.first()); assertEquals(checked, provider.getSubtaskRepository().read(checked.id))
        assertEquals(listOf(predecessor), TaskViews(clock).overdue(listOf(predecessor)))
        val allChildIds = mutableSetOf<String>()
        rows.drop(1).forEach { task ->
            assertEquals(spec.title, task.title); assertEquals(spec.listId, task.listId); assertEquals(spec.priority, task.priority)
            assertEquals(spec.shortNote, task.shortNote); assertEquals(spec.reminderTime, task.reminderTime)
            assertEquals(task.slotDate, task.scheduledDate); assertEquals(1, task.sourceRevision)
            assertFalse(task.isOverride); assertNull(task.completedAt); assertNull(task.completionDate)
            val children = provider.getSubtaskRepository().readForParent(task.id)
            assertEquals(listOf("A","B"), children.map { it.text }); assertTrue(children.none { it.isCompleted })
            children.forEach { assertTrue(allChildIds.add(it.id)); assertNotEquals(checked.id, it.id) }
        }
        val previousCompleted = rows[1]
        provider.getSubtaskRepository().readForParent(previousCompleted.id).forEach { provider.getSubtaskRepository().setChecked(it.id,true) }
        val completed = provider.getTaskRepository().complete(previousCompleted.id)
        val snapshot = db.tasks().forSeries(id).associateBy { it.id }
        assertEquals(0, repo.reconcile(id)); assertEquals(snapshot, db.tasks().forSeries(id).associateBy { it.id })
        clock.day = d("2026-10-13"); assertEquals(0, repo.reconcile(id))
        assertEquals(d("2026-10-15"), repo.read(id).revisions.single().revision.reconciledThrough)
        clock.day = d("2026-10-19"); assertEquals(4, repo.reconcile(id)); assertEquals(0, repo.reconcile(id))
        assertEquals(snapshot, db.tasks().forSeries(id).filter { it.id in snapshot }.associateBy { it.id })
        assertEquals(completed, provider.getTaskRepository().read(completed.id))
        val latest = db.tasks().forSlot(id,clock.day)!!
        assertNull(latest.completedAt); assertNull(latest.completionDate)
        assertTrue(provider.getSubtaskRepository().readForParent(latest.id).none { it.isCompleted })

        clock.day = d("2028-04-30")
        val monthly = repo.create(spec.copy(anchorDate=d("2028-01-31"),frequency="monthly",title="Monthly")).template.id
        val weekly = repo.create(spec.copy(anchorDate=d("2028-04-10"),frequency="weekly",title="Weekly")).template.id
        assertEquals(4, repo.reconcile(monthly)); assertEquals(3, repo.reconcile(weekly))
        assertEquals(listOf(d("2028-01-31"),d("2028-02-29"),d("2028-03-31"),d("2028-04-30")), db.tasks().forSeries(monthly).map { it.slotDate!! }.sorted())
        assertEquals(listOf(d("2028-04-10"),d("2028-04-17"),d("2028-04-24")), db.tasks().forSeries(weekly).map { it.slotDate!! }.sorted())
        assertEquals(clock.day, repo.read(weekly).revisions.single().revision.reconciledThrough) // Nonslot Apr30 processed.
        val calendarRows = listOf(monthly,weekly).associateWith { db.tasks().forSeries(it).associateBy { row -> row.id } }
        provider.close(); repo = RecurrenceRepository(provider.getDatabase(), clock)
        calendarRows.forEach { (series, expected) -> assertEquals(0,repo.reconcile(series)); assertEquals(expected,provider.getDatabase().tasks().forSeries(series).associateBy { it.id }) }
        clock.day=d("2028-05-01")
        val captured = repo.create(spec.copy(anchorDate=clock.day,title="Captured day")).template.id
        var clockReads=0
        val movingClock=object : Clock() {
            override fun getZone(): ZoneId=ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock=Clock.fixed(instant(),zone)
            override fun instant(): Instant=(if(clockReads++ == 0) d("2028-05-01") else d("2028-05-04")).atTime(12,0).toInstant(ZoneOffset.UTC)
        }
        assertEquals(1,RecurrenceRepository(provider.getDatabase(),movingClock).reconcile(captured))
        assertEquals(listOf(d("2028-05-01")),provider.getDatabase().tasks().forSeries(captured).map { it.slotDate })
    }

    @Test fun retainedRevisionIntervalsResetReplayAndOriginalReservationsStayIndependent() {
        val clock = TestClock(d("2026-10-12")); val db = provider.getDatabase(); val repo = RecurrenceRepository(db,clock)
        val lists = provider.getListRepository(); val tasks = provider.getTaskRepository()
        val work = lists.create("Work"); val personal = lists.create("Personal")
        val spec = RecurrenceSpec(d("2026-10-10"),"daily","Old",work.id,reminderTime=LocalTime.of(9,0))
        val id = repo.create(spec).template.id
        assertEquals(3,repo.reconcile(id))
        val old = db.tasks().forSeries(id).associateBy { it.slotDate }
        val pastUnfinished = old.getValue(d("2026-10-11"))
        val moved = tasks.update(old.getValue(d("2026-10-12")).id, old.getValue(d("2026-10-12")).content().copy(scheduledDate=d("2026-10-21"),listId=personal.id))
        val history = tasks.complete(old.getValue(d("2026-10-10")).id)
        clock.day=d("2026-10-13"); assertEquals(1,repo.reconcile(id))
        val undated = tasks.setScheduledDate(db.tasks().forSlot(id,clock.day)!!.id,null)
        clock.day=d("2026-10-14"); assertEquals(1,repo.reconcile(id))
        val completed = tasks.complete(db.tasks().forSlot(id,clock.day)!!.id)
        val next = spec.copy(anchorDate=clock.day,frequency="weekly",title="New",listId=personal.id,priority="low",shortNote="New note",checklist=listOf(Prototype(0,"Fresh")))
        repo.edit(id,next); repo.edit(id,next.copy(title="Latest")) // Revision2 is empty on same day.
        assertEquals(0,repo.reconcile(id)) // Completed original14 reserves it across revised schedule.
        tasks.delete(undated.id); assertEquals(listOf(RecurrenceExclusion(id,d("2026-10-13"))),repo.exclusions(id))
        clock.day=d("2026-10-28"); assertEquals(2,repo.reconcile(id))
        val generated = db.tasks().forSlot(id,d("2026-10-21"))!!
        assertEquals(moved.scheduledDate,generated.scheduledDate); assertNotEquals(moved.slotDate,generated.slotDate)
        assertEquals("Latest",generated.title); assertEquals(personal.id,generated.listId); assertEquals(3,generated.sourceRevision)
        assertEquals("low",generated.priority); assertEquals("New note",generated.shortNote)
        assertEquals(listOf("Fresh"),provider.getSubtaskRepository().readForParent(generated.id).map { it.text })
        listOf(pastUnfinished,moved,history,completed).forEach { assertEquals(it,tasks.read(it.id)) }
        val before = db.tasks().forSeries(id).associateBy { it.id }
        db.openHelper.writableDatabase.execSQL("UPDATE template_revisions SET reconciledThrough=NULL WHERE seriesId='$id'") // Restore replay fixture; no restore feature.
        provider.close(); val reopened=RecurrenceRepository(provider.getDatabase(),clock)
        assertEquals(0,reopened.reconcile(id)); assertEquals(before,provider.getDatabase().tasks().forSeries(id).associateBy { it.id })
        assertEquals(listOf(RecurrenceExclusion(id,d("2026-10-13"))),reopened.exclusions(id))
        assertNull(reopened.read(id).revisions[1].revision.reconciledThrough)
        assertEquals(d("2026-10-13"),reopened.read(id).revisions[0].revision.reconciledThrough)
        assertEquals(clock.day,reopened.read(id).revisions.last().revision.reconciledThrough)
    }

    @Test fun futureStartDisabledGapsAndDeletedSeriesListsCannotRecreateStorage() {
        val clock=TestClock(d("2026-10-13")); val db=provider.getDatabase(); val repo=RecurrenceRepository(db,clock)
        val work=provider.getListRepository().create("Work")
        val spec=RecurrenceSpec(d("2026-10-20"),"daily","Future",work.id)
        val future=repo.create(spec).template.id; val changed=repo.create(spec.copy(title="Changed")).template.id
        assertEquals(0,repo.reconcile()); assertTrue(db.tasks().forSeries(future).isEmpty())
        assertEquals(clock.day,repo.read(future).revisions.single().revision.reconciledThrough)
        clock.day=d("2026-10-14"); repo.edit(future,spec.copy(title="Future title"))
        repo.edit(changed,spec.copy(title="Changed title")); repo.edit(changed,spec.copy(anchorDate=clock.day,title="Now"))
        assertEquals(1,repo.reconcile()); assertEquals(listOf(clock.day),db.tasks().forSeries(changed).map { it.slotDate })
        val first=db.tasks().forSeries(changed).single(); repo.setEnabled(changed,false)
        clock.day=d("2026-10-20"); assertEquals(0,repo.reconcile(changed)); assertEquals(first,db.tasks().forSeries(changed).single())
        assertEquals(d("2026-10-14"),repo.read(changed).revisions.last().revision.reconciledThrough)
        repo.setEnabled(changed,true); assertEquals(6,repo.reconcile(changed)); assertEquals(1,repo.reconcile(future))
        assertEquals(listOf(d("2026-10-20")),db.tasks().forSeries(future).map { it.slotDate })
        assertEquals("Future title",db.tasks().forSeries(future).single().title)
        repo.delete(future); assertEquals(0,repo.reconcile(future))
        provider.getListRepository().delete(work.id); assertEquals(0,repo.reconcile(changed)); assertEquals(0,repo.reconcile())
        clock.day=d("2026-11-20"); provider.close()
        val reopened=RecurrenceRepository(provider.getDatabase(),clock)
        assertEquals(0,reopened.reconcile()); assertTrue(provider.getDatabase().recurrence().ids().isEmpty())
        assertTrue(provider.getDatabase().tasks().forSeries(changed).isEmpty())
        assertEquals(6,provider.getDatabase().openHelper.writableDatabase.version)
    }

    @Test fun failedLaterBatchKeepsEarlierProgressAndConcurrentRetriesCompleteFullRange() {
        val clock=TestClock(d("2022-01-03")); val db=provider.getDatabase(); val repo=RecurrenceRepository(db,clock)
        val id=repo.create(RecurrenceSpec(d("2020-01-01"),"daily","Long gap",checklist=listOf(Prototype(0,"Fresh")))).template.id
        val sql=db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_reconcile BEFORE UPDATE ON tasks WHEN NEW.seriesId='$id' AND NEW.slotDate='2021-01-05' BEGIN SELECT RAISE(ABORT,'injected later batch failure'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { repo.reconcile(id) }
        val committed=db.tasks().forSeries(id).associateBy { it.id }
        assertEquals(366,committed.size); assertEquals(d("2020-12-31"),repo.read(id).revisions.single().revision.reconciledThrough)
        assertNull(db.tasks().forSlot(id,d("2021-01-01")))
        val committedChildren=committed.keys.associateWith { provider.getSubtaskRepository().readForParent(it) }
        assertEquals(366,committedChildren.values.sumOf { it.size })
        assertThrows(android.database.sqlite.SQLiteException::class.java) { repo.reconcile(id) }
        assertEquals(committed,db.tasks().forSeries(id).associateBy { it.id })
        committedChildren.forEach { (task,children) -> assertEquals(children,provider.getSubtaskRepository().readForParent(task)) }
        sql.execSQL("DROP TRIGGER fail_reconcile")
        val pool=Executors.newFixedThreadPool(2); val start=CountDownLatch(1)
        try {
            val futures=(1..2).map { pool.submit(Callable { start.await(); RecurrenceRepository(db,clock).reconcile(id) }) }
            start.countDown(); assertEquals(368,futures.sumOf { it.get(30,TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
        val complete=db.tasks().forSeries(id).associateBy { it.id }
        assertEquals(734,complete.size); assertEquals(734,complete.values.map { it.slotDate }.distinct().size)
        assertEquals(d("2020-01-01"),complete.values.minOf { it.slotDate!! }); assertEquals(clock.day,complete.values.maxOf { it.slotDate!! })
        assertEquals(clock.day,repo.read(id).revisions.single().revision.reconciledThrough)
        assertEquals(committed,complete.filterKeys { it in committed })
        val children=complete.keys.associateWith { provider.getSubtaskRepository().readForParent(it) }
        assertEquals(734,children.values.flatten().map { it.id }.distinct().size); assertTrue(children.values.flatten().none { it.isCompleted })
        provider.close(); val reopened=RecurrenceRepository(provider.getDatabase(),clock)
        assertEquals(0,reopened.reconcile(id)); assertEquals(complete,provider.getDatabase().tasks().forSeries(id).associateBy { it.id })
        children.forEach { (task,expected) -> assertEquals(expected,provider.getSubtaskRepository().readForParent(task)) }
    }
}

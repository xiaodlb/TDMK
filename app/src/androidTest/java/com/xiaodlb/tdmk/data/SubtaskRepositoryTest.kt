package com.xiaodlb.tdmk.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SubtaskRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "subtasks-${UUID.randomUUID()}.db"
    private val provider = DatabaseProvider(context, name)
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TdmkDatabase::class.java)
    @After fun cleanup() { provider.close(); context.deleteDatabase(name) }

    @Test
    fun checklistIdentityTextOrderChecksPersistAndInvalidOperationsDoNotWrite() {
        val tasks = provider.getTaskRepository()
        val children = provider.getSubtaskRepository()
        val parent = tasks.create(TaskContent("Parent"))
        val other = tasks.create(TaskContent("Other"))
        val foreign = children.create(other.id, "Other child")
        val first = children.create(parent.id, "\u0085 A  🌿 \u3000")
        val second = children.create(parent.id, "B")
        val third = children.create(parent.id, "C")
        assertFalse(first.isCompleted); assertEquals("A  🌿", first.text)
        assertEquals(0L, first.order)
        val checked = children.setChecked(second.id, true)
        val edited = children.edit(first.id, "Edited")
        assertEquals(first.id, edited.id); assertTrue(checked.isCompleted)
        val reordered = children.reorder(parent.id, listOf(third.id, first.id, second.id))
        assertEquals(listOf(0L, 1L, 2L), reordered.map { it.order })
        assertEquals(listOf(foreign), children.readForParent(other.id))
        provider.close()
        val reopened = provider.getSubtaskRepository()
        assertEquals(reordered, reopened.readForParent(parent.id))
        listOf(listOf(first.id, first.id, second.id), listOf(first.id, second.id),
            listOf(first.id, second.id, "unknown"), listOf(first.id, second.id, foreign.id)).forEach {
            assertThrows(IllegalArgumentException::class.java) { reopened.reorder(parent.id, it) }
            assertEquals(reordered, reopened.readForParent(parent.id))
        }
        listOf("", "\u0085\u2007\u202F", "bad\uD800", "bad\uDC00").forEach {
            assertThrows(IllegalArgumentException::class.java) { reopened.create(parent.id, it) }
            assertThrows(IllegalArgumentException::class.java) { reopened.edit(first.id, it) }
            assertEquals(reordered, reopened.readForParent(parent.id))
        }
        assertThrows(NoSuchElementException::class.java) { reopened.create("unknown", "Valid") }
        assertThrows(NoSuchElementException::class.java) { reopened.readForParent("unknown") }
        assertThrows(NoSuchElementException::class.java) { reopened.reorder("unknown", emptyList()) }
        assertThrows(NoSuchElementException::class.java) { reopened.read("unknown") }
        assertThrows(NoSuchElementException::class.java) { reopened.edit("unknown", "Valid") }
        assertThrows(NoSuchElementException::class.java) { reopened.setChecked("unknown", true) }
        assertThrows(NoSuchElementException::class.java) { reopened.delete("unknown") }
        val sql = provider.getDatabase().openHelper.writableDatabase
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            sql.execSQL("INSERT INTO subtasks VALUES ('duplicate-order', '${parent.id}', 'Invalid', 0, 0)")
        }
        val compacted = reopened.delete(first.id)
        assertEquals(listOf(third.id, second.id), compacted.map { it.id }); assertEquals(listOf(0L, 1L), compacted.map { it.order })
        val appended = reopened.create(parent.id, "D")
        assertEquals(2L, appended.order)
        val final = reopened.readForParent(parent.id)
        val ids = provider.getListRepository().readAll().map { it.id } + listOf(parent.id, other.id) + final.map { it.id } + foreign.id
        assertEquals(ids.size, ids.toSet().size); ids.forEach { assertEquals(4, UUID.fromString(it).version()) }
        provider.close(); assertEquals(final, provider.getSubtaskRepository().readForParent(parent.id))
    }

    @Test
    fun everyCompletionPathGuardsChecklistAndObserversReceiveAtomicReopens(): Unit = runBlocking {
        val tasks = provider.getTaskRepository()
        val children = provider.getSubtaskRepository()
        val parent = tasks.create(TaskContent("Parent", scheduledDate = LocalDate.of(2026, 10, 12), reminderTime = LocalTime.of(9, 5)))
        assertNotNull(tasks.complete(parent.id).completedAt) // No children permits explicit completion.
        val active = Channel<List<Task>>(Channel.UNLIMITED)
        val completed = Channel<List<Task>>(Channel.UNLIMITED)
        val a = launch(Dispatchers.IO) { tasks.active().collect { active.send(it) } }
        val c = launch(Dispatchers.IO) { tasks.completed().collect { completed.send(it) } }
        try {
            awaitRows(completed) { it.any { row -> row.id == parent.id } }
            val one = children.create(parent.id, "One")
            val afterAdd = tasks.read(parent.id)
            assertNull(afterAdd.completedAt); assertNull(afterAdd.completionDate)
            assertEquals(parent.scheduledDate, afterAdd.scheduledDate); assertEquals(parent.reminderTime, afterAdd.reminderTime)
            awaitRows(active) { it.any { row -> row.id == parent.id } }; awaitRows(completed) { it.isEmpty() }
            val two = children.create(parent.id, "Two")
            children.setChecked(one.id, true)
            assertThrows(IllegalArgumentException::class.java) { tasks.complete(parent.id) }
            assertThrows(IllegalArgumentException::class.java) {
                tasks.setCompletion(parent.id, Instant.ofEpochMilli(1800000000000), LocalDate.of(2026, 10, 12))
            }
            assertFalse(children.read(two.id).isCompleted); assertNull(tasks.read(parent.id).completedAt)
            children.setChecked(two.id, true)
            assertNull(tasks.read(parent.id).completedAt) // Last check never auto-completes.
            val done = tasks.complete(parent.id)
            awaitRows(completed) { it.any { row -> row.id == parent.id } }
            children.edit(one.id, "Edited")
            assertEquals(done, tasks.read(parent.id)) // Unrelated text edit retains captured completion day.
            children.setChecked(one.id, false)
            val reopened = tasks.read(parent.id)
            assertNull(reopened.completedAt); assertNull(reopened.completionDate)
            assertEquals(parent.scheduledDate, reopened.scheduledDate); assertEquals(parent.reminderTime, reopened.reminderTime)
            assertTrue(children.read(two.id).isCompleted)
            awaitRows(active) { it.any { row -> row.id == parent.id } }; awaitRows(completed) { it.isEmpty() }
            children.delete(one.id)
            assertNull(tasks.read(parent.id).completedAt) // Deleting last unchecked never auto-completes.
            val again = tasks.complete(parent.id)
            children.delete(two.id)
            assertEquals(again, tasks.read(parent.id)) // Deletion preserves completed state/day.
            tasks.reopen(parent.id)
            assertEquals(parent.scheduledDate, tasks.read(parent.id).scheduledDate)
            val last = children.create(parent.id, "Checked")
            children.setChecked(last.id, true); tasks.complete(parent.id); tasks.reopen(parent.id)
            assertTrue(children.read(last.id).isCompleted)
        } finally { a.cancelAndJoin(); c.cancelAndJoin(); active.close(); completed.close() }
    }

    private suspend fun awaitRows(channel: Channel<List<Task>>, predicate: (List<Task>) -> Boolean) {
        withTimeout(10000) { while (!predicate(channel.receive())) { } }
    }

    @Test
    fun parentListCascadesAndInjectedReopenDeletionFailuresRollBack() {
        val lists = provider.getListRepository()
        val tasks = provider.getTaskRepository()
        val children = provider.getSubtaskRepository()
        val custom = lists.create("Work")
        val parent = tasks.create(TaskContent("Parent", listId = custom.id))
        val child = children.create(parent.id, "Checked")
        children.setChecked(child.id, true)
        val done = tasks.complete(parent.id)
        val sibling = tasks.create(TaskContent("Sibling", listId = custom.id))
        val siblingChild = children.create(sibling.id, "Sibling child")
        val unrelated = tasks.create(TaskContent("Unrelated"))
        val unrelatedChild = children.create(unrelated.id, "Keep")
        val beforeLists = lists.readAll()
        val beforeChildren = children.readForParent(parent.id)
        val sql = provider.getDatabase().openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_reopen BEFORE UPDATE ON tasks WHEN NEW.id = '${parent.id}' BEGIN SELECT RAISE(ABORT, 'injected reopen failure'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { children.setChecked(child.id, false) }
        assertThrows(android.database.sqlite.SQLiteException::class.java) { children.create(parent.id, "Unchecked") }
        assertEquals(done, tasks.read(parent.id)); assertEquals(beforeChildren, children.readForParent(parent.id))
        sql.execSQL("DROP TRIGGER fail_reopen")
        sql.execSQL("CREATE TRIGGER fail_list BEFORE DELETE ON lists WHEN OLD.id = '${custom.id}' BEGIN SELECT RAISE(ABORT, 'injected list failure after cascades'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { lists.delete(custom.id) }
        assertEquals(beforeLists, lists.readAll()); assertEquals(done, tasks.read(parent.id))
        assertEquals(beforeChildren, children.readForParent(parent.id)); assertEquals(siblingChild, children.read(siblingChild.id))
        sql.execSQL("DROP TRIGGER fail_list")
        tasks.delete(parent.id)
        assertThrows(NoSuchElementException::class.java) { children.read(child.id) }
        assertEquals(siblingChild, children.read(siblingChild.id))
        lists.delete(custom.id)
        assertThrows(NoSuchElementException::class.java) { children.read(siblingChild.id) }
        assertEquals(unrelatedChild, children.read(unrelatedChild.id))
        provider.close(); assertEquals(unrelatedChild, provider.getSubtaskRepository().read(unrelatedChild.id))
    }

    @Test
    fun exportedSchemaThreeMigrationPreservesEveryOldRowAndFurtherChecklistWrites() {
        val listId = UUID.randomUUID().toString()
        val datedId = UUID.randomUUID().toString()
        val undatedId = UUID.randomUUID().toString()
        val doneId = UUID.randomUUID().toString()
        migration.createDatabase(name, 3).apply {
            execSQL("INSERT INTO settings VALUES (1, 'dark')")
            execSQL("INSERT INTO lists VALUES ('$listId', 'General', 1, 0, 1000, 2000)")
            execSQL("INSERT INTO tasks VALUES ('$datedId', '$listId', 'Dated', '2026-10-12', 'high', '', '09:05', NULL, NULL, 3000, 4000)")
            execSQL("INSERT INTO tasks VALUES ('$undatedId', '$listId', 'Undated', NULL, 'medium', NULL, NULL, NULL, NULL, 5000, 6000)")
            execSQL("INSERT INTO tasks VALUES ('$doneId', '$listId', 'Completed', '2026-10-11', 'low', 'note', NULL, 7000, '2026-10-12', 8000, 9000)")
            close()
        }
        migration.runMigrationsAndValidate(name, 4, true, SubtaskSchema.migration3To4).close()
        val tasks = provider.getTaskRepository()
        val expectedDated = Task(datedId, listId, "Dated", LocalDate.of(2026, 10, 12), "high", "", LocalTime.of(9, 5), null, null, Instant.ofEpochMilli(3000), Instant.ofEpochMilli(4000))
        val expectedUndated = Task(undatedId, listId, "Undated", null, "medium", null, null, null, null, Instant.ofEpochMilli(5000), Instant.ofEpochMilli(6000))
        val expectedDone = Task(doneId, listId, "Completed", LocalDate.of(2026, 10, 11), "low", "note", null, Instant.ofEpochMilli(7000), LocalDate.of(2026, 10, 12), Instant.ofEpochMilli(8000), Instant.ofEpochMilli(9000))
        assertEquals(expectedDated, tasks.read(datedId)); assertEquals(expectedUndated, tasks.read(undatedId)); assertEquals(expectedDone, tasks.read(doneId))
        val expectedList = TaskList(listId, "General", true, 0, 1000, 2000)
        assertEquals(expectedList, provider.getListRepository().initialize())
        val child = provider.getSubtaskRepository().create(datedId, "After upgrade")
        val checked = provider.getSubtaskRepository().setChecked(child.id, true)
        provider.close()
        assertEquals(checked, provider.getSubtaskRepository().read(child.id))
        assertEquals(expectedDated, provider.getTaskRepository().read(datedId)); assertEquals(expectedUndated, provider.getTaskRepository().read(undatedId)); assertEquals(expectedDone, provider.getTaskRepository().read(doneId))
        assertEquals(expectedList, provider.getListRepository().initialize()); assertEquals(Settings(1, Appearance.DARK), provider.getDatabase().settings().read())
        assertEquals(4, provider.getDatabase().openHelper.readableDatabase.version)
    }
}

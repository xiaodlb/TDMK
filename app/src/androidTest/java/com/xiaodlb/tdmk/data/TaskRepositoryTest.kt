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
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "tasks-${UUID.randomUUID()}.db"
    private val provider = DatabaseProvider(context, name)
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TdmkDatabase::class.java)
    @After fun cleanup() { provider.close(); context.deleteDatabase(name) }

    @Test
    fun allFieldsUpdatesCompletionAndDateEncodingsSurviveReopen(): Unit = runBlocking {
        val db = provider.getDatabase()
        var clock = Instant.parse("2026-10-12T00:00:01.000Z").toEpochMilli()
        var zone = ZoneId.of("Pacific/Kiritimati")
        val tasks = TaskRepository(db, { clock }, { zone })
        val lists = provider.getListRepository()
        val default = lists.initialize()
        lists.rename(default.id, "General")
        val custom = lists.create("Work")
        val dated = tasks.create(TaskContent("\u0085\u00A0 Buy  🌿 \u3000", scheduledDate = LocalDate.of(2026, 10, 12),
            shortNote = "", reminderTime = LocalTime.of(9, 5)))
        assertEquals("Buy  🌿", dated.title)
        assertEquals(default.id, dated.listId)
        assertEquals("medium", dated.priority)
        assertEquals("", dated.shortNote)
        assertEquals(dated.createdAt, dated.updatedAt)
        val undated = tasks.create(TaskContent("Undated"))
        assertNull(undated.shortNote)
        clock += 10
        val changed = tasks.update(dated.id, dated.content().copy(title = "Edited", listId = custom.id,
            scheduledDate = LocalDate.of(2027, 2, 28), priority = "high", shortNote = "план\nnote", reminderTime = LocalTime.of(23, 59)))
        assertEquals(dated.id, changed.id)
        assertEquals(dated.createdAt, changed.createdAt)
        assertEquals(Instant.ofEpochMilli(clock), changed.updatedAt)
        clock += 10
        assertEquals(changed, tasks.update(changed.id, changed.content()))
        val done = tasks.complete(changed.id)
        assertEquals(LocalDate.of(2026, 10, 12), done.completionDate)
        zone = ZoneId.of("Etc/GMT+12")
        clock -= 1000
        val later = tasks.update(done.id, done.content().copy(priority = "low"))
        assertEquals(done.completedAt, later.completedAt)
        assertEquals(done.completionDate, later.completionDate)
        assertEquals(done.updatedAt, later.updatedAt)
        assertEquals(LocalDate.of(2027, 2, 28), later.scheduledDate)
        val completedOther = tasks.complete(undated.id)
        db.openHelper.readableDatabase.query("SELECT scheduledDate, reminderTime FROM tasks WHERE id = '${dated.id}'").use {
            assertTrue(it.moveToFirst()); assertEquals("2027-02-28", it.getString(0)); assertEquals("23:59", it.getString(1))
        }
        val ids = lists.readAll().map { it.id } + tasks.completed().first().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(4, UUID.fromString(it).version()) }
        provider.close()
        val reopened = provider.getTaskRepository()
        assertEquals(later, reopened.read(later.id))
        assertEquals(completedOther, reopened.read(completedOther.id))
        val removedDate = reopened.setScheduledDate(later.id, null)
        assertNull(removedDate.scheduledDate); assertNull(removedDate.reminderTime)
        assertEquals(later.completionDate, removedDate.completionDate)
        val unfinished = reopened.reopen(later.id)
        assertNull(unfinished.completedAt); assertNull(unfinished.completionDate)
        val additional = reopened.create(TaskContent("Still active"))
        assertEquals(setOf(unfinished.id, additional.id), reopened.active().first().map { it.id }.toSet())
        assertEquals(listOf(completedOther), reopened.completed().first())
        provider.close()
        assertEquals(unfinished, provider.getTaskRepository().read(unfinished.id))
    }

    @Test
    fun existingFlowCollectorsReceiveAllPersistedTransitions(): Unit = runBlocking {
        val tasks = provider.getTaskRepository()
        val active = Channel<List<Task>>(Channel.UNLIMITED)
        val completed = Channel<List<Task>>(Channel.UNLIMITED)
        val a = launch(Dispatchers.IO) { tasks.active().collect { active.send(it) } }
        val c = launch(Dispatchers.IO) { tasks.completed().collect { completed.send(it) } }
        try {
            awaitRows(active) { it.isEmpty() }; awaitRows(completed) { it.isEmpty() }
            val task = tasks.create(TaskContent("One"))
            awaitRows(active) { it.singleOrNull()?.id == task.id }
            tasks.update(task.id, task.content().copy(title = "Two"))
            awaitRows(active) { it.singleOrNull()?.title == "Two" }
            tasks.complete(task.id)
            awaitRows(active) { it.isEmpty() }; awaitRows(completed) { it.singleOrNull()?.id == task.id }
            tasks.reopen(task.id)
            awaitRows(active) { it.singleOrNull()?.id == task.id }; awaitRows(completed) { it.isEmpty() }
            tasks.delete(task.id)
            awaitRows(active) { it.isEmpty() }
            assertThrows(NoSuchElementException::class.java) { tasks.read(task.id) }
        } finally { a.cancelAndJoin(); c.cancelAndJoin(); active.close(); completed.close() }
    }

    private suspend fun awaitRows(channel: Channel<List<Task>>, predicate: (List<Task>) -> Boolean) {
        withTimeout(10000) { while (!predicate(channel.receive())) { /* Await real Room invalidation. */ } }
    }

    @Test
    fun invalidWritesAndUnknownIdsPreservePriorTask(): Unit = runBlocking {
        val tasks = provider.getTaskRepository()
        val existing = tasks.create(TaskContent("Keep", shortNote = "retained"))
        val invalid = listOf(TaskContent(""), TaskContent("\u0085\u2007\u202F"), TaskContent("bad\uD800"),
            TaskContent("Valid", priority = "urgent"), TaskContent("Valid", listId = "unknown"),
            TaskContent("Valid", scheduledDate = LocalDate.of(10000, 1, 1)),
            TaskContent("Valid", reminderTime = LocalTime.NOON),
            TaskContent("Valid", scheduledDate = LocalDate.of(2026, 1, 1), reminderTime = LocalTime.of(1, 2, 3)),
            TaskContent("Valid", shortNote = "bad\uDC00"))
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) { tasks.create(it) }
            assertThrows(IllegalArgumentException::class.java) { tasks.update(existing.id, it) }
            assertEquals(existing, tasks.read(existing.id))
        }
        assertThrows(IllegalArgumentException::class.java) { tasks.setCompletion(existing.id, Instant.now(), null) }
        assertThrows(IllegalArgumentException::class.java) { tasks.setCompletion(existing.id, null, LocalDate.of(2026, 1, 1)) }
        assertThrows(IllegalArgumentException::class.java) {
            tasks.setCompletion(existing.id, Instant.ofEpochSecond(1, 1), LocalDate.of(2026, 1, 1))
        }
        assertThrows(NoSuchElementException::class.java) { tasks.read("unknown") }
        assertThrows(NoSuchElementException::class.java) { tasks.update("unknown", TaskContent("Valid")) }
        assertThrows(NoSuchElementException::class.java) { tasks.delete("unknown") }
        assertEquals(listOf(existing), tasks.active().first())
        assertTrue(tasks.completed().first().isEmpty())
        provider.close(); assertEquals(existing, provider.getTaskRepository().read(existing.id))
    }

    @Test
    fun foreignKeyAndPopulatedListDeletionAreAtomic(): Unit = runBlocking {
        val lists = provider.getListRepository()
        val tasks = provider.getTaskRepository()
        val default = lists.initialize()
        lists.rename(default.id, "General")
        val custom = lists.create("Delete")
        val survivor = lists.create("Survivor")
        val one = tasks.create(TaskContent("One", listId = custom.id, shortNote = "private"))
        val two = tasks.complete(tasks.create(TaskContent("Two", listId = custom.id)).id)
        val unrelated = tasks.create(TaskContent("Unrelated", listId = survivor.id))
        val before = lists.readAll()
        val sql = provider.getDatabase().openHelper.writableDatabase
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            sql.execSQL("DELETE FROM lists WHERE id = '${custom.id}'")
        }
        sql.execSQL("CREATE TRIGGER fail_task_list_delete BEFORE DELETE ON lists WHEN OLD.id = '${custom.id}' BEGIN SELECT RAISE(ABORT, 'injected failure after task removal'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { lists.delete(custom.id) }
        assertEquals(before, lists.readAll()); assertEquals(one, tasks.read(one.id)); assertEquals(two, tasks.read(two.id))
        sql.execSQL("DROP TRIGGER fail_task_list_delete")
        assertThrows(IllegalArgumentException::class.java) { lists.delete(default.id) }
        lists.delete(custom.id)
        assertThrows(NoSuchElementException::class.java) { tasks.read(one.id) }
        assertThrows(NoSuchElementException::class.java) { tasks.read(two.id) }
        assertEquals(unrelated, tasks.read(unrelated.id)); assertEquals(1L, lists.read(survivor.id).order)
        tasks.delete(unrelated.id)
        provider.close()
        assertTrue(provider.getTaskRepository().active().first().isEmpty())
        assertTrue(provider.getTaskRepository().completed().first().isEmpty())
        assertEquals(1L, provider.getListRepository().read(survivor.id).order)
    }

    @Test
    fun exportedSchemaTwoUpgradePreservesSettingsListsAndFurtherTaskWrites() {
        val defaultId = UUID.randomUUID().toString()
        val customId = UUID.randomUUID().toString()
        migration.createDatabase(name, 2).apply {
            execSQL("INSERT INTO settings VALUES (1, 'dark')")
            execSQL("INSERT INTO lists VALUES ('$defaultId', 'General', 1, 0, 1000, 2000)")
            execSQL("INSERT INTO lists VALUES ('$customId', 'Work', 0, 1, 3000, 4000)")
            close()
        }
        migration.runMigrationsAndValidate(name, 3, true, TaskSchema.migration2To3).close()
        val lists = provider.getListRepository()
        assertEquals(TaskList(defaultId, "General", true, 0, 1000, 2000), lists.initialize())
        assertEquals(TaskList(customId, "Work", false, 1, 3000, 4000), lists.read(customId))
        val originalLists = lists.readAll()
        assertEquals(Settings(1, Appearance.DARK), provider.getDatabase().settings().read())
        val task = provider.getTaskRepository().create(TaskContent("After migration", listId = customId,
            scheduledDate = LocalDate.of(2026, 10, 12), reminderTime = LocalTime.of(9, 0)))
        provider.close()
        assertEquals(task, provider.getTaskRepository().read(task.id))
        assertEquals(originalLists, provider.getListRepository().readAll())
        assertEquals(Settings(1, Appearance.DARK), provider.getDatabase().settings().read())
        assertEquals(3, provider.getDatabase().openHelper.readableDatabase.version)
    }
}

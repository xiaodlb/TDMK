package com.xiaodlb.tdmk.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class NoteRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "notes-${UUID.randomUUID()}.db"
    private val provider = DatabaseProvider(context, name)
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TdmkDatabase::class.java)
    private val empty = RichBodyCodec.encode(RichBody(emptyList()))
    private val rich = RichBody(listOf(
        RichBlock("heading", 1, listOf(RichRun("Title", false))),
        RichBlock("heading", 2, listOf(RichRun("Two", true))),
        RichBlock("heading", 3, emptyList()),
        RichBlock("bullet", null, listOf(RichRun("  план 🌿", true), RichRun("\tcafé", false))),
        RichBlock("paragraph", null, emptyList()),
    ))
    @After fun cleanup() { provider.close(); context.deleteDatabase(name) }

    @Test fun richSaveRulesOrderingAndFurtherWritesSurviveReopen() {
        val db = provider.getDatabase()
        var clock = 1800000000000L
        val notes = NoteRepository(db) { clock }
        assertNull(notes.create("\u0085\u3000", empty))
        val blankStyle = RichBodyCodec.encode(RichBody(listOf(RichBlock("heading", 1, listOf(RichRun("", true))), RichBlock("bullet", null, emptyList()))))
        assertNull(notes.create("", blankStyle)); assertTrue(notes.readAll().isEmpty())
        val first = notes.create("\u00A0 Rich \u3000", RichBodyCodec.encode(rich))!!
        val untitled = notes.create("", RichBodyCodec.encode(rich))!!
        val titleOnly = notes.create("Title only", empty)!!
        assertEquals("Rich", first.title); assertEquals("Untitled", untitled.title)
        assertEquals(rich, first.body); assertEquals("Title\nTwo\n\n  план 🌿\tcafé\n", first.plainText)
        assertEquals(first.createdAt, first.updatedAt)
        assertEquals(listOf(first.id, untitled.id, titleOnly.id).sorted(), notes.readAll().map { it.id })
        val previousOrder = notes.readAll()
        clock += 100
        assertEquals(first, notes.update(first.id, "Rich", RichBodyCodec.encode(rich)))
        assertEquals(previousOrder, notes.readAll())
        val formatOnly = rich.copy(blocks = rich.blocks.mapIndexed { index, block -> if (index == 0) block.copy(runs = listOf(RichRun("Title", true))) else block })
        val changed = notes.update(first.id, "Rich", RichBodyCodec.encode(formatOnly))
        assertEquals(first.id, changed.id); assertEquals(first.createdAt, changed.createdAt)
        assertEquals(Instant.ofEpochMilli(clock), changed.updatedAt); assertEquals(first.plainText, changed.plainText)
        assertEquals(changed, notes.readAll().first())
        clock -= 1000
        val cleared = notes.update(first.id, "", empty)
        assertEquals("Untitled", cleared.title); assertEquals(first.id, cleared.id); assertEquals(first.createdAt, cleared.createdAt)
        assertEquals(changed.updatedAt, cleared.updatedAt); assertEquals("", cleared.plainText)
        val all = notes.readAll()
        provider.close(); assertEquals(all, provider.getNoteRepository().readAll())
        assertEquals(untitled, provider.getNoteRepository().read(untitled.id))
        val edited = provider.getNoteRepository().update(titleOnly.id, "Renamed", RichBodyCodec.encode(rich))
        provider.close(); assertEquals(edited, provider.getNoteRepository().read(edited.id))
    }

    @Test fun invalidNewContentAtomicFailureAndCorruptLocalBodyNeverOverwriteState() {
        val notes = provider.getNoteRepository()
        val note = notes.create("Keep", RichBodyCodec.encode(rich))!!
        val db = provider.getDatabase()
        val original = db.notes().find(note.id)!!
        listOf("{", empty.replace("\"version\":1", "\"version\":2"), empty.replace("tdmk.blocks", "other")).forEach {
            assertThrows(IllegalArgumentException::class.java) { notes.create("New", it) }
            assertThrows(IllegalArgumentException::class.java) { notes.update(note.id, "Changed", it) }
            assertEquals(original, db.notes().find(note.id))
        }
        assertThrows(IllegalArgumentException::class.java) { notes.create("bad\uD800", empty) }
        assertThrows(IllegalArgumentException::class.java) { notes.update(note.id, "bad\uDC00", empty) }
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_note BEFORE UPDATE ON notes BEGIN SELECT RAISE(ABORT, 'injected coherent save failure'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { notes.update(note.id, "Different", empty) }
        assertEquals(original, db.notes().find(note.id)); assertEquals(note, notes.read(note.id))
        sql.execSQL("DROP TRIGGER fail_note")
        listOf(empty.replace("\"version\":1", "\"version\":2"), "{corrupt").forEach { raw ->
            sql.execSQL("UPDATE notes SET body = ? WHERE id = ?", arrayOf(raw, note.id))
            val corrupt = db.notes().find(note.id)!!
            assertThrows(IllegalArgumentException::class.java) { notes.read(note.id) }
            assertThrows(IllegalArgumentException::class.java) { notes.update(note.id, "Replace", empty) }
            assertEquals(corrupt, db.notes().find(note.id)); assertEquals(raw, db.notes().find(note.id)!!.body)
        }
        val beforeReopen = db.notes().find(note.id)
        provider.close()
        assertThrows(IllegalArgumentException::class.java) { provider.getNoteRepository().read(note.id) }
        assertEquals(beforeReopen, provider.getDatabase().notes().find(note.id))
    }

    @Test fun independentDeletionAndUnknownIdsNeverRecreateRecords() {
        val notes = provider.getNoteRepository()
        val lists = provider.getListRepository()
        val tasks = provider.getTaskRepository()
        val children = provider.getSubtaskRepository()
        val note = notes.create("Independent", RichBodyCodec.encode(rich))!!
        val custom = lists.create("Work")
        val owned = tasks.create(TaskContent("Owned", listId = custom.id))
        children.create(owned.id, "Owned child")
        val another = tasks.create(TaskContent("Another", listId = custom.id))
        tasks.delete(owned.id); assertEquals(note, notes.read(note.id))
        lists.delete(custom.id); assertEquals(note, notes.read(note.id))
        assertThrows(NoSuchElementException::class.java) { tasks.read(another.id) }
        val retainedList = lists.create("Retain")
        val retainedTask = tasks.create(TaskContent("Retain", listId = retainedList.id))
        val retainedChild = children.create(retainedTask.id, "Retain")
        val ids = lists.readAll().map { it.id } + retainedTask.id + retainedChild.id + note.id
        assertEquals(ids.size, ids.toSet().size); ids.forEach { assertEquals(4, UUID.fromString(it).version()) }
        notes.delete(note.id)
        assertThrows(NoSuchElementException::class.java) { notes.read(note.id) }
        assertThrows(NoSuchElementException::class.java) { notes.update(note.id, "Deleted", empty) }
        assertThrows(NoSuchElementException::class.java) { notes.delete(note.id) }
        assertEquals(retainedList, lists.read(retainedList.id)); assertEquals(retainedTask, tasks.read(retainedTask.id)); assertEquals(retainedChild, children.read(retainedChild.id))
        provider.close(); assertTrue(provider.getNoteRepository().readAll().isEmpty())
        assertEquals(retainedChild, provider.getSubtaskRepository().read(retainedChild.id))
    }

    @Test fun exportedSchemaFourUpgradePreservesExistingDomainsAndFurtherRichWrites() {
        val listId = UUID.randomUUID().toString(); val taskId = UUID.randomUUID().toString(); val childId = UUID.randomUUID().toString()
        val completedAt = Instant.parse("2026-10-12T07:00:00Z").toEpochMilli()
        migration.createDatabase(name, 4).apply {
            execSQL("INSERT INTO settings VALUES (1, 'dark')")
            execSQL("INSERT INTO lists VALUES ('$listId', 'General', 1, 0, 1000, 2000)")
            execSQL("INSERT INTO tasks VALUES ('$taskId', '$listId', 'Completed', '2026-10-12', 'high', 'note', '09:05', $completedAt, '2026-10-12', 3000, $completedAt)")
            execSQL("INSERT INTO subtasks VALUES ('$childId', '$taskId', 'Checked', 0, 1)")
            close()
        }
        migration.runMigrationsAndValidate(name, 5, true, NoteSchema.migration4To5).close()
        val expectedTask = Task(taskId, listId, "Completed", LocalDate.of(2026, 10, 12), "high", "note", LocalTime.of(9, 5), Instant.ofEpochMilli(completedAt), LocalDate.of(2026, 10, 12), Instant.ofEpochMilli(3000), Instant.ofEpochMilli(completedAt))
        val expectedList = TaskList(listId, "General", true, 0, 1000, 2000)
        val expectedChild = Subtask(childId, taskId, "Checked", 0, true)
        assertEquals(expectedTask, provider.getTaskRepository().read(taskId)); assertEquals(expectedList, provider.getListRepository().initialize()); assertEquals(expectedChild, provider.getSubtaskRepository().read(childId))
        val note = provider.getNoteRepository().create("After migration", RichBodyCodec.encode(rich))!!
        provider.close()
        assertEquals(note, provider.getNoteRepository().read(note.id))
        assertEquals(expectedTask, provider.getTaskRepository().read(taskId)); assertEquals(expectedList, provider.getListRepository().initialize()); assertEquals(expectedChild, provider.getSubtaskRepository().read(childId))
        assertEquals(Settings(1, Appearance.DARK), provider.getDatabase().settings().read()); assertEquals(5, provider.getDatabase().openHelper.readableDatabase.version)
    }
}

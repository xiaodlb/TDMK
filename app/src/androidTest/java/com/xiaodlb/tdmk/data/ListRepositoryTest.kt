package com.xiaodlb.tdmk.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ListRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "lists-${UUID.randomUUID()}.db"
    private val provider = DatabaseProvider(context, name)
    @get:Rule
    val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TdmkDatabase::class.java)

    @After
    fun cleanup() {
        provider.close()
        context.deleteDatabase(name)
    }

    @Test
    fun customPermutationDeletionAndAppendPersistAcrossReopen() {
        val repository = provider.getListRepository()
        val default = repository.initialize()
        val first = repository.create("One")
        val second = repository.create("Two")
        val third = repository.create("Three")
        val reordered = repository.reorder(listOf(third.id, first.id, second.id))
        assertEquals(listOf(default.id, third.id, first.id, second.id), reordered.map { it.id })
        assertEquals(listOf(0L, 1L, 2L, 3L), reordered.map { it.order })
        provider.close()
        val reopened = provider.getListRepository()
        assertEquals(reordered, reopened.readAll())
        val beforeInvalid = reopened.readAll()
        listOf(listOf(first.id, first.id, second.id), listOf(first.id, second.id),
            listOf(first.id, second.id, "unknown"), listOf(default.id, first.id, second.id)).forEach {
            assertThrows(IllegalArgumentException::class.java) { reopened.reorder(it) }
            assertEquals(beforeInvalid, reopened.readAll())
        }
        val afterDelete = reopened.delete(first.id)
        assertEquals(listOf(default.id, third.id, second.id), afterDelete.map { it.id })
        assertEquals(listOf(0L, 1L, 2L), afterDelete.map { it.order })
        val fourth = reopened.create("Four")
        assertEquals(3L, fourth.order)
        val final = reopened.readAll()
        provider.close()
        assertEquals(final, provider.getListRepository().readAll())
        final.forEach { assertEquals(4, UUID.fromString(it.id).version()) }
    }

    @Test
    fun renameDefaultUnicodeValidationAndTimestampsPreserveIdentity() {
        val database = provider.getDatabase()
        var clock = 1800000000000L
        val repository = ListRepository(database) { clock }
        val default = repository.initialize()
        assertThrows(IllegalArgumentException::class.java) { repository.delete(default.id) }
        val renamed = repository.rename(default.id, "\u0085\u00A0 General \u3000")
        assertEquals("General", renamed.name)
        assertEquals(default.id, renamed.id)
        assertTrue(renamed.isDefault)
        assertEquals(0L, renamed.order)
        assertEquals(default.createdAt, renamed.createdAt)
        assertThrows(IllegalArgumentException::class.java) { repository.delete(renamed.id) }
        val custom = repository.create("\u2007 A\t  план 🌿 \u202F")
        assertEquals("A\t  план 🌿", custom.name)
        assertEquals(clock, custom.createdAt)
        assertEquals(custom.createdAt, custom.updatedAt)
        clock += 10
        val changed = repository.rename(custom.id, "Changed")
        assertEquals(custom.createdAt, changed.createdAt)
        assertEquals(clock, changed.updatedAt)
        clock += 10
        assertEquals(changed, repository.rename(custom.id, "Changed"))
        clock = custom.createdAt - 100
        assertEquals(changed.updatedAt, repository.rename(custom.id, "Again").updatedAt)
        assertEquals(repository.create("General").name, renamed.name) // Duplicate names are valid.
        val before = repository.readAll()
        listOf("", " \u0085\u2007\u202F\u3000", "bad\uD800", "\uDC00bad").forEach {
            assertThrows(IllegalArgumentException::class.java) { repository.create(it) }
            assertThrows(IllegalArgumentException::class.java) { repository.rename(default.id, it) }
            assertEquals(before, repository.readAll())
        }
        assertThrows(NoSuchElementException::class.java) { repository.read("missing") }
        assertThrows(NoSuchElementException::class.java) { repository.rename("missing", "Valid") }
        assertThrows(NoSuchElementException::class.java) { repository.delete("missing") }
        assertEquals(before, repository.readAll())
        provider.close()
        assertEquals(renamed, provider.getListRepository().initialize())
    }

    @Test
    fun concurrentInitializationAndReopenKeepExactlyOneDefault() {
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val jobs = (1..32).map { pool.submit(Callable {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                provider.getListRepository().initialize()
            }) }
            start.countDown()
            val results = jobs.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, results.map { it.id }.toSet().size)
            assertEquals("Inbox", results.first().name)
            assertEquals(0L, results.first().order)
            assertEquals(listOf(results.first()), provider.getListRepository().readAll())
            provider.close()
            assertEquals(results.first(), provider.getListRepository().initialize())
            assertEquals(1, provider.getListRepository().readAll().count { it.isDefault })
            val db = provider.getDatabase().openHelper.writableDatabase
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                db.execSQL("INSERT INTO lists VALUES ('other-id', 'Other', 1, 4, 0, 0)")
            }
            assertEquals(listOf(results.first()), provider.getListRepository().readAll())
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun injectedSqlFailuresRollBackPartialReorderAndDeletion() {
        val repository = provider.getListRepository()
        val first = repository.create("First")
        val second = repository.create("Second")
        val third = repository.create("Third")
        val before = repository.readAll()
        val sql = provider.getDatabase().openHelper.writableDatabase
        // Fail the second final-position update, after the first row already moved.
        sql.execSQL("CREATE TRIGGER fail_reorder BEFORE UPDATE OF `order` ON lists WHEN NEW.id = '${second.id}' AND NEW.`order` = 2 BEGIN SELECT RAISE(ABORT, 'injected reorder failure'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            repository.reorder(listOf(third.id, second.id, first.id))
        }
        assertEquals(before, repository.readAll())
        sql.execSQL("DROP TRIGGER fail_reorder")
        // Delete first, compact second, then fail while compacting third.
        sql.execSQL("CREATE TRIGGER fail_delete_compaction BEFORE UPDATE OF `order` ON lists WHEN NEW.id = '${third.id}' BEGIN SELECT RAISE(ABORT, 'injected compaction failure'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) { repository.delete(first.id) }
        assertEquals(before, repository.readAll())
        sql.execSQL("DROP TRIGGER fail_delete_compaction")
        provider.close()
        assertEquals(before, provider.getListRepository().readAll())
    }

    @Test
    fun exportedSchemaOneMigrationKeepsDarkSettingsAndSupportsFurtherWrites() {
        migration.createDatabase(name, 1).apply {
            execSQL("INSERT INTO settings (id, appearance) VALUES (1, 'dark')")
            close()
        }
        migration.runMigrationsAndValidate(name, 2, true, ListSchema.migration1To2).apply {
            query("SELECT appearance FROM settings WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("dark", it.getString(0))
            }
            close()
        }
        val repository = provider.getListRepository()
        val default = repository.initialize()
        assertEquals(Settings(1, Appearance.DARK), provider.getDatabase().settings().read())
        val added = repository.create("After upgrade")
        provider.getDatabase().settings().setAppearance(Appearance.LIGHT)
        provider.close()
        assertEquals(default, provider.getListRepository().initialize())
        assertEquals(added, provider.getListRepository().read(added.id))
        assertEquals(Settings(1, Appearance.LIGHT), provider.getDatabase().settings().read())
        assertEquals(6, provider.getDatabase().openHelper.readableDatabase.version)
    }
}

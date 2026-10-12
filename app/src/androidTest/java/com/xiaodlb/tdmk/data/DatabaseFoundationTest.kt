package com.xiaodlb.tdmk.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaodlb.tdmk.TdmkApplication
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DatabaseFoundationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    private val providers = mutableListOf<DatabaseProvider>()

    private fun fixtureName(): String = "foundation-${UUID.randomUUID()}.db".also { names += it }
    private fun provider(name: String): DatabaseProvider = DatabaseProvider(context, name).also { providers += it }

    @After
    fun cleanUpIsolatedFixtures() {
        providers.forEach { it.close() }
        // Test-only cleanup: production initialization never deletes a file.
        names.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun settingsIdentityAndValuesSurviveCloseAndTwoReopens() {
        val name = fixtureName()
        val firstOwner = provider(name)
        val first = firstOwner.getDatabase()
        assertTrue(context.getDatabasePath(name).isFile)
        assertEquals(Settings(1, Appearance.SYSTEM), first.settings().read())
        assertEquals(1, first.settings().setAppearance(Appearance.DARK))
        val saved = first.settings().read()
        assertEquals(Settings(1, Appearance.DARK), saved)
        firstOwner.close()
        assertFalse(first.isOpen)

        // A new provider models a new application process, with no cached Room instance.
        val secondOwner = provider(name)
        val second = secondOwner.getDatabase()
        assertNotSame(first, second)
        assertEquals(saved, second.settings().read())
        assertEquals(1, second.settings().setAppearance(Appearance.LIGHT))
        secondOwner.close()

        val third = provider(name).getDatabase()
        assertEquals(Settings(1, Appearance.LIGHT), third.settings().read())
        third.openHelper.readableDatabase.query("SELECT COUNT(*) FROM settings").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        assertEquals(6, third.openHelper.readableDatabase.version)
    }

    @Test
    fun concurrentApplicationAccessPublishesOneOpenedInstance() {
        val application = context.applicationContext as TdmkApplication
        val sharedProvider = application.databaseProvider
        assertSame(sharedProvider, application.databaseProvider)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val futures = (1..32).map {
                pool.submit(Callable {
                    assertTrue(start.await(10, TimeUnit.SECONDS))
                    application.databaseProvider.getDatabase()
                })
            }
            start.countDown()
            val opened = futures.map { it.get(30, TimeUnit.SECONDS) }
            opened.forEach {
                assertSame(opened.first(), it)
                assertTrue(it.isOpen)
            }
            assertSame(opened.first(), sharedProvider.getDatabase())
            assertTrue(context.getDatabasePath(DatabaseProvider.DATABASE_NAME).isFile)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
            sharedProvider.close()
        }
    }

    @Test
    fun settingsWriteRollsBackWithItsTransaction() {
        val database = provider(fixtureName()).getDatabase()
        assertThrows(IllegalStateException::class.java) {
            database.runInTransaction {
                database.settings().setAppearance(Appearance.DARK)
                throw IllegalStateException("cancel combined settings transaction")
            }
        }
        assertEquals(Settings(1, Appearance.SYSTEM), database.settings().read())
    }

    @Test
    fun unsupportedDowngradeFailsAndPreservesSentinelAndVersion() {
        val name = fixtureName()
        createIncompatibleFixture(name, 7)
        val owner = provider(name)
        repeat(2) {
            val failure = assertThrows(IllegalStateException::class.java) { owner.getDatabase() }
            assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("7 to 6"))
            assertFixtureUnchanged(name, 7)
        }
    }

    @Test
    fun incompatibleCurrentSchemaFailsAndPreservesSentinelAndVersion() {
        val name = fixtureName()
        createIncompatibleFixture(name, 6)
        val failure = assertThrows(IllegalStateException::class.java) { provider(name).getDatabase() }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("invalid schema", ignoreCase = true))
        assertFixtureUnchanged(name, 6)
    }

    @Test
    fun corruptFileOpenFailsWithoutDeletingOrReplacingBytes() {
        val name = fixtureName()
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val original = ByteArray(4096) { (it % 251).toByte() }
        path.writeBytes(original)
        val owner = provider(name)
        repeat(2) {
            assertThrows(android.database.sqlite.SQLiteException::class.java) { owner.getDatabase() }
            assertTrue(path.isFile)
            assertArrayEquals(original, path.readBytes())
        }
    }

    private fun createIncompatibleFixture(name: String, version: Int) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use {
            it.execSQL("CREATE TABLE sentinel (id TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            it.execSQL("INSERT INTO sentinel VALUES ('keep-id', 'план 🌿 — retained')")
            // Same version but deliberately incomplete settings table triggers real Room validation.
            it.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY NOT NULL)")
            it.execSQL("INSERT INTO settings VALUES (1)")
            it.version = version
        }
    }

    private fun assertFixtureUnchanged(name: String, version: Int) {
        val path = context.getDatabasePath(name)
        assertTrue(path.isFile)
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(version, it.version)
            it.rawQuery("SELECT id, value FROM sentinel", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("keep-id", cursor.getString(0))
                assertEquals("план 🌿 — retained", cursor.getString(1))
                assertFalse(cursor.moveToNext())
            }
            it.rawQuery("SELECT id FROM settings", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertFalse(cursor.moveToNext())
            }
            it.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE name = 'room_master_table'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}

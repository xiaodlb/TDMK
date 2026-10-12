package com.xiaodlb.tdmk.data

import android.content.Context
import android.os.Looper
import androidx.annotation.WorkerThread
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Application-owned, on-disk storage. Call from a worker thread; failures propagate unchanged. */
class DatabaseProvider(context: Context, private val filename: String = DATABASE_NAME) {
    private val applicationContext = context.applicationContext
    private var instance: TdmkDatabase? = null

    @WorkerThread
    @Synchronized
    fun getDatabase(): TdmkDatabase {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Open the database on a worker thread" }
        instance?.let { return it }
        val database = Room.databaseBuilder(applicationContext, TdmkDatabase::class.java, filename)
            .openHelperFactory(PreservingOpenHelperFactory())
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("INSERT INTO settings (id, appearance) VALUES (1, 'system')")
                }
            })
            // Register explicit, tested migrations here when a later issue increments the version.
            // No destructive migration, downgrade or automatic file recovery is permitted.
            .build()
        try {
            // Room.build() is lazy. Open and validate before publishing a successful instance.
            database.openHelper.writableDatabase
        } catch (failure: Exception) {
            try {
                database.close()
            } catch (closeFailure: Exception) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
        return database.also { instance = it }
    }

    /** Only for an owner shutting down all users of this provider, including device tests. */
    @Synchronized
    fun close() {
        instance?.close()
        instance = null
    }

    companion object {
        const val DATABASE_NAME = "tdmk.db"
    }
}

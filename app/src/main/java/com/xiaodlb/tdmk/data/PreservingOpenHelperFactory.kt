package com.xiaodlb.tdmk.data

import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/** Prevent the framework's default corruption callback from deleting a user's database. */
internal class PreservingOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val original = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(original.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = original.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = original.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) {
                throw SQLiteDatabaseCorruptException("Database corruption; preserved file: ${db.path}")
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(callback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false)
                .build(),
        )
    }
}

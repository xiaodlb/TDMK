package com.xiaodlb.tdmk.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object ListSchema {
    val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS lists (id TEXT NOT NULL, name TEXT NOT NULL, isDefault INTEGER NOT NULL, `order` INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_lists_order ON lists (`order`)")
            installDefaultConstraints(db)
        }
    }

    fun installDefaultConstraints(db: SupportSQLiteDatabase) {
        // True-only uniqueness: custom lists may all be false. Room validates tables/indexes, not triggers.
        db.execSQL("CREATE TRIGGER IF NOT EXISTS lists_one_default_insert BEFORE INSERT ON lists WHEN NEW.isDefault = 1 AND EXISTS (SELECT 1 FROM lists WHERE isDefault = 1) BEGIN SELECT RAISE(ABORT, 'Only one default list'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS lists_one_default_update BEFORE UPDATE OF isDefault ON lists WHEN NEW.isDefault = 1 AND EXISTS (SELECT 1 FROM lists WHERE isDefault = 1 AND id != NEW.id) BEGIN SELECT RAISE(ABORT, 'Only one default list'); END")
    }
}

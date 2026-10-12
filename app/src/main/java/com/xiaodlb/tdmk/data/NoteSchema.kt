package com.xiaodlb.tdmk.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object NoteSchema {
    val migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ListSchema.installDefaultConstraints(db)
            db.execSQL("CREATE TABLE IF NOT EXISTS notes (id TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, plainText TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
        }
    }
}

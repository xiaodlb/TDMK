package com.xiaodlb.tdmk.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object TaskSchema {
    val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ListSchema.installDefaultConstraints(db)
            db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL, listId TEXT NOT NULL, title TEXT NOT NULL, scheduledDate TEXT, priority TEXT NOT NULL, shortNote TEXT, reminderTime TEXT, completedAt INTEGER, completionDate TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(listId) REFERENCES lists(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_listId ON tasks(listId)")
        }
    }
}

package com.xiaodlb.tdmk.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object SubtaskSchema {
    val migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ListSchema.installDefaultConstraints(db)
            db.execSQL("CREATE TABLE IF NOT EXISTS subtasks (id TEXT NOT NULL, taskId TEXT NOT NULL, text TEXT NOT NULL, `order` INTEGER NOT NULL, isCompleted INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_subtasks_taskId_order ON subtasks(taskId, `order`)")
        }
    }
}

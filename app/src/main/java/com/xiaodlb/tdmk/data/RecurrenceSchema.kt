package com.xiaodlb.tdmk.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object RecurrenceSchema {
    val migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE templates (id TEXT NOT NULL, listId TEXT NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(listId) REFERENCES lists(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX index_templates_listId ON templates(listId)")
            db.execSQL("CREATE TABLE template_revisions (seriesId TEXT NOT NULL, number INTEGER NOT NULL, effectiveFrom TEXT NOT NULL, effectiveUntil TEXT, anchorDate TEXT NOT NULL, frequency TEXT NOT NULL, title TEXT NOT NULL, priority TEXT NOT NULL, shortNote TEXT, reminderTime TEXT, reconciledThrough TEXT, PRIMARY KEY(seriesId, number), FOREIGN KEY(seriesId) REFERENCES templates(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE checklist_prototypes (seriesId TEXT NOT NULL, revisionNumber INTEGER NOT NULL, `order` INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(seriesId, revisionNumber, `order`), FOREIGN KEY(seriesId, revisionNumber) REFERENCES template_revisions(seriesId, number) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE exclusions (seriesId TEXT NOT NULL, slotDate TEXT NOT NULL, PRIMARY KEY(seriesId, slotDate), FOREIGN KEY(seriesId) REFERENCES templates(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            // Copy BOTH parent and child tables before dropping either; dropping tasks first would CASCADE user subtasks.
            db.execSQL("CREATE TABLE tasks_new (id TEXT NOT NULL, listId TEXT NOT NULL, title TEXT NOT NULL, scheduledDate TEXT, priority TEXT NOT NULL, shortNote TEXT, reminderTime TEXT, completedAt INTEGER, completionDate TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, seriesId TEXT, slotDate TEXT, sourceRevision INTEGER, isOverride INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id), FOREIGN KEY(listId) REFERENCES lists(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(seriesId) REFERENCES templates(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(seriesId, sourceRevision) REFERENCES template_revisions(seriesId, number) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("INSERT INTO tasks_new SELECT id, listId, title, scheduledDate, priority, shortNote, reminderTime, completedAt, completionDate, createdAt, updatedAt, NULL, NULL, NULL, 0 FROM tasks")
            db.execSQL("CREATE TABLE subtasks_new (id TEXT NOT NULL, taskId TEXT NOT NULL, text TEXT NOT NULL, `order` INTEGER NOT NULL, isCompleted INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(taskId) REFERENCES tasks_new(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("INSERT INTO subtasks_new SELECT id, taskId, text, `order`, isCompleted FROM subtasks")
            db.execSQL("DROP TABLE subtasks")
            db.execSQL("DROP TABLE tasks")
            db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
            db.execSQL("ALTER TABLE subtasks_new RENAME TO subtasks")
            db.execSQL("CREATE INDEX index_tasks_listId ON tasks(listId)")
            db.execSQL("CREATE UNIQUE INDEX index_tasks_seriesId_slotDate ON tasks(seriesId, slotDate)")
            db.execSQL("CREATE INDEX index_tasks_seriesId_sourceRevision ON tasks(seriesId, sourceRevision)")
            db.execSQL("CREATE UNIQUE INDEX index_subtasks_taskId_order ON subtasks(taskId, `order`)")
            ListSchema.installDefaultConstraints(db)
            installConstraints(db)
        }
    }

    fun installConstraints(db: SupportSQLiteDatabase) {
        val valid = "((NEW.seriesId IS NULL AND NEW.slotDate IS NULL AND NEW.sourceRevision IS NULL AND NEW.isOverride = 0) OR (NEW.seriesId IS NOT NULL AND NEW.slotDate IS NOT NULL AND NEW.sourceRevision IS NOT NULL AND NEW.sourceRevision > 0 AND NEW.isOverride IN (0,1)))"
        listOf("INSERT", "UPDATE").forEach { operation ->
            db.execSQL("CREATE TRIGGER IF NOT EXISTS tasks_recurrence_${operation.lowercase()} BEFORE $operation ON tasks WHEN NOT $valid BEGIN SELECT RAISE(ABORT, 'Unpaired recurrence identity'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS tasks_reserved_${operation.lowercase()} BEFORE $operation ON tasks WHEN NEW.seriesId IS NOT NULL AND EXISTS(SELECT 1 FROM exclusions WHERE seriesId = NEW.seriesId AND slotDate = NEW.slotDate) BEGIN SELECT RAISE(ABORT, 'Excluded original slot'); END")
        }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS tasks_immutable_slot BEFORE UPDATE ON tasks WHEN OLD.seriesId IS NOT NULL AND (NEW.seriesId IS NOT OLD.seriesId OR NEW.slotDate IS NOT OLD.slotDate) BEGIN SELECT RAISE(ABORT, 'Original slot is immutable'); END")
        listOf("INSERT", "UPDATE").forEach { operation ->
            db.execSQL("CREATE TRIGGER IF NOT EXISTS exclusion_live_slot_${operation.lowercase()} BEFORE $operation ON exclusions WHEN EXISTS(SELECT 1 FROM tasks WHERE seriesId = NEW.seriesId AND slotDate = NEW.slotDate) BEGIN SELECT RAISE(ABORT, 'Live original slot'); END")
        }
    }
}

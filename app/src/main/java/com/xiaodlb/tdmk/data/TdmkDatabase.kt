package com.xiaodlb.tdmk.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Settings::class, TaskList::class, Task::class, Subtask::class, NoteRow::class], version = 5, exportSchema = true)
@TypeConverters(AppearanceConverters::class, TaskTimeConverters::class)
abstract class TdmkDatabase : RoomDatabase() {
    abstract fun settings(): SettingsDao
    internal abstract fun lists(): ListDao
    internal abstract fun tasks(): TaskDao
    internal abstract fun subtasks(): SubtaskDao
    internal abstract fun notes(): NoteDao
}

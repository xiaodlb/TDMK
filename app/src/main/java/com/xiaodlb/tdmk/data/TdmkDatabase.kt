package com.xiaodlb.tdmk.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Settings::class, TaskList::class], version = 2, exportSchema = true)
@TypeConverters(AppearanceConverters::class)
abstract class TdmkDatabase : RoomDatabase() {
    abstract fun settings(): SettingsDao
    internal abstract fun lists(): ListDao
}

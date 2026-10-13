package com.xiaodlb.tdmk.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import kotlinx.coroutines.flow.Flow

enum class Appearance(val storedValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark")
}

class AppearanceConverters {
    @TypeConverter
    fun encode(value: Appearance): String = value.storedValue

    @TypeConverter
    fun decode(value: String): Appearance = Appearance.entries.single { it.storedValue == value }
}

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val appearance: Appearance = Appearance.SYSTEM,
) {
    init {
        require(id == 1) { "Settings has only the local singleton identity 1" }
    }
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun read(): Settings

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<Settings>

    // A single SQL update is atomic; no insertion/deletion API can create another singleton.
    @Query("UPDATE settings SET appearance = :appearance WHERE id = 1")
    fun setAppearance(appearance: Appearance): Int
}

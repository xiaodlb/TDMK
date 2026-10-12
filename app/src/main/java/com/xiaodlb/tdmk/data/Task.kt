package com.xiaodlb.tdmk.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "tasks", foreignKeys = [ForeignKey(
    entity = TaskList::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.RESTRICT,
)], indices = [Index("listId")])
data class Task(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val scheduledDate: LocalDate?,
    val priority: String,
    val shortNote: String?,
    val reminderTime: LocalTime?,
    val completedAt: Instant?,
    val completionDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun content() = TaskContent(title, listId, scheduledDate, priority, shortNote, reminderTime)
}

data class TaskContent(
    val title: String,
    val listId: String? = null,
    val scheduledDate: LocalDate? = null,
    val priority: String = "medium",
    val shortNote: String? = null,
    val reminderTime: LocalTime? = null,
)

class TaskTimeConverters {
    @TypeConverter fun dateToText(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun textToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun timeToText(value: LocalTime?): String? = value?.toString()
    @TypeConverter fun textToTime(value: String?): LocalTime? = value?.let(LocalTime::parse)
    @TypeConverter fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

@Dao
internal interface TaskDao {
    @Insert fun insert(task: Task)
    @Update fun update(task: Task)
    @Query("SELECT * FROM tasks WHERE id = :id") fun find(id: String): Task?
    @Query("SELECT * FROM tasks WHERE completedAt IS NULL ORDER BY id") fun active(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE completedAt IS NOT NULL ORDER BY id") fun completed(): Flow<List<Task>>
    @Query("DELETE FROM tasks WHERE id = :id") fun delete(id: String)
    @Query("DELETE FROM tasks WHERE listId = :listId") fun deleteForList(listId: String)
}

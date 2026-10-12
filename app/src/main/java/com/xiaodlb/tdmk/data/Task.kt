package com.xiaodlb.tdmk.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "tasks", foreignKeys = [ForeignKey(
    entity = TaskList::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.RESTRICT,
), ForeignKey(entity = RecurrenceTemplate::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = RecurrenceRevision::class, parentColumns = ["seriesId", "number"], childColumns = ["seriesId", "sourceRevision"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("listId"), Index(value = ["seriesId", "slotDate"], unique = true), Index(value = ["seriesId", "sourceRevision"])])
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
    val seriesId: String? = null,
    val slotDate: LocalDate? = null,
    val sourceRevision: Int? = null,
    @ColumnInfo(defaultValue = "0") val isOverride: Boolean = false,
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
    @Query("SELECT * FROM tasks ORDER BY id") fun viewCandidates(): Flow<List<Task>>
    @Query("DELETE FROM tasks WHERE id = :id") fun delete(id: String)
    @Query("DELETE FROM tasks WHERE listId = :listId") fun deleteForList(listId: String)
    @Query("SELECT * FROM tasks WHERE listId = :listId") fun forList(listId: String): List<Task>
    @Query("SELECT * FROM tasks WHERE seriesId = :seriesId ORDER BY slotDate") fun forSeries(seriesId: String): List<Task>
    @Query("SELECT * FROM tasks WHERE seriesId = :seriesId AND slotDate = :slot") fun forSlot(seriesId: String, slot: LocalDate): Task?
    @Query("DELETE FROM tasks WHERE seriesId = :seriesId") fun deleteForSeries(seriesId: String)
}

package com.xiaodlb.tdmk.data

import androidx.room.*

@Entity(tableName = "subtasks", foreignKeys = [ForeignKey(
    entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index(value = ["taskId", "order"], unique = true)])
data class Subtask(
    @PrimaryKey val id: String,
    val taskId: String,
    val text: String,
    val order: Long,
    val isCompleted: Boolean = false,
)

@Dao
internal interface SubtaskDao {
    @Insert fun insert(subtask: Subtask)
    @Update fun update(subtask: Subtask)
    @Query("SELECT * FROM subtasks WHERE id = :id") fun find(id: String): Subtask?
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY `order`") fun forParent(taskId: String): List<Subtask>
    @Query("SELECT COUNT(*) FROM subtasks WHERE taskId = :taskId AND isCompleted = 0") fun unchecked(taskId: String): Int
    @Query("UPDATE subtasks SET `order` = `order` + :offset WHERE taskId = :taskId") fun moveAside(taskId: String, offset: Long)
    @Query("UPDATE subtasks SET `order` = :order WHERE id = :id") fun move(id: String, order: Long)
    @Query("DELETE FROM subtasks WHERE id = :id") fun delete(id: String)
}

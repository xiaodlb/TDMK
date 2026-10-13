package com.xiaodlb.tdmk.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "lists", indices = [Index(value = ["order"], unique = true)])
data class TaskList(
    @PrimaryKey val id: String,
    val name: String,
    val isDefault: Boolean,
    val order: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
internal interface ListDao {
    @Query("SELECT * FROM lists ORDER BY `order`")
    fun observe(): kotlinx.coroutines.flow.Flow<List<TaskList>>

    @Query("SELECT * FROM lists ORDER BY `order`")
    fun all(): List<TaskList>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun find(id: String): TaskList?

    @Query("SELECT * FROM lists WHERE isDefault = 1")
    fun defaultList(): TaskList?

    @Insert
    fun insert(list: TaskList)

    @Query("UPDATE lists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE lists SET `order` = `order` + :offset WHERE isDefault = 0")
    fun moveCustomsAside(offset: Long)

    @Query("UPDATE lists SET `order` = :order, updatedAt = :updatedAt WHERE id = :id")
    fun move(id: String, order: Long, updatedAt: Long)

    @Query("DELETE FROM lists WHERE id = :id")
    fun delete(id: String)
}

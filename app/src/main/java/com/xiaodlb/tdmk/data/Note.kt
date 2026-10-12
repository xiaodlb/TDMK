package com.xiaodlb.tdmk.data

import androidx.room.*
import java.time.Instant

data class Note(val id: String, val title: String, val body: RichBody, val plainText: String, val createdAt: Instant, val updatedAt: Instant)

@Entity(tableName = "notes")
internal data class NoteRow(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val plainText: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun validated(): Note = Note(id, title, RichBodyCodec.decode(body), plainText, createdAt, updatedAt)
}

@Dao
internal interface NoteDao {
    @Insert fun insert(note: NoteRow)
    @Update fun update(note: NoteRow)
    @Query("SELECT * FROM notes WHERE id = :id") fun find(id: String): NoteRow?
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, createdAt DESC, id ASC") fun all(): List<NoteRow>
    @Query("DELETE FROM notes WHERE id = :id") fun delete(id: String)
}

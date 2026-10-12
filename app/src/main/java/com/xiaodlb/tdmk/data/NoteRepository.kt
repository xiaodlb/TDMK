package com.xiaodlb.tdmk.data

import androidx.annotation.WorkerThread
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable

@WorkerThread
class NoteRepository internal constructor(private val database: TdmkDatabase, private val now: () -> Long = System::currentTimeMillis) {
    private val dao = database.notes()
    fun read(id: String): Note = row(id).validated()
    fun readAll(): List<Note> = dao.all().map { it.validated() }

    /** Null means an entirely blank new draft was discarded without a record/ID/timestamp. */
    fun create(title: String, body: String): Note? = transaction {
        val trimmed = StoredText.trim(title)
        val rich = RichBodyCodec.decode(body)
        val text = RichBodyCodec.plainText(rich)
        if (trimmed.isEmpty() && StoredText.trim(text).isEmpty()) null else {
            var id: String
            do { id = UUID.randomUUID().toString() } while (dao.find(id) != null || database.lists().find(id) != null ||
                database.tasks().find(id) != null || database.subtasks().find(id) != null)
            val time = instant()
            NoteRow(id, trimmed.ifEmpty { "Untitled" }, RichBodyCodec.encode(rich), text, time, time)
                .also { dao.insert(it) }.validated()
        }
    }

    fun update(id: String, title: String, body: String): Note = transaction {
        val previous = row(id)
        val before = previous.validated() // Unknown/corrupt local bodies cannot be overwritten.
        val newTitle = StoredText.trim(title).ifEmpty { "Untitled" }
        val rich = RichBodyCodec.decode(body)
        if (before.title == newTitle && before.body == rich) before else {
            previous.copy(title = newTitle, body = RichBodyCodec.encode(rich), plainText = RichBodyCodec.plainText(rich),
                updatedAt = maxOf(previous.updatedAt, instant())).also { dao.update(it) }.validated()
        }
    }

    fun delete(id: String) = transaction { read(id); dao.delete(id) }
    private fun row(id: String): NoteRow = dao.find(id) ?: throw NoSuchElementException("Unknown note ID: $id")
    private fun instant(): Instant = now().also {
        require(it in -62135596800000L..253402300799999L) { "Timestamp outside years 0001–9999" }
    }.let(Instant::ofEpochMilli)
    private fun <T> transaction(action: () -> T): T = database.runInTransaction(Callable { action() })
}

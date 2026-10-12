package com.xiaodlb.tdmk.data

import androidx.annotation.WorkerThread
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Callable

@WorkerThread
class TaskRepository internal constructor(
    private val database: TdmkDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val dao = database.tasks()
    fun active() = dao.active()
    fun completed() = dao.completed()
    fun read(id: String): Task = dao.find(id) ?: throw NoSuchElementException("Unknown task ID: $id")

    fun create(content: TaskContent): Task = transaction {
        val valid = validate(content)
        var id: String
        do { id = UUID.randomUUID().toString() } while (dao.find(id) != null || database.lists().find(id) != null || database.subtasks().find(id) != null || database.notes().find(id) != null)
        val time = instant()
        Task(id, valid.listId!!, valid.title, valid.scheduledDate, valid.priority, valid.shortNote,
            valid.reminderTime, null, null, time, time).also { dao.insert(it) }
    }

    fun update(id: String, content: TaskContent): Task = transaction {
        val previous = read(id)
        val valid = validate(content)
        val changed = previous.copy(listId = valid.listId!!, title = valid.title,
            scheduledDate = valid.scheduledDate, priority = valid.priority, shortNote = valid.shortNote,
            reminderTime = valid.reminderTime)
        save(previous, changed)
    }

    fun setScheduledDate(id: String, date: LocalDate?): Task = transaction {
        val previous = read(id)
        update(id, previous.content().copy(scheduledDate = date, reminderTime = if (date == null) null else previous.reminderTime))
    }

    fun complete(id: String): Task = transaction {
        val previous = read(id)
        require(database.subtasks().unchecked(id) == 0) { "Complete all subtasks before their parent" }
        if (previous.completedAt != null) previous else {
            val time = instant()
            setCompletion(id, time, time.atZone(zone()).toLocalDate())
        }
    }

    fun reopen(id: String): Task = setCompletion(id, null, null)

    fun setCompletion(id: String, at: Instant?, day: LocalDate?): Task = transaction {
        val previous = read(id)
        require((at == null) == (day == null)) { "Completion instant and captured day must be paired" }
        if (at != null) require(database.subtasks().unchecked(id) == 0) { "Complete all subtasks before their parent" }
        at?.let { validInstant(it) }
        day?.let { validDate(it) }
        save(previous, previous.copy(completedAt = at, completionDate = day))
    }

    fun delete(id: String) = transaction { read(id); dao.delete(id) }

    private fun save(previous: Task, changed: Task): Task {
        if (previous == changed) return previous
        return changed.copy(updatedAt = maxOf(previous.updatedAt, instant())).also { dao.update(it) }
    }

    private fun validate(content: TaskContent): TaskContent {
        val title = StoredText.label(content.title)
        require(content.priority in setOf("high", "medium", "low")) { "Unknown priority" }
        content.shortNote?.let { StoredText.validate(it) }
        content.scheduledDate?.let { validDate(it) }
        content.reminderTime?.let {
            require(content.scheduledDate != null) { "Reminder requires a date" }
            require(it.second == 0 && it.nano == 0) { "Reminder uses minute precision" }
        }
        val listId = content.listId ?: database.lists().defaultList()!!.id
        require(database.lists().find(listId) != null) { "Unknown list ID: $listId" }
        return content.copy(title = title, listId = listId)
    }

    private fun validDate(date: LocalDate) { require(date.year in 1..9999) { "Date outside years 0001–9999" } }
    private fun validInstant(value: Instant) {
        require(value.nano % 1000000 == 0 && value >= Instant.ofEpochMilli(-62135596800000L) &&
            value <= Instant.ofEpochMilli(253402300799999L)) { "Invalid millisecond instant" }
    }
    private fun instant(): Instant = Instant.ofEpochMilli(now()).also { validInstant(it) }
    private fun <T> transaction(action: () -> T): T = database.runInTransaction(Callable { action() })
}

package com.xiaodlb.tdmk.data

import androidx.annotation.WorkerThread
import java.util.UUID
import java.util.concurrent.Callable

@WorkerThread
class SubtaskRepository internal constructor(private val database: TdmkDatabase) {
    private val dao = database.subtasks()
    fun read(id: String): Subtask = dao.find(id) ?: throw NoSuchElementException("Unknown subtask ID: $id")
    fun readForParent(taskId: String): List<Subtask> { parent(taskId); return dao.forParent(taskId) }

    fun create(taskId: String, text: String): Subtask = add(taskId, text, true)
    internal fun createGenerated(taskId: String, text: String): Subtask = add(taskId, text, false)
    private fun add(taskId: String, text: String, userEdit: Boolean): Subtask = transaction {
        val valid = StoredText.label(text)
        val owner = parent(taskId)
        var id: String
        do { id = UUID.randomUUID().toString() } while (dao.find(id) != null ||
            database.tasks().find(id) != null || database.lists().find(id) != null || database.notes().find(id) != null || database.recurrence().find(id) != null)
        val child = Subtask(id, taskId, valid, dao.forParent(taskId).size.toLong())
        dao.insert(child)
        if (owner.completedAt != null) TaskRepository(database).reopen(taskId)
        if (userEdit) TaskRepository(database).markChecklistOverride(taskId)
        child
    }

    fun edit(id: String, text: String): Subtask = transaction {
        val previous = read(id)
        val updated = previous.copy(text = StoredText.label(text))
        if (updated != previous) {
            dao.update(updated)
            TaskRepository(database).markChecklistOverride(previous.taskId)
        }
        updated
    }

    fun setChecked(id: String, checked: Boolean): Subtask = transaction {
        val previous = read(id)
        val updated = previous.copy(isCompleted = checked)
        if (updated != previous) {
            dao.update(updated)
            if (!checked && parent(previous.taskId).completedAt != null) TaskRepository(database).reopen(previous.taskId)
            TaskRepository(database).markChecklistOverride(previous.taskId)
        }
        updated
    }

    fun reorder(taskId: String, ids: List<String>): List<Subtask> = transaction {
        parent(taskId)
        val children = dao.forParent(taskId)
        require(ids.size == children.size && ids.toSet().size == ids.size && ids.toSet() == children.map { it.id }.toSet()) {
            "Reorder requires every child of this parent exactly once"
        }
        if (ids != children.map { it.id }) {
            dao.moveAside(taskId, children.size.toLong())
            ids.forEachIndexed { position, id -> dao.move(id, position.toLong()) }
            TaskRepository(database).markChecklistOverride(taskId)
        }
        dao.forParent(taskId)
    }

    fun delete(id: String): List<Subtask> = transaction {
        val previous = read(id)
        dao.delete(id)
        TaskRepository(database).markChecklistOverride(previous.taskId)
        dao.forParent(previous.taskId).forEachIndexed { position, child ->
            if (child.order != position.toLong()) dao.move(child.id, position.toLong())
        }
        dao.forParent(previous.taskId)
    }

    private fun parent(id: String): Task = database.tasks().find(id) ?: throw NoSuchElementException("Unknown parent task ID: $id")
    private fun <T> transaction(action: () -> T): T = database.runInTransaction(Callable { action() })
}

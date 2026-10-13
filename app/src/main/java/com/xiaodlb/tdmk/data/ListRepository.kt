package com.xiaodlb.tdmk.data

import androidx.annotation.WorkerThread
import java.util.UUID
import java.util.concurrent.Callable

/** Lists-only transactions. Future populated-list deletion must integrate dependent storage here. */
@WorkerThread
class ListRepository internal constructor(
    private val database: TdmkDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.lists()

    fun initialize(): TaskList = transaction {
        dao.defaultList() ?: newList("Inbox", true, 0).also { dao.insert(it) }
    }

    fun readAll(): List<TaskList> = dao.all()
    fun observe() = dao.observe()

    fun read(id: String): TaskList = find(id)

    fun create(name: String): TaskList {
        val validName = StoredText.label(name)
        return transaction {
            newList(validName, false, dao.all().size.toLong()).also { dao.insert(it) }
        }
    }

    fun rename(id: String, name: String): TaskList {
        val validName = StoredText.label(name)
        return transaction {
            val previous = find(id)
            if (previous.name != validName) dao.rename(id, validName, updatedTime(previous))
            find(id)
        }
    }

    fun reorder(customIds: List<String>): List<TaskList> = transaction {
        val customs = dao.all().filterNot { it.isDefault }
        require(customIds.size == customs.size && customIds.toSet().size == customIds.size &&
            customIds.toSet() == customs.map { it.id }.toSet()) {
            "Reorder requires each current custom list ID exactly once"
        }
        if (customIds != customs.map { it.id }) {
            // Reserve disjoint positive positions inside the transaction to avoid unique-index collisions.
            dao.moveCustomsAside(customs.size.toLong())
            val previousById = customs.associateBy { it.id }
            customIds.forEachIndexed { index, id ->
                val previous = previousById.getValue(id)
                val order = index + 1L
                dao.move(id, order, if (previous.order == order) previous.updatedAt else updatedTime(previous))
            }
        }
        dao.all()
    }

    fun delete(id: String): List<TaskList> = transaction {
        val target = find(id)
        require(!target.isDefault) { "The default list cannot be deleted, even after rename" }
        RecurrenceRepository(database).deleteOwnedAndReserveExternal(id)
        dao.delete(id)
        // Ascending compaction fills the vacated slot before moving the next surviving row.
        dao.all().filterNot { it.isDefault }.forEachIndexed { index, previous ->
            val order = index + 1L
            if (previous.order != order) dao.move(previous.id, order, updatedTime(previous))
        }
        dao.all()
    }

    private fun find(id: String): TaskList = dao.find(id) ?: throw NoSuchElementException("Unknown list ID: $id")

    private fun newList(name: String, isDefault: Boolean, order: Long): TaskList {
        val instant = time()
        var id: String
        do { id = UUID.randomUUID().toString() } while (dao.find(id) != null || database.tasks().find(id) != null || database.subtasks().find(id) != null || database.notes().find(id) != null || database.recurrence().find(id) != null)
        return TaskList(id, name, isDefault, order, instant, instant)
    }

    private fun time(): Long = now().also {
        require(it in -62135596800000L..253402300799999L) { "Timestamp outside years 0001–9999" }
    }

    private fun updatedTime(previous: TaskList): Long = maxOf(previous.updatedAt, time())

    private fun <T> transaction(action: () -> T): T = database.runInTransaction(Callable { action() })

}

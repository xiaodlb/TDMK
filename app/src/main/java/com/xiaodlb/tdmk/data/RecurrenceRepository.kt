package com.xiaodlb.tdmk.data

import androidx.annotation.WorkerThread
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable

@WorkerThread
class RecurrenceRepository internal constructor(private val database: TdmkDatabase, private val clock: Clock = Clock.systemDefaultZone()) {
    private val dao = database.recurrence()

    fun read(id: String): Recurrence = transaction {
        val template = dao.find(id) ?: throw NoSuchElementException("Unknown series ID: $id")
        val revisions = dao.revisions(id)
        require(revisions.isNotEmpty()) { "Series requires revisions" }
        val result = revisions.mapIndexed { index, revision ->
            require(revision.number == index + 1) { "Noncontiguous revision numbers" }
            val prototypes = dao.prototypes(id, revision.number).map { Prototype(it.order, it.text) }
            validate(RecurrenceSpec(revision.anchorDate, revision.frequency, revision.title, template.listId,
                revision.priority, revision.shortNote, revision.reminderTime, prototypes))
            require(revision.effectiveFrom.year in 1..9999)
            if (index == 0) require(revision.effectiveFrom <= revision.anchorDate)
            val next = revisions.getOrNull(index + 1)
            require(revision.effectiveUntil == next?.effectiveFrom)
            if (next != null) require(next.effectiveFrom >= revision.effectiveFrom)
            revision.reconciledThrough?.let {
                require(it >= revision.effectiveFrom && (revision.effectiveUntil == null || it < revision.effectiveUntil)) { "Cursor outside interval" }
            }
            RevisionWithChecklist(revision, prototypes)
        }
        Recurrence(template, result)
    }

    fun exclusions(id: String): List<RecurrenceExclusion> = transaction {
        val series = read(id)
        dao.exclusions(id).also { rows -> rows.forEach { row ->
            require(series.revisions.any { SlotProvenance.matches(it.revision.anchorDate, it.revision.frequency, row.slotDate) }) { "Invalid exclusion provenance" }
            require(database.tasks().forSlot(id, row.slotDate) == null) { "Exclusion conflicts with a live slot" }
        } }
    }

    fun create(spec: RecurrenceSpec): Recurrence = transaction {
        val valid = validate(spec)
        var id: String
        do { id = UUID.randomUUID().toString() } while (dao.find(id) != null || database.tasks().find(id) != null ||
            database.lists().find(id) != null || database.subtasks().find(id) != null || database.notes().find(id) != null)
        val time = now()
        dao.insert(RecurrenceTemplate(id, valid.listId!!, true, time, time))
        insertRevision(id, 1, minOf(valid.anchorDate, today()), valid)
        read(id)
    }

    /** Commit supplied due slots and cursor together. Reservations are skipped; duplicate input is invalid. */
    fun writeKnownBatch(id: String, revisionNumber: Int, slots: List<LocalDate>, through: LocalDate): List<Task> =
        writeBatch(id, revisionNumber, slots, through, today())

    private fun writeBatch(id: String, revisionNumber: Int, slots: List<LocalDate>, through: LocalDate, dueDay: LocalDate): List<Task> = transaction {
        val series = read(id)
        require(series.template.enabled) { "Disabled series cannot write a batch" }
        val source = series.revisions.singleOrNull { it.revision.number == revisionNumber }
            ?: throw IllegalArgumentException("Unknown source revision")
        val revision = source.revision
        require(through.year in 1..9999 && through <= dueDay && through >= revision.effectiveFrom &&
            (revision.effectiveUntil == null || through < revision.effectiveUntil)) { "Batch cursor outside due interval" }
        require(revision.reconciledThrough == null || through >= revision.reconciledThrough) { "Cursor cannot move backward" }
        require(slots.distinct().size == slots.size) { "Duplicate batch slots" }
        slots.forEach { slot ->
            require(slot.year in 1..9999 && slot >= revision.effectiveFrom && slot <= through &&
                (revision.effectiveUntil == null || slot < revision.effectiveUntil) &&
                SlotProvenance.matches(revision.anchorDate, revision.frequency, slot)) { "Invalid original slot" }
        }
        val created = slots.mapNotNull { slot ->
            if (dao.excluded(id, slot) > 0 || database.tasks().forSlot(id, slot) != null) null else {
                val task = TaskRepository(database, { now().toEpochMilli() }, { clock.zone }).create(TaskContent(
                    revision.title, series.template.listId, slot, revision.priority, revision.shortNote, revision.reminderTime))
                source.checklist.forEach { SubtaskRepository(database).createGenerated(task.id, it.text) }
                task.copy(seriesId = id, slotDate = slot, sourceRevision = revisionNumber).also { database.tasks().update(it) }
            }
        }
        dao.updateRevision(revision.copy(reconciledThrough = through))
        created
    }

    /** Reconcile all stored series through one captured local day; removed/disabled series are skipped. */
    fun reconcile(): Int {
        val day = today()
        val ids = transaction { dao.ids() }
        return ids.fold(0) { total, id -> Math.addExact(total, reconcileSeries(id, day)) }
    }

    /** Returns newly created occurrences, after fully processing the due range (not a partial page). */
    fun reconcile(id: String): Int = reconcileSeries(id, today())

    private fun reconcileSeries(id: String, day: LocalDate): Int {
        require(day.year in 1..9999) { "Due day outside years 0001–9999" }
        var created = 0
        while (true) {
            // Re-read boundaries/cursor under the same transaction as the write. Concurrent replay,
            // edits, disabling and deletion cannot commit stale progress or resurrect removed rows.
            val batch = transaction {
                if (dao.find(id) == null) return@transaction null
                val series = read(id)
                if (!series.template.enabled) return@transaction null
                for (source in series.revisions) {
                    val revision = source.revision
                    val end = minOf(day, revision.effectiveUntil?.minusDays(1) ?: day)
                    val cursor = revision.reconciledThrough
                    if (revision.effectiveFrom > end || (cursor != null && cursor >= end)) continue
                    val from = cursor?.plusDays(1) ?: revision.effectiveFrom
                    // Bound one transaction's work, but keep looping until every retained interval
                    // is processed. Nonslot dates advance the cursor too, including before an anchor.
                    val through = minOf(from.plusDays(365), end)
                    val slots = RecurrenceCalendar.slots(revision.anchorDate, revision.frequency, from, through)
                    return@transaction writeBatch(id, revision.number, slots, through, day).size
                }
                null
            } ?: break
            created = Math.addExact(created, batch)
        }
        return created
    }

    fun edit(id: String, spec: RecurrenceSpec): Recurrence = transaction {
        val existing = read(id)
        val valid = validate(spec)
        val old = existing.revisions.last()
        val previous = old.revision
        val oldSpec = RecurrenceSpec(previous.anchorDate, previous.frequency, previous.title, existing.template.listId,
            previous.priority, previous.shortNote, previous.reminderTime, old.checklist)
        if (valid == oldSpec) existing else {
            val date = today()
            require(date >= previous.effectiveFrom) { "An edit cannot precede the current revision boundary" }
            val scheduleChanged = valid.anchorDate != previous.anchorDate || valid.frequency != previous.frequency
            if (scheduleChanged) require(valid.anchorDate >= date) { "New schedule anchor must be today or later" }
            val bounded = previous.reconciledThrough?.let { minOf(it, date.minusDays(1)) }?.takeIf { it >= previous.effectiveFrom }
            dao.updateRevision(previous.copy(effectiveUntil = date, reconciledThrough = bounded))
            val number = Math.addExact(previous.number, 1)
            insertRevision(id, number, date, valid)
            val time = now()
            dao.update(existing.template.copy(listId = valid.listId!!, updatedAt = maxOf(existing.template.updatedAt, time)))
            database.tasks().forSeries(id).filter { it.completedAt == null && !it.isOverride }.forEach { task ->
                if (scheduleChanged && requireNotNull(task.slotDate) >= date) {
                    database.tasks().delete(task.id) // Supersession is not user deletion: no exclusion.
                } else if (!scheduleChanged && task.scheduledDate == date) {
                    database.tasks().update(task.copy(listId = valid.listId, title = valid.title, priority = valid.priority,
                        shortNote = valid.shortNote, reminderTime = valid.reminderTime, sourceRevision = number,
                        updatedAt = maxOf(task.updatedAt, time)))
                    database.subtasks().deleteForParent(task.id)
                    valid.checklist.forEach { SubtaskRepository(database).createGenerated(task.id, it.text) }
                }
            }
            read(id)
        }
    }

    fun setEnabled(id: String, enabled: Boolean): Recurrence = transaction {
        val existing = read(id)
        if (existing.template.enabled != enabled) dao.update(existing.template.copy(enabled = enabled,
            updatedAt = maxOf(existing.template.updatedAt, now())))
        read(id)
    }

    fun deleteOccurrence(taskId: String) = transaction {
        val task = database.tasks().find(taskId) ?: throw NoSuchElementException("Unknown occurrence ID: $taskId")
        val id = requireNotNull(task.seriesId) { "Task is not a recurring occurrence" }
        val slot = requireNotNull(task.slotDate)
        val source = read(id).revisions.single { it.revision.number == task.sourceRevision }.revision
        require(SlotProvenance.matches(source.anchorDate, source.frequency, slot)) { "Invalid preserved slot provenance" }
        database.tasks().delete(taskId)
        dao.exclude(RecurrenceExclusion(id, slot))
    }

    fun delete(id: String) = transaction {
        read(id)
        database.tasks().deleteForSeries(id)
        dao.delete(id) // FK cascades remove revisions/prototypes/exclusions, after occurrences are gone.
    }

    internal fun deleteOwnedAndReserveExternal(listId: String) {
        dao.ownedBy(listId).forEach { delete(it.id) }
        database.tasks().forList(listId).forEach {
            if (it.seriesId != null) deleteOccurrence(it.id) else database.tasks().delete(it.id)
        }
    }

    private fun insertRevision(id: String, number: Int, from: LocalDate, spec: RecurrenceSpec) {
        dao.insertRevision(RecurrenceRevision(id, number, from, null, spec.anchorDate, spec.frequency, spec.title,
            spec.priority, spec.shortNote, spec.reminderTime, null))
        spec.checklist.forEach { dao.insertPrototype(ChecklistPrototype(id, number, it.order, it.text)) }
    }

    private fun validate(spec: RecurrenceSpec): RecurrenceSpec {
        require(spec.anchorDate.year in 1..9999) { "Anchor outside years 0001–9999" }
        require(spec.frequency in setOf("daily", "weekly", "monthly")) { "Unknown frequency" }
        require(spec.priority in setOf("high", "medium", "low")) { "Unknown priority" }
        spec.shortNote?.let { StoredText.validate(it) }
        spec.reminderTime?.let { require(it.second == 0 && it.nano == 0) { "Reminder uses minute precision" } }
        val listId = spec.listId ?: database.lists().defaultList()!!.id
        require(database.lists().find(listId) != null) { "Unknown owning list" }
        val checklist = spec.checklist.mapIndexed { index, item ->
            require(item.order == index.toLong()) { "Noncontiguous prototype order" }
            item.copy(text = StoredText.label(item.text))
        }
        return spec.copy(listId = listId, title = StoredText.label(spec.title), checklist = checklist)
    }
    private fun today(): LocalDate = LocalDate.now(clock)
    private fun now(): Instant = Instant.ofEpochMilli(clock.millis()).also {
        require(it >= Instant.ofEpochMilli(-62135596800000L) && it <= Instant.ofEpochMilli(253402300799999L))
    }
    private fun <T> transaction(action: () -> T): T = database.runInTransaction(Callable { action() })
}

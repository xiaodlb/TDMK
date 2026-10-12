package com.xiaodlb.tdmk.data

import androidx.room.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Entity(tableName = "templates", foreignKeys = [ForeignKey(entity = TaskList::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("listId")])
data class RecurrenceTemplate(@PrimaryKey val id: String, val listId: String, val enabled: Boolean, val createdAt: Instant, val updatedAt: Instant)

@Entity(tableName = "template_revisions", primaryKeys = ["seriesId", "number"], foreignKeys = [ForeignKey(entity = RecurrenceTemplate::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)])
data class RecurrenceRevision(
    val seriesId: String, val number: Int, val effectiveFrom: LocalDate, val effectiveUntil: LocalDate?,
    val anchorDate: LocalDate, val frequency: String, val title: String, val priority: String,
    val shortNote: String?, val reminderTime: LocalTime?, val reconciledThrough: LocalDate?,
)

@Entity(tableName = "checklist_prototypes", primaryKeys = ["seriesId", "revisionNumber", "order"], foreignKeys = [ForeignKey(entity = RecurrenceRevision::class, parentColumns = ["seriesId", "number"], childColumns = ["seriesId", "revisionNumber"], onDelete = ForeignKey.CASCADE)])
data class ChecklistPrototype(val seriesId: String, val revisionNumber: Int, val order: Long, val text: String)

@Entity(tableName = "exclusions", primaryKeys = ["seriesId", "slotDate"], foreignKeys = [ForeignKey(entity = RecurrenceTemplate::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)])
data class RecurrenceExclusion(val seriesId: String, val slotDate: LocalDate)

data class Prototype(val order: Long, val text: String)
data class RecurrenceSpec(
    val anchorDate: LocalDate, val frequency: String, val title: String,
    val listId: String? = null, val priority: String = "medium", val shortNote: String? = null,
    val reminderTime: LocalTime? = null, val checklist: List<Prototype> = emptyList(),
)
data class RevisionWithChecklist(val revision: RecurrenceRevision, val checklist: List<Prototype>)
data class Recurrence(val template: RecurrenceTemplate, val revisions: List<RevisionWithChecklist>)

/** Checks one known original slot, not schedule enumeration or catch-up. Cutoffs do not erase provenance. */
object SlotProvenance {
    fun matches(anchor: LocalDate, frequency: String, slot: LocalDate): Boolean {
        require(frequency in setOf("daily", "weekly", "monthly")) { "Unknown frequency" }
        if (slot < anchor) return false
        return when (frequency) {
            "daily" -> true
            "weekly" -> ChronoUnit.DAYS.between(anchor, slot) % 7 == 0L
            else -> slot.dayOfMonth == minOf(anchor.dayOfMonth, slot.lengthOfMonth())
        }
    }
}

@Dao
internal interface RecurrenceDao {
    @Query("SELECT id FROM templates ORDER BY id") fun ids(): List<String>
    @Insert fun insert(template: RecurrenceTemplate)
    @Update fun update(template: RecurrenceTemplate)
    @Query("SELECT * FROM templates WHERE id = :id") fun find(id: String): RecurrenceTemplate?
    @Query("SELECT * FROM templates WHERE listId = :listId") fun ownedBy(listId: String): List<RecurrenceTemplate>
    @Query("DELETE FROM templates WHERE id = :id") fun delete(id: String)
    @Insert fun insertRevision(revision: RecurrenceRevision)
    @Update fun updateRevision(revision: RecurrenceRevision)
    @Query("SELECT * FROM template_revisions WHERE seriesId = :id ORDER BY number") fun revisions(id: String): List<RecurrenceRevision>
    @Insert fun insertPrototype(prototype: ChecklistPrototype)
    @Query("SELECT * FROM checklist_prototypes WHERE seriesId = :id AND revisionNumber = :number ORDER BY `order`") fun prototypes(id: String, number: Int): List<ChecklistPrototype>
    @Insert fun exclude(exclusion: RecurrenceExclusion)
    @Query("SELECT * FROM exclusions WHERE seriesId = :id ORDER BY slotDate") fun exclusions(id: String): List<RecurrenceExclusion>
    @Query("SELECT COUNT(*) FROM exclusions WHERE seriesId = :id AND slotDate = :slot") fun excluded(id: String, slot: LocalDate): Int
}

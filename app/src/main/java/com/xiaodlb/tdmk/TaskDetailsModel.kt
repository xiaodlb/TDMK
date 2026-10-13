package com.xiaodlb.tdmk

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.xiaodlb.tdmk.data.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.Clock
import java.util.concurrent.Callable
import java.util.UUID

internal data class ChecklistDraft(val key: String, val savedId: String?, val text: String, val checked: Boolean) {
    fun label(): String = try { StoredText.label(text) } catch(_: IllegalArgumentException) {
        throw IllegalArgumentException("Enter a valid, nonblank checklist item.")
    }
}

internal data class TaskDraft(val title: String, val listId: String, val date: String, val priority: String, val note: String) {
    companion object {
        fun from(task: Task) = TaskDraft(task.title, task.listId, task.scheduledDate?.toString().orEmpty(), task.priority, task.shortNote.orEmpty())
    }
    fun content(saved: Task, listIds: Set<String>): TaskContent {
        val validTitle = try { StoredText.label(title) } catch(_: IllegalArgumentException) { throw IllegalArgumentException("Enter a valid, nonblank title.") }
        require(listId in listIds) { "The selected list no longer exists. Choose an existing list." }
        require(priority in setOf("high", "medium", "low")) { "Choose High, Medium or Low priority." }
        val dateValue = if(date.isEmpty()) null else try {
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date))
            LocalDate.parse(date).also { require(it.year in 1..9999) }
        } catch(_: Exception) { throw IllegalArgumentException("Enter a real date as YYYY-MM-DD, years 0001–9999, or clear the date.") }
        try { StoredText.validate(note) } catch(_: IllegalArgumentException) { throw IllegalArgumentException("The short note contains invalid Unicode. Correct the note.") }
        return TaskContent(validTitle, listId, dateValue, priority,
            if(note == saved.shortNote.orEmpty()) saved.shortNote else note,
            if(dateValue == null) null else saved.reminderTime)
    }
}

/** Retained only for configuration changes; process restart may discard an unsaved draft. */
class TaskDetailsModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    var selectedId by mutableStateOf<String?>(null); private set
    var saved by mutableStateOf<Task?>(null); private set
    internal var draft by mutableStateOf<TaskDraft?>(null); private set
    var lists by mutableStateOf<List<TaskList>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var pending by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var returnFocusId by mutableStateOf<String?>(null); private set
    var series by mutableStateOf<Recurrence?>(null); private set
    var frequency by mutableStateOf("one_time"); private set
    var seriesScope by mutableStateOf(false); private set
    var confirmation by mutableStateOf<String?>(null); private set
    var preview by mutableStateOf<List<LocalDate>>(emptyList()); private set
    fun chooseFrequency(value: String) { if(!pending) { frequency = value; error = null; updatePreview() } }
    fun chooseScope(wholeSeries: Boolean) { if(!pending) {
        if(seriesScope != wholeSeries) draft = if(wholeSeries) series?.revisions?.last()?.revision?.let { revision ->
            TaskDraft(revision.title, series!!.template.listId, revision.anchorDate.toString(), revision.priority, revision.shortNote.orEmpty())
        } else saved?.let(TaskDraft::from)
        if(seriesScope != wholeSeries) children = if(wholeSeries) series?.revisions?.last()?.checklist?.map {
            ChecklistDraft(UUID.randomUUID().toString(), null, it.text, false)
        }.orEmpty() else savedChildren.map { ChecklistDraft(it.id, it.id, it.text, it.isCompleted) }
        seriesScope = wholeSeries; error = null; updatePreview()
    } }
    fun cancelConfirmation() { if(!pending) confirmation = null }
    fun requestDelete() { if(!pending) confirmation = "delete_scope" }
    private fun updatePreview() {
        val date = try { LocalDate.parse(draft?.date ?: "") } catch(_: Exception) { null }
        preview = if(date == null || frequency == "one_time") emptyList() else try {
            RecurrenceCalendar.slots(date, frequency, date, minOf(date.plusMonths(4), LocalDate.MAX)).take(3)
        } catch(_: Exception) { emptyList() }
    }
    private var savedChildren = emptyList<Subtask>()
    internal var children by mutableStateOf<List<ChecklistDraft>>(emptyList()); private set
    var newChildText by mutableStateOf(""); private set
    internal var deleting by mutableStateOf<ChecklistDraft?>(null); private set
    fun editNewChild(text: String) { if(!pending) { newChildText = text; error = null } }
    fun addChild() {
        if(pending) return
        val child = ChecklistDraft(UUID.randomUUID().toString(), null, newChildText, false)
        try { child.label() } catch(failure: IllegalArgumentException) { error = failure.message; return }
        children = children + child; newChildText = ""; error = null
    }
    internal fun editChild(key: String, text: String? = null, checked: Boolean? = null) {
        if(!pending) { children = children.map { if(it.key == key) it.copy(text = text ?: it.text, checked = checked ?: it.checked) else it }; error = null }
    }
    internal fun askDelete(child: ChecklistDraft) { if(!pending) deleting = child }
    fun cancelDelete() { deleting = null }
    fun confirmDelete() { if(!pending) { children = children.filterNot { it.key == deleting?.key }; deleting = null } }
    fun focusReturned() { returnFocusId = null }
    fun open(id: String, provider: DatabaseProvider) {
        if(pending) return
        loadJob?.cancel()
        selectedId = id; saved = null; draft = null; series = null; frequency = "one_time"; seriesScope = false; confirmation = null; preview = emptyList(); lists = emptyList(); children = emptyList(); savedChildren = emptyList(); newChildText = ""; deleting = null; error = null; loading = true; returnFocusId = null
        loadJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { provider.getDatabase().runInTransaction(Callable {
                    val task = provider.getTaskRepository().read(id)
                    Pair(Triple(task, provider.getListRepository().readAll(), provider.getSubtaskRepository().readForParent(id)),
                        task.seriesId?.let { provider.getRecurrenceRepository().read(it) })
                }) }
                saved = result.first.first; lists = result.first.second; draft = TaskDraft.from(result.first.first)
                series = result.second
                frequency = result.second?.revisions?.last()?.revision?.frequency ?: "one_time"
                savedChildren = result.first.third
                children = result.first.third.map { ChecklistDraft(it.id, it.id, it.text, it.isCompleted) }
                updatePreview()
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error = if(failure is NoSuchElementException) "This task no longer exists. Dismiss or retry." else "Could not load task. Retry or dismiss." }
            finally { if(selectedId == id) loading = false }
        }
    }
    internal fun edit(value: TaskDraft) { if(!pending) { draft = value; error = null; updatePreview() } }
    fun dismiss() {
        if(pending) return
        loadJob?.cancel(); returnFocusId = selectedId; selectedId = null; saved = null; draft = null; series = null; confirmation = null; children = emptyList(); savedChildren = emptyList(); newChildText = ""; deleting = null; error = null; loading = false
    }
    fun save(provider: DatabaseProvider) {
        if(pending || loading) return
        val original = saved ?: return
        val editing = draft ?: return
        val checklist = children.toList()
        val originalChildren = savedChildren.toList()
        val selectedFrequency = frequency
        val whole = seriesScope
        if(series != null && selectedFrequency == "one_time" && confirmation != "pause_confirmed") { confirmation = "pause"; return }
        try {
            val proposed = editing.content(original, lists.map { it.id }.toSet())
            if(series == null && selectedFrequency != "one_time") require(proposed.scheduledDate != null) { "Choose a scheduled date before starting recurrence." }
            if(series != null && whole && selectedFrequency != "one_time") require(proposed.scheduledDate != null) { "Choose a series start date." }
            checklist.forEach { it.label() }
            require(newChildText.isEmpty()) { "Use Add item to include the new checklist entry, or clear it before Save." }
        }
        catch(failure: IllegalArgumentException) { confirmation = null; error = failure.message; return }
        confirmation = null
        pending = true; error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Check current record/lists and update atomically; deleted identities are never replaced.
                    provider.getDatabase().runInTransaction(Callable {
                        val current = provider.getTaskRepository().read(original.id)
                        require(current == original) { "This task changed. Dismiss and reopen before saving." }
                        val currentLists = provider.getListRepository().readAll()
                        val childRepository = provider.getSubtaskRepository()
                        require(childRepository.readForParent(original.id) == originalChildren) { "The saved checklist changed or an item was deleted. Dismiss and reopen to reload it; your draft was not saved." }
                        val content = editing.content(current, currentLists.map { it.id }.toSet())
                        val recurrence = provider.getRecurrenceRepository()
                        val savedSeries = series
                        if(savedSeries != null) require(recurrence.read(savedSeries.template.id) == savedSeries) { "This series changed. Dismiss and reopen before saving." }
                        if(savedSeries != null && selectedFrequency == "one_time") {
                            recurrence.setEnabled(savedSeries.template.id, false)
                            return@Callable
                        }
                        if(savedSeries != null && !whole) require(selectedFrequency == savedSeries.revisions.last().revision.frequency) { "Choose whole series to change frequency." }
                        if(savedSeries == null || !whole && selectedFrequency != "one_time") provider.getTaskRepository().update(original.id, content)
                        if(savedSeries == null || !whole) {
                            originalChildren.filter { child -> checklist.none { it.savedId == child.id } }.forEach { childRepository.delete(it.id) }
                            checklist.forEach { child ->
                                if(child.savedId == null) childRepository.createDraft(original.id, child.label(), child.checked)
                                else {
                                    childRepository.edit(child.savedId, child.label())
                                    childRepository.setChecked(child.savedId, child.checked)
                                }
                            }
                        }
                        if(savedSeries == null && selectedFrequency != "one_time") recurrence.convert(original.id, RecurrenceSpec(
                            requireNotNull(content.scheduledDate), selectedFrequency, content.title, content.listId,
                            content.priority, content.shortNote, content.reminderTime,
                            checklist.mapIndexed { index, item -> Prototype(index.toLong(), item.label()) }))
                        else if(savedSeries != null && whole && selectedFrequency != "one_time") {
                            recurrence.edit(savedSeries.template.id, RecurrenceSpec(requireNotNull(content.scheduledDate), selectedFrequency,
                                content.title, content.listId, content.priority, content.shortNote, savedSeries.revisions.last().revision.reminderTime,
                                checklist.mapIndexed { index, item -> Prototype(index.toLong(), item.label()) }))
                            recurrence.reconcile(savedSeries.template.id)
                        }
                    })
                }
                pending = false; dismiss()
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error = when(failure) {
                is NoSuchElementException -> "This task no longer exists. Your draft was not saved. Dismiss the sheet."
                is IllegalArgumentException -> failure.message ?: "Correct the task fields and try Save again."
                else -> "Could not save task. Your draft is kept; try Save again."
            } } finally { pending = false }
        }
    }
    fun confirmPause(provider: DatabaseProvider) { if(confirmation == "pause") { confirmation = "pause_confirmed"; save(provider) } }
    fun resume(provider: DatabaseProvider) {
        val existing = series ?: return
        if(pending) return
        if(confirmation != "resume") { confirmation = "resume"; return }
        confirmation = null; pending = true; error = null
        scope.launch {
            try { withContext(Dispatchers.IO) { provider.getDatabase().runInTransaction(Callable {
                require(provider.getRecurrenceRepository().read(existing.template.id) == existing) { "This series changed. Dismiss and reopen." }
                provider.getRecurrenceRepository().setEnabled(existing.template.id, true)
                provider.getRecurrenceRepository().reconcile(existing.template.id)
            }) }; pending = false; dismiss() }
            catch(failure: Exception) { error = failure.message ?: "Could not resume series. Try again." }
            finally { pending = false }
        }
    }
    fun confirmDelete(provider: DatabaseProvider, whole: Boolean) {
        if(confirmation != "delete_scope" && confirmation != "delete_confirm") return
        if(confirmation == "delete_scope") { seriesScope = whole; confirmation = "delete_confirm"; return }
        val current = saved ?: return
        val existing = series ?: return
        confirmation = null; pending = true; error = null
        scope.launch {
            try { withContext(Dispatchers.IO) { provider.getDatabase().runInTransaction(Callable {
                require(provider.getTaskRepository().read(current.id) == current) { "This task changed. Dismiss and reopen." }
                require(provider.getRecurrenceRepository().read(existing.template.id) == existing) { "This series changed. Dismiss and reopen." }
                if(whole) provider.getRecurrenceRepository().delete(existing.template.id)
                else provider.getRecurrenceRepository().deleteOccurrence(current.id)
            }) }; pending = false; dismiss() }
            catch(failure: Exception) { error = failure.message ?: "Could not delete. Try again." }
            finally { pending = false }
        }
    }
    override fun onCleared() { scope.cancel() }
}

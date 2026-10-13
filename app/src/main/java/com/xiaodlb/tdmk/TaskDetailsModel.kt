package com.xiaodlb.tdmk

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.xiaodlb.tdmk.data.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.util.concurrent.Callable

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
    fun focusReturned() { returnFocusId = null }
    fun open(id: String, provider: DatabaseProvider) {
        if(pending) return
        loadJob?.cancel()
        selectedId = id; saved = null; draft = null; lists = emptyList(); error = null; loading = true; returnFocusId = null
        loadJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { provider.getDatabase().runInTransaction(Callable {
                    provider.getTaskRepository().read(id) to provider.getListRepository().readAll()
                }) }
                saved = result.first; lists = result.second; draft = TaskDraft.from(result.first)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error = if(failure is NoSuchElementException) "This task no longer exists. Dismiss or retry." else "Could not load task. Retry or dismiss." }
            finally { if(selectedId == id) loading = false }
        }
    }
    internal fun edit(value: TaskDraft) { if(!pending) { draft = value; error = null } }
    fun dismiss() {
        if(pending) return
        loadJob?.cancel(); returnFocusId = selectedId; selectedId = null; saved = null; draft = null; error = null; loading = false
    }
    fun save(provider: DatabaseProvider) {
        if(pending || loading) return
        val original = saved ?: return
        val editing = draft ?: return
        try { editing.content(original, lists.map { it.id }.toSet()) }
        catch(failure: IllegalArgumentException) { error = failure.message; return }
        pending = true; error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Check current record/lists and update atomically; deleted identities are never replaced.
                    provider.getDatabase().runInTransaction(Callable {
                        val current = provider.getTaskRepository().read(original.id)
                        val currentLists = provider.getListRepository().readAll()
                        provider.getTaskRepository().update(original.id, editing.content(current, currentLists.map { it.id }.toSet()))
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
    override fun onCleared() { scope.cancel() }
}

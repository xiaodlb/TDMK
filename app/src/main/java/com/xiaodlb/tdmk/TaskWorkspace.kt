package com.xiaodlb.tdmk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import com.xiaodlb.tdmk.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate

/** Reusable real-storage host; outer shell supplies scrolling and safe/IME insets. */
@Composable
fun TaskWorkspace(provider: DatabaseProvider, context: TaskContext = TaskContext.Anytime, clock: Clock = Clock.systemDefaultZone(), onDetails: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var candidates by remember { mutableStateOf<List<Task>>(emptyList()) }
    var lists by remember { mutableStateOf<List<TaskList>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var draft by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var entryError by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var pendingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rowErrors by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var today by remember { mutableStateOf(LocalDate.now(clock)) }
    val owner = LocalContext.current as? LifecycleOwner
    DisposableEffect(owner, clock) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) today = LocalDate.now(clock) }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(clock) { while(isActive) { delay(60_000); today = LocalDate.now(clock) } }
    LaunchedEffect(provider, loadAttempt) {
        loadError = false
        try {
            val sources = withContext(Dispatchers.IO) { provider.getTaskRepository().viewCandidates() to provider.getListRepository().observe() }
            combine(sources.first, sources.second) { tasks, names -> tasks to names }.collect { (tasks, names) ->
                candidates = tasks; lists = names; loaded = true
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { loadError = true }
    }
    fun save() {
        if(saving || !loaded) return
        try { StoredText.label(draft) } catch(_: IllegalArgumentException) { entryError = "Enter a valid, nonblank title."; return }
        val title = draft
        saving = true; entryError = null; savedMessage = null
        scope.launch {
            try {
                val task = withContext(Dispatchers.IO) { provider.getTaskRepository().create(TaskContent(title, (context as? TaskContext.InList)?.id)) }
                draft = ""; savedMessage = "Task added to ${lists.firstOrNull { it.id == task.listId }?.name ?: "your list"}."
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { entryError = "Could not save task. Your title is kept; try Save again." }
            finally { saving = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShellSpacing.gap)) {
        if(loadError) {
            Text("Could not load tasks. Try again.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { loadAttempt++ }) { Text("Retry") }
        }
        if(!loaded) { if(!loadError) CircularProgressIndicator(); return@Column }
        QuickEntry(draft, saving, entryError, { draft = it; entryError = null; savedMessage = null }, ::save,
            { draft = ""; entryError = null; savedMessage = null; keyboard?.hide() })
        savedMessage?.let { Text(it, Modifier.testTag("entry_success").semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium) }
        Text(when(context) { TaskContext.Anytime -> "Anytime"; TaskContext.Today -> "Today"; TaskContext.Home -> "Overdue and today"; is TaskContext.InList -> lists.firstOrNull { it.id == context.id }?.name ?: "List" },
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val results = remember(candidates, context, today) { context.results(candidates, Clock.fixed(today.atStartOfDay(clock.zone).toInstant(), clock.zone)) }
        if(results.isEmpty()) Text("No tasks here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        results.forEach { task -> key(task.id) {
            TaskRow(task, lists.firstOrNull { it.id == task.listId }?.name ?: "List", task.id in pendingIds, rowErrors[task.id], { id ->
                if(id !in pendingIds) {
                    pendingIds = pendingIds + id; rowErrors = rowErrors - id
                    scope.launch {
                        try { withContext(Dispatchers.IO) {
                            val repository = provider.getTaskRepository()
                            if(repository.read(id).completedAt == null) repository.complete(id) else repository.reopen(id)
                        } } catch(cancelled: CancellationException) { throw cancelled }
                        catch(failure: Exception) { rowErrors = rowErrors + (id to if(failure is IllegalArgumentException && failure.message == "Complete all subtasks before their parent") "Complete all subtasks first. Task remains unfinished." else "Could not change completion. Try again.") }
                        finally { pendingIds = pendingIds - id }
                    }
                }
            }, onDetails)
        } }
    }
}

# Local application API

This documents the implemented Kotlin/Room interfaces. TDMK has no HTTP service, authentication endpoint or cloud API. Accounts and optional cloud sync are deferred. Backup/restore and notification scheduling remain backlog features; storing a reminder time does not schedule an alarm.

## Storage entry point

[`DatabaseProvider`](../app/src/main/java/com/xiaodlb/tdmk/data/DatabaseProvider.kt) owns the on-disk `tdmk.db`. `getDatabase(): TdmkDatabase`, `getListRepository(): ListRepository`, `getTaskRepository(): TaskRepository`, `getSubtaskRepository(): SubtaskRepository`, `getNoteRepository(): NoteRepository`, and `getRecurrenceRepository(): RecurrenceRepository` must be called on a worker thread. Repository constructors are internal; application callers obtain them through the provider. `close()` is for an owner shutting down every user, including tests.

[`TdmkDatabase`](../app/src/main/java/com/xiaodlb/tdmk/data/TdmkDatabase.kt) is Room schema version 6 with exported schemas. Explicit migrations cover 1→2→3→4→5→6. Opening validates before publishing the singleton; errors propagate and destructive migration is disabled. [`SettingsDao`](../app/src/main/java/com/xiaodlb/tdmk/data/Settings.kt) exposes `read(): Settings`, `observe(): Flow<Settings>`, and `setAppearance(appearance: Appearance): Int` through `database.settings()`. Appearance is SYSTEM, LIGHT or DARK; settings use singleton ID 1.

## Lists, tasks and checklists

[`ListRepository`](../app/src/main/java/com/xiaodlb/tdmk/data/ListRepository.kt):

```kotlin
fun initialize(): TaskList
fun readAll(): List<TaskList>
fun observe(): Flow<List<TaskList>>
fun read(id: String): TaskList
fun create(name: String): TaskList
fun rename(id: String, name: String): TaskList
fun reorder(customIds: List<String>): List<TaskList>
fun delete(id: String): List<TaskList>
```

One default list is retained and may be renamed. Reorder receives every custom list exactly once. Deletion preserves the default-list rule and removes owned data according to [behavior](behavior.md); UI confirmation belongs to the caller.

[`TaskRepository`](../app/src/main/java/com/xiaodlb/tdmk/data/TaskRepository.kt):

```kotlin
fun active(): Flow<List<Task>>
fun completed(): Flow<List<Task>>
fun viewCandidates(): Flow<List<Task>>
fun read(id: String): Task
fun create(content: TaskContent): Task
fun update(id: String, content: TaskContent): Task
fun setScheduledDate(id: String, date: LocalDate?): Task
fun complete(id: String): Task
fun reopen(id: String): Task
fun setCompletion(id: String, at: Instant?, day: LocalDate?): Task
fun delete(id: String)
```

[`TaskContent`](../app/src/main/java/com/xiaodlb/tdmk/data/Task.kt) carries `title`, nullable `listId` (default list when absent), nullable `scheduledDate`, `priority` (default `medium`), nullable `shortNote` and nullable `reminderTime`. Priorities are `high`, `medium`, `low`. Dates are years 0001–9999; reminders require a date and minute precision. Clearing a date clears its reminder. Completion requires checked subtasks and paired completion instant/day. Recurring edits preserve slot provenance and mark overrides where required; deleting an occurrence records an exclusion.

[`SubtaskRepository`](../app/src/main/java/com/xiaodlb/tdmk/data/SubtaskRepository.kt) exposes `read(id: String): Subtask`, `readForParent(taskId: String): List<Subtask>`, `create(taskId: String, text: String): Subtask`, `edit(id: String, text: String): Subtask`, `setChecked(id: String, checked: Boolean): Subtask`, `reorder(taskId: String, ids: List<String>): List<Subtask>`, and `delete(id: String): List<Subtask>`. Reorder requires every child exactly once. Adding an unchecked child or unchecking a child reopens a completed parent.

## Notes

[`NoteRepository`](../app/src/main/java/com/xiaodlb/tdmk/data/NoteRepository.kt) exposes `read(id: String): Note`, `readAll(): List<Note>`, `create(title: String, body: String): Note?`, `update(id: String, title: String, body: String): Note`, and `delete(id: String)`. `body` is the versioned rich-body JSON representation, not arbitrary HTML. A fully blank draft is discarded (`create` returns null); stored notes are independent of tasks.

[`RichBodyCodec`](../app/src/main/java/com/xiaodlb/tdmk/data/RichBodyCodec.kt) exposes `decode(raw: String): RichBody`, `encode(body: RichBody): String`, and `plainText(body: RichBody): String`. It validates blocks/runs and derives searchable text. See [note format](note-storage.md).

## Date views and recurrence

[`TaskViews(clock: Clock)`](../app/src/main/java/com/xiaodlb/tdmk/data/TaskViews.kt) exposes `today`, `overdue`, `anytime`, `upcoming`, `homeUpcoming`, `homeOverdueAndToday` (each receives `List<Task>` and returns `List<Task>`), `inList(tasks: List<Task>, listId: String): List<Task>`, and `history(tasks: List<Task>): List<CompletionGroup>`. Supply `viewCandidates`, including completed records, so captured completion-day eligibility can be evaluated. History groups older completions newest first. Upcoming covers the next seven days; Home upcoming ends Sunday.

[`RecurrenceSpec`](../app/src/main/java/com/xiaodlb/tdmk/data/Recurrence.kt) carries anchor date, `daily`/`weekly`/`monthly` frequency, title, optional owning list, priority, note, reminder time and ordered checklist prototypes. [`RecurrenceRepository`](../app/src/main/java/com/xiaodlb/tdmk/data/RecurrenceRepository.kt) exposes:

```kotlin
fun read(id: String): Recurrence
fun exclusions(id: String): List<RecurrenceExclusion>
fun create(spec: RecurrenceSpec): Recurrence
fun writeKnownBatch(id: String, revisionNumber: Int, slots: List<LocalDate>, through: LocalDate): List<Task>
fun reconcile(): Int
fun reconcile(id: String): Int
fun edit(id: String, spec: RecurrenceSpec): Recurrence
fun setEnabled(id: String, enabled: Boolean): Recurrence
fun deleteOccurrence(taskId: String)
fun delete(id: String)
```

Reconciliation returns the count of created occurrences. Series/slot uniqueness and recorded exclusions prevent duplicates and recreation after user deletion. Each occurrence has independent task/checklist state; edits create bounded revisions. [`RecurrenceCalendar.slots(anchor: LocalDate, frequency: String, from: LocalDate, through: LocalDate): List<LocalDate>`](../app/src/main/java/com/xiaodlb/tdmk/data/RecurrenceCalendar.kt) enumerates inclusive nominal dates with monthly day clamping. Clock injection makes reconciliation deterministic in tests.

## Errors and callers

Repositories validate inputs before writes and use Room transactions for multi-record changes. Unknown IDs in record lookup methods throw `NoSuchElementException`; invalid content/policies and foreign list references use `require` (`IllegalArgumentException`); worker-thread checks use `check` (`IllegalStateException`). Do not turn database open failures into silent resets. UI callers must dispatch blocking storage operations off the main thread and handle failures visibly. Read [data model](data-model.md), [behavior](behavior.md), [storage foundation](storage-foundation.md), and [testing guidelines](testing-guidelines.md) before changing these contracts.

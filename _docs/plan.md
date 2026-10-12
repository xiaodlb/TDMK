# Personal Android Task Planner — Project Scope

This document records the settled scope and later task-policy decisions. Explicit owner answers and choices made under the owner's 2026-10-12 delegation are distinguished in [behavior.md](behavior.md). Remaining non-task details at the end belong to later issues and will be resolved under that delegation.

## Purpose and platform

- A personal TO-DO planner with tasks and a separate notes area.
- Android is the first supported platform.
- The first version works offline without an account.
- Backup and restore are included in the first version.
- Optional accounts and cloud sync are planned for a later version.

## Tasks

### Organization

- Tasks belong to lists, including a default Inbox and custom lists.
- Tasks can optionally have a scheduled date.
- Scheduling uses dates only, with no task time slots or time blocks.
- Tasks without a date appear in their own list and in an Anytime view.
- Tasks sort by priority: High, Medium, then Low.
- Tasks with equal priority sort oldest created first.
- Priority levels are Low, Medium, and High.
- New tasks default to Medium priority unless the user selects another priority.
- A task added while viewing a list defaults to that list. Tasks added elsewhere default to Inbox.
- Inbox can be renamed while retaining its stable default-list identity; it cannot be deleted.

### Task details

- Title.
- List.
- Optional scheduled date.
- Priority.
- Optional short note within the task.
- Optional checklist of subtasks, each independently checkable.
- Optional individual reminder.
- Optional repetition: daily, weekly, or monthly.

### Overdue and completed tasks

- Unfinished tasks remain overdue until completed or rescheduled.
- A parent task can be completed only after all its subtasks are completed; attempting parent completion does not automatically complete unfinished subtasks.
- Completed tasks remain visible and crossed out for the rest of the completion day.
- On the next day, completed tasks disappear from active views and move to the completion log.
- The Log groups completed tasks by completion day, newest day first.
- Completed tasks can be reopened while preserving their scheduled date (or absence of a date) and subtask checked states. They return to active views according to the retained date.
- Unchecking a subtask of a completed parent automatically reopens the parent, preserving its scheduled date and all other subtask states. All subtasks must be checked before the parent can be completed again.

### Repeating tasks

- Support simple daily, weekly, and monthly schedules.
- Create each new occurrence on schedule, even when a previous occurrence remains unfinished.
- Unfinished previous occurrences remain overdue.
- Anchor recurrence to the start date: daily by local day, weekly by its weekday, monthly by its day number. Clamp missing monthly days to month end without changing the original anchor.
- Reconcile all due missed dates through today exactly once, preserving completion and occurrence exceptions; each new occurrence starts with unchecked subtasks.
- Single-occurrence edits, deletions, and reopening affect only that occurrence; deleted slots do not regenerate. Series edits apply prospectively from today, preserving completed history, past unfinished tasks, and overrides. Confirmed series deletion includes all occurrences and completed history.
- See [behavior.md](behavior.md) for schedule revisions, disabling recurrence, slot identity, and precise before/after examples.

### Reminders

- Reminders are configured individually for each task.
- The user chooses the priority and reminder time manually.
- A task must have a scheduled date before a reminder can be added.
- The reminder time is on the task's scheduled day.
- Priority does not automatically choose reminder timing or trigger repeated alerts.
- A reminder makes at most one notification attempt per configuration; past times and missed previous days do not replay or alert repeatedly on overdue tasks.
- Date changes retain reminder time on the new date and cancel the old alarm; date removal clears the reminder. Completion/deletion cancels alarms; reopening only re-arms future unconsumed times.
- Reminder times follow the current device timezone as local wall-clock times; clock changes recalculate future alarms without repeating consumed ones. DST gaps shift forward and overlaps use the earlier instant once.
- Notification/channel denial preserves saved data and shows blocked status. Exact-alarm access denial uses an approximate inexact fallback with possible OS delays. Re-enable only future unconsumed reminders, without backlog alerts; device validation is required during implementation.

### List management

- A Manage lists button appears at the end of the scrollable task tabs.
- List management supports creating, renaming, reordering, and deleting custom lists.
- Deleting a custom list also deletes its tasks, after confirmation; the stable default Inbox is protected. Deletion also stops its recurrence templates and removes their occurrences/overrides, with the consequences shown in confirmation.

## Separate notes

- Notes are independent of tasks and are not linked to them.
- Each note has a title and text content.
- Notes form one searchable collection, without folders or tags.
- Basic formatting includes bold, headings, and bullet lists.
- The Notes tab displays cards in a two-column grid with text previews.
- Selecting a note opens a full-screen editor.
- Task notes remain separate from this notes collection.
- Cards sort by saved modification time descending, then creation time descending, then stable ID ascending; opening unchanged content does not reorder it. Home shows the three most recent saved notes.
- Autosave after 500 ms idle and flush on leaving/backgrounding/export; report failures without silently discarding edits. Blank titles become Untitled when content exists; new entirely blank notes are discarded, existing notes cleared blank are retained as Untitled. Titles with empty bodies are allowed.

## Screens and navigation

### Main navigation

Four bottom tabs:

1. Home
2. Tasks
3. Notes
4. Log

Settings open from Home.

### Home

- The app opens to a dashboard.
- Show all overdue tasks and all tasks scheduled for today.
- Follow these with upcoming tasks and recent notes.
- Upcoming tasks on Home extend through the end of the current week.
- The week runs Monday through Sunday.
- Include a search field for Home content.

### Tasks

- Use scrollable tabs above the task content.
- Tab order: Today, Upcoming, Anytime, Inbox, then custom lists.
- The Inbox tab displays the current name of the stable default list when renamed.
- Today, Upcoming, and Anytime are date-based views across lists.
- Upcoming includes scheduled dates from tomorrow through today + 7 local calendar days, inclusive; Home's upcoming section still ends on Sunday.
- Custom list tabs display the tasks belonging to each list.
- Include a search field for Tasks content.
- Place Manage lists at the end of the tabs.

### Task creation and editing

- Use quick entry: enter a title first.
- Date, priority, reminder, short note, and subtasks can be added optionally.
- Task details open in a bottom sheet over the current screen.

### Notes

- Display notes as cards in a two-column grid.
- Include a search field for Notes content.
- Open notes in a full-screen editor with basic formatting controls.

### Log

- Display completed tasks grouped by completion day.
- Show the newest day first.
- Include a search field for completed tasks.

## Search

- Search covers tasks and separate notes.
- Each tab has its own search field, scoped to that tab's content.
- Log search covers completed tasks.
- The final navigation decision uses per-tab search fields rather than one global search screen.
- Match literal substrings in human-readable title/body fields using Unicode normalization and default full case folding; preserve accents, and trim/collapse whitespace for matching. Blank queries restore the ordinary view; no matches show an empty-result message.
- Home filters its ordinary task sections and three recent notes. Tasks searches active tasks across all lists/views, including completed today; Notes searches every saved separate note; Log searches historical completed tasks starting the next local day. Exact fields, examples, and exclusions are in [behavior.md](behavior.md).

## Deletion and recovery

- Confirm permanent deletion of individual tasks, subtasks, and notes; canceling changes no data or reminders. No in-app undo, trash, or recovery window.
- Task deletion includes its subtasks/short note/completion record and cancels its reminder. Notes remain independent. Recurring occurrence/series and custom-list deletion preserve the existing task-policy confirmations, exclusions, and protected default-list rules.
- A previous manual backup can restore only its full snapshot through replacement, not undo a single deletion.

## Backup and restore

- Export a version-1 portable UTF-8 JSON `.tdmk.json` snapshot through a user-selected Android document picker, with no account required. Local destinations work offline; selected external providers may have their own network requirements.
- Include lists, tasks/subtasks and completion history, formatted notes, recurrence templates/slots/exceptions, reminder configuration/consumed state, and user appearance mode. Exclude OS permissions/alarms/timezone, provider grants, transient UI state/drafts/indexes, and deleted record content except required recurrence exclusions.
- Restore validates the complete supported snapshot before explicit replacement confirmation; no merge. Unsupported, malformed, inconsistent, canceled, or failed pre-commit restores preserve current records/settings/schedules; replacement is atomic.
- Preserve occurrence identities/exclusions and reconcile newly due slots without duplicates. Rebuild only future unconsumed reminders under current device permissions/timezone, retaining known consumed-attempt safety state and never replaying past alarms.
- Backup limits are 50 MiB and 100,000 total entity records; export/provider failures are reported without changing app data or claiming a successful backup. Exact format/schema and implementation follow in later issues; [behavior.md](behavior.md) specifies examples, safety metadata, and realistic failure limitations.

## Visual style

- Minimal appearance with neutral colors, clean spacing, and subtle accents.
- Support both light and dark modes.
- Follow the phone's appearance setting by default.
- Allow a manual appearance override.

## First-version boundary

Included: offline Android app, dashboard, custom lists, date views, Anytime, overdue tracking, task priorities, task notes, subtasks, simple recurring tasks, individual reminders, separate formatted notes, per-tab search, completion log, list management, backup and restore, and light/dark appearance.

Deferred: accounts and optional cloud sync.

## Details still to decide

- The presentation of the dedicated Upcoming task tab.
- Screen mockups and a walkthrough of the daily workflow.

These remaining presentation/walkthrough details are not additional agreed features. Resolve them in their own groomed issues under the owner's delegation; task-policy choices for issue #2 and note/search/deletion/backup choices for issue #3 are fully recorded with decision sources in [behavior.md](behavior.md).

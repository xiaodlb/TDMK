# Personal Android Task Planner — Project Scope

This document records the decisions made during the project brainstorming session. Open questions at the end have not yet been decided.

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

### Repeating tasks

- Support simple daily, weekly, and monthly schedules.
- Create each new occurrence on schedule, even when a previous occurrence remains unfinished.
- Unfinished previous occurrences remain overdue.

### Reminders

- Reminders are configured individually for each task.
- The user chooses the priority and reminder time manually.
- A task must have a scheduled date before a reminder can be added.
- The reminder time is on the task's scheduled day.
- Priority does not automatically choose reminder timing or trigger repeated alerts.

### List management

- A Manage lists button appears at the end of the scrollable task tabs.
- List management supports creating, renaming, reordering, and deleting custom lists.
- Deleting a list also deletes its tasks, after confirmation.

## Separate notes

- Notes are independent of tasks and are not linked to them.
- Each note has a title and text content.
- Notes form one searchable collection, without folders or tags.
- Basic formatting includes bold, headings, and bullet lists.
- The Notes tab displays cards in a two-column grid with text previews.
- Selecting a note opens a full-screen editor.
- Task notes remain separate from this notes collection.

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
- How many recent notes appear on Home and how note cards are ordered.
- Editing and deleting one recurring occurrence versus the whole series.
- Monthly repetition when a month lacks the selected day.
- Reminder behavior for overdue tasks, missed reminders, and changed scheduled dates.
- Whether completed tasks can be reopened, their date/checklist behavior when reopened, and what happens if a subtask is unchecked after parent completion.
- Whether the built-in Inbox can be renamed or deleted.
- Deletion behavior for individual tasks and notes, including any undo or recovery.
- Backup format, export destination, and restore behavior.
- Exact search matching rules and Home search coverage.
- Screen mockups and a walkthrough of the daily workflow.

These remaining details are open questions, not additional agreed features.

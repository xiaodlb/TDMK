# Behavior decisions

This document records owner-approved behavior for TDMK. Task rules are the deliverable of [issue #2](https://github.com/xiaodlb/TDMK/issues/2); note/search/deletion-recovery/backup decisions belong to issue #3. Pending choices below are not defaults and must not be implemented as assumptions.

## Task rules already approved

These rules were approved by the owner during the scope discussion and are recorded in `_docs/plan.md`.

| Topic | Approved rule | Example |
| --- | --- | --- |
| Scheduling | A task has an optional date, without a task time slot or duration. | An undated task appears in its list and Anytime. |
| Priority | High, Medium, Low; views sort in that order. | High-priority tasks appear before Medium-priority tasks. |
| Default priority | Newly created tasks use Medium unless the user selects another priority. | Enter only a title: the saved task has Medium priority. |
| Equal-priority ordering | Tasks with the same priority appear oldest created first. | Before: task B was created Tuesday and task A Monday, both Medium. After: the view shows A before B; a later High task still appears before both. |
| Overdue | Unfinished tasks stay overdue until completed or rescheduled. | An unfinished Monday task remains overdue on Tuesday. |
| Completion | Completed tasks remain crossed out for the completion day; on the next day they disappear from active views and appear in Log. | Complete on Monday: crossed out Monday, historical on Tuesday. |
| Week | Weeks run Monday through Sunday; Home's upcoming section ends on Sunday. | On Friday, Home shows remaining upcoming dates through that Sunday. |
| Dedicated Upcoming range | The Tasks Upcoming tab includes scheduled dates from tomorrow through today + 7 local calendar days, inclusive. Home's upcoming section still ends on Sunday. | Before: on local Monday 2026-10-12, tasks are dated October 12, 13, 18, 19, and 20. After: Upcoming includes October 13, 18, and 19; October 12 is Today and October 20 is outside the range. Home's upcoming section ends October 18. |
| Recurrence | Daily, weekly, or monthly; a new occurrence is created on schedule even if its predecessor is unfinished. | A daily task can have yesterday's overdue occurrence and today's occurrence. |
| Reminders | Individually selected time on the task's scheduled day; a date is required. Priority remains independent. | A High task has no reminder unless the user explicitly sets one. |
| Task notes and subtasks | Tasks can have an optional short note and individually checkable subtasks. | Checking one subtask stores its own completion state. Parent-completion policy is pending below. |
| List defaults | Creation in a custom list uses that list; elsewhere it uses Inbox. | Quick entry in a date view adds the task to Inbox. |
| List deletion | Delete a list and its tasks only after confirmation. | Canceling confirmation retains the list and its tasks. Inbox protections are pending below. |

## Pending task decisions for issue #2

Ask the owner one question at a time and replace each pending entry with an approved rule and a before/after example. Issue #2 remains incomplete until all required answers are recorded.

- Parent completion and subtask behavior.
- Task reopening and its date/checklist behavior.
- Inbox rename and delete protections.
- Weekly/monthly anchors and missing monthly dates.
- Editing one recurring occurrence versus the series, including completed occurrences.
- Deleting one recurring occurrence versus the series, including completed occurrences and regeneration exclusions.
- Recurrence catch-up after missed intervals.
- Past or missed reminders and reminders on overdue tasks.
- Reminders when a task date changes or is removed.
- Clock/timezone changes and reminder interpretation.
- Notification permission/channel denial and unavailable exact-alarm permission.

## Approval record

Existing scope rules above: approved in the original owner scope discussion.

- Default priority: Medium. Explicitly approved by the owner for issue #2 on 2026-10-12 by selecting option 1.
- Equal-priority ordering: oldest created first. Explicitly approved by the owner for issue #2 on 2026-10-12 by selecting option 1.
- Dedicated Upcoming range: tomorrow through today + 7 local calendar days, inclusive; Home remains through Sunday. Explicitly approved by the owner for issue #2 on 2026-10-12 by selecting option 1.

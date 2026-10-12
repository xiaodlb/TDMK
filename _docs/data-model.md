# Local data contract (version 1)

Deliverable of [issue #4](https://github.com/xiaodlb/TDMK/issues/4), following the [scope](plan.md), [process](process.md), and completed [#2/#3 behavior decisions](behavior.md) at commit `3d7c89b`. The [PM handoff](https://github.com/xiaodlb/TDMK/issues/4#issuecomment-6128052952) fixes the ten acceptance criteria. This specifies logical Room/SQLite storage and portable records; it does not implement entities, migrations, repositories, recurrence, alarms, or export/restore.

Technical choices below (UUIDs, date-keyed slots, JSON block notes, field names, versioning, safety ledger and work marker) were made by the Engineer on 2026-10-12 under the owner's delegation. They are not individually owner-selected. Inbox rename, Medium default, oldest-created ordering, parent completion gate, and date ranges retain the explicit selections recorded in behavior.md. No account, server identity, backend, attachment, or synchronization protocol is required.

## Encodings and field notation

Every row below supplies type, nullability, creation default and validation. `required` means **no default**: the writer must supply a value, including during restore. A creation default is not permission to fill a missing backup property. Every documented portable property is required in JSON; nullable properties must explicitly contain null. No additional properties are accepted at any level in version 1. Local derived fields are excluded from JSON. Arrays preserve order only where explicitly stated.

| Type | Kotlin / SQLite / JSON encoding and validation |
| --- | --- |
| ID | UUID v4, cryptographically random on local record creation; canonical lowercase 36-character string / TEXT / string. Persist once, never regenerate on edit, migration or restore; reject duplicate IDs, retry a local generation collision. All record IDs across lists, tasks, subtasks, notes and templates are globally unique. |
| Text | String / TEXT / Unicode string; well-formed Unicode scalar sequence, UTF-8 on disk in backups (reject unpaired surrogates). Preserve original text except documented title/name trimming. No invented per-field length limit; backup limits still apply. |
| Label | Text with Unicode White_Space trimmed from both ends; must contain a non-White_Space scalar. Internal whitespace preserved. |
| Date | LocalDate / TEXT / `YYYY-MM-DD`, real proleptic Gregorian date, years 0001–9999. Never an instant, epoch-day timezone conversion, or UTC midnight. |
| Time | LocalTime / TEXT / `HH:mm`, 00:00–23:59 minute precision; seconds not accepted. |
| Instant | Instant / INTEGER epoch milliseconds / UTC string `YYYY-MM-DDTHH:mm:ss.SSSZ`; valid millisecond instant in years 0001–9999. Examples use this exact precision. |
| Bool | Boolean / INTEGER 0 or 1 / JSON boolean, never numeric JSON. |
| Order | Long / INTEGER / integer 0 through 9007199254740991 (portable exact JSON integer). Contiguous zero-based order within its owner, no duplicates. Reorder atomically. |
| Version | Int / INTEGER / positive JSON integer; supported values specified below, no floating values. |

All record creation instants use the injected current clock, all initial update instants equal creation. On a successful content/state edit set update to `max(previous update, current instant)`; unchanged saves do not bump it. Require update >= creation. Completion instant may precede creation/update after a wall-clock correction; do not falsify the actual event instant to satisfy an ordering assumption. Calendar day and timestamp range errors must fail clearly rather than wrap. New task order is priority High/Medium/Low, createdAt ascending, ID ascending; there is no persisted task display-position field.

## Entities, ownership and constraints

| Owner -> dependent | Cardinality and ownership / deletion |
| --- | --- |
| List -> Task | 1 to 0..many; every task has exactly one current list. Task.listId FK, delete RESTRICT: explicit transactional deletion computes affected series and tasks first. |
| List -> Template | 1 to 0..many; every template has exactly one owning list independent of an occurrence's edited list. Template.listId FK RESTRICT. |
| Template -> Task occurrence | 1 to 0..many; task.seriesId nullable FK RESTRICT, non-null only for occurrences; ordinary tasks have no template. Explicit series deletion removes all occurrences, including moved overrides/history, then template. |
| Task -> Subtask | 1 to 0..many; exactly one task owner, subtask.taskId FK CASCADE. Subtask has no independent reminder. |
| Template -> revision -> checklist prototype | 1 to 1..many ordered embedded revisions, each with 0..many ordered embedded items; owned content, not task subtasks. Removed only with its template. |
| Template -> Exclusion | 1 to 0..many, exclusion.seriesId FK CASCADE; minimal deleted-slot identity without deleted task content. |
| Note | Independent root; no task/list/template foreign keys or links. |
| Settings, LocalWork | Exactly one local singleton each, with no entity ownership. |
| ReminderAttempt | 0..many value-identity safety entries. **No FK to task/template**: detached consumed identities must survive replacement/deletion. These are not dangling entity references or retained deleted content. |

Enforce foreign keys in Room/SQLite and multi-record transactions; [SQLite foreign-key documentation](https://www.sqlite.org/foreignkeys.html) explains enforcement and CASCADE/RESTRICT. Exactly one list has isDefault=true; enforce uniqueness of true only (not of false). On first installation generate that list's ID once, name Inbox, order 0. Its identity travels in the snapshot: restore adopts the snapshot's one default ID, never identifies it by name or a hardcoded universal UUID. Renaming Inbox to General changes only name/update/shadow text; quick entry outside a custom list still selects its isDefault ID. Deletion of that list is rejected even after rename. Custom list order starts at 1; default list stays at 0, custom lists reorder 1..N.

Unique constraints: list order; (taskId, subtask order); (seriesId, slotDate) across live occurrences; (seriesId, slotDate) across exclusions; revision number within a template; prototype order within a revision; reminder composite identity below. Live occurrence and exclusion may not coexist for the same slot. List names need not be unique. Occurrence displayed dates need not be unique.

## Portable entity fields

Creation defaults apply to UI/domain construction only. All tables inherit the encoding rules above; `yes` in nullable is the only permission for null.

### Lists, tasks, subtasks

| Entity.field | Type | Nullable | Creation default | Validation / meaning |
| --- | --- | --- | --- | --- |
| List.id | ID | no | new ID | global primary identity |
| List.name | Label | no | Inbox for first default; required for custom | trimmed nonblank |
| List.isDefault | Bool | no | false except installation default | exactly one true |
| List.order | Order | no | required | default 0; custom 1..N |
| List.createdAt | Instant | no | now | retained identity creation |
| List.updatedAt | Instant | no | createdAt | common edit rule |
| Task.id | ID | no | new ID | global primary identity |
| Task.listId | ID | no | selected custom list, otherwise default ID | existing List |
| Task.title | Label | no | required | nonblank quick-entry title |
| Task.scheduledDate | Date | yes | null | displayed date; not original recurrence slot |
| Task.priority | Text enum | no | medium | high / medium / low only |
| Task.shortNote | Text | yes | null | optional plain task note, no rich markup; empty string valid |
| Task.reminderTime | Time | yes | null | non-null requires scheduledDate; date removal clears time |
| Task.completedAt | Instant | yes | null | paired with completionDate; only if every subtask checked |
| Task.completionDate | Date | yes | null | local calendar day at completion, paired with completedAt |
| Task.createdAt | Instant | no | now | generation time for new occurrences, not nominal slot midnight |
| Task.updatedAt | Instant | no | createdAt | common edit rule |
| Task.seriesId | ID | yes | null for ordinary task | existing Template when occurrence |
| Task.slotDate | Date | yes | null for ordinary task | immutable original nominal slot; paired with seriesId |
| Task.sourceRevision | Version | yes | null for ordinary task | paired with seriesId; number in that template which produced/currently supplies this unmodified record |
| Task.isOverride | Bool | no | false | ordinary tasks require false; true protects a single-occurrence edit/reopen, never reset by series reconciliation |
| Subtask.id | ID | no | new ID | global primary identity |
| Subtask.taskId | ID | no | required | existing Task |
| Subtask.text | Label | no | required | nonblank checklist text |
| Subtask.order | Order | no | append at owner count | contiguous within task |
| Subtask.isCompleted | Bool | no | false | independent checked state; uncheck/add unchecked under completed parent reopens it in same transaction |

No separate completed boolean, overdue flag, log copy, or subtask completion timestamp exists. CompletionDate is captured using the current device ZoneId when completedAt is recorded; it is **not recomputed** when travelling or editing the task. Compare it with today's current-device LocalDate to derive visibility; on backward date jumps a completionDate later than today stays crossed out in active views until that day passes, never leaks into historical Log. This boundary choice is technical completion bookkeeping under the delegation. Reopening clears both completion fields; completing again captures a new instant/day.

### Templates, revisions and exclusions

Template is one series root; revisions are a persisted JSON array in logical storage (Room converter to TEXT is one compatible representation). Revision/checklist objects have no independently generated record IDs. Array fields below have no SQL column per child; nested names are still persisted contract fields. Normalizing them later must preserve their composite identities.

| Entity.field | Type | Nullable | Creation default | Validation / meaning |
| --- | --- | --- | --- | --- |
| Template.id | ID | no | new ID | stable series ID |
| Template.listId | ID | no | creation list | existing List; current ownership for list deletion |
| Template.enabled | Bool | no | true | false stops future generation, keeps history/exclusions |
| Template.createdAt | Instant | no | now | common creation rule |
| Template.updatedAt | Instant | no | createdAt | content/revision/enable edits, not derived cursor progress |
| Template.revisions | Revision array | no | required | nonempty, revision ascending |
| Revision.number | Version | no | 1 initially, previous+1 on edit | contiguous 1..N; immutable identity within series |
| Revision.effectiveFrom | Date | no | initially min(anchorDate, creation local day); today on edit | inclusive boundary, nondecreasing (multiple edits today allowed); initial boundary <= anchorDate |
| Revision.effectiveUntil | Date | yes | null | exclusive next revision.effectiveFrom; final revision null; equal boundaries mean empty superseded revision |
| Revision.anchorDate | Date | no | required | first nominal due day; for schedule/start change must be today or later |
| Revision.frequency | Text enum | no | required | daily / weekly / monthly only; interval always one |
| Revision.title | Label | no | task title | nonblank copied content |
| Revision.priority | Text enum | no | medium | high / medium / low |
| Revision.shortNote | Text | yes | null | plain text, same as Task |
| Revision.reminderTime | Time | yes | null | local requested time copied on generation |
| Revision.checklist | Prototype array | no | [] | ordered 0..N-1, each item valid below |
| Revision.reconciledThrough | Date | yes | null | last completely processed inclusive nominal day in this revision interval; derived durable cursor, not portable |
| Prototype.order | Order | no | append at count | contiguous within revision |
| Prototype.text | Label | no | required | copied into fresh unchecked Subtask with new ID |
| Exclusion.seriesId | ID | no | required | existing Template, part of composite PK |
| Exclusion.slotDate | Date | no | required | original slot validated against at least one retained revision, part of PK |

Revision content excludes listId intentionally: template.listId is current owner; existing occurrences retain their own current list. A series move updates eligible today's unmodified tasks and uses the new list for future copies; past/completed/overridden tasks keep theirs. Revisions retain enough schedule provenance to validate old slots. On each edit append a revision and close the previous interval even for content-only changes; a content-only revision retains the original anchor/frequency. Never discard earlier revisions, including empty same-day ones. An occurrence/exclusion may reference a slot produced by an earlier revision even outside its now-shortened interval: that was a previously generated preserved identity. Validate its slot against that revision's recurrence rule and date >= anchor, not against the new cutoff. This permits today's completed slot to survive today's revision. Exclusions have no deleted title, list, short note, checklist or timestamp.

Initial-boundary correction on 2026-10-13 under the owner's routine-decision delegation: capture the current local creation day and set the first effectiveFrom to the earlier of that day and anchorDate. This permits prospective edits before a future start without decreasing revision boundaries; anchorDate still forbids slots before the chosen start. Do not recompute the stored boundary from createdAt using a later device timezone. For a past anchor retain anchorDate so missed dates remain reconcilable. Example: create October 13 with October 20 anchor, then edit content October 14: first interval becomes [October 13, October 14), the next starts October 14 with anchor October 20, and no early slot exists. Change the schedule on October 14 to anchor October 14: the new interval can produce that day's slot. This corrects technical bookkeeping, not the settled owner policy; see the [Engineer conflict record](https://github.com/xiaodlb/TDMK/issues/12#issuecomment-6133534566).

## Calendar and lifecycle

Date-only October 13 remains `2026-10-13` in UTC+5 and UTC+3; an undated task remains null. A dated task without a reminder has scheduledDate=`2026-10-13`, reminderTime=null. A reminder on that task with time `09:00` resolves to October 13 04:00Z in UTC+5 and 06:00Z in UTC+3 when still future. No timezone is stored on task/series scheduling.

At UTC+5 complete October 12 23:59:30: completedAt=`2026-10-12T18:59:30.000Z`, completionDate=`2026-10-12`. At local October 13 00:00 it becomes historical without writes. A second completion at 00:00:30 records `2026-10-12T19:00:30.000Z` and `2026-10-13`. Travel never rewrites either captured completion day. Refresh derived views on resume/local day/clock/timezone changes; log grouping is captured completionDate descending, then priority/creation/ID.

| State/action | Stored change and derived result | Reminder / checklist |
| --- | --- | --- |
| Active undated | completion fields null, scheduledDate null; list + Anytime | no reminder time permitted |
| Active dated | completion fields null; today/upcoming/list derived by date | optional individual time |
| Overdue | no stored overdue flag; unfinished scheduledDate < today | retains date until edit/completion; no daily replay |
| Complete | only if no unchecked children; store instant + local day | cancel alarm and posted notification; retain checklist/date |
| Completed today | completionDate=today; crossed out in eligible active views, all such tasks eligible Tasks search | no alarm |
| Historically completed | completionDate<today; excluded active views, Log grouped by completionDate | no midnight move/job |
| Reopen | clear both completion fields, retain date and all checked states; occurrence becomes override | arm only future unconsumed configuration; retained old date may become overdue |
| Uncheck/add unchecked subtask | atomically clear parent completion if completed, keep other children/date; occurrence override | same as reopen; re-completion blocked until all checked |
| Edit completed task | other fields can change without clearing completion, except unchecked child gate | still canceled; original completion instant/day preserved |
| Delete ordinary task | after permanent-delete confirmation, delete task + all children/history/short note | invalidate/cancel/dismiss; consumed identity retained without deleted content |
| Delete subtask | confirm permanent delete; compact order, preserve parent state unless separate reopening operation | no auto-completion, parent reminder unchanged; occurrence override |
| Delete occurrence | confirm this occurrence, atomically insert original-slot exclusion and delete task/children | cancel/dismiss; other slots untouched |
| Delete series | confirm all occurrences/history/overrides, delete them even in other lists, then exclusions/template | cancel all affected notifications/alarms |
| Delete custom list | confirm tasks/series/moved overrides; union tasks currently in list and all occurrences of templates owned by list; delete owned series/exclusions, then list | a task in deleted list from a surviving series gets its original-slot exclusion; this prevents regeneration into another list. Unrelated tasks in other lists survive |

No trash, undo or deleted-record body is stored. Canceling confirmations changes nothing. Explicit deletion transaction failure leaves old data and schedules intact. All changes affecting reminders set LocalWork.pending in the same transaction; OS operations follow commit. Task short notes and independent notes never cascade into each other.

## Recurrence reconciliation contract

Slot identity is the pair `(seriesId, slotDate)` where slotDate is the nominal date generated by the anchor: daily anchor+n days, weekly anchor+7n days, monthly anchor month+n with original day clamped to that month's last day. n>=0. Identity excludes revision number, display date, timezone, completion and task UUID. Reserve that pair with either the one live occurrence or the exclusion. Task UUID remains stable through ordinary edits; a superseded unmodified task removed for a schedule change may be replaced with a fresh UUID, but its slot pair never coexists twice.

Reconcile enabled templates through current-device today only, never create future active task records. Visit every eligible nominal date in each nonempty revision interval through today, starting after reconciledThrough (or at effectiveFrom/anchor). For each candidate: exclusion => skip; existing occurrence => preserve; otherwise create task/children from revision content and current template list, completed fields null, isOverride=false, children unchecked. Persist records and cursor advancement together per batch, advance through all visited days even if no schedule slot occurs, and never advance beyond an uncommitted batch. Backward day jumps do not erase cursor/reservations. Retries use unique slot constraints and a transaction, not only the cursor. Restore resets each cursor to null and replays all retained revision intervals through today against snapshot reservations. Disabled templates do not generate; their existing records remain.

Series edits take today's local date as effectiveFrom even when invoked from a historical record. Title/priority/note/reminder/checklist/list edits update only uncompleted unmodified generated records displayed today (original today's slot or retained nominal slot as applicable); past unfinished, completed and overrides stay fixed. Eligible task IDs/createdAt remain; checklist prototype replacement may create fresh subtask IDs with unchecked state, never auto-complete. Set sourceRevision to new number. Reset cursor for the new interval. Historical revision cursors are bounded by new exclusive cutoff.

Schedule/start changes remove unmodified unfinished old-schedule occurrences with original slots >=today, cancel their alarms, then introduce the new schedule with anchor>=today. Do not turn these superseded slots into user-deletion exclusions: they are no longer candidates in the superseded interval. Preserve completed records, overrides and exclusions, even if original slot >=today or displayed date differs. New schedule candidates first consult the **series-wide** reservations, including old revisions. Disabling repetition only sets enabled=false and cancels prepared future-slot alarms; retain existing occurrences and their individual still-valid reminders. If explicitly enabled again, reconcile retained schedule gaps through today; no implicit anchor reset. This enable bookkeeping is delegated contract choice, not another recurrence mode.

| Scenario | Expected records / replay |
| --- | --- |
| Daily restart/missed interval | Anchor Oct 12; Oct 12 unfinished. Resume Oct 15: exactly Oct 12/13/14/15, with fresh unchecked children for 13–15; Oct 12 overdue. Repeat reconciliation creates none. |
| Weekly | Anchor Monday Oct 12; completion Wednesday Oct 14 does not move anchor; next slot Oct 19. |
| Monthly missing date | Anchor Jan 31, 2026: Jan 31/Feb 28/Mar 31/Apr 30; 2028 Feb 29. Clamp does not mutate anchor. |
| One edit vs series | Move Oct 12 occurrence to Oct 20 and list Personal; original slot Oct 12 reserved, isOverride=true. Series title change today updates eligible today's plain occurrence only. Oct 20 override and past/completed records retain content. |
| Schedule revision | On Oct 14 preserve Oct 11 completed, Oct 13 overdue, Oct 12 override displayed Oct 20; remove plain unfinished Oct 14. Weekly new anchor Oct 16 generates only when due, then Oct 23. |
| Revision collisions | Revising on Oct 14 with new anchor Oct 14 cannot duplicate a completed Oct 14, override original Oct 14 displayed Oct 20, or excluded Oct 14. Each reserves original Oct 14. Conversely override original Oct 12 displayed Oct 14 does **not** reserve Oct 14: new Oct 14 slot may coexist on that display date. |
| One deletion vs series | Delete completed Oct 12 => exclusion Oct 12, Oct 13 remains. Delete series => all slots/history/moved overrides/exclusions gone. |
| Disabled | Disable on Oct 14 keeps all existing overdue/completed/override records; no new Oct 15. Reconciliation sees enabled=false. |
| Restore catch-up | Snapshot has Oct 12/14 and excluded Oct 13; restore Oct 15, reset cursors, create only Oct 15; replay remains identical. No past reminder replay. |

## Reminder identity, consumption and local durable work

A configuration is `(targetKind, targetId, slotDate, scheduledDate, reminderTime)`. For an ordinary task: targetKind=`task`, targetId=task.id, slotDate=null. For recurring task or prepared future slot: targetKind=`slot`, targetId=seriesId, slotDate=original nominal date. It is not keyed by revision/task UUID for a slot. Changing title, timezone, permissions, completion, or revision does not create a new configuration. Changing date/time does; returning to an already consumed identical date/time does not reset it. Serializing this tuple canonically as a JSON array is suitable for an unambiguous scheduler key; never ambiguous string concatenation.

| ReminderAttempt.field | Type | Nullable | Creation default | Validation |
| --- | --- | --- | --- | --- |
| targetKind | Text enum | no | required | task / slot |
| targetId | ID | no | required | value namespace, not FK |
| slotDate | Date | yes | null for task | required for slot; null for task |
| scheduledDate | Date | no | required | date of requested configuration |
| reminderTime | Time | no | required | local requested time |
| state | Text enum | no | pending | pending / attempted / suppressed / missed |
| consumedAt | Instant | yes | null | null iff pending; non-null for every other state |

Composite tuple is primary identity; no separate attempt UUID. `attempted` means the one notification attempt was claimed, not proof a user saw it. `suppressed` means due callback denied/invalid or too late; `missed` means reconciliation found an unconsumed past configuration. All three are terminal consumed states; none reset to pending. Claim consumption in a transaction **before** requesting OS notification to prevent crash replay; this can lose a notification if the process dies between claim and posting, and does not promise exactly-once visible delivery. Pending rows must resolve to a current unfinished configured task or valid future enabled-series slot; remove obsolete pending rows on edit/deletion/revision. Consumed rows intentionally require no live target: validation checks syntax/tuple/consumption only. Keep them indefinitely in v1 to prevent old-backup replay; no automatic purge window. They contain identities/time/state only, not deleted content. Detached records count toward backup limits.

Derived runtime status (`exact`, `approximate`, `blocked`, `missed`, `consumed`, `none`) is not a portable field. Pending denotes unconsumed, not that an OS alarm exists. Future pending configuration can arm if notifications allowed; exact access denial uses permitted inexact scheduling and shows approximate status. Notification/channel denial shows blocked; consume when due. Reconciliation that finds time <= now marks missed and never fires immediately. Only an already-armed callback, with a valid current generation token, can attempt late **on the same scheduled local day**, after existence/completion/exclusion/configuration/permission checks. A prepared slot callback first reconciles that due occurrence; excludes deleted slots and preserves edited occurrences. Past-day callback suppresses. Completion cancels without consuming a future pending attempt, so reopening before due can re-arm it; reopening after due marks missed, after attempt keeps consumed.

Use current device ZoneId at arming, never export device timezone. Resolve requested date/time with gap shift forward and earlier offset in overlap, as specified by [ZonedDateTime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html). New York 2026-03-08 02:30 resolves 03:30; 2026-11-01 01:30 chooses the earlier instant once. Clock/timezone changes invalidate obsolete arming tokens and recalculate future unconsumed times. See existing [Android scheduling/permissions sources](behavior.md#reminders-and-local-time); delivery requires later device validation.

| LocalWork.field (local only) | Type | Nullable | Creation default | Validation / purpose |
| --- | --- | --- | --- | --- |
| key | Text | no | scheduler | exactly one singleton, constant scheduler |
| generation | ID | no | new UUID | not a content record ID; replace on any reminder-affecting mutation, restore, reboot/timezone/clock reconciliation; stale callbacks/editors reject mismatched generation |
| pending | Bool | no | true | durable whole-scheduler cancel/rebuild work; false only after successful current-generation reconciliation |
| cancellations | Configuration tuple array | no | [] | local durable cancellation keys; each tuple has exactly ReminderAttempt's targetKind/targetId/slotDate/scheduledDate/reminderTime fields and validation, no state/consumedAt or FK; unique composite identities |

LocalWork shares the transactional database with records and appearance settings so their atomic replacement is representable. Before removing or changing a configuration, append its old tuple to cancellations in the same transaction; preserve previously pending cancellations. Persist every prepared future-slot configuration as a pending ReminderAttempt before OS arming. On a generation change include currently known attempt keys in the cancellation sweep, then reconstruct valid future configurations. Restore copies all current attempt keys into the local cancellation queue before replacement. Thus cancellation does not require enumerating unknown Android alarms or looking up deleted task records. Retry pending work on resume/reboot/process restart; generation mismatch forces callback rejection even before OS cancellation. Use the stored deterministic keys to cancel old alarms and dismiss app-owned notifications, then re-arm valid future configurations; OS handles are local transient integration details, not imported records. Only remove cancellation keys after successful OS cancellation and dismissals for the same generation. If OS work fails leave pending=true and report separately from a successful data commit. Concurrent edits change generation; worker checks it before marking false. Durable template cursors track recurrence progress separately. No transient UI query, editor draft or permission state is smuggled into LocalWork. Edit/autosave operations also carry the captured generation so old editors cannot recreate replaced/deleted data.

Replacement restore safety is a **union of consumed value identities**, not a record merge: before deleting current data read every current consumed tuple; union with every snapshot consumed tuple; replace snapshot pending tuple with consumed if present in union. For identical consumed tuples choose earliest consumedAt, and at a tied instant prefer attempted, then suppressed, then missed for deterministic state; every choice stays consumed. Retain unmatched consumed tuples as detached safety entries. Discard current pending entries and all current tasks/notes/settings; no deleted task content is retained. Restore snapshot pending entries only if their target/configuration validates, then classify past as missed or eligible future as pending. This union and LocalWork new generation/pending=true are committed with snapshot records/settings. Example: snapshot A at 18:00 pending, device already attempted same tuple and clock is now 17:00 => attempted retained, no new alert. An unrelated old device task X is deleted by replacement; its consumed tuple remains without a FK. Snapshot B tomorrow 09:00 can arm; snapshot C yesterday is marked missed. A fresh offline device cannot know attempts made elsewhere after export.

## Independent notes and searchable shadow text

Rich representation name: `tdmk.blocks`, version 1, a JSON object persisted as TEXT with a converter; its schema version is independent of Room and backup versions. Plain title is separate, never markup. Body blocks are flat paragraph/heading/bullet blocks; adjacent bullets form a list, no nesting, images, links, attachments or arbitrary attributes. Bold is an inline run property; heading levels 1–3 suffice for basic headings. Empty body is exactly an empty blocks array. Paragraph breaks are block boundaries; run text cannot contain CR/LF (editor newline makes another block), but tabs and other Unicode text are retained. Empty runs/blocks are allowed and never count as visible content by themselves. Supported formatting/text is lossless; no requirement to preserve irrelevant JSON whitespace or key order.

| Entity.field | Type | Nullable | Creation default | Validation |
| --- | --- | --- | --- | --- |
| Note.id | ID | no | new ID on first meaningful save | global identity, independent root |
| Note.title | Label | no | Untitled if body meaningful or existing cleared note | trim edges; nonblank on disk |
| Note.body | Rich object | no | empty body below | validated full schema, no unsupported fields |
| Note.createdAt | Instant | no | first successful save instant | never editor-open time for discarded drafts |
| Note.updatedAt | Instant | no | createdAt | successful changed title/body/format save only |
| Rich.format | Text | no | tdmk.blocks | exactly tdmk.blocks |
| Rich.version | Version | no | 1 | exactly supported 1 |
| Rich.blocks | Block array | no | [] | ordered content |
| Block.kind | Text enum | no | paragraph | paragraph / heading / bullet |
| Block.level | integer | yes | null | 1,2,3 only for heading; null required otherwise |
| Block.runs | Run array | no | [] | ordered text/format spans |
| Run.text | Text | no | empty string | no CR/LF, valid Unicode |
| Run.bold | Bool | no | false | applies to exactly this run |
| Note.plainText (local derived) | Text | no | rebuild | concatenate runs per block, join blocks with LF; no bullet glyph, heading marker or JSON attributes |
| Note.titleShadow, bodyShadow (local derived) | Text | no | rebuild | fold(title), fold(plainText) respectively |
| List.nameShadow (local derived) | Text | no | rebuild | fold(name) |
| Task.titleShadow, shortNoteShadow (local derived) | Text | no | rebuild | fold(title), fold(shortNote or empty string) |
| Subtask.textShadow (local derived) | Text | no | rebuild | fold(text) |
| Local search metadata.algorithmVersion | Version | no | 1 | singleton cache metadata; 1 identifies pipeline below |
| Local search metadata.unicodeVersion | Text | no | 17.0.0 | pinned Unicode 17.0.0 in pipeline v1; rebuild atomically if implementation upgrades version |

Invalid/unknown rich version on restore rejects the whole snapshot without lossy plaintext fallback. If incompatible/corrupt body is encountered locally, preserve original stored bytes, report error and block rewriting/export until supported validation succeeds; never overwrite with empty/plain text. No supported-version conversion is implemented here.

Empty and existing-cleared body example (new blank title with this body is discarded; existing saved note retains ID/title becomes Untitled):

```json
{"format":"tdmk.blocks","version":1,"blocks":[]}
```

Saved notes sort updatedAt descending, createdAt descending, ID ascending; Home takes first three before search filtering. A title Ideas with empty body is valid. Blank title plus body text saves Untitled; blank new title/body including empty bullet markers discards draft without record/timestamps. Existing note cleared entirely saves Untitled and empty blocks, same ID/createdAt and changed updatedAt. Meaningfulness uses Unicode White_Space trimming on visible run text, not style metadata. Autosave after 500 ms idle/flush before leaving/background/export; failed save does not change persisted timestamps/content and retains/report draft per #3. Opening unchanged content does not reorder notes.

Define fold(s) = NFKC(default full non-Turkic case fold(NFKC(s))), then trim/collapse every Unicode White_Space run to ASCII space. Use Unicode 17.0.0 normalization, CaseFolding entries C/F (ignore S/T), and White_Space property. The compatibility normalization algorithm is described in [Unicode UAX #15](https://www.unicode.org/reports/tr15/); the pinned [CaseFolding data](https://www.unicode.org/Public/17.0.0/ucd/CaseFolding.txt) defines full mappings. A future Unicode-data change changes cache metadata and rebuilds every shadow, never changes original content. Locale lowercase alone and SQLite NOCASE/LIKE defaults do not implement this contract.

Query uses the exact same fold then literal contiguous substring matching against each field separately. Do not concatenate title+note+list+subtasks. Task search reads the **current** owning-list name shadow through its FK; rename immediately affects matches. Rebuild shadows/plainText atomically with source edits and on restore/cache-version change before publishing query results. They may be persisted for efficient search, but are derived and excluded from backup. Null shortNote shadows to empty. Templates/prototypes/exclusions/reminder metadata are not search content.

| Input/query | Shadow / result |
| --- | --- |
| Straße and STRASSE | both strasse; match |
| café and `cafe\u0301` | both café; match, whereas unaccented cafe does not match café |
| `Buy\n\t milk` | buy milk; query buy milk matches within body |
| title Buy, body Milk | separate shadows buy / milk; query buy milk does not match |
| `milk.*` | literal string, not regex |
| Unicode-whitespace-only query | empty; restores ordinary selected tab, no persisted query |

Tasks query includes unfinished and completed-today tasks across **all lists/date views**, actual occurrences only, priority/creation/ID order; clearing returns selected subtab. Log searches historical completed tasks with grouping above. Home searches only its ordinary overdue/today/through-Sunday dated sections and preselected three notes; Notes searches all saved notes. Both task datasets search title/short note/each subtask/current-list name; note datasets search title/plainText. Preserve original accents/formatting; no fuzzy/token/accent-stripped match. Earlier #26 selected-view text is a future PM alignment item, not the contract here.

## Settings, versions and portable backup

| Field | Type | Nullable | Creation default | Validation |
| --- | --- | --- | --- | --- |
| Settings.appearance | Text enum | no | system | system / light / dark; one singleton in transactional store |
| Local schemaVersion | Version | no | 1 for first model implementation | Room database version/SQLite user_version, no portable record; explicit migration chain required |
| Envelope.format | Text | no | TDMK | exactly TDMK |
| Envelope.backupVersion | Version | no | 1 | exactly supported 1 |
| Envelope.exportedAt | Instant | no | current export clock | snapshot/export UTC instant, not used to recalculate data |
| Envelope.payload | Payload object | no | required | complete snapshot |
| Payload.lists/tasks/subtasks/notes/templates/exclusions/reminderAttempts | respective entity arrays | no | required | every array required; empty allowed except lists |
| Payload.settings | Settings object | no | required | exactly appearance property |

Envelope backupVersion governs every payload field in this document; rich body has its own format/version. Export maps exactly the portable tables' field names/types, including all task completion history, revisions without reconciledThrough, exclusions and attempt states. Embedded checklist prototypes/revisions retain array order. Note.body is a JSON object, not a doubly escaped JSON string. No portable schemaVersion, default-list replacement mapping, generated server IDs, search shadows/plainText/cache metadata, reconciliation cursors/LocalWork, OS alarms/permissions/channels/timezone, theme observed from OS, posted notifications, provider grants/destinations, navigation/query/editor drafts, credentials/signing material or deleted content. Appearance includes user selection, not OS effective theme. MIME application/json, suggested `TDMK-YYYYMMDD-HHmmss.tdmk.json`, one plaintext UTF-8 document through picker, new output file and provider-failure handling per #3.

Schema migrations preserve stable IDs, FK relationships, original slots/exclusions, creation/update/completion instants and captured completion days, checked states, formatting/run text and safety ledger; never destructive fallback. Version changes may rebuild derived fields/reset derived cursors for safe replay. A logical backup reader maps its supported version into local schema without importing database metadata. Unknown backup/rich versions reject before mutation; supported future conversions require explicit, tested contracts in their implementation issue. This is preparation for future sync, not conflict resolution, deletion tombstones for sync, or a backend contract.

Validation before export/restore: strict UTF-8 and JSON (reject duplicate property names, missing/extra properties, truncated/malformed data); exact types/enums/versions/ranges; global primary IDs and composite-key uniqueness; one protected default and contiguous list/subtask/prototype order; FK ownership and task recurrence triplet; monotonic revision numbers/boundaries and valid anchors/schedules; occurrence sourceRevision slot provenance and exclusion slot provenance/no overlap; completion pair and parent gate; reminder/date/target rules; body structure/Unicode and nonblank stored labels; timestamp update>=creation. A current task pending attempt must equal its current configuration; a pending future slot must be nominal, enabled, not excluded/reserved by a differently configured override/completed task, and future relative to export's local context. The receiver reclassifies formerly future pending as missed using its current clock rather than rejecting a valid older snapshot. Consumed entries can be detached by design, but their tuple/state syntax still must validate. No silent repair or partial import.

Limits: at most 50 MiB = 52,428,800 bytes of complete UTF-8 file and 100,000 total entity records. Count each list/task/subtask/note/template/exclusion/attempt plus each embedded revision/prototype (as template records); singleton settings and rich blocks/runs are not entity records, still byte-limited. This conservative explicit counting avoids bypass via embedded template history. Validate counters while staging; combined restored safety union must also fit the 100,000-record limit before confirmation/commit, otherwise fail clearly preserving current data. Export flushes drafts, reconciles due recurrence, reads a coherent transaction snapshot, validates limits; no truncated success. Unknown fields require a new backup version rather than guessed compatibility.

Restore stages/validates first, shows record counts and replacement warning, permits cancellation through confirmation; no mutation/OS cancellation on failure/cancel. Once confirmed block edits, capture consumed union, atomically replace all portable records/settings plus union, rebuild derived text, reset all revision cursors to null and write new LocalWork generation/pending=true. Commit is all-or-nothing including settings. Then invalidate editors/old scheduler callbacks, cancel old alarms/notifications, reconcile due slots and future eligible reminders. Success means committed data; scheduling failure leaves retry work pending and is reported separately. Process interruption must recover old or fully committed snapshot, never mixed records/settings. Snapshot only default list with empty arrays/settings is valid replacement; it clears user records but still retains device consumed safety union. No application implementation or device guarantees are claimed here.

## Annotated complete example snapshot

At export on October 12, 2026, device zone UTC+5: Inbox/default and Work; Read undated; Packing today at 18:00 with a checklist; Report completed today; Water plants daily Oct 11/12 (Oct 11 unfinished/overdue); exclusion for earlier Oct 10. Template anchor Oct 10 means the exclusion is valid. Trip body has heading, bold bullet and Unicode paragraph. Every portable property is shown; all listed nulls are intentional. The pending Packing attempt is future (13:00Z). A consumed Oct 11 slot attempt prevents replay even though that occurrence is unfinished. Restore derives note plainText `Trip\nPassport\nStraße — café, план 🌿` and shadows, never stores them as imported content.

```json
{
  "format": "TDMK",
  "backupVersion": 1,
  "exportedAt": "2026-10-12T07:00:00.000Z",
  "payload": {
    "lists": [
      {"id":"11111111-1111-4111-8111-111111111111","name":"Inbox","isDefault":true,"order":0,"createdAt":"2026-10-10T03:00:00.000Z","updatedAt":"2026-10-10T03:00:00.000Z"},
      {"id":"22222222-2222-4222-8222-222222222222","name":"Work","isDefault":false,"order":1,"createdAt":"2026-10-10T03:00:00.000Z","updatedAt":"2026-10-10T03:00:00.000Z"}
    ],
    "tasks": [
      {"id":"33333333-3333-4333-8333-333333333333","listId":"11111111-1111-4111-8111-111111111111","title":"Read","scheduledDate":null,"priority":"medium","shortNote":null,"reminderTime":null,"completedAt":null,"completionDate":null,"createdAt":"2026-10-10T04:00:00.000Z","updatedAt":"2026-10-10T04:00:00.000Z","seriesId":null,"slotDate":null,"sourceRevision":null,"isOverride":false},
      {"id":"44444444-4444-4444-8444-444444444444","listId":"22222222-2222-4222-8222-222222222222","title":"Packing","scheduledDate":"2026-10-12","priority":"high","shortNote":"Groceries for trip","reminderTime":"18:00","completedAt":null,"completionDate":null,"createdAt":"2026-10-10T05:00:00.000Z","updatedAt":"2026-10-10T05:00:00.000Z","seriesId":null,"slotDate":null,"sourceRevision":null,"isOverride":false},
      {"id":"55555555-5555-4555-8555-555555555555","listId":"22222222-2222-4222-8222-222222222222","title":"Report","scheduledDate":"2026-10-12","priority":"low","shortNote":null,"reminderTime":null,"completedAt":"2026-10-12T06:00:00.000Z","completionDate":"2026-10-12","createdAt":"2026-10-10T06:00:00.000Z","updatedAt":"2026-10-12T06:00:00.000Z","seriesId":null,"slotDate":null,"sourceRevision":null,"isOverride":false},
      {"id":"66666666-6666-4666-8666-666666666666","listId":"11111111-1111-4111-8111-111111111111","title":"Water plants","scheduledDate":"2026-10-11","priority":"medium","shortNote":null,"reminderTime":"09:00","completedAt":null,"completionDate":null,"createdAt":"2026-10-11T03:00:00.000Z","updatedAt":"2026-10-11T03:00:00.000Z","seriesId":"88888888-8888-4888-8888-888888888888","slotDate":"2026-10-11","sourceRevision":1,"isOverride":false},
      {"id":"77777777-7777-4777-8777-777777777777","listId":"11111111-1111-4111-8111-111111111111","title":"Water plants","scheduledDate":"2026-10-12","priority":"medium","shortNote":null,"reminderTime":"09:00","completedAt":null,"completionDate":null,"createdAt":"2026-10-12T03:00:00.000Z","updatedAt":"2026-10-12T03:00:00.000Z","seriesId":"88888888-8888-4888-8888-888888888888","slotDate":"2026-10-12","sourceRevision":1,"isOverride":false}
    ],
    "subtasks": [
      {"id":"99999999-9999-4999-8999-999999999999","taskId":"44444444-4444-4444-8444-444444444444","text":"Passport","order":0,"isCompleted":false}
    ],
    "templates": [
      {"id":"88888888-8888-4888-8888-888888888888","listId":"11111111-1111-4111-8111-111111111111","enabled":true,"createdAt":"2026-10-10T03:00:00.000Z","updatedAt":"2026-10-10T03:00:00.000Z","revisions":[
        {"number":1,"effectiveFrom":"2026-10-10","effectiveUntil":null,"anchorDate":"2026-10-10","frequency":"daily","title":"Water plants","priority":"medium","shortNote":null,"reminderTime":"09:00","checklist":[]}
      ]}
    ],
    "exclusions": [
      {"seriesId":"88888888-8888-4888-8888-888888888888","slotDate":"2026-10-10"}
    ],
    "reminderAttempts": [
      {"targetKind":"task","targetId":"44444444-4444-4444-8444-444444444444","slotDate":null,"scheduledDate":"2026-10-12","reminderTime":"18:00","state":"pending","consumedAt":null},
      {"targetKind":"slot","targetId":"88888888-8888-4888-8888-888888888888","slotDate":"2026-10-11","scheduledDate":"2026-10-11","reminderTime":"09:00","state":"attempted","consumedAt":"2026-10-11T04:00:00.000Z"},
      {"targetKind":"slot","targetId":"88888888-8888-4888-8888-888888888888","slotDate":"2026-10-12","scheduledDate":"2026-10-12","reminderTime":"09:00","state":"missed","consumedAt":"2026-10-12T07:00:00.000Z"}
    ],
    "notes": [
      {"id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","title":"Trip","body":{"format":"tdmk.blocks","version":1,"blocks":[
        {"kind":"heading","level":1,"runs":[{"text":"Trip","bold":false}]},
        {"kind":"bullet","level":null,"runs":[{"text":"Passport","bold":true}]},
        {"kind":"paragraph","level":null,"runs":[{"text":"Straße — café, план 🌿","bold":false}]}
      ]},"createdAt":"2026-10-10T07:00:00.000Z","updatedAt":"2026-10-12T05:00:00.000Z"}
    ],
    "settings": {"appearance":"system"}
  }
}
```

The example has **15** counted entity records: 2 lists + 5 tasks + 1 subtask + 1 template + 1 revision + 1 exclusion + 3 attempts + 1 note. Settings is not counted. Local revision reconciledThrough can be Oct 12 after reconciliation; LocalWork has an independently generated generation, pending status and cancellation queue; these local fields are deliberately absent from the portable snapshot. The small UUIDs here are illustrative valid v4 encodings, not a production generation algorithm.

## Documentation verification and implementation limits

Review this contract against all ten issue criteria and behavior.md, parse both JSON fences, verify exact keys/types/IDs/orders/FKs/slot and exclusion disjointness, completion gates, reminder configurations, rich-text flattening and record count. Exercise invalid date/duplicate ID/dangling FK/completed-with-unchecked cases against the example's contract, and reason through reservation replay, revision collisions and consumed union; these are documentation/example checks, not Android behavior tests. No application files or test suite are changed. `_docs/testing-guidelines.md` is still absent; no claim is made to have read it. Android build/storage/migration/notification/crash/provider/offline restart/device tests remain the linked implementation issues from #4; independent QA must verify this document before issue closure.

Engineer checks on 2026-10-12: parsed both actual JSON examples with a one-time script outside application/test source; checked exact portable fields, value types/encodings, UUID uniqueness, orders, foreign keys, revision/slot provenance, exclusion disjointness, completion gates, reminder state/configuration, rich-body flattening and the actual 15-record count. Mutated examples with February 30, duplicate ID, missing list, unchecked completed parent, exclusion/occurrence collision, unsupported rich version and mismatched pending reminder were rejected. Straße/STRASSE, composed/decomposed accent and whitespace examples passed on the available Python Unicode 15.1.0 runtime; this does not claim complete pinned Unicode 17.0.0 conformance. Local document links exist. Manual review covered all ten current criteria and the recurrence/restore/lifecycle examples; whitespace validation uses `git diff --check`. No Android build or device behavior was exercised for this documentation-only change.

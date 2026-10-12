# TDMK development instructions

## Project and sources of truth

TDMK is a personal Android task planner with separate notes. The agreed stack is Kotlin, Jetpack Compose, Material 3, and Room/SQLite. The first version works offline without an account; backup and restore are included, while accounts and optional cloud sync are deferred.

- Read `_docs/plan.md` before changing product behavior.
- `_docs/tasks.md` maps the implementation backlog to GitHub issues in https://github.com/xiaodlb/TDMK.
- Work on one GitHub issue at a time, following `_docs/process.md`. Read its acceptance criteria, goal, description, and prerequisites before starting, and re-read the acceptance criteria before closing. Numbered task references use the original backlog numbers.
- Read `_docs/behavior.md` and `_docs/data-model.md` when present. These files are planned deliverables and may not exist yet.
- Current user instructions take precedence over this file. Do not treat an undecided detail as an agreed requirement.

## Documents

Keep project guidance together in `_docs/` and add references here when new guidance is created.

- [`_docs/process.md`](_docs/process.md) - how work is organized. Read before starting work.
- Before writing tests, read `_docs/testing-guidelines.md` when present.
- For anything touching the UI, read `_docs/design-system.md` when present.

The testing and design guides are planned files and may not exist yet. If a referenced guide is missing, report that briefly and use the existing scope and development instructions; do not claim to have read it. If `_docs/api.md` is introduced, link it here as the API contract; no cloud API is included in the current first-version scope.

## Scope and behavior

- Keep task data and separate notes local and usable offline.
- Tasks belong to Inbox or custom lists and may have a scheduled date, short note, subtasks, a reminder, and daily/weekly/monthly recurrence.
- Task scheduling is date-only; an optional reminder has a time on that scheduled day.
- Priorities are High, Medium, and Low; task views sort in that order.
- Undated tasks appear in their list and Anytime. Unfinished dated tasks stay overdue until completed or rescheduled.
- Completed tasks remain crossed out on their completion day, then disappear from active views and appear in a log grouped by completion day, newest first.
- Recurring occurrences are independent: new ones appear on schedule even if older ones are unfinished. Reconciliation must avoid duplicates.
- Separate notes support titles, bold, headings, and bullet lists. They are not linked to tasks.
- Main navigation is Home, Tasks, Notes, and Log. Settings open from Home; each main tab has search scoped to its content.
- Home shows all overdue and today's tasks, upcoming tasks through Sunday, and recent notes. Weeks start Monday.
- Tasks uses scrollable date-view and list tabs; task details open in a bottom sheet. Notes use a two-column card grid and a full-screen editor.
- Use a minimal neutral design, light/dark themes, system-following appearance, and a manual override.
- List deletion deletes its tasks after confirmation.

Consult `_docs/plan.md` for the full scope and outstanding decisions. Resolve decisions that block the current task with the owner, one question at a time, and record the answers in the relevant documentation.

## Implementation approach

- Work on the requested issue or change, keeping it small and reviewable. Complete prerequisites before dependent work; avoid unrelated feature additions.
- Keep Compose UI, state handling, domain rules, and database access separated enough to test independently. Prefer straightforward architecture over unnecessary modules or abstractions.
- Use Room for structured local data and a suitable local settings store. Keep database changes explicit, export schemas, and preserve user data through migrations.
- Use stable record IDs. Design backup and note formats with versions; do not introduce a cloud backend solely to prepare for future sync.
- Make date and recurrence logic testable with a controllable clock and explicit timezone handling. Refresh date-sensitive views on resume and local-day changes.
- Persist completion timestamps and derive visibility by date rather than depending on an exact midnight background job.
- Schedule reminders using appropriate Android alarm/notification APIs and handle permissions, cancellations, reboot, and clock changes. Do not assume WorkManager runs at an exact reminder time.
- Store human-readable note search text alongside the chosen formatting representation when needed. Preserve formatting through editing, backup, and restore.
- Validate backups before applying them and use transactions where a partial write could corrupt related data.
- Check current official documentation when choosing dependency versions or using unfamiliar APIs. Do not invent versions or claim device behavior that has not been checked.

## Validation

The repository initially contains documentation only. Backlog task 1 establishes the Android project, Gradle wrapper, and a passing local unit test. After setup, document the actual build and test commands in the README; do not assume these commands work before the wrapper and modules exist.

For a standard `app` module, expected Windows commands are:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

- Run checks appropriate to the change. Add meaningful tests for date boundaries, recurrence, persistence, completion rules, migrations, and backup validation when those features are implemented.
- Use an Android phone or emulator for UI, notification permissions, alarm behavior, reboot recovery, and accessibility checks as relevant. Report when required device validation is unavailable.
- Verify offline behavior and persistence across application restarts for storage-related changes.
- Documentation-only changes require checking links, paths, and consistency with the scope, not an Android build.
- Report what was checked and any remaining limitations; never claim a test passed without running it.

## Python tooling commands and rules

The following user-provided instructions apply if Python tooling is introduced. The Android application still uses Kotlin and Gradle; no Python project or `pyproject.toml` has been set up yet.

### Commands

- `uv sync` - install dependencies.
- `uv run pytest` - run the whole Python test suite.
- `uv run pytest tests/test_home.py` - run one Python test file.

### Rules

- Python dependencies are added in `pyproject.toml`. Do not add one without asking the user.

## Repository hygiene and communication

- Use `codex/` as the prefix when creating a working branch unless the user specifies another name. Preserve unrelated local changes.
- Keep generated builds, SDK paths, signing keys, credentials, and local configuration out of version control. Add appropriate ignore rules when the project is initialized.
- Do not silently replace a missing prerequisite, unresolved product decision, or unavailable SDK with a different feature or stack.
- Keep user-facing explanations short and concrete. State what changed, how it was verified, and anything still blocked.
- Commit regularly at meaningful checkpoints as required by `_docs/process.md`. Push changes, manage issues, or publish releases when requested or authorized by the current task; local commits do not imply permission to publish.
- Do not start parallel agents unless the user explicitly requests delegation or parallel work.

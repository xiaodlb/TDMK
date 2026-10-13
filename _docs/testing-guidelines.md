# Testing guidelines

Read [the process](process.md) and the issue's acceptance criteria first. PM grooms, Engineer implements, and QA independently verifies every criterion. Only the orchestrator closes an issue after QA PASS. A deadline does not replace this gate.

## Commands and evidence

Use the SDK/JDK prerequisites in [README](../README.md). Run from the repository root in PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The last command requires an authorized phone or running emulator. Check `adb devices` first; set `ANDROID_SERIAL` when multiple devices are connected. Record device model, Android/API version, command, result, and commit tested. API 36 is the recorded storage-validation target, not proof of all supported Android versions.

For a focused local regression:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.xiaodlb.tdmk.data.RecurrenceCalendarTest"
```

For a focused device class:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.xiaodlb.tdmk.data.RecurrenceReconciliationTest"
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/androidTests/connected/debug/index.html`. APK: `app/build/outputs/apk/debug/app-debug.apk`. Generated results are ignored; schema snapshots in `app/schemas/` are tracked. A build or local test cannot establish device behavior. Preserve command failures and report unavailable device checks explicitly.

## Existing coverage

Local tests live in [`app/src/test`](../app/src/test/java/com/xiaodlb/tdmk): `LauncherConfigurationTest`, `AppThemeTest`, and data tests `RichBodyCodecTest`, `TaskViewsTest`, `CompletionViewsTest`, `RecurrenceCalendarTest`, `SlotProvenanceTest`.

Device tests live in [`app/src/androidTest`](../app/src/androidTest/java/com/xiaodlb/tdmk): `NavigationShellTest`, `AppearanceTest`, and data tests `DatabaseFoundationTest`, `ListRepositoryTest`, `TaskRepositoryTest`, `SubtaskRepositoryTest`, `NoteRepositoryTest`, `RecurrenceRepositoryTest`, `RecurrenceReconciliationTest`.

Storage checks use on-disk databases, reopen them, test rollback, migrations and failed-open preservation. Use isolated filenames and clean up only databases created by the test. Never test destructive recovery against a user's database. Read [storage policy](storage-foundation.md), [task storage](task-storage.md), [note storage](note-storage.md), [behavior](behavior.md), and [data model](data-model.md).

## Deterministic date and recurrence tests

Inject `Clock` into `TaskViews` and `RecurrenceRepository`; inject `now` and `zone` into `TaskRepository`. Use fixed instants and explicit zones. Never rely on today's host date, sleeping until midnight, or unspecified default timezone for expected values.

Cover local midnight, forward/backward day changes, timezone changes, Monday/Sunday and month/year boundaries. Verify Today, Anytime, overdue, seven-day Upcoming, Home's through-Sunday range, priority ordering, completion-day visibility and later history. Completion instant and captured local day must remain paired.

For recurrence, test daily/weekly/monthly anchors, short-month clamping, leap years, inclusive slot boundaries, delayed catch-up, independent unfinished occurrences, idempotent reconciliation, unique series/slot keys, revision boundaries, overrides and deletion exclusions. Use `RecurrenceCalendarTest` and `SlotProvenanceTest` for pure rules and device recurrence tests for actual constraints/transactions. Do not infer alarm delivery from a stored reminder time.

## Device and feature boundaries

For UI changes check real rendering, navigation/back behavior, process recreation, light/dark/system appearance, accessibility and offline persistence as applicable. Shell/appearance coverage does not establish completion of later task/note screens. When reminder scheduling or backup/restore is implemented, add permission/alarm/reboot/time-change and invalid-backup/transaction tests then; those features are not established by the present storage tests.

Documentation changes need link/path and source consistency checks, not an Android build. Record actual validation in the corresponding `_docs/task-N-validation.md` and QA issue comment; do not copy an old pass as a new result. Existing records include [task 1](task-1-validation.md), [task 5](task-5-validation.md), [task 10](task-10-validation.md), [task 11](task-11-validation.md), [task 12](task-12-validation.md), [task 13](task-13-validation.md), [task 14](task-14-validation.md), and [task 15](task-15-validation.md).

# Issue #8 Engineer validation

Checked 2026-10-12 against all five [criteria](issues/8.md) and [PM handoff](https://github.com/xiaodlb/TDMK/issues/8#issuecomment-6129420581). [Task/checklist policy](task-storage.md) records exact fields, completion/cascade integration, failure outcomes and delegated technical choices; the settled model/behavior is unchanged. Independent QA remains required.

Windows PowerShell; JDK 22.0.2, Gradle 9.1.0, AGP 9.0.1/Kotlin 2.2.10, Room 2.8.5/KSP 2.3.6 and existing time/coroutine compatibility configuration. No dependencies added. Existing SDK/cache environment used; `adb devices -l` confirmed emulator-5554 online, existing TDMK_API36 Android16/API36 AOSP x86_64. Emulator retained, no restart. Application has no account/network requirement or Internet permission.

Initial compilation/local run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain --offline
```

BUILD SUCCESSFUL, 32s, 77 tasks (18 executed, 59 up-to-date). Local suite 1 passed, zero failures/errors/skips; both APKs/schema4 compiled. Schema1/2/3 unchanged against 17f13e4.

Full applicable unit/build/device run:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain --offline
```

**BUILD SUCCESSFUL, 39s, 77 tasks (8 executed, 69 up-to-date)**. Full API36 suite **20 passed** (4 subtask, 5 task, 5 list, 6 foundation), **zero failures/errors/skips**; existing local suite **1 passed**. No test filters/omissions, no failed checks and no further reruns after this passing result.

| Subtask device case | Actual assertions |
| --- | --- |
| `checklistIdentityTextOrderChecksPersistAndInvalidOperationsDoNotWrite` | Three-child Unicode checklist edited/checked/reordered, preserving IDs/parent and independent states; other parent's child unchanged. Exact full rows survive close/reopen and later delete/compaction/append/reopen. Invalid text, duplicate/missing/unknown/other-parent permutations and unknown child/parent APIs rejected without changing prior rows. Actual SQLite duplicate owner/order insert fails unique constraint; current list/task/child IDs are distinct UUID v4. |
| `everyCompletionPathGuardsChecklistAndObserversReceiveAtomicReopens` | Explicit completion with no child, mixed-checklist guard on both complete/setCompletion, no child auto-check or parent auto-completion after last check. Adding unchecked child/unchecking child under completed parent atomically clears both completion fields, preserves date/reminder/other checks; continuously subscribed active/completed Flow observers receive transitions. Text edit/deletion retains completed instant/day; deleting last unchecked does not auto-complete; explicit reopen retains checked state/date. |
| `parentListCascadesAndInjectedReopenDeletionFailuresRollBack` | Injected SQLite parent-update failure after uncheck/insert retains exact prior child records/checks and parent completion pair. Injected list-delete failure after task/child removal restores full lists/order, children and parent completion. Actual parent CASCADE removes its children but preserves sibling; actual custom-list deletion removes sibling children but retains unrelated parent/child, including further reopen. Nonexistent-parent rejection is in the checklist case. |
| `exportedSchemaThreeMigrationPreservesEveryOldRowAndFurtherChecklistWrites` | Real on-disk exported schema3 with Dark Settings, renamed stable default/list timestamps, and dated/reminded, undated/null-note and completed task rows. Explicit 3-to-4 migration passes Room validation. Every prior Task field/list row/Settings retained through new child write/check/reopen; child full identity/state and SQLite version4 retained. |

All existing foundation/list/task cases run, including earlier migration paths, persistence, reactive changes, Unicode/unknown-ID validation, RESTRICT deletion/rollback and failed-open preservation. Foundation fixtures follow current schema4 (same-version incompatibility4, unsupported downgrade5-to-4). No in-memory tests or source inspection substitute for device storage evidence.

Tracked schema4 contains Settings/lists/tasks/subtasks with child CASCADE FK and unique owner/order index; schemas1–3 retained unchanged. Device report/XML/per-test logcats/UTP data: `app/build/reports/androidTests/connected/debug/index.html` and `app/build/outputs/androidTest-results/connected/debug`. Unit report/XML: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest`. Build/test artifacts remain ignored. Final report counts/artifact sizes/commits are recorded in the Engineer comment. Testing/design guides remain absent. Physical phone/minSdk execution/process-kill, UI/recurrence/OS alarms/search/date visibility are outside this issue; no future integrations claimed tested.

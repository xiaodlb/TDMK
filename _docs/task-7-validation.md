# Issue #7 Engineer validation

2026-10-12 checks against all six [criteria](issues/7.md) and [PM handoff](https://github.com/xiaodlb/TDMK/issues/7#issuecomment-6129187174). See [task storage](task-storage.md) for exact fields/API behavior/encodings, delegated choices and downstream boundaries. No issue criteria changed; independent QA remains required.

Windows PowerShell, JDK 22.0.2, Gradle 9.1.0, AGP 9.0.1/Kotlin 2.2.10, Room 2.8.5/KSP 2.3.6. Existing ignored SDK/cache paths used; desugaring 2.1.5 and coroutines 1.10.2 are pinned as necessary time/observable compatibility. `adb devices -l` confirmed emulator-5554 online; existing TDMK_API36, Android 16 API36 AOSP x86_64 remained running, without a restart. App has no account/network requirement or Internet permission.

Initial compilation/local checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```

BUILD SUCCESSFUL, 1m51s, 77 tasks executed; local suite 1 passed, zero failures/errors/skips. After targeted task tests and fixture-version updates, full unit/debug/device command:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain --offline
```

First complete run passed in 50s, 77 tasks (20 executed, 57 up-to-date); **16 device tests passed** (5 task, 5 list, 6 foundation), zero failures/errors/skips. Final same-command run also checks the explicit retained default triggers and full list-row equality after migration/write/reopen: **BUILD SUCCESSFUL, 1m30s, 77 tasks (14 executed, 63 up-to-date), 1 local and 16 device tests passed, zero failures/errors/skips**. No failed checks remain; no in-memory/source-inspection substitute is used.

| Task test | Real on-disk assertions |
| --- | --- |
| `allFieldsUpdatesCompletionAndDateEncodingsSurviveReopen` | Dated/undated tasks; default selection after Inbox rename; Unicode title trim/internal whitespace; Medium default, null versus empty note; change title/list/date/priority/note/time; stable ID/creation and update-clock rules; completion instant/day captured together; change timezone from UTC+14 to UTC-12 and edit without recomputing captured day/date; exact SQL date/time text; all fields survive reopen; remove date clears reminder; reopen clears completion pair; active/completed partition preserves records. |
| `existingFlowCollectorsReceiveAllPersistedTransitions` | Two collectors remain subscribed to real Room active/completed queries; receive initial state, create, content update, complete partition switch, reopen switch and delete without a database/application restart. Bounded channel checks await actual matching emissions, not fixed sleeps or manually refreshed reads. |
| `invalidWritesAndUnknownIdsPreservePriorTask` | Reject blank/Unicode-only/unpaired-surrogate titles, unknown priority/list, out-of-range date, reminder without date, non-minute time, invalid-Unicode note and unpaired/non-millisecond completion state; reject unknown read/update/delete. Existing full row/active set unchanged and survives reopen. |
| `foreignKeyAndPopulatedListDeletionAreAtomic` | Raw SQLite deletion of a populated parent fails RESTRICT; injected later list-deletion failure occurs after task removal and restores exact active/completed task rows, list snapshot/order. Default rename protection retained; successful custom deletion removes its tasks/short notes/completion metadata, retains unrelated task and compacts other list. Further task deletion/reopen leaves no history residue. |
| `exportedSchemaTwoUpgradePreservesSettingsListsAndFurtherTaskWrites` | Real exported schema 2 contains Dark Settings, stable renamed default/custom UUIDs, names/order and creation/update instants. Explicit 2-to-3 migration passes Room validation; all old rows preserved; subsequent dated/reminded task write and reopen retains task, full list rows and Settings/version3. |

Existing foundation/list suites still verify reopen state, singleton/default concurrency, transaction rollback, Unicode/default protection, earlier migrations and failed-open preservation. Fixture versions now follow current schema 3 (same-version incompatibility 3, unsupported downgrade 4-to-3). Historical generated schema 1/2 remain unchanged; 3 contains Settings/lists/ordinary tasks/FK/index. No destructive fallback, downgrade or alternate database exists.

Reports remain `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/androidTests/connected/debug/index.html`; unit XML under `app/build/test-results/testDebugUnitTest`, full device XML/per-test logcats/UTP evidence under `app/build/outputs/androidTest-results/connected/debug`. Build/test outputs remain ignored. Final counts/artifact sizes and commits are recorded in the Engineer comment. Testing/design guides remain absent. Physical phone/minSdk23 execution/process-kill/OS alarms, subtask/series/view/confirmation/search features are outside this delivery; Java-time minSdk compatibility is configured and API36 behavior is verified.

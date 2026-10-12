# Issue #11 completion results and validation

Checked 2026-10-13 against six [criteria](issues/11.md) and [PM handoff](https://github.com/xiaodlb/TDMK/issues/11#issuecomment-6130047886). Existing TaskRepository guarded complete/reopen/setCompletion behavior already meets the command requirements and was retained. New code adds only TaskViews.homeOverdueAndToday and history; schemas/dependencies/storage commands remain unchanged. Routine technical choices use the owner's delegation while preserving resolved model/behavior.

Home's dated overdue/today result uses #10's active eligibility and scheduledDate<=today. It retains same-day or backward-clock future-captured completions formerly scheduled before today; undated/future-scheduled tasks stay outside that section. #10.overdue remains unfinished-only. Applicable Today/Upcoming/Anytime/list results retain the stored completed pair so later rows can render their completed state. History filters captured completionDate<today, groups captured days descending, and uses existing priority/creation/ID order inside groups. Same-day/future-day/reopened/deleted records are absent. No record moves, midnight writes, Log copy/table or purge is required; an explicit Clock re-evaluates the same stored candidates for today/history.

Existing complete captures one instant/local day transactionally after the unchecked-child guard, and repeat complete returns the exact original Task (pair/ID/update timestamp). Reopen clears the pair and keeps date/reminder/child checks; completing again captures a new pair on the same ID. #8 automatic child reopening and rollback remain authoritative. #16 rendering, #23 Log UI, #35 foreground refresh, #28/#30 reminders and #12 recurrence remain deferred.

Windows PowerShell/JDK22.0.2, existing Gradle9.1.0/AGP9.0.1/Kotlin2.2.10/Room2.8.5/KSP2.3.6 and ignored SDK/cache environment:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain --offline
```

**BUILD SUCCESSFUL, 1m16s, 45 tasks (11 executed, 34 up-to-date)**. Whole local suite **11 passed** (completion3 + existing date-view5/codec2/launcher1), zero failures/errors/skips; debug APK built. Three deterministic checks cover:

- Home same-day formerly-overdue retention versus unchanged unfinished-only overdue, current/future captured eligibility, undated Anytime and older history.
- Two captured completion days, shuffled repeated inputs, High/Medium/Low, creation and stable-ID ties independent of scheduled date; today/future/reopened/unfinished exclusions and one occurrence per historical stored record.
- Local23:59:30/00:00:30 in Asia/Qyzylorda, next-day fresh domain instance (restart evaluation), forward/backward day jumps and UTC-12 travel at the same instant without rewriting captured completion day/date-only schedule.

Targeted actual API36 command/invalidation/persistence check:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.xiaodlb.tdmk.data.TaskRepositoryTest#completionRepeatReopenRecompleteAndCandidateVisibilityPersist" --no-daemon --console=plain --offline
```

**BUILD SUCCESSFUL, 48s, 71 tasks (7 executed, 64 up-to-date)**; **1 targeted API36 test passed, zero failures/errors/skips**. No failed checks or unnecessary repeated runs after success.

The case verifies mixed-child rejection through both command paths and no auto-check; injected SQLite completion failure preserves parent/child states; completes at23:59:30, repeats after00:00:30 with exact pair/timestamp retained, derives older history without a write, reopens with date/reminder/checks retained and subscribed candidates updated, re-completes on the same ID/new day with one row, retains formerly-overdue completion on Home while overdue is empty, and preserves captured day through timezone travel. After canceling the collector, actual database close/new-provider reopen retains the exact Task and children; next-day history derives from it, and subsequent task deletion removes history immediately. No unrelated device suite repeated.

Existing TDMK_API36/emulator-5554 Android16/API36 AOSP x86_64 stayed online; no restart/account/app-network requirement. Final device result/artifact sizes/commit are recorded in the Engineer comment. Unit/device reports remain in `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/androidTests/connected/debug/index.html`, with XML/per-test logs under ignored build outputs. Room schema5/all historical schemas unchanged; whitespace/local links checked. Testing/design guides absent. Physical hardware/OS refresh/UI/alarms/recurrence behavior not claimed.

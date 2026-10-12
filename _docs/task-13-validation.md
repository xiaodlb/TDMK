# Issue #13 reconciliation

Implementation follows the six [criteria](issues/13.md), existing [recurrence storage](recurrence-storage.md) and [PM handoff](https://github.com/xiaodlb/TDMK/issues/13#issuecomment-6133716024). Testing/design guides are absent. No schema/dependency change or scheduler/UI/OS notification work is included.

Technical choice under the owner's delegation: process at most 366 calendar days per transaction, re-reading the current series/revision/cursor before each batch. Continue synchronously until every retained nonempty interval through one captured current-device local day is processed; no lookback cap or partial-success return. For example, a daily multi-year gap spans multiple committed batches, while a later failed batch rolls back its rows/children/cursor and earlier batches remain available for retry. `reconcile()` visits all stored series; `reconcile(id)` also skips a removed ID. Both return newly created count only after completion and propagate a batch failure.

Calendar enumeration uses original anchor alignment (daily/weekly) and original day-number clamps per YearMonth (monthly). Cursors advance across nonslot days, never backwards. Each batch uses existing series-wide live/exclusion checks plus SQLite uniqueness; a reset-cursor replay preserves existing IDs, modified/completed/moved/undated records and deletions. Same-day empty intervals and disabled/deleted series produce nothing; re-enabled series catch up without changing anchors. New records use revision content/current template owner and unchecked fresh child IDs. Past requested reminder metadata is copied without sending notifications.

## Actual checks (2026-10-13)

Windows PowerShell/JDK22.0.2, retained SDK/cache and online emulator-5554 TDMK_API36 Android16/API36 AOSP x86_64:

```powershell
$env:GRADLE_USER_HOME='M:\WebFrony\TDMK\.tools\gradle-home'
$env:ANDROID_HOME='M:\WebFrony\TDMK\.tools\android-sdk'
$env:ANDROID_USER_HOME='M:\WebFrony\TDMK\.tools\android-user'
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain --offline
```

Final **BUILD SUCCESSFUL, 46s, 77 tasks (7 executed, 70 up-to-date)**. Whole local suite **16 passed**, whole API36 suite **35 passed**, zero failures/errors/skips, debug APK built. Schemas1–6 and dependencies unchanged. Initial run compiled production/debug and passed16 units but found nullable-date sorting in the new device fixture; fixture correction followed by the necessary successful run. No product-code failure, emulator restart or redundant checks.

Four new local cases check inclusive daily/weekly ranges across years, exact monthly normal/leap February and later-day recovery, future/empty bounds and years0001/9999 without overflow, unknown/invalid values, nonaligned weekly ranges and all2480 daily slots2020-Jan1 through2026-Oct15.

Four new on-disk device cases check:

- Oct12 unfinished predecessor and checked child preserved after close/reopen Oct15, exactly13/14/15 added with fresh unchecked children/null completions/copied list/priority/note/reminder; completed predecessor also remains exact after future generation. Repeat/backward/forward jumps preserve IDs/cursors. Monthly2028-Jan31/Feb29/Mar31/Apr30 and weekly Apr10/17/24 persist through reopen, with nonslot Apr30 cursor advancement. A clock that moves ahead during the call creates only the originally captured day's slot.
- Retained daily→weekly intervals, same-day empty revision, completed collision, moved original12 sharing displayedOct21 with actual21, deleted undated13 exclusion, original history/past unfinished rows and new revision content/current owner. Reset every cursor/reopen/reconcile: zero regenerated records and exact IDs/content/children/completions/exclusions retained; closed interval advances toOct13, empty interval remains null, current interval throughOct28.
- Future-start content edit with no early occurrence, same-day schedule change to today, disabled cursor/records retained then six-day re-enabled catch-up without anchor reset, per-series/all-series entrypoints, series and owning-list deletion followed by future reopen yielding no recreated templates/tasks; schema remains6.
- Daily2020-Jan1 through2022-Jan3: injected second-batch failure on2021-Jan5 leaves exactly366 earlier committed occurrences/children and cursor2020-Dec31; attempted second-batch rows are absent. Repeated failed retry preserves exact IDs/children. Remove trigger and run two concurrent repository calls: their combined new count is368, total734 unique slots/IDs and fresh children, final cursorJan3. Real close/reopen retry creates zero and retains every task/child exactly.

Existing storage/completion/revision/cascade/rollback and populated migration suites all pass. Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/androidTests/connected/debug/index.html`; XML/per-test logs remain under ignored build outputs. Harmless existing SDK XML warning remains. No physical hardware, background timing, alarm notification/delivery or backup-file result is claimed.

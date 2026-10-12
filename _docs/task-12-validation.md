# Issue #12 validation

Checked 2026-10-13 against all five [criteria](issues/12.md), [PM handoff](https://github.com/xiaodlb/TDMK/issues/12#issuecomment-6133398326) and [future-start correction](https://github.com/xiaodlb/TDMK/issues/12#issuecomment-6133562484). Implementation details/choices are in [recurrence storage](recurrence-storage.md). Existing pinned dependencies are unchanged; testing/design guides remain absent.

Windows PowerShell/JDK22.0.2, existing ignored SDK/cache and online emulator-5554 TDMK_API36 Android16/API36 AOSP x86_64:

```powershell
$env:GRADLE_USER_HOME='M:\WebFrony\TDMK\.tools\gradle-home'
$env:ANDROID_HOME='M:\WebFrony\TDMK\.tools\android-sdk'
$env:ANDROID_USER_HOME='M:\WebFrony\TDMK\.tools\android-user'
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain --offline
```

Final **BUILD SUCCESSFUL, 41s, 77 tasks (11 executed, 66 up-to-date)**. Whole local suite **12 passed**, whole device suite **31 passed**, zero failures/errors/skips; debug APK built. Local provenance test checks daily/weekly/monthly, Jan31 month clamps including leap Feb29, immutable anchor and unknown frequency. Existing codec/date/history/launcher units and all existing persistence/completion/rollback/migration device cases remain passing.

Five new actual-device checks cover:

- Complete template/revision/prototype/reminder metadata, known slots, fresh children, duplicate/live/exclusion/immutable identity/FK enforcement, malformed/unknown operations, injected second-write failure preserving all rows and cursor, enable/disable and database close/reopen.
- Prospective content and schedule revisions, equal-day empty intervals, fresh child replacement, preserved history/past unfinished/moved and undated overrides, supersession without exclusions, shared displayed dates with distinct original slots, reopening and original-source deletion after a shortened revision cutoff.
- Creation Oct13 with future anchor Oct20, content edit Oct14 retaining anchor/no early slots, then schedule edit to Oct14 permitting its occurrence; persisted anchors and rows survive reopening.
- Every user task/child mutation marking overrides, checked-parent automatic reopening, owned-series deletion including moved/history rows, surviving external-series exclusion on custom-list deletion, injected final-list-delete rollback preserving exact parents/children/revisions/exclusions/order, whole-series deletion and unrelated note/default protection.
- Exported schema5-to6 validation with all old Task/List/Subtask/Note fields and Settings preserved, including checked child/completion pair/reminder and rich JSON whitespace bytes; further recurrence/child writes and real close/reopen. Existing exported schema1/2/3/4 chain tests also reach schema6 preserving their populated domains. Historical schemas1–5 unchanged.

The first complete device run exposed a temporary child-FK name remaining after Android SQLite rename (5 migration failures, 26 behavioral tests passed). Migration now names the final `tasks` parent before rename; the necessary final rerun passed all31. No emulator restart or subsequent repeated suite. Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/androidTests/connected/debug/index.html`; per-test XML is under ignored build outputs. Harmless SDK XML/tool-version warning remains. No physical-device, generator, UI or alarm-delivery result is claimed.

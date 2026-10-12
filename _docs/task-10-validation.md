# Issue #10 task views and validation

Implements five [criteria](issues/10.md) following the [PM handoff](https://github.com/xiaodlb/TDMK/issues/10#issuecomment-6129856986), settled behavior/model and owner routine-choice delegation. `TaskViews` is small pure logic over immutable task records and explicit `java.time.Clock`. Each evaluation captures one LocalDate using that clock's timezone. Today matches today's scheduled date; Anytime matches null date; list results match stable list ID across name changes; overdue is unfinished-only before today. Date views span all lists and never rewrite records/date-only fields or use reminder minutes as task time slots.

Tasks Upcoming is tomorrow through today+7 inclusive; Home upcoming is tomorrow through the current Monday–Sunday week's Sunday inclusive. On Monday Oct12,2026, Tasks includes Oct13/18/19 and excludes Oct12/20; Home ends Oct18. On Sunday Home is empty while Tasks continues into the next week. All results use High/Medium/Low, createdAt ascending, ID ascending. Update/date/completion times do not replace those keys.

Public reusable `TaskViews.isActiveEligible(task, today)` includes unfinished and captured completionDate>=today. Historical completions remain stored but are excluded from applicable active results; future captured completion days remain eligible after backward day jumps. `TaskRepository.viewCandidates()` is a single observable all-records query supplying unfinished AND completed records, avoiding the unfinished-only active() pitfall. #11 still owns history/completion orchestration and Home retention of formerly overdue same-day completions; overdue itself remains unfinished-only. No UI/schema/dependency/command rewrite, search override, recurrence generation or foreground refresh service is added; #11/#20/#22/#26/#13/#35 retain scope.

2026-10-12 Windows/JDK22.0.2, existing Gradle9.1.0/AGP9.0.1/Kotlin2.2.10 and ignored SDK/cache environment:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain --offline
```

**BUILD SUCCESSFUL, 29s, 45 tasks (11 executed, 34 up-to-date)**. Entire local suite **8 passed** (5 date-view + existing launcher1/codec2), zero failures/errors/skips. Debug APK compiled. Five focused checks:

- Date membership across default/custom lists, null/unrelated dates, same-day/historical/future captured completions and immutable source data; formerly-overdue same-day completion eligibility remains reusable for #11.
- Monday Oct12 endpoints and excluded dates, Sunday/Monday Home range versus Tasks +7, both distinct inclusive ranges.
- Priority/creation/ID sorting over 30 fixed shuffled permutations, equal creation times, changed update times and independent reminder/date/completion metadata.
- Local23:59/00:00 in Asia/Qyzylorda and same instant in UTC-12, changing classification without date mutation. Jan31/month, Dec31/year, Feb28/29,2028 leap boundaries verify d-1/d0/d1..d7/d8 and null membership.
- Captured completion-day boundary on current/forward/backward dates, retaining every history record without moving/purging it.

The only storage change is an all-records read Flow, so one targeted API36 check exercises its actual rows/invalidation and filtering rather than repeating unrelated storage suites:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.xiaodlb.tdmk.data.TaskRepositoryTest#viewCandidatesKeepSameDayCompletionsAndEmitNewStoredRecords" --no-daemon --console=plain --offline
```

**BUILD SUCCESSFUL, 32s, 71 tasks (7 executed, 64 up-to-date)**. The **1 targeted API36 test passed, zero failures/errors/skips**. No failed checks remain and no further tests were run after the passing result.

Test keeps one candidate collector subscribed, creates unfinished/same-day/historical completed records, shows Today retains same-day completion while historical record remains stored, confirms renamed Inbox stable-ID filtering, and receives another persisted create. Existing TDMK_API36/emulator-5554 Android16/API36 AOSP x86_64 remained online; no emulator restart. No account/network requirement for app behavior. Final device count/artifact sizes/commit are in the Engineer comment. Room version/schema5 and all historical schemas unchanged; git whitespace/local links checked. Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/androidTests/connected/debug/index.html`, XML/per-test logs under the corresponding ignored outputs. Testing/design guides remain absent. Screens, refresh/OS behavior and physical hardware not claimed.

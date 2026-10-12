# Issue #5 Engineer validation

Validated on 2026-10-12 against the five current [acceptance criteria](issues/5.md) and [PM handoff](https://github.com/xiaodlb/TDMK/issues/5#issuecomment-6128716738). See [foundation policy](storage-foundation.md) for the exact Settings-only subset, initialization/failure policy, dependency compatibility sources and incremental migration ownership. This is Engineer readiness evidence; independent QA and orchestrator closure remain required.

## Environment and commands

Windows PowerShell; JDK 22.0.2, Gradle wrapper 9.1.0; AGP 9.0.1 built-in KGP 2.2.10 and unchanged Compose compiler 2.2.10. `buildEnvironment` confirmed KGP remains 2.2.10 and KSP resolves to pinned 2.3.6. Room runtime/compiler/plugin are pinned 2.8.5. Local ignored SDK/cache paths were supplied via `ANDROID_HOME`, `GRADLE_USER_HOME`, `ANDROID_USER_HOME` and `local.properties`; they are not project prerequisites or tracked files.

Before device execution, `adb devices -l` reported `emulator-5554 device`; `adb -s emulator-5554 shell getprop ro.build.version.sdk` returned 36, release returned 16, and `adb -s emulator-5554 emu avd name` returned `TDMK_API36`. Existing emulator process was running and was retained throughout validation; no emulator restart was needed. Image is Android 16 API 36 AOSP x86_64 on the existing AVD.

Initial complete applicable compilation/unit run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Result: **BUILD SUCCESSFUL**, 75 tasks executed, 1m25s. Existing `LauncherConfigurationTest`: **1 test, 0 failures, 0 errors, 0 skipped**. Production and instrumentation APKs compiled. The initial older KSP 2.2.10-2.0.2 check failed AGP built-in-Kotlin source-set restrictions before compilation; official KSP AGP 9 support starts at 2.3.1, so the pin was corrected to 2.3.6. No source-set restriction flag was disabled.

Final full applicable checks, after the device-test message assertion correction:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.xiaodlb.tdmk.data.DatabaseFoundationTest" --no-daemon --console=plain --offline
```

Result: **BUILD SUCCESSFUL**, 37s, 76 tasks (7 executed, 69 up-to-date). Unit/build results retained from the passing initial compilation; the changed instrumentation test recompiled and all **6 device tests passed, 0 failures/errors/skips**. The first connected attempt with offline mode lacked a UTP test-runner artifact and did not execute tests; one online run downloaded it. That online run executed all six tests with five passing and one message assertion failing because Room says lowercase `invalid schema`; the case-insensitive assertion was corrected, then the final entire class passed. Schema rejection itself was correct. No test failures remain.

## Assertions on actual disk storage

| Test | Observed checks/assertions |
| --- | --- |
| `settingsIdentityAndValuesSurviveCloseAndTwoReopens` | Isolated on-disk filename exists; initial Settings `(1, system)`; DAO write `(1, dark)` affects exactly one row; close makes original Room instance closed; a new provider reopens the same filename into a different instance and reads the exact saved identity/value; subsequent `(1, light)` write survives another close/new-provider reopen; exactly one Settings row and SQLite version 1. |
| `concurrentApplicationAccessPublishesOneOpenedInstance` | Actual manifest Application is `TdmkApplication`; same dependency provider on repeated access; 32 requests on 8 worker threads plus a repeated request return the identical successfully opened Room instance and production `tdmk.db` exists. |
| `settingsWriteRollsBackWithItsTransaction` | Write Dark in a transaction and deliberately throw; transaction exception observed and persisted Settings remains `(1, system)`. This supports later atomic multi-table work. |
| `unsupportedDowngradeFailsAndPreservesSentinelAndVersion` | Actual isolated SQLite fixture with version 2, incomplete Settings table and Unicode sentinel; two opens through the same provider fail with the clear missing 2-to-1 migration message. After each failure, read-only SQLite reads original version 2, `keep-id`, `план 🌿 — retained`, Settings id 1, and no replacement Room master table. Failed initialization was not published as success. |
| `incompatibleCurrentSchemaFailsAndPreservesSentinelAndVersion` | Actual version-1 fixture with incompatible Settings schema; provider's eager open fails with Room's invalid-schema diagnostic. Read-only SQLite still reads original version 1, unchanged sentinel identity/value and Settings row; no replacement Room master table. |
| `corruptFileOpenFailsWithoutDeletingOrReplacingBytes` | Invalid isolated 4,096-byte database opened through the production policy twice; each attempt raises SQLiteException; original file still exists and every byte matches. Tests do not claim corrupt records are readable. |

Tests use real Room/SQLite on private device storage, never an in-memory database. Only isolated fixture teardown deletes its test files; production provider never deletes/replaces user data. Storage requires no account/network and manifest has no Internet permission. New provider instances after explicit close exercise loss of the in-process Room cache; no claim is made that Android process-kill/restore workflows were tested.

## Artifacts and limits

- Generated tracked schema: `app/schemas/com.xiaodlb.tdmk.data.TdmkDatabase/1.json`, settings table/INTEGER id/TEXT appearance, version 1, identity hash `76d2399fac0b32ab856237a7b71fb265`.
- Final debug APK: `app/build/outputs/apk/debug/app-debug.apk`, **10,185,450 bytes**. Final instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, **382,096 bytes**.
- Unit report: `app/build/reports/tests/testDebugUnitTest/index.html`, XML `app/build/test-results/testDebugUnitTest/TEST-com.xiaodlb.tdmk.LauncherConfigurationTest.xml`.
- Device report: `app/build/reports/androidTests/connected/debug/index.html`; XML `app/build/outputs/androidTest-results/connected/debug/TEST-TDMK_API36(AVD) - 16-_app-.xml`; per-test logcats, UTP logs/device data and protobuf results under that ignored output directory.
- `git diff --check` passed. README/policy/validation local documentation links were checked. Builds emitted the existing SDK XML metadata warning; it did not prevent build/device execution.
- Testing/design guides are absent. Physical hardware, later entities, version upgrades to future schemas, domain repositories, full recovery UI, backup/restore/reminders and appearance UI are outside #5. Explicit migration paths and real preservation checks are required when their schema changes exist; this foundation configures no downgrade or destructive fallback.

All five criteria are implemented and exercised with the stated scope; issue remains open for independent QA.

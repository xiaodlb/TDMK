# Issue #6 Engineer validation

Validated on 2026-10-12 against all six current [criteria](issues/6.md) and [PM handoff](https://github.com/xiaodlb/TDMK/issues/6#issuecomment-6128965189). The [storage policy](storage-foundation.md) records fields, Unicode/clock/ID choices, failure results, default initialization, transactions, migrations and mandatory future populated-list deletion integration. Decisions preserve the explicit owner Inbox rename selection and delegated deletion protection. Independent QA is required before closure.

## Environment and commands

Windows PowerShell, JDK 22.0.2, Gradle 9.1.0 wrapper, AGP 9.0.1 built-in Kotlin 2.2.10, KSP 2.3.6, Room 2.8.5 (now including room-testing for generated-schema migration validation). SDK/cache use existing ignored local configuration. `adb devices -l` returned `emulator-5554 device`; SDK property was 36, Android release 16, existing AVD `TDMK_API36`, AOSP x86_64. The running emulator was retained throughout.

Initial full compilation/local checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```

BUILD SUCCESSFUL, 1m7s, 76 tasks executed. Existing local suite: 1 passed, 0 failures/errors/skips. Generated schema 2 and both APKs compiled. `git diff --exit-code -- app/schemas/com.xiaodlb.tdmk.data.TdmkDatabase/1.json` passed; historical schema 1 remains unchanged.

The first all-device run executed 11 tests: 10 passed; Room's migration helper failed before assertions with `GeneratedSerializer.typeParametersSerializers()` AbstractMethodError. Dependency insight showed Android test consistent resolution held serialization core/BOM 1.7.3 from the app while Room migration JSON required 1.8.1 (confirmed in Room 2.8.5's published Maven metadata). The pinned 1.8.1 serialization BOM aligns existing app/test transitives; no serialization compiler, data-format feature or toolchain change was added. The subsequent full check used:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain
```

Final result: **BUILD SUCCESSFUL**, 1m20s, 76 tasks executed; local suite **1 passed**, full API36 device suite **11 passed** (5 list/migration + 6 foundation), **0 failures/errors/skips**. This recompiles and reruns all applicable checks after the dependency alignment. No failed checks remain. The serialization BOM is necessary migration-helper test configuration within #6's approved test-dependency scope and delegated technical choices; no additional permission or feature is needed.

## Actual device assertions

| List test | What it exercises |
| --- | --- |
| `customPermutationDeletionAndAppendPersistAcrossReopen` | Three custom list IDs/names and 1..3 reordered positions survive actual close/reopen; default remains 0. Duplicate/missing/unknown/default-included permutations fail without changes. Delete compacts remaining positions; create appends; another reopen retains full rows; UUIDs are version 4. |
| `renameDefaultUnicodeValidationAndTimestampsPreserveIdentity` | Protected default deletion fails before/after rename to General; role/identity/order/creation instant survive. Unicode edge whitespace is trimmed while internal tabs/spaces/Cyrillic/emoji remain. Duplicate names allowed. Blank, Unicode-whitespace-only and unpaired-surrogate names rejected. Unknown read/rename/delete IDs fail without writes. Injected clock verifies creation/update equality, changed update time, unchanged-save retention and backward-clock max rule. Rename/default survives reopening. |
| `concurrentInitializationAndReopenKeepExactlyOneDefault` | 32 calls on 8 worker threads obtain a single stable Inbox ID/order0; full reopen retains it and one true default. Real SQLite insert of another true default is rejected by the installed constraint. |
| `injectedSqlFailuresRollBackPartialReorderAndDeletion` | Test-only SQLite trigger aborts a later final-position reorder update after earlier work; all prior rows/order/timestamps retained. A second trigger aborts later compaction after deletion and an early compaction update; deleted row and every prior position/timestamp restored. Dropping fixture triggers/reopening preserves the exact snapshot. Production contains no failure injection. |
| `exportedSchemaOneMigrationKeepsDarkSettingsAndSupportsFurtherWrites` | MigrationTestHelper creates actual on-disk schema 1 from tracked JSON, writes Dark, migrates using the registered explicit 1-to-2 path, validates schema 2 and checks Dark. The normal shared provider then initializes one Inbox, writes a custom list and Light settings; close/reopen preserves default/custom identity and further settings write, SQLite version2. |

The six existing foundation tests additionally exercise Settings persistence/rollback, Application singleton concurrency, incompatible-current-schema failure, unsupported downgrade and corrupt-byte retention. Their fixture versions now follow the current version 2: same-version incompatibility is 2, unsupported downgrade is 3-to-2. The preservation policy is unchanged. Application behavior uses private local SQLite with no account or Internet permission; only host dependency downloads use network.

## Evidence and scope

- Generated `app/schemas/com.xiaodlb.tdmk.data.TdmkDatabase/2.json`: Settings plus lists, version 2, identity hash `c841b6420b1b298b99bb902756c6fa91`; version 1 retained unchanged. List unique-order index matches explicit migration SQL. True-default uniqueness triggers are defined in `ListSchema`, installed on creation/upgrade and tested on-device; Room schema JSON does not export triggers.
- Unit report: `app/build/reports/tests/testDebugUnitTest/index.html`, XML under `app/build/test-results/testDebugUnitTest/`.
- Full device report: `app/build/reports/androidTests/connected/debug/index.html`; XML `app/build/outputs/androidTest-results/connected/debug/TEST-TDMK_API36(AVD) - 16-_app-.xml`; UTP logs and per-test logcats in that ignored output directory.
- Final APK sizes and whitespace/link checks are recorded in the Engineer issue comment. Existing SDK metadata/native-symbol warnings did not prevent compilation or device execution.
- Testing/design guides remain absent. No UI, tasks/subtasks/templates, alarm/backup/search implementation, speculative cascade handlers or populated-list cascade verification is included. Those required integrations remain #7/#8/#12 before #21 exposes populated deletion; #26 owns search caches. Physical device/process-kill workflows were not tested; real disk close/reopen and migration were tested as requested.

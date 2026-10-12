# Issue #9 Engineer validation

2026-10-12 checked against all six [criteria](issues/9.md) and [PM handoff](https://github.com/xiaodlb/TDMK/issues/9#issuecomment-6129622338). Exact format/save/error/derivation choices and deferred boundaries are in [note storage](note-storage.md); settled model/behavior unchanged. Independent QA remains required.

Windows PowerShell, JDK22.0.2, Gradle9.1.0, AGP9.0.1/Kotlin2.2.10, Room2.8.5/KSP2.3.6 and existing time/coroutine configuration. Necessary JSON runtime1.8.1 uses the existing pinned compatible serialization BOM, with no serialization/compiler/editor plugin. Existing ignored SDK/cache environment. `adb devices -l` reported emulator-5554 online: retained TDMK_API36 Android16/API36 AOSP x86_64; no restart. Application has no account/network requirement or Internet permission.

Initial compilation/local/APK check:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain --offline
```

BUILD SUCCESSFUL, 1m23s, 77 tasks executed. After focused codec/note tests and current fixture versions were written, full applicable check:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain --offline
```

**BUILD SUCCESSFUL, 44s, 77 tasks (15 executed, 62 up-to-date)**. **3 local tests passed** (launcher1/codec2), **24 full API36 tests passed** (notes4 + existing20), **zero failures/errors/skips**. No filters/omissions, failed checks, repeated device runs or in-memory substitutes.

| Focused check | Actual assertions |
| --- | --- |
| Local rich codec tests (2) | Paragraph/headings1–3/bullet/bold/Unicode/empty blocks round-trip exactly; visible whitespace/plain text LF derivation retained. Reject malformed/unsupported format/version, numeric-string/float version, missing/extra/duplicate keys (including escaped equivalents), wrong block/level/bool, CR/LF and unpaired Unicode. |
| `richSaveRulesOrderingAndFurtherWritesSurviveReopen` | Actual rich rows preserve block/run formatting/text and exact plain text; new blank/format-only-empty drafts discarded; Untitled fallback/title-only-empty saved. Creation/update equality, deterministic tied ID order, unchanged save leaves ordering/timestamps, formatting-only edit advances update with same preview, backward-clock max rule, existing cleared retention; full rows survive reopen and further writes/reopen. |
| `invalidNewContentAtomicFailureAndCorruptLocalBodyNeverOverwriteState` | Malformed/version/format/title Unicode inputs rejected without altering coherent row. Injected SQL save failure retains old title/body/plainText/timestamps. Actual local unsupported-version/corrupt body fails read/overwrite; every raw stored field/body text unchanged, including reopen. |
| `independentDeletionAndUnknownIdsNeverRecreateRecords` | Delete task and populated custom list with child => same note identity/formatting survives. Delete note => unrelated list/task/subtask exact rows survive. Deleted/unknown note read/update/delete raises explicit failure without recreating record; domain state/note absence survive reopen. All current IDs distinct UUIDv4. |
| `exportedSchemaFourUpgradePreservesExistingDomainsAndFurtherRichWrites` | Real exported schema4 fixture with Dark Settings, renamed stable list, dated/reminded completed task/completion pair and checked subtask. Explicit 4-to-5 path passes Room validation; exact prior rows remain through rich note write/reopen, plus note identity/body/plainText/timestamps/version5. |

Existing foundation/list/task/subtask suites include earlier migration paths, captured completion days/guards/reactive results, cascades and transaction rollback. Foundation fixture versions follow schema5 (incompatible same-version5, unsupported downgrade6-to-5). Schemas1–4 remain unchanged against 9d7f62b; schema5 contains independent notes with no FKs. Full registered chain1-to-2-to-3-to-4-to-5 retained.

Unit report/XML: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest`. Device report/XML/per-test logcats/UTP evidence: `app/build/reports/androidTests/connected/debug/index.html`, `app/build/outputs/androidTest-results/connected/debug`. Build/test artifacts ignored. Final artifact sizes/schema hash/commits in Engineer comment. Whitespace/local links checked before handoff. Testing/design guides absent. Physical phone/minSdk/process-kill/editor/card/Unicode-matching/backup/provider workflows not claimed; linked #24/#25/#26/#32/#33 remain deferred as groomed.

# Issue #15 validation

Checked 2026-10-13 against all five [criteria](issues/15.md)/[PM handoff](https://github.com/xiaodlb/TDMK/issues/15#issuecomment-6137544521). Implemented roles/tokens/selection/error policy are in [design system](design-system.md), now linked from AGENTS.md. Existing Room singleton/encoding/schema6 remain authoritative; Settings observation uses Flow and shared background access. No new dependency/store/schema/account/network/feature screen added. Testing guide absent; design guide was absent and created here as required.

Windows PowerShell/JDK22.0.2, retained SDK/cache, online emulator-5554 TDMK_API36 Android16/API36 AOSP x86_64:

```powershell
$env:GRADLE_USER_HOME='M:\WebFrony\TDMK\.tools\gradle-home'
$env:ANDROID_HOME='M:\WebFrony\TDMK\.tools\android-sdk'
$env:ANDROID_USER_HOME='M:\WebFrony\TDMK\.tools\android-user'
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain --offline
```

Whole local suite **18 passed** (new resolution/contrast2 plus existing16); debug/test APK built. All **40 device cases passed across the full run plus necessary failed-case rerun**: full run1m49s passed39/40 (both new appearance cases, two other navigation cases, all35 storage/migration/recurrence cases); existing layout fixture attempted a tab tap during the short background Settings reload after rotation. Added an explicit shell-ready wait. Only failed `NavigationShellTest#portraitLandscapeNormalAndDoubleTextHaveOperableLabelsAndSafeInsets` rerun **BUILD SUCCESSFUL1m1s, 71tasks (7executed64up-to-date), 1 passed, zero failures/errors/skips**. New fixture compilation initially lacked a Semantics accessor extension; corrected before device execution. No product failure, emulator restart or redundant passing-suite run. Schemas1–6/dependency pins unchanged. Final debug APK11,996,863bytes, testAPK1,267,622bytes.

Mode device case verifies exact selected radio semantics; Dark shared across all five destinations immediately; Dark and Light survive recreation; manual choices ignore device changes; manual→System follows live yes/no changes while storing system; failed SQL update retains Light/old Settings and existing lists with an observable polite error, then retry succeeds; another actual provider reads the saved Dark value. Both storage opening and writing run off main thread, and loading does not expose selectable controls until the stored mode is known. Navigation Back/saved-route behavior remains passing.

Render case switches Light/Dark at font1.0/2.0 in portrait/landscape, scrolls/taps each full-row option, checks >=48dp/window insets/selection, and captures Settings/Home for every combination. All16 actual screenshots were inspected: neutral consistent roles, readable labels/text/radio selection/focus and status/native-navigation contrast, no clipped or unreachable controls. Short layouts scroll; every option remains individually fully visible/operable. Normal/enlarged navigation labels and Settings/back remain safe outside cutout/system bars. Captures are in [evidence/task-15](evidence/task-15); examples: [Light enlarged Settings](evidence/task-15/settings-light-landscape-font2.0.png), [Dark enlarged Settings](evidence/task-15/settings-dark-landscape-font2.0.png), [Light enlarged Home](evidence/task-15/home-light-portrait-font2.0.png), [Dark enlarged Home](evidence/task-15/home-dark-portrait-font2.0.png).

## Actual process death/relaunch

After instrumentation (which cannot kill its own target process and continue), install the final APK once. Use Home Settings/observed control bounds to choose modes, `cmd uimode night yes/no`, `am force-stop`, verify pidof empty, then `am start -W -f 0x10008000 -n com.xiaodlb.tdmk/.MainActivity` with no reinstall between restarts. Reopen Settings and check the selected row in actual UIAutomator XML (checked parent of its text label), plus actual screenshot surface pixels. External helper quoting/child-versus-parent XML selection was corrected before this successful sequence; these were host-helper failures, not app failures.

| Stored choice / device mode | Old → new PID | Actual surface | Evidence |
| --- | --- | --- | --- |
| Fresh System / light | fresh launch | FAFAF9 | [Default](evidence/task-15/fresh-system-light.png) |
| Dark / light | 9930→10070 | 14171A | [Restart](evidence/task-15/manual-dark-restart.png) |
| Light / dark | 10070→10241 | FAFAF9 | [Restart](evidence/task-15/manual-light-restart.png) |
| System / dark | 10241→10377 | 14171A | [Restart](evidence/task-15/system-dark-restart.png) |
| System / light | 10377→10504 | FAFAF9 | [Restart](evidence/task-15/system-light-restart.png) |

All four processes were actually absent after force-stop and had new PIDs on relaunch. Dark/Light stayed selected despite opposite phone modes. Switching back to System followed current dark immediately, then live light, with System still selected before/after both restarts. [Machine-readable proof](evidence/task-15/process-restart.json) records values; screenshots show actual selections/contrast. Phone mode/font restored to light/1.0, app saved System; no app data/schema replacement. No checks repeated after these passes.

Ignored local/device HTML/XML reports remain under app/build/reports and outputs/androidTest-results. Latest device XML is the targeted layout case; initial full40 output records other39 successes. Physical-phone/TalkBack comprehensive audit remains #37; no unrelated feature/background timing claim.

## QA synchronization correction

Independent [QA](https://github.com/xiaodlb/TDMK/issues/15#issuecomment-6137926755) passed criteria 1–4, 18 units, debug build, rendered layout checks, navigation and external process-restart/live-mode checks. The combined appearance case stalled in Espresso's main-thread idle loop after a global `cmd uimode night yes` configuration change. A bounded native diagnostic found the activity RESUMED/focused with the expected night configuration, but no accessible Settings labels in the instrumentation root. This establishes a test synchronization problem; no application defect was demonstrated.

Global configuration assertions now run through the tracked [external device check](checks/appearance-system.ps1), outside Espresso. The existing Compose case retains manual choices, destination propagation, recreation, persisted System selection, injected SQL failure, visible error, unchanged Settings/lists, successful retry and reopened database assertions. All previous global-mode assertions remain covered by the external check.

Fresh affected verification on emulator-5554 / API 36:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.xiaodlb.tdmk.AppearanceTest#overridesSystemObservationRecreationAndSafeFailedSave" --no-daemon --console=plain --offline
& .\_docs\checks\appearance-system.ps1
```

The affected instrumentation case passed (1/1), including the formerly unreached error/retry assertions. Gradle succeeded in 45 seconds; 77 tasks, 6 executed/71 up-to-date. Unit/debug tasks were up-to-date; production source did not change and independent QA had freshly passed all 18 units/debug. The external check passed all eight selected-row/rendered-surface assertions: Dark on light/dark phone, Light on dark/light/dark phone, System immediately dark then live light/dark. [Result values](evidence/task-15/configuration-fix.json); local screenshots/XML remain in `.tools/appearance-system-results`. Device mode/font were restored to light/1.0 and saved appearance to System.

During diagnosis the device disappeared from adb and both emulator processes exited; the same existing AVD was relaunched only after confirming that process loss. Final checks used the live boot-complete device without further restarts. Earlier passing rendering/navigation/storage/process-restart checks were not repeated. No app behavior, acceptance criteria, schema or dependencies changed.

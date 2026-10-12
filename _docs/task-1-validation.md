# Task 1 acceptance and validation

Issue: https://github.com/xiaodlb/TDMK/issues/1

The current groomed issue has the following acceptance criteria. GitHub issue #1 is the source of truth:

- The repository contains a Gradle wrapper, one Android app module, pinned Kotlin/Compose/Material 3 dependencies, and ignore rules for local SDK paths, generated outputs, credentials, and signing keys.
- Installing and opening the debug APK on a supported Android phone or emulator displays an empty full-screen surface without crashing; record the device/API and result.
- The documented test command reports at least the scaffold test passing with zero failures, and the build command produces `app/build/outputs/apk/debug/app-debug.apk`.
- README.md documents compatible JDK/SDK prerequisites, setup, full test/build commands, a single-test command, and phone installation steps.
- This document records actual test/build results separately from any unperformed device verification.

Testing and design-system guides do not exist at the start of this task. Existing project instructions and scope apply. This milestone does not add task functionality, storage, navigation, accounts, or sync.

## Validation results

Verified on 2026-10-12 on Windows with Oracle JDK 22.0.2, Gradle 9.1.0, Android SDK Platform 36, and Build Tools 36.0.0.

The SDK and Gradle dependency cache used for this checkout are in ignored `.tools/` directories. `local.properties` points to `.tools/android-sdk`; neither SDK files nor machine-specific paths are committed.

Command executed from the repository root:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.tools/gradle-home'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) '.tools/android-user'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

- Gradle result: BUILD SUCCESSFUL, 42 tasks executed.
- Local test: `LauncherConfigurationTest`, 1 test, 0 failures, 0 errors, 0 skipped.
- Test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 9,902,174 bytes.
- Source review: `MainActivity` renders an empty full-screen Material 3 Surface, and the manifest declares its exported launcher entry.
- Gradle wrapper and Android command-line download checksums were verified against their official distributions.
- `git diff --check` passed.

The build emitted non-fatal SDK XML-version and native-symbol-stripping warnings. They did not prevent tests or APK generation.

## Backlog verification rerun

On 2026-10-12, the existing project was verified against the current groomed criteria instead of recreating it. Testing and design-system guides are still absent; no application source changes were needed.

The command above was rerun with `--offline --rerun-tasks` so the test and build executed again rather than returning only cached results:

- BUILD SUCCESSFUL in 27 seconds; 42 tasks executed.
- `LauncherConfigurationTest`: 1 passed, 0 failures, 0 errors, 0 skipped. The XML report timestamp is `2026-10-12T12:04:46.219Z`.
- Debug APK: 9,902,174 bytes at the documented output path.
- Non-fatal Kotlin-daemon access warnings caused compilation to use its successful in-process fallback. SDK XML and native-symbol warnings also remained non-fatal.

## Actual Android emulator launch verification

Installation and visual launch verification were completed on a running Android emulator, not inferred from the unit test or source review:

- Device: `emulator-5554`, AVD `TDMK_API36`, Pixel 5 display profile (1080 x 2340, 440 dpi), Android SDK built for x86_64.
- Android: 16, API 36; official AOSP `system-images;android-36;default;x86_64` revision 2.
- Emulator: 37.2.12, Windows Hypervisor Platform (WHPX), SwiftShader graphics, headless. Boot completed in 74,503 ms.
- The emulator and system-image archives matched the SHA-1 checksums in the official SDK repository metadata before extraction.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` returned `Success`.
- `adb shell am start -W -n com.xiaodlb.tdmk/.MainActivity` returned `Status: ok`, cold launch, activity `com.xiaodlb.tdmk/.MainActivity`, total time 1,190 ms.
- Activity inspection confirmed TDMK was the top resumed activity.
- A screenshot was captured with `adb shell screencap -p /sdcard/tdmk-task1-launch.png`, pulled to ignored `.tools/tdmk-task1-launch.png`, and visually inspected: an empty edge-to-edge pale Material surface, with only Android system bars; no text, controls, or crash/error screen.
- `adb logcat -d -b crash` returned no crash entries after clearing logs and launching the app.

For this checkout, emulator/user settings remain in ignored `.tools/` folders. C: lacked space for the system image and AVD, so the temporary image/AVD were created in `M:\TDMK-emulator-validation` with explicit scoped approval. These machine-specific files are not committed. The successful emulator remains running for independent QA.

Physical-phone verification has not been performed. The issue permits a phone **or** emulator, and the emulator check above fulfills that launch criterion. Issue #1 remains open for independent QA; the engineer does not close it.

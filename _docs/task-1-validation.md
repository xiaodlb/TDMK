# Task 1 acceptance and validation

Issue: https://github.com/xiaodlb/TDMK/issues/1

The issue has a goal and description rather than a separate acceptance checklist. The following criteria are derived directly from them, before implementation:

- A minimal Android application uses Kotlin, Jetpack Compose, Material 3, and Gradle.
- Its launch activity displays an empty screen.
- One local unit test runs and passes.
- The debug APK builds successfully.
- Setup prerequisites and repeatable build/test commands are documented.

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

## Remaining verification

`adb devices` reported no connected device. Installation and visual launch testing on a phone or emulator have not been performed; follow the README to verify the empty launch screen. Issue 1 remains open, and the implementation commits remain local pending publication/review.

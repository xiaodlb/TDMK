# TDMK

Personal Android task planner, built with Kotlin, Jetpack Compose, Material 3, and Room. The current foundation has an empty launch screen, versioned on-disk Settings storage, one local JUnit test, and targeted database device tests. Task/note features follow in later issues.

## Requirements

- Android Studio with support for Android Gradle Plugin 9.0.1, or command-line Android SDK tools.
- JDK 17 or newer compatible with Gradle 9.1.0. This project compiles Java/Kotlin to JVM 17.
- Android SDK Platform 36 and Build Tools 36.0.0.
- Internet access for the first dependency download. No account or network connection is needed by the empty application.

Gradle 9.1.0 is provided through the checked-in wrapper; a separate Gradle installation is unnecessary. Dependency versions are pinned in `gradle/libs.versions.toml`; AGP 9 supplies Kotlin support, so no separate Kotlin Android plugin is applied.

## Setup and checks (PowerShell)

Open this directory in Android Studio and let it configure the SDK location. For command-line setup, set `ANDROID_HOME` to your installed SDK directory, or add an ignored `local.properties` file containing `sdk.dir=C:/path/to/android-sdk`. Set `JAVA_HOME` to a compatible JDK if necessary.

Install missing SDK packages using Android Studio's SDK Manager, or your command-line tools after reviewing and accepting the SDK licenses:

```powershell
sdkmanager.bat "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

Run from the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.xiaodlb.tdmk.data.DatabaseFoundationTest"
```

Run just the launcher configuration test:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.xiaodlb.tdmk.LauncherConfigurationTest"
```

The local test checks that the manifest exposes exactly one exported launcher activity. It does not require a phone and does not verify visual rendering. Test results are in `app/build/reports/tests/testDebugUnitTest/index.html`; the debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

The database tests require a connected authorized device/emulator (API 36 is the issue #5 validation target). Select it with `ANDROID_SERIAL` when needed. They exercise actual disk persistence, Application singleton concurrency, transaction rollback and failed-open data preservation; reports are in `app/build/reports/androidTests/connected/debug/index.html`. First execution needs internet to download Android test tooling. Generated schemas in `app/schemas/` are tracked; build/test outputs stay ignored. See the [storage policy](_docs/storage-foundation.md) and [recorded device checks](_docs/task-5-validation.md).

To run on your Android phone, enable USB debugging, connect and authorize the computer, select the phone in Android Studio, and run the `app` configuration. Alternatively, use `adb install -r app/build/outputs/apk/debug/app-debug.apk` and open TDMK on the phone; this milestone should display an empty screen.

## Project guidance

- [Agent instructions](AGENTS.md)
- [Scope](_docs/plan.md)
- [Work process](_docs/process.md)
- [Issue backlog](_docs/tasks.md)
- [Task 1 acceptance and validation](_docs/task-1-validation.md)
- [Room foundation and migrations](_docs/storage-foundation.md)
- [Task 5 build and device validation](_docs/task-5-validation.md)

Python tooling is not set up; `uv` commands are not needed to build or test this Android application.

## Toolchain references

- [AGP 9.0 compatibility and built-in Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Compose compiler setup](https://developer.android.com/develop/ui/compose/compiler)
- [Compose dependency BOM](https://developer.android.com/develop/ui/compose/bom)

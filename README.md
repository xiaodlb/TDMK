# TDMK

Personal Android task planner, built with Kotlin, Jetpack Compose, Material 3, and Room. The current foundation includes a navigation shell, light/dark/system appearance controls, versioned on-disk Settings/list/task/checklist/rich-note/recurrence storage, deterministic domain tests, and persistence/migration device tests. Full task/note screens, backup/restore and notification scheduling remain later backlog work.

The authentic [brainstorming and development chat history](chat-brainstrom.md) includes all related primary chats through the stated submission-preparation cutoff, with source inventory and message counts. [The scope](_docs/plan.md) and [issue backlog](_docs/tasks.md) distinguish requirements from completed implementation; issues close only after QA PASS.

## Requirements

- Android Studio with support for Android Gradle Plugin 9.0.1, or command-line Android SDK tools.
- JDK 17 or newer compatible with Gradle 9.1.0. This project compiles Java/Kotlin to JVM 17.
- Android SDK Platform 36 and Build Tools 36.0.0.
- Internet access for the first dependency download. No account or network connection is needed by the application.

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
.\gradlew.bat :app:connectedDebugAndroidTest
```

Run just the launcher configuration test:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.xiaodlb.tdmk.LauncherConfigurationTest"
```

The local test checks that the manifest exposes exactly one exported launcher activity. It does not require a phone and does not verify visual rendering. Test results are in `app/build/reports/tests/testDebugUnitTest/index.html`; the debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

The database tests require a connected authorized device/emulator (API 36 is the issue #5 validation target). Select it with `ANDROID_SERIAL` when needed. They exercise actual disk persistence, Application singleton concurrency, transaction rollback and failed-open data preservation; reports are in `app/build/reports/androidTests/connected/debug/index.html`. First execution needs internet to download Android test tooling. Generated schemas in `app/schemas/` are tracked; build/test outputs stay ignored. See the [storage policy](_docs/storage-foundation.md) and [recorded device checks](_docs/task-5-validation.md).

To run on your Android phone, enable USB debugging, connect and authorize the computer, select the phone in Android Studio, and run the `app` configuration. Alternatively, use `adb install -r app/build/outputs/apk/debug/app-debug.apk` and open TDMK on the phone. The implemented shell has Home, Tasks, Notes and Log tabs, with Settings reached from Home.

## Project guidance

- [Agent instructions](AGENTS.md)
- [Claude instruction entry point](CLAUDE.md)
- [Brainstorming and development chat history](chat-brainstrom.md)
- [Scope](_docs/plan.md)
- [Work process](_docs/process.md)
- [Testing guidelines](_docs/testing-guidelines.md)
- [Local application API](_docs/api.md)
- [Design system](_docs/design-system.md)
- [PM role](_docs/team/pm.md), [Engineer role](_docs/team/software-engineer.md), [QA role](_docs/team/qa-engineer.md)
- [Issue backlog](_docs/tasks.md)
- [Task 1 acceptance and validation](_docs/task-1-validation.md)
- [Room foundation and migrations](_docs/storage-foundation.md)
- [Task 5 build and device validation](_docs/task-5-validation.md)
- [Task 6 list storage and migration validation](_docs/task-6-validation.md)
- [Ordinary-task storage policy](_docs/task-storage.md)
- [Task 7 persistence/reactive/migration checks](_docs/task-7-validation.md)
- [Task 8 checklist/completion/cascade checks](_docs/task-8-validation.md)
- [Rich-note storage policy](_docs/note-storage.md)
- [Task 9 codec/persistence/migration checks](_docs/task-9-validation.md)
- [Task 10 date views and deterministic checks](_docs/task-10-validation.md)
- [Task 11 completion/history and persistence checks](_docs/task-11-validation.md)
- [Task 12 recurrence storage and migration checks](_docs/task-12-validation.md)
- [Task 13 recurrence reconciliation checks](_docs/task-13-validation.md)
- [Task 14 theme checks](_docs/task-14-validation.md)
- [Task 15 navigation shell checks](_docs/task-15-validation.md)

Python tooling is not set up; `uv` commands are not needed to build or test this Android application.

## Toolchain references

- [AGP 9.0 compatibility and built-in Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Compose compiler setup](https://developer.android.com/develop/ui/compose/compiler)
- [Compose dependency BOM](https://developer.android.com/develop/ui/compose/bom)

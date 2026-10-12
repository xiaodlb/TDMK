# Issue #14 validation

Checked 2026-10-13 against all five [criteria](issues/14.md) and [PM handoff](https://github.com/xiaodlb/TDMK/issues/14#issuecomment-6133876000). Delegated Back/styling choices and scope are in [navigation shell](navigation-shell.md). No feature screens, database/domain reads, user-setting writes or appearance controls are added. Design/testing guides remain absent. Only necessary BOM-aligned Compose test dependency added; schema6/storage unaffected.

Windows PowerShell/JDK22.0.2, retained SDK/cache, online emulator-5554 TDMK_API36 Android16/API36 AOSP x86_64 (1080×2340 portrait, 2340×1080 landscape):

```powershell
$env:GRADLE_USER_HOME='M:\WebFrony\TDMK\.tools\gradle-home'
$env:ANDROID_HOME='M:\WebFrony\TDMK\.tools\android-sdk'
$env:ANDROID_USER_HOME='M:\WebFrony\TDMK\.tools\android-user'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.xiaodlb.tdmk.NavigationShellTest" --no-daemon --console=plain --offline
```

Initial local/debug/test compile **BUILD SUCCESSFUL1m27s, 76tasks executed**, whole local suite **16 passed**, zero failures/errors/skips, debug/test APKs built. Three focused Compose/device cases cover:

- Cold Home launch; exact four selectable tabs/order; every tab's exclusive placeholder and visible selected state; repeated taps and switching without Back history; every main-tab Back→Home; three Settings/Back cycles; Home's actual Android root Back leaves RESUMED state.
- Recreate each selected main destination after repeated switches; Settings survives activity recreation; Android Back and labeled UI back both return Home once, without duplicate child routes.
- Actual portrait/landscape at font scales1.0/2.0: tap every tab, assert readable/displayed selection/label semantics, exactly one text label with no redundant icon description, >=48dp targets and window-relative status/cutout/navigation bounds. Settings entry opens the distinct route and Back returns Home in every combination. Actual rotation retains open Settings and selected Notes. Capture Home/Settings screenshots in all four combinations, restore original font/orientation afterwards.

The first device class run passed the first two behavior/recreation cases and exposed missing horizontal cutout protection in Material3 default bar insets. Diagnostic root→window-coordinate correction confirmed the real landscape left cutout136px; both bars now explicitly use safeDrawing. Only the failed layout case was rerun; its inset-corrected run passed58s. Screenshot inspection found pale native navigation buttons on the light background, corrected with a neutral dark native-navigation scrim after the emulator retained white native icons despite the light-appearance flag, followed by necessary layout/rotation/contrast recaptures. No emulator restart, unrelated storage suite or unnecessary passing-case rerun.

Reports remain in ignored `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/androidTests/connected/debug/index.html`; latest targeted XML is the final layout case, with earlier two successes recorded in the initial runner output. Screenshot copies in device Download survive Gradle's APK cleanup; tracked final captures below support independent visual review. No physical phone/TalkBack user session, feature screen, theme persistence or OS reminder claim.

Final necessary layout-only command uses `-Pandroid.testInstrumentationRunnerArguments.class=com.xiaodlb.tdmk.NavigationShellTest#portraitLandscapeNormalAndDoubleTextHaveOperableLabelsAndSafeInsets` with the same task/environment. **BUILD SUCCESSFUL2m17s, 71tasks (13executed58up-to-date), 1 targeted test passed, zero failures/errors/skips**. All three focused cases have passed across these necessary runs (two navigation/recreation cases in the initial class run, final layout/actual-rotation case here). Final debug APK11,573,902bytes and testAPK1,271,344bytes. Font scale restored to1.0; emulator retained. No checks repeated after the final success.

All eight final API36 captures were visually inspected: four navigation labels remain fully readable, Home's Settings entry/Settings back accessible, selected state visible, body text wraps without clipping, and content/click targets remain outside status/navigation/cutout insets. White native navigation buttons have clear contrast on the neutral dark scrim in portrait and landscape. These are actual UI screenshots, not inferred layouts:

| Orientation / font scale | Home | Settings |
| --- | --- | --- |
| Portrait / 1.0 | [Home](evidence/task-14/home-portrait-font1.0.png) | [Settings](evidence/task-14/settings-portrait-font1.0.png) |
| Landscape / 1.0 | [Home](evidence/task-14/home-landscape-font1.0.png) | [Settings](evidence/task-14/settings-landscape-font1.0.png) |
| Portrait / 2.0 | [Home](evidence/task-14/home-portrait-font2.0.png) | [Settings](evidence/task-14/settings-portrait-font2.0.png) |
| Landscape / 2.0 | [Home](evidence/task-14/home-landscape-font2.0.png) | [Settings](evidence/task-14/settings-landscape-font2.0.png) |

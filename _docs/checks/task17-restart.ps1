param([string]$Serial = "emulator-5554")
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$adb = Join-Path $repoRoot ".tools/android-sdk/platform-tools/adb.exe"
$appApk = Join-Path $repoRoot "app/build/outputs/apk/debug/app-debug.apk"
$testApk = Join-Path $repoRoot "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
$evidence = Join-Path $repoRoot "_docs/evidence/task-17"
New-Item -ItemType Directory -Force $evidence | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
    return $result
}
Invoke-Adb @("install", "-r", $appApk)
Invoke-Adb @("install", "-r", $testApk)
$oldAirplane = (Invoke-Adb @("shell", "settings", "get", "global", "airplane_mode_on")) -join ""
try {
    Invoke-Adb @("shell", "cmd", "connectivity", "airplane-mode", "enable")
    $first = Invoke-Adb @("shell", "am", "instrument", "-w", "-e", "class", "com.xiaodlb.tdmk.TaskDetailsRestartTest#aSaveThroughRealApp", "com.xiaodlb.tdmk.test/androidx.test.runner.AndroidJUnitRunner")
    if (($first -join "`n") -notmatch 'OK \(1 test\)') { throw "Restart save phase failed: $($first -join ' ')" }
    Invoke-Adb @("shell", "am", "start", "-W", "-n", "com.xiaodlb.tdmk/.MainActivity")
    $before = (Invoke-Adb @("shell", "pidof", "com.xiaodlb.tdmk")) -join ""
    if (!$before) { throw "No live app process before force stop" }
    Invoke-Adb @("shell", "am", "force-stop", "com.xiaodlb.tdmk")
    $stopped = (& $adb -s $Serial shell pidof com.xiaodlb.tdmk) -join ""
    if ($stopped) { throw "App process remained alive after force stop" }
    Invoke-Adb @("shell", "am", "start", "-W", "-n", "com.xiaodlb.tdmk/.MainActivity")
    $after = (Invoke-Adb @("shell", "pidof", "com.xiaodlb.tdmk")) -join ""
    if (!$after -or $after -eq $before) { throw "App process was not replaced" }
    $second = Invoke-Adb @("shell", "am", "instrument", "-w", "-e", "class", "com.xiaodlb.tdmk.TaskDetailsRestartTest#bVerifySavedValuesAndClean", "com.xiaodlb.tdmk.test/androidx.test.runner.AndroidJUnitRunner")
    if (($second -join "`n") -notmatch 'OK \(1 test\)') { throw "Restart verify phase failed: $($second -join ' ')" }
    Invoke-Adb @("pull", "/sdcard/Download/tdmk-task17-restart.png", (Join-Path $evidence "offline-restart-sheet.png"))
    @{ device=$Serial; api=36; beforePid=$before; afterPid=$after; airplaneMode=1; save=($first -join "`n"); verify=($second -join "`n") } | ConvertTo-Json | Set-Content (Join-Path $evidence "offline-restart.json")
} finally {
    if ($oldAirplane -eq "0") { Invoke-Adb @("shell", "cmd", "connectivity", "airplane-mode", "disable") }
}

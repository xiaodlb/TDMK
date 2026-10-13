# External API36 appearance checks: global configuration is deliberately outside Espresso.
param([string]$Device='emulator-5554')
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$adbPath=Join-Path $repoRoot '.tools/android-sdk/platform-tools/adb.exe'
$workFolder=Join-Path $repoRoot '.tools/appearance-system-results'
New-Item -ItemType Directory -Path $workFolder -Force | Out-Null
Add-Type -AssemblyName System.Drawing
function InvokeDevice { param([Parameter(ValueFromRemainingArguments=$true)][string[]]$parameters)
 $result=& $adbPath "-s" $Device @parameters 2>&1
 if($LASTEXITCODE -ne 0){throw ($result -join "`n")}; return ($result -join "`n").Trim()
}
function ReadUi {
 InvokeDevice shell uiautomator dump /sdcard/tdmk-appearance-window.xml | Out-Null
 InvokeDevice pull /sdcard/tdmk-appearance-window.xml (Join-Path $workFolder 'window.xml') | Out-Null
 return [xml](Get-Content -Raw (Join-Path $workFolder 'window.xml'))
}
function FindLabel([string]$label) {
 for($attempt=0;$attempt -lt 6;$attempt++){
  $tree=ReadUi; $node=$tree.SelectSingleNode("//node[@text='$label']"); if($null -ne $node){return $node}
 }; throw "Missing visible label $label"
}
function ClickLabel([string]$label) {
 $node=FindLabel $label
 if($node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$'){throw 'Unknown bounds'}
 $x=([int]$matches[1]+[int]$matches[3])/2; $y=([int]$matches[2]+[int]$matches[4])/2
 InvokeDevice shell input tap ([int]$x) ([int]$y) | Out-Null
}
function Verify([string]$choice,[bool]$dark,[string]$name) {
 $node=FindLabel $choice
 if($node.selected -ne 'true' -and $node.checked -ne 'true' -and $node.ParentNode.checked -ne 'true'){throw "$choice is not selected"}
 InvokeDevice shell screencap "-p" /sdcard/tdmk-appearance-restart.png | Out-Null
 $file=Join-Path $workFolder "$name.png"
 InvokeDevice pull /sdcard/tdmk-appearance-restart.png $file | Out-Null
 $bitmap=[Drawing.Bitmap]::FromFile($file)
 try {$pixel=$bitmap.GetPixel(20,600); $actual='{0:X2}{1:X2}{2:X2}' -f $pixel.R,$pixel.G,$pixel.B}finally{$bitmap.Dispose()}
 $expected=if($dark){'14171A'}else{'FAFAF9'}
 if($actual -ne $expected){throw "Theme pixel $actual expected $expected"}
 return @{choice=$choice;resolved=if($dark){'dark'}else{'light'};surface=$actual;screenshot="$name.png"}
}
InvokeDevice install "-r" (Join-Path $repoRoot 'app/build/outputs/apk/debug/app-debug.apk') | Out-Null
InvokeDevice shell cmd uimode night no | Out-Null
InvokeDevice shell am start "-W" "-f" 0x10008000 "-n" com.xiaodlb.tdmk/.MainActivity | Out-Null
ClickLabel Settings
$checks=@()
try {
 ClickLabel Dark
 $checks+=Verify Dark $true 'dark-device-light'
 InvokeDevice shell cmd uimode night yes | Out-Null
 $checks+=Verify Dark $true 'dark-device-dark'
 ClickLabel Light
 $checks+=Verify Light $false 'light-device-dark'
 InvokeDevice shell cmd uimode night no | Out-Null
 $checks+=Verify Light $false 'light-device-light'
 InvokeDevice shell cmd uimode night yes | Out-Null
 $checks+=Verify Light $false 'light-device-dark-again'
 ClickLabel System
 $checks+=Verify System $true 'system-immediate-dark'
 InvokeDevice shell cmd uimode night no | Out-Null
 $checks+=Verify System $false 'system-live-light'
 InvokeDevice shell cmd uimode night yes | Out-Null
 $checks+=Verify System $true 'system-live-dark'
} finally {
 InvokeDevice shell cmd uimode night no | Out-Null
 ClickLabel System
}
$checks | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $workFolder 'results.json')
$checks | ConvertTo-Json -Depth 4

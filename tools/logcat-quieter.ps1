# Adb logcat with View tag at WARNING+ to hide API 35+ INFO spam:
#   "setRequestedFrameRate frameRate=NaN" (from androidx.compose, not the app).
# Other tags use verbose default (adjust *:D / *:I as you prefer).
# Usage: .\tools\logcat-quieter.ps1
#   Android Studio: same idea — in Logcat create a filter with tag "View" min level Warning,
#   or in the main filter line use: package:mine level:verbose and exclude by search if supported.
param(
  [string]$AppId = "com.phnem.vetro"
)
$errPref = $ErrorActionPreference
$ErrorActionPreference = "Stop"
try {
  $appPid = (adb shell pidof -s $AppId 2>$null).Trim()
  if ($appPid) {
    adb logcat --pid $appPid -T 0 View:W *:V
  } else {
    Write-Host "App not running, streaming all (View:W)..." -ForegroundColor Yellow
    adb logcat -T 0 View:W *:V
  }
} finally {
  $ErrorActionPreference = $errPref
}

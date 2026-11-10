# restore.ps1 - Puts the TV back the way Amazon shipped it.
# Re-enables every package this project disabled, restores the sleep/animation settings,
# stops the guard and side-button mapper, and hands the TV back to Amazon's home screen.
# Started by double-clicking 3-restore.cmd in the folder above.

param(
    [string]$TvIp = "",             # Fire TV IP; falls back to tv-config.cmd
    [string]$ExpectedSerial = ""    # optional ro.serialno, as a safety check
)

$Root = Split-Path -Parent $PSScriptRoot
$Adb  = Join-Path $Root "platform-tools\adb.exe"

if (-not $TvIp) {
    $cfg = Join-Path $Root "tv-config.cmd"
    if (Test-Path $cfg) {
        $m = Select-String -Path $cfg -Pattern 'TV_IP=(\S+)' | Select-Object -First 1
        if ($m) { $TvIp = $m.Matches[0].Groups[1].Value }
    }
}
if (-not $TvIp) {
    Write-Host "No TV IP found. Run: 3-restore.cmd -TvIp <address>  (or set tv-config.cmd)." -ForegroundColor Red
    exit 1
}
$Target = "${TvIp}:5555"

& $Adb connect $Target | Out-Null
$actual = (& $Adb -s $Target shell getprop ro.serialno | Out-String).Trim()
if (-not $actual) {
    Write-Host "Could not reach a TV at $Target. Nothing was changed." -ForegroundColor Red
    exit 1
}
if ($ExpectedSerial -and $actual -ne $ExpectedSerial) {
    Write-Host "Reached a TV with serial '$actual', not '$ExpectedSerial'. Nothing was changed." -ForegroundColor Red
    exit 1
}
Write-Host "Restoring TV $actual ..."

# Stop the on-TV helpers.
& $Adb -s $Target shell "pkill -f 'home-guard[.]sh'" 2>$null | Out-Null
& $Adb -s $Target shell "pkill -f 'logcat -T 1 -s ActivityManage[r]'" 2>$null | Out-Null
& $Adb -s $Target shell "pkill -f 'side-button[.]sh'" 2>$null | Out-Null
& $Adb -s $Target shell "pkill -f 'getevent -q /dev/input/even[t]1'" 2>$null | Out-Null

# Re-enable every package this project disabled (reads each disabled-ok.txt we wrote).
$lists = Get-ChildItem -Path (Join-Path $Root "logs") -Recurse -Filter "disabled-ok.txt" -ErrorAction SilentlyContinue
$pkgs = @($lists | ForEach-Object { Get-Content $_.FullName } | Where-Object { $_ -match '^\S+$' } | Sort-Object -Unique)
Write-Host "Re-enabling $($pkgs.Count) packages..."
foreach ($p in $pkgs) { & $Adb -s $Target shell "pm enable $p" | Out-Null }

# Restore the stock sleep / screensaver / animation settings.
& $Adb -s $Target shell "settings put secure sleep_timeout 840000"
& $Adb -s $Target shell "settings put system screen_off_timeout 900000"
& $Adb -s $Target shell "settings put secure screensaver_enabled 1"
& $Adb -s $Target shell "settings put global window_animation_scale 1"
& $Adb -s $Target shell "settings put global transition_animation_scale 1"
& $Adb -s $Target shell "settings put global animator_duration_scale 1"

# Switch FSTV Launcher off and go back to Amazon's home screen.
& $Adb -s $Target shell "pm disable-user --user 0 com.fstv.launcher" | Out-Null
& $Adb -s $Target shell "am start -n com.amazon.tv.launcher/.ui.HomeActivity_vNext" | Out-Null

Write-Host "Done. Amazon's home screen is back, bloat re-enabled, settings restored." -ForegroundColor Green
Write-Host "To use FSTV Launcher again: platform-tools\adb.exe -s $Target shell pm enable com.fstv.launcher"

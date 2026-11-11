# darken.ps1 - Dim the TV's real backlight (a true darken, not the grey AOD picture).
#   7-darken.cmd    -> darkens to the lowest backlight
#   8-brighten.cmd  -> restores your previous brightness
# This lowers the panel's LED backlight over ADB. It is fully reversible and touches nothing else.

param(
    [int]$Level = 0,          # 0 = dimmest backlight this panel allows
    [switch]$Restore,         # bring the backlight back to the saved value
    [string]$TvIp = ""
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
if (-not $TvIp) { $TvIp = (Read-Host "Enter your TV's IP address").Trim() }
if (-not $TvIp) { Write-Host "No TV IP given." -ForegroundColor Red; exit 1 }
$Target = "${TvIp}:5555"

if (-not (Test-Path $Adb)) { Write-Host "Run 2-build.cmd first (it downloads adb)." -ForegroundColor Red; exit 1 }

& $Adb connect $Target | Out-Null
Start-Sleep -Milliseconds 500
$line = @(& $Adb devices) | Where-Object { $_ -match ([regex]::Escape($Target)) } | Select-Object -First 1
$state = if ($line) { ($line -split "\s+")[1] } else { "missing" }
if ($state -ne "device") {
    Write-Host "Could not connect (state: $state). Check ADB Debugging is on and the IP is right." -ForegroundColor Red
    exit 1
}

function Get-Int($key, $fallback) {
    $v = (& $Adb -s $Target shell settings get system $key | Out-String).Trim()
    if ($v -match '^\d+$') { return [int]$v } else { return $fallback }
}

# Keep brightness manual, or the auto sensor fights our value.
& $Adb -s $Target shell "settings put system screen_brightness_mode 0" | Out-Null

if ($Restore) {
    $saved = Get-Int "fstv_saved_brightness" 102
    & $Adb -s $Target shell "settings put system screen_brightness $saved" | Out-Null
    Write-Host "Backlight restored to $saved / 255." -ForegroundColor Green
} else {
    $cur = Get-Int "screen_brightness" 102
    if ($cur -gt 0) {
        & $Adb -s $Target shell "settings put system fstv_saved_brightness $cur" | Out-Null
    }
    if ($Level -lt 0) { $Level = 0 }
    if ($Level -gt 255) { $Level = 255 }
    & $Adb -s $Target shell "settings put system screen_brightness $Level" | Out-Null
    Write-Host "Backlight darkened to $Level / 255 (was $cur). Run 8-brighten.cmd to restore." -ForegroundColor Green
}

# setup.ps1 - One-click setup: installs FSTV Launcher and turns on the tvOS-style home screen.
# Reads your TV's IP from tv-config.cmd (or asks). Run 2-build.cmd first to build the app.
# Started by double-clicking setup.cmd in the folder above.

param([string]$TvIp = "")

$Root = Split-Path -Parent $PSScriptRoot
$Adb  = Join-Path $Root "platform-tools\adb.exe"
$Apk  = Join-Path $Root "out\TvHome.apk"

function Step($m) { Write-Host ""; Write-Host "==> $m" -ForegroundColor Cyan }

if (-not $TvIp) {
    $cfg = Join-Path $Root "tv-config.cmd"
    if (Test-Path $cfg) {
        $m = Select-String -Path $cfg -Pattern 'TV_IP=(\S+)' | Select-Object -First 1
        if ($m) { $TvIp = $m.Matches[0].Groups[1].Value }
    }
}
if (-not $TvIp) { $TvIp = (Read-Host "Enter your TV's IP address (Settings > About > Network)").Trim() }
if (-not $TvIp) { Write-Host "No TV IP given." -ForegroundColor Red; exit 1 }
$Target = "${TvIp}:5555"

if (-not (Test-Path $Adb)) { Write-Host "Run 2-build.cmd first (it downloads adb and builds the app)." -ForegroundColor Red; exit 1 }
if (-not (Test-Path $Apk)) { Write-Host "The app isn't built yet - run 2-build.cmd first." -ForegroundColor Red; exit 1 }

Step "Connecting to $Target  (look at the TV and choose 'Allow' if it asks about USB debugging)"
& $Adb connect $Target | Out-Null
$deadline = (Get-Date).AddSeconds(120); $state = "missing"
while ((Get-Date) -lt $deadline) {
    $line = @(& $Adb devices) | Where-Object { $_ -match ([regex]::Escape($Target)) } | Select-Object -First 1
    $state = if ($line) { ($line -split "\s+")[1] } else { "missing" }
    if ($state -eq "device") { break }
    if ($state -ne "unauthorized") { & $Adb connect $Target | Out-Null }
    Start-Sleep -Seconds 2
}
if ($state -ne "device") {
    Write-Host "Could not connect (state: $state). Check ADB Debugging is on and the IP is right, then retry." -ForegroundColor Red
    exit 1
}
$model = (& $Adb -s $Target shell getprop ro.product.model | Out-String).Trim()
Write-Host "Connected to: $model"

Step "Installing FSTV Launcher"
& $Adb -s $Target install -r -g $Apk | Select-Object -Last 1

# Let the in-app home guard see the foreground app, so FSTV stays home with no PC needed.
& $Adb -s $Target shell "appops set com.fstv.launcher android:get_usage_stats allow" 2>$null | Out-Null

Step "Turning on the always-on look (never fully off, just dimmed) and faster animations"
& $Adb -s $Target shell "settings put secure sleep_timeout -1"
& $Adb -s $Target shell "settings put system screen_off_timeout 2147483647"
& $Adb -s $Target shell "settings put secure screensaver_enabled 0"
& $Adb -s $Target shell "settings put global window_animation_scale 0.5"
& $Adb -s $Target shell "settings put global transition_animation_scale 0.5"
& $Adb -s $Target shell "settings put global animator_duration_scale 0.5"

Step "Turning on HDMI-CEC (one remote, auto-wake, and the 5-min auto-return from dead inputs)"
& $Adb -s $Target shell "settings put global hdmi_control_enabled 1"                # master CEC switch
& $Adb -s $Target shell "settings put global hdmi_control_auto_wakeup_enabled 1"    # a device turning on wakes the TV
& $Adb -s $Target shell "settings put global hdmi_control_auto_device_off_enabled 1" # TV off -> connected devices standby
# Note: on some TVs (confirmed Toshiba Fire TV Edition), a PS5 may renegotiate HDMI audio to
# Dolby bitstream after CEC handshakes and lose TV-speaker sound. Fix on the PS5 side:
# Settings > Sound > Audio Output > Audio Format (Priority) > Linear PCM.

$files = "/sdcard/Android/data/com.fstv.launcher/files"
if (Test-Path (Join-Path $Root "images\wallpaper.jpg")) {
    Step "Copying your wallpaper and screensaver photos"
    & $Adb -s $Target shell "mkdir -p $files/screensaver"
    & $Adb -s $Target push (Join-Path $Root "images\wallpaper.jpg") "$files/wallpaper.jpg" | Out-Null
    if (Test-Path (Join-Path $Root "images\screensaver")) {
        & $Adb -s $Target push (Join-Path $Root "images\screensaver") "$files/" | Out-Null
    }
}

$amazonHome = (& $Adb -s $Target shell "pm list packages com.amazon.tv.launcher" | Out-String).Trim()
if ($amazonHome) {
    Step "Fire TV detected - starting the Home takeover (re-run 4-start-home-guard.cmd after a full power-off)"
    & $Adb -s $Target push (Join-Path $Root "scripts\home-guard.sh") /data/local/tmp/home-guard.sh | Out-Null
    & $Adb -s $Target shell "pkill -f 'home-guard[.]sh'; pkill -f 'logcat -T 1 -s ActivityManage[r]'"
    & $Adb -s $Target shell "setsid sh /data/local/tmp/home-guard.sh > /dev/null 2>&1 < /dev/null &"
} else {
    Write-Host "Not a Fire TV - open Settings and set FSTV Launcher as your default Home app." -ForegroundColor Yellow
}

& $Adb -s $Target shell "am start -n com.fstv.launcher/.HomeActivity" | Out-Null
& $Adb -s $Target shell "am kill-all" | Out-Null   # free RAM so the first app launches fast
Step "Done. Press Home on your remote - FSTV Launcher is ready."

# recon.ps1 - READ-ONLY survey of the Fire TV over Wi-Fi ADB.
# Changes nothing on the TV. Everything is saved to ..\logs\recon-<time>\
# Started by double-clicking 1-recon.cmd in the folder above.

param([string]$TvIp = "")

$ErrorActionPreference = "Continue"
$ProgressPreference    = "SilentlyContinue"   # makes downloads much faster on Windows PowerShell 5.1
$Root  = Split-Path -Parent $PSScriptRoot
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Out   = Join-Path $Root "logs\recon-$Stamp"
New-Item -ItemType Directory -Force -Path $Out | Out-Null
Start-Transcript -Path (Join-Path $Out "transcript.txt") | Out-Null

function Step([string]$msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }
function Note([string]$msg) { Write-Host "    $msg" -ForegroundColor Yellow }
function Fail([string]$msg) { Write-Host "    $msg" -ForegroundColor Red }
function Finish([int]$code) {
    Stop-Transcript | Out-Null
    Set-Content -Path (Join-Path $Root "logs\LAST_RUN.txt") -Value "recon-$Stamp exit=$code"
    exit $code
}

# ---------------------------------------------------------------- 1. adb
$Adb = Join-Path $Root "platform-tools\adb.exe"
if (-not (Test-Path $Adb)) {
    Step "Downloading Android platform-tools (adb) from Google"
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $zip = Join-Path $Root "platform-tools.zip"
        Invoke-WebRequest -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $Root -Force
        Remove-Item $zip -Force
    } catch {
        Fail "Download failed: $_"
        Finish 1
    }
}
& $Adb version | Select-Object -First 2
& $Adb start-server | Out-Null

# ---------------------------------------------------------------- 2. find the TV
if (-not $TvIp) {
    Note "The TV's IP is on the TV under Settings > My Fire TV > About > Network."
    $TvIp = (Read-Host "    Fire TV IP address (or just press Enter to scan your network)").Trim()
}
if (-not $TvIp) {
    Step "Scanning your local network for ADB (port 5555)"
    $cfg = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -ne $null -and $_.NetAdapter.Status -eq "Up" } | Select-Object -First 1
    if (-not $cfg) { Fail "Could not find an active network adapter on this PC."; Finish 1 }
    $myIp   = @($cfg.IPv4Address)[0].IPAddress
    $prefix = ($myIp.Split('.')[0..2]) -join '.'
    Note "This PC is $myIp - checking $prefix.1 to $prefix.254"
    $probes = @()
    foreach ($i in 1..254) {
        $ip = "$prefix.$i"
        if ($ip -eq $myIp) { continue }
        $c = New-Object System.Net.Sockets.TcpClient
        $probes += [pscustomobject]@{ Ip = $ip; Client = $c; Task = $c.ConnectAsync($ip, 5555) }
    }
    Start-Sleep -Milliseconds 2500
    $found = @($probes | Where-Object { $_.Task.Status -eq "RanToCompletion" } | ForEach-Object { $_.Ip })
    $probes | ForEach-Object { $_.Client.Close() }

    if ($found.Count -eq 0) {
        Fail "No device with ADB open was found."
        Fail "Check that ADB Debugging is ON (Settings > My Fire TV > Developer Options)"
        Fail "and that the TV is on the same network, then run this again and type the IP."
        Finish 1
    } elseif ($found.Count -eq 1) {
        $TvIp = $found[0]
        Note "Found ADB on $TvIp - you will be asked to confirm it is the right TV after connecting."
    } else {
        Note ("Found ADB on: " + ($found -join ", "))
        $TvIp = (Read-Host "    Type the TV's IP from the list above").Trim()
    }
}

# ---------------------------------------------------------------- 3. connect + authorize
$Serial = "${TvIp}:5555"
Step "Connecting to $Serial"
& $Adb disconnect $Serial 2>$null | Out-Null
& $Adb connect $Serial
Note "LOOK AT THE TV: if it asks 'Allow USB debugging?', choose"
Note "'Always allow from this computer' and then OK. Waiting up to 2 minutes..."
$deadline = (Get-Date).AddSeconds(120)
$state = "missing"
while ((Get-Date) -lt $deadline) {
    $line  = @(& $Adb devices) | Where-Object { $_ -match ("^" + [regex]::Escape($Serial) + "\s") } | Select-Object -First 1
    $state = if ($line) { ($line -split "\s+")[1] } else { "missing" }
    if ($state -eq "device") { break }
    if ($state -eq "missing" -or $state -eq "offline") { & $Adb connect $Serial | Out-Null }
    Start-Sleep -Seconds 2
}
if ($state -ne "device") {
    Fail "Could not get an authorized ADB connection (last state: $state)."
    Fail "If the TV showed a prompt, approve it and run this again."
    Finish 1
}
Note "Connected and authorized."
$model = (& $Adb -s $Serial shell getprop ro.product.model | Out-String).Trim()
$dname = (& $Adb -s $Serial shell settings get global device_name | Out-String).Trim()
Note "This TV reports: model '$model', name '$dname'"
Note "(Confirm this is the TV you intend to modify before continuing.)"
$ok = (Read-Host "    Is this the right TV? (y/n)").Trim()
if ($ok -notmatch '^[yY]') {
    & $Adb disconnect $Serial | Out-Null
    Fail "Disconnected without reading anything. Run again and type the right TV's IP."
    Finish 1
}

# ---------------------------------------------------------------- 4. read device info (read-only)
function Save([string]$name, [string]$cmd) {
    $res = & $Adb -s $Serial shell "( $cmd ) 2>&1"
    $res | Out-File -FilePath (Join-Path $Out $name) -Encoding utf8
    return $res
}

Step "Reading device info (nothing is changed on the TV)"
$props = Save "getprop.txt"             "getprop"
Save "shell-identity.txt"               "id; getenforce; ls -l /dev/input" | Out-Null
Save "packages-system.txt"              "pm list packages -s" | Out-Null
Save "packages-thirdparty.txt"          "pm list packages -3" | Out-Null
Save "packages-disabled.txt"            "pm list packages -d" | Out-Null
Save "packages-paths.txt"               "pm list packages -f" | Out-Null
$homeAct = Save "home-activity.txt"        "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME; echo ---; cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME; echo ---; dumpsys package com.amazon.tv.launcher | grep -E 'versionName|enabled='"
$acc  = Save "accessibility.txt"        "settings get secure enabled_accessibility_services; settings get secure accessibility_enabled"
Save "focus.txt"                        "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'" | Out-Null
$inp  = Save "input-devices.txt"        "getevent -pl"
Save "dumpsys-input.txt"                "dumpsys input" | Out-Null
Save "device-tools.txt"                 'for t in getevent sendevent input timeout pkill nohup setsid; do printf ''%s: '' $t; which $t || echo missing; done; toybox --version' | Out-Null

# ---------------------------------------------------------------- 5. side-button capture
Step "Side-button capture"
Note "Put the remote down so it doesn't add noise. When the countdown starts,"
Note "go to the TV's physical side button and:"
Note "  1) press it once, then wait 3 seconds"
Note "  2) press it twice quickly, then wait 3 seconds"
Note "  3) press and HOLD it for about 2 seconds, then let go"
Note "If the button turns the TV off, that's fine: turn it back on afterwards."
Note "Whatever was captured before that is kept."
$answer = (Read-Host "    Press Enter to start the 25-second capture, or type S to skip").Trim()
$cap = Join-Path $Out "button-capture.txt"
if ($answer -notmatch '^[sS]') {
    $p = Start-Process -FilePath $Adb -ArgumentList @("-s", $Serial, "shell", "getevent", "-lt") `
         -RedirectStandardOutput $cap -RedirectStandardError (Join-Path $Out "button-capture-stderr.txt") `
         -NoNewWindow -PassThru
    for ($s = 25; $s -gt 0; $s--) { Write-Host -NoNewline "`r    Capturing... $s s left   "; Start-Sleep -Seconds 1 }
    Write-Host ""
    if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
    & $Adb -s $Serial shell "pkill -f 'getevent -lt'" 2>$null | Out-Null
} else {
    Note "Skipped."
}

# ---------------------------------------------------------------- 6. summary
Step "Writing summary"
$keys = "ro.product.manufacturer","ro.product.model","ro.product.name","ro.product.device",
        "ro.build.version.release","ro.build.version.sdk","ro.build.display.id",
        "ro.build.version.name","ro.build.version.fireos","ro.build.version.number","ro.build.fingerprint"
$sum = New-Object System.Collections.Generic.List[string]
$sum.Add("Fire TV recon $Stamp  target=$Serial")
$sum.Add("")
$sum.Add("== Build ==")
foreach ($k in $keys) {
    $m = $props | Select-String -SimpleMatch "[$k]" | Select-Object -First 1
    if ($m) { $sum.Add($m.Line) }
}
$sum.Add("")
$sum.Add("== Home activity ==")
$homeAct | ForEach-Object { $sum.Add([string]$_) }
$sum.Add("")
$sum.Add("== Accessibility ==")
$acc | ForEach-Object { $sum.Add([string]$_) }
$sum.Add("")
$sum.Add("== Input devices ==")
$inp | Where-Object { $_ -match "add device|^\s+name:" } | ForEach-Object { $sum.Add([string]$_) }
$sum.Add("")
$sum.Add("== Button capture (key events only) ==")
if (Test-Path $cap) {
    Get-Content $cap | Where-Object { $_ -match "EV_KEY|EV_MSC" } | ForEach-Object { $sum.Add([string]$_) }
} else {
    $sum.Add("(skipped)")
}
$sum | Out-File -FilePath (Join-Path $Out "summary.txt") -Encoding utf8

Step "Done"
Note "Saved to: $Out"
Note "Go back to Claude and say 'recon done'."
Finish 0

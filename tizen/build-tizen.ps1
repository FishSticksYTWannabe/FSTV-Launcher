# build-tizen.ps1 - package + sign + install the Tizen launcher (Windows).
# Needs Tizen Studio's CLI on PATH (tizen, sdb). See BUILD.md.
#   .\build-tizen.ps1 -Profile FSTV -TvIp 192.168.1.50
param(
    [string]$Profile = "FSTV",   # the security profile name from Certificate Manager
    [string]$TvIp = "",          # optional: sdb-connect this TV first
    [string]$Device = ""         # optional: explicit sdb device; auto-detected otherwise
)
$ErrorActionPreference = "Stop"
$here = $PSScriptRoot

function Need($n) {
    if (-not (Get-Command $n -ErrorAction SilentlyContinue)) {
        Write-Host "Missing '$n'. Install Tizen Studio (+ TV extension) and add its tools to PATH." -ForegroundColor Red
        exit 1
    }
}
Need tizen; Need sdb

if ($TvIp) { & sdb connect $TvIp | Out-Null }
if (-not $Device) {
    $row = (& sdb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' } | Select-Object -First 1)
    if ($row) { $Device = ($row -split "\s+")[0] }
}

Push-Location $here
try {
    Write-Host "==> Building web app"
    & tizen build-web -- .
    Write-Host "==> Packaging + signing (profile: $Profile)"
    & tizen package -t wgt -s $Profile -- .buildResult
    $wgt = Get-ChildItem (Join-Path $here ".buildResult") -Filter *.wgt | Select-Object -First 1
    if (-not $wgt) { throw "No .wgt was produced." }
    if ($Device) {
        Write-Host "==> Installing on $Device"
        & tizen install -n $wgt.Name -- (Join-Path $here ".buildResult") -t $Device
    } else {
        Write-Host "Built $($wgt.FullName). Connect a TV (sdb) and re-run to install." -ForegroundColor Yellow
    }
} finally { Pop-Location }

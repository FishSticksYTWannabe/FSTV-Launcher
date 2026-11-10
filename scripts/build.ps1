# build.ps1 - Builds the launcher APK on Windows without Android Studio.
# The first run downloads a JDK, the Android SDK tools and Gradle into ..\toolchain.
# It changes nothing on the TV. The result is ..\out\TvHome.apk
# Started by double-clicking 2-build.cmd in the folder above.

param(
    [switch]$AcceptSdkLicenses,  # Accept Google's Android SDK licenses without prompting.
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"   # makes downloads much faster on Windows PowerShell 5.1
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem

$Root          = Split-Path -Parent $PSScriptRoot
$Tools         = Join-Path $Root "toolchain"
$Download      = Join-Path $Tools "_download"
$Jdk           = Join-Path $Tools "jdk"
$Sdk           = Join-Path $Tools "android-sdk"
$GradleVersion = "8.9"
$GradleDir     = Join-Path $Tools "gradle-$GradleVersion"
$Project       = Join-Path $Root "launcher"
$OutDir        = Join-Path $Root "out"

$JdkUrl        = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
$CmdlineUrl    = "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"
$GradleUrl     = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
$AndroidApi    = "34"
$BuildTools    = "34.0.0"

function Step([string]$msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }

function Get-Archive([string]$Url, [string]$Name) {
    $zip = Join-Path $Download "$Name.zip"
    $dir = Join-Path $Download $Name
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Write-Host "    downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $zip -UseBasicParsing
    Write-Host "    extracting"
    [IO.Compression.ZipFile]::ExtractToDirectory($zip, $dir)
    Remove-Item $zip -Force
    return $dir
}

New-Item -ItemType Directory -Force -Path $Tools, $Download, $OutDir | Out-Null

# ---------------------------------------------------------------- 1. JDK 17
if (-not (Test-Path (Join-Path $Jdk "bin\java.exe"))) {
    Step "Fetching JDK 17"
    $x = Get-Archive $JdkUrl "jdk"
    $inner = Get-ChildItem $x -Directory | Select-Object -First 1
    Move-Item $inner.FullName $Jdk
    Remove-Item $x -Recurse -Force
}

$env:JAVA_HOME = $Jdk
$env:Path      = (Join-Path $Jdk "bin") + ";" + $env:Path

# ---------------------------------------------------------------- 2. Android SDK
$Latest     = Join-Path $Sdk "cmdline-tools\latest"
$SdkManager = Join-Path $Latest "bin\sdkmanager.bat"
if (-not (Test-Path $SdkManager)) {
    Step "Fetching Android command-line tools"
    $x = Get-Archive $CmdlineUrl "cmdline-tools"
    New-Item -ItemType Directory -Force -Path (Split-Path $Latest) | Out-Null
    Move-Item (Join-Path $x "cmdline-tools") $Latest
    Remove-Item $x -Recurse -Force
}

$env:ANDROID_HOME     = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

if (-not (Test-Path (Join-Path $Sdk "licenses\android-sdk-license"))) {
    Step "Android SDK licenses"
    if ($AcceptSdkLicenses) {
        # A file works here where a PowerShell pipe does not: sdkmanager reads it as plain console input.
        $answers = Join-Path $Download "license-answers.txt"
        [IO.File]::WriteAllText($answers, (("y`r`n") * 30), [Text.Encoding]::ASCII)
        cmd /c "`"$SdkManager`" --sdk_root=`"$Sdk`" --licenses < `"$answers`""
        Remove-Item $answers -Force
    } else {
        Write-Host "    Google requires you to accept these before the SDK can be installed."
        & $SdkManager "--sdk_root=$Sdk" --licenses
    }
    if (-not (Test-Path (Join-Path $Sdk "licenses\android-sdk-license"))) {
        throw "The Android SDK licenses were not accepted, so the SDK cannot be installed."
    }
}

$Platform = Join-Path $Sdk "platforms\android-$AndroidApi\android.jar"
$Aapt     = Join-Path $Sdk "build-tools\$BuildTools\aapt.exe"
if (-not ((Test-Path $Platform) -and (Test-Path $Aapt))) {
    Step "Installing Android platform $AndroidApi and build-tools $BuildTools"
    & $SdkManager "--sdk_root=$Sdk" "platforms;android-$AndroidApi" "build-tools;$BuildTools" |
        ForEach-Object { if ($_ -notmatch '\[=') { $_ } }
    if (-not ((Test-Path $Platform) -and (Test-Path $Aapt))) {
        throw "Installing the Android SDK packages failed."
    }
}

# ---------------------------------------------------------------- 3. Gradle
$Gradle = Join-Path $GradleDir "bin\gradle.bat"
if (-not (Test-Path $Gradle)) {
    Step "Fetching Gradle $GradleVersion"
    $x = Get-Archive $GradleUrl "gradle"
    Move-Item (Join-Path $x "gradle-$GradleVersion") $GradleDir
    Remove-Item $x -Recurse -Force
}

# Keeps Gradle's large download cache inside this project instead of the user profile.
$env:GRADLE_USER_HOME = Join-Path $Tools "gradle-home"

# ---------------------------------------------------------------- 4. Build
Step "Building the launcher"
$tasks = @()
if ($Clean) { $tasks += "clean" }
$tasks += "assembleRelease"

Push-Location $Project
try {
    & $Gradle @tasks --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "The Gradle build failed (exit $LASTEXITCODE)." }
} finally {
    Pop-Location
}

# ---------------------------------------------------------------- 5. Result
$Built = Join-Path $Project "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $Built)) { throw "The build finished but $Built is missing." }
$Apk = Join-Path $OutDir "TvHome.apk"
Copy-Item $Built $Apk -Force

Step "Done"
Write-Host ("    {0}  ({1:N1} MB)" -f $Apk, ((Get-Item $Apk).Length / 1MB))

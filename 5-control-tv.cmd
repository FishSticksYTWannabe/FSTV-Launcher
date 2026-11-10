@echo off
rem Mirror the Fire TV on this PC and control it with mouse + keyboard (arrows = D-pad,
rem Enter = Select, right-click = Back). Requires scrcpy in a tools-scrcpy\ folder here
rem (download from https://github.com/Genymobile/scrcpy/releases and unzip into tools-scrcpy\).
cd /d "%~dp0"
call "%~dp0tv-config.cmd"
set TV=%TV_IP%:5555
platform-tools\adb.exe connect %TV%
set SCRCPY=
for /r "%~dp0tools-scrcpy" %%f in (scrcpy.exe) do set SCRCPY=%%f
if "%SCRCPY%"=="" (
    echo scrcpy.exe not found. Download scrcpy and unzip it into a tools-scrcpy\ folder here.
    pause
    exit /b 1
)
"%SCRCPY%" --tcpip=%TV% --window-title="FSTV Launcher" --stay-awake --max-fps=60
pause

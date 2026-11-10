@echo off
rem Double-click after a full TV restart to re-enable the side-button navigation mapper.
cd /d "%~dp0"
call "%~dp0tv-config.cmd"
set ADB=platform-tools\adb.exe
set TV=%TV_IP%:5555
%ADB% connect %TV%
%ADB% -s %TV% push scripts\side-button.sh /data/local/tmp/side-button.sh
%ADB% -s %TV% shell "pkill -f 'side-button[.]sh'; pkill -f 'getevent -q /dev/input/even[t]1'"
%ADB% -s %TV% shell "setsid sh /data/local/tmp/side-button.sh > /dev/null 2>&1 < /dev/null &"
echo.
echo Side-button mapper started.
pause

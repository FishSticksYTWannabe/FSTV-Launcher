@echo off
rem Double-click after a full TV restart so the Home button returns to FSTV Launcher.
cd /d "%~dp0"
call "%~dp0tv-config.cmd"
set ADB=platform-tools\adb.exe
set TV=%TV_IP%:5555
%ADB% connect %TV%
%ADB% -s %TV% push scripts\home-guard.sh /data/local/tmp/home-guard.sh
%ADB% -s %TV% shell "pkill -f 'home-guard[.]sh'; pkill -f 'logcat -T 1 -s ActivityManage[r]'"
%ADB% -s %TV% shell "setsid sh /data/local/tmp/home-guard.sh > /dev/null 2>&1 < /dev/null &"
echo.
echo Home guard started.
pause

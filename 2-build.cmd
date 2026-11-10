@echo off
rem Double-click to build the launcher APK. It does not touch the TV. The result goes to the out folder.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" %*
echo.
pause

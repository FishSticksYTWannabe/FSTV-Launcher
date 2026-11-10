@echo off
rem One-click setup: installs FSTV Launcher and turns on the tvOS-style home screen.
rem Run 2-build.cmd first, and set your TV's IP in tv-config.cmd.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup.ps1" %*
echo.
pause

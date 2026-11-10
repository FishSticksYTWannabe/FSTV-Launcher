@echo off
rem Double-click to give the TV back to Amazon's home screen. TV Home stays installed but switched off.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\restore.ps1" %*
echo.
pause

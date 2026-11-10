@echo off
rem Double-click to run the read-only Fire TV survey. Results go to the logs folder.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\recon.ps1" %*
echo.
pause

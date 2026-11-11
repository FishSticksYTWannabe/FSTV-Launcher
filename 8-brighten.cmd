@echo off
rem Restore the TV backlight to the brightness it had before 7-darken.cmd.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\darken.ps1" -Restore %*
pause

@echo off
rem Dim the TV's real backlight (a true darken, not the grey always-on picture).
rem Reversible: run 8-brighten.cmd to bring it back.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\darken.ps1" %*
pause

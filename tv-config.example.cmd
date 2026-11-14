@echo off
rem --- FSTV Launcher: your Fire TV's address ---
rem Copy this file to "tv-config.cmd" (next to it) and set your TV's IP address.
rem Find it on the TV under: Settings > My Fire TV > About > Network.
rem tv-config.cmd is git-ignored, so your IP never gets published.
set TV_IP=192.168.1.100

rem --- How many tiles per row in the grid. 5 is the default (bigger tiles); 6 or 7 are
rem --- tighter and fit more without scrolling. Re-run setup.cmd to apply a change.
set COLUMNS=5

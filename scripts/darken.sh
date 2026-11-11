#!/usr/bin/env bash
# darken.sh - Dim the TV's real backlight (a true darken, not the grey AOD picture).
#   bash scripts/darken.sh            # darken to the lowest backlight
#   bash scripts/darken.sh restore    # restore the saved brightness
#   bash scripts/darken.sh 40         # darken to a specific level (0-255)
# Reversible; touches only the backlight.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="$(command -v adb || echo "$ROOT/platform-tools/adb")"

TV_IP=""
[ -f "$ROOT/tv-config.cmd" ] && TV_IP="$(grep -oE 'TV_IP=[^[:space:]]+' "$ROOT/tv-config.cmd" | head -1 | cut -d= -f2)"
[ -z "$TV_IP" ] && { read -r -p "Enter your TV's IP address: " TV_IP; }
[ -z "$TV_IP" ] && { echo "No TV IP given."; exit 1; }
T="${TV_IP}:5555"

"$ADB" connect "$T" >/dev/null 2>&1
sleep 0.5
if ! "$ADB" devices | grep -q "$T[[:space:]]*device"; then
    echo "Could not connect. Check ADB Debugging is on and the IP is right."; exit 1
fi

geti() { v="$("$ADB" -s "$T" shell settings get system "$1" | tr -d '\r\n ')"; case "$v" in (''|*[!0-9]*) echo "$2";; (*) echo "$v";; esac; }

"$ADB" -s "$T" shell "settings put system screen_brightness_mode 0" >/dev/null 2>&1

if [ "${1:-}" = "restore" ]; then
    saved="$(geti fstv_saved_brightness 102)"
    "$ADB" -s "$T" shell "settings put system screen_brightness $saved" >/dev/null 2>&1
    echo "Backlight restored to $saved / 255."
else
    level="${1:-0}"
    [ "$level" -lt 0 ] 2>/dev/null && level=0
    [ "$level" -gt 255 ] 2>/dev/null && level=255
    cur="$(geti screen_brightness 102)"
    [ "$cur" -gt 0 ] 2>/dev/null && "$ADB" -s "$T" shell "settings put system fstv_saved_brightness $cur" >/dev/null 2>&1
    "$ADB" -s "$T" shell "settings put system screen_brightness $level" >/dev/null 2>&1
    echo "Backlight darkened to $level / 255 (was $cur). Run: bash scripts/darken.sh restore"
fi

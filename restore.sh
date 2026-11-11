#!/usr/bin/env bash
# restore.sh - Put the TV back the way Amazon shipped it (macOS/Linux).
# Re-enables every package this project disabled, restores the sleep/animation settings,
# stops the guard and side-button mapper, and hands the TV back to Amazon's home screen.
#
#   bash restore.sh 192.168.1.42
#   TV_IP=192.168.1.42 bash restore.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ -x "$ROOT/platform-tools/adb" ]; then ADB="$ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then ADB="adb"
else echo "adb not found. Install Android platform-tools." >&2; exit 1
fi

TV_IP="${1:-${TV_IP:-}}"
if [ -z "$TV_IP" ] && [ -f "$ROOT/tv-config.cmd" ]; then
  TV_IP="$(grep -oE 'TV_IP=[^[:space:]]+' "$ROOT/tv-config.cmd" | head -n1 | cut -d= -f2 || true)"
fi
[ -z "$TV_IP" ] && { echo "No TV IP found. Run: bash restore.sh <address>" >&2; exit 1; }
TARGET="$TV_IP:5555"

"$ADB" connect "$TARGET" >/dev/null || true
ACTUAL="$("$ADB" -s "$TARGET" shell getprop ro.serialno | tr -d '\r')"
[ -z "$ACTUAL" ] && { echo "Could not reach a TV at $TARGET. Nothing was changed." >&2; exit 1; }
echo "Restoring TV $ACTUAL ..."

# Stop the on-TV helpers.
"$ADB" -s "$TARGET" shell "pkill -f 'home-guard[.]sh'" 2>/dev/null || true
"$ADB" -s "$TARGET" shell "pkill -f 'logcat -T 1 -s ActivityManage[r]'" 2>/dev/null || true
"$ADB" -s "$TARGET" shell "pkill -f 'side-button[.]sh'" 2>/dev/null || true
"$ADB" -s "$TARGET" shell "pkill -f 'getevent -q /dev/input/even[t]1'" 2>/dev/null || true

# Re-enable every package this project disabled (reads each disabled-ok.txt we wrote).
if [ -d "$ROOT/logs" ]; then
  pkgs="$(find "$ROOT/logs" -name disabled-ok.txt -exec cat {} + 2>/dev/null | tr -d '\r' | grep -E '^\S+$' | sort -u || true)"
  if [ -n "$pkgs" ]; then
    echo "Re-enabling $(echo "$pkgs" | wc -l | tr -d ' ') packages..."
    while IFS= read -r p; do [ -n "$p" ] && "$ADB" -s "$TARGET" shell "pm enable $p" >/dev/null; done <<< "$pkgs"
  fi
fi

# Restore the stock sleep / screensaver / animation settings.
"$ADB" -s "$TARGET" shell "settings put secure sleep_timeout 840000"
"$ADB" -s "$TARGET" shell "settings put system screen_off_timeout 900000"
"$ADB" -s "$TARGET" shell "settings put secure screensaver_enabled 1"
"$ADB" -s "$TARGET" shell "settings put global window_animation_scale 1"
"$ADB" -s "$TARGET" shell "settings put global transition_animation_scale 1"
"$ADB" -s "$TARGET" shell "settings put global animator_duration_scale 1"

# Switch FSTV Launcher off and go back to Amazon's home screen.
"$ADB" -s "$TARGET" shell "pm disable-user --user 0 com.fstv.launcher" >/dev/null || true
"$ADB" -s "$TARGET" shell "am start -n com.amazon.tv.launcher/.ui.HomeActivity_vNext" >/dev/null || true

echo "Done. Amazon's home screen is back, bloat re-enabled, settings restored."
echo "To use FSTV Launcher again: $ADB -s $TARGET shell pm enable com.fstv.launcher"

#!/usr/bin/env bash
# setup.sh - Install FSTV Launcher and turn on the tvOS-style home screen (macOS/Linux).
# Reads your TV's IP from tv-config.cmd, the TV_IP env var, or the first argument.
# Run build.sh first. Needs adb (Android platform-tools) on your PATH, or in ./platform-tools.
#
#   bash setup.sh 192.168.1.42
#   TV_IP=192.168.1.42 bash setup.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APK="$ROOT/out/TvHome.apk"

# adb: prefer a local ./platform-tools copy, else whatever is on PATH.
if [ -x "$ROOT/platform-tools/adb" ]; then ADB="$ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then ADB="adb"
else echo "adb not found. Install Android platform-tools (brew install android-platform-tools, or apt install adb)." >&2; exit 1
fi

step() { printf '\n==> %s\n' "$1"; }

# ---- Find the TV IP ----------------------------------------------------------
TV_IP="${1:-${TV_IP:-}}"
if [ -z "$TV_IP" ] && [ -f "$ROOT/tv-config.cmd" ]; then
  TV_IP="$(grep -oE 'TV_IP=[^[:space:]]+' "$ROOT/tv-config.cmd" | head -n1 | cut -d= -f2 || true)"
fi
[ -z "$TV_IP" ] && read -r -p "Enter your TV's IP address (Settings > About > Network): " TV_IP
[ -z "$TV_IP" ] && { echo "No TV IP given." >&2; exit 1; }
TARGET="$TV_IP:5555"

# ---- Columns (grid width): COLUMNS env var, then tv-config.cmd, else 5 -------
COLUMNS_VAL="${COLUMNS:-}"
if [ -z "$COLUMNS_VAL" ] && [ -f "$ROOT/tv-config.cmd" ]; then
  COLUMNS_VAL="$(grep -oE 'COLUMNS=[0-9]+' "$ROOT/tv-config.cmd" | head -n1 | cut -d= -f2 || true)"
fi
case "$COLUMNS_VAL" in [4-8]) ;; *) COLUMNS_VAL=5;; esac

[ -f "$APK" ] || { echo "The app isn't built yet - run: bash build.sh" >&2; exit 1; }

step "Connecting to $TARGET  (look at the TV and choose 'Allow' if it asks about USB debugging)"
"$ADB" connect "$TARGET" >/dev/null || true
deadline=$(( $(date +%s) + 120 )); state="missing"
while [ "$(date +%s)" -lt "$deadline" ]; do
  state="$("$ADB" devices | awk -v t="$TARGET" '$1==t{print $2}' | head -n1)"; state="${state:-missing}"
  [ "$state" = "device" ] && break
  [ "$state" != "unauthorized" ] && "$ADB" connect "$TARGET" >/dev/null || true
  sleep 2
done
[ "$state" = "device" ] || { echo "Could not connect (state: $state). Check ADB Debugging is on and the IP is right." >&2; exit 1; }
echo "Connected to: $("$ADB" -s "$TARGET" shell getprop ro.product.model | tr -d '\r')"

step "Installing FSTV Launcher"
"$ADB" -s "$TARGET" install -r -g "$APK" | tail -n1

# Let the in-app home guard see the foreground app, so FSTV stays home with no PC needed.
"$ADB" -s "$TARGET" shell "appops set com.fstv.launcher android:get_usage_stats allow" 2>/dev/null || true

step "Turning on the always-on look (never fully off, just dimmed) and faster animations"
"$ADB" -s "$TARGET" shell "settings put secure sleep_timeout -1"
"$ADB" -s "$TARGET" shell "settings put system screen_off_timeout 2147483647"
"$ADB" -s "$TARGET" shell "settings put secure screensaver_enabled 0"
"$ADB" -s "$TARGET" shell "settings put global window_animation_scale 0.5"
"$ADB" -s "$TARGET" shell "settings put global transition_animation_scale 0.5"
"$ADB" -s "$TARGET" shell "settings put global animator_duration_scale 0.5"

step "Turning on HDMI-CEC (one remote, auto-wake, and the 5-min auto-return from dead inputs)"
"$ADB" -s "$TARGET" shell "settings put global hdmi_control_enabled 1"
"$ADB" -s "$TARGET" shell "settings put global hdmi_control_auto_wakeup_enabled 1"
"$ADB" -s "$TARGET" shell "settings put global hdmi_control_auto_device_off_enabled 1"
# Note: on some TVs (confirmed Toshiba Fire TV Edition), a PS5 may renegotiate HDMI audio to
# Dolby bitstream after CEC handshakes and lose TV-speaker sound. Fix on the PS5 side:
# Settings > Sound > Audio Output > Audio Format (Priority) > Linear PCM.

FILES="/sdcard/Android/data/com.fstv.launcher/files"
if [ -f "$ROOT/images/wallpaper.jpg" ]; then
  step "Copying your wallpaper and screensaver photos"
  "$ADB" -s "$TARGET" shell "mkdir -p $FILES/screensaver"
  "$ADB" -s "$TARGET" push "$ROOT/images/wallpaper.jpg" "$FILES/wallpaper.jpg" >/dev/null
  [ -d "$ROOT/images/screensaver" ] && "$ADB" -s "$TARGET" push "$ROOT/images/screensaver" "$FILES/" >/dev/null || true
fi

step "Setting the grid to $COLUMNS_VAL columns"
"$ADB" -s "$TARGET" shell "mkdir -p $FILES; echo $COLUMNS_VAL > $FILES/columns"

if "$ADB" -s "$TARGET" shell "pm list packages com.amazon.tv.launcher" | grep -q amazon; then
  step "Fire TV detected - starting the Home takeover (re-run bash start-home-guard.sh after a full power-off)"
  "$ADB" -s "$TARGET" push "$ROOT/scripts/home-guard.sh" /data/local/tmp/home-guard.sh >/dev/null
  "$ADB" -s "$TARGET" shell "pkill -f 'home-guard[.]sh'; pkill -f 'logcat -T 1 -s ActivityManage[r]'" || true
  "$ADB" -s "$TARGET" shell "setsid sh /data/local/tmp/home-guard.sh > /dev/null 2>&1 < /dev/null &" || true
else
  echo "Not a Fire TV - open Settings and set FSTV Launcher as your default Home app."
fi

"$ADB" -s "$TARGET" shell "am start -n com.fstv.launcher/.HomeActivity" >/dev/null
"$ADB" -s "$TARGET" shell "am kill-all" >/dev/null   # free RAM so the first app launches fast
step "Done. Press Home on your remote - FSTV Launcher is ready."

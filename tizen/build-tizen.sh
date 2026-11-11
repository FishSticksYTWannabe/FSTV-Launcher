#!/usr/bin/env bash
# build-tizen.sh - package + sign + install the Tizen launcher (macOS/Linux).
# Needs Tizen Studio's CLI on PATH (tizen, sdb). See BUILD.md.
#   ./build-tizen.sh <profile> <tv-ip>
set -euo pipefail

PROFILE="${1:-FSTV}"
TV_IP="${2:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing '$1'. Install Tizen Studio (+ TV extension) and add its tools to PATH." >&2; exit 1; }; }
need tizen; need sdb

[ -n "$TV_IP" ] && sdb connect "$TV_IP" >/dev/null || true
DEVICE="$(sdb devices | tail -n +2 | awk 'NF{print $1; exit}')"

cd "$HERE"
echo "==> Building web app"
tizen build-web -- .
echo "==> Packaging + signing (profile: $PROFILE)"
tizen package -t wgt -s "$PROFILE" -- .buildResult
WGT="$(ls .buildResult/*.wgt 2>/dev/null | head -n1 || true)"
[ -n "$WGT" ] || { echo "No .wgt was produced." >&2; exit 1; }
if [ -n "$DEVICE" ]; then
    echo "==> Installing on $DEVICE"
    tizen install -n "$(basename "$WGT")" -- .buildResult -t "$DEVICE"
else
    echo "Built $WGT. Connect a TV (sdb) and re-run to install."
fi

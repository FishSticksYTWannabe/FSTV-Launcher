#!/system/bin/sh
# Runs on the TV. Two jobs:
#
# 1. Keeps FSTV Launcher as the active home screen: whenever Fire OS brings Amazon's launcher
#    to the foreground (for example on a HOME press), this flips straight back to FSTV. It
#    polls the current foreground activity, so unlike a logcat watcher it is not affected by
#    log buffering.
#
# 2. Auto-returns to FSTV after 5 min on a dead HDMI input: watches CEC. If an external device
#    (PS5, console, player) is the active source on the HDMI bus AND that device has been in
#    standby the whole time, sends HOME. The HOME key triggers CEC One Touch Play, so the TV
#    flips its source back to Fire OS and FSTV comes up on its own.
#
# Lasts through sleep and ADB disconnects, but a full power-off stops it;
# 4-start-home-guard.cmd starts it again.

FSTV=com.fstv.launcher/.HomeActivity
IDLE_SECS=300          # 5 minutes on a dead external input -> press HOME
CEC_POLL_EVERY=12      # 12 * 0.4s = check CEC roughly every 5 s

uptime_s() { awk '{ print int($1) }' /proc/uptime; }

idle_since=""
tick=0

while true; do
    top=$(dumpsys activity activities 2>/dev/null | grep -m1 mResumedActivity)
    case "$top" in
        *com.amazon.tv.launcher/*)
            am start --activity-no-animation --ez go_to_start true -n "$FSTV" >/dev/null 2>&1
            ;;
    esac

    tick=$(( tick + 1 ))
    if [ "$tick" -ge "$CEC_POLL_EVERY" ]; then
        tick=0
        cec=$(dumpsys hdmi_control 2>/dev/null)
        case "$cec" in
            *"mActiveSource: (0x00, 0x0000)"*)
                # Fire OS owns the display; nothing to recover.
                idle_since=""
                ;;
            *)
                # Something else claimed the HDMI bus. Idle only counts if that device is in
                # standby (power_status: 1); if it's on (0), the user is actively using it.
                if echo "$cec" | grep -q "device_type: [1-9][0-9]* .*power_status: 1"; then
                    [ -z "$idle_since" ] && idle_since=$(uptime_s)
                    elapsed=$(( $(uptime_s) - idle_since ))
                    if [ "$elapsed" -ge "$IDLE_SECS" ]; then
                        # Press HOME. Fire OS's home key handler issues CEC One Touch Play,
                        # which tells the TV to switch to the Fire OS source. Our own guard
                        # (step 1 above) then keeps FSTV up instead of Amazon's launcher.
                        input keyevent 3 >/dev/null 2>&1
                        idle_since=""
                    fi
                else
                    idle_since=""
                fi
                ;;
        esac
    fi

    sleep 0.4
done

#!/system/bin/sh
# Runs on the TV. Keeps FSTV Launcher as the active home screen: whenever Fire OS brings
# Amazon's launcher to the foreground (for example on a HOME press), this flips straight back
# to FSTV. It polls the current foreground activity, so unlike a logcat watcher it is not
# affected by log buffering.
#
# Lasts through sleep and ADB disconnects, but a full power-off stops it;
# 4-start-home-guard.cmd starts it again.

FSTV=com.fstv.launcher/.HomeActivity

while true; do
    top=$(dumpsys activity activities 2>/dev/null | grep -m1 mResumedActivity)
    case "$top" in
        *com.amazon.tv.launcher/*)
            am start --activity-no-animation --ez go_to_start true -n "$FSTV" >/dev/null 2>&1
            ;;
    esac
    sleep 0.4
done

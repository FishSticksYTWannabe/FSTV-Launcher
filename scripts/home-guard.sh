#!/system/bin/sh
# Runs on the TV. When Fire OS starts Amazon's home screen, this opens TV Home over it.
# It lasts through sleep and ADB disconnects, but a full restart stops it.
# 4-start-home-guard.cmd starts it again.

while true; do
    logcat -T 1 -s ActivityManager:I | while read -r line; do
        case "$line" in
            *"START u0"*"category.HOME"*"cmp=com.amazon.tv.launcher/"*)
                am start --activity-no-animation --ez go_to_start true \
                    -n com.fstv.launcher/.HomeActivity > /dev/null 2>&1
                ;;
        esac
    done
    # logcat exits if the system log is cleared, so start it again.
    sleep 1
done

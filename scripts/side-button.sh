#!/system/bin/sh
# FSTV Launcher side-button mapper. Watches the TV's physical side button
# (/dev/input/event1, KEY_POWER) and maps short click patterns to navigation:
#   1 click  = Select
#   2 clicks = Right / Forward
#   3 clicks = Back
#   4+ clicks= Menu
#   hold     = fully power the TV off (true shutdown, not just sleep)
# Click actions are injected with sendevent into the keyboard device (fast, no JVM).
#
# Note: Fire OS also acts on this button natively (a short press opens the input/source
# menu); that cannot be suppressed without exclusively grabbing the device.
#
# Started from the PC with 6-start-side-button.cmd. Survives ADB disconnect and sleep,
# but a full power-off stops it; just run that .cmd again after turning the TV back on.

GAP=0.4          # seconds with no new press = burst ended (raise if doubles are missed)
HOLD_TICKS=2     # button held for GAP*HOLD_TICKS seconds (~0.8s) = a hold, not a click
POWER=0074       # KEY_POWER scan code (116) hex, on /dev/input/event1
KBD=/dev/input/event0   # aml_keypad; injected events here reach the system reader

tap() {          # inject one key press+release (codes from Vendor_0001_Product_0001.kl)
    sendevent $KBD 1 $1 1; sendevent $KBD 0 0 0
    sendevent $KBD 1 $1 0; sendevent $KBD 0 0 0
}

poweroff_tv() {  # fully shut the TV down; fall back to sleep if the shell user may not shut down
    reboot -p 2>/dev/null \
        || setprop sys.powerctl shutdown 2>/dev/null \
        || input keyevent 26
}

down=0
clicks=0
held=0
downticks=0

getevent -q /dev/input/event1 | while :; do
    if read -t $GAP ttype code value; then
        [ "$code" = "$POWER" ] || continue
        if [ "$value" = "00000001" ]; then
            down=1
            held=0
            downticks=0
        elif [ "$value" = "00000000" ]; then
            down=0
            if [ $held -eq 1 ]; then held=0; else clicks=$((clicks + 1)); fi
        fi
    else
        if [ $down -eq 1 ]; then
            downticks=$((downticks + 1))
            if [ $held -eq 0 ] && [ $downticks -ge $HOLD_TICKS ]; then
                held=1              # long press: fully power the TV off, once
                poweroff_tv
            fi
        elif [ $clicks -gt 0 ]; then
            case $clicks in
                1) tap 28 ;;   # DPAD_CENTER -> Select
                2) tap 106 ;;  # DPAD_RIGHT  -> Right / Forward
                3) tap 15 ;;   # BACK
                *) tap 139 ;;  # MENU
            esac
            clicks=0
        fi
    fi
done

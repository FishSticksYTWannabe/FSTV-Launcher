# Scripts reference

Every helper has a Windows version (double-click the `.cmd`, which runs a PowerShell script) and a
macOS/Linux version (run the `.sh` from a terminal). They all read your TV's IP from `tv-config.cmd`.

| Task | Windows | macOS / Linux |
| --- | --- | --- |
| Survey the TV (read-only) | `1-recon.cmd` | — |
| Build the APK | `2-build.cmd` | `bash build.sh` |
| Install + enable the launcher | `setup.cmd` | `bash setup.sh` |
| Undo everything (back to stock) | `3-restore.cmd` | `bash restore.sh` |
| Restart the Home takeover after a power-off | `4-start-home-guard.cmd` | re-run `bash setup.sh` |
| Mirror/control the TV from your computer | `5-control-tv.cmd` (scrcpy) | `scrcpy -s <ip>:5555` |
| Map the physical side button (experimental) | `6-start-side-button.cmd` | — |

## Requirements

- **Windows:** nothing extra — `2-build.cmd` downloads a JDK, the Android SDK tools and Gradle into
  `toolchain/` on first run, and the scripts fetch `adb` into `platform-tools/`.
- **macOS / Linux:** `curl` and `unzip` (preinstalled on both), plus `adb`. Install adb with
  `brew install android-platform-tools` (macOS) or `sudo apt install adb` (Debian/Ubuntu).
  `build.sh` downloads the JDK, Android SDK tools and Gradle into `toolchain/` itself.

## On-device scripts

- `scripts/home-guard.sh` — runs on the TV (Fire OS). Watches the system log and bounces you back to
  FSTV whenever Fire OS tries to show Amazon's launcher.
- `scripts/side-button.sh` — experimental. Maps the physical side button to navigation by click count.
  Off by default; see the note in the main README.

## Configuration

Copy `tv-config.example.cmd` to `tv-config.cmd` and set `TV_IP` to your TV's address. This file is
git-ignored so your TV's details never get committed.

To use your own wallpaper and screensaver, drop images into an `images/` folder before running setup:

- `images/wallpaper.jpg` — a 1920×1080 background.
- `images/screensaver/*.jpg` — the photos cycled when the screen is idle.

`setup` copies them to the launcher's private storage on the TV
(`/sdcard/Android/data/com.fstv.launcher/files/`). You can also push them there manually with `adb`.

# FSTV Launcher

A fast, tvOS-inspired replacement home screen for Fire TV and Android TV — no ads, no sponsored
rows, just your apps, with fluid animations, a rich always-on clock, and a photo screensaver. It can
take over as the real home screen even on Fire OS, which normally hard-wires the Home button to
Amazon's launcher.

![platform](https://img.shields.io/badge/platform-Fire%20TV%20%7C%20Android%20TV-333)
![Android](https://img.shields.io/badge/Android-7.1%2B%20(API%2025)-3DDC84)
![license](https://img.shields.io/badge/license-MIT-blue)

## Supported platforms

FSTV is an Android app, so it runs on **Android-based TV systems only**:

- **Fire OS** (Fire TV, Fire TV Edition TVs) — full support, including the reversible debloat and the Home takeover.
- **Android TV / Google TV** — installs and can be set as the default Home in Settings (no debloat or guard needed).
- **Not supported:** Samsung **Tizen**, LG **webOS**, **Roku OS**, Hisense **VIDAA**, and TiVo / Xumo **Entertainment OS** are not Android — they can't run APKs or let an app replace the home screen. Amazon's **Vega OS** (2025+ sticks) also blocks sideloading.

## Features

- **tvOS-style grid** — rounded app tiles that lift, tilt and catch the light on focus, with spring
  physics; a large preview of the focused app, a reflective shelf under the selection, and an open
  animation that zooms the tile to black before the app draws.
- **Always-on clock** — a corner clock with a greeting and date; after a couple of minutes idle the
  screen dims and a large digital clock fills the top; after an hour it becomes an Apple-style
  near-off screen with a big analog clock over a dimmed photo.
- **Photo screensaver + wallpaper** — your own images cycle instead of the TV sleeping.
- **Live TV / Inputs tile** — opens your TV's source app to switch inputs, when it has one
  (brand-agnostic).
- **Home takeover** (Fire OS) — a small on-device script returns you to FSTV whenever Fire OS tries
  to show Amazon's launcher, which can't be removed without root.
- **Reversible debloat** — `pm disable-user` helpers with a one-command restore that puts the TV back
  exactly as it shipped.

## Requirements

- A Fire TV or Android TV on the same network as your computer, with ADB debugging enabled.
- **Windows:** nothing else — the build script fetches its own toolchain.
- **macOS / Linux:** `adb` on your PATH (`brew install android-platform-tools` or
  `sudo apt install adb`). `build.sh` fetches the rest.

## Quick start

1. **Enable ADB on the TV.**
   - *Fire TV:* Settings → My Fire TV → Developer Options → ADB Debugging = On. (If Developer Options
     is hidden, open Settings → My Fire TV → About and click the top line 7 times first.)
   - *Android TV:* Settings → About, click Build 7 times, then Developer options → Network debugging = On.
   - Note the TV's IP under About → Network.
2. **Get the project:** click **Code → Download ZIP** above and unzip it (or `git clone`).
3. **Set your TV's IP:** copy `tv-config.example.cmd` to `tv-config.cmd` and put your IP in it.
4. **Build and install:**

   | | Windows | macOS / Linux |
   | --- | --- | --- |
   | Build | double-click `2-build.cmd` | `bash build.sh` |
   | Install | double-click `setup.cmd` | `bash setup.sh` |

   On the TV, choose **Allow** when it asks about USB debugging.
5. Press **Home** — you're on FSTV Launcher. Your installed apps are already there.

To undo everything, run `3-restore.cmd` (Windows) or `bash restore.sh` (macOS/Linux).

More detail — all the helper scripts, configuration, and adding your own wallpaper/screensaver — is in
[`docs/SCRIPTS.md`](docs/SCRIPTS.md).

## How the Home takeover works

Fire OS protects Amazon's launcher with a device profile-owner, and there is no public root for this
hardware, so FSTV can't replace it outright. Instead it declares itself a Home app and runs a tiny
log watcher on the TV that sends you straight back to FSTV whenever Amazon's launcher appears. On
plain Android TV, no takeover is needed — just set FSTV as the default Home app in Settings.

## Limitations

- **Amazon's launcher stays installed.** You'll see a brief flash of it on each Home press.
- **The physical side button is the power button.** Fire OS owns it (source menu on press, sleep on a
  long hold), so the experimental mapper can inject clicks but can't fully suppress the OS. Off by
  default.
- **Older Fire TV hardware is slow.** App launch speed is bounded by the device's RAM and CPU, not by
  the launcher.
- **Modifying your TV is at your own risk.** The debloat is reversible and nothing here roots or
  re-flashes the device, but you own the outcome.

## Building from source

Pure Kotlin, `minSdk 25`, no third-party dependencies. The build needs no Android Studio: `2-build.cmd`
(Windows) or `build.sh` (macOS/Linux) downloads a JDK 17, the Android command-line tools and Gradle
into `toolchain/`, builds `launcher/`, and writes `out/TvHome.apk`.

## Credits

Written with Claude, Anthropic's AI. Licensed under the [MIT License](LICENSE).

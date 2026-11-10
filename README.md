# FSTV Launcher

A fast, tvOS-inspired custom home screen for Fire TV (and other Android TV devices), written in
Kotlin. No ads, no sponsored rows — just your apps, with fluid spring animations, a rich
always-on clock, and a photo screensaver. Built to be sideloaded over ADB and to run as the real
home screen even on Fire OS, which normally hard-wires HOME to Amazon's launcher.

> **This was coded completely with AI.**
>
> **v0.1** — early, works-on-my-TV quality. Developed and tested on a Toshiba 43LF421U21
> (`AFTBAMR311`, Fire OS 6.7.1.1 / Android 7.1). It should run on other Fire TV and Android TV
> devices: the launcher app is a standard Android TV home screen that discovers your apps and
> scales to any screen. The setup scripts and the HOME-takeover trick are tuned for Fire OS,
> where Amazon hard-wires the Home button; on a normal Android TV you can just set FSTV as the
> default home in Settings and skip the guard.

## Features

- **tvOS-style grid** — rounded app tiles that lift, tilt and catch the light on focus, with
  spring physics; a large preview of the focused app up top; a reflective silver shelf under the
  selection; an open animation that zooms the tile to black before the app draws.
- **Always-on clock** — a quick-glance corner clock with a time-of-day greeting and date;
  after 2 minutes idle the screen dims and a big digital clock fills the top; after an hour it
  becomes an Apple-style near-off screen with a large **analog** clock over a dimmed photo
  screensaver. Minute changes animate.
- **Photo screensaver + wallpaper** — drop your own images in (see below); the TV shows them
  instead of sleeping.
- **Startup chime + wordmark**, launched on cold start.
- **Live TV / Inputs tile** — opens your TV's source app to switch HDMI/antenna inputs from the
  home screen (appears only on TVs that have one).
- **Home guard** — a tiny on-device script that returns you to FSTV whenever Fire OS tries to
  show Amazon's launcher (Amazon's launcher can't be removed without root).
- **Debloat + one-click restore** — reversible `pm disable-user` scripts, with a restore script
  that puts everything back.
- **Side-button mapper** — maps the TV's physical button: 1 click = Select, 2 = Right, 3 = Back,
  4 = Menu (hold is left to the OS, which sleeps the TV).

## Quick start (beginner-friendly)

You need a Windows PC, and the TV and PC on the same Wi-Fi. No coding and no Android Studio.

1. **Turn on ADB on the TV.**
   - Fire TV: *Settings > My Fire TV > Developer Options > ADB Debugging = On.* If you don't see
     Developer Options, open *Settings > My Fire TV > About* and click the top line 7 times first.
   - Other Android TV: *Settings > Device Preferences > About*, click *Build* 7 times, then
     *Developer options > Network/USB debugging = On.*
   - Note the TV's IP under *About > Network*.
2. **Get this project:** the green **Code** button above > **Download ZIP**, then unzip it.
3. **Tell it your TV's IP:** copy `tv-config.example.cmd` to **`tv-config.cmd`** and put your IP in it.
4. **Build the app:** double-click **`2-build.cmd`** and wait (the first run downloads the build tools).
5. **Set it up:** double-click **`setup.cmd`**. Look at the TV and choose *Allow* on the
   "Allow USB debugging?" prompt. It installs FSTV, turns on the always-on look, and (on Fire TV)
   starts the Home takeover.
6. Press **Home** — you're on FSTV Launcher. Your installed apps are already there; nothing to add.

To undo everything, double-click **`3-restore.cmd`**.

## Build

No Android Studio required. On Windows, double-click **`2-build.cmd`**. The first run downloads a
JDK, the Android command-line tools and Gradle into `toolchain/`, then builds `out/TvHome.apk`.

## Set up on your TV

1. On the TV: **Settings > My Fire TV > Developer Options > ADB Debugging = On**, and note the IP
   under **About > Network**.
2. Copy `tv-config.example.cmd` to **`tv-config.cmd`** and put your TV's IP in it.
3. Run the numbered scripts as needed:
   - **`1-recon.cmd`** — read-only survey (saves device info to `logs/`).
   - **`2-build.cmd`** — build the APK.
   - Install: `platform-tools\adb.exe -s <ip>:5555 install -r -g out\TvHome.apk`
   - **`4-start-home-guard.cmd`** — start the HOME redirect (re-run after a full power-off).
   - **`6-start-side-button.cmd`** — start the side-button mapper (re-run after a power-off).
   - **`5-control-tv.cmd`** — mirror + control the TV from your PC with scrcpy (download scrcpy
     into a `tools-scrcpy/` folder first).
   - **`3-restore.cmd`** — undo everything: re-enable all disabled apps, restore settings, hand
     the TV back to Amazon's home screen.

### Your images

Personal media is git-ignored. To use your own:

- Wallpaper: push a 1920×1080 JPG to
  `/sdcard/Android/data/com.fstv.launcher/files/wallpaper.jpg`
- Screensaver: push JPGs to
  `/sdcard/Android/data/com.fstv.launcher/files/screensaver/`

## Limitations & notes

- **Amazon's launcher stays installed.** Fire OS protects it (a device profile-owner blocks
  disabling it) and there's no public root for this hardware, so FSTV uses the Home guard to take
  over instead. You'll see a brief flash of Amazon's screen on each Home press.
- **The side button is the power button.** Short-click patterns are mapped, but a long press is
  handled by Fire OS and sleeps the TV; making that seamless would require exclusively grabbing
  the button with a native helper.
- **Modifying your TV is at your own risk.** The debloat is reversible with `3-restore.cmd`, and
  nothing here roots or re-flashes the device, but you are responsible for your hardware.

## License

MIT — see [LICENSE](LICENSE).

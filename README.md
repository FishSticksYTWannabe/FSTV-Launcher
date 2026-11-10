# FSTV Launcher

A fast, **tvOS-inspired** home screen for Fire TV and Android TV. No ads, no sponsored rows — just
your apps, with fluid spring animations, a rich always-on clock, and a photo screensaver. It can
take over as the real home screen even on Fire OS, which normally hard-wires the Home button to
Amazon's launcher.

![version](https://img.shields.io/badge/version-0.1-3DDC84)
![platform](https://img.shields.io/badge/platform-Fire%20TV%20%7C%20Android%20TV-333)
![minSdk](https://img.shields.io/badge/Android-7.1%2B%20(API%2025)-blue)
![license](https://img.shields.io/badge/license-MIT-green)
![made with AI](https://img.shields.io/badge/coded%20100%25%20with-AI-8A3FFC)

> **This project was coded completely with AI.** Every line — the Kotlin launcher, the build system,
> and the setup scripts — was written by **Claude** (Anthropic's AI), built and debugged live on a
> real Fire TV from plain-English direction. See [Credits](#credits).

---

## Features

- **tvOS-style grid** — rounded app tiles that lift, tilt and catch the light on focus, with spring
  physics; a large preview of the focused app up top; a reflective silver shelf under the selection;
  and an open animation that zooms the tile to black before the app draws.
- **Always-on clock** — a corner clock with a time-of-day greeting and date; after a couple of
  minutes idle the screen dims and a big digital clock fills the top; after an hour it becomes an
  Apple-style near-off screen with a large **analog** clock over a dimmed photo. Minute changes animate.
- **Photo screensaver + wallpaper** — your own images show instead of the TV sleeping.
- **Startup chime + wordmark** on cold start.
- **Live TV / Inputs tile** — opens your TV's source app to switch HDMI/antenna inputs from the home
  screen (appears only on TVs that have one; brand-agnostic).
- **Home guard** (Fire OS) — a tiny on-device script that returns you to FSTV whenever Fire OS tries
  to show Amazon's launcher, which can't be removed without root.
- **Reversible debloat + one-click restore** — `pm disable-user` scripts with a restore that puts
  everything back exactly as Amazon shipped it.

## Quick start (beginner-friendly)

You need a Windows PC, with the TV and PC on the same Wi-Fi. No coding and no Android Studio.

1. **Turn on ADB on the TV.**
   - Fire TV: *Settings → My Fire TV → Developer Options → ADB Debugging = On.* If Developer Options
     isn't there, open *Settings → My Fire TV → About* and click the top line 7 times first.
   - Other Android TV: *Settings → Device Preferences → About*, click *Build* 7 times, then
     *Developer options → Network/USB debugging = On.*
   - Note the TV's IP under *About → Network*.
2. **Get this project:** the green **Code** button → **Download ZIP**, then unzip it.
3. **Tell it your TV's IP:** copy `tv-config.example.cmd` to **`tv-config.cmd`** and put your IP in it.
4. **Build the app:** double-click **`2-build.cmd`** and wait (the first run downloads the build tools).
5. **Set it up:** double-click **`setup.cmd`**. Look at the TV and choose **Allow** on the
   "Allow USB debugging?" prompt. It installs FSTV, turns on the always-on look, and (on Fire TV)
   starts the Home takeover.
6. Press **Home** — you're on FSTV Launcher. Your installed apps are already there; nothing to add.

To undo everything later, double-click **`3-restore.cmd`**.

## Build from source

No Android Studio required. Double-click **`2-build.cmd`**; the first run downloads a JDK, the Android
command-line tools and Gradle into `toolchain/`, then builds `out/TvHome.apk`. The app is pure Kotlin,
`minSdk 25`, no third-party dependencies.

## The scripts

| Script | What it does |
| --- | --- |
| `1-recon.cmd` | Read-only survey of the TV (saves device info to `logs/`). |
| `2-build.cmd` | Build the APK. |
| `setup.cmd` | One-click install + enable (detects Fire TV vs Android TV). |
| `3-restore.cmd` | Undo everything and hand the TV back to its stock launcher. |
| `4-start-home-guard.cmd` | Restart the Home takeover after a full power-off (Fire TV). |
| `5-control-tv.cmd` | Mirror + control the TV from your PC with scrcpy. |
| `6-start-side-button.cmd` | (Experimental) map the physical side button to navigation. |

## Your images

Personal media is git-ignored — add your own:

- **Wallpaper:** push a 1920×1080 JPG to
  `/sdcard/Android/data/com.fstv.launcher/files/wallpaper.jpg`
- **Screensaver:** push JPGs to
  `/sdcard/Android/data/com.fstv.launcher/files/screensaver/`

## Limitations & notes

- **Amazon's launcher stays installed.** Fire OS protects it with a device profile-owner, and there's
  no public root for this hardware, so FSTV uses the Home guard to take over. You'll see a brief flash
  of Amazon's screen on each Home press.
- **The physical side button is the power button.** Fire OS owns it (it opens the source menu and
  sleeps on a long hold); the experimental mapper can inject clicks but can't fully suppress the OS,
  so it's left off by default.
- **Older Fire TV hardware is slow.** App launch speed is bounded by the device's RAM and CPU, not by
  the launcher.
- **Modifying your TV is at your own risk.** The debloat is reversible with `3-restore.cmd`, and
  nothing here roots or re-flashes the device, but you own the outcome.

## Credits

Designed and written end-to-end by **Claude**, Anthropic's AI, running in Claude Code. The repo owner
set the goals, tested each change on a real Fire TV, and published it — but the code itself is the AI's
work, start to finish.

## License

[MIT](LICENSE).

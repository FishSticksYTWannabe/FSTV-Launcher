# FSTV Launcher for Samsung Tizen TV — build & sideload

> **Experimental.** This is a *separate* Tizen web app (not the Fire TV APK). It lists and launches
> your installed apps with the tvOS look, auto-starting at power-on. It **cannot** replace the Home
> button, debloat the TV, map the side button, or free RAM — Samsung locks those; see the main
> README's Supported platforms note. Built to Tizen spec but **not yet tested on real hardware.**

## What you need (one time)
- **[Tizen Studio](https://developer.tizen.org/development/tizen-studio/download)** with the **TV
  extension** (gives you the `tizen` and `sdb` CLIs, Certificate Manager, and a TV emulator). The
  Tizen CLI alone also works.
- A free **Samsung account** (for signing).
- Your TV and PC on the same network.

## 1. Turn on Developer Mode on the TV
1. Open **Apps** on the TV, then press **1 2 3 4 5** on the remote.
2. Toggle **Developer mode = On**, enter your **PC's IP address**, and reboot the TV.

## 2. Connect the TV
```
sdb connect <TV-IP>
sdb devices          # note the device name in the list
```

## 3. Make a signing certificate (once)
In Tizen Studio: **Tools → Certificate Manager → + → Samsung → TV**, sign in with your Samsung
account, and let it create author + distributor certificates. **The TV must be connected (step 2)**
so the cert is bound to that TV's device ID. Give the security **profile** a name (e.g. `FSTV`).
Note: Samsung certs **expire** and are **per-TV** — redo this for another set.

## 4. Build, sign, install
From this `tizen/` folder:
```
# Windows
.\build-tizen.ps1 -Profile FSTV -TvIp <TV-IP>
# macOS / Linux
./build-tizen.sh FSTV <TV-IP>
```
That runs `tizen build-web` → `tizen package -t wgt -s <profile>` → `tizen install`. Or do it by hand:
```
tizen build-web -- .
tizen package -t wgt -s FSTV -- .buildResult
tizen install -n FSTVLauncher.wgt -t <device-from-sdb>
```

## 5. Auto-launch at power-on
Open the launcher once from the Apps list, then on the TV enable
**Smart Hub → (menu) → Autorun Last App = On** (name varies by model year). The last-used app
relaunches when the TV powers on — so the launcher comes up at boot. Pressing **Home** still opens
Samsung's Smart Hub; that can't be changed.

## Testing without a TV
Tizen Studio ships a **TV emulator** (Emulator Manager) — good for checking the UI and remote
navigation. App **listing/launching** behaves differently there than on a real TV (you'll see the
mock apps when `tizen` isn't present), so verify that part on hardware.

## Tuning on-device
- `js/apps.js` → `HIDDEN` : add ids of system/junk apps you don't want shown (log `getAppsInfo` to
  find them).
- `js/screensaver.js` → `PHOTOS` / `WALLPAPER` : drop images into `assets/` and list them.
- App **icon paths** from `getAppsInfo` vary by model; if icons don't show, the tiles fall back to a
  letter glyph — check the console and adjust.
- If `Autorun` or `setScreenSaver` isn't honored on your model, that's firmware-dependent.

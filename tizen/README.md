# FSTV Launcher — Tizen (experimental)

A Samsung **Tizen TV** build of FSTV: a tvOS-style **app launcher** that lists and launches your
installed apps, with the clock / always-on idle screen, photo screensaver, startup chime, and
Apple-style Settings/Store tiles — auto-starting at power-on.

It is a **separate web app** (HTML/CSS/JS), not the Fire TV APK. Because Samsung locks the platform,
it **cannot**: replace or intercept the Home button, debloat the TV, map a physical button, or free
RAM. Those stay Fire OS / Android TV only.

- **Build & sideload:** see [BUILD.md](BUILD.md) (Developer Mode → Samsung cert → `tizen` CLI → install → Autorun Last App).
- **Status:** built to Tizen spec, **not yet tested on real hardware**. A browser/Tizen emulator shows
  mock apps (no `tizen` object); real listing/launching only works on a TV.

```
config.xml        app manifest + privileges (application.info/launch, tv.inputdevice, power)
index.html        launcher shell
css/style.css     tvOS look: grid, focus, hero, shelf, clock, idle
js/apps.js        list + launch installed apps (tizen.application), browser fallback
js/app.js         focus engine, remote keys, clock/idle, chime
js/screensaver.js wallpaper + photo cycling
icon.png          app icon
build-tizen.ps1 / .sh   package + sign + install wrappers
```

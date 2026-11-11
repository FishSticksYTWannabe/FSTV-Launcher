/* app.js - FSTV Launcher for Tizen TV: grid focus engine, remote keys, clock/idle, chime. */
(function () {
    'use strict';

    var COLUMNS = 5;
    var ROW_PITCH = 282;     // tile height (176) + row gap (106)
    var IDLE_MS = 120000;    // dim + show the big clock after 2 min idle
    var DEEP_MS = 600000;    // near-off (AOD) after 10 min idle

    var grid, heroArt, heroTitle;
    var tiles = [];          // { el, app }
    var focus = 0;
    var idleTimer = 0, idleAt = 0;

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        grid = document.getElementById('grid');
        heroArt = document.getElementById('hero-art');
        heroTitle = document.getElementById('hero-title');

        keepAwake();
        registerKeys();
        document.addEventListener('keydown', onKey);

        FSTVScreensaver.init();
        startClock();
        playStartup();

        FSTVApps.load(function (apps) {
            buildGrid(apps);
            setFocus(0);
        });
    }

    /* ---- Build the grid ---- */
    function buildGrid(apps) {
        grid.innerHTML = '';
        tiles = [];
        apps.forEach(function (app) {
            var el = document.createElement('div');
            el.className = 'tile';
            styleTile(el, app);
            grid.appendChild(el);
            tiles.push({ el: el, app: app });
        });
        if (!tiles.length) { heroTitle.textContent = 'No apps found'; }
    }

    function styleTile(el, app) {
        var name = (app.name || '').toLowerCase();
        if (/setting/.test(name)) {
            el.className = 'tile settings';
            el.innerHTML = gearSvg();
            app.designed = true;
        } else if (/samsung apps|app store|galaxy store|^apps$|^store$/.test(name)) {
            el.className = 'tile store';
            el.innerHTML = gridSvg();
            app.designed = true;
        } else if (app.icon) {
            var img = document.createElement('img');
            img.src = app.icon;
            img.onerror = function () { el.removeChild(img); el.appendChild(glyph(app.name)); };
            el.appendChild(img);
        } else {
            el.appendChild(glyph(app.name));
        }
        var label = document.createElement('div');
        label.className = 'label';
        label.textContent = app.name || '';
        el.appendChild(label);
    }

    function glyph(name) {
        var g = document.createElement('div');
        g.className = 'glyph';
        g.textContent = (name || '?').charAt(0).toUpperCase();
        return g;
    }
    function gearSvg() {
        return '<div class="glyph"><svg width="110" height="110" viewBox="0 0 24 24" fill="#eceff1">' +
            '<path d="M19.43,12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49,1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46,2.18,14.25,2,14,2h-4c-.25,0-.46.18-.49.42l-.38,2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49,0-.61.22l-2,3.46c-.13.22-.07.49.12.64l2.11,1.65c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11,1.65c-.19.15-.24.42-.12.64l2,3.46c.12.22.39.3.61.22l2.49-1c.52.4,1.08.73,1.69.98l.38,2.65c.03.24.24.42.49.42h4c.25,0,.46-.18.49-.42l.38-2.65c.61-.25,1.17-.59,1.69-.98l2.49,1c.23.09.49,0,.61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12,15.5a3.5,3.5,0,1,1,0-7,3.5,3.5,0,0,1,0,7z"/></svg></div>';
    }
    function gridSvg() {
        var r = '<rect width="18" height="18" rx="4.5" ry="4.5" fill="#fff"/>';
        return '<div class="glyph"><svg width="96" height="96" viewBox="0 0 43 43">' +
            '<g>' + r.replace('<rect', '<rect x="0" y="0"') + '</g>' +
            '<g>' + r.replace('<rect', '<rect x="25" y="0"') + '</g>' +
            '<g>' + r.replace('<rect', '<rect x="0" y="25"') + '</g>' +
            '<g>' + r.replace('<rect', '<rect x="25" y="25"') + '</g>' +
            '</svg></div>';
    }

    /* ---- Focus + navigation ---- */
    function onKey(e) {
        poke();
        switch (e.keyCode) {
            case 37: move(-1, 0); break;   // left
            case 39: move(1, 0); break;    // right
            case 38: move(0, -1); break;   // up
            case 40: move(0, 1); break;    // down
            case 13: open(); break;        // enter / select
            case 10009: e.preventDefault(); break; // Tizen back: stay in the launcher
            default: return;
        }
        e.preventDefault();
    }

    function move(dx, dy) {
        if (!tiles.length) return;
        var col = focus % COLUMNS, row = Math.floor(focus / COLUMNS);
        var rows = Math.ceil(tiles.length / COLUMNS);
        if (dx) { col += dx; if (col < 0 || col >= COLUMNS) return; }
        if (dy) { row += dy; if (row < 0 || row >= rows) return; }
        var next = row * COLUMNS + col;
        if (next >= tiles.length) { if (dy > 0) next = tiles.length - 1; else return; }
        setFocus(next);
    }

    function setFocus(i) {
        if (i < 0 || i >= tiles.length) return;
        if (tiles[focus]) tiles[focus].el.classList.remove('focused');
        focus = i;
        var t = tiles[focus];
        t.el.classList.add('focused');

        var row = Math.floor(focus / COLUMNS);
        grid.style.transform = 'translateY(' + (row === 0 ? 0 : -(row * ROW_PITCH + 40)) + 'px)';
        document.body.classList.toggle('scrolled', row > 0);

        // Hero preview of the focused app.
        if (t.app.icon && !t.app.designed) {
            heroArt.src = t.app.icon; heroArt.style.display = '';
        } else {
            heroArt.removeAttribute('src'); heroArt.style.display = 'none';
        }
        heroTitle.textContent = t.app.name || '';
    }

    function open() {
        var t = tiles[focus];
        if (!t) return;
        t.el.style.transform = 'scale(1.3)';
        setTimeout(function () { FSTVApps.launch(t.app.id); }, 120);
        setTimeout(function () { if (t.el) t.el.style.transform = ''; }, 900);
    }

    /* ---- Clock + idle ---- */
    function startClock() { tick(); setInterval(tick, 1000); poke(); }

    function tick() {
        var d = new Date();
        var h = d.getHours(), m = d.getMinutes();
        var hh = (h % 12) || 12, mm = (m < 10 ? '0' : '') + m, ap = h < 12 ? 'AM' : 'PM';
        var time = hh + ':' + mm + ' ' + ap;
        setText('clock', time);
        setText('aod-time', time);
        var days = ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
        var months = ['January','February','March','April','May','June','July','August','September','October','November','December'];
        var date = days[d.getDay()] + ', ' + months[d.getMonth()] + ' ' + d.getDate();
        setText('date', date);
        setText('aod-date', date);
        setText('greeting', h < 5 ? 'Good night' : h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening');

        var idleFor = Date.now() - idleAt;
        setIdle(idleFor >= IDLE_MS, idleFor >= DEEP_MS);
    }

    function setIdle(idle, deep) {
        document.body.classList.toggle('idle', idle);
        document.body.classList.toggle('deep', deep);
        if (idle && FSTVScreensaver.count()) FSTVScreensaver.start(); else FSTVScreensaver.stop();
    }

    function poke() { idleAt = Date.now(); setIdle(false, false); }

    /* ---- Keep screen awake (Samsung-specific; ignored elsewhere) ---- */
    function keepAwake() {
        try {
            if (typeof webapis !== 'undefined' && webapis.appcommon) {
                webapis.appcommon.setScreenSaver(webapis.appcommon.AppCommonScreenSaverState.SCREEN_SAVER_OFF);
            }
        } catch (e) { /* not on a Samsung TV */ }
        try {
            if (typeof tizen !== 'undefined' && tizen.power) { tizen.power.request('SCREEN', 'SCREEN_NORMAL'); }
        } catch (e) { /* no power privilege */ }
    }

    function registerKeys() {
        try {
            if (typeof tizen !== 'undefined' && tizen.tvinputdevice) {
                ['ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue'].forEach(function (k) {
                    try { tizen.tvinputdevice.registerKey(k); } catch (e) {}
                });
            }
        } catch (e) {}
    }

    /* ---- Startup wordmark + a deep, soft chime via Web Audio ---- */
    function playStartup() {
        var splash = document.getElementById('splash');
        setTimeout(function () { splash.classList.add('gone'); }, 1700);
        setTimeout(function () { if (splash) splash.style.display = 'none'; }, 2400);
        try { chime(); } catch (e) {}
    }

    function chime() {
        var AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return;
        var ctx = new AC();
        [[146.83, 0], [220.0, 0.17]].forEach(function (pair) {
            var f = pair[0], t0 = ctx.currentTime + pair[1];
            [f, f * 0.5].forEach(function (freq, j) {
                var o = ctx.createOscillator(), g = ctx.createGain();
                o.type = 'sine'; o.frequency.value = freq;
                var peak = (j === 0 ? 0.16 : 0.09);
                g.gain.setValueAtTime(0.0001, t0);
                g.gain.linearRampToValueAtTime(peak, t0 + 0.05);
                g.gain.exponentialRampToValueAtTime(0.0001, t0 + 1.6);
                o.connect(g); g.connect(ctx.destination);
                o.start(t0); o.stop(t0 + 1.7);
            });
        });
    }

    function setText(id, s) { var el = document.getElementById(id); if (el) el.textContent = s; }
})();

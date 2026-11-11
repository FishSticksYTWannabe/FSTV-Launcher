/* screensaver.js - wallpaper + a cycling photo screensaver while the TV is idle.
 *
 * Drop your own images into tizen/assets/ and list their filenames in PHOTOS below
 * (a web app cannot list a folder on its own, so the names are declared here).
 * The wallpaper is assets/wallpaper.jpg if present, else a built-in gradient. */
var FSTVScreensaver = (function () {
    var PHOTOS = [
        // 'assets/shot1.jpg', 'assets/shot2.jpg', 'assets/shot3.jpg'
    ];
    var WALLPAPER = 'assets/wallpaper.jpg';

    var layer, idx = 0, timer = null;

    function init() {
        layer = document.getElementById('screensaver');
        // Use the wallpaper if the file exists; otherwise keep the CSS gradient backdrop.
        var probe = new Image();
        probe.onload = function () { document.getElementById('backdrop').style.backgroundImage = 'url(' + WALLPAPER + ')'; };
        probe.src = WALLPAPER;
    }

    function start() {
        if (!PHOTOS.length || timer) return;
        show(idx);
        timer = setInterval(function () { idx = (idx + 1) % PHOTOS.length; show(idx); }, 9000);
    }

    function stop() {
        if (timer) { clearInterval(timer); timer = null; }
        if (layer) layer.style.opacity = 0;
    }

    function show(i) {
        if (!layer) return;
        layer.style.backgroundImage = 'url(' + PHOTOS[i] + ')';
        layer.style.opacity = 1;
    }

    return { init: init, start: start, stop: stop, count: function () { return PHOTOS.length; } };
})();

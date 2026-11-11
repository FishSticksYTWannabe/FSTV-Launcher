/* apps.js - enumerate and launch installed Tizen apps, with a browser/emulator fallback. */
var FSTVApps = (function () {
    // Ids to never show (system/launcher junk). Fill this in on-device once you see the real ids.
    var HIDDEN = {};

    function hasTizen() {
        return (typeof tizen !== 'undefined') && tizen.application && tizen.application.getAppsInfo;
    }

    function load(onReady) {
        if (!hasTizen()) { onReady(mockApps()); return; }
        try {
            tizen.application.getAppsInfo(function (list) {
                var out = [];
                for (var i = 0; i < list.length; i++) {
                    var a = list[i];
                    if (!a || HIDDEN[a.id]) continue;
                    if (a.show === false) continue;            // skip entries the OS marks hidden
                    out.push({ id: a.id, name: a.name || a.id, icon: a.iconPath || '' });
                }
                out.sort(function (x, y) {
                    return (x.name || '').toLowerCase() < (y.name || '').toLowerCase() ? -1 : 1;
                });
                onReady(out);
            }, function (e) { console.error('getAppsInfo failed', e); onReady(mockApps()); });
        } catch (e) { console.error(e); onReady(mockApps()); }
    }

    function launch(id) {
        if (!hasTizen()) { console.log('launch (mock):', id); return; }
        try {
            tizen.application.launch(id, function () {}, function (e) { console.error('launch failed', id, e); });
        } catch (e) { console.error(e); }
    }

    // Shown only in a browser/emulator (no tizen object) so the UI is testable without real apps.
    function mockApps() {
        var names = ['Netflix', 'Prime Video', 'Disney+', 'YouTube', 'Apple TV', 'Spotify',
            'Max', 'Hulu', 'Plex', 'Twitch', 'Steam Link', 'Settings'];
        return names.map(function (n, i) { return { id: 'mock.' + i, name: n, icon: '' }; });
    }

    return { load: load, launch: launch, hasTizen: hasTizen };
})();

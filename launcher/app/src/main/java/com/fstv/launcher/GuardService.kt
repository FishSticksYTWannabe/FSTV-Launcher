package com.fstv.launcher

import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * Keeps FSTV as the home screen with no help from a PC. It runs as a foreground service, so the
 * system keeps this process (and the launcher) alive. Every [POLL_MS] it checks the foreground app;
 * if Fire OS has put Amazon's launcher up (e.g. a HOME press), it brings FSTV straight back. Because
 * the launcher stays resident, returning Home is instant instead of a cold start.
 *
 * Needs the GET_USAGE_STATS app-op, which setup grants once over ADB (it persists across reboots).
 */
class GuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var usage: UsageStatsManager? = null

    private var confirmedHome = false
    private var lastProactive = 0L

    /** Wall-clock time of the last MOVE_TO_FOREGROUND event we saw for Amazon's launcher. */
    private var amazonFreshAt = 0L

    private val poll = object : Runnable {
        override fun run() {
            val fg = foreground()
            if (fg == packageName) {
                confirmedHome = true
                // The user is back on FSTV (probably via HOME): drop any lingering Fire TV Home
                // "visit" so the next accidental trip to Amazon's launcher is bounced instantly.
                suppressUntil = 0L
                amazonFreshAt = 0L
            }
            // During the first stretch after a cold boot, usage access can still be unreadable
            // (fg == null) while Amazon's launcher is already up. Pull FSTV forward in that window
            // too - throttled, and only until we've seen FSTV foreground once - so the takeover is
            // quick instead of waiting on Fire OS's late BOOT_COMPLETED. After the boot window we go
            // strict: only bounce when Amazon's launcher is actually in front, so the guard never
            // yanks the user out of an app they opened.
            val now = SystemClock.uptimeMillis()
            val booting = SystemClock.elapsedRealtime() < BOOT_GRACE_MS && !confirmedHome
            val amazonUp = fg == AppRepository.STOCK_LAUNCHER
            val proactive = booting && (amazonUp || fg == null) && now - lastProactive >= PROACTIVE_MS

            // The Fire TV Home tile parks us on Amazon's launcher via suppressUntil. Cut the visit
            // short once the user has been inactive for IDLE_TIMEOUT_MS: no new fg event for the
            // launcher's package (tab switches and sub-activity navigation fire them; just staring
            // at a banner does not). If they are still interacting, the timer keeps resetting.
            if (amazonUp) {
                val wallNow = System.currentTimeMillis()
                if (amazonFreshAt == 0L) amazonFreshAt = wallNow
                if (now < suppressUntil && wallNow - amazonFreshAt >= IDLE_TIMEOUT_MS) {
                    suppressUntil = 0L
                    amazonFreshAt = 0L
                }
            } else {
                amazonFreshAt = 0L
            }

            if (now >= suppressUntil && (amazonUp || proactive)) {
                lastProactive = now
                startActivity(
                    Intent(this@GuardService, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        startForeground(NOTE_ID, note())
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** The package most recently moved to the foreground, or null if usage access is missing. */
    private fun foreground(): String? {
        val u = usage ?: return null
        val now = System.currentTimeMillis()
        val events = u.queryEvents(now - WINDOW_MS, now)
        val event = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                pkg = event.packageName
                if (event.packageName == AppRepository.STOCK_LAUNCHER &&
                    event.timeStamp > amazonFreshAt
                ) {
                    amazonFreshAt = event.timeStamp
                }
            }
        }
        return pkg
    }

    @Suppress("DEPRECATION")
    private fun note(): Notification = Notification.Builder(this)
        .setContentTitle("FSTV Launcher")
        .setContentText("Keeping this your home screen")
        .setSmallIcon(R.drawable.ic_home)
        .setPriority(Notification.PRIORITY_MIN)
        .setOngoing(true)
        .build()

    companion object {
        /**
         * Until this uptime (ms), the guard leaves Amazon's launcher alone. Set when the user
         * taps the "Fire TV Home" tile. The idle check (see [IDLE_TIMEOUT_MS]) usually cuts the
         * visit short well before this expires, and HOME resets it instantly.
         */
        @Volatile
        var suppressUntil = 0L
        private const val POLL_MS = 250L
        private const val WINDOW_MS = 10_000L
        private const val NOTE_ID = 42

        /** How long after a cold boot the guard may pull FSTV forward before usage access is ready. */
        private const val BOOT_GRACE_MS = 90_000L

        /** Minimum gap between proactive (boot-window) launch attempts, to avoid spamming. */
        private const val PROACTIVE_MS = 1_200L

        /** During a Fire TV Home visit, bounce back after this long with no fg event on Amazon's launcher. */
        private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L

        fun start(context: Context) {
            try {
                context.startService(Intent(context, GuardService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}

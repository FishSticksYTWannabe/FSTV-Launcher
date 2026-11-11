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

    private val poll = object : Runnable {
        override fun run() {
            if (SystemClock.uptimeMillis() >= suppressUntil && foreground() == AppRepository.STOCK_LAUNCHER) {
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
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) pkg = event.packageName
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
        /** While uptime < this, the guard leaves Amazon's launcher alone (the Fire TV Home escape). */
        @Volatile
        var suppressUntil = 0L
        private const val POLL_MS = 250L
        private const val WINDOW_MS = 10_000L
        private const val NOTE_ID = 42

        fun start(context: Context) {
            try {
                context.startService(Intent(context, GuardService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}

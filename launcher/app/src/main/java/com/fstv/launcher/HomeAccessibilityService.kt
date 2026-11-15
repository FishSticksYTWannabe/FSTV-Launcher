package com.fstv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Catches key presses before Fire OS's own overlays do. Needed because on Fire OS 6 the stock
 * system traps BACK on the launcher to show Amazon's "quick menu" shortcut strip, and routes HOME
 * to Amazon's launcher before any third-party home app can see it. Accessibility services run at a
 * higher priority than the standard dispatch, so this gets the first crack.
 *
 * Enabled at install time by setup.sh/setup.ps1 via
 *   settings put secure enabled_accessibility_services com.fstv.launcher/.HomeAccessibilityService
 *   settings put secure accessibility_enabled 1
 * so the user never has to visit the Accessibility settings screen.
 *
 * Note: this service does not expose assistive tech. It is only installed to recover key
 * handling that Fire OS steals from the home-app contract. The description shown in the
 * accessibility setting reflects that.
 */
class HomeAccessibilityService : AccessibilityService() {

    /** Tracks which package most recently brought a window to front. */
    @Volatile
    private var activePackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activePackage = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only use these events to track the foreground package, so onKeyEvent can decide
        // whether to intercept.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { activePackage = it.toString() }
        }
    }

    override fun onInterrupt() {
        // No continuous feedback to interrupt.
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Only act on the down edge, but consume both edges consistently so the system doesn't
        // see a dangling press.
        val down = event.action == KeyEvent.ACTION_DOWN
        return when (event.keyCode) {
            KeyEvent.KEYCODE_HOME -> {
                // On most Fire OS builds the system reserves HOME for its own dispatcher and
                // accessibility services never see it; on the builds that do route it here,
                // bringing HomeActivity forward with the go-to-start extra beats the usual
                // GuardService bounce by one full poll cycle.
                if (down) bringHome(resetFocus = true)
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                val pkg = activePackage
                when {
                    pkg == packageName -> {
                        // Intercept Back while FSTV is foreground so Amazon's quick-menu
                        // overlay doesn't inherit the press. Hand it to HomeActivity through a
                        // broadcast so its existing Back behavior (exit edit, scroll to top,
                        // bump) still runs.
                        if (down) sendBroadcast(
                            Intent(HomeActivity.ACTION_FSTV_BACK).setPackage(packageName),
                        )
                        true
                    }
                    pkg == AppRepository.STOCK_LAUNCHER -> {
                        // Fire OS's launcher is briefly foreground (e.g. because HOME was
                        // pressed). Treat Back there as "go to FSTV" so the user never lands in
                        // Amazon's quick menu.
                        if (down) bringHome(resetFocus = false)
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun bringHome(resetFocus: Boolean) {
        val intent = Intent(this, HomeActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )
        if (resetFocus) intent.putExtra(HomeActivity.EXTRA_GO_TO_START, true)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // The GuardService poll is still the ultimate safety net.
        }
    }
}

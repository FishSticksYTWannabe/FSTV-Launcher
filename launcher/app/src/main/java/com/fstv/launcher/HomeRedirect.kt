package com.fstv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings FSTV up as soon as the TV boots. Fire OS hands third-party apps the normal
 * BOOT_COMPLETED late (tens of seconds), so we also listen for LOCKED_BOOT_COMPLETED - the
 * direct-boot signal that arrives much earlier - and the OEM "quick boot" actions. Whichever
 * fires first, we start the guard; the guard then pulls FSTV to the front during the boot window.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            -> {
                // Start the guard first; it keeps FSTV home with no PC involvement.
                GuardService.start(context)
                // Best effort - during direct boot the system may defer this until unlock,
                // which on a TV (no PIN) is immediate. The guard covers that case regardless.
                try {
                    context.startActivity(
                        Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: Exception) {
                }
            }
        }
    }
}

package com.fstv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Opens this launcher when the TV boots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Start the guard first, so FSTV stays the home screen with no PC involvement after a reboot.
        GuardService.start(context)
        context.startActivity(
            Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

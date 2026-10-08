package com.rambo.carvoice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {

        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val launchIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            action = "com.rambo.carvoice.BOOT"

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        try {
            context.startActivity(launchIntent)
        } catch (_: Exception) {
            // Some OEM Android builds may restrict boot activity launch.
        }
    }
}

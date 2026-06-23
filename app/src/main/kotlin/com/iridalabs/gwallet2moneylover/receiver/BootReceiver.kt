package com.iridalabs.gwallet2moneylover.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Placeholder boot receiver. The NotificationListenerService is re-bound
 * automatically by Android after reboot as long as the user has granted
 * the notification access permission.  We keep this receiver for any
 * future initialisation work (e.g. re-creating notification channels).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed – channels will be recreated on next notification")
            com.iridalabs.gwallet2moneylover.notification.NotificationHelper
                .createChannels(context)
        }
    }
}

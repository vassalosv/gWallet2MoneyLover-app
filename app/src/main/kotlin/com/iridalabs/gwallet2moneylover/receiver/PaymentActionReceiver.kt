package com.iridalabs.gwallet2moneylover.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.iridalabs.gwallet2moneylover.notification.NotificationHelper
import com.iridalabs.gwallet2moneylover.ui.ConfirmationActivity

/**
 * Receives taps on the Yes / No buttons in the heads-up notification.
 */
class PaymentActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PaymentActionReceiver"
        const val ACTION_CONFIRMED = "com.iridalabs.gwallet2moneylover.ACTION_CONFIRMED"
        const val ACTION_DISMISSED = "com.iridalabs.gwallet2moneylover.ACTION_DISMISSED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")
        NotificationHelper.cancelPaymentNotification(context)

        when (intent.action) {
            ACTION_CONFIRMED -> {
                val launchIntent = Intent(context, ConfirmationActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtras(intent.extras ?: android.os.Bundle())
                }
                context.startActivity(launchIntent)
            }
            ACTION_DISMISSED -> {
                Log.d(TAG, "User dismissed the payment prompt")
                // Nothing else to do — PendingTransactionStore was already saved
                // and will be overwritten by the next payment
            }
        }
    }
}

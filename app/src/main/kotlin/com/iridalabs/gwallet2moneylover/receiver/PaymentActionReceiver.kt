package com.iridalabs.gwallet2moneylover.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.iridalabs.gwallet2moneylover.automation.MoneyLoverFlow
import com.iridalabs.gwallet2moneylover.notification.NotificationHelper

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
                val payment = NotificationHelper.paymentFromExtras(intent.extras)
                if (payment == null) {
                    Log.w(TAG, "Confirmed action without payment extras")
                } else {
                    MoneyLoverFlow.launch(context, payment)
                }
            }
            ACTION_DISMISSED -> {
                Log.d(TAG, "User dismissed the payment prompt")
                // Nothing else to do — PendingTransactionStore was already saved
                // and will be overwritten by the next payment
            }
        }
    }
}

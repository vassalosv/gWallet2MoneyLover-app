package com.iridalabs.gwallet2moneylover.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.iridalabs.gwallet2moneylover.R
import com.iridalabs.gwallet2moneylover.data.PaymentInfo
import com.iridalabs.gwallet2moneylover.receiver.PaymentActionReceiver

object NotificationHelper {

    const val CHANNEL_PAYMENT    = "payment_detected"
    const val CHANNEL_OVERLAY    = "overlay_service"
    const val NOTIF_ID_PAYMENT   = 1001
    const val NOTIF_ID_OVERLAY   = 1002

    const val EXTRA_PAYMENT_AMOUNT   = "amount"
    const val EXTRA_PAYMENT_CURRENCY = "currency"
    const val EXTRA_PAYMENT_SYMBOL   = "symbol"
    const val EXTRA_PAYMENT_MERCHANT = "merchant"
    const val EXTRA_PAYMENT_TIME     = "timestamp"
    const val EXTRA_PAYMENT_RAW      = "raw_text"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PAYMENT,
                context.getString(R.string.channel_payment_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_payment_desc)
                enableVibration(true)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY,
                context.getString(R.string.channel_overlay_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_overlay_desc)
            }
        )
    }

    /**
     * Build and post a heads-up notification with [Yes] and [No] action buttons.
     */
    fun postPaymentNotification(context: Context, payment: PaymentInfo) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val yesIntent = Intent(context, PaymentActionReceiver::class.java).apply {
            action = PaymentActionReceiver.ACTION_CONFIRMED
            putExtras(paymentExtras(payment))
        }
        val noIntent = Intent(context, PaymentActionReceiver::class.java).apply {
            action = PaymentActionReceiver.ACTION_DISMISSED
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val notification = NotificationCompat.Builder(context, CHANNEL_PAYMENT)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(
                context.getString(
                    R.string.notif_text,
                    payment.formattedAmount,
                    payment.merchantName
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        context.getString(
                            R.string.notif_big_text,
                            payment.formattedAmount,
                            payment.merchantName,
                            payment.formattedDate
                        )
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_EVENT)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.action_yes),
                PendingIntent.getBroadcast(context, 0, yesIntent, flags)
            )
            .addAction(
                R.drawable.ic_close,
                context.getString(R.string.action_no),
                PendingIntent.getBroadcast(context, 1, noIntent, flags)
            )
            .build()

        nm.notify(NOTIF_ID_PAYMENT, notification)
    }

    fun cancelPaymentNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIF_ID_PAYMENT)
    }

    /** Build a minimal persistent notification for OverlayService foreground requirement. */
    fun buildOverlayForegroundNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(context.getString(R.string.overlay_running))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    fun paymentExtras(p: PaymentInfo) = android.os.Bundle().apply {
        putDouble(EXTRA_PAYMENT_AMOUNT, p.amount)
        putString(EXTRA_PAYMENT_CURRENCY, p.currency)
        putString(EXTRA_PAYMENT_SYMBOL, p.currencySymbol)
        putString(EXTRA_PAYMENT_MERCHANT, p.merchantName)
        putLong(EXTRA_PAYMENT_TIME, p.timestamp)
        putString(EXTRA_PAYMENT_RAW, p.rawText)
    }

    fun paymentFromExtras(extras: android.os.Bundle?): PaymentInfo? {
        extras ?: return null
        val amount = extras.getDouble(EXTRA_PAYMENT_AMOUNT, -1.0)
        if (amount <= 0) return null
        return PaymentInfo(
            amount         = amount,
            currency       = extras.getString(EXTRA_PAYMENT_CURRENCY, "EUR") ?: "EUR",
            currencySymbol = extras.getString(EXTRA_PAYMENT_SYMBOL, "€") ?: "€",
            merchantName   = extras.getString(EXTRA_PAYMENT_MERCHANT, "") ?: "",
            timestamp      = extras.getLong(EXTRA_PAYMENT_TIME, System.currentTimeMillis()),
            rawText        = extras.getString(EXTRA_PAYMENT_RAW, "") ?: ""
        )
    }
}

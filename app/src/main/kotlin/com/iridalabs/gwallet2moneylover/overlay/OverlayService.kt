package com.iridalabs.gwallet2moneylover.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.iridalabs.gwallet2moneylover.R
import com.iridalabs.gwallet2moneylover.automation.MoneyLoverFlow
import com.iridalabs.gwallet2moneylover.data.AppSettings
import com.iridalabs.gwallet2moneylover.data.PaymentInfo
import com.iridalabs.gwallet2moneylover.notification.NotificationHelper
import com.iridalabs.gwallet2moneylover.receiver.PaymentActionReceiver

/**
 * Regular (non-foreground) service that shows a floating overlay card.
 * Started from a foreground context (MainActivity test button) or from
 * WalletNotificationListener (which is whitelisted for background starts).
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val payment = NotificationHelper.paymentFromExtras(intent?.extras)
        if (payment == null) {
            Log.w(TAG, "No payment in intent, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission, falling back to notification")
            if (AppSettings.isNotificationEnabled(this)) {
                safePostNotification(payment)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            showOverlay(payment)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}", e)
            safePostNotification(payment)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun showOverlay(payment: PaymentInfo) {
        dismissOverlay()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_payment, null)

        view.findViewById<TextView>(R.id.tvOverlayTitle).text = getString(R.string.overlay_title)
        view.findViewById<TextView>(R.id.tvOverlayAmount).text =
            getString(R.string.overlay_amount, payment.formattedAmount, payment.merchantName)
        view.findViewById<TextView>(R.id.tvOverlayDate).text =
            getString(R.string.overlay_date, payment.formattedDate)

        view.findViewById<Button>(R.id.btnOverlayYes).setOnClickListener {
            onUserConfirmed(payment)
        }
        view.findViewById<Button>(R.id.btnOverlayNo).setOnClickListener {
            onUserDismissed()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager!!.addView(view, params)
        overlayView = view
        Log.d(TAG, "Overlay shown for ${payment.formattedAmount}")
    }

    private fun onUserConfirmed(payment: PaymentInfo) {
        dismissOverlay()
        MoneyLoverFlow.launch(this, payment)
        stopSelf()
    }

    private fun onUserDismissed() {
        dismissOverlay()
        sendBroadcast(Intent(this, PaymentActionReceiver::class.java).apply {
            action = PaymentActionReceiver.ACTION_DISMISSED
        })
        stopSelf()
    }

    private fun dismissOverlay() {
        overlayView?.let { v ->
            try { windowManager?.removeView(v) } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun safePostNotification(payment: PaymentInfo) {
        try {
            NotificationHelper.postPaymentNotification(this, payment)
        } catch (e: Exception) {
            Log.e(TAG, "Could not post notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        dismissOverlay()
        super.onDestroy()
    }
}

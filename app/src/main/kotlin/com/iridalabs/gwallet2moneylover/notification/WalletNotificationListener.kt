package com.iridalabs.gwallet2moneylover.notification

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.iridalabs.gwallet2moneylover.data.AppSettings
import com.iridalabs.gwallet2moneylover.data.PaymentInfo
import com.iridalabs.gwallet2moneylover.data.PendingTransactionStore
import com.iridalabs.gwallet2moneylover.overlay.OverlayService
import com.iridalabs.gwallet2moneylover.parser.PaymentParser
import java.lang.ref.WeakReference

/**
 * Listens for wallet notifications and filters for payment events.
 */
class WalletNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WalletListener"

        private val WALLET_PACKAGES = setOf(
            "com.google.android.apps.walletnfcrel",
            "com.google.android.gms",
            "com.google.android.apps.nfc.payment",
            "com.samsung.android.spay",
            "com.google.android.apps.wallet"
        )

        val debugLog = ArrayDeque<String>(20)

        @Volatile private var activeListener: WeakReference<WalletNotificationListener>? = null
        private val manualScanPromptedKeys = LinkedHashSet<String>()

        data class ActiveScanResult(
            val listenerConnected: Boolean,
            val walletNotificationCount: Int = 0,
            val parsedPaymentCount: Int = 0,
            val promptedPayment: PaymentInfo? = null,
            val error: String? = null
        )

        fun addDebugEntry(entry: String) {
            if (debugLog.size >= 20) debugLog.removeFirst()
            debugLog.addLast(entry)
        }

        fun scanActiveWalletNotifications(): ActiveScanResult {
            val listener = activeListener?.get()
            if (listener == null) {
                val error = "Notification listener is not connected"
                addDebugEntry("Manual scan failed: $error")
                return ActiveScanResult(listenerConnected = false, error = error)
            }

            return listener.scanActiveWalletNotificationsInternal()
        }

        private fun paymentKey(payment: PaymentInfo): String =
            "${payment.amount}|${payment.currency}|${payment.merchantName}|${payment.rawText}"

        private fun rememberManualScanPrompt(payment: PaymentInfo) {
            manualScanPromptedKeys.add(paymentKey(payment))
            while (manualScanPromptedKeys.size > 50) {
                val first = manualScanPromptedKeys.firstOrNull() ?: break
                manualScanPromptedKeys.remove(first)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeListener = WeakReference(this)
        NotificationHelper.createChannels(this)
        Log.i(TAG, "WalletNotificationListener started")
        addDebugEntry("Service started")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeListener = WeakReference(this)
        Log.i(TAG, "Notification listener connected")
        addDebugEntry("Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (activeListener?.get() === this) activeListener = null
        Log.w(TAG, "Notification listener disconnected")
        addDebugEntry("Notification listener disconnected")
    }

    override fun onDestroy() {
        if (activeListener?.get() === this) activeListener = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in WALLET_PACKAGES) return

        val fields = notificationFields(sbn)
        val entry = "pkg=$pkg | title=\"${fields.title}\" | text=\"${fields.text}\" | sub=\"${fields.subText}\""
        Log.d(TAG, "Wallet notif: $entry")
        addDebugEntry(entry)

        tryParse(fields.title, fields.text, fields.subText, sbn.postTime)
    }

    private fun scanActiveWalletNotificationsInternal(): ActiveScanResult {
        val active = try {
            activeNotifications?.toList().orEmpty()
        } catch (e: SecurityException) {
            Log.e(TAG, "Manual scan failed: ${e.message}", e)
            addDebugEntry("Manual scan failed: ${e.message}")
            return ActiveScanResult(
                listenerConnected = true,
                error = e.message ?: "Could not read active notifications"
            )
        }

        val walletNotifications = active.filter { it.packageName in WALLET_PACKAGES }
        addDebugEntry("Manual scan: ${walletNotifications.size} active Wallet notification(s)")

        val parsed = walletNotifications.mapNotNull { sbn ->
            val fields = notificationFields(sbn)
            val entry = "active pkg=${sbn.packageName} | title=\"${fields.title}\" | text=\"${fields.text}\" | sub=\"${fields.subText}\""
            Log.d(TAG, "Manual scan wallet notif: $entry")
            addDebugEntry(entry)
            parsePayment(fields.title, fields.text, fields.subText, sbn.postTime)
        }.distinctBy { payment -> paymentKey(payment) }

        val unprompted = parsed.filter { payment -> paymentKey(payment) !in manualScanPromptedKeys }
        val paymentToPrompt = unprompted.maxByOrNull { it.timestamp }
        if (paymentToPrompt != null) {
            rememberManualScanPrompt(paymentToPrompt)
            addDebugEntry(
                "Manual scan parsed ${parsed.size}; prompting newest: " +
                    "${paymentToPrompt.formattedAmount} @ ${paymentToPrompt.merchantName}"
            )
            handleDetectedPayment(paymentToPrompt)
        } else if (parsed.isNotEmpty()) {
            addDebugEntry("Manual scan parsed ${parsed.size}; all were already prompted this session")
        } else {
            addDebugEntry("Manual scan parsed 0 payments")
        }

        return ActiveScanResult(
            listenerConnected = true,
            walletNotificationCount = walletNotifications.size,
            parsedPaymentCount = parsed.size,
            promptedPayment = paymentToPrompt
        )
    }

    private data class NotificationFields(
        val title: String,
        val text: String,
        val subText: String
    )

    private fun notificationFields(sbn: StatusBarNotification): NotificationFields {
        val extras = sbn.notification.extras
        return NotificationFields(
            title = extras.getString(Notification.EXTRA_TITLE).orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        )
    }

    private fun tryParse(title: String, text: String, subText: String, postTime: Long) {
        val payment = parsePayment(title, text, subText, postTime)
        if (payment != null) {
            Log.i(TAG, "Payment parsed: ${payment.formattedAmount} @ ${payment.merchantName}")
            addDebugEntry("Parsed: ${payment.formattedAmount} @ ${payment.merchantName}")
            handleDetectedPayment(payment)
        } else {
            Log.w(TAG, "No payment parsed from wallet notification")
            addDebugEntry("Could not parse payment - check format above")
        }
    }

    private fun parsePayment(title: String, text: String, subText: String, postTime: Long): PaymentInfo? {
        val candidates = listOf(
            title to text,
            title to subText,
            text to subText,
            "$title $text" to "",
            "$title $subText" to "",
            "$text $subText" to ""
        )

        for ((candidateTitle, candidateText) in candidates) {
            val payment = PaymentParser.parse(candidateTitle, candidateText, postTime)
            if (payment != null) return payment
        }

        return null
    }

    private fun handleDetectedPayment(payment: PaymentInfo) {
        PendingTransactionStore.save(this, payment)

        val overlayEnabled = AppSettings.isOverlayEnabled(this)
        val notificationEnabled = AppSettings.isNotificationEnabled(this)

        when {
            overlayEnabled && canDrawOverlay() -> showOverlay(payment)
            notificationEnabled -> NotificationHelper.postPaymentNotification(this, payment)
            else -> Log.w(TAG, "Both overlay and notification disabled; payment stored silently")
        }
    }

    private fun canDrawOverlay(): Boolean =
        android.provider.Settings.canDrawOverlays(this)

    private fun showOverlay(payment: PaymentInfo) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtras(NotificationHelper.paymentExtras(payment))
        }
        startService(intent)
    }
}

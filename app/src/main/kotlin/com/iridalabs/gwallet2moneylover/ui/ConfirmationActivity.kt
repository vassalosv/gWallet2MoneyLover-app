package com.iridalabs.gwallet2moneylover.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.iridalabs.gwallet2moneylover.R
import com.iridalabs.gwallet2moneylover.accessibility.MoneyLoverAccessibilityService
import com.iridalabs.gwallet2moneylover.data.AppSettings
import com.iridalabs.gwallet2moneylover.data.PaymentInfo
import com.iridalabs.gwallet2moneylover.data.PendingTransactionStore
import com.iridalabs.gwallet2moneylover.notification.NotificationHelper

/**
 * Translucent activity that summarises the detected payment and lets the user
 * choose to send it to Money Lover or cancel.
 *
 * This is launched both from the overlay Yes button and from notification actions.
 */
class ConfirmationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirmation)

        // Payment info comes either from intent extras (overlay/notification path)
        // or from the persistent store (rare re-launch path)
        val payment = NotificationHelper.paymentFromExtras(intent.extras)
            ?: PendingTransactionStore.load(this)

        if (payment == null) {
            Toast.makeText(this, getString(R.string.error_no_payment), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Ensure it is saved in the store so AccessibilityService can read it
        PendingTransactionStore.save(this, payment)
        bindViews(payment)
    }

    private fun bindViews(payment: PaymentInfo) {
        findViewById<TextView>(R.id.tvConfirmAmount).text =
            getString(R.string.confirm_amount, payment.formattedAmount)
        findViewById<TextView>(R.id.tvConfirmMerchant).text =
            getString(R.string.confirm_merchant, payment.merchantName)
        findViewById<TextView>(R.id.tvConfirmDate).text =
            getString(R.string.confirm_date, payment.formattedDate)

        findViewById<Button>(R.id.btnOpenMoneyLover).setOnClickListener {
            openMoneyLover(payment)
        }
        findViewById<Button>(R.id.btnConfirmCancel).setOnClickListener {
            PendingTransactionStore.clear(this)
            finish()
        }
    }

    private fun openMoneyLover(payment: PaymentInfo) {
        val mlPackage = AppSettings.getMoneyLoverPackage(this)

        // Check Money Lover is installed
        val installed = try {
            packageManager.getPackageInfo(mlPackage, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        if (!installed) {
            Toast.makeText(
                this,
                getString(R.string.error_ml_not_installed, mlPackage),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Check accessibility service is running
        if (!MoneyLoverAccessibilityService.isRunning) {
            Toast.makeText(this, getString(R.string.error_accessibility_off), Toast.LENGTH_LONG).show()
            // Open accessibility settings so the user can enable the service
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        // Signal the accessibility service: payment is ready, automate on next ML window
        MoneyLoverAccessibilityService.triggerAutomation(payment)

        // Launch Money Lover
        val launchIntent = packageManager.getLaunchIntentForPackage(mlPackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        }

        finish()
    }
}

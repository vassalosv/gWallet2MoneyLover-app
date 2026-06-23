package com.iridalabs.gwallet2moneylover.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import com.iridalabs.gwallet2moneylover.R
import com.iridalabs.gwallet2moneylover.accessibility.MoneyLoverAccessibilityService
import com.iridalabs.gwallet2moneylover.data.AppSettings
import com.iridalabs.gwallet2moneylover.data.PaymentInfo
import com.iridalabs.gwallet2moneylover.data.PendingTransactionStore

object MoneyLoverFlow {

    fun launch(context: Context, payment: PaymentInfo): Boolean {
        val appContext = context.applicationContext
        val mlPackage = AppSettings.getMoneyLoverPackage(appContext)

        PendingTransactionStore.save(appContext, payment)

        val installed = try {
            appContext.packageManager.getPackageInfo(mlPackage, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        if (!installed) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.error_ml_not_installed, mlPackage),
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        if (!MoneyLoverAccessibilityService.isRunning) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.error_accessibility_off),
                Toast.LENGTH_LONG
            ).show()
            appContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return false
        }

        MoneyLoverAccessibilityService.triggerAutomation(payment)

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(mlPackage)
        if (launchIntent == null) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.error_ml_not_installed, mlPackage),
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        appContext.startActivity(launchIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }
}

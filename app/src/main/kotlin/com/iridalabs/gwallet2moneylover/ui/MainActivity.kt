package com.iridalabs.gwallet2moneylover.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.iridalabs.gwallet2moneylover.R
import com.iridalabs.gwallet2moneylover.accessibility.MoneyLoverAccessibilityService
import com.iridalabs.gwallet2moneylover.data.AppSettings
import com.iridalabs.gwallet2moneylover.data.PaymentInfo
import com.iridalabs.gwallet2moneylover.notification.NotificationHelper
import com.iridalabs.gwallet2moneylover.notification.WalletNotificationListener
import com.iridalabs.gwallet2moneylover.overlay.OverlayService

class MainActivity : AppCompatActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Notification permission denied — heads-up alerts won't work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationHelper.createChannels(this)
        setSupportActionBar(findViewById(R.id.toolbar))

        // Request POST_NOTIFICATIONS at runtime (required Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.tvVersion).text = "v$versionName"

        findViewById<Button>(R.id.btnTestPayment).setOnClickListener {
            simulateFakePayment()
        }
        findViewById<Button>(R.id.btnScanActiveNotifications).setOnClickListener {
            scanActiveWalletNotifications()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshDebugLog()
        requestBatteryOptimizationExemption()
    }

    // ── Status checks ─────────────────────────────────────────────────────────

    private fun refreshStatus() {
        val notifGranted   = isNotificationListenerEnabled()
        val accessGranted  = MoneyLoverAccessibilityService.isRunning
        val overlayGranted = Settings.canDrawOverlays(this)
        val batteryOk      = isBatteryOptimizationIgnored()

        statusText(R.id.tvStatusNotification, notifGranted)
        statusText(R.id.tvStatusAccessibility, accessGranted)
        statusText(R.id.tvStatusOverlay, overlayGranted)
        statusText(R.id.tvStatusBattery, batteryOk)

        findViewById<Button>(R.id.btnGrantNotification).apply {
            isEnabled = !notifGranted
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        findViewById<Button>(R.id.btnGrantAccessibility).apply {
            isEnabled = !accessGranted
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        findViewById<Button>(R.id.btnGrantOverlay).apply {
            isEnabled = !overlayGranted
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        findViewById<Button>(R.id.btnGrantBattery).apply {
            isEnabled = !batteryOk
            setOnClickListener { requestBatteryOptimizationExemption() }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        if (!isBatteryOptimizationIgnored()) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                // Some OEMs don't support this intent — open battery settings instead
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) { /* ignore */ }
            }
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val component = ComponentName(this, WalletNotificationListener::class.java)
        return flat.split(":").any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    private fun statusText(viewId: Int, ok: Boolean) {
        val tv = findViewById<TextView>(viewId)
        tv.text = if (ok) getString(R.string.status_ok) else getString(R.string.status_not_granted)
        tv.setTextColor(
            if (ok) getColor(R.color.status_ok) else getColor(R.color.status_error)
        )
    }

    // ── Debug log ─────────────────────────────────────────────────────────────

    private fun refreshDebugLog() {
        val tv = findViewById<TextView>(R.id.tvDebugLog)
        val sv = findViewById<ScrollView>(R.id.svDebugLog)
        if (WalletNotificationListener.debugLog.isEmpty()) {
            tv.text = "(no wallet notifications received yet — make a test payment)"
        } else {
            tv.text = WalletNotificationListener.debugLog.joinToString("\n\n")
        }
        sv.post { sv.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ── Test button ───────────────────────────────────────────────────────────

    private fun simulateFakePayment() {
        val fakePayment = PaymentInfo(
            amount         = 12.40,
            currency       = "EUR",
            currencySymbol = "€",
            merchantName   = "AB Vasilopoulos (TEST)",
            timestamp      = System.currentTimeMillis()
        )
        WalletNotificationListener.addDebugEntry("▶ Simulated test payment fired")

        try {
            if (Settings.canDrawOverlays(this) && AppSettings.isOverlayEnabled(this)) {
                val intent = Intent(this, OverlayService::class.java).apply {
                    putExtras(NotificationHelper.paymentExtras(fakePayment))
                }
                startService(intent)
                Toast.makeText(this, "Overlay triggered…", Toast.LENGTH_SHORT).show()
            } else {
                NotificationHelper.postPaymentNotification(this, fakePayment)
                Toast.makeText(this, "Notification sent (check notification shade)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Simulate failed: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            WalletNotificationListener.addDebugEntry("✗ Simulate error: ${e.message}")
        }
        refreshDebugLog()
    }

    // ── Options menu ──────────────────────────────────────────────────────────

    private fun scanActiveWalletNotifications() {
        val result = WalletNotificationListener.scanActiveWalletNotifications()
        val message = when {
            !result.listenerConnected ->
                "Notification listener is not connected. Toggle Notification Access off/on."
            result.error != null ->
                "Scan failed: ${result.error}"
            result.walletNotificationCount == 0 ->
                "No active Wallet notifications found."
            result.parsedPaymentCount == 0 ->
                "Scanned ${result.walletNotificationCount} Wallet notification(s), parsed none."
            result.promptedPayment != null ->
                "Parsed ${result.parsedPaymentCount}; prompting ${result.promptedPayment.formattedAmount}."
            result.parsedPaymentCount > 0 ->
                "All parsed active payments were already prompted this session."
            else ->
                "Scan complete."
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        refreshDebugLog()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                refreshStatus()
                refreshDebugLog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

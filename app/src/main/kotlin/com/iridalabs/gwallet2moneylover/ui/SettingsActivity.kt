package com.iridalabs.gwallet2moneylover.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.iridalabs.gwallet2moneylover.R
import com.iridalabs.gwallet2moneylover.data.AppSettings

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbarSettings))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bindSettings()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun bindSettings() {
        val overlaySwitch = findViewById<SwitchMaterial>(R.id.switchOverlay)
        val notificationSwitch = findViewById<SwitchMaterial>(R.id.switchNotification)
        val debugSwitch = findViewById<SwitchMaterial>(R.id.switchDebug)
        val packageInput = findViewById<EditText>(R.id.etMoneyLoverPackage)
        val packageSummary = findViewById<TextView>(R.id.tvMoneyLoverPackageSummary)

        overlaySwitch.isChecked = AppSettings.isOverlayEnabled(this)
        notificationSwitch.isChecked = AppSettings.isNotificationEnabled(this)
        debugSwitch.isChecked = AppSettings.isDebugMode(this)

        val currentPackage = AppSettings.getMoneyLoverPackage(this)
        packageInput.setText(currentPackage)
        packageSummary.text = currentPackage

        overlaySwitch.setOnCheckedChangeListener { _, checked ->
            AppSettings.setOverlayEnabled(this, checked)
        }
        notificationSwitch.setOnCheckedChangeListener { _, checked ->
            AppSettings.setNotificationEnabled(this, checked)
        }
        debugSwitch.setOnCheckedChangeListener { _, checked ->
            AppSettings.setDebugMode(this, checked)
        }

        findViewById<Button>(R.id.btnSaveMoneyLoverPackage).setOnClickListener {
            val value = packageInput.text.toString().trim()
                .ifEmpty { AppSettings.DEFAULT_ML_PACKAGE }
            AppSettings.setMoneyLoverPackage(this, value)
            packageInput.setText(value)
            packageSummary.text = value
            Toast.makeText(this, "Money Lover package saved", Toast.LENGTH_SHORT).show()
        }
    }
}

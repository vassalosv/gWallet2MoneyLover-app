package com.iridalabs.gwallet2moneylover.ui

import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.iridalabs.gwallet2moneylover.R
import com.iridalabs.gwallet2moneylover.data.AppSettings

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // We build preferences programmatically so there's no need for a separate XML
            val context = requireContext()
            val screen = preferenceManager.createPreferenceScreen(context)
            preferenceManager.sharedPreferencesName = "app_settings"

            // ── Confirmation UI ───────────────────────────────────────────────

            val overlayPref = SwitchPreferenceCompat(context).apply {
                key   = AppSettings.KEY_OVERLAY_ENABLED
                title = getString(R.string.pref_overlay_title)
                summary = getString(R.string.pref_overlay_summary)
                setDefaultValue(true)
            }
            screen.addPreference(overlayPref)

            val notifPref = SwitchPreferenceCompat(context).apply {
                key   = AppSettings.KEY_NOTIFICATION_ENABLED
                title = getString(R.string.pref_notification_title)
                summary = getString(R.string.pref_notification_summary)
                setDefaultValue(true)
            }
            screen.addPreference(notifPref)

            // ── Money Lover ───────────────────────────────────────────────────

            val mlPackagePref = EditTextPreference(context).apply {
                key   = AppSettings.KEY_MONEY_LOVER_PACKAGE
                title = getString(R.string.pref_ml_package_title)
                summary = AppSettings.getMoneyLoverPackage(context)
                setDefaultValue(AppSettings.DEFAULT_ML_PACKAGE)
                setOnPreferenceChangeListener { pref, value ->
                    pref.summary = value.toString()
                    true
                }
                setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_TEXT
                    editText.hint = AppSettings.DEFAULT_ML_PACKAGE
                }
            }
            screen.addPreference(mlPackagePref)

            // ── Debug ─────────────────────────────────────────────────────────

            val debugPref = SwitchPreferenceCompat(context).apply {
                key   = AppSettings.KEY_DEBUG_MODE
                title = getString(R.string.pref_debug_title)
                summary = getString(R.string.pref_debug_summary)
                setDefaultValue(false)
            }
            screen.addPreference(debugPref)

            preferenceScreen = screen
        }
    }
}

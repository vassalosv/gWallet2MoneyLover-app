package com.iridalabs.gwallet2moneylover.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Application settings persisted in SharedPreferences.
 */
object AppSettings {

    private const val PREFS = "app_settings"

    const val KEY_OVERLAY_ENABLED      = "overlay_enabled"
    const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
    const val KEY_MONEY_LOVER_PACKAGE  = "money_lover_package"
    const val KEY_DEBUG_MODE           = "debug_mode"

    const val DEFAULT_ML_PACKAGE = "com.bookmark.money"

    // ── Overlay ──────────────────────────────────────────────────────────────

    fun isOverlayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_ENABLED, true)

    fun setOverlayEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()

    // ── Notification ─────────────────────────────────────────────────────────

    fun isNotificationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATION_ENABLED, true)

    fun setNotificationEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply()

    // ── Money Lover package name ──────────────────────────────────────────────

    fun getMoneyLoverPackage(context: Context): String =
        prefs(context).getString(KEY_MONEY_LOVER_PACKAGE, DEFAULT_ML_PACKAGE) ?: DEFAULT_ML_PACKAGE

    fun setMoneyLoverPackage(context: Context, pkg: String) =
        prefs(context).edit().putString(KEY_MONEY_LOVER_PACKAGE, pkg).apply()

    // ── Debug mode ────────────────────────────────────────────────────────────

    fun isDebugMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEBUG_MODE, false)

    fun setDebugMode(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun getAll(context: Context): SharedPreferences = prefs(context)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

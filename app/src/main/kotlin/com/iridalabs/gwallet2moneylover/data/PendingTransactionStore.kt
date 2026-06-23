package com.iridalabs.gwallet2moneylover.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple SharedPreferences-backed store for a single pending payment that the
 * user has confirmed but Money Lover hasn't been filled yet.
 */
object PendingTransactionStore {

    private const val PREFS = "pending_transaction"
    private const val KEY_PENDING    = "has_pending"
    private const val KEY_AMOUNT     = "amount"
    private const val KEY_CURRENCY   = "currency"
    private const val KEY_SYMBOL     = "currency_symbol"
    private const val KEY_MERCHANT   = "merchant"
    private const val KEY_TIMESTAMP  = "timestamp"
    private const val KEY_RAW        = "raw_text"

    fun save(context: Context, payment: PaymentInfo) {
        prefs(context).edit().apply {
            putBoolean(KEY_PENDING, true)
            putFloat(KEY_AMOUNT, payment.amount.toFloat())
            putString(KEY_CURRENCY, payment.currency)
            putString(KEY_SYMBOL, payment.currencySymbol)
            putString(KEY_MERCHANT, payment.merchantName)
            putLong(KEY_TIMESTAMP, payment.timestamp)
            putString(KEY_RAW, payment.rawText)
        }.apply()
    }

    fun load(context: Context): PaymentInfo? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return null
        return PaymentInfo(
            amount         = p.getFloat(KEY_AMOUNT, 0f).toDouble(),
            currency       = p.getString(KEY_CURRENCY, "EUR") ?: "EUR",
            currencySymbol = p.getString(KEY_SYMBOL, "€") ?: "€",
            merchantName   = p.getString(KEY_MERCHANT, "") ?: "",
            timestamp      = p.getLong(KEY_TIMESTAMP, System.currentTimeMillis()),
            rawText        = p.getString(KEY_RAW, "") ?: ""
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().putBoolean(KEY_PENDING, false).apply()
    }

    fun hasPending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING, false)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

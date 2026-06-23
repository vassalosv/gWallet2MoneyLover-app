package com.iridalabs.gwallet2moneylover.data

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parsed payment info extracted from a Google Wallet notification.
 */
data class PaymentInfo(
    val amount: Double,
    val currency: String,          // ISO code, e.g. "EUR"
    val currencySymbol: String,    // display symbol, e.g. "€"
    val merchantName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val rawText: String = ""
) : Serializable {

    /** e.g. "€12.40" */
    val formattedAmount: String
        get() = "$currencySymbol${"%.2f".format(amount)}"

    /** e.g. "17/06/2026" */
    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .format(Date(timestamp))

    companion object {
        private const val serialVersionUID = 1L
    }
}

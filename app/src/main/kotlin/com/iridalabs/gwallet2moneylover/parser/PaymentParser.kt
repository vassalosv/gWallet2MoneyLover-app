package com.iridalabs.gwallet2moneylover.parser

import android.util.Log
import com.iridalabs.gwallet2moneylover.data.PaymentInfo

/**
 * Regex-based parser for Google Wallet notification text.
 *
 * Handles common notification formats, e.g.:
 *   "€12.40 at AB Vasilopoulos"
 *   "You paid €12.40 at AB Vasilopoulos"
 *   "Payment of €12.40 to Merchant"
 *   "€12.40 • Merchant"
 *   "12,40 € at Merchant"   (comma decimal, symbol after amount)
 */
object PaymentParser {

    private const val TAG = "PaymentParser"

    /** Map from symbol → ISO currency code */
    private val SYMBOL_TO_ISO = mapOf(
        "€"   to "EUR",
        "\$"  to "USD",
        "£"   to "GBP",
        "¥"   to "JPY",
        "₹"   to "INR",
        "₽"   to "RUB",
        "₩"   to "KRW",
        "฿"   to "THB",
        "₺"   to "TRY",
        "₴"   to "UAH",
        "zł"  to "PLN",
        "kr"  to "NOK",   // approximation
        "CHF" to "CHF"
    )

    /** Matches: €12.40  or  € 12.40  (symbol first, optional space) */
    private const val AMOUNT_SYMBOL_FIRST =
        """([€${'$'}£¥₹₽₩฿₺₴]|zł|kr|CHF)\s*([\d]{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?)"""

    /** Matches: 12.40€  or  12,40 €  (symbol last) */
    private const val AMOUNT_SYMBOL_LAST =
        """([\d]{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?)\s*([€${'$'}£¥₹₽₩฿₺₴]|zł|kr|CHF)"""

    private const val MERCHANT_CAPTURE =
        """(.+?)(?:\s*${'$'}|\s+on\s+\d|\s+–|\s+\|)"""

    // ── Individual patterns ──────────────────────────────────────────────────

    /** €12.40 at/to/@ Merchant */
    private val P_SYMBOL_FIRST_AT = Regex(
        "$AMOUNT_SYMBOL_FIRST\\s+(?:at|to|@)\\s+$MERCHANT_CAPTURE",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /** €12.40 • Merchant  or  €12.40 – Merchant  or  €12.40 - Merchant */
    private val P_SYMBOL_FIRST_BULLET = Regex(
        "$AMOUNT_SYMBOL_FIRST\\s+[•·–\\-]\\s+(.+)",
        RegexOption.MULTILINE
    )

    /** Paid/Payment of €12.40 at/to Merchant */
    private val P_PAID_AT = Regex(
        """(?:paid?|payment\s+of)\s+$AMOUNT_SYMBOL_FIRST\s+(?:at|to)\s+$MERCHANT_CAPTURE""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /** 12,40€ at/to Merchant */
    private val P_SYMBOL_LAST_AT = Regex(
        "$AMOUNT_SYMBOL_LAST\\s+(?:at|to|@)\\s+$MERCHANT_CAPTURE",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /**
     * Real Google Wallet format (observed on Android 16):
     *   title = "COSMOS SPORT"
     *   text  = "€29.32 with Credit Visa ••6158"
     *
     * Matches the amount at the start of text; merchant taken from title.
     */
    private val P_AMOUNT_WITH = Regex(
        "$AMOUNT_SYMBOL_FIRST\\s+with\\b",
        setOf(RegexOption.IGNORE_CASE)
    )

    /** Same but symbol after amount: "29.32€ with ..." */
    private val P_AMOUNT_WITH_SYMBOL_LAST = Regex(
        "$AMOUNT_SYMBOL_LAST\\s+with\\b",
        setOf(RegexOption.IGNORE_CASE)
    )

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Attempt to parse a payment from notification title + text.
     * Returns null if no payment information can be extracted.
     */
    fun parse(
        title: String,
        text: String,
        timestamp: Long = System.currentTimeMillis()
    ): PaymentInfo? {
        val combined = buildString {
            if (title.isNotBlank()) { append(title); append(' ') }
            if (text.isNotBlank())  append(text)
        }.trim()

        Log.d(TAG, "Parsing: title=\"$title\" text=\"$text\"")

        // ── Priority 1: real Google Wallet format ────────────────────────────
        // title = merchant name, text = "€29.32 with Credit Visa ••XXXX"
        if (text.isNotBlank() && title.isNotBlank()) {
            P_AMOUNT_WITH.find(text)?.let { m ->
                val (symbol, amountRaw) = m.destructured
                return build(symbol, amountRaw, title.trim(), timestamp, text)
                    ?.also { Log.d(TAG, "Match P_AMOUNT_WITH (title=merchant): $it") }
            }
            P_AMOUNT_WITH_SYMBOL_LAST.find(text)?.let { m ->
                val (amountRaw, symbol) = m.destructured
                return build(symbol, amountRaw, title.trim(), timestamp, text)
                    ?.also { Log.d(TAG, "Match P_AMOUNT_WITH_SYMBOL_LAST (title=merchant): $it") }
            }
        }

        // ── Priority 2: classic combined-string patterns ─────────────────────
        for (pattern in listOf(P_PAID_AT, P_SYMBOL_FIRST_AT, P_SYMBOL_FIRST_BULLET)) {
            val m = pattern.find(combined) ?: continue
            val (symbol, amountRaw, merchant) = m.destructured
            return build(symbol, amountRaw, merchant, timestamp, combined)
                ?.also { Log.d(TAG, "Match: $it") }
        }

        // P_SYMBOL_LAST_AT → groups: amount, symbol, merchant
        P_SYMBOL_LAST_AT.find(combined)?.let { m ->
            val (amountRaw, symbol, merchant) = m.destructured
            return build(symbol, amountRaw, merchant, timestamp, combined)
                ?.also { Log.d(TAG, "Match (symbol last): $it") }
        }

        Log.d(TAG, "No match for combined: \"$combined\"")
        return null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun build(
        symbol: String,
        amountRaw: String,
        merchant: String,
        timestamp: Long,
        raw: String
    ): PaymentInfo? {
        val amount = normalizeAmount(amountRaw) ?: return null
        val currency = SYMBOL_TO_ISO[symbol] ?: symbol.uppercase()
        return PaymentInfo(
            amount         = amount,
            currency       = currency,
            currencySymbol = symbol,
            merchantName   = merchant.trim(),
            timestamp      = timestamp,
            rawText        = raw
        )
    }

    /**
     * Normalise "1.234,56" and "1,234.56" and "12.40" and "12,40" → Double.
     */
    private fun normalizeAmount(raw: String): Double? {
        val s = raw.trim()
        return when {
            // "1.234,56" European thousands separator
            s.contains('.') && s.contains(',') && s.indexOf('.') < s.indexOf(',') ->
                s.replace(".", "").replace(',', '.').toDoubleOrNull()
            // "1,234.56" US thousands separator
            s.contains(',') && s.contains('.') && s.indexOf(',') < s.indexOf('.') ->
                s.replace(",", "").toDoubleOrNull()
            // Only comma → decimal separator: "12,40"
            s.contains(',') && !s.contains('.') ->
                s.replace(',', '.').toDoubleOrNull()
            // Only period or nothing special
            else ->
                s.replace(",", "").toDoubleOrNull()
        }
    }
}

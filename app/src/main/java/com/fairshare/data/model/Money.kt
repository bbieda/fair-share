package com.fairshare.data.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.RoundingMode

/** Money helpers. Amounts are Long grosze (1 zł = 100 gr). */
object Money {
    private const val NBSP = ' '

    /**
     * Updated from the profile so every screen formats with the chosen currency.
     * It is Compose state, so screens redraw when the currency changes.
     */
    var currencyCode: String by mutableStateOf("PLN")

    /**
     * Parses user input like "12", "12,5", "12.50", "1 234,99" into grosze.
     * Returns null for empty, malformed, zero or negative input.
     */
    fun parse(input: String): Long? {
        val normalized = input.trim()
            .replace(" ", "")
            .replace(NBSP.toString(), "")
            .replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value.scale() > 2) return null
        return value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }

    /** 123456 -> "1 234,56 zł" (non-breaking spaces). Sign is kept for negatives. */
    fun format(grosze: Long, withCurrency: Boolean = true, currency: String = currencyCode): String {
        val abs = BigDecimal.valueOf(grosze).abs().movePointLeft(2).setScale(2)
        val (whole, fraction) = abs.toPlainString().split('.')
        val grouped = whole.reversed().chunked(3).joinToString(NBSP.toString()).reversed()
        val sign = if (grosze < 0) "-" else ""
        val text = "$sign$grouped,$fraction"
        return if (withCurrency) "$text$NBSP${symbol(currency)}" else text
    }

    fun symbol(currency: String): String = when (currency.uppercase()) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        "CZK" -> "Kč"
        else -> "zł"
    }

    /** Plain "12,50" for amount fields. */
    fun formatInput(grosze: Long): String =
        BigDecimal.valueOf(grosze).abs().movePointLeft(2).setScale(2).toPlainString().replace('.', ',')

    /**
     * Splits [total] into [parts] shares that differ by at most 1 gr and always sum to [total].
     * The first shares get the leftover grosze: 100 / 3 -> [34, 33, 33].
     */
    fun splitEqually(total: Long, parts: Int): List<Long> {
        require(parts > 0) { "parts must be > 0" }
        require(total >= 0) { "total must be >= 0" }
        val base = total / parts
        val remainder = (total % parts).toInt()
        return List(parts) { index -> if (index < remainder) base + 1 else base }
    }
}

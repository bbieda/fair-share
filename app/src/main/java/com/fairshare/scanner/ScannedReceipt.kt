package com.fairshare.scanner

import com.fairshare.scanner.rules.RuleBasedReceiptParser
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** One position the group can split: a product after its discounts and deposits, or a separate deposit. */
data class ScanItem(val name: String, val priceGrosze: Long)

enum class ScanSource(val label: String) {
    RULES("ML Kit + reguły (na telefonie)"),
    GEMINI("Gemini (zdjęcie)"),
}

/** What the scanner read from a receipt. Amounts in grosze, like the rest of the app. */
data class ScannedReceipt(
    val store: String?,
    /** Amount to pay ("SUMA PLN" / "DO ZAPŁATY", with deposits), or null when no total line was found. */
    val totalGrosze: Long?,
    val items: List<ScanItem>,
    val source: ScanSource,
    /** What the reader corrected or could not reconcile; shown in the debug section. */
    val notes: List<String> = emptyList(),
) {
    val itemsSumGrosze: Long get() = items.sumOf { it.priceGrosze }

    /**
     * The receipt's own checksum: the items (after discounts, with deposits) add up to the amount to pay.
     * In the evaluation (70 new receipts, ML Kit + rules) a receipt that passed it was never read wrong, so a
     * matching sum is shown right away and only the others go to the AI.
     */
    val sumMatches: Boolean
        get() = items.isNotEmpty() && totalGrosze != null && totalGrosze > 0 && itemsSumGrosze == totalGrosze

    /** How far the items are from the total (0 when they match), for picking the better of two readings. */
    val gap: Long get() = if (totalGrosze == null || items.isEmpty()) Long.MAX_VALUE else abs(itemsSumGrosze - totalGrosze)

    companion object {
        const val DEPOSIT_NAME = "Kaucja (opakowania zwrotne)"

        /**
         * Several receipts from one photo as one expense: all items, totals added up. A receipt whose total was not
         * found counts with the sum of its items.
         */
        fun combine(receipts: List<ScannedReceipt>): ScannedReceipt {
            val stores = receipts.mapNotNull { it.store }.distinct()
            return ScannedReceipt(
                store = stores.joinToString(" + ").ifEmpty { null },
                totalGrosze = receipts.sumOf { it.totalGrosze ?: it.itemsSumGrosze }.takeIf { it > 0 },
                items = receipts.flatMap { it.items },
                source = if (receipts.all { it.source == ScanSource.RULES }) ScanSource.RULES else ScanSource.GEMINI,
                notes = receipts.flatMap { it.notes },
            )
        }

        /**
         * Result of the rules → receipt. Deposits listed under the total become one item, like in the benchmark.
         * Unlike the benchmark's adapter, a receipt without a total line keeps `totalGrosze = null`, so it never
         * passes the checksum and goes to the AI (safer; in the evaluation ML Kit always found the total).
         */
        fun fromRules(result: RuleBasedReceiptParser.Result): ScannedReceipt {
            val items = result.items.map { ScanItem(it.name, it.priceGrosze) } +
                listOfNotNull(result.separateDepositGrosze.takeIf { it > 0 }?.let { ScanItem(DEPOSIT_NAME, it) })
            return ScannedReceipt(
                store = result.store,
                totalGrosze = result.totalGrosze,
                items = items,
                source = ScanSource.RULES,
                notes = result.notes,
            )
        }
    }
}

/** One line as a language model returns it (same JSON as the backend: typ / nazwa / kwota). */
data class ModelLine(val type: String, val name: String, val amount: Double)

/**
 * Lines from a model → receipt, with the same arithmetic as the backend (ReceiptModels.toReceipt): a discount or
 * deposit goes to the product above it, a receipt-wide discount is spread over all items, deposits under the total
 * become one item. The model only copies and labels lines; the sums are done here.
 */
object ModelLines {
    const val PRODUCT = "produkt"
    const val DISCOUNT = "rabat"
    const val DEPOSIT = "kaucja"
    const val RECEIPT_DISCOUNT = "rabat_ogolny"
    const val SEPARATE_DEPOSIT = "kaucja_osobna"
    val ALL = listOf(PRODUCT, DISCOUNT, DEPOSIT, RECEIPT_DISCOUNT, SEPARATE_DEPOSIT)

    /** Summary lines a model sometimes returns as a product; never an item. */
    private val SUMMARY_LINE = Regex(
        """^(pod)?suma\b|\bptu\b|sprzeda[żz] opodatkowana|ł[aą]cznie|zwrotne suma|zaoszcz[eę]dzono|udzielono|^(karta|gotówka|reszta|płatność)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun toReceipt(store: String?, total: Double, lines: List<ModelLine>, source: ScanSource): ScannedReceipt {
        val names = mutableListOf<String>()
        val prices = mutableListOf<Long>()
        var receiptDiscount = 0L
        var separateDeposits = 0L
        val notes = mutableListOf<String>()
        // deposits after the last product are the separate "Opakowania zwrotne" section
        val lastProduct = lines.indexOfLast { it.type == PRODUCT }

        for ((index, line) in lines.withIndex()) {
            val amount = abs(line.amount.toGrosze())
            val name = line.name.trim()
            if (SUMMARY_LINE.containsMatchIn(name)) continue
            val type = if (line.type == DEPOSIT && index > lastProduct && lastProduct >= 0) SEPARATE_DEPOSIT else line.type
            when (type) {
                PRODUCT -> if (name.isNotEmpty()) { names += name; prices += amount }
                DISCOUNT -> if (prices.isEmpty()) receiptDiscount += amount else prices[prices.lastIndex] -= amount
                DEPOSIT -> if (prices.isEmpty()) { names += name.ifEmpty { "Kaucja" }; prices += amount } else prices[prices.lastIndex] += amount
                RECEIPT_DISCOUNT -> receiptDiscount += amount
                SEPARATE_DEPOSIT -> separateDeposits += amount
                else -> notes += "Nieznany typ linii „${line.type}” (${line.name}) pominięto."
            }
        }
        if (receiptDiscount > 0 && prices.isNotEmpty()) {
            distributeDiscount(prices, receiptDiscount)
            notes += "Rabat na cały paragon rozłożono proporcjonalnie na pozycje."
        }
        if (separateDeposits > 0) { names += ScannedReceipt.DEPOSIT_NAME; prices += separateDeposits }

        return ScannedReceipt(
            store = store?.trim()?.ifBlank { null },
            totalGrosze = total.toGrosze().takeIf { it > 0 },
            items = names.zip(prices) { n, p -> ScanItem(n, p) },
            source = source,
            notes = notes,
        )
    }

    /** Takes [discount] off the prices in proportion, largest remainders first, so the sum is exact to the grosz. */
    internal fun distributeDiscount(prices: MutableList<Long>, discount: Long) {
        val base = prices.sumOf { it.coerceAtLeast(0) }
        if (base <= 0) return
        val shares = prices.map {
            BigDecimal.valueOf(it.coerceAtLeast(0) * discount).divide(BigDecimal.valueOf(base), 6, RoundingMode.DOWN)
        }
        val floors = shares.map { it.toLong() }.toMutableList()
        var left = discount - floors.sum()
        shares.indices.sortedByDescending { shares[it] - BigDecimal.valueOf(floors[it]) }.forEach { i ->
            if (left > 0) { floors[i]++; left-- }
        }
        prices.indices.forEach { prices[it] -= floors[it] }
    }

    private fun Double.toGrosze(): Long =
        BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
}

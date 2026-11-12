package com.fairshare.scanner.rules

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Reads a Polish receipt from OCR text (ML Kit / Tesseract) with plain rules – no language model.
 *
 * Same file as backend/src/main/kotlin/paragon/rules/RuleBasedReceiptParser.kt in the benchmark (only the package
 * differs): rules tuned on the dev receipts, frozen before the test run of 28.10.2026, later name cleanup (4.11)
 * and fixes from receipts scanned on the phone (11.11). Change it in both places.
 *
 * Uses only the Kotlin standard library and java.text, so the same file can run in the Android app
 * next to ML Kit. The receipt's own checksums (items must add up to the total) are used to spot and
 * fix typical OCR mistakes, e.g. a misread line total when "quantity × unit price" disagrees with it.
 */
object RuleBasedReceiptParser {

    data class Item(val name: String, val priceGrosze: Long)

    data class Result(
        val store: String?,
        /** Final amount to pay, or null when no total line was found. */
        val totalGrosze: Long?,
        val items: List<Item>,
        /** Deposits listed under the total (Lidl "Opakowania zwrotne"), not tied to one product. */
        val separateDepositGrosze: Long,
        /** What the parser corrected using the checksums – useful when looking at a wrong result. */
        val notes: List<String>,
    )

    fun parse(text: String): Result {
        val lines = text.lines().map(::normalizeNumbers).map { it.trim() }.filter { it.isNotEmpty() }
        val notes = mutableListOf<String>()
        val store = detectStore(lines)

        // a card terminal slip ("DOKUMENT NIEFISKALNY") has a total but no items
        val nonFiscal = lines.none { FISCAL_HEADER.containsMatchIn(it.fold()) } && lines.any { it.fold().contains("niefiskaln") }
        val drafts = if (nonFiscal) mutableListOf() else readItems(lines)
        val deposit = readSeparateDeposits(lines)
        val totals = totalCandidates(lines)

        var total = totals.lastOrNull { it.strong }?.amount ?: totals.lastOrNull()?.amount
        dropSubtotal(drafts, total?.let { it - deposit }, notes)
        val itemsSum = drafts.sumOf { it.price() }
        // Several "total" lines may be printed (subtotal, after deposits, payment); prefer the one the items add up to.
        if (drafts.isNotEmpty() && total != itemsSum + deposit) {
            totals.lastOrNull { it.amount == itemsSum + deposit }?.let {
                if (total != null) notes += "Total ${fmt(total)} → ${fmt(it.amount)} (the one the items add up to)"
                total = it.amount
            }
        }
        total?.let { repairWithChecksum(drafts, it - deposit, notes) }

        return Result(
            store = store,
            totalGrosze = total,
            items = drafts.map { Item(it.displayName(), it.price()) },
            separateDepositGrosze = deposit,
            notes = notes,
        )
    }

    // ---------------------------------------------------------------- items

    /** [nameOnOwnLine]: the name was printed on the line above the price (Castorama, PYZIK), not before the price. */
    private class Draft(val rawName: String, val gross: Long, val qty: Double?, val unit: Long?, val nameOnOwnLine: Boolean = false) {
        var discounts = 0L
        var deposits = 0L
        /** Price after discount printed on its own line (Lidl, Biedronka). */
        var net: Long? = null
        var override: Long? = null

        val qtyTimesUnit: Long? get() = if (qty != null && unit != null) (qty * unit).roundToLong() else null

        fun price(): Long = override ?: ((net ?: (gross - discounts)) + deposits)

        /** Other readings of the same line, used when the items do not add up. */
        fun alternatives(): Set<Long> = setOfNotNull(
            gross - discounts + deposits,
            qtyTimesUnit?.let { it - discounts + deposits },
            net?.let { it + deposits },
        ) - price()

        fun cleanedName(): String = cleanName(rawName, nameOnOwnLine)

        fun displayName(): String {
            val name = cleanedName().ifEmpty { "Pozycja" }
            val q = qty
            return if (q != null && q >= 2 && q == Math.floor(q)) "$name (${q.toInt()} szt.)" else name
        }
    }

    private fun readItems(lines: List<String>): MutableList<Draft> {
        val start = lines.indexOfFirst { FISCAL_HEADER.containsMatchIn(it.fold()) }.let { idx ->
            if (idx >= 0) idx + 1 else (lines.indexOfFirst { DATE_LINE.matches(it) } + 1).coerceAtLeast(0)
        }
        val drafts = mutableListOf<Draft>()
        var pendingName: String? = null
        var expectNet = false
        var pendingDiscountKeyword = false

        val itemLines = lines.drop(start).flatMap { line ->
            TWO_LINE_TOTALS.find(line)?.let { m -> listOf(line.substring(0, m.groups[2]!!.range.first).trim(), m.groupValues[2]).map(::normalizeNumbers) }
                ?: listOf(line)
        }
        for (line in itemLines) {
            val folded = line.fold()
            if (ITEMS_END.containsMatchIn(folded) || ITEMS_END_SUMMARY.containsMatchIn(folded) ||
                ITEMS_END_LOOSE.containsMatchIn(folded.replace(" ", ""))
            ) break
            // header lines (NIP, "30-444 Libertów") when the "PARAGON FISKALNY" line was not recognised
            if (HEADER_LINE.containsMatchIn(folded)) continue
            // Rossmann "Uwzgl. opust: -3,00 st. c 14,98": the price above already includes the discount
            // pharmacy "Bez recepty 39,99": how the item above was paid for, not another item
            if (INCLUDED_DISCOUNT.containsMatchIn(folded) || NOT_AN_ITEM.containsMatchIn(folded)) continue
            val amount = trailingAmount(line)?.takeUnless { a -> UNIT_PRICE_END.containsMatchIn(line.substring(0, a.start)) }
            val prefix = amount?.let { line.substring(0, it.start).trim() } ?: line

            val isDiscount = DISCOUNT.containsMatchIn(folded) || (amount != null && amount.negative)
            if (isDiscount && amount == null) { pendingDiscountKeyword = true; continue }
            if (amount != null && (isDiscount || (pendingDiscountKeyword && prefix.isBlank()))) {
                drafts.lastOrNull()?.let { it.discounts += abs(amount.grosze) }
                expectNet = true
                pendingDiscountKeyword = false
                pendingName = null
                continue
            }
            pendingDiscountKeyword = false

            if (amount != null && DEPOSIT.containsMatchIn(folded)) {
                drafts.lastOrNull()?.let { it.deposits += amount.grosze }
                continue
            }

            if (amount == null) {
                // quantity × unit price with the line total mangled or cut off by OCR: "1 x14,99 t4,.99#" under its
                // name, "BELLA PERFECTA 1 x6,99 6," with it
                val q = QTY.find(line)
                if (q != null) {
                    val before = line.substring(0, q.range.first).trim()
                    val after = line.substring(q.range.last + 1).trim()
                    val mangledTotal = after.length <= 10 && after.any { it.isDigit() } && !LETTER_WORD.containsMatchIn(after)
                    val nameHere = LETTER_WORD.containsMatchIn(before.replace(QTY_UNITS, " "))
                    if (mangledTotal && (nameHere || pendingName != null)) {
                        val qty = q.groupValues[1].toNumber()
                        val unit = q.groupValues[2].toGrosze()
                        val name = if (nameHere) before else pendingName.orEmpty()
                        drafts += Draft(name, (qty * unit).roundToLong(), qty, unit, nameOnOwnLine = !nameHere)
                        pendingName = null
                        expectNet = false
                        continue
                    }
                }
                // a name printed on its own line, the price comes on the next one
                if (LETTER_WORD.containsMatchIn(line)) pendingName = line
                continue
            }

            val qty = parseQuantity(prefix)
            val hasUnitPrice = qty != null || AMOUNT_ANYWHERE.containsMatchIn(prefix)
            val last = drafts.lastOrNull()
            if (expectNet && last != null && !hasUnitPrice) {
                // the price after the discount, sometimes with OCR junk in front of it
                val maxPlausible = maxOf(last.gross, last.qtyTimesUnit ?: 0L)
                if (amount.grosze in 0..maxPlausible) last.net = amount.grosze
                // a skewed photo puts that price on the next item's name row (Biedronka: "Renif32cmBezLT A 18,74")
                if (LETTER_WORD.containsMatchIn(prefix)) pendingName = prefix
                expectNet = false
                continue
            }
            expectNet = false

            val nameOnLine = LETTER_WORD.containsMatchIn(prefix.replace(QTY, " ").replace(QTY_UNITS, " "))
            val name = if (nameOnLine) prefix else pendingName.orEmpty()
            drafts += Draft(name, amount.grosze, qty?.first, qty?.second, nameOnOwnLine = !nameOnLine && pendingName != null)
            pendingName = null
        }
        return drafts
    }

    /**
     * A sum printed next to the items (a skewed photo puts "SPRZEDAŻ OPODATKOWANA 47,89" on the last item's row) reads as
     * one more item. The last item equal to the sum of all items above it is that sum: dropped when the total is unknown
     * or when the items then add up to it.
     */
    private fun dropSubtotal(drafts: MutableList<Draft>, target: Long?, notes: MutableList<String>) {
        if (drafts.size < 3) return
        val last = drafts.last()
        val before = drafts.dropLast(1).sumOf { it.price() }
        if (last.price() != before) return
        if (target != null && target != before) return
        notes += "${fmt(last.price())} is the sum of the items above it, not an item"
        drafts.removeAt(drafts.lastIndex)
    }

    /** If the items do not add up, try the other reading of one line (e.g. quantity × unit price). */
    private fun repairWithChecksum(drafts: List<Draft>, target: Long, notes: MutableList<String>) {
        val diff = target - drafts.sumOf { it.price() }
        if (diff == 0L || drafts.isEmpty()) return
        for (d in drafts) {
            val fix = d.alternatives().firstOrNull { it - d.price() == diff } ?: continue
            notes += "${d.cleanedName()}: ${fmt(d.price())} → ${fmt(fix)} (items now add up to the total)"
            d.override = fix
            return
        }
        // one item on the receipt: it costs what the receipt says, whatever OCR made of the item line
        if (drafts.size == 1 && target > 0) {
            notes += "${drafts[0].cleanedName()}: ${fmt(drafts[0].price())} → ${fmt(target)} (only item, takes the total)"
            drafts[0].override = target
            return
        }
        notes += "Items add up to ${fmt(drafts.sumOf { it.price() })}, total is ${fmt(target)} – could not reconcile"
    }

    // ---------------------------------------------------------------- deposits and totals

    private fun readSeparateDeposits(lines: List<String>): Long {
        var inSection = false
        var sectionFound = false
        var sectionSum: Long? = null
        var lineSum = 0L
        // Carrefour: "Kaucja 0,50" on its own after "SUMA PLN", before "Do zapłaty"
        var afterSum = false
        var looseSum = 0L
        for (line in lines) {
            val folded = line.fold()
            if (DEPOSIT_SECTION.containsMatchIn(folded)) {
                inSection = true
                sectionFound = true
                if (folded.contains("suma")) sectionSum = trailingAmount(line)?.grosze
                continue
            }
            if (!inSection) {
                if (folded.startsWith("suma pln") || folded.startsWith("suma:")) afterSum = true
                else if (afterSum && folded.startsWith("kaucj")) trailingAmount(line)?.let { if (it.grosze > 0) looseSum += it.grosze }
                continue
            }
            if (TOTAL_LINE.containsMatchIn(folded)) break
            if (Regex("""\bsuma\b""").containsMatchIn(folded)) { trailingAmount(line)?.let { sectionSum = it.grosze }; continue }
            trailingAmount(line)?.let { lineSum += it.grosze }
        }
        // a section header with nothing under it (Carrefour prints it at the very end, for the next slip) does not count
        return if (sectionFound && (sectionSum != null || lineSum > 0)) sectionSum ?: lineSum else looseSum
    }

    private data class TotalCandidate(val amount: Long, val strong: Boolean)

    private fun totalCandidates(lines: List<String>): List<TotalCandidate> {
        val out = mutableListOf<TotalCandidate>()
        for ((i, line) in lines.withIndex()) {
            val folded = line.fold()
            if (NOT_A_TOTAL.containsMatchIn(folded)) continue
            val strong = TOTAL_LINE.containsMatchIn(folded)
            val payment = PAYMENT_LINE.containsMatchIn(folded)
            if (!strong && !payment) continue
            // Nike: "379,51" printed on the line above "SUMA PLN"
            val amount = trailingAmount(line)?.grosze
                ?: lines.getOrNull(i - 1)?.takeIf { PURE_AMOUNT.matches(it) }?.let { trailingAmount(it)?.grosze }
                ?: lines.getOrNull(i + 1)?.takeIf { PURE_AMOUNT.matches(it) }?.let { trailingAmount(it)?.grosze }
                ?: continue
            if (amount > 0) out += TotalCandidate(amount, strong)
        }
        return out
    }

    // ---------------------------------------------------------------- store

    private val STORES = listOf(
        "biedronka" to "Biedronka", "jeronimo martins" to "Biedronka", "lidl" to "Lidl", "zabka" to "Żabka",
        "dino polska" to "Dino", "carrefour" to "Carrefour", "kaufland" to "Kaufland", "auchan" to "Auchan",
        "netto" to "Netto", "aldi" to "Aldi", "stokrotka" to "Stokrotka", "rossmann" to "Rossmann", "hebe" to "Hebe",
        "pepco" to "Pepco", "action" to "Action", "castorama" to "Castorama", "leroy merlin" to "Leroy Merlin",
        "obi" to "OBI", "ikea" to "IKEA", "mcdonald" to "McDonald's", "kfc" to "KFC", "burger king" to "Burger King",
        "nike" to "Nike", "adidas" to "adidas", "decathlon" to "Decathlon", "media expert" to "Media Expert",
        "euro agd" to "RTV Euro AGD", "empik" to "Empik", "orlen" to "Orlen", "circle k" to "Circle K",
        "lewiatan" to "Lewiatan", "intermarche" to "Intermarché", "polomarket" to "POLOmarket",
    )

    private fun detectStore(lines: List<String>): String? {
        fun find(text: String) = STORES.firstOrNull { (key, _) -> Regex("""\b$key""").containsMatchIn(text) }?.second
        val words = { ls: List<String> -> " " + ls.joinToString(" ") { it.fold().replace(Regex("[^a-z ]"), " ") } + " " }
        // OCR mixes up n/m and u/w ("Rossnann", "Libertouska"): compare with those letters folded on both sides
        fun confusable(text: String) = text.replace('n', 'm').replace('u', 'w')
        fun findLoose(text: String) = STORES.firstOrNull { (key, _) -> Regex("""\b${confusable(key)}""").containsMatchIn(confusable(text)) }?.second
        return find(words(lines.take(12))) ?: find(words(lines)) ?: findLoose(words(lines.take(12))) ?: headerName(lines)
    }

    /**
     * A shop that is not on the list: the first line of the header that looks like a name, i.e. not the
     * address, NIP, phone or a register number ("PYZIK", "Firma Handlowo Usługowa ALMA Leszek Decyk").
     */
    private fun headerName(lines: List<String>): String? {
        val end = lines.indexOfFirst { FISCAL_HEADER.containsMatchIn(it.fold()) }.let { if (it in 1..12) it else 6 }
        return lines.take(end).firstOrNull { line ->
            val f = line.fold()
            LETTER_WORD.containsMatchIn(line) && !NOT_A_NAME.containsMatchIn(f) && line.count { it.isDigit() } <= 2
        }?.trim(' ', '*', '-', '.', ',', ':')?.takeIf { it.length >= 3 }
    }

    // ---------------------------------------------------------------- text helpers

    private class Amount(val grosze: Long, val negative: Boolean, val start: Int)

    /** Amount at the end of a line: "47,88A", "-24,00", "6.1 C", "182,94 PLN". */
    private fun trailingAmount(line: String): Amount? {
        val m = TRAILING_AMOUNT.find(line) ?: return null
        val whole = m.groupValues[2].toLong()
        val frac = m.groupValues[3].padEnd(2, '0').toLong()
        val negative = m.groupValues[1] == "-"
        val value = whole * 100 + frac
        return Amount(if (negative) -value else value, negative, m.range.first)
    }

    /** "12 x3,99", "1SZT*7,98=", "1,530kg x 3.99", "1-2.99", "| * 9.99", "1 17.99", "10.99" → (qty, unit). */
    private fun parseQuantity(prefix: String): Pair<Double, Long>? {
        QTY.find(prefix)?.let { return it.groupValues[1].toNumber() to it.groupValues[2].toGrosze() }
        val bare = prefix.replace('|', '1').replace(Regex("""[*=]"""), " ").trim()
        if (LETTER_WORD.containsMatchIn(bare.replace(QTY_UNITS, " "))) return null
        val numbers = NUMBER.findAll(bare).map { it.value }.toList()
        return when {
            numbers.size >= 2 && !numbers[numbers.size - 2].contains(Regex("[.,]")) ->
                numbers[numbers.size - 2].toNumber() to numbers.last().toGrosze()
            numbers.isNotEmpty() && numbers.last().contains(Regex("[.,]")) -> 1.0 to numbers.last().toGrosze()
            else -> null
        }
    }

    private fun String.toNumber() = replace(',', '.').toDouble()
    private fun String.toGrosze() = (replace(',', '.').toDouble() * 100).roundToLong()

    /** OCR splits numbers: "22. 95", "33, 00", "1 * 16. 79", and drops the comma: "12 49A" at the end of a line. */
    private fun normalizeNumbers(line: String): String {
        var s = line.replace(Regex("""(\d)\s*([.,])\s+(\d)"""), "$1$2$3")
        // unit price and line total glued together: "1szt x8,208,20C" = "x8,20 8,20C"
        // (lowercase x, ×, * may have a space after them; uppercase X only when glued to the number: Lidl prints "X" as a VAT group)
        s = s.replace(Regex("""((?:[x×*]\s*|X)\d{1,5}[.,]\d{2})(\d{1,5}[.,]\d{2})"""), "$1 $2")
        // prices have exactly 2 decimals: a 3rd digit at the end is the VAT letter read as a digit ("5,490" = "5,49D")
        s = s.replace(Regex("""(\d[.,]\d{2})\d\s*$"""), "$1")
        if (TRAILING_AMOUNT.find(s) == null) s = s.replace(Regex("""(\d{1,4}) (\d{2})(\s*\p{L})?$"""), "$1,$2$3")
        // "1szt x4,50 4 506": the same with the VAT letter read as a digit, only after a unit price
        if (TRAILING_AMOUNT.find(s) == null && UNIT_PRICE.containsMatchIn(s)) s = s.replace(Regex("""(\d{1,4}) (\d{2})\d$"""), "$1,$2")
        return s
    }

    /** [ownLine]: the name had a line to itself, so nothing after it is a price or quantity. */
    private fun cleanName(raw: String, ownLine: Boolean = false): String {
        var s = raw.replace(QTY, " ").replace(Regex("""\s+"""), " ").trim()
        val tokens = s.split(' ').filter { it.isNotBlank() }.toMutableList()
        // leading: VAT letter, EAN / article codes, "55:"; also a VAT letter OCR read as something else ("4 5411183157255")
        while (tokens.isNotEmpty() && (tokens[0].matches(LEADING_CODE) || (tokens[0].length == 1 && tokens.getOrNull(1)?.matches(EAN) == true))) {
            tokens.removeAt(0)
        }
        // trailing: VAT/group letters (A, F, X…), numbers, prices and "1I:12.49" – but keep "500ml", "4-pak", and on a
        // name line of its own a size the printer cut short: "AKRYL ... 280 M", "TAŚMA ... 48 MM X 50"
        if (!(ownLine && endsWithSize(tokens))) {
            while (tokens.isNotEmpty() && tokens.last().let { t -> t.length == 1 || !t.any { it.isLetter() } || PRICE_IN_TOKEN.containsMatchIn(t) || ':' in t || '*' in t }) {
                tokens.removeAt(tokens.lastIndex)
            }
        }
        s = tokens.joinToString(" ").trim(' ', ':', '-', '*', '|')
        // OCR reads O as 0 inside words: "0CHRONNA" -> "OCHRONNA", "0liwki" -> "Oliwki" (not in "0,5L", "500ml", "10x")
        s = ZERO_IN_WORD.replace(s) { m ->
            if (m.range.first > 0 && s.getOrNull(m.range.last + 1)?.isLowerCase() == true) "o" else "O"
        }
        val letters = s.filter { it.isLetter() }
        if (letters.length >= 4 && letters.count { it.isUpperCase() } > letters.length * 0.7) {
            s = s.lowercase().replaceFirstChar { it.uppercase() }
        }
        return s
    }

    /** "... 280 M" or "... 48 MM X 50": a number with a unit at the end, optionally "x <number>" after it. */
    private fun endsWithSize(tokens: List<String>): Boolean {
        val n = tokens.size
        fun at(i: Int, r: Regex) = i in 0 until n && tokens[i].matches(r)
        return (at(n - 2, SIZE_NUMBER) && at(n - 1, SIZE_UNIT)) ||
            (at(n - 4, SIZE_NUMBER) && at(n - 3, SIZE_UNIT) && at(n - 2, SIZE_TIMES) && at(n - 1, SIZE_NUMBER))
    }

    /** Lowercase without Polish/other diacritics, for keyword matching that tolerates OCR ("OPUŠT", "PŁATNOŠCI"). */
    private fun String.fold(): String =
        Normalizer.normalize(lowercase().replace('ł', 'l'), Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")

    private fun fmt(grosze: Long) = "%d,%02d".format(grosze / 100, abs(grosze % 100))

    private val TRAILING_AMOUNT = Regex("""(-?)\s*(\d{1,5})[.,](\d{1,2})\s*(?:pln|zl)?\s*\p{L}?\s*$""", RegexOption.IGNORE_CASE)
    private val PURE_AMOUNT = Regex("""\s*-?\d{1,5}[.,]\d{1,2}\s*\p{L}?\s*""")
    private val AMOUNT_ANYWHERE = Regex("""\d[.,]\d{2}""")
    private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
    private val QTY = Regex("""(\d{1,4}(?:[.,]\d{1,3})?)\s*(?:szt\.?|kg)?\s*[x×*:-]\s*(\d{1,5}[.,]\d{1,2})""", RegexOption.IGNORE_CASE)
    private val QTY_UNITS = Regex("""(?i)szt\.?|kg""")
    private val PRICE_IN_TOKEN = Regex("""\d[.,]\d{2}""")
    private val LEADING_CODE = Regex("""[A-G]|\d{4,}:?|\d{1,4}:|:""")
    /** Barcode (EAN-8/13) or long article number. */
    private val EAN = Regex("""\d{8,}:?""")
    private val SIZE_NUMBER = Regex("""\d{1,5}(?:[.,]\d{1,3})?""")
    private val SIZE_UNIT = Regex("""(?i)m|ml|l|cl|kg|mm|cm|m2|m3""")
    private val SIZE_TIMES = Regex("""[xX×]""")
    private val ZERO_IN_WORD = Regex("""(?<=\p{L})0(?=\p{L})|(?<![\p{L}\d.,])0(?=\p{L}{3,})""")
    /** "PARAGON FISKALNY", also when OCR mangles it ("ooGON FISKALNY", "FISKALRY") – but not "NIEFISKALNY". */
    private val FISCAL_HEADER = Regex("""paragon\s*f\s*[il1]\s*s\s*k|(?<!nie\s?)f\s?[il1]\s?s\s?k\s?a\s?l""")
    private val LETTER_WORD = Regex("""\p{L}{3,}""")
    /** Header lines that are not the shop's name. */
    private val NOT_A_NAME = Regex("""\bnip\b|\btel\b|\bul\.|\bal\.|\d{2}-\d{3}|adres|siedzib|\bbdo\b|nr rej|paragon|fiskal|\bkasa\b|\bwww\b|http|@""")
    private val DATE_LINE = Regex("""\d{4}-\d{2}-\d{2}""")

    /** "SPRLEDAE PODA TKWEA" = "SPRZEDAŻ OPODATKOWANA", checked with the spaces removed. */
    private val ITEMS_END_LOOSE = Regex("""opodat|podatk""")
    /** Biedronka: "OPUSTY ŁĄCZNIE -29,98" and "Sp: A=49,98 C=11,49" come right after the items. */
    private val ITEMS_END_SUMMARY = Regex("""^opusty\b|\blacznie\b|^sp\s?:""")
    private val HEADER_LINE = Regex("""\bnip\b|\b\d{2}-\d{3}\b""")
    private val INCLUDED_DISCOUNT = Regex("""\bu[wu]?zgl""")
    private val NOT_AN_ITEM = Regex("""^(bez recept|na recept|ryczal|refundac)""")
    private val UNIT_PRICE = Regex("""[x×*]\s*\d{1,5}[.,]\d{2}""")
    private val ITEMS_END = Regex("""^(sprzed|ptu\b|piu\b|kwota\b|kuota\b|podsuma|su[mn]a\b|razem\b|d[o0]\s+zap)|opak\S*\s+\S*otne""")
    private val DISCOUNT = Regex("""opust|rabat|upust|kupon|taniej|promocj|znizk""")
    private val DEPOSIT = Regex("""kaucj""")
    private val DEPOSIT_SECTION = Regex("""opak\S*\s+\S*otne""")
    private val TOTAL_LINE = Regex("""^(su[mn]a\b|razem\b|d[o0]\s+zap)|sprzeda\S*\s*:""")
    private val PAYMENT_LINE = Regex("""^(karta|platnosc|card|debit|kredyt|rozliczenie platnosci)\b""")
    /** Two line totals with their VAT letters at the end of one row: "... 1szt x4,20 4,20A 4,80C". */
    private val TWO_LINE_TOTALS = Regex("""(?<![xX×*\d.,])(\d{1,5}[.,]\d{2}[A-G0])\s+(\d{1,5}[.,]\d{2}\s*[A-G0])\s*$""")
    /** "... x17,00 C": the amount is a unit price (nothing after it), so the line total is on the next row. */
    private val UNIT_PRICE_END = Regex("""(?:[x×*]\s*|X)$""")
    private val NOT_A_TOTAL = Regex("""ptu|piu|opust|otne|kwota|zaoszcz|gotowk|reszta|opodat""")
}

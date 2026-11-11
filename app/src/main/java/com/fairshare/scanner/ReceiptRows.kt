package com.fairshare.scanner

import kotlin.math.abs
import kotlin.math.tan

/** One line of text found by OCR, with its box in pixels of the upright photo. */
data class OcrLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerY: Float get() = (top + bottom) / 2
    val height: Float get() = bottom - top
}

/**
 * Puts OCR lines back into receipt rows. ML Kit returns the name ("Chleb") and the price ("5,49 C") of one
 * receipt row as separate lines; the rules expect them on one row. Lines whose vertical centres are within half a
 * line height and that do not overlap horizontally become one row, left to right, joined by three spaces.
 *
 * Same algorithm as in the benchmark (backend ApiHttp.rebuildRows, mlkit-runner, eval/ocr_engines.py), so the rules
 * see the text laid out exactly as in the tests.
 */
object ReceiptRows {
    private val AMOUNT_AT_END = Regex("""\d[.,]\d{2}\s*\S?\s*$""")
    private val AMOUNT_AT_START = Regex("""^\s*-?\d{1,5}[.,]\d{2}\b""")
    private val KEYWORD = Regex("""paragon|fiskaln|suma|razem|ptu|do zap|sprzeda""")

    /**
     * How much [text] looks like a receipt read the right way up. Read upside down, ML Kit still reads the words, but
     * the rows come out in reverse: "22,97   SUMA PLN" instead of "SUMA PLN   22,97", the total above the header.
     */
    fun orientationScore(text: String): Int {
        val rows = text.lines().filter { it.isNotBlank() }
        val atEnd = rows.count { AMOUNT_AT_END.containsMatchIn(it) }
        val atStartOnly = rows.count { AMOUNT_AT_START.containsMatchIn(it) && !AMOUNT_AT_END.containsMatchIn(it) }
        var score = (atEnd - atStartOnly) * 10 +
            rows.count { KEYWORD.containsMatchIn(it.lowercase()) } * 25 +
            text.count { it.isLetter() } / 20
        val header = rows.indexOfFirst { it.lowercase().contains("paragon") }
        val total = rows.indexOfFirst { it.lowercase().let { r -> r.startsWith("suma") || r.contains("do zap") } }
        if (header >= 0 && total >= 0) score += if (header < total) 50 else -50
        return score
    }


    /**
     * [tiltDegrees]: how much the text runs downhill to the right (clockwise, as ML Kit reports line angles). A receipt
     * photographed 1° askew puts the price at the right edge half a line lower than its name at the left edge, which
     * pairs it with the wrong row (seen on a Biedronka receipt); the rows are matched on heights levelled by the tilt.
     */
    fun rebuild(lines: List<OcrLine>, tiltDegrees: Float = 0f): String {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return ""
        val medianHeight = usable.map { it.height }.sorted()[usable.size / 2].coerceAtLeast(1e-9f)
        val slope = tan(Math.toRadians(tiltDegrees.toDouble())).toFloat()
        val level = usable.associateWith { it.centerY - (it.left + it.right) / 2 * slope }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in usable.sortedBy { level.getValue(it) }) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.map { level.getValue(it) }.average()
                if (abs(level.getValue(line) - rowCenter) < 0.5 * medianHeight && row.none { overlapsHorizontally(it, line) }) {
                    row += line
                    continue
                }
            }
            rows += mutableListOf(line)
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString("   ") { it.text.trim() } }
    }

    private fun overlapsHorizontally(a: OcrLine, b: OcrLine): Boolean {
        val overlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        return overlap > 0.3f * minOf(a.right - a.left, b.right - b.left)
    }
}

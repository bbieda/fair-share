package com.fairshare.scanner

import kotlin.math.abs

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

    fun rebuild(lines: List<OcrLine>): String {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return ""
        val medianHeight = usable.map { it.height }.sorted()[usable.size / 2].coerceAtLeast(1e-9f)
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in usable.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.map { it.centerY }.average()
                if (abs(line.centerY - rowCenter) < 0.5 * medianHeight && row.none { overlapsHorizontally(it, line) }) {
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

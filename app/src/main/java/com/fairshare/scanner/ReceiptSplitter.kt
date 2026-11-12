package com.fairshare.scanner

import java.text.Normalizer

/**
 * Tells several receipts in one photo apart. Receipts photographed together are separate pieces of paper: each has
 * its own "PARAGON FISKALNY" line, and there is a strip of table between them where OCR finds no text. The photo is cut
 * along such a strip into two parts with a header each, and each part again while it has more than one header, so
 * three receipts in an L (one on the left, two stacked on the right) come apart too.
 */
object ReceiptSplitter {
    private val FISCAL_HEADER = Regex("""paragon\s*f\s*[il1]\s*s\s*k|(?<!nie\s?)f\s?[il1]\s?s\s?k\s?a\s?l""")

    /** A receipt has at least a header, an item and a total: fewer lines are a stray line, not a receipt. */
    private const val MIN_LINES = 4

    fun isFiscalHeader(text: String): Boolean = FISCAL_HEADER.containsMatchIn(fold(text))

    /** One list of lines per receipt, left to right / top to bottom; a single list when there is one receipt. */
    fun split(lines: List<OcrLine>): List<List<OcrLine>> {
        if (lines.count { isFiscalHeader(it.text) } < 2 || lines.size < 2 * MIN_LINES) return listOf(lines)
        val lineHeight = lines.map { it.height }.sorted()[lines.size / 2]
        // Side by side, the text of two receipts can come within a few millimetres (seen: 40 px at 90 px line height);
        // inside one receipt, the full-width lines (header, address, totals) leave no vertical strip at all.
        // One under the other, a gap must be clearly bigger than the spacing between the lines of one receipt.
        val parts = cut(lines, minGap = 0.25f * lineHeight, start = { it.left }, end = { it.right })
            ?: cut(lines, minGap = 1.0f * lineHeight, start = { it.top }, end = { it.bottom })
            ?: return listOf(lines)
        return parts.flatMap { split(it) }
    }

    /** The widest empty strip (at least [minGap] wide) with a receipt on each side; null when there is none. */
    private fun cut(
        lines: List<OcrLine>,
        minGap: Float,
        start: (OcrLine) -> Float,
        end: (OcrLine) -> Float,
    ): List<List<OcrLine>>? {
        val sorted = lines.sortedBy(start)
        val gaps = mutableListOf<Pair<Float, Float>>() // empty strips (from, to) between the lines' extents
        var reach = end(sorted.first())
        for (line in sorted.drop(1)) {
            if (start(line) - reach >= minGap) gaps += reach to start(line)
            reach = maxOf(reach, end(line))
        }
        for ((from, to) in gaps.sortedByDescending { (from, to) -> to - from }) {
            val middle = (from + to) / 2
            val (before, after) = lines.partition { (start(it) + end(it)) / 2 < middle }
            if (listOf(before, after).all { side -> side.size >= MIN_LINES && side.any { isFiscalHeader(it.text) } }) {
                return listOf(before, after)
            }
        }
        return null
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase().replace('ł', 'l'), Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
}

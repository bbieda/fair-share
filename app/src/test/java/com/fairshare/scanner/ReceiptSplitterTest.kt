package com.fairshare.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptSplitterTest {

    /** A receipt as OCR lines: header, two items, total; [x], [y] = its top-left corner, 1000 px wide. */
    private fun receipt(x: Float, y: Float, total: String): List<OcrLine> {
        fun line(text: String, left: Float, row: Int, right: Float) = OcrLine(text, x + left, y + row * 60f, x + right, y + row * 60f + 40f)
        return listOf(
            line("PYZIK", 400f, 0, 600f),
            line("SKLEP SPOŻYWCZO-PRZEMYSŁOWY", 150f, 1, 850f),
            line("PARAGON FISKALNY", 250f, 2, 750f),
            line("Chleb C", 20f, 3, 200f),
            line("1szt x5,49 5,49C", 600f, 3, 980f),
            line("Mleko C", 20f, 4, 200f),
            line("1szt x3,20 3,20C", 600f, 4, 980f),
            line("SUMA PLN", 20f, 5, 300f),
            line(total, 800f, 5, 980f),
        )
    }

    @Test
    fun `one receipt stays one`() {
        val lines = receipt(0f, 0f, "8,69")
        assertEquals(listOf(lines), ReceiptSplitter.split(lines))
    }

    @Test
    fun `two receipts side by side are split between them`() {
        val left = receipt(0f, 0f, "8,69")
        val right = receipt(1150f, 80f, "25,99")
        val parts = ReceiptSplitter.split((left + right).shuffled(java.util.Random(1)))
        assertEquals(2, parts.size)
        assertEquals(left.toSet(), parts[0].toSet())
        assertEquals(right.toSet(), parts[1].toSet())
    }

    @Test
    fun `two receipts one under the other are split between them`() {
        val top = receipt(0f, 0f, "8,69")
        val bottom = receipt(60f, 520f, "48,50")
        val parts = ReceiptSplitter.split(top + bottom)
        assertEquals(2, parts.size)
        assertEquals(top.toSet(), parts[0].toSet())
        assertEquals(bottom.toSet(), parts[1].toSet())
    }

    @Test
    fun `three receipts - one on the left, two stacked on the right, text only a few pixels apart`() {
        val left = receipt(0f, 300f, "5,70")
        val topRight = receipt(1015f, 0f, "26,90")
        val bottomRight = receipt(1020f, 600f, "6,27")
        val parts = ReceiptSplitter.split(left + topRight + bottomRight)
        assertEquals(3, parts.size)
        assertEquals(listOf(left.toSet(), topRight.toSet(), bottomRight.toSet()), parts.map { it.toSet() })
    }

    @Test
    fun `a second header with no gap does not split a receipt`() {
        val lines = receipt(0f, 0f, "8,69") + OcrLine("PARAGON FISKALNY", 250f, 380f, 750f, 420f)
        assertEquals(1, ReceiptSplitter.split(lines).size)
    }

    @Test
    fun `header recognised despite OCR spaces, but not NIEFISKALNY`() {
        assertTrue(ReceiptSplitter.isFiscalHeader("PARAGON FI SKALNY"))
        assertTrue(ReceiptSplitter.isFiscalHeader("PARAGON FLSKALNY"))
        assertTrue(!ReceiptSplitter.isFiscalHeader("NIEFISKALNY"))
        assertTrue(!ReceiptSplitter.isFiscalHeader("DOKUMENT NIE FISKALNY"))
    }
}

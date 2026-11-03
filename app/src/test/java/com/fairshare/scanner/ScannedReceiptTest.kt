package com.fairshare.scanner

import com.fairshare.scanner.rules.RuleBasedReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedReceiptTest {

    @Test
    fun `rules result with a separate deposit passes the checksum`() {
        val receipt = ScannedReceipt.fromRules(
            RuleBasedReceiptParser.parse(
                """
                PARAGON FISKALNY
                Pepsi 500ml DRS A   1szt x5,50 5,50A
                Chipsy C   1szt x9,20 9,20C
                SUMA PLN   14,70
                OPAKOWANIA ZWROTNE WYDANIA
                Opakowanie zwr.   1 x0,50 0,50
                OPAKOWANIA ZWROTNE SUMA   0,50
                DO ZAPLATY 15,20 PLN
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(550L, 920L, 50L), receipt.items.map { it.priceGrosze })
        assertEquals(ScannedReceipt.DEPOSIT_NAME, receipt.items.last().name)
        assertEquals(1520L, receipt.totalGrosze)
        assertTrue(receipt.sumMatches)
        assertEquals(0L, receipt.gap)
        assertEquals(ScanSource.RULES, receipt.source)
    }

    @Test
    fun `a receipt without a total line never passes the checksum`() {
        val receipt = ScannedReceipt.fromRules(
            RuleBasedReceiptParser.parse("PARAGON FISKALNY\nChleb   1 x5,49 5,49C\nMleko   1 x3,20 3,20C"),
        )
        assertNull(receipt.totalGrosze)
        assertFalse(receipt.sumMatches)
        assertEquals(Long.MAX_VALUE, receipt.gap)
    }

    @Test
    fun `items that do not add up go to the AI step`() {
        val receipt = ScannedReceipt("Lidl", 1000L, listOf(ScanItem("A", 300L), ScanItem("B", 500L)), ScanSource.RULES)
        assertFalse(receipt.sumMatches)
        assertEquals(200L, receipt.gap)
    }

    @Test
    fun `model lines - discount and deposit go to the product above, separate deposits become one item`() {
        val receipt = ModelLines.toReceipt(
            store = " Dino ",
            total = 18.98,
            lines = listOf(
                ModelLine("produkt", "Masło ekstra 200g (2 szt.)", 15.98),
                ModelLine("rabat", "Rabat", -3.00),
                ModelLine("produkt", "Woda 1,5l", 2.50),
                ModelLine("kaucja", "Kaucja butelka", 0.50),
                ModelLine("produkt", "SUMA PLN", 15.98),
                ModelLine("kaucja", "Kaucja PET", 2.00),
                ModelLine("kaucja_osobna", "Kaucja puszka", 1.00),
            ),
            source = ScanSource.GEMINI,
        )
        assertEquals("Dino", receipt.store)
        assertEquals(
            listOf("Masło ekstra 200g (2 szt.)" to 1298L, "Woda 1,5l" to 300L, ScannedReceipt.DEPOSIT_NAME to 300L),
            receipt.items.map { it.name to it.priceGrosze },
        )
        assertEquals(1898L, receipt.totalGrosze)
        assertTrue(receipt.sumMatches)
    }

    @Test
    fun `model lines - a receipt-wide discount is spread exactly to the grosz`() {
        val receipt = ModelLines.toReceipt(
            store = "",
            total = 9.00,
            lines = listOf(
                ModelLine("produkt", "A", 3.33),
                ModelLine("produkt", "B", 3.33),
                ModelLine("produkt", "C", 3.34),
                ModelLine("rabat_ogolny", "Rabat karta -10%", -1.00),
            ),
            source = ScanSource.GEMINI,
        )
        assertNull(receipt.store)
        assertEquals(900L, receipt.itemsSumGrosze)
        assertTrue(receipt.sumMatches)
        assertTrue(receipt.notes.toString(), receipt.notes.isNotEmpty())
    }

    @Test
    fun `discount distribution keeps the sum exact`() {
        val prices = mutableListOf(1000L, 1L, 2999L)
        ModelLines.distributeDiscount(prices, 401L)
        assertEquals(4000L - 401L, prices.sum())
    }

    @Test
    fun `OCR lines on one receipt row are joined left to right`() {
        val text = ReceiptRows.rebuild(
            listOf(
                OcrLine("5,49 C", 600f, 102f, 700f, 122f),
                OcrLine("Chleb", 10f, 100f, 120f, 120f),
                OcrLine("Mleko", 10f, 130f, 120f, 150f),
                OcrLine("3,20 C", 600f, 131f, 700f, 151f),
                OcrLine("   ", 300f, 131f, 320f, 151f),
            ),
        )
        assertEquals("Chleb   5,49 C\nMleko   3,20 C", text)
    }

    @Test
    fun `OCR lines that overlap horizontally stay separate rows`() {
        val text = ReceiptRows.rebuild(
            listOf(
                OcrLine("PARAGON FISKALNY", 100f, 100f, 500f, 120f),
                OcrLine("FISKALNY", 300f, 105f, 500f, 125f),
            ),
        )
        assertEquals("PARAGON FISKALNY\nFISKALNY", text)
    }

    @Test
    fun `correction request names both sums`() {
        val text = ReceiptPrompt.correctionRequest(itemsSum = 1234L, total = 1534L)
        assertTrue(text, "12,34" in text || "12.34" in text)
        assertTrue(text, "15,34" in text || "15.34" in text)
    }
}

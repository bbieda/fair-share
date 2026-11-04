package com.fairshare.scanner.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same cases as backend/src/test/kotlin/paragon/RuleBasedReceiptParserTest.kt (without the one that reads the
 * evaluation photos' OCR text, which stays out of this repo). The parser is a copy, so these must keep passing in both.
 */
class RuleBasedReceiptParserTest {

    /** Final item prices the app would show: products + separately listed deposits as one item. */
    private fun prices(r: RuleBasedReceiptParser.Result) =
        (r.items.map { it.priceGrosze } + listOfNotNull(r.separateDepositGrosze.takeIf { it > 0 })).sorted()

    @Test
    fun `discount, price after discount and quantity times unit price`() {
        val r = RuleBasedReceiptParser.parse(
            """
            PARAGON FISKALNY
            Tatra Jasne piwo   12 x3,99 41,88A
            OPUST Tatra Jasne piwo   -24,00
            23,88A
            Masło Ekstra   3 x4,99 14,97C
            OPUST Masło Ekstra   -8,17
            Hlong   6,80C
            Podsuma   30,68
            SUMA PLN   30,68
            """.trimIndent()
        )
        // 41,88 is an OCR error (12 × 3,99 = 47,88), but the printed price after discount is right
        assertEquals(listOf(2388L, 680L), r.items.map { it.priceGrosze })
        assertEquals("Tatra Jasne piwo (12 szt.)", r.items[0].name)
        assertEquals(3068L, r.totalGrosze)
    }

    @Test
    fun `checksum picks quantity times unit price when the line total is misread`() {
        val r = RuleBasedReceiptParser.parse(
            """
            PARAGON FISKALNY
            Chleb   1 x5,49 5,49C
            Woda   6 x2,99 12,94A
            SUMA PLN   23,43
            """.trimIndent()
        )
        assertEquals(listOf(549L, 1794L), r.items.map { it.priceGrosze })
        assertTrue(r.notes.toString(), r.notes.any { "17,94" in it })
    }

    @Test
    fun `two-line items, VAT letter read as digit, total printed above its label`() {
        val r = RuleBasedReceiptParser.parse(
            """
            ooGON FISKALNY
            A 5907652120443 TASMA MALARSKA MAKO 30 MM
            1SZT*7,98= 7,98 A
            SOK JABŁ/MIĘTA 750ML   1 x5,49 5,490
            Sprzed. opod. PTU A
            13,47
            SUMA PLN
            """.trimIndent()
        )
        assertEquals(listOf("Tasma malarska mako 30 mm", "Sok jabł/mięta 750ml"), r.items.map { it.name })
        assertEquals(listOf(798L, 549L), r.items.map { it.priceGrosze })
        assertEquals(1347L, r.totalGrosze)
    }

    @Test
    fun `a shop that is not on the list is named after the first line of the header`() {
        val r = RuleBasedReceiptParser.parse(
            """
            PYZIK
            SKLEP SPOŻYWCZO-PRZEMYSŁOWY
            30-444 Libertów, ul.Jana Pawła II 38
            NIP 9442247469   nr:217449
            PARAGON FISKALNY
            Pączek C   1szt x4,50 4,50C
            SUMA PLN   4,50
            """.trimIndent()
        )
        assertEquals("PYZIK", r.store)
        val alma = RuleBasedReceiptParser.parse("Firma Handlowo Usługowa ALMA Leszek Decyk\n10-521 Olsztyn ul.Partyzantów 12A\nPARAGON FISKALNY\nX 1 szt.*10.95 10.95A\nSUMA: PLN 10.95")
        assertTrue(alma.store.toString(), alma.store.orEmpty().contains("ALMA", ignoreCase = true))
    }

    @Test
    fun `rows merged by OCR on a skewed photo`() {
        val r = RuleBasedReceiptParser.parse(
            """
            PARAGON FISKALNY
            Precle 140g C   1szt X3,50
            Drożdżówka C   1kg x4,00 3,50C
            Drożdżówka C   1kg x1,55 4,00C 1,55C
            Henglein Ciasto C
            1szt x8,208,20C
            Pierogi Świeże C   0,714kg x34,50 24,63C
            Pierogi   41,88
            SPRZEDAŻ OPODATKOWANA C
            SUMA PLN
            ROZLICZENIE PŁATNOŚCI   41,88 PLN
            """.trimIndent()
        )
        // "X3,50" is a unit price (its total is on the next row), two totals on one row are two items,
        // "x8,208,20C" is "x8,20 8,20C", and 41,88 next to "Pierogi" is the sum of the items above it
        assertEquals(listOf(350L, 400L, 155L, 820L, 2463L), r.items.map { it.priceGrosze })
        assertEquals(4188L, r.totalGrosze)
    }

    @Test
    fun `returnable packaging under the total, with OCR mistakes in the section`() {
        val r = RuleBasedReceiptParser.parse(
            """
            PARAGON FISKALNY
            Pepsi 500ml DRS A   1szt x5,50 5,50A
            Chipsy C   1szt x9,20 9,20C
            SUMA PLN   14,70
            OPAKOWANIA ZWROTNE WYDANIA
            Opakowanie zwr.   1 x0,50 0,50
            OPANOWANTA ZHROTNE SUMA   0,50
            D0 ZAPLATY 15,20 PLN
            """.trimIndent()
        )
        assertEquals(50L, r.separateDepositGrosze)
        assertEquals(1520L, r.totalGrosze)
        assertEquals(listOf(550L, 920L), r.items.map { it.priceGrosze })
    }

    @Test
    fun `card terminal slip has a total but no items`() {
        val r = RuleBasedReceiptParser.parse(
            """
            CASTORAMA POLSKA Sp. z o.o.
            DOKUMENT NIEFISKALNY
            Do paragonu: 50237
            Sprzedaż:   PLN 131,78
            NIEFISKALNY
            """.trimIndent()
        )
        assertEquals("Castorama", r.store)
        assertEquals(13178L, r.totalGrosze)
        assertTrue(r.items.isEmpty())
    }
}

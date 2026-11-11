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
    fun `barcode after a misread VAT letter is dropped, a size on its own name line is kept, 0 inside words is O`() {
        val r = RuleBasedReceiptParser.parse(
            """
            PARAGON FISKALNY
            4 5411183157255 AKRYL SOUDAL EXPRESS 280 M
            1SZT#13,78= 13,78 A
            A 5059340989907 FOLIA 0CHRONNA MEBLOWA 4X5
            1SZT*15,98= 15,98 A
            A 3663602932451 TAŚMA MALARSKA 48 MM X 50
            1SZT*7,77= 7,77 A
            Sprzed. opod. PTU A
            SUMA PLN   37,53
            """.trimIndent()
        )
        assertEquals(listOf("Akryl soudal express 280 m", "Folia ochronna meblowa 4x5", "Taśma malarska 48 mm x 50"), r.items.map { it.name })
        assertEquals(listOf(1378L, 1598L, 777L), r.items.map { it.priceGrosze })
        assertEquals(3753L, r.totalGrosze)
    }

    private fun prices(text: String) = RuleBasedReceiptParser.parse(text.trimIndent()).let { r ->
        Triple(r.totalGrosze, r.items.map { it.priceGrosze }, r.separateDepositGrosze)
    }

    @Test
    fun `header broken by OCR spaces - the shop address is not an item`() {
        val r = prices(
            """
            PYZIK
            30-444 Libertów, ul.Jana Pawła |1 36
            NIP 9442247469   nr:224776
            PARAGON FI SKALNY
            JAJA WIEJSKIE 10szt C
            1szt x14.90 14,90C
            TWARÓG 275g C
            1szt x7,70 7,70C
            SPRZEDAZ OPODATKOWANA C   22,60
            SUMA PLN   22,60
            """
        )
        assertEquals(Triple(2260L, listOf(1490L, 770L), 0L), r)
    }

    @Test
    fun `Rossmann discount lines only say the price already includes the discount`() {
        val r = prices(
            """
            Rossmann SDP SP. Z O.O.
            PARAGON FISKALNY
            FUSSWOHL DEZODORANT   2 x5,99 11,98A
            Uwzgl. opust: -3,00 st. c 14,98
            BELLA PERFECTA ULTRA   1 x6,99 6,99C
            ALOUETTE CLASSIC   2 x11,99 23,98A
            Uuzgl. opust: -4,00 st. c 27,98
            SPRZEDAZ OPODATKOWANA A   35,96
            SPRZEDAZ OPODATKOWANA C   6,99
            SUMA PLN   42,95
            """
        )
        assertEquals(Triple(4295L, listOf(1198L, 699L, 2398L), 0L), r)
    }

    @Test
    fun `pharmacy - Bez recepty lines and the Sp op subtotal are not items`() {
        val r = prices(
            """
            APTEKA
            PARAGON FISKALNY
            Reparil N żel 40 g
            1op 39,99   39,99 B
            Bez recepty   39,99
            Hirudoid żel 100 g
            1op 54,99   54,99 B
            Bez recepty   54,99
            Sp.op.B   94,98
            PTU B= 8,00%   7,04
            SUMA PLN   94,98
            """
        )
        assertEquals(Triple(9498L, listOf(3999L, 5499L), 0L), r)
    }

    @Test
    fun `garbled SPRZEDAZ OPODATKOWANA ends the items, VAT digit after a split price`() {
        val r = prices(
            """
            PYZIK
            PARAGON FISKALNY
            Chupa Chups Lizak 15g A 1szt x4,50 4.50A
            CHUPA CHUPS GUMA BALONOWA WATERMELON 20G
            1szt x4,50 4 506
            SPRLEDAE PODA TKWEA &   9.0
            SNA PTU   1,56
            SUMA PLN   9,00
            """
        )
        assertEquals(Triple(900L, listOf(450L, 450L), 0L), r)
    }

    @Test
    fun `line total cut off by OCR - quantity times unit price, the checksum fixes a misread total`() {
        val r = prices(
            """
            Rossmann SDP SP. Z O.O.
            PARAGON FISKALNY
            FUSSWOHL DEZODORANT   26,99 11,98A
            Uwzgl. opust:-3,00 st. c 14,98
            BELLA PERFECTA ULTRA   1 x6,99 6,
            ALOUETTE CLASSIC   2 x11,99 23,94
            Uwzgl. opust: -4,00 st. c 27,98
            SPRZEDAZ OPODATKOWANA A   35,96
            SUMA PLN   42,95
            """
        )
        assertEquals(Triple(4295L, listOf(1198L, 699L, 2398L), 0L), r)
    }

    @Test
    fun `Biedronka - price after discount on the next name row, mangled line total`() {
        val r = prices(
            """
            Biedronka
            PARAGON FISKALNY
            Renifer32cmNiebieski A   2 x14,99 29,98A
            OPUST   -11,24A
            Renifer32cmBez A   18,74
            1 x14,99 t4,.99#
            OPUST   -5,62A
            9.37
            Jaja Ściółk L 10szt C   1 x11.49 11.49C
            OPUSTY ŁĄCZNIE   -16,86
            Sp: A=28,11 C=11,49
            SUMA PLN   39,60
            """
        )
        assertEquals(Triple(3960L, listOf(1874L, 937L, 1149L), 0L), r)
    }

    @Test
    fun `Carrefour - deposit printed between SUMA PLN and Do zaplaty`() {
        val r = prices(
            """
            Carrefour EXPRESS
            PARAGON FISKALNY
            C_JOG.D/P SKYR   1*8,99= 8,99 C
            A_NAPÓJ ENERG   1*7,99= 7,99 A
            C_DESER BAKOMA   1*5,99= 5,99 C
            Sprzed. opod. PTU A   7,99
            SUMA PLN   22,97
            Kaucja   0,50
            Płatność   KARTA   23,47
            Do   zapłaty   23,47
            NIEFISKALNY
            Dane: OPAKOWANIA ZWROTNE WYDANIA
            """
        )
        assertEquals(Triple(2347L, listOf(899L, 799L, 599L), 50L), r)
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

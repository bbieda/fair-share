package com.fairshare.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSplitTest {
    private val ala = 1L
    private val bartek = 2L
    private val celina = 3L
    private val everyone = setOf(ala, bartek, celina)

    @Test
    fun `each item is split between the people who share it`() {
        val result = ItemSplit.split(
            listOf(
                1200L to setOf(ala),           // only Ala
                900L to setOf(ala, bartek),    // 4,50 each
                600L to everyone,              // 2,00 each
            ),
        )
        assertEquals(listOf(mapOf(ala to 1200L), mapOf(ala to 450L, bartek to 450L), mapOf(ala to 200L, bartek to 200L, celina to 200L)), result.perItem)
        assertEquals(mapOf(ala to 1850L, bartek to 650L, celina to 200L), result.perUser)
    }

    @Test
    fun `leftover grosze rotate between people instead of always landing on the first one`() {
        // 1,00 zł for three, three times: 34+33+33 each time, the extra grosz goes to a different person every time
        val result = ItemSplit.split(List(3) { 100L to everyone })
        assertEquals(mapOf(ala to 100L, bartek to 100L, celina to 100L), result.perUser)
        assertEquals(listOf(ala, bartek, celina), result.perItem.map { shares -> shares.entries.single { it.value == 34L }.key })
    }

    @Test
    fun `every item and the whole receipt add up exactly`() {
        val prices = listOf(1874L, 937L, 1149L, 2187L, 1L, 2L, 999L, 1378L, 50L)
        val sharers = listOf(everyone, setOf(ala, bartek), setOf(celina), everyone, everyone, everyone, setOf(bartek, celina), everyone, setOf(ala))
        val result = ItemSplit.split(prices.zip(sharers))
        result.perItem.forEachIndexed { index, shares -> assertEquals(prices[index], shares.values.sum()) }
        assertEquals(prices.sum(), result.perUser.values.sum())
        assertTrue(result.perItem.all { shares -> shares.values.max() - shares.values.min() <= 1 })
    }

    @Test
    fun `everything shared by everyone equals an equal split of the total`() {
        val prices = listOf(1874L, 937L, 1149L, 2187L, 598L, 1L)
        val result = ItemSplit.split(prices.map { it to everyone })
        // 6746 gr for three: 2249 + 2249 + 2248, the leftover grosze on the lowest ids
        assertEquals(mapOf(ala to 2249L, bartek to 2249L, celina to 2248L), result.perUser)
    }

    @Test
    fun `an item nobody shares is left out`() {
        val result = ItemSplit.split(listOf(500L to emptySet(), 300L to setOf(bartek)))
        assertEquals(listOf(emptyMap(), mapOf(bartek to 300L)), result.perItem)
        assertEquals(mapOf(bartek to 300L), result.perUser)
    }

    @Test
    fun `a discount line is split like an item, with the sign kept`() {
        val result = ItemSplit.split(listOf(1000L to everyone, -100L to everyone))
        assertEquals(mapOf(ala to -34L, bartek to -33L, celina to -33L), result.perItem[1])
        assertEquals(900L, result.perUser.values.sum())
        assertEquals(mapOf(ala to 300L, bartek to 300L, celina to 300L), result.perUser)
    }
}

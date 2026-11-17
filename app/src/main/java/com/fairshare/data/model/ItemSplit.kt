package com.fairshare.data.model

import kotlin.math.abs

/**
 * Splitting a receipt by items ("kto co brał"): every item is divided equally between the people who share it.
 *
 * A price that does not divide evenly leaves 1–2 grosze over. They go to whoever has taken the fewest leftover grosze
 * so far (the lower user id on a tie), so over a whole receipt nobody pays more than 1 gr above a perfectly even split:
 * when everyone shares every item, the result is the same as splitting the total equally.
 */
object ItemSplit {

    class Result(
        /** For each item, in the order given: user id -> grosze of that item. Empty when nobody shares the item. */
        val perItem: List<Map<Long, Long>>,
        /** User id -> grosze over all items. Only people who share at least one item. */
        val perUser: Map<Long, Long>,
    )

    /** [items]: price in grosze (negative for a discount line) and the ids of the people who share it. */
    fun split(items: List<Pair<Long, Set<Long>>>): Result {
        val leftoverTaken = mutableMapOf<Long, Int>()
        val perUser = mutableMapOf<Long, Long>()
        val perItem = items.map { (price, sharedBy) ->
            if (sharedBy.isEmpty()) return@map emptyMap()
            val ids = sharedBy.sorted()
            val sign = if (price < 0) -1 else 1
            val base = abs(price) / ids.size
            var left = (abs(price) % ids.size).toInt()
            val share = ids.associateWith { base }.toMutableMap()
            // for a discount the extra grosz of discount goes to whoever has overpaid the most
            for (id in ids.sortedWith(compareBy({ sign * (leftoverTaken[it] ?: 0) }, { it }))) {
                if (left == 0) break
                share[id] = base + 1
                leftoverTaken[id] = (leftoverTaken[id] ?: 0) + sign
                left--
            }
            share.mapValues { sign * it.value }.also { signed ->
                signed.forEach { (id, grosze) -> perUser[id] = (perUser[id] ?: 0L) + grosze }
            }
        }
        return Result(perItem, perUser)
    }
}

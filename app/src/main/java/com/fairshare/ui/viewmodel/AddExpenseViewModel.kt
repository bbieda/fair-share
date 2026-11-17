package com.fairshare.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.AppSettings
import com.fairshare.data.ExpenseItemInput
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.data.model.ItemSplit
import com.fairshare.data.model.Money
import com.fairshare.scanner.ScannedReceipt
import com.fairshare.ui.notifyIfEnabled
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/** EQUAL: the same for everyone ticked. CUSTOM: an amount typed per person. ITEMS: by receipt items, who took what. */
enum class SplitMode { EQUAL, CUSTOM, ITEMS }

/** One receipt item in the form: scanned or typed, with the people who share it. */
data class ItemDraft(
    /** Stable id inside the form (not a database id). */
    val key: Long,
    val name: String,
    /** Grosze; negative for a discount line. */
    val price: Long,
    val sharedBy: Set<Long>,
)

/** "Nowy wydatek" form. Field values live here so they survive rotation. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddExpenseViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: FairShareRepository,
    private val app: Application,
    private val settings: AppSettings,
) : ViewModel() {

    private val initialGroupId: Long? = savedStateHandle.get<Long>(GROUP_ID_ARG)?.takeIf { it > 0 }
    private val groupIdFlow = MutableStateFlow(initialGroupId)

    var groups by mutableStateOf<List<GroupSummary>>(emptyList()); private set
    var groupsLoaded by mutableStateOf(false); private set
    var selectedGroupId by mutableStateOf(initialGroupId); private set
    var members by mutableStateOf<List<UserEntity>>(emptyList()); private set

    var amount by mutableStateOf("")
    var description by mutableStateOf("")
    var payerId by mutableStateOf<Long?>(null)
    var participantIds by mutableStateOf<Set<Long>>(emptySet()); private set
    var splitMode by mutableStateOf(SplitMode.EQUAL); private set
    /** Grosze typed per person when [splitMode] is [SplitMode.CUSTOM]. */
    var customShares by mutableStateOf<Map<Long, String>>(emptyMap()); private set

    var category by mutableStateOf<ExpenseCategory?>(null)
    var customCategory by mutableStateOf("")
    var isCustomCategory by mutableStateOf(false)
    var savedCustomCategories by mutableStateOf<List<String>>(emptyList()); private set
    var date by mutableStateOf(LocalDate.now())

    /** "Biedronka · 12 pozycji" after a scan, shown in the "Paragon" row. */
    var scanSummary by mutableStateOf<String?>(null); private set

    /** Receipt items: from the scanner or typed in "Podziel pozycje". Used (and saved) when [splitMode] is ITEMS. */
    var items by mutableStateOf<List<ItemDraft>>(emptyList()); private set
    /** Amount to pay printed on the scanned receipt, to warn when the items do not add up to it. */
    var receiptTotal by mutableStateOf<Long?>(null); private set
    private var nextItemKey = 1L

    var error by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set

    /** Group for which participants were last reset to "everyone". */
    private var participantsInitializedFor: Long? = null

    val selectedGroup: GroupSummary? get() = groups.firstOrNull { it.id == selectedGroupId }
    val payer: UserEntity? get() = members.firstOrNull { it.id == payerId }

    /** Equal split in the same order that is saved (participant id ascending). */
    val equalShares: Map<Long, Long>?
        get() {
            val total = Money.parse(amount) ?: return null
            val ids = participantIds.sorted()
            if (ids.isEmpty()) return null
            return ids.zip(Money.splitEqually(total, ids.size)).toMap()
        }

    val itemsSum: Long get() = items.sumOf { it.price }

    /** Items nobody is assigned to; the expense cannot be saved with any. */
    val unassignedItems: Int get() = items.count { it.sharedBy.isEmpty() }

    /** Who pays how much of each item and in total. */
    val itemSplit: ItemSplit.Result get() = ItemSplit.split(items.map { it.price to it.sharedBy })

    /** Receipt total minus the items: what is still missing (> 0) or too much (< 0). Null without a scanned total. */
    val itemsGap: Long? get() = receiptTotal?.let { it - itemsSum }?.takeIf { it != 0L && items.isNotEmpty() }

    val customShareSum: Long?
        get() {
            if (participantIds.isEmpty()) return null
            var sum = 0L
            for (id in participantIds) {
                sum += Money.parse(customShares[id].orEmpty()) ?: return null
            }
            return sum
        }

    init {
        viewModelScope.launch {
            settings.customCategories.collect { savedCustomCategories = it }
        }
        viewModelScope.launch {
            repository.groupSummaries.collect { all ->
                val active = all.filter { !it.archived }
                groups = active
                groupsLoaded = true
                if (active.none { it.id == selectedGroupId }) selectGroup(active.firstOrNull()?.id)
            }
        }
        viewModelScope.launch {
            groupIdFlow
                .flatMapLatest { id ->
                    if (id == null) flowOf(null to emptyList())
                    else repository.observeMembers(id).map { id to it }
                }
                .collect { (groupId, list) -> onMembersLoaded(groupId, list) }
        }
    }

    fun selectGroup(groupId: Long?) {
        if (groupId == selectedGroupId && groupIdFlow.value == groupId) return
        selectedGroupId = groupId
        groupIdFlow.value = groupId
        error = null
    }

    fun toggleParticipant(userId: Long) {
        participantIds = if (userId in participantIds) {
            customShares = customShares - userId
            participantIds - userId
        } else {
            participantIds + userId
        }
        error = null
    }

    @JvmName("changeSplitMode")
    fun setSplitMode(mode: SplitMode) {
        if (mode == splitMode) return
        // leaving the items: the amount is the scanned receipt's total again, not the sum of what was read as items
        if (splitMode == SplitMode.ITEMS) receiptTotal?.let { amount = Money.formatInput(it) }
        splitMode = mode
        when (mode) {
            SplitMode.CUSTOM -> prefillCustomShares()
            SplitMode.ITEMS -> syncAmountWithItems()
            SplitMode.EQUAL -> Unit
        }
        error = null
    }

    // ---------- Receipt items ----------

    fun toggleItemPerson(itemKey: Long, userId: Long) = updateItem(itemKey) { item ->
        item.copy(sharedBy = if (userId in item.sharedBy) item.sharedBy - userId else item.sharedBy + userId)
    }

    /** Long press on a person: the item is theirs alone. */
    fun assignItemOnlyTo(itemKey: Long, userId: Long) = updateItem(itemKey) { it.copy(sharedBy = setOf(userId)) }

    /** "Wszyscy": everyone in the group shares the item; when they already do, nobody does. */
    fun toggleItemEveryone(itemKey: Long) {
        val everyone = members.map { it.id }.toSet()
        updateItem(itemKey) { it.copy(sharedBy = if (it.sharedBy == everyone) emptySet() else everyone) }
    }

    /**
     * Adds an item ([itemKey] null) or changes its name and price. Returns an error to show under the fields,
     * or null when saved. A new item is shared by everyone.
     */
    fun saveItem(itemKey: Long?, name: String, priceInput: String, discount: Boolean = false): String? {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isEmpty()) return "Wpisz nazwę pozycji"
        val value = Money.parse(priceInput) ?: return "Wpisz poprawną cenę, np. 12,50"
        val price = if (discount) -value else value
        if (itemKey == null) {
            items = items + ItemDraft(nextItemKey++, cleanName, price, members.map { it.id }.toSet())
            itemsChanged()
        } else {
            updateItem(itemKey) { it.copy(name = cleanName, price = price) }
        }
        return null
    }

    fun removeItem(itemKey: Long) {
        items = items.filterNot { it.key == itemKey }
        itemsChanged()
    }

    /** One tap for a receipt whose items do not add up to its total: the difference becomes an item for everyone. */
    fun addBalancingItem() {
        val gap = itemsGap ?: return
        val name = if (gap > 0) "Inne (różnica do paragonu)" else "Rabat (różnica do paragonu)"
        items = items + ItemDraft(nextItemKey++, name, gap, members.map { it.id }.toSet())
        itemsChanged()
    }

    private fun updateItem(itemKey: Long, change: (ItemDraft) -> ItemDraft) {
        items = items.map { if (it.key == itemKey) change(it) else it }
        itemsChanged()
    }

    private fun itemsChanged() {
        if (splitMode == SplitMode.ITEMS) syncAmountWithItems()
        error = null
    }

    /** Split by items: the expense is exactly the sum of its items. */
    private fun syncAmountWithItems() {
        amount = if (itemsSum > 0) Money.formatInput(itemsSum) else ""
    }

    fun updateCustomShare(userId: Long, input: String) {
        customShares = customShares + (userId to cleanAmountInput(input))
        error = null
    }

    private fun prefillCustomShares() {
        val equal = equalShares ?: return
        customShares = equal.mapValues { (_, share) -> Money.formatInput(share) }
    }

    fun updateAmount(input: String) {
        amount = cleanAmountInput(input)
        error = null
    }

    /** Keeps digits and one decimal separator with at most 2 digits after it. */
    private fun cleanAmountInput(input: String): String {
        val filtered = input.filter { it.isDigit() || it == ',' || it == '.' }
        val separator = filtered.indexOfFirst { it == ',' || it == '.' }
        if (separator < 0) return filtered
        val whole = filtered.substring(0, separator)
        val fraction = filtered.substring(separator + 1).filter { it.isDigit() }.take(2)
        return whole + filtered[separator] + fraction
    }

    /**
     * Fills the form from a scanned receipt: the amount to pay, the store as the description (unless typed already),
     * "Zakupy" when no category was picked, and the receipt's items, shared by everyone until changed in
     * "Podziel pozycje". When the items add up to the receipt's total, the split switches to "Pozycje"; a receipt
     * read with a wrong sum keeps its total and the split chosen so far (its items are there to correct).
     */
    fun applyScan(receipt: ScannedReceipt, summary: String?) {
        val scanned = receipt.items.filter { it.priceGrosze != 0L }
        val total = receipt.totalGrosze ?: scanned.sumOf { it.priceGrosze }
        if (total <= 0) return
        val everyone = members.map { it.id }.toSet()
        items = scanned.map { ItemDraft(nextItemKey++, it.name, it.priceGrosze, everyone) }
        receiptTotal = total
        amount = Money.formatInput(total)
        if (description.isBlank()) description = receipt.store?.takeIf { it.isNotBlank() } ?: "Zakupy"
        if (category == null && !isCustomCategory) category = ExpenseCategory.GROCERIES
        scanSummary = summary
        splitMode = when {
            items.isNotEmpty() && itemsSum == total -> SplitMode.ITEMS
            splitMode == SplitMode.ITEMS -> SplitMode.EQUAL
            else -> splitMode
        }
        if (splitMode == SplitMode.CUSTOM) prefillCustomShares()
        error = null
    }

    private fun onMembersLoaded(groupId: Long?, list: List<UserEntity>) {
        members = list
        val ids = list.map { it.id }.toSet()
        participantIds = if (participantsInitializedFor != groupId) {
            participantsInitializedFor = groupId
            customShares = emptyMap()
            // another group, other people: every item is shared by everyone again
            items = items.map { it.copy(sharedBy = ids) }
            if (splitMode == SplitMode.CUSTOM) splitMode = SplitMode.EQUAL
            ids
        } else {
            customShares = customShares.filterKeys { it in ids }
            items = items.map { it.copy(sharedBy = it.sharedBy intersect ids) }
            participantIds intersect ids
        }
        if (payerId !in ids) payerId = list.firstOrNull { it.isMe }?.id ?: list.firstOrNull()?.id
    }

    fun save(onSaved: () -> Unit) {
        if (saving) return
        val groupId = selectedGroupId ?: return fail("Najpierw wybierz grupę")
        val byItems = splitMode == SplitMode.ITEMS
        if (byItems && items.isEmpty()) return fail("Dodaj pozycje paragonu albo wybierz inny podział")
        val value = Money.parse(amount)
            ?: return fail(if (byItems) "Suma pozycji musi być większa od zera" else "Wpisz poprawną kwotę, np. 12,50")
        val payer = payerId ?: return fail("Wybierz, kto płacił")
        if (!byItems && participantIds.isEmpty()) return fail("Zaznacz osoby, między które dzielisz wydatek")
        if (isCustomCategory && customCategory.isBlank()) return fail("Wpisz nazwę własnej kategorii")
        val shares = (if (byItems) resolveItemShares(value) else resolveShares(value)) ?: return
        val itemInputs = if (byItems) {
            val perItem = itemSplit.perItem
            items.mapIndexed { index, item -> ExpenseItemInput(item.name, item.price, perItem[index]) }
        } else {
            emptyList()
        }
        val categoryKey = when {
            isCustomCategory -> ExpenseCategory.storedKey(customCategory)
            else -> category?.name
        }

        saving = true
        viewModelScope.launch {
            try {
                repository.addExpense(
                    groupId = groupId,
                    payerId = payer,
                    amount = value,
                    description = description,
                    category = categoryKey,
                    date = date,
                    shares = shares,
                    items = itemInputs,
                )
                if (isCustomCategory) settings.addCustomCategory(customCategory)
                app.notifyIfEnabled(settings, "Nowy wydatek", "${description.ifBlank { "Wydatek" }} · ${Money.format(value)}")
                onSaved()
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Nie udało się zapisać wydatku"
            } catch (e: Exception) {
                error = "Nie udało się zapisać wydatku. Spróbuj ponownie."
            } finally {
                saving = false
            }
        }
    }

    private fun resolveShares(total: Long): Map<Long, Long>? {
        if (splitMode == SplitMode.EQUAL) {
            return equalShares ?: fail("Wpisz poprawną kwotę, np. 12,50").let { null }
        }
        val parsed = mutableMapOf<Long, Long>()
        for (id in participantIds) {
            val share = Money.parse(customShares[id].orEmpty())
                ?: return fail("Uzupełnij kwotę przy każdej osobie").let { null }
            parsed[id] = share
        }
        val sum = parsed.values.sum()
        if (sum != total) {
            return fail("Udziały (${Money.format(sum)}) muszą sumować się do ${Money.format(total)}").let { null }
        }
        return parsed
    }

    /** Shares for a split by items: what each person's items add up to. */
    private fun resolveItemShares(total: Long): Map<Long, Long>? {
        val unassigned = unassignedItems
        if (unassigned > 0) {
            return fail("Pozycje bez przypisanej osoby: $unassigned. Otwórz „Podziel pozycje”.").let { null }
        }
        val perUser = itemSplit.perUser
        val negative = perUser.entries.firstOrNull { it.value < 0 }
        if (negative != null) {
            val name = members.firstOrNull { it.id == negative.key }?.let { if (it.isMe) "Ty" else it.name } ?: "Ktoś"
            return fail("$name ma w pozycjach więcej rabatu niż zakupów. Popraw przypisanie rabatu.").let { null }
        }
        val shares = perUser.filterValues { it > 0 }
        if (shares.values.sum() != total) return fail("Pozycje nie sumują się do kwoty wydatku").let { null }
        return shares
    }

    private fun fail(message: String) {
        error = message
    }

    companion object {
        const val GROUP_ID_ARG = "groupId"
    }
}
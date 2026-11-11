package com.fairshare.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.AppSettings
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.data.model.Money
import com.fairshare.ui.notifyIfEnabled
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

enum class SplitMode { EQUAL, CUSTOM }

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
        splitMode = mode
        if (mode == SplitMode.CUSTOM) prefillCustomShares()
        error = null
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
     * Fills the form from a scanned receipt: the amount to pay, the store as the description (unless typed already)
     * and "Zakupy" when no category was picked. The expense is still split equally; splitting by item is next.
     */
    fun applyScan(totalGrosze: Long, store: String?, summary: String?) {
        if (totalGrosze <= 0) return
        amount = BigDecimal.valueOf(totalGrosze).movePointLeft(2).setScale(2).toPlainString().replace('.', ',')
        if (description.isBlank()) description = store?.takeIf { it.isNotBlank() } ?: "Zakupy"
        if (category == null && !isCustomCategory) category = ExpenseCategory.GROCERIES
        scanSummary = summary
        error = null
    }

    private fun onMembersLoaded(groupId: Long?, list: List<UserEntity>) {
        members = list
        val ids = list.map { it.id }.toSet()
        participantIds = if (participantsInitializedFor != groupId) {
            participantsInitializedFor = groupId
            customShares = emptyMap()
            splitMode = SplitMode.EQUAL
            ids
        } else {
            customShares = customShares.filterKeys { it in ids }
            participantIds intersect ids
        }
        if (payerId !in ids) payerId = list.firstOrNull { it.isMe }?.id ?: list.firstOrNull()?.id
    }

    fun save(onSaved: () -> Unit) {
        if (saving) return
        val groupId = selectedGroupId ?: return fail("Najpierw wybierz grupę")
        val value = Money.parse(amount) ?: return fail("Wpisz poprawną kwotę, np. 12,50")
        val payer = payerId ?: return fail("Wybierz, kto płacił")
        if (participantIds.isEmpty()) return fail("Zaznacz osoby, między które dzielisz wydatek")
        if (isCustomCategory && customCategory.isBlank()) return fail("Wpisz nazwę własnej kategorii")
        val shares = resolveShares(value) ?: return
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

    private fun fail(message: String) {
        error = message
    }

    companion object {
        const val GROUP_ID_ARG = "groupId"
    }
}
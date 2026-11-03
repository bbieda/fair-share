package com.fairshare.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.data.model.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/** "Nowy wydatek" form. Field values live here so they survive rotation. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddExpenseViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: FairShareRepository,
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
    var category by mutableStateOf<ExpenseCategory?>(null)
    var date by mutableStateOf(LocalDate.now())

    /** "Biedronka · 12 pozycji" after a scan, shown in the "Paragon" row. */
    var scanSummary by mutableStateOf<String?>(null); private set

    var error by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set

    /** Group for which participants were last reset to "everyone". */
    private var participantsInitializedFor: Long? = null

    val selectedGroup: GroupSummary? get() = groups.firstOrNull { it.id == selectedGroupId }
    val payer: UserEntity? get() = members.firstOrNull { it.id == payerId }

    /** Preview of the biggest single share, e.g. 100 zł / 3 -> 33,34 zł. */
    val sharePreview: Long?
        get() {
            val total = Money.parse(amount) ?: return null
            if (participantIds.isEmpty()) return null
            return Money.splitEqually(total, participantIds.size).first()
        }

    init {
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
        participantIds = if (userId in participantIds) participantIds - userId else participantIds + userId
        error = null
    }

    fun updateAmount(input: String) {
        amount = input.filter { it.isDigit() || it == ',' || it == '.' }
        error = null
    }

    /**
     * Fills the form from a scanned receipt: the amount to pay, the store as the description (unless typed already)
     * and "Zakupy" when no category was picked. The expense is still split equally; splitting by item is next.
     */
    fun applyScan(totalGrosze: Long, store: String?, summary: String?) {
        if (totalGrosze <= 0) return
        amount = BigDecimal.valueOf(totalGrosze).movePointLeft(2).setScale(2).toPlainString().replace('.', ',')
        if (description.isBlank()) description = store?.takeIf { it.isNotBlank() } ?: "Zakupy"
        if (category == null) category = ExpenseCategory.GROCERIES
        scanSummary = summary
        error = null
    }

    private fun onMembersLoaded(groupId: Long?, list: List<UserEntity>) {
        members = list
        val ids = list.map { it.id }.toSet()
        participantIds = if (participantsInitializedFor != groupId) {
            participantsInitializedFor = groupId
            ids
        } else {
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

        saving = true
        viewModelScope.launch {
            try {
                repository.addExpense(
                    groupId = groupId,
                    payerId = payer,
                    amount = value,
                    description = description,
                    category = category,
                    date = date,
                    participantIds = participantIds,
                )
                onSaved()
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Nie udało się zapisać wydatku"
            } finally {
                saving = false
            }
        }
    }

    private fun fail(message: String) {
        error = message
    }
}

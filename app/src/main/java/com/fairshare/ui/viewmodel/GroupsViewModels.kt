package com.fairshare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.AppSettings
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.ExpenseListRow
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.MemberBalance
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.InviteCodes
import com.fairshare.ui.notifyIfEnabled
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Grupy" tab: list of groups + creating a new one. */
class GroupsViewModel(
    private val repository: FairShareRepository,
    private val app: Application,
    private val settings: AppSettings,
) : ViewModel() {

    /** null until the first read from the database finishes. */
    val groups: StateFlow<List<GroupSummary>?> = repository.groupSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createGroup(name: String, onCreated: (groupId: Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createGroup(name)
            app.notifyIfEnabled(settings, "Nowa grupa", name.trim())
            onCreated(id)
        }
    }

    fun openByInviteCode(raw: String, onOpened: (Long) -> Unit, onError: (String) -> Unit) {
        val id = InviteCodes.parse(raw)
        if (id == null) {
            onError("Nie rozpoznaję kodu. Przykład: FS-A")
            return
        }
        viewModelScope.launch {
            val group = repository.findGroup(id)
            if (group == null) {
                onError("Na tym telefonie nie ma grupy o tym kodzie.")
                return@launch
            }
            if (group.archived) repository.setArchived(id, false)
            onOpened(id)
        }
    }
}

data class GroupUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val archived: Boolean = false,
    val members: List<MemberBalance> = emptyList(),
    val expenses: List<ExpenseListRow> = emptyList(),
    /** People known from other groups who are not in this one yet. */
    val contacts: List<UserEntity> = emptyList(),
) {
    val myBalance: Long get() = members.firstOrNull { it.isMe }?.balance ?: 0L
}

/** Single group screen: members, balances, expenses, adding people. */
class GroupViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: FairShareRepository,
) : ViewModel() {

    val groupId: Long = checkNotNull(savedStateHandle.get<Long>(GROUP_ID_ARG)) { "Missing $GROUP_ID_ARG" }

    val state: StateFlow<GroupUiState> = combine(
        repository.observeGroup(groupId),
        repository.observeMemberBalances(groupId),
        repository.observeGroupExpenses(groupId),
        repository.allUsers,
    ) { group, members, expenses, users ->
        val memberIds = members.map { it.userId }.toSet()
        GroupUiState(
            loaded = true,
            name = group?.name.orEmpty(),
            archived = group?.archived == true,
            members = members,
            expenses = expenses,
            contacts = users.filter { !it.isMe && it.id !in memberIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupUiState())

    fun addMemberByName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addMemberByName(groupId, name) }
    }

    fun addContact(userId: Long) {
        viewModelScope.launch { repository.addMember(groupId, userId) }
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch { repository.deleteExpense(expenseId) }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameGroup(groupId, name) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { repository.setArchived(groupId, archived) }
    }
}

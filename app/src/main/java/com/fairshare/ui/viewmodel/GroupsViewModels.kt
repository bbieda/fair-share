package com.fairshare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.AppSettings
import com.fairshare.data.ExpenseDetails
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.ExpenseListRow
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.MemberBalance
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.InviteCodes
import com.fairshare.data.sync.JoinPreview
import com.fairshare.data.sync.SyncManager
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
    private val sync: SyncManager,
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

    /** Asks the server which group a code belongs to and who in it has no account yet. Needs the internet. */
    fun previewJoin(raw: String, onPreview: (code: String, JoinPreview) -> Unit, onError: (String) -> Unit) {
        val code = InviteCodes.normalize(raw)
        if (code == null) {
            onError("Kod ma 8 znaków, np. K7MQ-2XPA")
            return
        }
        viewModelScope.launch {
            try {
                onPreview(code, sync.joinPreview(code))
            } catch (e: Exception) {
                onError(e.toUserMessage())
            }
        }
    }

    /** Joins (as the person [claimPersonUid] added by name, when given), downloads the group and opens it. */
    fun join(code: String, claimPersonUid: String?, onJoined: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val groupId = sync.join(code, claimPersonUid)
                repository.findGroup(groupId)?.takeIf { it.archived }?.let { repository.setArchived(groupId, false) }
                onJoined(groupId)
            } catch (e: Exception) {
                onError(e.toUserMessage())
            }
        }
    }
}

data class GroupUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val archived: Boolean = false,
    /** null until the group has been sent to the server once. */
    val inviteCode: String? = null,
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
            inviteCode = group?.inviteCode,
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

    /** Who owes what of one expense and its receipt items, for the details dialog. */
    suspend fun expenseDetails(expenseId: Long): ExpenseDetails = repository.expenseDetails(expenseId)

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameGroup(groupId, name) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { repository.setArchived(groupId, archived) }
    }
}

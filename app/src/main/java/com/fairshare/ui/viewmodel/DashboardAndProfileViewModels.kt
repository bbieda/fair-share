package com.fairshare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.CategoryTotal
import com.fairshare.data.db.PersonBalance
import com.fairshare.data.db.UserEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class DashboardUiState(
    val people: List<PersonBalance> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val categories: List<CategoryTotal> = emptyList(),
) {
    /** Sum of what others owe me. */
    val owedToMe: Long get() = people.sumOf { it.balance.coerceAtLeast(0) }

    /** Sum of what I owe others (positive number). */
    val iOwe: Long get() = people.sumOf { (-it.balance).coerceAtLeast(0) }

    val net: Long get() = owedToMe - iOwe

    /** My share of this month's expenses. */
    val monthShare: Long get() = categories.sumOf { it.total }
}

/** "Pulpit" tab: balances with people and this month's spending. */
class DashboardViewModel(repository: FairShareRepository) : ViewModel() {
    private val month = YearMonth.now()

    val state: StateFlow<DashboardUiState> = combine(
        repository.personBalances,
        repository.observeMyCategoryTotals(month),
    ) { people, categories ->
        DashboardUiState(people = people, month = month, categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState(month = month))
}

/** "Profil i ustawienia": my name and wiping data. */
class ProfileViewModel(private val repository: FairShareRepository) : ViewModel() {
    val me: StateFlow<UserEntity?> = repository.me
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        viewModelScope.launch { repository.renameMe(name) }
    }

    fun deleteAllData() {
        viewModelScope.launch { repository.deleteAllData() }
    }
}

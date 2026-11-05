package com.fairshare.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.ActivityLogEntity
import com.fairshare.data.db.CategoryTotal
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.PersonBalance
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.Money
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
    val owedToMe: Long get() = people.sumOf { it.balance.coerceAtLeast(0) }
    val iOwe: Long get() = people.sumOf { (-it.balance).coerceAtLeast(0) }
    val net: Long get() = owedToMe - iOwe
    val monthShare: Long get() = categories.sumOf { it.total }
}

class DashboardViewModel(repository: FairShareRepository) : ViewModel() {
    private val month = YearMonth.now()

    val state: StateFlow<DashboardUiState> = combine(
        repository.personBalances,
        repository.observeMyCategoryTotals(month),
    ) { people, categories ->
        DashboardUiState(people = people, month = month, categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState(month = month))
}

class ProfileViewModel(private val repository: FairShareRepository) : ViewModel() {
    val me: StateFlow<UserEntity?> = repository.me
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        viewModelScope.launch { repository.renameMe(name) }
    }

    fun updatePaymentDetails(details: String) {
        viewModelScope.launch { repository.updatePaymentDetails(details) }
    }

    fun updateCurrency(currency: String) {
        viewModelScope.launch { repository.updateCurrency(currency) }
    }

    fun updatePhotoUri(photoUri: String) {
        viewModelScope.launch { repository.updatePhotoUri(photoUri) }
    }

    fun updatePhotoBitmap(bitmap: Bitmap) {
        // Obraz przekazany tymczasowo w pamięci aplikacji
        viewModelScope.launch { repository.updatePhotoUri("bitmap_cache") }
    }

    fun deleteAllData() {
        viewModelScope.launch { repository.deleteAllData() }
    }
}

class RozliczViewModel(private val repository: FairShareRepository) : ViewModel() {
    val personBalances: StateFlow<List<PersonBalance>> = repository.personBalances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<GroupSummary>> = repository.groupSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun settleUp(person: PersonBalance) {
        viewModelScope.launch {
            val activeGroup = groups.value.firstOrNull() ?: return@launch
            val amountToSettle = kotlin.math.abs(person.balance)
            repository.settleUp(
                targetUserId = person.userId,
                amountGrosze = amountToSettle,
                groupId = activeGroup.id
            )
        }
    }
}

data class StatystykiUiState(
    val month: YearMonth = YearMonth.now(),
    val monthTotal: Long = 0L,
    val categories: List<CategoryTotal> = emptyList(),
    val monthlyHistory: List<Pair<String, Long>> = emptyList()
)

class StatystykiViewModel(private val repository: FairShareRepository) : ViewModel() {
    private val currentMonth = YearMonth.now()

    val state: StateFlow<StatystykiUiState> = combine(
        repository.observeTotalSpentInRange(currentMonth),
        repository.observeMyCategoryTotals(currentMonth)
    ) { total, categories ->
        StatystykiUiState(
            month = currentMonth,
            monthTotal = total,
            categories = categories,
            monthlyHistory = listOf(
                "maj" to 0L, "cze" to 0L, "lip" to 0L,
                "sie" to 0L, "wrz" to 0L, "paź" to total
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatystykiUiState())
}

class AktywnoscViewModel(private val repository: FairShareRepository) : ViewModel() {
    val logs: StateFlow<List<ActivityLogEntity>> = repository.activityLogsList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
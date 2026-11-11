package com.fairshare.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.AppSettings
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.ActivityItem
import com.fairshare.data.db.CategoryTotal
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.PersonBalance
import com.fairshare.data.db.SettlementLine
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.Money
import com.fairshare.ui.ProfilePhotos
import com.fairshare.ui.components.monthShortName
import com.fairshare.ui.notifyIfEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
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

class ProfileViewModel(
    private val app: Application,
    private val repository: FairShareRepository,
    val settings: AppSettings,
) : ViewModel() {
    val me: StateFlow<UserEntity?> = repository.me
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        viewModelScope.launch { repository.renameMe(name) }
    }

    fun updatePaymentDetails(details: String) {
        viewModelScope.launch { repository.updatePaymentDetails(details) }
    }

    fun updateCurrency(currency: String) {
        viewModelScope.launch {
            repository.updateCurrency(currency)
            Money.currencyCode = currency
        }
    }

    /** Saves a picture from the gallery or from the camera as the profile photo. */
    fun savePhoto(uri: Uri, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val stored = withContext(Dispatchers.IO) { ProfilePhotos.save(app, uri) }
                repository.updatePhotoUri(stored)
            } catch (e: Exception) {
                onError(e.message ?: "Nie udało się zapisać zdjęcia")
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ProfilePhotos.delete(app) }
            repository.updatePhotoUri(null)
        }
    }

    /** Empty file the camera app can write the new profile photo into (shared through FileProvider). */
    fun newCameraUri(): Uri {
        val dir = File(app.cacheDir, "avatars").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    fun setTheme(mode: String) = settings.setTheme(mode)

    fun setNotifications(enabled: Boolean) = settings.setNotifications(enabled)

    fun addCustomCategory(name: String) = settings.addCustomCategory(name)

    fun removeCustomCategory(name: String) = settings.removeCustomCategory(name)

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAllData()
            settings.resetData()
            withContext(Dispatchers.IO) { ProfilePhotos.delete(app) }
        }
    }

    fun exportCsv(onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val csv = repository.buildExpensesCsv()
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, "fairshare.csv")
                    file.writeText(csv)
                    FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Fair Share — wydatki")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(Intent.createChooser(share, "Eksport CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                onError(e.message ?: "Nie udało się wyeksportować danych")
            }
        }
    }
}

class RozliczViewModel(
    private val app: Application,
    private val repository: FairShareRepository,
    private val settings: AppSettings,
) : ViewModel() {
    val me: StateFlow<UserEntity?> = repository.me
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lines: StateFlow<List<SettlementLine>> = repository.settlementLines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Records a full or partial repayment. [onError] gets a message the screen can show. */
    fun settle(line: SettlementLine, amountGrosze: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.settleUp(line.groupId, line.userId, amountGrosze)
                val title = if (line.balance < 0) "Spłata zapisana" else "Rozliczenie zapisane"
                app.notifyIfEnabled(settings, title, "${line.name} · ${Money.format(amountGrosze)}")
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Nie udało się zapisać spłaty")
            }
        }
    }

    fun savePaymentDetails(userId: Long, details: String) {
        viewModelScope.launch { repository.updatePersonPaymentDetails(userId, details) }
    }
}

enum class SpendScope { MY_SHARE, ALL }

data class StatystykiUiState(
    val month: YearMonth = YearMonth.now(),
    val monthTotal: Long = 0L,
    val categories: List<CategoryTotal> = emptyList(),
    val monthlyHistory: List<Pair<String, Long>> = emptyList(),
    val groups: List<GroupSummary> = emptyList(),
    val selectedGroupId: Long = -1L,
    val scopeMine: Boolean = true,
    val budgetGrosze: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatystykiViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: FairShareRepository,
    private val settings: AppSettings,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val groupId = MutableStateFlow(savedStateHandle.get<Long>("groupId")?.takeIf { it > 0 } ?: -1L)
    private val scope = MutableStateFlow(SpendScope.MY_SHARE)

    val monthOptions: List<YearMonth> = (0 until 18).map { YearMonth.now().minusMonths(it.toLong()) }

    val state: StateFlow<StatystykiUiState> = combine(
        month,
        groupId,
        scope,
        repository.groupSummaries,
        settings.monthlyBudgetGrosze,
    ) { selectedMonth, selectedGroup, selectedScope, groups, budget ->
        Filter(selectedMonth, selectedGroup, selectedScope, groups, budget)
    }.flatMapLatest { filter ->
        val from = filter.month.minusMonths(5).atDay(1).toEpochDay()
        val to = filter.month.atEndOfMonth().toEpochDay()
        val days = if (filter.scope == SpendScope.MY_SHARE) {
            repository.observeMyShareByDay(from, to, filter.groupId)
        } else {
            repository.observeAllSpendingByDay(from, to, filter.groupId)
        }
        val categories = if (filter.scope == SpendScope.MY_SHARE) {
            repository.observeMyCategoryTotals(filter.month, filter.groupId)
        } else {
            repository.observeAllCategoryTotals(filter.month, filter.groupId)
        }
        combine(days, categories) { dayTotals, categoryTotals ->
            val monthStart = filter.month.atDay(1).toEpochDay()
            val monthEnd = filter.month.atEndOfMonth().toEpochDay()
            val history = (5 downTo 0).map { offset ->
                val itemMonth = filter.month.minusMonths(offset.toLong())
                val start = itemMonth.atDay(1).toEpochDay()
                val end = itemMonth.atEndOfMonth().toEpochDay()
                val sum = dayTotals.filter { it.date in start..end }.sumOf { it.total }
                monthShortName(itemMonth.atDay(1)) to sum
            }
            StatystykiUiState(
                month = filter.month,
                monthTotal = dayTotals.filter { it.date in monthStart..monthEnd }.sumOf { it.total },
                categories = categoryTotals,
                monthlyHistory = history,
                groups = filter.groups,
                selectedGroupId = filter.groupId,
                scopeMine = filter.scope == SpendScope.MY_SHARE,
                budgetGrosze = filter.budget,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatystykiUiState())

    fun selectMonth(value: YearMonth) {
        month.value = value
    }

    fun selectGroup(id: Long) {
        groupId.value = id
    }

    fun toggleScope() {
        scope.value = if (scope.value == SpendScope.MY_SHARE) SpendScope.ALL else SpendScope.MY_SHARE
    }

    fun setBudget(grosze: Long) = settings.setMonthlyBudget(grosze)

    private data class Filter(
        val month: YearMonth,
        val groupId: Long,
        val scope: SpendScope,
        val groups: List<GroupSummary>,
        val budget: Long,
    )
}

class AktywnoscViewModel(private val repository: FairShareRepository) : ViewModel() {
    val logs: StateFlow<List<ActivityItem>> = repository.activityItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unread: StateFlow<Int> = repository.unreadActivityCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markAllRead() {
        viewModelScope.launch { repository.markAllActivitySeen() }
    }

    fun markRead(id: Long) {
        viewModelScope.launch { repository.markActivitySeen(id) }
    }
}
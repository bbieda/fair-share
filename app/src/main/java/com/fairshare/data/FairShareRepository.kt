package com.fairshare.data

import androidx.room.withTransaction
import com.fairshare.data.db.ActivityLogEntity
import com.fairshare.data.db.CategoryTotal
import com.fairshare.data.db.DayTotal
import com.fairshare.data.db.ExpenseEntity
import com.fairshare.data.db.ExpenseListRow
import com.fairshare.data.db.ExpenseShareEntity
import com.fairshare.data.db.FairShareDatabase
import com.fairshare.data.db.GroupEntity
import com.fairshare.data.db.GroupMemberEntity
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.MemberBalance
import com.fairshare.data.db.PersonBalance
import com.fairshare.data.db.SettlementLine
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.data.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class FairShareRepository(private val db: FairShareDatabase) {
    private val users = db.userDao()
    private val groups = db.groupDao()
    private val expenses = db.expenseDao()
    private val activityLogs = db.activityLogDao()

    // ---------- Users & Profile ----------

    val me: Flow<UserEntity> = users.observeMe().filterNotNull()
    private val meId: Flow<Long> = me.map { it.id }.distinctUntilChanged()
    val allUsers: Flow<List<UserEntity>> = users.observeAll()

    suspend fun requireMeId(): Long =
        users.getMe()?.id ?: users.insert(UserEntity(name = FairShareDatabase.DEFAULT_MY_NAME, isMe = true))

    suspend fun renameMe(name: String) {
        val clean = name.cleanName() ?: return
        val meUser = users.getMe() ?: return
        users.update(meUser.copy(name = clean))
        logActivity("PROFILE_UPDATE", "Zmieniono imię na $clean")
    }

    suspend fun updatePaymentDetails(details: String) {
        val meUser = users.getMe() ?: return
        users.update(meUser.copy(paymentDetails = details.trim()))
        logActivity("PROFILE_UPDATE", "Zaktualizowano dane do spłat")
    }

    suspend fun updateCurrency(currency: String) {
        val meUser = users.getMe() ?: return
        users.update(meUser.copy(defaultCurrency = currency))
        logActivity("PROFILE_UPDATE", "Zmieniono domyślną walutę na $currency")
    }

    suspend fun updatePhotoUri(photoUri: String?) {
        val meUser = users.getMe() ?: return
        users.update(meUser.copy(photoUri = photoUri))
        logActivity("PROFILE_UPDATE", "Zaktualizowano zdjęcie profilowe")
    }

    // ---------- Activity Logs ----------

    val activityLogsList: Flow<List<ActivityLogEntity>> = activityLogs.observeAll()

    suspend fun logActivity(actionType: String, description: String, groupId: Long? = null) {
        activityLogs.insert(
            ActivityLogEntity(
                groupId = groupId,
                actionType = actionType,
                description = description,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // ---------- Groups ----------

    val groupSummaries: Flow<List<GroupSummary>> = meId.flatMapLatest { groups.observeGroupSummaries(it) }

    fun observeGroup(groupId: Long): Flow<GroupEntity?> = groups.observeGroup(groupId)
    fun observeMembers(groupId: Long): Flow<List<UserEntity>> = groups.observeMembers(groupId)
    fun observeMemberBalances(groupId: Long): Flow<List<MemberBalance>> = groups.observeMemberBalances(groupId)

    suspend fun createGroup(name: String): Long {
        val clean = requireNotNull(name.cleanName()) { "Nazwa grupy nie może być pusta" }
        return db.withTransaction {
            val groupId = groups.insert(GroupEntity(name = clean))
            groups.insertMember(GroupMemberEntity(groupId = groupId, userId = requireMeId()))
            logActivity("CREATE_GROUP", "Utworzono grupę „$clean”", groupId)
            groupId
        }
    }

    suspend fun addMemberByName(groupId: Long, name: String): Long {
        val clean = requireNotNull(name.cleanName()) { "Imię nie może być puste" }
        return db.withTransaction {
            val userId = users.findOtherByName(clean)?.id ?: users.insert(UserEntity(name = clean))
            groups.insertMember(GroupMemberEntity(groupId = groupId, userId = userId))
            logActivity("ADD_MEMBER", "Dodano użytkownika $clean do grupy", groupId)
            userId
        }
    }

    suspend fun addMember(groupId: Long, userId: Long) {
        db.withTransaction {
            if (userId in groups.getMemberIds(groupId)) return@withTransaction
            groups.insertMember(GroupMemberEntity(groupId = groupId, userId = userId))
            val name = users.getById(userId)?.name ?: "osobę"
            logActivity("ADD_MEMBER", "Dodano $name do grupy", groupId)
        }
    }

    suspend fun findGroup(groupId: Long): GroupEntity? = groups.getGroup(groupId)

    suspend fun renameGroup(groupId: Long, name: String) {
        val clean = requireNotNull(name.cleanName()) { "Nazwa grupy nie może być pusta" }
        val group = groups.getGroup(groupId) ?: return
        groups.update(group.copy(name = clean))
        logActivity("RENAME_GROUP", "Zmieniono nazwę grupy na „$clean”", groupId)
    }

    suspend fun setArchived(groupId: Long, archived: Boolean) {
        val group = groups.getGroup(groupId) ?: return
        if (group.archived == archived) return
        groups.update(group.copy(archived = archived))
        val action = if (archived) "Zarchiwizowano grupę „${group.name}”" else "Przywrócono grupę „${group.name}”"
        logActivity("ARCHIVE_GROUP", action, groupId)
    }

    // ---------- Expenses & Settlements ----------

    fun observeGroupExpenses(groupId: Long): Flow<List<ExpenseListRow>> =
        meId.flatMapLatest { expenses.observeGroupExpenses(groupId, it) }

    /**
     * [shares] maps participant id to grosze. The values must be positive and sum to [amount].
     * [category] is an [ExpenseCategory] name or a custom label.
     */
    suspend fun addExpense(
        groupId: Long,
        payerId: Long,
        amount: Long,
        description: String,
        category: String?,
        date: LocalDate,
        shares: Map<Long, Long>,
        isSettlement: Boolean = false,
    ): Long {
        require(amount > 0) { "Kwota musi być większa od zera" }
        require(shares.isNotEmpty()) { "Wybierz przynajmniej jedną osobę do podziału" }
        require(shares.values.all { it > 0 }) { "Udział każdej osoby musi być większy od zera" }
        require(shares.values.sum() == amount) { "Udziały muszą sumować się do kwoty wydatku" }

        return db.withTransaction {
            val members = groups.getMemberIds(groupId).toSet()
            require(payerId in members) { "Płacący nie należy do grupy" }
            require(members.containsAll(shares.keys)) { "Ktoś z podziału nie należy do grupy" }

            val cleanCategory = category?.trim()?.ifEmpty { null }
            val cleanDesc = description.trim().ifEmpty {
                ExpenseCategory.fromKey(cleanCategory)?.label ?: cleanCategory ?: if (isSettlement) "Rozliczenie" else "Wydatek"
            }
            val expenseId = expenses.insertExpense(
                ExpenseEntity(
                    groupId = groupId,
                    payerId = payerId,
                    amount = amount,
                    description = cleanDesc,
                    category = cleanCategory,
                    date = date.toEpochDay(),
                    isSettlement = isSettlement,
                )
            )
            expenses.insertShares(
                shares.map { (userId, share) ->
                    ExpenseShareEntity(expenseId = expenseId, userId = userId, amount = share)
                }
            )
            val action = if (isSettlement) "SETTLEMENT" else "ADD_EXPENSE"
            val text = if (isSettlement) {
                "Zarejestrowano spłatę na kwotę ${Money.format(amount)}"
            } else {
                "Dodano wydatek „$cleanDesc” na kwotę ${Money.format(amount)}"
            }
            logActivity(action, text, groupId)
            expenseId
        }
    }

    /**
     * Records a repayment inside one group.
     * [signedBalance] is the current balance with that person in the group (> 0 they owe me).
     * [amountGrosze] may be the full debt or a part of it.
     */
    suspend fun settleUp(groupId: Long, otherUserId: Long, signedBalance: Long, amountGrosze: Long) {
        require(amountGrosze > 0) { "Kwota rozliczenia musi być większa od zera" }
        require(abs(signedBalance) >= amountGrosze) { "Kwota jest większa niż saldo w tej grupie" }
        val myId = requireMeId()
        val iOweThem = signedBalance < 0
        val payerId = if (iOweThem) myId else otherUserId
        val shareUserId = if (iOweThem) otherUserId else myId
        val otherName = users.getById(otherUserId)?.name ?: "osobę"
        val description = if (iOweThem) "Spłata dla $otherName" else "Spłata od $otherName"
        addExpense(
            groupId = groupId,
            payerId = payerId,
            amount = amountGrosze,
            description = description,
            category = null,
            date = LocalDate.now(),
            shares = mapOf(shareUserId to amountGrosze),
            isSettlement = true,
        )
    }

    suspend fun deleteExpense(expenseId: Long) {
        val expense = expenses.getExpense(expenseId) ?: return
        expenses.deleteExpense(expenseId)
        logActivity("DELETE_EXPENSE", "Usunięto wydatek „${expense.description}”", expense.groupId)
    }

    suspend fun buildExpensesCsv(): String {
        val rows = expenses.exportRows()
        val header = "data;grupa;opis;kategoria;kto_placil;kwota;rozliczenie;udzialy"
        val body = rows.joinToString("\n") { row ->
            listOf(
                LocalDate.ofEpochDay(row.date).toString(),
                row.groupName,
                row.description,
                ExpenseCategory.labelOf(row.category),
                row.payerName,
                Money.formatInput(row.amount),
                if (row.isSettlement) "tak" else "nie",
                row.shares.orEmpty(),
            ).joinToString(";") { csvCell(it) }
        }
        return if (body.isEmpty()) header else "$header\n$body\n"
    }

    // ---------- Balances & Statistics ----------

    val personBalances: Flow<List<PersonBalance>> = meId.flatMapLatest { expenses.observePersonBalances(it) }

    val settlementLines: Flow<List<SettlementLine>> =
        meId.flatMapLatest { expenses.observeSettlementLines(it) }.map { lines -> lines.filter { it.balance != 0L } }

    fun observeMyCategoryTotals(month: YearMonth, groupId: Long = -1L): Flow<List<CategoryTotal>> =
        meId.flatMapLatest {
            expenses.observeMyCategoryTotals(it, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay(), groupId)
        }

    fun observeAllCategoryTotals(month: YearMonth, groupId: Long = -1L): Flow<List<CategoryTotal>> =
        expenses.observeAllCategoryTotals(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay(), groupId)

    fun observeMyShareByDay(fromDay: Long, toDay: Long, groupId: Long): Flow<List<DayTotal>> =
        meId.flatMapLatest { expenses.observeMyShareByDay(it, fromDay, toDay, groupId) }

    fun observeAllSpendingByDay(fromDay: Long, toDay: Long, groupId: Long): Flow<List<DayTotal>> =
        expenses.observeAllSpendingByDay(fromDay, toDay, groupId)

    suspend fun markActivitySeen(id: Long) = activityLogs.markSeen(id)

    suspend fun markAllActivitySeen() = activityLogs.markAllSeen()

    val unreadActivityCount: Flow<Int> = activityLogs.observeUnreadCount()

    suspend fun deleteAllData() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        requireMeId()
    }

    private fun String.cleanName(): String? = trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
    }
}
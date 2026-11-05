package com.fairshare.data

import androidx.room.withTransaction
import com.fairshare.data.db.ActivityLogEntity
import com.fairshare.data.db.CategoryTotal
import com.fairshare.data.db.ExpenseEntity
import com.fairshare.data.db.ExpenseListRow
import com.fairshare.data.db.ExpenseShareEntity
import com.fairshare.data.db.FairShareDatabase
import com.fairshare.data.db.GroupEntity
import com.fairshare.data.db.GroupMemberEntity
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.db.MemberBalance
import com.fairshare.data.db.PersonBalance
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
        groups.insertMember(GroupMemberEntity(groupId = groupId, userId = userId))
    }

    // ---------- Expenses & Settlements ----------

    fun observeGroupExpenses(groupId: Long): Flow<List<ExpenseListRow>> =
        meId.flatMapLatest { expenses.observeGroupExpenses(groupId, it) }

    suspend fun addExpense(
        groupId: Long,
        payerId: Long,
        amount: Long,
        description: String,
        category: ExpenseCategory?,
        date: LocalDate,
        participantIds: Collection<Long>,
    ): Long {
        require(amount > 0) { "Kwota musi być większa od zera" }
        val participants = participantIds.distinct().sorted()
        require(participants.isNotEmpty()) { "Wybierz przynajmniej jedną osobę do podziału" }

        return db.withTransaction {
            val members = groups.getMemberIds(groupId).toSet()
            require(payerId in members) { "Płacący nie należy do grupy" }
            require(members.containsAll(participants)) { "Ktoś z podziału nie należy do grupy" }

            val cleanDesc = description.trim().ifEmpty { category?.label ?: "Wydatek" }
            val expenseId = expenses.insertExpense(
                ExpenseEntity(
                    groupId = groupId,
                    payerId = payerId,
                    amount = amount,
                    description = cleanDesc,
                    category = category?.name,
                    date = date.toEpochDay(),
                )
            )
            val shares = Money.splitEqually(amount, participants.size)
            expenses.insertShares(
                participants.zip(shares) { userId, share ->
                    ExpenseShareEntity(expenseId = expenseId, userId = userId, amount = share)
                }
            )
            logActivity("ADD_EXPENSE", "Dodano wydatek „$cleanDesc” na kwotę ${Money.format(amount)}", groupId)
            expenseId
        }
    }

    suspend fun settleUp(targetUserId: Long, amountGrosze: Long, groupId: Long) {
        require(amountGrosze > 0) { "Kwota rozliczenia musi być większa od zera" }
        val myId = requireMeId()
        db.withTransaction {
            val expenseId = expenses.insertExpense(
                ExpenseEntity(
                    groupId = groupId,
                    payerId = myId,
                    amount = amountGrosze,
                    description = "Rozliczenie / Spłata",
                    category = null,
                    date = LocalDate.now().toEpochDay()
                )
            )
            expenses.insertShares(
                listOf(ExpenseShareEntity(expenseId = expenseId, userId = targetUserId, amount = amountGrosze))
            )
            logActivity("SETTLEMENT", "Zarejestrowano spłatę na kwotę ${Money.format(amountGrosze)}", groupId)
        }
    }

    suspend fun deleteExpense(expenseId: Long) = expenses.deleteExpense(expenseId)

    // ---------- Balances & Statistics ----------

    val personBalances: Flow<List<PersonBalance>> = meId.flatMapLatest { expenses.observePersonBalances(it) }

    fun observeMyCategoryTotals(month: YearMonth): Flow<List<CategoryTotal>> =
        meId.flatMapLatest {
            expenses.observeMyCategoryTotals(it, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
        }

    fun observeTotalSpentInRange(month: YearMonth): Flow<Long> =
        meId.flatMapLatest {
            expenses.observeTotalSpentInRange(it, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
        }.map { it ?: 0L }

    suspend fun deleteAllData() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        requireMeId()
    }

    private fun String.cleanName(): String? = trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }
}
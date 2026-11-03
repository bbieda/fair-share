package com.fairshare.data

import androidx.room.withTransaction
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

/**
 * The single entry point the UI uses to read and change data.
 * Reads are Flows: Room re-emits them whenever a table they read from changes,
 * so screens update by themselves after an insert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FairShareRepository(private val db: FairShareDatabase) {
    private val users = db.userDao()
    private val groups = db.groupDao()
    private val expenses = db.expenseDao()

    // ---------- Users ----------

    /** The phone owner. */
    val me: Flow<UserEntity> = users.observeMe().filterNotNull()

    private val meId: Flow<Long> = me.map { it.id }.distinctUntilChanged()

    val allUsers: Flow<List<UserEntity>> = users.observeAll()

    /** Id of the phone owner; creates the row if it is missing for any reason. */
    suspend fun requireMeId(): Long =
        users.getMe()?.id ?: users.insert(UserEntity(name = FairShareDatabase.DEFAULT_MY_NAME, isMe = true))

    suspend fun renameMe(name: String) {
        val clean = name.cleanName() ?: return
        val me = users.getMe() ?: return
        users.update(me.copy(name = clean))
    }

    // ---------- Groups ----------

    val groupSummaries: Flow<List<GroupSummary>> = meId.flatMapLatest { groups.observeGroupSummaries(it) }

    fun observeGroup(groupId: Long): Flow<GroupEntity?> = groups.observeGroup(groupId)

    fun observeMembers(groupId: Long): Flow<List<UserEntity>> = groups.observeMembers(groupId)

    fun observeMemberBalances(groupId: Long): Flow<List<MemberBalance>> = groups.observeMemberBalances(groupId)

    /** Creates a group with the phone owner as its first member. Returns the new group's id. */
    suspend fun createGroup(name: String): Long {
        val clean = requireNotNull(name.cleanName()) { "Nazwa grupy nie może być pusta" }
        return db.withTransaction {
            val groupId = groups.insert(GroupEntity(name = clean))
            groups.insertMember(GroupMemberEntity(groupId = groupId, userId = requireMeId()))
            groupId
        }
    }

    /**
     * Adds a person to a group by name. Reuses an existing person with the same name
     * (case-insensitive), so "Ania" in two groups is the same person. Returns the user id.
     */
    suspend fun addMemberByName(groupId: Long, name: String): Long {
        val clean = requireNotNull(name.cleanName()) { "Imię nie może być puste" }
        return db.withTransaction {
            val userId = users.findOtherByName(clean)?.id ?: users.insert(UserEntity(name = clean))
            groups.insertMember(GroupMemberEntity(groupId = groupId, userId = userId))
            userId
        }
    }

    suspend fun addMember(groupId: Long, userId: Long) {
        groups.insertMember(GroupMemberEntity(groupId = groupId, userId = userId))
    }

    // ---------- Expenses ----------

    fun observeGroupExpenses(groupId: Long): Flow<List<ExpenseListRow>> =
        meId.flatMapLatest { expenses.observeGroupExpenses(groupId, it) }

    /**
     * Saves an expense split equally between [participantIds].
     * The leftover grosze go to the first participants, so shares always sum to [amount].
     */
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

            val expenseId = expenses.insertExpense(
                ExpenseEntity(
                    groupId = groupId,
                    payerId = payerId,
                    amount = amount,
                    description = description.trim().ifEmpty { category?.label ?: "Wydatek" },
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
            expenseId
        }
    }

    suspend fun deleteExpense(expenseId: Long) = expenses.deleteExpense(expenseId)

    // ---------- Balances ----------

    /** My net balance with every person I share a group with. */
    val personBalances: Flow<List<PersonBalance>> = meId.flatMapLatest { expenses.observePersonBalances(it) }

    /** My share of expenses in [month], per category. */
    fun observeMyCategoryTotals(month: YearMonth): Flow<List<CategoryTotal>> =
        meId.flatMapLatest {
            expenses.observeMyCategoryTotals(it, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
        }

    // ---------- Maintenance ----------

    /** Wipes every table and recreates the phone owner. */
    suspend fun deleteAllData() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        requireMeId()
    }

    private fun String.cleanName(): String? = trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }
}

package com.fairshare.data.db

/*
 * Read models returned by DAO queries. Field names must match the column
 * names/aliases in the SQL of FairShareDaos.kt.
 * Balances are in grosze: > 0 means others owe that person, < 0 means that person owes.
 */

data class GroupSummary(
    val id: Long,
    val name: String,
    val archived: Boolean,
    /** "Ty, Ania, Bartek" (null when the group has no members). */
    val memberNames: String?,
    val memberCount: Int,
    val expenseCount: Int,
    /** The phone owner's balance in this group. */
    val myBalance: Long,
)

data class MemberBalance(
    val userId: Long,
    val name: String,
    val isMe: Boolean,
    /** Paid minus owed inside one group. */
    val balance: Long,
)

data class ExpenseListRow(
    val id: Long,
    val description: String,
    val category: String?,
    val amount: Long,
    /** LocalDate.toEpochDay() */
    val date: Long,
    val payerId: Long,
    val payerName: String,
    val payerIsMe: Boolean,
    /** What the phone owner owes of this expense (0 when not a participant). */
    val myShare: Long,
    val participantCount: Int,
)

/** Net balance between the phone owner and one other person, across all groups. */
data class PersonBalance(
    val userId: Long,
    val name: String,
    /** > 0: they owe me, < 0: I owe them. */
    val balance: Long,
    val groupNames: String?,
)

data class CategoryTotal(
    /** ExpenseCategory name or null. */
    val category: String?,
    val total: Long,
)

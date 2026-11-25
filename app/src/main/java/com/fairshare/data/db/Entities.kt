package com.fairshare.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * SQLite tables (file: fairshare.db in the app's private databases folder).
 *
 * All money amounts are stored as Long in grosze (1 zł = 100), never as Double,
 * so splitting and summing never loses a grosz to rounding.
 *
 * The database is a copy of what the user's groups hold on the server (docs/chmura.md). Local ids (Long) stay
 * on this phone and are what the UI uses; `uid` is the id shared with the server and the other phones.
 */

/**
 * A person. Exactly one row has isMe = true: the logged-in account. [hasAccount] is false for someone added
 * by name who does not use the app (yet); such a person can be taken over when they join the group.
 */
@Entity(
    tableName = "users",
    indices = [Index("name"), Index("uid", unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val isMe: Boolean = false,
    val photoUri: String? = null,
    val paymentDetails: String? = null,
    val defaultCurrency: String = "PLN",
    val hasAccount: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A group of friends sharing costs (a trip, a flat, ...). */
@Entity(tableName = "friend_groups", indices = [Index("uid", unique = true)])
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val archived: Boolean = false,
    /** Code others join with, e.g. "K7MQ-2XPA" (stored without the dash). The server may replace it. */
    val inviteCode: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Which user belongs to which group (many-to-many). */
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId")],
)
data class GroupMemberEntity(
    val groupId: Long,
    val userId: Long,
    val joinedAt: Long = System.currentTimeMillis(),
)

/** One expense paid by [payerId] inside a group. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["payerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("payerId"), Index("date"), Index("uid", unique = true)],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val groupId: Long,
    val payerId: Long,
    /** Total in grosze. */
    val amount: Long,
    val description: String,
    /** [com.fairshare.data.model.ExpenseCategory] name, null = not chosen. */
    val category: String?,
    /** Day of the expense as LocalDate.toEpochDay(). */
    val date: Long,
    /** True for a recorded repayment. Settlements change balances and stay out of spending stats. */
    val isSettlement: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** How much of an expense one participant owes. Shares of one expense always sum to its amount. */
@Entity(
    tableName = "expense_shares",
    primaryKeys = ["expenseId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId")],
)
data class ExpenseShareEntity(
    val expenseId: Long,
    val userId: Long,
    /** Share in grosze. */
    val amount: Long,
)

/**
 * One line of the receipt of an expense split by items ("kto co brał"). An expense split equally or by typed amounts
 * has no rows here. The prices of an expense's items always sum to its amount.
 */
@Entity(
    tableName = "expense_items",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expenseId")],
)
data class ExpenseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val name: String,
    /** Price in grosze, after discounts. Negative for a discount line. */
    val price: Long,
    /** Order on the receipt, from 0. */
    val position: Int,
)

/**
 * How much of one item one person pays. The rows of one item sum to its price, and all rows of one person in an
 * expense sum to that person's row in expense_shares.
 */
@Entity(
    tableName = "expense_item_shares",
    primaryKeys = ["itemId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId")],
)
data class ExpenseItemShareEntity(
    val itemId: Long,
    val userId: Long,
    /** Share of the item in grosze. */
    val amount: Long,
)

/** Activity feed: my own changes and, after a sync, what the others did in my groups. `seen` stays on this phone. */
@Entity(
    tableName = "activity_logs",
    indices = [Index("timestamp"), Index("groupId"), Index("uid", unique = true)],
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val groupId: Long? = null,
    val actionType: String,
    val description: String,
    val seen: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    /** True when the phone owner paid or has a share in it (used by the "Dotyczy mnie" filter). */
    @ColumnInfo(defaultValue = "1") val involvesMe: Boolean = true,
)

/**
 * A change made on this phone that the server has not confirmed yet (the offline queue). [type] is one of
 * [com.fairshare.data.sync.Ops], [payload] its JSON. Sent in [id] order; removed once the server answered.
 */
@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
)

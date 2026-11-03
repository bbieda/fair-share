package com.fairshare.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * SQLite tables (file: fairshare.db in the app's private databases folder).
 *
 * All money amounts are stored as Long in grosze (1 zł = 100), never as Double,
 * so splitting and summing never loses a grosz to rounding.
 */

/** A person. Exactly one row has isMe = true: the owner of the phone ("Ty"). */
@Entity(
    tableName = "users",
    indices = [Index("name")],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isMe: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A group of friends sharing costs (a trip, a flat, ...). */
@Entity(tableName = "friend_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
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
    indices = [Index("groupId"), Index("payerId"), Index("date")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val payerId: Long,
    /** Total in grosze. */
    val amount: Long,
    val description: String,
    /** [com.fairshare.data.model.ExpenseCategory] name, null = not chosen. */
    val category: String?,
    /** Day of the expense as LocalDate.toEpochDay(). */
    val date: Long,
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

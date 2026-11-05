package com.fairshare.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE isMe = 1 LIMIT 1")
    suspend fun getMe(): UserEntity?

    @Query("SELECT * FROM users WHERE isMe = 1 LIMIT 1")
    fun observeMe(): Flow<UserEntity?>

    @Query("SELECT * FROM users ORDER BY isMe DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isMe = 0 AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findOtherByName(name: String): UserEntity?
}

@Dao
interface GroupDao {
    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Query("SELECT * FROM friend_groups WHERE id = :groupId")
    fun observeGroup(groupId: Long): Flow<GroupEntity?>

    @Query("SELECT * FROM friend_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: Long): GroupEntity?

    @Query(
        """
        SELECT g.id AS id, g.name AS name, g.archived AS archived,
            (SELECT GROUP_CONCAT(u.name, ', ') FROM users u
                INNER JOIN group_members m ON m.userId = u.id
                WHERE m.groupId = g.id) AS memberNames,
            (SELECT COUNT(*) FROM group_members m WHERE m.groupId = g.id) AS memberCount,
            (SELECT COUNT(*) FROM expenses e WHERE e.groupId = g.id) AS expenseCount,
            COALESCE((SELECT SUM(e.amount) FROM expenses e
                WHERE e.groupId = g.id AND e.payerId = :meId), 0)
            - COALESCE((SELECT SUM(s.amount) FROM expense_shares s
                INNER JOIN expenses e ON e.id = s.expenseId
                WHERE e.groupId = g.id AND s.userId = :meId), 0) AS myBalance
        FROM friend_groups g
        ORDER BY g.archived, g.createdAt DESC
        """
    )
    fun observeGroupSummaries(meId: Long): Flow<List<GroupSummary>>

    @Query(
        """
        SELECT u.* FROM users u
        INNER JOIN group_members m ON m.userId = u.id
        WHERE m.groupId = :groupId
        ORDER BY u.isMe DESC, u.name COLLATE NOCASE
        """
    )
    fun observeMembers(groupId: Long): Flow<List<UserEntity>>

    @Query("SELECT userId FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberIds(groupId: Long): List<Long>

    @Query(
        """
        SELECT u.id AS userId, u.name AS name, u.isMe AS isMe,
            COALESCE((SELECT SUM(e.amount) FROM expenses e
                WHERE e.groupId = :groupId AND e.payerId = u.id), 0)
            - COALESCE((SELECT SUM(s.amount) FROM expense_shares s
                INNER JOIN expenses e ON e.id = s.expenseId
                WHERE e.groupId = :groupId AND s.userId = u.id), 0) AS balance
        FROM users u
        INNER JOIN group_members m ON m.userId = u.id
        WHERE m.groupId = :groupId
        ORDER BY u.isMe DESC, u.name COLLATE NOCASE
        """
    )
    fun observeMemberBalances(groupId: Long): Flow<List<MemberBalance>>
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert
    suspend fun insertShares(shares: List<ExpenseShareEntity>)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: Long)

    @Query(
        """
        SELECT e.id AS id, e.description AS description, e.category AS category,
            e.amount AS amount, e.date AS date, e.payerId AS payerId,
            u.name AS payerName, u.isMe AS payerIsMe,
            COALESCE((SELECT s.amount FROM expense_shares s
                WHERE s.expenseId = e.id AND s.userId = :meId), 0) AS myShare,
            (SELECT COUNT(*) FROM expense_shares s WHERE s.expenseId = e.id) AS participantCount
        FROM expenses e
        INNER JOIN users u ON u.id = e.payerId
        WHERE e.groupId = :groupId
        ORDER BY e.date DESC, e.id DESC
        """
    )
    fun observeGroupExpenses(groupId: Long, meId: Long): Flow<List<ExpenseListRow>>

    @Query(
        """
        SELECT u.id AS userId, u.name AS name,
            COALESCE((SELECT SUM(s.amount) FROM expense_shares s
                INNER JOIN expenses e ON e.id = s.expenseId
                WHERE e.payerId = :meId AND s.userId = u.id), 0)
            - COALESCE((SELECT SUM(s.amount) FROM expense_shares s
                INNER JOIN expenses e ON e.id = s.expenseId
                WHERE e.payerId = u.id AND s.userId = :meId), 0) AS balance,
            (SELECT GROUP_CONCAT(g.name, ', ') FROM friend_groups g
                WHERE EXISTS (SELECT 1 FROM group_members a WHERE a.groupId = g.id AND a.userId = u.id)
                  AND EXISTS (SELECT 1 FROM group_members b WHERE b.groupId = g.id AND b.userId = :meId)
            ) AS groupNames
        FROM users u
        WHERE u.isMe = 0
          AND EXISTS (SELECT 1 FROM group_members a
                INNER JOIN group_members b ON b.groupId = a.groupId
                WHERE a.userId = u.id AND b.userId = :meId)
        ORDER BY ABS(balance) DESC, u.name COLLATE NOCASE
        """
    )
    fun observePersonBalances(meId: Long): Flow<List<PersonBalance>>

    @Query(
        """
        SELECT e.category AS category, SUM(s.amount) AS total
        FROM expense_shares s
        INNER JOIN expenses e ON e.id = s.expenseId
        WHERE s.userId = :meId AND e.date BETWEEN :fromDay AND :toDay
        GROUP BY e.category
        ORDER BY total DESC
        """
    )
    fun observeMyCategoryTotals(meId: Long, fromDay: Long, toDay: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT SUM(s.amount)
        FROM expense_shares s
        INNER JOIN expenses e ON e.id = s.expenseId
        WHERE s.userId = :meId AND e.date BETWEEN :fromDay AND :toDay
        """
    )
    fun observeTotalSpentInRange(meId: Long, fromDay: Long, toDay: Long): Flow<Long?>
}

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(log: ActivityLogEntity): Long

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ActivityLogEntity>>

    @Query("DELETE FROM activity_logs")
    suspend fun deleteAll()
}
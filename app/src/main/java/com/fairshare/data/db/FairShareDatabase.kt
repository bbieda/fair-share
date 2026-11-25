package com.fairshare.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local copy of the user's data on the server (docs/chmura.md), plus the queue of changes not sent yet.
 * Since version 6 (sync with the server) older databases are dropped: the data lives on the server now and
 * the old local-only ids cannot be matched with it.
 */
@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ExpenseEntity::class,
        ExpenseShareEntity::class,
        ActivityLogEntity::class,
        ExpenseItemEntity::class,
        ExpenseItemShareEntity::class,
        PendingOpEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class FairShareDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun pendingOpDao(): PendingOpDao

    companion object {
        /** File name of the SQLite database: /data/data/com.fairshare/databases/fairshare.db */
        const val FILE_NAME = "fairshare.db"

        fun build(context: Context): FairShareDatabase =
            Room.databaseBuilder(context.applicationContext, FairShareDatabase::class.java, FILE_NAME)
                // Up to version 5 the data was local only; it is not carried over to the server
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                .build()
    }
}

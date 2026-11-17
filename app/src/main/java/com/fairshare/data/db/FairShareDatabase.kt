package com.fairshare.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    ],
    version = 5,
    exportSchema = true,
)
abstract class FairShareDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        /** File name of the SQLite database: /data/data/com.fairshare/databases/fairshare.db */
        const val FILE_NAME = "fairshare.db"

        fun build(context: Context): FairShareDatabase =
            Room.databaseBuilder(context.applicationContext, FairShareDatabase::class.java, FILE_NAME)
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                // Only for very old test databases (1 and 2); from 3 on the user's data is kept
                .fallbackToDestructiveMigration()
                .build()

        /** Runs once, when the file is created: adds the phone owner ("Ty"). */
        private object SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(INSERT_ME, arrayOf<Any>(DEFAULT_MY_NAME, System.currentTimeMillis()))
            }
        }

        /** Adds the "involvesMe" flag to the activity log. Old rows count as "involves me". */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_logs ADD COLUMN involvesMe INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Adds the tables for expenses split by receipt items. The SQL is what Room generates for
         * [ExpenseItemEntity] and [ExpenseItemShareEntity] (see schemas/.../5.json): Room checks the tables against
         * the entities when the database opens, so change both together.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expense_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `expenseId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `price` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_items_expenseId` ON `expense_items` (`expenseId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expense_item_shares` (" +
                        "`itemId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`itemId`, `userId`), " +
                        "FOREIGN KEY(`itemId`) REFERENCES `expense_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_item_shares_userId` ON `expense_item_shares` (`userId`)")
            }
        }

        const val DEFAULT_MY_NAME = "Ty"
        private const val INSERT_ME = "INSERT INTO users (name, isMe, defaultCurrency, createdAt) VALUES (?, 1, 'PLN', ?)"
    }
}
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
    ],
    version = 4,
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
                .addMigrations(MIGRATION_3_4)
                // Only for very old test databases (1 and 2); 3 -> 4 keeps the user's data
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

        const val DEFAULT_MY_NAME = "Ty"
        private const val INSERT_ME = "INSERT INTO users (name, isMe, defaultCurrency, createdAt) VALUES (?, 1, 'PLN', ?)"
    }
}
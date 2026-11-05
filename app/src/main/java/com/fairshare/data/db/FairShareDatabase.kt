package com.fairshare.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 2,
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
                .fallbackToDestructiveMigration()
                .build()

        /** Runs once, when the file is created: adds the phone owner ("Ty"). */
        private object SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(INSERT_ME, arrayOf<Any>(DEFAULT_MY_NAME, System.currentTimeMillis()))
            }
        }

        const val DEFAULT_MY_NAME = "Ty"
        private const val INSERT_ME = "INSERT INTO users (name, isMe, defaultCurrency, createdAt) VALUES (?, 1, 'PLN', ?)"
    }
}
package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SalarySettings::class, Transaction::class, CurrencyItem::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun salaryDao(): SalaryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun currencyDao(): CurrencyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun safeExecSQL(db: SupportSQLiteDatabase, sql: String) {
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                Log.w("AppDatabase", "SQL execution skipped or column already exists: $sql", e)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN isSpendingDeductedFromSalary INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExecSQL(
                    db,
                    "CREATE TABLE IF NOT EXISTS `currencies` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `symbol` TEXT NOT NULL, `flag` TEXT NOT NULL DEFAULT '', `isCustom` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_1_8 = object : Migration(1, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN openingBalance REAL NOT NULL DEFAULT 0.0")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN securityEnabled INTEGER NOT NULL DEFAULT 0")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN securityPin TEXT NOT NULL DEFAULT ''")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN biometricEnabled INTEGER NOT NULL DEFAULT 0")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN autoAddDailySpending INTEGER NOT NULL DEFAULT 0")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'DARK'")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN appLanguage TEXT NOT NULL DEFAULT 'ar'")
                safeExecSQL(
                    db,
                    "CREATE TABLE IF NOT EXISTS `currencies` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `symbol` TEXT NOT NULL, `flag` TEXT NOT NULL DEFAULT '', `isCustom` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN openingBalance REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN securityEnabled INTEGER NOT NULL DEFAULT 0")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN securityPin TEXT NOT NULL DEFAULT ''")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN biometricEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN autoAddDailySpending INTEGER NOT NULL DEFAULT 0")
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'DARK'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExecSQL(db, "ALTER TABLE salary_settings ADD COLUMN appLanguage TEXT NOT NULL DEFAULT 'ar'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = try {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "salary_friend_database"
                    )
                    .addMigrations(MIGRATION_1_8, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                } catch (e: Exception) {
                    Log.e("AppDatabase", "Failed to build database with migrations, rebuilding destructively", e)
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "salary_friend_database"
                    )
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                }
                INSTANCE = instance
                instance
            }
        }
    }
}

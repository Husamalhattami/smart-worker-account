package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryDao {
    @Query("SELECT * FROM salary_settings WHERE id = 1 LIMIT 1")
    fun getSalarySettings(): Flow<SalarySettings?>

    @Query("SELECT * FROM salary_settings WHERE id = 1 LIMIT 1")
    suspend fun getSalarySettingsDirect(): SalarySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSalarySettings(settings: SalarySettings)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsDirect(): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY name ASC")
    fun getAllCurrencies(): Flow<List<CurrencyItem>>

    @Query("SELECT * FROM currencies ORDER BY name ASC")
    suspend fun getAllCurrenciesDirect(): List<CurrencyItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrencies(currencies: List<CurrencyItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrency(currency: CurrencyItem)

    @Delete
    suspend fun deleteCurrency(currency: CurrencyItem)

    @Query("SELECT COUNT(*) FROM currencies")
    suspend fun getCurrencyCount(): Int
}

@Dao
interface SalaryHistoryDao {
    @Query("SELECT * FROM salary_histories ORDER BY effectiveStartDate ASC")
    fun getAllSalaryHistories(): Flow<List<SalaryHistory>>

    @Query("SELECT * FROM salary_histories ORDER BY effectiveStartDate ASC")
    suspend fun getAllSalaryHistoriesDirect(): List<SalaryHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryHistory(history: SalaryHistory)

    @Query("DELETE FROM salary_histories WHERE id = :id")
    suspend fun deleteSalaryHistoryById(id: Int)

    @Query("DELETE FROM salary_histories")
    suspend fun deleteAllSalaryHistories()
}

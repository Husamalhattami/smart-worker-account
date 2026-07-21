package com.example.data

import kotlinx.coroutines.flow.Flow

class SalaryRepository(
    private val salaryDao: SalaryDao,
    private val transactionDao: TransactionDao
) {
    val salarySettings: Flow<SalarySettings?> = salaryDao.getSalarySettings()
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    suspend fun getSalarySettingsDirect(): SalarySettings? {
        return salaryDao.getSalarySettingsDirect()
    }

    suspend fun getAllTransactionsDirect(): List<Transaction> {
        return transactionDao.getAllTransactionsDirect()
    }

    suspend fun insertOrUpdateSalarySettings(settings: SalarySettings) {
        salaryDao.insertOrUpdateSalarySettings(settings)
    }

    suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Int) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun deleteAllTransactions() {
        transactionDao.deleteAllTransactions()
    }
}

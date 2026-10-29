package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class SalaryRepository(
    private val salaryDao: SalaryDao,
    private val transactionDao: TransactionDao,
    private val currencyDao: CurrencyDao,
    private val salaryHistoryDao: SalaryHistoryDao,
    private val leaveDao: LeaveDao
) {
    val salarySettings: Flow<SalarySettings?> = salaryDao.getSalarySettings()
        .catch { e ->
            Log.e("SalaryRepository", "Error reading salary settings", e)
            emit(SalarySettings())
        }

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
        .catch { e ->
            Log.e("SalaryRepository", "Error reading transactions", e)
            emit(emptyList())
        }

    val allCurrencies: Flow<List<CurrencyItem>> = currencyDao.getAllCurrencies()
        .catch { e ->
            Log.e("SalaryRepository", "Error reading currencies", e)
            emit(emptyList())
        }

    val salaryHistories: Flow<List<SalaryHistory>> = salaryHistoryDao.getAllSalaryHistories()
        .catch { e ->
            Log.e("SalaryRepository", "Error reading salary histories", e)
            emit(emptyList())
        }

    val allLeaves: Flow<List<LeaveRecord>> = leaveDao.getAllLeaves()
        .catch { e ->
            Log.e("SalaryRepository", "Error reading leaves", e)
            emit(emptyList())
        }

    suspend fun getAllLeavesDirect(): List<LeaveRecord> {
        return try {
            leaveDao.getAllLeavesDirect()
        } catch (e: Exception) {
            Log.e("SalaryRepository", "Error getting leaves direct", e)
            emptyList()
        }
    }

    suspend fun insertLeave(leave: LeaveRecord) {
        leaveDao.insertLeave(leave)
    }

    suspend fun updateLeave(leave: LeaveRecord) {
        leaveDao.updateLeave(leave)
    }

    suspend fun deleteLeaveById(id: Int) {
        leaveDao.deleteLeaveById(id)
    }

    suspend fun deleteAllLeaves() {
        leaveDao.deleteAllLeaves()
    }

    suspend fun getAllSalaryHistoriesDirect(): List<SalaryHistory> {
        val list = salaryHistoryDao.getAllSalaryHistoriesDirect()
        if (list.isEmpty()) {
            seedInitialSalaryHistoryIfEmpty()
            return salaryHistoryDao.getAllSalaryHistoriesDirect()
        }
        return list
    }

    suspend fun insertSalaryHistory(history: SalaryHistory) {
        salaryHistoryDao.insertSalaryHistory(history)
    }

    suspend fun deleteSalaryHistoryById(id: Int) {
        salaryHistoryDao.deleteSalaryHistoryById(id)
    }

    suspend fun seedInitialSalaryHistoryIfEmpty() {
        try {
            if (salaryHistoryDao.getAllSalaryHistoriesDirect().isNotEmpty()) return
            val settings = salaryDao.getSalarySettingsDirect() ?: SalarySettings()
            val txs = transactionDao.getAllTransactionsDirect()

            val calAug1 = java.util.Calendar.getInstance().apply {
                set(2026, java.util.Calendar.AUGUST, 1, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val calJul1 = java.util.Calendar.getInstance().apply {
                set(2026, java.util.Calendar.JULY, 1, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val hasJulTxs = txs.any { it.timestamp < calAug1.timeInMillis }

            if (hasJulTxs || settings.monthlySalary == 120000.0) {
                // User has month 7 (July) data with 150,000 rate and month 8 (August) with 120,000 rate
                val julHistory = SalaryHistory(
                    monthlySalary = 150000.0,
                    dailyRate = 5000.0,
                    effectiveStartDate = calJul1.timeInMillis,
                    note = "الراتب الشهري الأساسي (شهر 7)"
                )
                val augHistory = SalaryHistory(
                    monthlySalary = settings.monthlySalary,
                    dailyRate = settings.monthlySalary / 30.0,
                    effectiveStartDate = calAug1.timeInMillis,
                    note = "تحديث الراتب (شهر 8)"
                )
                salaryHistoryDao.insertSalaryHistory(julHistory)
                salaryHistoryDao.insertSalaryHistory(augHistory)
            } else {
                val defaultHistory = SalaryHistory(
                    monthlySalary = settings.monthlySalary,
                    dailyRate = settings.monthlySalary / 30.0,
                    effectiveStartDate = settings.startWorkDate,
                    note = "الراتب الأساسي الأولي"
                )
                salaryHistoryDao.insertSalaryHistory(defaultHistory)
            }
        } catch (e: Exception) {
            Log.e("SalaryRepository", "Error seeding initial salary history", e)
        }
    }

    suspend fun seedDefaultCurrenciesIfEmpty() {
        try {
            if (currencyDao.getCurrencyCount() == 0) {
                val defaults = listOf(
                    CurrencyItem("YER", "ريال يمني", "ر.ي", "🇾🇪"),
                    CurrencyItem("SAR", "ريال سعودي", "ر.س", "🇸🇦"),
                    CurrencyItem("USD", "دولار أمريكي", "$", "💵"),
                    CurrencyItem("EGP", "جنيه مصري", "ج.م", "🇪🇬"),
                    CurrencyItem("AED", "درهم إماراتي", "د.إ", "🇦🇪"),
                    CurrencyItem("KWD", "دينار كويتي", "د.ك", "🇰🇼"),
                    CurrencyItem("QAR", "ريال قطري", "ر.ق", "🇶🇦"),
                    CurrencyItem("OMR", "ريال عماني", "ر.ع", "🇴🇲"),
                    CurrencyItem("BHD", "دينار بحريني", "د.ب", "🇧🇭"),
                    CurrencyItem("JOD", "دينار أردني", "د.أ", "🇯🇴"),
                    CurrencyItem("IQD", "دينار عراقي", "د.ع", "🇮🇶"),
                    CurrencyItem("SDG", "جنيه سوداني", "ج.س", "🇸🇩"),
                    CurrencyItem("SYP", "ليرة سورية", "ل.س", "🇸🇾"),
                    CurrencyItem("LBP", "ليرة لبنانية", "ل.ل", "🇱🇧"),
                    CurrencyItem("EUR", "يورو", "€", "🇪🇺")
                )
                currencyDao.insertCurrencies(defaults)
            }
        } catch (e: Exception) {
            Log.e("SalaryRepository", "Error seeding currencies", e)
        }
    }

    suspend fun insertCurrency(currency: CurrencyItem) {
        currencyDao.insertCurrency(currency)
    }

    suspend fun deleteCurrency(currency: CurrencyItem) {
        currencyDao.deleteCurrency(currency)
    }

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

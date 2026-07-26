package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class SalaryRepository(
    private val salaryDao: SalaryDao,
    private val transactionDao: TransactionDao,
    private val currencyDao: CurrencyDao
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

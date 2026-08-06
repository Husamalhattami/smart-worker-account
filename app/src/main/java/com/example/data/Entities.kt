package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "salary_settings")
data class SalarySettings(
    @PrimaryKey val id: Int = 1,
    val monthlySalary: Double = 150000.0,
    val startWorkDate: Long = System.currentTimeMillis(),
    val stopWorkDate: Long? = null,
    val currency: String = "ريال يمني",
    val standardSpendingAmount: Double = 2000.0,
    val priorAbsenceCount: Int = 0,
    val priorSpendingsAmount: Double = 0.0,
    val priorDrawsAmount: Double = 0.0,
    val dailyReminderEnabled: Boolean = true,
    val dailyReminderHour: Int = 5,
    val dailyReminderMinute: Int = 0,
    val employeeName: String = "",
    val voiceEnabled: Boolean = true,
    val openingBalance: Double = 0.0,
    val securityEnabled: Boolean = false,
    val securityPin: String = "",
    val biometricEnabled: Boolean = false,
    val autoAddDailySpending: Boolean = false,
    val isSpendingDeductedFromSalary: Boolean = true,
    val isWorkPaused: Boolean = false,
    val themeMode: String = "DARK",
    val appLanguage: String = "ar"
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "SPENDING" (صرفة), "DRAW" (سحبية), "ABSENCE" (غياب)
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val voiceNotePath: String? = null,
    val voiceText: String? = null
)

@Entity(tableName = "currencies")
data class CurrencyItem(
    @PrimaryKey val id: String,
    val name: String,
    val symbol: String,
    val flag: String = "",
    val isCustom: Boolean = false
)

@Entity(tableName = "salary_histories")
data class SalaryHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monthlySalary: Double,
    val dailyRate: Double = monthlySalary / 30.0,
    val effectiveStartDate: Long,
    val note: String = ""
)

package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.util.Log
import com.example.data.LeaveRecord
import com.example.data.SalaryHistory
import com.example.data.SalarySettings
import com.example.data.Transaction
import com.example.viewmodel.SalaryMetrics
import java.text.SimpleDateFormat
import java.util.*

object PdfPrintHelper {

    private fun isSameDay(ms1: Long, ms2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = ms1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = ms2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatBalanceHtml(balance: Double, currency: String, balanceDisplayMode: String): String {
        if (balanceDisplayMode == "END_OF_REPORT") return "-"
        val bInt = balance.toInt()
        return when {
            balance > 0.001 -> """<span style="color: #047857; font-weight: bold; white-space: nowrap;">له: ${String.format(Locale("ar"), "%,d", bInt)} $currency</span>"""
            balance < -0.001 -> """<span style="color: #b91c1c; font-weight: bold; white-space: nowrap;">عليه: ${String.format(Locale("ar"), "%,d", Math.abs(bInt))} $currency</span>"""
            else -> """<span style="color: #64748b; font-weight: bold; white-space: nowrap;">0 $currency</span>"""
        }
    }

    fun printSalaryReport(
        context: Context,
        settings: SalarySettings,
        transactions: List<Transaction>,
        metrics: SalaryMetrics,
        startDateFilter: Long? = null,
        endDateFilter: Long? = null,
        showTotalSummary: Boolean = true,
        balanceDisplayMode: String = "AFTER_EACH_TX", // "AFTER_EACH_TX", "END_OF_MONTH", "END_OF_REPORT"
        transactionTypeFilter: String = "ALL", // "ALL", "SPENDING", "DRAW", "ABSENCE", "SETTLEMENT"
        salaryHistories: List<SalaryHistory> = emptyList(),
        leaves: List<LeaveRecord> = emptyList(),
        onSuccess: (() -> Unit)? = null
    ) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
        val dateString = sdf.format(Date())

        val startSdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val startWorkDateStr = startSdf.format(Date(settings.startWorkDate))

        val currency = if (settings.currency.isBlank()) "ريال" else settings.currency

        // Determine effective report start and end timestamps
        val nowMs = System.currentTimeMillis()

        val startCal = Calendar.getInstance().apply {
            timeInMillis = startDateFilter ?: settings.startWorkDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance().apply {
            if (endDateFilter != null) {
                timeInMillis = endDateFilter
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            } else {
                timeInMillis = nowMs
            }
        }

        val effectiveStartMs = startCal.timeInMillis
        val effectiveEndMs = endCal.timeInMillis

        // The midnight timestamp of the last day to iterate through in loops
        val lastDayMidnightMs = Calendar.getInstance().apply {
            timeInMillis = endCal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val dailyWage = if (settings.monthlySalary > 0) settings.monthlySalary / 30.0 else 5000.0

        // Filter transactions based on date range & type filter
        val rawFilteredTransactions = transactions.filter { tx ->
            val matchStart = tx.timestamp >= startCal.timeInMillis
            val matchEnd = tx.timestamp <= endCal.timeInMillis
            val matchType = when (transactionTypeFilter) {
                "SPENDING" -> tx.type == "SPENDING"
                "DRAW" -> tx.type == "DRAW"
                "ABSENCE" -> tx.type == "ABSENCE"
                "DEPOSIT" -> tx.type == "DEPOSIT"
                "SETTLEMENT" -> tx.type == "OPENING_BALANCE" || tx.type == "SETTLEMENT"
                else -> true
            }
            matchStart && matchEnd && matchType
        }.sortedWith(compareBy({ it.timestamp }, { if (it.type == "SETTLEMENT") 1 else 0 }))

        val filteredTransactions = rawFilteredTransactions

        // Type filter label
        val typeFilterTitle = when (transactionTypeFilter) {
            "SPENDING" -> "كشف الصرفيات والمصروفات اليومية"
            "DRAW" -> "كشف السحبيات والسلف المالية"
            "ABSENCE" -> "كشف سجل الغيابات وخصم الساعات"
            "DEPOSIT" -> "كشف الإيداعات والدفعات للحساب"
            "SETTLEMENT" -> "كشف التصفيات الأسبوعية والأرصدة"
            else -> "كشف حساب مالي شامل"
        }

        // Period filter label text
        val periodLabel = if (startDateFilter != null && endDateFilter != null) {
            "من ${startSdf.format(Date(startDateFilter))} إلى ${startSdf.format(Date(endDateFilter))}"
        } else if (startDateFilter != null) {
            "من ${startSdf.format(Date(startDateFilter))}"
        } else if (endDateFilter != null) {
            "حتى ${startSdf.format(Date(endDateFilter))}"
        } else {
            "كشف شامل من بداية التسجيل حتى اليوم"
        }

        // Calculate opening balance at report start date
        val openingTx = transactions.firstOrNull { it.type == "OPENING_BALANCE" }
        val initialOpeningAmount = openingTx?.amount ?: settings.openingBalance
        val initialStartDateMs = openingTx?.timestamp ?: settings.startWorkDate

        val (reportOpeningBalance, reportStartDateStr) = if (startCal.timeInMillis <= initialStartDateMs) {
            Pair(initialOpeningAmount, startSdf.format(Date(startCal.timeInMillis)))
        } else {
            val priorSettlement = transactions.filter { it.type == "SETTLEMENT" && it.timestamp < startCal.timeInMillis }
                .maxByOrNull { it.timestamp }
            val baseOpening = priorSettlement?.amount ?: initialOpeningAmount
            val baseMs = priorSettlement?.let {
                Calendar.getInstance().apply {
                    timeInMillis = it.timestamp
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } ?: initialStartDateMs

            val priorTxs = transactions.filter { it.timestamp >= baseMs && it.timestamp < startCal.timeInMillis && it.type != "SETTLEMENT" && it.type != "OPENING_BALANCE" }
            val priorSpendings = priorTxs.filter { it.type == "SPENDING" }.sumOf { it.amount } + (if (priorSettlement == null) settings.priorSpendingsAmount else 0.0)
            val priorDraws = priorTxs.filter { it.type == "DRAW" }.sumOf { it.amount } + (if (priorSettlement == null) settings.priorDrawsAmount else 0.0)
            val priorDeposits = priorTxs.filter { it.type == "DEPOSIT" }.sumOf { it.amount }
            val priorAbsenceTx = priorTxs.filter { it.type == "ABSENCE" }
            
            var priorEarnedSalary = 0.0
            var priorAbsenceDeduction = 0.0

            val priorCal = Calendar.getInstance().apply {
                timeInMillis = baseMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            while (priorCal.timeInMillis < startCal.timeInMillis) {
                val dayMs = priorCal.timeInMillis
                val activeHist = salaryHistories.filter { hist ->
                    val hMidnight = Calendar.getInstance().apply {
                        timeInMillis = hist.effectiveStartDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    hMidnight <= dayMs
                }.maxByOrNull { it.effectiveStartDate }

                val dayDailyWage = activeHist?.dailyRate ?: (if (settings.monthlySalary > 0) settings.monthlySalary / 30.0 else 5000.0)
                priorEarnedSalary += dayDailyWage

                val dayAbsTxs = priorAbsenceTx.filter { tx ->
                    val txCal = Calendar.getInstance().apply {
                        timeInMillis = tx.timestamp
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    txCal.timeInMillis == dayMs
                }
                for (tx in dayAbsTxs) {
                    val absHours = if (tx.amount <= 0.0) 12.0 else tx.amount
                    priorAbsenceDeduction += absHours * (dayDailyWage / 12.0)
                }

                priorCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (priorSettlement == null && settings.priorAbsenceCount > 0) {
                priorAbsenceDeduction += settings.priorAbsenceCount * 12.0 * (dailyWage / 12.0)
            }

            val computedOpening = if (settings.isSpendingDeductedFromSalary) {
                baseOpening + priorEarnedSalary + priorDeposits - priorSpendings - priorDraws - priorAbsenceDeduction
            } else {
                baseOpening + priorEarnedSalary + priorDeposits - priorDraws - priorAbsenceDeduction
            }
            Pair(computedOpening, startSdf.format(Date(startCal.timeInMillis)))
        }

        // Determine effective work period bounds
        val firstTxMs = transactions.minOfOrNull { it.timestamp } ?: settings.startWorkDate
        val activeStartBound = minOf(settings.startWorkDate, firstTxMs, openingTx?.timestamp ?: settings.startWorkDate)
        val effectiveWorkStart = Calendar.getInstance().apply {
            timeInMillis = activeStartBound
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val effectiveWorkStop = settings.stopWorkDate?.let { stopMs ->
            Calendar.getInstance().apply {
                timeInMillis = stopMs
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        }

        // Accurately calculate day-by-day metrics across the report period using salaryHistories
        var workedDaysInPeriod = 0
        var totalEarnedSalaryInPeriod = 0.0
        var periodSpendings = 0.0
        var periodDraws = 0.0
        var periodDeposits = 0.0
        var absenceHoursTotal = 0.0
        var absenceDeduction = 0.0

        val preCalcCal = Calendar.getInstance().apply {
            timeInMillis = maxOf(startCal.timeInMillis, effectiveWorkStart)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val preCalcEndMs = lastDayMidnightMs

        while (preCalcCal.timeInMillis <= preCalcEndMs) {
            val dayMidnight = preCalcCal.timeInMillis

            val activeHist = salaryHistories.filter { hist ->
                val hMidnight = Calendar.getInstance().apply {
                    timeInMillis = hist.effectiveStartDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                hMidnight <= dayMidnight
            }.maxByOrNull { it.effectiveStartDate }

            val dayDailyWage = activeHist?.dailyRate ?: (if (settings.monthlySalary > 0) settings.monthlySalary / 30.0 else 5000.0)

            val inLeave = LeaveUtils.isDayInLeave(dayMidnight, leaves)
            val isWorkDay = !inLeave && dayMidnight >= effectiveWorkStart &&
                    (effectiveWorkStop == null || dayMidnight <= effectiveWorkStop) &&
                    (dayMidnight < todayMidnight || settings.stopWorkDate != null)
            if (isWorkDay) {
                workedDaysInPeriod++
                totalEarnedSalaryInPeriod += dayDailyWage
            }

            val dayTxs = filteredTransactions.filter { tx ->
                val txCal = Calendar.getInstance().apply {
                    timeInMillis = tx.timestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                txCal.timeInMillis == dayMidnight
            }

            for (tx in dayTxs) {
                when (tx.type) {
                    "SPENDING" -> periodSpendings += tx.amount
                    "DRAW" -> periodDraws += tx.amount
                    "DEPOSIT" -> periodDeposits += tx.amount
                    "ABSENCE" -> {
                        val absHours = if (tx.amount <= 0.0) 12.0 else tx.amount
                        absenceHoursTotal += absHours
                        val absDed = absHours * (dayDailyWage / 12.0)
                        absenceDeduction += absDed
                    }
                }
            }

            preCalcCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val absenceDaysCount = absenceHoursTotal / 12.0
        val hasOpeningOrSettlementTx = filteredTransactions.any { it.type == "OPENING_BALANCE" || it.type == "SETTLEMENT" }
        val openingCreditToAdd = if (reportOpeningBalance > 0.0 && !hasOpeningOrSettlementTx) reportOpeningBalance else 0.0
        val openingDebtToAdd = if (reportOpeningBalance < -0.001 && !hasOpeningOrSettlementTx) Math.abs(reportOpeningBalance) else 0.0
        val totalPeriodEntitlements = totalEarnedSalaryInPeriod + openingCreditToAdd
        val totalPeriodDrawsWithOpening = periodDraws + openingDebtToAdd
        val finalNetBalance = if (settings.isSpendingDeductedFromSalary) {
            reportOpeningBalance + totalEarnedSalaryInPeriod + periodDeposits - periodSpendings - periodDraws - absenceDeduction
        } else {
            reportOpeningBalance + totalEarnedSalaryInPeriod + periodDeposits - periodDraws - absenceDeduction
        }

        val empName = if (settings.employeeName.isBlank()) "حسام الحطامي" else settings.employeeName

        // Build Paper-Optimized HTML Content
        val htmlContent = buildString {
            append("""
                <!DOCTYPE html>
                <html dir="rtl" lang="ar">
                <head>
                    <meta charset="UTF-8">
                    <title>$typeFilterTitle - $empName</title>
                    <style>
                        @page {
                            size: A4;
                            margin: 6mm;
                        }
                        body {
                            font-family: 'Segoe UI', Tahoma, Arial, sans-serif;
                            color: #0f172a;
                            background-color: #ffffff;
                            margin: 0;
                            padding: 4px;
                            direction: rtl;
                            font-size: 10px;
                            line-height: 1.25;
                        }
                        .header-container {
                            border-bottom: 2px solid #059669;
                            padding-bottom: 4px;
                            margin-bottom: 8px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }
                        .app-title {
                            font-size: 16px;
                            font-weight: bold;
                            color: #059669;
                            margin: 0;
                        }
                        .report-subtitle {
                            font-size: 10px;
                            color: #475569;
                            margin: 2px 0 0 0;
                        }
                        .employee-card {
                            background-color: #f8fafc;
                            border: 1px solid #cbd5e1;
                            border-radius: 6px;
                            padding: 6px 10px;
                            margin-bottom: 8px;
                        }
                        .employee-name {
                            font-size: 14px;
                            font-weight: bold;
                            color: #0f172a;
                            margin: 0 0 4px 0;
                        }
                        .meta-grid {
                            display: grid;
                            grid-template-columns: repeat(4, 1fr);
                            gap: 6px;
                            font-size: 10px;
                        }
                        .meta-item {
                            background-color: #ffffff;
                            padding: 3px 6px;
                            border-radius: 4px;
                            border: 1px solid #e2e8f0;
                            text-align: center;
                        }
                        .meta-label {
                            color: #64748b;
                            display: block;
                            font-size: 8.5px;
                            margin-bottom: 1px;
                        }
                        .meta-value {
                            color: #0f172a;
                            font-weight: bold;
                        }

                        /* Accounting Calculation Summary Box */
                        .summary-box {
                            background-color: #ecfdf5;
                            border: 1.5px solid #059669;
                            border-radius: 6px;
                            padding: 6px 10px;
                            margin-bottom: 10px;
                            font-size: 10px;
                        }
                        .summary-title {
                            font-size: 11px;
                            font-weight: bold;
                            color: #047857;
                            margin-bottom: 4px;
                            border-bottom: 1px solid #a7f3d0;
                            padding-bottom: 3px;
                        }
                        .summary-grid {
                            display: grid;
                            grid-template-columns: repeat(4, 1fr);
                            gap: 6px;
                            margin-bottom: 4px;
                        }
                        .step-item {
                            background-color: #ffffff;
                            padding: 4px 6px;
                            border-radius: 4px;
                            border: 1px solid #d1fae5;
                            text-align: center;
                        }
                        .step-final-row {
                            background-color: #059669;
                            color: #ffffff;
                            padding: 5px 8px;
                            border-radius: 5px;
                            font-weight: bold;
                            font-size: 11px;
                            display: flex;
                            justify-content: space-between;
                            margin-top: 3px;
                        }

                        /* Compact Accounting Table */
                        table {
                            width: 100%;
                            border-collapse: collapse;
                            margin-bottom: 10px;
                            font-size: 9.5px;
                            page-break-inside: auto;
                        }
                        tr {
                            page-break-inside: avoid;
                            page-break-after: auto;
                        }
                        th {
                            padding: 5px 3px;
                            font-size: 9.5px;
                            font-weight: bold;
                            text-align: center;
                            border: 1px solid #475569;
                            background-color: #0f172a;
                            color: #ffffff;
                        }
                        td {
                            padding: 4px 3px;
                            border: 1px solid #cbd5e1;
                            font-size: 9.5px;
                            text-align: center;
                            vertical-align: middle;
                        }
                        tr:nth-child(even) {
                            background-color: #f8fafc;
                        }
                        
                        /* Footer signature */
                        .footer-container {
                            margin-top: 15px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            font-size: 9.5px;
                            color: #64748b;
                            border-top: 1px solid #cbd5e1;
                            padding-top: 6px;
                            page-break-inside: avoid;
                        }

                        @media print {
                            body {
                                margin: 0;
                                -webkit-print-color-adjust: exact;
                            }
                        }
                    </style>
                </head>
                <body>
                    <div class="header-container">
                        <div>
                            <h1 class="app-title">$typeFilterTitle 📋</h1>
                            <p class="report-subtitle">مستند مالي محدد للفترة: ($periodLabel)</p>
                        </div>
                        <div style="text-align: left; font-size: 9.5px; color: #64748b;">
                            تاريخ الإصدار: $dateString
                        </div>
                    </div>

                    <div class="employee-card">
                        <div class="employee-name">الموظف / العامل: $empName</div>
                        <div class="meta-grid">
                            <div class="meta-item">
                                <span class="meta-label">تاريخ بدء العمل</span>
                                <span class="meta-value">$startWorkDateStr</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">الراتب الشهري المحدد</span>
                                <span class="meta-value" style="color: #059669;">${settings.monthlySalary.toInt()} $currency</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">الأجر اليومي المستحق</span>
                                <span class="meta-value">${dailyWage.toInt()} $currency / يوم</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">الرصيد الافتتاحي</span>
                                <span class="meta-value">${formatBalanceHtml(reportOpeningBalance, currency, "AFTER_EACH_TX")}</span>
                            </div>
                        </div>
                    </div>
            """.trimIndent())

            // Header Accounting Calculation Summary Box
            if (showTotalSummary) {
                val hasPriorBalance = Math.abs(reportOpeningBalance) > 0.001
                val priorBalanceGridItem = if (hasPriorBalance) {
                    if (reportOpeningBalance > 0) {
                        """
                        <div class="step-item" style="border-color: #a7f3d0; background-color: #f0fdf4; flex: 1; min-width: 75px;">
                            <span style="color: #047857; display: block; font-size: 8.5px; font-weight: bold;">رصيد سابق له (+)</span>
                            <span style="font-weight: bold; color: #047857;">+${String.format(Locale("ar"), "%,d", reportOpeningBalance.toInt())} $currency</span>
                        </div>
                        """.trimIndent()
                    } else {
                        """
                        <div class="step-item" style="border-color: #fecaca; background-color: #fef2f2; flex: 1; min-width: 75px;">
                            <span style="color: #b91c1c; display: block; font-size: 8.5px; font-weight: bold;">سلف وسحب سابق عليه (-)</span>
                            <span style="font-weight: bold; color: #b91c1c;">-${String.format(Locale("ar"), "%,d", Math.abs(reportOpeningBalance.toInt()))} $currency</span>
                        </div>
                        """.trimIndent()
                    }
                } else ""

                val finalRowBg = when {
                    finalNetBalance > 0.001 -> "#059669"
                    finalNetBalance < -0.001 -> "#dc2626"
                    else -> "#475569"
                }
                val finalRowTitle = when {
                    finalNetBalance > 0.001 -> "💰 إجمالي الصافي المتبقي المستحق (له في ذمة صاحب العمل):"
                    finalNetBalance < -0.001 -> "⚠️ إجمالي الصافي المطلوب والمتبقي (عليه سلف ومسحوبات فائضة):"
                    else -> "⚖️ إجمالي الصافي النهائي (الحساب مصفر ومتطابق تماماً):"
                }
                val finalRowAmount = when {
                    finalNetBalance > 0.001 -> "له: ${String.format(Locale("ar"), "%,d", finalNetBalance.toInt())} $currency"
                    finalNetBalance < -0.001 -> "عليه: ${String.format(Locale("ar"), "%,d", Math.abs(finalNetBalance.toInt()))} $currency"
                    else -> "0 $currency"
                }

                append("""
                    <div class="summary-box" style="border-color: ${if (finalNetBalance >= 0) "#059669" else "#dc2626"}; background-color: ${if (finalNetBalance >= 0) "#ecfdf5" else "#fef2f2"};">
                        <div class="summary-title" style="color: ${if (finalNetBalance >= 0) "#047857" else "#b91c1c"}; border-bottom-color: ${if (finalNetBalance >= 0) "#a7f3d0" else "#fecaca"};">📊 الخلاصة المالية والتصفية للفترة الحالية ($workedDaysInPeriod يوم عمل):</div>
                        <div class="summary-grid" style="display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 4px;">
                            $priorBalanceGridItem
                            <div class="step-item" style="flex: 1; min-width: 75px;">
                                <span style="color: #64748b; display: block; font-size: 8.5px;">إجمالي الصرفيات (-)</span>
                                <span style="font-weight: bold; color: #1d4ed8;">-${periodSpendings.toInt()} $currency</span>
                            </div>
                            <div class="step-item" style="flex: 1; min-width: 75px;">
                                <span style="color: #64748b; display: block; font-size: 8.5px;">إجمالي السحبيات (-)</span>
                                <span style="font-weight: bold; color: #b45309;">-${periodDraws.toInt()} $currency</span>
                            </div>
                            <div class="step-item" style="flex: 1; min-width: 75px;">
                                <span style="color: #64748b; display: block; font-size: 8.5px;">خصم الغياب (${absenceDaysCount} يوم) (-)</span>
                                <span style="font-weight: bold; color: #b91c1c;">-${absenceDeduction.toInt()} $currency</span>
                            </div>
                            <div class="step-item" style="flex: 1; min-width: 75px;">
                                <span style="color: #64748b; display: block; font-size: 8.5px;">مستحق أجر العمل للفترة ($workedDaysInPeriod يوم) (+)</span>
                                <span style="font-weight: bold; color: #047857;">+${totalEarnedSalaryInPeriod.toInt()} $currency</span>
                            </div>
                        </div>

                        <div class="step-final-row" style="background-color: $finalRowBg; color: #ffffff; padding: 6px 10px; border-radius: 5px; font-weight: bold; font-size: 11px; display: flex; justify-content: space-between; align-items: center; margin-top: 4px;">
                            <span>$finalRowTitle</span>
                            <span style="font-size: 13px;">$finalRowAmount</span>
                        </div>
                    </div>
                """.trimIndent())
            }

            // Table Header with strictly requested 7 columns:
            // 1. التاريخ | 2. البيان/الملاحظة | 3. الصرفيات (-) | 4. السحبيات (-) | 5. الغيابات (-) | 6. المستحقات (+) | 7. الرصيد المتبقي
            append("""
                <table style="width: 100%;">
                    <thead>
                        <tr>
                            <th style="width: 12%;">التاريخ</th>
                            <th style="width: 27%;">البيان / الملاحظة</th>
                            <th style="width: 12%; background-color: #1e40af; color: #ffffff;">الصرفيات (-)</th>
                            <th style="width: 12%; background-color: #d97706; color: #ffffff;">السحبيات (-)</th>
                            <th style="width: 12%; background-color: #b91c1c; color: #ffffff;">الغيابات (-)</th>
                            <th style="width: 12%; background-color: #047857; color: #ffffff;">المستحقات (+)</th>
                            <th style="width: 13%; background-color: #0f172a; color: #ffffff;">الرصيد المتبقي</th>
                        </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            var runningBalance = if (startDateFilter != null && startDateFilter > settings.startWorkDate) reportOpeningBalance else reportOpeningBalance

            val hasOpeningOrSettlementTx = filteredTransactions.any { it.type == "OPENING_BALANCE" || it.type == "SETTLEMENT" }

            // Opening Balance Header Row
            if (reportOpeningBalance != 0.0 && !hasOpeningOrSettlementTx) {
                val isCredit = reportOpeningBalance > 0
                val note = if (isCredit) "رصيد سابق مترحل دائن (مستحق له)" else "رصيد سابق مترحل مدين (سلف ومسحوبات سابقة عليه)"
                val earnedCell = if (isCredit) "<span style='color: #047857; font-weight: bold;'>+${reportOpeningBalance.toInt()} $currency</span>" else "-"
                val drawCell = if (!isCredit) "<span style='color: #b45309; font-weight: bold;'>${Math.abs(reportOpeningBalance.toInt())} $currency</span>" else "-"
                append("""
                    <tr style="background-color: #f1f5f9; font-weight: bold;">
                        <td>$reportStartDateStr</td>
                        <td style="text-align: right; padding-right: 6px;">$note</td>
                        <td>-</td>
                        <td>$drawCell</td>
                        <td>-</td>
                        <td>$earnedCell</td>
                        <td style="text-align: center;">${formatBalanceHtml(runningBalance, currency, balanceDisplayMode)}</td>
                    </tr>
                """.trimIndent())
            }

            val itemSdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
            val monthKeySdf = SimpleDateFormat("yyyy/MM", Locale("ar"))

            if (transactionTypeFilter == "ALL") {
                // Detailed daily breakdown with daily wage accrual
                val openingTx = transactions.firstOrNull { it.type == "OPENING_BALANCE" }
                val firstTxMs = transactions.minOfOrNull { it.timestamp } ?: settings.startWorkDate
                val activeStartBound = minOf(settings.startWorkDate, firstTxMs, openingTx?.timestamp ?: settings.startWorkDate)
                val effectiveWorkStart = Calendar.getInstance().apply {
                    timeInMillis = activeStartBound
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val effectiveWorkStop = settings.stopWorkDate?.let { stopMs ->
                    Calendar.getInstance().apply {
                        timeInMillis = stopMs
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                }

                val effectiveStartMs = maxOf(startCal.timeInMillis, effectiveWorkStart)

                val currLoopCal = Calendar.getInstance().apply {
                    timeInMillis = effectiveStartMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endLoopMs = lastDayMidnightMs

                var currentMonthKey = ""
                var monthSpendings = 0.0
                var monthDraws = 0.0
                var monthAbsences = 0.0
                var monthEarned = 0.0

                val processedLeaveIds = mutableSetOf<Int>()

                while (currLoopCal.timeInMillis <= endLoopMs) {
                    val dayMidnight = currLoopCal.timeInMillis
                    val formattedDate = itemSdf.format(Date(dayMidnight))
                    val mKey = monthKeySdf.format(Date(dayMidnight))

                    // Check if current day is in a leave period
                    val currentLeave = LeaveUtils.findLeaveForDay(dayMidnight, leaves)
                    val inLeave = currentLeave != null

                    // If entering a new leave period, handle consolidated banner or start banner
                    if (currentLeave != null && !processedLeaveIds.contains(currentLeave.id)) {
                        processedLeaveIds.add(currentLeave.id)

                        val lStartMs = Calendar.getInstance().apply {
                            timeInMillis = currentLeave.startDate
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis

                        val lEndMs = currentLeave.endDate?.let { endMs ->
                            Calendar.getInstance().apply {
                                timeInMillis = endMs
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }.timeInMillis
                        } ?: endLoopMs

                        val effectiveLeaveStartInReport = maxOf(startCal.timeInMillis, lStartMs)
                        val effectiveLeaveEndInReport = minOf(endLoopMs, lEndMs)

                        val leaveTxs = filteredTransactions.filter { tx ->
                            tx.timestamp in effectiveLeaveStartInReport..effectiveLeaveEndInReport
                        }

                        if (leaveTxs.isEmpty()) {
                            // Case 1: Leave with NO transactions -> Single consolidated ribbon row
                            val lStartStr = LeaveUtils.formatLeaveDate(currentLeave.startDate)
                            val lEndStr = if (currentLeave.endDate != null) LeaveUtils.formatLeaveDate(currentLeave.endDate) else "مستمرة حتى الآن"
                            val daysCount = LeaveUtils.calculateLeaveDays(currentLeave)
                            val reasonText = if (currentLeave.reason.isNotBlank()) "(${currentLeave.reason})" else ""
                            append("""
                                <tr style="background-color: #f1f5f9; border-top: 1.5px solid #cbd5e1; border-bottom: 1.5px solid #cbd5e1; font-weight: bold;">
                                    <td style="color: #475569; font-size: 8.5px;">$lStartStr إلى $lEndStr</td>
                                    <td colspan="5" style="text-align: right; padding-right: 8px; color: #475569; font-size: 9.5px;">
                                        🏖️ فترة إجازة رسمية $reasonText ($daysCount يوم) - لا توجد مستحقات يومية أو مسحوبات
                                    </td>
                                    <td style="text-align: center;">${formatBalanceHtml(runningBalance, currency, balanceDisplayMode)}</td>
                                </tr>
                            """.trimIndent())

                            // Fast forward loop to end of this leave
                            currLoopCal.timeInMillis = effectiveLeaveEndInReport
                            currLoopCal.set(Calendar.HOUR_OF_DAY, 0)
                            currLoopCal.set(Calendar.MINUTE, 0)
                            currLoopCal.set(Calendar.SECOND, 0)
                            currLoopCal.set(Calendar.MILLISECOND, 0)
                            currLoopCal.add(Calendar.DAY_OF_YEAR, 1)
                            continue
                        } else {
                            // Case 2: Leave WITH transactions -> Header banner for leave start
                            val lStartStr = LeaveUtils.formatLeaveDate(currentLeave.startDate)
                            val lEndStr = if (currentLeave.endDate != null) LeaveUtils.formatLeaveDate(currentLeave.endDate) else "حتى الآن"
                            val reasonText = if (currentLeave.reason.isNotBlank()) "(${currentLeave.reason})" else ""
                            append("""
                                <tr style="background-color: #f8fafc; border-top: 1.5px dashed #94a3b8; font-weight: bold;">
                                    <td style="color: #64748b; font-size: 8.5px;">$lStartStr</td>
                                    <td colspan="6" style="text-align: right; padding-right: 8px; color: #64748b; font-size: 9.5px;">
                                        🏖️ بداية فترة إجازة $reasonText من $lStartStr إلى $lEndStr [الأجر اليومي والصرفيات التلقائية متوقفة]
                                    </td>
                                </tr>
                            """.trimIndent())
                        }
                    }

                    // Determine daily wage rate for this specific day using salary history
                    val activeHist = salaryHistories.filter { hist ->
                        val hMidnight = Calendar.getInstance().apply {
                            timeInMillis = hist.effectiveStartDate
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        hMidnight <= dayMidnight
                    }.maxByOrNull { it.effectiveStartDate }
                    
                    val dayDailyWage = activeHist?.dailyRate ?: (if (settings.monthlySalary > 0) settings.monthlySalary / 30.0 else 5000.0)

                    // Display salary rate change notification row if this day marks a rate effective start
                    if (activeHist != null && isSameDay(dayMidnight, activeHist.effectiveStartDate)) {
                        val rateNote = if (activeHist.note.isNotBlank()) " - ${activeHist.note}" else ""
                        append("""
                            <tr style="background-color: #f0fdf4; border-top: 2px dashed #16a34a; border-bottom: 2px dashed #16a34a; font-weight: bold;">
                                <td colspan="7" style="text-align: center; color: #15803d; padding: 7px; font-size: 13px;">
                                    📢 تم اعتماد/تحديث الراتب الشهري: ${activeHist.monthlySalary.toInt()} $currency (الأجر اليومي: ${activeHist.dailyRate.toInt()} $currency)$rateNote - ابتداءً من $formattedDate
                                </td>
                            </tr>
                        """.trimIndent())
                    }

                    if (mKey != currentMonthKey) {
                        if (currentMonthKey.isNotBlank() && (monthSpendings > 0 || monthDraws > 0 || monthAbsences > 0 || monthEarned > 0)) {
                            // Monthly Subtotal Summary Row
                            append("""
                                <tr style="background-color: #f0fdf4; border-top: 1.5px solid #059669; border-bottom: 1.5px solid #059669; font-weight: bold;">
                                    <td colspan="2" style="text-align: right; color: #166534; padding: 5px;">📌 ملخص نهاية شهر ($currentMonthKey):</td>
                                    <td style="color: #1d4ed8;">-${monthSpendings.toInt()} $currency</td>
                                    <td style="color: #b45309;">-${monthDraws.toInt()} $currency</td>
                                    <td style="color: #b91c1c;">-${monthAbsences.toInt()} $currency</td>
                                    <td style="color: #047857;">+${monthEarned.toInt()} $currency</td>
                                    <td style="color: #1e40af;">${runningBalance.toInt()} $currency</td>
                                </tr>
                            """.trimIndent())
                        }
                        currentMonthKey = mKey
                        monthSpendings = 0.0
                        monthDraws = 0.0
                        monthAbsences = 0.0
                        monthEarned = 0.0
                    }

                    // Find transactions on this exact day
                    val dayTxList = filteredTransactions.filter { tx ->
                        val txCal = Calendar.getInstance().apply {
                            timeInMillis = tx.timestamp
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        txCal.timeInMillis == dayMidnight
                    }

                    val isWorkDay = !inLeave && dayMidnight >= effectiveWorkStart &&
                            (effectiveWorkStop == null || dayMidnight <= effectiveWorkStop) &&
                            (dayMidnight < todayMidnight || settings.stopWorkDate != null)

                    if (dayTxList.isEmpty()) {
                        if (isWorkDay) {
                            // Work day with no special deductions -> standard daily wage earned
                            runningBalance += dayDailyWage
                            monthEarned += dayDailyWage

                            append("""
                                <tr>
                                    <td>$formattedDate</td>
                                    <td style="text-align: right; padding-right: 6px; color: #334155;">أجر عمل يومي مستحق 💼</td>
                                    <td>-</td>
                                    <td>-</td>
                                    <td>-</td>
                                    <td><span style="color: #047857; font-weight: bold;">+${dayDailyWage.toInt()} $currency</span></td>
                                    <td style="text-align: center;">${formatBalanceHtml(runningBalance, currency, balanceDisplayMode)}</td>
                                </tr>
                            """.trimIndent())
                        }
                    } else {
                        var dailyWageCredited = false
                        for (tx in dayTxList) {
                            val noteText = tx.voiceText?.takeIf { it.isNotBlank() } ?: ""

                            var spendingCell = "-"
                            var drawCell = "-"
                            var absenceCell = "-"
                            var earnedCell = "-"
                            var noteLabel = ""

                            when (tx.type) {
                                "OPENING_BALANCE" -> {
                                    val isCredit = tx.amount >= 0
                                    noteLabel = if (noteText.isNotBlank()) noteText else if (isCredit) "رصيد افتتاحي سابق دائن (له)" else "رصيد افتتاحي سابق مدين (سلف ومسحوبات عليه)"
                                    if (isCredit) {
                                        earnedCell = "<span style='color: #047857; font-weight: bold;'>+${tx.amount.toInt()} $currency</span>"
                                    } else {
                                        drawCell = "<span style='color: #b45309; font-weight: bold;'>${Math.abs(tx.amount.toInt())} $currency</span>"
                                    }
                                    runningBalance = tx.amount
                                }
                                "SETTLEMENT" -> {
                                    val isCredit = tx.amount >= 0
                                    noteLabel = if (noteText.isNotBlank()) noteText else if (isCredit) "🔒 تصفية وإقفال حساب وتدوير رصيد دائن (له)" else "🔒 تصفية وإقفال حساب وتدوير رصيد مدين (عليه)"
                                    if (isCredit) {
                                        earnedCell = "<span style='color: #6b21a8; font-weight: bold;'>تدوير له: +${tx.amount.toInt()} $currency</span>"
                                    } else {
                                        drawCell = "<span style='color: #b45309; font-weight: bold;'>تدوير عليه: ${Math.abs(tx.amount.toInt())} $currency</span>"
                                    }
                                    runningBalance = tx.amount
                                    dailyWageCredited = true
                                }
                                "DEPOSIT" -> {
                                    noteLabel = if (inLeave) {
                                        "🏖️ أثناء الإجازة: " + (if (noteText.isNotBlank()) noteText else "إيداع / دفعة للحساب")
                                    } else {
                                        if (noteText.isNotBlank()) noteText else "إيداع / دفعة للحساب"
                                    }
                                    earnedCell = "<span style='color: #0284c7; font-weight: bold;'>+${tx.amount.toInt()} $currency</span>"
                                    runningBalance += tx.amount
                                }
                                "SPENDING" -> {
                                    noteLabel = if (inLeave) {
                                        "🏖️ صرفة أثناء الإجازة/يوم السفر: " + (if (noteText.isNotBlank()) noteText else "صرفة يومية (بدون أجر يومي)")
                                    } else {
                                        if (noteText.isNotBlank()) noteText else "صرفة يومية"
                                    }
                                    spendingCell = "<span style='color: #1d4ed8; font-weight: bold;'>${tx.amount.toInt()} $currency</span>"
                                    monthSpendings += tx.amount

                                    if (!dailyWageCredited && isWorkDay) {
                                        runningBalance += (dayDailyWage - tx.amount)
                                        monthEarned += dayDailyWage
                                        earnedCell = "<span style='color: #047857; font-weight: bold;'>+${dayDailyWage.toInt()} $currency</span>"
                                        dailyWageCredited = true
                                    } else {
                                        runningBalance -= tx.amount
                                    }
                                }
                                "DRAW" -> {
                                    noteLabel = if (inLeave) {
                                        "🏖️ سحبية/حوالة أثناء الإجازة: " + (if (noteText.isNotBlank()) noteText else "سحبية / سلفة")
                                    } else {
                                        if (noteText.isNotBlank()) noteText else "سحبية / سلفة"
                                    }
                                    drawCell = "<span style='color: #b45309; font-weight: bold;'>${tx.amount.toInt()} $currency</span>"
                                    monthDraws += tx.amount

                                    if (!dailyWageCredited && isWorkDay) {
                                        runningBalance += (dayDailyWage - tx.amount)
                                        monthEarned += dayDailyWage
                                        earnedCell = "<span style='color: #047857; font-weight: bold;'>+${dayDailyWage.toInt()} $currency</span>"
                                        dailyWageCredited = true
                                    } else {
                                        runningBalance -= tx.amount
                                    }
                                }
                                "ABSENCE" -> {
                                    val absHours = if (tx.amount <= 0.0) 12.0 else tx.amount
                                    val absDed = absHours * (dayDailyWage / 12.0)
                                    val durationText = if (absHours >= 12.0 || absHours <= 0.0) "خصم غياب (يوم كامل)" else "خصم غياب (${AbsenceUtils.formatAbsenceHoursDescription(absHours)})"
                                    noteLabel = if (noteText.isNotBlank()) "$durationText - $noteText" else durationText
                                    absenceCell = "<span style='color: #b91c1c; font-weight: bold;'>-${absDed.toInt()} $currency</span>"
                                    monthAbsences += absDed

                                    if (!dailyWageCredited && isWorkDay) {
                                        runningBalance += (dayDailyWage - absDed)
                                        monthEarned += dayDailyWage
                                        earnedCell = "<span style='color: #047857; font-weight: bold;'>+${dayDailyWage.toInt()} $currency</span>"
                                        dailyWageCredited = true
                                    } else {
                                        runningBalance -= absDed
                                    }
                                }
                            }

                            val rowStyle = if (inLeave) "background-color: #f8fafc; border-right: 3px solid #94a3b8;" else ""

                            append("""
                                <tr style="$rowStyle">
                                    <td>$formattedDate</td>
                                    <td style="text-align: right; padding-right: 6px;">$noteLabel</td>
                                    <td>$spendingCell</td>
                                    <td>$drawCell</td>
                                    <td>$absenceCell</td>
                                    <td>$earnedCell</td>
                                    <td style="text-align: center;">${formatBalanceHtml(runningBalance, currency, balanceDisplayMode)}</td>
                                </tr>
                            """.trimIndent())
                        }

                        if (!dailyWageCredited && isWorkDay) {
                            runningBalance += dayDailyWage
                            monthEarned += dayDailyWage
                            dailyWageCredited = true

                            append("""
                                <tr>
                                    <td>$formattedDate</td>
                                    <td style="text-align: right; padding-right: 6px; color: #334155;">أجر عمل يومي مستحق 💼</td>
                                    <td>-</td>
                                    <td>-</td>
                                    <td>-</td>
                                    <td><span style="color: #047857; font-weight: bold;">+${dayDailyWage.toInt()} $currency</span></td>
                                    <td style="text-align: center;">${formatBalanceHtml(runningBalance, currency, balanceDisplayMode)}</td>
                                </tr>
                            """.trimIndent())
                        }
                    }

                    // If today is the ending day of an ongoing or completed leave, output end banner
                    if (currentLeave != null && currentLeave.endDate != null && isSameDay(dayMidnight, currentLeave.endDate)) {
                        append("""
                            <tr style="background-color: #f8fafc; border-bottom: 1.5px dashed #94a3b8; font-weight: bold;">
                                <td style="color: #64748b; font-size: 8.5px;">${LeaveUtils.formatLeaveDate(currentLeave.endDate)}</td>
                                <td colspan="6" style="text-align: right; padding-right: 8px; color: #64748b; font-size: 9.5px;">
                                    🏖️ نهاية فترة الإجازة - استئناف احتساب العمل والأجر اليومي
                                </td>
                            </tr>
                        """.trimIndent())
                    }

                    currLoopCal.add(Calendar.DAY_OF_YEAR, 1)
                }

                if (currentMonthKey.isNotBlank() && (monthSpendings > 0 || monthDraws > 0 || monthAbsences > 0 || monthEarned > 0)) {
                    // Final Month Summary Row
                    append("""
                        <tr style="background-color: #f0fdf4; border-top: 1.5px solid #059669; border-bottom: 1.5px solid #059669; font-weight: bold;">
                            <td colspan="2" style="text-align: right; color: #166534; padding: 5px;">📌 ملخص نهاية شهر ($currentMonthKey):</td>
                            <td style="color: #1d4ed8;">-${monthSpendings.toInt()} $currency</td>
                            <td style="color: #b45309;">-${monthDraws.toInt()} $currency</td>
                            <td style="color: #b91c1c;">-${monthAbsences.toInt()} $currency</td>
                            <td style="color: #047857;">+${monthEarned.toInt()} $currency</td>
                            <td style="text-align: center;">${formatBalanceHtml(runningBalance, currency, "AFTER_EACH_TX")}</td>
                        </tr>
                    """.trimIndent())
                }
            } else {
                // Type-filtered list (e.g. SPENDING only, DRAW only, etc.)
                for (tx in filteredTransactions) {
                    val formattedDate = itemSdf.format(Date(tx.timestamp))
                    val noteText = tx.voiceText?.takeIf { it.isNotBlank() } ?: ""

                    var spendingCell = "-"
                    var drawCell = "-"
                    var absenceCell = "-"
                    var earnedCell = "-"
                    var noteLabel = ""

                    when (tx.type) {
                        "SPENDING" -> {
                            noteLabel = if (noteText.isNotBlank()) noteText else "صرفة يومية"
                            spendingCell = "<span style='color: #1d4ed8; font-weight: bold;'>${tx.amount.toInt()} $currency</span>"
                            runningBalance -= tx.amount
                        }
                        "DRAW" -> {
                            noteLabel = if (noteText.isNotBlank()) noteText else "سحبية / سلفة"
                            drawCell = "<span style='color: #b45309; font-weight: bold;'>${tx.amount.toInt()} $currency</span>"
                            runningBalance -= tx.amount
                        }
                        "ABSENCE" -> {
                            val absHours = if (tx.amount <= 0.0) 12.0 else tx.amount
                            val absDed = absHours * (dailyWage / 12.0)
                            val durationText = if (absHours >= 12.0 || absHours <= 0.0) "خصم غياب (يوم كامل)" else "خصم غياب (${AbsenceUtils.formatAbsenceHoursDescription(absHours)})"
                            noteLabel = if (noteText.isNotBlank()) "$durationText - $noteText" else durationText
                            absenceCell = "<span style='color: #b91c1c; font-weight: bold;'>-${absDed.toInt()} $currency</span>"
                            runningBalance -= absDed
                        }
                        "DEPOSIT" -> {
                            noteLabel = if (noteText.isNotBlank()) noteText else "إيداع / دفعة للحساب"
                            earnedCell = "<span style='color: #0284c7; font-weight: bold;'>+${tx.amount.toInt()} $currency</span>"
                            runningBalance += tx.amount
                        }
                        else -> {
                            val isCredit = tx.amount >= 0
                            val defaultLabel = when (tx.type) {
                                "OPENING_BALANCE" -> if (isCredit) "رصيد افتتاحي سابق دائن (له)" else "رصيد افتتاحي سابق مدين (عليه)"
                                "SETTLEMENT" -> if (isCredit) "تصفية وتدوير رصيد دائن (له)" else "تصفية وتدوير رصيد مدين (عليه)"
                                else -> "تصفية / رصيد"
                            }
                            noteLabel = if (noteText.isNotBlank()) noteText else defaultLabel
                            if (isCredit) {
                                earnedCell = "<span style='color: #047857; font-weight: bold;'>+${tx.amount.toInt()} $currency</span>"
                            } else {
                                drawCell = "<span style='color: #b45309; font-weight: bold;'>${Math.abs(tx.amount.toInt())} $currency</span>"
                            }
                            runningBalance = tx.amount
                        }
                    }

                    append("""
                        <tr>
                            <td>$formattedDate</td>
                            <td style="text-align: right; padding-right: 6px;">$noteLabel</td>
                            <td>$spendingCell</td>
                            <td>$drawCell</td>
                            <td>$absenceCell</td>
                            <td>$earnedCell</td>
                            <td style="text-align: center;">${formatBalanceHtml(runningBalance, currency, balanceDisplayMode)}</td>
                        </tr>
                    """.trimIndent())
                }
            }

            // Final Summary Row at Report End
            val finalRowClass = if (finalNetBalance > 0.001) {
                "background-color: #ecfdf5; border-top: 2.5px solid #059669;"
            } else if (finalNetBalance < -0.001) {
                "background-color: #fef2f2; border-top: 2.5px solid #dc2626;"
            } else {
                "background-color: #f8fafc; border-top: 2.5px solid #64748b;"
            }

            val finalRowTitle = if (finalNetBalance > 0.001) {
                "💰 إجمالي الصافي المتبقي المستحق (له في ذمة صاحب العمل):"
            } else if (finalNetBalance < -0.001) {
                "⚠️ إجمالي الصافي المطلوب والمتبقي (عليه سلف ومسحوبات فائضة):"
            } else {
                "⚖️ إجمالي الصافي النهائي (الحساب مصفر ومتطابق تماماً):"
            }

            val finalRowTitleColor = if (finalNetBalance > 0.001) "#065f46" else if (finalNetBalance < -0.001) "#991b1b" else "#334155"

            val finalBalanceCell = if (finalNetBalance > 0.001) {
                """<td style="color: #047857; font-size: 11px; font-weight: bold; background-color: #d1fae5; white-space: nowrap;">له: ${String.format(Locale("ar"), "%,d", finalNetBalance.toInt())} $currency</td>"""
            } else if (finalNetBalance < -0.001) {
                """<td style="color: #b91c1c; font-size: 11px; font-weight: bold; background-color: #fee2e2; white-space: nowrap;">عليه: ${String.format(Locale("ar"), "%,d", Math.abs(finalNetBalance.toInt()))} $currency</td>"""
            } else {
                """<td style="color: #475569; font-size: 11px; font-weight: bold; background-color: #f1f5f9; white-space: nowrap;">0 $currency</td>"""
            }

            append("""
                <tr style="$finalRowClass font-size: 10.5px; font-weight: bold;">
                    <td colspan="2" style="text-align: right; color: $finalRowTitleColor; padding: 6px;">$finalRowTitle</td>
                    <td style="color: #1d4ed8;">-${periodSpendings.toInt()} $currency</td>
                    <td style="color: #b45309;">-${totalPeriodDrawsWithOpening.toInt()} $currency</td>
                    <td style="color: #b91c1c;">-${absenceDeduction.toInt()} $currency</td>
                    <td style="color: #047857;">+${totalPeriodEntitlements.toInt()} $currency</td>
                    $finalBalanceCell
                </tr>
            """.trimIndent())

            append("""
                        </tbody>
                    </table>

                    <div class="footer-container">
                        <span>مستند حساب مالي رسمي مطبوع عبر تطبيق (حساب العامل الذكي 📱)</span>
                        <span>توقيع واستلام الموظف/العامل: ............................</span>
                    </div>
                </body>
                </html>
            """.trimIndent())
        }

        // Run on UI Thread to initialize WebView and print
        (context as? Activity)?.runOnUiThread {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val employeeCleanName = empName.replace(" ", "_")
                    val jobName = "كشف_حساب_$employeeCleanName"

                    val printAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        webView.createPrintDocumentAdapter(jobName)
                    } else {
                        @Suppress("DEPRECATION")
                        webView.createPrintDocumentAdapter()
                    }

                    printManager.print(
                        jobName,
                        printAdapter,
                        PrintAttributes.Builder().build()
                    )
                    Log.d("PdfPrintHelper", "PDF_SUCCESS")
                    Toast.makeText(context, "جاري فتح كشف الحساب ونظام الطباعة / PDF...", Toast.LENGTH_SHORT).show()
                    onSuccess?.invoke()
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }

    fun printWorkerBalancesReport(
        context: Context,
        workerBalances: List<com.example.viewmodel.WorkerBalanceItem>,
        cutoffDateMs: Long? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
        val dateString = sdf.format(Date())
        val dateSdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val cutoffDateStr = cutoffDateMs?.let { dateSdf.format(Date(it)) }

        val htmlContent = buildString {
            append("""
                <!DOCTYPE html>
                <html dir="rtl" lang="ar">
                <head>
                    <meta charset="UTF-8">
                    <title>كشف أرصدة العمال والموظفين</title>
                    <style>
                        @page {
                            size: A4;
                            margin: 8mm;
                        }
                        body {
                            font-family: 'Segoe UI', Tahoma, Arial, sans-serif;
                            color: #0f172a;
                            background-color: #ffffff;
                            margin: 0;
                            padding: 6px;
                            direction: rtl;
                            font-size: 11px;
                            line-height: 1.35;
                        }
                        .header-container {
                            border-bottom: 2px solid #059669;
                            padding-bottom: 6px;
                            margin-bottom: 12px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }
                        .app-title {
                            font-size: 18px;
                            font-weight: bold;
                            color: #059669;
                            margin: 0;
                        }
                        .report-subtitle {
                            font-size: 11px;
                            color: #475569;
                            margin: 3px 0 0 0;
                        }
                        .summary-box {
                            background-color: #f8fafc;
                            border: 1px solid #cbd5e1;
                            border-radius: 8px;
                            padding: 8px 12px;
                            margin-bottom: 12px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }
                        table {
                            width: 100%;
                            border-collapse: collapse;
                            margin-top: 4px;
                            margin-bottom: 16px;
                        }
                        th {
                            background-color: #059669;
                            color: #ffffff;
                            font-weight: bold;
                            padding: 8px 6px;
                            border: 1px solid #047857;
                            text-align: center;
                            font-size: 11px;
                        }
                        td {
                            padding: 8px 6px;
                            border: 1px solid #e2e8f0;
                            text-align: center;
                            font-size: 11px;
                        }
                        tr:nth-child(even) {
                            background-color: #f8fafc;
                        }
                        .footer-container {
                            margin-top: 24px;
                            border-top: 1px dashed #cbd5e1;
                            padding-top: 8px;
                            display: flex;
                            justify-content: space-between;
                            font-size: 10px;
                            color: #64748b;
                        }
                        .badge-positive {
                            color: #047857;
                            background-color: #ecfdf5;
                            border: 1px solid #a7f3d0;
                            padding: 4px 10px;
                            border-radius: 6px;
                            font-weight: bold;
                            display: inline-block;
                        }
                        .badge-negative {
                            color: #b91c1c;
                            background-color: #fef2f2;
                            border: 1px solid #fecaca;
                            padding: 4px 10px;
                            border-radius: 6px;
                            font-weight: bold;
                            display: inline-block;
                        }
                        .badge-zero {
                            color: #4b5563;
                            background-color: #f3f4f6;
                            border: 1px solid #e5e7eb;
                            padding: 4px 10px;
                            border-radius: 6px;
                            font-weight: bold;
                            display: inline-block;
                        }
                    </style>
                </head>
                <body>
                    <div class="header-container">
                        <div>
                            <h1 class="app-title">حساب العامل الذكي 📱</h1>
                            <p class="report-subtitle">كشف أرصدة وحسابات العمال والموظفين</p>
                        </div>
                        <div style="text-align: left; font-size: 10px; color: #475569;">
                            <div>📅 تاريخ إصدار الكشف: $dateString</div>
                            ${if (cutoffDateStr != null) """<div style="color: #059669; font-weight: bold; margin-top: 2px;">⏳ الأرصدة محسوبة حتى: $cutoffDateStr</div>""" else """<div>⏳ الأرصدة: حتى تاريخ اليوم</div>"""}
                            <div>👥 عدد العمال المشمولين: ${workerBalances.size}</div>
                        </div>
                    </div>

                    <div class="summary-box">
                        <span style="font-weight: bold; color: #1e293b;">📋 كشف تفصيلي بالأرصدة المستحقة ${if (cutoffDateStr != null) "حتى نهاية ($cutoffDateStr)" else "لكل عامل على حِدة"}:</span>
                        <span style="font-size: 10px; color: #64748b;">(مستند مالي رسمي)</span>
                    </div>

                    <table>
                        <thead>
                            <tr>
                                <th style="width: 35px;">م</th>
                                <th>اسم العامل / الموظف</th>
                                <th style="width: 100px;">الراتب الشهري</th>
                                <th style="width: 100px;">تاريخ بدء الحساب</th>
                                <th style="width: 160px;">${if (cutoffDateStr != null) "صافي الرصيد حتى $cutoffDateStr" else "صافي الرصيد والحالة"}</th>
                            </tr>
                        </thead>
                        <tbody>
            """.trimIndent())

            if (workerBalances.isEmpty()) {
                append("""
                    <tr>
                        <td colspan="5" style="padding: 16px; color: #64748b;">لا توجد حسابات عمال مسجلة حالياً.</td>
                    </tr>
                """.trimIndent())
            } else {
                workerBalances.forEachIndexed { index, worker ->
                    val startDateStr = dateSdf.format(Date(worker.startWorkDate))
                    val salaryStr = if (worker.monthlySalary > 0) "${worker.monthlySalary.toInt()} ${worker.currency}" else "غير محدد"
                    val balanceHtml = when {
                        worker.netBalance > 0.001 -> {
                            val amt = String.format(Locale("ar"), "%,d", worker.netBalance.toInt())
                            """<span class="badge-positive">له: $amt ${worker.currency}</span>"""
                        }
                        worker.netBalance < -0.001 -> {
                            val amt = String.format(Locale("ar"), "%,d", Math.abs(worker.netBalance).toInt())
                            """<span class="badge-negative">عليه: $amt ${worker.currency}</span>"""
                        }
                        else -> {
                            """<span class="badge-zero">لا يوجد رصيد</span>"""
                        }
                    }

                    append("""
                        <tr>
                            <td>${index + 1}</td>
                            <td style="font-weight: bold; text-align: right; padding-right: 10px; color: #0f172a;">${worker.workerName}</td>
                            <td>$salaryStr</td>
                            <td>$startDateStr</td>
                            <td>$balanceHtml</td>
                        </tr>
                    """.trimIndent())
                }
            }

            append("""
                        </tbody>
                    </table>

                    <div class="footer-container">
                        <span>مستند كشف أرصدة مالي رسمي صادر من تطبيق (حساب العامل الذكي 📱)${if (cutoffDateStr != null) " - حتى تاريخ $cutoffDateStr" else ""}</span>
                        <span>توقيع المحاسب / الإدارة: ............................</span>
                    </div>
                </body>
                </html>
            """.trimIndent())
        }

        (context as? Activity)?.runOnUiThread {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val jobName = if (cutoffDateStr != null) "كشف_أرصدة_العمال_حتى_${cutoffDateStr.replace("/", "-")}" else "كشف_أرصدة_العمال"

                    val printAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        webView.createPrintDocumentAdapter(jobName)
                    } else {
                        @Suppress("DEPRECATION")
                        webView.createPrintDocumentAdapter()
                    }

                    printManager.print(
                        jobName,
                        printAdapter,
                        PrintAttributes.Builder().build()
                    )
                    Log.d("PdfPrintHelper", "PDF_SUCCESS")
                    Toast.makeText(context, "جاري فتح كشف أرصدة العمال ونظام الطباعة / PDF...", Toast.LENGTH_SHORT).show()
                    onSuccess?.invoke()
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }
}

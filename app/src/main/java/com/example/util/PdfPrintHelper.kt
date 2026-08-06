package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
        salaryHistories: List<SalaryHistory> = emptyList()
    ) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
        val dateString = sdf.format(Date())

        val startSdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val startWorkDateStr = startSdf.format(Date(settings.startWorkDate))

        val currency = if (settings.currency.isBlank()) "ريال" else settings.currency

        // Determine effective report start and end timestamps
        val nowMs = System.currentTimeMillis()
        val effectiveStartMs = startDateFilter ?: settings.startWorkDate
        val effectiveEndMs = if (endDateFilter != null) Math.min(endDateFilter + 86400000L - 1L, nowMs) else nowMs

        val startCal = Calendar.getInstance().apply {
            timeInMillis = effectiveStartMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance().apply {
            timeInMillis = effectiveEndMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dailyWage = if (settings.monthlySalary > 0) settings.monthlySalary / 30.0 else 5000.0

        // Filter transactions based on date range & type filter
        val rawFilteredTransactions = transactions.filter { tx ->
            val matchStart = tx.timestamp >= startCal.timeInMillis
            val matchEnd = tx.timestamp <= (endCal.timeInMillis + 86400000L - 1L)
            val matchType = when (transactionTypeFilter) {
                "SPENDING" -> tx.type == "SPENDING"
                "DRAW" -> tx.type == "DRAW"
                "ABSENCE" -> tx.type == "ABSENCE"
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
            val baseMs = priorSettlement?.let { it.timestamp + 1000L } ?: initialStartDateMs

            val priorTxs = transactions.filter { it.timestamp >= baseMs && it.timestamp < startCal.timeInMillis && it.type != "SETTLEMENT" && it.type != "OPENING_BALANCE" }
            val priorSpendings = priorTxs.filter { it.type == "SPENDING" }.sumOf { it.amount }
            val priorDraws = priorTxs.filter { it.type == "DRAW" }.sumOf { it.amount }
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

            val computedOpening = baseOpening + priorEarnedSalary - priorSpendings - priorDraws - priorAbsenceDeduction
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
        var absenceHoursTotal = 0.0
        var absenceDeduction = 0.0

        val preCalcCal = Calendar.getInstance().apply {
            timeInMillis = maxOf(startCal.timeInMillis, effectiveWorkStart)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val preCalcEndMs = Calendar.getInstance().apply {
            timeInMillis = effectiveEndMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

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

            val isWorkDay = dayMidnight >= effectiveWorkStart && (effectiveWorkStop == null || dayMidnight <= effectiveWorkStop)
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
        val totalPeriodEntitlements = totalEarnedSalaryInPeriod + if (reportOpeningBalance != 0.0 && startDateFilter != null && !hasOpeningOrSettlementTx) reportOpeningBalance else if (reportOpeningBalance != 0.0 && startDateFilter == null) reportOpeningBalance else 0.0
        val finalNetBalance = reportOpeningBalance + totalEarnedSalaryInPeriod - periodSpendings - periodDraws - absenceDeduction

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
                                <span class="meta-value" style="color: #1e40af;">${reportOpeningBalance.toInt()} $currency</span>
                            </div>
                        </div>
                    </div>
            """.trimIndent())

            // Header Accounting Calculation Summary Box
            if (showTotalSummary) {
                append("""
                    <div class="summary-box">
                        <div class="summary-title">📊 الخلاصة المالية والتصفية للفترة الحالية ($workedDaysInPeriod يوم عمل):</div>
                        <div class="summary-grid">
                            <div class="step-item">
                                <span style="color: #64748b; display: block; font-size: 8.5px;">إجمالي الصرفيات (-)</span>
                                <span style="font-weight: bold; color: #1d4ed8;">-${periodSpendings.toInt()} $currency</span>
                            </div>
                            <div class="step-item">
                                <span style="color: #64748b; display: block; font-size: 8.5px;">إجمالي السحبيات (-)</span>
                                <span style="font-weight: bold; color: #b45309;">-${periodDraws.toInt()} $currency</span>
                            </div>
                            <div class="step-item">
                                <span style="color: #64748b; display: block; font-size: 8.5px;">خصم الغياب (${absenceDaysCount} يوم) (-)</span>
                                <span style="font-weight: bold; color: #b91c1c;">-${absenceDeduction.toInt()} $currency</span>
                            </div>
                            <div class="step-item">
                                <span style="color: #64748b; display: block; font-size: 8.5px;">مستحق أجر العمل للفترة ($workedDaysInPeriod يوم) (+)</span>
                                <span style="font-weight: bold; color: #047857;">+${totalEarnedSalaryInPeriod.toInt()} $currency</span>
                            </div>
                        </div>

                        <div class="step-final-row">
                            <span>💰 إجمالي الصافي المتبقي الكلي للعامل (شامل الرصيد السابق والأجر اليومي):</span>
                            <span>${finalNetBalance.toInt()} $currency</span>
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
            if (reportOpeningBalance != 0.0 && startDateFilter != null && !hasOpeningOrSettlementTx) {
                append("""
                    <tr style="background-color: #f1f5f9; font-weight: bold;">
                        <td>$reportStartDateStr</td>
                        <td style="text-align: right; padding-right: 6px;">رصيد سابق مترحل حتى بداية الفترة</td>
                        <td>-</td>
                        <td>-</td>
                        <td>-</td>
                        <td style="color: #059669;">+${reportOpeningBalance.toInt()} $currency</td>
                        <td style="color: #1e40af; font-size: 10px;">${runningBalance.toInt()} $currency</td>
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
                val endLoopMs = Calendar.getInstance().apply {
                    timeInMillis = effectiveEndMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                var currentMonthKey = ""
                var monthSpendings = 0.0
                var monthDraws = 0.0
                var monthAbsences = 0.0
                var monthEarned = 0.0

                while (currLoopCal.timeInMillis <= endLoopMs) {
                    val dayMidnight = currLoopCal.timeInMillis
                    val formattedDate = itemSdf.format(Date(dayMidnight))
                    val mKey = monthKeySdf.format(Date(dayMidnight))

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

                    val isWorkDay = dayMidnight >= effectiveWorkStart && (effectiveWorkStop == null || dayMidnight <= effectiveWorkStop)

                    if (dayTxList.isEmpty()) {
                        if (isWorkDay) {
                            // Work day with no special deductions -> standard daily wage earned
                            runningBalance += dayDailyWage
                            monthEarned += dayDailyWage

                            val showBal = if (balanceDisplayMode == "END_OF_REPORT") "-" else "${runningBalance.toInt()} $currency"

                            append("""
                                <tr>
                                    <td>$formattedDate</td>
                                    <td style="text-align: right; padding-right: 6px; color: #334155;">أجر عمل يومي مستحق 💼</td>
                                    <td>-</td>
                                    <td>-</td>
                                    <td>-</td>
                                    <td><span style="color: #047857; font-weight: bold;">+${dayDailyWage.toInt()} $currency</span></td>
                                    <td style="font-weight: bold; color: #1e40af;">$showBal</td>
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
                                    noteLabel = if (noteText.isNotBlank()) noteText else "رصيد افتتاحي سابق / أولي"
                                    earnedCell = "<span style='color: #047857; font-weight: bold;'>+${tx.amount.toInt()} $currency</span>"
                                    runningBalance = tx.amount
                                    // OPENING_BALANCE does not consume the daily wage credit for a work day
                                }
                                "SETTLEMENT" -> {
                                    noteLabel = if (noteText.isNotBlank()) noteText else "🔒 تصفية وإقفال حساب وتدوير رصيد"
                                    earnedCell = "<span style='color: #6b21a8; font-weight: bold;'>تدوير: ${tx.amount.toInt()} $currency</span>"
                                    runningBalance = tx.amount
                                    dailyWageCredited = true
                                }
                                "SPENDING" -> {
                                    noteLabel = if (noteText.isNotBlank()) noteText else "صرفة يومية"
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
                                    noteLabel = if (noteText.isNotBlank()) noteText else "سحبية / سلفة"
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
                                        // Standard daily wage earned, minus absence deduction
                                        runningBalance += (dayDailyWage - absDed)
                                        monthEarned += dayDailyWage
                                        earnedCell = "<span style='color: #047857; font-weight: bold;'>+${dayDailyWage.toInt()} $currency</span>"
                                        dailyWageCredited = true
                                    } else {
                                        runningBalance -= absDed
                                    }
                                }
                            }

                            val showBal = if (balanceDisplayMode == "END_OF_REPORT") "-" else "${runningBalance.toInt()} $currency"

                            append("""
                                <tr>
                                    <td>$formattedDate</td>
                                    <td style="text-align: right; padding-right: 6px;">$noteLabel</td>
                                    <td>$spendingCell</td>
                                    <td>$drawCell</td>
                                    <td>$absenceCell</td>
                                    <td>$earnedCell</td>
                                    <td style="font-weight: bold; color: #1e40af;">$showBal</td>
                                </tr>
                            """.trimIndent())
                        }

                        if (!dailyWageCredited && isWorkDay) {
                            runningBalance += dayDailyWage
                            monthEarned += dayDailyWage
                            dailyWageCredited = true

                            val showBal = if (balanceDisplayMode == "END_OF_REPORT") "-" else "${runningBalance.toInt()} $currency"

                            append("""
                                <tr>
                                    <td>$formattedDate</td>
                                    <td style="text-align: right; padding-right: 6px; color: #334155;">أجر عمل يومي مستحق 💼</td>
                                    <td>-</td>
                                    <td>-</td>
                                    <td>-</td>
                                    <td><span style="color: #047857; font-weight: bold;">+${dayDailyWage.toInt()} $currency</span></td>
                                    <td style="font-weight: bold; color: #1e40af;">$showBal</td>
                                </tr>
                            """.trimIndent())
                        }
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
                            <td style="color: #1e40af;">${runningBalance.toInt()} $currency</td>
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
                        else -> {
                            noteLabel = if (noteText.isNotBlank()) noteText else "تصفية / رصيد"
                            earnedCell = "<span style='color: #047857; font-weight: bold;'>+${tx.amount.toInt()} $currency</span>"
                            runningBalance = tx.amount
                        }
                    }

                    val showBal = if (balanceDisplayMode == "END_OF_REPORT") "-" else "${runningBalance.toInt()} $currency"

                    append("""
                        <tr>
                            <td>$formattedDate</td>
                            <td style="text-align: right; padding-right: 6px;">$noteLabel</td>
                            <td>$spendingCell</td>
                            <td>$drawCell</td>
                            <td>$absenceCell</td>
                            <td>$earnedCell</td>
                            <td style="font-weight: bold; color: #1e40af;">$showBal</td>
                        </tr>
                    """.trimIndent())
                }
            }

            // Final Summary Row at Report End
            append("""
                <tr style="background-color: #ecfdf5; font-size: 10.5px; font-weight: bold; border-top: 2.5px solid #059669;">
                    <td colspan="2" style="text-align: right; color: #065f46; padding: 6px;">إجمالي المستحق والصافي النهائي المتبقي للعامل:</td>
                    <td style="color: #1d4ed8;">-${periodSpendings.toInt()} $currency</td>
                    <td style="color: #b45309;">-${periodDraws.toInt()} $currency</td>
                    <td style="color: #b91c1c;">-${absenceDeduction.toInt()} $currency</td>
                    <td style="color: #047857;">+${totalPeriodEntitlements.toInt()} $currency</td>
                    <td style="color: #1e40af; font-size: 11px; background-color: #d1fae5;">${finalNetBalance.toInt()} $currency</td>
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
                    Toast.makeText(context, "جاري فتح كشف الحساب ونظام الطباعة / PDF...", Toast.LENGTH_SHORT).show()
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }
}

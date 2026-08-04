package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.data.SalarySettings
import com.example.data.Transaction
import com.example.viewmodel.SalaryMetrics
import java.text.SimpleDateFormat
import java.util.*

object PdfPrintHelper {

    fun printSalaryReport(
        context: Context,
        settings: SalarySettings,
        transactions: List<Transaction>,
        metrics: SalaryMetrics,
        startDateFilter: Long? = null,
        endDateFilter: Long? = null,
        showTotalSummary: Boolean = true,
        balanceDisplayMode: String = "AFTER_EACH_TX" // "AFTER_EACH_TX", "END_OF_MONTH", "END_OF_REPORT"
    ) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
        val dateString = sdf.format(Date())

        val startSdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val startWorkDateStr = startSdf.format(Date(settings.startWorkDate))

        val currency = if (settings.currency.isBlank()) "ريال" else settings.currency

        // Filter transactions based on date range
        val filteredTransactions = transactions.filter { tx ->
            val matchStart = startDateFilter == null || tx.timestamp >= startDateFilter
            val matchEnd = endDateFilter == null || tx.timestamp <= (endDateFilter + 86400000L - 1L)
            matchStart && matchEnd
        }.sortedBy { it.timestamp }

        // Filter label text
        val periodLabel = if (startDateFilter != null && endDateFilter != null) {
            "الفترة من ${startSdf.format(Date(startDateFilter))} إلى ${startSdf.format(Date(endDateFilter))}"
        } else if (startDateFilter != null) {
            "الفترة من ${startSdf.format(Date(startDateFilter))}"
        } else if (endDateFilter != null) {
            "الفترة حتى ${startSdf.format(Date(endDateFilter))}"
        } else {
            "كشف حساب شامل لجميع العمليات"
        }

        // Calculate accounting parameters
        val dailyWage = if (settings.monthlySalary > 0) settings.monthlySalary / 30.0 else 5000.0
        val periodSpendings = filteredTransactions.filter { it.type == "SPENDING" }.sumOf { it.amount }
        val periodDraws = filteredTransactions.filter { it.type == "DRAW" }.sumOf { it.amount }
        val absenceCount = filteredTransactions.count { it.type == "ABSENCE" }
        val absenceDeduction = absenceCount * dailyWage

        val monthlySalary = settings.monthlySalary
        val openingBalance = settings.openingBalance
        val netMonthEarnings = monthlySalary - periodSpendings - periodDraws - absenceDeduction
        val finalNetBalance = netMonthEarnings + openingBalance

        val empName = if (settings.employeeName.isBlank()) "حسام الحطامي" else settings.employeeName

        // Build HTML Content
        val htmlContent = buildString {
            append("""
                <!DOCTYPE html>
                <html dir="rtl" lang="ar">
                <head>
                    <meta charset="UTF-8">
                    <title>كشف حساب مالي - $empName</title>
                    <style>
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif, 'Cairo';
                            color: #1e293b;
                            background-color: #ffffff;
                            margin: 20px;
                            padding: 0;
                            direction: rtl;
                        }
                        .header-container {
                            border-bottom: 3px solid #10b981;
                            padding-bottom: 15px;
                            margin-bottom: 20px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }
                        .app-title {
                            font-size: 24px;
                            font-weight: bold;
                            color: #059669;
                            margin: 0;
                        }
                        .report-subtitle {
                            font-size: 14px;
                            color: #64748b;
                            margin: 5px 0 0 0;
                        }
                        .employee-card {
                            background-color: #f8fafc;
                            border: 1px solid #cbd5e1;
                            border-radius: 12px;
                            padding: 16px;
                            margin-bottom: 20px;
                        }
                        .employee-name {
                            font-size: 20px;
                            font-weight: bold;
                            color: #0f172a;
                            margin-top: 0;
                            margin-bottom: 10px;
                        }
                        .meta-grid {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 12px;
                            font-size: 14px;
                        }
                        .meta-item {
                            display: flex;
                            justify-content: space-between;
                            border-bottom: 1px dashed #e2e8f0;
                            padding-bottom: 4px;
                        }
                        .meta-label {
                            color: #64748b;
                            font-weight: bold;
                        }
                        .meta-value {
                            color: #0f172a;
                            font-weight: bold;
                        }

                        /* Accounting Calculation Summary Box */
                        .accounting-summary-box {
                            background-color: #ecfdf5;
                            border: 2px solid #059669;
                            border-radius: 12px;
                            padding: 16px;
                            margin-bottom: 25px;
                        }
                        .summary-title {
                            font-size: 16px;
                            font-weight: bold;
                            color: #047857;
                            margin-bottom: 12px;
                            border-bottom: 1px solid #a7f3d0;
                            padding-bottom: 6px;
                        }
                        .summary-grid {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 10px;
                            font-size: 14px;
                            margin-bottom: 12px;
                        }
                        .step-item {
                            display: flex;
                            justify-content: space-between;
                            background-color: #ffffff;
                            padding: 8px 12px;
                            border-radius: 8px;
                            border: 1px solid #d1fae5;
                        }
                        .step-result-row {
                            background-color: #d1fae5;
                            color: #065f46;
                            padding: 10px 14px;
                            border-radius: 8px;
                            font-weight: bold;
                            font-size: 15px;
                            display: flex;
                            justify-content: space-between;
                            margin-top: 8px;
                        }
                        .step-final-row {
                            background-color: #059669;
                            color: #ffffff;
                            padding: 12px 16px;
                            border-radius: 8px;
                            font-weight: bold;
                            font-size: 16px;
                            display: flex;
                            justify-content: space-between;
                            margin-top: 10px;
                        }

                        /* Accounting Table */
                        table {
                            width: 100%;
                            border-collapse: collapse;
                            margin-bottom: 25px;
                        }
                        th {
                            padding: 10px 12px;
                            font-size: 13px;
                            font-weight: bold;
                            text-align: center;
                            border: 1px solid #cbd5e1;
                        }
                        td {
                            padding: 10px 12px;
                            border: 1px solid #e2e8f0;
                            font-size: 13px;
                            text-align: center;
                        }
                        tr:nth-child(even) {
                            background-color: #f8fafc;
                        }
                        
                        /* Footer signature */
                        .footer-container {
                            margin-top: 40px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            font-size: 12px;
                            color: #64748b;
                            border-top: 1px solid #cbd5e1;
                            padding-top: 15px;
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
                            <h1 class="app-title">حساب العامل الذكي 📱</h1>
                            <p class="report-subtitle">كشف حساب مالي رسمي ومستند محاسبي ($periodLabel)</p>
                        </div>
                        <div style="text-align: left; font-size: 12px; color: #64748b;">
                            تاريخ الطباعة: $dateString
                        </div>
                    </div>

                    <div class="employee-card">
                        <div class="employee-name">الموظف / العامل: $empName</div>
                        <div class="meta-grid">
                            <div class="meta-item">
                                <span class="meta-label">تاريخ بدء العمل:</span>
                                <span class="meta-value">$startWorkDateStr</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">الراتب الشهري الأساسي:</span>
                                <span class="meta-value" style="color: #059669;">${monthlySalary.toInt()} $currency</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">معدل الأجر اليومي:</span>
                                <span class="meta-value">${dailyWage.toInt()} $currency / يوم</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">الرصيد الافتتاحي (السابق):</span>
                                <span class="meta-value" style="color: #1e40af;">${openingBalance.toInt()} $currency</span>
                            </div>
                        </div>
                    </div>
            """.trimIndent())

            // Detailed Accounting Monthly Calculation Summary Box
            if (showTotalSummary) {
                append("""
                    <div class="accounting-summary-box">
                        <div class="summary-title">📊 تفصيل وتصفية الحساب الشهري المالي:</div>
                        <div class="summary-grid">
                            <div class="step-item">
                                <span>1. راتب وأجر الشهر الأساسي:</span>
                                <span style="font-weight: bold; color: #059669;">+${monthlySalary.toInt()} $currency</span>
                            </div>
                            <div class="step-item">
                                <span>2. إجمالي الصرفيات (خصم):</span>
                                <span style="font-weight: bold; color: #b91c1c;">-${periodSpendings.toInt()} $currency</span>
                            </div>
                            <div class="step-item">
                                <span>3. إجمالي السحبيات (خصم):</span>
                                <span style="font-weight: bold; color: #b91c1c;">-${periodDraws.toInt()} $currency</span>
                            </div>
                            <div class="step-item">
                                <span>4. خصم الغياب ($absenceCount أيام):</span>
                                <span style="font-weight: bold; color: #b91c1c;">-${absenceDeduction.toInt()} $currency</span>
                            </div>
                        </div>

                        <div class="step-result-row">
                            <span>👈 صافي المتبقي من مستحق هذا الشهر:</span>
                            <span>${monthlySalary.toInt()} - ${periodSpendings.toInt()} - ${periodDraws.toInt()} - ${absenceDeduction.toInt()} = <u>${netMonthEarnings.toInt()} $currency</u></span>
                        </div>

                        ${if (openingBalance != 0.0) """
                        <div class="step-final-row">
                            <span>💰 إجمالي الصافي النهائي الكلي (المتبقي للعامل مع الرصيد السابق):</span>
                            <span>${netMonthEarnings.toInt()} + ${openingBalance.toInt()} = ${finalNetBalance.toInt()} $currency</span>
                        </div>
                        """ else """
                        <div class="step-final-row">
                            <span>💰 إجمالي الصافي النهائي المتبقي للعامل:</span>
                            <span>${netMonthEarnings.toInt()} $currency</span>
                        </div>
                        """}
                    </div>
                """.trimIndent())
            }

            // Table Header with strictly requested columns
            append("""
                <h3>سجل الحركة اليومية للحساب (التاريخ / ملاحظة / عليكم / لكم / الإجمالي) 📋</h3>
                <table>
                    <thead>
                        <tr style="background-color: #0f172a; color: #ffffff;">
                            <th style="width: 18%;">التاريخ</th>
                            <th style="width: 32%;">ملاحظة / البيان</th>
                            <th style="width: 17%; background-color: #991b1b; color: #ffffff;">عليكم (خصم/صرفة/سحبية)</th>
                            <th style="width: 17%; background-color: #059669; color: #ffffff;">لكم (باقي الراتب اليومي/مستحق)</th>
                            <th style="width: 16%; background-color: #1e40af; color: #ffffff;">الإجمالي (الرصيد)</th>
                        </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            var runningBalance = openingBalance

            // Opening Balance Row
            if (openingBalance != 0.0) {
                append("""
                    <tr style="background-color: #f1f5f9; font-weight: bold;">
                        <td>$startWorkDateStr</td>
                        <td>رصيد سابق / رصيد افتتاحي</td>
                        <td style="color: #64748b;">-</td>
                        <td style="color: #059669;">${openingBalance.toInt()} $currency</td>
                        <td style="color: #1e40af; font-size: 14px;">${runningBalance.toInt()} $currency</td>
                    </tr>
                """.trimIndent())
            }

            if (filteredTransactions.isEmpty() && openingBalance == 0.0) {
                append("""
                    <tr>
                        <td colspan="5" style="text-align: center; color: #94a3b8; padding: 30px;">
                            لا توجد أي عمليات مسجلة في هذه الفترة المحددة.
                        </td>
                    </tr>
                """.trimIndent())
            } else {
                val itemSdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))

                for (i in filteredTransactions.indices) {
                    val tx = filteredTransactions[i]
                    val formattedDate = itemSdf.format(Date(tx.timestamp))

                    val noteText = tx.voiceText?.takeIf { it.isNotBlank() } ?: ""

                    var aleikumStr = "-"
                    var lakumStr = "-"
                    var noteLabel = ""

                    when (tx.type) {
                        "SPENDING" -> {
                            noteLabel = if (noteText.isNotBlank()) "صرفة: $noteText" else "صرفة يومية"
                            aleikumStr = "${tx.amount.toInt()} $currency"
                            lakumStr = "${dailyWage.toInt()} $currency"
                            runningBalance += (dailyWage - tx.amount)
                        }
                        "DRAW" -> {
                            noteLabel = if (noteText.isNotBlank()) "سحبيّة: $noteText" else "سحبيّة نقداً"
                            aleikumStr = "${tx.amount.toInt()} $currency"
                            lakumStr = "-"
                            runningBalance -= tx.amount
                        }
                        "ABSENCE" -> {
                            noteLabel = if (noteText.isNotBlank()) "خصم غياب: $noteText" else "تسجيل غياب يوم"
                            aleikumStr = "${dailyWage.toInt()} $currency"
                            lakumStr = "-"
                            runningBalance -= dailyWage
                        }
                    }

                    append("""
                        <tr>
                            <td>$formattedDate</td>
                            <td style="text-align: right; font-weight: 500;">$noteLabel</td>
                            <td style="font-weight: bold; color: #991b1b;">$aleikumStr</td>
                            <td style="font-weight: bold; color: #059669;">$lakumStr</td>
                            <td style="font-weight: bold; color: #1e40af;">${runningBalance.toInt()} $currency</td>
                        </tr>
                    """.trimIndent())
                }
            }

            // Final Summary Row
            append("""
                <tr style="background-color: #ecfdf5; font-size: 14px; font-weight: bold; border-top: 3px solid #059669;">
                    <td colspan="2" style="text-align: right; color: #065f46; padding: 12px;">إجمالي صافي الرصيد المتبقي للعامل حتى الآن:</td>
                    <td style="color: #991b1b;">${(periodSpendings + periodDraws + absenceDeduction).toInt()} $currency</td>
                    <td style="color: #059669;">${(monthlySalary + openingBalance).toInt()} $currency</td>
                    <td style="color: #1e40af; font-size: 15px;">${runningBalance.toInt()} $currency</td>
                </tr>
            """.trimIndent())

            append("""
                        </tbody>
                    </table>

                    <div class="footer-container">
                        <span>تم إصدار كشف الحساب رسمياً وبدقة عبر تطبيق حساب العامل الذكي 📱</span>
                        <span>توقيع واستلام العامل: ............................</span>
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
                    Toast.makeText(context, "جاري تحضير كشف الحساب وفتح نافذة الطباعة / تصدير PDF...", Toast.LENGTH_SHORT).show()
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }
}


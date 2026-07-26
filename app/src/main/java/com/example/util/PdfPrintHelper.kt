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
        metrics: SalaryMetrics
    ) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
        val dateString = sdf.format(Date())

        val startSdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val startWorkDateStr = startSdf.format(Date(settings.startWorkDate))

        // Build HTML Content
        val htmlContent = buildString {
            append("""
                <!DOCTYPE html>
                <html dir="rtl" lang="ar">
                <head>
                    <meta charset="UTF-8">
                    <title>كشف الحساب المالي</title>
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
                            margin-bottom: 30px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }
                        .app-title {
                            font-size: 24px;
                            font-weight: bold;
                            color: #10b981;
                            margin: 0;
                        }
                        .report-subtitle {
                            font-size: 14px;
                            color: #64748b;
                            margin: 5px 0 0 0;
                        }
                        .employee-card {
                            background-color: #f8fafc;
                            border: 1px solid #e2e8f0;
                            border-radius: 12px;
                            padding: 20px;
                            margin-bottom: 25px;
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
                            gap: 15px;
                            font-size: 14px;
                        }
                        .meta-item {
                            display: flex;
                            justify-content: space-between;
                            border-bottom: 1px dashed #e2e8f0;
                            padding-bottom: 5px;
                        }
                        .meta-label {
                            color: #64748b;
                            font-weight: bold;
                        }
                        .meta-value {
                            color: #0f172a;
                            font-weight: bold;
                        }
                        
                        /* Metrics Grid Cards */
                        .metrics-container {
                            display: grid;
                            grid-template-columns: 1fr 1fr 1fr 1fr;
                            gap: 15px;
                            margin-bottom: 30px;
                        }
                        .metric-card {
                            border-radius: 10px;
                            padding: 15px;
                            text-align: center;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.02);
                        }
                        .metric-blue {
                            background-color: #eff6ff;
                            border: 1px solid #bfdbfe;
                            color: #1e40af;
                        }
                        .metric-red {
                            background-color: #fef2f2;
                            border: 1px solid #fecaca;
                            color: #991b1b;
                        }
                        .metric-orange {
                            background-color: #fffbeb;
                            border: 1px solid #fde68a;
                            color: #92400e;
                        }
                        .metric-green {
                            background-color: #ecfdf5;
                            border: 1px solid #a7f3d0;
                            color: #065f46;
                        }
                        .metric-title {
                            font-size: 12px;
                            color: #64748b;
                            margin-bottom: 5px;
                            text-transform: uppercase;
                        }
                        .metric-value {
                            font-size: 18px;
                            font-weight: bold;
                        }

                        /* Main Table */
                        table {
                            width: 100%;
                            border-collapse: collapse;
                            margin-bottom: 30px;
                        }
                        th {
                            background-color: #0f172a;
                            color: #ffffff;
                            text-align: right;
                            padding: 12px 15px;
                            font-size: 14px;
                            font-weight: bold;
                        }
                        td {
                            padding: 12px 15px;
                            border-bottom: 1px solid #e2e8f0;
                            font-size: 13px;
                        }
                        tr:nth-child(even) {
                            background-color: #f8fafc;
                        }
                        .badge {
                            display: inline-block;
                            padding: 4px 8px;
                            border-radius: 6px;
                            font-size: 11px;
                            font-weight: bold;
                            text-align: center;
                        }
                        .badge-spending {
                            background-color: #fecaca;
                            color: #991b1b;
                        }
                        .badge-draw {
                            background-color: #fde68a;
                            color: #92400e;
                        }
                        .badge-absence {
                            background-color: #e2e8f0;
                            color: #475569;
                        }
                        
                        /* Footer signature */
                        .footer-container {
                            margin-top: 50px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            font-size: 12px;
                            color: #94a3b8;
                            border-top: 1px solid #e2e8f0;
                            padding-top: 15px;
                        }

                        @media print {
                            body {
                                margin: 0;
                                -webkit-print-color-adjust: exact;
                            }
                            .no-print {
                                display: none;
                            }
                        }
                    </style>
                </head>
                <body>
                    <div class="header-container">
                        <div>
                            <h1 class="app-title">حساب العامل الذكي 📱</h1>
                            <p class="report-subtitle">كشف حساب مالي رسمي ودقيق للموظف</p>
                        </div>
                        <div style="text-align: left; font-size: 12px; color: #64748b;">
                            تاريخ الطباعة: $dateString
                        </div>
                    </div>

                    <div class="employee-card">
                        <div class="employee-name">الموظف: ${if (settings.employeeName.isBlank()) "حسام الحطامي" else settings.employeeName}</div>
                        <div class="meta-grid">
                            <div class="meta-item">
                                <span class="meta-label">تاريخ بدء العمل:</span>
                                <span class="meta-value">$startWorkDateStr</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">الراتب الشهري الأساسي:</span>
                                <span class="meta-value">${settings.monthlySalary.toInt()} ${settings.currency}</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">إجمالي الأيام المنقضية:</span>
                                <span class="meta-value">${metrics.elapsedDays} أيام</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">أيام الغياب غير المدفوعة:</span>
                                <span class="meta-value">${metrics.absenceCount} أيام</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">أيام الشغل الفعلية:</span>
                                <span class="meta-value" style="color: #10b981;">${metrics.workedDays} أيام</span>
                            </div>
                            ${if (settings.openingBalance != 0.0) """
                            <div class="meta-item">
                                <span class="meta-label">حساب سابق (رصيد افتتاحي):</span>
                                <span class="meta-value" style="color: #10b981;">${settings.openingBalance.toInt()} ${settings.currency}</span>
                            </div>
                            """ else ""}
                        </div>
                    </div>

                    <div class="metrics-container">
                        <div class="metric-card metric-blue">
                            <div class="metric-title">الراتب المستحق</div>
                            <div class="metric-value">${metrics.earnedSalary.toInt()} ${settings.currency}</div>
                        </div>
                        <div class="metric-card metric-red">
                            <div class="metric-title">إجمالي المصروفات</div>
                            <div class="metric-value">${metrics.totalSpendings.toInt()} ${settings.currency}</div>
                        </div>
                        <div class="metric-card metric-orange">
                            <div class="metric-title">إجمالي السحبيات</div>
                            <div class="metric-value">${metrics.totalDraws.toInt()} ${settings.currency}</div>
                        </div>
                        <div class="metric-card metric-green">
                            <div class="metric-title">المتبقي الصافي له</div>
                            <div class="metric-value" style="font-size: 20px;">${metrics.netBalance.toInt()} ${settings.currency}</div>
                        </div>
                    </div>

                    <!-- Single Day Analysis & Total Worked Days Summary -->
                    <div style="margin-top: 25px; margin-bottom: 25px; display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
                        <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 15px;">
                            <h4 style="margin-top: 0; margin-bottom: 10px; color: #0f172a; border-bottom: 2px solid #3b82f6; padding-bottom: 5px;">📅 التفاصيل لليوم الواحد:</h4>
                            <div class="meta-item" style="margin-bottom: 8px;">
                                <span class="meta-label">مستحق الراتب لليوم الواحد:</span>
                                <span class="meta-value" style="color: #1e40af;">${metrics.dailyRate.toInt()} ${settings.currency}</span>
                            </div>
                            <div class="meta-item" style="margin-bottom: 8px;">
                                <span class="meta-label">متوسط الصرفيات لليوم الواحد:</span>
                                <span class="meta-value" style="color: #991b1b;">${if (metrics.workedDays > 0) (metrics.totalSpendings / metrics.workedDays).toInt() else 0} ${settings.currency}</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">متوسط السحبيات لليوم الواحد:</span>
                                <span class="meta-value" style="color: #92400e;">${if (metrics.workedDays > 0) (metrics.totalDraws / metrics.workedDays).toInt() else 0} ${settings.currency}</span>
                            </div>
                        </div>
                        <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 15px;">
                            <h4 style="margin-top: 0; margin-bottom: 10px; color: #0f172a; border-bottom: 2px solid #10b981; padding-bottom: 5px;">💸 إجمالي التفاصيل لأيام الشغل كامل:</h4>
                            <div class="meta-item" style="margin-bottom: 8px;">
                                <span class="meta-label">أيام الشغل الفعلية:</span>
                                <span class="meta-value" style="color: #065f46;">${metrics.workedDays} أيام</span>
                            </div>
                            <div class="meta-item" style="margin-bottom: 8px;">
                                <span class="meta-label">إجمالي الصرفيات لكامل المدة:</span>
                                <span class="meta-value" style="color: #991b1b;">${metrics.totalSpendings.toInt()} ${settings.currency}</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">إجمالي السحبيات وسلفيات:</span>
                                <span class="meta-value" style="color: #92400e;">${metrics.totalDraws.toInt()} ${settings.currency}</span>
                            </div>
                        </div>
                    </div>

                    <h3>سجل تفاصيل العمليات اليومية 📋</h3>
                    <table>
                        <thead>
                            <tr>
                                <th style="width: 25%;">التاريخ الوقت</th>
                                <th style="width: 20%;">نوع العملية</th>
                                <th style="width: 20%;">المبلغ</th>
                                <th style="width: 35%;">ملاحظة / كلام صوتي</th>
                            </tr>
                        </thead>
                        <tbody>
            """.trimIndent())

            if (transactions.isEmpty()) {
                append("""
                    <tr>
                        <td colspan="4" style="text-align: center; color: #94a3b8; padding: 30px;">
                            لا توجد أي عمليات مسجلة في هذا الكشف حتى الآن.
                        </td>
                    </tr>
                """.trimIndent())
            } else {
                val itemSdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
                for (tx in transactions) {
                    val formattedTime = itemSdf.format(Date(tx.timestamp))
                    val badgeClass = when (tx.type) {
                        "SPENDING" -> "badge-spending"
                        "DRAW" -> "badge-draw"
                        "ABSENCE" -> "badge-absence"
                        else -> ""
                    }
                    val typeLabel = when (tx.type) {
                        "SPENDING" -> "صرفة"
                        "DRAW" -> "سحبيّة"
                        "ABSENCE" -> "تسجيل غياب"
                        else -> "عملية"
                    }
                    val amountLabel = when (tx.type) {
                        "ABSENCE" -> {
                            val hrs = tx.amount
                            if (hrs == 12.0 || hrs <= 0.0) "يوم كامل" else "${hrs.toInt()} ساعات"
                        }
                        else -> "${tx.amount.toInt()} ${settings.currency}"
                    }
                    val noteText = tx.voiceText ?: "سجلت بالصوت"

                    append("""
                        <tr>
                            <td>$formattedTime</td>
                            <td><span class="badge $badgeClass">$typeLabel</span></td>
                            <td style="font-weight: bold;">$amountLabel</td>
                            <td>$noteText</td>
                        </tr>
                    """.trimIndent())
                }
            }

            append("""
                        </tbody>
                    </table>

                    <div class="footer-container">
                        <span>تم إصدار هذا الكشف تلقائياً وبأمان عبر حساب العامل الذكي</span>
                        <span>توقيع الموظف: ............................</span>
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
                    // Create PrintManager instance
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val employeeCleanName = if (settings.employeeName.isBlank()) "حسام_الحطامي" else settings.employeeName.replace(" ", "_")
                    val jobName = "كشف_حساب_$employeeCleanName"

                    // Get print adapter
                    val printAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        webView.createPrintDocumentAdapter(jobName)
                    } else {
                        @Suppress("DEPRECATION")
                        webView.createPrintDocumentAdapter()
                    }

                    // Print document with standard settings
                    printManager.print(
                        jobName,
                        printAdapter,
                        PrintAttributes.Builder().build()
                    )
                    Toast.makeText(context, "جاري فتح نافذة الطباعة وتصدير الـ PDF...", Toast.LENGTH_SHORT).show()
                }
            }

            // Load generated HTML dynamically
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }
}

package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.SalarySettings
import com.example.data.Transaction
import com.example.ui.theme.*
import com.example.util.PdfPrintHelper
import com.example.viewmodel.SalaryMetrics
import com.example.viewmodel.SalaryViewModel
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class AppColors(
    val bg: Color = DarkSlateBackground,
    val surface: Color = DarkSlateSurface,
    val text: Color = Color.White,
    val textSub: Color = Color.LightGray,
    val textMuted: Color = Color.Gray,
    val border: Color = Color.Gray.copy(alpha = 0.3f)
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryApp(viewModel: SalaryViewModel) {
    val context = LocalContext.current
    val settingsState by viewModel.settings.collectAsState()
    val transactionsState by viewModel.transactions.collectAsState()
    val metricsState by viewModel.metrics.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val registeredUsers by viewModel.registeredUsers.collectAsState()

    val isListening by viewModel.isListening.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val isPlayingAudioId by viewModel.isPlayingAudioId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val speechInputText by viewModel.speechInputText.collectAsState()
    val updateInfoState by viewModel.updateInfo.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(true) }
    var showAddTxDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var isAppUnlocked by remember { mutableStateOf(false) }
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasRecordPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasRecordPermission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
            }
            if (permissions[Manifest.permission.RECORD_AUDIO] == false) {
                Toast.makeText(context, "الرجاء تفعيل صلاحية الميكروفون لاستخدام الإدخال الصوتي", Toast.LENGTH_LONG).show()
            }
        }
    )

    var hasGreeted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
        val permissionsToRequest = mutableListOf<String>()
        if (!hasRecordPermission) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(settingsState) {
        val name = settingsState?.employeeName ?: ""
        if (name.isNotBlank() && !hasGreeted) {
            hasGreeted = true
            val nameToCall = name.trim().split(" ").firstOrNull() ?: ""
            viewModel.speakOutText("مرحباً بك يا $nameToCall")
        }
    }

    val systemInDark = isSystemInDarkTheme()
    val isDark = when (settingsState?.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        "SYSTEM" -> systemInDark
        else -> true
    }

    val appColors = AppColors(
        bg = if (isDark) DarkSlateBackground else Color(0xFFF1F5F9),
        surface = if (isDark) DarkSlateSurface else Color.White,
        text = if (isDark) Color.White else Color(0xFF0F172A),
        textSub = if (isDark) Color.LightGray else Color(0xFF334155),
        textMuted = if (isDark) Color.Gray else Color(0xFF64748B),
        border = if (isDark) Color.Gray.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.5f)
    )

    CompositionLocalProvider(LocalAppColors provides appColors) {
    if (settingsState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppColors.current.bg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = EmeraldGreen)
        }
    } else if (settingsState?.employeeName.isNullOrBlank()) {
        WelcomeLoginScreen(
            registeredUsers = emptyList(),
            onLogin = {},
            onRegister = { name, salary, startDate, currency, standardSpending, dailyReminderEnabled, dailyReminderHour, dailyReminderMinute, openingBalance ->
                hasGreeted = false
                viewModel.updateSalarySettings(
                    salary = salary,
                    startDate = startDate,
                    currency = currency,
                    standardSpending = standardSpending,
                    priorAbsences = 0,
                    priorSpendings = 0.0,
                    priorDraws = 0.0,
                    dailyReminderEnabled = dailyReminderEnabled,
                    dailyReminderHour = dailyReminderHour,
                    dailyReminderMinute = dailyReminderMinute,
                    employeeName = name,
                    voiceEnabled = true,
                    openingBalance = openingBalance
                )
            },
            onDeleteUser = {}
        )
    } else if (settingsState?.securityEnabled == true && !isAppUnlocked) {
        AppLockScreen(
            correctPin = settingsState?.securityPin ?: "",
            biometricEnabled = settingsState?.biometricEnabled ?: false,
            onUnlockSuccess = { isAppUnlocked = true }
        )
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "حساب العامل الذكي",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // Refresh data / Reset
                    IconButton(
                        onClick = { viewModel.announceFinancialStatus() },
                        modifier = Modifier
                            .background(EmeraldGreen.copy(alpha = 0.15f), shape = CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "استمع لكامل التقرير المالي المسموع",
                            tint = EmeraldGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), shape = CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "تعديل إعدادات الراتب والعمل",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalAppColors.current.bg
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTxDialog = true },
                containerColor = EmeraldGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة عملية مخصصة",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = LocalAppColors.current.bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // --- HEADER: Welcome & Date info ---
            WelcomeSection(settingsState ?: SalarySettings())

            val currentCurrency = (settingsState ?: SalarySettings()).currency

            // --- VISUAL CENTRAL BALANCE DIAL ---
            BalanceDial(
                metrics = metricsState,
                currency = currentCurrency,
                onSpeakClick = { viewModel.announceFinancialStatus() }
            )

            // --- CORE STATS SECTION (Color Coded Cards) ---
            StatsSection(metrics = metricsState, currency = currentCurrency)

            // --- MICROPHONE VOICE RECORDER BUTTON (GIANT PULSING BTN) ---
            VoiceInputSection(
                isListening = isListening,
                isRecording = isRecordingVoice,
                speechInput = speechInputText,
                onRecordClick = {
                    if (hasRecordPermission) {
                        if (isRecordingVoice) {
                            viewModel.stopVoiceRecordingForCommand()
                        } else {
                            viewModel.startVoiceRecordingForCommand()
                        }
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                }
            )

            // Error or Status toast-like label
            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CrimsonRedDark.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- ONE-TAP QUICK PRESET ACTIONS (No Typing, No talking) ---
            QuickActionsSection(
                settings = settingsState ?: SalarySettings(),
                onSpendingClick = { amount -> viewModel.addTransaction("SPENDING", amount, null, "صرفة") },
                onAbsenceClick = { viewModel.addTransaction("ABSENCE", 12.0, null, "غياب يوم كامل") }
            )

            // --- RECENT TRANSACTIONS FEED (Timeline) ---
            TransactionsFeedSection(
                transactions = transactionsState,
                currency = currentCurrency,
                isPlayingId = isPlayingAudioId,
                onPlayVoice = { tx -> viewModel.playTransactionVoiceNote(tx) },
                onStopAudio = { viewModel.stopAudioPlayback() },
                onCardClick = { tx -> editingTransaction = tx }
            )

            // --- SHARE BUTTON (Reset button completely removed) ---
            ReportActionSection(
                onShareClick = {
                    val rText = viewModel.getReportText()
                    val empName = settingsState?.employeeName ?: "محمد خالد العريفي"
                    shareReport(context, rText, empName)
                },
                onPrintClick = {
                    val settings = settingsState ?: SalarySettings()
                    val txList = transactionsState
                    val metrics = metricsState
                    PdfPrintHelper.printSalaryReport(context, settings, txList, metrics)
                }
            )
        }
    }
    }

    // --- SETTINGS DIALOG ---
    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settingsState ?: SalarySettings(),
            onDismiss = { showSettingsDialog = false },
            onSave = { salary, startDate, currency, standardSpending, priorAbsences, priorSpendings, priorDraws, dailyReminderEnabled, dailyReminderHour, dailyReminderMinute, employeeName, voiceEnabled, openingBalance, securityEnabled, securityPin, biometricEnabled, autoAddDailySpending, themeMode ->
                viewModel.updateSalarySettings(
                    salary = salary,
                    startDate = startDate,
                    currency = currency,
                    standardSpending = standardSpending,
                    priorAbsences = priorAbsences,
                    priorSpendings = priorSpendings,
                    priorDraws = priorDraws,
                    dailyReminderEnabled = dailyReminderEnabled,
                    dailyReminderHour = dailyReminderHour,
                    dailyReminderMinute = dailyReminderMinute,
                    employeeName = employeeName,
                    voiceEnabled = voiceEnabled,
                    openingBalance = openingBalance,
                    securityEnabled = securityEnabled,
                    securityPin = securityPin,
                    biometricEnabled = biometricEnabled,
                    autoAddDailySpending = autoAddDailySpending,
                    themeMode = themeMode
                )
                showSettingsDialog = false
            }
        )
    }

    // --- UPDATE NOTIFICATION DIALOG ---
    if (updateInfoState.hasUpdate && showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Text(
                    text = "📢 يتوفر تحديث جديد للتطبيق!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "الإصدار الجديد: ${updateInfoState.versionName}",
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                    if (updateInfoState.changelog.isNotBlank()) {
                        Text(
                            text = "ما الجديد في هذا الإصدار:",
                            fontWeight = FontWeight.SemiBold,
                            color = LocalAppColors.current.textSub
                        )
                        Text(
                            text = updateInfoState.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textMuted
                        )
                    }
                    Text(
                        text = "يرجى تحميل النسخة الجديدة للحصول على آخر الميزات والتحسينات المحدثة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateInfoState.updateUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
                        }
                        showUpdateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("تحميل التحديث", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("لاحقاً", color = Color.Gray)
                }
            },
            containerColor = LocalAppColors.current.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // --- ADD TRANSACTION DIALOG ---
    if (showAddTxDialog) {
        AddTransactionDialog(
            settings = settingsState ?: SalarySettings(),
            onDismiss = { showAddTxDialog = false },
            onSave = { type, amount, note, timestamp ->
                viewModel.addTransaction(type, amount, null, note, timestamp)
                showAddTxDialog = false
            }
        )
    }

    // --- EDIT TRANSACTION DIALOG ---
    editingTransaction?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            currency = settingsState?.currency ?: "ريال يمني",
            onDismiss = { editingTransaction = null },
            onSave = { updatedTx ->
                viewModel.updateTransaction(updatedTx)
            },
            onDelete = { txToDelete ->
                viewModel.deleteTransaction(txToDelete)
            }
        )
    }
    }
}

@Composable
fun WelcomeSection(settings: SalarySettings) {
    val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ar"))
    val dateString = sdf.format(Date())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            val employeeName = if (settings.employeeName.isBlank()) "محمد خالد العريفي" else settings.employeeName
            Text(
                text = "مرحباً يا $employeeName 👋",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Right
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                textAlign = TextAlign.Right
            )
        }
    }
}

@Composable
fun ImageBannerSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.financial_safe_banner),
                contentDescription = "الخزنة المالية",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gentle overlay for luxury finish
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
            Text(
                text = "حافظ على فلوسك وراتبك بأمان",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun BalanceDial(
    metrics: SalaryMetrics,
    currency: String,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val dialSize = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - dialSize) / 2, (size.height - dialSize) / 2)
            val rectSize = Size(dialSize, dialSize)

            // Background arc (full circle)
            drawArc(
                color = Color.LightGray.copy(alpha = 0.1f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Calculate Angles
            val total = metrics.earnedSalary
            if (total > 0f) {
                val spentProportion = (metrics.totalSpendings / total).toFloat()
                val drawnProportion = (metrics.totalDraws / total).toFloat()
                val remainingProportion = (metrics.netBalance / total).toFloat().coerceIn(0f, 1f)

                var currentAngle = -90f

                // 1. Remaining Arc (Green)
                if (remainingProportion > 0) {
                    drawArc(
                        color = EmeraldGreen,
                        startAngle = currentAngle,
                        sweepAngle = remainingProportion * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = rectSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    currentAngle += remainingProportion * 360f
                }

                // 2. Spent Arc (Red)
                if (spentProportion > 0) {
                    drawArc(
                        color = CrimsonRed,
                        startAngle = currentAngle,
                        sweepAngle = spentProportion * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = rectSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    currentAngle += spentProportion * 360f
                }

                // 3. Drawn Arc (Orange)
                if (drawnProportion > 0) {
                    drawArc(
                        color = AmberOrange,
                        startAngle = currentAngle,
                        sweepAngle = drawnProportion * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = rectSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            } else {
                // If no salary earned yet, just draw simple blue arc
                drawArc(
                    color = EarnedBlue,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = rectSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "المتبقي لك",
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${metrics.netBalance.toInt()}",
                style = MaterialTheme.typography.displayMedium,
                color = EmeraldGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = currency,
                style = MaterialTheme.typography.titleMedium,
                color = EmeraldGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Giant sound button
            IconButton(
                onClick = onSpeakClick,
                modifier = Modifier
                    .size(54.dp)
                    .background(EmeraldGreen.copy(alpha = 0.2f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "استمع للرصيد المالي بصوت عالي",
                    tint = EmeraldGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun StatsSection(metrics: SalaryMetrics, currency: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (metrics.openingBalance != 0.0) {
            StatCard(
                title = "رصيد سابق / حساب مستحق لك",
                value = "${metrics.openingBalance.toInt()}",
                unit = currency,
                color = EmeraldGreen,
                icon = Icons.Default.AccountBalanceWallet,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Earned Salary (Blue)
            StatCard(
                title = "الراتب المستحق",
                value = "${metrics.earnedSalary.toInt()}",
                unit = currency,
                color = EarnedBlue,
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f)
            )
            // Absence days (Grey)
            val absenceValueStr = if (metrics.absenceCount % 1.0 == 0.0) "${metrics.absenceCount.toInt()}" else String.format(Locale.US, "%.1f", metrics.absenceCount)
            StatCard(
                title = "أيام الغياب",
                value = absenceValueStr,
                unit = "يوم غياب",
                color = Color.Gray,
                icon = Icons.Default.Cancel,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Spendings (Red)
            StatCard(
                title = "المصروفات اليومية",
                value = "${metrics.totalSpendings.toInt()}",
                unit = currency,
                color = CrimsonRed,
                icon = Icons.Default.TrendingDown,
                modifier = Modifier.weight(1f)
            )
            // Draws/Advances (Orange)
            StatCard(
                title = "السحبيات السلف",
                value = "${metrics.totalDraws.toInt()}",
                unit = currency,
                color = AmberOrange,
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    unit: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Right
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (unit.isNotEmpty()) {
                    Text(
                        text = "$unit ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.Right
                )
            }
        }
    }
}

@Composable
fun VoiceInputSection(
    isListening: Boolean,
    isRecording: Boolean,
    speechInput: String,
    onRecordClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) CrimsonRedDark.copy(alpha = 0.15f) else EmeraldGreen.copy(alpha = 0.1f)
        ),
        border = BorderStroke(2.dp, if (isRecording) CrimsonRed else EmeraldGreen),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isRecording) "جاري التسجيل... تكلم الآن" else "تكلم لتسجيل صرفية، سحبية، أو غياب",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Giant pulsing mic button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        if (isRecording) CrimsonRed.copy(alpha = 0.3f) else EmeraldGreen.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .clickable { onRecordClick() }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(70.dp)
                        .background(if (isRecording) CrimsonRed else EmeraldGreen, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "زر الإدخال الصوتي",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            AnimatedVisibility(visible = speechInput.isNotEmpty()) {
                Text(
                    text = "سمعتك تقول: \"$speechInput\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun QuickActionsSection(
    settings: SalarySettings,
    onSpendingClick: (Double) -> Unit,
    onAbsenceClick: () -> Unit
) {
    val cur = settings.currency
    val standardSpending = settings.standardSpendingAmount
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "إضافة سريعة بلمسة واحدة 👇",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.End)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    title = "صرفة اليوم المعتادة",
                    amountText = "+${standardSpending.toInt()} $cur",
                    color = CrimsonRed,
                    icon = Icons.Default.ShoppingCart,
                    onClick = { onSpendingClick(standardSpending) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    title = "غياب يوم كامل",
                    amountText = "١٢ ساعة",
                    color = Color.Gray,
                    icon = Icons.Default.EventBusy,
                    onClick = onAbsenceClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    amountText: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(135.dp)
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = BorderStroke(2.dp, color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TransactionsFeedSection(
    transactions: List<Transaction>,
    currency: String,
    isPlayingId: Int?,
    onPlayVoice: (Transaction) -> Unit,
    onStopAudio: () -> Unit,
    onCardClick: (Transaction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "شريط العمليات الأخيرة 📜",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.End)
        )

        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSlateSurface)
            ) {
                Text(
                    text = "لا توجد أي عمليات مسجلة حتى الآن",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { tx ->
                    TransactionItem(
                        transaction = tx,
                        currency = currency,
                        isPlaying = isPlayingId == tx.id,
                        onPlayVoice = { onPlayVoice(tx) },
                        onStopAudio = onStopAudio,
                        onCardClick = { onCardClick(tx) }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    currency: String,
    isPlaying: Boolean,
    onPlayVoice: () -> Unit,
    onStopAudio: () -> Unit,
    onCardClick: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
    val dateStr = sdf.format(Date(transaction.timestamp))

    val color = when (transaction.type) {
        "SPENDING" -> CrimsonRed
        "DRAW" -> AmberOrange
        "ABSENCE" -> Color.Gray
        else -> EarnedBlue
    }

    val icon = when (transaction.type) {
        "SPENDING" -> Icons.Default.ShoppingCart
        "DRAW" -> Icons.Default.Payments
        "ABSENCE" -> Icons.Default.EventBusy
        else -> Icons.Default.CheckCircle
    }

    val typeLabel = when (transaction.type) {
        "SPENDING" -> "صرفة مصروف"
        "DRAW" -> "سحبة سلفة"
        "ABSENCE" -> "تسجيل غياب"
        else -> "عملية"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Voice player if note exists
            if (transaction.voiceNotePath != null) {
                IconButton(
                    onClick = { if (isPlaying) onStopAudio() else onPlayVoice() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isPlaying) CrimsonRed.copy(alpha = 0.2f) else EmeraldGreen.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "تشغيل المذكرة الصوتية للعملية",
                        tint = if (isPlaying) CrimsonRed else EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Details
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val amountLabel = when (transaction.type) {
                        "ABSENCE" -> {
                            val hrs = transaction.amount
                            if (hrs == 12.0 || hrs <= 0.0) "غياب يوم كامل" else "غياب ${hrs.toInt()} ساعات"
                        }
                        else -> "${transaction.amount.toInt()} $currency"
                    }
                    Text(
                        text = amountLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (transaction.voiceText != null) {
                    Text(
                        text = transaction.voiceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Right
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                // Date & Time is ALWAYS shown here!
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Right
                )
            }

            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ReportActionSection(
    onShareClick: () -> Unit,
    onPrintClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Core print & export PDF action (full width, premium design)
        Button(
            onClick = onPrintClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("print_pdf_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldGreen,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = "طباعة الكشف كـ PDF",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "طباعة وتصدير الكشف كـ PDF",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Export/Share button (now full-width)
        Button(
            onClick = onShareClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("share_button"),
            colors = ButtonDefaults.buttonColors(containerColor = EarnedBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "مشاركة نصية",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AddTransactionDialog(
    settings: SalarySettings,
    onDismiss: () -> Unit,
    onSave: (type: String, amount: Double, note: String, timestamp: Long) -> Unit
) {
    var selectedType by remember { mutableStateOf("DRAW") } // DRAW, SPENDING, ABSENCE
    var amountInput by remember(selectedType) { 
        mutableStateOf(
            if (selectedType == "SPENDING") settings.standardSpendingAmount.toInt().toString() else ""
        ) 
    }
    var noteInput by remember { mutableStateOf("") }
    
    // Auto-fill spending note to "صرفة" as requested
    LaunchedEffect(selectedType) {
        if (selectedType == "SPENDING") {
            noteInput = "صرفة"
        } else {
            noteInput = ""
        }
    }
    
    // Absence variables
    var absenceIsFullDay by remember { mutableStateOf(true) }
    var absenceHoursInput by remember { mutableStateOf("2") } // Default 2 hours

    // Speech recognition for Note input
    val context = LocalContext.current
    var isListeningForNote by remember { mutableStateOf(false) }
    val dialogSpeechHelper = remember {
        com.example.util.SpeechRecognizerHelper(
            context = context,
            onResult = { result ->
                noteInput = result
                isListeningForNote = false
            },
            onError = { _ ->
                isListeningForNote = false
            },
            onListeningStateChange = { listening ->
                isListeningForNote = listening
            }
        )
    }
    
    DisposableEffect(Unit) {
        onDispose {
            dialogSpeechHelper.destroy()
        }
    }

    // Date picker state for backdating (e.g. adding tasks for past days)
    val calendar = remember { Calendar.getInstance() }
    var transactionDate by remember { mutableStateOf(calendar.timeInMillis) }
    val dateSdf = remember { SimpleDateFormat("yyyy/MM/dd", Locale("ar")) }
    
    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(Calendar.YEAR, year)
                selectedCal.set(Calendar.MONTH, month)
                selectedCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                transactionDate = selectedCal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "📝 تسجيل عملية جديدة",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                // Type Tab-like buttons
                Text(
                    text = "نوع العملية:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(
                        Triple("ABSENCE", "غياب", Color.Gray),
                        Triple("SPENDING", "صرفة", CrimsonRed),
                        Triple("DRAW", "سحبة/سلفة", AmberOrange)
                    )
                    types.forEach { (type, label, color) ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .background(
                                    if (isSelected) color else color.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) Color.White else color.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedType = type
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else color
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (selectedType != "ABSENCE") {
                    // Amount Field
                    Text(
                        text = if (selectedType == "DRAW") "مبلغ السحبيّة السلفة (${settings.currency}):" else "مبلغ الصرفة (${settings.currency}):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        placeholder = {
                            Text(
                                text = if (selectedType == "SPENDING") "${settings.standardSpendingAmount.toInt()}" else "أدخل المبلغ هنا...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (selectedType == "SPENDING") CrimsonRed else AmberOrange,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Note Field
                    Text(
                        text = "ملاحظة وبيان العملية (اختياري - لأيش):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        placeholder = {
                            Text(
                                text = if (selectedType == "SPENDING") "مثال: صرفة غداء، باص..." else "مثال: سلفة لمصاريف العيد...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (isListeningForNote) {
                                        dialogSpeechHelper.stopListening()
                                    } else {
                                        dialogSpeechHelper.startListening()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isListeningForNote) Icons.Default.Mic else Icons.Default.MicNone,
                                    contentDescription = "تحدث لكتابة الملاحظة",
                                    tint = if (isListeningForNote) CrimsonRed else Color.LightGray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (selectedType == "SPENDING") CrimsonRed else AmberOrange,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    AnimatedVisibility(visible = isListeningForNote) {
                        Text(
                            text = "جاري الاستماع... تكلم الآن 🎤",
                            color = CrimsonRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                } else {
                    // Absence Option
                    Text(
                        text = "مدة الغياب:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val absenceTypes = listOf(
                            Pair(true, "يوم كامل (١٢ ساعة)"),
                            Pair(false, "ساعات محددة")
                        )
                        absenceTypes.forEach { (isFull, label) ->
                            val isSel = absenceIsFullDay == isFull
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(
                                        if (isSel) Color.Gray else Color.Gray.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { absenceIsFullDay = isFull },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color.LightGray
                                )
                            }
                        }
                    }

                    if (!absenceIsFullDay) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "عدد ساعات الغياب (يوم الشغل ١٢ ساعة):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        OutlinedTextField(
                            value = absenceHoursInput,
                            onValueChange = { absenceHoursInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            placeholder = {
                                Text(
                                    text = "مثال: ٣ ساعات",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Gray,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    // Absence Note Field
                    Text(
                        text = "ملاحظة أو سبب الغياب (اختياري):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        placeholder = {
                            Text(
                                text = "مثال: عذر مرضي، غياب جزئي...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (isListeningForNote) {
                                        dialogSpeechHelper.stopListening()
                                    } else {
                                        dialogSpeechHelper.startListening()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isListeningForNote) Icons.Default.Mic else Icons.Default.MicNone,
                                    contentDescription = "تحدث لكتابة الملاحظة",
                                    tint = if (isListeningForNote) CrimsonRed else Color.LightGray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    AnimatedVisibility(visible = isListeningForNote) {
                        Text(
                            text = "جاري الاستماع... تكلم الآن 🎤",
                            color = CrimsonRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date Picker Field for backdating
                Text(
                    text = "تاريخ العملية (تعديل لإضافة الأيام السابقة):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                        .clickable { datePickerDialog.show() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "تحديد التاريخ",
                        tint = EmeraldGreen
                    )
                    Text(
                        text = dateSdf.format(Date(transactionDate)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء", color = Color.LightGray)
                    }

                    Button(
                        onClick = {
                            if (selectedType == "ABSENCE") {
                                val hrs = if (absenceIsFullDay) 12.0 else (absenceHoursInput.toDoubleOrNull() ?: 2.0)
                                val note = if (noteInput.isBlank()) {
                                    if (absenceIsFullDay) "غياب يوم كامل" else "غياب جزئي ${hrs.toInt()} ساعات"
                                } else noteInput
                                onSave("ABSENCE", hrs, note, transactionDate)
                            } else {
                                val amt = amountInput.toDoubleOrNull() ?: if (selectedType == "SPENDING") settings.standardSpendingAmount else 0.0
                                val defaultNote = if (selectedType == "SPENDING") "صرفة" else "سحبة سلفة"
                                val note = if (noteInput.isBlank()) defaultNote else noteInput
                                if (amt > 0.0) {
                                    onSave(selectedType, amt, note, transactionDate)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedType) {
                                "SPENDING" -> CrimsonRed
                                "DRAW" -> AmberOrange
                                else -> Color.Gray
                            }
                        ),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("حفظ العملية", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentSettings: SalarySettings,
    onDismiss: () -> Unit,
    onSave: (Double, Long, String, Double, Int, Double, Double, Boolean, Int, Int, String, Boolean, Double, Boolean, String, Boolean, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    var salaryInput by remember { mutableStateOf(currentSettings.monthlySalary.toInt().toString()) }
    var currencyInput by remember { mutableStateOf(currentSettings.currency) }
    var standardSpendingInput by remember { mutableStateOf(currentSettings.standardSpendingAmount.toInt().toString()) }
    var priorAbsencesInput by remember { mutableStateOf(currentSettings.priorAbsenceCount.toString()) }
    var priorSpendingsInput by remember { mutableStateOf(currentSettings.priorSpendingsAmount.toInt().toString()) }
    var priorDrawsInput by remember { mutableStateOf(currentSettings.priorDrawsAmount.toInt().toString()) }
    var employeeNameInput by remember { mutableStateOf(currentSettings.employeeName) }
    var openingBalanceInput by remember { mutableStateOf(currentSettings.openingBalance.toInt().toString()) }

    // Voice State
    var voiceEnabled by remember { mutableStateOf(currentSettings.voiceEnabled) }

    // Reminder States
    var dailyReminderEnabled by remember { mutableStateOf(currentSettings.dailyReminderEnabled) }
    var dailyReminderHour by remember { mutableStateOf(currentSettings.dailyReminderHour) }
    var dailyReminderMinute by remember { mutableStateOf(currentSettings.dailyReminderMinute) }

    // Security States
    var securityEnabled by remember { mutableStateOf(currentSettings.securityEnabled) }
    var securityPinInput by remember { mutableStateOf(currentSettings.securityPin) }
    var biometricEnabled by remember { mutableStateOf(currentSettings.biometricEnabled) }

    // Auto Daily Spending & Theme Modes State
    var autoAddDailySpending by remember { mutableStateOf(currentSettings.autoAddDailySpending) }
    var themeMode by remember { mutableStateOf(currentSettings.themeMode) }

    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateInMillis by remember { mutableStateOf(currentSettings.startWorkDate) }

    val timePickerDialog = remember {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minuteOfHour ->
                dailyReminderHour = hourOfDay
                dailyReminderMinute = minuteOfHour
            },
            dailyReminderHour,
            dailyReminderMinute,
            false // use AM/PM style format
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "⚙️ الإعدادات والتحكم بالتطبيق",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalAppColors.current.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                var selectedTab by remember { mutableStateOf(0) } // 0 = User Info, 1 = App Settings & Developer Info
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedTab = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) EmeraldGreen else LocalAppColors.current.surface.copy(alpha = 0.5f),
                            contentColor = if (selectedTab == 1) Color.White else LocalAppColors.current.text
                        ),
                        modifier = Modifier.weight(1f).border(1.dp, if (selectedTab == 1) Color.Transparent else LocalAppColors.current.border, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إعدادات البرنامج", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = { selectedTab = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 0) EmeraldGreen else LocalAppColors.current.surface.copy(alpha = 0.5f),
                            contentColor = if (selectedTab == 0) Color.White else LocalAppColors.current.text
                        ),
                        modifier = Modifier.weight(1f).border(1.dp, if (selectedTab == 0) Color.Transparent else LocalAppColors.current.border, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("معلومات المستخدم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                if (selectedTab == 0) {
                    // --- TAB 0: USER PROFILE & FINANCIALS ---
                    // Employee Name Field
                    Text(
                        text = "اسم الموظف الكريم:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = employeeNameInput,
                        onValueChange = { employeeNameInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        placeholder = { Text("مثلاً: محمد خالد العريفي", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Salary Field
                    Text(
                        text = "الراتب الشهري الأساسي:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = salaryInput,
                        onValueChange = { salaryInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Currency Symbol Field
                    Text(
                        text = "العملة (مثلاً: ريال يمني):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = currencyInput,
                        onValueChange = { currencyInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Standard Daily Spending Field
                    Text(
                        text = "قيمة الصرفة اليومية المعتادة:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = standardSpendingInput,
                        onValueChange = { standardSpendingInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Start Work Date Field
                    Text(
                        text = "تاريخ بدء العمل الفعلي:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    Button(
                        onClick = { showDatePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.text.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = sdf.format(Date(selectedDateInMillis)),
                            color = LocalAppColors.current.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                    // Auto Add Daily Spendings Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = autoAddDailySpending,
                            onCheckedChange = { autoAddDailySpending = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldGreen
                            )
                        )
                        Text(
                            text = "تمكين إضافة الصرفة تلقائياً:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textSub
                        )
                    }

                    HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                    Text(
                        text = "📜 بيانات سابقة (لمطابقة الكشف القديم):",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.text,
                        fontWeight = FontWeight.Bold
                    )

                    // Opening Balance / Previous Balance
                    Text(
                        text = "رصيد سابق / حساب مستحق لك (رصيد افتتاحي):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = openingBalanceInput,
                        onValueChange = { openingBalanceInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("مثلاً: 50000 أو 0 إذا لا يوجد", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Prior Absence count
                    Text(
                        text = "عدد أيام الغياب السابقة (أيام):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = priorAbsencesInput,
                        onValueChange = { priorAbsencesInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Prior Spendings amount
                    Text(
                        text = "مجموع صرفيات سابقة مخصومة:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = priorSpendingsInput,
                        onValueChange = { priorSpendingsInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Prior Draws amount
                    Text(
                        text = "مجموع سحبيات سلف سابقة مخصومة:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = priorDrawsInput,
                        onValueChange = { priorDrawsInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )
                } else {
                    // --- TAB 1: SYSTEM SETTINGS & DEVELOPER INFO ---
                    // Theme Mode Selection
                    Text(
                        text = "🎨 مظهر التطبيق (الوضع الليلي / النهاري):",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.text,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf(
                            "SYSTEM" to "تلقائي ⚙️",
                            "LIGHT" to "نهاري ☀️",
                            "DARK" to "ليلي 🌙"
                        )
                        themes.forEach { (mode, label) ->
                            val isSelected = themeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) EmeraldGreen else LocalAppColors.current.surface.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.Transparent else LocalAppColors.current.border,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { themeMode = mode }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else LocalAppColors.current.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                    // Speak Transactions Switch
                    Text(
                        text = "🔊 التوجيهات الصوتية والتحدث المباشر:",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.text,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = voiceEnabled,
                            onCheckedChange = { voiceEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldGreen
                            )
                        )
                        Text(
                            text = "نطق المعاملات صوتياً عند الإضافة والتحكم:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textSub
                        )
                    }

                    HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                    // Daily Reminder Settings
                    Text(
                        text = "⏰ وقت التنبيه لتذكير الصرفة أو السحبية أو الغياب:",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.text,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = dailyReminderEnabled,
                            onCheckedChange = { dailyReminderEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldGreen
                            )
                        )
                        Text(
                            text = "تفعيل التنبيه والتذكير اليومي بالتطبيق:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textSub
                        )
                    }
                    if (dailyReminderEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LocalAppColors.current.surface.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, LocalAppColors.current.border, shape = RoundedCornerShape(12.dp))
                                .clickable { timePickerDialog.show() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "تحديد وقت التنبيه اليومي",
                                tint = EmeraldGreen
                            )
                            val formattedTime = String.format(Locale("ar"), "%02d:%02d %s", 
                                if (dailyReminderHour % 12 == 0) 12 else dailyReminderHour % 12,
                                dailyReminderMinute,
                                if (dailyReminderHour >= 12) "م" else "ص"
                            )
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalAppColors.current.text,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                    // App Security Settings
                    Text(
                        text = "🔒 حماية وأمان التطبيق:",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.text,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = securityEnabled,
                            onCheckedChange = { securityEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldGreen
                            )
                        )
                        Text(
                            text = "تفعيل قفل التطبيق (PIN):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textSub
                        )
                    }
                    if (securityEnabled) {
                        OutlinedTextField(
                            value = securityPinInput,
                            onValueChange = { input ->
                                if (input.length <= 4 && input.all { char -> char.isDigit() }) {
                                    securityPinInput = input
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            placeholder = { Text("مثلاً: 1234", color = Color.Gray) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = LocalAppColors.current.border,
                                focusedTextColor = LocalAppColors.current.text,
                                unfocusedTextColor = LocalAppColors.current.text
                            )
                        )
                        val biometricManager = BiometricManager.from(context)
                        val isBiometricAvailable = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                        if (isBiometricAvailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Switch(
                                    checked = biometricEnabled,
                                    onCheckedChange = { biometricEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = EmeraldGreen
                                    )
                                )
                                Text(
                                    text = "تفعيل بصمة الإصبع للفتح السريع:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LocalAppColors.current.textSub
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                    // Developer Info Card
                    Text(
                        text = "👨‍💻 معلومات مطور التطبيق:",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.text,
                        fontWeight = FontWeight.Bold
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "الاسم: حسام محمد الحطامي",
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "البريد الإلكتروني: husamalhattami100@gmail.com",
                                color = LocalAppColors.current.textSub,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "رقم الجوال: 778383570",
                                color = LocalAppColors.current.textSub,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val salary = salaryInput.toDoubleOrNull() ?: 150000.0
                            val standardSpending = standardSpendingInput.toDoubleOrNull() ?: 2000.0
                            val priorAbsences = priorAbsencesInput.toIntOrNull() ?: 0
                            val priorSpendings = priorSpendingsInput.toDoubleOrNull() ?: 0.0
                            val priorDraws = priorDrawsInput.toDoubleOrNull() ?: 0.0
                            val currencyVal = if (currencyInput.isBlank()) "ريال يمني" else currencyInput
                            val employeeNameVal = if (employeeNameInput.isBlank()) "محمد خالد العريفي" else employeeNameInput
                            val openingBalance = openingBalanceInput.toDoubleOrNull() ?: 0.0
                            
                            onSave(
                                salary,
                                selectedDateInMillis,
                                currencyVal,
                                standardSpending,
                                priorAbsences,
                                priorSpendings,
                                priorDraws,
                                dailyReminderEnabled,
                                dailyReminderHour,
                                dailyReminderMinute,
                                employeeNameVal,
                                voiceEnabled,
                                openingBalance,
                                securityEnabled,
                                securityPinInput,
                                biometricEnabled,
                                autoAddDailySpending,
                                themeMode
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ الحساب", color = Color.White)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialogHelper(
            initialTime = selectedDateInMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date ->
                selectedDateInMillis = date
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogHelper(
    initialTime: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialTime
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val date = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    onDateSelected(date)
                }
            ) {
                Text("تأكيد", color = EmeraldGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    var amountInput by remember { mutableStateOf(transaction.amount.toInt().toString()) }
    var voiceTextInput by remember { mutableStateOf(transaction.voiceText ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateFormater = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
    val formattedDate = dateFormater.format(Date(transaction.timestamp))

    val typeLabel = when (transaction.type) {
        "SPENDING" -> "صرفة مصروف"
        "DRAW" -> "سحبة سلفة"
        "ABSENCE" -> "تسجيل غياب"
        else -> "عملية"
    }

    val typeColor = when (transaction.type) {
        "SPENDING" -> CrimsonRed
        "DRAW" -> AmberOrange
        "ABSENCE" -> Color.Gray
        else -> EarnedBlue
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "تأكيد الحذف ⚠️",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف هذه العملية نهائياً؟ لا يمكن استرجاع البيانات بعد الحذف.",
                    color = Color.LightGray,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(transaction)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("نعم، احذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء", color = Color.LightGray)
                }
            },
            containerColor = DarkSlateSurface
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "تعديل العملية 📝",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right
                )
                Text(
                    text = "$typeLabel - $formattedDate",
                    color = typeColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Right
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Input label
                val inputLabel = when (transaction.type) {
                    "ABSENCE" -> "مدة الغياب (بالساعات - 12 ساعة غياب يوم كامل):"
                    else -> "المبلغ ($currency):"
                }

                Text(
                    text = inputLabel,
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = typeColor,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Optional voice text / details
                Text(
                    text = "التفاصيل / الملاحظات:",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = voiceTextInput,
                    onValueChange = { voiceTextInput = it },
                    placeholder = { Text("مثلاً: غداء، سلفة طارئة، إلخ", color = Color.Gray) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = typeColor,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Save Button
                Button(
                    onClick = {
                        val parsedVal = amountInput.toDoubleOrNull() ?: transaction.amount
                        val updatedTx = transaction.copy(
                            amount = parsedVal,
                            voiceText = voiceTextInput.ifBlank { null }
                        )
                        onSave(updatedTx)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("حفظ", color = Color.White)
                }

                // Delete Button
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إلغاء", color = Color.LightGray)
            }
        },
        containerColor = DarkSlateSurface
    )
}

fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onFailure(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailure("فشلت مصادقة بصمة الإصبع")
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("تسجيل الدخول بالبصمة")
        .setSubtitle("استخدم بصمة إصبعك لفتح التطبيق")
        .setNegativeButtonText("إلغاء")
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onFailure("الجهاز لا يدعم أو غير مهيأ للبصمة")
    }
}

@Composable
fun AppLockScreen(
    correctPin: String,
    biometricEnabled: Boolean,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pinEntered by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("الرجاء إدخال الرمز السري للدخول") }
    var isError by remember { mutableStateOf(false) }

    fun tryUnlockWithBiometrics() {
        if (activity != null && biometricEnabled) {
            showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    onUnlockSuccess()
                },
                onFailure = { err ->
                    statusMessage = err
                    isError = true
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (biometricEnabled) {
            tryUnlockWithBiometrics()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(EmeraldGreen.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = "حساب العامل الذكي",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) CrimsonRed else Color.LightGray,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..4) {
                    val isFilled = pinEntered.length >= i
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                if (isFilled) EmeraldGreen else Color.Gray.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isFilled) EmeraldGreen else Color.Gray,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val buttonRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                for (row in buttonRows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (key in row) {
                            if (key == "BIO") {
                                if (biometricEnabled) {
                                    IconButton(
                                        onClick = { tryUnlockWithBiometrics() },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .background(EmeraldGreen.copy(alpha = 0.2f), shape = CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "فتح بالبصمة",
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(64.dp))
                                }
                            } else if (key == "DEL") {
                                IconButton(
                                    onClick = {
                                        if (pinEntered.isNotEmpty()) {
                                            pinEntered = pinEntered.dropLast(1)
                                            isError = false
                                            statusMessage = "الرجاء إدخال الرمز السري للدخول"
                                        }
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color.White.copy(alpha = 0.05f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "مسح",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (pinEntered.length < 4) {
                                            pinEntered += key
                                            isError = false
                                            statusMessage = "الرجاء إدخال الرمز السري للدخول"
                                            
                                            if (pinEntered.length == 4) {
                                                if (pinEntered == correctPin) {
                                                    onUnlockSuccess()
                                                } else {
                                                    pinEntered = ""
                                                    isError = true
                                                    statusMessage = "الرمز السري غير صحيح! حاول مجدداً"
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeLoginScreen(
    registeredUsers: List<String>,
    onLogin: (String) -> Unit,
    onRegister: (String, Double, Long, String, Double, Boolean, Int, Int, Double) -> Unit,
    onDeleteUser: (String) -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("") }
    var salaryInput by remember { mutableStateOf("150000") }
    var currencyInput by remember { mutableStateOf("ريال يمني") }
    var standardSpendingInput by remember { mutableStateOf("2000") }
    var openingBalanceInput by remember { mutableStateOf("0") }
    
    var dailyReminderEnabled by remember { mutableStateOf(true) }
    var dailyReminderHour by remember { mutableStateOf(5) }
    var dailyReminderMinute by remember { mutableStateOf(0) }
    
    var selectedDateInMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
    
    val timePickerDialog = remember {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minuteOfHour ->
                dailyReminderHour = hourOfDay
                dailyReminderMinute = minuteOfHour
            },
            dailyReminderHour,
            dailyReminderMinute,
            false
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            // App Logo or Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(EmeraldGreen.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(44.dp)
                )
            }
            
            Text(
                text = "مرحباً بك في حساب العامل الذكي 👋",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "الرجاء إدخال اسمك وبياناتك المالية لإعداد حسابك والبدء في تتبع راتبك وصرفياتك بكل سهولة.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Full Name
                    Text(
                        text = "الاسم الكريم:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = { Text("مثلاً: محمد خالد العريفي", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    // Monthly Salary
                    Text(
                        text = "الراتب الشهري الأساسي:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = salaryInput,
                        onValueChange = { salaryInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    // Currency
                    Text(
                        text = "العملة (مثلاً: ريال يمني):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = currencyInput,
                        onValueChange = { currencyInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Opening Balance / Previous Balance
                    Text(
                        text = "رصيد سابق / حساب مستحق لك (رصيد افتتاحي):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = openingBalanceInput,
                        onValueChange = { openingBalanceInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("مثلاً: 50000 أو 0 إذا لا يوجد", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    // Work Start Date
                    Text(
                        text = "تاريخ بدء العمل في هذا الشهر:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Button(
                        onClick = { showDatePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = EmeraldGreen
                            )
                            Text(
                                text = sdf.format(Date(selectedDateInMillis)),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // Daily reminder checkbox and time
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = dailyReminderEnabled,
                            onCheckedChange = { dailyReminderEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldGreen,
                                checkedTrackColor = EmeraldGreen.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = "تفعيل التنبيه الصوتي اليومي لعدم النسيان",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Right
                        )
                    }
                    
                    if (dailyReminderEnabled) {
                        Button(
                            onClick = { timePickerDialog.show() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = EmeraldGreen
                                )
                                val timeText = String.format(Locale.US, "%02d:%02d %s", 
                                    if (dailyReminderHour % 12 == 0) 12 else dailyReminderHour % 12,
                                    dailyReminderMinute,
                                    if (dailyReminderHour < 12) "ص" else "م"
                                )
                                Text(
                                    text = "وقت التنبيه: $timeText",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Save & Start Button
            Button(
                onClick = {
                    if (nameInput.trim().isBlank()) {
                        Toast.makeText(context, "الرجاء إدخال الاسم للبدء", Toast.LENGTH_LONG).show()
                    } else {
                        val salary = salaryInput.toDoubleOrNull() ?: 150000.0
                        val standardSpending = standardSpendingInput.toDoubleOrNull() ?: 2000.0
                        val currency = if (currencyInput.isBlank()) "ريال يمني" else currencyInput
                        val openingBalance = openingBalanceInput.toDoubleOrNull() ?: 0.0
                        
                        onRegister(
                            nameInput.trim(),
                            salary,
                            selectedDateInMillis,
                            currency,
                            standardSpending,
                            dailyReminderEnabled,
                            dailyReminderHour,
                            dailyReminderMinute,
                            openingBalance
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_save_button")
            ) {
                Text(
                    text = "حفظ والبدء في التطبيق 🚀",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
    
    if (showDatePicker) {
        DatePickerDialogHelper(
            initialTime = selectedDateInMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date ->
                selectedDateInMillis = date
                showDatePicker = false
            }
        )
    }
}

private fun shareReport(context: Context, reportText: String, employeeName: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "تقرير حساب $employeeName")
        putExtra(Intent.EXTRA_TEXT, reportText)
    }
    context.startActivity(Intent.createChooser(intent, "تصدير التقرير عبر:"))
}

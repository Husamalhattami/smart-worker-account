package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import java.io.File
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import android.app.Activity
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CurrencyItem
import com.example.data.SalarySettings
import com.example.data.Transaction
import com.example.viewmodel.UpdateInfo
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.LanguageStrings
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
    val coroutineScope = rememberCoroutineScope()
    val settingsState by viewModel.settings.collectAsState()
    val transactionsState by viewModel.transactions.collectAsState()
    val metricsState by viewModel.metrics.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val registeredUsers by viewModel.registeredUsers.collectAsState()
    val currenciesState by viewModel.currenciesState.collectAsStateWithLifecycle()

    val isListening by viewModel.isListening.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val isPlayingAudioId by viewModel.isPlayingAudioId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val speechInputText by viewModel.speechInputText.collectAsState()
    val updateInfoState by viewModel.updateInfo.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.userNotice.collect { notice ->
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        }
    }

    var showMainMenuDialog by remember { mutableStateOf(false) }
    var showDeveloperInfoDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUserSettingsDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasRecordPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activity = context as? Activity

    fun requestPermissionsSafely(permissions: Array<String>) {
        if (activity != null && permissions.isNotEmpty()) {
            try {
                ActivityCompat.requestPermissions(activity, permissions, 101)
            } catch (e: Exception) {
                Log.e("SalaryApp", "Failed to request permissions safely", e)
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = viewModel.exportBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "✅ تم حفظ النسخة الاحتياطية بنجاح!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ فشل حفظ ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val handleRestoreUri: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!json.isNullOrBlank()) {
                        val success = viewModel.restoreBackupJson(json)
                        if (success) {
                            Toast.makeText(context, "✅ تم استعادة جميع البيانات والراتب بنجاح!", Toast.LENGTH_LONG).show()
                            showBackupDialog = false
                        } else {
                            Toast.makeText(context, "❌ صيغة الملف غير صحيحة كنسخة احتياطية", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "⚠️ الملف المختار فارغ", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ فشل قراءة ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = handleRestoreUri
    )

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = handleRestoreUri
    )

    var hasGreeted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(isManual = false)
        val permissionsToRequest = mutableListOf<String>()
        if (!hasRecordPermission) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsSafely(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(updateInfoState.hasUpdate) {
        if (updateInfoState.hasUpdate) {
            showUpdateDialog = true
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

    val langCode = settingsState?.appLanguage ?: "ar"
    val appLang = AppLanguage.fromCode(langCode)
    val layoutDirection = if (appLang.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalLayoutDirection provides layoutDirection
    ) {
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
                    Column(verticalArrangement = Arrangement.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "حساب العامل الذكي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                        }
                        val empName = settingsState?.employeeName?.trim()
                        val greetingText = if (!empName.isNullOrEmpty()) "مرحباً بك $empName" else "مرحباً بك"
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldGreen
                        )
                    }
                },
                actions = {
                    // Audio Status Report Button
                    IconButton(
                        onClick = { viewModel.announceFinancialStatus() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(EmeraldGreen.copy(alpha = 0.15f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "استمع للتقرير الصوتي",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    // Menu Curtain Drawer Trigger Button
                    IconButton(
                        onClick = { showMainMenuDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(LocalAppColors.current.text.copy(alpha = 0.08f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "القائمة الرئيسية",
                            tint = LocalAppColors.current.text,
                            modifier = Modifier.size(20.dp)
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
                        requestPermissionsSafely(arrayOf(Manifest.permission.RECORD_AUDIO))
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
                    val empName = settingsState?.employeeName ?: "حسام الحطامي"
                    shareReport(context, rText, empName)
                },
                onPrintClick = {
                    val settings = settingsState ?: SalarySettings()
                    val txList = transactionsState
                    val metrics = metricsState
                    PdfPrintHelper.printSalaryReport(context, settings, txList, metrics)
                }
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    }

    // --- MAIN MENU DIALOG ---
    if (showMainMenuDialog) {
        MainMenuDialog(
            appLanguage = settingsState?.appLanguage ?: "ar",
            onDismiss = { showMainMenuDialog = false },
            onOpenAppSettings = {
                showMainMenuDialog = false
                showSettingsDialog = true
            },
            onOpenUserSettings = {
                showMainMenuDialog = false
                showUserSettingsDialog = true
            },
            onOpenBackup = {
                showMainMenuDialog = false
                showBackupDialog = true
            },
            onCheckUpdate = {
                showMainMenuDialog = false
                showUpdateDialog = true
                viewModel.checkForUpdates(isManual = true)
            },
            onOpenGuide = {
                showMainMenuDialog = false
                showGuideDialog = true
            },
            onOpenDeveloperInfo = {
                showMainMenuDialog = false
                showDeveloperInfoDialog = true
            }
        )
    }

    // --- BACKUP DIALOG ---
    if (showBackupDialog) {
        BackupDialog(
            appLanguage = settingsState?.appLanguage ?: "ar",
            onDismiss = { showBackupDialog = false },
            onExportBackup = {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                try {
                    createDocumentLauncher.launch("smart_worker_backup_$timeStamp.json")
                } catch (e: Exception) {
                    exportAndShareBackup(context, viewModel, coroutineScope)
                }
            },
            onRestoreBackup = {
                try {
                    getContentLauncher.launch("*/*")
                } catch (e1: Exception) {
                    try {
                        openDocumentLauncher.launch(arrayOf("*/*"))
                    } catch (e2: Exception) {
                        Toast.makeText(context, "⚠️ تعذر فتح متصفح الملفات، يمكنك استخدام خيار اللصق اليدوي للنص", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onRestoreFromText = { text ->
                coroutineScope.launch {
                    try {
                        val success = viewModel.restoreBackupJson(text)
                        if (success) {
                            Toast.makeText(context, "✅ تم استعادة جميع البيانات والراتب بنجاح!", Toast.LENGTH_LONG).show()
                            showBackupDialog = false
                        } else {
                            Toast.makeText(context, "❌ صيغة الملف أو النص غير صحيحة كنسخة احتياطية", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "❌ حدث خطأ أثناء استعادة النسخة من النص", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // --- APP GUIDE DIALOG ---
    if (showGuideDialog) {
        AppGuideDialog(
            onDismiss = { showGuideDialog = false }
        )
    }

    // --- APP UPDATE DIALOG ---
    if (showUpdateDialog) {
        AppUpdateDialog(
            updateInfoState = updateInfoState,
            onCheckUpdate = { viewModel.checkForUpdates(isManual = true) },
            onDismiss = { showUpdateDialog = false }
        )
    }

    // --- DEVELOPER INFO DIALOG ---
    if (showDeveloperInfoDialog) {
        DeveloperInfoDialog(
            appLanguage = settingsState?.appLanguage ?: "ar",
            onDismiss = { showDeveloperInfoDialog = false }
        )
    }

    // --- USER SETTINGS DIALOG ---
    if (showUserSettingsDialog) {
        UserSettingsDialog(
            currentSettings = settingsState ?: SalarySettings(),
            currencies = currenciesState,
            onAddCustomCurrency = { name, symbol, flag ->
                viewModel.addCustomCurrency(name, symbol, flag)
            },
            onDeleteCurrency = { item ->
                viewModel.deleteCurrency(item)
            },
            onDismiss = { showUserSettingsDialog = false },
            onSave = { employeeName, salary, currency, standardSpending, startDate, autoAddDailySpending, isSpendingDeductedFromSalary, openingBalance, priorAbsences, priorSpendings, priorDraws ->
                val curr = settingsState ?: SalarySettings()
                viewModel.updateSalarySettings(
                    salary = salary,
                    startDate = startDate,
                    currency = currency,
                    standardSpending = standardSpending,
                    priorAbsences = priorAbsences,
                    priorSpendings = priorSpendings,
                    priorDraws = priorDraws,
                    dailyReminderEnabled = curr.dailyReminderEnabled,
                    dailyReminderHour = curr.dailyReminderHour,
                    dailyReminderMinute = curr.dailyReminderMinute,
                    employeeName = employeeName,
                    voiceEnabled = curr.voiceEnabled,
                    openingBalance = openingBalance,
                    securityEnabled = curr.securityEnabled,
                    securityPin = curr.securityPin,
                    biometricEnabled = curr.biometricEnabled,
                    autoAddDailySpending = autoAddDailySpending,
                    isSpendingDeductedFromSalary = isSpendingDeductedFromSalary,
                    themeMode = curr.themeMode,
                    appLanguage = curr.appLanguage
                )
                showUserSettingsDialog = false
            }
        )
    }

    // --- APP SETTINGS DIALOG ---
    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settingsState ?: SalarySettings(),
            onDismiss = { showSettingsDialog = false },
            onSave = { voiceEnabled, dailyReminderEnabled, dailyReminderHour, dailyReminderMinute, securityEnabled, securityPin, biometricEnabled, themeMode, appLanguage ->
                val curr = settingsState ?: SalarySettings()
                viewModel.updateSalarySettings(
                    salary = curr.monthlySalary,
                    startDate = curr.startWorkDate,
                    currency = curr.currency,
                    standardSpending = curr.standardSpendingAmount,
                    priorAbsences = curr.priorAbsenceCount,
                    priorSpendings = curr.priorSpendingsAmount,
                    priorDraws = curr.priorDrawsAmount,
                    dailyReminderEnabled = dailyReminderEnabled,
                    dailyReminderHour = dailyReminderHour,
                    dailyReminderMinute = dailyReminderMinute,
                    employeeName = curr.employeeName,
                    voiceEnabled = voiceEnabled,
                    openingBalance = curr.openingBalance,
                    securityEnabled = securityEnabled,
                    securityPin = securityPin,
                    biometricEnabled = biometricEnabled,
                    autoAddDailySpending = curr.autoAddDailySpending,
                    isSpendingDeductedFromSalary = curr.isSpendingDeductedFromSalary,
                    themeMode = themeMode,
                    appLanguage = appLanguage
                )
                showSettingsDialog = false
            }
        )
    }

    // Update dialog handled above by AppUpdateDialog

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
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LocalAppColors.current.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "اليوم:",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textMuted,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.text
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
    val borderArcColor = LocalAppColors.current.border
    val textSubColor = LocalAppColors.current.textSub
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
                color = borderArcColor,
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
                color = textSubColor
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
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalAppColors.current.textSub,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                color = LocalAppColors.current.text,
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
                    color = LocalAppColors.current.textSub,
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "إضافة سريعة بلمسة واحدة 👇",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.text,
            modifier = Modifier.align(Alignment.End)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    title = "صرفة اليوم المعتادة",
                    amountText = "${standardSpending.toInt()} $cur",
                    accentColor = CrimsonRed,
                    icon = Icons.Default.ReceiptLong,
                    onClick = { onSpendingClick(standardSpending) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                QuickActionCard(
                    title = "تسجيل غياب يوم كامل",
                    amountText = "١٢ ساعة غياب",
                    accentColor = Color(0xFF6B7280),
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
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(115.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accentColor.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
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
            color = LocalAppColors.current.text,
            modifier = Modifier.align(Alignment.End)
        )

        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface)
            ) {
                Text(
                    text = "لا توجد أي عمليات مسجلة حتى الآن",
                    color = LocalAppColors.current.textMuted,
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
        "SPENDING" -> Icons.Default.ReceiptLong
        "DRAW" -> Icons.Default.AccountBalanceWallet
        "ABSENCE" -> Icons.Default.EventBusy
        else -> Icons.Default.CheckCircle
    }

    val typeLabel = when (transaction.type) {
        "SPENDING" -> "صرفة"
        "DRAW" -> "سحبيّة"
        "ABSENCE" -> "تسجيل غياب"
        else -> "عملية"
    }

    val amountLabel = when (transaction.type) {
        "ABSENCE" -> {
            val hrs = transaction.amount
            if (hrs == 12.0 || hrs <= 0.0) "غياب يوم كامل" else "غياب ${hrs.toInt()} ساعات"
        }
        "SPENDING" -> "-${transaction.amount.toInt()} $currency"
        "DRAW" -> "-${transaction.amount.toInt()} $currency"
        else -> "${transaction.amount.toInt()} $currency"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Category Icon Badge (RTL First = Far Right)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.15f), shape = CircleShape)
                    .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Details Column (RTL Middle = Aligned right)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalAppColors.current.text,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textMuted
                    )
                }

                if (!transaction.voiceText.isNullOrBlank()) {
                    Text(
                        text = transaction.voiceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. Amount & Voice Button (RTL Last = Far Left)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (transaction.voiceNotePath != null) {
                    IconButton(
                        onClick = { if (isPlaying) onStopAudio() else onPlayVoice() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isPlaying) CrimsonRed.copy(alpha = 0.2f) else EmeraldGreen.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "تشغيل المذكرة الصوتية للعملية",
                            tint = if (isPlaying) CrimsonRed else EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = amountLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
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
                    text = "📝 تسجيل عملية جديدة",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalAppColors.current.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // Type Tab-like buttons
                Text(
                    text = "نوع العملية:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textSub
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
                                    color = if (isSelected) color else color.copy(alpha = 0.4f),
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
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        placeholder = {
                            Text(
                                text = if (selectedType == "SPENDING") "${settings.standardSpendingAmount.toInt()}" else "أدخل المبلغ هنا...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.textMuted
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (selectedType == "SPENDING") CrimsonRed else AmberOrange,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Note Field
                    Text(
                        text = "ملاحظة وبيان العملية (اختياري - لأيش):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        placeholder = {
                            Text(
                                text = if (selectedType == "SPENDING") "مثال: صرفة غداء، باص..." else "مثال: سلفة لمصاريف العيد...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.textMuted
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
                                    tint = if (isListeningForNote) CrimsonRed else LocalAppColors.current.textSub,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (selectedType == "SPENDING") CrimsonRed else AmberOrange,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
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
                        color = LocalAppColors.current.textSub
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
                                        if (isSel) Color.Gray else LocalAppColors.current.text.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) LocalAppColors.current.text else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { absenceIsFullDay = isFull },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else LocalAppColors.current.textSub
                                )
                            }
                        }
                    }

                    if (!absenceIsFullDay) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "عدد ساعات الغياب (يوم الشغل ١٢ ساعة):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textSub
                        )
                        OutlinedTextField(
                            value = absenceHoursInput,
                            onValueChange = { absenceHoursInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                            placeholder = {
                                Text(
                                    text = "مثال: ٣ ساعات",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LocalAppColors.current.textMuted
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Gray,
                                unfocusedBorderColor = LocalAppColors.current.border,
                                focusedTextColor = LocalAppColors.current.text,
                                unfocusedTextColor = LocalAppColors.current.text
                            )
                        )
                    }

                    // Absence Note Field
                    Text(
                        text = "ملاحظة أو سبب الغياب (اختياري):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        placeholder = {
                            Text(
                                text = "مثال: عذر مرضي، غياب جزئي...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.textMuted
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
                                    tint = if (isListeningForNote) CrimsonRed else LocalAppColors.current.textSub,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
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
                    color = LocalAppColors.current.textSub
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalAppColors.current.text.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, LocalAppColors.current.border, shape = RoundedCornerShape(12.dp))
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
                        color = LocalAppColors.current.text,
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
                        border = BorderStroke(1.dp, LocalAppColors.current.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء", color = LocalAppColors.current.textSub)
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
    onSave: (
        voiceEnabled: Boolean,
        dailyReminderEnabled: Boolean,
        dailyReminderHour: Int,
        dailyReminderMinute: Int,
        securityEnabled: Boolean,
        securityPin: String,
        biometricEnabled: Boolean,
        themeMode: String,
        appLanguage: String
    ) -> Unit
) {
    val context = LocalContext.current

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

    // Theme & Language Modes State
    var themeMode by remember { mutableStateOf(currentSettings.themeMode) }
    var selectedAppLanguage by remember { mutableStateOf(currentSettings.appLanguage) }

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

                // 1. Theme Mode Selection
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

                // 2. App Language Selection
                Text(
                    text = "🌐 لغة التطبيق (App Language):",
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalAppColors.current.text,
                    fontWeight = FontWeight.Bold
                )
                val availableLanguages = listOf(
                    "ar" to "العربية",
                    "en" to "English",
                    "fr" to "Français",
                    "ur" to "اردو / हिन्दी"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableLanguages.forEach { (code, label) ->
                        val isSelected = selectedAppLanguage == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) EmeraldGreen else LocalAppColors.current.surface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else LocalAppColors.current.border,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedAppLanguage = code }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else LocalAppColors.current.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // 3. Voice Guidance
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

                // 4. Daily Reminder Settings
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
                        val formattedTime = String.format(
                            Locale("ar"), "%02d:%02d %s",
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

                // 5. App Security Settings
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
                            onSave(
                                voiceEnabled,
                                dailyReminderEnabled,
                                dailyReminderHour,
                                dailyReminderMinute,
                                securityEnabled,
                                securityPinInput,
                                biometricEnabled,
                                themeMode,
                                selectedAppLanguage
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ التغييرات", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun UserSettingsDialog(
    currentSettings: SalarySettings,
    currencies: List<CurrencyItem> = emptyList(),
    onAddCustomCurrency: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteCurrency: ((CurrencyItem) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (
        employeeName: String,
        salary: Double,
        currency: String,
        standardSpending: Double,
        startDate: Long,
        autoAddDailySpending: Boolean,
        isSpendingDeductedFromSalary: Boolean,
        openingBalance: Double,
        priorAbsences: Int,
        priorSpendings: Double,
        priorDraws: Double
    ) -> Unit
) {
    var employeeNameInput by remember { mutableStateOf(currentSettings.employeeName) }
    var salaryInput by remember { mutableStateOf(if (currentSettings.monthlySalary == 0.0) "" else currentSettings.monthlySalary.toInt().toString()) }
    var currencyInput by remember { mutableStateOf(currentSettings.currency) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var standardSpendingInput by remember { mutableStateOf(currentSettings.standardSpendingAmount.toInt().toString()) }
    var autoAddDailySpending by remember { mutableStateOf(currentSettings.autoAddDailySpending) }
    var isSpendingDeductedFromSalary by remember { mutableStateOf(currentSettings.isSpendingDeductedFromSalary) }
    var openingBalanceInput by remember { mutableStateOf(currentSettings.openingBalance.toInt().toString()) }
    var priorAbsencesInput by remember { mutableStateOf(currentSettings.priorAbsenceCount.toString()) }
    var priorSpendingsInput by remember { mutableStateOf(currentSettings.priorSpendingsAmount.toInt().toString()) }
    var priorDrawsInput by remember { mutableStateOf(currentSettings.priorDrawsAmount.toInt().toString()) }

    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateInMillis by remember { mutableStateOf(currentSettings.startWorkDate) }

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
                    text = "👤 معلومات المستخدم والراتب",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalAppColors.current.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // Employee Name
                Text(
                    text = "اسم المستخدم:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textSub
                )
                OutlinedTextField(
                    value = employeeNameInput,
                    onValueChange = { employeeNameInput = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                    placeholder = { Text("مثلاً: حسام الحطامي", color = Color.Gray) },
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
                    placeholder = { Text("مثلاً: 150000", color = Color.Gray) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
                    )
                )

                // Toggle: Is allowance/spending deducted from salary or net salary?
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LocalAppColors.current.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isSpendingDeductedFromSalary,
                            onCheckedChange = { isSpendingDeductedFromSalary = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldGreen
                            )
                        )
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text(
                                text = "هل الصرفة من حال الراتب؟",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                            Text(
                                text = if (isSpendingDeductedFromSalary) "مفعل: الصرفة مخصومة وتحسب من الراتب" else "غير مفعل: الراتب صافي بدون خصم الصرفة",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSpendingDeductedFromSalary) EmeraldGreen else LocalAppColors.current.textSub
                            )
                        }
                    }
                }

                // Currency Symbol Field
                Text(
                    text = "اختر العملة الرئيسية للراتب والحسابات 💱:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textSub,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCurrencyPicker = true },
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LocalAppColors.current.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showCurrencyPicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "اختيار من القائمة 💱",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val activeCurrencyObj = currencies.find {
                                it.name.trim() == currencyInput.trim() || it.symbol.trim() == currencyInput.trim()
                            }
                            if (activeCurrencyObj != null && activeCurrencyObj.flag.isNotBlank()) {
                                Text(text = activeCurrencyObj.flag, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = if (currencyInput.isBlank()) "ريال يمني" else currencyInput,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                        }
                    }
                }

                // Quick Currency Chips loaded from Room DB
                val displayCurrencies = if (currencies.isNotEmpty()) currencies else listOf(
                    CurrencyItem("YER", "ريال يمني", "ر.ي", "🇾🇪"),
                    CurrencyItem("SAR", "ريال سعودي", "ر.س", "🇸🇦"),
                    CurrencyItem("USD", "دولار أمريكي", "$", "💵"),
                    CurrencyItem("EGP", "جنيه مصري", "ج.م", "🇪🇬"),
                    CurrencyItem("AED", "درهم إماراتي", "د.إ", "🇦🇪")
                )

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    displayCurrencies.take(12).forEach { currItem ->
                        val isSelected = currencyInput.trim() == currItem.name.trim() || currencyInput.trim() == currItem.symbol.trim()
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) EmeraldGreen else LocalAppColors.current.surface.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else LocalAppColors.current.border,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { currencyInput = currItem.name }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${currItem.flag} ${currItem.name}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) Color.White else LocalAppColors.current.text,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

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
                            val salary = salaryInput.toDoubleOrNull() ?: 0.0
                            val standardSpending = standardSpendingInput.toDoubleOrNull() ?: 2000.0
                            val priorAbsences = priorAbsencesInput.toIntOrNull() ?: 0
                            val priorSpendings = priorSpendingsInput.toDoubleOrNull() ?: 0.0
                            val priorDraws = priorDrawsInput.toDoubleOrNull() ?: 0.0
                            val currencyVal = if (currencyInput.isBlank()) "ريال يمني" else currencyInput.trim()
                            val employeeNameVal = if (employeeNameInput.isBlank()) "حسام الحطامي" else employeeNameInput.trim()
                            val openingBalance = openingBalanceInput.toDoubleOrNull() ?: 0.0

                            onSave(
                                employeeNameVal,
                                salary,
                                currencyVal,
                                standardSpending,
                                selectedDateInMillis,
                                autoAddDailySpending,
                                isSpendingDeductedFromSalary,
                                openingBalance,
                                priorAbsences,
                                priorSpendings,
                                priorDraws
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ البيانات", color = Color.White)
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

    if (showCurrencyPicker) {
        CurrencySelectionDialog(
            currencies = currencies,
            selectedCurrency = currencyInput,
            onSelectCurrency = { selected -> currencyInput = selected },
            onAddCustomCurrency = onAddCustomCurrency,
            onDeleteCurrency = onDeleteCurrency,
            onDismiss = { showCurrencyPicker = false }
        )
    }
}

@Composable
fun CurrencySelectionDialog(
    currencies: List<CurrencyItem>,
    selectedCurrency: String,
    onSelectCurrency: (String) -> Unit,
    onAddCustomCurrency: (name: String, symbol: String, flag: String) -> Unit,
    onDeleteCurrency: ((CurrencyItem) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredCurrencies = remember(currencies, searchQuery) {
        if (searchQuery.isBlank()) {
            currencies
        } else {
            currencies.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.symbol.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = LocalAppColors.current.textSub
                        )
                    }
                    Text(
                        text = "💱 قائمة العملات المتاحة (Room)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث في العملات...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = EmeraldGreen)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
                    )
                )

                // Add Custom Currency Button
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "➕ إضافة عملة جديدة لقاعدة البيانات",
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Currencies list from Room DB
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredCurrencies.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد نتائج مطابقة لـ \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LocalAppColors.current.textSub
                                )
                            }
                        }
                    } else {
                        items(filteredCurrencies, key = { it.id }) { currencyItem ->
                            val isSelected = selectedCurrency.trim() == currencyItem.name.trim() ||
                                             selectedCurrency.trim() == currencyItem.symbol.trim()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectCurrency(currencyItem.name)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) EmeraldGreen.copy(alpha = 0.15f) else LocalAppColors.current.bg
                                ),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) EmeraldGreen else LocalAppColors.current.border
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = EmeraldGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (currencyItem.isCustom && onDeleteCurrency != null) {
                                            IconButton(
                                                onClick = { onDeleteCurrency(currencyItem) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "حذف العملة",
                                                    tint = Color.Red.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = currencyItem.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = LocalAppColors.current.text
                                            )
                                            Text(
                                                text = "الرمز: ${currencyItem.symbol}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = LocalAppColors.current.textSub
                                            )
                                        }
                                        if (currencyItem.flag.isNotBlank()) {
                                            Text(
                                                text = currencyItem.flag,
                                                style = MaterialTheme.typography.titleMedium
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
    }

    if (showAddDialog) {
        AddCustomCurrencyDialog(
            onAdd = { name, symbol, flag ->
                onAddCustomCurrency(name, symbol, flag)
                onSelectCurrency(name)
                showAddDialog = false
                onDismiss()
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddCustomCurrencyDialog(
    onAdd: (name: String, symbol: String, flag: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var symbolInput by remember { mutableStateOf("") }
    var flagInput by remember { mutableStateOf("💱") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "➕ إضافة عملة جديدة إلى Room",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                Text("اسم العملة (مثلاً: ليرة تركية):", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("اسم العملة", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
                    )
                )

                Text("رمز العملة (مثلاً: TL أو ₺):", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                OutlinedTextField(
                    value = symbolInput,
                    onValueChange = { symbolInput = it },
                    placeholder = { Text("رمز العملة", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
                    )
                )

                Text("الرمز التعبيري / العلم (اختياري):", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                OutlinedTextField(
                    value = flagInput,
                    onValueChange = { flagInput = it },
                    placeholder = { Text("🇹🇷", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء", color = Color.Gray)
                    }

                    Button(
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                onAdd(nameInput, symbolInput, flagInput)
                            }
                        },
                        enabled = nameInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ العملة", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
                    color = LocalAppColors.current.text,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف هذه العملية نهائياً؟ لا يمكن استرجاع البيانات بعد الحذف.",
                    color = LocalAppColors.current.textSub,
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
                    Text("إلغاء", color = LocalAppColors.current.textSub)
                }
            },
            containerColor = LocalAppColors.current.surface
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
                    color = LocalAppColors.current.text,
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
                    color = LocalAppColors.current.textSub,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = typeColor,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
                    )
                )

                // Optional voice text / details
                Text(
                    text = "التفاصيل / الملاحظات:",
                    color = LocalAppColors.current.textSub,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = voiceTextInput,
                    onValueChange = { voiceTextInput = it },
                    placeholder = { Text("مثلاً: غداء، سلفة طارئة، إلخ", color = LocalAppColors.current.textMuted) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = typeColor,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
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
                Text("إلغاء", color = LocalAppColors.current.textSub)
            }
        },
        containerColor = LocalAppColors.current.surface
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
            .background(LocalAppColors.current.bg),
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
                color = LocalAppColors.current.text,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) CrimsonRed else LocalAppColors.current.textSub,
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
                                        .background(LocalAppColors.current.text.copy(alpha = 0.05f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "مسح",
                                        tint = LocalAppColors.current.text,
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
                                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.text.copy(alpha = 0.05f)),
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = LocalAppColors.current.text,
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
    var salaryInput by remember { mutableStateOf("") }
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
            .background(LocalAppColors.current.bg)
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
                color = LocalAppColors.current.text,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "الرجاء إدخال اسمك وبياناتك المالية لإعداد حسابك والبدء في تتبع راتبك وصرفياتك بكل سهولة.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textSub,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // User Name
                    Text(
                        text = "اسم المستخدم:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = { Text("مثلاً: حسام الحطامي", color = LocalAppColors.current.textMuted) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )
                    
                    // Monthly Salary
                    Text(
                        text = "الراتب الشهري الأساسي:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = salaryInput,
                        onValueChange = { salaryInput = it },
                        placeholder = { Text("مثلاً: 150000", color = LocalAppColors.current.textMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )
                    
                    // Currency
                    Text(
                        text = "اختر العملة الرئيسية:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = currencyInput,
                        onValueChange = { currencyInput = it },
                        placeholder = { Text("مثلاً: ريال يمني", color = LocalAppColors.current.textMuted) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Popular Currencies Choice Chips
                    val popularCurrenciesList = listOf(
                        "ريال يمني 🇾🇪",
                        "ريال سعودي 🇸🇦",
                        "دولار أمريكي 💵",
                        "درهم إماراتي 🇦🇪",
                        "جنيه مصري 🇪🇬",
                        "دينار كويتي 🇰🇼",
                        "ريال قطري 🇶🇦",
                        "ريال عماني 🇴🇲",
                        "دينار بحريني 🇧🇭",
                        "يورو 🇪🇺",
                        "دينار أردني 🇯🇴",
                        "دينار عراقي 🇮🇶"
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        popularCurrenciesList.forEach { currItem ->
                            val cleanCurr = currItem.split(" ")[0]
                            val isSelected = currencyInput.trim() == currItem.trim() || currencyInput.trim() == cleanCurr
                            FilterChip(
                                selected = isSelected,
                                onClick = { currencyInput = cleanCurr },
                                label = { Text(currItem, style = MaterialTheme.typography.bodySmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

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
                        placeholder = { Text("مثلاً: 50000 أو 0 إذا لا يوجد", color = LocalAppColors.current.textMuted) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )
                    
                    // Work Start Date
                    Text(
                        text = "تاريخ بدء العمل في هذا الشهر:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    Button(
                        onClick = { showDatePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.text.copy(alpha = 0.05f)),
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
                                color = LocalAppColors.current.text,
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
                            color = LocalAppColors.current.text,
                            textAlign = TextAlign.Right
                        )
                    }
                    
                    if (dailyReminderEnabled) {
                        Button(
                            onClick = { timePickerDialog.show() },
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.text.copy(alpha = 0.05f)),
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
                                    color = LocalAppColors.current.text,
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
                        Toast.makeText(context, "الرجاء إدخال اسم المستخدم للبدء", Toast.LENGTH_LONG).show()
                    } else if (salaryInput.trim().isBlank() || salaryInput.toDoubleOrNull() == null) {
                        Toast.makeText(context, "الرجاء إدخال الراتب الشهري الأساسي", Toast.LENGTH_LONG).show()
                    } else {
                        val salary = salaryInput.toDoubleOrNull() ?: 0.0
                        val standardSpending = standardSpendingInput.toDoubleOrNull() ?: 2000.0
                        val currency = if (currencyInput.isBlank()) "ريال يمني" else currencyInput.trim()
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

private fun exportAndShareBackup(context: Context, viewModel: SalaryViewModel, coroutineScope: kotlinx.coroutines.CoroutineScope) {
    coroutineScope.launch {
        try {
            val json = viewModel.exportBackupJson()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val cacheFile = File(context.cacheDir, "smart_worker_backup_$timeStamp.json")
            cacheFile.writeText(json, Charsets.UTF_8)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "النسخة الاحتياطية - تطبيق حساب العامل الذكي")
                putExtra(Intent.EXTRA_TEXT, "ملف النسخة الاحتياطية لتطبيق حساب العامل الذكي ($timeStamp)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "تصدير أو مشاركة النسخة الاحتياطية:"))
        } catch (e: Exception) {
            try {
                val json = viewModel.exportBackupJson()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "النسخة الاحتياطية - تطبيق حساب العامل الذكي")
                    putExtra(Intent.EXTRA_TEXT, json)
                }
                context.startActivity(Intent.createChooser(shareIntent, "مشاركة بيانات النسخة الاحتياطية:"))
            } catch (ex: Exception) {
                Toast.makeText(context, "❌ حدث خطأ أثناء تصدير النسخة الاحتياطية", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun MainMenuDialog(
    appLanguage: String = "ar",
    onDismiss: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenUserSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDeveloperInfo: () -> Unit
) {
    val appLang = AppLanguage.fromCode(appLanguage)
    val strings = LanguageStrings(appLang)

    var isVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun dismissWithAnimation() {
        isVisible = false
        coroutineScope.launch {
            delay(280)
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isVisible) 0.5f else 0.0f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { dismissWithAnimation() }
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                    initialOffsetX = { -it }
                ) + fadeIn(animationSpec = tween(350)),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    targetOffsetX = { -it }
                ) + fadeOut(animationSpec = tween(280)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .widthIn(max = 330.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .clickable(enabled = false) {},
                    color = LocalAppColors.current.surface,
                    shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Drawer Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(EmeraldGreen.copy(alpha = 0.15f), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "القائمة الرئيسية",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LocalAppColors.current.text
                                    )
                                    Text(
                                        text = "حساب العامل الذكي",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalAppColors.current.textMuted
                                    )
                                }
                            }

                            IconButton(
                                onClick = { dismissWithAnimation() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(LocalAppColors.current.text.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = LocalAppColors.current.text,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                        // Menu Options
                        MenuOptionCard(
                            title = "⚙️ إعدادات التطبيق",
                            icon = Icons.Default.Settings,
                            color = EmeraldGreen,
                            onClick = { dismissWithAnimation(); onOpenAppSettings() }
                        )

                        MenuOptionCard(
                            title = "👤 إعدادات المستخدم",
                            icon = Icons.Default.Person,
                            color = EarnedBlue,
                            onClick = { dismissWithAnimation(); onOpenUserSettings() }
                        )

                        MenuOptionCard(
                            title = "💾 النسخ الاحتياطي والاستعادة",
                            icon = Icons.Default.Share,
                            color = Color(0xFF8E24AA),
                            onClick = { dismissWithAnimation(); onOpenBackup() }
                        )

                        MenuOptionCard(
                            title = "🔄 تحديثات التطبيق",
                            icon = Icons.Default.Refresh,
                            color = Color(0xFF00ACC1),
                            onClick = { dismissWithAnimation(); onCheckUpdate() }
                        )

                        MenuOptionCard(
                            title = "📖 لمحة ودليل الاستخدام",
                            icon = Icons.Default.Book,
                            color = Color(0xFFFF9800),
                            onClick = { dismissWithAnimation(); onOpenGuide() }
                        )

                        MenuOptionCard(
                            title = "ℹ️ معلومات المطور",
                            icon = Icons.Default.Info,
                            color = AmberOrange,
                            onClick = { dismissWithAnimation(); onOpenDeveloperInfo() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { dismissWithAnimation() },
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.cancelBtn, color = LocalAppColors.current.text, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupDialog(
    appLanguage: String = "ar",
    onDismiss: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onRestoreFromText: (String) -> Unit
) {
    val appLang = AppLanguage.fromCode(appLanguage)
    val strings = LanguageStrings(appLang)
    var showPasteMode by remember { mutableStateOf(false) }
    var pastedText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (showPasteMode) Icons.Default.Edit else Icons.Default.Share,
                        contentDescription = null,
                        tint = Color(0xFF8E24AA),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = if (showPasteMode) strings.pasteBackupTitle else strings.backupRestoreSection,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                if (showPasteMode) {
                    Text(
                        text = strings.pasteBackupHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSub,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = { Text(strings.pasteBackupHint, color = LocalAppColors.current.textSub, style = MaterialTheme.typography.bodySmall) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EarnedBlue,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    Button(
                        onClick = {
                            if (pastedText.isNotBlank()) {
                                onDismiss()
                                onRestoreFromText(pastedText)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.confirmRestoreBtn,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = { showPasteMode = false },
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.cancelBtn, color = LocalAppColors.current.text)
                    }
                } else {
                    Text(
                        text = strings.backupDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSub,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            onDismiss()
                            onExportBackup()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.createBackupBtn,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onRestoreBackup()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EarnedBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.restoreBackupBtn,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = { showPasteMode = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8E24AA)),
                        border = BorderStroke(1.5.dp, Color(0xFF8E24AA)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF8E24AA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.restoreByPasteBtn,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.cancelBtn, color = LocalAppColors.current.text)
                    }
                }
            }
        }
    }
}

@Composable
fun MenuOptionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(color.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSub
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                tint = LocalAppColors.current.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AppGuideDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "لمحة ودليل استخدام التطبيق",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // Overview card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LocalAppColors.current.border)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "💡 لمحة عن التطبيق:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Text(
                            text = "تطبيق (حساب العامل الذكي) هو حاسبتك الذكية المتكاملة لإدارة راتبك وسحبياتك (السلف) والصرفيات والغيابات بدقة وحماية كاملة، دون الحاجة للدفاتر الورقية.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.text
                        )
                    }
                }

                // Guide steps card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LocalAppColors.current.border)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "🛠️ دليل الاستخدام والتفعيل:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EarnedBlue
                        )

                        val steps = listOf(
                            "1️⃣ ضبط الحساب" to "من (إعدادات المستخدم) أدخل اسمك، راتبك، عملتك، تاريخ بدء العمل، وحدد هل الصرفة مخصومة من الراتب أم لا.",
                            "2️⃣ تسجيل العمليات" to "اضغط على زر (+) في الشاشة الرئيسية لإضافة صرفية يومية، سحبية (سلفة)، أو غياب بالأرقام أو بالأمر الصوتي.",
                            "3️⃣ الحساب والخصم الآلي" to "يقوم التطبيق باحتساب المستحق اليومي، الخصومات، والصفاية المتبقية لك فوراً بنظام حسابي دقيق.",
                            "4️⃣ التقارير وتصدير الكشف" to "من شاشة الكشف يمكنك معاينة أو طباعة كشف حساب PDF أو مشاركته نصياً عبر واتساب.",
                            "5️⃣ الأمان والنسخ الاحتياطي" to "احمِ بياناتك برمز PIN أو البصمة، وأجرِ نسخاً احتياطياً لبياناتك بانتظام لاستعادتها في أي وقت."
                        )

                        steps.forEach { (title, desc) ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalAppColors.current.text
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalAppColors.current.textSub
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق", color = LocalAppColors.current.text, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    updateInfoState: UpdateInfo,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF00ACC1),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "تحديثات التطبيق",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                if (updateInfoState.hasUpdate) {
                    Text(
                        text = "📢 يتوفر تحديث جديد للتطبيق!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                    Text(
                        text = "الإصدار المتاح: ${updateInfoState.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.text,
                        fontWeight = FontWeight.Bold
                    )
                    if (updateInfoState.changelog.isNotBlank()) {
                        Text(
                            text = updateInfoState.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalAppColors.current.textSub,
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateInfoState.updateUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "تعذر فتح رابط التحميل", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تحميل التحديث الجديد", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LocalAppColors.current.bg, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✅ أنت تستخدم أحدث إصدار من التطبيق",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "لا توجد تحديثات جديدة متاحة حالياً.\nإصدارك الحالي: v1.0.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textSub,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = onCheckUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EarnedBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("البحث عن تحديثات الآن", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق", color = LocalAppColors.current.text, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DeveloperInfoDialog(
    appLanguage: String = "ar",
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AmberOrange,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "معلومات المطور",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // Developer Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LocalAppColors.current.border)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "📱 تطبيق حساب العامل الذكي v1.0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )

                        HorizontalDivider(color = LocalAppColors.current.border, thickness = 0.5.dp)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = EarnedBlue)
                            Text(
                                text = "الاسم: حسام الحطامي",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = EmeraldGreen)
                            Text(
                                text = "husamalhattami100@gmail.com",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.text
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = AmberOrange)
                            Text(
                                text = "+967778383570",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.text,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Centered Callout Message
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "💬 يسعدنا تواصلكم معنا!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "شاركونا آراءكم واقتراحاتكم وتعديلاتكم والمشاكل التي تواجهكم لتطوير التطبيق نحو الأفضل دائماً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.text,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق", color = LocalAppColors.current.text, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

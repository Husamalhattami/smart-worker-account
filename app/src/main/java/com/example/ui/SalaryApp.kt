package com.example.ui

import com.example.util.AbsenceUtils
import com.example.util.LeaveUtils
import com.example.data.LeaveRecord

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream
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
import com.example.data.AppDatabase
import com.example.data.CurrencyItem
import com.example.data.SalarySettings
import com.example.data.Transaction
import com.example.data.SalaryHistory
import android.content.ContextWrapper
import java.io.FileInputStream
import java.io.FileOutputStream
import com.example.viewmodel.UpdateInfo
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AdMobManager
import com.example.util.AdMobBanner
import com.example.util.LanguageStrings
import com.example.util.PdfPrintHelper
import com.example.viewmodel.SalaryMetrics
import com.example.viewmodel.SalaryViewModel
import com.example.viewmodel.AppSecurityState
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
    val appSecurityState by viewModel.appSecurityState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactions.collectAsState()
    val metricsState by viewModel.metrics.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val registeredUsers by viewModel.registeredUsers.collectAsState()
    val alwaysOpenDefaultAccount by viewModel.alwaysOpenDefaultAccount.collectAsState()
    val currenciesState by viewModel.currenciesState.collectAsStateWithLifecycle()

    val isListening by viewModel.isListening.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val isPlayingAudioId by viewModel.isPlayingAudioId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val speechInputText by viewModel.speechInputText.collectAsState()
    val updateInfoState by viewModel.updateInfo.collectAsState()
    val salaryHistoriesState by viewModel.salaryHistoriesState.collectAsState()
    val leavesState by viewModel.leavesState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.userNotice.collect { notice ->
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        }
    }

    var showMainMenuDialog by remember { mutableStateOf(false) }
    var showDeveloperInfoDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var fileOpNotification by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUserSettingsDialog by remember { mutableStateOf(false) }
    var showSalaryHistoryDialog by remember { mutableStateOf(false) }
    var showLeaveManagementDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showAddTxDialog by remember { mutableStateOf(false) }
    var showPdfOptionsDialog by remember { mutableStateOf(false) }
    var showSettleConfirmDialog by remember { mutableStateOf(false) }
    var showWorkerAccountsDialog by remember { mutableStateOf(false) }
    var showPrintWorkersOptionsDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var isAppUnlocked by remember { mutableStateOf(false) }
    var userRequestedAccountSwitch by remember { mutableStateOf(false) }
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

    val activity = context.findActivity() ?: (context as? Activity)

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
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val currentActiveUser = viewModel.activeUser.value
                    val dbName = AppDatabase.getDatabaseNameForUser(currentActiveUser)
                    AppDatabase.checkpointDatabase(context, currentActiveUser)
                    val dbFile = context.getDatabasePath(dbName)
                    if (dbFile.exists()) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            FileInputStream(dbFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val msg = "✅ تم حفظ ملف قاعدة البيانات (.db) بنجاح!"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            fileOpNotification = Pair(true, msg)
                        }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val msg = "❌ لم يتم العثور على ملف قاعدة البيانات"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            fileOpNotification = Pair(false, msg)
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val msg = "❌ فشل حفظ ملف قاعدة البيانات (.db)"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        fileOpNotification = Pair(false, msg)
                    }
                }
            }
        } else {
            val msg = "⚠️ تم إلغاء عملية التصدير وحفظ النسخة الاحتياطية"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            fileOpNotification = Pair(false, msg)
        }
    }

    val performRestoreFromStream: (() -> InputStream?, String) -> Unit = { streamProvider, sourceName ->
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val inputStream = streamProvider()
                if (inputStream == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val msg = "❌ تعذر قراءة الملف: $sourceName"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        fileOpNotification = Pair(false, msg)
                    }
                    return@launch
                }

                val header = ByteArray(16)
                val bytesRead = inputStream.read(header)
                inputStream.close()

                val expectedHeader = "SQLite format 3".toByteArray(Charsets.US_ASCII)
                val isValidSqlite = bytesRead >= 15 && header.take(15).toByteArray().contentEquals(expectedHeader)

                if (!isValidSqlite) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val msg = "❌ الملف المختار ليس ملف قاعدة بيانات (.db) صحيح"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        fileOpNotification = Pair(false, msg)
                    }
                    return@launch
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "⏳ جاري استعادة قاعدة البيانات وإعادة تشغيل التطبيق...", Toast.LENGTH_SHORT).show()
                }

                // Copy incoming stream to a temporary cache file to read metadata safely
                val tempDbFile = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.db")
                streamProvider()?.use { input ->
                    FileOutputStream(tempDbFile).use { output ->
                        input.copyTo(output)
                    }
                }

                var backupWorkerName: String? = null
                try {
                    val sqliteDb = android.database.sqlite.SQLiteDatabase.openDatabase(tempDbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                    val cursor = sqliteDb.rawQuery("SELECT employeeName FROM salary_settings WHERE id = 1 LIMIT 1", null)
                    if (cursor.moveToFirst()) {
                        val nameCol = cursor.getColumnIndex("employeeName")
                        if (nameCol >= 0) {
                            val nameVal = cursor.getString(nameCol)
                            if (!nameVal.isNullOrBlank()) {
                                backupWorkerName = nameVal.trim()
                            }
                        }
                    }
                    cursor.close()
                    sqliteDb.close()
                } catch (e: Exception) {
                    Log.w("RestoreBackup", "Could not read employeeName from temp restored DB", e)
                }

                val currentActiveUser = viewModel.activeUser.value
                val targetWorkerName = if (!backupWorkerName.isNullOrBlank()) backupWorkerName else (currentActiveUser ?: "حساب_افتراضي")
                val dbName = AppDatabase.getDatabaseNameForUser(targetWorkerName)
                
                AppDatabase.closeDatabaseForUser(targetWorkerName)
                AppDatabase.closeDatabase()

                val dbFile = context.getDatabasePath(dbName)
                val walFile = File(dbFile.path + "-wal")
                val shmFile = File(dbFile.path + "-shm")
                val journalFile = File(dbFile.path + "-journal")

                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()
                if (journalFile.exists()) journalFile.delete()

                tempDbFile.copyTo(dbFile, overwrite = true)
                tempDbFile.delete()

                // Ensure employeeName in restored DB matches the target worker
                try {
                    val sqliteDb = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
                    sqliteDb.execSQL("UPDATE salary_settings SET employeeName = ? WHERE id = 1", arrayOf(targetWorkerName))
                    sqliteDb.close()
                } catch (e: Exception) {
                    Log.e("RestoreBackup", "Failed to update employeeName in restored DB", e)
                }

                // Register worker account and set as active user
                val sharedPrefs = context.getSharedPreferences("salary_app_prefs", Context.MODE_PRIVATE)
                val usersSet = sharedPrefs.getStringSet("registered_users", emptySet())?.toMutableSet() ?: mutableSetOf()
                usersSet.add(targetWorkerName)
                sharedPrefs.edit()
                    .putStringSet("registered_users", usersSet)
                    .putString("active_user", targetWorkerName)
                    .apply()

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showBackupDialog = false
                    val msg = "✅ تم استعادة قاعدة بيانات العامل ($targetWorkerName) بنجاح! يتم الآن تحديث التطبيق."
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    fileOpNotification = Pair(true, msg)

                    kotlinx.coroutines.delay(1800)
                    val activity = context.findActivity()
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                        activity?.finish()
                    } else {
                        activity?.recreate()
                    }
                }
            } catch (e: Exception) {
                Log.e("RestoreBackup", "Error restoring db", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = "❌ حدث خطأ أثناء استعادة ملف قاعدة البيانات: ${e.localizedMessage}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    fileOpNotification = Pair(false, msg)
                }
            }
        }
    }

    val handleRestoreUri: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            var fileName = "ملف خارجي (.db)"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) fileName = name
                        }
                    }
                }
            } catch (_: Exception) {}
            performRestoreFromStream({ context.contentResolver.openInputStream(uri) }, fileName)
        } else {
            val msg = "⚠️ تم إلغاء اختيار ملف استعادة قاعدة البيانات"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            fileOpNotification = Pair(false, msg)
        }
    }

    val universalFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: result.data?.clipData?.getItemAt(0)?.uri
            if (uri != null) {
                handleRestoreUri(uri)
            } else {
                val msg = "⚠️ تم إلغاء اختيار ملف استعادة قاعدة البيانات"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                fileOpNotification = Pair(false, msg)
            }
        }
    }

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

    LaunchedEffect(registeredUsers, activeUser) {
        if (registeredUsers.size == 1 && (activeUser.isNullOrBlank() || settingsState?.employeeName.isNullOrBlank()) && !userRequestedAccountSwitch) {
            viewModel.loginUser(registeredUsers.first())
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
    if (appSecurityState.isEnabled && !isAppUnlocked) {
        AppLockScreen(
            correctPin = appSecurityState.pin,
            biometricEnabled = appSecurityState.isBiometricEnabled,
            onUnlockSuccess = { isAppUnlocked = true }
        )
    } else if (settingsState == null && activeUser != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppColors.current.bg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = EmeraldGreen)
        }
    } else if (userRequestedAccountSwitch || activeUser.isNullOrBlank() || settingsState?.employeeName.isNullOrBlank() || registeredUsers.isEmpty()) {
        WelcomeLoginScreen(
            registeredUsers = registeredUsers,
            alwaysOpenDefaultAccount = alwaysOpenDefaultAccount,
            onToggleAlwaysOpen = { enabled, targetUser ->
                viewModel.setAlwaysOpenDefaultAccount(enabled, targetUser)
            },
            onLogin = { userName, rememberAlways ->
                userRequestedAccountSwitch = false
                hasGreeted = false
                if (rememberAlways) {
                    viewModel.setAlwaysOpenDefaultAccount(true, userName)
                } else {
                    viewModel.setAlwaysOpenDefaultAccount(false)
                }
                viewModel.loginUser(userName)
            },
            onRegister = { name, salary, startDate, currency, standardSpending, dailyReminderEnabled, dailyReminderHour, dailyReminderMinute, openingBalance, rememberAlways ->
                userRequestedAccountSwitch = false
                hasGreeted = false
                if (rememberAlways) {
                    viewModel.setAlwaysOpenDefaultAccount(true, name)
                } else {
                    viewModel.setAlwaysOpenDefaultAccount(false)
                }
                viewModel.registerNewUserAccount(
                    name = name,
                    salary = salary,
                    startDate = startDate,
                    currency = currency,
                    standardSpending = standardSpending,
                    dailyReminderEnabled = dailyReminderEnabled,
                    dailyReminderHour = dailyReminderHour,
                    dailyReminderMinute = dailyReminderMinute,
                    openingBalance = openingBalance
                )
            },
            onDeleteUser = { userName ->
                viewModel.deleteUser(userName)
            },
            onPrintBalancesReport = {
                showPrintWorkersOptionsDialog = true
            }
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
                        val empName = if (!activeUser.isNullOrBlank()) activeUser?.trim() else settingsState?.employeeName?.trim()
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
                    // Switch Worker / Accounts Button
                    IconButton(
                        onClick = { showWorkerAccountsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "تبديل العامل / الحسابات",
                            tint = EarnedBlue,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    // PDF Print / Export Report Button
                    IconButton(
                        onClick = { showPdfOptionsDialog = true },
                        modifier = Modifier.testTag("top_app_bar_print_pdf")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "طباعة وتصدير الكشف PDF",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    // Menu Drawer Trigger Button
                    IconButton(
                        onClick = { showMainMenuDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "القائمة الرئيسية",
                            tint = LocalAppColors.current.text,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalAppColors.current.bg
                )
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTxDialog = true },
                containerColor = EmeraldGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 12.dp, end = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة عملية مخصصة",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        bottomBar = {
            Surface(
                color = LocalAppColors.current.bg,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    AdMobBanner()
                }
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
            WelcomeSection(
                settings = settingsState ?: SalarySettings(),
                onPrintPdfClick = { showPdfOptionsDialog = true },
                onManageLeavesClick = { showLeaveManagementDialog = true }
            )

            // --- ACTIVE LEAVE / WORK PAUSED BANNER ---
            val activeLeave = LeaveUtils.getCurrentlyActiveLeave(leavesState)
            if (activeLeave != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AmberOrange.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, AmberOrange)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(AmberOrange.copy(alpha = 0.2f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BeachAccess,
                                    contentDescription = null,
                                    tint = AmberOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🏖️ العامل في فترة إجازة حالياً",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalAppColors.current.text
                                )
                                val endTxt = if (activeLeave.endDate != null) "حتى ${LeaveUtils.formatLeaveDate(activeLeave.endDate)}" else "مفتوحة حتى الآن"
                                Text(
                                    text = "بدأت من: ${LeaveUtils.formatLeaveDate(activeLeave.startDate)} ($endTxt) - ${activeLeave.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalAppColors.current.textSub,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = "💡 الأجر اليومي والصرفيات التلقائية متوقفة خلال الإجازة. يمكنك تسجيل السحبيات أو الحوالات أو صرفة يوم السفر بصورة طبيعية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalAppColors.current.textMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activeLeave.endDate == null) {
                                Button(
                                    onClick = { viewModel.endOngoingLeave(activeLeave.id, System.currentTimeMillis()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("إنهاء الإجازة والعودة ◀️", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            OutlinedButton(
                                onClick = { showLeaveManagementDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إدارة الإجازات ⚙️", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (settingsState?.isWorkPaused == true) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AmberOrange.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AmberOrange)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(AmberOrange.copy(alpha = 0.2f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                tint = AmberOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⏸️ احتساب العمل متوقف مؤقتاً",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                            Text(
                                text = "تراكم الأجر اليومي متوقف حالياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textSub
                            )
                        }
                        Button(
                            onClick = { viewModel.toggleWorkPause(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("استئناف ◀️", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

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
                onSpendingClick = { amount ->
                    AdMobManager.showInterstitial(activity) {
                        viewModel.addTransaction("SPENDING", amount, null, "صرفة")
                    }
                },
                onAbsenceClick = {
                    AdMobManager.showInterstitial(activity) {
                        viewModel.addTransaction("ABSENCE", 12.0, null, "غياب يوم كامل")
                    }
                }
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
                    showPdfOptionsDialog = true
                }
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    }

    // --- PDF OPTIONS DIALOG ---
    if (showPdfOptionsDialog) {
        PdfOptionsDialog(
            settings = settingsState ?: SalarySettings(),
            onDismiss = { showPdfOptionsDialog = false },
            onPrint = { startDate, endDate, showSummary, balanceMode, typeFilter ->
                showPdfOptionsDialog = false
                val settings = settingsState ?: SalarySettings()
                val txList = transactionsState
                val metrics = metricsState
                PdfPrintHelper.printSalaryReport(
                    context = context,
                    settings = settings,
                    transactions = txList,
                    metrics = metrics,
                    startDateFilter = startDate,
                    endDateFilter = endDate,
                    showTotalSummary = showSummary,
                    balanceDisplayMode = balanceMode,
                    transactionTypeFilter = typeFilter,
                    salaryHistories = salaryHistoriesState,
                    leaves = leavesState,
                    onSuccess = {
                        AdMobManager.showInterstitialAd(activity)
                    }
                )
            }
        )
    }

    // --- MAIN MENU DIALOG ---
    if (showMainMenuDialog) {
        MainMenuDialog(
            appLanguage = settingsState?.appLanguage ?: "ar",
            onDismiss = { showMainMenuDialog = false },
            onOpenAppSettings = {
                showMainMenuDialog = false
                AdMobManager.showInterstitial(activity) {
                    showSettingsDialog = true
                }
            },
            onOpenUserSettings = {
                showMainMenuDialog = false
                AdMobManager.showInterstitial(activity) {
                    showUserSettingsDialog = true
                }
            },
            onOpenBackup = {
                showMainMenuDialog = false
                AdMobManager.showInterstitial(activity) {
                    showBackupDialog = true
                }
            },
            onCheckUpdate = {
                showMainMenuDialog = false
                showUpdateDialog = true
                viewModel.checkForUpdates(isManual = true)
            },
            onOpenGuide = {
                showMainMenuDialog = false
                AdMobManager.showInterstitial(activity) {
                    showGuideDialog = true
                }
            },
            onOpenDeveloperInfo = {
                showMainMenuDialog = false
                AdMobManager.showInterstitial(activity) {
                    showDeveloperInfoDialog = true
                }
            },
            onSwitchWorker = {
                showMainMenuDialog = false
                AdMobManager.showInterstitial(activity) {
                    showWorkerAccountsDialog = true
                }
            },
            onPrintBalancesReport = {
                showMainMenuDialog = false
                showPrintWorkersOptionsDialog = true
            },
            onSettleAccount = {
                showMainMenuDialog = false
                showSettleConfirmDialog = true
            },
            onManageLeaves = {
                showMainMenuDialog = false
                showLeaveManagementDialog = true
            }
        )
    }

    // --- LEAVE MANAGEMENT DIALOG ---
    if (showLeaveManagementDialog) {
        LeaveManagementDialog(
            leaves = leavesState,
            currency = (settingsState ?: SalarySettings()).currency,
            onDismiss = { showLeaveManagementDialog = false },
            onAddLeave = { startDate, endDate, reason ->
                viewModel.addLeavePeriod(startDate, endDate, reason)
            },
            onEndOngoingLeave = { leaveId, endDate ->
                viewModel.endOngoingLeave(leaveId, endDate)
            },
            onDeleteLeave = { leaveId ->
                viewModel.deleteLeavePeriod(leaveId)
            }
        )
    }

    // --- WORKER ACCOUNTS & SWITCH WORKER DIALOG ---
    if (showWorkerAccountsDialog) {
        WorkerAccountsDialog(
            currentActiveUser = activeUser,
            registeredUsers = registeredUsers,
            alwaysOpenDefaultAccount = alwaysOpenDefaultAccount,
            onToggleAlwaysOpen = { enabled ->
                viewModel.setAlwaysOpenDefaultAccount(enabled, activeUser)
            },
            onDismiss = { showWorkerAccountsDialog = false },
            onLogin = { userName ->
                showWorkerAccountsDialog = false
                hasGreeted = false
                viewModel.loginUser(userName)
            },
            onRegister = { name, salary, startDate, currency, standardSpending, dailyReminderEnabled, dailyReminderHour, dailyReminderMinute, openingBalance ->
                showWorkerAccountsDialog = false
                hasGreeted = false
                viewModel.registerNewUserAccount(
                    name = name,
                    salary = salary,
                    startDate = startDate,
                    currency = currency,
                    standardSpending = standardSpending,
                    dailyReminderEnabled = dailyReminderEnabled,
                    dailyReminderHour = dailyReminderHour,
                    dailyReminderMinute = dailyReminderMinute,
                    openingBalance = openingBalance
                )
            },
            onDeleteUser = { userName ->
                viewModel.deleteUser(userName)
            },
            onPrintBalancesReport = {
                showWorkerAccountsDialog = false
                showPrintWorkersOptionsDialog = true
            }
        )
    }

    // --- SETTLE AND CLOSE PERIOD CONFIRMATION DIALOG ---
    if (showSettleConfirmDialog) {
        val cur = settingsState?.currency ?: "ريال يمني"
        var cutOffDateMs by remember { mutableStateOf(System.currentTimeMillis()) }
        var showCutoffDatePicker by remember { mutableStateOf(false) }

        val sdfCutoff = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val cutoffFormatted = sdfCutoff.format(Date(cutOffDateMs))

        if (showCutoffDatePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = cutOffDateMs }
            android.app.DatePickerDialog(
                context,
                { _, yr, mo, dy ->
                    val selectedCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, yr)
                        set(Calendar.MONTH, mo)
                        set(Calendar.DAY_OF_MONTH, dy)
                    }
                    cutOffDateMs = selectedCal.timeInMillis
                    showCutoffDatePicker = false
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog(
            onDismissRequest = { showSettleConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "إقفال وتصفية حساب الفترة 🔒",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "تاريخ الإقفال والتصفية:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.textSub
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCutoffDatePicker = true },
                        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "حتى تاريخ: $cutoffFormatted",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalAppColors.current.text
                                )
                            }
                            Text("تغيير 📅", color = EmeraldGreen, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = LocalAppColors.current.bg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "1. يتم تدوير المبلغ الصافي المستحق حتى تاريخ ($cutoffFormatted) ليكون رصيداً افتتاحياً جديداً للفترة التالية.",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.text
                            )
                            Text(
                                text = "2. تشمل التصفية كافة الصرفيات، السحبيات، والغيابات المسجلة حتى هذا اليوم.",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.text
                            )
                            Text(
                                text = "3. تبدأ الفترة الجديدة من اليوم التالي لتاريخ الإقفال تلقائياً، مع الاحتفاظ التام بكافة السجلات السابقة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettleConfirmDialog = false
                        AdMobManager.showInterstitial(activity) {
                            viewModel.settleAndLockAccount(cutOffDateMs)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تأكيد التصفية والإقفال 🔒", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSettleConfirmDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إلغاء", color = LocalAppColors.current.text)
                }
            },
            containerColor = LocalAppColors.current.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // --- BACKUP DIALOG ---
    if (showBackupDialog) {
        BackupDialog(
            appLanguage = settingsState?.appLanguage ?: "ar",
            onDismiss = { showBackupDialog = false },
            onSaveToPhone = {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val (success, msg) = saveBackupToSmartWorkerFolder(context, viewModel)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        fileOpNotification = Pair(success, msg)
                        if (success) {
                            AdMobManager.showInterstitialAd(activity)
                        }
                    }
                }
            },
            onShareBackup = {
                exportAndShareBackup(context, viewModel, coroutineScope) { success, msg ->
                    fileOpNotification = Pair(success, msg)
                    if (success) {
                        AdMobManager.showInterstitialAd(activity)
                    }
                }
            },
            onRestoreFromFile = { file ->
                performRestoreFromStream({ FileInputStream(file) }, file.name)
            },
            onBrowseExternalFile = {
                launchUniversalFilePicker(context, universalFilePickerLauncher) { errMsg ->
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    fileOpNotification = Pair(false, errMsg)
                }
            }
        )
    }

    // --- FILE OPERATION STATUS DIALOG ---
    if (fileOpNotification != null) {
        val (isSuccess, msg) = fileOpNotification!!
        AlertDialog(
            onDismissRequest = { fileOpNotification = null },
            icon = {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isSuccess) EmeraldGreen else Color.Red,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (isSuccess) "نجاح العملية" else "تنبيه حالة العملية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text
                )
            },
            text = {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textSub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { fileOpNotification = null },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSuccess) EmeraldGreen else EarnedBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حسناً", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = LocalAppColors.current.surface,
            shape = RoundedCornerShape(20.dp)
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
            onOpenSalaryHistory = {
                showSalaryHistoryDialog = true
            },
            onDismiss = { showUserSettingsDialog = false },
            onSave = { employeeName, salary, currency, standardSpending, startDate, autoAddDailySpending, isSpendingDeductedFromSalary, isWorkPaused, openingBalance, priorAbsences, priorSpendings, priorDraws ->
                AdMobManager.showInterstitial(activity) {
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
                        isWorkPaused = isWorkPaused,
                        themeMode = curr.themeMode,
                        appLanguage = curr.appLanguage
                    )
                }
                showUserSettingsDialog = false
            }
        )
    }

    // --- SALARY HISTORY DIALOG ---
    if (showSalaryHistoryDialog) {
        SalaryHistoryDialog(
            salaryHistories = salaryHistoriesState,
            currency = settingsState?.currency ?: "ريال",
            onAddHistory = { monthlySalary, effectiveStartDate, note ->
                viewModel.addSalaryHistory(monthlySalary, effectiveStartDate, note)
            },
            onDeleteHistory = { id ->
                viewModel.deleteSalaryHistory(id)
            },
            onDismiss = { showSalaryHistoryDialog = false }
        )
    }

    // --- APP SETTINGS DIALOG ---
    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settingsState ?: SalarySettings(),
            currentAppSecurity = appSecurityState,
            onDismiss = { showSettingsDialog = false },
            onSave = { voiceEnabled, dailyReminderEnabled, dailyReminderHour, dailyReminderMinute, securityEnabled, securityPin, biometricEnabled, themeMode, appLanguage ->
                AdMobManager.showInterstitial(activity) {
                    viewModel.updateAppSecurity(securityEnabled, securityPin, biometricEnabled)
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
                }
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
                AdMobManager.showInterstitial(activity) {
                    viewModel.addTransaction(type, amount, null, note, timestamp)
                }
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
                AdMobManager.showInterstitial(activity) {
                    viewModel.updateTransaction(updatedTx)
                }
            },
            onDelete = { txToDelete ->
                viewModel.deleteTransaction(txToDelete)
            }
        )
    }
    }

    // --- PRINT WORKERS OPTIONS DIALOG (DATE & PERSON SELECTION) ---
    if (showPrintWorkersOptionsDialog) {
        val usersList = if (registeredUsers.isEmpty()) {
            activeUser?.let { listOf(it) } ?: emptyList()
        } else {
            registeredUsers
        }
        PrintWorkersOptionsDialog(
            registeredUsers = usersList,
            activeUser = activeUser,
            onDismiss = { showPrintWorkersOptionsDialog = false },
            onConfirmPrint = { cutoffDateMs, selectedWorkers ->
                showPrintWorkersOptionsDialog = false
                coroutineScope.launch {
                    val balances = viewModel.getAllWorkersBalances(
                        cutoffDateMs = cutoffDateMs,
                        selectedWorkerNames = selectedWorkers
                    )
                    if (balances.isEmpty()) {
                        Toast.makeText(context, "لم يتم العثور على بيانات للعمال المحددين", Toast.LENGTH_SHORT).show()
                    } else {
                        val act = context.findActivity()
                        if (act != null) {
                            AdMobManager.showInterstitialAd(act) {
                                PdfPrintHelper.printWorkerBalancesReport(context, balances, cutoffDateMs)
                            }
                        } else {
                            PdfPrintHelper.printWorkerBalancesReport(context, balances, cutoffDateMs)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun WelcomeSection(
    settings: SalarySettings,
    onPrintPdfClick: () -> Unit = {},
    onManageLeavesClick: () -> Unit = {}
) {
    val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ar"))
    val dateString = sdf.format(Date())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LocalAppColors.current.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = "تاريخ اليوم:",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSub,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = dateString,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onManageLeavesClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberOrange),
                    border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BeachAccess, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("سجل الإجازات 🏖️", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPrintPdfClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("طباعة الكشف 📋", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
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
            val isPositive = metrics.netBalance > 0.001
            val isNegative = metrics.netBalance < -0.001
            val balanceColor = when {
                isPositive -> EmeraldGreen
                isNegative -> CrimsonRed
                else -> Color.Gray
            }
            val balanceTitle = when {
                isPositive -> "المتبقي لك (له)"
                isNegative -> "المطلوب عليك (سلف)"
                else -> "الحساب مصفر"
            }
            val balanceValueStr = when {
                isPositive -> "${metrics.netBalance.toInt()}"
                isNegative -> "${Math.abs(metrics.netBalance.toInt())}-"
                else -> "0"
            }
            Text(
                text = balanceTitle,
                style = MaterialTheme.typography.titleMedium,
                color = balanceColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = balanceValueStr,
                style = MaterialTheme.typography.displayMedium,
                color = balanceColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = currency,
                style = MaterialTheme.typography.titleMedium,
                color = balanceColor,
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
                title = "السحبيات والسلف",
                value = "${metrics.totalDraws.toInt()}",
                unit = currency,
                color = AmberOrange,
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
        }

        if (metrics.totalDeposits > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "إيداعات ودفعات للحساب",
                    value = "+${metrics.totalDeposits.toInt()}",
                    unit = currency,
                    color = DepositBlue,
                    icon = Icons.Default.Paid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
        "DEPOSIT" -> DepositBlue
        "OPENING_BALANCE" -> if (transaction.amount >= 0) EmeraldGreen else CrimsonRed
        "SETTLEMENT" -> if (transaction.amount >= 0) EmeraldGreen else CrimsonRed
        else -> EarnedBlue
    }

    val icon = when (transaction.type) {
        "SPENDING" -> Icons.Default.ReceiptLong
        "DRAW" -> Icons.Default.AccountBalanceWallet
        "ABSENCE" -> Icons.Default.EventBusy
        "DEPOSIT" -> Icons.Default.Paid
        "OPENING_BALANCE" -> Icons.Default.AccountBalance
        "SETTLEMENT" -> Icons.Default.Lock
        else -> Icons.Default.CheckCircle
    }

    val typeLabel = when (transaction.type) {
        "SPENDING" -> "صرفة"
        "DRAW" -> "سحبية"
        "ABSENCE" -> "تسجيل غياب"
        "DEPOSIT" -> "إيداع للحساب"
        "OPENING_BALANCE" -> if (transaction.amount >= 0) "رصيد افتتاحي (له)" else "رصيد افتتاحي (عليه سلف)"
        "SETTLEMENT" -> if (transaction.amount >= 0) "تصفية حساب (تدوير له)" else "تصفية حساب (تدوير عليه)"
        else -> "عملية"
    }

    val amountLabel = when (transaction.type) {
        "ABSENCE" -> {
            val hrs = transaction.amount
            if (hrs >= 12.0 || hrs <= 0.0) "غياب يوم كامل" else "غياب ${AbsenceUtils.formatAbsenceHoursDescription(hrs)}"
        }
        "SPENDING" -> "-${transaction.amount.toInt()} $currency"
        "DRAW" -> "-${transaction.amount.toInt()} $currency"
        "DEPOSIT" -> "+${transaction.amount.toInt()} $currency"
        "OPENING_BALANCE" -> if (transaction.amount >= 0) "+${transaction.amount.toInt()} $currency" else "-${Math.abs(transaction.amount.toInt())} $currency"
        "SETTLEMENT" -> if (transaction.amount >= 0) "تدوير له: +${transaction.amount.toInt()} $currency" else "تدوير عليه: -${Math.abs(transaction.amount.toInt())} $currency"
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
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalAppColors.current.text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textMuted
                )

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
    var selectedType by remember { mutableStateOf("DRAW") } // DRAW, SPENDING, ABSENCE, DEPOSIT
    var amountInput by remember(selectedType) { 
        mutableStateOf(
            if (selectedType == "SPENDING") settings.standardSpendingAmount.toInt().toString() else ""
        ) 
    }
    var noteInput by remember { mutableStateOf("") }
    
    // Auto-fill note depending on type
    LaunchedEffect(selectedType) {
        if (selectedType == "SPENDING") {
            noteInput = "صرفة"
        } else if (selectedType == "DEPOSIT") {
            noteInput = "إيداع للحساب"
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val types = listOf(
                        Triple("DEPOSIT", "إيداع للحساب", DepositBlue),
                        Triple("ABSENCE", "غياب", Color.Gray),
                        Triple("SPENDING", "صرفة", CrimsonRed),
                        Triple("DRAW", "سحبية", AmberOrange)
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
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else color,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (selectedType != "ABSENCE") {
                    // Amount Field
                    Text(
                        text = when (selectedType) {
                            "DRAW" -> "مبلغ السحبية / السلفة (${settings.currency}):"
                            "DEPOSIT" -> "مبلغ الإيداع / الدفعة للحساب (${settings.currency}):"
                            else -> "مبلغ الصرفة (${settings.currency}):"
                        },
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
                                text = when (selectedType) {
                                    "SPENDING" -> "${settings.standardSpendingAmount.toInt()}"
                                    "DEPOSIT" -> "أدخل المبلغ المودع للحساب..."
                                    else -> "أدخل المبلغ هنا..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.textMuted
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = when (selectedType) {
                                "SPENDING" -> CrimsonRed
                                "DRAW" -> AmberOrange
                                "DEPOSIT" -> DepositBlue
                                else -> Color.Gray
                            },
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Note Field
                    Text(
                        text = when (selectedType) {
                            "DEPOSIT" -> "بيان وملاحظة الإيداع (اختياري - تفاصيل الدفعة):"
                            else -> "ملاحظة وبيان العملية (اختياري - لأيش):"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        placeholder = {
                            Text(
                                text = when (selectedType) {
                                    "SPENDING" -> "مثال: صرفة غداء، باص..."
                                    "DEPOSIT" -> "مثال: تسليم دفعة للعم، فوق الحساب..."
                                    else -> "مثال: سلفة لمصاريف العيد..."
                                },
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
                            text = "عدد ساعات الغياب (مثال: 1.50 = ساعه ونص، 1.25 = ساعه وربع، 1.45 = ساعه و45 دقيقة):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.textSub
                        )
                        OutlinedTextField(
                            value = absenceHoursInput,
                            onValueChange = { absenceHoursInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                            placeholder = {
                                Text(
                                    text = "مثال: 1.50 أو 1.25 أو 1.45 أو 1:30",
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
                        if (absenceHoursInput.isNotBlank()) {
                            val calcHrs = AbsenceUtils.parseAbsenceHours(absenceHoursInput)
                            if (calcHrs > 0) {
                                Text(
                                    text = "💡 المدة المحسوبة: ${AbsenceUtils.formatAbsenceHoursDescription(calcHrs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
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
                                val hrs = if (absenceIsFullDay) 12.0 else AbsenceUtils.parseAbsenceHours(absenceHoursInput)
                                val descText = AbsenceUtils.formatAbsenceHoursDescription(hrs)
                                val note = if (noteInput.isBlank()) {
                                    if (absenceIsFullDay) "غياب يوم كامل" else "غياب جزئي ($descText)"
                                } else noteInput
                                if (hrs > 0.0) {
                                    onSave("ABSENCE", hrs, note, transactionDate)
                                }
                            } else {
                                val amt = amountInput.toDoubleOrNull() ?: if (selectedType == "SPENDING") settings.standardSpendingAmount else 0.0
                                val defaultNote = when (selectedType) {
                                    "SPENDING" -> "صرفة"
                                    "DRAW" -> "سحبية / سلفة"
                                    "DEPOSIT" -> "إيداع للحساب"
                                    else -> "عملية"
                                }
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
                                "DEPOSIT" -> DepositBlue
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
    currentAppSecurity: AppSecurityState,
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

    // Security States (Global App-Level Security)
    var securityEnabled by remember { mutableStateOf(currentAppSecurity.isEnabled) }
    var securityPinInput by remember { mutableStateOf(currentAppSecurity.pin) }
    var biometricEnabled by remember { mutableStateOf(currentAppSecurity.isBiometricEnabled) }

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
fun PdfOptionsDialog(
    settings: SalarySettings,
    onDismiss: () -> Unit,
    onPrint: (startDate: Long?, endDate: Long?, showTotalSummary: Boolean, balanceDisplayMode: String, transactionTypeFilter: String) -> Unit
) {
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
    val startOfMonthMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    var startDateFilter by remember { mutableStateOf<Long?>(startOfMonthMs) }
    var endDateFilter by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    var showTotalSummary by remember { mutableStateOf(true) }
    var balanceDisplayMode by remember { mutableStateOf("AFTER_EACH_TX") } // "AFTER_EACH_TX", "END_OF_MONTH", "END_OF_REPORT"
    var transactionTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "SPENDING", "DRAW", "ABSENCE", "SETTLEMENT"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إعدادات وتخصيص طباعة PDF 🖨️",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // 1. Date Range Options
                Text(
                    text = "1. تحديد الفترة الزمنية للكشف:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text
                )

                val isFromStartSelected = startDateFilter == null
                OutlinedButton(
                    onClick = {
                        startDateFilter = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isFromStartSelected) EmeraldGreen.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = if (isFromStartSelected) EmeraldGreen else LocalAppColors.current.text
                    ),
                    border = BorderStroke(
                        width = if (isFromStartSelected) 2.dp else 1.dp,
                        color = if (isFromStartSelected) EmeraldGreen else LocalAppColors.current.border
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = if (isFromStartSelected) EmeraldGreen else LocalAppColors.current.textSub,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "من بداية التسجيل (كشف شامل لكافة التواريخ)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isFromStartSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start Date
                    Column(modifier = Modifier.weight(1f)) {
                        Text("من تاريخ:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                        OutlinedButton(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(
                                width = if (!isFromStartSelected) 1.5.dp else 1.dp,
                                color = if (!isFromStartSelected) EmeraldGreen else LocalAppColors.current.border
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = startDateFilter?.let { sdf.format(Date(it)) } ?: "من بداية النظام",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!isFromStartSelected) EmeraldGreen else LocalAppColors.current.text
                            )
                        }
                    }

                    // End Date
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إلى تاريخ:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                        OutlinedButton(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = endDateFilter?.let { sdf.format(Date(it)) } ?: "الآن",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.text
                            )
                        }
                    }
                }

                // 2. Transaction Type Filter Selection
                HorizontalDivider(color = LocalAppColors.current.border, thickness = 0.5.dp)

                Text(
                    text = "2. نوع العمليات المراد طباعتها في الكشف:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text
                )

                val filterOptions = listOf(
                    "ALL" to "📋 جميع العمليات (شامل)",
                    "SPENDING" to "🛒 الصرفيات والمصروفات فقط",
                    "DRAW" to "💰 السحبيات (السلف المالية) فقط",
                    "DEPOSIT" to "💳 الإيداعات والدفعات للحساب فقط",
                    "ABSENCE" to "📅 سجل الغيابات والخصومات فقط",
                    "SETTLEMENT" to "🔒 التصفيات والأرصدة الافتتاحية فقط"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filterOptions.forEach { (typeKey, label) ->
                        val isSelected = transactionTypeFilter == typeKey
                        Surface(
                            onClick = { transactionTypeFilter = typeKey },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) EmeraldGreen.copy(alpha = 0.12f) else Color.Transparent,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) EmeraldGreen else LocalAppColors.current.border.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { transactionTypeFilter = typeKey },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) EmeraldGreen else LocalAppColors.current.text
                                )
                            }
                        }
                    }
                }

                // 3. Balance Display Mode
                HorizontalDivider(color = LocalAppColors.current.border, thickness = 0.5.dp)

                Text(
                    text = "3. طريقة إظهار وحساب الرصيد المتبقي:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { balanceDisplayMode = "AFTER_EACH_TX" }
                    ) {
                        RadioButton(
                            selected = balanceDisplayMode == "AFTER_EACH_TX",
                            onClick = { balanceDisplayMode = "AFTER_EACH_TX" },
                            colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                        )
                        Text(
                            text = "رصيد متتابع بعد كل عملية في الجدول",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.text
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { balanceDisplayMode = "END_OF_MONTH" }
                    ) {
                        RadioButton(
                            selected = balanceDisplayMode == "END_OF_MONTH",
                            onClick = { balanceDisplayMode = "END_OF_MONTH" },
                            colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                        )
                        Text(
                            text = "تجميع وإقفال ملخص بنهاية كل شهر",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.text
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { balanceDisplayMode = "END_OF_REPORT" }
                        ) {
                        RadioButton(
                            selected = balanceDisplayMode == "END_OF_REPORT",
                            onClick = { balanceDisplayMode = "END_OF_REPORT" },
                            colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                        )
                        Text(
                            text = "إظهار صافي الرصيد في أسفل التقرير فقط",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalAppColors.current.text
                        )
                    }
                }

                // 4. Header Card Toggle
                HorizontalDivider(color = LocalAppColors.current.border, thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = showTotalSummary,
                        onCheckedChange = { showTotalSummary = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldGreen)
                    )
                    Text(
                        text = "4. إظهار كرت الملخص المالي العلوي:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.text
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إلغاء", color = LocalAppColors.current.text)
                    }

                    Button(
                        onClick = {
                            onPrint(startDateFilter, endDateFilter, showTotalSummary, balanceDisplayMode, transactionTypeFilter)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("طباعة الآن 📄", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        val calendar = Calendar.getInstance()
        startDateFilter?.let { calendar.timeInMillis = it }
        android.app.DatePickerDialog(
            LocalContext.current,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startDateFilter = c.timeInMillis
                showStartDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showStartDatePicker = false }
            show()
        }
    }

    if (showEndDatePicker) {
        val calendar = Calendar.getInstance()
        endDateFilter?.let { calendar.timeInMillis = it }
        android.app.DatePickerDialog(
            LocalContext.current,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                endDateFilter = c.timeInMillis
                showEndDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showEndDatePicker = false }
            show()
        }
    }
}

@Composable
fun UserSettingsDialog(
    currentSettings: SalarySettings,
    currencies: List<CurrencyItem> = emptyList(),
    onAddCustomCurrency: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteCurrency: ((CurrencyItem) -> Unit)? = null,
    onOpenSalaryHistory: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (
        employeeName: String,
        salary: Double,
        currency: String,
        standardSpending: Double,
        startDate: Long,
        autoAddDailySpending: Boolean,
        isSpendingDeductedFromSalary: Boolean,
        isWorkPaused: Boolean,
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
    var isWorkPaused by remember { mutableStateOf(currentSettings.isWorkPaused) }
    var isOpeningBalanceCredit by remember { mutableStateOf(currentSettings.openingBalance >= 0) }
    var openingBalanceInput by remember { mutableStateOf(Math.abs(currentSettings.openingBalance.toInt()).toString()) }
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
                    placeholder = { Text("ادخل الراتب الشهري", color = Color.Gray) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
                    )
                )

                if (onOpenSalaryHistory != null) {
                    OutlinedButton(
                        onClick = { onOpenSalaryHistory.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EmeraldGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                    ) {
                        Text(
                            text = "📅 سجل وتحديثات الراتب (حسب التاريخ والشهور)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

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

                // Work Pause Toggle Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isWorkPaused,
                        onCheckedChange = { isWorkPaused = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AmberOrange
                        )
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "⏸️ إيقاف العمل مؤقتاً (إجازة / سفر):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = LocalAppColors.current.text
                        )
                        Text(
                            text = "تجميد احتساب الأجر اليومي والتنبيهات عند الإجازة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalAppColors.current.textSub
                        )
                    }
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
                    text = "رصيد سابق / حساب افتتاحي (حالة الرصيد القديم):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textSub
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isOpeningBalanceCredit,
                        onClick = { isOpeningBalanceCredit = true },
                        label = { Text("له دائن (+)", fontWeight = if (isOpeningBalanceCredit) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isOpeningBalanceCredit,
                        onClick = { isOpeningBalanceCredit = false },
                        label = { Text("عليه سلف/مدين (-)", fontWeight = if (!isOpeningBalanceCredit) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonRed,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = openingBalanceInput,
                    onValueChange = { openingBalanceInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("مثلاً: 50000 أو 0 إذا لا يوجد", color = Color.Gray) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isOpeningBalanceCredit) EmeraldGreen else CrimsonRed,
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
                            val rawOpBal = openingBalanceInput.toDoubleOrNull() ?: 0.0
                            val openingBalance = if (isOpeningBalanceCredit) Math.abs(rawOpBal) else -Math.abs(rawOpBal)

                            onSave(
                                employeeNameVal,
                                salary,
                                currencyVal,
                                standardSpending,
                                selectedDateInMillis,
                                autoAddDailySpending,
                                isSpendingDeductedFromSalary,
                                isWorkPaused,
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
                val cal = Calendar.getInstance().apply {
                    timeInMillis = date
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedDateInMillis = cal.timeInMillis
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
    var amountInput by remember {
        mutableStateOf(
            if (transaction.type == "ABSENCE") {
                AbsenceUtils.formatAbsenceHoursForInput(transaction.amount)
            } else {
                transaction.amount.toInt().toString()
            }
        )
    }
    var voiceTextInput by remember { mutableStateOf(transaction.voiceText ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateFormater = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
    val formattedDate = dateFormater.format(Date(transaction.timestamp))

    val typeLabel = when (transaction.type) {
        "SPENDING" -> "صرفة مصروف"
        "DRAW" -> "سحبية / سلفة"
        "ABSENCE" -> "تسجيل غياب"
        "DEPOSIT" -> "إيداع / دفعة للحساب"
        "OPENING_BALANCE" -> if (transaction.amount >= 0) "رصيد افتتاحي سابق (له)" else "رصيد افتتاحي سابق (عليه سلف)"
        "SETTLEMENT" -> if (transaction.amount >= 0) "تصفية وإقفال حساب (تدوير له)" else "تصفية وإقفال حساب (تدوير عليه)"
        else -> "عملية"
    }

    val typeColor = when (transaction.type) {
        "SPENDING" -> CrimsonRed
        "DRAW" -> AmberOrange
        "ABSENCE" -> Color.Gray
        "DEPOSIT" -> DepositBlue
        "OPENING_BALANCE" -> if (transaction.amount >= 0) EmeraldGreen else CrimsonRed
        "SETTLEMENT" -> if (transaction.amount >= 0) EmeraldGreen else CrimsonRed
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
                    "ABSENCE" -> "مدة الغياب بالساعات والدقائق (مثال: 1.50 = ساعه ونص، 1.25 = ساعه وربع، 1.45 = ساعه و45 دقيقة):"
                    "DEPOSIT" -> "مبلغ الإيداع / الدفعة للحساب ($currency):"
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                    placeholder = {
                        if (transaction.type == "ABSENCE") {
                            Text("مثال: 1.50 أو 1.25 أو 1.45", color = LocalAppColors.current.textMuted)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = typeColor,
                        unfocusedBorderColor = LocalAppColors.current.border,
                        focusedTextColor = LocalAppColors.current.text,
                        unfocusedTextColor = LocalAppColors.current.text
                    )
                )

                if (transaction.type == "ABSENCE" && amountInput.isNotBlank()) {
                    val calcHrs = AbsenceUtils.parseAbsenceHours(amountInput)
                    if (calcHrs > 0) {
                        Text(
                            text = "💡 المدة المحسوبة: ${AbsenceUtils.formatAbsenceHoursDescription(calcHrs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

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
                        val parsedVal = if (transaction.type == "ABSENCE") {
                            AbsenceUtils.parseAbsenceHours(amountInput)
                        } else {
                            amountInput.toDoubleOrNull() ?: transaction.amount
                        }
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
fun WorkerAccountsDialog(
    currentActiveUser: String?,
    registeredUsers: List<String>,
    alwaysOpenDefaultAccount: Boolean = false,
    onToggleAlwaysOpen: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onLogin: (String) -> Unit,
    onRegister: (String, Double, Long, String, Double, Boolean, Int, Int, Double) -> Unit,
    onDeleteUser: (String) -> Unit,
    onPrintBalancesReport: () -> Unit = {}
) {
    val context = LocalContext.current
    var showCreateForm by remember { mutableStateOf(registeredUsers.isEmpty()) }
    var userToDelete by remember { mutableStateOf<String?>(null) }

    var nameInput by remember { mutableStateOf("") }
    var salaryInput by remember { mutableStateOf("") }
    var currencyInput by remember { mutableStateOf("ريال يمني") }
    var standardSpendingInput by remember { mutableStateOf("2000") }
    var isOpeningBalanceCredit by remember { mutableStateOf(true) }
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateInMillis = it }
                    showDatePicker = false
                }) {
                    Text("موافق", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء", color = CrimsonRed)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = {
                Text("حذف حساب العامل ⚠️", fontWeight = FontWeight.Bold, color = CrimsonRed)
            },
            text = {
                Text(
                    "هل أنت متأكد من حذف حساب (${userToDelete}) بجميع بياناته وعملياته المالية؟ لا يمكن التراجع عن هذا الإجراء.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.text
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = userToDelete!!
                        userToDelete = null
                        onDeleteUser(name)
                        Toast.makeText(context, "تم حذف حساب $name", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("نعم، حذف الحساب", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("إلغاء", color = LocalAppColors.current.textSub)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(EmeraldGreen.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "حسابات العمال",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                            Text(
                                text = "التبديل بين الحسابات أو إضافة عامل جديد",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textSub
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = LocalAppColors.current.textSub)
                    }
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                if (!currentActiveUser.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "الحساب النشط حالياً:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentActiveUser,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalAppColors.current.text
                                )
                            }
                        }
                    }
                }

                if (registeredUsers.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "اختر حساب العامل للتبديل مباشرة:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = LocalAppColors.current.text
                        )

                        registeredUsers.forEach { user ->
                            val isActive = (user == currentActiveUser)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) LocalAppColors.current.bg else LocalAppColors.current.bg.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (isActive) EmeraldGreen else LocalAppColors.current.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isActive) EmeraldGreen else EarnedBlue
                                        )
                                        Column {
                                            Text(
                                                text = user,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = LocalAppColors.current.text
                                            )
                                            if (isActive) {
                                                Text(
                                                    text = "💚 مفعل الآن",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!isActive) {
                                            Button(
                                                onClick = { onLogin(user) },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("تبديل للحساب 🔑", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        IconButton(onClick = { userToDelete = user }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف العامل", tint = CrimsonRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (registeredUsers.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleAlwaysOpen(!alwaysOpenDefaultAccount) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (alwaysOpenDefaultAccount) EmeraldGreen.copy(alpha = 0.12f) else LocalAppColors.current.bg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (alwaysOpenDefaultAccount) EmeraldGreen else LocalAppColors.current.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = if (alwaysOpenDefaultAccount) EmeraldGreen else LocalAppColors.current.textMuted
                                )
                                Column {
                                    Text(
                                        text = "الدخول التلقائي دائماً بهذا الحساب عند الفتح",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LocalAppColors.current.text
                                    )
                                    Text(
                                        text = if (alwaysOpenDefaultAccount)
                                            "مفعل: يتم فتح حساب (${currentActiveUser ?: ""}) مباشرة عند تشغيل التطبيق."
                                            else "معطل: تظهر قائمة اختيار الحسابات في كل مرة تفتح فيها التطبيق.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (alwaysOpenDefaultAccount) EmeraldGreen else LocalAppColors.current.textMuted
                                    )
                                }
                            }
                            Switch(
                                checked = alwaysOpenDefaultAccount,
                                onCheckedChange = { onToggleAlwaysOpen(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = EmeraldGreen
                                )
                            )
                        }
                    }

                    Button(
                        onClick = onPrintBalancesReport,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📄 طباعة كشف أرصدة جميع العمال (PDF)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = { showCreateForm = !showCreateForm },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showCreateForm) LocalAppColors.current.border else EarnedBlue
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (showCreateForm) Icons.Default.ExpandLess else Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showCreateForm) "إخفاء نموذج الإضافة" else "عمل / إضافة حساب عامل جديد",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(visible = showCreateForm) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, EarnedBlue.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "📝 بيانات العامل الجديد:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EarnedBlue
                            )

                            Text("اسم العامل / الموظف:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                placeholder = { Text("مثلاً: أخي علي / حسام", color = LocalAppColors.current.textMuted) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = LocalAppColors.current.border
                                )
                            )

                            Text("الراتب الشهري الأساسي:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                            OutlinedTextField(
                                value = salaryInput,
                                onValueChange = { salaryInput = it },
                                placeholder = { Text("ادخل الراتب الشهري", color = LocalAppColors.current.textMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = LocalAppColors.current.border
                                )
                            )

                            Text("العملة الرئيسية:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                            OutlinedTextField(
                                value = currencyInput,
                                onValueChange = { currencyInput = it },
                                placeholder = { Text("مثلاً: ريال يمني", color = LocalAppColors.current.textMuted) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = LocalAppColors.current.border
                                )
                            )

                            val popularCurrenciesList = listOf(
                                "ريال يمني 🇾🇪", "ريال سعودي 🇸🇦", "دولار أمريكي 💵", "درهم إماراتي 🇦🇪", "جنيه مصري 🇪🇬", "دينار كويتي 🇰🇼"
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

                            Text("رصيد سابق / افتتاحي (حالة الرصيد القديم):", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = isOpeningBalanceCredit,
                                    onClick = { isOpeningBalanceCredit = true },
                                    label = { Text("له دائن (+)", fontWeight = if (isOpeningBalanceCredit) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldGreen,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !isOpeningBalanceCredit,
                                    onClick = { isOpeningBalanceCredit = false },
                                    label = { Text("عليه سلف/مدين (-)", fontWeight = if (!isOpeningBalanceCredit) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonRed,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = openingBalanceInput,
                                onValueChange = { openingBalanceInput = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder = { Text("0 إذا لا يوجد", color = LocalAppColors.current.textMuted) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isOpeningBalanceCredit) EmeraldGreen else CrimsonRed,
                                    unfocusedBorderColor = LocalAppColors.current.border
                                )
                            )

                            Text("تاريخ بدء العمل:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                            Button(
                                onClick = { showDatePicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.surface),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                border = BorderStroke(1.dp, LocalAppColors.current.border)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = EmeraldGreen)
                                    Text(
                                        text = sdf.format(Date(selectedDateInMillis)),
                                        color = LocalAppColors.current.text,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Switch(
                                    checked = dailyReminderEnabled,
                                    onCheckedChange = { dailyReminderEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen)
                                )
                                Text("تأكيد التنبيه الصوتي اليومي", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.text)
                            }

                            Button(
                                onClick = {
                                    if (nameInput.isBlank()) {
                                        Toast.makeText(context, "الرجاء إدخال اسم العامل", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val sal = salaryInput.toDoubleOrNull() ?: 0.0
                                    val stdSpend = standardSpendingInput.toDoubleOrNull() ?: 2000.0
                                    val rawOpBal = openingBalanceInput.toDoubleOrNull() ?: 0.0
                                    val opBal = if (isOpeningBalanceCredit) Math.abs(rawOpBal) else -Math.abs(rawOpBal)
                                    
                                    onRegister(
                                        nameInput.trim(),
                                        sal,
                                        selectedDateInMillis,
                                        currencyInput.trim().ifBlank { "ريال يمني" },
                                        stdSpend,
                                        dailyReminderEnabled,
                                        dailyReminderHour,
                                        dailyReminderMinute,
                                        opBal
                                    )
                                    Toast.makeText(context, "✅ تم إنشاء حساب العامل (${nameInput.trim()}) والتبديل إليه بنجاح!", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("حفظ وإنشاء الحساب والدخول مباشرة 🚀", color = Color.White, fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintWorkersOptionsDialog(
    registeredUsers: List<String>,
    activeUser: String?,
    onDismiss: () -> Unit,
    onConfirmPrint: (cutoffDateMs: Long?, selectedWorkers: Set<String>) -> Unit
) {
    val context = LocalContext.current
    var selectedWorkers by remember(registeredUsers) { mutableStateOf(registeredUsers.toSet()) }

    // Date selection mode:
    // 0 = حتى اليوم (الرصيد الفعلي الحالي)
    // 1 = نهاية الشهر السابق (تلقائياً)
    // 2 = تاريخ مخصص
    var dateMode by remember { mutableIntStateOf(0) }

    // Calculate end of previous month (e.g., in September, gives August 31st at 23:59:59)
    val lastDayOfPreviousMonthMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    val prevMonthName = remember {
        val cal = Calendar.getInstance().apply { timeInMillis = lastDayOfPreviousMonthMs }
        SimpleDateFormat("MMMM", Locale("ar")).format(cal.time)
    }
    val prevMonthDateStr = remember {
        SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date(lastDayOfPreviousMonthMs))
    }

    var customDateMs by remember { mutableStateOf(lastDayOfPreviousMonthMs) }
    var showCustomDatePicker by remember { mutableStateOf(false) }

    val dateSdf = remember { SimpleDateFormat("yyyy/MM/dd", Locale("ar")) }

    val effectiveCutoffMs: Long? = when (dateMode) {
        0 -> null
        1 -> lastDayOfPreviousMonthMs
        2 -> customDateMs
        else -> null
    }

    if (showCustomDatePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = customDateMs }
        android.app.DatePickerDialog(
            context,
            { _, yr, mo, dy ->
                val chosenCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, yr)
                    set(Calendar.MONTH, mo)
                    set(Calendar.DAY_OF_MONTH, dy)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                customDateMs = chosenCal.timeInMillis
                dateMode = 2
                showCustomDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showCustomDatePicker = false }
            show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "خيارات طباعة كشف الأرصدة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                            Text(
                                text = "تخصيص تاريخ الاحتساب والعمال المشمولين",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textMuted
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(LocalAppColors.current.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = LocalAppColors.current.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = LocalAppColors.current.border)
                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // --- SECTION 1: DATE SELECTION ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
                        border = BorderStroke(1.dp, LocalAppColors.current.border)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = EarnedBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تاريخ احتساب الأرصدة:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalAppColors.current.text
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Option 0: حتى اليوم (الرصيد الفعلي الحالي)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { dateMode = 0 },
                                color = if (dateMode == 0) EarnedBlue.copy(alpha = 0.12f) else LocalAppColors.current.bg,
                                border = BorderStroke(
                                    width = if (dateMode == 0) 1.5.dp else 1.dp,
                                    color = if (dateMode == 0) EarnedBlue else LocalAppColors.current.border
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = dateMode == 0,
                                        onClick = { dateMode = 0 },
                                        colors = RadioButtonDefaults.colors(selectedColor = EarnedBlue)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "حتى اليوم (الرصيد الفعلي الحالي)",
                                            fontWeight = if (dateMode == 0) FontWeight.Bold else FontWeight.Normal,
                                            color = LocalAppColors.current.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "احتساب جميع الساعات والأيام والعمليات حتى اللحظة",
                                            color = LocalAppColors.current.textMuted,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Option 1: نهاية الشهر السابق (مثلاً 31/08 لنهاية شهر 8)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { dateMode = 1 },
                                color = if (dateMode == 1) Color(0xFF059669).copy(alpha = 0.12f) else LocalAppColors.current.bg,
                                border = BorderStroke(
                                    width = if (dateMode == 1) 1.5.dp else 1.dp,
                                    color = if (dateMode == 1) Color(0xFF059669) else LocalAppColors.current.border
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = dateMode == 1,
                                        onClick = { dateMode = 1 },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF059669))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "نهاية الشهر السابق ($prevMonthDateStr)",
                                            fontWeight = if (dateMode == 1) FontWeight.Bold else FontWeight.Normal,
                                            color = LocalAppColors.current.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "مطابقة كشف صاحب الشغل لنهاية شهر $prevMonthName",
                                            color = LocalAppColors.current.textMuted,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Option 2: تاريخ مخصص يحدده المستخدم
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        dateMode = 2
                                        showCustomDatePicker = true
                                    },
                                color = if (dateMode == 2) Color(0xFFD97706).copy(alpha = 0.12f) else LocalAppColors.current.bg,
                                border = BorderStroke(
                                    width = if (dateMode == 2) 1.5.dp else 1.dp,
                                    color = if (dateMode == 2) Color(0xFFD97706) else LocalAppColors.current.border
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = dateMode == 2,
                                        onClick = {
                                            dateMode = 2
                                            showCustomDatePicker = true
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD97706))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "تحديد تاريخ كشف مخصص 📅",
                                            fontWeight = if (dateMode == 2) FontWeight.Bold else FontWeight.Normal,
                                            color = LocalAppColors.current.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (dateMode == 2) "التاريخ المعتمد: ${dateSdf.format(Date(customDateMs))}" else "اختر أي تاريخ كشف تريده (مثلاً 31/08)",
                                            color = if (dateMode == 2) Color(0xFFD97706) else LocalAppColors.current.textMuted,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (dateMode == 2) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            dateMode = 2
                                            showCustomDatePicker = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("تغيير 📅", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Notice Banner
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LocalAppColors.current.bg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (effectiveCutoffMs != null) Color(0xFF059669) else EarnedBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (effectiveCutoffMs != null) {
                                            "⏳ سيتم احتساب الأرصدة وساعات العمل والمسحوبات حتى نهاية يوم ${dateSdf.format(Date(effectiveCutoffMs))} فقط."
                                        } else {
                                            "⏳ سيتم احتساب الأرصدة الفعلية حتى تاريخ ولحظة اليوم."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalAppColors.current.text
                                    )
                                }
                            }
                        }
                    }

                    // --- SECTION 2: WORKERS SELECTION ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
                        border = BorderStroke(1.dp, LocalAppColors.current.border)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تحديد العمال المشمولين (${selectedWorkers.size} من ${registeredUsers.size}):",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = LocalAppColors.current.text
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        selectedWorkers = if (selectedWorkers.size == registeredUsers.size) {
                                            emptySet()
                                        } else {
                                            registeredUsers.toSet()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (selectedWorkers.size == registeredUsers.size) "إلغاء التحديد" else "تحديد الكل",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Workers List
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                registeredUsers.forEach { workerName ->
                                    val isSelected = selectedWorkers.contains(workerName)
                                    val isCurrentActive = workerName == activeUser

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedWorkers = if (isSelected) {
                                                    selectedWorkers - workerName
                                                } else {
                                                    selectedWorkers + workerName
                                                }
                                            },
                                        color = if (isSelected) EmeraldGreen.copy(alpha = 0.08f) else LocalAppColors.current.bg,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.2.dp else 1.dp,
                                            color = if (isSelected) EmeraldGreen.copy(alpha = 0.6f) else LocalAppColors.current.border
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedWorkers = if (checked) {
                                                        selectedWorkers + workerName
                                                    } else {
                                                        selectedWorkers - workerName
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (isSelected) EmeraldGreen else LocalAppColors.current.textMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = workerName,
                                                fontWeight = FontWeight.SemiBold,
                                                color = LocalAppColors.current.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isCurrentActive) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = EmeraldGreen.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "الحساب الحالي",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = EmeraldGreen,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedWorkers.isEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "⚠️ يجب تحديد عامل واحد على الأقل لطباعة الكشف",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = LocalAppColors.current.border)
                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء", color = LocalAppColors.current.text)
                    }

                    Button(
                        onClick = {
                            if (selectedWorkers.isNotEmpty()) {
                                onConfirmPrint(effectiveCutoffMs, selectedWorkers)
                            }
                        },
                        enabled = selectedWorkers.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            disabledContainerColor = LocalAppColors.current.border
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(2f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedWorkers.isEmpty()) "حدد عمال للطباعة" else "📄 طباعة (${selectedWorkers.size} عمال)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
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
    alwaysOpenDefaultAccount: Boolean = false,
    onToggleAlwaysOpen: ((Boolean, String?) -> Unit)? = null,
    onLogin: (String, Boolean) -> Unit,
    onRegister: (String, Double, Long, String, Double, Boolean, Int, Int, Double, Boolean) -> Unit,
    onDeleteUser: (String) -> Unit,
    onPrintBalancesReport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var rememberChoice by remember(alwaysOpenDefaultAccount) { mutableStateOf(alwaysOpenDefaultAccount) }
    var nameInput by remember { mutableStateOf("") }
    var salaryInput by remember { mutableStateOf("") }
    var currencyInput by remember { mutableStateOf("ريال يمني") }
    var standardSpendingInput by remember { mutableStateOf("2000") }
    var isOpeningBalanceCredit by remember { mutableStateOf(true) }
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

            // Multi-Worker Accounts Card (If registered workers exist)
            if (registeredUsers.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Group, contentDescription = null, tint = EmeraldGreen)
                            Text(
                                text = "حسابات العمال المسجلة (اختر عامل للدخول):",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                        }

                        HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                        registeredUsers.forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LocalAppColors.current.bg, RoundedCornerShape(12.dp))
                                    .clickable { onLogin(user, rememberChoice) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = EarnedBlue)
                                    Text(
                                        text = user,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = LocalAppColors.current.text
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onLogin(user, rememberChoice) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("دخول الحساب", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(onClick = { onDeleteUser(user) }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف العامل", tint = CrimsonRed)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rememberChoice = !rememberChoice },
                            colors = CardDefaults.cardColors(
                                containerColor = if (rememberChoice) EmeraldGreen.copy(alpha = 0.12f) else LocalAppColors.current.bg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (rememberChoice) EmeraldGreen else LocalAppColors.current.border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Switch(
                                    checked = rememberChoice,
                                    onCheckedChange = { rememberChoice = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = EmeraldGreen
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "تحديد كحساب دائم (الدخول به دائماً)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LocalAppColors.current.text
                                    )
                                    Text(
                                        text = if (rememberChoice)
                                            "سيتم فتح الحساب المختار تلقائياً في كل مرة تفتح فيها التطبيق دون إظهار هذه القائمة."
                                            else "ستظهر هذه القائمة لاختيار الحساب في كل مرة تفتح فيها التطبيق.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (rememberChoice) EmeraldGreen else LocalAppColors.current.textMuted
                                    )
                                }
                            }
                        }

                        if (onPrintBalancesReport != null) {
                            Button(
                                onClick = onPrintBalancesReport,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📄 طباعة كشف أرصدة جميع العمال (PDF)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Text(
                text = if (registeredUsers.isEmpty()) "الرجاء إدخال بيانات العامل لإعداد الحساب والبدء." else "أو إضافة حساب عامل جديد:",
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
                        placeholder = { Text("ادخل الراتب الشهري", color = LocalAppColors.current.textMuted) },
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
                        text = "رصيد سابق / حساب افتتاحي (حالة الرصيد القديم):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isOpeningBalanceCredit,
                            onClick = { isOpeningBalanceCredit = true },
                            label = { Text("له دائن (+)", fontWeight = if (isOpeningBalanceCredit) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreen,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isOpeningBalanceCredit,
                            onClick = { isOpeningBalanceCredit = false },
                            label = { Text("عليه سلف/مدين (-)", fontWeight = if (!isOpeningBalanceCredit) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = openingBalanceInput,
                        onValueChange = { openingBalanceInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("مثلاً: 50000 أو 0 إذا لا يوجد", color = LocalAppColors.current.textMuted) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isOpeningBalanceCredit) EmeraldGreen else CrimsonRed,
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
                        val rawOpBal = openingBalanceInput.toDoubleOrNull() ?: 0.0
                        val openingBalance = if (isOpeningBalanceCredit) Math.abs(rawOpBal) else -Math.abs(rawOpBal)
                        
                        onRegister(
                            nameInput.trim(),
                            salary,
                            selectedDateInMillis,
                            currency,
                            standardSpending,
                            dailyReminderEnabled,
                            dailyReminderHour,
                            dailyReminderMinute,
                            openingBalance,
                            rememberChoice
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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun getSmartWorkerBackupFileName(viewModel: SalaryViewModel): String {
    val rawAccountName = viewModel.activeUser.value?.takeIf { it.isNotBlank() }
        ?: viewModel.settings.value?.employeeName?.takeIf { it.isNotBlank() }
        ?: "حساب_العامل"
    val cleanAccountName = rawAccountName.trim().replace(" ", "_").replace(Regex("[^a-zA-Z0-9_\u0600-\u06FF]"), "")
    val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
    return "SmartWorker_${cleanAccountName}_$timeStamp.db"
}

private fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestStoragePermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "يرجى تفعيل صلاحية إدارة الملفات من إعدادات الهاتف", Toast.LENGTH_LONG).show()
            }
        }
    } else {
        val activity = context.findActivity()
        if (activity != null) {
            androidx.core.app.ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001
            )
        } else {
            Toast.makeText(context, "يرجى منح صلاحية الوصول للملفات من إعدادات التطبيق", Toast.LENGTH_LONG).show()
        }
    }
}

private fun saveBackupToSmartWorkerFolder(context: Context, viewModel: SalaryViewModel): Pair<Boolean, String> {
    try {
        val currentActiveUser = viewModel.activeUser.value
        val dbName = AppDatabase.getDatabaseNameForUser(currentActiveUser)
        AppDatabase.checkpointDatabase(context, currentActiveUser)
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            return Pair(false, "❌ لم يتم العثور على ملف قاعدة البيانات")
        }
        val backupFileName = getSmartWorkerBackupFileName(viewModel)
        var saved = false

        try {
            val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SmartWorker")
            if (!docsDir.exists()) docsDir.mkdirs()
            val targetDocsFile = File(docsDir, backupFileName)
            FileInputStream(dbFile).use { input ->
                FileOutputStream(targetDocsFile).use { output ->
                    input.copyTo(output)
                }
            }
            saved = targetDocsFile.exists()
        } catch (e: Exception) {
            Log.e("BackupSave", "Direct file save failed", e)
        }

        if (!saved && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/SmartWorker")
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(dbFile).use { `in` -> `in`.copyTo(out) }
                    }
                    saved = true
                }
            } catch (_: Exception) {}
        }

        if (saved) {
            Log.d("SalaryApp", "BACKUP_SUCCESS")
            return Pair(
                true,
                "✅ تم حفظ النسخة الاحتياطية بنجاح في مجلد (SmartWorker) داخل المستندات باسم:\n\n$backupFileName\n\n💡 ملاحظة: تم إنشاء ملف واحد فقط في المستندات لتجنب التكرار."
            )
        } else {
            return Pair(false, "❌ تعذر حفظ النسخة في ذاكرة الهاتف. يرجى التحقق من أذونات التخزين (اضغط على زر تفعيل أذونات الملفات) أو استخدم زر المشاركة.")
        }
    } catch (e: Exception) {
        Log.e("BackupSave", "Error in saveBackupToSmartWorkerFolder", e)
        return Pair(false, "❌ حدث خطأ أثناء حفظ النسخة الاحتياطية: ${e.localizedMessage}")
    }
}

private fun listSavedBackupFiles(): List<File> {
    val files = mutableMapOf<String, File>()
    try {
        val dirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SmartWorker"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SmartWorker")
        )
        for (dir in dirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { file -> file.isFile && file.name.endsWith(".db", ignoreCase = true) }?.forEach {
                    if (!files.containsKey(it.name) || (it.lastModified() > (files[it.name]?.lastModified() ?: 0L))) {
                        files[it.name] = it
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("BackupList", "Error listing backup files", e)
    }
    return files.values.sortedByDescending { it.lastModified() }
}

private fun launchUniversalFilePicker(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<Intent>, onFail: (String) -> Unit) {
    val intentsToTry = listOf(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("*/*", "application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3", "application/sql", "application/database"))
        },
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        },
        Intent("com.sec.android.app.myfiles.PICK_DATA").apply {
            putExtra("CONTENT_TYPE", "*/*")
            addCategory(Intent.CATEGORY_DEFAULT)
        },
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
        },
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
        }
    )

    for (intent in intentsToTry) {
        try {
            launcher.launch(intent)
            return
        } catch (e: Exception) {
            Log.w("BackupPicker", "Intent launch failed: ${intent.action}", e)
        }
    }

    try {
        val chooser = Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }, "اختر ملف النسخة الاحتياطية")
        launcher.launch(chooser)
        return
    } catch (e: Exception) {
        Log.e("BackupPicker", "Chooser failed", e)
    }

    onFail("⚠️ تعذر فتح متصفح ملفات النظام في هاتفك. لا تقلق! يرجى استخدام (مستكشف الملفات الداخلي) بالضغط على الزر الأزرق في النافذة لتصفح جميع مجلدات هاتفك مباشرة واختيار نسختك بسهولة.")
}

private fun exportAndShareBackup(
    context: Context,
    viewModel: SalaryViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val currentActiveUser = viewModel.activeUser.value
            val dbName = AppDatabase.getDatabaseNameForUser(currentActiveUser)
            AppDatabase.checkpointDatabase(context, currentActiveUser)
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = "❌ لم يتم العثور على ملف قاعدة البيانات"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onResult(false, msg)
                }
                return@launch
            }

            val backupFileName = getSmartWorkerBackupFileName(viewModel)
            val cacheFile = File(context.cacheDir, backupFileName)
            
            FileInputStream(dbFile).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "النسخة الاحتياطية لقاعدة البيانات (.db) - تطبيق حساب العامل الذكي")
                putExtra(Intent.EXTRA_TEXT, "ملف النسخة الاحتياطية لقاعدة البيانات ($backupFileName). يمكنك استعادته مباشرة من داخل التطبيق.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Log.d("SalaryApp", "BACKUP_SUCCESS")
                val msg = "✅ تم تجهيز ملف قاعدة البيانات (.db) للتصدير والمشاركة بنجاح!"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onResult(true, msg)
                context.startActivity(Intent.createChooser(shareIntent, "تصدير أو مشاركة ملف قاعدة البيانات (.db):"))
            }
        } catch (e: Exception) {
            Log.e("Backup", "Error exporting db", e)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val msg = "❌ حدث خطأ أثناء تصدير ملف قاعدة البيانات"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onResult(false, msg)
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
    onOpenDeveloperInfo: () -> Unit,
    onSwitchWorker: () -> Unit = {},
    onPrintBalancesReport: () -> Unit = {},
    onSettleAccount: () -> Unit = {},
    onManageLeaves: () -> Unit = {}
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
                .background(Color.Black.copy(alpha = if (isVisible) 0.55f else 0.0f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { dismissWithAnimation() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(350)),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(280)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {},
                    color = LocalAppColors.current.surface,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(5.dp)
                                .background(LocalAppColors.current.border.copy(alpha = 0.8f), CircleShape)
                        )

                        // Sheet Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(EmeraldGreen.copy(alpha = 0.2f), EarnedBlue.copy(alpha = 0.2f))
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "القائمة الرئيسية",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LocalAppColors.current.text
                                    )
                                    Text(
                                        text = "إدارة إعدادات وتفضيلات الحساب",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalAppColors.current.textMuted
                                    )
                                }
                            }

                            IconButton(
                                onClick = { dismissWithAnimation() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = LocalAppColors.current.textSub,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.6f), thickness = 1.dp)

                        // Section 1: Settings & Customization
                        MenuSectionGroup(title = "إعدادات التطبيق والحساب") {
                            MenuOptionCard(
                                title = "إعدادات التطبيق",
                                subtitle = "الأمان، العملة، والإشعارات التلقائية",
                                icon = Icons.Default.Settings,
                                color = EmeraldGreen,
                                onClick = { dismissWithAnimation(); onOpenAppSettings() }
                            )
                            HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.35f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            MenuOptionCard(
                                title = "إعدادات المستخدم",
                                subtitle = "بيانات العامل، الراتب الشهري، وساعات العمل",
                                icon = Icons.Default.Person,
                                color = EarnedBlue,
                                onClick = { dismissWithAnimation(); onOpenUserSettings() }
                            )
                            HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.35f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            MenuOptionCard(
                                title = "👥 حسابات العمال (تبديل العامل)",
                                subtitle = "التبديل بين حسابات العمال أو إضافة عامل جديد",
                                icon = Icons.Default.Group,
                                color = Color(0xFF00897B),
                                onClick = { dismissWithAnimation(); onSwitchWorker() }
                            )
                            HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.35f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            MenuOptionCard(
                                title = "🏖️ إدارة وسجل الإجازات",
                                subtitle = "تسجيل إجازة مفتوحة أو محددة وإيقاف المستحقات والصرفيات",
                                icon = Icons.Default.BeachAccess,
                                color = AmberOrange,
                                onClick = { dismissWithAnimation(); onManageLeaves() }
                            )
                            HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.35f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            MenuOptionCard(
                                title = "🔒 تصفية وإقفال حساب الفترة الحالية",
                                subtitle = "تدوير الصافي المتبقي كرصيد افتتاحي وتصفير العدادات للفترة القادمة",
                                icon = Icons.Default.Lock,
                                color = CrimsonRed,
                                onClick = { dismissWithAnimation(); onSettleAccount() }
                            )
                        }

                        // Section 2: Data & Updates
                        MenuSectionGroup(title = "البيانات والتقارير والتحديثات") {
                            MenuOptionCard(
                                title = "📄 طباعة كشف أرصدة جميع العمال (PDF)",
                                subtitle = "تصدير كشف شامل لجميع العمال المسجلين وأرصدتهم الحالية",
                                icon = Icons.Default.Print,
                                color = EmeraldGreen,
                                onClick = { dismissWithAnimation(); onPrintBalancesReport() }
                            )
                            HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.35f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            MenuOptionCard(
                                title = "النسخ الاحتياطي والاستعادة",
                                subtitle = "حفظ واسترجاع ملف قاعدة البيانات بأمان",
                                icon = Icons.Default.Share,
                                color = Color(0xFF8E24AA),
                                onClick = { dismissWithAnimation(); onOpenBackup() }
                            )
                            HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.35f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            MenuOptionCard(
                                title = "تحديثات التطبيق",
                                subtitle = "التحقق من أحدث الإصدارات والمميزات الجديدة",
                                icon = Icons.Default.Refresh,
                                color = Color(0xFF00ACC1),
                                onClick = { dismissWithAnimation(); onCheckUpdate() }
                            )
                        }

                        // Section 3: Help & About
                        MenuSectionGroup(title = "المساعدة والمعلومات") {
                            MenuOptionCard(
                                title = "لمحة ودليل الاستخدام",
                                subtitle = "شرح طريقة عمل الحسابات والتقارير المالية",
                                icon = Icons.Default.Book,
                                color = Color(0xFFFF9800),
                                onClick = { dismissWithAnimation(); onOpenGuide() }
                            )
                            HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.35f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                            MenuOptionCard(
                                title = "معلومات المطور",
                                subtitle = "تواصل مع مبرمج ومطور التطبيق للدعم والمقترحات",
                                icon = Icons.Default.Info,
                                color = AmberOrange,
                                onClick = { dismissWithAnimation(); onOpenDeveloperInfo() }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { dismissWithAnimation() },
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(strings.cancelBtn, color = LocalAppColors.current.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
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
    onSaveToPhone: () -> Unit,
    onShareBackup: () -> Unit,
    onRestoreFromFile: (File) -> Unit,
    onBrowseExternalFile: () -> Unit
) {
    val appLang = AppLanguage.fromCode(appLanguage)
    val strings = LanguageStrings(appLang)
    val context = LocalContext.current
    var confirmRestoreFile by remember { mutableStateOf<File?>(null) }
    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    var showInternalExplorer by remember { mutableStateOf(false) }
    var currentBrowseDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var currentDirFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentDirFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(currentBrowseDir, showInternalExplorer, hasPermission) {
        if (showInternalExplorer) {
            try {
                val all = currentBrowseDir.listFiles() ?: emptyArray()
                currentDirFolders = all.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
                currentDirFiles = all.filter { 
                    it.isFile && !it.name.startsWith(".") && (it.name.endsWith(".db", ignoreCase = true) || it.name.endsWith(".sqlite", ignoreCase = true) || it.name.endsWith(".db3", ignoreCase = true) || it.name.endsWith(".sqlite3", ignoreCase = true))
                }.sortedByDescending { it.lastModified() }
            } catch (e: Exception) {
                currentDirFolders = emptyList()
                currentDirFiles = emptyList()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .heightIn(max = 660.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border)
        ) {
            if (showInternalExplorer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(600.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = { showInternalExplorer = false }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = LocalAppColors.current.text)
                            }
                            Text("📂 مستكشف ملفات الهاتف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalAppColors.current.text)
                        }
                        if (!hasPermission) {
                            TextButton(onClick = { requestStoragePermission(context); hasPermission = hasStoragePermission(context) }) {
                                Text("🔑 تفعيل الأذونات", color = AmberOrange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (!hasPermission) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AmberOrange.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberOrange),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚠️ لتصفح مجلداتك (مثل WhatsApp والمستندات) بحرية، يرجى تفعيل الصلاحية:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.text, textAlign = TextAlign.Center)
                                Button(
                                    onClick = { requestStoragePermission(context); hasPermission = hasStoragePermission(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("🔑 منح صلاحية إدارة الملفات", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { currentBrowseDir = Environment.getExternalStorageDirectory() },
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🏠 الذاكرة الرئيسية", color = LocalAppColors.current.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "")
                                if (d.exists()) currentBrowseDir = d else Toast.makeText(context, "المجلد غير موجود", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📁 المستندات", color = LocalAppColors.current.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "")
                                if (d.exists()) currentBrowseDir = d else Toast.makeText(context, "المجلد غير موجود", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📥 التنزيلات", color = LocalAppColors.current.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val wa1 = File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents")
                                val wa2 = File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Documents")
                                val wa3 = File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media")
                                val waTarget = when {
                                    wa1.exists() -> wa1
                                    wa2.exists() -> wa2
                                    wa3.exists() -> wa3
                                    else -> File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp")
                                }
                                if (waTarget.exists()) currentBrowseDir = waTarget else Toast.makeText(context, "مجلد واتساب غير موجود في هذا المسار", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366).copy(alpha = 0.2f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("💬 واتساب (WhatsApp)", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📍 ${currentBrowseDir.absolutePath.replace("/storage/emulated/0", "الذاكرة الداخلية")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.text,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentBrowseDir != Environment.getExternalStorageDirectory() && currentBrowseDir.parentFile != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { currentBrowseDir = currentBrowseDir.parentFile!! },
                                    colors = ButtonDefaults.buttonColors(containerColor = EarnedBlue),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("⬆️ للأعلى", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (currentDirFolders.isEmpty() && currentDirFiles.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (!hasPermission) "⚠️ لا يمكن عرض المحتوى بسبب عدم تفعيل أذونات الوصول للملفات. اضغط على زر (منح صلاحية إدارة الملفات) بالأعلى." else "📭 لا توجد مجلدات أو ملفات نسخ احتياطية (.db) في هذا المسار",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.textSub,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(currentDirFolders.size) { idx ->
                                val folder = currentDirFolders[idx]
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.5.dp, LocalAppColors.current.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentBrowseDir = folder }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("📁", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = LocalAppColors.current.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            items(currentDirFiles.size) { idx ->
                                val file = currentDirFiles[idx]
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EmeraldGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { confirmRestoreFile = file }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("💾", style = MaterialTheme.typography.titleMedium)
                                            Column {
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldGreen,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val dateStr = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar")).format(Date(file.lastModified()))
                                                val sizeKb = file.length() / 1024
                                                Text(
                                                    text = "$dateStr (${sizeKb} KB)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = LocalAppColors.current.textSub
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = { confirmRestoreFile = file },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("استعادة", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showInternalExplorer = false },
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("الرجوع للقائمة السابقة", color = LocalAppColors.current.text, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color(0xFF8E24AA),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "إدارة النسخ الاحتياطية (.db)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = LocalAppColors.current.text
                        )
                    }

                    if (!hasPermission) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AmberOrange.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AmberOrange),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "⚠️ أذونات الوصول لملفات ومجلدات الهاتف غير مفعلة",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberOrange
                                )
                                Text(
                                    text = "لتجنب مشاكل المتصفح الخارجي ولتمكين التطبيق من تصفح مجلداتك (مثل المستندات و WhatsApp) وحفظ واستعادة النسخ بحرية، يرجى منح الصلاحية:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalAppColors.current.text,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        requestStoragePermission(context)
                                        hasPermission = hasStoragePermission(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("🔑 تفعيل صلاحية إدارة الملفات في الإعدادات", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.5f), thickness = 1.dp)

                    Text(
                        text = "📥 إنشاء ومشاركة نسخة احتياطية (.db)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        text = "يتم حفظ النسخة مباشرة في ذاكرة الهاتف في مجلد (SmartWorker) بتسمية ذكية تحتوي على اسم الحساب والتاريخ والوقت.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSub,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onSaveToPhone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "نسخة احتياطية في الجوال",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = {
                            onShareBackup()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مشاركة النسخة الاحتياطية",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    HorizontalDivider(color = LocalAppColors.current.border.copy(alpha = 0.5f), thickness = 1.dp)

                    Text(
                        text = "🔄 استعادة البيانات من نسخة محفوظة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0),
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Button(
                        onClick = {
                            if (!hasPermission) {
                                requestStoragePermission(context)
                            }
                            showInternalExplorer = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "استرجاع النسخة الاحتياطية من الهاتف",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onBrowseExternalFile()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE65100))
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "استرجاع عبر متصفح الملفات الخارجي (ملفاتي)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.cancelBtn, color = LocalAppColors.current.text, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (confirmRestoreFile != null) {
        AlertDialog(
            onDismissRequest = { confirmRestoreFile = null },
            icon = { Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(36.dp)) },
            title = { Text("تأكيد استعادة النسخة الاحتياطية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalAppColors.current.text) },
            text = {
                Text(
                    "هل أنت متأكد من رغبتك في استعادة البيانات من الملف:\n\n(${confirmRestoreFile!!.name})؟\n\nسيتم استبدال البيانات الحالية ببيانات هذه النسخة وإعادة تشغيل التطبيق.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textSub,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = confirmRestoreFile!!
                        confirmRestoreFile = null
                        onDismiss()
                        onRestoreFromFile(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EarnedBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("نعم، استعادة الآن", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreFile = null }) {
                    Text("إلغاء", color = LocalAppColors.current.textSub)
                }
            },
            containerColor = LocalAppColors.current.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun MenuSectionGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textSub,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, LocalAppColors.current.border.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)),
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
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
                    color = LocalAppColors.current.textMuted
                )
            }
        }

        Icon(
            imageVector = Icons.Default.KeyboardArrowLeft,
            contentDescription = null,
            tint = LocalAppColors.current.textMuted.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
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
    val isMandatoryUpdate = updateInfoState.hasUpdate && updateInfoState.isMandatory
    Dialog(
        onDismissRequest = {
            if (!isMandatoryUpdate) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatoryUpdate,
            dismissOnClickOutside = !isMandatoryUpdate
        )
    ) {
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
                        text = if (isMandatoryUpdate) "🚨 تحديث إجباري ضروري!" else "📢 يتوفر تحديث جديد للتطبيق!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isMandatoryUpdate) CrimsonRed else EmeraldGreen
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
                            if (!isMandatoryUpdate) {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isMandatoryUpdate) CrimsonRed else EmeraldGreen),
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
                                text = "لا توجد تحديثات جديدة متاحة حالياً.\nإصدارك الحالي: v1.3.0",
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

                if (!isMandatoryUpdate) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.border),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إغلاق", color = LocalAppColors.current.text, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "⚠️ هذا التحديث إجباري ولا يمكن استخدام التطبيق حتى يتم التحديث.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonRed,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
                            text = "📱 تطبيق حساب العامل الذكي v1.3.0",
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

@Composable
fun SalaryHistoryDialog(
    salaryHistories: List<SalaryHistory>,
    currency: String,
    onAddHistory: (monthlySalary: Double, effectiveStartDate: Long, note: String) -> Unit,
    onDeleteHistory: (id: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var monthlySalaryInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))

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
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "📅 سجل تحديثات وفئات الراتب",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "أضف تحديثات للراتب بحيث يتم حساب الأيام السابقة بالراتب القديم والأيام الجديدة بالراتب الجديد دون تغيير الماضي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textSub,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // Input section for new rate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalAppColors.current.bg, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "إضافة راتب/فئة جديدة:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )

                    OutlinedTextField(
                        value = monthlySalaryInput,
                        onValueChange = { monthlySalaryInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("الراتب الشهري (مثلاً 120000 أو 150000)", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    // Date selector button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LocalAppColors.current.border)
                        ) {
                            Text("📅 بداية التطبيق: ${sdf.format(Date(selectedDateMs))}", color = LocalAppColors.current.text)
                        }
                    }

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        placeholder = { Text("ملاحظة (مثلاً: راتب شهر 8)", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalAppColors.current.text),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = LocalAppColors.current.border,
                            focusedTextColor = LocalAppColors.current.text,
                            unfocusedTextColor = LocalAppColors.current.text
                        )
                    )

                    Button(
                        onClick = {
                            val sal = monthlySalaryInput.toDoubleOrNull()
                            if (sal != null && sal > 0) {
                                onAddHistory(sal, selectedDateMs, noteInput)
                                monthlySalaryInput = ""
                                noteInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("➕ حفظ وتطبيق الراتب", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // List of existing histories
                Text(
                    text = "السجلات المسجلة حالياً (${salaryHistories.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.text,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                val sortedHistories = salaryHistories.sortedByDescending { it.effectiveStartDate }
                if (sortedHistories.isEmpty()) {
                    Text(
                        text = "لا توجد سجلات راتب سابقة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSub
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedHistories.forEach { hist ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, LocalAppColors.current.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (salaryHistories.size > 1) {
                                        IconButton(onClick = { onDeleteHistory(hist.id) }) {
                                            Text("🗑️")
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "الراتب: ${hist.monthlySalary.toInt()} $currency (اليومي: ${hist.dailyRate.toInt()} $currency)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldGreen
                                        )
                                        Text(
                                            text = "ابتداءً من: ${sdf.format(Date(hist.effectiveStartDate))}${if (hist.note.isNotBlank()) " - ${hist.note}" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalAppColors.current.textSub
                                        )
                                    }
                                }
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

    if (showDatePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
        val context = LocalContext.current
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedDateMs = newCal.timeInMillis
                showDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showDatePicker = false }
            show()
        }
    }
}

@Composable
fun LeaveManagementDialog(
    leaves: List<LeaveRecord>,
    currency: String,
    onAddLeave: (startDate: Long, endDate: Long?, reason: String) -> Unit,
    onEndOngoingLeave: (leaveId: Int, endDate: Long) -> Unit,
    onDeleteLeave: (leaveId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))

    var startDateMs by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
    }

    var isOngoing by remember { mutableStateOf(false) }

    var endDateMs by remember {
        mutableStateOf(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis)
    }

    var reasonText by remember { mutableStateOf("سفر") }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var leaveToDeleteId by remember { mutableStateOf<Int?>(null) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Add new leave, 1 = History

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(AmberOrange.copy(alpha = 0.2f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BeachAccess,
                                contentDescription = null,
                                tint = AmberOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "🏖️ إدارة وسجل الإجازات",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.text
                            )
                            Text(
                                text = "تحديد أوقات الإجازة وإيقاف الرواتب التلقائية",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textSub
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = LocalAppColors.current.textSub
                        )
                    }
                }

                HorizontalDivider(color = LocalAppColors.current.border, thickness = 1.dp)

                // Tab Switcher (إضافة إجازة / سجل الإجازات)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalAppColors.current.bg, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { activeTab = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == 0) EmeraldGreen else Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(
                            text = "➕ تسجيل إجازة",
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == 0) Color.White else LocalAppColors.current.textSub,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = { activeTab = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == 1) AmberOrange else Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(
                            text = "📋 السجل (${leaves.size})",
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == 1) Color.White else LocalAppColors.current.textSub,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (activeTab == 0) {
                    // --- TAB 0: ADD NEW LEAVE FORM ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LocalAppColors.current.bg, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "تحديد تاريخ بدء الإجازة ونوعها:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = LocalAppColors.current.text
                        )

                        // Start Date Selector
                        OutlinedCard(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(containerColor = LocalAppColors.current.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("تاريخ بدء الإجازة:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                                }
                                Text(
                                    text = sdf.format(Date(startDateMs)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }

                        // Ongoing Leave Switch (عدها لم تنتهي بعد / مفتوحة)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOngoing) AmberOrange.copy(alpha = 0.12f) else LocalAppColors.current.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isOngoing) AmberOrange else LocalAppColors.current.border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "إجازة مفتوحة (عدها لم تنتهي بعد)",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LocalAppColors.current.text
                                    )
                                    Text(
                                        text = if (isOngoing) "الإجازة مستمرة وسارية حتى عودة العامل واستئناف العمل" else "تحديد موعد انتهاء محدد للإجازة",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalAppColors.current.textSub
                                    )
                                }
                                Switch(
                                    checked = isOngoing,
                                    onCheckedChange = { isOngoing = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = AmberOrange, checkedTrackColor = AmberOrange.copy(alpha = 0.5f))
                                )
                            }
                        }

                        // End Date Selector (if not ongoing)
                        if (!isOngoing) {
                            OutlinedCard(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = LocalAppColors.current.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EventAvailable,
                                            contentDescription = null,
                                            tint = EarnedBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text("تاريخ انتهاء الإجازة:", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textSub)
                                    }
                                    Text(
                                        text = sdf.format(Date(endDateMs)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = EarnedBlue
                                    )
                                }
                            }
                        }

                        // Reason / Note
                        OutlinedTextField(
                            value = reasonText,
                            onValueChange = { reasonText = it },
                            label = { Text("سبب الإجازة أو ملاحظة (سفر، عيد، خاصة)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Accounting explanation note
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LocalAppColors.current.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "💡 معلومات الحساب أثناء الإجازة:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldGreen
                                )
                                Text(
                                    text = "• يتوقف تراكم الأجر اليومي تماماً في أيام الإجازة.\n• تتوقف الصرفيات التلقائية.\n• إذا استلم صرفة يوم سفره أو حولت له سحبية، يمكنك إضافتها وستخصم من رصيده بصورة طبيعية دون احتساب أجر عمل.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalAppColors.current.textSub,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                                )
                            }
                        }

                        // Save Button
                        Button(
                            onClick = {
                                if (!isOngoing && endDateMs < startDateMs) {
                                    Toast.makeText(context, "تاريخ الانتهاء يجب أن يكون بعد تاريخ البدء", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                onAddLeave(
                                    startDateMs,
                                    if (isOngoing) null else endDateMs,
                                    reasonText
                                )
                                activeTab = 1
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "حفظ وتسجيل الإجازة 💾",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    // --- TAB 1: LEAVE HISTORY ---
                    if (leaves.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.bg),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🏖️", style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    text = "لا توجد إجازات مسجلة لهذا العامل",
                                    fontWeight = FontWeight.Bold,
                                    color = LocalAppColors.current.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "اضغط على زر (تسجيل إجازة) بالأعلى لإضافة إجازة جديدة.",
                                    color = LocalAppColors.current.textSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            leaves.forEach { leave ->
                                val isCurrentlyOpen = leave.endDate == null
                                val daysCount = LeaveUtils.calculateLeaveDays(leave)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrentlyOpen) AmberOrange.copy(alpha = 0.12f) else LocalAppColors.current.bg
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, if (isCurrentlyOpen) AmberOrange else LocalAppColors.current.border)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(if (isCurrentlyOpen) EmeraldGreen else Color.Gray, CircleShape)
                                                )
                                                Text(
                                                    text = if (isCurrentlyOpen) "🟢 إجازة جارية حالياً (مفتوحة)" else "⚪ إجازة منتهية",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isCurrentlyOpen) EmeraldGreen else LocalAppColors.current.textSub
                                                )
                                            }

                                            IconButton(
                                                onClick = { leaveToDeleteId = leave.id },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "حذف الإجازة",
                                                    tint = CrimsonRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "من: ${sdf.format(Date(leave.startDate))} ${if (leave.endDate != null) "إلى: " + sdf.format(Date(leave.endDate)) else "(مستمرة حتى الآن)"}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = LocalAppColors.current.text
                                                )
                                                Text(
                                                    text = "المدة: $daysCount يوم ${if (leave.reason.isNotBlank()) "• " + leave.reason else ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = LocalAppColors.current.textSub
                                                )
                                            }
                                        }

                                        if (isCurrentlyOpen) {
                                            Button(
                                                onClick = {
                                                    onEndOngoingLeave(leave.id, System.currentTimeMillis())
                                                    Toast.makeText(context, "تم إنهاء الإجازة واعتماد تاريخ اليوم كآخر يوم", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "إنهاء الإجازة والعودة للعمل اليوم ◀️",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Close Button
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

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startDateMs }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startDateMs = newCal.timeInMillis
                showStartDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showStartDatePicker = false }
            show()
        }
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = endDateMs }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                endDateMs = newCal.timeInMillis
                showEndDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showEndDatePicker = false }
            show()
        }
    }

    // Delete Confirmation Dialog
    if (leaveToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { leaveToDeleteId = null },
            title = { Text("تأكيد حذف الإجازة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف فترة الإجازة هذه؟ سيتم استئناف احتساب الأيام التابعة لها كأيام عمل.") },
            confirmButton = {
                Button(
                    onClick = {
                        leaveToDeleteId?.let { onDeleteLeave(it) }
                        leaveToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("حذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { leaveToDeleteId = null }) {
                    Text("إلغاء", color = LocalAppColors.current.textSub)
                }
            }
        )
    }
}

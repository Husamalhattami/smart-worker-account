package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import org.json.JSONArray
import org.json.JSONObject

data class SalaryMetrics(
    val daysInMonth: Int = 30,
    val dailyRate: Double = 100.0,
    val elapsedDays: Int = 0,
    val absenceCount: Double = 0.0,
    val workedDays: Double = 0.0,
    val totalSpendings: Double = 0.0,
    val totalDraws: Double = 0.0,
    val earnedSalary: Double = 0.0,
    val netBalance: Double = 0.0,
    val openingBalance: Double = 0.0
)

data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val versionName: String = "",
    val changelog: String = "",
    val updateUrl: String = "",
    val isMandatory: Boolean = false
)

class SalaryViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("salary_app_prefs", android.content.Context.MODE_PRIVATE)

    val activeUser = MutableStateFlow<String?>(null)
    val registeredUsers = MutableStateFlow<List<String>>(emptyList())

    private fun getRepoForUser(userName: String?): SalaryRepository {
        val userDb = AppDatabase.getDatabaseForUser(getApplication(), userName)
        return SalaryRepository(userDb.salaryDao(), userDb.transactionDao(), userDb.currencyDao(), userDb.salaryHistoryDao())
    }

    private val currentRepoFlow = MutableStateFlow<SalaryRepository>(getRepoForUser(sharedPrefs.getString("active_user", null)))
    private val repository: SalaryRepository get() = currentRepoFlow.value
    private val ttsHelper: ArabicTtsHelper = ArabicTtsHelper(application)
    private val recorderHelper: VoiceRecorderHelper = VoiceRecorderHelper(application)
    private var isCheckingAutoSpendings = false

    val settings: StateFlow<SalarySettings?> = currentRepoFlow
        .flatMapLatest { it.salarySettings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val transactions: StateFlow<List<Transaction>> = currentRepoFlow
        .flatMapLatest { it.allTransactions }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currenciesState: StateFlow<List<CurrencyItem>> = currentRepoFlow
        .flatMapLatest { it.allCurrencies }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val salaryHistoriesState: StateFlow<List<SalaryHistory>> = currentRepoFlow
        .flatMapLatest { it.salaryHistories }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val metrics: StateFlow<SalaryMetrics>

    // Voice & UI State Flow
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _isPlayingAudioId = MutableStateFlow<Int?>(null)
    val isPlayingAudioId: StateFlow<Int?> = _isPlayingAudioId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userNotice = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val userNotice: SharedFlow<String> = _userNotice.asSharedFlow()

    private val _updateInfo = MutableStateFlow(UpdateInfo())
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo.asStateFlow()

    private val _speechInputText = MutableStateFlow("")
    val speechInputText: StateFlow<String> = _speechInputText.asStateFlow()

    private var speechRecognizerHelper: SpeechRecognizerHelper? = null
    private var temporaryAudioPath: String? = null

    init {
        val usersSet = sharedPrefs.getStringSet("registered_users", emptySet())?.toMutableSet() ?: mutableSetOf()
        val initialActive = sharedPrefs.getString("active_user", null)
        if (!initialActive.isNullOrBlank()) {
            usersSet.add(initialActive.trim())
        }
        activeUser.value = initialActive
        registeredUsers.value = usersSet.toList().sorted()

        // Ensure default settings and currencies exist in the database if empty
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.seedDefaultCurrenciesIfEmpty()
                val current = repository.getSalarySettingsDirect()
                if (current == null) {
                    repository.insertOrUpdateSalarySettings(SalarySettings(id = 1, employeeName = initialActive ?: ""))
                    Log.d("SalaryViewModel", "Initialized default empty salary settings row in database.")
                } else if (current.employeeName.isNotBlank()) {
                    val existingName = current.employeeName.trim()
                    if (!usersSet.contains(existingName)) {
                        usersSet.add(existingName)
                        sharedPrefs.edit().putStringSet("registered_users", usersSet).apply()
                        registeredUsers.value = usersSet.toList().sorted()
                    }
                    if (initialActive.isNull_or_blank_check()) {
                        loginUser(existingName)
                    }
                }
                repairSettlementBalancesIfNeeded(repository)
            } catch (e: Exception) {
                Log.e("SalaryViewModel", "Error checking/initializing default settings", e)
            }
        }

        // Combine settings, transactions, and salaryHistories to compute metrics in real-time
        metrics = combine(settings, transactions, salaryHistoriesState) { settingsVal, txList, historiesList ->
            val actualSettings = settingsVal ?: SalarySettings()
            calculateSalaryMetrics(actualSettings, txList, historiesList)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SalaryMetrics())

        // Collect changes to settings to initialize user database if needed & schedule reminder
        viewModelScope.launch {
            settings.collect { actual ->
                if (actual != null) {
                    if (actual.dailyReminderEnabled) {
                        ReminderHelper.scheduleDailyReminder(
                            application,
                            actual.dailyReminderHour,
                            actual.dailyReminderMinute
                        )
                    }
                    if (actual.autoAddDailySpending) {
                        autoAddDailySpendingsIfEnabled(actual)
                    }
                }
            }
        }

        // Initialize Speech recognizer safely
        try {
            speechRecognizerHelper = SpeechRecognizerHelper(
                context = application,
                onResult = { result ->
                    _speechInputText.value = result
                    _isRecordingVoice.value = false // Stop pulsing/recording state
                    parseAndAddVoiceTransaction(result)
                },
                onError = { err ->
                    _errorMessage.value = err
                    _isRecordingVoice.value = false // Stop pulsing/recording state
                    speakOutText(err)
                },
                onListeningStateChange = { listening ->
                    _isListening.value = listening
                    if (!listening) {
                        _isRecordingVoice.value = false // Stop pulsing/recording state
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("SalaryViewModel", "Failed to initialize SpeechRecognizerHelper", e)
            speechRecognizerHelper = null
        }
    }

    private fun String?.isNull_or_blank_check(): Boolean = this.isNullOrBlank()

    fun registerNewUserAccount(
        name: String,
        salary: Double,
        startDate: Long,
        currency: String,
        standardSpending: Double,
        dailyReminderEnabled: Boolean,
        dailyReminderHour: Int,
        dailyReminderMinute: Int,
        openingBalance: Double
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return

        val usersSet = sharedPrefs.getStringSet("registered_users", emptySet())?.toMutableSet() ?: mutableSetOf()
        val isBrandNew = !usersSet.contains(trimmedName)
        usersSet.add(trimmedName)
        activeUser.value?.let { curr ->
            if (curr.isNotBlank()) usersSet.add(curr.trim())
        }
        sharedPrefs.edit().putStringSet("registered_users", usersSet).apply()
        sharedPrefs.edit().putString("active_user", trimmedName).apply()
        registeredUsers.value = usersSet.toList().sorted()
        activeUser.value = trimmedName

        AppDatabase.closeDatabaseForUser(trimmedName)

        val newRepo = getRepoForUser(trimmedName)
        currentRepoFlow.value = newRepo

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isBrandNew) {
                    newRepo.deleteAllTransactions()
                }
                newRepo.seedDefaultCurrenciesIfEmpty()
                val current = newRepo.getSalarySettingsDirect() ?: SalarySettings(id = 1)
                val newSettings = current.copy(
                    id = 1,
                    monthlySalary = salary,
                    startWorkDate = startDate,
                    currency = currency,
                    standardSpendingAmount = standardSpending,
                    priorAbsenceCount = 0,
                    priorSpendingsAmount = 0.0,
                    priorDrawsAmount = 0.0,
                    dailyReminderEnabled = dailyReminderEnabled,
                    dailyReminderHour = dailyReminderHour,
                    dailyReminderMinute = dailyReminderMinute,
                    employeeName = trimmedName,
                    voiceEnabled = true,
                    openingBalance = openingBalance
                )
                newRepo.insertOrUpdateSalarySettings(newSettings)
            } catch (e: Exception) {
                Log.e("SalaryViewModel", "Error registering new user account for $trimmedName", e)
            }
        }
    }

    fun loginUser(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        sharedPrefs.edit().putString("active_user", trimmedName).apply()
        
        val usersSet = sharedPrefs.getStringSet("registered_users", emptySet())?.toMutableSet() ?: mutableSetOf()
        usersSet.add(trimmedName)
        activeUser.value?.let { curr ->
            if (curr.isNotBlank()) usersSet.add(curr.trim())
        }
        sharedPrefs.edit().putStringSet("registered_users", usersSet).apply()
        
        registeredUsers.value = usersSet.toList().sorted()
        activeUser.value = trimmedName

        val newRepo = getRepoForUser(trimmedName)
        currentRepoFlow.value = newRepo

        viewModelScope.launch(Dispatchers.IO) {
            try {
                newRepo.seedDefaultCurrenciesIfEmpty()
                val current = newRepo.getSalarySettingsDirect()
                if (current == null) {
                    newRepo.insertOrUpdateSalarySettings(SalarySettings(id = 1, employeeName = trimmedName))
                } else if (current.employeeName.isBlank()) {
                    newRepo.insertOrUpdateSalarySettings(current.copy(employeeName = trimmedName))
                }
            } catch (e: Exception) {
                Log.e("SalaryViewModel", "Error initializing repo for user $trimmedName", e)
            }
        }
    }

    fun logoutUser() {
        sharedPrefs.edit().remove("active_user").apply()
        activeUser.value = null
        val defaultRepo = getRepoForUser(null)
        currentRepoFlow.value = defaultRepo
    }

    fun deleteUser(name: String) {
        val trimmedName = name.trim()
        val usersSet = sharedPrefs.getStringSet("registered_users", emptySet())?.toMutableSet() ?: mutableSetOf()
        usersSet.remove(trimmedName)
        sharedPrefs.edit().putStringSet("registered_users", usersSet).apply()
        
        registeredUsers.value = usersSet.toList().sorted()
        
        AppDatabase.closeDatabaseForUser(trimmedName)
        
        try {
            val dbName = AppDatabase.getDatabaseNameForUser(trimmedName)
            getApplication<Application>().deleteDatabase(dbName)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (activeUser.value == trimmedName) {
            val nextUser = registeredUsers.value.firstOrNull()
            if (nextUser != null) {
                loginUser(nextUser)
            } else {
                logoutUser()
            }
        }
    }

    private fun calculateSalaryMetrics(
        settings: SalarySettings,
        txList: List<Transaction>,
        salaryHistories: List<SalaryHistory> = emptyList()
    ): SalaryMetrics {
        val calendarNow = Calendar.getInstance()
        
        val calStartRaw = Calendar.getInstance().apply { timeInMillis = settings.startWorkDate }
        val isIntradayStart = calStartRaw.get(Calendar.HOUR_OF_DAY) != 0 || 
                              calStartRaw.get(Calendar.MINUTE) != 0 || 
                              calStartRaw.get(Calendar.SECOND) != 0

        val calStartMidnight = Calendar.getInstance().apply {
            timeInMillis = settings.startWorkDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val activePeriodStart = if (isIntradayStart) settings.startWorkDate else calStartMidnight.timeInMillis
        
        val calNowMidnight = Calendar.getInstance().apply {
            timeInMillis = settings.stopWorkDate ?: calendarNow.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val diffInMillis = calNowMidnight.timeInMillis - calStartMidnight.timeInMillis
        val diffInDays = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
        val elapsedDays = if (settings.stopWorkDate != null) maxOf(0, diffInDays + 1) else maxOf(0, diffInDays)

        // Calculate gross earned day-by-day based on active SalaryHistory for each day
        var grossEarned = 0.0
        for (d in 0 until elapsedDays) {
            val dayMs = calStartMidnight.timeInMillis + (d * 86400000L)
            val activeHist = salaryHistories.filter { it.effectiveStartDate <= dayMs }
                .maxByOrNull { it.effectiveStartDate }
            val dayRate = activeHist?.dailyRate ?: (if (settings.monthlySalary > 0) settings.monthlySalary / 30.0 else 5000.0)
            grossEarned += dayRate
        }

        val activeTxList = txList.filter { it.timestamp >= activePeriodStart && it.type != "SETTLEMENT" && it.type != "OPENING_BALANCE" }

        val currentDailyRate = salaryHistories.filter { it.effectiveStartDate <= calendarNow.timeInMillis }
            .maxByOrNull { it.effectiveStartDate }?.dailyRate ?: (if (settings.monthlySalary > 0) settings.monthlySalary / 30.0 else 5000.0)
        val currentHourlyRate = currentDailyRate / 12.0

        val totalAbsenceHours = activeTxList.filter { it.type == "ABSENCE" }.sumOf { 
            if (it.amount <= 0.0) 12.0 else it.amount 
        } + (settings.priorAbsenceCount * 12.0)

        val absenceDeduction = activeTxList.filter { it.type == "ABSENCE" }.sumOf { tx ->
            val txHours = if (tx.amount <= 0.0) 12.0 else tx.amount
            val txHist = salaryHistories.filter { it.effectiveStartDate <= tx.timestamp }
                .maxByOrNull { it.effectiveStartDate }
            val txRate = txHist?.dailyRate ?: currentDailyRate
            txHours * (txRate / 12.0)
        } + (settings.priorAbsenceCount * 12.0 * currentHourlyRate)

        val absenceCount = totalAbsenceHours / 12.0
        val totalSpendings = activeTxList.filter { it.type == "SPENDING" }.sumOf { it.amount } + settings.priorSpendingsAmount
        val totalDraws = activeTxList.filter { it.type == "DRAW" }.sumOf { it.amount } + settings.priorDrawsAmount
        val workedDays = maxOf(0.0, elapsedDays.toDouble() - absenceCount)
        
        val earnedSalary = maxOf(0.0, grossEarned - absenceDeduction)
        
        val netBalance = if (settings.isSpendingDeductedFromSalary) {
            earnedSalary + settings.openingBalance - totalSpendings - totalDraws
        } else {
            earnedSalary + settings.openingBalance - totalDraws
        }

        return SalaryMetrics(
            daysInMonth = 30,
            dailyRate = currentDailyRate,
            elapsedDays = elapsedDays,
            absenceCount = absenceCount,
            workedDays = workedDays,
            totalSpendings = totalSpendings,
            totalDraws = totalDraws,
            earnedSalary = earnedSalary,
            netBalance = netBalance,
            openingBalance = settings.openingBalance
        )
    }

    // Helper to adjust current opening balance when a transaction prior to active period is added, modified, or deleted
    private fun adjustOpeningBalanceForOldTx(
        currentSettings: SalarySettings,
        oldTx: Transaction?,
        newTx: Transaction?
    ): SalarySettings {
        val dailyRate = if (currentSettings.monthlySalary > 0) currentSettings.monthlySalary / 30.0 else 5000.0
        val hourlyRate = dailyRate / 12.0

        val txTime = newTx?.timestamp ?: oldTx?.timestamp ?: return currentSettings
        if (txTime < currentSettings.startWorkDate) {
            val oldEffect = when (oldTx?.type) {
                "SPENDING", "DRAW" -> - (oldTx.amount)
                "ABSENCE" -> {
                    val hrs = if (oldTx.amount <= 0.0) 12.0 else oldTx.amount
                    - (hrs * hourlyRate)
                }
                else -> 0.0
            }

            val newEffect = when (newTx?.type) {
                "SPENDING", "DRAW" -> - (newTx.amount)
                "ABSENCE" -> {
                    val hrs = if (newTx.amount <= 0.0) 12.0 else newTx.amount
                    - (hrs * hourlyRate)
                }
                else -> 0.0
            }

            val delta = newEffect - oldEffect
            if (delta != 0.0) {
                return currentSettings.copy(
                    openingBalance = currentSettings.openingBalance + delta
                )
            }
        }
        return currentSettings
    }

    // Explicitly Settle and Lock current active period: turns current net balance into new opening balance, resets active counters
    fun settleAndLockAccount(cutOffDateMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val currentSettings = repository.getSalarySettingsDirect() ?: SalarySettings()
            val txList = repository.getAllTransactionsDirect()

            // End of cutoff day: 23:59:59.999
            val calCutoffEnd = Calendar.getInstance().apply {
                timeInMillis = cutOffDateMs
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val effectiveCutoffMs = calCutoffEnd.timeInMillis

            // Period start midnight
            val calStartMidnight = Calendar.getInstance().apply {
                timeInMillis = currentSettings.startWorkDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Period cutoff midnight
            val calCutoffMidnight = Calendar.getInstance().apply {
                timeInMillis = cutOffDateMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            var totalEarnedSalary = 0.0
            var absenceDeduction = 0.0

            val salaryHistories = repository.getAllSalaryHistoriesDirect()

            val activeTxList = txList.filter { 
                it.timestamp >= calStartMidnight.timeInMillis && it.timestamp <= effectiveCutoffMs && it.type != "SETTLEMENT" && it.type != "OPENING_BALANCE"
            }

            val calcCal = Calendar.getInstance().apply {
                timeInMillis = calStartMidnight.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val cutoffMidnightMs = calCutoffMidnight.timeInMillis

            // Iterate day by day to calculate gross earned salary and absence deductions accurately using salaryHistories
            while (calcCal.timeInMillis <= cutoffMidnightMs) {
                val dayMs = calcCal.timeInMillis
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

                val dayRate = activeHist?.dailyRate ?: (if (currentSettings.monthlySalary > 0) currentSettings.monthlySalary / 30.0 else 5000.0)
                totalEarnedSalary += dayRate

                val dayAbsTxs = activeTxList.filter { tx ->
                    val txCal = Calendar.getInstance().apply {
                        timeInMillis = tx.timestamp
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    txCal.timeInMillis == dayMs && tx.type == "ABSENCE"
                }
                for (tx in dayAbsTxs) {
                    val absHours = if (tx.amount <= 0.0) 12.0 else tx.amount
                    absenceDeduction += absHours * (dayRate / 12.0)
                }

                calcCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (currentSettings.priorAbsenceCount > 0) {
                val currentDailyRate = salaryHistories.filter { it.effectiveStartDate <= calCutoffEnd.timeInMillis }
                    .maxByOrNull { it.effectiveStartDate }?.dailyRate ?: (if (currentSettings.monthlySalary > 0) currentSettings.monthlySalary / 30.0 else 5000.0)
                absenceDeduction += currentSettings.priorAbsenceCount * 12.0 * (currentDailyRate / 12.0)
            }

            val totalSpendings = activeTxList.filter { it.type == "SPENDING" }.sumOf { it.amount } + currentSettings.priorSpendingsAmount
            val totalDraws = activeTxList.filter { it.type == "DRAW" }.sumOf { it.amount } + currentSettings.priorDrawsAmount

            val netEarnedAfterAbsence = maxOf(0.0, totalEarnedSalary - absenceDeduction)
            val netSettlementBalance = if (currentSettings.isSpendingDeductedFromSalary) {
                currentSettings.openingBalance + netEarnedAfterAbsence - totalSpendings - totalDraws
            } else {
                currentSettings.openingBalance + netEarnedAfterAbsence - totalDraws
            }

            // 1. Record chronological SETTLEMENT transaction into database with effective cutoff timestamp
            val sdfDate = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
            val dateFormatted = sdfDate.format(Date(effectiveCutoffMs))
            val settlementTx = Transaction(
                type = "SETTLEMENT",
                amount = netSettlementBalance,
                timestamp = effectiveCutoffMs,
                voiceText = "🔒 تصفية وإقفال حساب وتدوير الرصيد"
            )
            repository.insertTransaction(settlementTx)

            // 2. Next period starts at start of next day
            val nextPeriodStartMs = calCutoffMidnight.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

            // Update baseline settings for the new period
            val updated = currentSettings.copy(
                openingBalance = netSettlementBalance,
                startWorkDate = nextPeriodStartMs,
                stopWorkDate = null,
                isWorkPaused = false,
                priorAbsenceCount = 0,
                priorSpendingsAmount = 0.0,
                priorDrawsAmount = 0.0
            )
            repository.insertOrUpdateSalarySettings(updated)
            repairSettlementBalancesIfNeeded(repository)
            _userNotice.tryEmit("🔒 تم تصفية وإقفال حساب الفترة حتى $dateFormatted وتدوير الصافي (${netSettlementBalance.toInt()} ريال) بنجاح")
            speakOutText("تمت تصفية وإقفال حساب الفترة وتدوير الصافي المتبقي بنجاح")
        }
    }

    private suspend fun repairSettlementBalancesIfNeeded(repo: SalaryRepository) {
        val currentSettings = repo.getSalarySettingsDirect() ?: return
        val txList = repo.getAllTransactionsDirect()
        val settlementTxs = txList.filter { it.type == "SETTLEMENT" }.sortedBy { it.timestamp }
        if (settlementTxs.isEmpty()) return

        val salaryHistories = repo.getAllSalaryHistoriesDirect()
        val openingTx = txList.firstOrNull { it.type == "OPENING_BALANCE" }
        val initialOpening = openingTx?.amount ?: currentSettings.openingBalance
        val firstTxMs = txList.minOfOrNull { it.timestamp } ?: currentSettings.startWorkDate
        val initialStartMs = minOf(currentSettings.startWorkDate, firstTxMs, openingTx?.timestamp ?: currentSettings.startWorkDate)

        var prevOpening = initialOpening
        var prevStartMs = initialStartMs

        var updatedAny = false
        var finalSettlementNet = initialOpening

        for (sTx in settlementTxs) {
            val sCutoffMs = sTx.timestamp

            val calStartMidnight = Calendar.getInstance().apply {
                timeInMillis = prevStartMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val calCutoffMidnight = Calendar.getInstance().apply {
                timeInMillis = sCutoffMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val activeTxList = txList.filter {
                it.timestamp >= calStartMidnight.timeInMillis &&
                it.timestamp <= sCutoffMs &&
                it.type != "SETTLEMENT" &&
                it.type != "OPENING_BALANCE" &&
                it.id != sTx.id
            }

            var periodEarned = 0.0
            var periodAbsenceDeduction = 0.0

            val calcCal = Calendar.getInstance().apply {
                timeInMillis = calStartMidnight.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            while (calcCal.timeInMillis <= calCutoffMidnight.timeInMillis) {
                val dayMs = calcCal.timeInMillis
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

                val dayRate = activeHist?.dailyRate ?: (if (currentSettings.monthlySalary > 0) currentSettings.monthlySalary / 30.0 else 5000.0)
                periodEarned += dayRate

                val dayAbsTxs = activeTxList.filter { tx ->
                    val txCal = Calendar.getInstance().apply {
                        timeInMillis = tx.timestamp
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    txCal.timeInMillis == dayMs && tx.type == "ABSENCE"
                }
                for (tx in dayAbsTxs) {
                    val absHours = if (tx.amount <= 0.0) 12.0 else tx.amount
                    periodAbsenceDeduction += absHours * (dayRate / 12.0)
                }

                calcCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val periodSpendings = activeTxList.filter { it.type == "SPENDING" }.sumOf { it.amount }
            val periodDraws = activeTxList.filter { it.type == "DRAW" }.sumOf { it.amount }

            val netEarned = maxOf(0.0, periodEarned - periodAbsenceDeduction)
            val calculatedNetBalance = if (currentSettings.isSpendingDeductedFromSalary) {
                prevOpening + netEarned - periodSpendings - periodDraws
            } else {
                prevOpening + netEarned - periodDraws
            }

            if (Math.abs(sTx.amount - calculatedNetBalance) > 0.01) {
                repo.insertTransaction(sTx.copy(amount = calculatedNetBalance))
                updatedAny = true
            }

            finalSettlementNet = calculatedNetBalance
            prevOpening = calculatedNetBalance
            prevStartMs = calCutoffMidnight.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        }

        if (updatedAny || Math.abs(currentSettings.openingBalance - finalSettlementNet) > 0.01) {
            repo.insertOrUpdateSalarySettings(
                currentSettings.copy(openingBalance = finalSettlementNet)
            )
        }
    }

    // Toggle pause/resume work
    fun toggleWorkPause(isPaused: Boolean) {
        viewModelScope.launch {
            val currentSettings = repository.getSalarySettingsDirect() ?: SalarySettings()
            val now = System.currentTimeMillis()
            if (isPaused) {
                val updated = currentSettings.copy(
                    isWorkPaused = true,
                    stopWorkDate = now
                )
                repository.insertOrUpdateSalarySettings(updated)
                _userNotice.tryEmit("⏸️ تم إيقاف احتساب العمل والتراكم اليومي مؤقتاً (وضع الإجازة)")
                speakOutText("تم إيقاف احتساب العمل مؤقتاً")
            } else {
                val pauseStart = currentSettings.stopWorkDate
                val newStartWorkDate = if (pauseStart != null && now > pauseStart) {
                    val pausedDuration = now - pauseStart
                    currentSettings.startWorkDate + pausedDuration
                } else {
                    currentSettings.startWorkDate
                }
                val updated = currentSettings.copy(
                    isWorkPaused = false,
                    stopWorkDate = null,
                    startWorkDate = newStartWorkDate
                )
                repository.insertOrUpdateSalarySettings(updated)
                _userNotice.tryEmit("▶️ تم استئناف احتساب العمل والأجر اليومي بنجاح")
                speakOutText("تم استئناف احتساب العمل بنجاح")
            }
        }
    }

    // Update Salary settings (with custom name, currency, standard spending, historical adjustments, and daily reminder settings)
    fun updateSalarySettings(
        salary: Double,
        startDate: Long,
        currency: String,
        standardSpending: Double,
        priorAbsences: Int,
        priorSpendings: Double,
        priorDraws: Double,
        dailyReminderEnabled: Boolean,
        dailyReminderHour: Int,
        dailyReminderMinute: Int,
        employeeName: String,
        voiceEnabled: Boolean = true,
        openingBalance: Double = 0.0,
        securityEnabled: Boolean = false,
        securityPin: String = "",
        biometricEnabled: Boolean = false,
        autoAddDailySpending: Boolean = false,
        isSpendingDeductedFromSalary: Boolean = true,
        isWorkPaused: Boolean = false,
        themeMode: String = "DARK",
        appLanguage: String = "ar"
    ) {
        viewModelScope.launch {
            val current = repository.getSalarySettingsDirect() ?: SalarySettings()
            
            // Check if monthly salary rate changed or user explicitly reset start date
            var finalOpeningBalance = openingBalance
            var finalStartDate = startDate
            var finalPriorAbsences = priorAbsences
            var finalPriorSpendings = priorSpendings
            var finalPriorDraws = priorDraws
            
            if (current.monthlySalary > 0.0 && salary != current.monthlySalary && startDate == current.startWorkDate) {
                val txList = repository.getAllTransactionsDirect()
                val oldMetrics = calculateSalaryMetrics(current, txList)
                // Lock in net balance under old rate into opening balance & create settlement transaction
                finalOpeningBalance = oldMetrics.netBalance
                finalStartDate = System.currentTimeMillis()
                
                val settlementTx = Transaction(
                    type = "SETTLEMENT",
                    amount = oldMetrics.netBalance,
                    timestamp = finalStartDate,
                    voiceText = "🔒 تصفية وإقفال حساب وتعديل الراتب إلى ${salary.toInt()} (الراتب السابق: ${current.monthlySalary.toInt()})"
                )
                repository.insertTransaction(settlementTx)
                
                finalPriorAbsences = 0
                finalPriorSpendings = 0.0
                finalPriorDraws = 0.0
            }

            val updated = current.copy(
                monthlySalary = salary,
                startWorkDate = finalStartDate,
                currency = currency,
                standardSpendingAmount = standardSpending,
                priorAbsenceCount = finalPriorAbsences,
                priorSpendingsAmount = finalPriorSpendings,
                priorDrawsAmount = finalPriorDraws,
                dailyReminderEnabled = dailyReminderEnabled,
                dailyReminderHour = dailyReminderHour,
                dailyReminderMinute = dailyReminderMinute,
                employeeName = employeeName,
                voiceEnabled = voiceEnabled,
                openingBalance = finalOpeningBalance,
                securityEnabled = securityEnabled,
                securityPin = securityPin,
                biometricEnabled = biometricEnabled,
                autoAddDailySpending = autoAddDailySpending,
                isSpendingDeductedFromSalary = isSpendingDeductedFromSalary,
                isWorkPaused = isWorkPaused,
                themeMode = themeMode,
                appLanguage = appLanguage
            )
            repository.insertOrUpdateSalarySettings(updated)

            val calMidnight = Calendar.getInstance().apply {
                timeInMillis = finalStartDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            repository.insertSalaryHistory(
                SalaryHistory(
                    monthlySalary = salary,
                    dailyRate = salary / 30.0,
                    effectiveStartDate = calMidnight.timeInMillis,
                    note = "تحديث إعدادات الراتب"
                )
            )

            // Ensure initial OPENING_BALANCE transaction is recorded if non-zero and no prior settlement/opening exists
            val existingTx = repository.getAllTransactionsDirect()
            val hasOpeningOrSettlement = existingTx.any { it.type == "OPENING_BALANCE" || it.type == "SETTLEMENT" }
            if (!hasOpeningOrSettlement && finalOpeningBalance > 0.0) {
                repository.insertTransaction(
                    Transaction(
                        type = "OPENING_BALANCE",
                        amount = finalOpeningBalance,
                        timestamp = finalStartDate,
                        voiceText = "رصيد افتتاحي سابق / أولي"
                    )
                )
            }

            if (employeeName.isNotBlank()) {
                loginUser(employeeName)
            }
            
            // Re-schedule or cancel alarm based on settings change
            if (dailyReminderEnabled) {
                ReminderHelper.scheduleDailyReminder(
                    getApplication(),
                    dailyReminderHour,
                    dailyReminderMinute
                )
            } else {
                ReminderHelper.cancelDailyReminder(getApplication())
            }
            
            speakOutText("تم تحديث إعدادات الراتب والتنبيه الصوتي اليومي بنجاح")
        }
    }

    // Add Direct Transaction
    fun addTransaction(type: String, amount: Double, audioPath: String? = null, text: String? = null, customTimestamp: Long? = null) {
        viewModelScope.launch {
            val tx = Transaction(
                type = type,
                amount = amount,
                timestamp = customTimestamp ?: System.currentTimeMillis(),
                voiceNotePath = audioPath,
                voiceText = text
            )
            repository.insertTransaction(tx)
            
            val currentSettings = repository.getSalarySettingsDirect()
            if (currentSettings != null) {
                val adjusted = adjustOpeningBalanceForOldTx(currentSettings, null, tx)
                if (adjusted != currentSettings) {
                    repository.insertOrUpdateSalarySettings(adjusted)
                }
            }
            
            val cur = settings.value?.currency ?: "ريال يمني"
            // Speak confirmation in clean Arabic words
            val amountInWords = ArabicNumbersToWords.convert(amount.toInt())
            val announceText = when (type) {
                "SPENDING" -> "تم تسجيل صرفة بقيمة $amountInWords $cur"
                "DRAW" -> "تم تسجيل سحبية بقيمة $amountInWords $cur"
                "ABSENCE" -> {
                    if (amount >= 12.0 || amount <= 0.0) {
                        "تم تسجيل غياب يوم كامل"
                    } else {
                        val hrsDesc = AbsenceUtils.formatAbsenceHoursDescription(amount)
                        "تم تسجيل غياب لمدة $hrsDesc"
                    }
                }
                else -> "تم تسجيل عملية جديدة"
            }
            _userNotice.tryEmit("✅ $announceText")
            speakOutText(announceText)
        }
    }

    // Parse voice commands using Gemini or fallback
    private fun parseAndAddVoiceTransaction(inputText: String) {
        viewModelScope.launch {
            // First show parsing alert
            _errorMessage.value = "جاري فهم كلامك..."
            
            // Attempt Gemini parsing
            var parsedResult = withContext(Dispatchers.IO) {
                GeminiClient.parseArabicVoiceText(inputText)
            }
            
            // Fallback to local regex parser if Gemini fails or is offline
            if (parsedResult == null) {
                parsedResult = LocalArabicParser.parse(inputText)
            }

            _errorMessage.value = null

            if (parsedResult != null) {
                // If it's a valid parsing
                val audioPathToSave = temporaryAudioPath
                temporaryAudioPath = null // consume it
                
                addTransaction(
                    type = parsedResult.type,
                    amount = parsedResult.amount,
                    audioPath = audioPathToSave,
                    text = parsedResult.note?.ifBlank { null } ?: inputText
                )
            } else {
                _errorMessage.value = "عذراً، لم أستطع فهم المبلغ أو العملية"
                speakOutText("عذراً، لم أفهم العملية. يرجى تكرار التسجيل بصوت واضح")
            }
        }
    }

    // Voice Notes Player / Recorder
    fun startVoiceRecordingForCommand() {
        viewModelScope.launch {
            recorderHelper.stopPlayback()
            temporaryAudioPath = null
            _isRecordingVoice.value = true
            speakOutText("تكلم الآن، أنا أسمعك")
            // Start Speech recognizer safely without resource conflict
            speechRecognizerHelper?.startListening()
        }
    }

    fun stopVoiceRecordingForCommand() {
        _isRecordingVoice.value = false
        speechRecognizerHelper?.stopListening()
    }

    fun playTransactionVoiceNote(transaction: Transaction) {
        val path = transaction.voiceNotePath ?: return
        _isPlayingAudioId.value = transaction.id
        recorderHelper.playAudio(path) {
            _isPlayingAudioId.value = null
        }
    }

    fun stopAudioPlayback() {
        recorderHelper.stopPlayback()
        _isPlayingAudioId.value = null
    }

    // Announce current balance and status aloud using Arabic words
    fun announceFinancialStatus() {
        val m = metrics.value
        val s = settings.value ?: SalarySettings()
        val cur = s.currency
        
        val earnedStr = ArabicNumbersToWords.convert(m.earnedSalary.toInt())
        val spendingsStr = ArabicNumbersToWords.convert(m.totalSpendings.toInt())
        val drawsStr = ArabicNumbersToWords.convert(m.totalDraws.toInt())
        val balanceStr = ArabicNumbersToWords.convert(m.netBalance.toInt())
        
        val openingStr = if (s.openingBalance != 0.0) {
            val opWords = ArabicNumbersToWords.convert(s.openingBalance.toInt())
            "برصيد افتتاحي قدره $opWords $cur. "
        } else {
            ""
        }
        
        val absenceCountText = if (m.absenceCount % 1.0 == 0.0) {
            ArabicNumbersToWords.convert(m.absenceCount.toInt()) + " أيام"
        } else {
            String.format(Locale.US, "%.1f", m.absenceCount) + " أيام"
        }
        
        val text = "لديك في حسابك المتبقي $balanceStr $cur. ${openingStr}وراتبك المستحق حتى اليوم هو $earnedStr $cur. وإجمالي مصروفاتك هو $spendingsStr $cur. وإجمالي سحبياتك وسلفياتك هي $drawsStr $cur. وغبت $absenceCountText."
        speakOutText(text)
    }

    fun speakOutText(text: String) {
        if (settings.value?.voiceEnabled != false) {
            ttsHelper.speak(text)
        }
    }

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
            
            val currentSettings = repository.getSalarySettingsDirect()
            if (currentSettings != null) {
                val adjusted = adjustOpeningBalanceForOldTx(currentSettings, tx, null)
                if (adjusted != currentSettings) {
                    repository.insertOrUpdateSalarySettings(adjusted)
                }
            }

            // Delete voice file if exists
            tx.voiceNotePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            _userNotice.tryEmit("🗑️ تم مسح العملية بنجاح")
            speakOutText("تم مسح العملية")
        }
    }

    fun updateTransaction(tx: Transaction) {
        viewModelScope.launch {
            val oldTxList = repository.getAllTransactionsDirect()
            val oldTx = oldTxList.find { it.id == tx.id }
            repository.insertTransaction(tx)

            val currentSettings = repository.getSalarySettingsDirect()
            if (currentSettings != null && oldTx != null) {
                val adjusted = adjustOpeningBalanceForOldTx(currentSettings, oldTx, tx)
                if (adjusted != currentSettings) {
                    repository.insertOrUpdateSalarySettings(adjusted)
                }
            }

            _userNotice.tryEmit("✏️ تم تحديث العملية بنجاح")
            speakOutText("تم تحديث العملية بنجاح")
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.deleteAllTransactions()
            repository.insertOrUpdateSalarySettings(SalarySettings())
            speakOutText("تم تصفير جميع البيانات والبدء من جديد")
        }
    }

    fun getReportText(): String {
        val m = metrics.value
        val s = settings.value ?: SalarySettings()
        val cur = s.currency
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val startDateStr = sdf.format(Date(s.startWorkDate))
        
        val actualWorkedDays = m.workedDays
        val avgSpendingPerDay = if (actualWorkedDays > 0) m.totalSpendings / actualWorkedDays else 0.0
        val avgDrawsPerDay = if (actualWorkedDays > 0) m.totalDraws / actualWorkedDays else 0.0
        val dailySalaryRate = m.dailyRate
        val employeeName = if (s.employeeName.isBlank()) "حسام الحطامي" else s.employeeName
        
        val openingBalanceLine = if (s.openingBalance != 0.0) "\n            💰 رصيد افتتاحي (حساب سابق): ${s.openingBalance.toInt()} $cur" else ""
        
        return """
            📊 *تقرير الحساب المالي للموظف: $employeeName* 📊
            
            📅 تاريخ بدء العمل: $startDateStr
            💵 الراتب الشهري الأساسي: ${s.monthlySalary.toInt()} $cur
            💰 الراتب المستحق حتى اليوم: ${m.earnedSalary.toInt()} $cur$openingBalanceLine
            
            📅 إجمالي أيام المنقضية: ${m.elapsedDays} أيام
            ❌ غيابات غير مدفوعة: ${m.absenceCount} أيام
            🛠️ أيام الشغل الفعلية: ${actualWorkedDays} أيام
            
            ------------------------
            💸 *التفاصيل لأيام الشغل كامل:*
            🍔 إجمالي المصروفات اليومية: ${m.totalSpendings.toInt()} $cur
            💸 إجمالي السحبيات (سلفيات): ${m.totalDraws.toInt()} $cur
            
            ------------------------
            📅 *التفاصيل لليوم الواحد:*
            💵 مستحق الراتب لليوم الواحد: ${dailySalaryRate.toInt()} $cur
            🍔 متوسط الصرفيات لليوم الواحد: ${avgSpendingPerDay.toInt()} $cur
            💸 متوسط السحبيات لليوم الواحد: ${avgDrawsPerDay.toInt()} $cur
            
            ========================
            💵 *المبلغ المتبقي الصافي له: ${m.netBalance.toInt()} $cur*
            ========================
            
            تم التصدير بواسطة تطبيق حساب العامل الذكي 📱
        """.trimIndent()
    }

    private val autoAddMutex = Mutex()

    private fun autoAddDailySpendingsIfEnabled(settings: SalarySettings) {
        viewModelScope.launch(Dispatchers.IO) {
            autoAddMutex.withLock {
                try {
                    val txList = repository.getAllTransactionsDirect()
                    
                    val calStart = Calendar.getInstance().apply {
                        timeInMillis = settings.startWorkDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    val calToday = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    val existingSpendingDays = txList.filter { it.type == "SPENDING" }.map { tx ->
                        Calendar.getInstance().apply {
                            timeInMillis = tx.timestamp
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }.toSet()

                    val listToInsert = mutableListOf<Transaction>()
                    val daysLimit = 100 // Prevent adding infinite records
                    var daysAdded = 0
                    
                    while (!calStart.after(calToday) && daysAdded < daysLimit) {
                        val dayMidnight = calStart.timeInMillis
                        if (!existingSpendingDays.contains(dayMidnight)) {
                            listToInsert.add(
                                Transaction(
                                    type = "SPENDING",
                                    amount = settings.standardSpendingAmount,
                                    timestamp = dayMidnight + 12 * 60 * 60 * 1000, // noon
                                    voiceText = "صرفة يومية تلقائية 🤖"
                                )
                            )
                        }
                        calStart.add(Calendar.DAY_OF_YEAR, 1)
                        daysAdded++
                    }

                    if (listToInsert.isNotEmpty()) {
                        for (tx in listToInsert) {
                            repository.insertTransaction(tx)
                        }
                        if (settings.voiceEnabled) {
                            speakOutText("تمت إضافة الصرفيات التلقائية المتأخرة")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SalaryViewModel", "Error auto-adding spendings", e)
                }
            }
        }
    }

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isManual) {
                _userNotice.tryEmit("🔍 جاري فحص التحديثات المباشرة...")
            }

            val urlsToTry = listOf(
                "https://raw.githubusercontent.com/Husamalhattami/smart-worker-account/main/update.json",
                "https://raw.githubusercontent.com/Husamalhattami/smart-worker-account/master/update.json",
                "https://raw.githubusercontent.com/Husamalhattami/smart-worker-account/main/version.json",
                "https://raw.githubusercontent.com/Husamalhattami/smart-worker-account/master/version.json"
            )
            var success = false
            var lastResponseCode = -1

            for (urlString in urlsToTry) {
                try {
                    val url = java.net.URL(urlString)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 6000
                    connection.readTimeout = 6000
                    connection.useCaches = false
                    connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    connection.setRequestProperty("Pragma", "no-cache")
                    connection.setRequestProperty("User-Agent", "AndroidSalaryApp/1.3.0")

                    lastResponseCode = connection.responseCode
                    if (lastResponseCode == 200) {
                        val text = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(text)
                        
                        val versionCode = if (json.has("versionCode")) {
                            json.optInt("versionCode", json.optString("versionCode", "130").toIntOrNull() ?: 130)
                        } else {
                            json.optInt("version_code", 130)
                        }
                        
                        val versionName = json.optString("versionName", json.optString("version_name", "1.3.0"))
                        val changelog = json.optString("changelog", json.optString("notes", ""))
                        val updateUrl = json.optString("updateUrl", json.optString("download_url", "https://github.com/husamalhattami100"))
                        val isMandatory = json.optBoolean("isMandatory", json.optBoolean("is_mandatory", json.optBoolean("forceUpdate", json.optBoolean("force_update", false))))

                        val hasNewer = isRemoteVersionNewer(versionCode, versionName)

                        if (hasNewer) {
                            _updateInfo.value = UpdateInfo(
                                hasUpdate = true,
                                versionName = versionName,
                                changelog = changelog,
                                updateUrl = updateUrl,
                                isMandatory = isMandatory
                            )
                            if (isManual) {
                                _userNotice.tryEmit("📢 يتوفر تحديث جديد v$versionName!")
                            }
                        } else {
                            _updateInfo.value = UpdateInfo(hasUpdate = false)
                            if (isManual) {
                                _userNotice.tryEmit("✅ أنت تستخدم أحدث إصدار من التطبيق (v1.3.0)")
                            }
                        }
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    Log.e("SalaryViewModel", "Error fetching update from $urlString", e)
                }
            }

            if (!success) {
                _updateInfo.value = UpdateInfo(hasUpdate = false)
                if (isManual) {
                    if (lastResponseCode > 0) {
                        _userNotice.tryEmit("⚠️ تعذر العثور على ملف التحديث في السيرفر (كود $lastResponseCode)")
                    } else {
                        _userNotice.tryEmit("⚠️ تعذر الاتصال بسيرفر التحديثات. يرجى التأكد من توفر الإنترنت")
                    }
                }
            }
        }
    }

    private fun isRemoteVersionNewer(remoteCode: Int, remoteName: String): Boolean {
        val currentCode = 130
        val currentName = "1.3.0"

        // If remote code is clearly an integer higher than 130 (e.g. 131, 140, etc.)
        if (remoteCode > currentCode && remoteCode >= 100) {
            return true
        }

        // Compare semantic version names (e.g. "1.3.0" vs "1.4.0")
        val currentParts = currentName.split(".").mapNotNull { it.trim().toIntOrNull() }
        val remoteParts = remoteName.split(".").mapNotNull { it.trim().toIntOrNull() }

        if (currentParts.isNotEmpty() && remoteParts.isNotEmpty()) {
            val maxLen = maxOf(currentParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val cPart = currentParts.getOrElse(i) { 0 }
                val rPart = remoteParts.getOrElse(i) { 0 }
                if (rPart > cPart) return true
                if (rPart < cPart) return false
            }
            return false
        }

        return false
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val s = repository.getSalarySettingsDirect() ?: SalarySettings()
        val txs = repository.getAllTransactionsDirect()

        val settingsObj = JSONObject().apply {
            put("id", s.id)
            put("monthlySalary", s.monthlySalary)
            put("startWorkDate", s.startWorkDate)
            put("stopWorkDate", s.stopWorkDate ?: 0L)
            put("currency", s.currency)
            put("standardSpendingAmount", s.standardSpendingAmount)
            put("priorAbsenceCount", s.priorAbsenceCount)
            put("priorSpendingsAmount", s.priorSpendingsAmount)
            put("priorDrawsAmount", s.priorDrawsAmount)
            put("dailyReminderEnabled", s.dailyReminderEnabled)
            put("dailyReminderHour", s.dailyReminderHour)
            put("dailyReminderMinute", s.dailyReminderMinute)
            put("employeeName", s.employeeName)
            put("voiceEnabled", s.voiceEnabled)
            put("openingBalance", s.openingBalance)
            put("securityEnabled", s.securityEnabled)
            put("securityPin", s.securityPin)
            put("biometricEnabled", s.biometricEnabled)
            put("autoAddDailySpending", s.autoAddDailySpending)
            put("isSpendingDeductedFromSalary", s.isSpendingDeductedFromSalary)
            put("themeMode", s.themeMode)
            put("appLanguage", s.appLanguage)
        }

        val txArray = JSONArray()
        for (tx in txs) {
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("type", tx.type)
                put("amount", tx.amount)
                put("timestamp", tx.timestamp)
                put("voiceNotePath", tx.voiceNotePath ?: "")
                put("voiceText", tx.voiceText ?: "")
            }
            txArray.put(obj)
        }

        val root = JSONObject().apply {
            put("version", 1)
            put("appName", "SmartWorkerAccount")
            put("exportDate", System.currentTimeMillis())
            put("settings", settingsObj)
            put("transactions", txArray)
        }

        root.toString(2)
    }

    suspend fun restoreBackupJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (root.has("settings")) {
                val sObj = root.getJSONObject("settings")
                val activeName = activeUser.value
                val targetName = if (!activeName.isNullOrBlank()) activeName else sObj.optString("employeeName", "")
                val restoredSettings = SalarySettings(
                    id = 1,
                    monthlySalary = sObj.optDouble("monthlySalary", 150000.0),
                    startWorkDate = sObj.optLong("startWorkDate", sObj.optLong("startDate", System.currentTimeMillis())),
                    stopWorkDate = if (sObj.has("stopWorkDate") && sObj.getLong("stopWorkDate") > 0) sObj.getLong("stopWorkDate") else null,
                    currency = sObj.optString("currency", "ريال يمني"),
                    standardSpendingAmount = sObj.optDouble("standardSpendingAmount", sObj.optDouble("standardSpending", 2000.0)),
                    priorAbsenceCount = sObj.optInt("priorAbsenceCount", sObj.optInt("priorAbsences", 0)),
                    priorSpendingsAmount = sObj.optDouble("priorSpendingsAmount", sObj.optDouble("priorSpendings", 0.0)),
                    priorDrawsAmount = sObj.optDouble("priorDrawsAmount", sObj.optDouble("priorDraws", 0.0)),
                    dailyReminderEnabled = sObj.optBoolean("dailyReminderEnabled", true),
                    dailyReminderHour = sObj.optInt("dailyReminderHour", 5),
                    dailyReminderMinute = sObj.optInt("dailyReminderMinute", 0),
                    employeeName = targetName,
                    voiceEnabled = sObj.optBoolean("voiceEnabled", true),
                    openingBalance = sObj.optDouble("openingBalance", 0.0),
                    securityEnabled = sObj.optBoolean("securityEnabled", false),
                    securityPin = sObj.optString("securityPin", ""),
                    biometricEnabled = sObj.optBoolean("biometricEnabled", false),
                    autoAddDailySpending = sObj.optBoolean("autoAddDailySpending", false),
                    isSpendingDeductedFromSalary = sObj.optBoolean("isSpendingDeductedFromSalary", true),
                    themeMode = sObj.optString("themeMode", "DARK"),
                    appLanguage = sObj.optString("appLanguage", "ar")
                )
                repository.insertOrUpdateSalarySettings(restoredSettings)
            }

            if (root.has("transactions")) {
                val txArray = root.getJSONArray("transactions")
                repository.deleteAllTransactions()
                for (i in 0 until txArray.length()) {
                    val obj = txArray.getJSONObject(i)
                    val vNote = obj.optString("voiceNotePath", obj.optString("audioNotePath", ""))
                    val vText = obj.optString("voiceText", obj.optString("note", ""))
                    val tx = Transaction(
                        id = obj.optInt("id", 0),
                        type = obj.optString("type", "SPENDING"),
                        amount = obj.optDouble("amount", 0.0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        voiceNotePath = if (vNote.isBlank()) null else vNote,
                        voiceText = if (vText.isBlank()) null else vText
                    )
                    repository.insertTransaction(tx)
                }
            }
            _userNotice.tryEmit("✅ تم استعادة النسخة الاحتياطية بنجاح!")
            true
        } catch (e: Exception) {
            Log.e("SalaryViewModel", "Error restoring backup JSON", e)
            _userNotice.tryEmit("❌ فشلت استعادة النسخة الاحتياطية. صيغة الملف غير صحيحة.")
            false
        }
    }

    fun addCustomCurrency(name: String, symbol: String, flag: String = "💱") {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val trimmedSymbol = symbol.trim().ifEmpty { trimmedName }
            if (trimmedName.isNotEmpty()) {
                val id = "CUSTOM_${System.currentTimeMillis()}"
                repository.insertCurrency(
                    CurrencyItem(
                        id = id,
                        name = trimmedName,
                        symbol = trimmedSymbol,
                        flag = flag.trim().ifEmpty { "💱" },
                        isCustom = true
                    )
                )
                _userNotice.tryEmit("✅ تم إضافة العملة الجديدة: $trimmedName")
            }
        }
    }

    fun deleteCurrency(currencyItem: CurrencyItem) {
        viewModelScope.launch {
            repository.deleteCurrency(currencyItem)
            _userNotice.tryEmit("🗑️ تم حذف العملة")
        }
    }

    fun addSalaryHistory(monthlySalary: Double, effectiveStartDate: Long, note: String = "") {
        viewModelScope.launch {
            val dailyRate = if (monthlySalary > 0) monthlySalary / 30.0 else 0.0
            val calMidnight = Calendar.getInstance().apply {
                timeInMillis = effectiveStartDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val history = SalaryHistory(
                monthlySalary = monthlySalary,
                dailyRate = dailyRate,
                effectiveStartDate = calMidnight.timeInMillis,
                note = note.trim()
            )
            repository.insertSalaryHistory(history)
            
            // Also update current salary settings if this is the newest rate
            val currentSettings = repository.getSalarySettingsDirect() ?: SalarySettings()
            val histories = repository.getAllSalaryHistoriesDirect()
            val latestHistory = histories.maxByOrNull { it.effectiveStartDate }
            if (latestHistory != null && latestHistory.effectiveStartDate >= currentSettings.startWorkDate) {
                repository.insertOrUpdateSalarySettings(
                    currentSettings.copy(
                        monthlySalary = latestHistory.monthlySalary
                    )
                )
            }
            _userNotice.tryEmit("✅ تم حفظ سجل الراتب للفيئة المالية المحدد")
        }
    }

    fun deleteSalaryHistory(id: Int) {
        viewModelScope.launch {
            repository.deleteSalaryHistoryById(id)
            _userNotice.tryEmit("🗑️ تم حذف سجل الراتب")
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        recorderHelper.stopPlayback()
        speechRecognizerHelper?.destroy()
    }
}

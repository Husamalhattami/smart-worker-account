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
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    val updateUrl: String = ""
)

class SalaryViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("salary_app_prefs", android.content.Context.MODE_PRIVATE)

    val activeUser = MutableStateFlow<String?>(null)
    val registeredUsers = MutableStateFlow<List<String>>(emptyList())

    private val db = AppDatabase.getDatabase(application)
    private val repository = SalaryRepository(db.salaryDao(), db.transactionDao())
    private val ttsHelper: ArabicTtsHelper = ArabicTtsHelper(application)
    private val recorderHelper: VoiceRecorderHelper = VoiceRecorderHelper(application)
    private var isCheckingAutoSpendings = false

    val settings: StateFlow<SalarySettings?> = repository.salarySettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
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

    private val _updateInfo = MutableStateFlow(UpdateInfo())
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo.asStateFlow()

    private val _speechInputText = MutableStateFlow("")
    val speechInputText: StateFlow<String> = _speechInputText.asStateFlow()

    private var speechRecognizerHelper: SpeechRecognizerHelper? = null
    private var temporaryAudioPath: String? = null

    init {
        // Ensure default settings exist in the database if empty, so the Flow emits instead of getting stuck
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = repository.getSalarySettingsDirect()
                if (current == null) {
                    repository.insertOrUpdateSalarySettings(SalarySettings(id = 1, employeeName = ""))
                    Log.d("SalaryViewModel", "Initialized default empty salary settings row in database.")
                }
            } catch (e: Exception) {
                Log.e("SalaryViewModel", "Error checking/initializing default settings", e)
            }
        }

        // Combine settings and transactions to compute metrics in real-time
        metrics = combine(settings, transactions) { settingsVal, txList ->
            val actualSettings = settingsVal ?: SalarySettings()
            calculateSalaryMetrics(actualSettings, txList)
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

        // Initialize Speech recognizer
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
    }

    fun loginUser(name: String) {
        val trimmedName = name.trim()
        sharedPrefs.edit().putString("active_user", trimmedName).apply()
        
        val usersSet = sharedPrefs.getStringSet("registered_users", emptySet()) ?: emptySet()
        val newUsersSet = usersSet.toMutableSet().apply { add(trimmedName) }
        sharedPrefs.edit().putStringSet("registered_users", newUsersSet).apply()
        
        registeredUsers.value = newUsersSet.toList().sorted()
        activeUser.value = trimmedName
    }

    fun logoutUser() {
        sharedPrefs.edit().remove("active_user").apply()
        activeUser.value = null
    }

    fun deleteUser(name: String) {
        val trimmedName = name.trim()
        val usersSet = sharedPrefs.getStringSet("registered_users", emptySet()) ?: emptySet()
        val newUsersSet = usersSet.toMutableSet().apply { remove(trimmedName) }
        sharedPrefs.edit().putStringSet("registered_users", newUsersSet).apply()
        
        registeredUsers.value = newUsersSet.toList().sorted()
        
        if (activeUser.value == trimmedName) {
            logoutUser()
        }
        
        try {
            val dbName = "salary_db_${trimmedName.replace("\\s+".toRegex(), "_").lowercase()}"
            getApplication<Application>().deleteDatabase(dbName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateSalaryMetrics(settings: SalarySettings, txList: List<Transaction>): SalaryMetrics {
        val calendarNow = Calendar.getInstance()
        
        // Normalize calendars to midnight to compute precise difference in days
        val calStartMidnight = Calendar.getInstance().apply {
            timeInMillis = settings.startWorkDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val calNowMidnight = Calendar.getInstance().apply {
            timeInMillis = settings.stopWorkDate ?: calendarNow.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val diffInMillis = calNowMidnight.timeInMillis - calStartMidnight.timeInMillis
        val diffInDays = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
        val elapsedDays = if (diffInDays < 0) 0 else (diffInDays + 1)

        // Standard month is 30 days for base rate calculation
        val dailyRate = settings.monthlySalary / 30.0
        val hourlyRate = dailyRate / 12.0 // standard 12-hour workday

        // Calculate total absence hours. If amount == 0.0, we treat it as 12 hours (full day).
        // If amount > 0.0, we treat it as the exact missed hours.
        val totalAbsenceHours = txList.filter { it.type == "ABSENCE" }.sumOf { 
            if (it.amount <= 0.0) 12.0 else it.amount 
        } + (settings.priorAbsenceCount * 12.0)

        // absenceCount in days (Double)
        val absenceCount = totalAbsenceHours / 12.0

        val totalSpendings = txList.filter { it.type == "SPENDING" }.sumOf { it.amount } + settings.priorSpendingsAmount
        val totalDraws = txList.filter { it.type == "DRAW" }.sumOf { it.amount } + settings.priorDrawsAmount

        // workedDays in days (Double)
        val workedDays = maxOf(0.0, elapsedDays.toDouble() - absenceCount)
        
        // Earned salary is base rate * elapsed days, minus absence deduction
        val earnedSalary = maxOf(0.0, (elapsedDays.toDouble() * dailyRate) - (totalAbsenceHours * hourlyRate))
        val netBalance = earnedSalary + settings.openingBalance - totalSpendings - totalDraws

        return SalaryMetrics(
            daysInMonth = 30, // Base is 30
            dailyRate = dailyRate,
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
        themeMode: String = "DARK"
    ) {
        viewModelScope.launch {
            val current = repository.getSalarySettingsDirect() ?: SalarySettings()
            val updated = current.copy(
                monthlySalary = salary,
                startWorkDate = startDate,
                currency = currency,
                standardSpendingAmount = standardSpending,
                priorAbsenceCount = priorAbsences,
                priorSpendingsAmount = priorSpendings,
                priorDrawsAmount = priorDraws,
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
            repository.insertOrUpdateSalarySettings(updated)
            
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
            
            val cur = settings.value?.currency ?: "ريال يمني"
            // Speak confirmation in clean Arabic words
            val amountInWords = ArabicNumbersToWords.convert(amount.toInt())
            val announceText = when (type) {
                "SPENDING" -> "تم تسجيل صرفة بقيمة $amountInWords $cur"
                "DRAW" -> "تم تسجيل سحبية بقيمة $amountInWords $cur"
                "ABSENCE" -> {
                    if (amount == 12.0 || amount <= 0.0) {
                        "تم تسجيل غياب يوم كامل"
                    } else {
                        val hrsInWords = ArabicNumbersToWords.convert(amount.toInt())
                        "تم تسجيل غياب لمدة $hrsInWords ساعات"
                    }
                }
                else -> "تم تسجيل عملية جديدة"
            }
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
                    text = inputText
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
            // Delete voice file if exists
            tx.voiceNotePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            speakOutText("تم مسح العملية")
        }
    }

    fun updateTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(tx)
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
        val employeeName = if (s.employeeName.isBlank()) "محمد خالد العريفي" else s.employeeName
        
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
            
            تم التصدير بواسطة تطبيق رفيق الراتب الذكي 📱
        """.trimIndent()
    }

    private fun autoAddDailySpendingsIfEnabled(settings: SalarySettings) {
        if (isCheckingAutoSpendings) return
        isCheckingAutoSpendings = true
        viewModelScope.launch(Dispatchers.IO) {
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
            } finally {
                isCheckingAutoSpendings = false
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch update file from GitHub raw
                val url = java.net.URL("https://raw.githubusercontent.com/husamalhattami100/salary-companion/main/update.json")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val versionPattern = "\"versionCode\"\\s*:\\s*(\\d+)".toRegex()
                    val namePattern = "\"versionName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val urlPattern = "\"updateUrl\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val changelogPattern = "\"changelog\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    
                    val versionCode = versionPattern.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val versionName = namePattern.find(text)?.groupValues?.get(1) ?: "1.0.0"
                    val updateUrl = urlPattern.find(text)?.groupValues?.get(1) ?: "https://github.com/husamalhattami100"
                    val changelog = changelogPattern.find(text)?.groupValues?.get(1) ?: ""
                    
                    if (versionCode > 1) { // Current version is 1
                        _updateInfo.value = UpdateInfo(
                            hasUpdate = true,
                            versionName = versionName,
                            changelog = changelog,
                            updateUrl = updateUrl
                        )
                    } else {
                        _updateInfo.value = UpdateInfo(hasUpdate = false)
                    }
                } else {
                    _updateInfo.value = UpdateInfo(hasUpdate = false)
                }
            } catch (e: Exception) {
                Log.e("SalaryViewModel", "Error checking for updates", e)
                _updateInfo.value = UpdateInfo(hasUpdate = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        recorderHelper.stopPlayback()
        speechRecognizerHelper?.destroy()
    }
}

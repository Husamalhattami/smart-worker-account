package com.example

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.SalaryApp
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AdMobManager
import com.example.viewmodel.SalaryViewModel

class MainActivity : FragmentActivity() {
    private val viewModel: SalaryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdMobManager.init(this)
        enableEdgeToEdge()
        setContent {
            val settingsState by viewModel.settings.collectAsState()
            val themeMode = settingsState?.themeMode ?: "DARK"
            val systemInDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                "SYSTEM" -> systemInDark
                else -> true
            }
            MyApplicationTheme(darkTheme = isDark) {
                SalaryApp(viewModel)
            }
        }
        
        handleReminderIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("FROM_REMINDER", false)) {
            // Wait slightly for TTS to initialize fully
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val name = viewModel.settings.value?.employeeName ?: ""
                val nameToCall = if (name.isBlank()) "محمد" else (name.trim().split(" ").firstOrNull() ?: "محمد")
                viewModel.speakOutText("أهلاً بك يا $nameToCall. يرجى تسجيل بيانات يومك من شغل أو غياب أو صرفة الآن لتفادي النسيان.")
            }, 1500)
        }
    }
}

package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SalarySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, rescheduling daily reminder.")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val settings = db.salaryDao().getSalarySettingsDirect() ?: SalarySettings()
                    if (settings.dailyReminderEnabled) {
                        ReminderHelper.scheduleDailyReminder(
                            context,
                            settings.dailyReminderHour,
                            settings.dailyReminderMinute
                        )
                        Log.d("BootReceiver", "Rescheduled daily reminder for ${settings.dailyReminderHour}:${settings.dailyReminderMinute}")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule alarm on boot", e)
                }
            }
        }
    }
}

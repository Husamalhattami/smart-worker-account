package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.SalarySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Locale

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val settings = db.salaryDao().getSalarySettingsDirect() ?: SalarySettings()
                
                if (settings.dailyReminderEnabled) {
                    // Show Notification
                    showNotification(context, settings)
                    
                    // Reschedule for tomorrow
                    ReminderHelper.scheduleDailyReminder(
                        context,
                        settings.dailyReminderHour,
                        settings.dailyReminderMinute
                    )
                    
                    // Play voice alarm/speech
                    speakReminder(context, settings)
                }
            } catch (e: Exception) {
                Log.e("ReminderAlarmReceiver", "Error processing alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, settings: SalarySettings) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_salary_reminder_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تنبيهات رفيق الراتب اليومية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة لتذكيرك اليومي بتسجيل بياناتك لتفادي النسيان"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FROM_REMINDER", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val nameToCall = if (settings.employeeName.isBlank()) "محمد" else (settings.employeeName.trim().split(" ").firstOrNull() ?: "محمد")

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("تذكير رفيق الراتب الذكي ⏰")
            .setContentText("يا $nameToCall، يرجى تسجيل بيانات يومك (شغل، غياب، أو صرفة)")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("يا $nameToCall، يرجى تسجيل بيانات يومك (شغل، غياب، أو صرفة) لضمان عدم نسيان التسجيل لحساب راتبك الصافي بدقة."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun speakReminder(context: Context, settings: SalarySettings) {
        val nameToCall = if (settings.employeeName.isBlank()) "محمد" else (settings.employeeName.trim().split(" ").firstOrNull() ?: "محمد")
        CoroutineScope(Dispatchers.Main).launch {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let { textToSpeech ->
                        textToSpeech.language = Locale("ar")
                        val reminderText = "يا $nameToCall، لا تنسى تسجيل بيانات يومك من شغل أو غياب أو صرفة"
                        textToSpeech.speak(reminderText, TextToSpeech.QUEUE_FLUSH, null, "AlarmReminderSpeechId")
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(6000)
                            try {
                                textToSpeech.stop()
                                textToSpeech.shutdown()
                            } catch (e: Exception) {
                                Log.e("ReminderAlarmReceiver", "Error shutting down TTS", e)
                            }
                        }
                    }
                }
            }
        }
    }
}

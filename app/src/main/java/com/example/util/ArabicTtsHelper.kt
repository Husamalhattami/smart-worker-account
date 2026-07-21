package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class ArabicTtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        try {
            // Explicitly try initializing Google TTS engine for standard Arabic support
            tts = TextToSpeech(context.applicationContext, this, "com.google.android.tts")
        } catch (e: Exception) {
            Log.e("ArabicTtsHelper", "Failed to initialize Google TTS engine, using default.", e)
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("ArabicTtsHelper", "Arabic language is not supported or missing data on this device.")
            } else {
                isInitialized = true
                Log.d("ArabicTtsHelper", "Arabic TTS Initialized successfully.")
            }
        } else {
            Log.e("ArabicTtsHelper", "Initialization of TextToSpeech failed.")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ArabicTtsUtteranceId")
        } else {
            Log.w("ArabicTtsHelper", "TTS not initialized yet. Cannot speak: $text")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

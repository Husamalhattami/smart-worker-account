package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChange: (Boolean) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA") // Use Arabic SA
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ar-SA")
    }

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                mainHandler.post { onListeningStateChange(true) }
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {}

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                mainHandler.post { onListeningStateChange(false) }
                            }

                            override fun onError(error: Int) {
                                val message = when (error) {
                                    SpeechRecognizer.ERROR_AUDIO -> "خطأ في الصوت"
                                    SpeechRecognizer.ERROR_CLIENT -> "خطأ من الهاتف"
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحيات غير كافية"
                                    SpeechRecognizer.ERROR_NETWORK -> "خطأ في الشبكة"
                                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة الشبكة"
                                    SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمعك جيداً"
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "النظام مشغول"
                                    SpeechRecognizer.ERROR_SERVER -> "خطأ في الخادم"
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "انتهت مهلة التحدث"
                                    else -> "خطأ غير معروف"
                                }
                                Log.e("SpeechRecognizerHelper", "Error code: $error - $message")
                                mainHandler.post {
                                    onListeningStateChange(false)
                                    onError(message)
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    val resultText = matches[0]
                                    mainHandler.post {
                                        onListeningStateChange(false)
                                        onResult(resultText)
                                    }
                                } else {
                                    mainHandler.post {
                                        onListeningStateChange(false)
                                        onError("لم أسمعك جيداً")
                                    }
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }
                speechRecognizer?.startListening(recognizerIntent)
            } catch (e: Exception) {
                Log.e("SpeechRecognizerHelper", "Failed to start listening", e)
                onError("فشل بدء التعرف على الصوت")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("SpeechRecognizerHelper", "Failed to stop listening", e)
            }
            onListeningStateChange(false)
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechRecognizerHelper", "Failed to destroy speech recognizer", e)
            }
            speechRecognizer = null
        }
    }
}

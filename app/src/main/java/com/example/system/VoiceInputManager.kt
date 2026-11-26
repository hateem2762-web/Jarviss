package com.example.system

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "VoiceInputManager"

data class VoiceInputState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null,
)

class VoiceInputManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var onFinalResultCallback: ((String) -> Unit)? = null
    private var triedLocaleFallback = false

    private val _state = MutableStateFlow(VoiceInputState())
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    fun isAvailable(): Boolean {
        if (SpeechRecognizer.isRecognitionAvailable(context)) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        runOnMain {
            if (!isAvailable()) {
                _state.value = VoiceInputState(
                    errorMessage = "Bu cihazda ses tanıma servisi bulunamadı (Google Ses Hizmetleri gerekli)."
                )
                return@runOnMain
            }

            onFinalResultCallback = onFinalResult
            triedLocaleFallback = false
            startSessionInternal(useTurkishLocale = true)
        }
    }

    private fun startSessionInternal(useTurkishLocale: Boolean) {
        stopInternal()

        val recognizer = try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "createSpeechRecognizer failed", e)
            null
        }

        if (recognizer == null) {
            _state.value = VoiceInputState(
                isListening = false,
                errorMessage = "Ses tanıma motoru başlatılamadı."
            )
            return
        }

        speechRecognizer = recognizer
        _state.value = VoiceInputState(isListening = true)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (speechRecognizer !== recognizer) return
                _state.value = _state.value.copy(isListening = true, errorMessage = null)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (speechRecognizer !== recognizer) return
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _state.value = _state.value.copy(rmsLevel = normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (speechRecognizer !== recognizer) return
                _state.value = _state.value.copy(rmsLevel = 0f)
            }

            override fun onError(error: Int) {
                if (speechRecognizer !== recognizer) return

                // If tr-TR isn't installed/supported on this device, retry once with default device language
                if (
                    useTurkishLocale &&
                    !triedLocaleFallback &&
                    (error == 12 || error == 13 || error == 14)
                ) {
                    triedLocaleFallback = true
                    Log.w(TAG, "tr-TR voice model unavailable ($error); retrying with device locale")
                    startSessionInternal(useTurkishLocale = false)
                    return
                }

                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Ses anlaşılamadı, tekrar dene."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Konuşma algılanmadı."
                    SpeechRecognizer.ERROR_AUDIO -> "Mikrofon kullanılamıyor veya başka bir uygulama kullanıyor."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ses tanıma için internet bağlantısı gerekli."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekli."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ses tanıma servisi meşgul, bir saniye sonra tekrar dene."
                    SpeechRecognizer.ERROR_CLIENT -> "Ses tanıma bağlantısı sıfırlandı, tekrar dene."
                    SpeechRecognizer.ERROR_SERVER -> "Ses tanıma sunucusuna ulaşılamadı."
                    11 -> "Ses tanıma servisi bağlantısı kesildi, tekrar dene."
                    12, 13 -> "Türkçe ses paketi cihazda yüklü değil."
                    14, 15 -> "Cihaz ses tanıma servisi şu an yanıt vermiyor."
                    else -> "Sesli giriş tamamlanamadı (Kod: $error)."
                }
                Log.w(TAG, "SpeechRecognizer error: $error")
                _state.value = VoiceInputState(isListening = false, errorMessage = msg)
            }

            override fun onResults(results: Bundle?) {
                if (speechRecognizer !== recognizer) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim().orEmpty()
                _state.value = VoiceInputState(isListening = false)
                if (transcript.isNotEmpty()) {
                    onFinalResultCallback?.invoke(transcript)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (speechRecognizer !== recognizer) return
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    _state.value = _state.value.copy(partialText = partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            val langTag = if (useTurkishLocale) "tr-TR" else Locale.getDefault().toLanguageTag()
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _state.value = VoiceInputState(
                isListening = false,
                errorMessage = "Ses tanıma başlatılamadı: ${e.localizedMessage ?: "hata"}"
            )
        }
    }

    fun stopListening() {
        runOnMain {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            _state.value = _state.value.copy(isListening = false, rmsLevel = 0f)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun stopInternal() {
        val old = speechRecognizer
        speechRecognizer = null
        if (old != null) {
            try {
                old.cancel()
            } catch (_: Exception) {
            }
            try {
                old.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun destroy() {
        runOnMain {
            stopInternal()
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}

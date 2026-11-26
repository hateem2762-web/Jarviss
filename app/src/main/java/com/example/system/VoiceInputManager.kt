package com.example.system

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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

    private var speechRecognizer: SpeechRecognizer? = null
    private var onFinalResultCallback: ((String) -> Unit)? = null

    private val _state = MutableStateFlow(VoiceInputState())
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(onFinalResult: (String) -> Unit) {
        if (!isAvailable()) {
            _state.value = VoiceInputState(
                errorMessage = "Bu cihazda ses tanıma servisi kullanılamıyor."
            )
            return
        }

        onFinalResultCallback = onFinalResult
        stopInternal()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceInputState(isListening = true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _state.value = _state.value.copy(rmsLevel = normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(rmsLevel = 0f)
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Ses anlaşılamadı, tekrar dene."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Konuşma algılanmadı."
                    SpeechRecognizer.ERROR_AUDIO -> "Mikrofon hatası oluştu."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ses tanıma için ağ bağlantısı gerekli."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekli."
                    else -> "Sesli giriş tamamlanamadı ($error)."
                }
                Log.w(TAG, "SpeechRecognizer error: $error")
                _state.value = VoiceInputState(isListening = false, errorMessage = msg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim().orEmpty()
                _state.value = VoiceInputState(isListening = false)
                if (transcript.isNotEmpty()) {
                    onFinalResultCallback?.invoke(transcript)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _state.value = VoiceInputState(
                isListening = false,
                errorMessage = "Ses tanıma başlatılamadı."
            )
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = _state.value.copy(isListening = false, rmsLevel = 0f)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun stopInternal() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    fun destroy() {
        stopInternal()
    }
}

package com.example.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "WakeWordManager"

class WakeWordManager(
    private val context: Context,
    private val onWakeWordDetected: (trailingCommand: String?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var pausedForActiveSession = false
    private var restartAttempt = 0
    private var pendingRestartRunnable: Runnable? = null

    fun isRunning(): Boolean = active && !pausedForActiveSession

    fun start() {
        runOnMain {
            if (!hasMicPermission() || !isRecognizerSupported()) {
                active = false
                return@runOnMain
            }
            if (active && !pausedForActiveSession && recognizer != null) {
                return@runOnMain
            }
            active = true
            pausedForActiveSession = false
            restartAttempt = 0
            scheduleRestart(delayMs = 150L)
        }
    }

    fun pauseForActiveVoiceSession() {
        runOnMain {
            pausedForActiveSession = true
            cancelPendingRestart()
            destroyRecognizer()
        }
    }

    fun resumeAfterVoiceSession(delayMs: Long = 500L) {
        runOnMain {
            if (!active || !hasMicPermission()) return@runOnMain
            pausedForActiveSession = false
            restartAttempt = 0
            scheduleRestart(delayMs = delayMs)
        }
    }

    fun stop() {
        runOnMain {
            active = false
            pausedForActiveSession = false
            cancelPendingRestart()
            destroyRecognizer()
        }
    }

    private fun startRecognitionPass() {
        if (!active || pausedForActiveSession || !hasMicPermission()) return

        destroyRecognizer()

        val instance = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to create wake word recognizer", e)
            null
        }

        if (instance == null) {
            scheduleRestart(delayMs = 3_000L)
            return
        }

        recognizer = instance
        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (recognizer !== instance) return
                restartAttempt = 0
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (recognizer !== instance) return
                if (!active || pausedForActiveSession) return

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    stop()
                    return
                }

                val delay = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 350L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1_200L
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> 4_000L
                    else -> {
                        restartAttempt = (restartAttempt + 1).coerceAtMost(4)
                        600L * restartAttempt
                    }
                }
                scheduleRestart(delay)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (recognizer !== instance) return
                if (!active || pausedForActiveSession) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val first = matches?.firstOrNull().orEmpty()
                if (containsWakeWord(first)) {
                    // Wait briefly only if user is still speaking a follow-up phrase;
                    // if no immediate continuation, wake immediately on final or partial.
                    if (extractTrailingCommand(first) != null) {
                        triggerWake(first)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                if (recognizer !== instance) return
                if (!active || pausedForActiveSession) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val hit = matches.firstOrNull { containsWakeWord(it) }
                if (hit != null) {
                    triggerWake(hit)
                } else {
                    scheduleRestart(delayMs = 300L)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            instance.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Wake word startListening failed", e)
            scheduleRestart(delayMs = 2_000L)
        }
    }

    private fun triggerWake(fullTranscript: String) {
        val trailing = extractTrailingCommand(fullTranscript)
        pauseForActiveVoiceSession()
        onWakeWordDetected(trailing)
    }

    private fun scheduleRestart(delayMs: Long) {
        cancelPendingRestart()
        if (!active || pausedForActiveSession) return
        val runnable = Runnable { startRecognitionPass() }
        pendingRestartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingRestart() {
        pendingRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRestartRunnable = null
    }

    private fun destroyRecognizer() {
        val old = recognizer
        recognizer = null
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

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isRecognizerSupported(): Boolean {
        if (SpeechRecognizer.isRecognitionAvailable(context)) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    companion object {
        private val wakeVariants = listOf(
            "jarvis",
            "carvis",
            "yarvis",
            "cervis",
            "jarviz",
            "carviz",
            "harvis",
            "calvis",
            "servis",
        )

        fun containsWakeWord(transcript: String): Boolean {
            val norm = CoordinateMatcher.normalize(transcript)
            if (norm.isBlank()) return false
            val words = norm.split(' ')
            return words.any { word ->
                word == "jarvis" ||
                    word == "carvis" ||
                    word == "yarvis" ||
                    word == "cervis" ||
                    word == "jarviz" ||
                    word == "carviz" ||
                    word == "harvis"
            }
        }

        fun extractTrailingCommand(transcript: String): String? {
            val trimmed = transcript.trim()
            if (trimmed.isBlank()) return null
            val tokens = trimmed.split(Regex("\\s+"))
            val wakeIdx = tokens.indexOfFirst { token ->
                val norm = CoordinateMatcher.normalize(token)
                norm in wakeVariants && norm != "servis"
            }.takeIf { it >= 0 } ?: tokens.indexOfFirst { token ->
                CoordinateMatcher.normalize(token) == "jarvis"
            }

            if (wakeIdx < 0 || wakeIdx >= tokens.lastIndex) return null
            val trailing = tokens.drop(wakeIdx + 1)
                .joinToString(" ")
                .trim(',', '.', ':', ';', '!', '?')
                .trim()
            return trailing.takeIf { it.length >= 3 }
        }
    }
}

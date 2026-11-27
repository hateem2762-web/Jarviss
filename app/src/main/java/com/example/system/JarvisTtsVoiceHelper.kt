package com.example.system

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.example.data.preferences.JarvisPreferences
import java.util.Locale

data class TtsVoiceOption(
    val name: String,
    val label: String,
    val isMaleHint: Boolean,
    val isLocal: Boolean,
)

data class AppliedVoiceInfo(
    val activeVoiceName: String = "",
    val activeLabel: String = "Erkek (Otomatik)",
    val pitchApplied: Float = 0.88f,
    val availableOptions: List<TtsVoiceOption> = emptyList(),
)

object JarvisTtsVoiceHelper {

    private val KNOWN_MALE_VOICE_PREFIXES = listOf(
        "tr-tr-x-ama",
        "tr-tr-x-tmc",
    )

    private val KNOWN_FEMALE_VOICE_PREFIXES = listOf(
        "tr-tr-x-cfs",
        "tr-tr-x-efu",
        "tr-tr-x-mfm",
    )

    fun configureTurkishVoice(
        tts: TextToSpeech?,
        prefs: JarvisPreferences,
    ): AppliedVoiceInfo {
        if (tts == null) return AppliedVoiceInfo()

        val installedTurkish = getInstalledTurkishVoices(tts)
        val options = buildVoiceOptions(installedTurkish)

        val explicitlyChosen = prefs.selectedTtsVoiceName
            .takeIf { it.isNotBlank() }
            ?.let { wanted -> installedTurkish.firstOrNull { it.name == wanted } }

        val selectedVoice = when {
            explicitlyChosen != null -> explicitlyChosen
            installedTurkish.isNotEmpty() -> {
                if (prefs.maleVoiceEnabled) {
                    installedTurkish.maxByOrNull {
                        scoreVoiceForGender(
                            voiceName = it.name,
                            isLocal = !it.isNetworkConnectionRequired,
                            preferMale = true,
                        )
                    }
                } else {
                    installedTurkish.maxByOrNull {
                        scoreVoiceForGender(
                            voiceName = it.name,
                            isLocal = !it.isNetworkConnectionRequired,
                            preferMale = false,
                        )
                    }
                }
            }
            else -> null
        }

        if (selectedVoice != null) {
            runCatching { tts.voice = selectedVoice }
        }

        val isNativeMaleVoice = selectedVoice != null && isLikelyMaleVoiceName(selectedVoice.name)
        val pitch = when {
            explicitlyChosen != null -> if (prefs.maleVoiceEnabled && !isNativeMaleVoice) 0.78f else 0.96f
            prefs.maleVoiceEnabled && isNativeMaleVoice -> 0.92f
            prefs.maleVoiceEnabled -> 0.76f
            else -> 1.0f
        }
        val speechRate = if (prefs.maleVoiceEnabled) 1.02f else 1.0f

        runCatching {
            tts.setPitch(pitch)
            tts.setSpeechRate(speechRate)
        }

        val activeLabel = when {
            explicitlyChosen != null -> options.firstOrNull { it.name == explicitlyChosen.name }?.label
                ?: explicitlyChosen.name
            prefs.maleVoiceEnabled && isNativeMaleVoice -> "Erkek Sesi (Doğal)"
            prefs.maleVoiceEnabled -> "Erkek Sesi (Bariton)"
            else -> "Standart Ses"
        }

        return AppliedVoiceInfo(
            activeVoiceName = selectedVoice?.name.orEmpty(),
            activeLabel = activeLabel,
            pitchApplied = pitch,
            availableOptions = options,
        )
    }

    fun getInstalledTurkishVoices(tts: TextToSpeech): List<Voice> {
        val allVoices = runCatching { tts.voices }.getOrNull().orEmpty()
        return allVoices
            .filter { voice ->
                voice.locale?.language.equals("tr", ignoreCase = true) &&
                    !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .sortedWith(
                compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                    .thenBy { it.name }
            )
    }

    fun buildVoiceOptions(voices: List<Voice>): List<TtsVoiceOption> {
        return voices.mapIndexed { idx, voice ->
            val isLocal = !voice.isNetworkConnectionRequired
            val maleHint = isLikelyMaleVoiceName(voice.name)
            val femaleHint = isLikelyFemaleVoiceName(voice.name)
            val genderTag = when {
                maleHint -> " • Erkek"
                femaleHint -> " • Kadın"
                else -> ""
            }
            val storageTag = if (isLocal) "Yerel" else "Ağ"
            TtsVoiceOption(
                name = voice.name,
                label = "Ses ${idx + 1} ($storageTag$genderTag)",
                isMaleHint = maleHint,
                isLocal = isLocal,
            )
        }
    }

    fun scoreVoiceForGender(
        voiceName: String,
        isLocal: Boolean,
        preferMale: Boolean,
    ): Int {
        var score = if (isLocal) 15 else 0
        val maleHint = isLikelyMaleVoiceName(voiceName)
        val femaleHint = isLikelyFemaleVoiceName(voiceName)
        if (preferMale) {
            if (maleHint) score += 100
            if (femaleHint) score -= 80
        } else {
            if (femaleHint) score += 100
            if (maleHint) score -= 80
        }
        return score
    }

    fun isLikelyMaleVoiceName(rawName: String): Boolean {
        val norm = rawName.lowercase(Locale.ROOT)
        if (isLikelyFemaleVoiceName(norm)) return false
        if (KNOWN_MALE_VOICE_PREFIXES.any { norm.contains(it) }) return true
        return Regex("""(^|[^a-z])(male|erkek|man)([^a-z]|$)""").containsMatchIn(norm)
    }

    fun isLikelyFemaleVoiceName(rawName: String): Boolean {
        val norm = rawName.lowercase(Locale.ROOT)
        if (KNOWN_FEMALE_VOICE_PREFIXES.any { norm.contains(it) }) return true
        return Regex("""(^|[^a-z])(female|kadin|woman)([^a-z]|$)""").containsMatchIn(norm)
    }

    fun openInstallTtsData(context: Context): Boolean {
        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val settingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(installIntent)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(settingsIntent)
                true
            }.getOrDefault(false)
        }
    }
}

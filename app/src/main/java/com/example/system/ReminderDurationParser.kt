package com.example.system

import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

data class ParsedDuration(
    val minutes: Int? = null,
    val seconds: Int? = null,
) {
    val totalMillis: Long
        get() = ((minutes ?: 0).coerceAtLeast(0) * 60_000L) +
            ((seconds ?: 0).coerceAtLeast(0) * 1_000L)
}

object ReminderDurationParser {

    private val numberWords = mapOf(
        "bir" to 1,
        "iki" to 2,
        "uc" to 3,
        "dort" to 4,
        "bes" to 5,
        "alti" to 6,
        "yedi" to 7,
        "sekiz" to 8,
        "dokuz" to 9,
        "on" to 10,
        "onbir" to 11,
        "on bir" to 11,
        "oniki" to 12,
        "on iki" to 12,
        "onbes" to 15,
        "on bes" to 15,
        "yirmi" to 20,
        "yirmi bes" to 25,
        "otuz" to 30,
        "kirk" to 40,
        "kirk bes" to 45,
        "elli" to 50,
        "altmis" to 60,
        "one" to 1,
        "two" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "ten" to 10,
        "fifteen" to 15,
        "twenty" to 20,
        "thirty" to 30,
        "forty five" to 45,
    )

    private val hourMinuteRegex = Regex(
        """(\d+(?:[.,]\d+)?)\s*(?:saat(?:te|lik)?|sa|hours?|hrs?)\b(?:\s*(?:sonra|icinde)\b)?(?:\s*(\d+)\s*(?:dakika(?:da|lik)?|dk|mins?|minutes?)\b)?"""
    )
    private val halfHourPrefixRegex = Regex(
        """\b(yarim|ceyrek)\s*(?:saat(?:te|lik)?|sa)\b"""
    )
    private val bukukHourRegex = Regex(
        """\b(\d+|bir|iki|uc|dort|bes|alti)\s*bucuk\s*(?:saat(?:te|lik)?|sa)\b"""
    )
    private val minuteSecondRegex = Regex(
        """(\d+(?:[.,]\d+)?)\s*(?:dakika(?:da|lik)?|dk|mins?|minutes?)\b(?:\s*(\d+)\s*(?:saniye(?:de|lik)?|sn|secs?|seconds?)\b)?"""
    )
    private val secondOnlyRegex = Regex(
        """(\d+)\s*(?:saniye(?:de|lik)?|sn|secs?|seconds?)\b"""
    )
    private val clockTimeRegex = Regex(
        """(?:saat\s*)?([01]?\d|2[0-3])[:.]([0-5]\d)(?:'?[dt][ae]|['\s]|$)"""
    )

    fun parse(rawPrompt: String, nowMillis: Long = System.currentTimeMillis()): ParsedDuration? {
        val norm = normalizeTurkish(rawPrompt)
        if (norm.isBlank()) return null

        // Replace standalone Turkish/English number words before time units
        val replaced = replaceNumberWordsBeforeUnits(norm)

        // 1. "yarım saat" / "çeyrek saat"
        halfHourPrefixRegex.find(replaced)?.let { match ->
            val mins = if (match.groupValues[1] == "ceyrek") 15 else 30
            return ParsedDuration(minutes = mins, seconds = null)
        }

        // 2. "1 buçuk saat" / "bir buçuk saat"
        bukukHourRegex.find(replaced)?.let { match ->
            val rawNum = match.groupValues[1]
            val hours = rawNum.toIntOrNull() ?: numberWords[rawNum] ?: 1
            return ParsedDuration(minutes = hours * 60 + 30, seconds = null)
        }

        // 3. Explicit hours (+ optional minutes)
        hourMinuteRegex.find(replaced)?.let { match ->
            val hoursFloat = match.groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
            val extraMins = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val totalMins = (hoursFloat * 60f).roundToInt() + extraMins
            if (totalMins > 0) {
                return ParsedDuration(minutes = totalMins, seconds = null)
            }
        }

        // 4. Explicit minutes (+ optional seconds)
        minuteSecondRegex.find(replaced)?.let { match ->
            val minsFloat = match.groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
            val extraSecs = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val totalSeconds = (minsFloat * 60f).roundToInt() + extraSecs
            if (totalSeconds > 0) {
                val mins = totalSeconds / 60
                val secs = totalSeconds % 60
                return ParsedDuration(
                    minutes = mins.takeIf { it > 0 },
                    seconds = secs.takeIf { it > 0 },
                )
            }
        }

        // 5. Explicit seconds only
        secondOnlyRegex.find(replaced)?.let { match ->
            val secs = match.groupValues[1].toIntOrNull() ?: 0
            if (secs > 0) {
                return ParsedDuration(
                    minutes = (secs / 60).takeIf { it > 0 },
                    seconds = (secs % 60).takeIf { it > 0 },
                )
            }
        }

        // 6. Clock time e.g. "saat 16:30'da" or "16:30 hatirlat"
        val hasClockKeyword = replaced.contains("saat") ||
            replaced.contains("hatirlat") ||
            replaced.contains("alarm") ||
            replaced.contains("kur")
        if (hasClockKeyword) {
            clockTimeRegex.find(replaced)?.let { match ->
                val targetHour = match.groupValues[1].toIntOrNull() ?: return@let
                val targetMin = match.groupValues[2].toIntOrNull() ?: return@let
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= nowMillis + 5_000L) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                val diffSeconds = ((cal.timeInMillis - nowMillis) / 1_000L).toInt().coerceAtLeast(1)
                val mins = diffSeconds / 60
                val secs = diffSeconds % 60
                return ParsedDuration(
                    minutes = mins.takeIf { it > 0 },
                    seconds = secs.takeIf { it > 0 },
                )
            }
        }

        return null
    }

    private fun replaceNumberWordsBeforeUnits(text: String): String {
        var result = text
        val sortedWords = numberWords.entries.sortedByDescending { it.key.length }
        for ((word, value) in sortedWords) {
            val regex = Regex("""\b${Regex.escape(word)}\s+(dakika|dk|saniye|sn|saat|sa)\b""")
            result = regex.replace(result) { m -> "$value ${m.groupValues[1]}" }
        }
        return result
    }

    private fun normalizeTurkish(input: String): String {
        return input
            .lowercase(Locale.ROOT)
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('İ', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

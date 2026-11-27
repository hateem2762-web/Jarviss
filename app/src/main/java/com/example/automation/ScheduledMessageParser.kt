package com.example.automation

import com.example.model.InstalledAppInfo
import java.util.Locale

data class ParsedScheduledMessageCommand(
    val appName: String,
    val appPackage: String,
    val recipients: List<String>,
    val messageText: String,
    val hour: Int,
    val minute: Int,
    val repeatDaily: Boolean = true,
)

object ScheduledMessageParser {

    private val KNOWN_APPS = listOf(
        Triple("TikTok", "com.zhiliaoapp.musically", listOf("tiktok", "tik tok")),
        Triple("WhatsApp", "com.whatsapp", listOf("whatsapp", "wp", "watsap")),
        Triple("Instagram", "com.instagram.android", listOf("instagram", "insta", "ig")),
        Triple("Telegram", "org.telegram.messenger", listOf("telegram")),
        Triple("Mesajlar", "com.google.android.apps.messaging", listOf("sms", "mesajlar")),
    )

    fun parse(
        rawPrompt: String,
        installedApps: List<InstalledAppInfo> = emptyList(),
    ): ParsedScheduledMessageCommand? {
        val trimmed = rawPrompt.trim()
        if (trimmed.isBlank()) return null
        val lower = normalize(trimmed)

        val hasSendVerb = lower.contains("mesaj") ||
            lower.contains("mesaji") ||
            lower.contains("gonder") ||
            lower.contains("at") ||
            lower.contains("yaz")
        if (!hasSendVerb) return null

        // Extract HH:MM or HH.MM or "saat 23"
        val timePair = extractHourMinute(trimmed) ?: return null

        // Resolve target app
        val (appName, appPkg) = resolveApp(lower, installedApps) ?: return null

        // Extract quoted message text if given (e.g. '.' or "." or "merhaba")
        val quotedMessage = extractQuotedMessage(trimmed)

        // Extract recipients from "...'ta <recipients>'e/ye ..." or "kisilere ..."
        val recipients = extractRecipients(trimmed, quotedMessage)
        val messageText = quotedMessage?.ifBlank { "." }
            ?: inferUnquotedMessage(trimmed)
            ?: "."

        if (recipients.isEmpty()) return null

        val repeatDaily = !lower.contains("tek sefer") && !lower.contains("sadece bugun")

        return ParsedScheduledMessageCommand(
            appName = appName,
            appPackage = appPkg,
            recipients = recipients,
            messageText = messageText,
            hour = timePair.first,
            minute = timePair.second,
            repeatDaily = repeatDaily,
        )
    }

    fun resolveKnownAppPackage(appHint: String, installedApps: List<InstalledAppInfo> = emptyList()): Pair<String, String>? {
        return resolveApp(normalize(appHint), installedApps)
    }

    private fun extractHourMinute(raw: String): Pair<Int, Int>? {
        val hhmmRegex = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
        hhmmRegex.find(raw)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: return@let
            return h to min
        }
        val hourOnlyRegex = Regex("""\bsaat\s+([01]?\d|2[0-3])\b""", RegexOption.IGNORE_CASE)
        hourOnlyRegex.find(raw)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            return h to 0
        }
        return null
    }

    private fun resolveApp(
        normalizedPrompt: String,
        installedApps: List<InstalledAppInfo>,
    ): Pair<String, String>? {
        for ((defaultLabel, defaultPkg, aliases) in KNOWN_APPS) {
            if (aliases.any { normalizedPrompt.contains(it) }) {
                val installedMatch = installedApps.firstOrNull { app ->
                    val lbl = normalize(app.label)
                    val pkg = normalize(app.packageName)
                    aliases.any { alias -> lbl.contains(alias) || pkg.contains(alias) } ||
                        (defaultLabel == "TikTok" && (pkg.contains("musically") || pkg.contains("trill")))
                }
                return (installedMatch?.label ?: defaultLabel) to (installedMatch?.packageName ?: defaultPkg)
            }
        }
        for (app in installedApps) {
            val lbl = normalize(app.label)
            if (lbl.length >= 3 && normalizedPrompt.contains(lbl)) {
                return app.label to app.packageName
            }
        }
        return null
    }

    private fun extractQuotedMessage(raw: String): String? {
        val doubleQuote = Regex("""(?:^|[\s(\[])["“”]([^"“”]{1,120})["“”](?=$|[\s)\].,;:!?])""")
        doubleQuote.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return it
        }
        val singleQuote = Regex("""(?:^|[\s(\[])['‘’]([^'‘’]{1,120})['‘’](?=$|[\s)\].,;:!?])""")
        singleQuote.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return it
        }
        val looseDouble = Regex("""["“”]([^"“”]{1,120})["“”]""")
        return looseDouble.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractRecipients(raw: String, quotedMessage: String?): List<String> {
        var working = raw
        if (quotedMessage != null) {
            working = working
                .replace("'$quotedMessage'", " ")
                .replace("\"$quotedMessage\"", " ")
        }
        // Look specifically for substring after app locative ('ta/'te/'da/'de) and before "mesaj"/"gönder"/"at"/"yaz"
        val appLocativeRegex = Regex(
            """(?:tiktok|whatsapp|instagram|telegram|mesajlar)(?:['’]?(?:ta|te|da|de))?\s+(.+?)\s+(?:mesaj|mesajı|mesajını|gönder|gonder|at|yaz|ilet)""",
            RegexOption.IGNORE_CASE,
        )
        val segment = appLocativeRegex.find(working)?.groupValues?.getOrNull(1)
            ?: Regex("""\b(?:kişilere|kisilere|şu kişilere|su kisilere)\s+(.+?)\s+(?:mesaj|gönder|gonder|at|yaz)""", RegexOption.IGNORE_CASE)
                .find(working)?.groupValues?.getOrNull(1)
            ?: return emptyList()

        val cleaned = segment
            .replace(Regex("""['’](?:a|e|ya|ye|na|ne|lara|lere|larına|lerine)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:kişilere|kişilerine|kişisine|kisilere|kisilerine|kisisine|şu|su)\b""", RegexOption.IGNORE_CASE), " ")

        return cleaned
            .split(Regex("""\s*(?:,|\bve\b)\s*""", RegexOption.IGNORE_CASE))
            .map { token ->
                token.trim()
                    .replace(Regex("""^(?:kişilere|kisilere)\s+""", RegexOption.IGNORE_CASE), "")
                    .trim('.', ',', ':', ';', ' ')
            }
            .filter { it.isNotBlank() && !it.equals("kişiler", true) && !it.equals("kisiler", true) }
            .distinct()
    }

    private fun inferUnquotedMessage(raw: String): String? {
        val m = Regex("""\b(?:olarak|diye|şunu|sunu)\s+(.+?)\s+(?:mesaj|gönder|gonder|at|yaz)""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.trim()
        return m?.takeIf { it.isNotBlank() }
    }

    private fun normalize(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('İ', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .trim()
    }
}

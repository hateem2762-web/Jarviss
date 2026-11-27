package com.example.automation

import com.example.model.InstalledAppInfo
import com.example.model.QuickMessageDraft
import com.example.model.QuickMessageTimingMode
import java.util.Calendar
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
        Triple("WhatsApp", "com.whatsapp", listOf("whatsapp", "wp", "watsap", "vatsap")),
        Triple("TikTok", "com.zhiliaoapp.musically", listOf("tiktok", "tik tok")),
        Triple("Instagram", "com.instagram.android", listOf("instagram", "insta", "ig")),
        Triple("Telegram", "org.telegram.messenger", listOf("telegram")),
        Triple("Mesajlar", "com.google.android.apps.messaging", listOf("sms", "mesajlar")),
    )

    fun parse(
        rawPrompt: String,
        installedApps: List<InstalledAppInfo> = emptyList(),
    ): ParsedScheduledMessageCommand? {
        val draft = parseDraft(rawPrompt, installedApps) ?: return null
        return ParsedScheduledMessageCommand(
            appName = draft.appName,
            appPackage = draft.appPackage,
            recipients = draft.recipientsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
            messageText = draft.messageText.ifBlank { "." },
            hour = draft.hour,
            minute = draft.minute,
            repeatDaily = draft.repeatDaily,
        )
    }

    /**
     * Parses any immediate, relative-delay ("5 dakika sonra"), or clock-time messaging intent,
     * even when the recipient name is omitted or unclear, so the Quick Compose Box can open
     * pre-filled and let the user type/confirm the recipient name effortlessly.
     */
    fun parseDraft(
        rawPrompt: String,
        installedApps: List<InstalledAppInfo> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
    ): QuickMessageDraft? {
        val trimmed = rawPrompt.trim()
        if (trimmed.isBlank()) return null
        val lower = normalize(trimmed)

        val resolvedApp = resolveApp(lower, installedApps)
        val hasAppMention = resolvedApp != null
        val hasExplicitMessageCue = lower.contains("mesaj") ||
            lower.contains("mesaji") ||
            lower.contains("dm")
        val hasSendVerb = hasMessagingVerb(lower)

        // Avoid hijacking news or web-download or air-gesture commands
        if (lower.contains("indir") || lower.contains("haber") || lower.contains("gundem") ||
            lower.contains("el kontrol") || lower.contains("el hareket") || lower.contains("not al")
        ) {
            return null
        }

        val hasClockTime = extractHourMinute(trimmed) != null
        val isMessagingIntent = hasSendVerb || hasClockTime
        if (!isMessagingIntent) return null

        val (appName, appPkg) = resolvedApp ?: ("WhatsApp" to "com.whatsapp")
        val quotedMessage = extractQuotedMessage(trimmed)
        val recipients = extractRecipients(trimmed, quotedMessage)
        val messageText = quotedMessage?.ifBlank { "" }
            ?: inferUnquotedMessage(trimmed, recipients)
            ?: ""

        val delayMins = extractRelativeDelayMinutes(lower)
        val clockPair = extractHourMinute(trimmed)

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val (timingMode, finalDelayMins, hour, minute) = when {
            delayMins != null -> {
                cal.add(Calendar.MINUTE, delayMins)
                Quad(QuickMessageTimingMode.DELAY_MINUTES, delayMins, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            }
            clockPair != null -> {
                Quad(QuickMessageTimingMode.CLOCK_TIME, 5, clockPair.first, clockPair.second)
            }
            else -> {
                Quad(QuickMessageTimingMode.IMMEDIATE, 5, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            }
        }

        val repeatDaily = timingMode == QuickMessageTimingMode.CLOCK_TIME &&
            !lower.contains("tek sefer") &&
            !lower.contains("sadece bugun")

        return QuickMessageDraft(
            id = System.currentTimeMillis(),
            appName = appName,
            appPackage = appPkg,
            recipientsText = recipients.joinToString(", "),
            messageText = messageText,
            timingMode = timingMode,
            delayMinutes = finalDelayMins,
            hour = hour,
            minute = minute,
            repeatDaily = repeatDaily,
        )
    }

    fun encodeDraftTarget(draft: QuickMessageDraft): String {
        return listOf(
            "COMPOSE",
            draft.appName,
            draft.appPackage,
            draft.recipientsText.replace("|", " "),
            draft.timingMode.name,
            draft.delayMinutes.toString(),
            draft.hour.toString(),
            draft.minute.toString(),
            draft.repeatDaily.toString(),
        ).joinToString("|")
    }

    fun decodeDraftFromAction(
        target: String,
        message: String,
        installedApps: List<InstalledAppInfo> = emptyList(),
    ): QuickMessageDraft? {
        val raw = target.trim()
        val cal = Calendar.getInstance()
        if (raw.startsWith("COMPOSE|")) {
            val parts = raw.split("|")
            val appName = parts.getOrNull(1).orEmpty().ifBlank { "WhatsApp" }
            val appPkg = parts.getOrNull(2).orEmpty().ifBlank {
                resolveKnownAppPackage(appName, installedApps)?.second ?: "com.whatsapp"
            }
            val recipientsText = parts.getOrNull(3).orEmpty().trim()
            val timing = runCatching {
                QuickMessageTimingMode.valueOf(parts.getOrNull(4).orEmpty())
            }.getOrDefault(QuickMessageTimingMode.IMMEDIATE)
            val delayMins = parts.getOrNull(5)?.toIntOrNull()?.coerceIn(1, 720) ?: 5
            val hour = parts.getOrNull(6)?.toIntOrNull()?.coerceIn(0, 23) ?: cal.get(Calendar.HOUR_OF_DAY)
            val minute = parts.getOrNull(7)?.toIntOrNull()?.coerceIn(0, 59) ?: cal.get(Calendar.MINUTE)
            val repeat = parts.getOrNull(8)?.toBooleanStrictOrNull() ?: false
            return QuickMessageDraft(
                id = System.currentTimeMillis(),
                appName = appName,
                appPackage = appPkg,
                recipientsText = recipientsText,
                messageText = message.trim(),
                timingMode = timing,
                delayMinutes = delayMins,
                hour = hour,
                minute = minute,
                repeatDaily = repeat,
            )
        }
        if (raw.contains(":")) {
            val parts = raw.split(":", limit = 3)
            val appHint = parts.getOrNull(0).orEmpty().trim()
            val resolved = resolveKnownAppPackage(appHint, installedApps)
            val appName = resolved?.first ?: appHint.ifBlank { "WhatsApp" }
            val appPkg = resolved?.second ?: "com.whatsapp"
            val recipients = parts.getOrNull(1).orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
            val timeToken = parts.getOrNull(2).orEmpty().trim().lowercase(Locale.ROOT)
            return when {
                timeToken.isBlank() || timeToken == "now" || timeToken == "hemen" || timeToken == "simdi" || timeToken == "şimdi" -> {
                    QuickMessageDraft(
                        appName = appName,
                        appPackage = appPkg,
                        recipientsText = recipients,
                        messageText = message.trim(),
                        timingMode = QuickMessageTimingMode.IMMEDIATE,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                    )
                }
                timeToken.startsWith("+") || timeToken.endsWith("m") || timeToken.endsWith("dk") -> {
                    val mins = Regex("""\d+""").find(timeToken)?.value?.toIntOrNull()?.coerceIn(1, 720) ?: 5
                    cal.add(Calendar.MINUTE, mins)
                    QuickMessageDraft(
                        appName = appName,
                        appPackage = appPkg,
                        recipientsText = recipients,
                        messageText = message.trim(),
                        timingMode = QuickMessageTimingMode.DELAY_MINUTES,
                        delayMinutes = mins,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                    )
                }
                else -> {
                    val hm = timeToken.split(":", ".")
                    val h = hm.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23)
                    val m = hm.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    if (h != null) {
                        QuickMessageDraft(
                            appName = appName,
                            appPackage = appPkg,
                            recipientsText = recipients,
                            messageText = message.trim(),
                            timingMode = QuickMessageTimingMode.CLOCK_TIME,
                            hour = h,
                            minute = m,
                        )
                    } else {
                        QuickMessageDraft(
                            appName = appName,
                            appPackage = appPkg,
                            recipientsText = recipients,
                            messageText = message.trim(),
                            timingMode = QuickMessageTimingMode.IMMEDIATE,
                            hour = cal.get(Calendar.HOUR_OF_DAY),
                            minute = cal.get(Calendar.MINUTE),
                        )
                    }
                }
            }
        }
        return parseDraft(raw, installedApps)?.let { draft ->
            if (message.isNotBlank() && draft.messageText.isBlank()) {
                draft.copy(messageText = message.trim())
            } else {
                draft
            }
        }
    }

    private data class Quad(
        val mode: QuickMessageTimingMode,
        val delay: Int,
        val hour: Int,
        val minute: Int,
    )

    private fun hasMessagingVerb(lower: String): Boolean {
        return lower.contains("mesaj") ||
            lower.contains("mesaji") ||
            lower.contains("gonder") ||
            Regex("""\bat\b|\batsin\b|\byaz\b|\bilet\b|\byolla\b""").containsMatchIn(lower)
    }

    fun resolveKnownAppPackage(appHint: String, installedApps: List<InstalledAppInfo> = emptyList()): Pair<String, String>? {
        return resolveApp(normalize(appHint), installedApps)
    }

    private fun extractRelativeDelayMinutes(normalized: String): Int? {
        val minRegex = Regex("""\b(\d{1,3})\s*(?:dakika|dk)\s*(?:sonra|icinde)?\b""")
        minRegex.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { mins ->
            if (mins > 0) return mins.coerceIn(1, 720)
        }
        val hourRegex = Regex("""\b(\d{1,2})\s*saat\s*(?:sonra|icinde)?\b""")
        hourRegex.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { hrs ->
            if (hrs > 0) return (hrs * 60).coerceIn(1, 720)
        }
        if (normalized.contains("yarim saat sonra")) return 30
        return null
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
        val appLocativeRegex = Regex(
            """(?:tiktok|tik tok|whatsapp|wp|watsap|instagram|insta|telegram|mesajlar)(?:['’]?(?:ta|te|da|de|tan|ten|dan|den))?(?:\s+sonra)?\s+(.+?)\s+(?:mesaj|mesajı|mesajını|gönder|gonder|at|atsın|atsin|yaz|ilet|yolla)""",
            RegexOption.IGNORE_CASE,
        )
        val segment = appLocativeRegex.find(working)?.groupValues?.getOrNull(1)
            ?: Regex("""\b(?:kişilere|kisilere|şu kişilere|su kisilere)\s+(.+?)\s+(?:mesaj|gönder|gonder|at|atsın|atsin|yaz)""", RegexOption.IGNORE_CASE)
                .find(working)?.groupValues?.getOrNull(1)
            ?: Regex("""\b(?:hemen|şimdi|simdi|sonra)\s+([A-ZÇĞİÖŞÜa-zçğıöşü0-9_.\s]{2,36}?)(?:['’]?(?:a|e|ya|ye|na|ne))\s+(?:mesaj|gönder|gonder|at|yaz|selam|naber|merhaba)""", RegexOption.IGNORE_CASE)
                .find(working)?.groupValues?.getOrNull(1)
            ?: return emptyList()

        val cleaned = segment
            .replace(Regex("""['’](?:lara|lere|larına|lerine)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:kişilere|kişilerine|kişisine|kisilere|kisilerine|kisisine|şu|su|hemen|şimdi|simdi|mesajlara|mesajları|mesajlar)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b\d{1,3}\s*(?:dakika|dk|saat)\s*sonra\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b(?:saat\s+)?\d{1,2}[:.]\d{2}(?:['’]?(?:da|de|ta|te|dan|den|tan|ten)\s*sonra)?\b""", RegexOption.IGNORE_CASE), " ")

        return cleaned
            .split(Regex("""\s*(?:,|\bve\b)\s*""", RegexOption.IGNORE_CASE))
            .map { token ->
                token.trim()
                    .replace(Regex("""^(?:kişilere|kisilere|hemen|şimdi|simdi)\s+""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""['’]?(?:ye|ya|e|a)$""", RegexOption.IGNORE_CASE), "")
                    .trim('.', ',', ':', ';', ' ')
            }
            .filter {
                it.isNotBlank() &&
                    !it.equals("kişiler", true) &&
                    !it.equals("kisiler", true) &&
                    !it.equals("mesaj", true) &&
                    !it.equals("mesajlar", true) &&
                    !it.equals("hemen", true) &&
                    !it.equals("şimdi", true)
            }
            .distinct()
    }

    private fun inferUnquotedMessage(raw: String, recipients: List<String>): String? {
        val m = Regex("""\b(?:olarak|diye|şunu|sunu)\s+(.+?)\s+(?:mesaj|gönder|gonder|at|atsın|atsin|yaz)""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.trim()
        if (!m.isNullOrBlank()) return m

        // Also match "<recipient>'e <message> yaz/at/gonder"
        if (recipients.isNotEmpty()) {
            val lastRec = Regex.escape(recipients.last())
            val afterRec = Regex("""$lastRec(?:['’]?(?:a|e|ya|ye|na|ne))?\s+(.+?)\s+(?:mesaj|mesajı|gönder|gonder|at|atsın|atsin|yaz|ilet|yolla)\b""", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.getOrNull(1)?.trim()
            if (!afterRec.isNullOrBlank() &&
                !afterRec.equals("mesaj", true) &&
                !afterRec.equals("hemen", true) &&
                !afterRec.equals("şimdi", true) &&
                !afterRec.matches(Regex("""^\d+\s*(?:dakika|dk)\s*sonra$""", RegexOption.IGNORE_CASE))
            ) {
                return afterRec
            }
        }
        return null
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

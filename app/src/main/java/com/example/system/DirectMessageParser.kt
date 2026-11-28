package com.example.system

import com.example.model.InstalledAppInfo
import java.util.Locale

data class ParsedDirectMessageCommand(
    val appName: String,
    val appPackage: String,
    val recipient: String,
    val messageText: String,
)

/**
 * Parses natural-language instant messaging commands in Turkish (e.g.
 * "WhatsApp'ta Ali'ye selam yaz", "Telegram'da Ahmet'e yarın görüşürüz mesajı at",
 * "SMS ile Anneme eve geldim yaz") without any scheduling or delay logic.
 */
object DirectMessageParser {

    private val KNOWN_APPS = listOf(
        Triple("WhatsApp", "com.whatsapp", listOf("whatsapp", "wp", "watsap", "vatsap")),
        Triple("Telegram", "org.telegram.messenger", listOf("telegram")),
        Triple("Mesajlar", "com.google.android.apps.messaging", listOf("sms", "mesajlar", "kisa mesaj")),
        Triple("Instagram", "com.instagram.android", listOf("instagram", "insta", "ig")),
        Triple("TikTok", "com.zhiliaoapp.musically", listOf("tiktok", "tik tok")),
    )

    private val MESSAGING_APP_ALIASES = KNOWN_APPS.flatMap { it.third }.toSet()

    fun parse(
        rawPrompt: String,
        installedApps: List<InstalledAppInfo> = emptyList(),
    ): ParsedDirectMessageCommand? {
        val cleanedPrompt = rawPrompt
            .trim()
            .replace(Regex("""^(?:hey\s+)?jarvis[\s,:]*""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanedPrompt.isBlank()) return null

        val lower = normalize(cleanedPrompt)

        // Guard against hijacking unrelated commands
        if (
            lower.contains("indir") ||
            lower.contains("haber") ||
            lower.contains("gundem") ||
            lower.contains("el kontrol") ||
            lower.contains("el hareket") ||
            lower.contains("not al") ||
            lower.contains("not defter") ||
            lower.contains("hatirlat")
        ) {
            return null
        }

        val resolvedApp = resolveApp(lower, installedApps)
        val hasAppMention = resolvedApp != null
        val hasMessageWord = lower.contains("mesaj") ||
            lower.contains("sms") ||
            lower.contains("dm")
        val hasSendVerb = hasMessagingVerb(lower)

        // Require both a messaging verb and either an explicit messaging app or explicit message/SMS/DM cue
        if (!hasSendVerb || (!hasAppMention && !hasMessageWord)) {
            return null
        }

        val (appName, appPkg) = resolvedApp ?: ("WhatsApp" to "com.whatsapp")
        val quotedMessage = extractQuotedMessage(cleanedPrompt)
        val (recipient, extractedMsg) = extractRecipientAndMessage(
            raw = cleanedPrompt,
            quotedMessage = quotedMessage,
        )

        val finalRecipient = recipient.trim()
        val finalMessage = (quotedMessage ?: extractedMsg).trim()

        if (finalRecipient.isBlank() && finalMessage.isBlank()) {
            return null
        }

        return ParsedDirectMessageCommand(
            appName = appName,
            appPackage = appPkg,
            recipient = finalRecipient,
            messageText = finalMessage,
        )
    }

    fun encodeTarget(appName: String, appPackage: String, recipient: String): String {
        return listOf(
            "DIRECT",
            appName.replace("|", " ").trim(),
            appPackage.replace("|", " ").trim(),
            recipient.replace("|", " ").trim(),
        ).joinToString("|")
    }

    fun decodeTarget(
        rawTarget: String,
        rawMessage: String,
        installedApps: List<InstalledAppInfo> = emptyList(),
    ): ParsedDirectMessageCommand? {
        val target = rawTarget.trim()
        val msg = rawMessage.trim()

        if (target.startsWith("DIRECT|")) {
            val parts = target.split("|")
            val appNameHint = parts.getOrNull(1).orEmpty().trim()
            val appPkgHint = parts.getOrNull(2).orEmpty().trim()
            val recipient = parts.getOrNull(3).orEmpty().trim()
            val resolved = resolveKnownAppPackage(appNameHint, installedApps)
            val appName = appNameHint.ifBlank { resolved?.first ?: "WhatsApp" }
            val appPkg = appPkgHint.ifBlank { resolved?.second ?: "com.whatsapp" }
            return ParsedDirectMessageCommand(
                appName = appName,
                appPackage = appPkg,
                recipient = recipient,
                messageText = msg,
            )
        }

        if (target.contains(":")) {
            val parts = target.split(":", limit = 2)
            val appHint = parts.getOrNull(0).orEmpty().trim()
            val recipient = parts.getOrNull(1).orEmpty()
                .split(",")
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
            val resolved = resolveKnownAppPackage(appHint, installedApps)
            val appName = resolved?.first ?: appHint.ifBlank { "WhatsApp" }
            val appPkg = resolved?.second ?: "com.whatsapp"
            return ParsedDirectMessageCommand(
                appName = appName,
                appPackage = appPkg,
                recipient = recipient,
                messageText = msg,
            )
        }

        return parse(target, installedApps)?.let { parsed ->
            if (msg.isNotBlank() && parsed.messageText.isBlank()) {
                parsed.copy(messageText = msg)
            } else {
                parsed
            }
        }
    }

    fun resolveKnownAppPackage(
        appHint: String,
        installedApps: List<InstalledAppInfo> = emptyList(),
    ): Pair<String, String>? {
        return resolveApp(normalize(appHint), installedApps)
    }

    private fun hasMessagingVerb(lower: String): Boolean {
        return lower.contains("mesaj") ||
            lower.contains("gonder") ||
            Regex("""(?<![\p{L}\p{N}])(at|atsin|yaz|ilet|yolla)(?![\p{L}\p{N}])""").containsMatchIn(lower)
    }

    private fun resolveApp(
        normalizedPrompt: String,
        installedApps: List<InstalledAppInfo>,
    ): Pair<String, String>? {
        for ((defaultLabel, defaultPkg, aliases) in KNOWN_APPS) {
            if (aliases.any { alias ->
                    Regex("""(?<![\p{L}\p{N}])${Regex.escape(alias)}(?![\p{L}\p{N}])|${Regex.escape(alias)}""")
                        .containsMatchIn(normalizedPrompt)
                }
            ) {
                val installedMatch = installedApps.firstOrNull { app ->
                    val lbl = normalize(app.label)
                    val pkg = normalize(app.packageName)
                    aliases.any { alias -> lbl.contains(alias) || pkg.contains(alias) } ||
                        (defaultLabel == "TikTok" && (pkg.contains("musically") || pkg.contains("trill"))) ||
                        (defaultLabel == "Mesajlar" && (pkg.contains("messaging") || pkg == "com.android.mms"))
                }
                return (installedMatch?.label ?: defaultLabel) to (installedMatch?.packageName ?: defaultPkg)
            }
        }
        return null
    }

    private fun extractQuotedMessage(raw: String): String? {
        val doubleQuote = Regex("""["“”]([^"“”]{1,220})["“”]""")
        doubleQuote.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return it
        }
        // Only treat straight single quotes as message quotes when bounded by whitespace/punctuation (never Turkish apostrophes)
        val singleQuote = Regex("""(?:^|[\s(\[:])['‘’]([^'‘’]{1,220})['‘’](?=$|[\s)\].,;:!?])""")
        return singleQuote.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractRecipientAndMessage(
        raw: String,
        quotedMessage: String?,
    ): Pair<String, String> {
        var working = raw
        if (quotedMessage != null) {
            working = working
                .replace("\"$quotedMessage\"", " ")
                .replace("“$quotedMessage”", " ")
                .replace("'$quotedMessage'", " ")
                .trim()
        }

        // Strip app prefix or standalone app mention clause while keeping order info
        val appMentionRegex = Regex(
            """(?<![\p{L}\p{N}])(?:whatsapp|wp|watsap|vatsap|telegram|instagram|insta|ig|tiktok|tik\s+tok|mesajlar|sms)(?:['’]?(?:ta|te|da|de|tan|ten|dan|den|le|la)|(?:\s+(?:ile|üzerinden|uzerinden|uygulamasından|uygulamasindan|uygulamasında|uygulamasinda)))?(?![\p{L}\p{N}])""",
            RegexOption.IGNORE_CASE,
        )

        val withoutApp = appMentionRegex.replace(working, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Try matching: "<Recipient>'(y/n)(a/e) <Message?> <send verb>"
        val recipientDativeRegex = Regex(
            """^(.{1,42}?)(?:['’](?:ya|ye|na|ne|a|e)|(?:\s+kişisine|\s+kisisini|\s+kisisine))(?:\s+(.*))?$""",
            RegexOption.IGNORE_CASE,
        )

        val dativeMatch = recipientDativeRegex.find(withoutApp)
        if (dativeMatch != null) {
            val rawRecipient = cleanRecipientToken(dativeMatch.groupValues[1])
            val tail = dativeMatch.groupValues.getOrNull(2).orEmpty().trim()
            val cleanedMsg = if (quotedMessage != null) {
                ""
            } else {
                cleanTrailingSendPhrase(tail)
            }
            if (rawRecipient.isNotBlank()) {
                return rawRecipient to cleanedMsg
            }
        }

        // Try non-apostrophe 2-word capitalized recipient first, e.g. "Mustafa Yılmaza eve geldim yaz"
        val twoWordInlineDativeRegex = Regex(
            """(?<![\p{L}\p{N}])([A-ZÇĞİÖŞÜ][a-zçğıöşü0-9_.]{1,22}\s+[A-ZÇĞİÖŞÜ][a-zçğıöşü0-9_.]{1,22}?)(?:['’]?(?i:ya|ye|na|ne|a|e))\s+(.*?)(?:\s+(?i:mesaj\s*(?:at|gönder|gonder|yaz|yolla|ilet)|mesajı\s*(?:at|gönder|gonder|yaz)|yaz|at|atsın|atsin|gönder|gonder|ilet|yolla))(?:\s+(?i:lütfen|lutfen|hemen|şimdi|simdi))?[.!?]*$""",
        )
        val twoWordMatch = twoWordInlineDativeRegex.find(withoutApp)
        if (twoWordMatch != null) {
            val candidateRecipient = cleanRecipientToken(twoWordMatch.groupValues[1])
            val candidateMsg = if (quotedMessage != null) "" else cleanMessageBody(twoWordMatch.groupValues[2])
            if (candidateRecipient.isNotBlank() && normalize(candidateRecipient) !in MESSAGING_APP_ALIASES) {
                return candidateRecipient to candidateMsg
            }
        }

        // Single-word non-apostrophe dative inside the sentence, e.g. "Aliye mesaj at merhaba" or "Anneme eve geldim yaz"
        val inlineDativeRegex = Regex(
            """(?<![\p{L}\p{N}])([A-ZÇĞİÖŞÜa-zçğıöşü0-9_.]{2,26}?)(?:['’]?(?:ya|ye|na|ne|a|e))\s+(.*?)(?:\s+(?:mesaj\s*(?:at|gönder|gonder|yaz|yolla|ilet)|mesajı\s*(?:at|gönder|gonder|yaz)|yaz|at|atsın|atsin|gönder|gonder|ilet|yolla))(?:\s+(?:lütfen|lutfen|hemen|şimdi|simdi))?[.!?]*$""",
            RegexOption.IGNORE_CASE,
        )
        val inlineMatch = inlineDativeRegex.find(withoutApp)
        if (inlineMatch != null) {
            val candidateRecipient = cleanRecipientToken(inlineMatch.groupValues[1])
            val candidateMsg = if (quotedMessage != null) "" else cleanMessageBody(inlineMatch.groupValues[2])
            if (candidateRecipient.isNotBlank() && normalize(candidateRecipient) !in MESSAGING_APP_ALIASES) {
                return candidateRecipient to candidateMsg
            }
        }

        // Fallback: "<Recipient> kişisine ..." without suffix match at start
        val afterColon = Regex("""(?:yaz|at|gönder|gonder|mesaj\s+at)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
            .find(withoutApp)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        return "" to (quotedMessage ?: afterColon)
    }

    private fun cleanRecipientToken(raw: String): String {
        return raw
            .replace(Regex("""^(?:hemen|şimdi|simdi|lütfen|lutfen|bana|şu|su)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""['’](?:ya|ye|na|ne|a|e)$""", RegexOption.IGNORE_CASE), "")
            .trim(',', '.', ':', ';', ' ')
    }

    private fun cleanTrailingSendPhrase(rawTail: String): String {
        if (rawTail.isBlank()) return ""
        var text = rawTail.trim()

        // Check colon syntax like "mesaj yaz: Merhaba nasılsın"
        val colonMatch = Regex("""^(?:mesaj(?:ı|ini|ını)?\s*)?(?:yaz|at|gönder|gonder|yolla|ilet)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)
        if (colonMatch != null) {
            return colonMatch.groupValues[1].trim()
        }

        text = text
            .replace(
                Regex(
                    """\s*(?:diye|olarak)?\s*(?:bir\s+)?(?:mesaj(?:ı|ını|ini)?\s+)?(?:gönder|gonder|yaz|at|atsın|atsin|yolla|ilet)(?:\s+(?:lütfen|lutfen|hemen|şimdi|simdi))?[.!?]*$""",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .trim()
        return cleanMessageBody(text)
    }

    private fun cleanMessageBody(raw: String): String {
        val cleaned = raw
            .replace(Regex("""^(?:olarak|diye|şunu|sunu|mesaj\s+olarak)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:diye|olarak)$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val norm = normalize(cleaned)
        if (norm in setOf("mesaj", "mesaji", "mesajini", "hemen", "simdi", "dm", "sms", "lutfen")) {
            return ""
        }
        return cleaned
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

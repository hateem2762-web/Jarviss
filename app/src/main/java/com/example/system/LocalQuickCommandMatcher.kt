package com.example.system

import com.example.automation.ScheduledMessageParser
import com.example.model.ActionIntent
import com.example.model.JarvisAction
import java.util.Locale

object LocalQuickCommandMatcher {

    fun match(rawPrompt: String, isNewsModeActive: Boolean = false): JarvisAction? {
        val norm = normalize(rawPrompt)
        if (norm.isBlank()) return null

        // 0. Active News View controls ("Sonraki haber", "Önceki haber", "Detaylandır", "Kapat / Sohbete dön")
        if (isNewsModeActive) {
            matchNewsControl(norm)?.let { return it }
        }

        // 0.5 Scheduled Automatic Messaging ("Saat 23:00'ten sonra TikTok'ta Ahmet'e '.' mesajı at")
        matchScheduledMessageCommand(rawPrompt)?.let { return it }

        // 0.6 Autonomous Web Browsing & File / APK Download ("... sitesine gir şunu indir", "... APK indir")
        matchWebDownloadCommand(rawPrompt, norm)?.let { return it }

        // 1. Natural language News / Briefing triggers (general or topic-specific)
        matchNewsRequest(rawPrompt = rawPrompt, norm = norm)?.let { return it }

        // 2. "Bu ekranı not al" / "Şu ekranı not al" / "Bunu not defterine kaydet"
        val isScreenNoteCommand =
            (norm.contains("ekran") && (norm.contains("not al") || norm.contains("not et") || norm.contains("kaydet"))) ||
                norm.contains("not defterine kaydet") ||
                norm.contains("not defterine ekle") ||
                norm == "not al"
        if (isScreenNoteCommand) {
            return JarvisAction(intent = ActionIntent.CAPTURE_SCREEN_NOTE)
        }

        // 3. Flashlight
        if (norm in setOf("fener", "feneri ac", "fener ac", "flasi ac", "flas ac", "isigi ac")) {
            return JarvisAction(intent = ActionIntent.TORCH_CONTROL, target = "on")
        }
        if (norm in setOf("feneri kapat", "fener kapat", "feneri kapa", "flasi kapat", "flas kapat", "isigi kapat")) {
            return JarvisAction(intent = ActionIntent.TORCH_CONTROL, target = "off")
        }

        // 4. Battery status
        if (
            norm.contains("sarjim kac") ||
            norm.contains("sarj yuzde") ||
            norm.contains("pil yuzde") ||
            norm.contains("pil durumu") ||
            norm.contains("sarj durumu") ||
            norm.contains("batarya durumu") ||
            norm.contains("sarj oluyor mu")
        ) {
            return JarvisAction(intent = ActionIntent.BATTERY_STATUS)
        }

        // 5. Direct System Settings shortcuts
        if (norm.contains("ayar")) {
            when {
                norm.contains("wifi") || norm.contains("wi fi") || norm.contains("kablosuz") ->
                    return JarvisAction(intent = ActionIntent.OPEN_SYSTEM_SETTINGS, target = "wifi")
                norm.contains("bluetooth") ->
                    return JarvisAction(intent = ActionIntent.OPEN_SYSTEM_SETTINGS, target = "bluetooth")
                norm.contains("ses ayar") ->
                    return JarvisAction(intent = ActionIntent.OPEN_SYSTEM_SETTINGS, target = "sound")
                norm.contains("erisilebilirlik") ->
                    return JarvisAction(intent = ActionIntent.OPEN_SYSTEM_SETTINGS, target = "accessibility")
            }
        }

        return null
    }

    private fun matchScheduledMessageCommand(rawPrompt: String): JarvisAction? {
        val parsed = ScheduledMessageParser.parse(rawPrompt) ?: return null
        val formattedTime = String.format(Locale.ROOT, "%02d:%02d", parsed.hour, parsed.minute)
        val encodedTarget = listOf(
            parsed.appName,
            parsed.appPackage,
            parsed.recipients.joinToString(","),
            formattedTime,
            parsed.repeatDaily.toString(),
        ).joinToString("|")
        return JarvisAction(
            intent = ActionIntent.SCHEDULE_MESSAGE,
            target = encodedTarget,
            message = parsed.messageText,
        )
    }

    private fun matchWebDownloadCommand(rawPrompt: String, norm: String): JarvisAction? {
        if (norm.contains("ses paketi")) return null
        val wantsDownload = norm.contains("indir") || norm.contains("download")
        if (!wantsDownload) return null

        val hasWebOrFileCue = norm.contains("site") ||
            norm.contains("sayfa") ||
            norm.contains("apk") ||
            norm.contains("zip") ||
            norm.contains("pdf") ||
            norm.contains("dosya") ||
            norm.contains("telefona") ||
            norm.contains("cihaza") ||
            norm.contains("bulup") ||
            rawPrompt.contains("http://", ignoreCase = true) ||
            rawPrompt.contains("https://", ignoreCase = true) ||
            Regex("""[a-zA-Z0-9-]+\.[a-zA-Z]{2,}""").containsMatchIn(rawPrompt)

        if (!hasWebOrFileCue) return null

        val cleanedTarget = rawPrompt
            .replace(Regex("""^(?:hey\s+)?jarvis[\s,:]*""", RegexOption.IGNORE_CASE), "")
            .trim()
        return JarvisAction(
            intent = ActionIntent.WEB_DOWNLOAD_AGENT,
            target = cleanedTarget,
        )
    }

    private fun matchNewsRequest(rawPrompt: String, norm: String): JarvisAction? {
        val trimmed = norm
            .removePrefix("hey jarvis ")
            .removePrefix("jarvis ")
            .trim()

        // Guard against messaging commands like "Ahmet'e haber ver toplantı iptal"
        val isMessagingCommand = trimmed.contains("whatsapp") ||
            trimmed.contains("mesaj") ||
            trimmed.contains("sms") ||
            (trimmed.contains("haber ver") &&
                !trimmed.contains("hakkinda") &&
                !trimmed.contains("ile ilgili") &&
                !trimmed.contains("konusunda") &&
                !trimmed.contains("dair") &&
                !trimmed.contains("uzerine") &&
                !trimmed.contains("ne var") &&
                !trimmed.contains("neler var") &&
                !trimmed.contains("neler oluyor") &&
                !trimmed.contains("gundem") &&
                !trimmed.contains("haberler"))
        if (isMessagingCommand) return null

        val generalTriggers = listOf(
            "gundemi ac",
            "gundem ac",
            "gundemden bahset",
            "gundemde ne var",
            "gundem ne",
            "gundemi ver",
            "gundemi oku",
            "haberleri ver",
            "haberleri ac",
            "haberleri de",
            "haberleri soyle",
            "haberleri oku",
            "haberlere bakalim",
            "haberleri goster",
            "sabah brifingi",
            "haber brifingi",
            "gunluk brifing",
            "gundem bulteni",
            "haber bulteni",
            "bugun neler oluyor",
            "bugun ne oldu",
            "dunyada ne oluyor",
            "neler olmus",
            "son dakika haber",
        )

        val hasTopicRelationWithNews = (trimmed.contains("hakkinda") ||
            trimmed.contains("ile ilgili") ||
            trimmed.contains("konusunda") ||
            trimmed.contains("dair") ||
            trimmed.contains("uzerine")) &&
            (trimmed.contains("haber") ||
                trimmed.contains("gundem") ||
                trimmed.contains("gelisme") ||
                trimmed.contains("son durum") ||
                trimmed.contains("ne var"))

        val hasNewsPluralOrDefinite = Regex("""\bhaber(i|ini|in|ler|leri|lerini|lerden|lere|e)?\b""")
            .containsMatchIn(trimmed) &&
            (trimmed.contains("ver") ||
                trimmed.contains("ac") ||
                trimmed.contains("anlat") ||
                trimmed.contains("bahset") ||
                trimmed.contains("getir") ||
                trimmed.contains("goster") ||
                trimmed.contains("oku") ||
                trimmed.contains("soyle") ||
                trimmed.contains("bakalim") ||
                trimmed.contains("ozet") ||
                trimmed.contains("ne var") ||
                trimmed.contains("neler var") ||
                trimmed.contains("neler oluyor") ||
                trimmed.endsWith("haberleri") ||
                trimmed.endsWith("haberler") ||
                trimmed.endsWith("haberi") ||
                trimmed.endsWith("gundemi"))

        val hasTopicGundemOrUpdate = (trimmed.contains("gundemi") ||
            trimmed.contains("gundemini") ||
            trimmed.contains("gundeminden") ||
            trimmed.contains("gelismeleri") ||
            trimmed.contains("gelismelerinden")) &&
            (trimmed.contains("ver") ||
                trimmed.contains("ac") ||
                trimmed.contains("anlat") ||
                trimmed.contains("bahset") ||
                trimmed.contains("getir") ||
                trimmed.contains("goster") ||
                trimmed.contains("oku") ||
                trimmed.contains("ne") ||
                trimmed.contains("bakalim"))

        val isGeneralMatch = generalTriggers.any { trimmed.contains(it) } ||
            (trimmed.contains("haber") &&
                (trimmed.contains("bakalim") || trimmed.contains("anlat") || trimmed.contains("ozet"))) ||
            (trimmed.contains("gundem") && trimmed.contains("ozet"))

        if (!isGeneralMatch && !hasTopicRelationWithNews && !hasNewsPluralOrDefinite && !hasTopicGundemOrUpdate) {
            return null
        }

        val topic = extractNewsTopic(rawPrompt)
        return JarvisAction(intent = ActionIntent.OPEN_NEWS_BRIEFING, target = topic)
    }

    fun extractNewsTopic(rawPrompt: String): String {
        val strippedSuffixes = rawPrompt
            .replace(
                Regex(
                    """['’](?:da|de|ta|te|dan|den|tan|ten|in|ın|un|ün|nin|nın|nun|nün|a|e|ya|ye|la|le|i|ı|u|ü|yi|yı|yu|yü)\b""",
                    RegexOption.IGNORE_CASE,
                ),
                ""
            )
            .replace(Regex("""[?!.,;:"“”()]+"""), " ")

        val rawTokens = strippedSuffixes
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

        val topicTokens = rawTokens.filter { token ->
            val key = normalize(token)
            key.isNotBlank() && key !in NEWS_FILLER_WORDS
        }
        return topicTokens.joinToString(" ").trim()
    }

    private fun matchNewsControl(norm: String): JarvisAction? {
        val cleaned = norm.removePrefix("jarvis ").trim()
        return when {
            cleaned.contains("sonraki") ||
                cleaned.contains("siradaki") ||
                cleaned == "ileri" ||
                cleaned == "gec" ||
                cleaned.contains("bunu gec") ||
                cleaned.contains("diger haber") ->
                JarvisAction(intent = ActionIntent.NEWS_CONTROL, target = "next")

            cleaned.contains("onceki") ||
                cleaned == "geri" ||
                cleaned.contains("geri don") ->
                JarvisAction(intent = ActionIntent.NEWS_CONTROL, target = "previous")

            cleaned.contains("detay") ||
                cleaned.contains("ayrinti") ||
                cleaned.contains("devamini anlat") ||
                cleaned.contains("biraz daha ac") ->
                JarvisAction(intent = ActionIntent.NEWS_CONTROL, target = "detail")

            cleaned.contains("duraklat") ||
                cleaned.contains("durdur") ||
                cleaned == "sus" ->
                JarvisAction(intent = ActionIntent.NEWS_CONTROL, target = "pause")

            cleaned.contains("devam et") ||
                cleaned == "devam" ||
                cleaned.contains("tekrar oku") ->
                JarvisAction(intent = ActionIntent.NEWS_CONTROL, target = "resume")

            cleaned.contains("sohbete don") ||
                cleaned.contains("haberi kapat") ||
                cleaned.contains("haberleri kapat") ||
                cleaned.contains("haberden cik") ||
                cleaned.contains("gundemi kapat") ||
                cleaned == "kapat" ->
                JarvisAction(intent = ActionIntent.NEWS_CONTROL, target = "close")

            else -> null
        }
    }

    private val NEWS_FILLER_WORDS = setOf(
        "jarvis", "hey", "carvis", "yarvis",
        "bana", "bize", "bir", "bi", "su", "bu", "o",
        "soyle", "boyle", "lutfen", "rica", "etsem", "ozellikle",
        "hakkinda", "hakkindaki", "ilgili", "konusunda", "konusundaki",
        "dair", "uzerine", "ile", "ve", "de", "da", "mi", "mu",
        "haber", "haberi", "haberini", "haberler", "haberleri", "haberlerini",
        "haberlerden", "haberlere", "habere", "haberle",
        "gundem", "gundemi", "gundemini", "gundemde", "gundemden", "gundeminden",
        "brifing", "brifingi", "bulten", "bulteni",
        "gelisme", "gelismeler", "gelismeleri", "gelismelerinden",
        "ver", "verir", "verirmisin", "misin", "musun", "ac", "acar", "acarmisin",
        "anlat", "anlatir", "anlatirmisin", "bahset", "bahseder", "bahsedermisin",
        "getir", "goster", "oku", "soyleyiver", "bakalim",
        "nedir", "neler", "ne", "var", "yok", "oluyor", "oldu", "olmus",
        "son", "dakika", "durum", "ozet", "ozeti",
        "guncel", "bugun", "bugunku", "sabah", "gunluk", "dunyada", "simdi",
    )

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
            .replace('-', ' ')
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

package com.example.model

import java.util.Locale

enum class SceneCardType {
    EMAIL_CARD,
    METRIC_EVENT,
    SPORTS_SCORE,
    WEATHER_CARD,
    FINANCE_CARD,
    MEDIA_CARD,
    SYSTEM_STATUS,
    TOPIC_VISUAL,
}

data class AiSceneBeat(
    val cueKey: String = "",
    val cardType: String = "",
    val title: String = "",
    val highlightMetric: String = "",
    val highlightLabel: String = "",
    val sender: String = "",
    val subject: String = "",
    val detail: String = "",
)

data class NarrationSceneCard(
    val sentenceIndex: Int,
    val totalSentences: Int,
    val sentenceText: String,
    val cardType: SceneCardType,
    val visualKey: String,
    val categoryBadge: String,
    val title: String,
    val subtitle: String,
    val highlightMetric: String = "",
    val highlightUnit: String = "",
    val emailSender: String = "",
    val emailSubject: String = "",
    val emailCount: String = "",
    val accentToneKey: String = "neutral",
)

object SentenceSegmenter {

    private val ABBREVIATIONS = setOf(
        "dr", "prof", "doç", "yrd", "av", "vb", "vs", "örn", "dk", "sn",
        "sa", "st", "mah", "cad", "sok", "no", "apt", "tel", "inc", "ltd",
        "m", "km", "cm", "kg", "gr", "tl", "usd", "eur",
    )

    fun split(text: String): List<String> {
        val cleaned = text
            .replace("\r\n", "\n")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        val rawSentences = mutableListOf<String>()
        val buffer = StringBuilder()

        var i = 0
        while (i < cleaned.length) {
            val ch = cleaned[i]
            if (ch == '\n') {
                val part = buffer.toString().trim()
                if (part.isNotBlank()) rawSentences.add(part)
                buffer.clear()
                i++
                continue
            }

            buffer.append(ch)

            if (ch == '.' || ch == '!' || ch == '?') {
                val prevChar = cleaned.getOrNull(i - 1)
                val nextChar = cleaned.getOrNull(i + 1)
                val isDecimal = ch == '.' && prevChar?.isDigit() == true && nextChar?.isDigit() == true
                val isEllipsis = ch == '.' && (prevChar == '.' || nextChar == '.')
                val isBoundary = nextChar == null || nextChar.isWhitespace() || nextChar == '"' || nextChar == '\''

                if (!isDecimal && !isEllipsis && isBoundary) {
                    val lastWord = buffer.toString()
                        .dropLast(1)
                        .substringAfterLast(' ')
                        .trim('"', '\'', '(', '[')
                        .lowercase(Locale("tr", "TR"))
                    val isAbbreviation = ch == '.' && lastWord in ABBREVIATIONS
                    val isOrdinalNumber = ch == '.' && lastWord.all { it.isDigit() } && lastWord.isNotEmpty()
                    if (!isAbbreviation && !isOrdinalNumber) {
                        val candidate = buffer.toString().trim()
                        if (candidate.isNotBlank()) {
                            rawSentences.add(candidate)
                            buffer.clear()
                        }
                    }
                }
            }
            i++
        }

        val tail = buffer.toString().trim()
        if (tail.isNotBlank()) {
            rawSentences.add(tail)
        }

        // Split overly long sentences gently at semicolons or em-dashes for responsive pacing
        val expanded = mutableListOf<String>()
        for (sentence in rawSentences) {
            if (sentence.length > 165 && (sentence.contains(';') || sentence.contains(" — "))) {
                val clauses = sentence.split(Regex("""\s*(?:;|—)\s*"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (clauses.size > 1) {
                    expanded.addAll(clauses)
                    continue
                }
            }
            expanded.add(sentence)
        }

        // Merge tiny trailing fragments (< 6 chars) so TTS & cards never flash blankly
        val merged = mutableListOf<String>()
        for (item in expanded) {
            if (merged.isNotEmpty() && item.length < 6) {
                merged[merged.lastIndex] = "${merged.last()} $item".trim()
            } else {
                merged.add(item)
            }
        }
        return merged.ifEmpty { listOf(cleaned) }
    }
}

object SentenceSceneAnalyzer {

    private val trLocale = Locale("tr", "TR")

    private val EMAIL_COUNT_REGEX = Regex(
        """(?<![\p{L}\p{N}])(\d+)\s*(?:yeni|okunmamış|önemli|adet|tane)?\s*(?:e-?posta|mail|ileti|mesaj)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )
    private val EMAIL_ADDRESS_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val QUOTED_REGEX = Regex("""["“]([^"”]{2,80})["”]""")
    private val SENDER_FROM_REGEX = Regex(
        """(?<![\p{L}\p{N}])([A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ0-9.]+(?:\s+[A-ZÇĞİÖŞÜ][a-zçğıöşüA-ZÇĞİÖŞÜ0-9.]+){0,3})(?:'?[dDtT][aAeE]n| tarafından| adresinden| gel[ea]n)(?![\p{L}\p{N}])"""
    )
    private val SUBJECT_REGEX = Regex(
        """(?:konulu|konusu[:\s]+|başlıklı)\s*["“']?([^"”.,;\n]{3,60})""",
        RegexOption.IGNORE_CASE
    )

    private val SCORE_REGEX = Regex("""(?<![\p{L}\p{N}])(\d{1,2})\s*[-:]\s*(\d{1,2})(?![\p{L}\p{N}])""")
    private val CLOCK_CONTEXT_REGEX = Regex(
        """(?:saat|saati|saatte)\s+\d{1,2}:\d{2}|\d{1,2}:\d{2}'(?:da|de|ta|te|dan|den|tan|ten)?""",
        RegexOption.IGNORE_CASE
    )

    private val INCIDENT_METRIC_REGEX = Regex(
        """(?<![\p{L}\p{N}])(\d+(?:[.,]\d+)?)\s*(yaralı|ölü|can kaybı|kişi|gözaltı|tutuklu|şüpheli|taraftar|mağdur|itfaiye|ekip|ambulans|olay|vaka|hasar|ev|araç|büyüklüğünde|şiddetinde)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )
    private val GENERAL_NUMBER_UNIT_REGEX = Regex(
        """(%\s*\d+(?:[.,]\d+)?|\d+(?:[.,]\d+)?\s*(?:%|yüzde|milyon|milyar|bin|kişi|adet|tane|puan|gol|dakika|dk|saat|saniye|km|metre|yıl|gün|derece|°c))""",
        RegexOption.IGNORE_CASE
    )
    private val FINANCE_METRIC_REGEX = Regex(
        """(%\s*\d+(?:[.,]\d+)?|\d+(?:[.,]\d+)?\s*(?:tl|lira|₺|dolar|usd|\$|euro|eur|€|sterlin|gbp|gram|çeyrek|puan|baz puan|%))""",
        RegexOption.IGNORE_CASE
    )
    private val TEMP_REGEX = Regex(
        """(-?\d+(?:[.,]\d+)?)\s*(?:derece|°c?)""",
        RegexOption.IGNORE_CASE
    )

    fun buildScenesForNarration(
        fullText: String,
        actionIntent: ActionIntent? = null,
        aiCues: List<String> = emptyList(),
        aiSceneBeats: List<AiSceneBeat> = emptyList(),
    ): List<NarrationSceneCard> {
        val sentences = SentenceSegmenter.split(fullText)
        if (sentences.isEmpty()) return emptyList()
        return sentences.mapIndexed { idx, sentence ->
            analyzeSentence(
                sentence = sentence,
                index = idx,
                total = sentences.size,
                actionIntent = actionIntent,
                aiCues = aiCues,
                aiSceneBeats = aiSceneBeats,
            )
        }
    }

    fun analyzeSentence(
        sentence: String,
        index: Int,
        total: Int,
        actionIntent: ActionIntent? = null,
        aiCues: List<String> = emptyList(),
        aiSceneBeats: List<AiSceneBeat> = emptyList(),
    ): NarrationSceneCard {
        val cleanSentence = sentence.trim()
        val norm = normalize(cleanSentence)
        val matchedBeat = matchBeatForSentence(index, norm, aiSceneBeats)

        // 1. Gmail / Email Detection (explicit rule requested by user)
        if (isEmailSentence(norm, matchedBeat)) {
            return buildEmailScene(
                sentence = cleanSentence,
                index = index,
                total = total,
                beat = matchedBeat,
            )
        }

        // 2. Conflict / Incident / News Event with Number or Detail (e.g., "14 yaralı")
        // Checked before generic stadium/venue keywords so stadium incidents show impact/casualty metrics
        val incidentCard = tryBuildIncidentOrEventScene(
            sentence = cleanSentence,
            norm = norm,
            index = index,
            total = total,
            beat = matchedBeat,
        )
        if (incidentCard != null) return incidentCard

        // 3. Sports Score Detection
        val scoreCard = tryBuildSportsScoreScene(
            sentence = cleanSentence,
            norm = norm,
            index = index,
            total = total,
            beat = matchedBeat,
        )
        if (scoreCard != null) return scoreCard

        // 4. Finance / Market / Currency
        val financeCard = tryBuildFinanceScene(
            sentence = cleanSentence,
            norm = norm,
            index = index,
            total = total,
            beat = matchedBeat,
        )
        if (financeCard != null) return financeCard

        // 5. Weather / Temperature
        val weatherCard = tryBuildWeatherScene(
            sentence = cleanSentence,
            norm = norm,
            index = index,
            total = total,
            beat = matchedBeat,
        )
        if (weatherCard != null) return weatherCard

        // 6. Media / Playback
        val mediaCard = tryBuildMediaScene(
            sentence = cleanSentence,
            norm = norm,
            index = index,
            total = total,
            actionIntent = actionIntent,
            beat = matchedBeat,
        )
        if (mediaCard != null) return mediaCard

        // 7. System / Device / Action Status
        val systemCard = tryBuildSystemScene(
            sentence = cleanSentence,
            norm = norm,
            index = index,
            total = total,
            actionIntent = actionIntent,
            beat = matchedBeat,
        )
        if (systemCard != null) return systemCard

        // 8. General Metric or AI Beat or Contextual Sentence Topic Card
        return buildGeneralOrMetricScene(
            sentence = cleanSentence,
            norm = norm,
            index = index,
            total = total,
            actionIntent = actionIntent,
            aiCues = aiCues,
            beat = matchedBeat,
        )
    }

    private fun matchBeatForSentence(
        index: Int,
        normSentence: String,
        beats: List<AiSceneBeat>,
    ): AiSceneBeat? {
        if (beats.isEmpty()) return null
        // Check keyword overlap first so synced beat follows sentence meaning
        for (beat in beats) {
            val tokens = buildList {
                addAll(normalize(beat.title).split(' '))
                addAll(normalize(beat.highlightMetric).split(' '))
                addAll(normalize(beat.sender).split(' '))
                addAll(normalize(beat.subject).split(' '))
            }.filter { it.length >= 3 }
            if (tokens.any { normSentence.contains(it) }) {
                return beat
            }
        }
        return beats.getOrNull(index)
    }

    private fun isEmailSentence(norm: String, beat: AiSceneBeat?): Boolean {
        if (beat?.cardType?.equals("email", ignoreCase = true) == true) return true
        val emailKeywords = listOf(
            "gmail", "e-posta", "eposta", "gelen kutu", "mail", "mailler",
            "mailin", "mailleri", "ileti", "outlook", "posta kutu"
        )
        return emailKeywords.any { norm.contains(it) } || EMAIL_ADDRESS_REGEX.containsMatchIn(norm)
    }

    private fun buildEmailScene(
        sentence: String,
        index: Int,
        total: Int,
        beat: AiSceneBeat?,
    ): NarrationSceneCard {
        val emailCount = EMAIL_COUNT_REGEX.find(sentence)?.groupValues?.getOrNull(1)
            ?.let { "$it MAİL" }
            ?: beat?.highlightMetric?.takeIf { it.isNotBlank() }.orEmpty()

        val emailAddress = EMAIL_ADDRESS_REGEX.find(sentence)?.value.orEmpty()
        val quoted = QUOTED_REGEX.find(sentence)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val subjectMatch = SUBJECT_REGEX.find(sentence)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val senderStopWords = setOf(
            "gmail", "mail", "posta", "e-posta", "eposta", "gelen", "bugun", "son",
            "outlook", "yeni", "gelen kutusu", "inbox"
        )
        val senderFromText = SENDER_FROM_REGEX.findAll(sentence)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .firstOrNull { candidate ->
                candidate.isNotBlank() && normalize(candidate) !in senderStopWords
            }
            .orEmpty()

        val sender = beat?.sender?.takeIf { it.isNotBlank() }
            ?: senderFromText.ifBlank { emailAddress.ifBlank { "Gelen Kutusu" } }

        val cleanedSubjectMatch = subjectMatch.takeUnless {
            val n = normalize(it)
            n.startsWith("mail") || n.startsWith("e-posta") || n.startsWith("eposta") ||
                n.startsWith("mesaj") || n.startsWith("ileti") || n == "geldi" || n == "var"
        }.orEmpty()

        val subject = beat?.subject?.takeIf { it.isNotBlank() }
            ?: quoted.ifBlank {
                cleanedSubjectMatch.ifBlank {
                    beat?.title?.takeIf { it.isNotBlank() } ?: "E-Posta Özeti"
                }
            }

        val detail = beat?.detail?.takeIf { it.isNotBlank() } ?: condenseSummary(sentence)

        return NarrationSceneCard(
            sentenceIndex = index,
            totalSentences = total,
            sentenceText = sentence,
            cardType = SceneCardType.EMAIL_CARD,
            visualKey = "email_inbox",
            categoryBadge = "GMAIL // GELEN KUTUSU",
            title = subject,
            subtitle = detail,
            highlightMetric = emailCount,
            highlightUnit = if (emailCount.isNotBlank()) "GELEN" else "",
            emailSender = sender,
            emailSubject = subject,
            emailCount = emailCount,
            accentToneKey = "email",
        )
    }

    private fun tryBuildSportsScoreScene(
        sentence: String,
        norm: String,
        index: Int,
        total: Int,
        beat: AiSceneBeat?,
    ): NarrationSceneCard? {
        val sportKeywords = listOf(
            "mac", "skor", "gol", "derbi", "futbol", "basketbol", "besiktas",
            "fenerbahce", "galatasaray", "trabzon", "stadyum", "sampiyon",
            "galibiyet", "maglubiyet", "berabere", "penalti", "devre"
        )
        val hasSportKeyword = sportKeywords.any { norm.contains(it) } ||
            beat?.cardType?.equals("sports_score", ignoreCase = true) == true
        if (!hasSportKeyword) return null

        // Ignore clock strings like "saat 20:00"
        val cleanedForScore = CLOCK_CONTEXT_REGEX.replace(sentence, " ")
        val scoreMatch = SCORE_REGEX.find(cleanedForScore)
        val visualKey = when {
            norm.contains("besiktas") || norm.contains("kartal") -> "eagle_crest"
            norm.contains("stadyum") || norm.contains("arena") || norm.contains("saha") -> "stadium"
            norm.contains("kupa") || norm.contains("sampiyon") -> "trophy_cup"
            else -> "ball_sport"
        }

        val metric = if (scoreMatch != null) {
            "${scoreMatch.groupValues[1]} - ${scoreMatch.groupValues[2]}"
        } else {
            beat?.highlightMetric?.takeIf { it.isNotBlank() }.orEmpty()
        }
        val unit = if (scoreMatch != null) {
            "SKOR"
        } else {
            beat?.highlightLabel?.takeIf { it.isNotBlank() } ?: "MAÇ DETAYI"
        }

        val title = beat?.title?.takeIf { it.isNotBlank() }
            ?: when (visualKey) {
                "eagle_crest" -> "BEŞİKTAŞ // KARŞILAŞMA"
                "stadium" -> "STADYUM // MAÇ"
                "trophy_cup" -> "ŞAMPİYONLUK // KUPA"
                else -> "SPOR // SKOR"
            }

        return NarrationSceneCard(
            sentenceIndex = index,
            totalSentences = total,
            sentenceText = sentence,
            cardType = if (metric.isNotBlank()) SceneCardType.SPORTS_SCORE else SceneCardType.TOPIC_VISUAL,
            visualKey = visualKey,
            categoryBadge = "SPOR // CANLI VERİ",
            title = title,
            subtitle = condenseSummary(sentence),
            highlightMetric = metric,
            highlightUnit = if (metric.isNotBlank()) unit else "",
            accentToneKey = "sport",
        )
    }

    private fun tryBuildIncidentOrEventScene(
        sentence: String,
        norm: String,
        index: Int,
        total: Int,
        beat: AiSceneBeat?,
    ): NarrationSceneCard? {
        val incidentKeywords = listOf(
            "kavga", "catisma", "saldiri", "kaza", "olay", "gerilim", "yarali",
            "olu", "can kaybi", "gozalti", "tutukl", "yangin", "deprem",
            "patlama", "mudahale", "darbe", "yumruk", "siddet", "tehlike"
        )
        val hasIncident = incidentKeywords.any { norm.contains(it) } ||
            beat?.cardType?.equals("metric_event", ignoreCase = true) == true
        if (!hasIncident) return null

        val incidentMatch = INCIDENT_METRIC_REGEX.find(sentence)
        val generalMatch = if (incidentMatch == null) GENERAL_NUMBER_UNIT_REGEX.find(sentence) else null

        val metricNumber = when {
            incidentMatch != null -> incidentMatch.groupValues[1]
            generalMatch != null -> generalMatch.value.trim()
            else -> beat?.highlightMetric?.takeIf { it.isNotBlank() }.orEmpty()
        }
        val metricUnit = when {
            incidentMatch != null -> incidentMatch.groupValues[2].uppercase(trLocale)
            generalMatch != null -> beat?.highlightLabel?.takeIf { it.isNotBlank() }?.uppercase(trLocale) ?: "DETAY"
            else -> beat?.highlightLabel?.takeIf { it.isNotBlank() }?.uppercase(trLocale).orEmpty()
        }

        val visualKey = when {
            norm.contains("yangin") || norm.contains("alev") || norm.contains("patlama") -> "flame_energy"
            norm.contains("kavga") || norm.contains("catisma") || norm.contains("yumruk") ||
                norm.contains("darbe") || norm.contains("saldiri") || norm.contains("yarali") -> "fist_impact"
            else -> "shield_alert"
        }

        val badge = when (visualKey) {
            "fist_impact" -> "OLAY // ÇATIŞMA"
            "flame_energy" -> "ACİL // YANGIN"
            else -> "OLAY // RAPOR"
        }
        val title = beat?.title?.takeIf { it.isNotBlank() }
            ?: when {
                norm.contains("yarali") || norm.contains("kavga") || norm.contains("catisma") -> "Olay & Hasar Özeti"
                norm.contains("deprem") -> "Deprem Raporu"
                norm.contains("kaza") -> "Kaza Bilgisi"
                else -> "Gelişme Detayı"
            }

        return NarrationSceneCard(
            sentenceIndex = index,
            totalSentences = total,
            sentenceText = sentence,
            cardType = SceneCardType.METRIC_EVENT,
            visualKey = visualKey,
            categoryBadge = badge,
            title = title,
            subtitle = condenseSummary(sentence),
            highlightMetric = metricNumber,
            highlightUnit = metricUnit,
            accentToneKey = "alert",
        )
    }

    private fun tryBuildFinanceScene(
        sentence: String,
        norm: String,
        index: Int,
        total: Int,
        beat: AiSceneBeat?,
    ): NarrationSceneCard? {
        val financeKeywords = listOf(
            "dolar", "euro", "borsa", "altin", "faiz", "enflasyon", "butce",
            "fiyat", "ekonomi", "kur", "lira", "kripto", "bitcoin"
        )
        val hasFinance = financeKeywords.any { norm.contains(it) } ||
            sentence.contains('₺') || sentence.contains('$') || sentence.contains('€') ||
            beat?.cardType?.equals("finance", ignoreCase = true) == true
        if (!hasFinance) return null

        val match = FINANCE_METRIC_REGEX.find(sentence)?.value?.trim()
            ?: beat?.highlightMetric?.takeIf { it.isNotBlank() }.orEmpty()

        return NarrationSceneCard(
            sentenceIndex = index,
            totalSentences = total,
            sentenceText = sentence,
            cardType = SceneCardType.FINANCE_CARD,
            visualKey = "money_chart",
            categoryBadge = "EKONOMİ // VERİ",
            title = beat?.title?.takeIf { it.isNotBlank() } ?: "Piyasa & Finans Özeti",
            subtitle = condenseSummary(sentence),
            highlightMetric = match,
            highlightUnit = if (match.isNotBlank()) "DEĞER" else "",
            accentToneKey = "finance",
        )
    }

    private fun tryBuildWeatherScene(
        sentence: String,
        norm: String,
        index: Int,
        total: Int,
        beat: AiSceneBeat?,
    ): NarrationSceneCard? {
        val weatherKeywords = listOf(
            "hava durumu", "sicaklik", "derece", "yagmur", "firtina", "kar ",
            "ruzgar", "gunesli", "parcali bulutlu", "nem orani"
        )
        val hasWeather = weatherKeywords.any { norm.contains(it) } ||
            sentence.contains("°C", ignoreCase = true) ||
            beat?.cardType?.equals("weather", ignoreCase = true) == true
        if (!hasWeather) return null

        val tempMatch = TEMP_REGEX.find(sentence)?.groupValues?.getOrNull(1)?.let { "${it}°C" }
            ?: beat?.highlightMetric?.takeIf { it.isNotBlank() }.orEmpty()
        val isStorm = norm.contains("yagmur") || norm.contains("firtina") || norm.contains("kar") || norm.contains("simsek")

        return NarrationSceneCard(
            sentenceIndex = index,
            totalSentences = total,
            sentenceText = sentence,
            cardType = SceneCardType.WEATHER_CARD,
            visualKey = if (isStorm) "weather_storm" else "weather_sun",
            categoryBadge = "METEOROLOJİ // HAVA",
            title = beat?.title?.takeIf { it.isNotBlank() } ?: if (isStorm) "Yağışlı Hava" else "Hava Durumu",
            subtitle = condenseSummary(sentence),
            highlightMetric = tempMatch,
            highlightUnit = if (tempMatch.isNotBlank()) "SICAKLIK" else "",
            accentToneKey = "weather",
        )
    }

    private fun tryBuildMediaScene(
        sentence: String,
        norm: String,
        index: Int,
        total: Int,
        actionIntent: ActionIntent?,
        beat: AiSceneBeat?,
    ): NarrationSceneCard? {
        val mediaKeywords = listOf("spotify", "youtube", "tiktok", "muzik", "sarki", "video", "oynat", "caliyor")
        val isMedia = actionIntent == ActionIntent.OPEN_AND_PLAY_CONTENT ||
            actionIntent == ActionIntent.SEARCH_YOUTUBE ||
            mediaKeywords.any { norm.contains(it) } ||
            beat?.cardType?.equals("media", ignoreCase = true) == true
        if (!isMedia) return null

        val platform = when {
            norm.contains("spotify") -> "SPOTIFY"
            norm.contains("youtube") -> "YOUTUBE"
            norm.contains("tiktok") -> "TIKTOK"
            else -> "MEDYA"
        }
        val quoted = QUOTED_REGEX.find(sentence)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        return NarrationSceneCard(
            sentenceIndex = index,
            totalSentences = total,
            sentenceText = sentence,
            cardType = SceneCardType.MEDIA_CARD,
            visualKey = "music_wave",
            categoryBadge = "$platform // OYNATMA",
            title = quoted.ifBlank { beat?.title?.takeIf { it.isNotBlank() } ?: "Medya Akışı" },
            subtitle = condenseSummary(sentence),
            accentToneKey = "media",
        )
    }

    private fun tryBuildSystemScene(
        sentence: String,
        norm: String,
        index: Int,
        total: Int,
        actionIntent: ActionIntent?,
        beat: AiSceneBeat?,
    ): NarrationSceneCard? {
        val detectedIntent = when {
            actionIntent != null && actionIntent != ActionIntent.NONE -> actionIntent
            norm.contains("apk") || norm.contains("indir") -> ActionIntent.WEB_DOWNLOAD_AGENT
            norm.contains("hatirlat") || norm.contains("zamanlay") -> ActionIntent.SET_REMINDER
            norm.contains("mesaj") && (norm.contains("gonder") || norm.contains("at") || norm.contains("yaz")) -> ActionIntent.SEND_MESSAGE
            norm.contains("pil") || norm.contains("sarj") || norm.contains("batarya") -> ActionIntent.BATTERY_STATUS
            norm.contains("fener") || norm.contains("flas") -> ActionIntent.TORCH_CONTROL
            norm.contains("not defter") || norm.contains("ekran notu") -> ActionIntent.CAPTURE_SCREEN_NOTE
            else -> null
        } ?: return null

        val (visualKey, badge, defaultTitle) = when (detectedIntent) {
            ActionIntent.WEB_DOWNLOAD_AGENT -> Triple("download_file", "WEB AJANI // İNDİRME", "Dosya İndirme")
            ActionIntent.SEND_MESSAGE -> Triple("message_send", "MESAJ // ANLIK İLETİM", "Mesaj Gönderildi")
            ActionIntent.SET_REMINDER -> Triple("timer_clock", "ZAMANLAYICI // HATIRLATICI", "Hatırlatıcı Kuruldu")
            ActionIntent.CAPTURE_SCREEN_NOTE -> Triple("note_scroll", "ARŞİV // NOT DEFTERİ", "Ekran Notu")
            ActionIntent.TORCH_CONTROL -> Triple("flashlight_beam", "DONANIM // FENER", "El Feneri")
            ActionIntent.BATTERY_STATUS -> Triple("battery_power", "GÜÇ // BATARYA", "Batarya Durumu")
            ActionIntent.AIR_GESTURE_CONTROL -> Triple("brain_spark", "KAMERA // EL KONTROLÜ", "El Hareketi Modu")
            ActionIntent.OPEN_SYSTEM_SETTINGS -> Triple("code_terminal", "SİSTEM // AYARLAR", "Sistem Ayarı")
            else -> Triple("rocket_launch", "SİSTEM // İŞLEM", detectedIntent.displayLabel)
        }

        val metric = GENERAL_NUMBER_UNIT_REGEX.find(sentence)?.value?.trim()
            ?: beat?.highlightMetric?.takeIf { it.isNotBlank() }.orEmpty()

        return NarrationSceneCard(
            sentenceIndex = index,
            totalSentences = total,
            sentenceText = sentence,
            cardType = SceneCardType.SYSTEM_STATUS,
            visualKey = visualKey,
            categoryBadge = badge,
            title = beat?.title?.takeIf { it.isNotBlank() } ?: defaultTitle,
            subtitle = condenseSummary(sentence),
            highlightMetric = metric,
            highlightUnit = if (metric.isNotBlank()) "DURUM" else "",
            accentToneKey = "system",
        )
    }

    private fun buildGeneralOrMetricScene(
        sentence: String,
        norm: String,
        index: Int,
        total: Int,
        actionIntent: ActionIntent?,
        aiCues: List<String>,
        beat: AiSceneBeat?,
    ): NarrationSceneCard {
        val cuesForSentence = VisualCueResolver.resolveCues(
            aiCues = emptyList(),
            text = sentence,
            actionIntent = null,
        )
        val resolvedVisual = beat?.cueKey?.takeIf { it.isNotBlank() }
            ?: cuesForSentence.firstOrNull()?.key
            ?: aiCues.getOrNull(index % aiCues.size.coerceAtLeast(1))?.takeIf { it.isNotBlank() }
            ?: VisualCueResolver.resolveCues(aiCues, sentence, actionIntent).firstOrNull()?.key
            ?: "brain_spark"

        val numberMatch = GENERAL_NUMBER_UNIT_REGEX.find(sentence)?.value?.trim()
            ?: beat?.highlightMetric?.takeIf { it.isNotBlank() }.orEmpty()

        val cardType = if (numberMatch.isNotBlank()) SceneCardType.METRIC_EVENT else SceneCardType.TOPIC_VISUAL
        val badge = cuesForSentence.firstOrNull()?.label
            ?: beat?.highlightLabel?.takeIf { it.isNotBlank() }?.uppercase(trLocale)
            ?: "CANLI // BAĞLAM"

        val title = beat?.title?.takeIf { it.isNotBlank() }
            ?: extractHeadlinePhrase(sentence)

        return NarrationSceneCard(
            sentenceIndex = index,
            totalSentences = total,
            sentenceText = sentence,
            cardType = cardType,
            visualKey = resolvedVisual,
            categoryBadge = badge,
            title = title,
            subtitle = condenseSummary(sentence),
            highlightMetric = numberMatch,
            highlightUnit = if (numberMatch.isNotBlank()) {
                beat?.highlightLabel?.takeIf { it.isNotBlank() }?.uppercase(trLocale) ?: "VERİ"
            } else {
                ""
            },
            accentToneKey = if (norm.contains("hata") || norm.contains("uyari")) "alert" else "neutral",
        )
    }

    private fun extractHeadlinePhrase(sentence: String): String {
        val clean = sentence.trim().trimEnd('.', '!', '?')
        if (clean.length <= 42) return clean
        val words = clean.split(Regex("""\s+""")).take(5)
        return words.joinToString(" ") + "…"
    }

    private fun condenseSummary(sentence: String): String {
        val clean = sentence.trim()
        return if (clean.length <= 135) {
            clean
        } else {
            clean.take(132).substringBeforeLast(' ') + "…"
        }
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

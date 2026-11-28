package com.example.model

import java.util.Locale

data class VisualCueItem(
    val key: String,
    val label: String,
)

object VisualCueResolver {

    private val KEYWORD_CUES: List<Pair<List<String>, VisualCueItem>> = listOf(
        listOf("gmail", "e-posta", "eposta", "mail", "gelen kutusu", "outlook") to
            VisualCueItem("email_inbox", "GMAIL // E-POSTA"),
        listOf("besiktas", "beşiktaş", "kartal", "siyah beyaz", "bjk") to
            VisualCueItem("eagle_crest", "BEŞİKTAŞ // KARTAL"),
        listOf("stadyum", "stat", "saha", "tribun", "tribün", "arena", "vodafone", "tupras", "tüpraş") to
            VisualCueItem("stadium", "STADYUM // SAHA"),
        listOf("kavga", "catisma", "çatışma", "tartisma", "tartışma", "gerilim", "olay", "yumruk") to
            VisualCueItem("fist_impact", "GERİLİM // OLAY"),
        listOf("mac", "maç", "gol", "derbi", "futbol", "penalti", "penaltı", "fenerbahce", "galatasaray", "trabzon") to
            VisualCueItem("ball_sport", "MAÇ // FUTBOL"),
        listOf("kupa", "sampiyon", "şampiyon", "zafer", "madalya", "odul", "ödül") to
            VisualCueItem("trophy_cup", "ŞAMPİYONLUK // KUPA"),
        listOf("indir", "apk", "dosya", "zip", "pdf", "download", "kurulum") to
            VisualCueItem("download_file", "DOSYA // İNDİRME"),
        listOf("web", "site", "sayfa", "tarayici", "tarayıcı", "url", "baglanti", "bağlantı", "internet") to
            VisualCueItem("globe_network", "WEB AJANI // AĞ"),
        listOf("guvenlik", "güvenlik", "onay", "uyari", "uyarı", "supheli", "şüpheli", "risk", "hata", "kilit") to
            VisualCueItem("shield_alert", "GÜVENLİK // KONTROL"),
        listOf("mesaj", "tiktok", "whatsapp", "instagram", "telegram", "sohbet", "gonder", "gönder", "yanit") to
            VisualCueItem("message_send", "MESAJ // İLETİM"),
        listOf("saat", "alarm", "zaman", "dakika", "saniye", "hatirlat", "hatırlat", "takvim") to
            VisualCueItem("timer_clock", "ZAMANLAYICI"),
        listOf("firtina", "fırtına", "yagmur", "yağmur", "kar", "simsek", "şimşek", "ruzgar", "rüzgar") to
            VisualCueItem("weather_storm", "HAVA // FIRTINA"),
        listOf("gunes", "güneş", "sicak", "sıcak", "hava durumu", "ilık", "açık hava") to
            VisualCueItem("weather_sun", "HAVA // GÜNEŞLİ"),
        listOf("roket", "uzay", "uydu", "mars", "ucus", "uçuş", "fırlat", "nasa") to
            VisualCueItem("rocket_launch", "YÖRÜNGE // HIZ"),
        listOf("yapay zeka", "analiz", "strateji", "fikir", "noral", "nöral", "gemini", "bilim") to
            VisualCueItem("brain_spark", "NÖRAL // ANALİZ"),
        listOf("ekonomi", "dolar", "euro", "borsa", "altin", "altın", "butce", "bütçe", "fiyat", "para") to
            VisualCueItem("money_chart", "EKONOMİ // VERİ"),
        listOf("muzik", "müzik", "sarki", "şarkı", "ses", "radyo", "spotify", "dinle") to
            VisualCueItem("music_wave", "SES // FREKANS"),
        listOf("kod", "yazilim", "yazılım", "kotlin", "python", "terminal", "sunucu", "api") to
            VisualCueItem("code_terminal", "KOD // SİSTEM"),
        listOf("fener", "flas", "flaş", "isik", "ışık", "el feneri") to
            VisualCueItem("flashlight_beam", "DONANIM // FENER"),
        listOf("pil", "sarj", "şarj", "batarya", "enerji", "guc", "güç") to
            VisualCueItem("battery_power", "GÜÇ // BATARYA"),
        listOf("not", "arsiv", "arşiv", "ekran", "kayit", "kayıt") to
            VisualCueItem("note_scroll", "ARŞİV // NOT"),
        listOf("saglik", "sağlık", "kalp", "nabiz", "nabız", "spor", "doktor") to
            VisualCueItem("heart_health", "SAĞLIK // NABIZ"),
        listOf("ates", "ateş", "sicaklik", "sıcaklık", "yangin", "yangın", "tutku") to
            VisualCueItem("flame_energy", "ENERJİ // SICAKLIK"),
    )

    private val CANONICAL_LABELS = mapOf(
        "email_inbox" to "GMAIL // E-POSTA",
        "eagle_crest" to "BEŞİKTAŞ // KARTAL",
        "stadium" to "STADYUM // SAHA",
        "fist_impact" to "GERİLİM // OLAY",
        "ball_sport" to "MAÇ // FUTBOL",
        "trophy_cup" to "ŞAMPİYONLUK // KUPA",
        "download_file" to "DOSYA // İNDİRME",
        "globe_network" to "WEB AJANI // AĞ",
        "shield_alert" to "GÜVENLİK // KONTROL",
        "message_send" to "MESAJ // İLETİM",
        "timer_clock" to "ZAMANLAYICI",
        "weather_storm" to "HAVA // FIRTINA",
        "weather_sun" to "HAVA // GÜNEŞLİ",
        "rocket_launch" to "YÖRÜNGE // HIZ",
        "brain_spark" to "NÖRAL // ANALİZ",
        "money_chart" to "EKONOMİ // VERİ",
        "music_wave" to "SES // FREKANS",
        "code_terminal" to "KOD // SİSTEM",
        "flashlight_beam" to "DONANIM // FENER",
        "battery_power" to "GÜÇ // BATARYA",
        "note_scroll" to "ARŞİV // NOT",
        "heart_health" to "SAĞLIK // NABIZ",
        "flame_energy" to "ENERJİ // SICAKLIK",
    )

    fun resolveCues(
        aiCues: List<String>,
        text: String,
        actionIntent: ActionIntent? = null,
    ): List<VisualCueItem> {
        val result = LinkedHashMap<String, VisualCueItem>()

        // 1. Add AI-suggested cues first (mapped to canonical vector scenes)
        for (raw in aiCues) {
            val mapped = mapSingleCueKey(raw)
            if (mapped != null && !result.containsKey(mapped.key)) {
                result[mapped.key] = mapped
            }
            if (result.size >= 4) break
        }

        // 2. Scan narrative text for rich Turkish/English scene concepts in order of appearance
        val normText = normalize(text)
        if (normText.isNotBlank() && result.size < 4) {
            val orderedMatches = mutableListOf<Pair<Int, VisualCueItem>>()
            for ((keywords, cue) in KEYWORD_CUES) {
                var earliestIdx = Int.MAX_VALUE
                for (kw in keywords) {
                    val idx = normText.indexOf(kw)
                    if (idx >= 0 && idx < earliestIdx) {
                        earliestIdx = idx
                    }
                }
                if (earliestIdx != Int.MAX_VALUE) {
                    orderedMatches.add(earliestIdx to cue)
                }
            }
            orderedMatches.sortedBy { it.first }.forEach { (_, cue) ->
                if (result.size < 4 && !result.containsKey(cue.key)) {
                    result[cue.key] = cue
                }
            }
        }

        // 3. Action-based deterministic cue if needed
        if (result.isEmpty() && actionIntent != null) {
            val fallback = when (actionIntent) {
                ActionIntent.WEB_DOWNLOAD_AGENT -> VisualCueItem("download_file", "DOSYA // İNDİRME")
                ActionIntent.SEND_MESSAGE -> VisualCueItem("message_send", "MESAJ // İLETİM")
                ActionIntent.SET_REMINDER -> VisualCueItem("timer_clock", "ZAMANLAYICI")
                ActionIntent.CAPTURE_SCREEN_NOTE -> VisualCueItem("note_scroll", "ARŞİV // NOT")
                ActionIntent.TORCH_CONTROL -> VisualCueItem("flashlight_beam", "DONANIM // FENER")
                ActionIntent.BATTERY_STATUS -> VisualCueItem("battery_power", "GÜÇ // BATARYA")
                ActionIntent.OPEN_URL -> VisualCueItem("globe_network", "WEB AJANI // AĞ")
                ActionIntent.SEARCH_YOUTUBE, ActionIntent.VOLUME_CONTROL ->
                    VisualCueItem("music_wave", "SES // FREKANS")
                ActionIntent.OPEN_SYSTEM_SETTINGS -> VisualCueItem("code_terminal", "KOD // SİSTEM")
                ActionIntent.TAP_COORDINATE -> VisualCueItem("shield_alert", "HEDEF // OTOMASYON")
                ActionIntent.OPEN_APP, ActionIntent.GO_HOME ->
                    VisualCueItem("rocket_launch", "KOMUT // BAŞLAT")
                ActionIntent.OPEN_NEWS_BRIEFING, ActionIntent.NEWS_CONTROL ->
                    VisualCueItem("globe_network", "GÜNDEM // YAYIN")
                ActionIntent.AIR_GESTURE_CONTROL ->
                    VisualCueItem("brain_spark", "EL KONTROLÜ // KAMERA")
                ActionIntent.OPEN_AND_PLAY_CONTENT ->
                    VisualCueItem("music_wave", "MEDYA // İÇERİK")
                ActionIntent.NONE -> null
            }
            if (fallback != null) {
                result[fallback.key] = fallback
            }
        }

        // 4. General analytical cue if speaking general knowledge with no keyword hit
        if (result.isEmpty() && text.isNotBlank()) {
            result["brain_spark"] = VisualCueItem("brain_spark", "NÖRAL // ANLATIM")
        }

        return result.values.toList().take(4)
    }

    private fun mapSingleCueKey(raw: String): VisualCueItem? {
        val clean = normalize(raw).replace(' ', '_')
        if (clean.isBlank()) return null

        CANONICAL_LABELS[clean]?.let { label ->
            return VisualCueItem(clean, label)
        }

        for ((keywords, cue) in KEYWORD_CUES) {
            if (keywords.any { clean.contains(normalize(it)) }) {
                return cue
            }
        }

        return VisualCueItem(
            key = clean,
            label = raw.trim().replace('_', ' ').uppercase(Locale("tr", "TR")).take(22),
        )
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

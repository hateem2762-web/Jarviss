package com.example.system

import java.util.Locale

data class ExtractedWhatsAppMessage(
    val sourcePackage: String,
    val senderName: String,
    val messageText: String,
    val timestampMillis: Long,
)

object WhatsAppMessageFilter {

    val WHATSAPP_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

    private val urgentKeywords = setOf(
        "acil",
        "hemen",
        "cabuk",
        "çabuk",
        "kaza",
        "hastane",
        "polis",
        "ambulans",
        "ara",
        "arayin",
        "arayın",
        "yardim",
        "yardım",
        "onemli",
        "önemli",
        "iptal",
        "toplantı",
        "toplanti",
        "fatura",
        "odeme",
        "ödeme",
        "banka",
        "sifre",
        "şifre",
        "kod",
        "ucus",
        "uçuş",
        "sinav",
        "sınav",
        "son",
        "vakit",
        "urgent",
        "asap",
        "call",
        "sos",
    )

    private val trivialWords = setOf(
        "ok",
        "okay",
        "okey",
        "okdir",
        "k",
        "kk",
        "tamam",
        "tamamdir",
        "tamamdır",
        "tmm",
        "tm",
        "olur",
        "oldu",
        "peki",
        "eyv",
        "eyvallah",
        "sagol",
        "sağol",
        "saol",
        "tesekkurler",
        "teşekkürler",
        "tesekkur",
        "teşekkür",
        "tsk",
        "tşk",
        "rica",
        "evet",
        "evt",
        "hayir",
        "hayır",
        "yok",
        "aynen",
        "he",
        "ha",
        "hi",
        "hı",
        "hmm",
        "hm",
        "hmmm",
        "hehe",
        "haha",
        "hahaha",
        "hahahaha",
        "lol",
        "lmao",
        "sjsj",
        "sjsjsj",
        "asdf",
        "slm",
        "selam",
        "mrb",
        "merhaba",
        "nbr",
        "iyiyim",
        "iyi",
        "guzel",
        "güzel",
        "super",
        "süper",
        "harika",
        "bay",
        "by",
        "bye",
        "bb",
        "gorusuruz",
        "görüşürüz",
        "byee",
        "amin",
        "insallah",
        "inşallah",
        "masallah",
        "maşallah",
    )

    private val systemNoisePatterns = listOf(
        Regex("^\\d+\\s+(yeni\\s+)?mesaj.*$", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\s+sohbetten\\s+\\d+\\s+mesaj.*$", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\s+new\\s+messages?.*$", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\s+messages?\\s+from\\s+\\d+\\s+chats?.*$", RegexOption.IGNORE_CASE),
        Regex(".*mesajlar\\s+kontrol\\s+ediliyor.*", RegexOption.IGNORE_CASE),
        Regex(".*checking\\s+for\\s+new\\s+messages.*", RegexOption.IGNORE_CASE),
        Regex(".*whatsapp\\s+web.*aktif.*", RegexOption.IGNORE_CASE),
        Regex(".*yedekleme.*", RegexOption.IGNORE_CASE),
        Regex(".*backing\\s+up\\s+messages.*", RegexOption.IGNORE_CASE),
        Regex(".*gelen\\s+sesli\\s+arama.*", RegexOption.IGNORE_CASE),
        Regex(".*cevapsız\\s+(sesli|görüntülü)\\s+arama.*", RegexOption.IGNORE_CASE),
        Regex("^bu\\s+mesaj\\s+silindi\\.?$", RegexOption.IGNORE_CASE),
        Regex("^this\\s+message\\s+was\\s+deleted\\.?$", RegexOption.IGNORE_CASE),
    )

    private val pureLaughOrSmashRegex = Regex(
        "^(ha|he|hi|sj|as|df|kd|wq|xo)+[haesjkdwqxo!?.]*$",
        RegexOption.IGNORE_CASE
    )

    fun isWhatsAppPackage(packageName: String?): Boolean {
        return packageName != null && packageName in WHATSAPP_PACKAGES
    }

    fun isSystemOrSummaryNoise(senderName: String, messageText: String): Boolean {
        val cleanSender = senderName.trim()
        val cleanMsg = messageText.trim()
        if (cleanSender.isBlank() || cleanMsg.isBlank()) return true

        val lowerSender = cleanSender.lowercase(Locale("tr", "TR"))
        if (lowerSender == "whatsapp" || lowerSender == "whatsapp business") {
            return true
        }

        return systemNoisePatterns.any { it.matches(cleanMsg) }
    }

    fun shouldAnalyzeWithAi(senderName: String, messageText: String): Boolean {
        if (isSystemOrSummaryNoise(senderName, messageText)) return false

        val cleanMsg = messageText.trim()
        val normalized = CoordinateMatcher.normalize(cleanMsg)
        if (normalized.isBlank()) return false

        // Always allow urgent keywords even in very short messages (e.g. "Acil!", "Ara!")
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        if (tokens.any { it in urgentKeywords }) {
            return true
        }

        // Ignore pure punctuation/emojis or ultra-short meaningless strings
        val letterCount = normalized.count { it.isLetterOrDigit() }
        if (letterCount < 3) return false

        // Ignore single trivial acknowledgment/chatter word or repeated trivial tokens ("tamam tamam")
        if (tokens.all { it in trivialWords || pureLaughOrSmashRegex.matches(it) }) {
            return false
        }

        // Ignore short 1-token messages without question mark or urgency (e.g., "geldim" vs "geliyorum?")
        if (tokens.size == 1 && normalized.length <= 4 && !cleanMsg.contains('?')) {
            return false
        }

        return true
    }
}

package com.example.model

import java.util.Locale

enum class JarvisDestination {
    ASSISTANT,
    NOTES,
    COORDINATES,
    REMINDERS,
    SETTINGS,
}

enum class ActionIntent(val wireValue: String, val displayLabel: String) {
    OPEN_APP("open_app", "Uygulama Aç"),
    OPEN_URL("open_url", "Bağlantı Aç"),
    SEARCH_YOUTUBE("search_youtube", "YouTube Ara"),
    SET_REMINDER("set_reminder", "Hatırlatıcı Kur"),
    GO_HOME("go_home", "Ana Ekrana Dön"),
    TAP_COORDINATE("tap_coordinate", "Koordinata Dokun"),
    TORCH_CONTROL("torch_control", "Fener Kontrolü"),
    VOLUME_CONTROL("volume_control", "Ses Kontrolü"),
    BATTERY_STATUS("battery_status", "Pil Durumu"),
    OPEN_SYSTEM_SETTINGS("open_system_settings", "Sistem Ayarı"),
    CAPTURE_SCREEN_NOTE("capture_screen_note", "Ekranı Not Al"),
    OPEN_NEWS_BRIEFING("open_news_briefing", "Gündem Bülteni"),
    NEWS_CONTROL("news_control", "Haber Kontrolü"),
    WEB_DOWNLOAD_AGENT("web_download_agent", "Web İndirme Ajanı"),
    SCHEDULE_MESSAGE("schedule_message", "Zamanlı Mesaj"),
    AIR_GESTURE_CONTROL("air_gesture_control", "El Kontrolü"),
    NONE("none", "Yanıt");

    companion object {
        fun fromWire(value: String?): ActionIntent {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == normalized } ?: NONE
        }
    }
}

enum class ActionExecutionStatus {
    PENDING,
    EXECUTED,
    FAILED,
}

data class JarvisAction(
    val intent: ActionIntent,
    val target: String = "",
    val minutes: Int? = null,
    val seconds: Int? = null,
    val message: String = "",
) {
    fun totalDelayMillis(): Long {
        val mins = (minutes ?: 0).coerceAtLeast(0)
        val secs = (seconds ?: 0).coerceAtLeast(0)
        return if (mins == 0 && secs == 0) {
            10 * 60_000L
        } else {
            mins * 60_000L + secs * 1_000L
        }
    }

    fun formattedDurationLabel(): String {
        val mins = (minutes ?: 0).coerceAtLeast(0)
        val secs = (seconds ?: 0).coerceAtLeast(0)
        return when {
            mins >= 60 && mins % 60 == 0 && secs == 0 -> "${mins / 60} sa"
            mins >= 60 && secs == 0 -> "${mins / 60} sa ${mins % 60} dk"
            mins > 0 && secs > 0 -> "$mins dk $secs sn"
            mins > 0 -> "$mins dk"
            secs > 0 -> "$secs sn"
            else -> "10 dk"
        }
    }

    fun toPrettyJson(): String {
        val escapedTarget = escapeJson(target)
        val escapedMessage = escapeJson(message)
        val minutesLiteral = minutes?.toString() ?: "null"
        val secondsLiteral = seconds?.toString() ?: "null"
        return """
            {
              "type": "action",
              "intent": "${intent.wireValue}",
              "target": "$escapedTarget",
              "minutes": $minutesLiteral,
              "seconds": $secondsLiteral,
              "message": "$escapedMessage"
            }
        """.trimIndent()
    }

    private fun escapeJson(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}

data class JarvisMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val action: JarvisAction? = null,
    val actionStatus: ActionExecutionStatus = ActionExecutionStatus.PENDING,
    val actionStatusDetail: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
)

data class ReminderItem(
    val id: Long,
    val message: String,
    val triggerAtMillis: Long,
    val durationLabel: String,
    val isCompleted: Boolean,
    val createdAtMillis: Long,
)

data class TapCoordinateItem(
    val id: Long,
    val profilePackageName: String,
    val profileName: String,
    val label: String,
    val xPercent: Float,
    val yPercent: Float,
    val xPx: Int,
    val yPx: Int,
    val createdAtMillis: Long,
)

data class ProactiveAlertItem(
    val id: Long,
    val sourcePackage: String,
    val senderName: String,
    val originalMessage: String,
    val summary: String,
    val importanceScore: Int,
    val importanceReason: String,
    val draftReply: String,
    val messageTimestampMillis: Long,
    val createdAtMillis: Long,
    val isDismissed: Boolean = false,
)

data class NoteItem(
    val id: Long,
    val title: String,
    val content: String,
    val sourcePackage: String,
    val sourceAppName: String,
    val screenshotPath: String = "",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class ScheduledMessageRuleItem(
    val id: Long,
    val targetAppPackage: String,
    val targetAppName: String,
    val recipients: List<String>,
    val messageText: String,
    val triggerHour: Int,
    val triggerMinute: Int,
    val repeatDaily: Boolean,
    val isEnabled: Boolean,
    val nextTriggerAtMillis: Long,
    val lastRunAtMillis: Long?,
    val lastStatus: String,
    val createdAtMillis: Long,
) {
    fun formattedTime(): String = String.format(Locale.ROOT, "%02d:%02d", triggerHour, triggerMinute)
}

data class ScheduledMessageLogItem(
    val id: Long,
    val ruleId: Long,
    val targetAppName: String,
    val recipientsSummary: String,
    val status: String,
    val detail: String,
    val executedAtMillis: Long,
)

enum class JarvisMood(val wireValue: String) {
    NEUTRAL("neutral"),
    SERIOUS("serious"),
    ANALYTICAL("analytical"),
    ENERGETIC("energetic"),
    ALERT("alert");

    companion object {
        fun fromWire(value: String?): JarvisMood {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == normalized } ?: NEUTRAL
        }
    }
}

enum class JarvisVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR,
}

enum class AirGestureScope(val wireValue: String, val label: String) {
    ALL_APPS("all_apps", "Tüm Uygulamalar"),
    VIDEO_FEEDS_ONLY("video_feeds", "Sadece TikTok / Video Akışları");

    companion object {
        fun fromWire(value: String?): AirGestureScope {
            val clean = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == clean } ?: ALL_APPS
        }
    }
}

enum class AirGestureHorizontalAction(val wireValue: String, val label: String) {
    SWIPE("swipe", "Yatay Kaydırma (Sağ / Sol)"),
    BACK_NEXT("back_next", "Geri Git (Sol) / Sağa Kaydır");

    companion object {
        fun fromWire(value: String?): AirGestureHorizontalAction {
            val clean = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == clean } ?: SWIPE
        }
    }
}

enum class AirGestureSensitivity(val wireValue: String, val label: String) {
    EASY("easy", "Kolay (Hafif Hareket)"),
    BALANCED("balanced", "Dengeli"),
    PRECISE("precise", "Hassas (Büyük Hareket)");

    companion object {
        fun fromWire(value: String?): AirGestureSensitivity {
            val clean = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == clean } ?: EASY
        }
    }
}

enum class AirGestureTwoFingerAction(val wireValue: String, val label: String) {
    DOUBLE_TAP_LIKE("like", "Beğen (Çift Tık)"),
    PLAY_PAUSE("play_pause", "Oynat / Duraklat"),
    BACK("back", "Geri Git");

    companion object {
        fun fromWire(value: String?): AirGestureTwoFingerAction {
            val clean = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == clean } ?: DOUBLE_TAP_LIKE
        }
    }
}

enum class QuickMessageTimingMode {
    IMMEDIATE,
    DELAY_MINUTES,
    CLOCK_TIME,
}

data class QuickMessageDraft(
    val id: Long = System.currentTimeMillis(),
    val appName: String = "WhatsApp",
    val appPackage: String = "com.whatsapp",
    val recipientsText: String = "",
    val messageText: String = "",
    val timingMode: QuickMessageTimingMode = QuickMessageTimingMode.IMMEDIATE,
    val delayMinutes: Int = 5,
    val hour: Int = 23,
    val minute: Int = 0,
    val repeatDaily: Boolean = false,
)

object HudTopicResolver {
    fun resolveHeader(
        isListening: Boolean,
        isProcessing: Boolean,
        hasError: Boolean,
        hasProactiveAlert: Boolean,
        latestActionIntent: ActionIntent?,
        aiTopicTitle: String,
        webAgentHeader: String = "",
    ): String {
        return when {
            hasError -> "SİSTEM UYARISI // KRİTİK"
            isListening -> "SES KANALI // DİNLENİYOR"
            webAgentHeader.isNotBlank() -> webAgentHeader.trim().uppercase(Locale("tr", "TR"))
            isProcessing -> "NÖRAL ÇEKİRDEK // ANALİZ"
            hasProactiveAlert -> "İLETİŞİM PROTOKOLÜ // TASLAK"
            latestActionIntent == ActionIntent.CAPTURE_SCREEN_NOTE -> "GÖRSEL TARAMA // ARŞİV KAYDI"
            latestActionIntent == ActionIntent.TAP_COORDINATE -> "HEDEF KİLİTLEME // OTOMASYON"
            latestActionIntent == ActionIntent.SET_REMINDER -> "ZAMANLAYICI // HATIRLATMA"
            latestActionIntent == ActionIntent.SCHEDULE_MESSAGE -> "OTOMASYON // ZAMANLI MESAJ"
            latestActionIntent == ActionIntent.WEB_DOWNLOAD_AGENT -> "WEB AJANI // İNDİRME"
            latestActionIntent == ActionIntent.AIR_GESTURE_CONTROL -> "EL KONTROLÜ // KAMERA"
            latestActionIntent == ActionIntent.OPEN_NEWS_BRIEFING ||
                latestActionIntent == ActionIntent.NEWS_CONTROL -> "JARVIS // GÜNDEM"
            latestActionIntent in setOf(
                ActionIntent.BATTERY_STATUS,
                ActionIntent.TORCH_CONTROL,
                ActionIntent.VOLUME_CONTROL,
                ActionIntent.OPEN_SYSTEM_SETTINGS,
            ) -> "SİSTEM DURUMU // ANALİZ"
            aiTopicTitle.isNotBlank() -> aiTopicTitle.trim().uppercase(Locale("tr", "TR"))
            latestActionIntent in setOf(
                ActionIntent.OPEN_APP,
                ActionIntent.OPEN_URL,
                ActionIntent.SEARCH_YOUTUBE,
                ActionIntent.GO_HOME,
            ) -> "KOMUT İCRASI // YÖNLENDİRME"
            else -> "ÇEKİRDEK HAZIR // BEKLEMEDE"
        }
    }

    fun moodForAction(intent: ActionIntent, fallbackMood: JarvisMood): JarvisMood {
        return when (intent) {
            ActionIntent.CAPTURE_SCREEN_NOTE,
            ActionIntent.BATTERY_STATUS,
            ActionIntent.WEB_DOWNLOAD_AGENT -> JarvisMood.ANALYTICAL
            ActionIntent.TAP_COORDINATE,
            ActionIntent.TORCH_CONTROL,
            ActionIntent.SEARCH_YOUTUBE,
            ActionIntent.AIR_GESTURE_CONTROL -> JarvisMood.ENERGETIC
            ActionIntent.SET_REMINDER,
            ActionIntent.SCHEDULE_MESSAGE,
            ActionIntent.OPEN_SYSTEM_SETTINGS,
            ActionIntent.VOLUME_CONTROL,
            ActionIntent.OPEN_NEWS_BRIEFING,
            ActionIntent.NEWS_CONTROL -> JarvisMood.SERIOUS
            else -> fallbackMood
        }
    }
}


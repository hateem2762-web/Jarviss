package com.example.model

enum class JarvisDestination {
    ASSISTANT,
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

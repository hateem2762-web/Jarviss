package com.example.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ai.JarvisRepository
import com.example.ai.configureAppCheck
import com.example.data.local.JarvisDatabase
import com.example.data.local.ProactiveAlertEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ProactiveAlertItem
import com.example.overlay.JarvisOverlayService
import com.example.system.ExtractedWhatsAppMessage
import com.example.system.WhatsAppMessageFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "JarvisNotifListener"
private const val DEDUP_WINDOW_MS = 25_000L

class JarvisNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefsRepo: UserPreferencesRepository
    private val aiRepository = JarvisRepository()

    private val recentProcessedMessages = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > 64
    }

    override fun onCreate() {
        super.onCreate()
        configureAppCheck(this, null)
        prefsRepo = UserPreferencesRepository(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!WhatsAppMessageFilter.isWhatsAppPackage(sbn.packageName)) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val category = notification.category
        if (
            category == Notification.CATEGORY_CALL ||
            category == Notification.CATEGORY_PROGRESS ||
            category == Notification.CATEGORY_SERVICE
        ) {
            return
        }

        val extracted = extractWhatsAppMessage(sbn, notification) ?: return
        if (!WhatsAppMessageFilter.shouldAnalyzeWithAi(extracted.senderName, extracted.messageText)) {
            Log.d(TAG, "Filtered trivial/noise WhatsApp message from ${extracted.senderName}")
            return
        }

        val dedupKey = "${extracted.sourcePackage}|${extracted.senderName}|${extracted.messageText}"
        val now = System.currentTimeMillis()
        synchronized(recentProcessedMessages) {
            val lastSeen = recentProcessedMessages[dedupKey]
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
                return
            }
            recentProcessedMessages[dedupKey] = now
        }

        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true && action.actionIntent != null
        }
        val contentIntent = notification.contentIntent

        serviceScope.launch {
            try {
                val prefs = prefsRepo.preferences.first()
                if (!prefs.proactiveAlertsEnabled) return@launch

                val evaluationResult = aiRepository.evaluateWhatsAppMessage(
                    senderName = extracted.senderName,
                    messageText = extracted.messageText,
                    userContext = prefs.userContext,
                )

                evaluationResult.onSuccess { eval ->
                    Log.d(
                        TAG,
                        "WhatsApp scored ${eval.importanceScore}/${prefs.importanceThreshold} from ${extracted.senderName}"
                    )
                    if (eval.importanceScore < prefs.importanceThreshold) {
                        return@onSuccess
                    }

                    val dao = JarvisDatabase.getInstance(applicationContext).jarvisDao()
                    val entity = ProactiveAlertEntity(
                        sourcePackage = extracted.sourcePackage,
                        senderName = extracted.senderName,
                        originalMessage = extracted.messageText,
                        summary = eval.summary,
                        importanceScore = eval.importanceScore,
                        importanceReason = eval.importanceReason,
                        draftReply = eval.draftReply,
                        messageTimestampMillis = extracted.timestampMillis,
                        createdAtMillis = System.currentTimeMillis(),
                        isDismissed = false,
                    )
                    val insertedId = dao.insertProactiveAlert(entity)

                    ProactiveAlertActionCache.put(
                        alertId = insertedId,
                        contentIntent = contentIntent,
                        replyAction = replyAction,
                    )

                    val domainItem = ProactiveAlertItem(
                        id = insertedId,
                        sourcePackage = entity.sourcePackage,
                        senderName = entity.senderName,
                        originalMessage = entity.originalMessage,
                        summary = entity.summary,
                        importanceScore = entity.importanceScore,
                        importanceReason = entity.importanceReason,
                        draftReply = entity.draftReply,
                        messageTimestampMillis = entity.messageTimestampMillis,
                        createdAtMillis = entity.createdAtMillis,
                        isDismissed = false,
                    )

                    ProactiveAlertNotifier.showProactiveAlertNotification(
                        context = applicationContext,
                        alert = domainItem,
                    )

                    if (
                        Settings.canDrawOverlays(applicationContext) &&
                        (prefs.floatingBubbleEnabled || prefs.persistentTriggerEnabled || prefs.wakeWordEnabled)
                    ) {
                        JarvisOverlayService.showProactiveAlert(
                            context = applicationContext,
                            alertId = insertedId,
                        )
                    }
                }.onFailure { err ->
                    Log.w(TAG, "WhatsApp importance evaluation failed: ${err.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling WhatsApp notification", e)
            }
        }
    }

    private fun extractWhatsAppMessage(
        sbn: StatusBarNotification,
        notification: Notification,
    ): ExtractedWhatsAppMessage? {
        val extras = notification.extras ?: return null

        val messagingStyle = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (_: Exception) {
            null
        }

        if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
            val latest = messagingStyle.messages.last()
            val rawText = latest.text?.toString()?.trim().orEmpty()
            val personName = latest.person?.name?.toString()?.trim().orEmpty()
            val conversationTitle = messagingStyle.conversationTitle?.toString()?.trim().orEmpty()
            val fallbackTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()

            val sender = when {
                messagingStyle.isGroupConversation &&
                    conversationTitle.isNotBlank() &&
                    personName.isNotBlank() &&
                    !personName.equals(conversationTitle, ignoreCase = true) ->
                    "$personName ($conversationTitle)"
                personName.isNotBlank() -> personName
                conversationTitle.isNotBlank() -> conversationTitle
                else -> fallbackTitle
            }

            if (sender.isBlank() || rawText.isBlank()) return null
            return ExtractedWhatsAppMessage(
                sourcePackage = sbn.packageName,
                senderName = sender,
                messageText = rawText,
                timestampMillis = latest.timestamp.takeIf { it > 0L } ?: sbn.postTime,
            )
        }

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val regularText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val messageText = bigText.ifBlank { regularText }

        if (sender.isBlank() || messageText.isBlank()) return null
        return ExtractedWhatsAppMessage(
            sourcePackage = sbn.packageName,
            senderName = sender,
            messageText = messageText,
            timestampMillis = sbn.postTime,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (context.packageName in enabledPackages) return true

            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            val expected = ComponentName(context, JarvisNotificationListenerService::class.java)
            return flat.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == expected
            }
        }

        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

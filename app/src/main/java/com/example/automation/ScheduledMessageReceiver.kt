package com.example.automation

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.accessibility.JarvisAccessibilityService
import com.example.data.local.JarvisDatabase
import com.example.data.local.NoteJsonStore
import com.example.data.local.ScheduledMessageLogEntity
import com.example.data.local.ScheduledMessageRuleEntity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ScheduledMsgReceiver"

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        if (ruleId <= 0L) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                executeRule(appContext, ruleId)
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled message execution failed for rule $ruleId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_RULE_ID = "extra_scheduled_rule_id"

        suspend fun executeRule(context: Context, ruleId: Long): String {
            val db = JarvisDatabase.getInstance(context)
            val dao = db.jarvisDao()
            val noteStore = NoteJsonStore(context, dao)
            val rule = dao.getScheduledRuleById(ruleId) ?: return "Zamanlı mesaj kuralı bulunamadı."
            val now = System.currentTimeMillis()
            val recipients = rule.recipientsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }

            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isLocked = (keyguardManager?.isKeyguardLocked == true) ||
                (powerManager?.isInteractive == false)

            if (isLocked) {
                val detail = "Cihaz ekranı kilitli/kapalı olduğu için ${rule.targetAppName} mesaj otomasyonu çalıştırılamadı."
                recordOutcome(context, dao, noteStore, rule, "ATLANDI (KİLİTLİ EKRAN)", detail, now, writeNote = true)
                return detail
            }

            val a11yService = JarvisAccessibilityService.getInstance()
            if (a11yService == null) {
                val detail = "JARVIS Erişilebilirlik Servisi açık olmadığı için ${rule.targetAppName} mesajı gönderilemedi."
                recordOutcome(context, dao, noteStore, rule, "BAŞARISIZ (ERİŞİLEBİLİRLİK KAPALI)", detail, now, writeNote = true)
                return detail
            }

            val report = a11yService.executeScheduledMessageAutomation(
                targetPackage = rule.targetAppPackage,
                targetAppName = rule.targetAppName,
                recipients = recipients,
                messageText = rule.messageText,
            )

            val statusLabel = when {
                report.success -> "BAŞARILI (${report.sentCount}/${report.totalCount})"
                report.sentCount > 0 -> "KISMİ (${report.sentCount}/${report.totalCount})"
                else -> "BAŞARISIZ"
            }

            recordOutcome(
                context = context,
                dao = dao,
                noteStore = noteStore,
                rule = rule,
                statusLabel = statusLabel,
                detail = report.summary,
                nowMillis = now,
                writeNote = !report.success,
            )
            return report.summary
        }

        private suspend fun recordOutcome(
            context: Context,
            dao: com.example.data.local.JarvisDao,
            noteStore: NoteJsonStore,
            rule: ScheduledMessageRuleEntity,
            statusLabel: String,
            detail: String,
            nowMillis: Long,
            writeNote: Boolean,
        ) {
            val nextTrigger = if (rule.repeatDaily) {
                val tomorrow = ScheduledMessageScheduler.computeNextTriggerMillis(
                    hour = rule.triggerHour,
                    minute = rule.triggerMinute,
                    nowMillis = nowMillis + TimeUnit.MINUTES.toMillis(2),
                )
                ScheduledMessageScheduler.scheduleRule(context, rule.id, tomorrow)
                tomorrow
            } else {
                0L
            }

            dao.updateScheduledRuleStatus(
                id = rule.id,
                lastRunAtMillis = nowMillis,
                lastStatus = statusLabel,
                isEnabled = rule.repeatDaily,
                nextTriggerAtMillis = nextTrigger,
            )

            dao.insertScheduledLog(
                ScheduledMessageLogEntity(
                    ruleId = rule.id,
                    targetAppName = rule.targetAppName,
                    recipientsSummary = rule.recipientsCsv,
                    status = statusLabel,
                    detail = detail,
                    executedAtMillis = nowMillis,
                )
            )

            if (writeNote) {
                noteStore.saveNote(
                    title = "Zamanlı Mesaj Uyarısı (${rule.targetAppName})",
                    content = "Saat: %02d:%02d\nKişiler: ${rule.recipientsCsv}\nMesaj: \"${rule.messageText}\"\nDurum: $statusLabel\nDetay: $detail"
                        .format(rule.triggerHour, rule.triggerMinute),
                    sourcePackage = rule.targetAppPackage,
                    sourceAppName = rule.targetAppName,
                    screenshotBitmap = null,
                )
            }

            postNotification(
                context = context,
                ruleId = rule.id,
                title = "${rule.targetAppName} Zamanlı Mesaj · $statusLabel",
                body = detail,
            )
        }

        private fun postNotification(
            context: Context,
            ruleId: Long,
            title: String,
            body: String,
        ) {
            ScheduledMessageScheduler.ensureNotificationChannel(context)
            val openIntent = PendingIntent.getActivity(
                context,
                (300_000 + ruleId.toInt()),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, ScheduledMessageScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_jarvis_tile)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()

            try {
                context.getSystemService(NotificationManager::class.java)
                    ?.notify(300_000 + ruleId.toInt(), notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission missing", e)
            }
        }
    }
}

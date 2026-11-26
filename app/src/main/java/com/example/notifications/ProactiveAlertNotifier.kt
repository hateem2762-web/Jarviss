package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ProactiveAlertItem

object ProactiveAlertNotifier {

    const val CHANNEL_ID = "jarvis_proactive_alerts_channel"
    const val EXTRA_PROACTIVE_ALERT_ID = "extra_proactive_alert_id"
    const val EXTRA_EDIT_MODE = "extra_proactive_edit_mode"
    private const val NOTIFICATION_BASE_ID = 8200

    fun notificationIdFor(alertId: Long): Int =
        NOTIFICATION_BASE_ID + (alertId % 1000).toInt()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.proactive_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.proactive_channel_desc)
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun showProactiveAlertNotification(
        context: Context,
        alert: ProactiveAlertItem,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)
        val notifId = notificationIdFor(alert.id)

        val openMainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PROACTIVE_ALERT_ID, alert.id)
            putExtra(EXTRA_EDIT_MODE, true)
        }
        val contentPending = PendingIntent.getActivity(
            context,
            notifId,
            openMainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyIntent = Intent(context, ProactiveAlertActionReceiver::class.java).apply {
            action = ProactiveAlertActionReceiver.ACTION_COPY_DRAFT
            putExtra(ProactiveAlertActionReceiver.EXTRA_ALERT_ID, alert.id)
            putExtra(ProactiveAlertActionReceiver.EXTRA_DRAFT_TEXT, alert.draftReply)
        }
        val copyPending = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openWhatsAppIntent = Intent(context, ProactiveAlertActionReceiver::class.java).apply {
            action = ProactiveAlertActionReceiver.ACTION_OPEN_WHATSAPP
            putExtra(ProactiveAlertActionReceiver.EXTRA_ALERT_ID, alert.id)
            putExtra(ProactiveAlertActionReceiver.EXTRA_SOURCE_PACKAGE, alert.sourcePackage)
            putExtra(ProactiveAlertActionReceiver.EXTRA_DRAFT_TEXT, alert.draftReply)
        }
        val openWhatsAppPending = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 2,
            openWhatsAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = buildString {
            append("Özet: ").append(alert.summary)
            append("\nNeden önemli: ").append(alert.importanceReason)
            append("\n\nHazır Cevap Taslağı:\n\"").append(alert.draftReply).append("\"")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis_tile)
            .setContentTitle("${alert.senderName} • Önem ${alert.importanceScore}/10")
            .setContentText(alert.summary)
            .setSubText("JARVIS Proaktif Taslak")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("${alert.senderName} (Önem: ${alert.importanceScore}/10)")
                    .bigText(bodyText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .addAction(
                android.R.drawable.ic_menu_save,
                "Kopyala",
                copyPending
            )
            .addAction(
                android.R.drawable.ic_menu_edit,
                "Düzenle",
                contentPending
            )
            .addAction(
                android.R.drawable.ic_menu_send,
                "WhatsApp'ta Aç",
                openWhatsAppPending
            )

        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    fun cancelAlertNotification(context: Context, alertId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(alertId))
    }
}

package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.local.JarvisDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ProactiveAlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getLongExtra(EXTRA_ALERT_ID, -1L)
        val draftText = intent.getStringExtra(EXTRA_DRAFT_TEXT).orEmpty()
        val sourcePkg = intent.getStringExtra(EXTRA_SOURCE_PACKAGE).orEmpty()

        when (intent.action) {
            ACTION_COPY_DRAFT -> {
                if (draftText.isNotBlank()) {
                    ProactiveAlertActionCache.copyToClipboard(context, draftText)
                    Toast.makeText(context, "Hazır cevap panoya kopyalandı.", Toast.LENGTH_SHORT).show()
                }
            }

            ACTION_OPEN_WHATSAPP -> {
                val opened = ProactiveAlertActionCache.openWhatsAppChat(
                    context = context,
                    alertId = alertId,
                    sourcePackage = sourcePkg,
                    draftToCopy = draftText,
                )
                if (opened) {
                    Toast.makeText(
                        context,
                        "Taslak kopyalandı, WhatsApp açılıyor…",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, "WhatsApp açılamadı.", Toast.LENGTH_SHORT).show()
                }
                if (alertId > 0L) {
                    ProactiveAlertNotifier.cancelAlertNotification(context, alertId)
                }
            }

            ACTION_DISMISS_ALERT -> {
                if (alertId > 0L) {
                    ProactiveAlertNotifier.cancelAlertNotification(context, alertId)
                    ProactiveAlertActionCache.remove(alertId)
                    val pendingResult = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            JarvisDatabase.getInstance(context).jarvisDao().dismissProactiveAlert(alertId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_COPY_DRAFT = "com.example.action.PROACTIVE_COPY_DRAFT"
        const val ACTION_OPEN_WHATSAPP = "com.example.action.PROACTIVE_OPEN_WHATSAPP"
        const val ACTION_DISMISS_ALERT = "com.example.action.PROACTIVE_DISMISS_ALERT"

        const val EXTRA_ALERT_ID = "extra_alert_id"
        const val EXTRA_DRAFT_TEXT = "extra_draft_text"
        const val EXTRA_SOURCE_PACKAGE = "extra_source_package"
    }
}

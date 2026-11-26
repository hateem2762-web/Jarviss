package com.example.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ProactiveActionCache"

data class CachedWhatsAppHandles(
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?,
)

object ProactiveAlertActionCache {

    private val handles = ConcurrentHashMap<Long, CachedWhatsAppHandles>()

    fun put(
        alertId: Long,
        contentIntent: PendingIntent?,
        replyAction: Notification.Action?,
    ) {
        if (handles.size > 40) {
            val oldest = handles.keys.sorted().take(15)
            oldest.forEach { handles.remove(it) }
        }
        handles[alertId] = CachedWhatsAppHandles(
            contentIntent = contentIntent,
            replyAction = replyAction,
        )
    }

    fun hasDirectReply(alertId: Long): Boolean {
        val action = handles[alertId]?.replyAction ?: return false
        return action.remoteInputs?.isNotEmpty() == true && action.actionIntent != null
    }

    fun copyToClipboard(context: Context, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("JARVIS WhatsApp Taslak", clean))
    }

    fun sendDirectReply(context: Context, alertId: Long, replyText: String): Boolean {
        val cleanReply = replyText.trim()
        if (cleanReply.isEmpty()) return false
        val action = handles[alertId]?.replyAction ?: return false
        val remoteInputs = action.remoteInputs ?: return false
        val pendingIntent = action.actionIntent ?: return false
        if (remoteInputs.isEmpty()) return false

        return try {
            val resultsBundle = Bundle()
            for (remoteInput in remoteInputs) {
                resultsBundle.putCharSequence(remoteInput.resultKey, cleanReply)
            }
            val fillInIntent = Intent()
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)
            pendingIntent.send(context, 0, fillInIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct RemoteInput reply failed; caller can open WhatsApp chat instead", e)
            false
        }
    }

    fun openWhatsAppChat(
        context: Context,
        alertId: Long,
        sourcePackage: String,
        draftToCopy: String?,
    ): Boolean {
        if (!draftToCopy.isNullOrBlank()) {
            copyToClipboard(context, draftToCopy)
        }

        val cachedIntent = handles[alertId]?.contentIntent
        if (cachedIntent != null) {
            try {
                cachedIntent.send()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Cached WhatsApp contentIntent failed; falling back to package launch", e)
            }
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(sourcePackage)
            ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else {
            false
        }
    }

    fun remove(alertId: Long) {
        handles.remove(alertId)
    }
}

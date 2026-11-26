package com.example.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.example.R
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ReminderScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inProcessJobs = ConcurrentHashMap<Long, Job>()
    private val pendingIds = ConcurrentHashMap.newKeySet<Long>()

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.reminder_channel_name)
            val desc = context.getString(R.string.reminder_channel_desc)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                ReminderReceiver.CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = desc
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 200, 350)
                setSound(soundUri, audioAttrs)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun markFiredIfPending(reminderId: Long): Boolean {
        if (reminderId == 0L) return true
        inProcessJobs.remove(reminderId)?.cancel()
        return pendingIds.remove(reminderId)
    }

    fun scheduleReminder(
        context: Context,
        reminderId: Long,
        triggerAtMillis: Long,
        message: String,
    ) {
        ensureNotificationChannel(context)
        val appContext = context.applicationContext
        val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
        pendingIds.add(reminderId)

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderReceiver.EXTRA_REMINDER_MESSAGE, message)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetTime = System.currentTimeMillis() + delayMs
        alarmManager?.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            targetTime,
            pendingIntent
        )

        // Exact in-process precision timer for short reminders (e.g., 10 seconds) so Doze batching never delays them
        if (delayMs <= 5 * 60_000L) {
            inProcessJobs.remove(reminderId)?.cancel()
            inProcessJobs[reminderId] = scope.launch {
                delay(delayMs)
                if (pendingIds.contains(reminderId)) {
                    appContext.sendBroadcast(
                        Intent(appContext, ReminderReceiver::class.java).apply {
                            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
                            putExtra(ReminderReceiver.EXTRA_REMINDER_MESSAGE, message)
                        }
                    )
                }
            }
        }
    }

    fun cancelReminder(context: Context, reminderId: Long) {
        pendingIds.remove(reminderId)
        inProcessJobs.remove(reminderId)?.cancel()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

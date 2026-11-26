package com.example.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.MainActivity
import com.example.R
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ReminderScheduler"

object ReminderScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inProcessJobs = ConcurrentHashMap<Long, Job>()
    private val firedIds = ConcurrentHashMap.newKeySet<Long>()

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

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to open exact alarm settings", e)
            }
        }
    }

    fun markFiredIfPending(reminderId: Long): Boolean {
        inProcessJobs.remove(reminderId)?.cancel()
        if (reminderId == 0L) return true
        return firedIds.add(reminderId)
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
        firedIds.remove(reminderId)

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

        val showIntent = PendingIntent.getActivity(
            appContext,
            (reminderId + 50_000L).toInt(),
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetTime = System.currentTimeMillis() + delayMs
        try {
            if (alarmManager != null) {
                val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()
                if (canExact) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(targetTime, showIntent),
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetTime,
                        pendingIntent
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing; falling back to setAndAllowWhileIdle", e)
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTime,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "AlarmManager scheduling failed", e)
        }

        // Exact in-process precision timer (up to 60 minutes) to guarantee exact second/minute delivery while app or overlay service is alive
        if (delayMs <= 60 * 60_000L) {
            inProcessJobs.remove(reminderId)?.cancel()
            inProcessJobs[reminderId] = scope.launch {
                delay(delayMs)
                if (!firedIds.contains(reminderId)) {
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
        firedIds.add(reminderId)
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

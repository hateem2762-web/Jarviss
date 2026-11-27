package com.example.automation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import java.util.Calendar

private const val TAG = "ScheduledMsgScheduler"

object ScheduledMessageScheduler {
    const val CHANNEL_ID = "jarvis_scheduled_messages"
    private const val REQUEST_CODE_OFFSET = 200_000

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Zamanlı Otomatik Mesajlar",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Zamanlanan otomatik mesaj görevlerinin başlangıç ve sonuç bildirimleri"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun computeNextTriggerMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMillis + 5_000L) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun scheduleRule(
        context: Context,
        ruleId: Long,
        triggerAtMillis: Long,
    ) {
        ensureNotificationChannel(context)
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(appContext, ScheduledMessageReceiver::class.java).apply {
            putExtra(ScheduledMessageReceiver.EXTRA_RULE_ID, ruleId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_OFFSET + ruleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val showIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_OFFSET + 50_000 + ruleId.toInt(),
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val targetTime = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 2_000L)
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(targetTime, showIntent),
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTime,
                    pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied; falling back to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTime,
                pendingIntent,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule message rule #$ruleId", e)
        }
    }

    fun cancelRule(context: Context, ruleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduledMessageReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_OFFSET + ruleId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

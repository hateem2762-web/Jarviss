package com.example.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.model.ActionExecutionStatus
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "DeviceControlManager"

class DeviceControlManager(private val context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    @Volatile
    private var torchEnabled: Boolean = false

    @Volatile
    private var torchCallbackRegistered: Boolean = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            torchEnabled = enabled
        }
    }

    private fun ensureTorchCallbackRegistered() {
        if (torchCallbackRegistered) return
        try {
            cameraManager?.registerTorchCallback(torchCallback, null)
            torchCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register TorchCallback", e)
        }
    }

    fun controlFlashlight(rawMode: String): ActionExecutionResult {
        val cm = cameraManager ?: return ActionExecutionResult(
            status = ActionExecutionStatus.FAILED,
            detail = "Bu cihazda kamera/fener servisine erişilemedi."
        )
        ensureTorchCallbackRegistered()

        val flashCameraId = try {
            val ids = cm.cameraIdList
            ids.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query camera flash", e)
            null
        }

        if (flashCameraId == null) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Cihazda kullanılabilir flaş/fener bulunamadı."
            )
        }

        val normalized = rawMode.trim().lowercase(Locale.ROOT)
        val nextState = when {
            normalized in setOf("on", "ac", "aç", "enable", "true", "1") -> true
            normalized in setOf("off", "kapat", "kapa", "disable", "false", "0") -> false
            normalized.contains("kapat") || normalized.contains("off") -> false
            normalized.contains("ac") || normalized.contains("aç") || normalized.contains("on") -> true
            else -> !torchEnabled
        }

        return try {
            cm.setTorchMode(flashCameraId, nextState)
            torchEnabled = nextState
            ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = if (nextState) "Fener açıldı." else "Fener kapatıldı."
            )
        } catch (e: Exception) {
            Log.e(TAG, "setTorchMode($nextState) failed", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Fener kontrol edilemedi: ${e.localizedMessage ?: "kamera başka bir uygulama tarafından kullanılıyor olabilir"}"
            )
        }
    }

    fun controlVolume(rawCommand: String): ActionExecutionResult {
        val am = audioManager ?: return ActionExecutionResult(
            status = ActionExecutionStatus.FAILED,
            detail = "Ses yöneticisine erişilemedi."
        )
        val norm = rawCommand.trim().lowercase(Locale.ROOT)
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')

        // Determine target stream
        val streamType = when {
            norm.contains("bildirim") || norm.contains("notification") -> AudioManager.STREAM_NOTIFICATION
            norm.contains("zil") || norm.contains("ring") || norm.contains("arama") -> AudioManager.STREAM_RING
            norm.contains("alarm") -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val streamLabel = when (streamType) {
            AudioManager.STREAM_NOTIFICATION -> "Bildirim"
            AudioManager.STREAM_RING -> "Zil"
            AudioManager.STREAM_ALARM -> "Alarm"
            else -> "Medya"
        }

        // Check percentage value e.g. "media:set:60" or "50"
        val percentMatch = Regex("""(\d{1,3})""").find(norm)?.groupValues?.get(1)?.toIntOrNull()
        val isPercentageTarget = percentMatch != null && (
            norm.contains("set") || norm.contains("%") || norm.contains("yuzde") || norm.contains("seviye")
            )

        return try {
            when {
                norm.contains("vibrate") || norm.contains("titresim") -> {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    ActionExecutionResult(
                        status = ActionExecutionStatus.EXECUTED,
                        detail = "Telefon titreşim moduna alındı."
                    )
                }

                norm.contains("unmute") || norm.contains("sesi ac") || norm.contains("sessizden cik") ||
                    norm.contains("normal") -> {
                    if (streamType == AudioManager.STREAM_RING || norm.contains("genel") || norm.contains("zil")) {
                        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                    am.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
                    if (am.getStreamVolume(streamType) == 0) {
                        am.setStreamVolume(streamType, (max * 0.4f).roundToInt().coerceAtLeast(1), AudioManager.FLAG_SHOW_UI)
                    }
                    val pct = (am.getStreamVolume(streamType) * 100f / max).roundToInt()
                    ActionExecutionResult(
                        status = ActionExecutionStatus.EXECUTED,
                        detail = "$streamLabel sesi açıldı (%$pct)."
                    )
                }

                norm.contains("mute") || norm.contains("sessiz") || norm.contains("sustur") -> {
                    if (streamType == AudioManager.STREAM_RING || norm.contains("telefon") || norm.contains("all")) {
                        if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted) {
                            am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                            openIntentSafely(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            return ActionExecutionResult(
                                status = ActionExecutionStatus.EXECUTED,
                                detail = "Titreşim moduna alındı. Tam sessiz için 'Rahatsız Etmeyin' erişim ayarları açıldı."
                            )
                        }
                        am.ringerMode = AudioManager.RINGER_MODE_SILENT
                        return ActionExecutionResult(
                            status = ActionExecutionStatus.EXECUTED,
                            detail = "Zil sesi sessize alındı."
                        )
                    }
                    am.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI)
                    ActionExecutionResult(
                        status = ActionExecutionStatus.EXECUTED,
                        detail = "$streamLabel sesi kapatıldı."
                    )
                }

                isPercentageTarget && percentMatch != null -> {
                    val clampedPct = percentMatch.coerceIn(0, 100)
                    val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
                    val targetIndex = (max * clampedPct / 100f).roundToInt().coerceIn(0, max)
                    am.setStreamVolume(streamType, targetIndex, AudioManager.FLAG_SHOW_UI)
                    ActionExecutionResult(
                        status = ActionExecutionStatus.EXECUTED,
                        detail = "$streamLabel sesi %$clampedPct seviyesine ayarlandı."
                    )
                }

                norm.contains("max") || norm.contains("fulle") || norm.contains("en yuksek") -> {
                    val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
                    am.setStreamVolume(streamType, max, AudioManager.FLAG_SHOW_UI)
                    ActionExecutionResult(
                        status = ActionExecutionStatus.EXECUTED,
                        detail = "$streamLabel sesi maksimuma (%100) getirildi."
                    )
                }

                norm.contains("down") || norm.contains("kis") || norm.contains("azalt") || norm.contains("dusur") -> {
                    val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
                    val current = am.getStreamVolume(streamType)
                    val step = (max / 6).coerceAtLeast(1)
                    val next = (current - step).coerceAtLeast(0)
                    am.setStreamVolume(streamType, next, AudioManager.FLAG_SHOW_UI)
                    val pct = (next * 100f / max).roundToInt()
                    ActionExecutionResult(
                        status = ActionExecutionStatus.EXECUTED,
                        detail = "$streamLabel sesi kısıldı (%$pct)."
                    )
                }

                else -> {
                    // Default: raise volume
                    val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
                    val current = am.getStreamVolume(streamType)
                    val step = (max / 6).coerceAtLeast(1)
                    val next = (current + step).coerceAtMost(max)
                    am.setStreamVolume(streamType, next, AudioManager.FLAG_SHOW_UI)
                    val pct = (next * 100f / max).roundToInt()
                    ActionExecutionResult(
                        status = ActionExecutionStatus.EXECUTED,
                        detail = "$streamLabel sesi artırıldı (%$pct)."
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Volume adjustment blocked by DND policy", e)
            openIntentSafely(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Sessiz mod değişikliği için 'Rahatsız Etmeyin' izni gerekiyor; ayar sayfası açıldı."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Audio control failed", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Ses ayarı değiştirilemedi."
            )
        }
    }

    fun getBatteryStatusSummary(): ActionExecutionResult {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val levelFromIntent = batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else -1
        } ?: -1

        val propCapacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val percentage = when {
            levelFromIntent in 0..100 -> levelFromIntent
            propCapacity in 0..100 -> propCapacity
            else -> -1
        }

        if (percentage < 0) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Pil bilgisi okunamadı."
            )
        }

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val sourceLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "priz (AC) ile"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB ile"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "kablosuz olarak"
            else -> ""
        }

        val stateText = when (status) {
            BatteryManager.BATTERY_STATUS_FULL -> "tam dolu"
            BatteryManager.BATTERY_STATUS_CHARGING -> {
                if (sourceLabel.isNotBlank()) "$sourceLabel şarj oluyor" else "şarj oluyor"
            }
            else -> "şarjda değil"
        }

        return ActionExecutionResult(
            status = ActionExecutionStatus.EXECUTED,
            detail = "Şarj seviyesi %$percentage, şu an $stateText."
        )
    }

    fun openSystemSettings(rawTarget: String): ActionExecutionResult {
        val norm = rawTarget.trim().lowercase(Locale.ROOT)
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')

        val (action, label) = when {
            norm.contains("wifi") || norm.contains("wi-fi") || norm.contains("internet") ->
                Settings.ACTION_WIFI_SETTINGS to "Wi-Fi ayarları"
            norm.contains("bluetooth") || norm.contains("bt") ->
                Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth ayarları"
            norm.contains("ses") || norm.contains("sound") || norm.contains("audio") ->
                Settings.ACTION_SOUND_SETTINGS to "Ses ayarları"
            norm.contains("erisilebilir") || norm.contains("accessibility") ->
                Settings.ACTION_ACCESSIBILITY_SETTINGS to "Erişilebilirlik ayarları"
            norm.contains("bildirim erisim") || norm.contains("notification listener") ->
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS to "Bildirim erişimi ayarları"
            norm.contains("bildirim") || norm.contains("notification") ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS to "Bildirim ayarları"
                } else {
                    Settings.ACTION_SETTINGS to "Sistem ayarları"
                }
            norm.contains("pil") || norm.contains("batarya") || norm.contains("battery") ->
                Intent.ACTION_POWER_USAGE_SUMMARY to "Pil ayarları"
            norm.contains("ekran") || norm.contains("parlaklik") || norm.contains("display") ->
                Settings.ACTION_DISPLAY_SETTINGS to "Ekran ayarları"
            norm.contains("konum") || norm.contains("location") ->
                Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Konum ayarları"
            else -> Settings.ACTION_SETTINGS to "Sistem ayarları"
        }

        val opened = if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.startActivity(
                    Intent(action).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                true
            } catch (_: Exception) {
                openIntentSafely(Settings.ACTION_SETTINGS)
            }
        } else {
            openIntentSafely(action) || openIntentSafely(Settings.ACTION_SETTINGS)
        }

        return if (opened) {
            ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "$label açıldı."
            )
        } else {
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "$label açılamadı."
            )
        }
    }

    private fun openIntentSafely(action: String): Boolean {
        return try {
            context.startActivity(
                Intent(action).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open intent $action", e)
            false
        }
    }
}

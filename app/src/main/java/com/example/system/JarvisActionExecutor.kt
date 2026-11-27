package com.example.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.accessibility.JarvisAccessibilityService
import com.example.ai.JarvisRepository
import com.example.automation.ScheduledMessageParser
import com.example.automation.ScheduledMessageScheduler
import com.example.data.local.CoordinateJsonStore
import com.example.data.local.JarvisDao
import com.example.data.local.NoteJsonStore
import com.example.data.local.ReminderEntity
import com.example.data.local.ScheduledMessageRuleEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ActionExecutionStatus
import com.example.model.ActionIntent
import com.example.model.AirGestureScope
import com.example.model.InstalledAppInfo
import com.example.model.JarvisAction
import com.example.model.TapCoordinateItem
import com.example.overlay.JarvisOverlayService
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "JarvisActionExecutor"

data class ActionExecutionResult(
    val status: ActionExecutionStatus,
    val detail: String,
)

class JarvisActionExecutor(
    private val context: Context,
    private val dao: JarvisDao,
    private val aiRepository: JarvisRepository = JarvisRepository(),
) {
    private val coordinateStore = CoordinateJsonStore(context, dao)
    private val noteStore = NoteJsonStore(context, dao)
    private val deviceControl = DeviceControlManager(context)
    private val prefsRepo = UserPreferencesRepository(context)

    suspend fun getInstalledLauncherApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val discovered = LinkedHashMap<String, InstalledAppInfo>()

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            }

            for (info in resolveInfos) {
                try {
                    val pkg = info.activityInfo?.packageName ?: continue
                    if (pkg == context.packageName) continue
                    val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                    if (label.isNotEmpty()) {
                        discovered.putIfAbsent(pkg, InstalledAppInfo(label = label, packageName = pkg))
                    }
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryIntentActivities failed", e)
        }

        try {
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }

            for (appInfo in installedApps) {
                try {
                    val pkg = appInfo.packageName
                    if (pkg == context.packageName || discovered.containsKey(pkg)) continue
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        val label = pm.getApplicationLabel(appInfo)?.toString()?.trim().orEmpty()
                        if (label.isNotEmpty()) {
                            discovered[pkg] = InstalledAppInfo(label = label, packageName = pkg)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getInstalledApplications failed", e)
        }

        discovered.values.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun resolveAppDisplayName(packageName: String): String {
        if (packageName.isBlank()) return CoordinateJsonStore.GENERAL_PROFILE_NAME
        if (packageName == CoordinateJsonStore.GENERAL_PROFILE_PACKAGE) {
            return CoordinateJsonStore.GENERAL_PROFILE_NAME
        }
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString().trim().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    suspend fun execute(
        action: JarvisAction,
        activeProfilePackage: String? = null,
        beforeTap: (suspend () -> Unit)? = null,
        afterTap: (suspend () -> Unit)? = null,
    ): ActionExecutionResult {
        return when (action.intent) {
            ActionIntent.OPEN_APP -> executeOpenApp(action.target)
            ActionIntent.OPEN_URL -> executeOpenUrl(action.target)
            ActionIntent.SEARCH_YOUTUBE -> executeSearchYoutube(action.target)
            ActionIntent.SET_REMINDER -> executeSetReminder(action)
            ActionIntent.GO_HOME -> executeGoHome()
            ActionIntent.TAP_COORDINATE -> executeTapCoordinateByLabel(
                labelTarget = action.target,
                activeProfilePackage = activeProfilePackage,
                beforeTap = beforeTap,
                afterTap = afterTap,
            )
            ActionIntent.TORCH_CONTROL -> deviceControl.controlFlashlight(action.target)
            ActionIntent.VOLUME_CONTROL -> deviceControl.controlVolume(action.target)
            ActionIntent.BATTERY_STATUS -> deviceControl.getBatteryStatusSummary()
            ActionIntent.OPEN_SYSTEM_SETTINGS -> deviceControl.openSystemSettings(action.target)
            ActionIntent.CAPTURE_SCREEN_NOTE -> executeCaptureScreenNote(
                beforeCapture = beforeTap,
                afterCapture = afterTap,
            )
            ActionIntent.OPEN_NEWS_BRIEFING -> ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "Gündem bülteni hazırlanıyor."
            )
            ActionIntent.NEWS_CONTROL -> ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "Haber kontrolü uygulandı."
            )
            ActionIntent.WEB_DOWNLOAD_AGENT -> ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "Web ajanı başlatıldı: ${action.target.ifBlank { "indirme görevi" }}"
            )
            ActionIntent.SCHEDULE_MESSAGE -> executeScheduleMessage(action)
            ActionIntent.AIR_GESTURE_CONTROL -> executeAirGestureControl(action.target)
            ActionIntent.NONE -> ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = ""
            )
        }
    }

    suspend fun executeCaptureScreenNote(
        beforeCapture: (suspend () -> Unit)? = null,
        afterCapture: (suspend () -> Unit)? = null,
    ): ActionExecutionResult {
        val service = JarvisAccessibilityService.getInstance()
        if (service == null) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Ekranı okuyabilmek için Ayarlar'dan 'JARVIS Dokunma Asistanı' erişilebilirlik servisini açmalısın."
            )
        }

        var capturedBitmap: Bitmap? = null
        return try {
            beforeCapture?.invoke()
            delay(170L)

            val capturedText = service.extractVisibleScreenText()
            val needsOcrScreenshot = capturedText.text.length < 40
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                capturedBitmap = service.captureScreenBitmap()
            }

            afterCapture?.invoke()

            val sourcePkg = capturedText.sourcePackage
                .ifBlank { JarvisAccessibilityService.currentForegroundPackage.value.orEmpty() }
            val sourceAppName = if (sourcePkg.isNotBlank()) {
                resolveAppDisplayName(sourcePkg)
            } else {
                "Ekran"
            }

            if (capturedText.text.isBlank() && capturedBitmap == null) {
                return ActionExecutionResult(
                    status = ActionExecutionStatus.FAILED,
                    detail = "Bu ekrandan okunabilir metin veya görüntü alınamadı (korumalı içerik olabilir)."
                )
            }

            val aiResult = if (needsOcrScreenshot || capturedText.text.length >= 20) {
                aiRepository.generateNoteFromScreen(
                    extractedText = capturedText.text,
                    screenshot = if (needsOcrScreenshot) capturedBitmap else null,
                    sourceAppName = sourceAppName,
                ).getOrNull()
            } else {
                null
            }

            val finalTitle = aiResult?.title?.takeIf { it.isNotBlank() }
                ?: "$sourceAppName Ekran Notu"
            val finalContent = aiResult?.content?.takeIf { it.isNotBlank() }
                ?: capturedText.text.ifBlank { "Ekran görüntüsü kaydedildi." }

            noteStore.saveNote(
                title = finalTitle,
                content = finalContent,
                sourcePackage = sourcePkg,
                sourceAppName = sourceAppName,
                screenshotBitmap = capturedBitmap,
            )

            ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "Not Defteri'ne kaydedildi: \"$finalTitle\" ($sourceAppName)"
            )
        } catch (e: Exception) {
            afterCapture?.invoke()
            Log.e(TAG, "Screen note capture failed", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Ekran notu alınamadı: ${e.localizedMessage ?: "hata"}"
            )
        } finally {
            try {
                capturedBitmap?.recycle()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun executeTapOnCoordinate(
        coordinate: TapCoordinateItem,
        beforeTap: (suspend () -> Unit)? = null,
        afterTap: (suspend () -> Unit)? = null,
    ): ActionExecutionResult {
        val service = JarvisAccessibilityService.getInstance()
        if (service == null) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Erişilebilirlik Servisi açık değil. Ayarlardan 'JARVIS Dokunma Asistanı'nı etkinleştir."
            )
        }

        return try {
            beforeTap?.invoke()
            delay(130L)
            val tapped = service.dispatchTapAtPercent(coordinate.xPercent, coordinate.yPercent)
            delay(100L)
            afterTap?.invoke()

            if (tapped) {
                val xPct = (coordinate.xPercent * 100).toInt()
                val yPct = (coordinate.yPercent * 100).toInt()
                ActionExecutionResult(
                    status = ActionExecutionStatus.EXECUTED,
                    detail = "\"${coordinate.label}\" (${coordinate.profileName}, %$xPct, %$yPct) noktasına dokunuldu."
                )
            } else {
                ActionExecutionResult(
                    status = ActionExecutionStatus.FAILED,
                    detail = "\"${coordinate.label}\" noktasına dokunma hareketi sistem tarafından iptal edildi."
                )
            }
        } catch (e: Exception) {
            afterTap?.invoke()
            Log.e(TAG, "Tap dispatch failed", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Dokunma başarısız: ${e.localizedMessage ?: "hata"}"
            )
        }
    }

    private suspend fun executeTapCoordinateByLabel(
        labelTarget: String,
        activeProfilePackage: String?,
        beforeTap: (suspend () -> Unit)?,
        afterTap: (suspend () -> Unit)?,
    ): ActionExecutionResult {
        val candidates = coordinateStore.getCoordinatesForActiveOrFallback(activeProfilePackage)
        val matched = CoordinateMatcher.findByModelLabel(labelTarget, candidates)
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "\"$labelTarget\" için kayıtlı ekran koordinatı bulunamadı."
            )
        return executeTapOnCoordinate(matched, beforeTap, afterTap)
    }

    private suspend fun executeOpenApp(target: String): ActionExecutionResult {
        val query = target.trim()
        if (query.isEmpty()) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Açılacak uygulama adı belirtilmedi."
            )
        }

        val apps = getInstalledLauncherApps()
        val matched = findBestAppMatch(query, apps)
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Cihazda \"$query\" uygulaması bulunamadı."
            )

        val launchIntent = context.packageManager.getLaunchIntentForPackage(matched.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "${matched.label} (${matched.packageName}) başlatılamadı."
            )

        return try {
            context.startActivity(launchIntent)
            ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "${matched.label} açıldı."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app ${matched.packageName}", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "${matched.label} açılamadı: ${e.localizedMessage ?: "hata"}"
            )
        }
    }

    private fun executeOpenUrl(rawTarget: String): ActionExecutionResult {
        val trimmed = rawTarget.trim()
        if (trimmed.isEmpty()) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Açılacak bağlantı belirtilmedi."
            )
        }
        val normalizedUrl = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "$normalizedUrl açılıyor."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL $normalizedUrl", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Bağlantı açılamadı: $normalizedUrl"
            )
        }
    }

    private fun executeSearchYoutube(query: String): ActionExecutionResult {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "YouTube arama sorgusu boş."
            )
        }
        val encoded = Uri.encode(cleaned)
        val youtubeUrl = "https://www.youtube.com/results?search_query=$encoded"
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "YouTube'da \"$cleaned\" aranıyor."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search YouTube for $cleaned", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "YouTube araması başlatılamadı."
            )
        }
    }

    private suspend fun executeSetReminder(action: JarvisAction): ActionExecutionResult {
        val reminderText = action.message.ifBlank { action.target }.ifBlank { "Hatırlatıcı" }
        val delayMillis = action.totalDelayMillis()
        val durationLabel = action.formattedDurationLabel()
        val triggerAt = System.currentTimeMillis() + delayMillis

        return try {
            val id = dao.insertReminder(
                ReminderEntity(
                    message = reminderText,
                    triggerAtMillis = triggerAt,
                    minutesFromNow = action.minutes,
                    secondsFromNow = action.seconds,
                    durationLabel = durationLabel,
                    isCompleted = false,
                )
            )
            ReminderScheduler.scheduleReminder(
                context = context,
                reminderId = id,
                triggerAtMillis = triggerAt,
                message = reminderText
            )
            ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "$durationLabel sonra için kuruldu: \"$reminderText\""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set reminder", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Hatırlatıcı kaydedilemedi."
            )
        }
    }

    private suspend fun executeScheduleMessage(action: JarvisAction): ActionExecutionResult {
        val rawTarget = action.target.trim()
        val apps = getInstalledLauncherApps()

        var appName = ""
        var appPackage = ""
        var recipients = emptyList<String>()
        var hour = 23
        var minute = 0
        var repeatDaily = true
        var messageText = action.message.trim().ifBlank { "." }

        if (rawTarget.contains("|")) {
            val parts = rawTarget.split("|")
            appName = parts.getOrNull(0).orEmpty().trim()
            appPackage = parts.getOrNull(1).orEmpty().trim()
            recipients = parts.getOrNull(2).orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }
            val hm = parts.getOrNull(3).orEmpty().split(":")
            hour = hm.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 23
            minute = hm.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            repeatDaily = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: true
        } else if (rawTarget.contains(":")) {
            // Gemini format: "<app>:<recipient1,recipient2>:<HH:MM>"
            val parts = rawTarget.split(":", limit = 3)
            val appHint = parts.getOrNull(0).orEmpty().trim()
            val resolved = ScheduledMessageParser.resolveKnownAppPackage(appHint, apps)
            appName = resolved?.first ?: appHint.ifBlank { "TikTok" }
            appPackage = resolved?.second ?: findBestAppMatch(appName, apps)?.packageName ?: "com.zhiliaoapp.musically"
            recipients = parts.getOrNull(1).orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }
            val timeParts = parts.getOrNull(2).orEmpty().split(":", ".")
            hour = timeParts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 23
            minute = timeParts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        } else {
            val parsed = ScheduledMessageParser.parse(rawTarget, apps)
            if (parsed != null) {
                appName = parsed.appName
                appPackage = parsed.appPackage
                recipients = parsed.recipients
                hour = parsed.hour
                minute = parsed.minute
                repeatDaily = parsed.repeatDaily
                if (action.message.isBlank()) {
                    messageText = parsed.messageText
                }
            }
        }

        if (recipients.isEmpty() || appPackage.isBlank()) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Zamanlı mesaj için uygulama, saat ve en az bir alıcı adı belirtilmeli (Alarmlar sekmesinden de ekleyebilirsin)."
            )
        }

        val triggerAt = ScheduledMessageScheduler.computeNextTriggerMillis(hour, minute)
        val timeFormatted = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
        val ruleId = dao.insertScheduledRule(
            ScheduledMessageRuleEntity(
                targetAppPackage = appPackage,
                targetAppName = appName,
                recipientsCsv = recipients.joinToString(", "),
                messageText = messageText,
                triggerHour = hour,
                triggerMinute = minute,
                repeatDaily = repeatDaily,
                isEnabled = true,
                nextTriggerAtMillis = triggerAt,
                lastStatus = "Zamanlandı ($timeFormatted)",
            )
        )
        ScheduledMessageScheduler.scheduleRule(context, ruleId, triggerAt)
        val a11yHint = if (JarvisAccessibilityService.getInstance() == null) {
            " (Not: Otomatik gönderim için Erişilebilirlik Servisini açık tut.)"
        } else {
            ""
        }
        return ActionExecutionResult(
            status = ActionExecutionStatus.EXECUTED,
            detail = "Zamanlı mesaj kuruldu: $timeFormatted · $appName -> ${recipients.joinToString(", ")} (\"$messageText\")$a11yHint"
        )
    }

    private suspend fun executeAirGestureControl(rawTarget: String): ActionExecutionResult {
        val target = rawTarget.trim().lowercase(Locale.ROOT)
        if (target == "off") {
            prefsRepo.setGlobalAirGestureEnabled(false)
            JarvisOverlayService.syncMode(context)
            return ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "Uzaktan el hareketi kontrolü kapatıldı; kamera serbest bırakıldı."
            )
        }

        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCamera) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Uzaktan el kontrolü için Kamera izni gerekli. Ayarlar'dan izin verip açabilirsin."
            )
        }

        when (target) {
            "on_video" -> prefsRepo.setGlobalAirGestureScope(AirGestureScope.VIDEO_FEEDS_ONLY)
            "on_all" -> prefsRepo.setGlobalAirGestureScope(AirGestureScope.ALL_APPS)
        }
        prefsRepo.setGlobalAirGestureEnabled(true)
        JarvisOverlayService.syncMode(context)

        val a11yHint = if (JarvisAccessibilityService.getInstance() == null) {
            " (Kaydırma için Erişilebilirlik Servisini açık tut.)"
        } else {
            ""
        }
        val scopeLabel = when (target) {
            "on_video" -> " (Sadece TikTok / Video Akışları)"
            "on_all" -> " (Tüm Uygulamalar)"
            else -> ""
        }
        return ActionExecutionResult(
            status = ActionExecutionStatus.EXECUTED,
            detail = "Uzaktan el hareketi kontrolü açıldı$scopeLabel.$a11yHint"
        )
    }

    private fun executeGoHome(): ActionExecutionResult {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            ActionExecutionResult(
                status = ActionExecutionStatus.EXECUTED,
                detail = "Ana ekrana dönüldü."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to go home", e)
            ActionExecutionResult(
                status = ActionExecutionStatus.FAILED,
                detail = "Ana ekrana geçilemedi."
            )
        }
    }

    fun openSystemTimer(totalSeconds: Int, message: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds.coerceAtLeast(1))
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "No timer app available", e)
            false
        }
    }

    private fun findBestAppMatch(query: String, apps: List<InstalledAppInfo>): InstalledAppInfo? {
        val normalizedQuery = CoordinateMatcher.normalize(query)
        val candidates = expandAliases(normalizedQuery)
        var bestApp: InstalledAppInfo? = null
        var bestScore = 0

        for (app in apps) {
            val normLabel = CoordinateMatcher.normalize(app.label)
            val normPkg = CoordinateMatcher.normalize(app.packageName)
            val labelWords = normLabel.split(" ").filter { it.isNotEmpty() }

            for (candidate in candidates) {
                val score = when {
                    normLabel == candidate -> 100
                    normLabel.startsWith(candidate) -> 90
                    labelWords.any { it == candidate } -> 85
                    normLabel.contains(candidate) -> 78
                    candidate.contains(normLabel) && normLabel.length >= 2 -> 68
                    normPkg.contains(candidate) && candidate.length >= 3 -> 55
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    bestApp = app
                }
            }
        }
        return if (bestScore >= 55) bestApp else null
    }

    private fun expandAliases(normalizedQuery: String): List<String> {
        val cleaned = normalizedQuery
            .replace("uygulamasini", "")
            .replace("uygulamasi", "")
            .replace("ac", "")
            .trim()
        val base = cleaned.ifBlank { normalizedQuery }
        val aliases = mutableListOf(normalizedQuery, base)

        val map = mapOf(
            "ayarlar" to listOf("settings"),
            "kamera" to listOf("camera"),
            "tarayici" to listOf("chrome", "browser"),
            "chrome" to listOf("browser"),
            "saat" to listOf("clock", "alarm"),
            "takvim" to listOf("calendar"),
            "hesap makinesi" to listOf("calculator"),
            "harita" to listOf("maps"),
            "haritalar" to listOf("maps"),
            "xerite" to listOf("maps"),
            "muzik" to listOf("music", "spotify", "yt music"),
            "rehber" to listOf("contacts"),
            "kisiler" to listOf("contacts"),
            "mesajlar" to listOf("messages"),
            "telefon" to listOf("phone", "dialer"),
            "galeri" to listOf("photos", "gallery"),
            "fotograflar" to listOf("photos"),
            "dosyalar" to listOf("files"),
            "notlar" to listOf("notes", "keep"),
            "posta" to listOf("gmail", "mail"),
            "magaza" to listOf("play store"),
            "play store" to listOf("google play"),
        )
        map.forEach { (k, v) ->
            if (base.contains(k) || normalizedQuery.contains(k)) aliases.addAll(v)
        }
        return aliases.distinct().filter { it.isNotEmpty() }
    }
}

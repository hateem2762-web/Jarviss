package com.example.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.accessibility.JarvisAccessibilityService
import com.example.ai.JarvisRepository
import com.example.ai.configureAppCheck
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CoordinateJsonStore
import com.example.data.local.JarvisDatabase
import com.example.data.preferences.JarvisPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ActionExecutionStatus
import com.example.model.ActionIntent
import com.example.model.HudTopicResolver
import com.example.model.InstalledAppInfo
import com.example.model.JarvisMood
import com.example.model.JarvisVisualState
import com.example.model.ProactiveAlertItem
import com.example.notifications.ProactiveAlertActionCache
import com.example.notifications.ProactiveAlertNotifier
import com.example.system.CoordinateMatcher
import com.example.system.JarvisActionExecutor
import com.example.system.LocalQuickCommandMatcher
import com.example.system.VoiceInputManager
import com.example.system.WakeWordManager
import com.example.ui.components.JarvisHudHeader
import com.example.ui.components.LiveSubtitleDisplay
import com.example.ui.components.ParticleNebulaCanvas
import com.example.ui.components.ProactiveAlertCard
import com.example.ui.components.resolveMoodAccentColor
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "JarvisOverlayService"

data class OverlayUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialText: String = "",
    val rmsLevel: Float = 0f,
    val userText: String = "",
    val replyText: String = "",
    val ttsSummary: String = "",
    val actionDetail: String = "",
    val actionFailed: Boolean = false,
    val isProcessing: Boolean = false,
    val errorText: String? = null,
    val ttsEnabled: Boolean = false,
    val copied: Boolean = false,
    val activeProfileLabel: String = "",
    val mood: JarvisMood = JarvisMood.NEUTRAL,
    val topicTitle: String = "",
    val lastActionIntent: ActionIntent? = null,
    val recentLines: List<Pair<Boolean, String>> = emptyList(),
)

data class CrosshairPositionState(
    val xPercent: Float = 0.5f,
    val yPercent: Float = 0.5f,
    val xPx: Int = 0,
    val yPx: Int = 0,
    val profilePackageName: String = CoordinateJsonStore.GENERAL_PROFILE_PACKAGE,
    val profileName: String = CoordinateJsonStore.GENERAL_PROFILE_NAME,
)

class JarvisOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var dao: com.example.data.local.JarvisDao
    private lateinit var coordinateStore: CoordinateJsonStore
    private lateinit var actionExecutor: JarvisActionExecutor
    private lateinit var voiceManager: VoiceInputManager
    private lateinit var wakeWordManager: WakeWordManager
    private val aiRepository = JarvisRepository()

    private val overlayState = MutableStateFlow(OverlayUiState())
    private val crosshairState = MutableStateFlow(CrosshairPositionState())
    private val wakeWordIndicator = MutableStateFlow(false)
    private val bubbleMoodState = MutableStateFlow(JarvisMood.NEUTRAL)
    private val isDraggingBubble = MutableStateFlow(false)
    private val isOverDismissZone = MutableStateFlow(false)
    private var prefsSnapshot = JarvisPreferences()

    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayLifecycle: OverlayLifecycleOwner? = null

    private var bubbleView: ComposeView? = null
    private var bubbleLifecycle: OverlayLifecycleOwner? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var dismissZoneView: ComposeView? = null
    private var dismissZoneLifecycle: OverlayLifecycleOwner? = null

    private var crosshairReticleView: ComposeView? = null
    private var crosshairReticleParams: WindowManager.LayoutParams? = null
    private var crosshairReticleLifecycle: OverlayLifecycleOwner? = null

    private var crosshairBarView: ComposeView? = null
    private var crosshairBarLifecycle: OverlayLifecycleOwner? = null

    private var labelDialogView: ComposeView? = null
    private var labelDialogLifecycle: OverlayLifecycleOwner? = null

    private var proactiveAlertView: ComposeView? = null
    private var proactiveAlertLifecycle: OverlayLifecycleOwner? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var autoCloseJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        configureAppCheck(this, null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefsRepo = UserPreferencesRepository(this)
        dao = JarvisDatabase.getInstance(this).jarvisDao()
        coordinateStore = CoordinateJsonStore(this, dao)
        actionExecutor = JarvisActionExecutor(this, dao, aiRepository)
        voiceManager = VoiceInputManager(this)
        wakeWordManager = WakeWordManager(this) { trailingCommand ->
            if (Settings.canDrawOverlays(this)) {
                showExpandedOverlay(autoSubmitQuery = trailingCommand)
            }
        }

        initTextToSpeech()
        if (!ensureForegroundNotification()) {
            stopSelf()
            return
        }

        serviceScope.launch {
            prefsRepo.preferences.collect { prefs ->
                prefsSnapshot = prefs
                wakeWordIndicator.value = prefs.wakeWordEnabled && hasMicPermission()
                overlayState.value = overlayState.value.copy(
                    ttsEnabled = prefs.speakReplies,
                    activeProfileLabel = resolveActiveProfilePair(prefs).second,
                )
                ensureForegroundNotification()
                syncWakeWordState(prefs)

                if (
                    prefs.floatingBubbleEnabled &&
                    Settings.canDrawOverlays(this@JarvisOverlayService) &&
                    overlayView == null &&
                    crosshairReticleView == null &&
                    labelDialogView == null
                ) {
                    showBubble()
                } else if (!prefs.floatingBubbleEnabled) {
                    removeBubble()
                }
            }
        }

        serviceScope.launch {
            voiceManager.state.collect { voice ->
                overlayState.value = overlayState.value.copy(
                    isListening = voice.isListening,
                    partialText = voice.partialText,
                    rmsLevel = voice.rmsLevel,
                    errorText = voice.errorMessage ?: overlayState.value.errorText,
                )
            }
        }
    }

    private fun initTextToSpeech() {
        try {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res = tts?.setLanguage(Locale("tr", "TR"))
                    ttsReady = res != TextToSpeech.LANG_MISSING_DATA &&
                        res != TextToSpeech.LANG_NOT_SUPPORTED
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            overlayState.value = overlayState.value.copy(isSpeaking = true)
                        }

                        override fun onDone(utteranceId: String?) {
                            overlayState.value = overlayState.value.copy(isSpeaking = false)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            overlayState.value = overlayState.value.copy(isSpeaking = false)
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS init skipped", e)
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun syncWakeWordState(prefs: JarvisPreferences) {
        val canRunWakeWord = prefs.wakeWordEnabled &&
            hasMicPermission() &&
            overlayView == null &&
            crosshairReticleView == null &&
            labelDialogView == null

        if (canRunWakeWord) {
            wakeWordManager.start()
        } else if (!prefs.wakeWordEnabled) {
            wakeWordManager.stop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ensureForegroundNotification()) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) {
                    showExpandedOverlay()
                }
            }
            ACTION_START_CROSSHAIR -> {
                if (Settings.canDrawOverlays(this)) {
                    showCrosshairRecorder()
                }
            }
            ACTION_SHOW_PROACTIVE_ALERT -> {
                val alertId = intent.getLongExtra(EXTRA_PROACTIVE_ALERT_ID, -1L)
                if (alertId > 0L && Settings.canDrawOverlays(this)) {
                    showProactiveAlertWindow(alertId)
                }
            }
            ACTION_SYNC_MODE -> {
                serviceScope.launch {
                    val prefs = prefsRepo.preferences.first()
                    prefsSnapshot = prefs
                    wakeWordIndicator.value = prefs.wakeWordEnabled && hasMicPermission()
                    ensureForegroundNotification()
                    syncWakeWordState(prefs)

                    if (
                        prefs.floatingBubbleEnabled &&
                        Settings.canDrawOverlays(this@JarvisOverlayService) &&
                        overlayView == null &&
                        crosshairReticleView == null
                    ) {
                        showBubble()
                    } else if (!prefs.floatingBubbleEnabled) {
                        removeBubble()
                    }
                    if (
                        !prefs.persistentTriggerEnabled &&
                        !prefs.floatingBubbleEnabled &&
                        !prefs.wakeWordEnabled &&
                        overlayView == null &&
                        crosshairReticleView == null
                    ) {
                        stopSelf()
                    }
                }
            }
            ACTION_DISABLE_ALL -> {
                disableAllOverlayModesCompletely()
            }
            ACTION_PAUSE_WAKE -> {
                wakeWordManager.pauseForActiveVoiceSession()
            }
            ACTION_RESUME_WAKE -> {
                if (prefsSnapshot.wakeWordEnabled) {
                    wakeWordManager.resumeAfterVoiceSession()
                }
            }
            ACTION_STOP_SERVICE -> {
                wakeWordManager.stop()
                removeAllOverlayWindows()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun ensureForegroundNotification(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val speakIntent = Intent(this, OverlayLaunchActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val speakPendingIntent = PendingIntent.getActivity(
            this,
            9001,
            speakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val crosshairIntent = Intent(this, OverlayLaunchActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(OverlayLaunchActivity.EXTRA_START_CROSSHAIR, true)
        }
        val crosshairPendingIntent = PendingIntent.getActivity(
            this,
            9002,
            crosshairIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disableIntent = Intent(this, JarvisOverlayService::class.java).apply {
            action = ACTION_DISABLE_ALL
        }
        val disablePendingIntent = PendingIntent.getService(
            this,
            9003,
            disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentSummary = if (prefsSnapshot.wakeWordEnabled && hasMicPermission()) {
            "\"Jarvis\" sesli uyandırma aktif • Konuş veya nokta kaydet"
        } else {
            "Konuş veya ekrandan dokunma noktası kaydet"
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis_tile)
            .setContentTitle("JARVIS Hızlı Erişim")
            .setContentText(contentSummary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(speakPendingIntent)
            .addAction(
                R.drawable.ic_jarvis_tile,
                getString(R.string.speak_to_jarvis),
                speakPendingIntent
            )
            .addAction(
                R.drawable.ic_jarvis_tile,
                getString(R.string.record_point),
                crosshairPendingIntent
            )
            .addAction(
                R.drawable.ic_jarvis_tile,
                "Kapat",
                disablePendingIntent
            )
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val fgsType = if (prefsSnapshot.wakeWordEnabled && hasMicPermission()) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to enter foreground safely", e)
            false
        }
    }

    private fun overlayLayoutType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun resolveActiveProfilePair(prefs: JarvisPreferences): Pair<String, String> {
        val detectedPkg = JarvisAccessibilityService.currentForegroundPackage.value
            ?.takeIf { it.isNotBlank() && it != packageName }
        val pkg = detectedPkg
            ?: prefs.manualProfilePackageName.takeIf { it.isNotBlank() }
            ?: CoordinateJsonStore.GENERAL_PROFILE_PACKAGE
        val name = if (detectedPkg != null) {
            actionExecutor.resolveAppDisplayName(detectedPkg)
        } else if (prefs.manualProfileName.isNotBlank()) {
            prefs.manualProfileName
        } else {
            actionExecutor.resolveAppDisplayName(pkg)
        }
        return pkg to name
    }

    private fun showExpandedOverlay(autoSubmitQuery: String? = null) {
        autoCloseJob?.cancel()
        wakeWordManager.pauseForActiveVoiceSession()
        removeBubble()
        removeCrosshairWindows()
        removeLabelDialog()

        if (overlayView != null) {
            if (!autoSubmitQuery.isNullOrBlank()) {
                submitQuery(autoSubmitQuery)
            } else {
                startOverlayListening()
            }
            return
        }

        val (_, activeLabel) = resolveActiveProfilePair(prefsSnapshot)
        overlayState.value = OverlayUiState(
            ttsEnabled = prefsSnapshot.speakReplies,
            activeProfileLabel = activeLabel,
            mood = bubbleMoodState.value,
        )

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        overlayLifecycle = lifecycleOwner

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (20 * resources.displayMetrics.density).roundToInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        overlayParams = params

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val state by overlayState.collectAsStateWithLifecycle()
                JarvisTheme(darkTheme = true) {
                    OverlayCardContent(
                        state = state,
                        onMicToggle = {
                            if (state.isListening) {
                                voiceManager.stopListening()
                            } else {
                                startOverlayListening()
                            }
                        },
                        onSendText = { query -> submitQuery(query) },
                        onCaptureScreenNote = { submitQuery("Bu ekranı not al") },
                        onRecordPoint = { showCrosshairRecorder() },
                        onCopyReply = { copyReplyToClipboard() },
                        onToggleTts = { toggleTts() },
                        onOpenFullApp = {
                            val appIntent = Intent(this@JarvisOverlayService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(appIntent)
                            collapseOrStopOverlay()
                        },
                        onClose = { minimizeToFloatingBubble() },
                    )
                }
            }
        }

        overlayView = composeView
        try {
            windowManager.addView(composeView, params)
            if (!autoSubmitQuery.isNullOrBlank()) {
                submitQuery(autoSubmitQuery)
            } else {
                startOverlayListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach overlay view", e)
            removeExpandedOverlay()
        }
    }

    private fun startOverlayListening() {
        if (!hasMicPermission()) {
            overlayState.value = overlayState.value.copy(
                errorText = getString(R.string.mic_permission_needed)
            )
            return
        }

        wakeWordManager.pauseForActiveVoiceSession()
        overlayState.value = overlayState.value.copy(errorText = null, copied = false)
        voiceManager.startListening { transcript ->
            submitQuery(transcript)
        }
    }

    private fun hideOverlayTemporarilyForTap() {
        overlayView?.let { view ->
            view.visibility = View.INVISIBLE
            overlayParams?.let { lp ->
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                try {
                    windowManager.updateViewLayout(view, lp)
                } catch (_: Exception) {
                }
            }
        }
        bubbleView?.let { it.visibility = View.INVISIBLE }
    }

    private fun restoreOverlayAfterTap() {
        overlayView?.let { view ->
            overlayParams?.let { lp ->
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                try {
                    windowManager.updateViewLayout(view, lp)
                } catch (_: Exception) {
                }
            }
            view.visibility = View.VISIBLE
        }
        bubbleView?.let { it.visibility = View.VISIBLE }
    }

    private fun submitQuery(rawText: String) {
        val query = rawText.trim()
        if (query.isEmpty() || overlayState.value.isProcessing) return

        autoCloseJob?.cancel()
        val updatedWithUser = (overlayState.value.recentLines + (true to query)).takeLast(8)
        overlayState.value = overlayState.value.copy(
            userText = query,
            isProcessing = true,
            isSpeaking = false,
            errorText = null,
            actionDetail = "",
            actionFailed = false,
            copied = false,
            topicTitle = "",
            lastActionIntent = null,
            recentLines = updatedWithUser,
        )

        serviceScope.launch {
            try {
                val prefs = prefsRepo.preferences.first()
                val (activePkg, activeLabel) = resolveActiveProfilePair(prefs)
                val profileCoords = coordinateStore.getCoordinatesForActiveOrFallback(activePkg)

                val userMsgId = dao.insertMessage(
                    ChatMessageEntity(
                        isUser = true,
                        text = query,
                        timestampMillis = System.currentTimeMillis(),
                    )
                )

                // 0-TOKEN FAST PATH: check saved coordinates locally before touching Gemini API
                val zeroTokenMatch = CoordinateMatcher.findZeroTokenMatch(query, profileCoords)
                if (zeroTokenMatch != null) {
                    val replyText = "\"${zeroTokenMatch.label}\" noktasına dokunuluyor."
                    val botMsgId = dao.insertMessage(
                        ChatMessageEntity(
                            isUser = false,
                            text = replyText,
                            intentWire = ActionIntent.TAP_COORDINATE.wireValue,
                            target = zeroTokenMatch.label,
                            actionMessage = zeroTokenMatch.profileName,
                            actionStatus = "PENDING",
                            timestampMillis = System.currentTimeMillis(),
                        )
                    )
                    val execResult = actionExecutor.executeTapOnCoordinate(
                        coordinate = zeroTokenMatch,
                        beforeTap = { hideOverlayTemporarilyForTap() },
                        afterTap = { restoreOverlayAfterTap() },
                    )
                    dao.updateMessageActionStatus(
                        id = botMsgId,
                        status = execResult.status.name,
                        detail = execResult.detail,
                    )
                    val resolvedMood = if (execResult.status == ActionExecutionStatus.FAILED) {
                        JarvisMood.ALERT
                    } else {
                        HudTopicResolver.moodForAction(ActionIntent.TAP_COORDINATE, JarvisMood.ENERGETIC)
                    }
                    bubbleMoodState.value = resolvedMood
                    overlayState.value = overlayState.value.copy(
                        replyText = replyText,
                        ttsSummary = replyText,
                        actionDetail = "${execResult.detail} (0 token)",
                        actionFailed = execResult.status == ActionExecutionStatus.FAILED,
                        isProcessing = false,
                        mood = resolvedMood,
                        lastActionIntent = ActionIntent.TAP_COORDINATE,
                        recentLines = (overlayState.value.recentLines + (false to replyText)).takeLast(8),
                    )
                    if (execResult.status == ActionExecutionStatus.EXECUTED) {
                        autoCloseJob = serviceScope.launch {
                            delay(900L)
                            collapseOrStopOverlay()
                        }
                    }
                    return@launch
                }

                // 0-TOKEN FAST PATH 2: local hardware controls & screen note capture
                val localAction = LocalQuickCommandMatcher.match(query)
                if (localAction != null) {
                    val execResult = actionExecutor.execute(
                        action = localAction,
                        activeProfilePackage = activePkg,
                        beforeTap = { hideOverlayTemporarilyForTap() },
                        afterTap = { restoreOverlayAfterTap() },
                    )
                    val replyText = execResult.detail.ifBlank { "${localAction.intent.displayLabel} tamamlandı." }
                    val botMsgId = dao.insertMessage(
                        ChatMessageEntity(
                            isUser = false,
                            text = replyText,
                            intentWire = localAction.intent.wireValue,
                            target = localAction.target,
                            actionStatus = execResult.status.name,
                            actionStatusDetail = execResult.detail,
                            timestampMillis = System.currentTimeMillis(),
                        )
                    )
                    val resolvedMood = if (execResult.status == ActionExecutionStatus.FAILED) {
                        JarvisMood.ALERT
                    } else {
                        HudTopicResolver.moodForAction(localAction.intent, JarvisMood.SERIOUS)
                    }
                    bubbleMoodState.value = resolvedMood
                    overlayState.value = overlayState.value.copy(
                        replyText = replyText,
                        ttsSummary = replyText,
                        actionDetail = "${execResult.detail} (0 token)",
                        actionFailed = execResult.status == ActionExecutionStatus.FAILED,
                        isProcessing = false,
                        mood = resolvedMood,
                        lastActionIntent = localAction.intent,
                        recentLines = (overlayState.value.recentLines + (false to replyText)).takeLast(8),
                    )
                    if (overlayState.value.ttsEnabled && ttsReady) {
                        tts?.speak(
                            replyText,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "jarvis_overlay_$botMsgId"
                        )
                    }
                    if (
                        execResult.status == ActionExecutionStatus.EXECUTED &&
                        localAction.intent == ActionIntent.OPEN_SYSTEM_SETTINGS
                    ) {
                        autoCloseJob = serviceScope.launch {
                            delay(850L)
                            collapseOrStopOverlay()
                        }
                    }
                    return@launch
                }

                // Otherwise route through Gemini (text-only saved label list)
                val apps = actionExecutor.getInstalledLauncherApps()
                val result = aiRepository.askJarvis(
                    userPrompt = query,
                    userContext = prefs.userContext,
                    recentMessages = emptyList(),
                    installedApps = apps,
                    savedCoordinateLabels = profileCoords.map { it.label }.distinct(),
                    activeProfileLabel = activeLabel,
                )

                result.fold(
                    onSuccess = { reply ->
                        val action = reply.action
                        val insertedId = dao.insertMessage(
                            ChatMessageEntity(
                                isUser = false,
                                text = reply.text,
                                intentWire = action?.intent?.wireValue,
                                target = action?.target,
                                minutes = action?.minutes,
                                seconds = action?.seconds,
                                actionMessage = action?.message,
                                actionStatus = if (action == null) "EXECUTED" else "PENDING",
                                timestampMillis = System.currentTimeMillis(),
                            )
                        )

                        val initialMood = if (action != null) {
                            HudTopicResolver.moodForAction(action.intent, reply.mood)
                        } else {
                            reply.mood
                        }
                        bubbleMoodState.value = initialMood

                        if (action != null) {
                            val execResult = actionExecutor.execute(
                                action = action,
                                activeProfilePackage = activePkg,
                                beforeTap = { hideOverlayTemporarilyForTap() },
                                afterTap = { restoreOverlayAfterTap() },
                            )
                            dao.updateMessageActionStatus(
                                id = insertedId,
                                status = execResult.status.name,
                                detail = execResult.detail,
                            )
                            val displayReply = if (
                                action.intent in setOf(
                                    ActionIntent.BATTERY_STATUS,
                                    ActionIntent.TORCH_CONTROL,
                                    ActionIntent.VOLUME_CONTROL,
                                    ActionIntent.CAPTURE_SCREEN_NOTE,
                                ) && execResult.detail.isNotBlank()
                            ) {
                                execResult.detail
                            } else {
                                reply.text
                            }
                            val finalMood = if (execResult.status == ActionExecutionStatus.FAILED) {
                                JarvisMood.ALERT
                            } else {
                                initialMood
                            }
                            bubbleMoodState.value = finalMood
                            overlayState.value = overlayState.value.copy(
                                replyText = displayReply,
                                ttsSummary = displayReply,
                                actionDetail = execResult.detail,
                                actionFailed = execResult.status == ActionExecutionStatus.FAILED,
                                isProcessing = false,
                                mood = finalMood,
                                topicTitle = reply.topicTitle,
                                lastActionIntent = action.intent,
                                recentLines = (overlayState.value.recentLines + (false to displayReply)).takeLast(8),
                            )

                            if (overlayState.value.ttsEnabled && ttsReady) {
                                tts?.speak(
                                    displayReply,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "jarvis_overlay_$insertedId"
                                )
                            }

                            val shouldAutoClose = execResult.status == ActionExecutionStatus.EXECUTED &&
                                action.intent in setOf(
                                    ActionIntent.OPEN_APP,
                                    ActionIntent.OPEN_URL,
                                    ActionIntent.SEARCH_YOUTUBE,
                                    ActionIntent.GO_HOME,
                                    ActionIntent.TAP_COORDINATE,
                                    ActionIntent.OPEN_SYSTEM_SETTINGS,
                                )
                            if (shouldAutoClose) {
                                autoCloseJob = serviceScope.launch {
                                    delay(950L)
                                    collapseOrStopOverlay()
                                }
                            }
                        } else {
                            overlayState.value = overlayState.value.copy(
                                replyText = reply.text,
                                ttsSummary = reply.ttsSummary,
                                isProcessing = false,
                                mood = initialMood,
                                topicTitle = reply.topicTitle,
                                lastActionIntent = null,
                                recentLines = (overlayState.value.recentLines + (false to reply.text)).takeLast(8),
                            )
                            if (overlayState.value.ttsEnabled && ttsReady) {
                                tts?.speak(
                                    reply.ttsSummary,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "jarvis_overlay_$insertedId"
                                )
                            }
                        }
                    },
                    onFailure = { err ->
                        Log.w(TAG, "Gemini failed for userMsg=$userMsgId: ${err.message}")
                        bubbleMoodState.value = JarvisMood.ALERT
                        overlayState.value = overlayState.value.copy(
                            isProcessing = false,
                            mood = JarvisMood.ALERT,
                            errorText = err.localizedMessage ?: "Yanıt alınamadı.",
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Overlay query failed", e)
                bubbleMoodState.value = JarvisMood.ALERT
                overlayState.value = overlayState.value.copy(
                    isProcessing = false,
                    mood = JarvisMood.ALERT,
                    errorText = e.localizedMessage ?: "Bir hata oluştu.",
                )
            }
        }
    }

    // ---- CROSSHAIR RECORDER OVERLAY ----

    private fun showCrosshairRecorder() {
        autoCloseJob?.cancel()
        voiceManager.stopListening()
        removeExpandedOverlay()
        removeBubble()
        removeCrosshairWindows()
        removeLabelDialog()

        serviceScope.launch {
            val prefs = prefsRepo.preferences.first()
            prefsSnapshot = prefs
            val (activePkg, activeName) = resolveActiveProfilePair(prefs)
            val (screenW, screenH) = JarvisAccessibilityService.getRealScreenSize(this@JarvisOverlayService)
            val reticleSizePx = (56 * resources.displayMetrics.density).roundToInt()

            val initialX = ((screenW - reticleSizePx) / 2f).roundToInt()
            val initialY = ((screenH - reticleSizePx) / 2f).roundToInt()
            val centerX = initialX + reticleSizePx / 2
            val centerY = initialY + reticleSizePx / 2

            crosshairState.value = CrosshairPositionState(
                xPercent = (centerX.toFloat() / screenW).coerceIn(0f, 1f),
                yPercent = (centerY.toFloat() / screenH).coerceIn(0f, 1f),
                xPx = centerX,
                yPx = centerY,
                profilePackageName = activePkg,
                profileName = activeName,
            )

            // 1. Draggable Crosshair Reticle Window
            val reticleOwner = OverlayLifecycleOwner().apply { onCreate() }
            crosshairReticleLifecycle = reticleOwner
            val reticleParams = WindowManager.LayoutParams(
                reticleSizePx,
                reticleSizePx,
                overlayLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }
            crosshairReticleParams = reticleParams

            val reticleView = ComposeView(this@JarvisOverlayService).apply {
                setViewTreeLifecycleOwner(reticleOwner)
                setViewTreeSavedStateRegistryOwner(reticleOwner)
                setViewTreeViewModelStoreOwner(reticleOwner)
                setContent {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    crosshairReticleParams?.let { lp ->
                                        lp.x = (lp.x + dragAmount.x.roundToInt())
                                            .coerceIn(0, (screenW - reticleSizePx).coerceAtLeast(0))
                                        lp.y = (lp.y + dragAmount.y.roundToInt())
                                            .coerceIn(0, (screenH - reticleSizePx).coerceAtLeast(0))
                                        try {
                                            windowManager.updateViewLayout(this@apply, lp)
                                        } catch (_: Exception) {
                                        }
                                        val cx = lp.x + reticleSizePx / 2
                                        val cy = lp.y + reticleSizePx / 2
                                        crosshairState.value = crosshairState.value.copy(
                                            xPercent = (cx.toFloat() / screenW).coerceIn(0f, 1f),
                                            yPercent = (cy.toFloat() / screenH).coerceIn(0f, 1f),
                                            xPx = cx,
                                            yPx = cy,
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        CrosshairTargetGraphic()
                    }
                }
            }
            crosshairReticleView = reticleView

            // 2. Floating Control Pill ("Kaydet" / "İptal")
            val barOwner = OverlayLifecycleOwner().apply { onCreate() }
            crosshairBarLifecycle = barOwner
            val barParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (28 * resources.displayMetrics.density).roundToInt()
            }

            val barView = ComposeView(this@JarvisOverlayService).apply {
                setViewTreeLifecycleOwner(barOwner)
                setViewTreeSavedStateRegistryOwner(barOwner)
                setViewTreeViewModelStoreOwner(barOwner)
                setContent {
                    val pos by crosshairState.collectAsStateWithLifecycle()
                    JarvisTheme(darkTheme = true) {
                        CrosshairControlPill(
                            state = pos,
                            onSaveClick = { openSaveCoordinateDialog() },
                            onCancelClick = { finishCrosshairFlow() },
                        )
                    }
                }
            }
            crosshairBarView = barView

            try {
                windowManager.addView(reticleView, reticleParams)
                windowManager.addView(barView, barParams)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show crosshair recorder", e)
                removeCrosshairWindows()
            }
        }
    }

    private fun openSaveCoordinateDialog() {
        val snapshot = crosshairState.value
        removeCrosshairWindows()

        serviceScope.launch {
            val installedApps = actionExecutor.getInstalledLauncherApps().take(20)
            val dialogOwner = OverlayLifecycleOwner().apply { onCreate() }
            labelDialogLifecycle = dialogOwner

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            val dialogView = ComposeView(this@JarvisOverlayService).apply {
                setViewTreeLifecycleOwner(dialogOwner)
                setViewTreeSavedStateRegistryOwner(dialogOwner)
                setViewTreeViewModelStoreOwner(dialogOwner)
                setContent {
                    JarvisTheme(darkTheme = true) {
                        SaveCoordinateDialogOverlay(
                            initialPosition = snapshot,
                            installedApps = installedApps,
                            onConfirmSave = { pkg, name, label ->
                                serviceScope.launch {
                                    coordinateStore.saveCoordinate(
                                        profilePackageName = pkg,
                                        profileName = name,
                                        label = label,
                                        xPercent = snapshot.xPercent,
                                        yPercent = snapshot.yPercent,
                                        xPx = snapshot.xPx,
                                        yPx = snapshot.yPx,
                                    )
                                    removeLabelDialog()
                                    if (prefsSnapshot.floatingBubbleEnabled && Settings.canDrawOverlays(this@JarvisOverlayService)) {
                                        showBubble()
                                    } else if (!prefsSnapshot.persistentTriggerEnabled) {
                                        stopSelf()
                                    }
                                }
                            },
                            onCancel = { finishCrosshairFlow() },
                        )
                    }
                }
            }

            labelDialogView = dialogView
            try {
                windowManager.addView(dialogView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach save coordinate dialog", e)
                removeLabelDialog()
            }
        }
    }

    private fun finishCrosshairFlow() {
        removeCrosshairWindows()
        removeLabelDialog()
        if (prefsSnapshot.floatingBubbleEnabled && Settings.canDrawOverlays(this)) {
            showBubble()
        } else if (!prefsSnapshot.persistentTriggerEnabled) {
            stopSelf()
        }
    }

    private fun removeCrosshairWindows() {
        crosshairReticleView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
            }
        }
        crosshairReticleView = null
        crosshairReticleParams = null
        crosshairReticleLifecycle?.onDestroy()
        crosshairReticleLifecycle = null

        crosshairBarView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
            }
        }
        crosshairBarView = null
        crosshairBarLifecycle?.onDestroy()
        crosshairBarLifecycle = null
    }

    private fun removeLabelDialog() {
        labelDialogView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
            }
        }
        labelDialogView = null
        labelDialogLifecycle?.onDestroy()
        labelDialogLifecycle = null
    }

    private fun copyReplyToClipboard() {
        val textToCopy = overlayState.value.replyText.trim()
        if (textToCopy.isEmpty()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("JARVIS", textToCopy))
        overlayState.value = overlayState.value.copy(copied = true)
    }

    private fun toggleTts() {
        val next = !overlayState.value.ttsEnabled
        overlayState.value = overlayState.value.copy(ttsEnabled = next)
        serviceScope.launch {
            prefsRepo.setSpeakReplies(next)
        }
        if (next && ttsReady) {
            val summary = overlayState.value.ttsSummary.ifBlank { overlayState.value.replyText }
            if (summary.isNotBlank()) {
                tts?.speak(summary, TextToSpeech.QUEUE_FLUSH, null, "jarvis_overlay_manual")
            }
        } else {
            tts?.stop()
            overlayState.value = overlayState.value.copy(isSpeaking = false)
        }
    }

    private fun disableFloatingBubbleOnly() {
        removeDismissZone()
        removeBubble()
        serviceScope.launch {
            prefsRepo.setFloatingBubbleEnabled(false)
            val updated = prefsRepo.preferences.first()
            prefsSnapshot = updated
            if (!updated.persistentTriggerEnabled && !updated.wakeWordEnabled && overlayView == null) {
                stopSelf()
            }
        }
    }

    private fun disableAllOverlayModesCompletely() {
        autoCloseJob?.cancel()
        voiceManager.stopListening()
        wakeWordManager.stop()
        removeAllOverlayWindows()
        serviceScope.launch {
            prefsRepo.disableAllOverlayModes()
            stopSelf()
        }
    }

    private fun minimizeToFloatingBubble() {
        autoCloseJob?.cancel()
        voiceManager.stopListening()
        removeExpandedOverlay()

        if (Settings.canDrawOverlays(this)) {
            if (!prefsSnapshot.floatingBubbleEnabled) {
                prefsSnapshot = prefsSnapshot.copy(floatingBubbleEnabled = true)
                serviceScope.launch {
                    prefsRepo.setFloatingBubbleEnabled(true)
                }
            }
            showBubble()
            ensureForegroundNotification()
        }

        if (prefsSnapshot.wakeWordEnabled && hasMicPermission()) {
            wakeWordManager.resumeAfterVoiceSession()
        }

        if (!Settings.canDrawOverlays(this) &&
            !prefsSnapshot.persistentTriggerEnabled &&
            !prefsSnapshot.wakeWordEnabled
        ) {
            stopSelf()
        }
    }

    private fun collapseOrStopOverlay() {
        autoCloseJob?.cancel()
        voiceManager.stopListening()
        removeExpandedOverlay()
        if (prefsSnapshot.floatingBubbleEnabled && Settings.canDrawOverlays(this)) {
            showBubble()
        }
        if (prefsSnapshot.wakeWordEnabled && hasMicPermission()) {
            wakeWordManager.resumeAfterVoiceSession()
        }
        if (
            !prefsSnapshot.floatingBubbleEnabled &&
            !prefsSnapshot.persistentTriggerEnabled &&
            !prefsSnapshot.wakeWordEnabled
        ) {
            stopSelf()
        }
    }

    private fun removeExpandedOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        overlayParams = null
        overlayLifecycle?.onDestroy()
        overlayLifecycle = null
    }

    private fun showBubble() {
        if (bubbleView != null || !Settings.canDrawOverlays(this)) return

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        bubbleLifecycle = lifecycleOwner

        val density = resources.displayMetrics.density
        val (screenW, screenH) = JarvisAccessibilityService.getRealScreenSize(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).roundToInt()
            y = (220 * density).roundToInt()
        }
        bubbleParams = params

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val wakeActive by wakeWordIndicator.collectAsStateWithLifecycle()
                val bubbleMood by bubbleMoodState.collectAsStateWithLifecycle()
                val bubbleAccent = resolveMoodAccentColor(
                    visualState = if (wakeActive) JarvisVisualState.LISTENING else JarvisVisualState.IDLE,
                    mood = bubbleMood,
                )
                val openAssistantLabel = stringResource(R.string.speak_to_jarvis)
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Minimal matte circle bubble with live miniature Particle Nebula (tap to re-open, drag to dismiss)
                    Surface(
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("floating_jarvis_bubble")
                            .semantics { contentDescription = openAssistantLabel }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDraggingBubble.value = true
                                        isOverDismissZone.value = false
                                        showDismissZone()
                                    },
                                    onDragEnd = {
                                        val shouldDismiss = isOverDismissZone.value
                                        isDraggingBubble.value = false
                                        isOverDismissZone.value = false
                                        removeDismissZone()
                                        if (shouldDismiss) {
                                            disableFloatingBubbleOnly()
                                        }
                                    },
                                    onDragCancel = {
                                        isDraggingBubble.value = false
                                        isOverDismissZone.value = false
                                        removeDismissZone()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        bubbleParams?.let { lp ->
                                            lp.x = (lp.x + dragAmount.x.roundToInt())
                                                .coerceIn(0, (screenW - (56 * density).roundToInt()).coerceAtLeast(0))
                                            lp.y = (lp.y + dragAmount.y.roundToInt())
                                                .coerceIn(0, (screenH - (56 * density).roundToInt()).coerceAtLeast(0))
                                            try {
                                                windowManager.updateViewLayout(this@apply, lp)
                                            } catch (_: Exception) {
                                            }
                                            val inDismissBand = lp.y > screenH * 0.74f &&
                                                lp.x > screenW * 0.20f &&
                                                lp.x < screenW * 0.80f
                                            isOverDismissZone.value = inDismissBand
                                        }
                                    }
                                )
                            }
                            .clip(CircleShape)
                            .clickable { showExpandedOverlay() },
                        shape = CircleShape,
                        color = Color(0xF2080B10),
                        border = BorderStroke(1.dp, bubbleAccent.copy(alpha = 0.55f)),
                        shadowElevation = 12.dp,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ParticleNebulaCanvas(
                                visualState = if (wakeActive) JarvisVisualState.LISTENING else JarvisVisualState.IDLE,
                                mood = bubbleMood,
                                rmsLevel = if (wakeActive) 0.14f else 0f,
                                compactMode = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (wakeActive) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(7.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(JarvisEmerald)
                                )
                            }
                        }
                    }
                }
            }
        }

        bubbleView = composeView
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating bubble", e)
            removeBubble()
        }
    }

    private fun showDismissZone() {
        if (dismissZoneView != null || !Settings.canDrawOverlays(this)) return
        val owner = OverlayLifecycleOwner().apply { onCreate() }
        dismissZoneLifecycle = owner

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (36 * resources.displayMetrics.density).roundToInt()
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                val hovered by isOverDismissZone.collectAsStateWithLifecycle()
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (hovered) JarvisCrimson else Color(0xE611161F),
                    border = BorderStroke(
                        1.dp,
                        if (hovered) Color.White.copy(alpha = 0.7f) else Color(0x40FFFFFF),
                    ),
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (hovered) "Bırak ve Kapat" else "Baloncuğu Kapat",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        dismissZoneView = view
        try {
            windowManager.addView(view, params)
        } catch (_: Exception) {
            removeDismissZone()
        }
    }

    private fun removeDismissZone() {
        dismissZoneView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
            }
        }
        dismissZoneView = null
        dismissZoneLifecycle?.onDestroy()
        dismissZoneLifecycle = null
    }

    private fun removeBubble() {
        removeDismissZone()
        bubbleView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        bubbleLifecycle?.onDestroy()
        bubbleLifecycle = null
        bubbleParams = null
    }

    private fun removeAllOverlayWindows() {
        removeExpandedOverlay()
        removeBubble()
        removeCrosshairWindows()
        removeLabelDialog()
        removeProactiveAlertWindow()
    }

    private fun showProactiveAlertWindow(alertId: Long) {
        serviceScope.launch {
            val entity = dao.getProactiveAlertById(alertId) ?: return@launch
            if (entity.isDismissed) return@launch
            val item = ProactiveAlertItem(
                id = entity.id,
                sourcePackage = entity.sourcePackage,
                senderName = entity.senderName,
                originalMessage = entity.originalMessage,
                summary = entity.summary,
                importanceScore = entity.importanceScore,
                importanceReason = entity.importanceReason,
                draftReply = entity.draftReply,
                messageTimestampMillis = entity.messageTimestampMillis,
                createdAtMillis = entity.createdAtMillis,
                isDismissed = entity.isDismissed,
            )

            removeProactiveAlertWindow()
            val owner = OverlayLifecycleOwner().apply { onCreate() }
            proactiveAlertLifecycle = owner

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (52 * resources.displayMetrics.density).roundToInt()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            val view = ComposeView(this@JarvisOverlayService).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setContent {
                    JarvisTheme(darkTheme = true) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ProactiveAlertCard(
                                alert = item,
                                hasDirectReply = ProactiveAlertActionCache.hasDirectReply(item.id),
                                onDraftChange = { edited ->
                                    serviceScope.launch {
                                        dao.updateProactiveDraft(item.id, edited)
                                    }
                                },
                                onCopyDraft = { draft ->
                                    ProactiveAlertActionCache.copyToClipboard(this@JarvisOverlayService, draft)
                                    android.widget.Toast.makeText(
                                        this@JarvisOverlayService,
                                        "Taslak kopyalandı.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onOpenWhatsApp = { draft ->
                                    ProactiveAlertActionCache.openWhatsAppChat(
                                        context = this@JarvisOverlayService,
                                        alertId = item.id,
                                        sourcePackage = item.sourcePackage,
                                        draftToCopy = draft,
                                    )
                                    ProactiveAlertNotifier.cancelAlertNotification(
                                        this@JarvisOverlayService,
                                        item.id
                                    )
                                    removeProactiveAlertWindow()
                                },
                                onSendDirectReply = { draft ->
                                    val sent = ProactiveAlertActionCache.sendDirectReply(
                                        context = this@JarvisOverlayService,
                                        alertId = item.id,
                                        replyText = draft,
                                    )
                                    if (sent) {
                                        android.widget.Toast.makeText(
                                            this@JarvisOverlayService,
                                            "WhatsApp yanıtı gönderildi.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        serviceScope.launch {
                                            dao.dismissProactiveAlert(item.id)
                                        }
                                        ProactiveAlertNotifier.cancelAlertNotification(
                                            this@JarvisOverlayService,
                                            item.id
                                        )
                                        ProactiveAlertActionCache.remove(item.id)
                                        removeProactiveAlertWindow()
                                    } else {
                                        ProactiveAlertActionCache.openWhatsAppChat(
                                            context = this@JarvisOverlayService,
                                            alertId = item.id,
                                            sourcePackage = item.sourcePackage,
                                            draftToCopy = draft,
                                        )
                                        removeProactiveAlertWindow()
                                    }
                                },
                                onDismiss = {
                                    serviceScope.launch {
                                        dao.dismissProactiveAlert(item.id)
                                    }
                                    ProactiveAlertNotifier.cancelAlertNotification(
                                        this@JarvisOverlayService,
                                        item.id
                                    )
                                    ProactiveAlertActionCache.remove(item.id)
                                    removeProactiveAlertWindow()
                                },
                                modifier = Modifier.widthIn(max = 540.dp),
                            )
                        }
                    }
                }
            }

            proactiveAlertView = view
            try {
                windowManager.addView(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show proactive overlay alert", e)
                removeProactiveAlertWindow()
            }
        }
    }

    private fun removeProactiveAlertWindow() {
        proactiveAlertView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
            }
        }
        proactiveAlertView = null
        proactiveAlertLifecycle?.onDestroy()
        proactiveAlertLifecycle = null
    }

    override fun onDestroy() {
        super.onDestroy()
        autoCloseJob?.cancel()
        wakeWordManager.stop()
        voiceManager.destroy()
        tts?.stop()
        tts?.shutdown()
        removeAllOverlayWindows()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "jarvis_overlay_channel"
        private const val NOTIFICATION_ID = 7001

        const val ACTION_SHOW_OVERLAY = "com.example.action.SHOW_OVERLAY"
        const val ACTION_START_CROSSHAIR = "com.example.action.START_CROSSHAIR"
        const val ACTION_SHOW_PROACTIVE_ALERT = "com.example.action.SHOW_PROACTIVE_ALERT"
        const val ACTION_SYNC_MODE = "com.example.action.SYNC_MODE"
        const val ACTION_DISABLE_ALL = "com.example.action.DISABLE_ALL_OVERLAY"
        const val ACTION_PAUSE_WAKE = "com.example.action.PAUSE_WAKE_WORD"
        const val ACTION_RESUME_WAKE = "com.example.action.RESUME_WAKE_WORD"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_OVERLAY_SERVICE"
        const val EXTRA_PROACTIVE_ALERT_ID = "extra_proactive_alert_id"

        fun showOverlay(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            startForegroundSafely(context, intent)
        }

        fun startCrosshair(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_START_CROSSHAIR
            }
            startForegroundSafely(context, intent)
        }

        fun showProactiveAlert(context: Context, alertId: Long) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_SHOW_PROACTIVE_ALERT
                putExtra(EXTRA_PROACTIVE_ALERT_ID, alertId)
            }
            startForegroundSafely(context, intent)
        }

        fun syncMode(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_SYNC_MODE
            }
            startForegroundSafely(context, intent)
        }

        fun pauseWakeWord(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_PAUSE_WAKE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun resumeWakeWord(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_RESUME_WAKE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun disableAll(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_DISABLE_ALL
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }

        private fun startForegroundSafely(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService blocked by system state", e)
            }
        }
    }
}

@Composable
private fun OverlayCardContent(
    state: OverlayUiState,
    onMicToggle: () -> Unit,
    onSendText: (String) -> Unit,
    onCaptureScreenNote: () -> Unit,
    onRecordPoint: () -> Unit,
    onCopyReply: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenFullApp: () -> Unit,
    onClose: () -> Unit,
) {
    var showTextField by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }
    var isMinimizing by remember { mutableStateOf(false) }

    val shrinkScale by animateFloatAsState(
        targetValue = if (isMinimizing) 0.22f else 1f,
        animationSpec = tween(durationMillis = 175),
        label = "overlay_shrink_scale",
    )
    val shrinkAlpha by animateFloatAsState(
        targetValue = if (isMinimizing) 0f else 1f,
        animationSpec = tween(durationMillis = 165),
        label = "overlay_shrink_alpha",
    )

    LaunchedEffect(isMinimizing) {
        if (isMinimizing) {
            delay(175L)
            onClose()
        }
    }

    val visualState = when {
        state.errorText != null -> JarvisVisualState.ERROR
        state.isListening -> JarvisVisualState.LISTENING
        state.isProcessing -> JarvisVisualState.THINKING
        state.isSpeaking -> JarvisVisualState.SPEAKING
        else -> JarvisVisualState.IDLE
    }

    val effectiveMood = if (state.errorText != null) JarvisMood.ALERT else state.mood
    val accentColor = resolveMoodAccentColor(visualState, effectiveMood)
    val dynamicHeader = remember(
        state.isListening,
        state.isProcessing,
        state.errorText,
        state.lastActionIntent,
        state.topicTitle,
    ) {
        HudTopicResolver.resolveHeader(
            isListening = state.isListening,
            isProcessing = state.isProcessing,
            hasError = state.errorText != null,
            hasProactiveAlert = false,
            latestActionIntent = state.lastActionIntent,
            aiTopicTitle = state.topicTitle,
        )
    }

    var ttsCadenceRms by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isSpeaking) {
        if (!state.isSpeaking) {
            ttsCadenceRms = 0f
            return@LaunchedEffect
        }
        var t = 0f
        while (state.isSpeaking) {
            t += 0.26f
            ttsCadenceRms = (0.28f + 0.26f * abs(sin(t)) + 0.14f * abs(sin(t * 2.2f)))
                .coerceIn(0.16f, 0.72f)
            delay(65L)
        }
    }

    val effectiveRms = when {
        state.isListening -> state.rmsLevel
        state.isSpeaking -> ttsCadenceRms
        state.isProcessing -> 0.18f
        else -> 0f
    }

    val liveSubtitleText = when {
        state.errorText != null -> state.errorText
        state.isListening -> state.partialText.ifBlank { "Dinliyorum…" }
        state.isProcessing -> "Nöral analiz yürütülüyor…"
        state.replyText.isNotBlank() -> state.replyText
        else -> ""
    }
    val liveSubtitleKey = when {
        state.errorText != null -> "err_${state.errorText}"
        state.isListening -> "listen"
        state.isProcessing -> "proc"
        state.replyText.isNotBlank() -> "reply_${state.replyText.hashCode()}"
        else -> "idle"
    }

    val historyScroll = rememberScrollState()
    LaunchedEffect(state.recentLines.size) {
        if (state.recentLines.isNotEmpty()) {
            historyScroll.animateScrollTo(historyScroll.maxValue)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .widthIn(max = 520.dp)
            .graphicsLayer {
                scaleX = shrinkScale
                scaleY = shrinkScale
                alpha = shrinkAlpha
            },
        shape = RoundedCornerShape(26.dp),
        color = Color(0xF5080B10),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        shadowElevation = 20.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Minimalist Top HUD Utility Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JARVIS",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFFF1F5F9),
                    )
                    if (state.activeProfileLabel.isNotBlank()) {
                        Text(
                            text = state.activeProfileLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7D899C),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = { showTextField = !showTextField },
                    modifier = Modifier.testTag("overlay_keyboard_toggle_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = "Yazarak komut ver",
                        tint = if (showTextField) accentColor else Color(0xFF8B97AA),
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = onToggleTts,
                    modifier = Modifier.testTag("overlay_tts_toggle_button"),
                ) {
                    Icon(
                        imageVector = if (state.ttsEnabled) {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        },
                        contentDescription = "Sesli okuma aç veya kapat",
                        tint = if (state.ttsEnabled) accentColor else Color(0xFF8B97AA),
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = { isMinimizing = true },
                    modifier = Modifier.testTag("overlay_minimize_bubble_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Yüzen baloncuğa küçült",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Contextual HUD Header
            JarvisHudHeader(
                topicHeader = dynamicHeader,
                visualState = visualState,
                mood = effectiveMood,
            )

            // Centerpiece Reactive Particle Nebula (tap to start/stop listening)
            val micToggleDesc = stringResource(R.string.cd_voice_input)
            Box(
                modifier = Modifier
                    .size(134.dp)
                    .clip(CircleShape)
                    .testTag("overlay_particle_nebula")
                    .semantics { contentDescription = micToggleDesc }
                    .clickable(onClick = onMicToggle),
                contentAlignment = Alignment.Center,
            ) {
                ParticleNebulaCanvas(
                    visualState = visualState,
                    mood = effectiveMood,
                    rmsLevel = effectiveRms,
                    compactMode = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Live Typewriter Subtitle Stream
            LiveSubtitleDisplay(
                subtitleText = liveSubtitleText,
                subtitleKey = liveSubtitleKey,
                visualState = visualState,
                mood = effectiveMood,
            )

            if (state.actionDetail.isNotBlank() && state.actionDetail != state.replyText) {
                Text(
                    text = state.actionDetail,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.actionFailed) JarvisCrimson else JarvisEmerald,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Bubble-free translucent chat log with smooth top fade-out mask
            if (state.recentLines.size > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(historyScroll)
                            .padding(top = 14.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.recentLines.forEach { (isUser, text) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (isUser) "SEN" else "JARVIS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                    ),
                                    color = if (isUser) Color(0xFF7D899C) else accentColor,
                                )
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isUser) Color(0xFF94A3B8) else Color(0xFFE2E8F0),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xF5080B10),
                                        Color.Transparent,
                                    )
                                )
                            ),
                    )
                }
            }

            AnimatedVisibility(visible = showTextField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("overlay_command_input"),
                        placeholder = { Text("Komut veya soru yaz…", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (draftText.isNotBlank()) {
                                    onSendText(draftText)
                                    draftText = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF232B38),
                            focusedTextColor = Color(0xFFF1F5F9),
                            unfocusedTextColor = Color(0xFFF1F5F9),
                        ),
                    )
                    FilledIconButton(
                        onClick = {
                            if (draftText.isNotBlank()) {
                                onSendText(draftText)
                                draftText = ""
                            }
                        },
                        enabled = draftText.isNotBlank() && !state.isProcessing,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("overlay_send_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = accentColor,
                            contentColor = Color(0xFF090B0F),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_send_command),
                        )
                    }
                }
            }

            // Compact Quick Action Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCaptureScreenNote,
                    enabled = !state.isProcessing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, Color(0xFF2B3545)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NoteAdd,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ekranı Not Al", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onRecordPoint,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, Color(0xFF2B3545)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddLocationAlt,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nokta", style = MaterialTheme.typography.labelSmall)
                }

                if (state.replyText.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCopyReply,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Color(0xFF2B3545)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                    ) {
                        Icon(
                            imageVector = if (state.copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.copied) {
                                stringResource(R.string.copied_feedback)
                            } else {
                                "Kopyala"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenFullApp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, Color(0xFF2B3545)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF1F5F9)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uygulama", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CrosshairTargetGraphic() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerR = size.minDimension * 0.42f
        val innerR = size.minDimension * 0.14f

        drawCircle(
            color = Color(0x99080B10),
            radius = outerR,
            center = center,
        )
        drawCircle(
            color = JarvisCyan,
            radius = outerR,
            center = center,
            style = Stroke(width = 3.5f),
        )
        drawCircle(
            color = JarvisAmber,
            radius = innerR,
            center = center,
        )
        // Crosshair lines
        drawLine(
            color = JarvisCyan,
            start = Offset(center.x - outerR - 8f, center.y),
            end = Offset(center.x - innerR - 4f, center.y),
            strokeWidth = 3f,
        )
        drawLine(
            color = JarvisCyan,
            start = Offset(center.x + innerR + 4f, center.y),
            end = Offset(center.x + outerR + 8f, center.y),
            strokeWidth = 3f,
        )
        drawLine(
            color = JarvisCyan,
            start = Offset(center.x, center.y - outerR - 8f),
            end = Offset(center.x, center.y - innerR - 4f),
            strokeWidth = 3f,
        )
        drawLine(
            color = JarvisCyan,
            start = Offset(center.x, center.y + innerR + 4f),
            end = Offset(center.x, center.y + outerR + 8f),
            strokeWidth = 3f,
        )
    }
}

@Composable
private fun CrosshairControlPill(
    state: CrosshairPositionState,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    val xPct = (state.xPercent * 100).roundToInt()
    val yPct = (state.yPercent * 100).roundToInt()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF00E141E),
        border = BorderStroke(1.5.dp, JarvisCyan.copy(alpha = 0.75f)),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = null,
                tint = JarvisAmber,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = state.profileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE6EEF8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "%$xPct, %$yPct  (${state.xPx}×${state.yPx} px)",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisCyan,
                )
            }
            Button(
                onClick = onSaveClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisCyan,
                    contentColor = Color(0xFF080B10),
                ),
            ) {
                Text("Kaydet", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onCancelClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text("İptal", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun SaveCoordinateDialogOverlay(
    initialPosition: CrosshairPositionState,
    installedApps: List<InstalledAppInfo>,
    onConfirmSave: (profilePackageName: String, profileName: String, label: String) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedPkg by remember { mutableStateOf(initialPosition.profilePackageName) }
    var selectedName by remember { mutableStateOf(initialPosition.profileName) }
    var labelText by remember { mutableStateOf("") }

    val quickLabels = listOf("transfer", "kadro", "devam", "tamam", "başla", "maç")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .widthIn(max = 460.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0E141E),
        border = BorderStroke(1.5.dp, JarvisCyan.copy(alpha = 0.8f)),
        shadowElevation = 20.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Dokunma Noktasını Kaydet",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE6EEF8),
            )

            Text(
                text = "Hedef Profil: $selectedName (%${(initialPosition.xPercent * 100).roundToInt()}, %${(initialPosition.yPercent * 100).roundToInt()})",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisCyan,
            )

            // Quick profile switcher in case user wants to pin a specific game/app
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val options = buildList {
                    add(initialPosition.profilePackageName to initialPosition.profileName)
                    add(CoordinateJsonStore.GENERAL_PROFILE_PACKAGE to CoordinateJsonStore.GENERAL_PROFILE_NAME)
                    installedApps.forEach { add(it.packageName to it.label) }
                }.distinctBy { it.first }

                options.forEach { (pkg, name) ->
                    val active = pkg == selectedPkg
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) JarvisCyan.copy(alpha = 0.2f) else Color(0xFF141D2B),
                        border = BorderStroke(
                            1.dp,
                            if (active) JarvisCyan else Color(0xFF283548),
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectedPkg = pkg
                                selectedName = name
                            },
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) JarvisCyan else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Komut / Nokta Adı (örn: transfer)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (labelText.isNotBlank()) {
                            onConfirmSave(selectedPkg, selectedName, labelText.trim())
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = Color(0xFF283548),
                    focusedTextColor = Color(0xFFE6EEF8),
                    unfocusedTextColor = Color(0xFFE6EEF8),
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quickLabels.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF141D2B),
                        border = BorderStroke(1.dp, Color(0xFF283548)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { labelText = preset },
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text("İptal", color = Color(0xFF94A3B8))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirmSave(selectedPkg, selectedName, labelText.trim()) },
                    enabled = labelText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = Color(0xFF080B10),
                    ),
                ) {
                    Text("Kaydet")
                }
            }
        }
    }
}



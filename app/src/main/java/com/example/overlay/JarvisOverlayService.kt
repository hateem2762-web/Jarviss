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
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CoordinateJsonStore
import com.example.data.local.JarvisDatabase
import com.example.data.preferences.JarvisPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ActionExecutionStatus
import com.example.model.ActionIntent
import com.example.model.InstalledAppInfo
import com.example.system.CoordinateMatcher
import com.example.system.JarvisActionExecutor
import com.example.system.VoiceInputManager
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisTheme
import java.util.Locale
import kotlin.math.roundToInt
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
    private val aiRepository = JarvisRepository()

    private val overlayState = MutableStateFlow(OverlayUiState())
    private val crosshairState = MutableStateFlow(CrosshairPositionState())
    private var prefsSnapshot = JarvisPreferences()

    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayLifecycle: OverlayLifecycleOwner? = null

    private var bubbleView: ComposeView? = null
    private var bubbleLifecycle: OverlayLifecycleOwner? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var crosshairReticleView: ComposeView? = null
    private var crosshairReticleParams: WindowManager.LayoutParams? = null
    private var crosshairReticleLifecycle: OverlayLifecycleOwner? = null

    private var crosshairBarView: ComposeView? = null
    private var crosshairBarLifecycle: OverlayLifecycleOwner? = null

    private var labelDialogView: ComposeView? = null
    private var labelDialogLifecycle: OverlayLifecycleOwner? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var autoCloseJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefsRepo = UserPreferencesRepository(this)
        dao = JarvisDatabase.getInstance(this).jarvisDao()
        coordinateStore = CoordinateJsonStore(this, dao)
        actionExecutor = JarvisActionExecutor(this, dao)
        voiceManager = VoiceInputManager(this)

        initTextToSpeech()
        ensureForegroundNotification()

        serviceScope.launch {
            prefsRepo.preferences.collect { prefs ->
                prefsSnapshot = prefs
                overlayState.value = overlayState.value.copy(
                    ttsEnabled = prefs.speakReplies,
                    activeProfileLabel = resolveActiveProfilePair(prefs).second,
                )
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
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("tr", "TR"))
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA &&
                    res != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundNotification()

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
            ACTION_SYNC_MODE -> {
                serviceScope.launch {
                    val prefs = prefsRepo.preferences.first()
                    prefsSnapshot = prefs
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
                        overlayView == null &&
                        crosshairReticleView == null
                    ) {
                        stopSelf()
                    }
                }
            }
            ACTION_STOP_SERVICE -> {
                removeAllOverlayWindows()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun ensureForegroundNotification() {
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

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis_tile)
            .setContentTitle("JARVIS Hızlı Asistan")
            .setContentText("Konuş veya ekrandan dokunma noktası kaydet")
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
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

    private fun showExpandedOverlay() {
        autoCloseJob?.cancel()
        removeBubble()
        removeCrosshairWindows()
        removeLabelDialog()

        if (overlayView != null) {
            startOverlayListening()
            return
        }

        val (_, activeLabel) = resolveActiveProfilePair(prefsSnapshot)
        overlayState.value = OverlayUiState(
            ttsEnabled = prefsSnapshot.speakReplies,
            activeProfileLabel = activeLabel,
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
                        onClose = { collapseOrStopOverlay() },
                    )
                }
            }
        }

        overlayView = composeView
        try {
            windowManager.addView(composeView, params)
            startOverlayListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach overlay view", e)
            removeExpandedOverlay()
        }
    }

    private fun startOverlayListening() {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            overlayState.value = overlayState.value.copy(
                errorText = getString(R.string.mic_permission_needed)
            )
            return
        }

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
        overlayState.value = overlayState.value.copy(
            userText = query,
            isProcessing = true,
            errorText = null,
            actionDetail = "",
            actionFailed = false,
            copied = false,
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
                    overlayState.value = overlayState.value.copy(
                        replyText = replyText,
                        ttsSummary = replyText,
                        actionDetail = "${execResult.detail} (0 token)",
                        actionFailed = execResult.status == ActionExecutionStatus.FAILED,
                        isProcessing = false,
                    )
                    if (execResult.status == ActionExecutionStatus.EXECUTED) {
                        autoCloseJob = serviceScope.launch {
                            delay(900L)
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

                        overlayState.value = overlayState.value.copy(
                            replyText = reply.text,
                            ttsSummary = reply.ttsSummary,
                            isProcessing = false,
                        )

                        if (overlayState.value.ttsEnabled && ttsReady) {
                            tts?.speak(
                                reply.ttsSummary,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "jarvis_overlay_$insertedId"
                            )
                        }

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
                            overlayState.value = overlayState.value.copy(
                                actionDetail = execResult.detail,
                                actionFailed = execResult.status == ActionExecutionStatus.FAILED,
                            )

                            val shouldAutoClose = execResult.status == ActionExecutionStatus.EXECUTED &&
                                action.intent in setOf(
                                    ActionIntent.OPEN_APP,
                                    ActionIntent.OPEN_URL,
                                    ActionIntent.SEARCH_YOUTUBE,
                                    ActionIntent.GO_HOME,
                                    ActionIntent.TAP_COORDINATE,
                                )
                            if (shouldAutoClose) {
                                autoCloseJob = serviceScope.launch {
                                    delay(950L)
                                    collapseOrStopOverlay()
                                }
                            }
                        }
                    },
                    onFailure = { err ->
                        Log.w(TAG, "Gemini failed for userMsg=$userMsgId: ${err.message}")
                        overlayState.value = overlayState.value.copy(
                            isProcessing = false,
                            errorText = err.localizedMessage ?: "Yanıt alınamadı.",
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Overlay query failed", e)
                overlayState.value = overlayState.value.copy(
                    isProcessing = false,
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
        }
    }

    private fun collapseOrStopOverlay() {
        autoCloseJob?.cancel()
        voiceManager.stopListening()
        removeExpandedOverlay()
        if (prefsSnapshot.floatingBubbleEnabled && Settings.canDrawOverlays(this)) {
            showBubble()
        } else if (!prefsSnapshot.persistentTriggerEnabled) {
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
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0E14))
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(listOf(JarvisCyan, JarvisAmber)),
                            shape = CircleShape,
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                bubbleParams?.let { lp ->
                                    lp.x += dragAmount.x.roundToInt()
                                    lp.y += dragAmount.y.roundToInt()
                                    try {
                                        windowManager.updateViewLayout(this@apply, lp)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                        .clickable { showExpandedOverlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_core),
                        contentDescription = stringResource(R.string.speak_to_jarvis),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
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

    private fun removeBubble() {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        autoCloseJob?.cancel()
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
        const val ACTION_SYNC_MODE = "com.example.action.SYNC_MODE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_OVERLAY_SERVICE"

        fun showOverlay(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startCrosshair(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_START_CROSSHAIR
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun syncMode(context: Context) {
            val intent = Intent(context, JarvisOverlayService::class.java).apply {
                action = ACTION_SYNC_MODE
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

@Composable
private fun OverlayCardContent(
    state: OverlayUiState,
    onMicToggle: () -> Unit,
    onSendText: (String) -> Unit,
    onRecordPoint: () -> Unit,
    onCopyReply: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenFullApp: () -> Unit,
    onClose: () -> Unit,
) {
    var showTextField by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .widthIn(max = 540.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0E141E),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                listOf(JarvisCyan.copy(alpha = 0.85f), JarvisAmber.copy(alpha = 0.55f))
            ),
        ),
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, JarvisCyan, CircleShape),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_core),
                        contentDescription = stringResource(R.string.cd_jarvis_core),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JARVIS",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE6EEF8),
                    )
                    Text(
                        text = when {
                            state.isListening -> "Dinleniyor…"
                            state.isProcessing -> "Düşünüyor…"
                            state.activeProfileLabel.isNotBlank() -> "Profil: ${state.activeProfileLabel}"
                            else -> "Hızlı Komut Penceresi"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isListening) JarvisCrimson else JarvisCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onRecordPoint) {
                    Icon(
                        imageVector = Icons.Rounded.AddLocationAlt,
                        contentDescription = stringResource(R.string.record_point),
                        tint = JarvisAmber,
                    )
                }

                IconButton(onClick = { showTextField = !showTextField }) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = "Yazarak komut ver",
                        tint = if (showTextField) JarvisCyan else Color(0xFF94A3B8),
                    )
                }

                IconButton(onClick = onToggleTts) {
                    Icon(
                        imageVector = if (state.ttsEnabled) {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        },
                        contentDescription = "Sesli okuma aç veya kapat",
                        tint = if (state.ttsEnabled) JarvisCyan else Color(0xFF94A3B8),
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close_overlay),
                        tint = Color(0xFF94A3B8),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141D2B))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledIconButton(
                    onClick = onMicToggle,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.isListening) JarvisCrimson else JarvisCyan,
                        contentColor = if (state.isListening) Color.White else Color(0xFF080B10),
                    ),
                ) {
                    Icon(
                        imageVector = if (state.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = stringResource(R.string.cd_voice_input),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (state.isListening) {
                        VoiceWaveEqualizer(
                            rmsLevel = state.rmsLevel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                        )
                        if (state.partialText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.partialText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE6EEF8),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (state.isProcessing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = JarvisCyan,
                            )
                            Text(
                                text = state.userText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = state.userText.ifBlank { "Konuş, yaz veya + simgesiyle ekrandan nokta kaydet." },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.userText.isBlank()) Color(0xFF94A3B8) else Color(0xFFE6EEF8),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        modifier = Modifier.weight(1f),
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
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = Color(0xFF283548),
                            focusedTextColor = Color(0xFFE6EEF8),
                            unfocusedTextColor = Color(0xFFE6EEF8),
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
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = JarvisAmber,
                            contentColor = Color(0xFF080B10),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_send_command),
                        )
                    }
                }
            }

            if (state.errorText != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF4C0519),
                ) {
                    Text(
                        text = state.errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFECDD3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    )
                }
            }

            if (state.replyText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141D2B),
                    border = BorderStroke(1.dp, Color(0xFF283548)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = state.replyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE6EEF8),
                        )

                        if (state.actionDetail.isNotBlank()) {
                            Text(
                                text = state.actionDetail,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.actionFailed) JarvisCrimson else JarvisEmerald,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCopyReply,
                    enabled = state.replyText.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                ) {
                    Icon(
                        imageVector = if (state.copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (state.copied) {
                            stringResource(R.string.copied_feedback)
                        } else {
                            stringResource(R.string.copy_to_clipboard)
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                OutlinedButton(
                    onClick = onOpenFullApp,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE6EEF8)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.expand_fullscreen),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.close_overlay),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                    )
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

@Composable
private fun VoiceWaveEqualizer(
    rmsLevel: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        val barCount = 18
        val gap = size.width / (barCount * 2f)
        val barWidth = gap
        val ampBoost = (0.35f + rmsLevel * 0.65f).coerceIn(0.3f, 1f)

        for (i in 0 until barCount) {
            val factor = kotlin.math.abs(kotlin.math.sin((i * 0.55f + phase * 3.14f).toDouble())).toFloat()
            val barHeight = (size.height * (0.22f + factor * 0.78f * ampBoost)).coerceAtLeast(4f)
            val x = i * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = if (i % 3 == 0) JarvisAmber else JarvisCyan,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

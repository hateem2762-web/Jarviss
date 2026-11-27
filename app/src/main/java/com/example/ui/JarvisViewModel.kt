package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessibility.JarvisAccessibilityService
import com.example.ai.AppCheckStatusInfo
import com.example.ai.JarvisRepository
import com.example.ai.clearCustomDebugToken
import com.example.ai.generateAndSaveDebugToken
import com.example.ai.getAppCheckStatus
import com.example.ai.saveCustomDebugToken
import com.example.automation.ScheduledMessageParser
import com.example.automation.ScheduledMessageReceiver
import com.example.automation.ScheduledMessageScheduler
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CoordinateJsonStore
import com.example.data.local.JarvisDatabase
import com.example.data.local.NoteEntity
import com.example.data.local.NoteJsonStore
import com.example.data.local.ProactiveAlertEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.ScheduledMessageLogEntity
import com.example.data.local.ScheduledMessageRuleEntity
import com.example.data.local.TapCoordinateEntity
import com.example.data.local.toDomain
import com.example.data.preferences.JarvisPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ActionExecutionStatus
import com.example.model.ActionIntent
import com.example.model.HudTopicResolver
import com.example.model.InstalledAppInfo
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
import com.example.model.JarvisMood
import com.example.model.NoteItem
import com.example.model.ProactiveAlertItem
import com.example.model.ReminderItem
import com.example.model.ScheduledMessageLogItem
import com.example.model.ScheduledMessageRuleItem
import com.example.model.TapCoordinateItem
import com.example.model.VisualCueItem
import com.example.model.VisualCueResolver
import com.example.news.HandSwipeGesture
import com.example.news.NewsBriefingState
import com.example.news.RssNewsFetcher
import com.example.notifications.ProactiveAlertActionCache
import com.example.notifications.ProactiveAlertNotifier
import com.example.overlay.JarvisOverlayService
import com.example.system.AppliedVoiceInfo
import com.example.system.CoordinateMatcher
import com.example.system.JarvisActionExecutor
import com.example.system.JarvisTtsVoiceHelper
import com.example.system.LocalQuickCommandMatcher
import com.example.system.ReminderScheduler
import com.example.system.VoiceInputManager
import com.example.system.VoiceInputState
import com.example.webagent.WebAgentUiState
import com.example.webagent.WebDownloadAgent
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "JarvisViewModel"

data class JarvisUiState(
    val messages: List<JarvisMessage> = emptyList(),
    val reminders: List<ReminderItem> = emptyList(),
    val coordinates: List<TapCoordinateItem> = emptyList(),
    val proactiveAlerts: List<ProactiveAlertItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val scheduledMessageRules: List<ScheduledMessageRuleItem> = emptyList(),
    val scheduledMessageLogs: List<ScheduledMessageLogItem> = emptyList(),
    val preferences: JarvisPreferences = JarvisPreferences(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val voiceState: VoiceInputState = VoiceInputState(),
    val detectedForegroundPackage: String? = null,
    val detectedForegroundName: String = "",
    val isAccessibilityConnected: Boolean = false,
    val isProcessing: Boolean = false,
    val speakingMessageId: Long? = null,
    val errorMessage: String? = null,
    val lastFailedPrompt: String? = null,
    val toastMessage: String? = null,
    val appCheckStatus: AppCheckStatusInfo = AppCheckStatusInfo(),
    val currentMood: JarvisMood = JarvisMood.NEUTRAL,
    val currentTopicTitle: String = "",
    val newsBriefing: NewsBriefingState = NewsBriefingState(),
    val ttsVoiceInfo: AppliedVoiceInfo = AppliedVoiceInfo(),
    val webAgentState: WebAgentUiState = WebAgentUiState(),
    val visualCues: List<VisualCueItem> = emptyList(),
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = JarvisDatabase.getInstance(application).jarvisDao()
    private val coordinateStore = CoordinateJsonStore(application, dao)
    private val noteStore = NoteJsonStore(application, dao)
    private val prefsRepo = UserPreferencesRepository(application)
    private val aiRepository = JarvisRepository()
    private val rssFetcher = RssNewsFetcher()
    private val actionExecutor = JarvisActionExecutor(application, dao, aiRepository)
    private val voiceManager = VoiceInputManager(application)
    private val webDownloadAgent = WebDownloadAgent(application)

    private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isProcessingFlow = MutableStateFlow(false)
    private val speakingMessageIdFlow = MutableStateFlow<Long?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val lastFailedPromptFlow = MutableStateFlow<String?>(null)
    private val toastFlow = MutableStateFlow<String?>(null)
    private val appCheckStatusFlow = MutableStateFlow(getAppCheckStatus(application))
    private val currentMoodFlow = MutableStateFlow(JarvisMood.NEUTRAL)
    private val currentTopicTitleFlow = MutableStateFlow("")
    private val newsBriefingFlow = MutableStateFlow(NewsBriefingState())
    private val ttsVoiceInfoFlow = MutableStateFlow(AppliedVoiceInfo())
    private val visualCuesFlow = MutableStateFlow<List<VisualCueItem>>(emptyList())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var latestPrefs = JarvisPreferences()
    private var voiceConfiguredFromPrefs = false
    private var activeNewsUtteranceId: String? = null
    private var newsFetchJob: Job? = null
    private var gestureBadgeResetJob: Job? = null
    private var visualCueResetJob: Job? = null

    private data class ExtrasPersistentSnapshot(
        val preferences: JarvisPreferences,
        val notes: List<NoteEntity>,
        val scheduledRules: List<ScheduledMessageRuleEntity>,
        val scheduledLogs: List<ScheduledMessageLogEntity>,
    )

    private data class PersistentSnapshot(
        val messages: List<ChatMessageEntity>,
        val reminders: List<ReminderEntity>,
        val coordinates: List<TapCoordinateEntity>,
        val proactiveAlerts: List<ProactiveAlertEntity>,
        val extras: ExtrasPersistentSnapshot,
    )

    private data class RuntimeSnapshot(
        val apps: List<InstalledAppInfo>,
        val voice: VoiceInputState,
        val foregroundPkg: String?,
        val accessibilityConnected: Boolean,
        val appCheckStatus: AppCheckStatusInfo,
    )

    private data class TransientSnapshot(
        val processing: Boolean,
        val speakingId: Long?,
        val error: String?,
        val failedPrompt: String?,
        val toast: String?,
    )

    private data class HudVisualSnapshot(
        val mood: JarvisMood,
        val topicTitle: String,
        val voiceInfo: AppliedVoiceInfo,
        val visualCues: List<VisualCueItem>,
        val webAgentState: WebAgentUiState,
    )

    private val extrasPersistentFlow: Flow<ExtrasPersistentSnapshot> = combine(
        prefsRepo.preferences,
        dao.observeNotes(),
        dao.observeScheduledRules(),
        dao.observeScheduledLogs(),
    ) { prefs, notes, rules, logs ->
        ExtrasPersistentSnapshot(
            preferences = prefs,
            notes = notes,
            scheduledRules = rules,
            scheduledLogs = logs,
        )
    }

    private val persistentFlow: Flow<PersistentSnapshot> = combine(
        dao.observeMessages(),
        dao.observeReminders(),
        dao.observeCoordinates(),
        dao.observeActiveProactiveAlerts(),
        extrasPersistentFlow,
    ) { msgEntities, remEntities, coordEntities, alertEntities, extras ->
        PersistentSnapshot(
            messages = msgEntities,
            reminders = remEntities,
            coordinates = coordEntities,
            proactiveAlerts = alertEntities,
            extras = extras,
        )
    }

    private val runtimeFlow: Flow<RuntimeSnapshot> = combine(
        installedAppsFlow,
        voiceManager.state,
        JarvisAccessibilityService.currentForegroundPackage,
        JarvisAccessibilityService.isServiceConnected,
        appCheckStatusFlow,
    ) { apps, voice, fgPkg, a11yConnected, appCheck ->
        RuntimeSnapshot(
            apps = apps,
            voice = voice,
            foregroundPkg = fgPkg,
            accessibilityConnected = a11yConnected,
            appCheckStatus = appCheck,
        )
    }

    private val transientFlow: Flow<TransientSnapshot> = combine(
        isProcessingFlow,
        speakingMessageIdFlow,
        errorFlow,
        lastFailedPromptFlow,
        toastFlow,
    ) { processing, speakingId, error, failedPrompt, toast ->
        TransientSnapshot(
            processing = processing,
            speakingId = speakingId,
            error = error,
            failedPrompt = failedPrompt,
            toast = toast,
        )
    }

    private val hudMetaFlow: Flow<HudVisualSnapshot> = combine(
        currentMoodFlow,
        currentTopicTitleFlow,
        ttsVoiceInfoFlow,
        visualCuesFlow,
        webDownloadAgent.state,
    ) { mood, topic, voiceInfo, cues, webState ->
        HudVisualSnapshot(
            mood = mood,
            topicTitle = topic,
            voiceInfo = voiceInfo,
            visualCues = cues,
            webAgentState = webState,
        )
    }

    val uiState: StateFlow<JarvisUiState> = combine(
        persistentFlow,
        runtimeFlow,
        transientFlow,
        hudMetaFlow,
        newsBriefingFlow,
    ) { persistent, runtime, transient, hudMeta, newsState ->
        val fgPkg = runtime.foregroundPkg
        val fgName = if (!fgPkg.isNullOrBlank()) {
            actionExecutor.resolveAppDisplayName(fgPkg)
        } else {
            ""
        }

        JarvisUiState(
            messages = persistent.messages.map { it.toDomain() },
            reminders = persistent.reminders.map { entity ->
                val label = entity.durationLabel.ifBlank {
                    when {
                        (entity.secondsFromNow ?: 0) > 0 -> "${entity.secondsFromNow} sn"
                        (entity.minutesFromNow ?: 0) > 0 -> "${entity.minutesFromNow} dk"
                        else -> ""
                    }
                }
                ReminderItem(
                    id = entity.id,
                    message = entity.message,
                    triggerAtMillis = entity.triggerAtMillis,
                    durationLabel = label,
                    isCompleted = entity.isCompleted,
                    createdAtMillis = entity.createdAtMillis,
                )
            },
            coordinates = persistent.coordinates.map { it.toDomain() },
            proactiveAlerts = persistent.proactiveAlerts.map { it.toProactiveDomain() },
            notes = persistent.extras.notes.map { it.toDomain() },
            scheduledMessageRules = persistent.extras.scheduledRules.map { it.toDomain() },
            scheduledMessageLogs = persistent.extras.scheduledLogs.map { it.toDomain() },
            preferences = persistent.extras.preferences,
            installedApps = runtime.apps,
            voiceState = runtime.voice,
            detectedForegroundPackage = fgPkg,
            detectedForegroundName = fgName,
            isAccessibilityConnected = runtime.accessibilityConnected,
            isProcessing = transient.processing,
            speakingMessageId = transient.speakingId,
            errorMessage = transient.error,
            lastFailedPrompt = transient.failedPrompt,
            toastMessage = transient.toast,
            appCheckStatus = runtime.appCheckStatus,
            currentMood = hudMeta.mood,
            currentTopicTitle = hudMeta.topicTitle,
            newsBriefing = newsState,
            ttsVoiceInfo = hudMeta.voiceInfo,
            webAgentState = hudMeta.webAgentState,
            visualCues = hudMeta.visualCues,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JarvisUiState(),
    )

    private var wasListeningInApp = false

    init {
        refreshInstalledApps()
        initTextToSpeech()
        viewModelScope.launch {
            prefsRepo.preferences.collect { prefs ->
                val voiceChanged = prefs.maleVoiceEnabled != latestPrefs.maleVoiceEnabled ||
                    prefs.selectedTtsVoiceName != latestPrefs.selectedTtsVoiceName
                latestPrefs = prefs
                if (ttsReady && (!voiceConfiguredFromPrefs || voiceChanged)) {
                    voiceConfiguredFromPrefs = true
                    ttsVoiceInfoFlow.value = JarvisTtsVoiceHelper.configureTurkishVoice(tts, prefs)
                }
            }
        }
        viewModelScope.launch {
            voiceManager.state.collect { voice ->
                if (wasListeningInApp && !voice.isListening && uiState.value.preferences.wakeWordEnabled) {
                    JarvisOverlayService.resumeWakeWord(getApplication())
                }
                wasListeningInApp = voice.isListening
            }
        }
    }

    private fun initTextToSpeech() {
        try {
            tts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res = tts?.setLanguage(Locale("tr", "TR"))
                    ttsReady = res != TextToSpeech.LANG_MISSING_DATA &&
                        res != TextToSpeech.LANG_NOT_SUPPORTED
                    if (ttsReady) {
                        voiceConfiguredFromPrefs = true
                        ttsVoiceInfoFlow.value = JarvisTtsVoiceHelper.configureTurkishVoice(tts, latestPrefs)
                    }
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId != null && utteranceId.startsWith("jarvis_news_")) {
                                viewModelScope.launch {
                                    onNewsUtteranceFinished(utteranceId)
                                }
                            } else {
                                speakingMessageIdFlow.value = null
                                scheduleVisualCueClear(delayMs = 2_400L)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId != null && utteranceId.startsWith("jarvis_news_")) {
                                viewModelScope.launch {
                                    if (utteranceId == activeNewsUtteranceId) {
                                        activeNewsUtteranceId = null
                                        newsBriefingFlow.value = newsBriefingFlow.value.copy(isSpeakingBriefing = false)
                                    }
                                }
                            } else {
                                speakingMessageIdFlow.value = null
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech init failed safely", e)
            ttsReady = false
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            installedAppsFlow.value = actionExecutor.getInstalledLauncherApps()
        }
    }

    fun sendCommand(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || isProcessingFlow.value) return
        dispatchCommand(text = text, insertUserBubble = true)
    }

    fun retryLastCommand() {
        val failed = lastFailedPromptFlow.value?.trim().orEmpty()
        if (failed.isEmpty() || isProcessingFlow.value) return
        dispatchCommand(text = failed, insertUserBubble = false)
    }

    private fun resolveActiveProfilePair(
        prefs: JarvisPreferences,
        detectedForegroundPkg: String?,
    ): Pair<String, String> {
        val selfPkg = getApplication<Application>().packageName
        val detected = detectedForegroundPkg?.takeIf { it.isNotBlank() && it != selfPkg }
        val pkg = detected
            ?: prefs.manualProfilePackageName.takeIf { it.isNotBlank() }
            ?: CoordinateJsonStore.GENERAL_PROFILE_PACKAGE
        val name = when {
            detected != null -> actionExecutor.resolveAppDisplayName(detected)
            prefs.manualProfileName.isNotBlank() -> prefs.manualProfileName
            else -> actionExecutor.resolveAppDisplayName(pkg)
        }
        return pkg to name
    }

    private fun dispatchCommand(text: String, insertUserBubble: Boolean) {
        errorFlow.value = null
        lastFailedPromptFlow.value = null
        voiceManager.clearError()

        viewModelScope.launch {
            isProcessingFlow.value = true
            try {
                val current = uiState.value
                val snapshotMessages = current.messages
                val snapshotPrefs = current.preferences
                val snapshotApps = actionExecutor.getInstalledLauncherApps().also {
                    installedAppsFlow.value = it
                }

                if (insertUserBubble) {
                    dao.insertMessage(
                        ChatMessageEntity(
                            isUser = true,
                            text = text,
                            timestampMillis = System.currentTimeMillis(),
                        )
                    )
                }

                val (activeProfilePkg, activeProfileLabel) = resolveActiveProfilePair(
                    prefs = snapshotPrefs,
                    detectedForegroundPkg = current.detectedForegroundPackage,
                )
                val activeCoordinates = coordinateStore.getCoordinatesForActiveOrFallback(activeProfilePkg)

                // 0-Token Fast Path 1: saved screen coordinate
                val zeroTokenMatch = CoordinateMatcher.findZeroTokenMatch(text, activeCoordinates)
                if (zeroTokenMatch != null) {
                    val execResult = actionExecutor.executeTapOnCoordinate(zeroTokenMatch)
                    val replyText = if (execResult.status == ActionExecutionStatus.EXECUTED) {
                        "\"${zeroTokenMatch.label}\" noktasına dokunuluyor."
                    } else {
                        execResult.detail
                    }
                    currentMoodFlow.value = if (execResult.status == ActionExecutionStatus.FAILED) {
                        JarvisMood.ALERT
                    } else {
                        JarvisMood.ENERGETIC
                    }
                    currentTopicTitleFlow.value = "HEDEF KİLİTLEME // OTOMASYON"
                    val insertedId = dao.insertMessage(
                        ChatMessageEntity(
                            isUser = false,
                            text = replyText,
                            intentWire = ActionIntent.TAP_COORDINATE.wireValue,
                            target = zeroTokenMatch.label,
                            actionStatus = execResult.status.name,
                            actionStatusDetail = execResult.detail,
                            timestampMillis = System.currentTimeMillis(),
                        )
                    )
                    speakIfEnabled(snapshotPrefs.speakReplies, insertedId, replyText)
                    return@launch
                }

                // 0-Token Fast Path 2: unambiguous local device/screen-note/news commands
                val localAction = LocalQuickCommandMatcher.match(
                    rawPrompt = text,
                    isNewsModeActive = current.newsBriefing.isVisible,
                )
                if (localAction != null) {
                    if (localAction.intent == ActionIntent.OPEN_NEWS_BRIEFING) {
                        val topic = localAction.target.trim()
                        currentMoodFlow.value = JarvisMood.SERIOUS
                        currentTopicTitleFlow.value = if (topic.isNotBlank()) {
                            "GÜNDEM // ${topic.uppercase(Locale("tr", "TR"))}"
                        } else {
                            "JARVIS // GÜNDEM"
                        }
                        dao.insertMessage(
                            ChatMessageEntity(
                                isUser = false,
                                text = if (topic.isNotBlank()) {
                                    "\"$topic\" ile ilgili güncel haberler hazırlanıyor."
                                } else {
                                    "Güncel gündem bülteni hazırlanıyor."
                                },
                                intentWire = localAction.intent.wireValue,
                                target = topic,
                                actionStatus = ActionExecutionStatus.EXECUTED.name,
                                actionStatusDetail = "Haber sunumu açıldı.",
                                timestampMillis = System.currentTimeMillis(),
                            )
                        )
                        openNewsBriefing(topic)
                        return@launch
                    }
                    if (localAction.intent == ActionIntent.NEWS_CONTROL) {
                        handleNewsControl(localAction.target)
                        return@launch
                    }
                    if (localAction.intent == ActionIntent.WEB_DOWNLOAD_AGENT) {
                        triggerWebAgentTask(
                            rawTarget = localAction.target,
                            confirmBeforeDownload = snapshotPrefs.webAgentConfirmBeforeDownload,
                            insertReplyMessage = true,
                        )
                        return@launch
                    }

                    val execResult = actionExecutor.execute(
                        action = localAction,
                        activeProfilePackage = activeProfilePkg,
                    )
                    val replyText = execResult.detail.ifBlank { "${localAction.intent.displayLabel} tamamlandı." }
                    currentMoodFlow.value = if (execResult.status == ActionExecutionStatus.FAILED) {
                        JarvisMood.ALERT
                    } else {
                        HudTopicResolver.moodForAction(localAction.intent, JarvisMood.ENERGETIC)
                    }
                    currentTopicTitleFlow.value = HudTopicResolver.resolveHeader(
                        isListening = false,
                        isProcessing = false,
                        hasError = execResult.status == ActionExecutionStatus.FAILED,
                        hasProactiveAlert = false,
                        latestActionIntent = localAction.intent,
                        aiTopicTitle = "",
                    )
                    val insertedId = dao.insertMessage(
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
                    showVisualCuesForNarration(
                        aiCues = emptyList(),
                        text = replyText,
                        actionIntent = localAction.intent,
                    )
                    speakIfEnabled(snapshotPrefs.speakReplies, insertedId, replyText)
                    return@launch
                }

                val result = aiRepository.askJarvis(
                    userPrompt = text,
                    userContext = snapshotPrefs.userContext,
                    recentMessages = snapshotMessages,
                    installedApps = snapshotApps,
                    savedCoordinateLabels = activeCoordinates.map { it.label }.distinct(),
                    activeProfileLabel = activeProfileLabel,
                )

                result.fold(
                    onSuccess = { reply ->
                        val action = reply.action
                        currentMoodFlow.value = action?.let {
                            HudTopicResolver.moodForAction(it.intent, reply.mood)
                        } ?: reply.mood
                        currentTopicTitleFlow.value = HudTopicResolver.resolveHeader(
                            isListening = false,
                            isProcessing = false,
                            hasError = false,
                            hasProactiveAlert = false,
                            latestActionIntent = action?.intent,
                            aiTopicTitle = reply.topicTitle,
                        )
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

                        if (action?.intent == ActionIntent.OPEN_NEWS_BRIEFING) {
                            val topic = action.target.trim()
                            dao.updateMessageActionStatus(
                                id = insertedId,
                                status = ActionExecutionStatus.EXECUTED.name,
                                detail = "Haber sunumu açıldı.",
                            )
                            openNewsBriefing(topic)
                            return@fold
                        }
                        if (action?.intent == ActionIntent.NEWS_CONTROL) {
                            dao.updateMessageActionStatus(
                                id = insertedId,
                                status = ActionExecutionStatus.EXECUTED.name,
                                detail = "Haber kontrolü uygulandı.",
                            )
                            handleNewsControl(action.target)
                            return@fold
                        }
                        if (action?.intent == ActionIntent.WEB_DOWNLOAD_AGENT) {
                            dao.updateMessageActionStatus(
                                id = insertedId,
                                status = ActionExecutionStatus.EXECUTED.name,
                                detail = "Web indirme ajanı başlatıldı.",
                            )
                            triggerWebAgentTask(
                                rawTarget = action.target.ifBlank { text },
                                confirmBeforeDownload = snapshotPrefs.webAgentConfirmBeforeDownload,
                                insertReplyMessage = false,
                            )
                            return@fold
                        }

                        var spokenText = reply.ttsSummary
                        if (action != null && snapshotPrefs.autoExecuteActions) {
                            val execResult = actionExecutor.execute(
                                action = action,
                                activeProfilePackage = activeProfilePkg,
                            )
                            dao.updateMessageActionStatus(
                                id = insertedId,
                                status = execResult.status.name,
                                detail = execResult.detail,
                            )
                            if (execResult.status == ActionExecutionStatus.FAILED) {
                                currentMoodFlow.value = JarvisMood.ALERT
                            }
                            if (
                                action.intent == ActionIntent.BATTERY_STATUS ||
                                action.intent == ActionIntent.TORCH_CONTROL ||
                                action.intent == ActionIntent.VOLUME_CONTROL ||
                                action.intent == ActionIntent.CAPTURE_SCREEN_NOTE ||
                                action.intent == ActionIntent.SCHEDULE_MESSAGE
                            ) {
                                if (execResult.detail.isNotBlank()) {
                                    spokenText = execResult.detail
                                }
                            }
                        }

                        showVisualCuesForNarration(
                            aiCues = reply.visualCues,
                            text = reply.text,
                            actionIntent = action?.intent,
                        )
                        speakIfEnabled(snapshotPrefs.speakReplies, insertedId, spokenText)
                    },
                    onFailure = { throwable ->
                        Log.e(TAG, "askJarvis failed", throwable)
                        currentMoodFlow.value = JarvisMood.ALERT
                        currentTopicTitleFlow.value = "SİSTEM UYARISI // KRİTİK"
                        lastFailedPromptFlow.value = text
                        errorFlow.value = throwable.localizedMessage
                            ?: "JARVIS yanıt verirken bir hata oluştu."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "dispatchCommand failed", e)
                currentMoodFlow.value = JarvisMood.ALERT
                currentTopicTitleFlow.value = "SİSTEM UYARISI // KRİTİK"
                lastFailedPromptFlow.value = text
                errorFlow.value = e.localizedMessage
                    ?: "Komut işlenirken beklenmeyen bir hata oluştu."
            } finally {
                isProcessingFlow.value = false
            }
        }
    }

    private fun speakIfEnabled(enabled: Boolean, messageId: Long, text: String) {
        if (enabled && ttsReady && text.isNotBlank()) {
            speakingMessageIdFlow.value = messageId
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_$messageId"
            )
        }
    }

    fun executeActionForMessage(messageId: Long, action: JarvisAction) {
        if (action.intent == ActionIntent.WEB_DOWNLOAD_AGENT) {
            triggerWebAgentTask(
                rawTarget = action.target,
                confirmBeforeDownload = uiState.value.preferences.webAgentConfirmBeforeDownload,
                insertReplyMessage = false,
            )
            return
        }
        viewModelScope.launch {
            val current = uiState.value
            val (activeProfilePkg, _) = resolveActiveProfilePair(
                prefs = current.preferences,
                detectedForegroundPkg = current.detectedForegroundPackage,
            )
            val execResult = actionExecutor.execute(
                action = action,
                activeProfilePackage = activeProfilePkg,
            )
            dao.updateMessageActionStatus(
                id = messageId,
                status = execResult.status.name,
                detail = execResult.detail,
            )
            if (execResult.detail.isNotBlank()) {
                toastFlow.value = execResult.detail
            }
        }
    }

    // ---- Visual Narration Cues & Web Agent & Scheduled Messaging ----

    private fun showVisualCuesForNarration(
        aiCues: List<String>,
        text: String,
        actionIntent: ActionIntent? = null,
    ) {
        val cues = VisualCueResolver.resolveCues(aiCues, text, actionIntent)
        visualCuesFlow.value = cues
        val isSpeakingNow = uiState.value.preferences.speakReplies && ttsReady
        if (!isSpeakingNow) {
            scheduleVisualCueClear(delayMs = 11_000L)
        } else {
            visualCueResetJob?.cancel()
        }
    }

    private fun scheduleVisualCueClear(delayMs: Long) {
        visualCueResetJob?.cancel()
        visualCueResetJob = viewModelScope.launch {
            delay(delayMs)
            if (speakingMessageIdFlow.value == null) {
                visualCuesFlow.value = emptyList()
            }
        }
    }

    private fun triggerWebAgentTask(
        rawTarget: String,
        confirmBeforeDownload: Boolean,
        insertReplyMessage: Boolean,
    ) {
        val target = rawTarget.trim()
        currentMoodFlow.value = JarvisMood.ANALYTICAL
        currentTopicTitleFlow.value = "WEB AJANI // İNDİRME"
        showVisualCuesForNarration(
            aiCues = listOf("globe_network", "download_file"),
            text = target,
            actionIntent = ActionIntent.WEB_DOWNLOAD_AGENT,
        )

        if (insertReplyMessage) {
            viewModelScope.launch {
                dao.insertMessage(
                    ChatMessageEntity(
                        isUser = false,
                        text = "Web ajanı görevlendirildi: \"$target\"",
                        intentWire = ActionIntent.WEB_DOWNLOAD_AGENT.wireValue,
                        target = target,
                        actionStatus = ActionExecutionStatus.EXECUTED.name,
                        actionStatusDetail = "Site taranıyor…",
                        timestampMillis = System.currentTimeMillis(),
                    )
                )
            }
        }

        webDownloadAgent.startAutonomousTask(
            rawRequest = target,
            confirmBeforeDownload = confirmBeforeDownload,
        ) { statusMsg, isError ->
            if (isError) {
                currentMoodFlow.value = JarvisMood.ALERT
            }
            if (latestPrefs.speakReplies && ttsReady) {
                tts?.speak(statusMsg, TextToSpeech.QUEUE_FLUSH, null, "jarvis_web_agent_${System.nanoTime()}")
            }
        }
    }

    fun confirmWebDownload() {
        webDownloadAgent.confirmPendingDownload { statusMsg, isError ->
            if (isError) currentMoodFlow.value = JarvisMood.ALERT
            if (latestPrefs.speakReplies && ttsReady) {
                tts?.speak(statusMsg, TextToSpeech.QUEUE_FLUSH, null, "jarvis_web_agent_${System.nanoTime()}")
            }
        }
    }

    fun dismissWebAgent() {
        webDownloadAgent.cancelOrDismiss()
        currentTopicTitleFlow.value = ""
    }

    fun openCompletedWebDownload() {
        val (_, detail) = webDownloadAgent.openDownloadedFile()
        toastFlow.value = detail
    }

    fun setWebAgentConfirmBeforeDownload(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setWebAgentConfirmBeforeDownload(enabled)
        }
    }

    fun saveScheduledMessageRule(
        existingId: Long?,
        appName: String,
        appPackage: String,
        recipientsRaw: String,
        messageText: String,
        hour: Int,
        minute: Int,
        repeatDaily: Boolean,
    ) {
        val cleanRecipients = recipientsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (cleanRecipients.isEmpty()) {
            toastFlow.value = "En az bir alıcı adı yazmalısın."
            return
        }
        val cleanMsg = messageText.ifBlank { "." }
        val resolvedPair = if (appPackage.isNotBlank()) {
            appName.ifBlank { actionExecutor.resolveAppDisplayName(appPackage) } to appPackage
        } else {
            ScheduledMessageParser.resolveKnownAppPackage(appName, installedAppsFlow.value)
                ?: ("TikTok" to "com.zhiliaoapp.musically")
        }
        val triggerAt = ScheduledMessageScheduler.computeNextTriggerMillis(hour, minute)
        val formattedTime = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

        viewModelScope.launch {
            if (existingId != null && existingId > 0L) {
                dao.updateScheduledRule(
                    id = existingId,
                    targetAppPackage = resolvedPair.second,
                    targetAppName = resolvedPair.first,
                    recipientsCsv = cleanRecipients.joinToString(", "),
                    messageText = cleanMsg,
                    triggerHour = hour.coerceIn(0, 23),
                    triggerMinute = minute.coerceIn(0, 59),
                    repeatDaily = repeatDaily,
                    isEnabled = true,
                    nextTriggerAtMillis = triggerAt,
                )
                ScheduledMessageScheduler.scheduleRule(getApplication(), existingId, triggerAt)
                toastFlow.value = "Zamanlı mesaj güncellendi ($formattedTime)."
            } else {
                val newId = dao.insertScheduledRule(
                    ScheduledMessageRuleEntity(
                        targetAppPackage = resolvedPair.second,
                        targetAppName = resolvedPair.first,
                        recipientsCsv = cleanRecipients.joinToString(", "),
                        messageText = cleanMsg,
                        triggerHour = hour.coerceIn(0, 23),
                        triggerMinute = minute.coerceIn(0, 59),
                        repeatDaily = repeatDaily,
                        isEnabled = true,
                        nextTriggerAtMillis = triggerAt,
                        lastStatus = "Zamanlandı ($formattedTime)",
                    )
                )
                ScheduledMessageScheduler.scheduleRule(getApplication(), newId, triggerAt)
                toastFlow.value = "Zamanlı mesaj kuruldu: $formattedTime"
            }
        }
    }

    fun toggleScheduledMessageRule(rule: ScheduledMessageRuleItem) {
        viewModelScope.launch {
            val nextEnabled = !rule.isEnabled
            val nextTrigger = if (nextEnabled) {
                ScheduledMessageScheduler.computeNextTriggerMillis(rule.triggerHour, rule.triggerMinute)
            } else {
                0L
            }
            dao.setScheduledRuleEnabled(rule.id, nextEnabled, nextTrigger)
            if (nextEnabled) {
                ScheduledMessageScheduler.scheduleRule(getApplication(), rule.id, nextTrigger)
                toastFlow.value = "${rule.targetAppName} zamanlı mesajı aktif."
            } else {
                ScheduledMessageScheduler.cancelRule(getApplication(), rule.id)
                toastFlow.value = "${rule.targetAppName} zamanlı mesajı durduruldu."
            }
        }
    }

    fun deleteScheduledMessageRule(ruleId: Long) {
        viewModelScope.launch {
            ScheduledMessageScheduler.cancelRule(getApplication(), ruleId)
            dao.deleteScheduledRule(ruleId)
            toastFlow.value = "Zamanlı mesaj kuralı silindi."
        }
    }

    fun runScheduledMessageRuleNow(rule: ScheduledMessageRuleItem) {
        viewModelScope.launch {
            val summary = ScheduledMessageReceiver.executeRule(getApplication(), rule.id)
            toastFlow.value = summary
        }
    }

    fun clearScheduledMessageLogs() {
        viewModelScope.launch {
            dao.clearScheduledLogs()
            toastFlow.value = "Gönderim günlükleri temizlendi."
        }
    }

    // ---- Notes ("Not Defteri") ----

    fun addManualNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            noteStore.saveNote(
                title = title.trim().ifBlank { "Hızlı Not" },
                content = content.trim(),
                sourcePackage = getApplication<Application>().packageName,
                sourceAppName = "Manuel Not",
                screenshotBitmap = null,
            )
            toastFlow.value = "Not Defteri'ne kaydedildi."
        }
    }

    fun updateNote(id: Long, title: String, content: String) {
        viewModelScope.launch {
            noteStore.updateNote(id = id, title = title, content = content)
            toastFlow.value = "Not güncellendi."
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            noteStore.deleteNote(id)
            toastFlow.value = "Not silindi."
        }
    }

    fun selectManualProfile(packageName: String, displayName: String) {
        viewModelScope.launch {
            prefsRepo.setManualProfile(packageName, displayName)
            toastFlow.value = "Aktif profil: $displayName"
        }
    }

    fun deleteCoordinate(id: Long) {
        viewModelScope.launch {
            coordinateStore.deleteCoordinate(id)
            toastFlow.value = "Kayıtlı nokta silindi."
        }
    }

    fun testTapCoordinate(coordinate: TapCoordinateItem) {
        viewModelScope.launch {
            val execResult = actionExecutor.executeTapOnCoordinate(coordinate)
            if (execResult.detail.isNotBlank()) {
                toastFlow.value = execResult.detail
            }
        }
    }

    fun toggleSpeakMessage(message: JarvisMessage) {
        if (speakingMessageIdFlow.value == message.id) {
            tts?.stop()
            speakingMessageIdFlow.value = null
            return
        }
        if (!ttsReady) {
            toastFlow.value = "Sesli okuma motoru hazır değil."
            return
        }
        val spokenSnippet = if (message.text.length > 260) {
            message.text.take(260).substringBeforeLast(" ") + "."
        } else {
            message.text
        }
        speakingMessageIdFlow.value = message.id
        tts?.speak(spokenSnippet, TextToSpeech.QUEUE_FLUSH, null, "msg_${message.id}")
    }

    fun copyTextToClipboard(text: String) {
        val app = getApplication<Application>()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("JARVIS", text))
        toastFlow.value = "Panoya kopyalandı."
    }

    fun startVoiceCommand() {
        if (uiState.value.preferences.wakeWordEnabled) {
            JarvisOverlayService.pauseWakeWord(getApplication())
        }
        voiceManager.clearError()
        voiceManager.startListening { transcript ->
            sendCommand(transcript)
        }
    }

    fun stopVoiceCommand() {
        voiceManager.stopListening()
        if (uiState.value.preferences.wakeWordEnabled) {
            JarvisOverlayService.resumeWakeWord(getApplication())
        }
    }

    fun toggleReminderComplete(reminder: ReminderItem) {
        viewModelScope.launch {
            val next = !reminder.isCompleted
            dao.setReminderCompleted(reminder.id, next)
            if (next) {
                ReminderScheduler.cancelReminder(getApplication(), reminder.id)
            }
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            ReminderScheduler.cancelReminder(getApplication(), reminderId)
            dao.deleteReminder(reminderId)
            toastFlow.value = "Hatırlatıcı silindi."
        }
    }

    fun exportReminderToSystemTimer(reminder: ReminderItem) {
        val remainingSeconds = ((reminder.triggerAtMillis - System.currentTimeMillis()) / 1_000L)
            .toInt()
            .coerceAtLeast(5)
        val launched = actionExecutor.openSystemTimer(remainingSeconds, reminder.message)
        if (!launched) {
            toastFlow.value = "Cihazda sistem zamanlayıcısı bulunamadı."
        }
    }

    fun updateUserContext(contextText: String) {
        viewModelScope.launch {
            prefsRepo.updateUserContext(contextText)
            toastFlow.value = "Kullanıcı bağlamı güncellendi."
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDarkTheme(enabled) }
    }

    fun setAutoExecuteActions(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoExecuteActions(enabled) }
    }

    fun setSpeakReplies(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSpeakReplies(enabled)
            if (!enabled) {
                tts?.stop()
                speakingMessageIdFlow.value = null
            }
        }
    }

    fun setMaleVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setMaleVoiceEnabled(enabled)
            val updated = latestPrefs.copy(maleVoiceEnabled = enabled)
            latestPrefs = updated
            if (ttsReady) {
                voiceConfiguredFromPrefs = true
                ttsVoiceInfoFlow.value = JarvisTtsVoiceHelper.configureTurkishVoice(tts, updated)
            }
        }
    }

    fun setSelectedTtsVoiceName(voiceName: String) {
        viewModelScope.launch {
            prefsRepo.setSelectedTtsVoiceName(voiceName)
            val updated = latestPrefs.copy(selectedTtsVoiceName = voiceName)
            latestPrefs = updated
            if (ttsReady) {
                voiceConfiguredFromPrefs = true
                ttsVoiceInfoFlow.value = JarvisTtsVoiceHelper.configureTurkishVoice(tts, updated)
            }
        }
    }

    fun testJarvisVoice() {
        if (!ttsReady) {
            toastFlow.value = "Türkçe ses motoru henüz hazır değil."
            return
        }
        ttsVoiceInfoFlow.value = JarvisTtsVoiceHelper.configureTurkishVoice(tts, latestPrefs)
        tts?.speak(
            "Merhaba, ben JARVIS. Yeni ses profilim aktif.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis_voice_test_${System.nanoTime()}",
        )
    }

    fun openInstallTtsVoices() {
        val opened = JarvisTtsVoiceHelper.openInstallTtsData(getApplication())
        if (!opened) {
            toastFlow.value = "Sistem ses ayarları açılamadı."
        }
    }

    fun setPersistentTriggerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setPersistentTriggerEnabled(enabled)
            val current = uiState.value.preferences
            if (enabled || current.floatingBubbleEnabled || current.wakeWordEnabled) {
                JarvisOverlayService.syncMode(getApplication())
            } else {
                JarvisOverlayService.disableAll(getApplication())
            }
        }
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setFloatingBubbleEnabled(enabled)
            val current = uiState.value.preferences
            if (enabled || current.persistentTriggerEnabled || current.wakeWordEnabled) {
                JarvisOverlayService.syncMode(getApplication())
            } else {
                JarvisOverlayService.disableAll(getApplication())
            }
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setWakeWordEnabled(enabled)
            val current = uiState.value.preferences
            if (enabled || current.persistentTriggerEnabled || current.floatingBubbleEnabled) {
                JarvisOverlayService.syncMode(getApplication())
            } else {
                JarvisOverlayService.disableAll(getApplication())
            }
            toastFlow.value = if (enabled) {
                "\"Jarvis\" sesli uyandırma açıldı."
            } else {
                "\"Jarvis\" sesli uyandırma kapatıldı."
            }
        }
    }

    fun disableAllOverlayModes() {
        viewModelScope.launch {
            prefsRepo.disableAllOverlayModes()
            JarvisOverlayService.disableAll(getApplication())
            toastFlow.value = "Kayan baloncuk ve hızlı servis kapatıldı."
        }
    }

    fun syncOverlayServiceIfNeeded() {
        val prefs = uiState.value.preferences
        if (prefs.persistentTriggerEnabled || prefs.floatingBubbleEnabled || prefs.wakeWordEnabled) {
            JarvisOverlayService.syncMode(getApplication())
        }
    }

    fun setProactiveAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setProactiveAlertsEnabled(enabled)
            toastFlow.value = if (enabled) {
                "Proaktif WhatsApp takibi açıldı."
            } else {
                "Proaktif WhatsApp takibi kapatıldı."
            }
        }
    }

    fun setNewsAirGestureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setNewsAirGestureEnabled(enabled)
            toastFlow.value = if (enabled) {
                "Haber modunda el hareketi kontrolü açıldı."
            } else {
                "Haber modunda el hareketi kontrolü kapatıldı."
            }
        }
    }

    // ---- News Briefing & Air Gesture Controls ----

    fun refreshNewsBriefing() {
        openNewsBriefing(newsBriefingFlow.value.topicQuery)
    }

    fun openNewsBriefing(topicQuery: String = "") {
        val cleanTopic = topicQuery.trim()
        newsFetchJob?.cancel()
        activeNewsUtteranceId = null
        tts?.stop()
        speakingMessageIdFlow.value = null
        currentMoodFlow.value = JarvisMood.SERIOUS
        currentTopicTitleFlow.value = if (cleanTopic.isNotBlank()) {
            "GÜNDEM // ${cleanTopic.uppercase(Locale("tr", "TR"))}"
        } else {
            "JARVIS // GÜNDEM"
        }

        newsBriefingFlow.value = newsBriefingFlow.value.copy(
            isVisible = true,
            isLoading = true,
            topicQuery = cleanTopic,
            items = emptyList(),
            currentIndex = 0,
            isTtsPaused = false,
            isSpeakingBriefing = false,
            showingDetail = false,
            errorMessage = null,
            lastGestureLabel = "",
        )

        newsFetchJob = viewModelScope.launch {
            try {
                val rawArticles = rssFetcher.fetchCandidateArticles(topicQuery = cleanTopic)
                if (!newsBriefingFlow.value.isVisible) return@launch
                if (rawArticles.isEmpty()) {
                    newsBriefingFlow.value = newsBriefingFlow.value.copy(
                        isLoading = false,
                        errorMessage = if (cleanTopic.isNotBlank()) {
                            "\"$cleanTopic\" ile ilgili güncel haber bulunamadı."
                        } else {
                            "Haber kaynaklarına ulaşılamadı. İnternet bağlantını kontrol et."
                        },
                    )
                    return@launch
                }

                val curatedResult = aiRepository.curateNewsBriefing(
                    rawArticles = rawArticles,
                    userContext = uiState.value.preferences.userContext,
                    topicQuery = cleanTopic,
                )
                if (!newsBriefingFlow.value.isVisible) return@launch

                curatedResult.fold(
                    onSuccess = { items ->
                        newsBriefingFlow.value = newsBriefingFlow.value.copy(
                            isLoading = false,
                            items = items,
                            currentIndex = 0,
                            showingDetail = false,
                            errorMessage = if (items.isEmpty()) {
                                if (cleanTopic.isNotBlank()) {
                                    "\"$cleanTopic\" için uygun haber bulunamadı."
                                } else {
                                    "Güncel haber bulunamadı."
                                }
                            } else {
                                null
                            },
                        )
                        if (items.isNotEmpty()) {
                            speakCurrentNewsItem(withIntro = true)
                        }
                    },
                    onFailure = { err ->
                        newsBriefingFlow.value = newsBriefingFlow.value.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Gündem hazırlanamadı.",
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "openNewsBriefing failed", e)
                if (newsBriefingFlow.value.isVisible) {
                    newsBriefingFlow.value = newsBriefingFlow.value.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Haberler yüklenirken hata oluştu.",
                    )
                }
            }
        }
    }

    fun selectNewsIndex(index: Int, fromGesture: Boolean = false) {
        val cur = newsBriefingFlow.value
        if (!cur.isVisible || cur.items.isEmpty()) return
        val safeIndex = index.coerceIn(0, cur.items.lastIndex)
        if (safeIndex == cur.currentIndex && !fromGesture) return

        newsBriefingFlow.value = cur.copy(
            currentIndex = safeIndex,
            showingDetail = false,
        )
        if (!cur.isTtsPaused) {
            speakCurrentNewsItem(withIntro = false)
        }
    }

    fun toggleNewsTtsPause() {
        val cur = newsBriefingFlow.value
        if (!cur.isVisible) return
        if (!cur.isTtsPaused) {
            activeNewsUtteranceId = null
            tts?.stop()
            newsBriefingFlow.value = cur.copy(
                isTtsPaused = true,
                isSpeakingBriefing = false,
            )
        } else {
            newsBriefingFlow.value = cur.copy(isTtsPaused = false)
            speakCurrentNewsItem(withIntro = false)
        }
    }

    fun toggleNewsDetail(forceDetail: Boolean? = null) {
        val cur = newsBriefingFlow.value
        if (!cur.isVisible || cur.items.isEmpty()) return
        val nextDetail = forceDetail ?: !cur.showingDetail
        if (nextDetail == cur.showingDetail && forceDetail != null) return
        newsBriefingFlow.value = cur.copy(showingDetail = nextDetail)
        if (!cur.isTtsPaused) {
            speakCurrentNewsItem(withIntro = false)
        }
    }

    fun handleNewsControl(target: String) {
        val cur = newsBriefingFlow.value
        when (target.lowercase(Locale.ROOT)) {
            "next" -> if (cur.currentIndex < cur.items.lastIndex) {
                selectNewsIndex(cur.currentIndex + 1)
            } else {
                toastFlow.value = "Son haberdesin."
            }
            "previous" -> if (cur.currentIndex > 0) {
                selectNewsIndex(cur.currentIndex - 1)
            } else {
                toastFlow.value = "İlk haberdesin."
            }
            "detail" -> toggleNewsDetail(forceDetail = true)
            "pause" -> if (!cur.isTtsPaused) toggleNewsTtsPause()
            "resume" -> if (cur.isTtsPaused || !cur.isSpeakingBriefing) {
                newsBriefingFlow.value = cur.copy(isTtsPaused = false)
                speakCurrentNewsItem(withIntro = false)
            }
            "close" -> closeNewsBriefing()
        }
    }

    fun closeNewsBriefing() {
        newsFetchJob?.cancel()
        gestureBadgeResetJob?.cancel()
        activeNewsUtteranceId = null
        tts?.stop()
        newsBriefingFlow.value = NewsBriefingState()
        currentMoodFlow.value = JarvisMood.NEUTRAL
        currentTopicTitleFlow.value = ""
    }

    fun updateGestureSensorStatus(active: Boolean, statusText: String) {
        val cur = newsBriefingFlow.value
        if (cur.isVisible || !active) {
            newsBriefingFlow.value = cur.copy(
                gestureSensorActive = active,
                gestureStatusText = statusText,
            )
        }
    }

    fun onAirGestureDetected(gesture: HandSwipeGesture) {
        val cur = newsBriefingFlow.value
        if (!cur.isVisible || cur.items.isEmpty()) return
        val label = when (gesture) {
            HandSwipeGesture.SWIPE_RIGHT -> {
                if (cur.currentIndex < cur.items.lastIndex) {
                    selectNewsIndex(cur.currentIndex + 1, fromGesture = true)
                }
                "SAĞA KAYDIRILDI -> SONRAKİ"
            }
            HandSwipeGesture.SWIPE_LEFT -> {
                if (cur.currentIndex > 0) {
                    selectNewsIndex(cur.currentIndex - 1, fromGesture = true)
                }
                "SOLA KAYDIRILDI -> ÖNCEKİ"
            }
        }
        newsBriefingFlow.value = newsBriefingFlow.value.copy(lastGestureLabel = label)
        gestureBadgeResetJob?.cancel()
        gestureBadgeResetJob = viewModelScope.launch {
            delay(1_400L)
            if (newsBriefingFlow.value.lastGestureLabel == label) {
                newsBriefingFlow.value = newsBriefingFlow.value.copy(lastGestureLabel = "")
            }
        }
    }

    private fun speakCurrentNewsItem(withIntro: Boolean) {
        val cur = newsBriefingFlow.value
        val item = cur.currentItem ?: return
        if (!ttsReady || cur.isTtsPaused) return

        val body = if (cur.showingDetail) item.detailedSummary else item.briefSummary
        val speech = if (withIntro && cur.currentIndex == 0 && !cur.showingDetail) {
            if (cur.topicQuery.isNotBlank()) {
                "${cur.topicQuery} hakkında güncel haberlere geçiyorum. $body"
            } else {
                "Güncel bültene başlıyorum. ${item.category.label}. $body"
            }
        } else {
            body
        }
        val utteranceId = "jarvis_news_${cur.currentIndex}_${System.nanoTime()}"
        activeNewsUtteranceId = utteranceId
        newsBriefingFlow.value = cur.copy(isSpeakingBriefing = true)
        tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun onNewsUtteranceFinished(utteranceId: String) {
        if (utteranceId != activeNewsUtteranceId) return
        val cur = newsBriefingFlow.value
        if (!cur.isVisible || cur.isTtsPaused || cur.items.isEmpty()) {
            activeNewsUtteranceId = null
            newsBriefingFlow.value = cur.copy(isSpeakingBriefing = false)
            return
        }
        if (!cur.showingDetail && cur.currentIndex < cur.items.lastIndex) {
            val nextIdx = cur.currentIndex + 1
            newsBriefingFlow.value = cur.copy(
                currentIndex = nextIdx,
                showingDetail = false,
            )
            speakCurrentNewsItem(withIntro = false)
        } else {
            activeNewsUtteranceId = null
            newsBriefingFlow.value = cur.copy(isSpeakingBriefing = false)
        }
    }

    fun setImportanceThreshold(threshold: Int) {
        viewModelScope.launch {
            prefsRepo.setImportanceThreshold(threshold)
        }
    }

    fun updateProactiveAlertDraft(alertId: Long, draft: String) {
        viewModelScope.launch {
            dao.updateProactiveDraft(alertId, draft)
        }
    }

    fun copyProactiveDraft(draft: String) {
        ProactiveAlertActionCache.copyToClipboard(getApplication(), draft)
        toastFlow.value = "Cevap taslağı panoya kopyalandı."
    }

    fun openWhatsAppForAlert(alert: ProactiveAlertItem, draft: String) {
        viewModelScope.launch {
            dao.updateProactiveDraft(alert.id, draft)
            val opened = ProactiveAlertActionCache.openWhatsAppChat(
                context = getApplication(),
                alertId = alert.id,
                sourcePackage = alert.sourcePackage,
                draftToCopy = draft,
            )
            ProactiveAlertNotifier.cancelAlertNotification(getApplication(), alert.id)
            toastFlow.value = if (opened) {
                "Taslak panoya kopyalandı, WhatsApp açılıyor."
            } else {
                "Cihazda WhatsApp bulunamadı."
            }
        }
    }

    fun sendProactiveDirectReply(alert: ProactiveAlertItem, draft: String) {
        viewModelScope.launch {
            val sent = ProactiveAlertActionCache.sendDirectReply(
                context = getApplication(),
                alertId = alert.id,
                replyText = draft,
            )
            if (sent) {
                dao.dismissProactiveAlert(alert.id)
                ProactiveAlertNotifier.cancelAlertNotification(getApplication(), alert.id)
                ProactiveAlertActionCache.remove(alert.id)
                toastFlow.value = "${alert.senderName} için yanıt gönderildi."
            } else {
                openWhatsAppForAlert(alert, draft)
            }
        }
    }

    fun dismissProactiveAlert(alertId: Long) {
        viewModelScope.launch {
            dao.dismissProactiveAlert(alertId)
            ProactiveAlertNotifier.cancelAlertNotification(getApplication(), alertId)
            ProactiveAlertActionCache.remove(alertId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearMessages()
            currentMoodFlow.value = JarvisMood.NEUTRAL
            currentTopicTitleFlow.value = ""
            visualCuesFlow.value = emptyList()
            toastFlow.value = "Sohbet geçmişi temizlendi."
        }
    }

    fun dismissError() {
        errorFlow.value = null
        lastFailedPromptFlow.value = null
        if (currentMoodFlow.value == JarvisMood.ALERT) {
            currentMoodFlow.value = JarvisMood.NEUTRAL
            currentTopicTitleFlow.value = ""
        }
        voiceManager.clearError()
    }

    fun refreshAppCheckStatus() {
        appCheckStatusFlow.value = getAppCheckStatus(getApplication())
    }

    fun generateAppCheckDebugToken() {
        val updated = generateAndSaveDebugToken(getApplication())
        appCheckStatusFlow.value = updated
        if (updated.debugToken.isNotBlank()) {
            copyTextToClipboard(updated.debugToken)
            toastFlow.value = "Debug Token oluşturuldu ve panoya kopyalandı."
        }
    }

    fun saveAppCheckDebugToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return
        appCheckStatusFlow.value = saveCustomDebugToken(getApplication(), trimmed)
        toastFlow.value = "App Check Debug Token kaydedildi ve aktif edildi."
    }

    fun resetAppCheckToRecaptcha() {
        appCheckStatusFlow.value = clearCustomDebugToken(getApplication())
        toastFlow.value = "Özel Debug Token temizlendi."
    }

    fun consumeToast() {
        toastFlow.value = null
    }

    override fun onCleared() {
        super.onCleared()
        newsFetchJob?.cancel()
        gestureBadgeResetJob?.cancel()
        visualCueResetJob?.cancel()
        webDownloadAgent.cancelOrDismiss()
        voiceManager.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    private fun ScheduledMessageRuleEntity.toDomain(): ScheduledMessageRuleItem =
        ScheduledMessageRuleItem(
            id = id,
            targetAppPackage = targetAppPackage,
            targetAppName = targetAppName,
            recipients = recipientsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
            messageText = messageText,
            triggerHour = triggerHour,
            triggerMinute = triggerMinute,
            repeatDaily = repeatDaily,
            isEnabled = isEnabled,
            nextTriggerAtMillis = nextTriggerAtMillis,
            lastRunAtMillis = lastRunAtMillis,
            lastStatus = lastStatus,
            createdAtMillis = createdAtMillis,
        )

    private fun ScheduledMessageLogEntity.toDomain(): ScheduledMessageLogItem =
        ScheduledMessageLogItem(
            id = id,
            ruleId = ruleId,
            targetAppName = targetAppName,
            recipientsSummary = recipientsSummary,
            status = status,
            detail = detail,
            executedAtMillis = executedAtMillis,
        )

    private fun ProactiveAlertEntity.toProactiveDomain(): ProactiveAlertItem {
        return ProactiveAlertItem(
            id = id,
            sourcePackage = sourcePackage,
            senderName = senderName,
            originalMessage = originalMessage,
            summary = summary,
            importanceScore = importanceScore,
            importanceReason = importanceReason,
            draftReply = draftReply,
            messageTimestampMillis = messageTimestampMillis,
            createdAtMillis = createdAtMillis,
            isDismissed = isDismissed,
        )
    }

    private fun ChatMessageEntity.toDomain(): JarvisMessage {
        val intent = ActionIntent.fromWire(intentWire)
        val action = if (intent != ActionIntent.NONE) {
            JarvisAction(
                intent = intent,
                target = target.orEmpty(),
                minutes = minutes,
                seconds = seconds,
                message = actionMessage.orEmpty(),
            )
        } else {
            null
        }
        val parsedStatus = runCatching { ActionExecutionStatus.valueOf(actionStatus) }
            .getOrDefault(ActionExecutionStatus.PENDING)

        return JarvisMessage(
            id = id,
            isUser = isUser,
            text = text,
            action = action,
            actionStatus = parsedStatus,
            actionStatusDetail = actionStatusDetail,
            timestampMillis = timestampMillis,
        )
    }
}

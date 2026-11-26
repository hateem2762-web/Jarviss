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
import com.example.ai.JarvisRepository
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CoordinateJsonStore
import com.example.data.local.JarvisDatabase
import com.example.data.local.ReminderEntity
import com.example.data.local.TapCoordinateEntity
import com.example.data.local.toDomain
import com.example.data.preferences.JarvisPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ActionExecutionStatus
import com.example.model.ActionIntent
import com.example.model.InstalledAppInfo
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
import com.example.model.ReminderItem
import com.example.model.TapCoordinateItem
import com.example.overlay.JarvisOverlayService
import com.example.system.CoordinateMatcher
import com.example.system.JarvisActionExecutor
import com.example.system.ReminderScheduler
import com.example.system.VoiceInputManager
import com.example.system.VoiceInputState
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = JarvisDatabase.getInstance(application).jarvisDao()
    private val coordinateStore = CoordinateJsonStore(application, dao)
    private val prefsRepo = UserPreferencesRepository(application)
    private val actionExecutor = JarvisActionExecutor(application, dao)
    private val voiceManager = VoiceInputManager(application)
    private val aiRepository = JarvisRepository()

    private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isProcessingFlow = MutableStateFlow(false)
    private val speakingMessageIdFlow = MutableStateFlow<Long?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val lastFailedPromptFlow = MutableStateFlow<String?>(null)
    private val toastFlow = MutableStateFlow<String?>(null)

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private data class PersistentSnapshot(
        val messages: List<ChatMessageEntity>,
        val reminders: List<ReminderEntity>,
        val coordinates: List<TapCoordinateEntity>,
        val preferences: JarvisPreferences,
    )

    private data class RuntimeSnapshot(
        val apps: List<InstalledAppInfo>,
        val voice: VoiceInputState,
        val foregroundPkg: String?,
        val accessibilityConnected: Boolean,
    )

    private data class TransientSnapshot(
        val processing: Boolean,
        val speakingId: Long?,
        val error: String?,
        val failedPrompt: String?,
        val toast: String?,
    )

    private val persistentFlow: Flow<PersistentSnapshot> = combine(
        dao.observeMessages(),
        dao.observeReminders(),
        dao.observeCoordinates(),
        prefsRepo.preferences,
    ) { msgEntities, remEntities, coordEntities, prefs ->
        PersistentSnapshot(
            messages = msgEntities,
            reminders = remEntities,
            coordinates = coordEntities,
            preferences = prefs,
        )
    }

    private val runtimeFlow: Flow<RuntimeSnapshot> = combine(
        installedAppsFlow,
        voiceManager.state,
        JarvisAccessibilityService.currentForegroundPackage,
        JarvisAccessibilityService.isServiceConnected,
    ) { apps, voice, fgPkg, a11yConnected ->
        RuntimeSnapshot(
            apps = apps,
            voice = voice,
            foregroundPkg = fgPkg,
            accessibilityConnected = a11yConnected,
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

    val uiState: StateFlow<JarvisUiState> = combine(
        persistentFlow,
        runtimeFlow,
        transientFlow,
    ) { persistent, runtime, transient ->
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
            preferences = persistent.preferences,
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
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            speakingMessageIdFlow.value = null
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            speakingMessageIdFlow.value = null
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

                // 0-Token Fast Path: match saved coordinate label locally before calling Gemini
                val zeroTokenMatch = CoordinateMatcher.findZeroTokenMatch(text, activeCoordinates)
                if (zeroTokenMatch != null) {
                    val execResult = actionExecutor.executeTapOnCoordinate(zeroTokenMatch)
                    val replyText = if (execResult.status == ActionExecutionStatus.EXECUTED) {
                        "\"${zeroTokenMatch.label}\" noktasına dokunuluyor."
                    } else {
                        execResult.detail
                    }
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
                    if (snapshotPrefs.speakReplies && ttsReady) {
                        speakingMessageIdFlow.value = insertedId
                        tts?.speak(
                            replyText,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "jarvis_$insertedId"
                        )
                    }
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

                        if (snapshotPrefs.speakReplies && ttsReady) {
                            speakingMessageIdFlow.value = insertedId
                            tts?.speak(
                                reply.ttsSummary,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "jarvis_$insertedId"
                            )
                        }

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
                        }
                    },
                    onFailure = { throwable ->
                        Log.e(TAG, "askJarvis failed", throwable)
                        lastFailedPromptFlow.value = text
                        errorFlow.value = throwable.localizedMessage
                            ?: "JARVIS yanıt verirken bir hata oluştu."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "dispatchCommand failed", e)
                lastFailedPromptFlow.value = text
                errorFlow.value = e.localizedMessage
                    ?: "Komut işlenirken beklenmeyen bir hata oluştu."
            } finally {
                isProcessingFlow.value = false
            }
        }
    }

    fun executeActionForMessage(messageId: Long, action: JarvisAction) {
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

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearMessages()
            toastFlow.value = "Sohbet geçmişi temizlendi."
        }
    }

    fun dismissError() {
        errorFlow.value = null
        lastFailedPromptFlow.value = null
        voiceManager.clearError()
    }

    fun consumeToast() {
        toastFlow.value = null
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
        tts?.stop()
        tts?.shutdown()
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

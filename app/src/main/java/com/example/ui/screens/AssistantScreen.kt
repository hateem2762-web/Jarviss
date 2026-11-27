package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HudTopicResolver
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
import com.example.model.JarvisMood
import com.example.model.JarvisVisualState
import com.example.model.ProactiveAlertItem
import com.example.news.NewsBriefingStage
import com.example.notifications.ProactiveAlertActionCache
import com.example.ui.JarvisUiState
import com.example.ui.components.CinematicAiEditStage
import com.example.ui.components.JarvisHudHeader
import com.example.ui.components.LiveSubtitleDisplay
import com.example.ui.components.MinimalistChatStreamView
import com.example.ui.components.ParticleNebulaCanvas
import com.example.ui.components.ProactiveAlertCard
import com.example.ui.components.VoiceOrbButton
import com.example.ui.components.WebAgentCard
import com.example.ui.components.resolveMoodAccentColor
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.sin
import kotlinx.coroutines.delay

private data class QuickCommandPrompt(
    val label: String,
    val command: String,
    val icon: ImageVector,
)

private val StarterCommands = listOf(
    QuickCommandPrompt("Ekranı not al", "Bu ekranı not al", Icons.Rounded.EditNote),
    QuickCommandPrompt("Spotify'da çal", "Spotify'da Duman aç", Icons.Rounded.PlayCircle),
    QuickCommandPrompt("Gündem bülteni", "Gündemde ne var?", Icons.Rounded.Public),
    QuickCommandPrompt("Feneri aç", "Feneri aç", Icons.Rounded.FlashlightOn),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    uiState: JarvisUiState,
    hasOverlayPermission: Boolean,
    hasMicPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    hasNotificationListenerPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onDisableAllOverlays: () -> Unit,
    onUpdateProactiveDraft: (Long, String) -> Unit,
    onCopyProactiveDraft: (String) -> Unit,
    onOpenProactiveWhatsApp: (ProactiveAlertItem, String) -> Unit,
    onSendProactiveDirectReply: (ProactiveAlertItem, String) -> Unit,
    onDismissProactiveAlert: (Long) -> Unit,
    onSendCommand: (String) -> Unit,
    onRetry: () -> Unit,
    onRunAction: (Long, JarvisAction) -> Unit,
    onCopyMessage: (String) -> Unit,
    onToggleSpeakMessage: (JarvisMessage) -> Unit,
    onVoiceOrbClick: () -> Unit,
    onContextClick: () -> Unit,
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    onSelectNewsIndex: (Int) -> Unit,
    onToggleNewsTtsPause: () -> Unit,
    onToggleNewsDetail: () -> Unit,
    onRefreshNews: () -> Unit,
    onCloseNews: () -> Unit,
    onConfirmWebDownload: () -> Unit,
    onOpenWebDownload: () -> Unit,
    onDismissWebAgent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSpeakingCinematic = uiState.speakingMessageId != null && !uiState.newsBriefing.isVisible

    BackHandler(enabled = isSpeakingCinematic) {
        val latestBot = uiState.messages.lastOrNull { !it.isUser }
        if (latestBot != null) {
            onToggleSpeakMessage(latestBot)
        }
    }

    BackHandler(enabled = uiState.newsBriefing.isVisible) {
        onCloseNews()
    }

    var inputText by rememberSaveable { mutableStateOf("") }
    var showPermissionSheet by remember { mutableStateOf(false) }
    val combinedError = uiState.errorMessage ?: uiState.voiceState.errorMessage

    val latestBotMessage = remember(uiState.messages) {
        uiState.messages.lastOrNull { !it.isUser }
    }
    val visualState = when {
        combinedError != null -> JarvisVisualState.ERROR
        uiState.voiceState.isListening -> JarvisVisualState.LISTENING
        uiState.isProcessing -> JarvisVisualState.THINKING
        uiState.speakingMessageId != null -> JarvisVisualState.SPEAKING
        else -> JarvisVisualState.IDLE
    }
    val effectiveMood = when {
        combinedError != null -> JarvisMood.ALERT
        uiState.globalAirGestureState.lastActionLabel.isNotBlank() -> JarvisMood.ENERGETIC
        uiState.proactiveAlerts.isNotEmpty() && latestBotMessage == null -> JarvisMood.SERIOUS
        else -> uiState.currentMood
    }
    val dynamicHeader = remember(
        uiState.voiceState.isListening,
        uiState.isProcessing,
        combinedError,
        uiState.proactiveAlerts.size,
        latestBotMessage?.id,
        uiState.currentTopicTitle,
        uiState.webAgentState.isVisible,
        uiState.webAgentState.headerTitle,
    ) {
        HudTopicResolver.resolveHeader(
            isListening = uiState.voiceState.isListening,
            isProcessing = uiState.isProcessing,
            hasError = combinedError != null,
            hasProactiveAlert = uiState.proactiveAlerts.isNotEmpty(),
            latestActionIntent = latestBotMessage?.action?.intent,
            aiTopicTitle = uiState.currentTopicTitle,
            webAgentHeader = uiState.webAgentState.takeIf { it.isVisible }?.headerTitle.orEmpty(),
        )
    }

    // Subtle speaking cadence amplitude when JARVIS is reading aloud (TTS)
    var ttsCadenceRms by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(uiState.speakingMessageId) {
        if (uiState.speakingMessageId == null) {
            ttsCadenceRms = 0f
            return@LaunchedEffect
        }
        var t = 0f
        while (true) {
            t += 0.25f
            ttsCadenceRms = (0.28f + 0.25f * kotlin.math.abs(sin(t)) + 0.14f * kotlin.math.abs(sin(t * 2.3f)))
                .coerceIn(0.15f, 0.72f)
            delay(65L)
        }
    }

    var gesturePulseRms by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(uiState.globalAirGestureState.triggerCount) {
        if (uiState.globalAirGestureState.triggerCount > 0L) {
            gesturePulseRms = 0.58f
            delay(450L)
            gesturePulseRms = 0f
        }
    }

    val effectiveRms = when {
        uiState.voiceState.isListening -> uiState.voiceState.rmsLevel
        uiState.speakingMessageId != null -> ttsCadenceRms
        uiState.isProcessing -> 0.18f
        gesturePulseRms > 0f -> gesturePulseRms
        else -> 0f
    }

    val liveSubtitleText = when {
        combinedError != null -> combinedError
        uiState.voiceState.isListening -> uiState.voiceState.partialText.ifBlank { "Dinliyorum…" }
        uiState.isProcessing -> "Nöral analiz yürütülüyor…"
        latestBotMessage != null -> latestBotMessage.text
        else -> ""
    }
    val liveSubtitleKey = when {
        combinedError != null -> "err_$combinedError"
        uiState.voiceState.isListening -> "listen"
        uiState.isProcessing -> "proc"
        latestBotMessage != null -> latestBotMessage.id
        else -> "idle"
    }
    val accentColor = resolveMoodAccentColor(visualState, effectiveMood)

    val needsNotifListenerSetup = uiState.preferences.proactiveAlertsEnabled &&
        !hasNotificationListenerPermission
    val hasMissingPermissions = !hasOverlayPermission || !hasMicPermission || !hasAccessibilityPermission || needsNotifListenerSetup

    // Permissions Dialog on demand (keeps main screen 100% clean)
    if (showPermissionSheet) {
        AlertDialog(
            onDismissRequest = { showPermissionSheet = false },
            confirmButton = {
                TextButton(onClick = { showPermissionSheet = false }) {
                    Text("Kapat", color = MaterialTheme.colorScheme.primary)
                }
            },
            title = {
                Text(
                    text = "SİSTEM İZİNLERİ",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                PermissionOnboardingCard(
                    hasOverlayPermission = hasOverlayPermission,
                    hasMicPermission = hasMicPermission,
                    hasAccessibilityPermission = hasAccessibilityPermission,
                    showNotificationListenerButton = needsNotifListenerSetup,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestMicPermission = onRequestMicPermission,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    AnimatedContent(
        targetState = isSpeakingCinematic,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(220))
        },
        label = "assistant_cinematic_scene_transition",
    ) { speakingActive ->
        if (speakingActive) {
            // Fullscreen Cinematic "AI Edit" Motion Scene during Speech
            CinematicAiEditStage(
                spokenText = latestBotMessage?.text ?: liveSubtitleText,
                topicTitle = dynamicHeader,
                visualCues = uiState.visualCues,
                mood = effectiveMood,
                ttsCadenceRms = effectiveRms,
                onStopSpeaking = {
                    latestBotMessage?.let { onToggleSpeakMessage(it) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Ultra-Clean, De-Cluttered Minimalist Canvas
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top Minimalist HUD Bar
                MinimalistTopHudBar(
                    topicHeader = dynamicHeader,
                    visualState = visualState,
                    mood = effectiveMood,
                    hasMissingPermissions = hasMissingPermissions,
                    onOpenPermissions = { showPermissionSheet = true },
                    onLaunchOverlay = onLaunchOverlay,
                    onContextClick = onContextClick,
                    hasMessages = uiState.messages.isNotEmpty(),
                    onClearChat = onClearChat,
                )

                AnimatedContent(
                    targetState = uiState.newsBriefing.isVisible,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "assistant_news_stage_transition",
                ) { inNewsMode ->
                    if (inNewsMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            NewsBriefingStage(
                                state = uiState.newsBriefing,
                                compactOverlay = false,
                                onSelectIndex = onSelectNewsIndex,
                                onTogglePauseTts = onToggleNewsTtsPause,
                                onToggleDetail = onToggleNewsDetail,
                                onRefresh = onRefreshNews,
                                onClose = onCloseNews,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Centered Breathing Particle Nebula & Frameless Live Subtitles
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val voiceOrbDesc = stringResource(R.string.cd_voice_input)
                                val orbSize = if (uiState.messages.isEmpty() && uiState.proactiveAlerts.isEmpty()) 210.dp else 168.dp

                                Box(
                                    modifier = Modifier
                                        .size(orbSize)
                                        .clip(RoundedCornerShape(120.dp))
                                        .semantics { contentDescription = voiceOrbDesc }
                                        .clickable(onClick = onVoiceOrbClick)
                                        .testTag("jarvis_particle_nebula"),
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

                                LiveSubtitleDisplay(
                                    subtitleText = liveSubtitleText,
                                    subtitleKey = liveSubtitleKey,
                                    visualState = visualState,
                                    mood = effectiveMood,
                                    modifier = Modifier.heightIn(min = 38.dp),
                                )

                                AnimatedVisibility(visible = uiState.webAgentState.isVisible) {
                                    WebAgentCard(
                                        state = uiState.webAgentState,
                                        onConfirmDownload = onConfirmWebDownload,
                                        onOpenDownloadedFile = onOpenWebDownload,
                                        onDismiss = onDismissWebAgent,
                                        compactOverlay = false,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }

                            // Active Proactive WhatsApp Alerts (if any)
                            if (uiState.proactiveAlerts.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 210.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    uiState.proactiveAlerts.forEach { alert ->
                                        ProactiveAlertCard(
                                            alert = alert,
                                            hasDirectReply = ProactiveAlertActionCache.hasDirectReply(alert.id),
                                            onDraftChange = { edited -> onUpdateProactiveDraft(alert.id, edited) },
                                            onCopyDraft = { draft -> onCopyProactiveDraft(draft) },
                                            onOpenWhatsApp = { draft -> onOpenProactiveWhatsApp(alert, draft) },
                                            onSendDirectReply = { draft -> onSendProactiveDirectReply(alert, draft) },
                                            onDismiss = { onDismissProactiveAlert(alert.id) },
                                        )
                                    }
                                }
                            }

                            // Translucent Bubble-Free Minimalist Chat Stream OR Starter Chips
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                if (uiState.messages.isEmpty()) {
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        StarterCommands.forEachIndexed { index, item ->
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { onSendCommand(item.command) }
                                                    .testTag("quick_command_chip_$index"),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = null,
                                                        tint = accentColor,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Text(
                                                        text = item.label,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    MinimalistChatStreamView(
                                        messages = uiState.messages,
                                        accentColor = accentColor,
                                        speakingMessageId = uiState.speakingMessageId,
                                        onRunAction = onRunAction,
                                        onCopyMessage = onCopyMessage,
                                        onToggleSpeakMessage = onToggleSpeakMessage,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = combinedError != null) {
                    if (combinedError != null) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = combinedError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                if (uiState.lastFailedPrompt != null && !uiState.isProcessing) {
                                    TextButton(
                                        onClick = onRetry,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("retry_command_button"),
                                    ) {
                                        Text(
                                            text = "Tekrar Dene",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onDismissError,
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Hatayı kapat",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                CommandDock(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    isListening = uiState.voiceState.isListening,
                    rmsLevel = uiState.voiceState.rmsLevel,
                    isProcessing = uiState.isProcessing,
                    onSend = {
                        val toSend = inputText.trim()
                        if (toSend.isNotEmpty()) {
                            onSendCommand(toSend)
                            inputText = ""
                        }
                    },
                    onVoiceOrbClick = onVoiceOrbClick,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
        }
    }
}

@Composable
private fun MinimalistTopHudBar(
    topicHeader: String,
    visualState: JarvisVisualState,
    mood: JarvisMood,
    hasMissingPermissions: Boolean,
    onOpenPermissions: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onContextClick: () -> Unit,
    hasMessages: Boolean,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        JarvisHudHeader(
            topicHeader = topicHeader,
            visualState = visualState,
            mood = mood,
            modifier = Modifier.weight(1f, fill = false),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (hasMissingPermissions) {
                IconButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("grant_permission_badge"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "İzinler",
                        tint = JarvisAmber,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            IconButton(
                onClick = onLaunchOverlay,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("launch_overlay_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureInPictureAlt,
                    contentDescription = "Kayan Pencere",
                    tint = JarvisCyan,
                    modifier = Modifier.size(18.dp),
                )
            }

            IconButton(
                onClick = onContextClick,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Kullanıcı Bağlamı",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (hasMessages) {
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Sohbeti Temizle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionOnboardingCard(
    hasOverlayPermission: Boolean,
    hasMicPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    showNotificationListenerButton: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Tam kontrol için eksik izinler:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasOverlayPermission) {
                    OutlinedButton(
                        onClick = onRequestOverlayPermission,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_overlay_permission_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(imageVector = Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Üstte Göster", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!hasMicPermission) {
                    OutlinedButton(
                        onClick = onRequestMicPermission,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_mic_permission_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(imageVector = Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mikrofon", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!hasAccessibilityPermission) {
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_accessibility_permission_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(imageVector = Icons.Rounded.AccessibilityNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Erişilebilirlik", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (showNotificationListenerButton) {
                    OutlinedButton(
                        onClick = onOpenNotificationListenerSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_notification_listener_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(imageVector = Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bildirim Erişimi", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandDock(
    inputText: String,
    onInputChange: (String) -> Unit,
    isListening: Boolean,
    rmsLevel: Float,
    isProcessing: Boolean,
    onSend: () -> Unit,
    onVoiceOrbClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.hint_command),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            AnimatedVisibility(visible = inputText.isNotBlank() && !isProcessing) {
                FilledIconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_command_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.cd_send_command),
                    )
                }
            }

            VoiceOrbButton(
                isListening = isListening,
                rmsLevel = rmsLevel,
                onClick = onVoiceOrbClick,
            )
        }
    }
}

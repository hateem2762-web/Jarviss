package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
import com.example.ui.JarvisUiState
import com.example.ui.components.ActionDispatchCard
import com.example.ui.components.JarvisTopHudBar
import com.example.ui.components.VoiceOrbButton
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCyan

private data class QuickCommandPrompt(
    val label: String,
    val command: String,
    val icon: ImageVector,
)

private val StarterCommands = listOf(
    QuickCommandPrompt("Ayarları aç", "Ayarları aç", Icons.AutoMirrored.Rounded.Launch),
    QuickCommandPrompt("10 sn hatırlatıcı", "10 saniye sonra su içmeyi hatırlat", Icons.Rounded.Alarm),
    QuickCommandPrompt("YouTube'da lofi ara", "YouTube'da lofi beats ara", Icons.Rounded.PlayCircle),
    QuickCommandPrompt("İş başvurusu yaz", "Bana kısa ve profesyonel bir iş başvurusu ön yazısı hazırla", Icons.Rounded.Lightbulb),
    QuickCommandPrompt("github.com aç", "github.com sitesini aç", Icons.Rounded.Public),
    QuickCommandPrompt("Ana ekrana dön", "Ana ekrana dön", Icons.Rounded.Home),
)

@Composable
fun AssistantScreen(
    uiState: JarvisUiState,
    hasOverlayPermission: Boolean,
    hasMicPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onSendCommand: (String) -> Unit,
    onRetry: () -> Unit,
    onRunAction: (Long, JarvisAction) -> Unit,
    onCopyMessage: (String) -> Unit,
    onToggleSpeakMessage: (JarvisMessage) -> Unit,
    onVoiceOrbClick: () -> Unit,
    onContextClick: () -> Unit,
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val primaryGlow = MaterialTheme.colorScheme.primary

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val combinedError = uiState.errorMessage ?: uiState.voiceState.errorMessage

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.16f),
                        radius = size.maxDimension * 0.55f,
                    )
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JarvisTopHudBar(
            isListening = uiState.voiceState.isListening,
            isProcessing = uiState.isProcessing,
            hasUserContext = uiState.preferences.userContext.isNotBlank(),
            hasMessages = uiState.messages.isNotEmpty(),
            onContextClick = onContextClick,
            onClearChat = onClearChat,
        )

        // Overlay, Mic & Accessibility Permission Setup Banner or Quick Overlay Launch Bar
        if (!hasOverlayPermission || !hasMicPermission || !hasAccessibilityPermission) {
            PermissionOnboardingCard(
                hasOverlayPermission = hasOverlayPermission,
                hasMicPermission = hasMicPermission,
                hasAccessibilityPermission = hasAccessibilityPermission,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestMicPermission = onRequestMicPermission,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(
                    onClick = onLaunchOverlay,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("launch_overlay_button"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureInPictureAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Küçük Pencere (Overlay) Aç",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (uiState.messages.isEmpty()) {
                EmptyAssistantHud(
                    onQuickCommand = onSendCommand,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            isSpeaking = uiState.speakingMessageId == msg.id,
                            onRunAction = { action -> onRunAction(msg.id, action) },
                            onCopy = { onCopyMessage(msg.text) },
                            onToggleSpeak = { onToggleSpeakMessage(msg) },
                        )
                    }

                    if (uiState.voiceState.isListening) {
                        item(key = "listening_live") {
                            LiveStatusRow(
                                text = uiState.voiceState.partialText.ifBlank {
                                    "Seni dinliyorum…"
                                },
                                isLoading = false,
                            )
                        }
                    } else if (uiState.isProcessing) {
                        item(key = "processing_live") {
                            LiveStatusRow(
                                text = "Komut çözümleniyor…",
                                isLoading = true,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionOnboardingCard(
    hasOverlayPermission: Boolean,
    hasMicPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Tam erişim ve ekrana dokunma için eksik izinler:",
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
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Üstte Göster İzni Ver", style = MaterialTheme.typography.labelMedium)
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
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mikrofon İzni Ver", style = MaterialTheme.typography.labelMedium)
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
                        Icon(
                            imageVector = Icons.Rounded.AccessibilityNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Erişilebilirlik Aç", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyAssistantHud(
    onQuickCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(JarvisCyan, MaterialTheme.colorScheme.secondary)
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_core),
                contentDescription = stringResource(R.string.cd_jarvis_core),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(18.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StarterCommands.forEachIndexed { index, item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onQuickCommand(item.command) }
                        .testTag("quick_command_chip_$index"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
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
    }
}

@Composable
private fun MessageBubble(
    message: JarvisMessage,
    isSpeaking: Boolean,
    onRunAction: (JarvisAction) -> Unit,
    onCopy: () -> Unit,
    onToggleSpeak: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (message.isUser) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.widthIn(max = 520.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(0.95f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (message.action != null) {
                        ActionDispatchCard(
                            message = message,
                            action = message.action,
                            onRunAction = { onRunAction(message.action) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onCopy,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("copy_message_button_${message.id}"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kopyala", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = onToggleSpeak,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("tts_message_button_${message.id}"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) {
                                    Icons.Rounded.StopCircle
                                } else {
                                    Icons.AutoMirrored.Rounded.VolumeUp
                                },
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSpeaking) "Durdur" else "Sesli Oku",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStatusRow(
    text: String,
    isLoading: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                ),
            )

            AnimatedVisibility(visible = inputText.isNotBlank() && !isProcessing) {
                FilledIconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_command_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
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

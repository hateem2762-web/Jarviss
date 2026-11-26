package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.JarvisUiState

private val ContextPresets = listOf(
    "Kısa ve net yanıt ver",
    "Müzik deyince Spotify aç",
    "Teknik terimleri İngilizce koru",
    "Resmi değil samimi ol",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
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
    onStartCrosshair: () -> Unit,
    onSaveUserContext: (String) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleAutoExecute: (Boolean) -> Unit,
    onToggleSpeakReplies: (Boolean) -> Unit,
    onTogglePersistentTrigger: (Boolean) -> Unit,
    onToggleFloatingBubble: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleProactiveAlerts: (Boolean) -> Unit,
    onChangeImportanceThreshold: (Int) -> Unit,
    onDisableAllOverlays: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var contextDraft by rememberSaveable { mutableStateOf(uiState.preferences.userContext) }
    var showAppsPreview by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.preferences.userContext) {
        if (contextDraft.isEmpty() && uiState.preferences.userContext.isNotEmpty()) {
            contextDraft = uiState.preferences.userContext
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Overlay & Quick Trigger Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Kayan Pencere (Overlay) ve Hızlı Tetikleme",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Hızlı Ayarlar kutucuğundan (Quick Settings Tile), kalıcı mini bildirimden veya kayan baloncuktan herhangi bir ekranda JARVIS'i açabilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onRequestOverlayPermission,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasOverlayPermission) "Üstte Göster: Açık" else "Üstte Göster İzni",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        OutlinedButton(
                            onClick = onRequestMicPermission,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasMicPermission) "Mikrofon: Açık" else "Mikrofon İzni",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    val a11yActive = hasAccessibilityPermission || uiState.isAccessibilityConnected
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_accessibility_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessibilityNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (a11yActive) {
                                "Erişilebilirlik Dokunma Servisi: Aktif"
                            } else {
                                "Erişilebilirlik Servisini Aç (Dokunma İçin)"
                            }
                        )
                    }

                    SettingToggleRow(
                        title = "Kalıcı mini bildirim",
                        subtitle = "Bildirim panelinde tek tıkla \"JARVIS'e Konuş\" ve \"Nokta Kaydet\" kısayolu tutar",
                        checked = uiState.preferences.persistentTriggerEnabled,
                        onCheckedChange = onTogglePersistentTrigger,
                        testTag = "toggle_persistent_trigger",
                    )

                    SettingToggleRow(
                        title = "Kayan baloncuk",
                        subtitle = "Ekran kenarında sade hızlı erişim ikonu gösterir (üzerindeki çarpıdan veya aşağı sürükleyerek kapatılır)",
                        checked = uiState.preferences.floatingBubbleEnabled,
                        onCheckedChange = onToggleFloatingBubble,
                        testTag = "toggle_floating_bubble",
                    )

                    SettingToggleRow(
                        title = "\"Jarvis\" sesli uyandırma (uygulama dışı)",
                        subtitle = "Başka bir uygulamadayken \"Jarvis\" dediğinizde kayan pencereyi açar",
                        checked = uiState.preferences.wakeWordEnabled,
                        onCheckedChange = onToggleWakeWord,
                        testTag = "toggle_wake_word",
                    )

                    if (
                        uiState.preferences.floatingBubbleEnabled ||
                        uiState.preferences.persistentTriggerEnabled ||
                        uiState.preferences.wakeWordEnabled
                    ) {
                        OutlinedButton(
                            onClick = onDisableAllOverlays,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("disable_all_overlays_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kayan Baloncuk ve Servisi Tamamen Kapat")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onLaunchOverlay,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_launch_overlay_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kayan Pencere", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = onStartCrosshair,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_start_crosshair_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddLocationAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nokta Kaydet", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Proactive WhatsApp Assistant Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Proaktif WhatsApp Asistanı",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Siz komut vermeden gelen WhatsApp bildirimlerini önem sırasına göre süzer; eşik üstü mesajlar için özel bildirim ve hazır cevap taslağı üretir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedButton(
                        onClick = onOpenNotificationListenerSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_notification_listener_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasNotificationListenerPermission) {
                                "Bildirim Erişimi (WhatsApp Takibi): Açık"
                            } else {
                                "Bildirim Erişimini Aç (WhatsApp İçin)"
                            }
                        )
                    }

                    SettingToggleRow(
                        title = "Proaktif bildirim açık/kapalı",
                        subtitle = "Önemli WhatsApp mesajlarını otomatik analiz edip bildirim ve taslak hazırlar",
                        checked = uiState.preferences.proactiveAlertsEnabled,
                        onCheckedChange = onToggleProactiveAlerts,
                        testTag = "toggle_proactive_alerts",
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Önem Eşiği",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "${uiState.preferences.importanceThreshold} / 10",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Text(
                            text = "Sadece ${uiState.preferences.importanceThreshold} ve üzeri önem puanı alan mesajlar bildirilir (kısa/anlamsız mesajlar API'ye gönderilmeden elenir).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = uiState.preferences.importanceThreshold.toFloat(),
                            onValueChange = { raw ->
                                onChangeImportanceThreshold(kotlin.math.round(raw).toInt().coerceIn(1, 10))
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            enabled = uiState.preferences.proactiveAlertsEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("importance_threshold_slider"),
                        )
                    }
                }
            }

            // User Context Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.context_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.context_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = contextDraft,
                        onValueChange = { contextDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_context_input"),
                        minLines = 4,
                        maxLines = 7,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.context_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ContextPresets.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!contextDraft.contains(preset)) {
                                            contextDraft = if (contextDraft.isBlank()) {
                                                preset
                                            } else {
                                                "$contextDraft\n- $preset"
                                            }
                                        }
                                    },
                            ) {
                                Text(
                                    text = "+ $preset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onSaveUserContext(contextDraft) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_context_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.save_context))
                    }
                }
            }

            // Preferences Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Asistan Davranışı",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    SettingToggleRow(
                        title = "Aksiyonları otomatik çalıştır",
                        subtitle = "Uygulama açma, URL ve hatırlatıcıları anında uygular",
                        checked = uiState.preferences.autoExecuteActions,
                        onCheckedChange = onToggleAutoExecute,
                        testTag = "toggle_auto_execute",
                    )

                    SettingToggleRow(
                        title = "Yanıtları sesli oku (TTS)",
                        subtitle = "JARVIS yanıt özetlerini Türkçe seslendirir",
                        checked = uiState.preferences.speakReplies,
                        onCheckedChange = onToggleSpeakReplies,
                        testTag = "toggle_speak_replies",
                    )

                    SettingToggleRow(
                        title = "Obsidian Koyu HUD",
                        subtitle = "Yüksek kontrastlı gece komuta arayüzü",
                        checked = uiState.preferences.darkTheme,
                        onCheckedChange = onToggleDarkTheme,
                        testTag = "toggle_dark_theme",
                    )
                }
            }

            // Installed Apps Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Taranan Uygulamalar (${uiState.installedApps.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        TextButton(
                            onClick = { showAppsPreview = !showAppsPreview },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text(if (showAppsPreview) "Gizle" else "Göster")
                        }
                    }

                    if (showAppsPreview) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            uiState.installedApps.take(36).forEach { app ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("settings_clear_history_button"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sohbet Geçmişini Temizle")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Geçmiş temizlensin mi?") },
            text = { Text("Tüm konuşma ve aksiyon geçmişi kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearHistory()
                    }
                ) {
                    Text("Temizle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Vazgeç")
                }
            },
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

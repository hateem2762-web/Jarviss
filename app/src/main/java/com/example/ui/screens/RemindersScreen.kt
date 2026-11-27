package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.InstalledAppInfo
import com.example.model.ReminderItem
import com.example.model.ScheduledMessageLogItem
import com.example.model.ScheduledMessageRuleItem
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PresetMessagingApps = listOf(
    "TikTok" to "com.zhiliaoapp.musically",
    "WhatsApp" to "com.whatsapp",
    "Instagram" to "com.instagram.android",
    "Telegram" to "org.telegram.messenger",
)

@Composable
fun RemindersScreen(
    reminders: List<ReminderItem>,
    scheduledRules: List<ScheduledMessageRuleItem>,
    scheduledLogs: List<ScheduledMessageLogItem>,
    isAccessibilityConnected: Boolean,
    installedApps: List<InstalledAppInfo>,
    canScheduleExactAlarms: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleComplete: (ReminderItem) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onExportTimer: (ReminderItem) -> Unit,
    onSaveScheduledRule: (Long?, String, String, String, String, Int, Int, Boolean) -> Unit,
    onToggleScheduledRule: (ScheduledMessageRuleItem) -> Unit,
    onDeleteScheduledRule: (Long) -> Unit,
    onRunScheduledRuleNow: (ScheduledMessageRuleItem) -> Unit,
    onClearScheduledLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
    var editingRule by remember { mutableStateOf<ScheduledMessageRuleItem?>(null) }
    var showRuleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (selectedSubTab == 0) {
                            Icons.Rounded.NotificationsActive
                        } else {
                            Icons.Rounded.ScheduleSend
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (selectedSubTab == 0) "Hatırlatıcılar" else "Zamanlı Otomatik Mesaj",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (selectedSubTab == 0) {
                    val activeCount = reminders.count { !it.isCompleted }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "$activeCount aktif",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            editingRule = null
                            showRuleDialog = true
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_scheduled_rule_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kural Ekle", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Segmented Switcher: Reminders vs Scheduled Auto-Messaging
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val tab0Active = selectedSubTab == 0
                val tab1Active = selectedSubTab == 1
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (tab0Active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (tab0Active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedSubTab = 0 }
                        .testTag("subtab_reminders"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (tab0Active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hatırlatıcı (${reminders.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (tab0Active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (tab1Active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (tab1Active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedSubTab = 1 }
                        .testTag("subtab_scheduled_messages"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScheduleSend,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (tab1Active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Oto Mesaj (${scheduledRules.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (tab1Active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = JarvisAmber,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tam Dakikalık Alarm İzni",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Uygulama kapalıyken bile tam saniyesinde çalışması için Alarmlar ve Hatırlatıcılar iznini açın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onRequestExactAlarmPermission,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_exact_alarm_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Aç", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (selectedSubTab == 0) {
            if (reminders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.reminders_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.reminders_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(reminders, key = { it.id }) { item ->
                        ReminderCard(
                            item = item,
                            onToggleComplete = { onToggleComplete(item) },
                            onDelete = { onDeleteReminder(item.id) },
                            onExportTimer = { onExportTimer(item) },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isAccessibilityConnected) {
                    item(key = "a11y_warning") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, JarvisAmber.copy(alpha = 0.55f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccessibilityNew,
                                    contentDescription = null,
                                    tint = JarvisAmber,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Erişilebilirlik Servisi Gerekli",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "TikTok / WhatsApp içinde otomatik kişi arama ve mesaj gönderme için JARVIS Erişilebilirlik Servisi'ni açın.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenAccessibilitySettings,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("scheduled_msg_open_a11y_button"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Aç", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                if (scheduledRules.isEmpty()) {
                    item(key = "empty_rules") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ScheduleSend,
                                    contentDescription = null,
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(44.dp),
                                )
                                Text(
                                    text = "Henüz zamanlı otomatik mesaj yok",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "\"Saat 23:00'ten sonra TikTok'ta Ahmet ve Ayşe'ye '.' mesajı at\" de veya yukarıdaki + Kural Ekle butonunu kullan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Button(
                                    onClick = {
                                        editingRule = null
                                        showRuleDialog = true
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("İlk Kuralı Oluştur")
                                }
                            }
                        }
                    }
                } else {
                    items(scheduledRules, key = { it.id }) { rule ->
                        ScheduledRuleCard(
                            rule = rule,
                            onToggle = { onToggleScheduledRule(rule) },
                            onEdit = {
                                editingRule = rule
                                showRuleDialog = true
                            },
                            onRunNow = { onRunScheduledRuleNow(rule) },
                            onDelete = { onDeleteScheduledRule(rule.id) },
                        )
                    }
                }

                if (scheduledLogs.isNotEmpty()) {
                    item(key = "logs_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Gönderim Günlükleri",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            TextButton(
                                onClick = onClearScheduledLogs,
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Temizle", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    items(scheduledLogs.take(20), key = { "log_${it.id}" }) { log ->
                        ScheduledLogRow(log = log)
                    }
                }
            }
        }
    }

    if (showRuleDialog) {
        ScheduledRuleEditorDialog(
            initialRule = editingRule,
            installedApps = installedApps,
            onDismiss = { showRuleDialog = false },
            onSave = { appName, appPkg, recipientsCsv, messageText, hour, minute, repeatDaily ->
                onSaveScheduledRule(
                    editingRule?.id,
                    appName,
                    appPkg,
                    recipientsCsv,
                    messageText,
                    hour,
                    minute,
                    repeatDaily,
                )
                showRuleDialog = false
            },
        )
    }
}

@Composable
private fun ReminderCard(
    item: ReminderItem,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onExportTimer: () -> Unit,
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss · dd MMM", Locale("tr", "TR"))
    val formattedTime = timeFormat.format(Date(item.triggerAtMillis))

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${item.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggleComplete() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reminder_checkbox_${item.id}"),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.isCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                    ),
                    color = if (item.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                val meta = buildString {
                    if (item.durationLabel.isNotBlank()) {
                        append(item.durationLabel)
                        append("  •  ")
                    }
                    append(formattedTime)
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (!item.isCompleted) {
                IconButton(
                    onClick = onExportTimer,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reminder_timer_button_${item.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = "Sistem zamanlayıcısına aktar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reminder_delete_button_${item.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Hatırlatıcıyı sil",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScheduledRuleCard(
    rule: ScheduledMessageRuleItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (rule.isEnabled) JarvisCyan.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scheduled_rule_card_${rule.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = JarvisCyan.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = rule.formattedTime(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = JarvisCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Column {
                        Text(
                            text = rule.targetAppName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (rule.repeatDaily) "Her gün tekrarlar" else "Tek seferlik",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("scheduled_rule_switch_${rule.id}"),
                )
            }

            Text(
                text = "Alıcılar: ${rule.recipients.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Mesaj: \"${rule.messageText}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (rule.lastStatus.isNotBlank()) {
                Text(
                    text = "Durum: ${rule.lastStatus}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRunNow,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scheduled_rule_run_now_${rule.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Şimdi Dene", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scheduled_rule_edit_${rule.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Kuralı düzenle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scheduled_rule_delete_${rule.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Kuralı sil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledLogRow(log: ScheduledMessageLogItem) {
    val timeFormat = SimpleDateFormat("dd MMM HH:mm:ss", Locale("tr", "TR"))
    val statusColor = when (log.status) {
        "SENT" -> JarvisEmerald
        "SKIPPED_LOCKED" -> JarvisAmber
        else -> JarvisCrimson
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${log.targetAppName} • ${log.recipientsSummary}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = log.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
            Text(
                text = log.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeFormat.format(Date(log.executedAtMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun ScheduledRuleEditorDialog(
    initialRule: ScheduledMessageRuleItem?,
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onSave: (
        appName: String,
        appPackage: String,
        recipientsCsv: String,
        messageText: String,
        hour: Int,
        minute: Int,
        repeatDaily: Boolean,
    ) -> Unit,
) {
    var selectedAppName by remember { mutableStateOf(initialRule?.targetAppName ?: "TikTok") }
    var selectedAppPkg by remember { mutableStateOf(initialRule?.targetAppPackage ?: "com.zhiliaoapp.musically") }
    var recipientsCsv by remember { mutableStateOf(initialRule?.recipients?.joinToString(", ") ?: "") }
    var messageText by remember { mutableStateOf(initialRule?.messageText ?: ".") }
    var hourText by remember { mutableStateOf((initialRule?.triggerHour ?: 23).toString().padStart(2, '0')) }
    var minuteText by remember { mutableStateOf((initialRule?.triggerMinute ?: 0).toString().padStart(2, '0')) }
    var repeatDaily by remember { mutableStateOf(initialRule?.repeatDaily ?: true) }

    val appOptions = remember(installedApps) {
        buildList {
            PresetMessagingApps.forEach { add(it) }
            installedApps.take(12).forEach { add(it.label to it.packageName) }
        }.distinctBy { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialRule == null) "Zamanlı Otomatik Mesaj" else "Kuralı Düzenle")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Hedef Uygulama",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    appOptions.forEach { (label, pkg) ->
                        val active = pkg == selectedAppPkg
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedAppName = label
                                    selectedAppPkg = pkg
                                },
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = recipientsCsv,
                    onValueChange = { recipientsCsv = it },
                    label = { Text("Kişiler (virgülle ayırın)") },
                    placeholder = { Text("Ahmet, Ayşe") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_recipients_input"),
                )

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Gönderilecek Mesaj") },
                    placeholder = { Text(".") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_message_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Saat (0-23)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_hour_input"),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Dakika (0-59)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_minute_input"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Her gün tekrarla",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = repeatDaily,
                        onCheckedChange = { repeatDaily = it },
                        modifier = Modifier.testTag("rule_repeat_daily_switch"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = (hourText.toIntOrNull() ?: 23).coerceIn(0, 23)
                    val m = (minuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                    onSave(selectedAppName, selectedAppPkg, recipientsCsv, messageText, h, m, repeatDaily)
                },
                enabled = recipientsCsv.isNotBlank(),
                modifier = Modifier.testTag("rule_save_button"),
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
    )
}

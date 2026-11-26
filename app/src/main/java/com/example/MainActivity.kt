package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.accessibility.JarvisAccessibilityService
import com.example.ai.configureAppCheck
import com.example.model.JarvisDestination
import com.example.notifications.JarvisNotificationListenerService
import com.example.notifications.ProactiveAlertNotifier
import com.example.overlay.JarvisOverlayService
import com.example.overlay.OverlayLaunchActivity
import com.example.system.ReminderScheduler
import com.example.ui.JarvisViewModel
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.CoordinatesScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var openAssistantTabSignal by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        if (intent?.getLongExtra(ProactiveAlertNotifier.EXTRA_PROACTIVE_ALERT_ID, -1L) ?: -1L > 0L) {
            openAssistantTabSignal++
        }

        enableEdgeToEdge()
        setContent {
            JarvisApp(
                launchedForOverlayPermission = intent?.getBooleanExtra(
                    OverlayLaunchActivity.EXTRA_NEEDS_OVERLAY_PERMISSION,
                    false
                ) == true,
                openAssistantTabSignal = openAssistantTabSignal,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getLongExtra(ProactiveAlertNotifier.EXTRA_PROACTIVE_ALERT_ID, -1L) > 0L) {
            openAssistantTabSignal++
        }
    }
}

@Composable
fun JarvisApp(
    launchedForOverlayPermission: Boolean = false,
    openAssistantTabSignal: Int = 0,
    viewModel: JarvisViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentDestination by rememberSaveable { mutableStateOf(JarvisDestination.ASSISTANT) }
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(JarvisAccessibilityService.isAccessibilityEnabled(context))
    }
    var hasNotificationListenerPermission by remember {
        mutableStateOf(JarvisNotificationListenerService.isNotificationListenerEnabled(context))
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(ReminderScheduler.canScheduleExactAlarms(context))
    }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(openAssistantTabSignal) {
        if (openAssistantTabSignal > 0) {
            currentDestination = JarvisDestination.ASSISTANT
        }
    }

    // Refresh permission states whenever returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasAccessibilityPermission = JarvisAccessibilityService.isAccessibilityEnabled(context)
                hasNotificationListenerPermission =
                    JarvisNotificationListenerService.isNotificationListenerEnabled(context)
                hasExactAlarmPermission = ReminderScheduler.canScheduleExactAlarms(context)
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasNotificationPermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                viewModel.refreshInstalledApps()
                if (hasNotificationPermission) {
                    viewModel.syncOverlayServiceIfNeeded()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = currentDestination != JarvisDestination.ASSISTANT) {
        currentDestination = JarvisDestination.ASSISTANT
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.mic_permission_needed))
            }
        }
    }

    val voiceAndStartMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.startVoiceCommand()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.mic_permission_needed))
            }
        }
    }

    val wakeWordMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.setWakeWordEnabled(true)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.mic_permission_needed))
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted
        viewModel.syncOverlayServiceIfNeeded()
    }

    val requestOverlayPermission = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val launchOverlay = {
        if (Settings.canDrawOverlays(context)) {
            JarvisOverlayService.showOverlay(context)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.overlay_permission_needed))
            }
            requestOverlayPermission()
        }
    }

    val launchCrosshair = {
        if (Settings.canDrawOverlays(context)) {
            JarvisOverlayService.startCrosshair(context)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.overlay_permission_needed))
            }
            requestOverlayPermission()
        }
    }

    val openAccessibilitySettings = {
        JarvisAccessibilityService.openAccessibilitySettings(context)
    }

    val openNotificationListenerSettings = {
        JarvisNotificationListenerService.openNotificationListenerSettings(context)
    }

    LaunchedEffect(launchedForOverlayPermission) {
        if (launchedForOverlayPermission && !Settings.canDrawOverlays(context)) {
            snackbarHostState.showSnackbar(context.getString(R.string.overlay_permission_needed))
        }
    }

    val toggleFloatingBubble: (Boolean) -> Unit = { enabled ->
        if (enabled && !Settings.canDrawOverlays(context)) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.overlay_permission_needed))
            }
            requestOverlayPermission()
        } else {
            viewModel.setFloatingBubbleEnabled(enabled)
        }
    }

    val toggleWakeWord: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setWakeWordEnabled(false)
        } else if (!Settings.canDrawOverlays(context)) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "\"Jarvis\" uyandırma için önce Üstte Göster izni gerekli."
                )
            }
            requestOverlayPermission()
        } else if (!hasMicPermission) {
            wakeWordMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.setWakeWordEnabled(true)
        }
    }

    val needsServiceOrReminders = uiState.reminders.isNotEmpty() ||
        uiState.preferences.persistentTriggerEnabled ||
        uiState.preferences.floatingBubbleEnabled ||
        uiState.preferences.wakeWordEnabled ||
        (uiState.preferences.proactiveAlertsEnabled && hasNotificationListenerPermission)

    LaunchedEffect(needsServiceOrReminders, hasNotificationPermission) {
        if (!needsServiceOrReminders) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            if (!askedNotificationPermission) {
                askedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.syncOverlayServiceIfNeeded()
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    val activeRemindersCount = remember(uiState.reminders) {
        uiState.reminders.count { !it.isCompleted }
    }

    val onVoiceOrbClick = {
        if (uiState.voiceState.isListening) {
            viewModel.stopVoiceCommand()
        } else if (hasMicPermission) {
            viewModel.startVoiceCommand()
        } else {
            voiceAndStartMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    JarvisTheme(darkTheme = uiState.preferences.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        JarvisBottomNavBar(
                            currentDestination = currentDestination,
                            activeRemindersCount = activeRemindersCount,
                            onSelect = { currentDestination = it },
                        )
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        JarvisNavRail(
                            currentDestination = currentDestination,
                            activeRemindersCount = activeRemindersCount,
                            onSelect = { currentDestination = it },
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentDestination) {
                            JarvisDestination.ASSISTANT -> AssistantScreen(
                                uiState = uiState,
                                hasOverlayPermission = hasOverlayPermission,
                                hasMicPermission = hasMicPermission,
                                hasAccessibilityPermission = hasAccessibilityPermission,
                                hasNotificationListenerPermission = hasNotificationListenerPermission,
                                onRequestOverlayPermission = requestOverlayPermission,
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onOpenAccessibilitySettings = openAccessibilitySettings,
                                onOpenNotificationListenerSettings = openNotificationListenerSettings,
                                onLaunchOverlay = launchOverlay,
                                onToggleWakeWord = toggleWakeWord,
                                onDisableAllOverlays = viewModel::disableAllOverlayModes,
                                onUpdateProactiveDraft = viewModel::updateProactiveAlertDraft,
                                onCopyProactiveDraft = viewModel::copyProactiveDraft,
                                onOpenProactiveWhatsApp = viewModel::openWhatsAppForAlert,
                                onSendProactiveDirectReply = viewModel::sendProactiveDirectReply,
                                onDismissProactiveAlert = viewModel::dismissProactiveAlert,
                                onSendCommand = viewModel::sendCommand,
                                onRetry = viewModel::retryLastCommand,
                                onRunAction = viewModel::executeActionForMessage,
                                onCopyMessage = viewModel::copyTextToClipboard,
                                onToggleSpeakMessage = viewModel::toggleSpeakMessage,
                                onVoiceOrbClick = onVoiceOrbClick,
                                onContextClick = { currentDestination = JarvisDestination.SETTINGS },
                                onClearChat = viewModel::clearHistory,
                                onDismissError = viewModel::dismissError,
                            )

                            JarvisDestination.NOTES -> NotesScreen(
                                notes = uiState.notes,
                                onAddNote = viewModel::addManualNote,
                                onUpdateNote = viewModel::updateNote,
                                onDeleteNote = viewModel::deleteNote,
                                onCopyNote = viewModel::copyTextToClipboard,
                            )

                            JarvisDestination.COORDINATES -> CoordinatesScreen(
                                uiState = uiState,
                                hasAccessibilityPermission = hasAccessibilityPermission,
                                onOpenAccessibilitySettings = openAccessibilitySettings,
                                onSelectManualProfile = viewModel::selectManualProfile,
                                onStartCrosshair = launchCrosshair,
                                onTestTapCoordinate = viewModel::testTapCoordinate,
                                onDeleteCoordinate = viewModel::deleteCoordinate,
                            )

                            JarvisDestination.REMINDERS -> RemindersScreen(
                                reminders = uiState.reminders,
                                canScheduleExactAlarms = hasExactAlarmPermission,
                                onRequestExactAlarmPermission = {
                                    ReminderScheduler.openExactAlarmSettings(context)
                                },
                                onToggleComplete = viewModel::toggleReminderComplete,
                                onDeleteReminder = viewModel::deleteReminder,
                                onExportTimer = viewModel::exportReminderToSystemTimer,
                            )

                            JarvisDestination.SETTINGS -> SettingsScreen(
                                uiState = uiState,
                                hasOverlayPermission = hasOverlayPermission,
                                hasMicPermission = hasMicPermission,
                                hasAccessibilityPermission = hasAccessibilityPermission,
                                hasNotificationListenerPermission = hasNotificationListenerPermission,
                                onRequestOverlayPermission = requestOverlayPermission,
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onOpenAccessibilitySettings = openAccessibilitySettings,
                                onOpenNotificationListenerSettings = openNotificationListenerSettings,
                                onLaunchOverlay = launchOverlay,
                                onStartCrosshair = launchCrosshair,
                                onSaveUserContext = viewModel::updateUserContext,
                                onToggleDarkTheme = viewModel::setDarkTheme,
                                onToggleAutoExecute = viewModel::setAutoExecuteActions,
                                onToggleSpeakReplies = viewModel::setSpeakReplies,
                                onTogglePersistentTrigger = viewModel::setPersistentTriggerEnabled,
                                onToggleFloatingBubble = toggleFloatingBubble,
                                onToggleWakeWord = toggleWakeWord,
                                onToggleProactiveAlerts = viewModel::setProactiveAlertsEnabled,
                                onChangeImportanceThreshold = viewModel::setImportanceThreshold,
                                onDisableAllOverlays = viewModel::disableAllOverlayModes,
                                onClearHistory = viewModel::clearHistory,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JarvisBottomNavBar(
    currentDestination: JarvisDestination,
    activeRemindersCount: Int,
    onSelect: (JarvisDestination) -> Unit,
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = currentDestination == JarvisDestination.ASSISTANT,
                onClick = { onSelect(JarvisDestination.ASSISTANT) },
                modifier = Modifier.testTag("nav_assistant"),
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (currentDestination == JarvisDestination.ASSISTANT) {
                            Icons.Rounded.AutoAwesome
                        } else {
                            Icons.Outlined.AutoAwesome
                        },
                        contentDescription = stringResource(R.string.nav_assistant),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_assistant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            NavigationBarItem(
                selected = currentDestination == JarvisDestination.NOTES,
                onClick = { onSelect(JarvisDestination.NOTES) },
                modifier = Modifier.testTag("nav_notes"),
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (currentDestination == JarvisDestination.NOTES) {
                            Icons.Rounded.EditNote
                        } else {
                            Icons.Outlined.EditNote
                        },
                        contentDescription = stringResource(R.string.nav_notes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_notes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            NavigationBarItem(
                selected = currentDestination == JarvisDestination.COORDINATES,
                onClick = { onSelect(JarvisDestination.COORDINATES) },
                modifier = Modifier.testTag("nav_coordinates"),
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (currentDestination == JarvisDestination.COORDINATES) {
                            Icons.Rounded.MyLocation
                        } else {
                            Icons.Outlined.MyLocation
                        },
                        contentDescription = stringResource(R.string.nav_coordinates),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_coordinates),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            NavigationBarItem(
                selected = currentDestination == JarvisDestination.REMINDERS,
                onClick = { onSelect(JarvisDestination.REMINDERS) },
                modifier = Modifier.testTag("nav_reminders"),
                colors = itemColors,
                icon = {
                    RemindersBadgeIcon(
                        selected = currentDestination == JarvisDestination.REMINDERS,
                        count = activeRemindersCount,
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_reminders),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            NavigationBarItem(
                selected = currentDestination == JarvisDestination.SETTINGS,
                onClick = { onSelect(JarvisDestination.SETTINGS) },
                modifier = Modifier.testTag("nav_settings"),
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (currentDestination == JarvisDestination.SETTINGS) {
                            Icons.Rounded.Tune
                        } else {
                            Icons.Outlined.Tune
                        },
                        contentDescription = stringResource(R.string.nav_settings),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun JarvisNavRail(
    currentDestination: JarvisDestination,
    activeRemindersCount: Int,
    onSelect: (JarvisDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavigationRailItem(
            selected = currentDestination == JarvisDestination.ASSISTANT,
            onClick = { onSelect(JarvisDestination.ASSISTANT) },
            modifier = Modifier.testTag("rail_assistant"),
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = if (currentDestination == JarvisDestination.ASSISTANT) {
                        Icons.Rounded.AutoAwesome
                    } else {
                        Icons.Outlined.AutoAwesome
                    },
                    contentDescription = stringResource(R.string.nav_assistant),
                )
            },
            label = { Text(stringResource(R.string.nav_assistant)) },
        )

        NavigationRailItem(
            selected = currentDestination == JarvisDestination.NOTES,
            onClick = { onSelect(JarvisDestination.NOTES) },
            modifier = Modifier.testTag("rail_notes"),
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = if (currentDestination == JarvisDestination.NOTES) {
                        Icons.Rounded.EditNote
                    } else {
                        Icons.Outlined.EditNote
                    },
                    contentDescription = stringResource(R.string.nav_notes),
                )
            },
            label = { Text(stringResource(R.string.nav_notes)) },
        )

        NavigationRailItem(
            selected = currentDestination == JarvisDestination.COORDINATES,
            onClick = { onSelect(JarvisDestination.COORDINATES) },
            modifier = Modifier.testTag("rail_coordinates"),
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = if (currentDestination == JarvisDestination.COORDINATES) {
                        Icons.Rounded.MyLocation
                    } else {
                        Icons.Outlined.MyLocation
                    },
                    contentDescription = stringResource(R.string.nav_coordinates),
                )
            },
            label = { Text(stringResource(R.string.nav_coordinates)) },
        )

        NavigationRailItem(
            selected = currentDestination == JarvisDestination.REMINDERS,
            onClick = { onSelect(JarvisDestination.REMINDERS) },
            modifier = Modifier.testTag("rail_reminders"),
            colors = itemColors,
            icon = {
                RemindersBadgeIcon(
                    selected = currentDestination == JarvisDestination.REMINDERS,
                    count = activeRemindersCount,
                )
            },
            label = { Text(stringResource(R.string.nav_reminders)) },
        )

        NavigationRailItem(
            selected = currentDestination == JarvisDestination.SETTINGS,
            onClick = { onSelect(JarvisDestination.SETTINGS) },
            modifier = Modifier.testTag("rail_settings"),
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = if (currentDestination == JarvisDestination.SETTINGS) {
                        Icons.Rounded.Tune
                    } else {
                        Icons.Outlined.Tune
                    },
                    contentDescription = stringResource(R.string.nav_settings),
                )
            },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
    }
}

@Composable
private fun RemindersBadgeIcon(
    selected: Boolean,
    count: Int,
) {
    BadgedBox(
        badge = {
            if (count > 0) {
                Badge { Text(text = count.toString()) }
            }
        }
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.Alarm else Icons.Outlined.Alarm,
            contentDescription = stringResource(R.string.nav_reminders),
        )
    }
}

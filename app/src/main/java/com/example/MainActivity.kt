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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.example.overlay.JarvisOverlayService
import com.example.overlay.OverlayLaunchActivity
import com.example.ui.JarvisViewModel
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.CoordinatesScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            JarvisApp(
                launchedForOverlayPermission = intent?.getBooleanExtra(
                    OverlayLaunchActivity.EXTRA_NEEDS_OVERLAY_PERMISSION,
                    false
                ) == true
            )
        }
    }
}

@Composable
fun JarvisApp(
    launchedForOverlayPermission: Boolean = false,
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
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Refresh permission states whenever returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasAccessibilityPermission = JarvisAccessibilityService.isAccessibilityEnabled(context)
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.refreshInstalledApps()
                viewModel.syncOverlayServiceIfNeeded()
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
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

    LaunchedEffect(launchedForOverlayPermission) {
        if (launchedForOverlayPermission && !Settings.canDrawOverlays(context)) {
            snackbarHostState.showSnackbar(context.getString(R.string.overlay_permission_needed))
        }
    }

    // Request notification permission on Android 13+ when persistent trigger or reminders are used
    LaunchedEffect(uiState.reminders.size, uiState.preferences.persistentTriggerEnabled) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !askedNotificationPermission &&
            (uiState.reminders.isNotEmpty() || uiState.preferences.persistentTriggerEnabled)
        ) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                askedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.syncOverlayServiceIfNeeded()
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
                                onRequestOverlayPermission = requestOverlayPermission,
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onOpenAccessibilitySettings = openAccessibilitySettings,
                                onLaunchOverlay = launchOverlay,
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
                                onToggleComplete = viewModel::toggleReminderComplete,
                                onDeleteReminder = viewModel::deleteReminder,
                                onExportTimer = viewModel::exportReminderToSystemTimer,
                            )

                            JarvisDestination.SETTINGS -> SettingsScreen(
                                uiState = uiState,
                                hasOverlayPermission = hasOverlayPermission,
                                hasMicPermission = hasMicPermission,
                                hasAccessibilityPermission = hasAccessibilityPermission,
                                onRequestOverlayPermission = requestOverlayPermission,
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onOpenAccessibilitySettings = openAccessibilitySettings,
                                onLaunchOverlay = launchOverlay,
                                onStartCrosshair = launchCrosshair,
                                onSaveUserContext = viewModel::updateUserContext,
                                onToggleDarkTheme = viewModel::setDarkTheme,
                                onToggleAutoExecute = viewModel::setAutoExecuteActions,
                                onToggleSpeakReplies = viewModel::setSpeakReplies,
                                onTogglePersistentTrigger = viewModel::setPersistentTriggerEnabled,
                                onToggleFloatingBubble = viewModel::setFloatingBubbleEnabled,
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
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        NavigationBarItem(
            selected = currentDestination == JarvisDestination.ASSISTANT,
            onClick = { onSelect(JarvisDestination.ASSISTANT) },
            modifier = Modifier.testTag("nav_assistant"),
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

        NavigationBarItem(
            selected = currentDestination == JarvisDestination.COORDINATES,
            onClick = { onSelect(JarvisDestination.COORDINATES) },
            modifier = Modifier.testTag("nav_coordinates"),
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

        NavigationBarItem(
            selected = currentDestination == JarvisDestination.REMINDERS,
            onClick = { onSelect(JarvisDestination.REMINDERS) },
            modifier = Modifier.testTag("nav_reminders"),
            icon = {
                RemindersBadgeIcon(
                    selected = currentDestination == JarvisDestination.REMINDERS,
                    count = activeRemindersCount,
                )
            },
            label = { Text(stringResource(R.string.nav_reminders)) },
        )

        NavigationBarItem(
            selected = currentDestination == JarvisDestination.SETTINGS,
            onClick = { onSelect(JarvisDestination.SETTINGS) },
            modifier = Modifier.testTag("nav_settings"),
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
private fun JarvisNavRail(
    currentDestination: JarvisDestination,
    activeRemindersCount: Int,
    onSelect: (JarvisDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavigationRailItem(
            selected = currentDestination == JarvisDestination.ASSISTANT,
            onClick = { onSelect(JarvisDestination.ASSISTANT) },
            modifier = Modifier.testTag("rail_assistant"),
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
            selected = currentDestination == JarvisDestination.COORDINATES,
            onClick = { onSelect(JarvisDestination.COORDINATES) },
            modifier = Modifier.testTag("rail_coordinates"),
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

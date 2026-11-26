package com.example.ui.components

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ActionExecutionStatus
import com.example.model.ActionIntent
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun JarvisTopHudBar(
    isListening: Boolean,
    isProcessing: Boolean,
    hasUserContext: Boolean,
    hasMessages: Boolean,
    onContextClick: () -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = when {
        isListening -> stringResource(R.string.status_listening)
        isProcessing -> stringResource(R.string.status_thinking)
        else -> stringResource(R.string.status_ready)
    }
    val statusColor = when {
        isListening -> JarvisCrimson
        isProcessing -> JarvisAmber
        else -> JarvisEmerald
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
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

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AssistChip(
                onClick = onContextClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("hud_context_chip"),
                label = {
                    Text(
                        text = if (hasUserContext) "Bağlam Aktif" else "Bağlam Ekle",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (hasUserContext) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    labelColor = if (hasUserContext) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            )

            if (hasMessages) {
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_chat_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.cd_clear_chat),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceOrbButton(
    isListening: Boolean,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val ringScale = if (isListening) {
        (pulse + rmsLevel * 0.25f).coerceAtMost(1.5f)
    } else {
        1f
    }

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isListening) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = (size.minDimension / 2f) * (0.72f * ringScale)
                drawCircle(
                    color = JarvisCrimson.copy(alpha = 0.28f),
                    radius = radius.coerceAtMost(size.minDimension / 2f),
                )
            }
        }

        FilledIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .testTag("voice_orb_button"),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isListening) JarvisCrimson else MaterialTheme.colorScheme.primary,
                contentColor = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.cd_voice_input),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun ActionDispatchCard(
    message: JarvisMessage,
    action: JarvisAction,
    onRunAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showJson by remember { mutableStateOf(false) }

    val (statusLabel, statusColor) = when (message.actionStatus) {
        ActionExecutionStatus.EXECUTED -> stringResource(R.string.action_executed) to JarvisEmerald
        ActionExecutionStatus.FAILED -> stringResource(R.string.action_failed) to JarvisCrimson
        ActionExecutionStatus.PENDING -> stringResource(R.string.action_pending) to JarvisAmber
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(
            width = 1.dp,
            color = statusColor.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                ) {
                    Icon(
                        imageVector = action.intent.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = action.intent.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.16f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = when (message.actionStatus) {
                                ActionExecutionStatus.EXECUTED -> Icons.Rounded.CheckCircle
                                ActionExecutionStatus.FAILED -> Icons.Rounded.ErrorOutline
                                ActionExecutionStatus.PENDING -> Icons.Rounded.Schedule
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            val summaryParts = buildList {
                if (action.target.isNotBlank()) add("Hedef: ${action.target}")
                if (action.minutes != null || action.seconds != null) {
                    add("Süre: ${action.formattedDurationLabel()}")
                }
                if (action.message.isNotBlank()) add("Not: ${action.message}")
            }
            if (summaryParts.isNotEmpty()) {
                Text(
                    text = summaryParts.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (message.actionStatusDetail.isNotBlank()) {
                Text(
                    text = message.actionStatusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.actionStatus == ActionExecutionStatus.FAILED) {
                        JarvisCrimson
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRunAction,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("action_run_button_${message.id}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (message.actionStatus == ActionExecutionStatus.PENDING) {
                            "Çalıştır"
                        } else {
                            stringResource(R.string.action_rerun)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showJson = !showJson }
                        .testTag("action_json_toggle_${message.id}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(R.string.action_show_json),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showJson) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = action.toPrettyJson(),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

private fun ActionIntent.icon(): ImageVector = when (this) {
    ActionIntent.OPEN_APP -> Icons.AutoMirrored.Rounded.Launch
    ActionIntent.OPEN_URL -> Icons.Rounded.Public
    ActionIntent.SEARCH_YOUTUBE -> Icons.Rounded.PlayCircle
    ActionIntent.SET_REMINDER -> Icons.Rounded.Alarm
    ActionIntent.GO_HOME -> Icons.Rounded.Home
    ActionIntent.TAP_COORDINATE -> Icons.Rounded.TouchApp
    ActionIntent.NONE -> Icons.Rounded.Code
}

package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NarrationSceneCard
import com.example.model.SceneCardType
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ContextualSceneCard(
    scene: NarrationSceneCard,
    defaultAccent: Color,
    compactOverlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sceneAccent = resolveSceneCardAccent(scene, defaultAccent)

    val subtlePulse by rememberInfiniteTransition(label = "scene_card_pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    AnimatedContent(
        targetState = scene,
        contentKey = { "${it.sentenceIndex}_${it.cardType}_${it.visualKey}_${it.highlightMetric}_${it.emailSender}_${it.title}" },
        transitionSpec = {
            (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 6 }) togetherWith
                (fadeOut(tween(170)) + slideOutVertically(tween(170)) { -it / 6 })
        },
        modifier = modifier
            .widthIn(max = if (compactOverlay) 480.dp else 560.dp)
            .fillMaxWidth(),
        label = "contextual_scene_transition",
    ) { current ->
        val cardAccent = resolveSceneCardAccent(current, defaultAccent)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Canlı bağlam kartı: ${current.categoryBadge} ${current.title}"
                }
                .testTag("contextual_scene_card"),
            shape = RoundedCornerShape(if (compactOverlay) 16.dp else 20.dp),
            color = Color(0xD90A1019),
            border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.42f)),
            shadowElevation = 10.dp,
        ) {
            when (current.cardType) {
                SceneCardType.EMAIL_CARD -> EmailSceneCardBody(
                    scene = current,
                    accent = cardAccent,
                    pulse = subtlePulse,
                    compactOverlay = compactOverlay,
                )

                SceneCardType.METRIC_EVENT,
                SceneCardType.SPORTS_SCORE,
                SceneCardType.FINANCE_CARD,
                SceneCardType.WEATHER_CARD -> MetricFocusedSceneCardBody(
                    scene = current,
                    accent = cardAccent,
                    pulse = subtlePulse,
                    compactOverlay = compactOverlay,
                )

                SceneCardType.MEDIA_CARD,
                SceneCardType.SYSTEM_STATUS,
                SceneCardType.TOPIC_VISUAL -> TopicVisualSceneCardBody(
                    scene = current,
                    accent = cardAccent,
                    pulse = subtlePulse,
                    compactOverlay = compactOverlay,
                )
            }
        }
    }
}

@Composable
private fun EmailSceneCardBody(
    scene: NarrationSceneCard,
    accent: Color,
    pulse: Float,
    compactOverlay: Boolean,
) {
    val pad = if (compactOverlay) 11.dp else 14.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(if (compactOverlay) 7.dp else 9.dp),
    ) {
        SceneCardHeaderRow(
            badge = scene.categoryBadge,
            sentenceIndex = scene.sentenceIndex,
            totalSentences = scene.totalSentences,
            accent = accent,
            trailingTag = scene.emailCount,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Sender avatar circle
            val initial = scene.emailSender.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
            Box(
                modifier = Modifier
                    .size(if (compactOverlay) 36.dp else 42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (scene.emailSender == "Gelen Kutusu") {
                    Icon(
                        imageVector = Icons.Rounded.Mail,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(if (compactOverlay) 18.dp else 21.dp),
                    )
                } else {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = accent,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = scene.emailSender.ifBlank { "Gelen Kutusu" },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color(0xFFF8FAFC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scene.emailSubject.ifBlank { scene.title },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scene.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    maxLines = if (compactOverlay) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier.size(if (compactOverlay) 44.dp else 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawVisualCueVector(
                        cueKey = "email_inbox",
                        accent = accent,
                        pulse = pulse,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricFocusedSceneCardBody(
    scene: NarrationSceneCard,
    accent: Color,
    pulse: Float,
    compactOverlay: Boolean,
) {
    val pad = if (compactOverlay) 11.dp else 14.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(if (compactOverlay) 7.dp else 9.dp),
    ) {
        SceneCardHeaderRow(
            badge = scene.categoryBadge,
            sentenceIndex = scene.sentenceIndex,
            totalSentences = scene.totalSentences,
            accent = accent,
            trailingTag = "",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Contextual icon stage
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.11f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                modifier = Modifier.size(if (compactOverlay) 52.dp else 60.dp),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    drawVisualCueVector(
                        cueKey = scene.visualKey,
                        accent = accent,
                        pulse = pulse,
                    )
                }
            }

            // Text detail
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = scene.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(0xFFF8FAFC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scene.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    maxLines = if (compactOverlay) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Corner/Side Highlight Metric Callout (e.g. "14 YARALI", "2 - 1 SKOR", "%15")
            if (scene.highlightMetric.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = if (compactOverlay) 10.dp else 12.dp,
                            vertical = if (compactOverlay) 7.dp else 9.dp,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = scene.highlightMetric,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (scene.highlightMetric.length > 5) 16.sp else 20.sp,
                            ),
                            color = Color(0xFFF8FAFC),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        if (scene.highlightUnit.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = scene.highlightUnit,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = accent,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicVisualSceneCardBody(
    scene: NarrationSceneCard,
    accent: Color,
    pulse: Float,
    compactOverlay: Boolean,
) {
    val pad = if (compactOverlay) 11.dp else 14.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(if (compactOverlay) 6.dp else 8.dp),
    ) {
        SceneCardHeaderRow(
            badge = scene.categoryBadge,
            sentenceIndex = scene.sentenceIndex,
            totalSentences = scene.totalSentences,
            accent = accent,
            trailingTag = scene.highlightMetric,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.11f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                modifier = Modifier.size(if (compactOverlay) 48.dp else 56.dp),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    drawVisualCueVector(
                        cueKey = scene.visualKey,
                        accent = accent,
                        pulse = pulse,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = scene.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(0xFFF8FAFC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scene.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    maxLines = if (compactOverlay) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SceneCardHeaderRow(
    badge: String,
    sentenceIndex: Int,
    totalSentences: Int,
    accent: Color,
    trailingTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.9.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (trailingTag.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = trailingTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            if (totalSentences > 1) {
                Text(
                    text = "${sentenceIndex + 1}/$totalSentences",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.5.sp,
                    ),
                    color = Color(0xFF94A3B8),
                )
            }
        }
    }
}

private fun resolveSceneCardAccent(
    scene: NarrationSceneCard,
    defaultAccent: Color,
): Color {
    return when (scene.accentToneKey) {
        "email" -> Color(0xFFF87171) // Warm Gmail coral-red
        "alert" -> JarvisCrimson
        "sport" -> JarvisCyan
        "finance" -> JarvisEmerald
        "weather" -> JarvisAmber
        "media" -> Color(0xFFA78BFA)
        "system" -> JarvisCyan
        else -> defaultAccent
    }
}

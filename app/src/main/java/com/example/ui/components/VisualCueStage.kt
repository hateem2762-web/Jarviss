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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VisualCueItem
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun VisualCueStage(
    cues: List<VisualCueItem>,
    accentColor: Color,
    isActive: Boolean,
    compactOverlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (cues.isEmpty()) return

    var activeIndex by remember(cues) { mutableIntStateOf(0) }
    LaunchedEffect(cues, isActive) {
        if (cues.size <= 1) {
            activeIndex = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(if (isActive) 1_650L else 2_400L)
            activeIndex = (activeIndex + 1) % cues.size
        }
    }

    val safeIndex = activeIndex.coerceIn(0, cues.lastIndex)
    val activeCue = cues[safeIndex]

    val pulseTransition = rememberInfiniteTransition(label = "visual_cue_pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cue_pulse",
    )

    val cardWidth = if (compactOverlay) 108.dp else 132.dp
    val canvasHeight = if (compactOverlay) 64.dp else 82.dp

    Surface(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (cues.size > 1) {
                    activeIndex = (activeIndex + 1) % cues.size
                }
            }
            .semantics { contentDescription = "Canlı görsel sahne: ${activeCue.label}" }
            .testTag("visual_cue_stage"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xCC0A111C),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.36f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (compactOverlay) 6.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = activeCue,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(initialScale = 0.86f, animationSpec = tween(240))) togetherWith
                            (fadeOut(tween(180)) + scaleOut(targetScale = 0.9f, animationSpec = tween(180)))
                    },
                    label = "visual_cue_swap",
                ) { cue ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCueHudBackdrop(accentColor, pulse)
                        drawVisualCueVector(
                            cueKey = cue.key,
                            accent = accentColor,
                            pulse = pulse,
                        )
                    }
                }
            }

            Text(
                text = activeCue.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = if (compactOverlay) 8.5.sp else 9.5.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = accentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (cues.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    cues.forEachIndexed { idx, _ ->
                        Box(
                            modifier = Modifier
                                .size(if (idx == safeIndex) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx == safeIndex) accentColor else accentColor.copy(alpha = 0.30f)
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCueHudBackdrop(accent: Color, pulse: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension * 0.42f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.16f + 0.08f * pulse),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = r * 1.25f,
        ),
        radius = r * 1.25f,
        center = Offset(cx, cy),
    )

    // Subtle corner brackets for HUD frame
    val b = size.minDimension * 0.16f
    val stroke = 1.6f
    val c = accent.copy(alpha = 0.42f)
    drawLine(c, Offset(2f, 2f), Offset(2f + b, 2f), stroke)
    drawLine(c, Offset(2f, 2f), Offset(2f, 2f + b), stroke)
    drawLine(c, Offset(size.width - 2f, 2f), Offset(size.width - 2f - b, 2f), stroke)
    drawLine(c, Offset(size.width - 2f, 2f), Offset(size.width - 2f, 2f + b), stroke)
    drawLine(c, Offset(2f, size.height - 2f), Offset(2f + b, size.height - 2f), stroke)
    drawLine(c, Offset(2f, size.height - 2f), Offset(2f, size.height - 2f - b), stroke)
    drawLine(c, Offset(size.width - 2f, size.height - 2f), Offset(size.width - 2f - b, size.height - 2f), stroke)
    drawLine(c, Offset(size.width - 2f, size.height - 2f), Offset(size.width - 2f, size.height - 2f - b), stroke)
}

internal fun DrawScope.drawVisualCueVector(
    cueKey: String,
    accent: Color,
    pulse: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val u = size.minDimension * 0.34f
    val stroke = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val bright = Color(0xFFF1F5F9)

    when (cueKey) {
        "email_inbox" -> {
            val w = u * 1.68f
            val h = u * 1.12f
            val left = cx - w / 2f
            val top = cy - h / 2f
            drawRoundRect(
                color = accent.copy(alpha = 0.16f),
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(10f, 10f),
                style = stroke,
            )
            val flap = Path().apply {
                moveTo(left + 4f, top + 6f)
                lineTo(cx, cy + h * 0.10f)
                lineTo(left + w - 4f, top + 6f)
            }
            drawPath(flap, color = bright, style = stroke)
            // Unread pulse dot on corner
            drawCircle(
                color = Color(0xFFEF4444),
                radius = 5.5f + 1.5f * pulse,
                center = Offset(left + w - 4f, top + 4f),
            )
        }

        "eagle_crest" -> {
            // Stylized geometric wings + crest shield
            val wingPath = Path().apply {
                moveTo(cx, cy - u * 0.35f)
                lineTo(cx - u * 1.05f, cy - u * 0.72f)
                lineTo(cx - u * 0.62f, cy + u * 0.12f)
                lineTo(cx, cy + u * 0.62f)
                lineTo(cx + u * 0.62f, cy + u * 0.12f)
                lineTo(cx + u * 1.05f, cy - u * 0.72f)
                close()
            }
            drawPath(wingPath, color = accent.copy(alpha = 0.22f))
            drawPath(wingPath, color = accent, style = stroke)
            // Crest stripes (black-and-white style contrast)
            drawLine(bright, Offset(cx, cy - u * 0.2f), Offset(cx, cy + u * 0.45f), 2.4f)
            drawCircle(bright, radius = u * 0.14f, center = Offset(cx, cy - u * 0.55f))
        }

        "stadium" -> {
            // Oval stadium arena + pitch lines + floodlights
            drawOval(
                color = accent.copy(alpha = 0.16f),
                topLeft = Offset(cx - u * 1.05f, cy - u * 0.55f),
                size = Size(u * 2.1f, u * 1.1f),
            )
            drawOval(
                color = accent,
                topLeft = Offset(cx - u * 1.05f, cy - u * 0.55f),
                size = Size(u * 2.1f, u * 1.1f),
                style = stroke,
            )
            drawLine(bright.copy(alpha = 0.8f), Offset(cx, cy - u * 0.48f), Offset(cx, cy + u * 0.48f), 1.8f)
            drawCircle(bright.copy(alpha = 0.8f), radius = u * 0.20f, center = Offset(cx, cy), style = Stroke(1.8f))
            // 4 corner floodlight beams
            val beamAlpha = 0.45f + 0.35f * pulse
            drawCircle(bright.copy(alpha = beamAlpha), radius = 3.2f, center = Offset(cx - u * 0.82f, cy - u * 0.68f))
            drawCircle(bright.copy(alpha = beamAlpha), radius = 3.2f, center = Offset(cx + u * 0.82f, cy - u * 0.68f))
        }

        "fist_impact" -> {
            // Shockwave starburst + clash vectors
            val spikes = 8
            val impactPath = Path()
            for (i in 0 until spikes * 2) {
                val r = if (i % 2 == 0) u * (0.95f + 0.12f * pulse) else u * 0.42f
                val angle = (i * PI / spikes).toFloat()
                val px = cx + cos(angle) * r
                val py = cy + sin(angle) * r
                if (i == 0) impactPath.moveTo(px, py) else impactPath.lineTo(px, py)
            }
            impactPath.close()
            drawPath(impactPath, color = Color(0xFFF43F5E).copy(alpha = 0.25f))
            drawPath(impactPath, color = Color(0xFFF43F5E), style = stroke)
            drawLine(bright, Offset(cx - u * 0.28f, cy - u * 0.28f), Offset(cx + u * 0.28f, cy + u * 0.28f), 3f)
            drawLine(bright, Offset(cx + u * 0.28f, cy - u * 0.28f), Offset(cx - u * 0.28f, cy + u * 0.28f), 3f)
        }

        "ball_sport" -> {
            val r = u * 0.82f
            drawCircle(accent.copy(alpha = 0.16f), radius = r, center = Offset(cx, cy))
            drawCircle(accent, radius = r, center = Offset(cx, cy), style = stroke)
            val pentagon = Path()
            for (i in 0 until 5) {
                val a = (-PI / 2 + i * 2 * PI / 5).toFloat()
                val px = cx + cos(a) * r * 0.38f
                val py = cy + sin(a) * r * 0.38f
                if (i == 0) pentagon.moveTo(px, py) else pentagon.lineTo(px, py)
                drawLine(bright.copy(alpha = 0.7f), Offset(px, py), Offset(cx + cos(a) * r * 0.88f, cy + sin(a) * r * 0.88f), 1.8f)
            }
            pentagon.close()
            drawPath(pentagon, color = bright)
        }

        "trophy_cup" -> {
            val cup = Path().apply {
                moveTo(cx - u * 0.58f, cy - u * 0.72f)
                lineTo(cx + u * 0.58f, cy - u * 0.72f)
                lineTo(cx + u * 0.36f, cy + u * 0.08f)
                lineTo(cx - u * 0.36f, cy + u * 0.08f)
                close()
            }
            drawPath(cup, color = accent.copy(alpha = 0.22f))
            drawPath(cup, color = accent, style = stroke)
            drawLine(accent, Offset(cx, cy + u * 0.08f), Offset(cx, cy + u * 0.62f), 3f)
            drawLine(bright, Offset(cx - u * 0.42f, cy + u * 0.65f), Offset(cx + u * 0.42f, cy + u * 0.65f), 3.2f)
        }

        "download_file" -> {
            // Document tray + downward animated arrow
            drawLine(accent, Offset(cx, cy - u * 0.82f + u * 0.12f * pulse), Offset(cx, cy + u * 0.22f), 3.2f, cap = StrokeCap.Round)
            drawLine(accent, Offset(cx - u * 0.38f, cy - u * 0.10f), Offset(cx, cy + u * 0.26f), 3.2f, cap = StrokeCap.Round)
            drawLine(accent, Offset(cx + u * 0.38f, cy - u * 0.10f), Offset(cx, cy + u * 0.26f), 3.2f, cap = StrokeCap.Round)
            val tray = Path().apply {
                moveTo(cx - u * 0.78f, cy + u * 0.28f)
                lineTo(cx - u * 0.78f, cy + u * 0.72f)
                lineTo(cx + u * 0.78f, cy + u * 0.72f)
                lineTo(cx + u * 0.78f, cy + u * 0.28f)
            }
            drawPath(tray, color = bright, style = stroke)
        }

        "globe_network" -> {
            val r = u * 0.82f
            drawCircle(accent.copy(alpha = 0.14f), radius = r, center = Offset(cx, cy))
            drawCircle(accent, radius = r, center = Offset(cx, cy), style = stroke)
            drawOval(
                color = accent.copy(alpha = 0.85f),
                topLeft = Offset(cx - r * 0.48f, cy - r),
                size = Size(r * 0.96f, r * 2f),
                style = Stroke(1.8f),
            )
            drawLine(bright.copy(alpha = 0.75f), Offset(cx - r, cy), Offset(cx + r, cy), 1.8f)
            drawCircle(bright, radius = 3.5f, center = Offset(cx + cos(pulse * 3f) * r * 0.5f, cy - r * 0.42f))
        }

        "shield_alert" -> {
            val shield = Path().apply {
                moveTo(cx, cy - u * 0.86f)
                lineTo(cx + u * 0.75f, cy - u * 0.50f)
                lineTo(cx + u * 0.58f, cy + u * 0.38f)
                lineTo(cx, cy + u * 0.88f)
                lineTo(cx - u * 0.58f, cy + u * 0.38f)
                lineTo(cx - u * 0.75f, cy - u * 0.50f)
                close()
            }
            drawPath(shield, color = accent.copy(alpha = 0.2f))
            drawPath(shield, color = accent, style = stroke)
            drawLine(bright, Offset(cx, cy - u * 0.35f), Offset(cx, cy + u * 0.14f), 3f, cap = StrokeCap.Round)
            drawCircle(bright, radius = 2.6f, center = Offset(cx, cy + u * 0.40f))
        }

        "message_send" -> {
            val plane = Path().apply {
                moveTo(cx - u * 0.84f, cy - u * 0.54f)
                lineTo(cx + u * 0.88f, cy)
                lineTo(cx - u * 0.84f, cy + u * 0.54f)
                lineTo(cx - u * 0.42f, cy)
                close()
            }
            drawPath(plane, color = accent.copy(alpha = 0.22f))
            drawPath(plane, color = accent, style = stroke)
            drawLine(bright, Offset(cx - u * 0.42f, cy), Offset(cx + u * 0.55f, cy), 2f)
        }

        "timer_clock" -> {
            val r = u * 0.82f
            drawCircle(accent.copy(alpha = 0.14f), radius = r, center = Offset(cx, cy))
            drawCircle(accent, radius = r, center = Offset(cx, cy), style = stroke)
            drawLine(bright, Offset(cx, cy), Offset(cx, cy - r * 0.58f), 2.8f, cap = StrokeCap.Round)
            val handAngle = (pulse * PI * 0.7).toFloat()
            drawLine(accent, Offset(cx, cy), Offset(cx + cos(handAngle) * r * 0.48f, cy + sin(handAngle) * r * 0.48f), 2.5f, cap = StrokeCap.Round)
        }

        "weather_storm" -> {
            // Cloud + lightning bolt
            drawCircle(bright.copy(alpha = 0.85f), radius = u * 0.34f, center = Offset(cx - u * 0.28f, cy - u * 0.24f), style = stroke)
            drawCircle(bright.copy(alpha = 0.85f), radius = u * 0.42f, center = Offset(cx + u * 0.14f, cy - u * 0.32f), style = stroke)
            val bolt = Path().apply {
                moveTo(cx + u * 0.12f, cy - u * 0.05f)
                lineTo(cx - u * 0.22f, cy + u * 0.32f)
                lineTo(cx + u * 0.08f, cy + u * 0.32f)
                lineTo(cx - u * 0.14f, cy + u * 0.82f)
            }
            drawPath(bolt, color = Color(0xFFF59E0B), style = stroke)
        }

        "weather_sun" -> {
            val r = u * 0.42f
            drawCircle(Color(0xFFF59E0B).copy(alpha = 0.28f), radius = r * 1.25f, center = Offset(cx, cy))
            drawCircle(Color(0xFFFBBF24), radius = r, center = Offset(cx, cy), style = stroke)
            for (i in 0 until 8) {
                val a = (i * PI / 4 + pulse * 0.2).toFloat()
                drawLine(
                    color = Color(0xFFFBBF24),
                    start = Offset(cx + cos(a) * r * 1.35f, cy + sin(a) * r * 1.35f),
                    end = Offset(cx + cos(a) * r * 1.85f, cy + sin(a) * r * 1.85f),
                    strokeWidth = 2.4f,
                    cap = StrokeCap.Round,
                )
            }
        }

        "rocket_launch" -> {
            val rocket = Path().apply {
                moveTo(cx, cy - u * 0.90f)
                lineTo(cx + u * 0.36f, cy + u * 0.18f)
                lineTo(cx - u * 0.36f, cy + u * 0.18f)
                close()
            }
            drawPath(rocket, color = accent.copy(alpha = 0.24f))
            drawPath(rocket, color = accent, style = stroke)
            drawCircle(bright, radius = u * 0.14f, center = Offset(cx, cy - u * 0.22f))
            drawLine(Color(0xFFF59E0B), Offset(cx, cy + u * 0.28f), Offset(cx, cy + u * (0.72f + 0.18f * pulse)), 3.4f, cap = StrokeCap.Round)
        }

        "money_chart" -> {
            val bars = listOf(0.32f, 0.54f, 0.44f, 0.78f)
            val barW = u * 0.26f
            bars.forEachIndexed { idx, h ->
                val x = cx - u * 0.72f + idx * (barW + u * 0.14f)
                val barH = u * 1.25f * h
                drawRoundRect(
                    color = if (idx == bars.lastIndex) accent else accent.copy(alpha = 0.55f),
                    topLeft = Offset(x, cy + u * 0.65f - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }

        "music_wave" -> {
            val heights = listOf(0.35f, 0.75f, 0.95f, 0.58f, 0.82f)
            heights.forEachIndexed { idx, h ->
                val anim = h * (0.65f + 0.35f * sin(pulse * PI.toFloat() + idx))
                val x = cx - u * 0.64f + idx * u * 0.32f
                drawLine(
                    color = accent,
                    start = Offset(x, cy - u * 0.68f * anim),
                    end = Offset(x, cy + u * 0.68f * anim),
                    strokeWidth = 3.6f,
                    cap = StrokeCap.Round,
                )
            }
        }

        "code_terminal" -> {
            drawRoundRect(
                color = accent,
                topLeft = Offset(cx - u * 0.88f, cy - u * 0.62f),
                size = Size(u * 1.76f, u * 1.24f),
                cornerRadius = CornerRadius(10f, 10f),
                style = stroke,
            )
            drawLine(bright, Offset(cx - u * 0.45f, cy - u * 0.18f), Offset(cx - u * 0.18f, cy), 2.5f, cap = StrokeCap.Round)
            drawLine(bright, Offset(cx - u * 0.18f, cy), Offset(cx - u * 0.45f, cy + u * 0.18f), 2.5f, cap = StrokeCap.Round)
            drawLine(accent, Offset(cx - u * 0.02f, cy + u * 0.18f), Offset(cx + u * 0.46f, cy + u * 0.18f), 2.5f, cap = StrokeCap.Round)
        }

        "flashlight_beam" -> {
            val beam = Path().apply {
                moveTo(cx - u * 0.18f, cy + u * 0.10f)
                lineTo(cx - u * 0.78f, cy - u * 0.80f)
                lineTo(cx + u * 0.78f, cy - u * 0.80f)
                lineTo(cx + u * 0.18f, cy + u * 0.10f)
                close()
            }
            drawPath(beam, color = Color(0xFFFDE047).copy(alpha = 0.25f + 0.15f * pulse))
            drawRoundRect(
                color = accent,
                topLeft = Offset(cx - u * 0.20f, cy + u * 0.10f),
                size = Size(u * 0.40f, u * 0.68f),
                cornerRadius = CornerRadius(6f, 6f),
                style = stroke,
            )
        }

        "battery_power" -> {
            drawRoundRect(
                color = accent,
                topLeft = Offset(cx - u * 0.44f, cy - u * 0.65f),
                size = Size(u * 0.88f, u * 1.38f),
                cornerRadius = CornerRadius(8f, 8f),
                style = stroke,
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.7f),
                topLeft = Offset(cx - u * 0.28f, cy - u * 0.15f),
                size = Size(u * 0.56f, u * 0.72f),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        "note_scroll" -> {
            drawRoundRect(
                color = accent,
                topLeft = Offset(cx - u * 0.64f, cy - u * 0.78f),
                size = Size(u * 1.28f, u * 1.56f),
                cornerRadius = CornerRadius(10f, 10f),
                style = stroke,
            )
            drawLine(bright, Offset(cx - u * 0.36f, cy - u * 0.32f), Offset(cx + u * 0.36f, cy - u * 0.32f), 2.4f, cap = StrokeCap.Round)
            drawLine(bright, Offset(cx - u * 0.36f, cy), Offset(cx + u * 0.36f, cy), 2.4f, cap = StrokeCap.Round)
            drawLine(accent, Offset(cx - u * 0.36f, cy + u * 0.32f), Offset(cx + u * 0.14f, cy + u * 0.32f), 2.4f, cap = StrokeCap.Round)
        }

        "heart_health" -> {
            val ekg = Path().apply {
                moveTo(cx - u * 0.90f, cy + u * 0.08f)
                lineTo(cx - u * 0.40f, cy + u * 0.08f)
                lineTo(cx - u * 0.20f, cy - u * 0.55f)
                lineTo(cx + u * 0.08f, cy + u * 0.58f)
                lineTo(cx + u * 0.30f, cy - u * 0.18f)
                lineTo(cx + u * 0.45f, cy + u * 0.08f)
                lineTo(cx + u * 0.90f, cy + u * 0.08f)
            }
            drawPath(ekg, color = accent, style = stroke)
        }

        "flame_energy" -> {
            val flame = Path().apply {
                moveTo(cx, cy - u * 0.88f)
                lineTo(cx + u * 0.55f, cy + u * 0.16f)
                lineTo(cx, cy + u * 0.82f)
                lineTo(cx - u * 0.55f, cy + u * 0.16f)
                close()
            }
            drawPath(flame, color = Color(0xFFF97316).copy(alpha = 0.28f))
            drawPath(flame, color = Color(0xFFFB923C), style = stroke)
        }

        else -> {
            // Neural orbit nodes ("brain_spark" & custom cue keys)
            val r = u * 0.72f
            drawCircle(accent.copy(alpha = 0.22f), radius = r * 0.35f, center = Offset(cx, cy))
            drawCircle(bright, radius = r * 0.16f, center = Offset(cx, cy))
            for (i in 0 until 3) {
                val angle = (i * 2 * PI / 3 + pulse * 0.8).toFloat()
                val nx = cx + cos(angle) * r
                val ny = cy + sin(angle) * r
                drawLine(accent.copy(alpha = 0.65f), Offset(cx, cy), Offset(nx, ny), 1.8f)
                drawCircle(accent, radius = 4.2f, center = Offset(nx, ny))
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.JarvisMood
import com.example.model.VisualCueItem
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

private data class CinematicAtmosphereParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var alpha: Float,
    var colorIndex: Int,
    var phase: Float,
)

/**
 * Fullscreen Cinematic "AI Edit" Motion Stage.
 * Activates when JARVIS is narrating / speaking aloud via TTS.
 * Dynamic particle expansion with motion blur, kinetic typography punches,
 * and mood-reactive atmospheric scene transformation.
 */
@Composable
fun CinematicAiEditStage(
    spokenText: String,
    topicTitle: String,
    visualCues: List<VisualCueItem>,
    mood: JarvisMood,
    ttsCadenceRms: Float,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cinematic_motion_anim")
    val timeProgression by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "time_progression",
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val punchPhrases = remember(spokenText) {
        extractKineticPunchPhrases(spokenText)
    }

    var phraseIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(punchPhrases, spokenText) {
        if (punchPhrases.isEmpty()) return@LaunchedEffect
        phraseIndex = 0
        while (true) {
            delay(1400L)
            phraseIndex = (phraseIndex + 1) % punchPhrases.size
        }
    }

    var cueIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(visualCues) {
        if (visualCues.isEmpty()) return@LaunchedEffect
        cueIndex = 0
        while (visualCues.size > 1) {
            delay(2800L)
            cueIndex = (cueIndex + 1) % visualCues.size
        }
    }

    val activeCue = remember(visualCues, cueIndex) {
        if (visualCues.isNotEmpty()) visualCues[cueIndex % visualCues.size] else null
    }

    val themeColors = remember(mood, activeCue?.key) {
        resolveAtmosphericColors(mood, activeCue?.key)
    }
    val primaryAccent = themeColors.first
    val secondaryAccent = themeColors.second

    val particles = remember {
        List(64) {
            CinematicAtmosphereParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                vx = (Random.nextFloat() - 0.5f) * 0.0035f,
                vy = (Random.nextFloat() - 0.5f) * 0.0035f,
                radius = Random.nextFloat() * 2.8f + 1.2f,
                alpha = Random.nextFloat() * 0.65f + 0.25f,
                colorIndex = Random.nextInt(3),
                phase = Random.nextFloat() * 6.28f,
            )
        }
    }

    val currentPhrase = punchPhrases.getOrNull(phraseIndex % punchPhrases.size.coerceAtLeast(1)).orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onStopSpeaking,
            )
            .testTag("cinematic_ai_edit_stage"),
        contentAlignment = Alignment.Center,
    ) {
        // Fullscreen dynamic background, vignette, glow and atmospheric particle streaks
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAtmosphericBackdrop(
                primaryAccent = primaryAccent,
                secondaryAccent = secondaryAccent,
                pulseScale = pulseScale,
                cadenceRms = ttsCadenceRms,
            )

            // Draw motion blur particle field
            drawCinematicParticles(
                particles = particles,
                primaryAccent = primaryAccent,
                secondaryAccent = secondaryAccent,
                time = timeProgression,
                cadenceRms = ttsCadenceRms,
            )

            // Draw large central symbolic geometric constellation
            if (activeCue != null) {
                drawSymbolicGeometricConstellation(
                    cueKey = activeCue.key,
                    accent = primaryAccent,
                    pulse = pulseScale,
                    time = timeProgression,
                )
            }
        }

        // Top Sleek Header Indicator
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 18.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryAccent.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(primaryAccent)
                    )
                    Text(
                        text = (if (topicTitle.isNotBlank()) topicTitle else "JARVIS // ANLATIM").uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = primaryAccent,
                    )
                    if (activeCue != null) {
                        Text(
                            text = "// ${activeCue.label}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                letterSpacing = 0.8.sp,
                            ),
                            color = Color(0xFF94A3B8),
                        )
                    }
                }
            }
        }

        // Center Kinetic Typography Punches
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = currentPhrase,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.86f, animationSpec = tween(240))) togetherWith
                        (fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.08f, animationSpec = tween(180)))
                },
                label = "kinetic_typography_anim",
            ) { phrase ->
                Text(
                    text = phrase.uppercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        letterSpacing = 1.5.sp,
                        lineHeight = 34.sp,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Bottom Frameless Live Subtitle & Stop Speaking Affordance
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = spokenText.take(180) + if (spokenText.length > 180) "…" else "",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.4.sp,
                ),
                color = Color(0xFFE2E8F0),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
                    .clickable(onClick = onStopSpeaking)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = "Durdur",
                    tint = JarvisCrimson,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "DURDUR",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = JarvisCrimson,
                )
            }
        }
    }
}

private fun DrawScope.drawAtmosphericBackdrop(
    primaryAccent: Color,
    secondaryAccent: Color,
    pulseScale: Float,
    cadenceRms: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxDim = size.maxDimension

    // Deep central atmospheric glow
    val glowRadius = (maxDim * 0.45f) * (0.85f + 0.30f * pulseScale + 0.35f * cadenceRms)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryAccent.copy(alpha = 0.22f + 0.16f * cadenceRms),
                secondaryAccent.copy(alpha = 0.08f + 0.08f * cadenceRms),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = Offset(cx, cy),
    )

    // Cinematic Vignette: dark borders fading towards center
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.45f),
                Color.Black.copy(alpha = 0.88f),
            ),
            center = Offset(cx, cy),
            radius = maxDim * 0.70f,
        )
    )
}

private fun DrawScope.drawCinematicParticles(
    particles: List<CinematicAtmosphereParticle>,
    primaryAccent: Color,
    secondaryAccent: Color,
    time: Float,
    cadenceRms: Float,
) {
    val w = size.width
    val h = size.height
    val speedMultiplier = 1f + cadenceRms * 3.5f

    for (p in particles) {
        // Move particle
        p.x += p.vx * speedMultiplier
        p.y += p.vy * speedMultiplier

        if (p.x < 0f) p.x = 1f
        if (p.x > 1f) p.x = 0f
        if (p.y < 0f) p.y = 1f
        if (p.y > 1f) p.y = 0f

        val px = p.x * w
        val py = p.y * h

        val color = when (p.colorIndex) {
            0 -> primaryAccent
            1 -> secondaryAccent
            else -> Color(0xFFF8FAFC)
        }

        val effAlpha = (p.alpha * (0.7f + 0.3f * sin(time + p.phase))).coerceIn(0.1f, 1f)

        // Draw particle motion streak (streak line in direction of velocity)
        val streakLength = (16f + 30f * cadenceRms)
        drawLine(
            color = color.copy(alpha = effAlpha * 0.5f),
            start = Offset(px - p.vx * streakLength * w, py - p.vy * streakLength * h),
            end = Offset(px, py),
            strokeWidth = p.radius * 0.9f,
            cap = StrokeCap.Round,
        )

        // Draw bright particle core
        drawCircle(
            color = color.copy(alpha = effAlpha),
            radius = p.radius * (1f + 0.4f * cadenceRms),
            center = Offset(px, py),
        )
    }
}

private fun DrawScope.drawSymbolicGeometricConstellation(
    cueKey: String,
    accent: Color,
    pulse: Float,
    time: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dim = size.minDimension * 0.40f

    // Draw rotating subtle outer constellation ring
    val ringR = dim * 0.85f * pulse
    drawCircle(
        color = accent.copy(alpha = 0.12f),
        radius = ringR,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 16f), time * 20f)),
    )

    // Delegate vector symbol rendering
    drawVisualCueVector(
        cueKey = cueKey,
        accent = accent,
        pulse = pulse,
    )
}

private fun extractKineticPunchPhrases(text: String): List<String> {
    if (text.isBlank()) return listOf("JARVIS // AKTİF")

    val words = text.trim()
        .replace(Regex("""[.,;:!?/\\()\[\]"']"""), " ")
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() }

    if (words.isEmpty()) return listOf("JARVIS // AKTİF")

    val phrases = mutableListOf<String>()
    var i = 0
    while (i < words.size) {
        val count = when {
            words.size - i <= 3 -> words.size - i
            words[i].length > 9 -> 2
            else -> 3
        }
        val chunk = words.subList(i, (i + count).coerceAtMost(words.size)).joinToString(" ")
        phrases.add(chunk)
        i += count
    }
    return phrases.take(12)
}

private fun resolveAtmosphericColors(mood: JarvisMood, cueKey: String?): Pair<Color, Color> {
    return when {
        cueKey in setOf("eagle_crest", "stadium", "trophy_cup", "ball_sport") ->
            Color(0xFF38BDF8) to Color(0xFFFBBF24) // Cyan & Amber Gold for Sports/Victory
        cueKey in setOf("shield_alert", "flame_energy") || mood == JarvisMood.ALERT ->
            JarvisCrimson to Color(0xFFF97316) // Crimson & Electric Orange for Alert/Crisis
        cueKey in setOf("code_terminal", "rocket_launch", "brain_spark") || mood == JarvisMood.ANALYTICAL ->
            JarvisCyan to JarvisEmerald // Cyber Neon & Matrix Emerald for Tech/Code
        cueKey in setOf("music_wave") ->
            Color(0xFFA855F7) to Color(0xFF38BDF8) // Violet & Cyan for Media/Music
        cueKey in setOf("money_chart") ->
            JarvisEmerald to JarvisAmber
        mood == JarvisMood.SERIOUS ->
            Color(0xFF64748B) to JarvisCyan
        else ->
            JarvisCyan to Color(0xFF818CF8) // Neutral Electric Blue
    }
}

package com.example.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.JarvisMood
import com.example.model.JarvisVisualState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Custom Canvas-based reactive particle cloud / nebula representing JARVIS.
 * Renders hundreds of glowing, dusty particles forming an organic, breathing sphere
 * that reacts in real time to microphone RMS level, conversation state, and JARVIS mood.
 */
class ParticleNebulaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    private data class NebulaParticle(
        val baseAngle: Float,
        val baseRadiusNorm: Float, // 0..1 concentrated toward core
        val orbitSpeed: Float,
        val radialFreq: Float,
        val phase: Float,
        val sizeScale: Float,
        val layer: Int, // 0 = core dust, 1 = mid swirl, 2 = outer mist
        val baseAlpha: Float,
        val colorMix: Float, // 0 = primary, 1 = secondary/highlight
    )

    private var compactMode: Boolean = false
    private var visualState: JarvisVisualState = JarvisVisualState.IDLE
    private var mood: JarvisMood = JarvisMood.NEUTRAL
    private var targetRms: Float = 0f
    private var smoothedRms: Float = 0f

    private var currentPrimaryColor: Int = Color.rgb(30, 86, 198)
    private var currentSecondaryColor: Int = Color.rgb(125, 211, 252)
    private var shockwaveProgress: Float = 1f // < 1f means active shockwave ring
    private var lastFrameNanos: Long = 0L
    private var elapsedSeconds: Float = 0f
    private var isRunning: Boolean = false

    private val density = resources.displayMetrics.density
    private var particles: List<NebulaParticle> = createParticles(count = 290)

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
    }

    private var cachedGlowRadius: Float = -1f
    private var cachedGlowPrimary: Int = 0
    private var cachedGlowSecondary: Int = 0

    fun setCompactMode(compact: Boolean) {
        if (compactMode == compact) return
        compactMode = compact
        particles = createParticles(count = if (compact) 74 else 290)
        cachedGlowRadius = -1f
        invalidate()
    }

    fun updateState(
        state: JarvisVisualState,
        newMood: JarvisMood,
        rmsLevel: Float,
    ) {
        val stateChanged = visualState != state || mood != newMood
        val becameAlert = (state == JarvisVisualState.ERROR && visualState != JarvisVisualState.ERROR) ||
            (newMood == JarvisMood.ALERT && mood != JarvisMood.ALERT) ||
            (newMood == JarvisMood.ENERGETIC && mood != JarvisMood.ENERGETIC && state == JarvisVisualState.SPEAKING)

        visualState = state
        mood = newMood
        targetRms = rmsLevel.coerceIn(0f, 1f)

        if (becameAlert && stateChanged) {
            shockwaveProgress = 0f
        }
    }

    fun triggerShockwave() {
        shockwaveProgress = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isRunning) {
            isRunning = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDetachedFromWindow() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos
        }
        val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = frameTimeNanos

        // Smooth audio RMS & advance animation clock
        val rmsAttack = if (targetRms > smoothedRms) 14f else 6f
        smoothedRms += (targetRms - smoothedRms) * (dt * rmsAttack).coerceAtMost(1f)

        val speedMultiplier = when (visualState) {
            JarvisVisualState.ERROR -> 2.3f
            JarvisVisualState.LISTENING -> 1.45f + smoothedRms * 2.1f
            JarvisVisualState.THINKING -> 1.75f
            JarvisVisualState.SPEAKING -> when (mood) {
                JarvisMood.ENERGETIC -> 1.9f + smoothedRms * 1.1f
                JarvisMood.SERIOUS -> 1.65f + smoothedRms * 0.8f
                JarvisMood.ANALYTICAL -> 1.25f + smoothedRms * 0.6f
                JarvisMood.ALERT -> 2.1f
                JarvisMood.NEUTRAL -> 1.35f + smoothedRms * 0.7f
            }
            JarvisVisualState.IDLE -> when (mood) {
                JarvisMood.SERIOUS -> 1.05f
                JarvisMood.ANALYTICAL -> 0.85f
                JarvisMood.ENERGETIC -> 1.1f
                JarvisMood.ALERT -> 1.5f
                JarvisMood.NEUTRAL -> 0.68f
            }
        }
        elapsedSeconds += dt * speedMultiplier

        // Smooth color interpolation
        val (targetPrim, targetSec) = targetPalette(visualState, mood)
        val colorLerp = (dt * 4.5f).coerceAtMost(1f)
        currentPrimaryColor = lerpRgb(currentPrimaryColor, targetPrim, colorLerp)
        currentSecondaryColor = lerpRgb(currentSecondaryColor, targetSec, colorLerp)

        if (shockwaveProgress < 1f) {
            shockwaveProgress = (shockwaveProgress + dt * 1.7f).coerceAtMost(1f)
        }

        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f
        val cy = h * 0.5f
        val minDim = minOf(w, h)
        val baseRadius = if (compactMode) minDim * 0.34f else minDim * 0.36f

        // Organic breathing + audio expansion
        val breathAmp = if (visualState == JarvisVisualState.IDLE) 0.045f else 0.06f
        val breath = 1f + sin(elapsedSeconds * 1.45f) * breathAmp
        val audioBoost = smoothedRms * (if (visualState == JarvisVisualState.LISTENING) 0.24f else 0.14f)
        val burstScale = if (shockwaveProgress < 1f && visualState == JarvisVisualState.ERROR) {
            sin(shockwaveProgress * PI.toFloat()) * 0.16f
        } else {
            0f
        }
        val activeRadius = baseRadius * (breath + audioBoost + burstScale)

        // Subtle ambient core glow
        val glowRadius = activeRadius * 1.32f
        if (
            cachedGlowRadius <= 0f ||
            kotlin.math.abs(cachedGlowRadius - glowRadius) > 3f ||
            cachedGlowPrimary != currentPrimaryColor ||
            cachedGlowSecondary != currentSecondaryColor
        ) {
            cachedGlowRadius = glowRadius
            cachedGlowPrimary = currentPrimaryColor
            cachedGlowSecondary = currentSecondaryColor
            coreGlowPaint.shader = RadialGradient(
                cx,
                cy,
                glowRadius.coerceAtLeast(1f),
                intArrayOf(
                    withAlpha(currentSecondaryColor, if (compactMode) 95 else 72),
                    withAlpha(currentPrimaryColor, if (compactMode) 48 else 36),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, glowRadius, coreGlowPaint)

        // Optional shockwave ring on Error / Energetic burst
        if (shockwaveProgress < 1f) {
            val ringRadius = activeRadius * (0.55f + shockwaveProgress * 0.9f)
            val ringAlpha = ((1f - shockwaveProgress) * 165f).toInt().coerceIn(0, 255)
            shockPaint.color = withAlpha(currentSecondaryColor, ringAlpha)
            canvas.drawCircle(cx, cy, ringRadius, shockPaint)
        }

        // Subtle segmented HUD orbital arcs in full mode
        if (!compactMode) {
            val arcR = activeRadius * 1.15f
            arcPaint.color = withAlpha(currentSecondaryColor, 46)
            val rotDeg = (elapsedSeconds * 14f) % 360f
            canvas.drawArc(cx - arcR, cy - arcR, cx + arcR, cy + arcR, rotDeg, 48f, false, arcPaint)
            canvas.drawArc(cx - arcR, cy - arcR, cx + arcR, cy + arcR, rotDeg + 180f, 48f, false, arcPaint)
        }

        val jitterScale = when {
            visualState == JarvisVisualState.ERROR -> (1f - shockwaveProgress * 0.5f) * 7f * density
            visualState == JarvisVisualState.LISTENING -> smoothedRms * 5.2f * density
            visualState == JarvisVisualState.SPEAKING -> (0.8f + smoothedRms * 3.5f) * density
            else -> 0f
        }

        val waveStrength = when (mood) {
            JarvisMood.ANALYTICAL -> 0.14f
            JarvisMood.SERIOUS -> 0.08f
            JarvisMood.ENERGETIC -> 0.16f
            JarvisMood.ALERT -> 0.18f
            JarvisMood.NEUTRAL -> 0.10f
        }

        val orbitTightness = if (mood == JarvisMood.SERIOUS) 0.88f else 1.0f

        // Render dusty multi-layered particle swarm
        for (p in particles) {
            val angle = p.baseAngle + elapsedSeconds * p.orbitSpeed *
                (if (p.layer == 0) 1.15f else if (p.layer == 1) 0.8f else 0.45f)

            // Non-circular organic silhouette distortion
            val organicEdge = 1f +
                0.11f * sin(angle * 3f + elapsedSeconds * 0.75f) +
                0.08f * cos(angle * 5f - elapsedSeconds * 1.1f) +
                0.05f * sin(angle * 7f + elapsedSeconds * 1.5f + p.phase)

            // Radial breathing/dispersal wave per particle
            val dispersion = sin(elapsedSeconds * p.radialFreq + p.phase) * waveStrength *
                (0.35f + p.baseRadiusNorm * 0.85f)

            val audioExpand = if (p.layer >= 1) smoothedRms * 0.22f * p.baseRadiusNorm else smoothedRms * 0.07f
            val rNorm = (p.baseRadiusNorm * orbitTightness + dispersion + audioExpand).coerceIn(0.02f, 1.32f)
            val dist = activeRadius * rNorm * organicEdge

            val jitterX = if (jitterScale > 0.1f) sin(elapsedSeconds * 21f + p.phase * 3f) * jitterScale else 0f
            val jitterY = if (jitterScale > 0.1f) cos(elapsedSeconds * 19f + p.phase * 5f) * jitterScale else 0f

            val px = cx + cos(angle) * dist + jitterX
            val py = cy + sin(angle) * dist + jitterY

            val baseDotPx = (if (compactMode) 1.1f else 1.45f) * density * p.sizeScale
            val dotRadius = baseDotPx * (1f + smoothedRms * 0.28f)

            val particleColor = lerpRgb(currentPrimaryColor, currentSecondaryColor, p.colorMix)
            val flicker = 0.76f + 0.24f * sin(elapsedSeconds * 2.2f + p.phase * 2f)
            val edgeFade = (1.18f - rNorm * 0.48f).coerceIn(0.22f, 1f)
            val alpha = (p.baseAlpha * flicker * edgeFade * 255f).toInt().coerceIn(18, 255)

            // Soft outer dust halo on brighter particles
            if (p.sizeScale > 1.08f) {
                haloPaint.color = withAlpha(particleColor, (alpha * 0.26f).toInt())
                canvas.drawCircle(px, py, dotRadius * 2.35f, haloPaint)
            }

            particlePaint.color = withAlpha(particleColor, alpha)
            canvas.drawCircle(px, py, dotRadius, particlePaint)
        }
    }

    private fun targetPalette(state: JarvisVisualState, currentMood: JarvisMood): Pair<Int, Int> {
        if (state == JarvisVisualState.ERROR || currentMood == JarvisMood.ALERT) {
            return Color.rgb(225, 29, 72) to Color.rgb(253, 164, 175) // Crimson / rose-white
        }
        if (state == JarvisVisualState.LISTENING) {
            return Color.rgb(6, 182, 212) to Color.rgb(52, 211, 153) // Cyan / emerald green
        }
        return when (currentMood) {
            JarvisMood.NEUTRAL -> if (state == JarvisVisualState.IDLE) {
                Color.rgb(23, 63, 158) to Color.rgb(96, 165, 250) // Deep night blue / ice blue
            } else {
                Color.rgb(37, 99, 235) to Color.rgb(125, 211, 252)
            }
            JarvisMood.SERIOUS -> Color.rgb(217, 119, 6) to Color.rgb(253, 224, 71) // Amber / gold
            JarvisMood.ANALYTICAL -> Color.rgb(99, 102, 241) to Color.rgb(192, 132, 252) // Indigo / violet
            JarvisMood.ENERGETIC -> Color.rgb(6, 182, 212) to Color.rgb(103, 232, 249) // Electric turquoise
            JarvisMood.ALERT -> Color.rgb(225, 29, 72) to Color.rgb(253, 164, 175)
        }
    }

    private fun lerpRgb(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * f).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * f).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun createParticles(count: Int): List<NebulaParticle> {
        val rng = Random(4207)
        return List(count) { idx ->
            val layer = when {
                idx < count * 0.42f -> 0
                idx < count * 0.80f -> 1
                else -> 2
            }
            // Concentrated toward the center, sparse dusty haze outward
            val u = rng.nextFloat()
            val radiusNorm = when (layer) {
                0 -> sqrt(u) * 0.56f
                1 -> 0.34f + sqrt(u) * 0.56f
                else -> 0.62f + u * 0.52f
            }
            val dirSign = if (idx % 3 == 0) -1f else 1f
            NebulaParticle(
                baseAngle = rng.nextFloat() * (2f * PI.toFloat()),
                baseRadiusNorm = radiusNorm,
                orbitSpeed = dirSign * (0.14f + rng.nextFloat() * 0.36f),
                radialFreq = 0.8f + rng.nextFloat() * 1.9f,
                phase = rng.nextFloat() * (2f * PI.toFloat()),
                sizeScale = when (layer) {
                    0 -> 0.7f + rng.nextFloat() * 0.85f
                    1 -> 0.55f + rng.nextFloat() * 0.9f
                    else -> 0.4f + rng.nextFloat() * 0.65f
                },
                layer = layer,
                baseAlpha = when (layer) {
                    0 -> 0.55f + rng.nextFloat() * 0.45f
                    1 -> 0.40f + rng.nextFloat() * 0.45f
                    else -> 0.22f + rng.nextFloat() * 0.35f
                },
                colorMix = rng.nextFloat(),
            )
        }
    }
}

@Composable
fun ParticleNebulaCanvas(
    visualState: JarvisVisualState,
    mood: JarvisMood,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ParticleNebulaView(ctx).apply {
                setCompactMode(compactMode)
                updateState(visualState, mood, rmsLevel)
            }
        },
        update = { view ->
            view.setCompactMode(compactMode)
            view.updateState(visualState, mood, rmsLevel)
        },
    )
}
